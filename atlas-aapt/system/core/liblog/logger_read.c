/*
** Copyright 2013-2014, The Android Open Source Project
**
** Licensed under the Apache License, Version 2.0 (the "License");
** you may not use this file except in compliance with the License.
** You may obtain a copy of the License at
**
**     http://www.apache.org/licenses/LICENSE-2.0
**
** Unless required by applicable law or agreed to in writing, software
** distributed under the License is distributed on an "AS IS" BASIS,
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
** See the License for the specific language governing permissions and
** limitations under the License.
*/

#include <errno.h>
#include <fcntl.h>
#include <pthread.h>
#include <sched.h>
#include <stddef.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#include <cutils/list.h>
#include <log/log.h>
#include <log/logger.h>
#include <private/android_filesystem_config.h>

#include "config_read.h"
#include "log_portability.h"
#include "logger.h"

/* android_logger_alloc unimplemented, no use case */
/* android_logger_free not exported */
static void android_logger_free(struct logger *logger)
{
    struct android_log_logger *logger_internal =
            (struct android_log_logger *)logger;

    if (!logger_internal) {
        return;
    }

    list_remove(&logger_internal->node);

    free(logger_internal);
}

/* android_logger_alloc unimplemented, no use case */

/* method for getting the associated sublog id */
LIBLOG_ABI_PUBLIC log_id_t android_logger_get_id(struct logger *logger)
{
    return ((struct android_log_logger *)logger)->logId;
}

static int init_transport_context(struct android_log_logger_list *logger_list)
{
    struct android_log_transport_read *transport;
    struct listnode *node;

    if (!logger_list) {
        return -EINVAL;
    }

    if (list_empty(&logger_list->logger)) {
        return -EINVAL;
    }

    if (!list_empty(&logger_list->transport)) {
        return 0;
    }

    __android_log_lock();
    /* mini __write_to_log_initialize() to populate transports */
    if (list_empty(&__android_log_transport_read) &&
            list_empty(&__android_log_persist_read)) {
        __android_log_config_read();
    }
    __android_log_unlock();

    node = (logger_list->mode & ANDROID_LOG_PSTORE) ?
            &__android_log_persist_read : &__android_log_transport_read;

    read_transport_for_each(transport, node) {
        struct android_log_transport_context *transp;
        struct android_log_logger *logger;
        unsigned logMask = 0;

        logger_for_each(logger, logger_list) {
            log_id_t logId = logger->logId;

            if ((logId == LOG_ID_SECURITY) &&
                    (__android_log_uid() != AID_SYSTEM)) {
                continue;
            }
            if (transport->read &&
                    (!transport->available ||
                        (transport->available(logId) >= 0))) {
                logMask |= 1 << logId;
            }
        }
        if (!logMask) {
            continue;
        }
        transp = calloc(1, sizeof(*transp));
        if (!transp) {
            return -ENOMEM;
        }
        transp->parent = logger_list;
        transp->transport = transport;
        transp->logMask = logMask;
        transp->ret = 1;
        list_add_tail(&logger_list->transport, &transp->node);
    }
    if (list_empty(&logger_list->transport)) {
        return -ENODEV;
    }
    return 0;
}

#define LOGGER_FUNCTION(logger, def, func, args...)                           \
    ssize_t ret = -EINVAL;                                                    \
    struct android_log_transport_context *transp;                             \
    struct android_log_logger *logger_internal =                              \
            (struct android_log_logger *)logger;                              \
                                                                              \
    if (!logger_internal) {                                                   \
        return ret;                                                           \
    }                                                                         \
    ret = init_transport_context(logger_internal->parent);                    \
    if (ret < 0) {                                                            \
        return ret;                                                           \
    }                                                                         \
                                                                              \
    ret = (def);                                                              \
    transport_context_for_each(transp, logger_internal->parent) {             \
        if ((transp->logMask & (1 << logger_internal->logId)) &&              \
                transp->transport && transp->transport->func) {               \
            ssize_t retval = (transp->transport->func)(logger_internal,       \
                                                       transp, ## args);      \
            if ((ret >= 0) || (ret == (def))) {                               \
                ret = retval;                                                 \
            }                                                                 \
        }                                                                     \
    }                                                                         \
    return ret

LIBLOG_ABI_PUBLIC int android_logger_clear(struct logger *logger)
{
    LOGGER_FUNCTION(logger, -ENODEV, clear);
}

/* returns the total size of the log's ring buffer */
LIBLOG_ABI_PUBLIC long android_logger_get_log_size(struct logger *logger)
{
    LOGGER_FUNCTION(logger, -ENODEV, getSize);
}

LIBLOG_ABI_PUBLIC int android_logger_set_log_size(struct logger *logger,
                                                  unsigned long size)
{
    LOGGER_FUNCTION(logger, -ENODEV, setSize, size);
}

/*
 * returns the readable size of the log's ring buffer (that is, amount of the
 * log consumed)
 */
LIBLOG_ABI_PUBLIC long android_logger_get_log_readable_size(
        struct logger *logger)
{
    LOGGER_FUNCTION(logger, -ENODEV, getReadableSize);
}

/*
 * returns the logger version
 */
LIBLOG_ABI_PUBLIC int android_logger_get_log_version(struct logger *logger)
{
    LOGGER_FUNCTION(logger, 4, version);
}

#define LOGGER_LIST_FUNCTION(logger_list, def, func, args...)                 \
    struct android_log_transport_context *transp;                             \
    struct android_log_logger_list *logger_list_internal =                    \
            (struct android_log_logger_list *)logger_list;                    \
                                                                              \
    ssize_t ret = init_transport_context(logger_list_internal);               \
    if (ret < 0) {                                                            \
        return ret;                                                           \
    }                                                                         \
                                                                              \
    ret = (def);                                                              \
    transport_context_for_each(transp, logger_list_internal) {                \
        if (transp->transport && (transp->transport->func)) {                 \
            ssize_t retval = (transp->transport->func)(logger_list_internal,  \
                                                       transp, ## args);      \
            if ((ret >= 0) || (ret == (def))) {                               \
                ret = retval;                                                 \
            }                                                                 \
        }                                                                     \
    }                                                                         \
    return ret

/*
 * returns statistics
 */
LIBLOG_ABI_PUBLIC ssize_t android_logger_get_statistics(
        struct logger_list *logger_list,
        char *buf, size_t len)
{
    LOGGER_LIST_FUNCTION(logger_list, -ENODEV, getStats, buf, len);
}

LIBLOG_ABI_PUBLIC ssize_t android_logger_get_prune_list(
        struct logger_list *logger_list,
        char *buf, size_t len)
{
    LOGGER_LIST_FUNCTION(logger_list, -ENODEV, getPrune, buf, len);
}

LIBLOG_ABI_PUBLIC int android_logger_set_prune_list(
        struct logger_list *logger_list,
        char *buf, size_t len)
{
    LOGGER_LIST_FUNCTION(logger_list, -ENODEV, setPrune, buf, len);
}

LIBLOG_ABI_PUBLIC struct logger_list *android_logger_list_alloc(
        int mode,
        unsigned int tail,
        pid_t pid)
{
    struct android_log_logger_list *logger_list;

    logger_list = calloc(1, sizeof(*logger_list));
    if (!logger_list) {
        return NULL;
    }

    list_init(&logger_list->logger);
    list_init(&logger_list->transport);
    logger_list->mode = mode;
    logger_list->tail = tail;
    logger_list->pid = pid;

    return (struct logger_list *)logger_list;
}

LIBLOG_ABI_PUBLIC struct logger_list *android_logger_list_alloc_time(
        int mode,
        log_time start,
        pid_t pid)
{
    struct android_log_logger_list *logger_list;

    logger_list = calloc(1, sizeof(*logger_list));
    if (!logger_list) {
        return NULL;
    }

    list_init(&logger_list->logger);
    list_init(&logger_list->transport);
    logger_list->mode = mode;
    logger_list->start = start;
    logger_list->pid = pid;

    return (struct logger_list *)logger_list;
}

/* android_logger_list_register unimplemented, no use case */
/* android_logger_list_unregister unimplemented, no use case */

/* Open the named log and add it to the logger list */
LIBLOG_ABI_PUBLIC struct logger *android_logger_open(
        struct logger_list *logger_list,
        log_id_t logId)
{
    struct android_log_logger_list *logger_list_internal =
            (struct android_log_logger_list *)logger_list;
    struct android_log_logger *logger;

    if (!logger_list_internal || (logId >= LOG_ID_MAX)) {
        goto err;
    }

    logger_for_each(logger, logger_list_internal) {
        if (logger->logId == logId) {
            goto ok;
        }
    }

    logger = calloc(1, sizeof(*logger));
    if (!logger) {
        goto err;
    }

    logger->logId = logId;
    list_add_tail(&logger_list_internal->logger, &logger->node);
    logger->parent = logger_list_internal;

    /* Reset known transports to re-evaluate, we just added one */
    while (!list_empty(&logger_list_internal->transport)) {
        struct listnode *node = list_head(&logger_list_internal->transport);
        struct android_log_transport_context *transp =
                node_to_item(node, struct android_log_transport_context, node);

        list_remove(&transp->node);
        free(transp);
    }
    goto ok;

err:
    logger = NULL;
ok:
    return (struct logger *)logger;
}

/* Open the single named log and make it part of a new logger list */
LIBLOG_ABI_PUBLIC struct logger_list *android_logger_list_open(
        log_id_t logId,
        int mode,
        unsigned int tail,
        pid_t pid)
{
    struct logger_list *logger_list =
            android_logger_list_alloc(mode, tail, pid);

    if (!logger_list) {
        return NULL;
    }

    if (!android_logger_open(logger_list, logId)) {
        android_logger_list_free(logger_list);
        return NULL;
    }

    return logger_list;
}

/* Read from the selected logs */
LIBLOG_ABI_PUBLIC int android_logger_list_read(struct logger_list *logger_list,
                                               struct log_msg *log_msg)
{
    struct android_log_transport_context *transp;
    struct android_log_logger_list *logger_list_internal =
            (struct android_log_logger_list *)logger_list;

    int ret = init_transport_context(logger_list_internal);
    if (ret < 0) {
        return ret;
    }

    /* at least one transport */
    transp = node_to_item(logger_list_internal->transport.next,
                          struct android_log_transport_context, node);

    /* more than one transport? */
    if (transp->node.next != &logger_list_internal->transport) {
        /* Poll and merge sort the entries if from multiple transports */
        struct android_log_transport_context *oldest = NULL;
        int ret;
        int polled = 0;
        do {
            if (polled) {
                sched_yield();
            }
            ret = -1000;
            polled = 0;
            do {
                int retval = transp->ret;
                if ((retval > 0) && !transp->logMsg.entry.len) {
                    if (!transp->transport->read) {
                        retval = transp->ret = 0;
                    } else if ((logger_list_internal->mode &
                                ANDROID_LOG_NONBLOCK) ||
                            !transp->transport->poll) {
                        retval = transp->ret = (*transp->transport->read)(
                                logger_list_internal,
                                transp,
                                &transp->logMsg);
                    } else {
                        int pollval = (*transp->transport->poll)(
                                logger_list_internal, transp);
                        if (pollval <= 0) {
                            sched_yield();
                            pollval = (*transp->transport->poll)(
                                    logger_list_internal, transp);
                        }
                        polled = 1;
                        if (pollval < 0) {
                            if ((pollval == -EINTR) || (pollval == -EAGAIN)) {
                                return -EAGAIN;
                            }
                            retval = transp->ret = pollval;
                        } else if (pollval > 0) {
                            retval = transp->ret = (*transp->transport->read)(
                                    logger_list_internal,
                                    transp,
                                    &transp->logMsg);
                        }
                    }
                }
                if (ret < retval) {
                    ret = retval;
                }
                if ((transp->ret > 0) && transp->logMsg.entry.len &&
                        (!oldest ||
                            (oldest->logMsg.entry.sec >
                                transp->logMsg.entry.sec) ||
                            ((oldest->logMsg.entry.sec ==
                                    transp->logMsg.entry.sec) &&
                                (oldest->logMsg.entry.nsec >
                                    transp->logMsg.entry.nsec)))) {
                    oldest = transp;
                }
                transp = node_to_item(transp->node.next,
                                      struct android_log_transport_context,
                                      node);
            } while (transp != node_to_item(
                    &logger_list_internal->transport,
                    struct android_log_transport_context,
                    node));
            if (!oldest &&
                    (logger_list_internal->mode & ANDROID_LOG_NONBLOCK)) {
                return (ret < 0) ? ret : -EAGAIN;
            }
            transp = node_to_item(logger_list_internal->transport.next,
                                  struct android_log_transport_context, node);
        } while (!oldest && (ret > 0));
        if (!oldest) {
            return ret;
        }
        memcpy(log_msg, &oldest->logMsg, oldest->logMsg.entry.len +
                    (oldest->logMsg.entry.hdr_size ?
                        oldest->logMsg.entry.hdr_size :
                        sizeof(struct logger_entry)));
        oldest->logMsg.entry.len = 0; /* Mark it as copied */
        return oldest->ret;
    }

    /* if only one, no need to copy into transport_context and merge-sort */
    return (transp->transport->read)(logger_list_internal, transp, log_msg);
}

/* Close all the logs */
LIBLOG_ABI_PUBLIC void android_logger_list_free(struct logger_list *logger_list)
{
    struct android_log_logger_list *logger_list_internal =
            (struct android_log_logger_list *)logger_list;

    if (logger_list_internal == NULL) {
        return;
    }

    while (!list_empty(&logger_list_internal->transport)) {
        struct listnode *node = list_head(&logger_list_internal->transport);
        struct android_log_transport_context *transp =
                node_to_item(node, struct android_log_transport_context, node);

        if (transp->transport && transp->transport->close) {
            (*transp->transport->close)(logger_list_internal, transp);
        }
        list_remove(&transp->node);
        free(transp);
    }

    while (!list_empty(&logger_list_internal->logger)) {
        struct listnode *node = list_head(&logger_list_internal->logger);
        struct android_log_logger *logger =
                node_to_item(node, struct android_log_logger, node);
        android_logger_free((struct logger *)logger);
    }

    free(logger_list_internal);
}
