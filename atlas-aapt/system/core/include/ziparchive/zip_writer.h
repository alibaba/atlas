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

#ifndef LIBZIPARCHIVE_ZIPWRITER_H_
#define LIBZIPARCHIVE_ZIPWRITER_H_

#include "android-base/macros.h"
#include <utils/Compat.h>

#include <cstdio>
#include <ctime>
#include <memory>
#include <string>
#include <vector>
#include <zlib.h>

/**
 * Writes a Zip file via a stateful interface.
 *
 * Example:
 *
 *   FILE* file = fopen("path/to/zip.zip", "wb");
 *
 *   ZipWriter writer(file);
 *
 *   writer.StartEntry("test.txt", ZipWriter::kCompress | ZipWriter::kAlign);
 *   writer.WriteBytes(buffer, bufferLen);
 *   writer.WriteBytes(buffer2, bufferLen2);
 *   writer.FinishEntry();
 *
 *   writer.StartEntry("empty.txt", 0);
 *   writer.FinishEntry();
 *
 *   writer.Finish();
 *
 *   fclose(file);
 */
class ZipWriter {
public:
  enum {
    /**
     * Flag to compress the zip entry using deflate.
     */
    kCompress = 0x01,

    /**
     * Flag to align the zip entry data on a 32bit boundary. Useful for
     * mmapping the data at runtime.
     */
    kAlign32 = 0x02,
  };

  static const char* ErrorCodeString(int32_t error_code);

  /**
   * Create a ZipWriter that will write into a FILE stream. The file should be opened with
   * open mode of "wb" or "w+b". ZipWriter does not take ownership of the file stream. The
   * caller is responsible for closing the file.
   */
  explicit ZipWriter(FILE* f);

  // Move constructor.
  ZipWriter(ZipWriter&& zipWriter);

  // Move assignment.
  ZipWriter& operator=(ZipWriter&& zipWriter);

  /**
   * Starts a new zip entry with the given path and flags.
   * Flags can be a bitwise OR of ZipWriter::kCompress and ZipWriter::kAlign.
   * Subsequent calls to WriteBytes(const void*, size_t) will add data to this entry.
   * Returns 0 on success, and an error value < 0 on failure.
   */
  int32_t StartEntry(const char* path, size_t flags);

  /**
   * Starts a new zip entry with the given path and flags, where the
   * entry will be aligned to the given alignment.
   * Flags can only be ZipWriter::kCompress. Using the flag ZipWriter::kAlign32
   * will result in an error.
   * Subsequent calls to WriteBytes(const void*, size_t) will add data to this entry.
   * Returns 0 on success, and an error value < 0 on failure.
   */
  int32_t StartAlignedEntry(const char* path, size_t flags, uint32_t alignment);

  /**
   * Same as StartEntry(const char*, size_t), but sets a last modified time for the entry.
   */
  int32_t StartEntryWithTime(const char* path, size_t flags, time_t time);

  /**
   * Same as StartAlignedEntry(const char*, size_t), but sets a last modified time for the entry.
   */
  int32_t StartAlignedEntryWithTime(const char* path, size_t flags, time_t time,
                                    uint32_t alignment);

  /**
   * Writes bytes to the zip file for the previously started zip entry.
   * Returns 0 on success, and an error value < 0 on failure.
   */
  int32_t WriteBytes(const void* data, size_t len);

  /**
   * Finish a zip entry started with StartEntry(const char*, size_t) or
   * StartEntryWithTime(const char*, size_t, time_t). This must be called before
   * any new zip entries are started, or before Finish() is called.
   * Returns 0 on success, and an error value < 0 on failure.
   */
  int32_t FinishEntry();

  /**
   * Writes the Central Directory Headers and flushes the zip file stream.
   * Returns 0 on success, and an error value < 0 on failure.
   */
  int32_t Finish();

private:
  DISALLOW_COPY_AND_ASSIGN(ZipWriter);

  struct FileInfo {
    std::string path;
    uint16_t compression_method;
    uint32_t crc32;
    uint32_t compressed_size;
    uint32_t uncompressed_size;
    uint16_t last_mod_time;
    uint16_t last_mod_date;
    uint32_t local_file_header_offset;
  };

  int32_t HandleError(int32_t error_code);
  int32_t PrepareDeflate();
  int32_t StoreBytes(FileInfo* file, const void* data, size_t len);
  int32_t CompressBytes(FileInfo* file, const void* data, size_t len);
  int32_t FlushCompressedBytes(FileInfo* file);

  enum class State {
    kWritingZip,
    kWritingEntry,
    kDone,
    kError,
  };

  FILE* file_;
  off64_t current_offset_;
  State state_;
  std::vector<FileInfo> files_;

  std::unique_ptr<z_stream, void(*)(z_stream*)> z_stream_;
  std::vector<uint8_t> buffer_;
};

#endif /* LIBZIPARCHIVE_ZIPWRITER_H_ */
