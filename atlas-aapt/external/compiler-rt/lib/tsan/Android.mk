#
# Copyright (C) 2015 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
#

LOCAL_PATH:= $(call my-dir)

ifeq ($(HOST_OS),linux)
ifeq ($(HOST_ARCH),x86_64)

tsan_rtl_files := \
  rtl/tsan_clock.cc \
  rtl/tsan_flags.cc \
  rtl/tsan_fd.cc \
  rtl/tsan_ignoreset.cc \
  rtl/tsan_interceptors.cc \
  rtl/tsan_interface_ann.cc \
  rtl/tsan_interface_atomic.cc \
  rtl/tsan_interface.cc \
  rtl/tsan_interface_java.cc \
  rtl/tsan_md5.cc \
  rtl/tsan_mman.cc \
  rtl/tsan_mutex.cc \
  rtl/tsan_mutexset.cc \
  rtl/tsan_report.cc \
  rtl/tsan_rtl.cc \
  rtl/tsan_rtl_mutex.cc \
  rtl/tsan_rtl_report.cc \
  rtl/tsan_rtl_thread.cc \
  rtl/tsan_stack_trace.cc \
  rtl/tsan_stat.cc \
  rtl/tsan_suppressions.cc \
  rtl/tsan_symbolize.cc \
  rtl/tsan_sync.cc \
  rtl/tsan_platform_linux.cc \
  rtl/tsan_platform_posix.cc \
  rtl/tsan_rtl_amd64.S \

tsan_rtl_cppflags := -std=c++11 -Wall -Werror -Wno-unused-parameter -Wno-non-virtual-dtor \
                     -fno-rtti -fno-builtin

tsan_rtl_c_includes := \
  $(LOCAL_PATH)/.. \

include $(CLEAR_VARS)
LOCAL_MODULE := libtsan
LOCAL_CPP_EXTENSION := .cc
LOCAL_C_INCLUDES := $(tsan_rtl_c_includes)
LOCAL_CPPFLAGS := $(tsan_rtl_cppflags)
LOCAL_SRC_FILES := $(tsan_rtl_files)
LOCAL_CXX_STL := none
LOCAL_SANITIZE := never
LOCAL_MULTILIB := 64
LOCAL_WHOLE_STATIC_LIBRARIES := libinterception libsan libubsan
include $(BUILD_HOST_STATIC_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := libtsan_cxx
LOCAL_CPP_EXTENSION := .cc
LOCAL_C_INCLUDES = $(tsan_rtl_c_includes)
LOCAL_CPPFLAGS := $(tsan_rtl_cppflags)
LOCAL_SRC_FILES := rtl/tsan_new_delete.cc
LOCAL_CXX_STL := none
LOCAL_SANITIZE := never
LOCAL_MULTILIB := 64
LOCAL_WHOLE_STATIC_LIBRARIES := libubsan_cxx
include $(BUILD_HOST_STATIC_LIBRARY)

tsan_unit_test_src_files := \
  tests/unit/tsan_clock_test.cc \
  tests/unit/tsan_dense_alloc_test.cc \
  tests/unit/tsan_flags_test.cc \
  tests/unit/tsan_mman_test.cc \
  tests/unit/tsan_mutex_test.cc \
  tests/unit/tsan_mutexset_test.cc \
  tests/unit/tsan_shadow_test.cc \
  tests/unit/tsan_stack_test.cc \
  tests/unit/tsan_sync_test.cc \
  tests/unit/tsan_unit_test_main.cc \
  tests/unit/tsan_vector_test.cc \

tsan_unit_test_c_includes := \
  $(LOCAL_PATH)/rtl \
  $(LOCAL_PATH)/.. \

ifneq (true,$(SKIP_LLVM_TESTS))
ifndef SANITIZE_HOST

include $(CLEAR_VARS)
LOCAL_MODULE := libtsan_unit_test
LOCAL_CPP_EXTENSION := .cc
LOCAL_C_INCLUDES := $(tsan_unit_test_c_includes)
LOCAL_CPPFLAGS := $(tsan_rtl_cppflags)
LOCAL_SRC_FILES := $(tsan_unit_test_src_files)
LOCAL_SANITIZE := never
LOCAL_MULTILIB := 64
LOCAL_STATIC_LIBRARIES := libtsan libubsan
LOCAL_LDLIBS := -lrt -ldl
include $(BUILD_HOST_NATIVE_TEST)


tsan_rtl_test_src_files := \
  tests/rtl/tsan_bench.cc \
  tests/rtl/tsan_mop.cc \
  tests/rtl/tsan_mutex.cc \
  tests/rtl/tsan_posix.cc \
  tests/rtl/tsan_string.cc \
  tests/rtl/tsan_test_util_posix.cc \
  tests/rtl/tsan_test.cc \
  tests/rtl/tsan_thread.cc \

tsan_rtl_test_c_includes := \
  $(LOCAL_PATH)/rtl \
  $(LOCAL_PATH)/.. \

include $(CLEAR_VARS)
LOCAL_MODULE := libtsan_rtl_test
LOCAL_CPP_EXTENSION := .cc
LOCAL_C_INCLUDES := $(tsan_rtl_test_c_includes)
LOCAL_CPPFLAGS := $(tsan_rtl_cppflags)
LOCAL_SRC_FILES := $(tsan_rtl_test_src_files)
LOCAL_SANITIZE := never
LOCAL_MULTILIB := 64
LOCAL_STATIC_LIBRARIES := libtsan libubsan
LOCAL_LDLIBS := -lrt -ldl
include $(BUILD_HOST_NATIVE_TEST)

endif # SANITIZE_HOST
endif # SKIP_LLVM_TESTS

endif  # ifeq ($(HOST_ARCH),x86_64)
endif  # ifeq ($(HOST_OS),linux)
