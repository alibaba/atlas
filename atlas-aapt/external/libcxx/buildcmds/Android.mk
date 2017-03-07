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

# Don't build for unbundled branches
ifeq (,$(TARGET_BUILD_APPS))

include $(CLEAR_VARS)
LOCAL_MODULE := libc++_build_commands_$(ANDROID_DEVICE)
LOCAL_SRC_FILES := dummy.cpp
LOCAL_CXX_STL := libc++
LOCAL_C_INCLUDES := $(LOCAL_PATH)/../test/support
LOCAL_CPPFLAGS := \
    -std=c++14 \
    -fsized-deallocation \
    -fexceptions \
    -UNDEBUG \
    -w \
    -Wno-error=non-virtual-dtor \

# Optimization is causing relocation for nothrow new to be thrown away.
# http://llvm.org/bugs/show_bug.cgi?id=21421
LOCAL_CPPFLAGS += -O0

LOCAL_RTTI_FLAG := -frtti
include $(LOCAL_PATH)/testconfig.mk

endif
