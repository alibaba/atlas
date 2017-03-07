#
# Copyright (C) 2014 The Android Open Source Project
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

LOCAL_PATH := $(call my-dir)

LIBCXXABI_SRC_FILES := \
    src/abort_message.cpp \
    src/cxa_aux_runtime.cpp \
    src/cxa_default_handlers.cpp \
    src/cxa_demangle.cpp \
    src/cxa_exception.cpp \
    src/cxa_exception_storage.cpp \
    src/cxa_guard.cpp \
    src/cxa_handlers.cpp \
    src/cxa_new_delete.cpp \
    src/cxa_personality.cpp \
    src/cxa_thread_atexit.cpp \
    src/cxa_unexpected.cpp \
    src/cxa_vector.cpp \
    src/cxa_virtual.cpp \
    src/exception.cpp \
    src/private_typeinfo.cpp \
    src/stdexcept.cpp \
    src/typeinfo.cpp \

LIBCXXABI_INCLUDES := \
    $(LOCAL_PATH)/include \
    external/libcxx/include \

LIBCXXABI_RTTI_FLAG := -frtti
LIBCXXABI_CPPFLAGS := \
    -std=c++14 \
    -fexceptions \
    -Wall \
    -Wextra \
    -Wno-unused-function \
    -Werror \

include $(CLEAR_VARS)
LOCAL_MODULE := libc++abi
LOCAL_CLANG := true
LOCAL_SRC_FILES := $(LIBCXXABI_SRC_FILES)
LOCAL_C_INCLUDES := $(LIBCXXABI_INCLUDES)
LOCAL_C_INCLUDES_arm := external/libunwind_llvm/include
LOCAL_EXPORT_C_INCLUDE_DIRS := $(LOCAL_PATH)/include
LOCAL_CPPFLAGS := $(LIBCXXABI_CPPFLAGS) -DHAVE___CXA_THREAD_ATEXIT_IMPL
LOCAL_CPPFLAGS_arm := -DLIBCXXABI_USE_LLVM_UNWINDER=1
LOCAL_CPPFLAGS_arm64 := -DLIBCXXABI_USE_LLVM_UNWINDER=0
LOCAL_CPPFLAGS_mips := -DLIBCXXABI_USE_LLVM_UNWINDER=0
LOCAL_CPPFLAGS_mips64 := -DLIBCXXABI_USE_LLVM_UNWINDER=0
LOCAL_CPPFLAGS_x86 := -DLIBCXXABI_USE_LLVM_UNWINDER=0
LOCAL_CPPFLAGS_x86_64 := -DLIBCXXABI_USE_LLVM_UNWINDER=0
LOCAL_RTTI_FLAG := $(LIBCXXABI_RTTI_FLAG)
LOCAL_CXX_STL := none
LOCAL_SANITIZE := never
include $(BUILD_STATIC_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := libc++abi
LOCAL_CLANG := true
LOCAL_SRC_FILES := $(LIBCXXABI_SRC_FILES)
LOCAL_C_INCLUDES := $(LIBCXXABI_INCLUDES)
LOCAL_EXPORT_C_INCLUDE_DIRS := $(LOCAL_PATH)/include
LOCAL_CPPFLAGS := $(LIBCXXABI_CPPFLAGS)
LOCAL_RTTI_FLAG := $(LIBCXXABI_RTTI_FLAG)
LOCAL_MULTILIB := both
LOCAL_CXX_STL := none
LOCAL_SANITIZE := never

ifeq ($(HOST_OS),darwin)
# libcxxabi really doesn't like the non-LLVM assembler on Darwin
LOCAL_ASFLAGS += -integrated-as
LOCAL_CFLAGS += -integrated-as
LOCAL_CPPFLAGS += -integrated-as
endif

include $(BUILD_HOST_STATIC_LIBRARY)
