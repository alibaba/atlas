/* safe_iop
 * License:: released in to the public domain
 * Author:: Will Drewry <redpig@dataspill.org>
 * Copyright 2007,2008 redpig@dataspill.org
 * Some portions copyright The Android Open Source Project
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS
 * OF ANY KIND, either express or implied.
 * 
 * To Do:
 * - Add varargs style interface for safe_<op>()
 * - Add support for safe conversion
 * - Add additional sizes to safe_iopf (currently 32-bit only)
 *   (this will make use of the safe conversion above)
 * - Add left shift support
 * - Add more test cases for interfaces (op_mixed)
 * - Add more tests for edge cases I've missed? and for thoroughness
 *
 * History:
 * = 0.3
 * - solidified code into a smaller number of macros and functions
 * - added typeless functions using gcc magic (typeof)
 * - deprecrated old interfaces (-DSAFE_IOP_COMPAT)
 * - discover size maximums automagically
 * - separated test cases for easier understanding
 * - significantly expanded test cases
 * - derive type maximums and minimums internally (checked in testing)
 * = 0.2
 * - Removed dependence on twos complement arithmetic to allow macro-ized
 *   definitions
 * - Added (s)size_t support
 * - Added (u)int8,16,64 support
 * - Added portable inlining
 * - Added support for NULL result pointers
 * - Added support for header-only use (safe_iop.c only needed for safe_iopf)
 * = 0.1
 * - Initial release
 *
 * Contributors & thanks:
 * - peter@valchev.net for his review, comments, and enthusiasm
 * - thanks to Google for contributing some time
 */

/* This library supplies a set of standard functions for performing and
 * checking safe integer operations. The code is based on examples from
 * https://www.securecoding.cert.org/confluence/display/seccode/INT32-C.+Ensure+that+operations+on+signed+integers+do+not+result+in+overflow
 *
 * Inline functions are available for specific operations.  If the result
 * pointer is NULL, the function will still return 1 or 0 if it would
 * or would not overflow.  If multiple operations need to be performed,
 * safe_iopf provides a format-string driven model, but it does not yet support
 * non-32 bit operations
 *
 * NOTE: This code assumes int32_t to be signed.
 */
#ifndef _SAFE_IOP_H
#define _SAFE_IOP_H
#include <limits.h>  /* for CHAR_BIT */
#include <assert.h>  /* for type enforcement */

typedef enum { SAFE_IOP_TYPE_S32 = 1,
               SAFE_IOP_TYPE_U32,
               SAFE_IOP_TYPE_DEFAULT = SAFE_IOP_TYPE_S32,
               } safe_type_t;

#define SAFE_IOP_TYPE_PREFIXES "us"

/* use a nice prefix :) */
#define __sio(x) OPAQUE_SAFE_IOP_PREFIX_ ## x
#define OPAQUE_SAFE_IOP_PREFIX_var(x) __sio(VARIABLE_ ## x)
#define OPAQUE_SAFE_IOP_PREFIX_m(x) __sio(MACRO_ ## x)


/* A recursive macro which safely multiplies the given type together.
 * _ptr may be NULL.
 * mixed types or mixed sizes will unconditionally return 0;
 */
#define OPAQUE_SAFE_IOP_PREFIX_MACRO_smax(_a) \
  ((typeof(_a))(~((typeof(_a)) 1 << ((sizeof(typeof(_a)) * CHAR_BIT) - 1))))
#define OPAQUE_SAFE_IOP_PREFIX_MACRO_smin(_a) \
  ((typeof(_a))(-__sio(m)(smax)(_a) - 1))
#define OPAQUE_SAFE_IOP_PREFIX_MACRO_umax(_a) ((typeof(_a))(~((typeof(_a)) 0)))

#define OPAQUE_SAFE_IOP_PREFIX_MACRO_type_enforce(__A, __B) \
  ((((__sio(m)(smin)(__A) <= ((typeof(__A))0)) && \
     (__sio(m)(smin)(__B) <= ((typeof(__B))0))) || \
   (((__sio(m)(smin)(__A) > ((typeof(__A))0))) && \
     (__sio(m)(smin)(__B) > ((typeof(__B))0)))) && \
   (sizeof(typeof(__A)) == sizeof(typeof(__B)))) 


/* We use a non-void wrapper for assert(). This allows us to factor it away on
 * -DNDEBUG but still have conditionals test the result (and optionally return
 *  false).
 */
#if defined(NDEBUG)
#  define OPAQUE_SAFE_IOP_PREFIX_MACRO_assert(x) (x)
#else
#  define OPAQUE_SAFE_IOP_PREFIX_MACRO_assert(x) ({ assert(x); 1; })
#endif


/* Primary interface macros */
/* type checking is compiled out if NDEBUG supplied. */
#define safe_add(_ptr, __a, __b) \
 ({ int __sio(var)(ok) = 0; \
    typeof(__a) __sio(var)(_a) = (__a); \
    typeof(__b) __sio(var)(_b) = (__b); \
    typeof(_ptr) __sio(var)(p) = (_ptr); \
    if (__sio(m)(assert)(__sio(m)(type_enforce)(__sio(var)(_a), \
                                                __sio(var)(_b)))) { \
      if (__sio(m)(smin)(__sio(var)(_a)) <= ((typeof(__sio(var)(_a)))0)) { \
        __sio(var)(ok) = safe_sadd(__sio(var)(p), \
                                   __sio(var)(_a), \
                                   __sio(var)(_b)); \
      } else { \
        __sio(var)(ok) = safe_uadd(__sio(var)(p), \
                                   __sio(var)(_a), \
                                   __sio(var)(_b)); \
      } \
    } \
    __sio(var)(ok); })

#define safe_add3(_ptr, _A, _B, _C) \
({ typeof(_A) __sio(var)(a) = (_A); \
   typeof(_B) __sio(var)(b) = (_B); \
   typeof(_C) __sio(var)(c) = (_C); \
   typeof(_A) __sio(var)(r) = 0; \
   (safe_add(&(__sio(var)(r)), __sio(var)(a), __sio(var)(b)) && \
    safe_add((_ptr), __sio(var)(r), __sio(var)(c))); })

#define safe_add4(_ptr, _A, _B, _C, _D) \
({ typeof(_A) __sio(var)(a) = (_A); \
   typeof(_B) __sio(var)(b) = (_B); \
   typeof(_C) __sio(var)(c) = (_C); \
   typeof(_D) __sio(var)(d) = (_D); \
   typeof(_A) __sio(var)(r) = 0; \
  (safe_add(&(__sio(var)(r)), __sio(var)(a), __sio(var)(b)) && \
   safe_add(&(__sio(var)(r)), __sio(var)(r), __sio(var)(c)) && \
   safe_add((_ptr), __sio(var)(r), (__sio(var)(d)))); })

#define safe_add5(_ptr, _A, _B, _C, _D, _E) \
({ typeof(_A) __sio(var)(a) = (_A); \
   typeof(_B) __sio(var)(b) = (_B); \
   typeof(_C) __sio(var)(c) = (_C); \
   typeof(_D) __sio(var)(d) = (_D); \
   typeof(_E) __sio(var)(e) = (_E); \
   typeof(_A) __sio(var)(r) = 0; \
  (safe_add(&(__sio(var)(r)), __sio(var)(a), __sio(var)(b)) && \
   safe_add(&(__sio(var)(r)), __sio(var)(r), __sio(var)(c)) && \
   safe_add(&(__sio(var)(r)), __sio(var)(r), __sio(var)(d)) && \
   safe_add((_ptr), __sio(var)(r), __sio(var)(e))); })

#define safe_sub(_ptr, __a, __b) \
 ({ int __sio(var)(ok) = 0; \
    typeof(__a) __sio(var)(_a) = (__a); \
    typeof(__b) __sio(var)(_b) = (__b); \
    typeof(_ptr) __sio(var)(p) = (_ptr); \
    if (__sio(m)(assert)(__sio(m)(type_enforce)(__sio(var)(_a), \
                                                __sio(var)(_b)))) { \
      if (__sio(m)(umax)(__sio(var)(_a)) <= ((typeof(__sio(var)(_a)))0)) { \
        __sio(var)(ok) = safe_ssub(__sio(var)(p), \
                                   __sio(var)(_a), \
                                   __sio(var)(_b)); \
      } else { \
        __sio(var)(ok) = safe_usub(__sio(var)(p), \
                                   __sio(var)(_a), \
                                   __sio(var)(_b)); \
      } \
    } \
    __sio(var)(ok); })

/* These are sequentially performed */
#define safe_sub3(_ptr, _A, _B, _C) \
({ typeof(_A) __sio(var)(a) = (_A); \
   typeof(_B) __sio(var)(b) = (_B); \
   typeof(_C) __sio(var)(c) = (_C); \
   typeof(_A) __sio(var)(r) = 0; \
   (safe_sub(&(__sio(var)(r)), __sio(var)(a), __sio(var)(b)) && \
    safe_sub((_ptr), __sio(var)(r), __sio(var)(c))); })

#define safe_sub4(_ptr, _A, _B, _C, _D) \
({ typeof(_A) __sio(var)(a) = (_A); \
   typeof(_B) __sio(var)(b) = (_B); \
   typeof(_C) __sio(var)(c) = (_C); \
   typeof(_D) __sio(var)(d) = (_D); \
   typeof(_A) __sio(var)(r) = 0; \
  (safe_sub(&(__sio(var)(r)), __sio(var)(a), __sio(var)(b)) && \
   safe_sub(&(__sio(var)(r)), __sio(var)(r), __sio(var)(c)) && \
   safe_sub((_ptr), __sio(var)(r), (__sio(var)(d)))); })

#define safe_sub5(_ptr, _A, _B, _C, _D, _E) \
({ typeof(_A) __sio(var)(a) = (_A); \
   typeof(_B) __sio(var)(b) = (_B); \
   typeof(_C) __sio(var)(c) = (_C); \
   typeof(_D) __sio(var)(d) = (_D); \
   typeof(_E) __sio(var)(e) = (_E); \
   typeof(_A) __sio(var)(r) = 0; \
   (safe_sub(&(__sio(var)(r)), __sio(var)(a), __sio(var)(b)) && \
    safe_sub(&(__sio(var)(r)), __sio(var)(r), __sio(var)(c)) && \
    safe_sub(&(__sio(var)(r)), __sio(var)(r), __sio(var)(d)) && \
    safe_sub((_ptr), __sio(var)(r), __sio(var)(e))); })


 
#define safe_mul(_ptr, __a, __b) \
 ({ int __sio(var)(ok) = 0; \
    typeof(__a) __sio(var)(_a) = (__a); \
    typeof(__b) __sio(var)(_b) = (__b); \
    typeof(_ptr) __sio(var)(p) = (_ptr); \
    if (__sio(m)(assert)(__sio(m)(type_enforce)(__sio(var)(_a), \
                                                __sio(var)(_b)))) { \
      if (__sio(m)(umax)(__sio(var)(_a)) <= ((typeof(__sio(var)(_a)))0)) { \
        __sio(var)(ok) = safe_smul(__sio(var)(p), \
                                   __sio(var)(_a), \
                                   __sio(var)(_b)); \
      } else { \
        __sio(var)(ok) = safe_umul(__sio(var)(p), \
                                   __sio(var)(_a), \
                                   __sio(var)(_b)); \
      } \
    } \
    __sio(var)(ok); })

#define safe_mul3(_ptr, _A, _B, _C) \
({ typeof(_A) __sio(var)(a) = (_A); \
   typeof(_B) __sio(var)(b) = (_B); \
   typeof(_C) __sio(var)(c) = (_C); \
   typeof(_A) __sio(var)(r) = 0; \
   (safe_mul(&(__sio(var)(r)), __sio(var)(a), __sio(var)(b)) && \
    safe_mul((_ptr), __sio(var)(r), __sio(var)(c))); })

#define safe_mul4(_ptr, _A, _B, _C, _D) \
({ typeof(_A) __sio(var)(a) = (_A); \
   typeof(_B) __sio(var)(b) = (_B); \
   typeof(_C) __sio(var)(c) = (_C); \
   typeof(_D) __sio(var)(d) = (_D); \
   typeof(_A) __sio(var)(r) = 0; \
  (safe_mul(&(__sio(var)(r)), __sio(var)(a), __sio(var)(b)) && \
   safe_mul(&(__sio(var)(r)), __sio(var)(r), __sio(var)(c)) && \
   safe_mul((_ptr), __sio(var)(r), (__sio(var)(d)))); })

#define safe_mul5(_ptr, _A, _B, _C, _D, _E) \
({ typeof(_A) __sio(var)(a) = (_A); \
   typeof(_B) __sio(var)(b) = (_B); \
   typeof(_C) __sio(var)(c) = (_C); \
   typeof(_D) __sio(var)(d) = (_D); \
   typeof(_E) __sio(var)(e) = (_E); \
   typeof(_A) __sio(var)(r) = 0; \
  (safe_mul(&(__sio(var)(r)), __sio(var)(a), __sio(var)(b)) && \
   safe_mul(&(__sio(var)(r)), __sio(var)(r), __sio(var)(c)) && \
   safe_mul(&(__sio(var)(r)), __sio(var)(r), __sio(var)(d)) && \
   safe_mul((_ptr), __sio(var)(r), __sio(var)(e))); })

#define safe_div(_ptr, __a, __b) \
 ({ int __sio(var)(ok) = 0; \
    typeof(__a) __sio(var)(_a) = (__a); \
    typeof(__b) __sio(var)(_b) = (__b); \
    typeof(_ptr) __sio(var)(p) = (_ptr); \
    if (__sio(m)(assert)(__sio(m)(type_enforce)(__sio(var)(_a), \
                                                __sio(var)(_b)))) { \
      if (__sio(m)(umax)(__sio(var)(_a)) <= ((typeof(__sio(var)(_a)))0)) { \
        __sio(var)(ok) = safe_sdiv(__sio(var)(p), \
                                   __sio(var)(_a), \
                                   __sio(var)(_b)); \
      } else { \
        __sio(var)(ok) = safe_udiv(__sio(var)(p), \
                                   __sio(var)(_a), \
                                   __sio(var)(_b)); \
      } \
    } \
    __sio(var)(ok); })

#define safe_div3(_ptr, _A, _B, _C) \
({ typeof(_A) __sio(var)(a) = (_A); \
   typeof(_B) __sio(var)(b) = (_B); \
   typeof(_C) __sio(var)(c) = (_C); \
   typeof(_A) __sio(var)(r) = 0; \
   (safe_div(&(__sio(var)(r)), __sio(var)(a), __sio(var)(b)) && \
    safe_div((_ptr), __sio(var)(r), __sio(var)(c))); })

#define safe_div4(_ptr, _A, _B, _C, _D) \
({ typeof(_A) __sio(var)(a) = (_A); \
   typeof(_B) __sio(var)(b) = (_B); \
   typeof(_C) __sio(var)(c) = (_C); \
   typeof(_D) __sio(var)(d) = (_D); \
   typeof(_A) __sio(var)(r) = 0; \
  (safe_div(&(__sio(var)(r)), __sio(var)(a), __sio(var)(b)) && \
   safe_div(&(__sio(var)(r)), __sio(var)(r), __sio(var)(c)) && \
   safe_div((_ptr), __sio(var)(r), (__sio(var)(d)))); })

#define safe_div5(_ptr, _A, _B, _C, _D, _E) \
({ typeof(_A) __sio(var)(a) = (_A); \
   typeof(_B) __sio(var)(b) = (_B); \
   typeof(_C) __sio(var)(c) = (_C); \
   typeof(_D) __sio(var)(d) = (_D); \
   typeof(_E) __sio(var)(e) = (_E); \
   typeof(_A) __sio(var)(r) = 0; \
  (safe_div(&(__sio(var)(r)), __sio(var)(a), __sio(var)(b)) && \
   safe_div(&(__sio(var)(r)), __sio(var)(r), __sio(var)(c)) && \
   safe_div(&(__sio(var)(r)), __sio(var)(r), __sio(var)(d)) && \
   safe_div((_ptr), __sio(var)(r), __sio(var)(e))); })

#define safe_mod(_ptr, __a, __b) \
 ({ int __sio(var)(ok) = 0; \
    typeof(__a) __sio(var)(_a) = (__a); \
    typeof(__b) __sio(var)(_b) = (__b); \
    typeof(_ptr) __sio(var)(p) = (_ptr); \
    if (__sio(m)(assert)(__sio(m)(type_enforce)(__sio(var)(_a), \
                                                __sio(var)(_b)))) { \
      if (__sio(m)(umax)(__sio(var)(_a)) <= ((typeof(__sio(var)(_a)))0)) { \
        __sio(var)(ok) = safe_smod(__sio(var)(p), \
                                   __sio(var)(_a), \
                                   __sio(var)(_b)); \
      } else { \
        __sio(var)(ok) = safe_umod(__sio(var)(p), \
                                   __sio(var)(_a), \
                                   __sio(var)(_b)); \
      } \
    } \
    __sio(var)(ok); })

#define safe_mod3(_ptr, _A, _B, _C) \
({ typeof(_A) __sio(var)(a) = (_A); \
   typeof(_B) __sio(var)(b) = (_B); \
   typeof(_C) __sio(var)(c) = (_C); \
   typeof(_A) __sio(var)(r) = 0; \
   (safe_mod(&(__sio(var)(r)), __sio(var)(a), __sio(var)(b)) && \
    safe_mod((_ptr), __sio(var)(r), __sio(var)(c))); })

#define safe_mod4(_ptr, _A, _B, _C, _D) \
({ typeof(_A) __sio(var)(a) = (_A); \
   typeof(_B) __sio(var)(b) = (_B); \
   typeof(_C) __sio(var)(c) = (_C); \
   typeof(_D) __sio(var)(d) = (_D); \
   typeof(_A) __sio(var)(r) = 0; \
   (safe_mod(&(__sio(var)(r)), __sio(var)(a), __sio(var)(b)) && \
    safe_mod(&(__sio(var)(r)), __sio(var)(r), __sio(var)(c)) && \
    safe_mod((_ptr), __sio(var)(r), (__sio(var)(d)))); })

#define safe_mod5(_ptr, _A, _B, _C, _D, _E) \
({ typeof(_A) __sio(var)(a) = (_A); \
   typeof(_B) __sio(var)(b) = (_B); \
   typeof(_C) __sio(var)(c) = (_C), \
   typeof(_D) __sio(var)(d) = (_D); \
   typeof(_E) __sio(var)(e) = (_E); \
   typeof(_A) __sio(var)(r) = 0; \
   (safe_mod(&(__sio(var)(r)), __sio(var)(a), __sio(var)(b)) && \
    safe_mod(&(__sio(var)(r)), __sio(var)(r), __sio(var)(c)) && \
    safe_mod(&(__sio(var)(r)), __sio(var)(r), __sio(var)(d)) && \
    safe_mod((_ptr), __sio(var)(r), __sio(var)(e))); })

/*** Safe integer operation implementation macros ***/

#define safe_uadd(_ptr, _a, _b) \
 ({ int __sio(var)(ok) = 0; \
    if ((typeof(_a))(_b) <= (typeof(_a))(__sio(m)(umax)(_a) - (_a))) { \
      if ((_ptr)) { *((typeof(_a)*)(_ptr)) = (_a) + (_b); } \
      __sio(var)(ok) = 1; \
    } __sio(var)(ok); })

#define safe_sadd(_ptr, _a, _b) \
  ({ int __sio(var)(ok) = 1; \
     if (((_b) > (typeof(_a))0) && ((_a) > (typeof(_a))0)) { /*>0*/ \
       if ((_a) > (typeof(_a))(__sio(m)(smax)(_a) - (_b))) __sio(var)(ok) = 0; \
     } else if (!((_b) > (typeof(_a))0) && !((_a) > (typeof(_a))0)) { /*<0*/ \
       if ((_a) < (typeof(_a))(__sio(m)(smin)(_a) - (_b))) __sio(var)(ok) = 0; \
     } \
     if (__sio(var)(ok) && (_ptr)) { *((typeof(_a)*)(_ptr)) = (_a) + (_b); } \
     __sio(var)(ok); })

#define safe_usub(_ptr, _a, _b) \
  ({ int __sio(var)(ok) = 0; \
     if ((_a) >= (_b)) { \
       if ((_ptr)) { *((typeof(_a)*)(_ptr)) = (_a) - (_b); } \
       __sio(var)(ok) = 1; \
     } \
     __sio(var)(ok); }) 

#define safe_ssub(_ptr, _a, _b) \
  ({ int __sio(var)(ok) = 0; \
     if (!((_b) <= 0 && (_a) > (__sio(m)(smax)(_a) + (_b))) && \
         !((_b) > 0 && (_a) < (__sio(m)(smin)(_a) + (_b)))) { \
         __sio(var)(ok) = 1; \
         if ((_ptr)) { *((typeof(_a)*)(_ptr)) = (_a) - (_b); } \
     } \
     __sio(var)(ok); }) 

#define safe_umul(_ptr, _a, _b) \
  ({ int __sio(var)(ok) = 0; \
     if (!(_b) || (_a) <= (__sio(m)(umax)(_a) / (_b))) { \
       __sio(var)(ok) = 1; \
       if ((_ptr)) { *((typeof(_a)*)(_ptr)) = (_a) * (_b); } \
     } \
     __sio(var)(ok); }) 

#define safe_smul(_ptr, _a, _b) \
  ({ int __sio(var)(ok) = 1; \
    if ((_a) > 0) {  /* a is positive */ \
      if ((_b) > 0) {  /* b and a are positive */ \
        if ((_a) > (__sio(m)(smax)(_a) / (_b))) { \
          __sio(var)(ok) = 0; \
        } \
      } /* end if a and b are positive */ \
      else { /* a positive, b non-positive */ \
        if ((_b) < (__sio(m)(smin)(_a) / (_a))) { \
          __sio(var)(ok) = 0; \
        } \
      } /* a positive, b non-positive */ \
    } /* end if a is positive */ \
    else { /* a is non-positive */ \
      if ((_b) > 0) { /* a is non-positive, b is positive */ \
        if ((_a) < (__sio(m)(smin)(_a) / (_b))) { \
        __sio(var)(ok) = 0; \
        } \
      } /* end if a is non-positive, b is positive */ \
      else { /* a and b are non-positive */ \
        if( ((_a) != 0) && ((_b) < (__sio(m)(smax)(_a) / (_a)))) { \
          __sio(var)(ok) = 0; \
        } \
      } /* end if a and b are non-positive */ \
    } /* end if a is non-positive */ \
    if (__sio(var)(ok) && (_ptr)) { *((typeof(_a)*)(_ptr)) = (_a) * (_b); } \
    __sio(var)(ok); }) 

/* div-by-zero is the only thing addressed */
#define safe_udiv(_ptr, _a, _b) \
 ({ int __sio(var)(ok) = 0; \
    if ((_b) != 0) { \
      if ((_ptr)) { *((typeof(_a)*)(_ptr)) = (_a) / (_b); } \
      __sio(var)(ok) = 1; \
    } \
    __sio(var)(ok); })

/* Addreses div by zero and smin -1 */
#define safe_sdiv(_ptr, _a, _b) \
 ({ int __sio(var)(ok) = 0; \
    if ((_b) != 0 && \
        (((_a) != __sio(m)(smin)(_a)) || ((_b) != (typeof(_b))-1))) { \
      if ((_ptr)) { *((typeof(_a)*)(_ptr)) = (_a) / (_b); } \
      __sio(var)(ok) = 1; \
    } \
    __sio(var)(ok); })

#define safe_umod(_ptr, _a, _b) \
 ({ int __sio(var)(ok) = 0; \
    if ((_b) != 0) { \
      if ((_ptr)) { *((typeof(_a)*)(_ptr)) = (_a) % (_b); } \
      __sio(var)(ok) = 1; \
    } \
    __sio(var)(ok); })

#define safe_smod(_ptr, _a, _b) \
 ({ int __sio(var)(ok) = 0; \
    if ((_b) != 0 && \
        (((_a) != __sio(m)(smin)(_a)) || ((_b) != (typeof(_b))-1))) { \
      if ((_ptr)) { *((typeof(_a)*)(_ptr)) = (_a) % (_b); } \
      __sio(var)(ok) = 1; \
    } \
    __sio(var)(ok); })

#if SAFE_IOP_COMPAT
/* These are used for testing for easy type enforcement */
#include <sys/types.h>
#include <limits.h>

#ifndef SAFE_IOP_INLINE
#  if defined(__GNUC__) && (__GNUC__ > 3 || __GNUC__ == 3 &&  __GNUC_MINOR__ > 0)
#    define SAFE_IOP_INLINE __attribute__((always_inline)) static inline
#  else
#    define SAFE_IOP_INLINE static inline
#  endif
#endif

#define MAKE_UADD(_prefix, _bits, _type, _max) \
  SAFE_IOP_INLINE \
  int safe_add##_prefix##_bits (_type *result, _type value, _type a) { \
    return safe_uadd(result, value, a); \
  }

#define MAKE_SADD(_prefix, _bits, _type, _max) \
  SAFE_IOP_INLINE \
  int safe_add##_prefix##_bits(_type *result, _type value, _type a) { \
    return safe_sadd(result, value, a); \
  }

#define MAKE_USUB(_prefix, _bits, _type) \
  SAFE_IOP_INLINE \
  int safe_sub##_prefix##_bits(_type *result, _type value, _type a) { \
    return safe_usub(result, value, a); \
  }

#define MAKE_SSUB(_prefix, _bits, _type, _min, _max) \
  SAFE_IOP_INLINE \
  int safe_sub##_prefix##_bits(_type *result, _type value, _type a) { \
    return safe_ssub(result, value, a); \
  }

#define MAKE_UMUL(_prefix, _bits, _type, _max) \
  SAFE_IOP_INLINE \
  int safe_mul##_prefix##_bits(_type *result, _type value, _type a) { \
    return safe_umul(result, value, a); \
  }


#define MAKE_SMUL(_prefix, _bits, _type, _max, _min) \
  SAFE_IOP_INLINE \
  int safe_mul##_prefix##_bits(_type *result, _type value, _type a) { \
    return safe_smul(result, value, a); \
  }

#define MAKE_UDIV(_prefix, _bits, _type) \
  SAFE_IOP_INLINE \
  int safe_div##_prefix##_bits(_type *result, _type value, _type a) { \
    return safe_udiv(result, value, a); \
  }

#define MAKE_SDIV(_prefix, _bits, _type, _min) \
  SAFE_IOP_INLINE \
  int safe_div##_prefix##_bits(_type *result, _type value, _type a) { \
    return safe_sdiv(result, value, a); \
  }

#define MAKE_UMOD(_prefix, _bits, _type) \
  SAFE_IOP_INLINE \
  int safe_mod##_prefix##_bits(_type *result, _type value, _type a) { \
    return safe_umod(result, value, a); \
  }

#define MAKE_SMOD(_prefix, _bits, _type, _min) \
  SAFE_IOP_INLINE \
  int safe_mod##_prefix##_bits(_type *result, _type value, _type a) { \
    return safe_smod(result, value, a); \
  }

/* __LP64__ is given by GCC. Without more work, this is bound to GCC. */
#if __LP64__ == 1 || __SIZEOF_LONG__ > __SIZEOF_INT__
#  define SAFE_INT64_MAX 0x7fffffffffffffffL
#  define SAFE_UINT64_MAX 0xffffffffffffffffUL
#  define SAFE_INT64_MIN (-SAFE_INT64_MAX - 1L)
#elif __SIZEOF_LONG__ == __SIZEOF_INT__
#  define SAFE_INT64_MAX 0x7fffffffffffffffLL
#  define SAFE_UINT64_MAX 0xffffffffffffffffULL
#  define SAFE_INT64_MIN (-SAFE_INT64_MAX - 1LL)
#else
#  warning "64-bit support disabled"
#  define SAFE_IOP_NO_64 1
#endif

/* Assumes SSIZE_MAX */
#ifndef SSIZE_MIN
#  if SSIZE_MAX == LONG_MAX
#    define SSIZE_MIN LONG_MIN
#  elif SSIZE_MAX == LONG_LONG_MAX
#    define SSIZE_MIN LONG_LONG_MIN
#  else
#    error "SSIZE_MIN is not defined and could not be guessed"
#  endif
#endif



#ifndef SAFE_IOP_NO_64
  MAKE_UADD(u, 64, u_int64_t, SAFE_UINT64_MAX)
#endif
MAKE_UADD(,szt, size_t, SIZE_MAX)
MAKE_UADD(u, 32, u_int32_t, UINT_MAX)
MAKE_UADD(u, 16, u_int16_t, USHRT_MAX)
MAKE_UADD(u,  8, u_int8_t, UCHAR_MAX)

#ifndef SAFE_IOP_NO_64
  MAKE_SADD(s, 64, int64_t, SAFE_INT64_MAX)
#endif
MAKE_SADD(s, szt, ssize_t, SSIZE_MAX)
MAKE_SADD(s, 32, int32_t, INT_MAX)
MAKE_SADD(s, 16, int16_t, SHRT_MAX)
MAKE_SADD(s,  8, int8_t, SCHAR_MAX)

#ifndef SAFE_IOP_NO_64
  MAKE_USUB(u, 64, u_int64_t)
#endif
MAKE_USUB(, szt, size_t)
MAKE_USUB(u, 32, u_int32_t)
MAKE_USUB(u, 16, u_int16_t)
MAKE_USUB(u, 8, u_int8_t)

#ifndef SAFE_IOP_NO_64
  MAKE_SSUB(s, 64, int64_t, SAFE_INT64_MIN, SAFE_INT64_MAX)
#endif
MAKE_SSUB(s, szt, ssize_t, SSIZE_MIN, SSIZE_MAX)
MAKE_SSUB(s, 32, int32_t, INT_MIN, INT_MAX)
MAKE_SSUB(s, 16, int16_t, SHRT_MIN, SHRT_MAX)
MAKE_SSUB(s,  8, int8_t, SCHAR_MIN, SCHAR_MAX)


#ifndef SAFE_IOP_NO_64
  MAKE_UMUL(u, 64, u_int64_t, SAFE_UINT64_MAX)
#endif
MAKE_UMUL(, szt, size_t, SIZE_MAX)
MAKE_UMUL(u, 32, u_int32_t, UINT_MAX)
MAKE_UMUL(u, 16, u_int16_t, USHRT_MAX)
MAKE_UMUL(u, 8, u_int8_t,  UCHAR_MAX)

#ifndef SAFE_IOP_NO_64
  MAKE_SMUL(s, 64, int64_t, SAFE_INT64_MAX, SAFE_INT64_MIN)
#endif
MAKE_SMUL(s, szt, ssize_t, SSIZE_MAX, SSIZE_MIN)
MAKE_SMUL(s, 32, int32_t, INT_MAX, INT_MIN)
MAKE_SMUL(s, 16, int16_t, SHRT_MAX, SHRT_MIN)
MAKE_SMUL(s,  8, int8_t,  SCHAR_MAX, SCHAR_MIN)


#ifndef SAFE_IOP_NO_64
  MAKE_UDIV(u, 64, u_int64_t)
#endif
MAKE_UDIV(, szt, size_t)
MAKE_UDIV(u, 32, u_int32_t)
MAKE_UDIV(u, 16, u_int16_t)
MAKE_UDIV(u,  8, u_int8_t)

#ifndef SAFE_IOP_NO_64
  MAKE_SDIV(s, 64, int64_t, SAFE_INT64_MIN)
#endif
MAKE_SDIV(s, szt, ssize_t, SSIZE_MIN)
MAKE_SDIV(s, 32, int32_t, INT_MIN)
MAKE_SDIV(s, 16, int16_t, SHRT_MIN)
MAKE_SDIV(s,  8, int8_t,  SCHAR_MIN)


#ifndef SAFE_IOP_NO_64
  MAKE_UMOD(u, 64, u_int64_t)
#endif
MAKE_UMOD(, szt, size_t)
MAKE_UMOD(u, 32, u_int32_t)
MAKE_UMOD(u, 16, u_int16_t)
MAKE_UMOD(u,  8, u_int8_t)

#ifndef SAFE_IOP_NO_64
  MAKE_SMOD(s, 64, int64_t, SAFE_INT64_MIN)
#endif
MAKE_SMOD(s, szt, ssize_t, SSIZE_MIN)
MAKE_SMOD(s, 32, int32_t, INT_MIN)
MAKE_SMOD(s, 16, int16_t, SHRT_MIN)
MAKE_SMOD(s, 8, int8_t,  SCHAR_MIN)

/* Cleanup the macro spam */
#undef MAKE_SMUL
#undef MAKE_UMUL
#undef MAKE_SSUB
#undef MAKE_USUB
#undef MAKE_SADD
#undef MAKE_UADD
#undef MAKE_UDIV
#undef MAKE_SDIV
#undef MAKE_UMOD
#undef MAKE_SMOD

#endif  /* SAFE_IOP_COMPAT */



/* safe_iopf
 *
 * Takes in a character array which specifies the operations
 * to perform on a given value. The value will be assumed to be
 * of the type specified for each operation.
 *
 * Currently accepted format syntax is:
 *   [type_marker]operation...
 * The type marker may be any of the following:
 * - s32 for signed int32
 * - u32 for unsigned int32
 * If no type_marker is specified, it is assumed to be s32.
 *
 * Currently, this only performs correctly with 32-bit integers.
 *
 * The operation must be one of the following:
 * - * -- multiplication
 * - / -- division
 * - - -- subtraction
 * - + -- addition
 * - % -- modulo (remainder)
 * 
 * Whitespace will be ignored.
 *
 * Args:
 * - pointer to the final result  (this must be at least the size of int32)
 * - array of format characters
 * - all remaining arguments are derived from the format
 * Output:
 * - Returns 1 on success leaving the result in value
 * - Returns 0 on failure leaving the contents of value *unknown*
 */

int safe_iopf(void *result, const char *const fmt, ...);


#endif  /* _SAFE_IOP_H */
