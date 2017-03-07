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
#ifndef _SYS_SYSCONF_H_
#define _SYS_SYSCONF_H_

#include <sys/cdefs.h>

__BEGIN_DECLS

/* as listed by Posix sysconf() description */
/* most of these will return -1 and ENOSYS  */

#define _SC_ARG_MAX             0x0000
#define _SC_BC_BASE_MAX         0x0001
#define _SC_BC_DIM_MAX          0x0002
#define _SC_BC_SCALE_MAX        0x0003
#define _SC_BC_STRING_MAX       0x0004
#define _SC_CHILD_MAX           0x0005
#define _SC_CLK_TCK             0x0006
#define _SC_COLL_WEIGHTS_MAX    0x0007
#define _SC_EXPR_NEST_MAX       0x0008
#define _SC_LINE_MAX            0x0009
#define _SC_NGROUPS_MAX         0x000a
#define _SC_OPEN_MAX            0x000b
#define _SC_PASS_MAX            0x000c
#define _SC_2_C_BIND            0x000d
#define _SC_2_C_DEV             0x000e
#define _SC_2_C_VERSION         0x000f  /* Obsolescent in POSIX.1-2008, TODO: remove it. */
#define _SC_2_CHAR_TERM         0x0010
#define _SC_2_FORT_DEV          0x0011
#define _SC_2_FORT_RUN          0x0012
#define _SC_2_LOCALEDEF         0x0013
#define _SC_2_SW_DEV            0x0014
#define _SC_2_UPE               0x0015
#define _SC_2_VERSION           0x0016
#define _SC_JOB_CONTROL         0x0017
#define _SC_SAVED_IDS           0x0018
#define _SC_VERSION             0x0019
#define _SC_RE_DUP_MAX          0x001a
#define _SC_STREAM_MAX          0x001b
#define _SC_TZNAME_MAX          0x001c
#define _SC_XOPEN_CRYPT         0x001d
#define _SC_XOPEN_ENH_I18N      0x001e
#define _SC_XOPEN_SHM           0x001f
#define _SC_XOPEN_VERSION       0x0020
#define _SC_XOPEN_XCU_VERSION   0x0021  /* Obsolescent in POSIX.1-2008, TODO: remove it. */
#define _SC_XOPEN_REALTIME      0x0022
#define _SC_XOPEN_REALTIME_THREADS  0x0023
#define _SC_XOPEN_LEGACY        0x0024
#define _SC_ATEXIT_MAX          0x0025
#define _SC_IOV_MAX             0x0026
#define _SC_PAGESIZE            0x0027
#define _SC_PAGE_SIZE           0x0028
#define _SC_XOPEN_UNIX          0x0029
#define _SC_XBS5_ILP32_OFF32    0x002a  /* Obsolescent in POSIX.1-2008, TODO: remove it. */
#define _SC_XBS5_ILP32_OFFBIG   0x002b  /* Obsolescent in POSIX.1-2008, TODO: remove it. */
#define _SC_XBS5_LP64_OFF64     0x002c  /* Obsolescent in POSIX.1-2008, TODO: remove it. */
#define _SC_XBS5_LPBIG_OFFBIG   0x002d  /* Obsolescent in POSIX.1-2008, TODO: remove it. */
#define _SC_AIO_LISTIO_MAX      0x002e
#define _SC_AIO_MAX             0x002f
#define _SC_AIO_PRIO_DELTA_MAX  0x0030
#define _SC_DELAYTIMER_MAX      0x0031
#define _SC_MQ_OPEN_MAX         0x0032
#define _SC_MQ_PRIO_MAX         0x0033
#define _SC_RTSIG_MAX           0x0034
#define _SC_SEM_NSEMS_MAX       0x0035
#define _SC_SEM_VALUE_MAX       0x0036
#define _SC_SIGQUEUE_MAX        0x0037
#define _SC_TIMER_MAX           0x0038
#define _SC_ASYNCHRONOUS_IO     0x0039
#define _SC_FSYNC               0x003a
#define _SC_MAPPED_FILES        0x003b
#define _SC_MEMLOCK             0x003c
#define _SC_MEMLOCK_RANGE       0x003d
#define _SC_MEMORY_PROTECTION   0x003e
#define _SC_MESSAGE_PASSING     0x003f
#define _SC_PRIORITIZED_IO      0x0040
#define _SC_PRIORITY_SCHEDULING 0x0041
#define _SC_REALTIME_SIGNALS    0x0042
#define _SC_SEMAPHORES          0x0043
#define _SC_SHARED_MEMORY_OBJECTS  0x0044
#define _SC_SYNCHRONIZED_IO     0x0045
#define _SC_TIMERS              0x0046
#define _SC_GETGR_R_SIZE_MAX    0x0047
#define _SC_GETPW_R_SIZE_MAX    0x0048
#define _SC_LOGIN_NAME_MAX      0x0049
#define _SC_THREAD_DESTRUCTOR_ITERATIONS  0x004a
#define _SC_THREAD_KEYS_MAX     0x004b
#define _SC_THREAD_STACK_MIN    0x004c
#define _SC_THREAD_THREADS_MAX  0x004d
#define _SC_TTY_NAME_MAX        0x004e

#define _SC_THREADS                     0x004f
#define _SC_THREAD_ATTR_STACKADDR       0x0050
#define _SC_THREAD_ATTR_STACKSIZE       0x0051
#define _SC_THREAD_PRIORITY_SCHEDULING  0x0052
#define _SC_THREAD_PRIO_INHERIT         0x0053
#define _SC_THREAD_PRIO_PROTECT         0x0054
#define _SC_THREAD_SAFE_FUNCTIONS       0x0055

#define _SC_NPROCESSORS_CONF            0x0060
#define _SC_NPROCESSORS_ONLN            0x0061
#define _SC_PHYS_PAGES                  0x0062
#define _SC_AVPHYS_PAGES                0x0063
#define _SC_MONOTONIC_CLOCK             0x0064

#define _SC_2_PBS               0x0065
#define _SC_2_PBS_ACCOUNTING    0x0066
#define _SC_2_PBS_CHECKPOINT    0x0067
#define _SC_2_PBS_LOCATE        0x0068
#define _SC_2_PBS_MESSAGE       0x0069
#define _SC_2_PBS_TRACK         0x006a
#define _SC_ADVISORY_INFO       0x006b
#define _SC_BARRIERS            0x006c
#define _SC_CLOCK_SELECTION     0x006d
#define _SC_CPUTIME             0x006e
#define _SC_HOST_NAME_MAX       0x006f
#define _SC_IPV6                0x0070
#define _SC_RAW_SOCKETS         0x0071
#define _SC_READER_WRITER_LOCKS 0x0072
#define _SC_REGEXP              0x0073
#define _SC_SHELL               0x0074
#define _SC_SPAWN               0x0075
#define _SC_SPIN_LOCKS          0x0076
#define _SC_SPORADIC_SERVER     0x0077
#define _SC_SS_REPL_MAX         0x0078
#define _SC_SYMLOOP_MAX         0x0079
#define _SC_THREAD_CPUTIME      0x007a
#define _SC_THREAD_PROCESS_SHARED       0x007b
#define _SC_THREAD_ROBUST_PRIO_INHERIT  0x007c
#define _SC_THREAD_ROBUST_PRIO_PROTECT  0x007d
#define _SC_THREAD_SPORADIC_SERVER      0x007e
#define _SC_TIMEOUTS            0x007f
#define _SC_TRACE               0x0080
#define _SC_TRACE_EVENT_FILTER  0x0081
#define _SC_TRACE_EVENT_NAME_MAX  0x0082
#define _SC_TRACE_INHERIT       0x0083
#define _SC_TRACE_LOG           0x0084
#define _SC_TRACE_NAME_MAX      0x0085
#define _SC_TRACE_SYS_MAX       0x0086
#define _SC_TRACE_USER_EVENT_MAX  0x0087
#define _SC_TYPED_MEMORY_OBJECTS  0x0088
#define _SC_V7_ILP32_OFF32      0x0089
#define _SC_V7_ILP32_OFFBIG     0x008a
#define _SC_V7_LP64_OFF64       0x008b
#define _SC_V7_LPBIG_OFFBIG     0x008c
#define _SC_XOPEN_STREAMS       0x008d
#define _SC_XOPEN_UUCP          0x008e

#define _SC_LEVEL1_ICACHE_SIZE      0x008f
#define _SC_LEVEL1_ICACHE_ASSOC     0x0090
#define _SC_LEVEL1_ICACHE_LINESIZE  0x0091
#define _SC_LEVEL1_DCACHE_SIZE      0x0092
#define _SC_LEVEL1_DCACHE_ASSOC     0x0093
#define _SC_LEVEL1_DCACHE_LINESIZE  0x0094
#define _SC_LEVEL2_CACHE_SIZE       0x0095
#define _SC_LEVEL2_CACHE_ASSOC      0x0096
#define _SC_LEVEL2_CACHE_LINESIZE   0x0097
#define _SC_LEVEL3_CACHE_SIZE       0x0098
#define _SC_LEVEL3_CACHE_ASSOC      0x0099
#define _SC_LEVEL3_CACHE_LINESIZE   0x009a
#define _SC_LEVEL4_CACHE_SIZE       0x009b
#define _SC_LEVEL4_CACHE_ASSOC      0x009c
#define _SC_LEVEL4_CACHE_LINESIZE   0x009d

long sysconf(int);

__END_DECLS

#endif /* _SYS_SYSCONF_H_ */
