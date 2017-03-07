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

san_test_files := \
    sanitizer_allocator_test.cc \
    sanitizer_atomic_test.cc \
    sanitizer_bitvector_test.cc \
    sanitizer_bvgraph_test.cc \
    sanitizer_common_test.cc \
    sanitizer_deadlock_detector_test.cc \
    sanitizer_flags_test.cc \
    sanitizer_format_interceptor_test.cc \
    sanitizer_ioctl_test.cc \
    sanitizer_libc_test.cc \
    sanitizer_linux_test.cc \
    sanitizer_list_test.cc \
    sanitizer_mutex_test.cc \
    sanitizer_nolibc_test.cc \
    sanitizer_posix_test.cc \
    sanitizer_printf_test.cc \
    sanitizer_procmaps_test.cc \
    sanitizer_stackdepot_test.cc \
    sanitizer_stacktrace_printer_test.cc \
    sanitizer_stacktrace_test.cc \
    sanitizer_stoptheworld_test.cc \
    sanitizer_suppressions_test.cc \
    sanitizer_test_main.cc \
    sanitizer_thread_registry_test.cc \

san_test_cppflags := \
    -fvisibility=hidden \
    -fno-exceptions \
    -fno-rtti \
    -std=c++11 \
    -Wall \
    -Werror \
    -Wno-unused-parameter \
    -Wno-non-virtual-dtor \

san_test_c_includes := \
    external/compiler-rt/lib \

ifneq ($(HOST_OS),darwin)

include $(CLEAR_VARS)
LOCAL_MODULE := san_test
LOCAL_CPP_EXTENSION := .cc
LOCAL_C_INCLUDES := $(san_test_c_includes)
LOCAL_CPPFLAGS := $(san_test_cppflags)
LOCAL_SRC_FILES := $(san_test_files)
LOCAL_STATIC_LIBRARIES := libsan
LOCAL_LDLIBS := -ldl -lrt
LOCAL_SANITIZE := never
include $(BUILD_HOST_NATIVE_TEST)

include $(CLEAR_VARS)
LOCAL_MODULE := san_test-Nolibc
LOCAL_CPP_EXTENSION := .cc
LOCAL_C_INCLUDES := $(san_test_c_includes) external/gtest/include
LOCAL_CPPFLAGS := $(san_test_cppflags)
LOCAL_SRC_FILES := sanitizer_nolibc_test_main.cc
LOCAL_STATIC_LIBRARIES := libsan libgtest_host
LOCAL_LDFLAGS := -nostdlib -Qunused-arguments
LOCAL_LDLIBS := -ldl
LOCAL_SANITIZE := never
include $(BUILD_HOST_EXECUTABLE)

endif
