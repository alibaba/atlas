/*-
 * Copyright (c) 1997 Niklas Hallqvist.  All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 * THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

#ifndef _SYS_ENDIAN_H_
#define _SYS_ENDIAN_H_

#include <sys/cdefs.h>

#include <stdint.h>

#define _LITTLE_ENDIAN	1234
#define _BIG_ENDIAN	4321
#define _PDP_ENDIAN	3412
#define _BYTE_ORDER _LITTLE_ENDIAN
#define __LITTLE_ENDIAN_BITFIELD

#ifndef __LITTLE_ENDIAN
#define __LITTLE_ENDIAN _LITTLE_ENDIAN
#endif
#ifndef __BIG_ENDIAN
#define __BIG_ENDIAN _BIG_ENDIAN
#endif
#define __BYTE_ORDER _BYTE_ORDER

#define __swap16 __builtin_bswap16
#define __swap32 __builtin_bswap32
#define __swap64 __builtin_bswap64

/* glibc compatibility. */
__BEGIN_DECLS
uint32_t htonl(uint32_t) __pure2;
uint16_t htons(uint16_t) __pure2;
uint32_t ntohl(uint32_t) __pure2;
uint16_t ntohs(uint16_t) __pure2;
__END_DECLS

#define htonl(x) __swap32(x)
#define htons(x) __swap16(x)
#define ntohl(x) __swap32(x)
#define ntohs(x) __swap16(x)

/* Bionic additions */
#define htonq(x) __swap64(x)
#define ntohq(x) __swap64(x)

#if __BSD_VISIBLE
#define LITTLE_ENDIAN _LITTLE_ENDIAN
#define BIG_ENDIAN _BIG_ENDIAN
#define PDP_ENDIAN _PDP_ENDIAN
#define BYTE_ORDER _BYTE_ORDER

#define	NTOHL(x) (x) = ntohl((u_int32_t)(x))
#define	NTOHS(x) (x) = ntohs((u_int16_t)(x))
#define	HTONL(x) (x) = htonl((u_int32_t)(x))
#define	HTONS(x) (x) = htons((u_int16_t)(x))

#define htobe16 __swap16
#define htobe32 __swap32
#define htobe64 __swap64
#define betoh16 __swap16
#define betoh32 __swap32
#define betoh64 __swap64

#define htole16(x) (x)
#define htole32(x) (x)
#define htole64(x) (x)
#define letoh16(x) (x)
#define letoh32(x) (x)
#define letoh64(x) (x)

/*
 * glibc-compatible beXXtoh/leXXtoh synonyms for htobeXX/htoleXX.
 * The BSDs export both sets of names, bionic historically only
 * exported the ones above (or on the rhs here), and glibc only
 * exports these names (on the lhs).
 */
#define be16toh(x) htobe16(x)
#define be32toh(x) htobe32(x)
#define be64toh(x) htobe64(x)
#define le16toh(x) htole16(x)
#define le32toh(x) htole32(x)
#define le64toh(x) htole64(x)
#endif /* __BSD_VISIBLE */

#endif /* _SYS_ENDIAN_H_ */
