#
# Copyright (C) 2013 The Android Open Source Project
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

LOCAL_PATH := $(call my-dir)

libziparchive_source_files := \
    zip_archive.cc \
    zip_archive_stream_entry.cc \
    zip_writer.cc \

libziparchive_test_files := \
    entry_name_utils_test.cc \
    zip_archive_test.cc \
    zip_writer_test.cc \

# ZLIB_CONST turns on const for input buffers, which is pretty standard.
libziparchive_common_c_flags := \
    -DZLIB_CONST \
    -Werror \
    -Wall \

# Incorrectly warns when C++11 empty brace {} initializer is used.
# https://gcc.gnu.org/bugzilla/show_bug.cgi?id=61489
libziparchive_common_cpp_flags := \
    -Wold-style-cast \
    -Wno-missing-field-initializers \

include $(CLEAR_VARS)
LOCAL_CPP_EXTENSION := .cc
LOCAL_SRC_FILES := $(libziparchive_source_files)
LOCAL_STATIC_LIBRARIES := libz
LOCAL_SHARED_LIBRARIES := libutils libbase
LOCAL_MODULE:= libziparchive
LOCAL_CFLAGS := $(libziparchive_common_c_flags)
LOCAL_CPPFLAGS := $(libziparchive_common_cpp_flags)
include $(BUILD_STATIC_LIBRARY)

include $(CLEAR_VARS)
LOCAL_CPP_EXTENSION := .cc
LOCAL_SRC_FILES := $(libziparchive_source_files)
LOCAL_STATIC_LIBRARIES := libz libutils libbase
LOCAL_MODULE:= libziparchive-host
LOCAL_CFLAGS := $(libziparchive_common_c_flags)
LOCAL_CFLAGS_windows := -mno-ms-bitfields
LOCAL_CPPFLAGS := $(libziparchive_common_cpp_flags)

LOCAL_MULTILIB := both
LOCAL_MODULE_HOST_OS := darwin linux windows
include $(BUILD_HOST_STATIC_LIBRARY)

include $(CLEAR_VARS)
LOCAL_CPP_EXTENSION := .cc
LOCAL_SRC_FILES := $(libziparchive_source_files)
LOCAL_STATIC_LIBRARIES := libutils
LOCAL_SHARED_LIBRARIES := libz-host liblog libbase
LOCAL_MODULE:= libziparchive-host
LOCAL_CFLAGS := $(libziparchive_common_c_flags)
LOCAL_CPPFLAGS := $(libziparchive_common_cpp_flags)
LOCAL_MULTILIB := both
include $(BUILD_HOST_SHARED_LIBRARY)

# Tests.
include $(CLEAR_VARS)
LOCAL_MODULE := ziparchive-tests
LOCAL_CPP_EXTENSION := .cc
LOCAL_CFLAGS := $(libziparchive_common_c_flags)
LOCAL_CPPFLAGS := $(libziparchive_common_cpp_flags)
LOCAL_SRC_FILES := $(libziparchive_test_files)
LOCAL_SHARED_LIBRARIES := \
    libbase \
    liblog \

LOCAL_STATIC_LIBRARIES := \
    libziparchive \
    libz \
    libutils \

include $(BUILD_NATIVE_TEST)

include $(CLEAR_VARS)
LOCAL_MODULE := ziparchive-tests-host
LOCAL_CPP_EXTENSION := .cc
LOCAL_CFLAGS := $(libziparchive_common_c_flags)
LOCAL_CPPFLAGS := -Wno-unnamed-type-template-args $(libziparchive_common_cpp_flags)
LOCAL_SRC_FILES := $(libziparchive_test_files)
LOCAL_STATIC_LIBRARIES := \
    libziparchive-host \
    libz \
    libbase \
    libutils \
    liblog \

LOCAL_MODULE_HOST_OS := darwin linux windows
include $(BUILD_HOST_NATIVE_TEST)
