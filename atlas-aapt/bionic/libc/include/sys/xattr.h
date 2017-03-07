/*
 * Copyright (C) 2012 The Android Open Source Project
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
#ifndef _SYS_XATTR_H_
#define _SYS_XATTR_H_

#include <sys/types.h>

__BEGIN_DECLS

#define XATTR_CREATE 1
#define XATTR_REPLACE 2

extern int fsetxattr(int fd, const char *name, const void *value, size_t size, int flags);
extern int setxattr(const char *path, const char *name, const void *value, size_t size, int flags);
extern int lsetxattr(const char *path, const char *name, const void *value, size_t size, int flags);

extern ssize_t fgetxattr(int fd, const char *name, void *value, size_t size);
extern ssize_t getxattr(const char *path, const char *name, void *value, size_t size);
extern ssize_t lgetxattr(const char *path, const char *name, void *value, size_t size);

extern ssize_t listxattr(const char *path, char *list, size_t size);
extern ssize_t llistxattr(const char *path, char *list, size_t size);
extern ssize_t flistxattr(int fd, char *list, size_t size);

extern int removexattr(const char *path, const char *name);
extern int lremovexattr(const char *path, const char *name);
extern int fremovexattr(int fd, const char *name);

__END_DECLS

#endif /* _SYS_XATTR_H_ */
