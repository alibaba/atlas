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
#include <fcntl.h>
#include <unistd.h>

#include <log/log.h>

#include "config_write.h"
#include "fake_log_device.h"
#include "log_portability.h"
#include "logger.h"

static int fakeOpen();
static void fakeClose();
static int fakeWrite(log_id_t log_id, struct timespec *ts,
                     struct iovec *vec, size_t nr);

static int logFds[(int)LOG_ID_MAX] = { -1, -1, -1, -1, -1, -1 };

LIBLOG_HIDDEN struct android_log_transport_write fakeLoggerWrite = {
    .node = { &fakeLoggerWrite.node, &fakeLoggerWrite.node },
    .context.private = &logFds,
    .name = "fake",
    .available = NULL,
    .open = fakeOpen,
    .close = fakeClose,
    .write = fakeWrite,
};

static int fakeOpen() {
    int i;

    for (i = 0; i < LOG_ID_MAX; i++) {
        char buf[sizeof("/dev/log_security")];
        snprintf(buf, sizeof(buf), "/dev/log_%s", android_log_id_to_name(i));
        logFds[i] = fakeLogOpen(buf, O_WRONLY);
    }
    return 0;
}

static void fakeClose() {
    int i;

    for (i = 0; i < LOG_ID_MAX; i++) {
        fakeLogClose(logFds[i]);
        logFds[i] = -1;
    }
}

static int fakeWrite(log_id_t log_id, struct timespec *ts __unused,
                      struct iovec *vec, size_t nr)
{
    ssize_t ret;
    int logFd;

    if (/*(int)log_id >= 0 &&*/ (int)log_id >= (int)LOG_ID_MAX) {
        return -EBADF;
    }

    logFd = logFds[(int)log_id];
    ret = TEMP_FAILURE_RETRY(fakeLogWritev(logFd, vec, nr));
    if (ret < 0) {
        ret = -errno;
    }

    return ret;
}
