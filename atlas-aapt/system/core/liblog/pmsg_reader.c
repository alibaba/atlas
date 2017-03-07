/*
 * Copyright (C) 2007-2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <ctype.h>
#include <errno.h>
#include <fcntl.h>
#include <stdbool.h>
#include <stdlib.h>
#include <string.h>
#include <sys/types.h>

#include <private/android_filesystem_config.h>
#include <private/android_logger.h>

#include "config_read.h"
#include "logger.h"

static int pmsgAvailable(log_id_t logId);
static int pmsgVersion(struct android_log_logger *logger,
                       struct android_log_transport_context *transp);
static int pmsgRead(struct android_log_logger_list *logger_list,
                    struct android_log_transport_context *transp,
                    struct log_msg *log_msg);
static void pmsgClose(struct android_log_logger_list *logger_list,
                      struct android_log_transport_context *transp);
static int pmsgClear(struct android_log_logger *logger,
                     struct android_log_transport_context *transp);

LIBLOG_HIDDEN struct android_log_transport_read pmsgLoggerRead = {
    .node = { &pmsgLoggerRead.node, &pmsgLoggerRead.node },
    .name = "pmsg",
    .available = pmsgAvailable,
    .version = pmsgVersion,
    .read = pmsgRead,
    .poll = NULL,
    .close = pmsgClose,
    .clear = pmsgClear,
    .setSize = NULL,
    .getSize = NULL,
    .getReadableSize = NULL,
    .getPrune = NULL,
    .setPrune = NULL,
    .getStats = NULL,
};

static int pmsgAvailable(log_id_t logId)
{
    if (logId > LOG_ID_SECURITY) {
        return -EINVAL;
    }
    if (access("/dev/pmsg0", W_OK) == 0) {
        return 0;
    }
    return -EBADF;
}

/* Determine the credentials of the caller */
static bool uid_has_log_permission(uid_t uid)
{
    return (uid == AID_SYSTEM) || (uid == AID_LOG) || (uid == AID_ROOT);
}

static uid_t get_best_effective_uid()
{
    uid_t euid;
    uid_t uid;
    gid_t gid;
    ssize_t i;
    static uid_t last_uid = (uid_t) -1;

    if (last_uid != (uid_t) -1) {
        return last_uid;
    }
    uid = __android_log_uid();
    if (uid_has_log_permission(uid)) {
        return last_uid = uid;
    }
    euid = geteuid();
    if (uid_has_log_permission(euid)) {
        return last_uid = euid;
    }
    gid = getgid();
    if (uid_has_log_permission(gid)) {
        return last_uid = gid;
    }
    gid = getegid();
    if (uid_has_log_permission(gid)) {
        return last_uid = gid;
    }
    i = getgroups((size_t) 0, NULL);
    if (i > 0) {
        gid_t list[i];

        getgroups(i, list);
        while (--i >= 0) {
            if (uid_has_log_permission(list[i])) {
                return last_uid = list[i];
            }
        }
    }
    return last_uid = uid;
}

static int pmsgClear(struct android_log_logger *logger __unused,
                     struct android_log_transport_context *transp __unused)
{
    if (uid_has_log_permission(get_best_effective_uid())) {
        return unlink("/sys/fs/pstore/pmsg-ramoops-0");
    }
    errno = EPERM;
    return -1;
}

/*
 * returns the logger version
 */
static int pmsgVersion(struct android_log_logger *logger __unused,
                       struct android_log_transport_context *transp __unused)
{
    return 4;
}

static int pmsgRead(struct android_log_logger_list *logger_list,
                    struct android_log_transport_context *transp,
                    struct log_msg *log_msg)
{
    ssize_t ret;
    off_t current, next;
    uid_t uid;
    struct android_log_logger *logger;
    struct __attribute__((__packed__)) {
        android_pmsg_log_header_t p;
        android_log_header_t l;
    } buf;
    static uint8_t preread_count;
    bool is_system;

    memset(log_msg, 0, sizeof(*log_msg));

    if (transp->context.fd <= 0) {
        int fd = open("/sys/fs/pstore/pmsg-ramoops-0", O_RDONLY | O_CLOEXEC);

        if (fd < 0) {
            return -errno;
        }
        if (fd == 0) { /* Argggg */
            fd = open("/sys/fs/pstore/pmsg-ramoops-0", O_RDONLY | O_CLOEXEC);
            close(0);
            if (fd < 0) {
                return -errno;
            }
        }
        transp->context.fd = fd;
        preread_count = 0;
    }

    while(1) {
        if (preread_count < sizeof(buf)) {
            ret = TEMP_FAILURE_RETRY(read(transp->context.fd,
                                          &buf.p.magic + preread_count,
                                          sizeof(buf) - preread_count));
            if (ret < 0) {
                return -errno;
            }
            preread_count += ret;
        }
        if (preread_count != sizeof(buf)) {
            return preread_count ? -EIO : -EAGAIN;
        }
        if ((buf.p.magic != LOGGER_MAGIC)
         || (buf.p.len <= sizeof(buf))
         || (buf.p.len > (sizeof(buf) + LOGGER_ENTRY_MAX_PAYLOAD))
         || (buf.l.id >= LOG_ID_MAX)
         || (buf.l.realtime.tv_nsec >= NS_PER_SEC)) {
            do {
                memmove(&buf.p.magic, &buf.p.magic + 1, --preread_count);
            } while (preread_count && (buf.p.magic != LOGGER_MAGIC));
            continue;
        }
        preread_count = 0;

        if ((transp->logMask & (1 << buf.l.id)) &&
                ((!logger_list->start.tv_sec && !logger_list->start.tv_nsec) ||
                    ((logger_list->start.tv_sec <= buf.l.realtime.tv_sec) &&
                        ((logger_list->start.tv_sec != buf.l.realtime.tv_sec) ||
                            (logger_list->start.tv_nsec <=
                                buf.l.realtime.tv_nsec)))) &&
                (!logger_list->pid || (logger_list->pid == buf.p.pid))) {
            uid = get_best_effective_uid();
            is_system = uid_has_log_permission(uid);
            if (is_system || (uid == buf.p.uid)) {
                ret = TEMP_FAILURE_RETRY(read(transp->context.fd,
                                          is_system ?
                                              log_msg->entry_v4.msg :
                                              log_msg->entry_v3.msg,
                                          buf.p.len - sizeof(buf)));
                if (ret < 0) {
                    return -errno;
                }
                if (ret != (ssize_t)(buf.p.len - sizeof(buf))) {
                    return -EIO;
                }

                log_msg->entry_v4.len = buf.p.len - sizeof(buf);
                log_msg->entry_v4.hdr_size = is_system ?
                    sizeof(log_msg->entry_v4) :
                    sizeof(log_msg->entry_v3);
                log_msg->entry_v4.pid = buf.p.pid;
                log_msg->entry_v4.tid = buf.l.tid;
                log_msg->entry_v4.sec = buf.l.realtime.tv_sec;
                log_msg->entry_v4.nsec = buf.l.realtime.tv_nsec;
                log_msg->entry_v4.lid = buf.l.id;
                if (is_system) {
                    log_msg->entry_v4.uid = buf.p.uid;
                }

                return ret + log_msg->entry_v4.hdr_size;
            }
        }

        current = TEMP_FAILURE_RETRY(lseek(transp->context.fd,
                                           (off_t)0, SEEK_CUR));
        if (current < 0) {
            return -errno;
        }
        next = TEMP_FAILURE_RETRY(lseek(transp->context.fd,
                                        (off_t)(buf.p.len - sizeof(buf)),
                                        SEEK_CUR));
        if (next < 0) {
            return -errno;
        }
        if ((next - current) != (ssize_t)(buf.p.len - sizeof(buf))) {
            return -EIO;
        }
    }
}

static void pmsgClose(struct android_log_logger_list *logger_list __unused,
                      struct android_log_transport_context *transp) {
    if (transp->context.fd > 0) {
        close (transp->context.fd);
    }
    transp->context.fd = 0;
}

LIBLOG_ABI_PRIVATE ssize_t __android_log_pmsg_file_read(
        log_id_t logId,
        char prio,
        const char *prefix,
        __android_log_pmsg_file_read_fn fn, void *arg) {
    ssize_t ret;
    struct android_log_logger_list logger_list;
    struct android_log_transport_context transp;
    struct content {
        struct listnode node;
        union {
            struct logger_entry_v4 entry;
            struct logger_entry_v4 entry_v4;
            struct logger_entry_v3 entry_v3;
            struct logger_entry_v2 entry_v2;
            struct logger_entry    entry_v1;
        };
    } *content;
    struct names {
        struct listnode node;
        struct listnode content;
        log_id_t id;
        char prio;
        char name[];
    } *names;
    struct listnode name_list;
    struct listnode *node, *n;
    size_t len, prefix_len;

    if (!fn) {
        return -EINVAL;
    }

    /* Add just enough clues in logger_list and transp to make API function */
    memset(&logger_list, 0, sizeof(logger_list));
    memset(&transp, 0, sizeof(transp));

    logger_list.mode = ANDROID_LOG_PSTORE |
                       ANDROID_LOG_NONBLOCK |
                       ANDROID_LOG_RDONLY;
    transp.logMask = (unsigned)-1;
    if (logId != LOG_ID_ANY) {
        transp.logMask = (1 << logId);
    }
    transp.logMask &= ~((1 << LOG_ID_KERNEL) |
                        (1 << LOG_ID_EVENTS) |
                        (1 << LOG_ID_SECURITY));
    if (!transp.logMask) {
        return -EINVAL;
    }

    /* Initialize name list */
    list_init(&name_list);

    ret = SSIZE_MAX;

    /* Validate incoming prefix, shift until it contains only 0 or 1 : or / */
    prefix_len = 0;
    if (prefix) {
        const char *prev = NULL, *last = NULL, *cp = prefix;
        while ((cp = strpbrk(cp, "/:"))) {
            prev = last;
            last = cp;
            cp = cp + 1;
        }
        if (prev) {
            prefix = prev + 1;
        }
        prefix_len = strlen(prefix);
    }

    /* Read the file content */
    while (pmsgRead(&logger_list, &transp, &transp.logMsg) > 0) {
        char *cp;
        size_t hdr_size = transp.logMsg.entry.hdr_size ?
            transp.logMsg.entry.hdr_size : sizeof(transp.logMsg.entry_v1);
        char *msg = (char *)&transp.logMsg + hdr_size;
        char *split = NULL;

        /* Check for invalid sequence number */
        if ((transp.logMsg.entry.nsec % ANDROID_LOG_PMSG_FILE_SEQUENCE) ||
                ((transp.logMsg.entry.nsec / ANDROID_LOG_PMSG_FILE_SEQUENCE) >=
                    ANDROID_LOG_PMSG_FILE_MAX_SEQUENCE)) {
            continue;
        }

        /* Determine if it has <dirbase>:<filebase> format for tag */
        len = transp.logMsg.entry.len - sizeof(prio);
        for (cp = msg + sizeof(prio);
                *cp && isprint(*cp) && !isspace(*cp) && --len;
                ++cp) {
            if (*cp == ':') {
                if (split) {
                    break;
                }
                split = cp;
            }
        }
        if (*cp || !split) {
            continue;
        }

        /* Filters */
        if (prefix_len && strncmp(msg + sizeof(prio), prefix, prefix_len)) {
            size_t offset;
            /*
             *   Allow : to be a synonym for /
             * Things we do dealing with const char * and do not alloc
             */
            split = strchr(prefix, ':');
            if (split) {
                continue;
            }
            split = strchr(prefix, '/');
            if (!split) {
                continue;
            }
            offset = split - prefix;
            if ((msg[offset + sizeof(prio)] != ':') ||
                    strncmp(msg + sizeof(prio), prefix, offset)) {
                continue;
            }
            ++offset;
            if ((prefix_len > offset) &&
                    strncmp(&msg[offset + sizeof(prio)], split + 1, prefix_len - offset)) {
                continue;
            }
        }

        if ((prio != ANDROID_LOG_ANY) && (*msg < prio)) {
            continue;
        }

        /* check if there is an existing entry */
        list_for_each(node, &name_list) {
            names = node_to_item(node, struct names, node);
            if (!strcmp(names->name, msg + sizeof(prio)) &&
                    (names->id == transp.logMsg.entry.lid) &&
                    (names->prio == *msg)) {
                break;
            }
        }

        /* We do not have an existing entry, create and add one */
        if (node == &name_list) {
            static const char numbers[] = "0123456789";
            unsigned long long nl;

            len = strlen(msg + sizeof(prio)) + 1;
            names = calloc(1, sizeof(*names) + len);
            if (!names) {
                ret = -ENOMEM;
                break;
            }
            strcpy(names->name, msg + sizeof(prio));
            names->id = transp.logMsg.entry.lid;
            names->prio = *msg;
            list_init(&names->content);
            /*
             * Insert in reverse numeric _then_ alpha sorted order as
             * representative of log rotation:
             *
             *   log.10
             *   klog.10
             *   . . .
             *   log.2
             *   klog.2
             *   log.1
             *   klog.1
             *   log
             *   klog
             *
             * thus when we present the content, we are provided the oldest
             * first, which when 'refreshed' could spill off the end of the
             * pmsg FIFO but retaining the newest data for last with best
             * chances to survive.
             */
            nl = 0;
            cp = strpbrk(names->name, numbers);
            if (cp) {
                nl = strtoull(cp, NULL, 10);
            }
            list_for_each_reverse(node, &name_list) {
                struct names *a_name = node_to_item(node, struct names, node);
                const char *r = a_name->name;
                int compare = 0;

                unsigned long long nr = 0;
                cp = strpbrk(r, numbers);
                if (cp) {
                    nr = strtoull(cp, NULL, 10);
                }
                if (nr != nl) {
                    compare = (nl > nr) ? 1 : -1;
                }
                if (compare == 0) {
                    compare = strcmp(names->name, r);
                }
                if (compare <= 0) {
                    break;
                }
            }
            list_add_head(node, &names->node);
        }

        /* Remove any file fragments that match our sequence number */
        list_for_each_safe(node, n, &names->content) {
            content = node_to_item(node, struct content, node);
            if (transp.logMsg.entry.nsec == content->entry.nsec) {
                list_remove(&content->node);
                free(content);
            }
        }

        /* Add content */
        content = calloc(1, sizeof(content->node) +
                hdr_size + transp.logMsg.entry.len);
        if (!content) {
            ret = -ENOMEM;
            break;
        }
        memcpy(&content->entry, &transp.logMsg.entry,
               hdr_size + transp.logMsg.entry.len);

        /* Insert in sequence number sorted order, to ease reconstruction */
        list_for_each_reverse(node, &names->content) {
            if ((node_to_item(node, struct content, node))->entry.nsec <
                    transp.logMsg.entry.nsec) {
                break;
            }
        }
        list_add_head(node, &content->node);
    }
    pmsgClose(&logger_list, &transp);

    /* Progress through all the collected files */
    list_for_each_safe(node, n, &name_list) {
        struct listnode *content_node, *m;
        char *buf;
        size_t sequence, tag_len;

        names = node_to_item(node, struct names, node);

        /* Construct content into a linear buffer */
        buf = NULL;
        len = 0;
        sequence = 0;
        tag_len = strlen(names->name) + sizeof(char); /* tag + nul */
        list_for_each_safe(content_node, m, &names->content) {
            ssize_t add_len;

            content = node_to_item(content_node, struct content, node);
            add_len = content->entry.len - tag_len - sizeof(prio);
            if (add_len <= 0) {
                list_remove(content_node);
                free(content);
                continue;
            }

            if (!buf) {
                buf = malloc(sizeof(char));
                if (!buf) {
                    ret = -ENOMEM;
                    list_remove(content_node);
                    free(content);
                    continue;
                }
                *buf = '\0';
            }

            /* Missing sequence numbers */
            while (sequence < content->entry.nsec) {
                /* plus space for enforced nul */
                buf = realloc(buf, len + sizeof(char) + sizeof(char));
                if (!buf) {
                    break;
                }
                buf[len] = '\f'; /* Mark missing content with a form feed */
                buf[++len] = '\0';
                sequence += ANDROID_LOG_PMSG_FILE_SEQUENCE;
            }
            if (!buf) {
                ret = -ENOMEM;
                list_remove(content_node);
                free(content);
                continue;
            }
            /* plus space for enforced nul */
            buf = realloc(buf, len + add_len + sizeof(char));
            if (!buf) {
                ret = -ENOMEM;
                list_remove(content_node);
                free(content);
                continue;
            }
            memcpy(buf + len,
                   (char *)&content->entry + content->entry.hdr_size +
                       tag_len + sizeof(prio),
                   add_len);
            len += add_len;
            buf[len] = '\0'; /* enforce trailing hidden nul */
            sequence = content->entry.nsec + ANDROID_LOG_PMSG_FILE_SEQUENCE;

            list_remove(content_node);
            free(content);
        }
        if (buf) {
            if (len) {
                /* Buffer contains enforced trailing nul just beyond length */
                ssize_t r;
                *strchr(names->name, ':') = '/'; /* Convert back to filename */
                r = (*fn)(names->id, names->prio, names->name, buf, len, arg);
                if ((ret >= 0) && (r > 0)) {
                    if (ret == SSIZE_MAX) {
                        ret = r;
                    } else {
                        ret += r;
                    }
                } else if (r < ret) {
                    ret = r;
                }
            }
            free(buf);
        }
        list_remove(node);
        free(names);
    }
    return (ret == SSIZE_MAX) ? -ENOENT : ret;
}
