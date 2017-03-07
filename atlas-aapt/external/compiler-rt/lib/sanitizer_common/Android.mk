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

san_rtl_files := \
    sanitizer_allocator.cc \
    sanitizer_common.cc \
    sanitizer_deadlock_detector1.cc \
    sanitizer_deadlock_detector2.cc \
    sanitizer_flags.cc \
    sanitizer_flag_parser.cc \
    sanitizer_libc.cc \
    sanitizer_libignore.cc \
    sanitizer_linux.cc \
    sanitizer_mac.cc \
    sanitizer_persistent_allocator.cc \
    sanitizer_platform_limits_linux.cc \
    sanitizer_platform_limits_posix.cc \
    sanitizer_posix.cc \
    sanitizer_printf.cc \
    sanitizer_procmaps_common.cc \
    sanitizer_procmaps_freebsd.cc \
    sanitizer_procmaps_linux.cc \
    sanitizer_procmaps_mac.cc \
    sanitizer_stackdepot.cc \
    sanitizer_stacktrace.cc \
    sanitizer_stacktrace_printer.cc \
    sanitizer_suppressions.cc \
    sanitizer_symbolizer.cc \
    sanitizer_symbolizer_libbacktrace.cc \
    sanitizer_symbolizer_win.cc \
    sanitizer_tls_get_addr.cc \
    sanitizer_thread_registry.cc \
    sanitizer_win.cc \

san_cdep_files := \
    sanitizer_common_libcdep.cc \
    sanitizer_coverage_libcdep.cc \
    sanitizer_coverage_mapping_libcdep.cc \
    sanitizer_linux_libcdep.cc \
    sanitizer_posix_libcdep.cc \
    sanitizer_stacktrace_libcdep.cc \
    sanitizer_stoptheworld_linux_libcdep.cc \
    sanitizer_symbolizer_libcdep.cc \
    sanitizer_symbolizer_posix_libcdep.cc \
    sanitizer_unwind_linux_libcdep.cc \

san_rtl_cppflags := \
    -fvisibility=hidden \
    -fno-exceptions \
    -std=c++11 \
    -Wall \
    -Werror \
    -Wno-unused-parameter \

san_rtl_c_includes := \
    external/compiler-rt/lib \

################################################################################
# Host modules

ifneq ($(HOST_OS),darwin)

include $(CLEAR_VARS)
LOCAL_MODULE := libsan
LOCAL_CPP_EXTENSION := .cc
LOCAL_C_INCLUDES := $(san_rtl_c_includes)
LOCAL_CPPFLAGS := $(san_rtl_cppflags)
LOCAL_SRC_FILES := $(san_rtl_files) $(san_cdep_files)
LOCAL_CXX_STL := none
LOCAL_SANITIZE := never
LOCAL_MULTILIB := both
include $(BUILD_HOST_STATIC_LIBRARY)

endif

ifndef SANITIZE_HOST
include $(LOCAL_PATH)/tests/Android.mk
endif
