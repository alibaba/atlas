/*
 * Copyright (C) 2008 The Android Open Source Project
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
 * System clock functions.
 */

#if defined(__ANDROID__)
#include <linux/ioctl.h>
#include <linux/rtc.h>
#include <utils/Atomic.h>
#include <linux/android_alarm.h>
#endif

#include <sys/time.h>
#include <limits.h>
#include <fcntl.h>
#include <string.h>

#include <utils/SystemClock.h>
#include <utils/Timers.h>

#define LOG_TAG "SystemClock"
#include <utils/Log.h>

namespace android {

/*
 * native public static long uptimeMillis();
 */
int64_t uptimeMillis()
{
    int64_t when = systemTime(SYSTEM_TIME_MONOTONIC);
    return (int64_t) nanoseconds_to_milliseconds(when);
}

/*
 * native public static long elapsedRealtime();
 */
int64_t elapsedRealtime()
{
	return nanoseconds_to_milliseconds(elapsedRealtimeNano());
}

#define METHOD_CLOCK_GETTIME    0
#define METHOD_IOCTL            1
#define METHOD_SYSTEMTIME       2

/*
 * To debug/verify the timestamps returned by the kernel, change
 * DEBUG_TIMESTAMP to 1 and call the timestamp routine from a single thread
 * in the test program. b/10899829
 */
#define DEBUG_TIMESTAMP         0

#if DEBUG_TIMESTAMP && defined(__arm__)
static inline void checkTimeStamps(int64_t timestamp,
                                   int64_t volatile *prevTimestampPtr,
                                   int volatile *prevMethodPtr,
                                   int curMethod)
{
    /*
     * Disable the check for SDK since the prebuilt toolchain doesn't contain
     * gettid, and int64_t is different on the ARM platform
     * (ie long vs long long).
     */
    int64_t prevTimestamp = *prevTimestampPtr;
    int prevMethod = *prevMethodPtr;

    if (timestamp < prevTimestamp) {
        static const char *gettime_method_names[] = {
            "clock_gettime",
            "ioctl",
            "systemTime",
        };

        ALOGW("time going backwards: prev %lld(%s) vs now %lld(%s), tid=%d",
              prevTimestamp, gettime_method_names[prevMethod],
              timestamp, gettime_method_names[curMethod],
              gettid());
    }
    // NOTE - not atomic and may generate spurious warnings if the 64-bit
    // write is interrupted or not observed as a whole.
    *prevTimestampPtr = timestamp;
    *prevMethodPtr = curMethod;
}
#else
#define checkTimeStamps(timestamp, prevTimestampPtr, prevMethodPtr, curMethod)
#endif

/*
 * native public static long elapsedRealtimeNano();
 */
int64_t elapsedRealtimeNano()
{
#if defined(__ANDROID__)
    struct timespec ts;
    int result;
    int64_t timestamp;
#if DEBUG_TIMESTAMP
    static volatile int64_t prevTimestamp;
    static volatile int prevMethod;
#endif

    static int s_fd = -1;

    if (s_fd == -1) {
        int fd = open("/dev/alarm", O_RDONLY);
        if (android_atomic_cmpxchg(-1, fd, &s_fd)) {
            close(fd);
        }
    }

    result = ioctl(s_fd,
            ANDROID_ALARM_GET_TIME(ANDROID_ALARM_ELAPSED_REALTIME), &ts);

    if (result == 0) {
        timestamp = seconds_to_nanoseconds(ts.tv_sec) + ts.tv_nsec;
        checkTimeStamps(timestamp, &prevTimestamp, &prevMethod, METHOD_IOCTL);
        return timestamp;
    }

    // /dev/alarm doesn't exist, fallback to CLOCK_BOOTTIME
    result = clock_gettime(CLOCK_BOOTTIME, &ts);
    if (result == 0) {
        timestamp = seconds_to_nanoseconds(ts.tv_sec) + ts.tv_nsec;
        checkTimeStamps(timestamp, &prevTimestamp, &prevMethod,
                        METHOD_CLOCK_GETTIME);
        return timestamp;
    }

    // XXX: there was an error, probably because the driver didn't
    // exist ... this should return
    // a real error, like an exception!
    timestamp = systemTime(SYSTEM_TIME_MONOTONIC);
    checkTimeStamps(timestamp, &prevTimestamp, &prevMethod,
                    METHOD_SYSTEMTIME);
    return timestamp;
#else
    return systemTime(SYSTEM_TIME_MONOTONIC);
#endif
}

}; // namespace android
