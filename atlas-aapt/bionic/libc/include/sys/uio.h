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
#ifndef _SYS_UIO_H_
#define _SYS_UIO_H_

#include <sys/cdefs.h>
#include <sys/types.h>
#include <linux/uio.h>

__BEGIN_DECLS

int readv(int, const struct iovec*, int);
int writev(int, const struct iovec*, int);

#if defined(__USE_GNU)
#if defined(__USE_FILE_OFFSET64)
ssize_t preadv(int, const struct iovec*, int, off_t) __RENAME(preadv64);
ssize_t pwritev(int, const struct iovec*, int, off_t) __RENAME(pwritev64);
#else
ssize_t preadv(int, const struct iovec*, int, off_t);
ssize_t pwritev(int, const struct iovec*, int, off_t);
#endif
ssize_t preadv64(int, const struct iovec*, int, off64_t);
ssize_t pwritev64(int, const struct iovec*, int, off64_t);
#endif

#if defined(__USE_GNU)
ssize_t process_vm_readv(pid_t, const struct iovec*, unsigned long, const struct iovec*, unsigned long, unsigned long);
ssize_t process_vm_writev(pid_t, const struct iovec*, unsigned long, const struct iovec*, unsigned long, unsigned long);
#endif

__END_DECLS

#endif /* _SYS_UIO_H_ */
