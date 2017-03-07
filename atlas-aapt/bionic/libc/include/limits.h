/*	$OpenBSD: limits.h,v 1.13 2005/12/31 19:29:38 millert Exp $	*/
/*	$NetBSD: limits.h,v 1.7 1994/10/26 00:56:00 cgd Exp $	*/

/*
 * Copyright (c) 1988 The Regents of the University of California.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the University nor the names of its contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE REGENTS AND CONTRIBUTORS ``AS IS'' AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE REGENTS OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS
 * OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 *
 *	@(#)limits.h	5.9 (Berkeley) 4/3/91
 */

#ifndef _LIMITS_H_
#define	_LIMITS_H_

#include <sys/cdefs.h>

#if __XPG_VISIBLE
#define PASS_MAX		128	/* _PASSWORD_LEN from <pwd.h> */

#define NL_ARGMAX		9
#define NL_LANGMAX		14
#define NL_MSGMAX		32767
#define NL_NMAX			1
#define NL_SETMAX		255
#define NL_TEXTMAX		255

#define TMP_MAX                 308915776
#endif /* __XPG_VISIBLE */

#include <sys/limits.h>

#if __POSIX_VISIBLE
#include <sys/syslimits.h>
#endif

/* GLibc compatibility definitions.
   Note that these are defined by GCC's <limits.h>
   only when __GNU_LIBRARY__ is defined, i.e. when
   targetting GLibc. */
#ifndef LONG_LONG_MIN
#define LONG_LONG_MIN  LLONG_MIN
#endif

#ifndef LONG_LONG_MAX
#define LONG_LONG_MAX  LLONG_MAX
#endif

#ifndef ULONG_LONG_MAX
#define ULONG_LONG_MAX  ULLONG_MAX
#endif

/* BSD compatibility definitions. */
#if __BSD_VISIBLE
#define SIZE_T_MAX ULONG_MAX
#endif /* __BSD_VISIBLE */

#define SSIZE_MAX LONG_MAX

#define MB_LEN_MAX 4

#define SEM_VALUE_MAX 0x3fffffff

/* POSIX says these belong in <unistd.h> but BSD has some in <limits.h>. */
#include <bits/posix_limits.h>

#define HOST_NAME_MAX _POSIX_HOST_NAME_MAX
#endif /* !_LIMITS_H_ */
