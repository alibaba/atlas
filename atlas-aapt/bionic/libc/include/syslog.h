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

#ifndef _SYSLOG_H
#define _SYSLOG_H

#include <stdio.h>
#include <sys/cdefs.h>
#include <stdarg.h>

__BEGIN_DECLS

/* Priorities are translated to Android log priorities as shown. */
#define LOG_EMERG   0 /* ERROR */
#define LOG_ALERT   1 /* ERROR */
#define LOG_CRIT    2 /* ERROR */
#define LOG_ERR     3 /* ERROR */
#define LOG_WARNING 4 /* WARN */
#define LOG_NOTICE  5 /* INFO */
#define LOG_INFO    6 /* INFO */
#define LOG_DEBUG   7 /* DEBUG */

#define LOG_PRIMASK 7
#define LOG_PRI(x) ((x) & LOG_PRIMASK)
#define LOG_MAKEPRI(fac, pri) ((fac) | (pri))

/* Facilities are currently ignored on Android. */
#define LOG_KERN     0000
#define LOG_USER     0010
#define LOG_MAIL     0020
#define LOG_DAEMON   0030
#define LOG_AUTH     0040
#define LOG_SYSLOG   0050
#define LOG_LPR      0060
#define LOG_NEWS     0070
#define LOG_UUCP     0100
#define LOG_CRON     0110
#define LOG_AUTHPRIV 0120
#define LOG_FTP      0130
#define LOG_LOCAL0   0200
#define LOG_LOCAL1   0210
#define LOG_LOCAL2   0220
#define LOG_LOCAL3   0230
#define LOG_LOCAL4   0240
#define LOG_LOCAL5   0250
#define LOG_LOCAL6   0260
#define LOG_LOCAL7   0270

#define LOG_FACMASK 01770
#define LOG_FAC(x) (((x) >> 3) & (LOG_FACMASK >> 3))

#define LOG_MASK(pri) (1 << (pri))
#define LOG_UPTO(pri) ((1 << ((pri)+1)) - 1)

/* openlog(3) flags are currently ignored on Android. */
#define LOG_PID    0x01
#define LOG_CONS   0x02
#define LOG_ODELAY 0x04
#define LOG_NDELAY 0x08
#define LOG_NOWAIT 0x10
#define LOG_PERROR 0x20

void closelog(void);
void openlog(const char*, int, int);
int setlogmask(int);
void syslog(int, const char*, ...) __printflike(2, 3);
void vsyslog(int, const char*, va_list) __printflike(2, 0);

__END_DECLS

#endif /* _SYSLOG_H */
