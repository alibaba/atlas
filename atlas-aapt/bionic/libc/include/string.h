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

#ifndef _STRING_H
#define _STRING_H

#include <sys/cdefs.h>
#include <stddef.h>
#include <xlocale.h>

__BEGIN_DECLS

#if defined(__USE_BSD)
#include <strings.h>
#endif

extern void*  memccpy(void* __restrict, const void* __restrict, int, size_t);
extern void*  memchr(const void *, int, size_t) __purefunc;
extern void*  memrchr(const void *, int, size_t) __purefunc;
extern int    memcmp(const void *, const void *, size_t) __purefunc;
extern void*  memcpy(void* __restrict, const void* __restrict, size_t);
#if defined(__USE_GNU)
extern void*  mempcpy(void* __restrict, const void* __restrict, size_t);
#endif
extern void*  memmove(void *, const void *, size_t);
extern void*  memset(void *, int, size_t);
extern void*  memmem(const void *, size_t, const void *, size_t) __purefunc;

extern char*  strchr(const char *, int) __purefunc;
extern char* __strchr_chk(const char *, int, size_t);
#if defined(__USE_GNU)
#if defined(__cplusplus)
extern "C++" char* strchrnul(char*, int) __RENAME(strchrnul) __purefunc;
extern "C++" const char* strchrnul(const char*, int) __RENAME(strchrnul) __purefunc;
#else
char* strchrnul(const char*, int) __purefunc;
#endif
#endif

extern char*  strrchr(const char *, int) __purefunc;
extern char* __strrchr_chk(const char *, int, size_t);

extern size_t strlen(const char *) __purefunc;
extern size_t __strlen_chk(const char *, size_t);
extern int    strcmp(const char *, const char *) __purefunc;
extern char*  stpcpy(char* __restrict, const char* __restrict);
extern char*  strcpy(char* __restrict, const char* __restrict);
extern char*  strcat(char* __restrict, const char* __restrict);

int strcasecmp(const char*, const char*) __purefunc;
int strcasecmp_l(const char*, const char*, locale_t) __purefunc;
int strncasecmp(const char*, const char*, size_t) __purefunc;
int strncasecmp_l(const char*, const char*, size_t, locale_t) __purefunc;

extern char*  strdup(const char *);

extern char*  strstr(const char *, const char *) __purefunc;
extern char*  strcasestr(const char *haystack, const char *needle) __purefunc;
extern char*  strtok(char* __restrict, const char* __restrict);
extern char*  strtok_r(char* __restrict, const char* __restrict, char** __restrict);

extern char* strerror(int);
extern char* strerror_l(int, locale_t);
#if defined(__USE_GNU)
extern char* strerror_r(int, char*, size_t) __RENAME(__gnu_strerror_r);
#else /* POSIX */
extern int strerror_r(int, char*, size_t);
#endif

extern size_t strnlen(const char *, size_t) __purefunc;
extern char*  strncat(char* __restrict, const char* __restrict, size_t);
extern char*  strndup(const char *, size_t);
extern int    strncmp(const char *, const char *, size_t) __purefunc;
extern char*  stpncpy(char* __restrict, const char* __restrict, size_t);
extern char*  strncpy(char* __restrict, const char* __restrict, size_t);

extern size_t strlcat(char* __restrict, const char* __restrict, size_t);
extern size_t strlcpy(char* __restrict, const char* __restrict, size_t);

extern size_t strcspn(const char *, const char *) __purefunc;
extern char*  strpbrk(const char *, const char *) __purefunc;
extern char*  strsep(char** __restrict, const char* __restrict);
extern size_t strspn(const char *, const char *);

extern char*  strsignal(int  sig);

extern int    strcoll(const char *, const char *) __purefunc;
extern size_t strxfrm(char* __restrict, const char* __restrict, size_t);

extern int    strcoll_l(const char *, const char *, locale_t) __purefunc;
extern size_t strxfrm_l(char* __restrict, const char* __restrict, size_t, locale_t);

#if defined(__USE_GNU) && !defined(basename)
/*
 * glibc has a basename in <string.h> that's different to the POSIX one in <libgen.h>.
 * It doesn't modify its argument, and in C++ it's const-correct.
 */

#if defined(__cplusplus)
extern "C++" char* basename(char*) __RENAME(__gnu_basename) __nonnull((1));
extern "C++" const char* basename(const char*) __RENAME(__gnu_basename) __nonnull((1));
#else
extern char* basename(const char*) __RENAME(__gnu_basename) __nonnull((1));
#endif
#endif

extern void* __memchr_chk(const void*, int, size_t, size_t);
__errordecl(__memchr_buf_size_error, "memchr called with size bigger than buffer");

extern void* __memrchr_chk(const void*, int, size_t, size_t);
__errordecl(__memrchr_buf_size_error, "memrchr called with size bigger than buffer");
extern void* __memrchr_real(const void*, int, size_t) __RENAME(memrchr);

extern char* __stpncpy_chk2(char* __restrict, const char* __restrict, size_t, size_t, size_t);
extern char* __strncpy_chk2(char* __restrict, const char* __restrict, size_t, size_t, size_t);
extern size_t __strlcpy_real(char* __restrict, const char* __restrict, size_t) __RENAME(strlcpy);
extern size_t __strlcpy_chk(char *, const char *, size_t, size_t);
extern size_t __strlcat_real(char* __restrict, const char* __restrict, size_t) __RENAME(strlcat);
extern size_t __strlcat_chk(char* __restrict, const char* __restrict, size_t, size_t);

#if defined(__BIONIC_FORTIFY)

__BIONIC_FORTIFY_INLINE
void* memchr(const void *s, int c, size_t n) {
    size_t bos = __bos(s);

#if !defined(__clang__)
    if (bos == __BIONIC_FORTIFY_UNKNOWN_SIZE) {
        return __builtin_memchr(s, c, n);
    }

    if (__builtin_constant_p(n) && (n > bos)) {
        __memchr_buf_size_error();
    }

    if (__builtin_constant_p(n) && (n <= bos)) {
        return __builtin_memchr(s, c, n);
    }
#endif

    return __memchr_chk(s, c, n, bos);
}

__BIONIC_FORTIFY_INLINE
void* memrchr(const void *s, int c, size_t n) {
    size_t bos = __bos(s);

#if !defined(__clang__)
    if (bos == __BIONIC_FORTIFY_UNKNOWN_SIZE) {
        return __memrchr_real(s, c, n);
    }

    if (__builtin_constant_p(n) && (n > bos)) {
        __memrchr_buf_size_error();
    }

    if (__builtin_constant_p(n) && (n <= bos)) {
        return __memrchr_real(s, c, n);
    }
#endif

    return __memrchr_chk(s, c, n, bos);
}

__BIONIC_FORTIFY_INLINE
void* memcpy(void* __restrict dest, const void* __restrict src, size_t copy_amount) {
    return __builtin___memcpy_chk(dest, src, copy_amount, __bos0(dest));
}

__BIONIC_FORTIFY_INLINE
void* memmove(void *dest, const void *src, size_t len) {
    return __builtin___memmove_chk(dest, src, len, __bos0(dest));
}

__BIONIC_FORTIFY_INLINE
char* stpcpy(char* __restrict dest, const char* __restrict src) {
    return __builtin___stpcpy_chk(dest, src, __bos(dest));
}

__BIONIC_FORTIFY_INLINE
char* strcpy(char* __restrict dest, const char* __restrict src) {
    return __builtin___strcpy_chk(dest, src, __bos(dest));
}

__BIONIC_FORTIFY_INLINE
char* stpncpy(char* __restrict dest, const char* __restrict src, size_t n) {
    size_t bos_dest = __bos(dest);
    size_t bos_src = __bos(src);

    if (bos_src == __BIONIC_FORTIFY_UNKNOWN_SIZE) {
        return __builtin___stpncpy_chk(dest, src, n, bos_dest);
    }

    if (__builtin_constant_p(n) && (n <= bos_src)) {
        return __builtin___stpncpy_chk(dest, src, n, bos_dest);
    }

    size_t slen = __builtin_strlen(src);
    if (__builtin_constant_p(slen)) {
        return __builtin___stpncpy_chk(dest, src, n, bos_dest);
    }

    return __stpncpy_chk2(dest, src, n, bos_dest, bos_src);
}

__BIONIC_FORTIFY_INLINE
char* strncpy(char* __restrict dest, const char* __restrict src, size_t n) {
    size_t bos_dest = __bos(dest);
    size_t bos_src = __bos(src);

    if (bos_src == __BIONIC_FORTIFY_UNKNOWN_SIZE) {
        return __builtin___strncpy_chk(dest, src, n, bos_dest);
    }

    if (__builtin_constant_p(n) && (n <= bos_src)) {
        return __builtin___strncpy_chk(dest, src, n, bos_dest);
    }

    size_t slen = __builtin_strlen(src);
    if (__builtin_constant_p(slen)) {
        return __builtin___strncpy_chk(dest, src, n, bos_dest);
    }

    return __strncpy_chk2(dest, src, n, bos_dest, bos_src);
}

__BIONIC_FORTIFY_INLINE
char* strcat(char* __restrict dest, const char* __restrict src) {
    return __builtin___strcat_chk(dest, src, __bos(dest));
}

__BIONIC_FORTIFY_INLINE
char *strncat(char* __restrict dest, const char* __restrict src, size_t n) {
    return __builtin___strncat_chk(dest, src, n, __bos(dest));
}

__BIONIC_FORTIFY_INLINE
void* memset(void *s, int c, size_t n) {
    return __builtin___memset_chk(s, c, n, __bos0(s));
}

__BIONIC_FORTIFY_INLINE
size_t strlcpy(char* __restrict dest, const char* __restrict src, size_t size) {
    size_t bos = __bos(dest);

#if !defined(__clang__)
    // Compiler doesn't know destination size. Don't call __strlcpy_chk
    if (bos == __BIONIC_FORTIFY_UNKNOWN_SIZE) {
        return __strlcpy_real(dest, src, size);
    }

    // Compiler can prove, at compile time, that the passed in size
    // is always <= the actual object size. Don't call __strlcpy_chk
    if (__builtin_constant_p(size) && (size <= bos)) {
        return __strlcpy_real(dest, src, size);
    }
#endif /* !defined(__clang__) */

    return __strlcpy_chk(dest, src, size, bos);
}


__BIONIC_FORTIFY_INLINE
size_t strlcat(char* __restrict dest, const char* __restrict src, size_t size) {
    size_t bos = __bos(dest);

#if !defined(__clang__)
    // Compiler doesn't know destination size. Don't call __strlcat_chk
    if (bos == __BIONIC_FORTIFY_UNKNOWN_SIZE) {
        return __strlcat_real(dest, src, size);
    }

    // Compiler can prove, at compile time, that the passed in size
    // is always <= the actual object size. Don't call __strlcat_chk
    if (__builtin_constant_p(size) && (size <= bos)) {
        return __strlcat_real(dest, src, size);
    }
#endif /* !defined(__clang__) */

    return __strlcat_chk(dest, src, size, bos);
}

__BIONIC_FORTIFY_INLINE
size_t strlen(const char *s) {
    size_t bos = __bos(s);

#if !defined(__clang__)
    // Compiler doesn't know destination size. Don't call __strlen_chk
    if (bos == __BIONIC_FORTIFY_UNKNOWN_SIZE) {
        return __builtin_strlen(s);
    }

    size_t slen = __builtin_strlen(s);
    if (__builtin_constant_p(slen)) {
        return slen;
    }
#endif /* !defined(__clang__) */

    return __strlen_chk(s, bos);
}

__BIONIC_FORTIFY_INLINE
char* strchr(const char *s, int c) {
    size_t bos = __bos(s);

#if !defined(__clang__)
    // Compiler doesn't know destination size. Don't call __strchr_chk
    if (bos == __BIONIC_FORTIFY_UNKNOWN_SIZE) {
        return __builtin_strchr(s, c);
    }

    size_t slen = __builtin_strlen(s);
    if (__builtin_constant_p(slen) && (slen < bos)) {
        return __builtin_strchr(s, c);
    }
#endif /* !defined(__clang__) */

    return __strchr_chk(s, c, bos);
}

__BIONIC_FORTIFY_INLINE
char* strrchr(const char *s, int c) {
    size_t bos = __bos(s);

#if !defined(__clang__)
    // Compiler doesn't know destination size. Don't call __strrchr_chk
    if (bos == __BIONIC_FORTIFY_UNKNOWN_SIZE) {
        return __builtin_strrchr(s, c);
    }

    size_t slen = __builtin_strlen(s);
    if (__builtin_constant_p(slen) && (slen < bos)) {
        return __builtin_strrchr(s, c);
    }
#endif /* !defined(__clang__) */

    return __strrchr_chk(s, c, bos);
}


#endif /* defined(__BIONIC_FORTIFY) */

__END_DECLS

#endif /* _STRING_H */
