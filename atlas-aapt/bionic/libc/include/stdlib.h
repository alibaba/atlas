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

#ifndef _STDLIB_H
#define _STDLIB_H

#include <sys/cdefs.h>
#include <xlocale.h>

#include <alloca.h>
#include <malloc.h>
#include <stddef.h>

__BEGIN_DECLS

#define EXIT_FAILURE 1
#define EXIT_SUCCESS 0

extern __noreturn void abort(void);
extern __noreturn void exit(int);
extern __noreturn void _Exit(int);
extern int atexit(void (*)(void));

#if __ISO_C_VISIBLE >= 2011 || __cplusplus >= 201103L
int at_quick_exit(void (*)(void));
void quick_exit(int) __noreturn;
#endif

extern char* getenv(const char*);
extern int putenv(char*);
extern int setenv(const char*, const char*, int);
extern int unsetenv(const char*);
extern int clearenv(void);

extern char* mkdtemp(char*);
extern char* mktemp(char*) __attribute__((deprecated("mktemp is unsafe, use mkstemp or tmpfile instead")));

extern int mkostemp64(char*, int);
extern int mkostemp(char*, int);
extern int mkostemps64(char*, int, int);
extern int mkostemps(char*, int, int);
extern int mkstemp64(char*);
extern int mkstemp(char*);
extern int mkstemps64(char*, int);
extern int mkstemps(char*, int);

extern long strtol(const char *, char **, int);
extern long long strtoll(const char *, char **, int);
extern unsigned long strtoul(const char *, char **, int);
extern unsigned long long strtoull(const char *, char **, int);

extern int posix_memalign(void **memptr, size_t alignment, size_t size);

extern double atof(const char*) __INTRODUCED_IN(21);

extern double strtod(const char*, char**) __LIBC_ABI_PUBLIC__;
extern float strtof(const char*, char**) __LIBC_ABI_PUBLIC__ __INTRODUCED_IN(21);
extern long double strtold(const char*, char**) __LIBC_ABI_PUBLIC__;

extern long double strtold_l(const char *, char **, locale_t) __LIBC_ABI_PUBLIC__;
extern long long strtoll_l(const char *, char **, int, locale_t) __LIBC_ABI_PUBLIC__;
extern unsigned long long strtoull_l(const char *, char **, int, locale_t) __LIBC_ABI_PUBLIC__;

extern int atoi(const char*) __purefunc;
extern long atol(const char*) __purefunc;
extern long long atoll(const char*) __purefunc;

extern int abs(int) __pure2 __INTRODUCED_IN(21);
extern long labs(long) __pure2 __INTRODUCED_IN(21);
extern long long llabs(long long) __pure2 __INTRODUCED_IN(21);

extern char * realpath(const char *path, char *resolved);
extern int system(const char *string);

extern void * bsearch(const void *key, const void *base0,
	size_t nmemb, size_t size,
	int (*compar)(const void *, const void *));

extern void qsort(void *, size_t, size_t, int (*)(const void *, const void *));

uint32_t arc4random(void);
uint32_t arc4random_uniform(uint32_t);
void arc4random_buf(void*, size_t);

#define RAND_MAX 0x7fffffff

int rand(void) __INTRODUCED_IN(21);
int rand_r(unsigned int*);
void srand(unsigned int) __INTRODUCED_IN(21);

double drand48(void);
double erand48(unsigned short[3]);
long jrand48(unsigned short[3]);
void lcong48(unsigned short[7]);
long lrand48(void);
long mrand48(void);
long nrand48(unsigned short[3]);
unsigned short* seed48(unsigned short[3]);
void srand48(long);

char* initstate(unsigned int, char*, size_t);
long random(void) __INTRODUCED_IN(21);
char* setstate(char*);
void srandom(unsigned int) __INTRODUCED_IN(21);

int getpt(void);
int grantpt(int) __INTRODUCED_IN(21);
int posix_openpt(int);
char* ptsname(int);
int ptsname_r(int, char*, size_t);
int unlockpt(int);

typedef struct {
    int  quot;
    int  rem;
} div_t;

extern div_t   div(int, int) __pure2;

typedef struct {
    long int  quot;
    long int  rem;
} ldiv_t;

extern ldiv_t   ldiv(long, long) __pure2;

typedef struct {
    long long int  quot;
    long long int  rem;
} lldiv_t;

extern lldiv_t   lldiv(long long, long long) __pure2;

/* BSD compatibility. */
extern const char* getprogname(void);
extern void setprogname(const char*);

/* make STLPort happy */
extern int      mblen(const char *, size_t);
extern size_t   mbstowcs(wchar_t *, const char *, size_t);
extern int      mbtowc(wchar_t *, const char *, size_t);

/* Likewise, make libstdc++-v3 happy.  */
extern int	wctomb(char *, wchar_t);
extern size_t	wcstombs(char *, const wchar_t *, size_t);

extern size_t __ctype_get_mb_cur_max(void);
#define MB_CUR_MAX __ctype_get_mb_cur_max()

#if __ANDROID_API__ < 21
#include <android/legacy_stdlib_inlines.h>
#endif

#if defined(__BIONIC_FORTIFY)

extern char* __realpath_real(const char*, char*) __RENAME(realpath);
__errordecl(__realpath_size_error, "realpath output parameter must be NULL or a >= PATH_MAX bytes buffer");

#if !defined(__clang__)
__BIONIC_FORTIFY_INLINE
char* realpath(const char* path, char* resolved) {
    size_t bos = __bos(resolved);

    /* PATH_MAX is unavailable without polluting the namespace, but it's always 4096 on Linux */
    if (bos != __BIONIC_FORTIFY_UNKNOWN_SIZE && bos < 4096) {
        __realpath_size_error();
    }

    return __realpath_real(path, resolved);
}
#endif

#endif /* defined(__BIONIC_FORTIFY) */

__END_DECLS

#endif /* _STDLIB_H */
