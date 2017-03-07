/*	$OpenBSD: stdio.h,v 1.35 2006/01/13 18:10:09 miod Exp $	*/
/*	$NetBSD: stdio.h,v 1.18 1996/04/25 18:29:21 jtc Exp $	*/

/*-
 * Copyright (c) 1990 The Regents of the University of California.
 * All rights reserved.
 *
 * This code is derived from software contributed to Berkeley by
 * Chris Torek.
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
 *	@(#)stdio.h	5.17 (Berkeley) 6/3/91
 */

#ifndef	_STDIO_H_
#define	_STDIO_H_

#include <sys/cdefs.h>
#include <sys/types.h>

#include <stdarg.h>
#include <stddef.h>

#define __need_NULL
#include <stddef.h>

__BEGIN_DECLS

typedef off_t fpos_t;
typedef off64_t fpos64_t;

struct __sFILE;
typedef struct __sFILE FILE;

extern FILE* stdin;
extern FILE* stdout;
extern FILE* stderr;
/* C99 and earlier plus current C++ standards say these must be macros. */
#define stdin stdin
#define stdout stdout
#define stderr stderr

/*
 * The following three definitions are for ANSI C, which took them
 * from System V, which brilliantly took internal interface macros and
 * made them official arguments to setvbuf(), without renaming them.
 * Hence, these ugly _IOxxx names are *supposed* to appear in user code.
 *
 * Although numbered as their counterparts above, the implementation
 * does not rely on this.
 */
#define	_IOFBF	0		/* setvbuf should set fully buffered */
#define	_IOLBF	1		/* setvbuf should set line buffered */
#define	_IONBF	2		/* setvbuf should set unbuffered */

#define	BUFSIZ	1024		/* size of buffer used by setbuf */
#define	EOF	(-1)

/*
 * FOPEN_MAX is a minimum maximum, and is the number of streams that
 * stdio can provide without attempting to allocate further resources
 * (which could fail).  Do not use this for anything.
 */

#define	FOPEN_MAX	20	/* must be <= OPEN_MAX <sys/syslimits.h> */
#define	FILENAME_MAX	1024	/* must be <= PATH_MAX <sys/syslimits.h> */

/* System V/ANSI C; this is the wrong way to do this, do *not* use these. */
#if __BSD_VISIBLE || __XPG_VISIBLE
#define	P_tmpdir	"/tmp/"
#endif
#define	L_tmpnam	1024	/* XXX must be == PATH_MAX */
#define	TMP_MAX		308915776

#define SEEK_SET 0
#define SEEK_CUR 1
#define SEEK_END 2

/*
 * Functions defined in ANSI C standard.
 */
void	 clearerr(FILE *);
int	 fclose(FILE *);
int	 feof(FILE *);
int	 ferror(FILE *);
int	 fflush(FILE *);
int	 fgetc(FILE *);
char	*fgets(char * __restrict, int, FILE * __restrict);
int	 fprintf(FILE * __restrict , const char * __restrict, ...)
		__printflike(2, 3);
int	 fputc(int, FILE *);
int	 fputs(const char * __restrict, FILE * __restrict);
size_t	 fread(void * __restrict, size_t, size_t, FILE * __restrict);
int	 fscanf(FILE * __restrict, const char * __restrict, ...)
		__scanflike(2, 3);
size_t	 fwrite(const void * __restrict, size_t, size_t, FILE * __restrict);
int	 getc(FILE *);
int	 getchar(void);
ssize_t	 getdelim(char ** __restrict, size_t * __restrict, int,
	    FILE * __restrict);
ssize_t	 getline(char ** __restrict, size_t * __restrict, FILE * __restrict);

void	 perror(const char *);
int	 printf(const char * __restrict, ...)
		__printflike(1, 2);
int	 putc(int, FILE *);
int	 putchar(int);
int	 puts(const char *);
int	 remove(const char *);
void	 rewind(FILE *);
int	 scanf(const char * __restrict, ...)
		__scanflike(1, 2);
void	 setbuf(FILE * __restrict, char * __restrict);
int	 setvbuf(FILE * __restrict, char * __restrict, int, size_t);
int	 sscanf(const char * __restrict, const char * __restrict, ...)
		__scanflike(2, 3);
int	 ungetc(int, FILE *);
int	 vfprintf(FILE * __restrict, const char * __restrict, __va_list)
		__printflike(2, 0);
int	 vprintf(const char * __restrict, __va_list)
		__printflike(1, 0);

int dprintf(int, const char * __restrict, ...) __printflike(2, 3);
int vdprintf(int, const char * __restrict, __va_list) __printflike(2, 0);

#ifndef __AUDIT__
#if !defined(__STDC_VERSION__) || __STDC_VERSION__ < 201112L
char* gets(char*) __attribute__((deprecated("gets is unsafe, use fgets instead")));
#endif
int sprintf(char* __restrict, const char* __restrict, ...)
    __printflike(2, 3) __warnattr("sprintf is often misused; please use snprintf");
int vsprintf(char* __restrict, const char* __restrict, __va_list)
    __printflike(2, 0) __warnattr("vsprintf is often misused; please use vsnprintf");
char* tmpnam(char*) __attribute__((deprecated("tmpnam is unsafe, use mkstemp or tmpfile instead")));
#if __XPG_VISIBLE
char* tempnam(const char*, const char*)
    __attribute__((deprecated("tempnam is unsafe, use mkstemp or tmpfile instead")));
#endif
#endif

int rename(const char*, const char*);
int renameat(int, const char*, int, const char*);

int fseek(FILE*, long, int);
long ftell(FILE*);

#if defined(__USE_FILE_OFFSET64)
int fgetpos(FILE*, fpos_t*) __RENAME(fgetpos64);
int fsetpos(FILE*, const fpos_t*) __RENAME(fsetpos64);
int fseeko(FILE*, off_t, int) __RENAME(fseeko64);
off_t ftello(FILE*) __RENAME(ftello64);
#  if defined(__USE_BSD)
FILE* funopen(const void*,
              int (*)(void*, char*, int),
              int (*)(void*, const char*, int),
              fpos_t (*)(void*, fpos_t, int),
              int (*)(void*)) __RENAME(funopen64);
#  endif
#else
int fgetpos(FILE*, fpos_t*);
int fsetpos(FILE*, const fpos_t*);
int fseeko(FILE*, off_t, int);
off_t ftello(FILE*);
#  if defined(__USE_BSD)
FILE* funopen(const void*,
              int (*)(void*, char*, int),
              int (*)(void*, const char*, int),
              fpos_t (*)(void*, fpos_t, int),
              int (*)(void*));
#  endif
#endif
int fgetpos64(FILE*, fpos64_t*);
int fsetpos64(FILE*, const fpos64_t*);
int fseeko64(FILE*, off64_t, int);
off64_t ftello64(FILE*);
#if defined(__USE_BSD)
FILE* funopen64(const void*,
                int (*)(void*, char*, int),
                int (*)(void*, const char*, int),
                fpos64_t (*)(void*, fpos64_t, int),
                int (*)(void*));
#endif

FILE* fopen(const char* __restrict, const char* __restrict);
FILE* fopen64(const char* __restrict, const char* __restrict);
FILE* freopen(const char* __restrict, const char* __restrict, FILE* __restrict);
FILE* freopen64(const char* __restrict, const char* __restrict, FILE* __restrict);
FILE* tmpfile(void);
FILE* tmpfile64(void);

#if __ISO_C_VISIBLE >= 1999 || __BSD_VISIBLE
int	 snprintf(char * __restrict, size_t, const char * __restrict, ...)
		__printflike(3, 4);
int	 vfscanf(FILE * __restrict, const char * __restrict, __va_list)
		__scanflike(2, 0);
int	 vscanf(const char *, __va_list)
		__scanflike(1, 0);
int	 vsnprintf(char * __restrict, size_t, const char * __restrict, __va_list)
		__printflike(3, 0);
int	 vsscanf(const char * __restrict, const char * __restrict, __va_list)
		__scanflike(2, 0);
#endif /* __ISO_C_VISIBLE >= 1999 || __BSD_VISIBLE */

/*
 * Functions defined in POSIX 1003.1.
 */
#if __BSD_VISIBLE || __POSIX_VISIBLE || __XPG_VISIBLE
#define	L_ctermid	1024	/* size for ctermid(); PATH_MAX */

FILE	*fdopen(int, const char *);
int	 fileno(FILE *);

#if (__POSIX_VISIBLE >= 199209)
int	 pclose(FILE *);
FILE	*popen(const char *, const char *);
#endif

#if __POSIX_VISIBLE >= 199506
void	 flockfile(FILE *);
int	 ftrylockfile(FILE *);
void	 funlockfile(FILE *);

/*
 * These are normally used through macros as defined below, but POSIX
 * requires functions as well.
 */
int	 getc_unlocked(FILE *);
int	 getchar_unlocked(void);
int	 putc_unlocked(int, FILE *);
int	 putchar_unlocked(int);
#endif /* __POSIX_VISIBLE >= 199506 */

#if __POSIX_VISIBLE >= 200809
FILE* fmemopen(void*, size_t, const char*);
FILE* open_memstream(char**, size_t*);
#endif /* __POSIX_VISIBLE >= 200809 */

#endif /* __BSD_VISIBLE || __POSIX_VISIBLE || __XPG_VISIBLE */

/*
 * Routines that are purely local.
 */
#if __BSD_VISIBLE
int	 asprintf(char ** __restrict, const char * __restrict, ...)
		__printflike(2, 3);
char	*fgetln(FILE * __restrict, size_t * __restrict);
int	 fpurge(FILE *);
void	 setbuffer(FILE *, char *, int);
int	 setlinebuf(FILE *);
int	 vasprintf(char ** __restrict, const char * __restrict,
    __va_list)
		__printflike(2, 0);

void clearerr_unlocked(FILE*);
int feof_unlocked(FILE*);
int ferror_unlocked(FILE*);
int fileno_unlocked(FILE*);

#define fropen(cookie, fn) funopen(cookie, fn, 0, 0, 0)
#define fwopen(cookie, fn) funopen(cookie, 0, fn, 0, 0)
#endif /* __BSD_VISIBLE */

extern char* __fgets_chk(char*, int, FILE*, size_t);
extern char* __fgets_real(char*, int, FILE*) __RENAME(fgets);
__errordecl(__fgets_too_big_error, "fgets called with size bigger than buffer");
__errordecl(__fgets_too_small_error, "fgets called with size less than zero");

extern size_t __fread_chk(void * __restrict, size_t, size_t, FILE * __restrict, size_t);
extern size_t __fread_real(void * __restrict, size_t, size_t, FILE * __restrict) __RENAME(fread);
__errordecl(__fread_too_big_error, "fread called with size * count bigger than buffer");
__errordecl(__fread_overflow, "fread called with overflowing size * count");

extern size_t __fwrite_chk(const void * __restrict, size_t, size_t, FILE * __restrict, size_t);
extern size_t __fwrite_real(const void * __restrict, size_t, size_t, FILE * __restrict) __RENAME(fwrite);
__errordecl(__fwrite_too_big_error, "fwrite called with size * count bigger than buffer");
__errordecl(__fwrite_overflow, "fwrite called with overflowing size * count");

#if defined(__BIONIC_FORTIFY)

__BIONIC_FORTIFY_INLINE
__printflike(3, 0)
int vsnprintf(char *dest, size_t size, const char *format, __va_list ap)
{
    return __builtin___vsnprintf_chk(dest, size, 0, __bos(dest), format, ap);
}

__BIONIC_FORTIFY_INLINE
__printflike(2, 0)
int vsprintf(char *dest, const char *format, __va_list ap)
{
    return __builtin___vsprintf_chk(dest, 0, __bos(dest), format, ap);
}

#if defined(__clang__)
  #if !defined(snprintf)
    #define __wrap_snprintf(dest, size, ...) __builtin___snprintf_chk(dest, size, 0, __bos(dest), __VA_ARGS__)
    #define snprintf(...) __wrap_snprintf(__VA_ARGS__)
  #endif
#else
__BIONIC_FORTIFY_INLINE
__printflike(3, 4)
int snprintf(char *dest, size_t size, const char *format, ...)
{
    return __builtin___snprintf_chk(dest, size, 0,
        __bos(dest), format, __builtin_va_arg_pack());
}
#endif

#if defined(__clang__)
  #if !defined(sprintf)
    #define __wrap_sprintf(dest, ...) __builtin___sprintf_chk(dest, 0, __bos(dest), __VA_ARGS__)
    #define sprintf(...) __wrap_sprintf(__VA_ARGS__)
  #endif
#else
__BIONIC_FORTIFY_INLINE
__printflike(2, 3)
int sprintf(char *dest, const char *format, ...)
{
    return __builtin___sprintf_chk(dest, 0,
        __bos(dest), format, __builtin_va_arg_pack());
}
#endif

__BIONIC_FORTIFY_INLINE
size_t fread(void * __restrict buf, size_t size, size_t count, FILE * __restrict stream) {
    size_t bos = __bos0(buf);

#if !defined(__clang__)
    if (bos == __BIONIC_FORTIFY_UNKNOWN_SIZE) {
        return __fread_real(buf, size, count, stream);
    }

    if (__builtin_constant_p(size) && __builtin_constant_p(count)) {
        size_t total;
        if (__size_mul_overflow(size, count, &total)) {
            __fread_overflow();
        }

        if (total > bos) {
            __fread_too_big_error();
        }

        return __fread_real(buf, size, count, stream);
    }
#endif

    return __fread_chk(buf, size, count, stream, bos);
}

__BIONIC_FORTIFY_INLINE
size_t fwrite(const void * __restrict buf, size_t size, size_t count, FILE * __restrict stream) {
    size_t bos = __bos0(buf);

#if !defined(__clang__)
    if (bos == __BIONIC_FORTIFY_UNKNOWN_SIZE) {
        return __fwrite_real(buf, size, count, stream);
    }

    if (__builtin_constant_p(size) && __builtin_constant_p(count)) {
        size_t total;
        if (__size_mul_overflow(size, count, &total)) {
            __fwrite_overflow();
        }

        if (total > bos) {
            __fwrite_too_big_error();
        }

        return __fwrite_real(buf, size, count, stream);
    }
#endif

    return __fwrite_chk(buf, size, count, stream, bos);
}

#if !defined(__clang__)

__BIONIC_FORTIFY_INLINE
char *fgets(char* dest, int size, FILE* stream) {
    size_t bos = __bos(dest);

    // Compiler can prove, at compile time, that the passed in size
    // is always negative. Force a compiler error.
    if (__builtin_constant_p(size) && (size < 0)) {
        __fgets_too_small_error();
    }

    // Compiler doesn't know destination size. Don't call __fgets_chk
    if (bos == __BIONIC_FORTIFY_UNKNOWN_SIZE) {
        return __fgets_real(dest, size, stream);
    }

    // Compiler can prove, at compile time, that the passed in size
    // is always <= the actual object size. Don't call __fgets_chk
    if (__builtin_constant_p(size) && (size <= (int) bos)) {
        return __fgets_real(dest, size, stream);
    }

    // Compiler can prove, at compile time, that the passed in size
    // is always > the actual object size. Force a compiler error.
    if (__builtin_constant_p(size) && (size > (int) bos)) {
        __fgets_too_big_error();
    }

    return __fgets_chk(dest, size, stream, bos);
}

#endif /* !defined(__clang__) */

#endif /* defined(__BIONIC_FORTIFY) */

__END_DECLS

#endif /* _STDIO_H_ */
