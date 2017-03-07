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

#ifndef _LIBMEMTRACK_MEMTRACK_H_
#define _LIBMEMTRACK_MEMTRACK_H_

#include <sys/types.h>
#include <stddef.h>
#include <cutils/compiler.h>

#ifdef __cplusplus
extern "C" {
#endif

/**
 * struct memtrack_proc
 *
 * an opaque handle to the memory stats on a process.
 * Created with memtrack_proc_new, destroyed by
 * memtrack_proc_destroy.  Can be reused multiple times with
 * memtrack_proc_get.
 */
struct memtrack_proc;

/**
 * memtrack_init
 *
 * Must be called once before calling any other functions.  After this function
 * is called, everything else is thread-safe.
 *
 * Returns 0 on success, -errno on error.
 */
int memtrack_init(void);

/**
 * memtrack_proc_new
 *
 * Return a new handle to hold process memory stats.
 *
 * Returns NULL on error.
 */
struct memtrack_proc *memtrack_proc_new(void);

/**
 * memtrack_proc_destroy
 *
 * Free all memory associated with a process memory stats handle.
 */
void memtrack_proc_destroy(struct memtrack_proc *p);

/**
 * memtrack_proc_get
 *
 * Fill a process memory stats handle with data about the given pid.  Can be
 * called on a handle that was just allocated with memtrack_proc_new,
 * or on a handle that has been previously passed to memtrack_proc_get
 * to replace the data with new data on the same or another process.  It is
 * expected that the second call on the same handle should not require
 * allocating any new memory.
 *
 * Returns 0 on success, -errno on error.
 */
int memtrack_proc_get(struct memtrack_proc *p, pid_t pid);

/**
 * memtrack_proc_graphics_total
 *
 * Return total amount of memory that has been allocated for use as window
 * buffers.  Does not differentiate between memory that has already been
 * accounted for by reading /proc/pid/smaps and memory that has not been
 * accounted for.
 *
 * Returns non-negative size in bytes on success, -errno on error.
 */
ssize_t memtrack_proc_graphics_total(struct memtrack_proc *p);

/**
 * memtrack_proc_graphics_pss
 *
 * Return total amount of memory that has been allocated for use as window
 * buffers, but has not already been accounted for by reading /proc/pid/smaps.
 * Memory that is shared across processes may already be divided by the
 * number of processes that share it (preferred), or may be charged in full to
 * every process that shares it, depending on the capabilities of the driver.
 *
 * Returns non-negative size in bytes on success, -errno on error.
 */
ssize_t memtrack_proc_graphics_pss(struct memtrack_proc *p);

/**
 * memtrack_proc_gl_total
 *
 * Same as memtrack_proc_graphics_total, but counts GL memory (which
 * should not overlap with graphics memory) instead of graphics memory.
 *
 * Returns non-negative size in bytes on success, -errno on error.
 */
ssize_t memtrack_proc_gl_total(struct memtrack_proc *p);

/**
 * memtrack_proc_gl_pss
 *
 * Same as memtrack_proc_graphics_total, but counts GL memory (which
 * should not overlap with graphics memory) instead of graphics memory.
 *
 * Returns non-negative size in bytes on success, -errno on error.
 */
ssize_t memtrack_proc_gl_pss(struct memtrack_proc *p);

/**
 * memtrack_proc_other_total
 *
 * Same as memtrack_proc_graphics_total, but counts miscellaneous memory
 * not tracked by gl or graphics calls above.
 *
 * Returns non-negative size in bytes on success, -errno on error.
 */
ssize_t memtrack_proc_other_total(struct memtrack_proc *p);

/**
 * memtrack_proc_other_pss
 *
 * Same as memtrack_proc_graphics_total, but counts miscellaneous memory
 * not tracked by gl or graphics calls above.
 *
 * Returns non-negative size in bytes on success, -errno on error.
 */
ssize_t memtrack_proc_other_pss(struct memtrack_proc *p);

#ifdef __cplusplus
}
#endif

#endif
