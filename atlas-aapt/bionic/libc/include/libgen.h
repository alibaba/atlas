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

#ifndef _LIBGEN_H
#define _LIBGEN_H

#include <sys/cdefs.h>
#include <sys/types.h>


__BEGIN_DECLS

/*
 * Including <string.h> will get you the GNU basename, unless <libgen.h> is
 * included, either before or after including <string.h>.
 *
 * Note that this has the wrong argument cv-qualifiers, but doesn't modify its
 * input and uses thread-local storage for the result if necessary.
 */
extern char* __posix_basename(const char*) __RENAME(basename);

#define basename __posix_basename

/* This has the wrong argument cv-qualifiers, but doesn't modify its input and uses thread-local storage for the result if necessary. */
extern char* dirname(const char*);

#if !defined(__LP64__)
/* These non-standard functions are not needed on Android; basename and dirname use thread-local storage. */
extern int dirname_r(const char*, char*, size_t);
extern int basename_r(const char*, char*, size_t);
#endif

__END_DECLS

#endif /* _LIBGEN_H */
