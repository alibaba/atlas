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

#include <ctype.h>
#include <limits.h>
#include <stdio.h>
#include <string.h>

#include <log/log_read.h>

#include "log_portability.h"

LIBLOG_ABI_PRIVATE const char log_time::default_format[] = "%m-%d %H:%M:%S.%q";
LIBLOG_ABI_PRIVATE const timespec log_time::EPOCH = { 0, 0 };

// Add %#q for fractional seconds to standard strptime function

LIBLOG_ABI_PRIVATE char *log_time::strptime(const char *s, const char *format) {
    time_t now;
#ifdef __linux__
    *this = log_time(CLOCK_REALTIME);
    now = tv_sec;
#else
    time(&now);
    tv_sec = now;
    tv_nsec = 0;
#endif

    struct tm *ptm;
#if !defined(_WIN32)
    struct tm tmBuf;
    ptm = localtime_r(&now, &tmBuf);
#else
    ptm = localtime(&now);
#endif

    char fmt[strlen(format) + 1];
    strcpy(fmt, format);

    char *ret = const_cast<char *> (s);
    char *cp;
    for (char *f = cp = fmt; ; ++cp) {
        if (!*cp) {
            if (f != cp) {
                ret = ::strptime(ret, f, ptm);
            }
            break;
        }
        if (*cp != '%') {
            continue;
        }
        char *e = cp;
        ++e;
#if (defined(__BIONIC__))
        if (*e == 's') {
            *cp = '\0';
            if (*f) {
                ret = ::strptime(ret, f, ptm);
                if (!ret) {
                    break;
                }
            }
            tv_sec = 0;
            while (isdigit(*ret)) {
                tv_sec = tv_sec * 10 + *ret - '0';
                ++ret;
            }
            now = tv_sec;
#if !defined(_WIN32)
            ptm = localtime_r(&now, &tmBuf);
#else
            ptm = localtime(&now);
#endif
        } else
#endif
        {
            unsigned num = 0;
            while (isdigit(*e)) {
                num = num * 10 + *e - '0';
                ++e;
            }
            if (*e != 'q') {
                continue;
            }
            *cp = '\0';
            if (*f) {
                ret = ::strptime(ret, f, ptm);
                if (!ret) {
                    break;
                }
            }
            unsigned long mul = NS_PER_SEC;
            if (num == 0) {
                num = INT_MAX;
            }
            tv_nsec = 0;
            while (isdigit(*ret) && num && (mul > 1)) {
                --num;
                mul /= 10;
                tv_nsec = tv_nsec + (*ret - '0') * mul;
                ++ret;
            }
        }
        f = cp = e;
        ++f;
    }

    if (ret) {
        tv_sec = mktime(ptm);
        return ret;
    }

    // Upon error, place a known value into the class, the current time.
#ifdef __linux__
    *this = log_time(CLOCK_REALTIME);
#else
    time(&now);
    tv_sec = now;
    tv_nsec = 0;
#endif
    return ret;
}

LIBLOG_ABI_PRIVATE log_time log_time::operator-= (const timespec &T) {
    // No concept of negative time, clamp to EPOCH
    if (*this <= T) {
        return *this = EPOCH;
    }

    if (this->tv_nsec < (unsigned long int)T.tv_nsec) {
        --this->tv_sec;
        this->tv_nsec = NS_PER_SEC + this->tv_nsec - T.tv_nsec;
    } else {
        this->tv_nsec -= T.tv_nsec;
    }
    this->tv_sec -= T.tv_sec;

    return *this;
}

LIBLOG_ABI_PRIVATE log_time log_time::operator+= (const timespec &T) {
    this->tv_nsec += (unsigned long int)T.tv_nsec;
    if (this->tv_nsec >= NS_PER_SEC) {
        this->tv_nsec -= NS_PER_SEC;
        ++this->tv_sec;
    }
    this->tv_sec += T.tv_sec;

    return *this;
}

LIBLOG_ABI_PRIVATE log_time log_time::operator-= (const log_time &T) {
    // No concept of negative time, clamp to EPOCH
    if (*this <= T) {
        return *this = EPOCH;
    }

    if (this->tv_nsec < T.tv_nsec) {
        --this->tv_sec;
        this->tv_nsec = NS_PER_SEC + this->tv_nsec - T.tv_nsec;
    } else {
        this->tv_nsec -= T.tv_nsec;
    }
    this->tv_sec -= T.tv_sec;

    return *this;
}

LIBLOG_ABI_PRIVATE log_time log_time::operator+= (const log_time &T) {
    this->tv_nsec += T.tv_nsec;
    if (this->tv_nsec >= NS_PER_SEC) {
        this->tv_nsec -= NS_PER_SEC;
        ++this->tv_sec;
    }
    this->tv_sec += T.tv_sec;

    return *this;
}
