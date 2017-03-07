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

#ifndef LIBZIPARCHIVE_ENTRY_NAME_UTILS_INL_H_
#define LIBZIPARCHIVE_ENTRY_NAME_UTILS_INL_H_

#include <stddef.h>
#include <stdint.h>

// Check if |length| bytes at |entry_name| constitute a valid entry name.
// Entry names must be valid UTF-8 and must not contain '0'.
inline bool IsValidEntryName(const uint8_t* entry_name, const size_t length) {
  for (size_t i = 0; i < length; ++i) {
    const uint8_t byte = entry_name[i];
    if (byte == 0) {
      return false;
    } else if ((byte & 0x80) == 0) {
      // Single byte sequence.
      continue;
    } else if ((byte & 0xc0) == 0x80 || (byte & 0xfe) == 0xfe) {
      // Invalid sequence.
      return false;
    } else {
      // 2-5 byte sequences.
      for (uint8_t first = byte << 1; first & 0x80; first <<= 1) {
        ++i;

        // Missing continuation byte..
        if (i == length) {
          return false;
        }

        // Invalid continuation byte.
        const uint8_t continuation_byte = entry_name[i];
        if ((continuation_byte & 0xc0) != 0x80) {
          return false;
        }
      }
    }
  }

  return true;
}


#endif  // LIBZIPARCHIVE_ENTRY_NAME_UTILS_INL_H_
