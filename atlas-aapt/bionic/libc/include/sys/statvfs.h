/*
 * Copyright (C) 2013 The Android Open Source Project
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

#ifndef _SYS_STATVFS_H_
#define _SYS_STATVFS_H_

#include <stdint.h>
#include <sys/cdefs.h>
#include <sys/types.h>

__BEGIN_DECLS

#ifdef __LP64__
#define __STATVFS64_RESERVED uint32_t __f_reserved[6];
#else
#define __STATVFS64_RESERVED
#endif

#define __STATVFS64_BODY \
  unsigned long f_bsize; \
  unsigned long f_frsize; \
  fsblkcnt_t    f_blocks; \
  fsblkcnt_t    f_bfree; \
  fsblkcnt_t    f_bavail; \
  fsfilcnt_t    f_files; \
  fsfilcnt_t    f_ffree; \
  fsfilcnt_t    f_favail; \
  unsigned long f_fsid; \
  unsigned long f_flag; \
  unsigned long f_namemax; \
  __STATVFS64_RESERVED

struct statvfs { __STATVFS64_BODY };
struct statvfs64 { __STATVFS64_BODY };

#undef __STATVFS64_BODY
#undef __STATVFS64_RESERVED

#define ST_RDONLY      0x0001
#define ST_NOSUID      0x0002
#define ST_NODEV       0x0004
#define ST_NOEXEC      0x0008
#define ST_SYNCHRONOUS 0x0010
#define ST_MANDLOCK    0x0040
#define ST_NOATIME     0x0400
#define ST_NODIRATIME  0x0800
#define ST_RELATIME    0x1000

extern int statvfs(const char* __restrict, struct statvfs* __restrict) __nonnull((1, 2));
extern int statvfs64(const char* __restrict, struct statvfs64* __restrict) __nonnull((1, 2));
extern int fstatvfs(int, struct statvfs*) __nonnull((2));
extern int fstatvfs64(int, struct statvfs64*) __nonnull((2));

__END_DECLS

#endif /* _SYS_STATVFS_H_ */
