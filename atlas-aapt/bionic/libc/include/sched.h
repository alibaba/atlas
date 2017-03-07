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
#ifndef _SCHED_H_
#define _SCHED_H_

#include <bits/timespec.h>
#include <linux/sched.h>
#include <sys/cdefs.h>

__BEGIN_DECLS

/* This name is used by glibc, but not by the kernel. */
#define SCHED_OTHER SCHED_NORMAL

struct sched_param {
  int sched_priority;
};

extern int sched_setscheduler(pid_t, int, const struct sched_param*);
extern int sched_getscheduler(pid_t);
extern int sched_yield(void);
extern int sched_get_priority_max(int);
extern int sched_get_priority_min(int);
extern int sched_setparam(pid_t, const struct sched_param*);
extern int sched_getparam(pid_t, struct sched_param*);
extern int sched_rr_get_interval(pid_t, struct timespec*);

#if defined(__USE_GNU)

extern int clone(int (*)(void*), void*, int, void*, ...);
extern int unshare(int);
extern int sched_getcpu(void);
extern int setns(int, int);

#ifdef __LP64__
#define CPU_SETSIZE 1024
#else
#define CPU_SETSIZE 32
#endif

#define __CPU_BITTYPE  unsigned long int  /* mandated by the kernel  */
#define __CPU_BITS     (8 * sizeof(__CPU_BITTYPE))
#define __CPU_ELT(x)   ((x) / __CPU_BITS)
#define __CPU_MASK(x)  ((__CPU_BITTYPE)1 << ((x) & (__CPU_BITS - 1)))

typedef struct {
  __CPU_BITTYPE  __bits[ CPU_SETSIZE / __CPU_BITS ];
} cpu_set_t;

extern int sched_setaffinity(pid_t pid, size_t setsize, const cpu_set_t* set);

extern int sched_getaffinity(pid_t pid, size_t setsize, cpu_set_t* set);

#define CPU_ZERO(set)          CPU_ZERO_S(sizeof(cpu_set_t), set)
#define CPU_SET(cpu, set)      CPU_SET_S(cpu, sizeof(cpu_set_t), set)
#define CPU_CLR(cpu, set)      CPU_CLR_S(cpu, sizeof(cpu_set_t), set)
#define CPU_ISSET(cpu, set)    CPU_ISSET_S(cpu, sizeof(cpu_set_t), set)
#define CPU_COUNT(set)         CPU_COUNT_S(sizeof(cpu_set_t), set)
#define CPU_EQUAL(set1, set2)  CPU_EQUAL_S(sizeof(cpu_set_t), set1, set2)

#define CPU_AND(dst, set1, set2)  __CPU_OP(dst, set1, set2, &)
#define CPU_OR(dst, set1, set2)   __CPU_OP(dst, set1, set2, |)
#define CPU_XOR(dst, set1, set2)  __CPU_OP(dst, set1, set2, ^)

#define __CPU_OP(dst, set1, set2, op)  __CPU_OP_S(sizeof(cpu_set_t), dst, set1, set2, op)

/* Support for dynamically-allocated cpu_set_t */

#define CPU_ALLOC_SIZE(count) \
  __CPU_ELT((count) + (__CPU_BITS - 1)) * sizeof(__CPU_BITTYPE)

#define CPU_ALLOC(count)  __sched_cpualloc((count))
#define CPU_FREE(set)     __sched_cpufree((set))

extern cpu_set_t* __sched_cpualloc(size_t count);
extern void       __sched_cpufree(cpu_set_t* set);

#define CPU_ZERO_S(setsize, set)  __builtin_memset(set, 0, setsize)

#define CPU_SET_S(cpu, setsize, set) \
  do { \
    size_t __cpu = (cpu); \
    if (__cpu < 8 * (setsize)) \
      (set)->__bits[__CPU_ELT(__cpu)] |= __CPU_MASK(__cpu); \
  } while (0)

#define CPU_CLR_S(cpu, setsize, set) \
  do { \
    size_t __cpu = (cpu); \
    if (__cpu < 8 * (setsize)) \
      (set)->__bits[__CPU_ELT(__cpu)] &= ~__CPU_MASK(__cpu); \
  } while (0)

#define CPU_ISSET_S(cpu, setsize, set) \
  (__extension__ ({ \
    size_t __cpu = (cpu); \
    (__cpu < 8 * (setsize)) \
      ? ((set)->__bits[__CPU_ELT(__cpu)] & __CPU_MASK(__cpu)) != 0 \
      : 0; \
  }))

#define CPU_EQUAL_S(setsize, set1, set2)  (__builtin_memcmp(set1, set2, setsize) == 0)

#define CPU_AND_S(setsize, dst, set1, set2)  __CPU_OP_S(setsize, dst, set1, set2, &)
#define CPU_OR_S(setsize, dst, set1, set2)   __CPU_OP_S(setsize, dst, set1, set2, |)
#define CPU_XOR_S(setsize, dst, set1, set2)  __CPU_OP_S(setsize, dst, set1, set2, ^)

#define __CPU_OP_S(setsize, dstset, srcset1, srcset2, op) \
  do { \
    cpu_set_t* __dst = (dstset); \
    const __CPU_BITTYPE* __src1 = (srcset1)->__bits; \
    const __CPU_BITTYPE* __src2 = (srcset2)->__bits; \
    size_t __nn = 0, __nn_max = (setsize)/sizeof(__CPU_BITTYPE); \
    for (; __nn < __nn_max; __nn++) \
      (__dst)->__bits[__nn] = __src1[__nn] op __src2[__nn]; \
  } while (0)

#define CPU_COUNT_S(setsize, set)  __sched_cpucount((setsize), (set))

extern int __sched_cpucount(size_t setsize, cpu_set_t* set);

#endif /* __USE_GNU */

__END_DECLS

#endif /* _SCHED_H_ */
