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

/*
 * pmsg write handler
 */

#include <errno.h>
#include <fcntl.h>
#include <stdlib.h>
#include <string.h>
#include <sys/types.h>
#include <time.h>

#include <log/log.h>
#include <log/logger.h>

#include <private/android_filesystem_config.h>
#include <private/android_logger.h>

#include "config_write.h"
#include "log_portability.h"
#include "logger.h"

static int pmsgOpen();
static void pmsgClose();
static int pmsgAvailable(log_id_t logId);
static int pmsgWrite(log_id_t logId, struct timespec *ts,
                      struct iovec *vec, size_t nr);

LIBLOG_HIDDEN struct android_log_transport_write pmsgLoggerWrite = {
    .node = { &pmsgLoggerWrite.node, &pmsgLoggerWrite.node },
    .context.fd = -1,
    .name = "pmsg",
    .available = pmsgAvailable,
    .open = pmsgOpen,
    .close = pmsgClose,
    .write = pmsgWrite,
};

static int pmsgOpen()
{
    if (pmsgLoggerWrite.context.fd < 0) {
        pmsgLoggerWrite.context.fd = TEMP_FAILURE_RETRY(open("/dev/pmsg0", O_WRONLY | O_CLOEXEC));
    }

    return pmsgLoggerWrite.context.fd;
}

static void pmsgClose()
{
    if (pmsgLoggerWrite.context.fd >= 0) {
        close(pmsgLoggerWrite.context.fd);
        pmsgLoggerWrite.context.fd = -1;
    }
}

static int pmsgAvailable(log_id_t logId)
{
    if (logId > LOG_ID_SECURITY) {
        return -EINVAL;
    }
    if ((logId != LOG_ID_SECURITY) &&
            (logId != LOG_ID_EVENTS) &&
            !__android_log_is_debuggable()) {
        return -EINVAL;
    }
    if (pmsgLoggerWrite.context.fd < 0) {
        if (access("/dev/pmsg0", W_OK) == 0) {
            return 0;
        }
        return -EBADF;
    }
    return 1;
}

/*
 * Extract a 4-byte value from a byte stream.
 */
static inline uint32_t get4LE(const uint8_t* src)
{
    return src[0] | (src[1] << 8) | (src[2] << 16) | (src[3] << 24);
}

static int pmsgWrite(log_id_t logId, struct timespec *ts,
                      struct iovec *vec, size_t nr)
{
    static const unsigned headerLength = 2;
    struct iovec newVec[nr + headerLength];
    android_log_header_t header;
    android_pmsg_log_header_t pmsgHeader;
    size_t i, payloadSize;
    ssize_t ret;

    if ((logId == LOG_ID_EVENTS) && !__android_log_is_debuggable()) {
        if (vec[0].iov_len < 4) {
            return -EINVAL;
        }

        if (SNET_EVENT_LOG_TAG != get4LE(vec[0].iov_base)) {
            return -EPERM;
        }
    }

    if (pmsgLoggerWrite.context.fd < 0) {
        return -EBADF;
    }

    /*
     *  struct {
     *      // what we provide to pstore
     *      android_pmsg_log_header_t pmsgHeader;
     *      // what we provide to file
     *      android_log_header_t header;
     *      // caller provides
     *      union {
     *          struct {
     *              char     prio;
     *              char     payload[];
     *          } string;
     *          struct {
     *              uint32_t tag
     *              char     payload[];
     *          } binary;
     *      };
     *  };
     */

    pmsgHeader.magic = LOGGER_MAGIC;
    pmsgHeader.len = sizeof(pmsgHeader) + sizeof(header);
    pmsgHeader.uid = __android_log_uid();
    pmsgHeader.pid = __android_log_pid();

    header.id = logId;
    header.tid = gettid();
    header.realtime.tv_sec = ts->tv_sec;
    header.realtime.tv_nsec = ts->tv_nsec;

    newVec[0].iov_base   = (unsigned char *)&pmsgHeader;
    newVec[0].iov_len    = sizeof(pmsgHeader);
    newVec[1].iov_base   = (unsigned char *)&header;
    newVec[1].iov_len    = sizeof(header);

    for (payloadSize = 0, i = headerLength; i < nr + headerLength; i++) {
        newVec[i].iov_base = vec[i - headerLength].iov_base;
        payloadSize += newVec[i].iov_len = vec[i - headerLength].iov_len;

        if (payloadSize > LOGGER_ENTRY_MAX_PAYLOAD) {
            newVec[i].iov_len -= payloadSize - LOGGER_ENTRY_MAX_PAYLOAD;
            if (newVec[i].iov_len) {
                ++i;
            }
            payloadSize = LOGGER_ENTRY_MAX_PAYLOAD;
            break;
        }
    }
    pmsgHeader.len += payloadSize;

    ret = TEMP_FAILURE_RETRY(writev(pmsgLoggerWrite.context.fd, newVec, i));
    if (ret < 0) {
        ret = errno ? -errno : -ENOTCONN;
    }

    if (ret > (ssize_t)(sizeof(header) + sizeof(pmsgHeader))) {
        ret -= sizeof(header) - sizeof(pmsgHeader);
    }

    return ret;
}

/*
 * Virtual pmsg filesystem
 *
 * Payload will comprise the string "<basedir>:<basefile>\0<content>" to a
 * maximum of LOGGER_ENTRY_MAX_PAYLOAD, but scaled to the last newline in the
 * file.
 *
 * Will hijack the header.realtime.tv_nsec field for a sequence number in usec.
 */

static inline const char *strnrchr(const char *buf, size_t len, char c) {
    const char *cp = buf + len;
    while ((--cp > buf) && (*cp != c));
    if (cp <= buf) {
        return buf + len;
    }
    return cp;
}

/* Write a buffer as filename references (tag = <basedir>:<basename>) */
LIBLOG_ABI_PRIVATE ssize_t __android_log_pmsg_file_write(
        log_id_t logId,
        char prio,
        const char *filename,
        const char *buf, size_t len) {
    int fd;
    size_t length, packet_len;
    const char *tag;
    char *cp, *slash;
    struct timespec ts;
    struct iovec vec[3];

    /* Make sure the logId value is not a bad idea */
    if ((logId == LOG_ID_KERNEL) ||       /* Verbotten */
            (logId == LOG_ID_EVENTS) ||   /* Do not support binary content */
            (logId == LOG_ID_SECURITY) || /* Bad idea to allow */
            ((unsigned)logId >= 32)) {    /* fit within logMask on arch32 */
        return -EINVAL;
    }

    clock_gettime(android_log_clockid(), &ts);

    cp = strdup(filename);
    if (!cp) {
        return -ENOMEM;
    }

    fd = pmsgLoggerWrite.context.fd;
    if (fd < 0) {
        __android_log_lock();
        fd = pmsgOpen();
        __android_log_unlock();
        if (fd < 0) {
            return -EBADF;
        }
    }

    tag = cp;
    slash = strrchr(cp, '/');
    if (slash) {
        *slash = ':';
        slash = strrchr(cp, '/');
        if (slash) {
            tag = slash + 1;
        }
    }

    length = strlen(tag) + 1;
    packet_len = LOGGER_ENTRY_MAX_PAYLOAD - sizeof(char) - length;

    vec[0].iov_base = &prio;
    vec[0].iov_len  = sizeof(char);
    vec[1].iov_base = (unsigned char *)tag;
    vec[1].iov_len  = length;

    for (ts.tv_nsec = 0, length = len;
            length;
            ts.tv_nsec += ANDROID_LOG_PMSG_FILE_SEQUENCE) {
        ssize_t ret;
        size_t transfer;

        if ((ts.tv_nsec / ANDROID_LOG_PMSG_FILE_SEQUENCE) >=
                ANDROID_LOG_PMSG_FILE_MAX_SEQUENCE) {
            len -= length;
            break;
        }

        transfer = length;
        if (transfer > packet_len) {
            transfer = strnrchr(buf, packet_len - 1, '\n') - buf;
            if ((transfer < length) && (buf[transfer] == '\n')) {
                ++transfer;
            }
        }

        vec[2].iov_base = (unsigned char *)buf;
        vec[2].iov_len  = transfer;

        ret = pmsgWrite(logId, &ts, vec, sizeof(vec) / sizeof(vec[0]));

        if (ret <= 0) {
            free(cp);
            return ret;
        }
        length -= transfer;
        buf += transfer;
    }
    free(cp);
    return len;
}
