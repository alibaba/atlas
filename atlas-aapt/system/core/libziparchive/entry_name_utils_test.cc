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

#include "entry_name_utils-inl.h"

#include <gtest/gtest.h>

TEST(entry_name_utils, NullChars) {
  // 'A', 'R', '\0', 'S', 'E'
  const uint8_t zeroes[] = { 0x41, 0x52, 0x00, 0x53, 0x45 };
  ASSERT_FALSE(IsValidEntryName(zeroes, sizeof(zeroes)));

  const uint8_t zeroes_continuation_chars[] = { 0xc2, 0xa1, 0xc2, 0x00 };
  ASSERT_FALSE(IsValidEntryName(zeroes_continuation_chars,
                                sizeof(zeroes_continuation_chars)));
}

TEST(entry_name_utils, InvalidSequence) {
  // 0xfe is an invalid start byte
  const uint8_t invalid[] = { 0x41, 0xfe };
  ASSERT_FALSE(IsValidEntryName(invalid, sizeof(invalid)));

  // 0x91 is an invalid start byte (it's a valid continuation byte).
  const uint8_t invalid2[] = { 0x41, 0x91 };
  ASSERT_FALSE(IsValidEntryName(invalid2, sizeof(invalid2)));
}

TEST(entry_name_utils, TruncatedContinuation) {
  // Malayalam script with truncated bytes. There should be 2 bytes
  // after 0xe0
  const uint8_t truncated[] = { 0xe0, 0xb4, 0x85, 0xe0, 0xb4 };
  ASSERT_FALSE(IsValidEntryName(truncated, sizeof(truncated)));

  // 0xc2 is the start of a 2 byte sequence that we've subsequently
  // dropped.
  const uint8_t truncated2[] = { 0xc2, 0xc2, 0xa1 };
  ASSERT_FALSE(IsValidEntryName(truncated2, sizeof(truncated2)));
}

TEST(entry_name_utils, BadContinuation) {
  // 0x41 is an invalid continuation char, since it's MSBs
  // aren't "10..." (are 01).
  const uint8_t bad[] = { 0xc2, 0xa1, 0xc2, 0x41 };
  ASSERT_FALSE(IsValidEntryName(bad, sizeof(bad)));

  // 0x41 is an invalid continuation char, since it's MSBs
  // aren't "10..." (are 11).
  const uint8_t bad2[] = { 0xc2, 0xa1, 0xc2, 0xfe };
  ASSERT_FALSE(IsValidEntryName(bad2, sizeof(bad2)));
}
