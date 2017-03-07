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

#include "ziparchive/zip_archive.h"
#include "ziparchive/zip_writer.h"

#include <android-base/test_utils.h>
#include <gtest/gtest.h>
#include <time.h>
#include <memory>
#include <vector>

struct zipwriter : public ::testing::Test {
  TemporaryFile* temp_file_;
  int fd_;
  FILE* file_;

  void SetUp() override {
    temp_file_ = new TemporaryFile();
    fd_ = temp_file_->fd;
    file_ = fdopen(fd_, "w");
    ASSERT_NE(file_, nullptr);
  }

  void TearDown() override {
    fclose(file_);
    delete temp_file_;
  }
};

TEST_F(zipwriter, WriteUncompressedZipWithOneFile) {
  ZipWriter writer(file_);

  const char* expected = "hello";

  ASSERT_EQ(0, writer.StartEntry("file.txt", 0));
  ASSERT_EQ(0, writer.WriteBytes("he", 2));
  ASSERT_EQ(0, writer.WriteBytes("llo", 3));
  ASSERT_EQ(0, writer.FinishEntry());
  ASSERT_EQ(0, writer.Finish());

  ASSERT_GE(0, lseek(fd_, 0, SEEK_SET));

  ZipArchiveHandle handle;
  ASSERT_EQ(0, OpenArchiveFd(fd_, "temp", &handle, false));

  ZipEntry data;
  ASSERT_EQ(0, FindEntry(handle, ZipString("file.txt"), &data));
  EXPECT_EQ(strlen(expected), data.compressed_length);
  EXPECT_EQ(strlen(expected), data.uncompressed_length);
  EXPECT_EQ(kCompressStored, data.method);

  char buffer[6];
  EXPECT_EQ(0,
            ExtractToMemory(handle, &data, reinterpret_cast<uint8_t*>(&buffer), sizeof(buffer)));
  buffer[5] = 0;

  EXPECT_STREQ(expected, buffer);

  CloseArchive(handle);
}

TEST_F(zipwriter, WriteUncompressedZipWithMultipleFiles) {
  ZipWriter writer(file_);

  ASSERT_EQ(0, writer.StartEntry("file.txt", 0));
  ASSERT_EQ(0, writer.WriteBytes("he", 2));
  ASSERT_EQ(0, writer.FinishEntry());

  ASSERT_EQ(0, writer.StartEntry("file/file.txt", 0));
  ASSERT_EQ(0, writer.WriteBytes("llo", 3));
  ASSERT_EQ(0, writer.FinishEntry());

  ASSERT_EQ(0, writer.StartEntry("file/file2.txt", 0));
  ASSERT_EQ(0, writer.FinishEntry());

  ASSERT_EQ(0, writer.Finish());

  ASSERT_GE(0, lseek(fd_, 0, SEEK_SET));

  ZipArchiveHandle handle;
  ASSERT_EQ(0, OpenArchiveFd(fd_, "temp", &handle, false));

  char buffer[4];
  ZipEntry data;

  ASSERT_EQ(0, FindEntry(handle, ZipString("file.txt"), &data));
  EXPECT_EQ(kCompressStored, data.method);
  EXPECT_EQ(2u, data.compressed_length);
  EXPECT_EQ(2u, data.uncompressed_length);
  ASSERT_EQ(0,
            ExtractToMemory(handle, &data, reinterpret_cast<uint8_t*>(buffer), arraysize(buffer)));
  buffer[2] = 0;
  EXPECT_STREQ("he", buffer);

  ASSERT_EQ(0, FindEntry(handle, ZipString("file/file.txt"), &data));
  EXPECT_EQ(kCompressStored, data.method);
  EXPECT_EQ(3u, data.compressed_length);
  EXPECT_EQ(3u, data.uncompressed_length);
  ASSERT_EQ(0,
            ExtractToMemory(handle, &data, reinterpret_cast<uint8_t*>(buffer), arraysize(buffer)));
  buffer[3] = 0;
  EXPECT_STREQ("llo", buffer);

  ASSERT_EQ(0, FindEntry(handle, ZipString("file/file2.txt"), &data));
  EXPECT_EQ(kCompressStored, data.method);
  EXPECT_EQ(0u, data.compressed_length);
  EXPECT_EQ(0u, data.uncompressed_length);

  CloseArchive(handle);
}

TEST_F(zipwriter, WriteUncompressedZipFileWithAlignedFlag) {
  ZipWriter writer(file_);

  ASSERT_EQ(0, writer.StartEntry("align.txt", ZipWriter::kAlign32));
  ASSERT_EQ(0, writer.WriteBytes("he", 2));
  ASSERT_EQ(0, writer.FinishEntry());
  ASSERT_EQ(0, writer.Finish());

  ASSERT_GE(0, lseek(fd_, 0, SEEK_SET));

  ZipArchiveHandle handle;
  ASSERT_EQ(0, OpenArchiveFd(fd_, "temp", &handle, false));

  ZipEntry data;
  ASSERT_EQ(0, FindEntry(handle, ZipString("align.txt"), &data));
  EXPECT_EQ(0, data.offset & 0x03);

  CloseArchive(handle);
}

void ConvertZipTimeToTm(uint32_t& zip_time, struct tm* tm) {
  memset(tm, 0, sizeof(struct tm));
  tm->tm_hour = (zip_time >> 11) & 0x1f;
  tm->tm_min = (zip_time >> 5) & 0x3f;
  tm->tm_sec = (zip_time & 0x1f) << 1;

  tm->tm_year = ((zip_time >> 25) & 0x7f) + 80;
  tm->tm_mon = ((zip_time >> 21) & 0xf) - 1;
  tm->tm_mday = (zip_time >> 16) & 0x1f;
}

static struct tm MakeTm() {
  struct tm tm;
  memset(&tm, 0, sizeof(struct tm));
  tm.tm_year = 2001 - 1900;
  tm.tm_mon = 1;
  tm.tm_mday = 12;
  tm.tm_hour = 18;
  tm.tm_min = 30;
  tm.tm_sec = 20;
  return tm;
}

TEST_F(zipwriter, WriteUncompressedZipFileWithAlignedFlagAndTime) {
  ZipWriter writer(file_);

  struct tm tm = MakeTm();
  time_t time = mktime(&tm);
  ASSERT_EQ(0, writer.StartEntryWithTime("align.txt", ZipWriter::kAlign32, time));
  ASSERT_EQ(0, writer.WriteBytes("he", 2));
  ASSERT_EQ(0, writer.FinishEntry());
  ASSERT_EQ(0, writer.Finish());

  ASSERT_GE(0, lseek(fd_, 0, SEEK_SET));

  ZipArchiveHandle handle;
  ASSERT_EQ(0, OpenArchiveFd(fd_, "temp", &handle, false));

  ZipEntry data;
  ASSERT_EQ(0, FindEntry(handle, ZipString("align.txt"), &data));
  EXPECT_EQ(0, data.offset & 0x03);

  struct tm mod;
  ConvertZipTimeToTm(data.mod_time, &mod);
  EXPECT_EQ(tm.tm_sec, mod.tm_sec);
  EXPECT_EQ(tm.tm_min, mod.tm_min);
  EXPECT_EQ(tm.tm_hour, mod.tm_hour);
  EXPECT_EQ(tm.tm_mday, mod.tm_mday);
  EXPECT_EQ(tm.tm_mon, mod.tm_mon);
  EXPECT_EQ(tm.tm_year, mod.tm_year);

  CloseArchive(handle);
}

TEST_F(zipwriter, WriteUncompressedZipFileWithAlignedValue) {
  ZipWriter writer(file_);

  ASSERT_EQ(0, writer.StartAlignedEntry("align.txt", 0, 4096));
  ASSERT_EQ(0, writer.WriteBytes("he", 2));
  ASSERT_EQ(0, writer.FinishEntry());
  ASSERT_EQ(0, writer.Finish());

  ASSERT_GE(0, lseek(fd_, 0, SEEK_SET));

  ZipArchiveHandle handle;
  ASSERT_EQ(0, OpenArchiveFd(fd_, "temp", &handle, false));

  ZipEntry data;
  ASSERT_EQ(0, FindEntry(handle, ZipString("align.txt"), &data));
  EXPECT_EQ(0, data.offset & 0xfff);

  CloseArchive(handle);
}

TEST_F(zipwriter, WriteUncompressedZipFileWithAlignedValueAndTime) {
  ZipWriter writer(file_);

  struct tm tm = MakeTm();
  time_t time = mktime(&tm);
  ASSERT_EQ(0, writer.StartAlignedEntryWithTime("align.txt", 0, time, 4096));
  ASSERT_EQ(0, writer.WriteBytes("he", 2));
  ASSERT_EQ(0, writer.FinishEntry());
  ASSERT_EQ(0, writer.Finish());

  ASSERT_GE(0, lseek(fd_, 0, SEEK_SET));

  ZipArchiveHandle handle;
  ASSERT_EQ(0, OpenArchiveFd(fd_, "temp", &handle, false));

  ZipEntry data;
  ASSERT_EQ(0, FindEntry(handle, ZipString("align.txt"), &data));
  EXPECT_EQ(0, data.offset & 0xfff);

  struct tm mod;
  ConvertZipTimeToTm(data.mod_time, &mod);
  EXPECT_EQ(tm.tm_sec, mod.tm_sec);
  EXPECT_EQ(tm.tm_min, mod.tm_min);
  EXPECT_EQ(tm.tm_hour, mod.tm_hour);
  EXPECT_EQ(tm.tm_mday, mod.tm_mday);
  EXPECT_EQ(tm.tm_mon, mod.tm_mon);
  EXPECT_EQ(tm.tm_year, mod.tm_year);

  CloseArchive(handle);
}

TEST_F(zipwriter, WriteCompressedZipWithOneFile) {
  ZipWriter writer(file_);

  ASSERT_EQ(0, writer.StartEntry("file.txt", ZipWriter::kCompress));
  ASSERT_EQ(0, writer.WriteBytes("helo", 4));
  ASSERT_EQ(0, writer.FinishEntry());
  ASSERT_EQ(0, writer.Finish());

  ASSERT_GE(0, lseek(fd_, 0, SEEK_SET));

  ZipArchiveHandle handle;
  ASSERT_EQ(0, OpenArchiveFd(fd_, "temp", &handle, false));

  ZipEntry data;
  ASSERT_EQ(0, FindEntry(handle, ZipString("file.txt"), &data));
  EXPECT_EQ(kCompressDeflated, data.method);
  EXPECT_EQ(4u, data.uncompressed_length);

  char buffer[5];
  ASSERT_EQ(0,
            ExtractToMemory(handle, &data, reinterpret_cast<uint8_t*>(buffer), arraysize(buffer)));
  buffer[4] = 0;

  EXPECT_STREQ("helo", buffer);

  CloseArchive(handle);
}

TEST_F(zipwriter, WriteCompressedZipFlushFull) {
  // This exact data will cause the Finish() to require multiple calls
  // to deflate() because the ZipWriter buffer isn't big enough to hold
  // the entire compressed data buffer.
  constexpr size_t kBufSize = 10000000;
  std::vector<uint8_t> buffer(kBufSize);
  size_t prev = 1;
  for (size_t i = 0; i < kBufSize; i++) {
    buffer[i] = i + prev;
    prev = i;
  }

  ZipWriter writer(file_);
  ASSERT_EQ(0, writer.StartEntry("file.txt", ZipWriter::kCompress));
  ASSERT_EQ(0, writer.WriteBytes(buffer.data(), buffer.size()));
  ASSERT_EQ(0, writer.FinishEntry());
  ASSERT_EQ(0, writer.Finish());

  ASSERT_GE(0, lseek(fd_, 0, SEEK_SET));

  ZipArchiveHandle handle;
  ASSERT_EQ(0, OpenArchiveFd(fd_, "temp", &handle, false));

  ZipEntry data;
  ASSERT_EQ(0, FindEntry(handle, ZipString("file.txt"), &data));
  EXPECT_EQ(kCompressDeflated, data.method);
  EXPECT_EQ(kBufSize, data.uncompressed_length);

  std::vector<uint8_t> decompress(kBufSize);
  memset(decompress.data(), 0, kBufSize);
  ASSERT_EQ(0, ExtractToMemory(handle, &data, decompress.data(), decompress.size()));
  EXPECT_EQ(0, memcmp(decompress.data(), buffer.data(), kBufSize))
      << "Input buffer and output buffer are different.";

  CloseArchive(handle);
}

TEST_F(zipwriter, CheckStartEntryErrors) {
  ZipWriter writer(file_);

  ASSERT_EQ(-5, writer.StartAlignedEntry("align.txt", ZipWriter::kAlign32, 4096));
  ASSERT_EQ(-6, writer.StartAlignedEntry("align.txt", 0, 3));
}
