/*	$OpenBSD: ieee.h,v 1.4 2011/11/08 17:06:51 deraadt Exp $	*/
/*	$NetBSD: ieee.h,v 1.2 2001/02/21 17:43:50 bjh21 Exp $	*/

/*
 * Copyright (c) 1992, 1993
 *	The Regents of the University of California.  All rights reserved.
 *
 * This software was developed by the Computer Systems Engineering group
 * at Lawrence Berkeley Laboratory under DARPA contract BG 91-66 and
 * contributed to Berkeley.
 *
 * All advertising materials mentioning features or use of this software
 * must display the following acknowledgement:
 *	This product includes software developed by the University of
 *	California, Lawrence Berkeley Laboratory.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. All advertising materials mentioning features or use of this software
 *    must display the following acknowledgement:
 *	This product includes software developed by the University of
 *	California, Berkeley and its contributors.
 * 4. Neither the name of the University nor the names of its contributors
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
 *	@(#)ieee.h	8.1 (Berkeley) 6/11/93
 */

#ifndef _MACHINE_IEEE_H_
#define _MACHINE_IEEE_H_

#include <sys/types.h>

__BEGIN_DECLS

#define SNG_EXPBITS	8
#define SNG_FRACBITS	23

#define SNG_EXP_INFNAN	255
#define SNG_EXP_BIAS	127

struct ieee_single {
  unsigned sng_frac:23;
  unsigned sng_exp:8;
  unsigned sng_sign:1;
};

#define DBL_EXPBITS	11
#define DBL_FRACHBITS	20
#define DBL_FRACLBITS	32
#define DBL_FRACBITS	52

#define DBL_EXP_INFNAN	2047
#define DBL_EXP_BIAS	1023

struct ieee_double {
  unsigned dbl_fracl;
  unsigned dbl_frach:20;
  unsigned dbl_exp:11;
  unsigned dbl_sign:1;
};

#if __LP64__

/* 64-bit Android uses ld128 long doubles. */

#define EXT_EXPBITS	15
#define EXT_FRACHBITS	16
#define EXT_FRACHMBITS	32
#define EXT_FRACLMBITS	32
#define EXT_FRACLBITS	32
#define EXT_FRACBITS	112

#define EXT_EXP_INFNAN	32767
#define EXT_EXP_BIAS	16383

#define EXT_IMPLICIT_NBIT

#define EXT_TO_ARRAY32(p, a) do { \
  (a)[0] = (uint32_t)(p)->ext_fracl; \
  (a)[1] = (uint32_t)(p)->ext_fraclm; \
  (a)[2] = (uint32_t)(p)->ext_frachm; \
  (a)[3] = (uint32_t)(p)->ext_frach; \
} while(0)

struct ieee_ext {
  unsigned ext_fracl;
  unsigned ext_fraclm;
  unsigned ext_frachm;
  unsigned ext_frach:16;
  unsigned ext_exp:15;
  unsigned ext_sign:1;
};

#endif

__END_DECLS

#endif /* _MACHINE_IEEE_H_ */
