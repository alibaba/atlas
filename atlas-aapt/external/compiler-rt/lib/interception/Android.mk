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

interception_src_files := \
    interception_linux.cc \
    interception_mac.cc \
    interception_type_test.cc \
    interception_win.cc \

interception_cppflags := \
    -fvisibility=hidden \
    -fno-exceptions \
    -std=c++11 \
    -Wall \
    -Werror \
    -Wno-unused-parameter \

interception_c_includes := \
    external/compiler-rt/lib \

include $(CLEAR_VARS)
LOCAL_MODULE := libinterception
LOCAL_CPP_EXTENSION := .cc
LOCAL_C_INCLUDES := $(interception_c_includes)
LOCAL_CPPFLAGS := $(interception_cppflags)
LOCAL_SRC_FILES := $(interception_src_files)
LOCAL_CXX_STL := none
LOCAL_SANITIZE := never
LOCAL_MULTILIB := both
include $(BUILD_HOST_STATIC_LIBRARY)
