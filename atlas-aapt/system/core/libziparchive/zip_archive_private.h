/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef LIBZIPARCHIVE_ZIPARCHIVE_PRIVATE_H_
#define LIBZIPARCHIVE_ZIPARCHIVE_PRIVATE_H_

#include <stdint.h>
#include <stdlib.h>
#include <unistd.h>

#include <utils/FileMap.h>
#include <ziparchive/zip_archive.h>

struct ZipArchive {
  // open Zip archive
  const int fd;
  const bool close_file;

  // mapped central directory area
  off64_t directory_offset;
  android::FileMap directory_map;

  // number of entries in the Zip archive
  uint16_t num_entries;

  // We know how many entries are in the Zip archive, so we can have a
  // fixed-size hash table. We define a load factor of 0.75 and over
  // allocate so the maximum number entries can never be higher than
  // ((4 * UINT16_MAX) / 3 + 1) which can safely fit into a uint32_t.
  uint32_t hash_table_size;
  ZipString* hash_table;

  ZipArchive(const int fd, bool assume_ownership) :
      fd(fd),
      close_file(assume_ownership),
      directory_offset(0),
      num_entries(0),
      hash_table_size(0),
      hash_table(NULL) {}

  ~ZipArchive() {
    if (close_file && fd >= 0) {
      close(fd);
    }

    free(hash_table);
  }
};

#endif  // LIBZIPARCHIVE_ZIPARCHIVE_PRIVATE_H_
