/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <sys/mman.h>
#include <sys/types.h>

#include <memory>

#include <cutils/memory.h>
#include <gtest/gtest.h>

#define FENCEPOST_LENGTH 8

#define MAX_TEST_SIZE (64*1024)
// Choose values that have no repeating byte values.
#define MEMSET16_PATTERN 0xb139
#define MEMSET32_PATTERN 0x48193a27

enum test_e {
  MEMSET16 = 0,
  MEMSET32,
};

static int g_memset16_aligns[][2] = {
  { 2, 0 },
  { 4, 0 },
  { 8, 0 },
  { 16, 0 },
  { 32, 0 },
  { 64, 0 },
  { 128, 0 },

  { 4, 2 },

  { 8, 2 },
  { 8, 4 },
  { 8, 6 },

  { 128, 2 },
  { 128, 4 },
  { 128, 6 },
  { 128, 8 },
  { 128, 10 },
  { 128, 12 },
  { 128, 14 },
  { 128, 16 },
};

static int g_memset32_aligns[][2] = {
  { 4, 0 },
  { 8, 0 },
  { 16, 0 },
  { 32, 0 },
  { 64, 0 },
  { 128, 0 },

  { 8, 4 },

  { 128, 4 },
  { 128, 8 },
  { 128, 12 },
  { 128, 16 },
};

static size_t GetIncrement(size_t len, size_t min_incr) {
  if (len >= 4096) {
    return 1024;
  } else if (len >= 1024) {
    return 256;
  }
  return min_incr;
}

// Return a pointer into the current buffer with the specified alignment.
static void *GetAlignedPtr(void *orig_ptr, int alignment, int or_mask) {
  uint64_t ptr = reinterpret_cast<uint64_t>(orig_ptr);
  if (alignment > 0) {
      // When setting the alignment, set it to exactly the alignment chosen.
      // The pointer returned will be guaranteed not to be aligned to anything
      // more than that.
      ptr += alignment - (ptr & (alignment - 1));
      ptr |= alignment | or_mask;
  }

  return reinterpret_cast<void*>(ptr);
}

static void SetFencepost(uint8_t *buffer) {
  for (int i = 0; i < FENCEPOST_LENGTH; i += 2) {
    buffer[i] = 0xde;
    buffer[i+1] = 0xad;
  }
}

static void VerifyFencepost(uint8_t *buffer) {
  for (int i = 0; i < FENCEPOST_LENGTH; i += 2) {
    if (buffer[i] != 0xde || buffer[i+1] != 0xad) {
      uint8_t expected_value;
      if (buffer[i] == 0xde) {
        i++;
        expected_value = 0xad;
      } else {
        expected_value = 0xde;
      }
      ASSERT_EQ(expected_value, buffer[i]);
    }
  }
}

void RunMemsetTests(test_e test_type, uint32_t value, int align[][2], size_t num_aligns) {
  size_t min_incr = 4;
  if (test_type == MEMSET16) {
    min_incr = 2;
    value |= value << 16;
  }
  std::unique_ptr<uint32_t[]> expected_buf(new uint32_t[MAX_TEST_SIZE/sizeof(uint32_t)]);
  for (size_t i = 0; i < MAX_TEST_SIZE/sizeof(uint32_t); i++) {
    expected_buf[i] = value;
  }

  // Allocate one large buffer with lots of extra space so that we can
  // guarantee that all possible alignments will fit.
  std::unique_ptr<uint8_t[]> buf(new uint8_t[3*MAX_TEST_SIZE]);
  uint8_t *buf_align;
  for (size_t i = 0; i < num_aligns; i++) {
    size_t incr = min_incr;
    for (size_t len = incr; len <= MAX_TEST_SIZE; len += incr) {
      incr = GetIncrement(len, min_incr);

      buf_align = reinterpret_cast<uint8_t*>(GetAlignedPtr(
          buf.get()+FENCEPOST_LENGTH, align[i][0], align[i][1]));

      SetFencepost(&buf_align[-FENCEPOST_LENGTH]);
      SetFencepost(&buf_align[len]);

      memset(buf_align, 0xff, len);
      if (test_type == MEMSET16) {
        android_memset16(reinterpret_cast<uint16_t*>(buf_align), value, len);
      } else {
        android_memset32(reinterpret_cast<uint32_t*>(buf_align), value, len);
      }
      ASSERT_EQ(0, memcmp(expected_buf.get(), buf_align, len))
          << "Failed size " << len << " align " << align[i][0] << " " << align[i][1] << "\n";

      VerifyFencepost(&buf_align[-FENCEPOST_LENGTH]);
      VerifyFencepost(&buf_align[len]);
    }
  }
}

TEST(libcutils, android_memset16_non_zero) {
  RunMemsetTests(MEMSET16, MEMSET16_PATTERN, g_memset16_aligns, sizeof(g_memset16_aligns)/sizeof(int[2]));
}

TEST(libcutils, android_memset16_zero) {
  RunMemsetTests(MEMSET16, 0, g_memset16_aligns, sizeof(g_memset16_aligns)/sizeof(int[2]));
}

TEST(libcutils, android_memset32_non_zero) {
  RunMemsetTests(MEMSET32, MEMSET32_PATTERN, g_memset32_aligns, sizeof(g_memset32_aligns)/sizeof(int[2]));
}

TEST(libcutils, android_memset32_zero) {
  RunMemsetTests(MEMSET32, 0, g_memset32_aligns, sizeof(g_memset32_aligns)/sizeof(int[2]));
}
