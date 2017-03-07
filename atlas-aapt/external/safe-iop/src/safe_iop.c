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
 * See safe_iop.h for more info.
 */
#include <stdint.h>
#include <stdarg.h>
#include <string.h>
#include <sys/types.h>

#include <safe_iop.h>

/* Read off the type if the first value matches a type prefix
 * and consume characters if successful.
 */
static int _safe_op_read_type(safe_type_t *type, const char **c) {
  if (type == NULL) {
    return 0;
  }
  if (c == NULL || *c == NULL || **c == '\0') {
    return 0;
  }
  /* Extract a type for the operation if there is one */
  if (strchr(SAFE_IOP_TYPE_PREFIXES, **c) != NULL) {
    switch(**c) {
      case 'u':
        if ((*(*c+1) && *(*c+1) == '3') &&
            (*(*c+2) && *(*c+2) == '2')) {
          *type = SAFE_IOP_TYPE_U32;
          *c += 3; /* Advance past type */
        }
        break;
      case 's':
        if ((*(*c+1) && *(*c+1) == '3') &&
            (*(*c+2) && *(*c+2) == '2')) {
          *type = SAFE_IOP_TYPE_S32;
          *c += 3; /* Advance past type */
        }
        break;
      default:
        /* Unknown type */
        return 0;
    }
  }
  return 1;
}

#define _SAFE_IOP_TYPE_CASE(_type, _func) { \
  _type a = va_arg(ap, _type), value = *((_type *) result); \
  if (!baseline) { \
    value = a; \
    a = va_arg(ap, _type); \
    baseline = 1; \
  } \
  if (! _func( (_type *) result, value, a)) \
    return 0; \
}
#define _SAFE_IOP_OP_CASE(u32func, s32func) \
  switch (type) { \
    case SAFE_IOP_TYPE_U32: \
      _SAFE_IOP_TYPE_CASE(u_int32_t, u32func); \
      break; \
    case SAFE_IOP_TYPE_S32: \
      _SAFE_IOP_TYPE_CASE(int32_t, s32func); \
      break; \
    default: \
      return 0; \
  }

int safe_iopf(void *result, const char *const fmt, ...) {
  va_list ap;
  int baseline = 0; /* indicates if the base value is present */

  const char *c = NULL;
  safe_type_t type = SAFE_IOP_TYPE_DEFAULT;
  /* Result should not be NULL */
  if (!result)
    return 0;

  va_start(ap, fmt);
  if (fmt == NULL || fmt[0] == '\0')
    return 0;
  for(c=fmt;(*c);c++) {
    /* Read the type if specified */
    if (!_safe_op_read_type(&type, &c)) {
      return 0;
    }

    /* Process the the operations */
    switch(*c) { /* operation */
      case '+': /* add */
        _SAFE_IOP_OP_CASE(safe_uadd, safe_sadd);
        break;
      case '-': /* sub */
        _SAFE_IOP_OP_CASE(safe_usub, safe_ssub);
        break;
      case '*': /* mul */
        _SAFE_IOP_OP_CASE(safe_umul, safe_smul);
        break;
      case '/': /* div */
        _SAFE_IOP_OP_CASE(safe_udiv, safe_sdiv);
        break;
      case '%': /* mod */
        _SAFE_IOP_OP_CASE(safe_umod, safe_smod);
        break;
      default:
       /* unknown op */
       return 0;
    }
    /* Reset the type */
   type = SAFE_IOP_TYPE_DEFAULT;
  }
  /* Success! */
  return 1;
}

#ifdef SAFE_IOP_TEST
#include <stdio.h>
#include <stdint.h>
#include <limits.h>

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

/* Pull these from GNU's limit.h */
#ifndef LLONG_MAX
#  define LLONG_MAX 9223372036854775807LL
#endif
#ifndef LLONG_MIN
#  define LLONG_MIN (-LLONG_MAX - 1LL)
#endif
#ifndef ULLONG_MAX
#  define ULLONG_MAX 18446744073709551615ULL
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

#define EXPECT_FALSE(cmd) ({ \
  printf("%s: EXPECT_FALSE(" #cmd ") => ", __func__); \
  if ((cmd) != 0) { printf(" FAILED\n"); expect_fail++; r = 0; } \
  else { printf(" PASSED\n"); expect_succ++; } \
  expect++; \
  })
#define EXPECT_TRUE(cmd) ({ \
  printf("%s: EXPECT_TRUE(" #cmd ") => ", __func__); \
  if ((cmd) != 1) { printf(" FAILED\n"); expect_fail++; r = 0; } \
  else { printf(" PASSED\n"); expect_succ++; } \
  expect++;  \
  })

static int expect = 0, expect_succ = 0, expect_fail = 0;

/***** ADD *****/
int T_add_s8() {
  int r=1;
  int8_t a, b;
  a=SCHAR_MIN; b=-1; EXPECT_FALSE(safe_add(NULL, a, b));
  a=SCHAR_MAX; b=1; EXPECT_FALSE(safe_add(NULL, a, b));
  a=10; b=11; EXPECT_TRUE(safe_add(NULL, a, b));
  a=-10; b=-11; EXPECT_TRUE(safe_add(NULL, a, b));
  a=SCHAR_MIN; b=SCHAR_MAX; EXPECT_TRUE(safe_add(NULL, a, b));
  a=SCHAR_MIN+1; b=-1; EXPECT_TRUE(safe_add(NULL, a, b));
  a=SCHAR_MAX/2; b=SCHAR_MAX/2; EXPECT_TRUE(safe_add(NULL, a, b));
  return r;
}

int T_add_s16() {
  int r=1;
  int16_t a, b;
  a=SHRT_MIN; b=-1; EXPECT_FALSE(safe_add(NULL, a, b));
  a=SHRT_MAX; b=1; EXPECT_FALSE(safe_add(NULL, a, b));
  a=10; b=11; EXPECT_TRUE(safe_add(NULL, a, b));
  a=SHRT_MIN; b=SHRT_MAX; EXPECT_TRUE(safe_add(NULL, a, b));
  a=SHRT_MAX/2; b=SHRT_MAX/2; EXPECT_TRUE(safe_add(NULL, a, b));
  return r;
}

int T_add_s32() {
  int r=1;
  int32_t a, b;
  a=INT_MIN; b=-1; EXPECT_FALSE(safe_add(NULL, a, b));
  a=INT_MAX; b=1; EXPECT_FALSE(safe_add(NULL, a, b));
  a=10; b=11; EXPECT_TRUE(safe_add(NULL, a, b));
  a=INT_MIN; b=INT_MAX; EXPECT_TRUE(safe_add(NULL, a, b));
  a=INT_MAX/2; b=INT_MAX/2; EXPECT_TRUE(safe_add(NULL, a, b));
  return r;
}

int T_add_s64() {
  int r=1;
  int64_t a, b;
  a=SAFE_INT64_MIN; b=-1; EXPECT_FALSE(safe_add(NULL, a, b));
  a=SAFE_INT64_MAX; b=1; EXPECT_FALSE(safe_add(NULL, a, b));
  a=10; b=11; EXPECT_TRUE(safe_add(NULL, a, b));
  a=SAFE_INT64_MIN; b=SAFE_INT64_MAX; EXPECT_TRUE(safe_add(NULL, a, b));
  a=SAFE_INT64_MAX/2; b=SAFE_INT64_MAX/2; EXPECT_TRUE(safe_add(NULL, a, b));
  return r;
}

int T_add_long() {
  int r=1;
  long a, b;
  a=LONG_MIN; b=-1; EXPECT_FALSE(safe_add(NULL, a, b));
  a=LONG_MAX; b=1; EXPECT_FALSE(safe_add(NULL, a, b));
  a=10; b=11; EXPECT_TRUE(safe_add(NULL, a, b));
  a=LONG_MIN; b=LONG_MAX; EXPECT_TRUE(safe_add(NULL, a, b));
  a=LONG_MAX/2; b=LONG_MAX/2; EXPECT_TRUE(safe_add(NULL, a, b));
  return r;
}
int T_add_longlong() {
  int r=1;
  long long a, b;
  a=LLONG_MIN; b=-1; EXPECT_FALSE(safe_add(NULL, a, b));
  a=LLONG_MAX; b=1; EXPECT_FALSE(safe_add(NULL, a, b));
  a=10; b=11; EXPECT_TRUE(safe_add(NULL, a, b));
  a=LLONG_MIN; b=LLONG_MAX; EXPECT_TRUE(safe_add(NULL, a, b));
  a=LLONG_MAX/2; b=LLONG_MAX/2; EXPECT_TRUE(safe_add(NULL, a, b));
  return r;
}
int T_add_ssizet() {
  int r=1;
  ssize_t a, b;
  a=SSIZE_MIN; b=-1; EXPECT_FALSE(safe_add(NULL, a, b));
  a=SSIZE_MAX; b=1; EXPECT_FALSE(safe_add(NULL, a, b));
  a=10; b=11; EXPECT_TRUE(safe_add(NULL, a, b));
  a=SSIZE_MIN; b=SSIZE_MAX; EXPECT_TRUE(safe_add(NULL, a, b));
  a=SSIZE_MAX/2; b=SSIZE_MAX/2; EXPECT_TRUE(safe_add(NULL, a, b));
  return r;
}

int T_add_u8() {
  int r=1;
  uint8_t a, b;
  a=1; b=UCHAR_MAX; EXPECT_FALSE(safe_add(NULL, a, b));
  a=UCHAR_MAX/2; b=a+2; EXPECT_FALSE(safe_add(NULL, a, b));
  a=UCHAR_MAX/2; b=a; EXPECT_TRUE(safe_add(NULL, a, b));
  a=UCHAR_MAX/2; b=a+1; EXPECT_TRUE(safe_add(NULL, a, b));
  a=10; b=11; EXPECT_TRUE(safe_add(NULL, a, b));
  a=0; b=UCHAR_MAX; EXPECT_TRUE(safe_add(NULL, a, b));
  return r;
}

int T_add_u16() {
  int r=1;
  uint16_t a, b;
  a=1; b=USHRT_MAX; EXPECT_FALSE(safe_add(NULL, a, b));
  a=USHRT_MAX/2; b=a+2; EXPECT_FALSE(safe_add(NULL, a, b));
  a=USHRT_MAX/2; b=a; EXPECT_TRUE(safe_add(NULL, a, b));
  a=USHRT_MAX/2; b=a+1; EXPECT_TRUE(safe_add(NULL, a, b));
  a=10; b=11; EXPECT_TRUE(safe_add(NULL, a, b));
  a=0; b=USHRT_MAX; EXPECT_TRUE(safe_add(NULL, a, b));
  return r;
}

int T_add_u32() {
  int r=1;
  uint32_t a, b;
  a=1; b=UINT_MAX; EXPECT_FALSE(safe_add(NULL, a, b));
  a=UINT_MAX/2; b=a+2; EXPECT_FALSE(safe_add(NULL, a, b));
  a=UINT_MAX/2; b=a; EXPECT_TRUE(safe_add(NULL, a, b));
  a=UINT_MAX/2; b=a+1; EXPECT_TRUE(safe_add(NULL, a, b));
  a=10; b=11; EXPECT_TRUE(safe_add(NULL, a, b));
  a=0; b=UINT_MAX; EXPECT_TRUE(safe_add(NULL, a, b));
  return r;
}

int T_add_u64() {
  int r=1;
  uint64_t a, b;
  a=1; b=SAFE_UINT64_MAX; EXPECT_FALSE(safe_add(NULL, a, b));
  a=SAFE_UINT64_MAX/2; b=a+2; EXPECT_FALSE(safe_add(NULL, a, b));
  a=SAFE_UINT64_MAX/2; b=a; EXPECT_TRUE(safe_add(NULL, a, b));
  a=SAFE_UINT64_MAX/2; b=a+1; EXPECT_TRUE(safe_add(NULL, a, b));
  a=10; b=11; EXPECT_TRUE(safe_add(NULL, a, b));
  a=0; b=SAFE_UINT64_MAX; EXPECT_TRUE(safe_add(NULL, a, b));
  return r;
}

int T_add_ulong() {
  int r=1;
  unsigned long a, b;
  a=1; b=ULONG_MAX; EXPECT_FALSE(safe_add(NULL, a, b));
  a=ULONG_MAX/2; b=a+2; EXPECT_FALSE(safe_add(NULL, a, b));
  a=ULONG_MAX/2; b=a; EXPECT_TRUE(safe_add(NULL, a, b));
  a=ULONG_MAX/2; b=a+1; EXPECT_TRUE(safe_add(NULL, a, b));
  a=10; b=11; EXPECT_TRUE(safe_add(NULL, a, b));
  a=0; b=ULONG_MAX; EXPECT_TRUE(safe_add(NULL, a, b));
  return r;
}

int T_add_ulonglong() {
  int r=1;
  unsigned long long a, b;
  a=1; b=ULLONG_MAX; EXPECT_FALSE(safe_add(NULL, a, b));
  a=ULLONG_MAX/2; b=a+2; EXPECT_FALSE(safe_add(NULL, a, b));
  a=ULLONG_MAX/2; b=a; EXPECT_TRUE(safe_add(NULL, a, b));
  a=ULLONG_MAX/2; b=a+1; EXPECT_TRUE(safe_add(NULL, a, b));
  a=10; b=11; EXPECT_TRUE(safe_add(NULL, a, b));
  a=0; b=ULLONG_MAX; EXPECT_TRUE(safe_add(NULL, a, b));
  return r;
}

int T_add_sizet() {
  int r=1;
  size_t a, b;
  a=1; b=SIZE_MAX; EXPECT_FALSE(safe_add(NULL, a, b));
  a=SIZE_MAX/2; b=a+2; EXPECT_FALSE(safe_add(NULL, a, b));
  a=SIZE_MAX/2; b=a; EXPECT_TRUE(safe_add(NULL, a, b));
  a=SIZE_MAX/2; b=a+1; EXPECT_TRUE(safe_add(NULL, a, b));
  a=10; b=11; EXPECT_TRUE(safe_add(NULL, a, b));
  a=0; b=SIZE_MAX; EXPECT_TRUE(safe_add(NULL, a, b));
  return r;
}

int T_add_mixed() {
  int r=1;
  int8_t a = 1;
  uint8_t b = 2;
  uint16_t c = 3;
  EXPECT_FALSE(safe_add(NULL, a, b));
  EXPECT_FALSE(safe_add(NULL, b, c));
  EXPECT_FALSE(safe_add(NULL, a, c));
  EXPECT_FALSE(safe_add3(NULL, a, b, c));
  return r;
}

int T_add_increment() {
  int r=1;
  uint16_t a = 1, b = 2, c = 0, d[2]= {0};
  uint16_t *cur = d;
  EXPECT_TRUE(safe_add(cur++, a++, b));
  EXPECT_TRUE(cur == &d[1]);
  EXPECT_TRUE(d[0] == 3);
  EXPECT_TRUE(a == 2);
  a = 1; b = 2; c = 1; cur=d;d[0] = 0;
  EXPECT_TRUE(safe_add3(cur++, a++, b++, c));
  EXPECT_TRUE(d[0] == 4);
  EXPECT_TRUE(cur == &d[1]);
  EXPECT_TRUE(a == 2);
  EXPECT_TRUE(b == 3);
  EXPECT_TRUE(c == 1);
  return r;
}



/***** SUB *****/
int T_sub_s8() {
  int r=1;
  int8_t a, b;
  a=SCHAR_MIN; b=1; EXPECT_FALSE(safe_sub(NULL, a, b));
  a=SCHAR_MIN; b=SCHAR_MAX; EXPECT_FALSE(safe_sub(NULL, a, b));
  a=SCHAR_MIN/2; b=SCHAR_MAX; EXPECT_FALSE(safe_sub(NULL, a, b));
  a=-2; b=SCHAR_MAX; EXPECT_FALSE(safe_sub(NULL, a, b));
  a=SCHAR_MAX; b=SCHAR_MAX; EXPECT_TRUE(safe_sub(NULL, a, b));
  a=10; b=2; EXPECT_TRUE(safe_sub(NULL, a, b));
  a=2; b=10; EXPECT_TRUE(safe_sub(NULL, a, b));
  return r;
}

int T_sub_s16() {
  int r=1;
  int16_t a, b;
  a=SHRT_MIN; b=1; EXPECT_FALSE(safe_sub(NULL, a, b));
  a=SHRT_MIN; b=SHRT_MAX; EXPECT_FALSE(safe_sub(NULL, a, b));
  a=SHRT_MIN/2; b=SHRT_MAX; EXPECT_FALSE(safe_sub(NULL, a, b));
  a=-2; b=SHRT_MAX; EXPECT_FALSE(safe_sub(NULL, a, b));
  a=SHRT_MAX; b=SHRT_MAX; EXPECT_TRUE(safe_sub(NULL, a, b));
  a=10; b=2; EXPECT_TRUE(safe_sub(NULL, a, b));
  a=2; b=10; EXPECT_TRUE(safe_sub(NULL, a, b));
  return r;
}

int T_sub_s32() {
  int r=1;
  int32_t a, b;
  a=INT_MIN; b=1; EXPECT_FALSE(safe_sub(NULL, a, b));
  a=INT_MIN; b=INT_MAX; EXPECT_FALSE(safe_sub(NULL, a, b));
  a=INT_MIN/2; b=INT_MAX; EXPECT_FALSE(safe_sub(NULL, a, b));
  a=-2; b=INT_MAX; EXPECT_FALSE(safe_sub(NULL, a, b));
  a=INT_MAX; b=INT_MAX; EXPECT_TRUE(safe_sub(NULL, a, b));
  a=10; b=2; EXPECT_TRUE(safe_sub(NULL, a, b));
  a=2; b=10; EXPECT_TRUE(safe_sub(NULL, a, b));
  return r;
}

int T_sub_s64() {
  int r=1;
  int64_t a, b;
  a=SAFE_INT64_MIN; b=1; EXPECT_FALSE(safe_sub(NULL, a, b));
  a=SAFE_INT64_MIN; b=SAFE_INT64_MAX; EXPECT_FALSE(safe_sub(NULL, a, b));
  a=SAFE_INT64_MIN/2; b=SAFE_INT64_MAX; EXPECT_FALSE(safe_sub(NULL, a, b));
  a=-2; b=SAFE_INT64_MAX; EXPECT_FALSE(safe_sub(NULL, a, b));
  a=SAFE_INT64_MAX; b=SAFE_INT64_MAX; EXPECT_TRUE(safe_sub(NULL, a, b));
  a=10; b=2; EXPECT_TRUE(safe_sub(NULL, a, b));
  a=2; b=10; EXPECT_TRUE(safe_sub(NULL, a, b));
  return r;
}

int T_sub_long() {
  int r=1;
  long a, b;
  a=LONG_MIN; b=1; EXPECT_FALSE(safe_sub(NULL, a, b));
  a=LONG_MIN; b=LONG_MAX; EXPECT_FALSE(safe_sub(NULL, a, b));
  a=LONG_MIN/2; b=LONG_MAX; EXPECT_FALSE(safe_sub(NULL, a, b));
  a=-2; b=LONG_MAX; EXPECT_FALSE(safe_sub(NULL, a, b));
  a=LONG_MAX; b=LONG_MAX; EXPECT_TRUE(safe_sub(NULL, a, b));
  a=10; b=2; EXPECT_TRUE(safe_sub(NULL, a, b));
  a=2; b=10; EXPECT_TRUE(safe_sub(NULL, a, b));
  return r;
}

int T_sub_longlong() {
  int r=1;
  long long a, b;
  a=LLONG_MIN; b=1; EXPECT_FALSE(safe_sub(NULL, a, b));
  a=LLONG_MIN; b=LLONG_MAX; EXPECT_FALSE(safe_sub(NULL, a, b));
  a=LLONG_MIN/2; b=LLONG_MAX; EXPECT_FALSE(safe_sub(NULL, a, b));
  a=-2; b=LLONG_MAX; EXPECT_FALSE(safe_sub(NULL, a, b));
  a=LLONG_MAX; b=LLONG_MAX; EXPECT_TRUE(safe_sub(NULL, a, b));
  a=10; b=2; EXPECT_TRUE(safe_sub(NULL, a, b));
  a=2; b=10; EXPECT_TRUE(safe_sub(NULL, a, b));
  return r;
}

int T_sub_ssizet() {
  int r=1;
  ssize_t a, b;
  a=SSIZE_MIN; b=1; EXPECT_FALSE(safe_sub(NULL, a, b));
  a=SSIZE_MIN; b=SSIZE_MAX; EXPECT_FALSE(safe_sub(NULL, a, b));
  a=SSIZE_MIN/2; b=SSIZE_MAX; EXPECT_FALSE(safe_sub(NULL, a, b));
  a=-2; b=SSIZE_MAX; EXPECT_FALSE(safe_sub(NULL, a, b));
  a=SSIZE_MAX; b=SSIZE_MAX; EXPECT_TRUE(safe_sub(NULL, a, b));
  a=10; b=2; EXPECT_TRUE(safe_sub(NULL, a, b));
  a=2; b=10; EXPECT_TRUE(safe_sub(NULL, a, b));
  return r;
}

int T_sub_u8() {
  int r=1;
  uint8_t a, b;
  a=0; b=UCHAR_MAX; EXPECT_FALSE(safe_sub(NULL, a, b));
  a=UCHAR_MAX-1; b=UCHAR_MAX; EXPECT_FALSE(safe_sub(NULL, a, b));
  a=UCHAR_MAX; b=UCHAR_MAX; EXPECT_TRUE(safe_sub(NULL, a, b));
  a=1; b=100; EXPECT_FALSE(safe_sub(NULL, a, b));
  a=100; b=0; EXPECT_TRUE(safe_sub(NULL, a, b));
  a=10; b=2; EXPECT_TRUE(safe_sub(NULL, a, b));
  a=0; b=0; EXPECT_TRUE(safe_sub(NULL, a, b));
  return r;
}

int T_sub_u16() {
  int r=1;
  uint16_t a, b;
  a=0; b=USHRT_MAX; EXPECT_FALSE(safe_sub(NULL, a, b));
  a=USHRT_MAX-1; b=USHRT_MAX; EXPECT_FALSE(safe_sub(NULL, a, b));
  a=USHRT_MAX; b=USHRT_MAX; EXPECT_TRUE(safe_sub(NULL, a, b));
  a=1; b=100; EXPECT_FALSE(safe_sub(NULL, a, b));
  a=100; b=0; EXPECT_TRUE(safe_sub(NULL, a, b));
  a=10; b=2; EXPECT_TRUE(safe_sub(NULL, a, b));
  a=0; b=0; EXPECT_TRUE(safe_sub(NULL, a, b));
  return r;
}

int T_sub_u32() {
  int r=1;
  uint32_t a, b;
  a=UINT_MAX-1; b=UINT_MAX; EXPECT_FALSE(safe_sub(NULL, a, b));
  a=UINT_MAX; b=UINT_MAX; EXPECT_TRUE(safe_sub(NULL, a, b));
  a=1; b=100; EXPECT_FALSE(safe_sub(NULL, a, b));
  a=100; b=0; EXPECT_TRUE(safe_sub(NULL, a, b));
  a=10; b=2; EXPECT_TRUE(safe_sub(NULL, a, b));
  a=0; b=0; EXPECT_TRUE(safe_sub(NULL, a, b));
  return r;
}

int T_sub_u64() {
  int r=1;
  uint64_t a, b;
  a=SAFE_UINT64_MAX-1; b=SAFE_UINT64_MAX; EXPECT_FALSE(safe_sub(NULL, a, b));
  a=SAFE_UINT64_MAX; b=SAFE_UINT64_MAX; EXPECT_TRUE(safe_sub(NULL, a, b));
  a=1; b=100; EXPECT_FALSE(safe_sub(NULL, a, b));
  a=100; b=0; EXPECT_TRUE(safe_sub(NULL, a, b));
  a=10; b=2; EXPECT_TRUE(safe_sub(NULL, a, b));
  a=0; b=0; EXPECT_TRUE(safe_sub(NULL, a, b));
  return r;
}

int T_sub_ulong() {
  int r=1;
  unsigned long a, b;
  a=ULONG_MAX-1; b=ULONG_MAX; EXPECT_FALSE(safe_sub(NULL, a, b));
  a=ULONG_MAX; b=ULONG_MAX; EXPECT_TRUE(safe_sub(NULL, a, b));
  a=1; b=100; EXPECT_FALSE(safe_sub(NULL, a, b));
  a=100; b=0; EXPECT_TRUE(safe_sub(NULL, a, b));
  a=10; b=2; EXPECT_TRUE(safe_sub(NULL, a, b));
  a=0; b=0; EXPECT_TRUE(safe_sub(NULL, a, b));
  return r;
}

int T_sub_ulonglong() {
  int r=1;
  unsigned long long a, b;
  a=ULLONG_MAX-1; b=ULLONG_MAX; EXPECT_FALSE(safe_sub(NULL, a, b));
  a=ULLONG_MAX; b=ULLONG_MAX; EXPECT_TRUE(safe_sub(NULL, a, b));
  a=1; b=100; EXPECT_FALSE(safe_sub(NULL, a, b));
  a=100; b=0; EXPECT_TRUE(safe_sub(NULL, a, b));
  a=10; b=2; EXPECT_TRUE(safe_sub(NULL, a, b));
  a=0; b=0; EXPECT_TRUE(safe_sub(NULL, a, b));
  return r;
}

int T_sub_sizet() {
  int r=1;
  size_t a, b;
  a=SIZE_MAX-1; b=SIZE_MAX; EXPECT_FALSE(safe_sub(NULL, a, b));
  a=SIZE_MAX; b=SIZE_MAX; EXPECT_TRUE(safe_sub(NULL, a, b));
  a=1; b=100; EXPECT_FALSE(safe_sub(NULL, a, b));
  a=100; b=0; EXPECT_TRUE(safe_sub(NULL, a, b));
  a=10; b=2; EXPECT_TRUE(safe_sub(NULL, a, b));
  a=0; b=0; EXPECT_TRUE(safe_sub(NULL, a, b));
  return r;
}

/***** MUL *****/
int T_mul_s8() {
  int r=1;
  int8_t a, b;
  a=SCHAR_MIN; b=-1; EXPECT_FALSE(safe_mul(NULL, a, b));
  a=SCHAR_MIN; b=-2; EXPECT_FALSE(safe_mul(NULL, a, b));
  a=SCHAR_MAX; b=SCHAR_MAX; EXPECT_FALSE(safe_mul(NULL, a, b));
  a=SCHAR_MAX/2+1; b=2; EXPECT_FALSE(safe_mul(NULL, a, b));
  a=SCHAR_MAX/2; b=2; EXPECT_TRUE(safe_mul(NULL, a, b));
  a=100; b=0; EXPECT_TRUE(safe_mul(NULL, a, b));
  a=10; b=2; EXPECT_TRUE(safe_mul(NULL, a, b));
  a=SCHAR_MAX; b=0; EXPECT_TRUE(safe_mul(NULL, a, b));
  a=SCHAR_MIN; b=0; EXPECT_TRUE(safe_mul(NULL, a, b));
  a=0; b=SCHAR_MAX; EXPECT_TRUE(safe_mul(NULL, a, b));
  a=0; b=SCHAR_MIN; EXPECT_TRUE(safe_mul(NULL, a, b));
  a=0; b=0; EXPECT_TRUE(safe_mul(NULL, a, b));
  return r;
}

int T_mul_s16() {
  int r=1;
  int16_t a, b;
  a=SHRT_MIN; b=-1; EXPECT_FALSE(safe_mul(NULL, a, b));
  a=SHRT_MIN; b=-2; EXPECT_FALSE(safe_mul(NULL, a, b));
  a=SHRT_MAX; b=SHRT_MAX; EXPECT_FALSE(safe_mul(NULL, a, b));
  a=SHRT_MAX/2+1; b=2; EXPECT_FALSE(safe_mul(NULL, a, b));
  a=SHRT_MAX/2; b=2; EXPECT_TRUE(safe_mul(NULL, a, b));
  a=100; b=0; EXPECT_TRUE(safe_mul(NULL, a, b));
  a=10; b=2; EXPECT_TRUE(safe_mul(NULL, a, b));
  a=SHRT_MAX; b=0; EXPECT_TRUE(safe_mul(NULL, a, b));
  a=SHRT_MIN; b=0; EXPECT_TRUE(safe_mul(NULL, a, b));
  a=0; b=SHRT_MAX; EXPECT_TRUE(safe_mul(NULL, a, b));
  a=0; b=SHRT_MIN; EXPECT_TRUE(safe_mul(NULL, a, b));
  a=0; b=0; EXPECT_TRUE(safe_mul(NULL, a, b));
  return r;
}

int T_mul_s32() {
  int r=1;
  int32_t a, b;
  a=INT_MIN; b=-1; EXPECT_FALSE(safe_mul(NULL, a, b));
  a=INT_MIN; b=-2; EXPECT_FALSE(safe_mul(NULL, a, b));
  a=INT_MAX; b=INT_MAX; EXPECT_FALSE(safe_mul(NULL, a, b));
  a=INT_MAX/2+1; b=2; EXPECT_FALSE(safe_mul(NULL, a, b));
  a=INT_MAX/2; b=2; EXPECT_TRUE(safe_mul(NULL, a, b));
  a=100; b=0; EXPECT_TRUE(safe_mul(NULL, a, b));
  a=10; b=2; EXPECT_TRUE(safe_mul(NULL, a, b));
  a=INT_MAX; b=0; EXPECT_TRUE(safe_mul(NULL, a, b));
  a=INT_MIN; b=0; EXPECT_TRUE(safe_mul(NULL, a, b));
  a=0; b=INT_MAX; EXPECT_TRUE(safe_mul(NULL, a, b));
  a=0; b=INT_MIN; EXPECT_TRUE(safe_mul(NULL, a, b));
  a=0; b=0; EXPECT_TRUE(safe_mul(NULL, a, b));
  return r;
}

int T_mul_s64() {
  int r=1;
  int64_t a, b;
  a=SAFE_INT64_MIN; b=-1; EXPECT_FALSE(safe_mul(NULL, a, b));
  a=SAFE_INT64_MIN; b=-2; EXPECT_FALSE(safe_mul(NULL, a, b));
  a=SAFE_INT64_MAX; b=SAFE_INT64_MAX; EXPECT_FALSE(safe_mul(NULL, a, b));
  a=SAFE_INT64_MAX/2+1; b=2; EXPECT_FALSE(safe_mul(NULL, a, b));
  a=SAFE_INT64_MAX/2; b=2; EXPECT_TRUE(safe_mul(NULL, a, b));
  a=100; b=0; EXPECT_TRUE(safe_mul(NULL, a, b));
  a=10; b=2; EXPECT_TRUE(safe_mul(NULL, a, b));
  a=SAFE_INT64_MAX; b=0; EXPECT_TRUE(safe_mul(NULL, a, b));
  a=SAFE_INT64_MIN; b=0; EXPECT_TRUE(safe_mul(NULL, a, b));
  a=0; b=SAFE_INT64_MAX; EXPECT_TRUE(safe_mul(NULL, a, b));
  a=0; b=SAFE_INT64_MIN; EXPECT_TRUE(safe_mul(NULL, a, b));
  a=0; b=0; EXPECT_TRUE(safe_mul(NULL, a, b));
  return r;
}

int T_mul_long() {
  int r=1;
  long a, b;
  a=LONG_MIN; b=-1; EXPECT_FALSE(safe_mul(NULL, a, b));
  a=LONG_MIN; b=-2; EXPECT_FALSE(safe_mul(NULL, a, b));
  a=LONG_MAX; b=LONG_MAX; EXPECT_FALSE(safe_mul(NULL, a, b));
  a=LONG_MAX/2+1; b=2; EXPECT_FALSE(safe_mul(NULL, a, b));
  a=LONG_MAX/2; b=2; EXPECT_TRUE(safe_mul(NULL, a, b));
  a=100; b=0; EXPECT_TRUE(safe_mul(NULL, a, b));
  a=10; b=2; EXPECT_TRUE(safe_mul(NULL, a, b));
  a=LONG_MAX; b=0; EXPECT_TRUE(safe_mul(NULL, a, b));
  a=LONG_MIN; b=0; EXPECT_TRUE(safe_mul(NULL, a, b));
  a=0; b=LONG_MAX; EXPECT_TRUE(safe_mul(NULL, a, b));
  a=0; b=LONG_MIN; EXPECT_TRUE(safe_mul(NULL, a, b));
  a=0; b=0; EXPECT_TRUE(safe_mul(NULL, a, b));
  return r;
}
int T_mul_longlong() {
  int r=1;
  long long a, b;
  a=LLONG_MIN; b=-1; EXPECT_FALSE(safe_mul(NULL, a, b));
  a=LLONG_MIN; b=-2; EXPECT_FALSE(safe_mul(NULL, a, b));
  a=LLONG_MAX; b=LLONG_MAX; EXPECT_FALSE(safe_mul(NULL, a, b));
  a=LLONG_MAX/2+1; b=2; EXPECT_FALSE(safe_mul(NULL, a, b));
  a=LLONG_MAX/2; b=2; EXPECT_TRUE(safe_mul(NULL, a, b));
  a=100; b=0; EXPECT_TRUE(safe_mul(NULL, a, b));
  a=10; b=2; EXPECT_TRUE(safe_mul(NULL, a, b));
  a=LLONG_MAX; b=0; EXPECT_TRUE(safe_mul(NULL, a, b));
  a=LLONG_MIN; b=0; EXPECT_TRUE(safe_mul(NULL, a, b));
  a=0; b=LLONG_MAX; EXPECT_TRUE(safe_mul(NULL, a, b));
  a=0; b=LLONG_MIN; EXPECT_TRUE(safe_mul(NULL, a, b));
  a=0; b=0; EXPECT_TRUE(safe_mul(NULL, a, b));
  return r;
}
int T_mul_ssizet() {
  int r=1;
  ssize_t a, b;
  a=SSIZE_MIN; b=-1; EXPECT_FALSE(safe_mul(NULL, a, b));
  a=SSIZE_MIN; b=-2; EXPECT_FALSE(safe_mul(NULL, a, b));
  a=SSIZE_MAX; b=SSIZE_MAX; EXPECT_FALSE(safe_mul(NULL, a, b));
  a=SSIZE_MAX/2+1; b=2; EXPECT_FALSE(safe_mul(NULL, a, b));
  a=SSIZE_MAX/2; b=2; EXPECT_TRUE(safe_mul(NULL, a, b));
  a=100; b=0; EXPECT_TRUE(safe_mul(NULL, a, b));
  a=10; b=2; EXPECT_TRUE(safe_mul(NULL, a, b));
  a=SSIZE_MAX; b=0; EXPECT_TRUE(safe_mul(NULL, a, b));
  a=SSIZE_MIN; b=0; EXPECT_TRUE(safe_mul(NULL, a, b));
  a=0; b=SSIZE_MAX; EXPECT_TRUE(safe_mul(NULL, a, b));
  a=0; b=SSIZE_MIN; EXPECT_TRUE(safe_mul(NULL, a, b));
  a=0; b=0; EXPECT_TRUE(safe_mul(NULL, a, b));
  return r;
}

int T_mul_u8() {
  int r=1;
  uint8_t a, b;
  a=UCHAR_MAX-1; b=2; EXPECT_FALSE(safe_mul(NULL, a, b));
  a=2; b=UCHAR_MAX-1; EXPECT_FALSE(safe_mul(NULL, a, b));
  a=UCHAR_MAX; b=2; EXPECT_FALSE(safe_mul(NULL, a, b));
  a=2; b=UCHAR_MAX; EXPECT_FALSE(safe_mul(NULL, a, b));
  a=UCHAR_MAX/2+1; b=2; EXPECT_FALSE(safe_mul(NULL, a, b));
  a=2; b=UCHAR_MAX/2+1; EXPECT_FALSE(safe_mul(NULL, a, b));
  a=UCHAR_MAX/2; b=2; EXPECT_TRUE(safe_mul(NULL, a, b));
  a=0; b=UCHAR_MAX; EXPECT_TRUE(safe_mul(NULL, a, b));
  a=1; b=UCHAR_MAX; EXPECT_TRUE(safe_mul(NULL, a, b));
  a=UCHAR_MAX; b=0; EXPECT_TRUE(safe_mul(NULL, a, b));
  a=UCHAR_MAX; b=1; EXPECT_TRUE(safe_mul(NULL, a, b));
  a=10; b=2; EXPECT_TRUE(safe_mul(NULL, a, b));
  return r;
}

int T_mul_u16() {
  int r=1;
  uint16_t a, b;
  a=USHRT_MAX-1; b=2; EXPECT_FALSE(safe_mul(NULL, a, b));
  a=2; b=USHRT_MAX-1; EXPECT_FALSE(safe_mul(NULL, a, b));
  a=USHRT_MAX; b=2; EXPECT_FALSE(safe_mul(NULL, a, b));
  a=2; b=USHRT_MAX; EXPECT_FALSE(safe_mul(NULL, a, b));
  a=USHRT_MAX/2+1; b=2; EXPECT_FALSE(safe_mul(NULL, a, b));
  a=2; b=USHRT_MAX/2+1; EXPECT_FALSE(safe_mul(NULL, a, b));
  a=USHRT_MAX/2; b=2; EXPECT_TRUE(safe_mul(NULL, a, b));
  a=0; b=USHRT_MAX; EXPECT_TRUE(safe_mul(NULL, a, b));
  a=1; b=USHRT_MAX; EXPECT_TRUE(safe_mul(NULL, a, b));
  a=USHRT_MAX; b=0; EXPECT_TRUE(safe_mul(NULL, a, b));
  a=USHRT_MAX; b=1; EXPECT_TRUE(safe_mul(NULL, a, b));
  a=10; b=2; EXPECT_TRUE(safe_mul(NULL, a, b));
  return r;
}

int T_mul_u32() {
  int r=1;
  uint32_t a, b;
  a=UINT_MAX-1; b=2; EXPECT_FALSE(safe_mul(NULL, a, b));
  a=2; b=UINT_MAX-1; EXPECT_FALSE(safe_mul(NULL, a, b));
  a=UINT_MAX; b=2; EXPECT_FALSE(safe_mul(NULL, a, b));
  a=2; b=UINT_MAX; EXPECT_FALSE(safe_mul(NULL, a, b));
  a=UINT_MAX/2+1; b=2; EXPECT_FALSE(safe_mul(NULL, a, b));
  a=2; b=UINT_MAX/2+1; EXPECT_FALSE(safe_mul(NULL, a, b));
  a=UINT_MAX/2; b=2; EXPECT_TRUE(safe_mul(NULL, a, b));
  a=0; b=UINT_MAX; EXPECT_TRUE(safe_mul(NULL, a, b));
  a=1; b=UINT_MAX; EXPECT_TRUE(safe_mul(NULL, a, b));
  a=UINT_MAX; b=0; EXPECT_TRUE(safe_mul(NULL, a, b));
  a=UINT_MAX; b=1; EXPECT_TRUE(safe_mul(NULL, a, b));
  a=10; b=2; EXPECT_TRUE(safe_mul(NULL, a, b));
  return r;
}

int T_mul_u64() {
  int r=1;
  uint64_t a, b;
  a=SAFE_UINT64_MAX-1; b=2; EXPECT_FALSE(safe_mul(NULL, a, b));
  a=2; b=SAFE_UINT64_MAX-1; EXPECT_FALSE(safe_mul(NULL, a, b));
  a=SAFE_UINT64_MAX; b=2; EXPECT_FALSE(safe_mul(NULL, a, b));
  a=2; b=SAFE_UINT64_MAX; EXPECT_FALSE(safe_mul(NULL, a, b));
  a=SAFE_UINT64_MAX/2+1; b=2; EXPECT_FALSE(safe_mul(NULL, a, b));
  a=2; b=SAFE_UINT64_MAX/2+1; EXPECT_FALSE(safe_mul(NULL, a, b));
  a=SAFE_UINT64_MAX/2; b=2; EXPECT_TRUE(safe_mul(NULL, a, b));
  a=0; b=SAFE_UINT64_MAX; EXPECT_TRUE(safe_mul(NULL, a, b));
  a=1; b=SAFE_UINT64_MAX; EXPECT_TRUE(safe_mul(NULL, a, b));
  a=SAFE_UINT64_MAX; b=0; EXPECT_TRUE(safe_mul(NULL, a, b));
  a=SAFE_UINT64_MAX; b=1; EXPECT_TRUE(safe_mul(NULL, a, b));
  a=10; b=2; EXPECT_TRUE(safe_mul(NULL, a, b));
  return r;
}

int T_mul_ulong() {
  int r=1;
  unsigned long a, b;
  a=ULONG_MAX-1; b=2; EXPECT_FALSE(safe_mul(NULL, a, b));
  a=2; b=ULONG_MAX-1; EXPECT_FALSE(safe_mul(NULL, a, b));
  a=ULONG_MAX; b=2; EXPECT_FALSE(safe_mul(NULL, a, b));
  a=2; b=ULONG_MAX; EXPECT_FALSE(safe_mul(NULL, a, b));
  a=ULONG_MAX/2+1; b=2; EXPECT_FALSE(safe_mul(NULL, a, b));
  a=2; b=ULONG_MAX/2+1; EXPECT_FALSE(safe_mul(NULL, a, b));
  a=ULONG_MAX/2; b=2; EXPECT_TRUE(safe_mul(NULL, a, b));
  a=0; b=ULONG_MAX; EXPECT_TRUE(safe_mul(NULL, a, b));
  a=1; b=ULONG_MAX; EXPECT_TRUE(safe_mul(NULL, a, b));
  a=ULONG_MAX; b=0; EXPECT_TRUE(safe_mul(NULL, a, b));
  a=ULONG_MAX; b=1; EXPECT_TRUE(safe_mul(NULL, a, b));
  a=10; b=2; EXPECT_TRUE(safe_mul(NULL, a, b));
  return r;
}

int T_mul_ulonglong() {
  int r=1;
  unsigned long long a, b;
  a=ULLONG_MAX-1; b=2; EXPECT_FALSE(safe_mul(NULL, a, b));
  a=2; b=ULLONG_MAX-1; EXPECT_FALSE(safe_mul(NULL, a, b));
  a=ULLONG_MAX; b=2; EXPECT_FALSE(safe_mul(NULL, a, b));
  a=2; b=ULLONG_MAX; EXPECT_FALSE(safe_mul(NULL, a, b));
  a=ULLONG_MAX/2+1; b=2; EXPECT_FALSE(safe_mul(NULL, a, b));
  a=2; b=ULLONG_MAX/2+1; EXPECT_FALSE(safe_mul(NULL, a, b));
  a=ULLONG_MAX/2; b=2; EXPECT_TRUE(safe_mul(NULL, a, b));
  a=0; b=ULLONG_MAX; EXPECT_TRUE(safe_mul(NULL, a, b));
  a=1; b=ULLONG_MAX; EXPECT_TRUE(safe_mul(NULL, a, b));
  a=ULLONG_MAX; b=0; EXPECT_TRUE(safe_mul(NULL, a, b));
  a=ULLONG_MAX; b=1; EXPECT_TRUE(safe_mul(NULL, a, b));
  a=10; b=2; EXPECT_TRUE(safe_mul(NULL, a, b));
  return r;
}

int T_mul_sizet() {
  int r=1;
  size_t a, b;
  a=SIZE_MAX-1; b=2; EXPECT_FALSE(safe_mul(NULL, a, b));
  a=2; b=SIZE_MAX-1; EXPECT_FALSE(safe_mul(NULL, a, b));
  a=SIZE_MAX; b=2; EXPECT_FALSE(safe_mul(NULL, a, b));
  a=2; b=SIZE_MAX; EXPECT_FALSE(safe_mul(NULL, a, b));
  a=SIZE_MAX/2+1; b=2; EXPECT_FALSE(safe_mul(NULL, a, b));
  a=2; b=SIZE_MAX/2+1; EXPECT_FALSE(safe_mul(NULL, a, b));
  a=SIZE_MAX/2; b=2; EXPECT_TRUE(safe_mul(NULL, a, b));
  a=0; b=SIZE_MAX; EXPECT_TRUE(safe_mul(NULL, a, b));
  a=1; b=SIZE_MAX; EXPECT_TRUE(safe_mul(NULL, a, b));
  a=SIZE_MAX; b=0; EXPECT_TRUE(safe_mul(NULL, a, b));
  a=SIZE_MAX; b=1; EXPECT_TRUE(safe_mul(NULL, a, b));
  a=10; b=2; EXPECT_TRUE(safe_mul(NULL, a, b));
  return r;
}

/***** MOD *****/
int T_mod_s8() {
  int r=1;
  int8_t a, b;
  a=SCHAR_MIN; b=-1; EXPECT_FALSE(safe_mod(NULL, a, b));
  a=100; b=0; EXPECT_FALSE(safe_mod(NULL, a, b));
  a=10; b=2; EXPECT_TRUE(safe_mod(NULL, a, b));
  return r;
}

int T_mod_s16() {
  int r=1;
  int16_t a, b;
  a=SHRT_MIN; b=-1; EXPECT_FALSE(safe_mod(NULL, a, b));
  a=100; b=0; EXPECT_FALSE(safe_mod(NULL, a, b));
  a=10; b=2; EXPECT_TRUE(safe_mod(NULL, a, b));
  return r;
}

int T_mod_s32() {
  int r=1;
  int32_t a, b;
  a=INT_MIN; b=-1; EXPECT_FALSE(safe_mod(NULL, a, b));
  a=100; b=0; EXPECT_FALSE(safe_mod(NULL, a, b));
  a=10; b=2; EXPECT_TRUE(safe_mod(NULL, a, b));
  return r;
}

int T_mod_s64() {
  int r=1;
  int64_t a, b;
  a=SAFE_INT64_MIN; b=-1; EXPECT_FALSE(safe_mod(NULL, a, b));
  a=100; b=0; EXPECT_FALSE(safe_mod(NULL, a, b));
  a=10; b=2; EXPECT_TRUE(safe_mod(NULL, a, b));
  return r;
}

int T_mod_long() {
  int r=1;
  long a, b;
  a=LONG_MIN; b=-1; EXPECT_FALSE(safe_mod(NULL, a, b));
  a=100; b=0; EXPECT_FALSE(safe_mod(NULL, a, b));
  a=10; b=2; EXPECT_TRUE(safe_mod(NULL, a, b));
  return r;
}
int T_mod_longlong() {
  int r=1;
  long long a, b;
  a=LLONG_MIN; b=-1LL; EXPECT_FALSE(safe_mod(NULL, a, b));
  a=100LL; b=0LL; EXPECT_FALSE(safe_mod(NULL, a, b));
  a=10LL; b=2LL; EXPECT_TRUE(safe_mod(NULL, a, b));
  return r;
}
int T_mod_ssizet() {
  int r=1;
  ssize_t a, b;
  a=SSIZE_MIN; b=-1; EXPECT_FALSE(safe_mod(NULL, a, b));
  a=100; b=0; EXPECT_FALSE(safe_mod(NULL, a, b));
  a=10; b=2; EXPECT_TRUE(safe_mod(NULL, a, b));
  return r;
}

int T_mod_u8() {
  int r=1;
  uint8_t a, b;
  a=0; b=UCHAR_MAX; EXPECT_TRUE(safe_mod(NULL, a, b));
  a=100; b=0; EXPECT_FALSE(safe_mod(NULL, a, b));
  a=10; b=2; EXPECT_TRUE(safe_mod(NULL, a, b));
  return r;
}

int T_mod_u16() {
  int r=1;
  uint16_t a, b;
  a=0; b=USHRT_MAX; EXPECT_TRUE(safe_mod(NULL, a, b));
  a=100; b=0; EXPECT_FALSE(safe_mod(NULL, a, b));
  a=10; b=2; EXPECT_TRUE(safe_mod(NULL, a, b));
  return r;
}

int T_mod_u32() {
  int r=1;
  uint32_t a, b;
  a=0; b=UINT_MAX; EXPECT_TRUE(safe_mod(NULL, a, b));
  a=100; b=0; EXPECT_FALSE(safe_mod(NULL, a, b));
  a=10; b=2; EXPECT_TRUE(safe_mod(NULL, a, b));
  return r;
}

int T_mod_u64() {
  int r=1;
  uint64_t a, b;
  a=0; b=SAFE_INT64_MAX; EXPECT_TRUE(safe_mod(NULL, a, b));
  a=100; b=0; EXPECT_FALSE(safe_mod(NULL, a, b));
  a=10; b=2; EXPECT_TRUE(safe_mod(NULL, a, b));
  return r;
}

int T_mod_ulong() {
  int r=1;
  unsigned long a, b;
  a=0; b=LONG_MAX; EXPECT_TRUE(safe_mod(NULL, a, b));
  a=100; b=0; EXPECT_FALSE(safe_mod(NULL, a, b));
  a=10; b=2; EXPECT_TRUE(safe_mod(NULL, a, b));
  return r;
}

int T_mod_ulonglong() {
  int r=1;
  unsigned long long a, b;
  a=0ULL; b=~0ULL; EXPECT_TRUE(safe_mod(NULL, a, b));
  a=100ULL; b=0ULL; EXPECT_FALSE(safe_mod(NULL, a, b));
  a=10ULL; b=2ULL; EXPECT_TRUE(safe_mod(NULL, a, b));
  return r;
}

int T_mod_sizet() {
  int r=1;
  size_t a, b;
  a=0; b=SIZE_MAX; EXPECT_TRUE(safe_mod(NULL, a, b));
  a=100; b=0; EXPECT_FALSE(safe_mod(NULL, a, b));
  a=10; b=2; EXPECT_TRUE(safe_mod(NULL, a, b));
  return r;
}

/***** DIV *****/
int T_div_s8() {
  int r=1;
  int8_t a, b;
  a=SCHAR_MIN; b=-1; EXPECT_FALSE(safe_div(NULL, a, b));
  a=100; b=0; EXPECT_FALSE(safe_div(NULL, a, b));
  a=10; b=2; EXPECT_TRUE(safe_div(NULL, a, b));
  return r;
}

int T_div_s16() {
  int r=1;
  int16_t a, b;
  a=SHRT_MIN; b=-1; EXPECT_FALSE(safe_div(NULL, a, b));
  a=100; b=0; EXPECT_FALSE(safe_div(NULL, a, b));
  a=10; b=2; EXPECT_TRUE(safe_div(NULL, a, b));
  return r;
}

int T_div_s32() {
  int r=1;
  int32_t a, b;
  a=INT_MIN; b=-1; EXPECT_FALSE(safe_div(NULL, a, b));
  a=100; b=0; EXPECT_FALSE(safe_div(NULL, a, b));
  a=10; b=2; EXPECT_TRUE(safe_div(NULL, a, b));
  return r;
}

int T_div_s64() {
  int r=1;
  int64_t a, b;
  a=SAFE_INT64_MIN; b=-1; EXPECT_FALSE(safe_div(NULL, a, b));
  a=100; b=0; EXPECT_FALSE(safe_div(NULL, a, b));
  a=10; b=2; EXPECT_TRUE(safe_div(NULL, a, b));
  return r;
}

int T_div_long() {
  int r=1;
  long a, b;
  a=LONG_MIN; b=-1; EXPECT_FALSE(safe_div(NULL, a, b));
  a=100; b=0; EXPECT_FALSE(safe_div(NULL, a, b));
  a=10; b=2; EXPECT_TRUE(safe_div(NULL, a, b));
  return r;
}
int T_div_longlong() {
  int r=1;
  long long a, b;
  a=LLONG_MIN; b=-1LL; EXPECT_FALSE(safe_div(NULL, a, b));
  a=100LL; b=0LL; EXPECT_FALSE(safe_div(NULL, a, b));
  a=10LL; b=2LL; EXPECT_TRUE(safe_div(NULL, a, b));
  return r;
}
int T_div_ssizet() {
  int r=1;
  ssize_t a, b;
  a=SSIZE_MIN; b=-1; EXPECT_FALSE(safe_div(NULL, a, b));
  a=100; b=0; EXPECT_FALSE(safe_div(NULL, a, b));
  a=10; b=2; EXPECT_TRUE(safe_div(NULL, a, b));
  return r;
}

int T_div_u8() {
  int r=1;
  uint8_t a, b;
  a=0; b=UCHAR_MAX; EXPECT_TRUE(safe_div(NULL, a, b));
  a=100; b=0; EXPECT_FALSE(safe_div(NULL, a, b));
  a=10; b=2; EXPECT_TRUE(safe_div(NULL, a, b));
  return r;
}

int T_div_u16() {
  int r=1;
  uint16_t a, b;
  a=0; b=USHRT_MAX; EXPECT_TRUE(safe_div(NULL, a, b));
  a=100; b=0; EXPECT_FALSE(safe_div(NULL, a, b));
  a=10; b=2; EXPECT_TRUE(safe_div(NULL, a, b));
  return r;
}

int T_div_u32() {
  int r=1;
  uint32_t a, b;
  a=0; b=UINT_MAX; EXPECT_TRUE(safe_div(NULL, a, b));
  a=100; b=0; EXPECT_FALSE(safe_div(NULL, a, b));
  a=10; b=2; EXPECT_TRUE(safe_div(NULL, a, b));
  return r;
}

int T_div_u64() {
  int r=1;
  uint64_t a, b;
  a=0; b=SAFE_INT64_MAX; EXPECT_TRUE(safe_div(NULL, a, b));
  a=100; b=0; EXPECT_FALSE(safe_div(NULL, a, b));
  a=10; b=2; EXPECT_TRUE(safe_div(NULL, a, b));
  return r;
}

int T_div_ulong() {
  int r=1;
  unsigned long a, b;
  a=0; b=LONG_MAX; EXPECT_TRUE(safe_div(NULL, a, b));
  a=100; b=0; EXPECT_FALSE(safe_div(NULL, a, b));
  a=10; b=2; EXPECT_TRUE(safe_div(NULL, a, b));
  return r;
}

int T_div_ulonglong() {
  int r=1;
  unsigned long long a, b;
  a=0ULL; b=~0ULL; EXPECT_TRUE(safe_div(NULL, a, b));
  a=100ULL; b=0ULL; EXPECT_FALSE(safe_div(NULL, a, b));
  a=10ULL; b=2ULL; EXPECT_TRUE(safe_div(NULL, a, b));
  return r;
}

int T_div_sizet() {
  int r=1;
  size_t a, b;
  a=0; b=SIZE_MAX; EXPECT_TRUE(safe_div(NULL, a, b));
  a=100; b=0; EXPECT_FALSE(safe_div(NULL, a, b));
  a=10; b=2; EXPECT_TRUE(safe_div(NULL, a, b));
  return r;
}

int T_magic_constants() {
  int r=1;
  EXPECT_TRUE(__sio(m)(smin)(((int8_t)0)) == SCHAR_MIN);
  EXPECT_TRUE(__sio(m)(smax)(((int8_t)0)) == SCHAR_MAX);
  EXPECT_TRUE(__sio(m)(umax)(((uint8_t)0)) == UCHAR_MAX);

  EXPECT_TRUE(__sio(m)(smin)(((int16_t)0)) == SHRT_MIN);
  EXPECT_TRUE(__sio(m)(smax)(((int16_t)0)) == SHRT_MAX);
  EXPECT_TRUE(__sio(m)(umax)(((uint16_t)0)) == USHRT_MAX);

  EXPECT_TRUE(__sio(m)(smin)(((int32_t)0)) == INT_MIN);
  EXPECT_TRUE(__sio(m)(smax)(((int32_t)0)) == INT_MAX);
  EXPECT_TRUE(__sio(m)(umax)(((uint32_t)0)) == UINT_MAX);

  EXPECT_TRUE(__sio(m)(smin)(((int64_t)0)) == SAFE_INT64_MIN);
  EXPECT_TRUE(__sio(m)(smax)(((int64_t)0)) == SAFE_INT64_MAX);
  EXPECT_TRUE(__sio(m)(umax)(((uint64_t)0)) == SAFE_UINT64_MAX);

  EXPECT_TRUE(__sio(m)(smin)(((ssize_t)0)) == SSIZE_MIN);
  EXPECT_TRUE(__sio(m)(smax)(((ssize_t)0)) == SSIZE_MAX);
  EXPECT_TRUE(__sio(m)(umax)(((size_t)0)) == SIZE_MAX);

  EXPECT_TRUE(__sio(m)(smin)(((long)0)) == LONG_MIN);
  EXPECT_TRUE(__sio(m)(smax)(((long)0)) == LONG_MAX);
  EXPECT_TRUE(__sio(m)(umax)(((unsigned long)0)) == ULONG_MAX);

  EXPECT_TRUE(__sio(m)(smin)(((long long)0)) == LLONG_MIN);
  EXPECT_TRUE(__sio(m)(smax)(((long long)0)) == LLONG_MAX);
  EXPECT_TRUE(__sio(m)(umax)(((unsigned long long)0)) == ULLONG_MAX);

  return r;
}




int main(int argc, char **argv) {
  /* test inlines */
  int tests = 0, succ = 0, fail = 0;
  tests++; if (T_div_s8())  succ++; else fail++;
  tests++; if (T_div_s16()) succ++; else fail++;
  tests++; if (T_div_s32()) succ++; else fail++;
  tests++; if (T_div_s64()) succ++; else fail++;
  tests++; if (T_div_long()) succ++; else fail++;
  tests++; if (T_div_longlong()) succ++; else fail++;
  tests++; if (T_div_ssizet()) succ++; else fail++;
  tests++; if (T_div_u8())  succ++; else fail++;
  tests++; if (T_div_u16()) succ++; else fail++;
  tests++; if (T_div_u32()) succ++; else fail++;
  tests++; if (T_div_u64()) succ++; else fail++;
  tests++; if (T_div_ulong()) succ++; else fail++;
  tests++; if (T_div_ulonglong()) succ++; else fail++;
  tests++; if (T_div_sizet()) succ++; else fail++;

  tests++; if (T_mod_s8())  succ++; else fail++;
  tests++; if (T_mod_s16()) succ++; else fail++;
  tests++; if (T_mod_s32()) succ++; else fail++;
  tests++; if (T_mod_s64()) succ++; else fail++;
  tests++; if (T_mod_long()) succ++; else fail++;
  tests++; if (T_mod_longlong()) succ++; else fail++;
  tests++; if (T_mod_ssizet()) succ++; else fail++;
  tests++; if (T_mod_u8())  succ++; else fail++;
  tests++; if (T_mod_u16()) succ++; else fail++;
  tests++; if (T_mod_u32()) succ++; else fail++;
  tests++; if (T_mod_u64()) succ++; else fail++;
  tests++; if (T_mod_ulong()) succ++; else fail++;
  tests++; if (T_mod_ulonglong()) succ++; else fail++;
  tests++; if (T_mod_sizet()) succ++; else fail++;

  tests++; if (T_mul_s8())  succ++; else fail++;
  tests++; if (T_mul_s16()) succ++; else fail++;
  tests++; if (T_mul_s32()) succ++; else fail++;
  tests++; if (T_mul_s64()) succ++; else fail++;
  tests++; if (T_mul_long()) succ++; else fail++;
  tests++; if (T_mul_longlong()) succ++; else fail++;
  tests++; if (T_mul_ssizet()) succ++; else fail++;
  tests++; if (T_mul_u8())  succ++; else fail++;
  tests++; if (T_mul_u16()) succ++; else fail++;
  tests++; if (T_mul_u32()) succ++; else fail++;
  tests++; if (T_mul_u64()) succ++; else fail++;
  tests++; if (T_mul_ulong()) succ++; else fail++;
  tests++; if (T_mul_ulonglong()) succ++; else fail++;
  tests++; if (T_mul_sizet()) succ++; else fail++;

  tests++; if (T_sub_s8())  succ++; else fail++;
  tests++; if (T_sub_s16()) succ++; else fail++;
  tests++; if (T_sub_s32()) succ++; else fail++;
  tests++; if (T_sub_s64()) succ++; else fail++;
  tests++; if (T_sub_long()) succ++; else fail++;
  tests++; if (T_sub_longlong()) succ++; else fail++;
  tests++; if (T_sub_ssizet()) succ++; else fail++;
  tests++; if (T_sub_u8())  succ++; else fail++;
  tests++; if (T_sub_u16()) succ++; else fail++;
  tests++; if (T_sub_u32()) succ++; else fail++;
  tests++; if (T_sub_u64()) succ++; else fail++;
  tests++; if (T_sub_ulong()) succ++; else fail++;
  tests++; if (T_sub_ulonglong()) succ++; else fail++;
  tests++; if (T_sub_sizet()) succ++; else fail++;

  tests++; if (T_add_s8())  succ++; else fail++;
  tests++; if (T_add_s16()) succ++; else fail++;
  tests++; if (T_add_s32()) succ++; else fail++;
  tests++; if (T_add_s64()) succ++; else fail++;
  tests++; if (T_add_long()) succ++; else fail++;
  tests++; if (T_add_longlong()) succ++; else fail++;
  tests++; if (T_add_ssizet()) succ++; else fail++;
  tests++; if (T_add_u8())  succ++; else fail++;
  tests++; if (T_add_u16()) succ++; else fail++;
  tests++; if (T_add_u32()) succ++; else fail++;
  tests++; if (T_add_u64()) succ++; else fail++;
  tests++; if (T_add_ulong()) succ++; else fail++;
  tests++; if (T_add_ulonglong()) succ++; else fail++;
  tests++; if (T_add_sizet()) succ++; else fail++;
  tests++; if (T_add_mixed()) succ++; else fail++;
  tests++; if (T_add_increment()) succ++; else fail++;

  tests++; if (T_magic_constants()) succ++; else fail++;

  printf("%d/%d expects succeeded (%d failures)\n",
         expect_succ, expect, expect_fail);
  printf("%d/%d tests succeeded (%d failures)\n", succ, tests, fail);
  /* TODO: Add tests for safe_iopf when upgraded */
  return fail;
}
#endif
