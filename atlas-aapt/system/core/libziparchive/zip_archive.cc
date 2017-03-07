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

/*
 * Read-only access to Zip archives, with minimal heap allocation.
 */

#include <assert.h>
#include <errno.h>
#include <fcntl.h>
#include <inttypes.h>
#include <limits.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#include <memory>
#include <vector>

#include "android-base/file.h"
#include "android-base/macros.h"  // TEMP_FAILURE_RETRY may or may not be in unistd
#include "android-base/memory.h"
#include "log/log.h"
#include "utils/Compat.h"
#include "utils/FileMap.h"
#include "ziparchive/zip_archive.h"
#include "zlib.h"

#include "entry_name_utils-inl.h"
#include "zip_archive_common.h"
#include "zip_archive_private.h"

using android::base::get_unaligned;

// This is for windows. If we don't open a file in binary mode, weird
// things will happen.
#ifndef O_BINARY
#define O_BINARY 0
#endif

// The maximum number of bytes to scan backwards for the EOCD start.
static const uint32_t kMaxEOCDSearch = kMaxCommentLen + sizeof(EocdRecord);

static const char* kErrorMessages[] = {
  "Unknown return code.",
  "Iteration ended",
  "Zlib error",
  "Invalid file",
  "Invalid handle",
  "Duplicate entries in archive",
  "Empty archive",
  "Entry not found",
  "Invalid offset",
  "Inconsistent information",
  "Invalid entry name",
  "I/O Error",
  "File mapping failed"
};

static const int32_t kErrorMessageUpperBound = 0;

static const int32_t kIterationEnd = -1;

// We encountered a Zlib error when inflating a stream from this file.
// Usually indicates file corruption.
static const int32_t kZlibError = -2;

// The input file cannot be processed as a zip archive. Usually because
// it's too small, too large or does not have a valid signature.
static const int32_t kInvalidFile = -3;

// An invalid iteration / ziparchive handle was passed in as an input
// argument.
static const int32_t kInvalidHandle = -4;

// The zip archive contained two (or possibly more) entries with the same
// name.
static const int32_t kDuplicateEntry = -5;

// The zip archive contains no entries.
static const int32_t kEmptyArchive = -6;

// The specified entry was not found in the archive.
static const int32_t kEntryNotFound = -7;

// The zip archive contained an invalid local file header pointer.
static const int32_t kInvalidOffset = -8;

// The zip archive contained inconsistent entry information. This could
// be because the central directory & local file header did not agree, or
// if the actual uncompressed length or crc32 do not match their declared
// values.
static const int32_t kInconsistentInformation = -9;

// An invalid entry name was encountered.
static const int32_t kInvalidEntryName = -10;

// An I/O related system call (read, lseek, ftruncate, map) failed.
static const int32_t kIoError = -11;

// We were not able to mmap the central directory or entry contents.
static const int32_t kMmapFailed = -12;

static const int32_t kErrorMessageLowerBound = -13;

/*
 * A Read-only Zip archive.
 *
 * We want "open" and "find entry by name" to be fast operations, and
 * we want to use as little memory as possible.  We memory-map the zip
 * central directory, and load a hash table with pointers to the filenames
 * (which aren't null-terminated).  The other fields are at a fixed offset
 * from the filename, so we don't need to extract those (but we do need
 * to byte-read and endian-swap them every time we want them).
 *
 * It's possible that somebody has handed us a massive (~1GB) zip archive,
 * so we can't expect to mmap the entire file.
 *
 * To speed comparisons when doing a lookup by name, we could make the mapping
 * "private" (copy-on-write) and null-terminate the filenames after verifying
 * the record structure.  However, this requires a private mapping of
 * every page that the Central Directory touches.  Easier to tuck a copy
 * of the string length into the hash table entry.
 */

/*
 * Round up to the next highest power of 2.
 *
 * Found on http://graphics.stanford.edu/~seander/bithacks.html.
 */
static uint32_t RoundUpPower2(uint32_t val) {
  val--;
  val |= val >> 1;
  val |= val >> 2;
  val |= val >> 4;
  val |= val >> 8;
  val |= val >> 16;
  val++;

  return val;
}

static uint32_t ComputeHash(const ZipString& name) {
  uint32_t hash = 0;
  uint16_t len = name.name_length;
  const uint8_t* str = name.name;

  while (len--) {
    hash = hash * 31 + *str++;
  }

  return hash;
}

/*
 * Convert a ZipEntry to a hash table index, verifying that it's in a
 * valid range.
 */
static int64_t EntryToIndex(const ZipString* hash_table,
                            const uint32_t hash_table_size,
                            const ZipString& name) {
  const uint32_t hash = ComputeHash(name);

  // NOTE: (hash_table_size - 1) is guaranteed to be non-negative.
  uint32_t ent = hash & (hash_table_size - 1);
  while (hash_table[ent].name != NULL) {
    if (hash_table[ent] == name) {
      return ent;
    }

    ent = (ent + 1) & (hash_table_size - 1);
  }

  ALOGV("Zip: Unable to find entry %.*s", name.name_length, name.name);
  return kEntryNotFound;
}

/*
 * Add a new entry to the hash table.
 */
static int32_t AddToHash(ZipString *hash_table, const uint64_t hash_table_size,
                         const ZipString& name) {
  const uint64_t hash = ComputeHash(name);
  uint32_t ent = hash & (hash_table_size - 1);

  /*
   * We over-allocated the table, so we're guaranteed to find an empty slot.
   * Further, we guarantee that the hashtable size is not 0.
   */
  while (hash_table[ent].name != NULL) {
    if (hash_table[ent] == name) {
      // We've found a duplicate entry. We don't accept it
      ALOGW("Zip: Found duplicate entry %.*s", name.name_length, name.name);
      return kDuplicateEntry;
    }
    ent = (ent + 1) & (hash_table_size - 1);
  }

  hash_table[ent].name = name.name;
  hash_table[ent].name_length = name.name_length;
  return 0;
}

static int32_t MapCentralDirectory0(int fd, const char* debug_file_name,
                                    ZipArchive* archive, off64_t file_length,
                                    off64_t read_amount, uint8_t* scan_buffer) {
  const off64_t search_start = file_length - read_amount;

  if (lseek64(fd, search_start, SEEK_SET) != search_start) {
    ALOGW("Zip: seek %" PRId64 " failed: %s", static_cast<int64_t>(search_start),
          strerror(errno));
    return kIoError;
  }
  if (!android::base::ReadFully(fd, scan_buffer, static_cast<size_t>(read_amount))) {
    ALOGW("Zip: read %" PRId64 " failed: %s", static_cast<int64_t>(read_amount),
          strerror(errno));
    return kIoError;
  }

  /*
   * Scan backward for the EOCD magic.  In an archive without a trailing
   * comment, we'll find it on the first try.  (We may want to consider
   * doing an initial minimal read; if we don't find it, retry with a
   * second read as above.)
   */
  int i = read_amount - sizeof(EocdRecord);
  for (; i >= 0; i--) {
    if (scan_buffer[i] == 0x50) {
      uint32_t* sig_addr = reinterpret_cast<uint32_t*>(&scan_buffer[i]);
      if (get_unaligned<uint32_t>(sig_addr) == EocdRecord::kSignature) {
        ALOGV("+++ Found EOCD at buf+%d", i);
        break;
      }
    }
  }
  if (i < 0) {
    ALOGD("Zip: EOCD not found, %s is not zip", debug_file_name);
    return kInvalidFile;
  }

  const off64_t eocd_offset = search_start + i;
  const EocdRecord* eocd = reinterpret_cast<const EocdRecord*>(scan_buffer + i);
  /*
   * Verify that there's no trailing space at the end of the central directory
   * and its comment.
   */
  const off64_t calculated_length = eocd_offset + sizeof(EocdRecord)
      + eocd->comment_length;
  if (calculated_length != file_length) {
    ALOGW("Zip: %" PRId64 " extraneous bytes at the end of the central directory",
          static_cast<int64_t>(file_length - calculated_length));
    return kInvalidFile;
  }

  /*
   * Grab the CD offset and size, and the number of entries in the
   * archive and verify that they look reasonable.
   */
  if (eocd->cd_start_offset + eocd->cd_size > eocd_offset) {
    ALOGW("Zip: bad offsets (dir %" PRIu32 ", size %" PRIu32 ", eocd %" PRId64 ")",
        eocd->cd_start_offset, eocd->cd_size, static_cast<int64_t>(eocd_offset));
    return kInvalidOffset;
  }
  if (eocd->num_records == 0) {
    ALOGW("Zip: empty archive?");
    return kEmptyArchive;
  }

  ALOGV("+++ num_entries=%" PRIu32 " dir_size=%" PRIu32 " dir_offset=%" PRIu32,
        eocd->num_records, eocd->cd_size, eocd->cd_start_offset);

  /*
   * It all looks good.  Create a mapping for the CD, and set the fields
   * in archive.
   */
  if (!archive->directory_map.create(debug_file_name, fd,
          static_cast<off64_t>(eocd->cd_start_offset),
          static_cast<size_t>(eocd->cd_size), true /* read only */) ) {
    return kMmapFailed;
  }

  archive->num_entries = eocd->num_records;
  archive->directory_offset = eocd->cd_start_offset;

  return 0;
}

/*
 * Find the zip Central Directory and memory-map it.
 *
 * On success, returns 0 after populating fields from the EOCD area:
 *   directory_offset
 *   directory_map
 *   num_entries
 */
static int32_t MapCentralDirectory(int fd, const char* debug_file_name,
                                   ZipArchive* archive) {

  // Test file length. We use lseek64 to make sure the file
  // is small enough to be a zip file (Its size must be less than
  // 0xffffffff bytes).
  off64_t file_length = lseek64(fd, 0, SEEK_END);
  if (file_length == -1) {
    ALOGV("Zip: lseek on fd %d failed", fd);
    return kInvalidFile;
  }

  if (file_length > static_cast<off64_t>(0xffffffff)) {
    ALOGV("Zip: zip file too long %" PRId64, static_cast<int64_t>(file_length));
    return kInvalidFile;
  }

  if (file_length < static_cast<off64_t>(sizeof(EocdRecord))) {
    ALOGV("Zip: length %" PRId64 " is too small to be zip", static_cast<int64_t>(file_length));
    return kInvalidFile;
  }

  /*
   * Perform the traditional EOCD snipe hunt.
   *
   * We're searching for the End of Central Directory magic number,
   * which appears at the start of the EOCD block.  It's followed by
   * 18 bytes of EOCD stuff and up to 64KB of archive comment.  We
   * need to read the last part of the file into a buffer, dig through
   * it to find the magic number, parse some values out, and use those
   * to determine the extent of the CD.
   *
   * We start by pulling in the last part of the file.
   */
  off64_t read_amount = kMaxEOCDSearch;
  if (file_length < read_amount) {
    read_amount = file_length;
  }

  uint8_t* scan_buffer = reinterpret_cast<uint8_t*>(malloc(read_amount));
  int32_t result = MapCentralDirectory0(fd, debug_file_name, archive,
                                        file_length, read_amount, scan_buffer);

  free(scan_buffer);
  return result;
}

/*
 * Parses the Zip archive's Central Directory.  Allocates and populates the
 * hash table.
 *
 * Returns 0 on success.
 */
static int32_t ParseZipArchive(ZipArchive* archive) {
  const uint8_t* const cd_ptr =
      reinterpret_cast<const uint8_t*>(archive->directory_map.getDataPtr());
  const size_t cd_length = archive->directory_map.getDataLength();
  const uint16_t num_entries = archive->num_entries;

  /*
   * Create hash table.  We have a minimum 75% load factor, possibly as
   * low as 50% after we round off to a power of 2.  There must be at
   * least one unused entry to avoid an infinite loop during creation.
   */
  archive->hash_table_size = RoundUpPower2(1 + (num_entries * 4) / 3);
  archive->hash_table = reinterpret_cast<ZipString*>(calloc(archive->hash_table_size,
      sizeof(ZipString)));

  /*
   * Walk through the central directory, adding entries to the hash
   * table and verifying values.
   */
  const uint8_t* const cd_end = cd_ptr + cd_length;
  const uint8_t* ptr = cd_ptr;
  for (uint16_t i = 0; i < num_entries; i++) {
    const CentralDirectoryRecord* cdr =
        reinterpret_cast<const CentralDirectoryRecord*>(ptr);
    if (cdr->record_signature != CentralDirectoryRecord::kSignature) {
      ALOGW("Zip: missed a central dir sig (at %" PRIu16 ")", i);
      return -1;
    }

    if (ptr + sizeof(CentralDirectoryRecord) > cd_end) {
      ALOGW("Zip: ran off the end (at %" PRIu16 ")", i);
      return -1;
    }

    const off64_t local_header_offset = cdr->local_file_header_offset;
    if (local_header_offset >= archive->directory_offset) {
      ALOGW("Zip: bad LFH offset %" PRId64 " at entry %" PRIu16,
          static_cast<int64_t>(local_header_offset), i);
      return -1;
    }

    const uint16_t file_name_length = cdr->file_name_length;
    const uint16_t extra_length = cdr->extra_field_length;
    const uint16_t comment_length = cdr->comment_length;
    const uint8_t* file_name = ptr + sizeof(CentralDirectoryRecord);

    /* check that file name is valid UTF-8 and doesn't contain NUL (U+0000) characters */
    if (!IsValidEntryName(file_name, file_name_length)) {
      return -1;
    }

    /* add the CDE filename to the hash table */
    ZipString entry_name;
    entry_name.name = file_name;
    entry_name.name_length = file_name_length;
    const int add_result = AddToHash(archive->hash_table,
        archive->hash_table_size, entry_name);
    if (add_result != 0) {
      ALOGW("Zip: Error adding entry to hash table %d", add_result);
      return add_result;
    }

    ptr += sizeof(CentralDirectoryRecord) + file_name_length + extra_length + comment_length;
    if ((ptr - cd_ptr) > static_cast<int64_t>(cd_length)) {
      ALOGW("Zip: bad CD advance (%tu vs %zu) at entry %" PRIu16,
          ptr - cd_ptr, cd_length, i);
      return -1;
    }
  }
  ALOGV("+++ zip good scan %" PRIu16 " entries", num_entries);

  return 0;
}

static int32_t OpenArchiveInternal(ZipArchive* archive,
                                   const char* debug_file_name) {
  int32_t result = -1;
  if ((result = MapCentralDirectory(archive->fd, debug_file_name, archive))) {
    return result;
  }

  if ((result = ParseZipArchive(archive))) {
    return result;
  }

  return 0;
}

int32_t OpenArchiveFd(int fd, const char* debug_file_name,
                      ZipArchiveHandle* handle, bool assume_ownership) {
  ZipArchive* archive = new ZipArchive(fd, assume_ownership);
  *handle = archive;
  return OpenArchiveInternal(archive, debug_file_name);
}

int32_t OpenArchive(const char* fileName, ZipArchiveHandle* handle) {
  const int fd = open(fileName, O_RDONLY | O_BINARY, 0);
  ZipArchive* archive = new ZipArchive(fd, true);
  *handle = archive;

  if (fd < 0) {
    ALOGW("Unable to open '%s': %s", fileName, strerror(errno));
    return kIoError;
  }

  return OpenArchiveInternal(archive, fileName);
}

/*
 * Close a ZipArchive, closing the file and freeing the contents.
 */
void CloseArchive(ZipArchiveHandle handle) {
  ZipArchive* archive = reinterpret_cast<ZipArchive*>(handle);
  ALOGV("Closing archive %p", archive);
  delete archive;
}

static int32_t UpdateEntryFromDataDescriptor(int fd,
                                             ZipEntry *entry) {
  uint8_t ddBuf[sizeof(DataDescriptor) + sizeof(DataDescriptor::kOptSignature)];
  if (!android::base::ReadFully(fd, ddBuf, sizeof(ddBuf))) {
    return kIoError;
  }

  const uint32_t ddSignature = *(reinterpret_cast<const uint32_t*>(ddBuf));
  const uint16_t offset = (ddSignature == DataDescriptor::kOptSignature) ? 4 : 0;
  const DataDescriptor* descriptor = reinterpret_cast<const DataDescriptor*>(ddBuf + offset);

  entry->crc32 = descriptor->crc32;
  entry->compressed_length = descriptor->compressed_size;
  entry->uncompressed_length = descriptor->uncompressed_size;

  return 0;
}

// Attempts to read |len| bytes into |buf| at offset |off|.
// On non-Windows platforms, callers are guaranteed that the |fd|
// offset is unchanged and there is no side effect to this call.
//
// On Windows platforms this is not thread-safe.
static inline bool ReadAtOffset(int fd, uint8_t* buf, size_t len, off64_t off) {
#if !defined(_WIN32)
  return TEMP_FAILURE_RETRY(pread64(fd, buf, len, off));
#else
  if (lseek64(fd, off, SEEK_SET) != off) {
    ALOGW("Zip: failed seek to offset %" PRId64, off);
    return false;
  }
  return android::base::ReadFully(fd, buf, len);
#endif
}

static int32_t FindEntry(const ZipArchive* archive, const int ent,
                         ZipEntry* data) {
  const uint16_t nameLen = archive->hash_table[ent].name_length;

  // Recover the start of the central directory entry from the filename
  // pointer.  The filename is the first entry past the fixed-size data,
  // so we can just subtract back from that.
  const uint8_t* ptr = archive->hash_table[ent].name;
  ptr -= sizeof(CentralDirectoryRecord);

  // This is the base of our mmapped region, we have to sanity check that
  // the name that's in the hash table is a pointer to a location within
  // this mapped region.
  const uint8_t* base_ptr = reinterpret_cast<const uint8_t*>(
    archive->directory_map.getDataPtr());
  if (ptr < base_ptr || ptr > base_ptr + archive->directory_map.getDataLength()) {
    ALOGW("Zip: Invalid entry pointer");
    return kInvalidOffset;
  }

  const CentralDirectoryRecord *cdr =
      reinterpret_cast<const CentralDirectoryRecord*>(ptr);

  // The offset of the start of the central directory in the zipfile.
  // We keep this lying around so that we can sanity check all our lengths
  // and our per-file structures.
  const off64_t cd_offset = archive->directory_offset;

  // Fill out the compression method, modification time, crc32
  // and other interesting attributes from the central directory. These
  // will later be compared against values from the local file header.
  data->method = cdr->compression_method;
  data->mod_time = cdr->last_mod_date << 16 | cdr->last_mod_time;
  data->crc32 = cdr->crc32;
  data->compressed_length = cdr->compressed_size;
  data->uncompressed_length = cdr->uncompressed_size;

  // Figure out the local header offset from the central directory. The
  // actual file data will begin after the local header and the name /
  // extra comments.
  const off64_t local_header_offset = cdr->local_file_header_offset;
  if (local_header_offset + static_cast<off64_t>(sizeof(LocalFileHeader)) >= cd_offset) {
    ALOGW("Zip: bad local hdr offset in zip");
    return kInvalidOffset;
  }

  uint8_t lfh_buf[sizeof(LocalFileHeader)];
  if (!ReadAtOffset(archive->fd, lfh_buf, sizeof(lfh_buf), local_header_offset)) {
    ALOGW("Zip: failed reading lfh name from offset %" PRId64,
        static_cast<int64_t>(local_header_offset));
    return kIoError;
  }

  const LocalFileHeader *lfh = reinterpret_cast<const LocalFileHeader*>(lfh_buf);

  if (lfh->lfh_signature != LocalFileHeader::kSignature) {
    ALOGW("Zip: didn't find signature at start of lfh, offset=%" PRId64,
        static_cast<int64_t>(local_header_offset));
    return kInvalidOffset;
  }

  // Paranoia: Match the values specified in the local file header
  // to those specified in the central directory.
  if ((lfh->gpb_flags & kGPBDDFlagMask) == 0) {
    data->has_data_descriptor = 0;
    if (data->compressed_length != lfh->compressed_size
        || data->uncompressed_length != lfh->uncompressed_size
        || data->crc32 != lfh->crc32) {
      ALOGW("Zip: size/crc32 mismatch. expected {%" PRIu32 ", %" PRIu32
        ", %" PRIx32 "}, was {%" PRIu32 ", %" PRIu32 ", %" PRIx32 "}",
        data->compressed_length, data->uncompressed_length, data->crc32,
        lfh->compressed_size, lfh->uncompressed_size, lfh->crc32);
      return kInconsistentInformation;
    }
  } else {
    data->has_data_descriptor = 1;
  }

  // Check that the local file header name matches the declared
  // name in the central directory.
  if (lfh->file_name_length == nameLen) {
    const off64_t name_offset = local_header_offset + sizeof(LocalFileHeader);
    if (name_offset + lfh->file_name_length > cd_offset) {
      ALOGW("Zip: Invalid declared length");
      return kInvalidOffset;
    }

    uint8_t* name_buf = reinterpret_cast<uint8_t*>(malloc(nameLen));
    if (!ReadAtOffset(archive->fd, name_buf, nameLen, name_offset)) {
      ALOGW("Zip: failed reading lfh name from offset %" PRId64, static_cast<int64_t>(name_offset));
      free(name_buf);
      return kIoError;
    }

    if (memcmp(archive->hash_table[ent].name, name_buf, nameLen)) {
      free(name_buf);
      return kInconsistentInformation;
    }

    free(name_buf);
  } else {
    ALOGW("Zip: lfh name did not match central directory.");
    return kInconsistentInformation;
  }

  const off64_t data_offset = local_header_offset + sizeof(LocalFileHeader)
      + lfh->file_name_length + lfh->extra_field_length;
  if (data_offset > cd_offset) {
    ALOGW("Zip: bad data offset %" PRId64 " in zip", static_cast<int64_t>(data_offset));
    return kInvalidOffset;
  }

  if (static_cast<off64_t>(data_offset + data->compressed_length) > cd_offset) {
    ALOGW("Zip: bad compressed length in zip (%" PRId64 " + %" PRIu32 " > %" PRId64 ")",
      static_cast<int64_t>(data_offset), data->compressed_length, static_cast<int64_t>(cd_offset));
    return kInvalidOffset;
  }

  if (data->method == kCompressStored &&
    static_cast<off64_t>(data_offset + data->uncompressed_length) > cd_offset) {
     ALOGW("Zip: bad uncompressed length in zip (%" PRId64 " + %" PRIu32 " > %" PRId64 ")",
       static_cast<int64_t>(data_offset), data->uncompressed_length,
       static_cast<int64_t>(cd_offset));
     return kInvalidOffset;
  }

  data->offset = data_offset;
  return 0;
}

struct IterationHandle {
  uint32_t position;
  // We're not using vector here because this code is used in the Windows SDK
  // where the STL is not available.
  ZipString prefix;
  ZipString suffix;
  ZipArchive* archive;

  IterationHandle(const ZipString* in_prefix,
                  const ZipString* in_suffix) {
    if (in_prefix) {
      uint8_t* name_copy = new uint8_t[in_prefix->name_length];
      memcpy(name_copy, in_prefix->name, in_prefix->name_length);
      prefix.name = name_copy;
      prefix.name_length = in_prefix->name_length;
    } else {
      prefix.name = NULL;
      prefix.name_length = 0;
    }
    if (in_suffix) {
      uint8_t* name_copy = new uint8_t[in_suffix->name_length];
      memcpy(name_copy, in_suffix->name, in_suffix->name_length);
      suffix.name = name_copy;
      suffix.name_length = in_suffix->name_length;
    } else {
      suffix.name = NULL;
      suffix.name_length = 0;
    }
  }

  ~IterationHandle() {
    delete[] prefix.name;
    delete[] suffix.name;
  }
};

int32_t StartIteration(ZipArchiveHandle handle, void** cookie_ptr,
                       const ZipString* optional_prefix,
                       const ZipString* optional_suffix) {
  ZipArchive* archive = reinterpret_cast<ZipArchive*>(handle);

  if (archive == NULL || archive->hash_table == NULL) {
    ALOGW("Zip: Invalid ZipArchiveHandle");
    return kInvalidHandle;
  }

  IterationHandle* cookie = new IterationHandle(optional_prefix, optional_suffix);
  cookie->position = 0;
  cookie->archive = archive;

  *cookie_ptr = cookie ;
  return 0;
}

void EndIteration(void* cookie) {
  delete reinterpret_cast<IterationHandle*>(cookie);
}

int32_t FindEntry(const ZipArchiveHandle handle, const ZipString& entryName,
                  ZipEntry* data) {
  const ZipArchive* archive = reinterpret_cast<ZipArchive*>(handle);
  if (entryName.name_length == 0) {
    ALOGW("Zip: Invalid filename %.*s", entryName.name_length, entryName.name);
    return kInvalidEntryName;
  }

  const int64_t ent = EntryToIndex(archive->hash_table,
    archive->hash_table_size, entryName);

  if (ent < 0) {
    ALOGV("Zip: Could not find entry %.*s", entryName.name_length, entryName.name);
    return ent;
  }

  return FindEntry(archive, ent, data);
}

int32_t Next(void* cookie, ZipEntry* data, ZipString* name) {
  IterationHandle* handle = reinterpret_cast<IterationHandle*>(cookie);
  if (handle == NULL) {
    return kInvalidHandle;
  }

  ZipArchive* archive = handle->archive;
  if (archive == NULL || archive->hash_table == NULL) {
    ALOGW("Zip: Invalid ZipArchiveHandle");
    return kInvalidHandle;
  }

  const uint32_t currentOffset = handle->position;
  const uint32_t hash_table_length = archive->hash_table_size;
  const ZipString* hash_table = archive->hash_table;

  for (uint32_t i = currentOffset; i < hash_table_length; ++i) {
    if (hash_table[i].name != NULL &&
        (handle->prefix.name_length == 0 ||
         hash_table[i].StartsWith(handle->prefix)) &&
        (handle->suffix.name_length == 0 ||
         hash_table[i].EndsWith(handle->suffix))) {
      handle->position = (i + 1);
      const int error = FindEntry(archive, i, data);
      if (!error) {
        name->name = hash_table[i].name;
        name->name_length = hash_table[i].name_length;
      }

      return error;
    }
  }

  handle->position = 0;
  return kIterationEnd;
}

class Writer {
 public:
  virtual bool Append(uint8_t* buf, size_t buf_size) = 0;
  virtual ~Writer() {}
 protected:
  Writer() = default;
 private:
  DISALLOW_COPY_AND_ASSIGN(Writer);
};

// A Writer that writes data to a fixed size memory region.
// The size of the memory region must be equal to the total size of
// the data appended to it.
class MemoryWriter : public Writer {
 public:
  MemoryWriter(uint8_t* buf, size_t size) : Writer(),
      buf_(buf), size_(size), bytes_written_(0) {
  }

  virtual bool Append(uint8_t* buf, size_t buf_size) override {
    if (bytes_written_ + buf_size > size_) {
      ALOGW("Zip: Unexpected size " ZD " (declared) vs " ZD " (actual)",
            size_, bytes_written_ + buf_size);
      return false;
    }

    memcpy(buf_ + bytes_written_, buf, buf_size);
    bytes_written_ += buf_size;
    return true;
  }

 private:
  uint8_t* const buf_;
  const size_t size_;
  size_t bytes_written_;
};

// A Writer that appends data to a file |fd| at its current position.
// The file will be truncated to the end of the written data.
class FileWriter : public Writer {
 public:

  // Creates a FileWriter for |fd| and prepare to write |entry| to it,
  // guaranteeing that the file descriptor is valid and that there's enough
  // space on the volume to write out the entry completely and that the file
  // is truncated to the correct length.
  //
  // Returns a valid FileWriter on success, |nullptr| if an error occurred.
  static std::unique_ptr<FileWriter> Create(int fd, const ZipEntry* entry) {
    const uint32_t declared_length = entry->uncompressed_length;
    const off64_t current_offset = lseek64(fd, 0, SEEK_CUR);
    if (current_offset == -1) {
      ALOGW("Zip: unable to seek to current location on fd %d: %s", fd, strerror(errno));
      return nullptr;
    }

    int result = 0;
#if defined(__linux__)
    if (declared_length > 0) {
      // Make sure we have enough space on the volume to extract the compressed
      // entry. Note that the call to ftruncate below will change the file size but
      // will not allocate space on disk and this call to fallocate will not
      // change the file size.
      // Note: fallocate is only supported by the following filesystems -
      // btrfs, ext4, ocfs2, and xfs. Therefore fallocate might fail with
      // EOPNOTSUPP error when issued in other filesystems.
      // Hence, check for the return error code before concluding that the
      // disk does not have enough space.
      result = TEMP_FAILURE_RETRY(fallocate(fd, 0, current_offset, declared_length));
      if (result == -1 && errno == ENOSPC) {
        ALOGW("Zip: unable to allocate space for file to %" PRId64 ": %s",
              static_cast<int64_t>(declared_length + current_offset), strerror(errno));
        return std::unique_ptr<FileWriter>(nullptr);
      }
    }
#endif  // __linux__

    result = TEMP_FAILURE_RETRY(ftruncate(fd, declared_length + current_offset));
    if (result == -1) {
      ALOGW("Zip: unable to truncate file to %" PRId64 ": %s",
            static_cast<int64_t>(declared_length + current_offset), strerror(errno));
      return std::unique_ptr<FileWriter>(nullptr);
    }

    return std::unique_ptr<FileWriter>(new FileWriter(fd, declared_length));
  }

  virtual bool Append(uint8_t* buf, size_t buf_size) override {
    if (total_bytes_written_ + buf_size > declared_length_) {
      ALOGW("Zip: Unexpected size " ZD " (declared) vs " ZD " (actual)",
            declared_length_, total_bytes_written_ + buf_size);
      return false;
    }

    const bool result = android::base::WriteFully(fd_, buf, buf_size);
    if (result) {
      total_bytes_written_ += buf_size;
    } else {
      ALOGW("Zip: unable to write " ZD " bytes to file; %s", buf_size, strerror(errno));
    }

    return result;
  }
 private:
  FileWriter(const int fd, const size_t declared_length) :
      Writer(),
      fd_(fd),
      declared_length_(declared_length),
      total_bytes_written_(0) {
  }

  const int fd_;
  const size_t declared_length_;
  size_t total_bytes_written_;
};

// This method is using libz macros with old-style-casts
#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Wold-style-cast"
static inline int zlib_inflateInit2(z_stream* stream, int window_bits) {
  return inflateInit2(stream, window_bits);
}
#pragma GCC diagnostic pop

static int32_t InflateEntryToWriter(int fd, const ZipEntry* entry,
                                    Writer* writer, uint64_t* crc_out) {
  const size_t kBufSize = 32768;
  std::vector<uint8_t> read_buf(kBufSize);
  std::vector<uint8_t> write_buf(kBufSize);
  z_stream zstream;
  int zerr;

  /*
   * Initialize the zlib stream struct.
   */
  memset(&zstream, 0, sizeof(zstream));
  zstream.zalloc = Z_NULL;
  zstream.zfree = Z_NULL;
  zstream.opaque = Z_NULL;
  zstream.next_in = NULL;
  zstream.avail_in = 0;
  zstream.next_out = &write_buf[0];
  zstream.avail_out = kBufSize;
  zstream.data_type = Z_UNKNOWN;

  /*
   * Use the undocumented "negative window bits" feature to tell zlib
   * that there's no zlib header waiting for it.
   */
  zerr = zlib_inflateInit2(&zstream, -MAX_WBITS);
  if (zerr != Z_OK) {
    if (zerr == Z_VERSION_ERROR) {
      ALOGE("Installed zlib is not compatible with linked version (%s)",
        ZLIB_VERSION);
    } else {
      ALOGW("Call to inflateInit2 failed (zerr=%d)", zerr);
    }

    return kZlibError;
  }

  auto zstream_deleter = [](z_stream* stream) {
    inflateEnd(stream);  /* free up any allocated structures */
  };

  std::unique_ptr<z_stream, decltype(zstream_deleter)> zstream_guard(&zstream, zstream_deleter);

  const uint32_t uncompressed_length = entry->uncompressed_length;

  uint32_t compressed_length = entry->compressed_length;
  do {
    /* read as much as we can */
    if (zstream.avail_in == 0) {
      const size_t getSize = (compressed_length > kBufSize) ? kBufSize : compressed_length;
      if (!android::base::ReadFully(fd, read_buf.data(), getSize)) {
        ALOGW("Zip: inflate read failed, getSize = %zu: %s", getSize, strerror(errno));
        return kIoError;
      }

      compressed_length -= getSize;

      zstream.next_in = &read_buf[0];
      zstream.avail_in = getSize;
    }

    /* uncompress the data */
    zerr = inflate(&zstream, Z_NO_FLUSH);
    if (zerr != Z_OK && zerr != Z_STREAM_END) {
      ALOGW("Zip: inflate zerr=%d (nIn=%p aIn=%u nOut=%p aOut=%u)",
          zerr, zstream.next_in, zstream.avail_in,
          zstream.next_out, zstream.avail_out);
      return kZlibError;
    }

    /* write when we're full or when we're done */
    if (zstream.avail_out == 0 ||
      (zerr == Z_STREAM_END && zstream.avail_out != kBufSize)) {
      const size_t write_size = zstream.next_out - &write_buf[0];
      if (!writer->Append(&write_buf[0], write_size)) {
        // The file might have declared a bogus length.
        return kInconsistentInformation;
      }

      zstream.next_out = &write_buf[0];
      zstream.avail_out = kBufSize;
    }
  } while (zerr == Z_OK);

  assert(zerr == Z_STREAM_END);     /* other errors should've been caught */

  // stream.adler holds the crc32 value for such streams.
  *crc_out = zstream.adler;

  if (zstream.total_out != uncompressed_length || compressed_length != 0) {
    ALOGW("Zip: size mismatch on inflated file (%lu vs %" PRIu32 ")",
        zstream.total_out, uncompressed_length);
    return kInconsistentInformation;
  }

  return 0;
}

static int32_t CopyEntryToWriter(int fd, const ZipEntry* entry, Writer* writer,
                                 uint64_t *crc_out) {
  static const uint32_t kBufSize = 32768;
  std::vector<uint8_t> buf(kBufSize);

  const uint32_t length = entry->uncompressed_length;
  uint32_t count = 0;
  uint64_t crc = 0;
  while (count < length) {
    uint32_t remaining = length - count;

    // Safe conversion because kBufSize is narrow enough for a 32 bit signed
    // value.
    const size_t block_size = (remaining > kBufSize) ? kBufSize : remaining;
    if (!android::base::ReadFully(fd, buf.data(), block_size)) {
      ALOGW("CopyFileToFile: copy read failed, block_size = %zu: %s", block_size, strerror(errno));
      return kIoError;
    }

    if (!writer->Append(&buf[0], block_size)) {
      return kIoError;
    }
    crc = crc32(crc, &buf[0], block_size);
    count += block_size;
  }

  *crc_out = crc;

  return 0;
}

int32_t ExtractToWriter(ZipArchiveHandle handle,
                        ZipEntry* entry, Writer* writer) {
  ZipArchive* archive = reinterpret_cast<ZipArchive*>(handle);
  const uint16_t method = entry->method;
  off64_t data_offset = entry->offset;

  if (lseek64(archive->fd, data_offset, SEEK_SET) != data_offset) {
    ALOGW("Zip: lseek to data at %" PRId64 " failed", static_cast<int64_t>(data_offset));
    return kIoError;
  }

  // this should default to kUnknownCompressionMethod.
  int32_t return_value = -1;
  uint64_t crc = 0;
  if (method == kCompressStored) {
    return_value = CopyEntryToWriter(archive->fd, entry, writer, &crc);
  } else if (method == kCompressDeflated) {
    return_value = InflateEntryToWriter(archive->fd, entry, writer, &crc);
  }

  if (!return_value && entry->has_data_descriptor) {
    return_value = UpdateEntryFromDataDescriptor(archive->fd, entry);
    if (return_value) {
      return return_value;
    }
  }

  // TODO: Fix this check by passing the right flags to inflate2 so that
  // it calculates the CRC for us.
  if (entry->crc32 != crc && false) {
    ALOGW("Zip: crc mismatch: expected %" PRIu32 ", was %" PRIu64, entry->crc32, crc);
    return kInconsistentInformation;
  }

  return return_value;
}

int32_t ExtractToMemory(ZipArchiveHandle handle, ZipEntry* entry,
                        uint8_t* begin, uint32_t size) {
  std::unique_ptr<Writer> writer(new MemoryWriter(begin, size));
  return ExtractToWriter(handle, entry, writer.get());
}

int32_t ExtractEntryToFile(ZipArchiveHandle handle,
                           ZipEntry* entry, int fd) {
  std::unique_ptr<Writer> writer(FileWriter::Create(fd, entry));
  if (writer.get() == nullptr) {
    return kIoError;
  }

  return ExtractToWriter(handle, entry, writer.get());
}

const char* ErrorCodeString(int32_t error_code) {
  if (error_code > kErrorMessageLowerBound && error_code < kErrorMessageUpperBound) {
    return kErrorMessages[error_code * -1];
  }

  return kErrorMessages[0];
}

int GetFileDescriptor(const ZipArchiveHandle handle) {
  return reinterpret_cast<ZipArchive*>(handle)->fd;
}
