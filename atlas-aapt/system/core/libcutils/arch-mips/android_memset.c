/*
 * Copyright (C) 2015 The Android Open Source Project
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

/* generic C version for any machine */

#include <cutils/memory.h>

#ifdef __clang__
__attribute__((no_sanitize("integer")))
#endif
void android_memset16(uint16_t* dst, uint16_t value, size_t size)
{
   /* optimized version of
      size >>= 1;
      while (size--)
        *dst++ = value;
   */

   size >>= 1;
   if (((uintptr_t)dst & 2) && size) {
      /* fill unpaired first elem separately */
      *dst++ = value;
      size--;
   }
   /* dst is now 32-bit-aligned */
   /* fill body with 32-bit pairs */
   uint32_t value32 = (((uint32_t)value) << 16) | ((uint32_t)value);
   android_memset32((uint32_t*) dst, value32, size<<1);
   if (size & 1) {
      dst[size-1] = value;  /* fill unpaired last elem */
   }
}


#ifdef __clang__
__attribute__((no_sanitize("integer")))
#endif
void android_memset32(uint32_t* dst, uint32_t value, size_t size)
{
   /* optimized version of
      size >>= 2;
      while (size--)
         *dst++ = value;
   */

   size >>= 2;
   if (((uintptr_t)dst & 4) && size) {
      /* fill unpaired first 32-bit elem separately */
      *dst++ = value;
      size--;
   }
   /* dst is now 64-bit aligned */
   /* fill body with 64-bit pairs */
   uint64_t value64 = (((uint64_t)value) << 32) | ((uint64_t)value);
   uint64_t* dst64 = (uint64_t*)dst;

   while (size >= 12) {
      dst64[0] = value64;
      dst64[1] = value64;
      dst64[2] = value64;
      dst64[3] = value64;
      dst64[4] = value64;
      dst64[5] = value64;
      size  -= 12;
      dst64 += 6;
   }

   /* fill remainder with original 32-bit single-elem loop */
   dst = (uint32_t*) dst64;
   while (size != 0) {
       size--;
      *dst++ = value;
   }

}
