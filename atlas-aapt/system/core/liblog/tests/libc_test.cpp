/*
 * Copyright (C) 2014 The Android Open Source Project
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

#include <fcntl.h>
#include <sys/cdefs.h>

#include <gtest/gtest.h>

// Should be in bionic test suite, *but* we are using liblog to confirm
// end-to-end logging, so let the overly cute oedipus complex begin ...
#include "../../../../bionic/libc/bionic/libc_logging.cpp" // not Standalone
#define _ANDROID_LOG_H // Priorities redefined
#define _LIBS_LOG_LOG_H // log ids redefined
typedef unsigned char log_id_t; // log_id_t missing as a result
#define _LIBS_LOG_LOG_READ_H // log_time redefined

#include <log/log.h>
#include <log/logger.h>
#include <log/log_read.h>

TEST(libc, __libc_android_log_event_int) {
    struct logger_list *logger_list;

    pid_t pid = getpid();

    ASSERT_TRUE(NULL != (logger_list = android_logger_list_open(
        LOG_ID_EVENTS, ANDROID_LOG_RDONLY | ANDROID_LOG_NONBLOCK, 1000, pid)));

    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    int value = ts.tv_nsec;

    __libc_android_log_event_int(0, value);
    usleep(1000000);

    int count = 0;

    for (;;) {
        log_msg log_msg;
        if (android_logger_list_read(logger_list, &log_msg) <= 0) {
            break;
        }

        ASSERT_EQ(log_msg.entry.pid, pid);

        if ((log_msg.entry.len != (4 + 1 + 4))
         || ((int)log_msg.id() != LOG_ID_EVENTS)) {
            continue;
        }

        char *eventData = log_msg.msg();

        int incoming = (eventData[0] & 0xFF) |
                      ((eventData[1] & 0xFF) << 8) |
                      ((eventData[2] & 0xFF) << 16) |
                      ((eventData[3] & 0xFF) << 24);

        if (incoming != 0) {
            continue;
        }

        if (eventData[4] != EVENT_TYPE_INT) {
            continue;
        }

        incoming = (eventData[4 + 1 + 0] & 0xFF) |
                  ((eventData[4 + 1 + 1] & 0xFF) << 8) |
                  ((eventData[4 + 1 + 2] & 0xFF) << 16) |
                  ((eventData[4 + 1 + 3] & 0xFF) << 24);

        if (incoming == value) {
            ++count;
        }
    }

    EXPECT_EQ(1, count);

    android_logger_list_close(logger_list);
}

TEST(libc, __libc_fatal_no_abort) {
    struct logger_list *logger_list;

    pid_t pid = getpid();

    ASSERT_TRUE(NULL != (logger_list = android_logger_list_open(
        (log_id_t)LOG_ID_CRASH, ANDROID_LOG_RDONLY | ANDROID_LOG_NONBLOCK, 1000, pid)));

    char b[80];
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);

    __libc_fatal_no_abort("%u.%09u", (unsigned)ts.tv_sec, (unsigned)ts.tv_nsec);
    snprintf(b, sizeof(b),"%u.%09u", (unsigned)ts.tv_sec, (unsigned)ts.tv_nsec);
    usleep(1000000);

    int count = 0;

    for (;;) {
        log_msg log_msg;
        if (android_logger_list_read(logger_list, &log_msg) <= 0) {
            break;
        }

        ASSERT_EQ(log_msg.entry.pid, pid);

        if ((int)log_msg.id() != LOG_ID_CRASH) {
            continue;
        }

        char *data = log_msg.msg();

        if ((*data == ANDROID_LOG_FATAL)
                && !strcmp(data + 1, "libc")
                && !strcmp(data + 1 + strlen(data + 1) + 1, b)) {
            ++count;
        }
    }

    EXPECT_EQ(1, count);

    android_logger_list_close(logger_list);
}

TEST(libc, __pstore_append) {
    FILE *fp;
    ASSERT_TRUE(NULL != (fp = fopen("/dev/pmsg0", "a")));
    static const char message[] = "libc.__pstore_append\n";
    ASSERT_EQ((size_t)1, fwrite(message, sizeof(message), 1, fp));
    ASSERT_EQ(0, fclose(fp));
    fprintf(stderr, "Reboot, ensure string libc.__pstore_append is in /sys/fs/pstore/pmsg-ramoops-0\n");
}
