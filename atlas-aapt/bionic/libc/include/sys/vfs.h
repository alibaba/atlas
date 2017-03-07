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

#ifndef _SYS_VFS_H_
#define _SYS_VFS_H_

#include <stdint.h>
#include <sys/cdefs.h>
#include <sys/types.h>

__BEGIN_DECLS

/* The kernel's __kernel_fsid_t has a 'val' member but glibc uses '__val'. */
typedef struct { int __val[2]; } __fsid_t;
typedef __fsid_t fsid_t;

#if defined(__aarch64__) || defined(__x86_64__)
#define __STATFS64_BODY \
  uint64_t f_type; \
  uint64_t f_bsize; \
  uint64_t f_blocks; \
  uint64_t f_bfree; \
  uint64_t f_bavail; \
  uint64_t f_files; \
  uint64_t f_ffree; \
  fsid_t f_fsid; \
  uint64_t f_namelen; \
  uint64_t f_frsize; \
  uint64_t f_flags; \
  uint64_t f_spare[4]; \

#elif defined(__mips__) && defined(__LP64__)
/* 64-bit MIPS. */
#define __STATFS64_BODY \
  uint64_t f_type; \
  uint64_t f_bsize; \
  uint64_t f_frsize; /* Fragment size - unsupported. */ \
  uint64_t f_blocks; \
  uint64_t f_bfree; \
  uint64_t f_files; \
  uint64_t f_ffree; \
  uint64_t f_bavail; \
  fsid_t f_fsid; \
  uint64_t f_namelen; \
  uint64_t f_flags; \
  uint64_t f_spare[5]; \

#elif defined(__mips__)
/* 32-bit MIPS (corresponds to the kernel's statfs64 type). */
#define __STATFS64_BODY \
  uint32_t f_type; \
  uint32_t f_bsize; \
  uint32_t f_frsize; \
  uint32_t __pad; \
  uint64_t f_blocks; \
  uint64_t f_bfree; \
  uint64_t f_files; \
  uint64_t f_ffree; \
  uint64_t f_bavail; \
  fsid_t f_fsid; \
  uint32_t f_namelen; \
  uint32_t f_flags; \
  uint32_t f_spare[5]; \

#else
/* 32-bit ARM or x86 (corresponds to the kernel's statfs64 type). */
#define __STATFS64_BODY \
  uint32_t f_type; \
  uint32_t f_bsize; \
  uint64_t f_blocks; \
  uint64_t f_bfree; \
  uint64_t f_bavail; \
  uint64_t f_files; \
  uint64_t f_ffree; \
  fsid_t f_fsid; \
  uint32_t f_namelen; \
  uint32_t f_frsize; \
  uint32_t f_flags; \
  uint32_t f_spare[4]; \

#endif

struct statfs { __STATFS64_BODY };
struct statfs64 { __STATFS64_BODY };

#undef __STATFS64_BODY

/* Declare that we have the f_namelen, f_frsize, and f_flags fields. */
#define _STATFS_F_NAMELEN
#define _STATFS_F_FRSIZE
#define _STATFS_F_FLAGS

/* Pull in the kernel magic numbers. */
#include <linux/magic.h>
/* Add in ones that we had historically that aren't in the uapi header. */
#define BEFS_SUPER_MAGIC      0x42465331
#define BFS_MAGIC             0x1BADFACE
#define CIFS_MAGIC_NUMBER     0xFF534D42
#define COH_SUPER_MAGIC       0x012FF7B7
#define DEVFS_SUPER_MAGIC     0x1373
#define EXT_SUPER_MAGIC       0x137D
#define EXT2_OLD_SUPER_MAGIC  0xEF51
#define HFS_SUPER_MAGIC       0x4244
#define JFS_SUPER_MAGIC       0x3153464a
#define NTFS_SB_MAGIC         0x5346544e
#define ROMFS_MAGIC           0x7275
#define SYSV2_SUPER_MAGIC     0x012FF7B6
#define SYSV4_SUPER_MAGIC     0x012FF7B5
#define UDF_SUPER_MAGIC       0x15013346
#define UFS_MAGIC             0x00011954
#define VXFS_SUPER_MAGIC      0xa501FCF5
#define XENIX_SUPER_MAGIC     0x012FF7B4
#define XFS_SUPER_MAGIC       0x58465342

extern int statfs(const char*, struct statfs*) __nonnull((1, 2));
extern int statfs64(const char*, struct statfs64*) __nonnull((1, 2));
extern int fstatfs(int, struct statfs*) __nonnull((2));
extern int fstatfs64(int, struct statfs64*) __nonnull((2));

__END_DECLS

#endif /* _SYS_VFS_H_ */
