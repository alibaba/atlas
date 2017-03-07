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

#ifndef _SYS_SELECT_H_
#define _SYS_SELECT_H_

#include <linux/time.h>
#include <signal.h>
#include <sys/cdefs.h>
#include <sys/types.h>

__BEGIN_DECLS

#define FD_SETSIZE 1024
#define NFDBITS (8 * sizeof(unsigned long))
#define __FDSET_LONGS (FD_SETSIZE/NFDBITS)

typedef struct {
  unsigned long fds_bits[__FDSET_LONGS];
} fd_set;

#define __FDELT(fd) ((fd) / NFDBITS)
#define __FDMASK(fd) (1UL << ((fd) % NFDBITS))
#define __FDS_BITS(set) (((fd_set*)(set))->fds_bits)

/* Inline loop so we don't have to declare memset. */
#define FD_ZERO(set) \
  do { \
    size_t __i; \
    for (__i = 0; __i < __FDSET_LONGS; ++__i) { \
      (set)->fds_bits[__i] = 0; \
    } \
  } while (0)

extern void __FD_CLR_chk(int, fd_set*, size_t);
extern void __FD_SET_chk(int, fd_set*, size_t);
extern int  __FD_ISSET_chk(int, fd_set*, size_t);

#if defined(__BIONIC_FORTIFY)
#define FD_CLR(fd, set) __FD_CLR_chk(fd, set, __bos(set))
#define FD_SET(fd, set) __FD_SET_chk(fd, set, __bos(set))
#define FD_ISSET(fd, set) __FD_ISSET_chk(fd, set, __bos(set))
#else
#define FD_CLR(fd, set) (__FDS_BITS(set)[__FDELT(fd)] &= ~__FDMASK(fd))
#define FD_SET(fd, set) (__FDS_BITS(set)[__FDELT(fd)] |= __FDMASK(fd))
#define FD_ISSET(fd, set) ((__FDS_BITS(set)[__FDELT(fd)] & __FDMASK(fd)) != 0)
#endif /* defined(__BIONIC_FORTIFY) */

extern int select(int, fd_set*, fd_set*, fd_set*, struct timeval*);
extern int pselect(int, fd_set*, fd_set*, fd_set*, const struct timespec*, const sigset_t*);

__END_DECLS

#endif /* _SYS_SELECT_H_ */
