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

#define __STDC_LIMIT_MACROS

#include <gtest/gtest.h>

#include <memory>
#include <stdint.h>

#include "SharedBuffer.h"

TEST(SharedBufferTest, TestAlloc) {
  EXPECT_DEATH(android::SharedBuffer::alloc(SIZE_MAX), "");
  EXPECT_DEATH(android::SharedBuffer::alloc(SIZE_MAX - sizeof(android::SharedBuffer)), "");

  // Make sure we don't die here.
  // Check that null is returned, as we are asking for the whole address space.
  android::SharedBuffer* buf =
      android::SharedBuffer::alloc(SIZE_MAX - sizeof(android::SharedBuffer) - 1);
  ASSERT_EQ(nullptr, buf);

  buf = android::SharedBuffer::alloc(0);
  ASSERT_NE(nullptr, buf);
  ASSERT_EQ(0U, buf->size());
  buf->release();
}

TEST(SharedBufferTest, TestEditResize) {
  android::SharedBuffer* buf = android::SharedBuffer::alloc(10);
  EXPECT_DEATH(buf->editResize(SIZE_MAX - sizeof(android::SharedBuffer)), "");
  buf = android::SharedBuffer::alloc(10);
  EXPECT_DEATH(buf->editResize(SIZE_MAX), "");

  buf = android::SharedBuffer::alloc(10);
  // Make sure we don't die here.
  // Check that null is returned, as we are asking for the whole address space.
  buf = buf->editResize(SIZE_MAX - sizeof(android::SharedBuffer) - 1);
  ASSERT_EQ(nullptr, buf);

  buf = android::SharedBuffer::alloc(10);
  buf = buf->editResize(0);
  ASSERT_EQ(0U, buf->size());
  buf->release();
}
