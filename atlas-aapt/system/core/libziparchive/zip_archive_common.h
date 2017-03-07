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

#ifndef LIBZIPARCHIVE_ZIPARCHIVECOMMON_H_
#define LIBZIPARCHIVE_ZIPARCHIVECOMMON_H_

#include "android-base/macros.h"

#include <inttypes.h>

// The "end of central directory" (EOCD) record. Each archive
// contains exactly once such record which appears at the end of
// the archive. It contains archive wide information like the
// number of entries in the archive and the offset to the central
// directory of the offset.
struct EocdRecord {
  static const uint32_t kSignature = 0x06054b50;

  // End of central directory signature, should always be
  // |kSignature|.
  uint32_t eocd_signature;
  // The number of the current "disk", i.e, the "disk" that this
  // central directory is on.
  //
  // This implementation assumes that each archive spans a single
  // disk only. i.e, that disk_num == 1.
  uint16_t disk_num;
  // The disk where the central directory starts.
  //
  // This implementation assumes that each archive spans a single
  // disk only. i.e, that cd_start_disk == 1.
  uint16_t cd_start_disk;
  // The number of central directory records on this disk.
  //
  // This implementation assumes that each archive spans a single
  // disk only. i.e, that num_records_on_disk == num_records.
  uint16_t num_records_on_disk;
  // The total number of central directory records.
  uint16_t num_records;
  // The size of the central directory (in bytes).
  uint32_t cd_size;
  // The offset of the start of the central directory, relative
  // to the start of the file.
  uint32_t cd_start_offset;
  // Length of the central directory comment.
  uint16_t comment_length;
 private:
  EocdRecord() = default;
  DISALLOW_COPY_AND_ASSIGN(EocdRecord);
} __attribute__((packed));

// A structure representing the fixed length fields for a single
// record in the central directory of the archive. In addition to
// the fixed length fields listed here, each central directory
// record contains a variable length "file_name" and "extra_field"
// whose lengths are given by |file_name_length| and |extra_field_length|
// respectively.
struct CentralDirectoryRecord {
  static const uint32_t kSignature = 0x02014b50;

  // The start of record signature. Must be |kSignature|.
  uint32_t record_signature;
  // Tool version. Ignored by this implementation.
  uint16_t version_made_by;
  // Tool version. Ignored by this implementation.
  uint16_t version_needed;
  // The "general purpose bit flags" for this entry. The only
  // flag value that we currently check for is the "data descriptor"
  // flag.
  uint16_t gpb_flags;
  // The compression method for this entry, one of |kCompressStored|
  // and |kCompressDeflated|.
  uint16_t compression_method;
  // The file modification time and date for this entry.
  uint16_t last_mod_time;
  uint16_t last_mod_date;
  // The CRC-32 checksum for this entry.
  uint32_t crc32;
  // The compressed size (in bytes) of this entry.
  uint32_t compressed_size;
  // The uncompressed size (in bytes) of this entry.
  uint32_t uncompressed_size;
  // The length of the entry file name in bytes. The file name
  // will appear immediately after this record.
  uint16_t file_name_length;
  // The length of the extra field info (in bytes). This data
  // will appear immediately after the entry file name.
  uint16_t extra_field_length;
  // The length of the entry comment (in bytes). This data will
  // appear immediately after the extra field.
  uint16_t comment_length;
  // The start disk for this entry. Ignored by this implementation).
  uint16_t file_start_disk;
  // File attributes. Ignored by this implementation.
  uint16_t internal_file_attributes;
  // File attributes. Ignored by this implementation.
  uint32_t external_file_attributes;
  // The offset to the local file header for this entry, from the
  // beginning of this archive.
  uint32_t local_file_header_offset;
 private:
  CentralDirectoryRecord() = default;
  DISALLOW_COPY_AND_ASSIGN(CentralDirectoryRecord);
} __attribute__((packed));

// The local file header for a given entry. This duplicates information
// present in the central directory of the archive. It is an error for
// the information here to be different from the central directory
// information for a given entry.
struct LocalFileHeader {
  static const uint32_t kSignature = 0x04034b50;

  // The local file header signature, must be |kSignature|.
  uint32_t lfh_signature;
  // Tool version. Ignored by this implementation.
  uint16_t version_needed;
  // The "general purpose bit flags" for this entry. The only
  // flag value that we currently check for is the "data descriptor"
  // flag.
  uint16_t gpb_flags;
  // The compression method for this entry, one of |kCompressStored|
  // and |kCompressDeflated|.
  uint16_t compression_method;
  // The file modification time and date for this entry.
  uint16_t last_mod_time;
  uint16_t last_mod_date;
  // The CRC-32 checksum for this entry.
  uint32_t crc32;
  // The compressed size (in bytes) of this entry.
  uint32_t compressed_size;
  // The uncompressed size (in bytes) of this entry.
  uint32_t uncompressed_size;
  // The length of the entry file name in bytes. The file name
  // will appear immediately after this record.
  uint16_t file_name_length;
  // The length of the extra field info (in bytes). This data
  // will appear immediately after the entry file name.
  uint16_t extra_field_length;
 private:
  LocalFileHeader() = default;
  DISALLOW_COPY_AND_ASSIGN(LocalFileHeader);
} __attribute__((packed));

struct DataDescriptor {
  // The *optional* data descriptor start signature.
  static const uint32_t kOptSignature = 0x08074b50;

  // CRC-32 checksum of the entry.
  uint32_t crc32;
  // Compressed size of the entry.
  uint32_t compressed_size;
  // Uncompressed size of the entry.
  uint32_t uncompressed_size;
 private:
  DataDescriptor() = default;
  DISALLOW_COPY_AND_ASSIGN(DataDescriptor);
} __attribute__((packed));

// mask value that signifies that the entry has a DD
static const uint32_t kGPBDDFlagMask = 0x0008;

// The maximum size of a central directory or a file
// comment in bytes.
static const uint32_t kMaxCommentLen = 65535;

#endif /* LIBZIPARCHIVE_ZIPARCHIVECOMMON_H_ */
