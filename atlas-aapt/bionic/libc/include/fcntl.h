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

#ifndef _FCNTL_H
#define _FCNTL_H

#include <sys/cdefs.h>
#include <sys/types.h>
#include <linux/fadvise.h>
#include <linux/fcntl.h>
#include <linux/stat.h>
#include <linux/uio.h>

#if defined(__USE_GNU) || defined(__USE_BSD)
#include <bits/lockf.h>
#endif

__BEGIN_DECLS

#ifdef __LP64__
/* LP64 kernels don't have F_*64 defines because their flock is 64-bit. */
#define F_GETLK64  F_GETLK
#define F_SETLK64  F_SETLK
#define F_SETLKW64 F_SETLKW
#endif

#define O_ASYNC FASYNC
#define O_RSYNC O_SYNC

#define SPLICE_F_MOVE 1
#define SPLICE_F_NONBLOCK 2
#define SPLICE_F_MORE 4
#define SPLICE_F_GIFT 8

#define SYNC_FILE_RANGE_WAIT_BEFORE 1
#define SYNC_FILE_RANGE_WRITE 2
#define SYNC_FILE_RANGE_WAIT_AFTER 4

extern int creat(const char*, mode_t);
extern int creat64(const char*, mode_t);
extern int fcntl(int, int, ...);
extern int openat(int, const char*, int, ...);
extern int openat64(int, const char*, int, ...);
extern int open(const char*, int, ...);
extern int open64(const char*, int, ...);
extern ssize_t splice(int, off64_t*, int, off64_t*, size_t, unsigned int);
extern ssize_t tee(int, int, size_t, unsigned int);
extern int unlinkat(int, const char*, int);
extern ssize_t vmsplice(int, const struct iovec*, size_t, unsigned int);

#if defined(__USE_FILE_OFFSET64)
extern int fallocate(int, int, off_t, off_t) __RENAME(fallocate64);
extern int posix_fadvise(int, off_t, off_t, int) __RENAME(posix_fadvise64);
extern int posix_fallocate(int, off_t, off_t) __RENAME(posix_fallocate);
#else
extern int fallocate(int, int, off_t, off_t);
extern int posix_fadvise(int, off_t, off_t, int);
extern int posix_fallocate(int, off_t, off_t);
#endif
extern int fallocate64(int, int, off64_t, off64_t);
extern int posix_fadvise64(int, off64_t, off64_t, int);
extern int posix_fallocate64(int, off64_t, off64_t);

extern int __open_2(const char*, int);
extern int __open_real(const char*, int, ...) __RENAME(open);
extern int __openat_2(int, const char*, int);
extern int __openat_real(int, const char*, int, ...) __RENAME(openat);
__errordecl(__creat_missing_mode, "called with O_CREAT, but missing mode");
__errordecl(__creat_too_many_args, "too many arguments");

#if defined(__BIONIC_FORTIFY)

#if !defined(__clang__)

__BIONIC_FORTIFY_INLINE
int open(const char* pathname, int flags, ...) {
    if (__builtin_constant_p(flags)) {
        if ((flags & O_CREAT) && __builtin_va_arg_pack_len() == 0) {
            __creat_missing_mode();  // compile time error
        }
    }

    if (__builtin_va_arg_pack_len() > 1) {
        __creat_too_many_args();  // compile time error
    }

    if ((__builtin_va_arg_pack_len() == 0) && !__builtin_constant_p(flags)) {
        return __open_2(pathname, flags);
    }

    return __open_real(pathname, flags, __builtin_va_arg_pack());
}

__BIONIC_FORTIFY_INLINE
int openat(int dirfd, const char* pathname, int flags, ...) {
    if (__builtin_constant_p(flags)) {
        if ((flags & O_CREAT) && __builtin_va_arg_pack_len() == 0) {
            __creat_missing_mode();  // compile time error
        }
    }

    if (__builtin_va_arg_pack_len() > 1) {
        __creat_too_many_args();  // compile time error
    }

    if ((__builtin_va_arg_pack_len() == 0) && !__builtin_constant_p(flags)) {
        return __openat_2(dirfd, pathname, flags);
    }

    return __openat_real(dirfd, pathname, flags, __builtin_va_arg_pack());
}

#endif /* !defined(__clang__) */

#endif /* defined(__BIONIC_FORTIFY) */

__END_DECLS

#endif /* _FCNTL_H */
