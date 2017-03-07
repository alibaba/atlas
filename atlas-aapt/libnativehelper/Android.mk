# Copyright (C) 2009 The Android Open Source Project
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

local_src_files := \
    JNIHelp.cpp \
    JniConstants.cpp \
    toStringArray.cpp

#
# Build for the target (device).
#

include $(CLEAR_VARS)
LOCAL_SRC_FILES := \
    $(local_src_files) \
    JniInvocation.cpp \
    AsynchronousCloseMonitor.cpp

LOCAL_SHARED_LIBRARIES := liblog
LOCAL_MODULE_TAGS := optional
LOCAL_MODULE := libnativehelper
LOCAL_CLANG := true
LOCAL_CFLAGS := -Werror -fvisibility=protected
LOCAL_C_INCLUDES := libcore/include
LOCAL_SHARED_LIBRARIES += libdl
LOCAL_ADDITIONAL_DEPENDENCIES := $(LOCAL_PATH)/Android.mk
include $(BUILD_SHARED_LIBRARY)


#
# NDK-only build for the target (device), using libc++.
# - Relies only on NDK exposed functionality.
# - This doesn't include JniInvocation.
#

include $(CLEAR_VARS)
LOCAL_MODULE_TAGS := optional
LOCAL_MODULE := libnativehelper_compat_libc++
LOCAL_CLANG := true
LOCAL_C_INCLUDES := \
    $(LOCAL_PATH)/include/nativehelper
LOCAL_EXPORT_C_INCLUDE_DIRS := \
    $(LOCAL_PATH)/include/nativehelper
LOCAL_CFLAGS := -Werror
LOCAL_SRC_FILES := $(local_src_files)
LOCAL_LDFLAGS := -llog -ldl
LOCAL_SDK_VERSION := 19
LOCAL_NDK_STL_VARIANT := c++_static
LOCAL_ADDITIONAL_DEPENDENCIES := $(LOCAL_PATH)/Android.mk
include $(BUILD_SHARED_LIBRARY)


#
# Build for the host.
#

include $(CLEAR_VARS)
LOCAL_MODULE := libnativehelper
LOCAL_MODULE_TAGS := optional
LOCAL_CLANG := true
LOCAL_SRC_FILES := \
    $(local_src_files) \
    JniInvocation.cpp
ifeq ($(HOST_OS),linux)
LOCAL_SRC_FILES += AsynchronousCloseMonitor.cpp
endif
LOCAL_CFLAGS := -Werror -fvisibility=protected
LOCAL_C_INCLUDES := libcore/include
LOCAL_SHARED_LIBRARIES := liblog
LOCAL_LDFLAGS := -ldl
LOCAL_ADDITIONAL_DEPENDENCIES := $(LOCAL_PATH)/Android.mk
LOCAL_MULTILIB := both
include $(BUILD_HOST_SHARED_LIBRARY)


#
# Build static for the host.
#

include $(CLEAR_VARS)
LOCAL_MODULE := libnativehelper
LOCAL_MODULE_TAGS := optional
LOCAL_CLANG := true
LOCAL_SRC_FILES := \
    $(local_src_files) \
    JniInvocation.cpp
ifeq ($(HOST_OS),linux)
LOCAL_SRC_FILES += AsynchronousCloseMonitor.cpp
endif
LOCAL_CFLAGS := -Werror -fvisibility=protected
LOCAL_C_INCLUDES := libcore/include
LOCAL_STATIC_LIBRARIES := liblog
LOCAL_LDFLAGS := -ldl
LOCAL_ADDITIONAL_DEPENDENCIES := $(LOCAL_PATH)/Android.mk
LOCAL_MULTILIB := both
include $(BUILD_HOST_STATIC_LIBRARY)


#
# Tests.
#

include $(LOCAL_PATH)/tests/Android.mk
