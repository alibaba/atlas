/*
 * Copyright (C) 2015 The Android Open Source Project
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

// Read-only stream access to Zip archives entries.
#ifndef LIBZIPARCHIVE_ZIPARCHIVESTREAMENTRY_H_
#define LIBZIPARCHIVE_ZIPARCHIVESTREAMENTRY_H_

#include <vector>

#include <ziparchive/zip_archive.h>

class ZipArchiveStreamEntry {
 public:
  virtual ~ZipArchiveStreamEntry() {}

  virtual const std::vector<uint8_t>* Read() = 0;

  virtual bool Verify() = 0;

  static ZipArchiveStreamEntry* Create(ZipArchiveHandle handle, const ZipEntry& entry);
  static ZipArchiveStreamEntry* CreateRaw(ZipArchiveHandle handle, const ZipEntry& entry);

 protected:
  ZipArchiveStreamEntry(ZipArchiveHandle handle) : handle_(handle) {}

  virtual bool Init(const ZipEntry& entry);

  ZipArchiveHandle handle_;

  uint32_t crc32_;
};

#endif  // LIBZIPARCHIVE_ZIPARCHIVESTREAMENTRY_H_
