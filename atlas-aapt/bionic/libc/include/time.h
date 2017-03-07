/*
 * Copyright (C) 2008 The Android Open Source Project
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *  * Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *  * Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS
 * OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 * OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 */

#ifndef _TIME_H_
#define _TIME_H_

#include <sys/cdefs.h>
#include <sys/time.h>
#include <xlocale.h>

__BEGIN_DECLS

#define CLOCKS_PER_SEC 1000000

extern char* tzname[] __LIBC_ABI_PUBLIC__;
extern int daylight __LIBC_ABI_PUBLIC__;
extern long int timezone __LIBC_ABI_PUBLIC__;

struct sigevent;

struct tm {
  int tm_sec;
  int tm_min;
  int tm_hour;
  int tm_mday;
  int tm_mon;
  int tm_year;
  int tm_wday;
  int tm_yday;
  int tm_isdst;
  long int tm_gmtoff;
  const char* tm_zone;
};

#define TM_ZONE tm_zone

extern time_t time(time_t*) __LIBC_ABI_PUBLIC__;
extern int nanosleep(const struct timespec*, struct timespec*) __LIBC_ABI_PUBLIC__;

extern char* asctime(const struct tm*) __LIBC_ABI_PUBLIC__;
extern char* asctime_r(const struct tm*, char*) __LIBC_ABI_PUBLIC__;

extern double difftime(time_t, time_t) __LIBC_ABI_PUBLIC__;
extern time_t mktime(struct tm*) __LIBC_ABI_PUBLIC__;

extern struct tm* localtime(const time_t*) __LIBC_ABI_PUBLIC__;
extern struct tm* localtime_r(const time_t*, struct tm*) __LIBC_ABI_PUBLIC__;

extern struct tm* gmtime(const time_t*) __LIBC_ABI_PUBLIC__;
extern struct tm* gmtime_r(const time_t*, struct tm*) __LIBC_ABI_PUBLIC__;

extern char* strptime(const char*, const char*, struct tm*) __LIBC_ABI_PUBLIC__;
extern size_t strftime(char*, size_t, const char*, const struct tm*) __LIBC_ABI_PUBLIC__;
extern size_t strftime_l(char *, size_t, const char *, const struct tm *, locale_t) __LIBC_ABI_PUBLIC__;

extern char* ctime(const time_t*) __LIBC_ABI_PUBLIC__;
extern char* ctime_r(const time_t*, char*) __LIBC_ABI_PUBLIC__;

extern void tzset(void) __LIBC_ABI_PUBLIC__;

extern clock_t clock(void) __LIBC_ABI_PUBLIC__;

extern int clock_getcpuclockid(pid_t, clockid_t*) __LIBC_ABI_PUBLIC__;

extern int clock_getres(clockid_t, struct timespec*) __LIBC_ABI_PUBLIC__;
extern int clock_gettime(clockid_t, struct timespec*) __LIBC_ABI_PUBLIC__;
extern int clock_nanosleep(clockid_t, int, const struct timespec*, struct timespec*) __LIBC_ABI_PUBLIC__;
extern int clock_settime(clockid_t, const struct timespec*) __LIBC_ABI_PUBLIC__;

extern int timer_create(int, struct sigevent*, timer_t*) __LIBC_ABI_PUBLIC__;
extern int timer_delete(timer_t) __LIBC_ABI_PUBLIC__;
extern int timer_settime(timer_t, int, const struct itimerspec*, struct itimerspec*) __LIBC_ABI_PUBLIC__;
extern int timer_gettime(timer_t, struct itimerspec*) __LIBC_ABI_PUBLIC__;
extern int timer_getoverrun(timer_t) __LIBC_ABI_PUBLIC__;

/* Non-standard extensions that are in the BSDs and glibc. */
extern time_t timelocal(struct tm*) __LIBC_ABI_PUBLIC__;
extern time_t timegm(struct tm*) __LIBC_ABI_PUBLIC__;

__END_DECLS

#endif /* _TIME_H_ */
