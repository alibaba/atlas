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

#ifndef SYSTEM_CORE_INCLUDE_BINDERWRAPPER_BINDER_TEST_BASE_H_
#define SYSTEM_CORE_INCLUDE_BINDERWRAPPER_BINDER_TEST_BASE_H_

#include <base/macros.h>
#include <gtest/gtest.h>

namespace android {

class StubBinderWrapper;

// Class that can be inherited from (or aliased via typedef/using) when writing
// tests that use StubBinderManager.
class BinderTestBase : public ::testing::Test {
 public:
  BinderTestBase();
  ~BinderTestBase() override;

  StubBinderWrapper* binder_wrapper() { return binder_wrapper_; }

 protected:
  StubBinderWrapper* binder_wrapper_;  // Not owned.

 private:
  DISALLOW_COPY_AND_ASSIGN(BinderTestBase);
};

}  // namespace android

#endif  // SYSTEM_CORE_INCLUDE_BINDERWRAPPER_BINDER_TEST_BASE_H_
