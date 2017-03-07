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

#include <errno.h>
#include <stdatomic.h>
#include <stdlib.h>
#include <string.h>
#include <sys/time.h>

#ifdef __BIONIC__
#include <android/set_abort_message.h>
#endif

#include <log/event_tag_map.h>
#include <log/logd.h>
#include <log/logger.h>
#include <log/log_read.h>
#include <private/android_filesystem_config.h>
#include <private/android_logger.h>

#include "config_write.h"
#include "log_portability.h"
#include "logger.h"

#define LOG_BUF_SIZE 1024

static int __write_to_log_init(log_id_t, struct iovec *vec, size_t nr);
static int (*write_to_log)(log_id_t, struct iovec *vec, size_t nr) = __write_to_log_init;

/*
 * This is used by the C++ code to decide if it should write logs through
 * the C code.  Basically, if /dev/socket/logd is available, we're running in
 * the simulator rather than a desktop tool and want to use the device.
 */
static enum {
    kLogUninitialized, kLogNotAvailable, kLogAvailable
} g_log_status = kLogUninitialized;

static int check_log_uid_permissions()
{
#if defined(__BIONIC__)
    uid_t uid = __android_log_uid();

    /* Matches clientHasLogCredentials() in logd */
    if ((uid != AID_SYSTEM) && (uid != AID_ROOT) && (uid != AID_LOG)) {
        uid = geteuid();
        if ((uid != AID_SYSTEM) && (uid != AID_ROOT) && (uid != AID_LOG)) {
            gid_t gid = getgid();
            if ((gid != AID_SYSTEM) &&
                    (gid != AID_ROOT) &&
                    (gid != AID_LOG)) {
                gid = getegid();
                if ((gid != AID_SYSTEM) &&
                        (gid != AID_ROOT) &&
                        (gid != AID_LOG)) {
                    int num_groups;
                    gid_t *groups;

                    num_groups = getgroups(0, NULL);
                    if (num_groups <= 0) {
                        return -EPERM;
                    }
                    groups = calloc(num_groups, sizeof(gid_t));
                    if (!groups) {
                        return -ENOMEM;
                    }
                    num_groups = getgroups(num_groups, groups);
                    while (num_groups > 0) {
                        if (groups[num_groups - 1] == AID_LOG) {
                            break;
                        }
                        --num_groups;
                    }
                    free(groups);
                    if (num_groups <= 0) {
                        return -EPERM;
                    }
                }
            }
        }
    }
#endif
    return 0;
}

static void __android_log_cache_available(
        struct android_log_transport_write *node)
{
    size_t i;

    if (node->logMask) {
        return;
    }

    for (i = LOG_ID_MIN; i < LOG_ID_MAX; ++i) {
        if (node->write &&
                (i != LOG_ID_KERNEL) &&
                ((i != LOG_ID_SECURITY) ||
                    (check_log_uid_permissions() == 0)) &&
                (!node->available || ((*node->available)(i) >= 0))) {
            node->logMask |= 1 << i;
        }
    }
}

LIBLOG_ABI_PUBLIC int __android_log_dev_available()
{
    struct android_log_transport_write *node;

    if (list_empty(&__android_log_transport_write)) {
        return kLogUninitialized;
    }

    write_transport_for_each(node, &__android_log_transport_write) {
        __android_log_cache_available(node);
        if (node->logMask) {
            return kLogAvailable;
        }
    }
    return kLogNotAvailable;
}

/* log_init_lock assumed */
static int __write_to_log_initialize()
{
    struct android_log_transport_write *transport;
    struct listnode *n;
    int i = 0, ret = 0;

    __android_log_config_write();
    write_transport_for_each_safe(transport, n, &__android_log_transport_write) {
        __android_log_cache_available(transport);
        if (!transport->logMask) {
            list_remove(&transport->node);
            continue;
        }
        if (!transport->open || ((*transport->open)() < 0)) {
            if (transport->close) {
                (*transport->close)();
            }
            list_remove(&transport->node);
            continue;
        }
        ++ret;
    }
    write_transport_for_each_safe(transport, n, &__android_log_persist_write) {
        __android_log_cache_available(transport);
        if (!transport->logMask) {
            list_remove(&transport->node);
            continue;
        }
        if (!transport->open || ((*transport->open)() < 0)) {
            if (transport->close) {
                (*transport->close)();
            }
            list_remove(&transport->node);
            continue;
        }
        ++i;
    }
    if (!ret && !i) {
        return -ENODEV;
    }

    return ret;
}

/*
 * Extract a 4-byte value from a byte stream. le32toh open coded
 */
static inline uint32_t get4LE(const uint8_t* src)
{
    return src[0] | (src[1] << 8) | (src[2] << 16) | (src[3] << 24);
}

static int __write_to_log_daemon(log_id_t log_id, struct iovec *vec, size_t nr)
{
    struct android_log_transport_write *node;
    int ret;
    struct timespec ts;
    size_t len, i;

    for (len = i = 0; i < nr; ++i) {
        len += vec[i].iov_len;
    }
    if (!len) {
        return -EINVAL;
    }

#if defined(__BIONIC__)
    if (log_id == LOG_ID_SECURITY) {
        if (vec[0].iov_len < 4) {
            return -EINVAL;
        }

        ret = check_log_uid_permissions();
        if (ret < 0) {
            return ret;
        }
        if (!__android_log_security()) {
            /* If only we could reset downstream logd counter */
            return -EPERM;
        }
    } else if (log_id == LOG_ID_EVENTS) {
        static atomic_uintptr_t map;
        const char *tag;
        EventTagMap *m, *f;

        if (vec[0].iov_len < 4) {
            return -EINVAL;
        }

        tag = NULL;
        f = NULL;
        m = (EventTagMap *)atomic_load(&map);

        if (!m) {
            ret = __android_log_trylock();
            m = (EventTagMap *)atomic_load(&map); /* trylock flush cache */
            if (!m) {
                m = android_openEventTagMap(EVENT_TAG_MAP_FILE);
                if (ret) { /* trylock failed, use local copy, mark for close */
                    f = m;
                } else {
                    if (!m) { /* One chance to open map file */
                        m = (EventTagMap *)(uintptr_t)-1LL;
                    }
                    atomic_store(&map, (uintptr_t)m);
                }
            }
            if (!ret) { /* trylock succeeded, unlock */
                __android_log_unlock();
            }
        }
        if (m && (m != (EventTagMap *)(uintptr_t)-1LL)) {
            tag = android_lookupEventTag(m, get4LE(vec[0].iov_base));
        }
        ret = __android_log_is_loggable(ANDROID_LOG_INFO,
                                        tag,
                                        ANDROID_LOG_VERBOSE);
        if (f) { /* local copy marked for close */
            android_closeEventTagMap(f);
        }
        if (!ret) {
            return -EPERM;
        }
    } else {
        /* Validate the incoming tag, tag content can not split across iovec */
        char prio = ANDROID_LOG_VERBOSE;
        const char *tag = vec[0].iov_base;
        size_t len = vec[0].iov_len;
        if (!tag) {
            len = 0;
        }
        if (len > 0) {
            prio = *tag;
            if (len > 1) {
                --len;
                ++tag;
            } else {
                len = vec[1].iov_len;
                tag = ((const char *)vec[1].iov_base);
                if (!tag) {
                    len = 0;
                }
            }
        }
        /* tag must be nul terminated */
        if (strnlen(tag, len) >= len) {
            tag = NULL;
        }

        if (!__android_log_is_loggable(prio, tag, ANDROID_LOG_VERBOSE)) {
            return -EPERM;
        }
    }

    clock_gettime(android_log_clockid(), &ts);
#else
    /* simulate clock_gettime(CLOCK_REALTIME, &ts); */
    {
        struct timeval tv;
        gettimeofday(&tv, NULL);
        ts.tv_sec = tv.tv_sec;
        ts.tv_nsec = tv.tv_usec * 1000;
    }
#endif

    ret = 0;
    i = 1 << log_id;
    write_transport_for_each(node, &__android_log_transport_write) {
        if (node->logMask & i) {
            ssize_t retval;
            retval = (*node->write)(log_id, &ts, vec, nr);
            if (ret >= 0) {
                ret = retval;
            }
        }
    }

    write_transport_for_each(node, &__android_log_persist_write) {
        if (node->logMask & i) {
            (void)(*node->write)(log_id, &ts, vec, nr);
        }
    }

    return ret;
}

static int __write_to_log_init(log_id_t log_id, struct iovec *vec, size_t nr)
{
    __android_log_lock();

    if (write_to_log == __write_to_log_init) {
        int ret;

        ret = __write_to_log_initialize();
        if (ret < 0) {
            __android_log_unlock();
            if (!list_empty(&__android_log_persist_write)) {
                __write_to_log_daemon(log_id, vec, nr);
            }
            return ret;
        }

        write_to_log = __write_to_log_daemon;
    }

    __android_log_unlock();

    return write_to_log(log_id, vec, nr);
}

LIBLOG_ABI_PUBLIC int __android_log_write(int prio, const char *tag,
                                          const char *msg)
{
    return __android_log_buf_write(LOG_ID_MAIN, prio, tag, msg);
}

LIBLOG_ABI_PUBLIC int __android_log_buf_write(int bufID, int prio,
                                              const char *tag, const char *msg)
{
    struct iovec vec[3];
    char tmp_tag[32];

    if (!tag)
        tag = "";

    /* XXX: This needs to go! */
    if ((bufID != LOG_ID_RADIO) &&
         (!strcmp(tag, "HTC_RIL") ||
        !strncmp(tag, "RIL", 3) || /* Any log tag with "RIL" as the prefix */
        !strncmp(tag, "IMS", 3) || /* Any log tag with "IMS" as the prefix */
        !strcmp(tag, "AT") ||
        !strcmp(tag, "GSM") ||
        !strcmp(tag, "STK") ||
        !strcmp(tag, "CDMA") ||
        !strcmp(tag, "PHONE") ||
        !strcmp(tag, "SMS"))) {
            bufID = LOG_ID_RADIO;
            /* Inform third party apps/ril/radio.. to use Rlog or RLOG */
            snprintf(tmp_tag, sizeof(tmp_tag), "use-Rlog/RLOG-%s", tag);
            tag = tmp_tag;
    }

#if __BIONIC__
    if (prio == ANDROID_LOG_FATAL) {
        android_set_abort_message(msg);
    }
#endif

    vec[0].iov_base = (unsigned char *)&prio;
    vec[0].iov_len  = 1;
    vec[1].iov_base = (void *)tag;
    vec[1].iov_len  = strlen(tag) + 1;
    vec[2].iov_base = (void *)msg;
    vec[2].iov_len  = strlen(msg) + 1;

    return write_to_log(bufID, vec, 3);
}

LIBLOG_ABI_PUBLIC int __android_log_vprint(int prio, const char *tag,
                                           const char *fmt, va_list ap)
{
    char buf[LOG_BUF_SIZE];

    vsnprintf(buf, LOG_BUF_SIZE, fmt, ap);

    return __android_log_write(prio, tag, buf);
}

LIBLOG_ABI_PUBLIC int __android_log_print(int prio, const char *tag,
                                          const char *fmt, ...)
{
    va_list ap;
    char buf[LOG_BUF_SIZE];

    va_start(ap, fmt);
    vsnprintf(buf, LOG_BUF_SIZE, fmt, ap);
    va_end(ap);

    return __android_log_write(prio, tag, buf);
}

LIBLOG_ABI_PUBLIC int __android_log_buf_print(int bufID, int prio,
                                              const char *tag,
                                              const char *fmt, ...)
{
    va_list ap;
    char buf[LOG_BUF_SIZE];

    va_start(ap, fmt);
    vsnprintf(buf, LOG_BUF_SIZE, fmt, ap);
    va_end(ap);

    return __android_log_buf_write(bufID, prio, tag, buf);
}

LIBLOG_ABI_PUBLIC void __android_log_assert(const char *cond, const char *tag,
                                            const char *fmt, ...)
{
    char buf[LOG_BUF_SIZE];

    if (fmt) {
        va_list ap;
        va_start(ap, fmt);
        vsnprintf(buf, LOG_BUF_SIZE, fmt, ap);
        va_end(ap);
    } else {
        /* Msg not provided, log condition.  N.B. Do not use cond directly as
         * format string as it could contain spurious '%' syntax (e.g.
         * "%d" in "blocks%devs == 0").
         */
        if (cond)
            snprintf(buf, LOG_BUF_SIZE, "Assertion failed: %s", cond);
        else
            strcpy(buf, "Unspecified assertion failed");
    }

    __android_log_write(ANDROID_LOG_FATAL, tag, buf);
    abort(); /* abort so we have a chance to debug the situation */
    /* NOTREACHED */
}

LIBLOG_ABI_PUBLIC int __android_log_bwrite(int32_t tag,
                                           const void *payload, size_t len)
{
    struct iovec vec[2];

    vec[0].iov_base = &tag;
    vec[0].iov_len = sizeof(tag);
    vec[1].iov_base = (void*)payload;
    vec[1].iov_len = len;

    return write_to_log(LOG_ID_EVENTS, vec, 2);
}

LIBLOG_ABI_PUBLIC int __android_log_security_bwrite(int32_t tag,
                                                    const void *payload,
                                                    size_t len)
{
    struct iovec vec[2];

    vec[0].iov_base = &tag;
    vec[0].iov_len = sizeof(tag);
    vec[1].iov_base = (void*)payload;
    vec[1].iov_len = len;

    return write_to_log(LOG_ID_SECURITY, vec, 2);
}

/*
 * Like __android_log_bwrite, but takes the type as well.  Doesn't work
 * for the general case where we're generating lists of stuff, but very
 * handy if we just want to dump an integer into the log.
 */
LIBLOG_ABI_PUBLIC int __android_log_btwrite(int32_t tag, char type,
                                            const void *payload, size_t len)
{
    struct iovec vec[3];

    vec[0].iov_base = &tag;
    vec[0].iov_len = sizeof(tag);
    vec[1].iov_base = &type;
    vec[1].iov_len = sizeof(type);
    vec[2].iov_base = (void*)payload;
    vec[2].iov_len = len;

    return write_to_log(LOG_ID_EVENTS, vec, 3);
}

/*
 * Like __android_log_bwrite, but used for writing strings to the
 * event log.
 */
LIBLOG_ABI_PUBLIC int __android_log_bswrite(int32_t tag, const char *payload)
{
    struct iovec vec[4];
    char type = EVENT_TYPE_STRING;
    uint32_t len = strlen(payload);

    vec[0].iov_base = &tag;
    vec[0].iov_len = sizeof(tag);
    vec[1].iov_base = &type;
    vec[1].iov_len = sizeof(type);
    vec[2].iov_base = &len;
    vec[2].iov_len = sizeof(len);
    vec[3].iov_base = (void*)payload;
    vec[3].iov_len = len;

    return write_to_log(LOG_ID_EVENTS, vec, 4);
}

/*
 * Like __android_log_security_bwrite, but used for writing strings to the
 * security log.
 */
LIBLOG_ABI_PUBLIC int __android_log_security_bswrite(int32_t tag,
                                                     const char *payload)
{
    struct iovec vec[4];
    char type = EVENT_TYPE_STRING;
    uint32_t len = strlen(payload);

    vec[0].iov_base = &tag;
    vec[0].iov_len = sizeof(tag);
    vec[1].iov_base = &type;
    vec[1].iov_len = sizeof(type);
    vec[2].iov_base = &len;
    vec[2].iov_len = sizeof(len);
    vec[3].iov_base = (void*)payload;
    vec[3].iov_len = len;

    return write_to_log(LOG_ID_SECURITY, vec, 4);
}
