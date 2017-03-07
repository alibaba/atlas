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

#include "entry_name_utils-inl.h"
#include "zip_archive_common.h"
#include "ziparchive/zip_writer.h"

#include <utils/Log.h>

#include <sys/param.h>

#include <cassert>
#include <cstdio>
#include <memory>
#include <vector>
#include <zlib.h>
#define DEF_MEM_LEVEL 8                // normally in zutil.h?

#if !defined(powerof2)
#define powerof2(x) ((((x)-1)&(x))==0)
#endif

/* Zip compression methods we support */
enum {
  kCompressStored     = 0,        // no compression
  kCompressDeflated   = 8,        // standard deflate
};

// Size of the output buffer used for compression.
static const size_t kBufSize = 32768u;

// No error, operation completed successfully.
static const int32_t kNoError = 0;

// The ZipWriter is in a bad state.
static const int32_t kInvalidState = -1;

// There was an IO error while writing to disk.
static const int32_t kIoError = -2;

// The zip entry name was invalid.
static const int32_t kInvalidEntryName = -3;

// An error occurred in zlib.
static const int32_t kZlibError = -4;

// The start aligned function was called with the aligned flag.
static const int32_t kInvalidAlign32Flag = -5;

// The alignment parameter is not a power of 2.
static const int32_t kInvalidAlignment = -6;

static const char* sErrorCodes[] = {
    "Invalid state",
    "IO error",
    "Invalid entry name",
    "Zlib error",
};

const char* ZipWriter::ErrorCodeString(int32_t error_code) {
  if (error_code < 0 && (-error_code) < static_cast<int32_t>(arraysize(sErrorCodes))) {
    return sErrorCodes[-error_code];
  }
  return nullptr;
}

static void DeleteZStream(z_stream* stream) {
  deflateEnd(stream);
  delete stream;
}

ZipWriter::ZipWriter(FILE* f) : file_(f), current_offset_(0), state_(State::kWritingZip),
                                z_stream_(nullptr, DeleteZStream), buffer_(kBufSize) {
}

ZipWriter::ZipWriter(ZipWriter&& writer) : file_(writer.file_),
                                           current_offset_(writer.current_offset_),
                                           state_(writer.state_),
                                           files_(std::move(writer.files_)),
                                           z_stream_(std::move(writer.z_stream_)),
                                           buffer_(std::move(writer.buffer_)){
  writer.file_ = nullptr;
  writer.state_ = State::kError;
}

ZipWriter& ZipWriter::operator=(ZipWriter&& writer) {
  file_ = writer.file_;
  current_offset_ = writer.current_offset_;
  state_ = writer.state_;
  files_ = std::move(writer.files_);
  z_stream_ = std::move(writer.z_stream_);
  buffer_ = std::move(writer.buffer_);
  writer.file_ = nullptr;
  writer.state_ = State::kError;
  return *this;
}

int32_t ZipWriter::HandleError(int32_t error_code) {
  state_ = State::kError;
  z_stream_.reset();
  return error_code;
}

int32_t ZipWriter::StartEntry(const char* path, size_t flags) {
  uint32_t alignment = 0;
  if (flags & kAlign32) {
    flags &= ~kAlign32;
    alignment = 4;
  }
  return StartAlignedEntryWithTime(path, flags, time_t(), alignment);
}

int32_t ZipWriter::StartAlignedEntry(const char* path, size_t flags, uint32_t alignment) {
  return StartAlignedEntryWithTime(path, flags, time_t(), alignment);
}

int32_t ZipWriter::StartEntryWithTime(const char* path, size_t flags, time_t time) {
  uint32_t alignment = 0;
  if (flags & kAlign32) {
    flags &= ~kAlign32;
    alignment = 4;
  }
  return StartAlignedEntryWithTime(path, flags, time, alignment);
}

static void ExtractTimeAndDate(time_t when, uint16_t* out_time, uint16_t* out_date) {
  /* round up to an even number of seconds */
  when = static_cast<time_t>((static_cast<unsigned long>(when) + 1) & (~1));

  struct tm* ptm;
#if !defined(_WIN32)
    struct tm tm_result;
    ptm = localtime_r(&when, &tm_result);
#else
    ptm = localtime(&when);
#endif

  int year = ptm->tm_year;
  if (year < 80) {
    year = 80;
  }

  *out_date = (year - 80) << 9 | (ptm->tm_mon + 1) << 5 | ptm->tm_mday;
  *out_time = ptm->tm_hour << 11 | ptm->tm_min << 5 | ptm->tm_sec >> 1;
}

int32_t ZipWriter::StartAlignedEntryWithTime(const char* path, size_t flags,
                                             time_t time, uint32_t alignment) {
  if (state_ != State::kWritingZip) {
    return kInvalidState;
  }

  if (flags & kAlign32) {
    return kInvalidAlign32Flag;
  }

  if (powerof2(alignment) == 0) {
    return kInvalidAlignment;
  }

  FileInfo fileInfo = {};
  fileInfo.path = std::string(path);
  fileInfo.local_file_header_offset = current_offset_;

  if (!IsValidEntryName(reinterpret_cast<const uint8_t*>(fileInfo.path.data()),
                       fileInfo.path.size())) {
    return kInvalidEntryName;
  }

  LocalFileHeader header = {};
  header.lfh_signature = LocalFileHeader::kSignature;

  // Set this flag to denote that a DataDescriptor struct will appear after the data,
  // containing the crc and size fields.
  header.gpb_flags |= kGPBDDFlagMask;

  if (flags & ZipWriter::kCompress) {
    fileInfo.compression_method = kCompressDeflated;

    int32_t result = PrepareDeflate();
    if (result != kNoError) {
      return result;
    }
  } else {
    fileInfo.compression_method = kCompressStored;
  }
  header.compression_method = fileInfo.compression_method;

  ExtractTimeAndDate(time, &fileInfo.last_mod_time, &fileInfo.last_mod_date);
  header.last_mod_time = fileInfo.last_mod_time;
  header.last_mod_date = fileInfo.last_mod_date;

  header.file_name_length = fileInfo.path.size();

  off64_t offset = current_offset_ + sizeof(header) + fileInfo.path.size();
  std::vector<char> zero_padding;
  if (alignment != 0 && (offset & (alignment - 1))) {
    // Pad the extra field so the data will be aligned.
    uint16_t padding = alignment - (offset % alignment);
    header.extra_field_length = padding;
    offset += padding;
    zero_padding.resize(padding);
    memset(zero_padding.data(), 0, zero_padding.size());
  }

  if (fwrite(&header, sizeof(header), 1, file_) != 1) {
    return HandleError(kIoError);
  }

  if (fwrite(path, sizeof(*path), fileInfo.path.size(), file_) != fileInfo.path.size()) {
    return HandleError(kIoError);
  }

  if (header.extra_field_length != 0 &&
      fwrite(zero_padding.data(), 1, header.extra_field_length, file_)
      != header.extra_field_length) {
    return HandleError(kIoError);
  }

  files_.emplace_back(std::move(fileInfo));

  current_offset_ = offset;
  state_ = State::kWritingEntry;
  return kNoError;
}

int32_t ZipWriter::PrepareDeflate() {
  assert(state_ == State::kWritingZip);

  // Initialize the z_stream for compression.
  z_stream_ = std::unique_ptr<z_stream, void(*)(z_stream*)>(new z_stream(), DeleteZStream);

  int zerr = deflateInit2(z_stream_.get(), Z_BEST_COMPRESSION, Z_DEFLATED, -MAX_WBITS,
                          DEF_MEM_LEVEL, Z_DEFAULT_STRATEGY);
  if (zerr != Z_OK) {
    if (zerr == Z_VERSION_ERROR) {
      ALOGE("Installed zlib is not compatible with linked version (%s)", ZLIB_VERSION);
      return HandleError(kZlibError);
    } else {
      ALOGE("deflateInit2 failed (zerr=%d)", zerr);
      return HandleError(kZlibError);
    }
  }

  z_stream_->next_out = buffer_.data();
  z_stream_->avail_out = buffer_.size();
  return kNoError;
}

int32_t ZipWriter::WriteBytes(const void* data, size_t len) {
  if (state_ != State::kWritingEntry) {
    return HandleError(kInvalidState);
  }

  FileInfo& currentFile = files_.back();
  int32_t result = kNoError;
  if (currentFile.compression_method & kCompressDeflated) {
    result = CompressBytes(&currentFile, data, len);
  } else {
    result = StoreBytes(&currentFile, data, len);
  }

  if (result != kNoError) {
    return result;
  }

  currentFile.crc32 = crc32(currentFile.crc32, reinterpret_cast<const Bytef*>(data), len);
  currentFile.uncompressed_size += len;
  return kNoError;
}

int32_t ZipWriter::StoreBytes(FileInfo* file, const void* data, size_t len) {
  assert(state_ == State::kWritingEntry);

  if (fwrite(data, 1, len, file_) != len) {
    return HandleError(kIoError);
  }
  file->compressed_size += len;
  current_offset_ += len;
  return kNoError;
}

int32_t ZipWriter::CompressBytes(FileInfo* file, const void* data, size_t len) {
  assert(state_ == State::kWritingEntry);
  assert(z_stream_);
  assert(z_stream_->next_out != nullptr);
  assert(z_stream_->avail_out != 0);

  // Prepare the input.
  z_stream_->next_in = reinterpret_cast<const uint8_t*>(data);
  z_stream_->avail_in = len;

  while (z_stream_->avail_in > 0) {
    // We have more data to compress.
    int zerr = deflate(z_stream_.get(), Z_NO_FLUSH);
    if (zerr != Z_OK) {
      return HandleError(kZlibError);
    }

    if (z_stream_->avail_out == 0) {
      // The output is full, let's write it to disk.
      size_t write_bytes = z_stream_->next_out - buffer_.data();
      if (fwrite(buffer_.data(), 1, write_bytes, file_) != write_bytes) {
        return HandleError(kIoError);
      }
      file->compressed_size += write_bytes;
      current_offset_ += write_bytes;

      // Reset the output buffer for the next input.
      z_stream_->next_out = buffer_.data();
      z_stream_->avail_out = buffer_.size();
    }
  }
  return kNoError;
}

int32_t ZipWriter::FlushCompressedBytes(FileInfo* file) {
  assert(state_ == State::kWritingEntry);
  assert(z_stream_);
  assert(z_stream_->next_out != nullptr);
  assert(z_stream_->avail_out != 0);

  // Keep deflating while there isn't enough space in the buffer to
  // to complete the compress.
  int zerr;
  while ((zerr = deflate(z_stream_.get(), Z_FINISH)) == Z_OK) {
    assert(z_stream_->avail_out == 0);
    size_t write_bytes = z_stream_->next_out - buffer_.data();
    if (fwrite(buffer_.data(), 1, write_bytes, file_) != write_bytes) {
      return HandleError(kIoError);
    }
    file->compressed_size += write_bytes;
    current_offset_ += write_bytes;

    z_stream_->next_out = buffer_.data();
    z_stream_->avail_out = buffer_.size();
  }
  if (zerr != Z_STREAM_END) {
    return HandleError(kZlibError);
  }

  size_t write_bytes = z_stream_->next_out - buffer_.data();
  if (write_bytes != 0) {
    if (fwrite(buffer_.data(), 1, write_bytes, file_) != write_bytes) {
      return HandleError(kIoError);
    }
    file->compressed_size += write_bytes;
    current_offset_ += write_bytes;
  }
  z_stream_.reset();
  return kNoError;
}

int32_t ZipWriter::FinishEntry() {
  if (state_ != State::kWritingEntry) {
    return kInvalidState;
  }

  FileInfo& currentFile = files_.back();
  if (currentFile.compression_method & kCompressDeflated) {
    int32_t result = FlushCompressedBytes(&currentFile);
    if (result != kNoError) {
      return result;
    }
  }

  const uint32_t sig = DataDescriptor::kOptSignature;
  if (fwrite(&sig, sizeof(sig), 1, file_) != 1) {
    state_ = State::kError;
    return kIoError;
  }

  DataDescriptor dd = {};
  dd.crc32 = currentFile.crc32;
  dd.compressed_size = currentFile.compressed_size;
  dd.uncompressed_size = currentFile.uncompressed_size;
  if (fwrite(&dd, sizeof(dd), 1, file_) != 1) {
    return HandleError(kIoError);
  }

  current_offset_ += sizeof(DataDescriptor::kOptSignature) + sizeof(dd);
  state_ = State::kWritingZip;
  return kNoError;
}

int32_t ZipWriter::Finish() {
  if (state_ != State::kWritingZip) {
    return kInvalidState;
  }

  off64_t startOfCdr = current_offset_;
  for (FileInfo& file : files_) {
    CentralDirectoryRecord cdr = {};
    cdr.record_signature = CentralDirectoryRecord::kSignature;
    cdr.gpb_flags |= kGPBDDFlagMask;
    cdr.compression_method = file.compression_method;
    cdr.last_mod_time = file.last_mod_time;
    cdr.last_mod_date = file.last_mod_date;
    cdr.crc32 = file.crc32;
    cdr.compressed_size = file.compressed_size;
    cdr.uncompressed_size = file.uncompressed_size;
    cdr.file_name_length = file.path.size();
    cdr.local_file_header_offset = file.local_file_header_offset;
    if (fwrite(&cdr, sizeof(cdr), 1, file_) != 1) {
      return HandleError(kIoError);
    }

    if (fwrite(file.path.data(), 1, file.path.size(), file_) != file.path.size()) {
      return HandleError(kIoError);
    }

    current_offset_ += sizeof(cdr) + file.path.size();
  }

  EocdRecord er = {};
  er.eocd_signature = EocdRecord::kSignature;
  er.disk_num = 0;
  er.cd_start_disk = 0;
  er.num_records_on_disk = files_.size();
  er.num_records = files_.size();
  er.cd_size = current_offset_ - startOfCdr;
  er.cd_start_offset = startOfCdr;

  if (fwrite(&er, sizeof(er), 1, file_) != 1) {
    return HandleError(kIoError);
  }

  if (fflush(file_) != 0) {
    return HandleError(kIoError);
  }

  current_offset_ += sizeof(er);
  state_ = State::kDone;
  return kNoError;
}
