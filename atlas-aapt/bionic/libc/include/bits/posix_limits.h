/*
 * Copyright (C) 2014 The Android Open Source Project
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

#ifndef _BITS_POSIX_LIMITS_H_
#define _BITS_POSIX_LIMITS_H_


/* Any constant values here other than -1 or 200809L are explicitly specified by POSIX.1-2008. */
/* Keep it sorted. */
#define _POSIX_ADVISORY_INFO        200809L
#define _POSIX_AIO_LISTIO_MAX       2
#define _POSIX_AIO_MAX              1
#define _POSIX_ARG_MAX              4096
#define _POSIX_ASYNCHRONOUS_IO      -1  /* not implemented */
#define _POSIX_BARRIERS             -1  /* not implemented */
#define _POSIX_CHILD_MAX            25
#define _POSIX_CHOWN_RESTRICTED     1  /* yes, chown requires appropriate privileges */
#define _POSIX_CLOCK_SELECTION      200809L
#define _POSIX_CPUTIME              0  /* Use sysconf to detect support at runtime. */
#define _POSIX_DELAYTIMER_MAX       32
#define _POSIX_FSYNC                200809L  /* fdatasync() supported */
#define _POSIX_HOST_NAME_MAX        255
#define _POSIX_IPV6                 200809L
#define _POSIX_JOB_CONTROL          1  /* job control is a Linux feature */
#define _POSIX_LINK_MAX             8
#define _POSIX_LOGIN_NAME_MAX       9  /* includes trailing NUL */
#define _POSIX_MAPPED_FILES         200809L  /* mmap-ed files supported */
#define _POSIX_MAX_CANON            255
#define _POSIX_MAX_INPUT            255
#define _POSIX_MEMLOCK              200809L
#define _POSIX_MEMLOCK_RANGE        200809L
#define _POSIX_MEMORY_PROTECTION    200809L
#define _POSIX_MESSAGE_PASSING      -1  /* not implemented */
#define _POSIX_MONOTONIC_CLOCK      0  /* the monotonic clock may be available; ask sysconf */
#define _POSIX_MQ_OPEN_MAX          8
#define _POSIX_MQ_PRIO_MAX          32
#define _POSIX_NAME_MAX             14
#define _POSIX_NGROUPS_MAX          8
#define _POSIX_NO_TRUNC             1  /* very long pathnames generate an error */
#define _POSIX_OPEN_MAX             20
#define _POSIX_PATH_MAX             256
#define _POSIX_PIPE_BUF             512
#define _POSIX_PRIORITY_SCHEDULING  200809L  /* priority scheduling is a Linux feature */
#define _POSIX_PRIORITIZED_IO       -1  /* not implemented */
#define _POSIX_RAW_SOCKETS          200809L
#define _POSIX_READER_WRITER_LOCKS  200809L
#define _POSIX_REALTIME_SIGNALS     200809L
#define _POSIX_REGEXP               1
#define _POSIX_RE_DUP_MAX           255
#define _POSIX_SAVED_IDS            1  /* saved user ids is a Linux feature */
#define _POSIX_SEMAPHORES           200809L
#define _POSIX_SEM_NSEMS_MAX        256
#define _POSIX_SEM_VALUE_MAX        32767
#define _POSIX_SHARED_MEMORY_OBJECTS  -1  /* shm_open()/shm_unlink() not implemented */
#define _POSIX_SHELL                1   /* system() supported */
#define _POSIX_SIGQUEUE_MAX         32
#define _POSIX_SPAWN                -1  /* not implemented */
#define _POSIX_SPIN_LOCKS           -1  /* not implemented */
#define _POSIX_SPORADIC_SERVER      -1  /* not implemented */
#define _POSIX_SSIZE_MAX            32767
#define _POSIX_STREAM_MAX           8
#define _POSIX_SYMLINK_MAX          255
#define _POSIX_SYMLOOP_MAX          8
#define _POSIX_SYNCHRONIZED_IO      200809L  /* synchronized i/o supported */
#define _POSIX_THREADS              200809L  /* we support threads */
#define _POSIX_THREAD_ATTR_STACKADDR  200809L
#define _POSIX_THREAD_ATTR_STACKSIZE  200809L
#define _POSIX_THREAD_CPUTIME       0  /* Use sysconf to detect support at runtime. */
#define _POSIX_THREAD_DESTRUCTOR_ITERATIONS 4
#define _POSIX_THREAD_KEYS_MAX      128
#define _POSIX_THREAD_PRIORITY_SCHEDULING 200809L
#define _POSIX_THREAD_PRIO_INHERIT  200809L  /* linux feature */
#define _POSIX_THREAD_PRIO_PROTECT  200809L  /* linux feature */
#define _POSIX_THREAD_PROCESS_SHARED  -1  /* not implemented */
#define _POSIX_THREAD_ROBUST_PRIO_INHERIT -1  /* not implemented */
#define _POSIX_THREAD_ROBUST_PRIO_PROTECT -1  /* not implemented */
#define _POSIX_THREAD_SAFE_FUNCTIONS 200809L
#define _POSIX_THREAD_SPORADIC_SERVER -1  /* not implemented */
#define _POSIX_THREAD_THREADS_MAX   64
#define _POSIX_TIMEOUTS             200809L
#define _POSIX_TIMERS               200809L  /* Posix timers are supported */
#define _POSIX_TIMER_MAX            32
#define _POSIX_TRACE                -1  /* not implemented */
#define _POSIX_TRACE_EVENT_FILTER   -1  /* not implemented */
#define _POSIX_TRACE_INHERIT        -1  /* not implemented */
#define _POSIX_TRACE_LOG            -1  /* not implemented */
#define _POSIX_TRACE_NAME_MAX       8
#define _POSIX_TRACE_SYS_MAX        8
#define _POSIX_TRACE_USER_EVENT_MAX 32
#define _POSIX_TTY_NAME_MAX         9  /* includes trailing NUL */
#define _POSIX_TYPED_MEMORY_OBJECTS -1  /* not implemented */
#define _POSIX_TZNAME_MAX           6
#define _POSIX_VDISABLE             '\0'

#if defined(__LP64__)
#define _POSIX_V7_ILP32_OFF32       -1
#define _POSIX_V7_ILP32_OFFBIG      -1
#define _POSIX_V7_LP64_OFF64         1
#define _POSIX_V7_LPBIG_OFFBIG       1
#else
#define _POSIX_V7_ILP32_OFF32        1
#define _POSIX_V7_ILP32_OFFBIG      -1
#define _POSIX_V7_LP64_OFF64        -1
#define _POSIX_V7_LPBIG_OFFBIG      -1
#endif

#define _POSIX2_BC_BASE_MAX         99
#define _POSIX2_BC_DIM_MAX          2048
#define _POSIX2_BC_SCALE_MAX        99
#define _POSIX2_BC_STRING_MAX       1000
#define _POSIX2_CHARCLASS_NAME_MAX  14
#define _POSIX2_CHAR_TERM           -1  /* not implemented */
#define _POSIX2_COLL_WEIGHTS_MAX    2
#define _POSIX2_C_BIND              _POSIX_VERSION
#define _POSIX2_C_DEV               -1  /* c dev utilities not implemented */
#define _POSIX2_EXPR_NEST_MAX       32
#define _POSIX2_LINE_MAX            2048
#define _POSIX2_LOCALEDEF           -1  /* localedef utilitiy not implemented */
#define _POSIX2_RE_DUP_MAX          _POSIX_RE_DUP_MAX
#define _POSIX2_SW_DEV              -1  /* software dev utilities not implemented */
#define _POSIX2_UPE                 -1  /* user portability utilities not implemented */

#define _XOPEN_ENH_I18N             -1  /* we don't support internationalization in the C library */
#define _XOPEN_CRYPT                -1  /* don't support X/Open Encryption */
#define _XOPEN_IOV_MAX              16
#define _XOPEN_LEGACY               -1  /* not support all */
#define _XOPEN_REALTIME             -1 /* we don't support all these functions */
#define _XOPEN_REALTIME_THREADS     -1  /* same here */
#define _XOPEN_SHM                  -1
#define _XOPEN_UNIX                 1

#endif
