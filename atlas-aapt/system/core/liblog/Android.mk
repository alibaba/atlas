#
# Copyright (C) 2008-2014 The Android Open Source Project
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
LOCAL_PATH := $(my-dir)
include $(CLEAR_VARS)

# This is what we want to do:
#  liblog_cflags := $(shell \
#   sed -n \
#       's/^\([0-9]*\)[ \t]*liblog[ \t].*/-DLIBLOG_LOG_TAG=\1/p' \
#       $(LOCAL_PATH)/event.logtags)
# so make sure we do not regret hard-coding it as follows:
liblog_cflags := -DLIBLOG_LOG_TAG=1005
liblog_cflags += -DSNET_EVENT_LOG_TAG=1397638484

liblog_sources := log_event_list.c log_event_write.c logger_write.c
liblog_sources += config_write.c logger_name.c logger_lock.c
liblog_host_sources := $(liblog_sources) fake_log_device.c event.logtags
liblog_host_sources += fake_writer.c
liblog_target_sources := $(liblog_sources) event_tag_map.c
liblog_target_sources += config_read.c log_time.cpp log_is_loggable.c logprint.c
liblog_target_sources += pmsg_reader.c pmsg_writer.c
liblog_target_sources += logd_reader.c logd_writer.c logger_read.c

# Shared and static library for host
# ========================================================
LOCAL_MODULE := liblog
LOCAL_SRC_FILES := $(liblog_host_sources)
# some files must not be compiled when building against Mingw
# they correspond to features not used by our host development tools
# which are also hard or even impossible to port to native Win32
LOCAL_SRC_FILES_darwin := event_tag_map.c
LOCAL_SRC_FILES_linux := event_tag_map.c
LOCAL_SRC_FILES_windows := uio.c
LOCAL_CFLAGS := -DFAKE_LOG_DEVICE=1 -Werror -fvisibility=hidden $(liblog_cflags)
LOCAL_MULTILIB := both
LOCAL_MODULE_HOST_OS := darwin linux windows
include $(BUILD_HOST_STATIC_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := liblog
LOCAL_WHOLE_STATIC_LIBRARIES := liblog
LOCAL_LDLIBS_linux := -lrt
LOCAL_MULTILIB := both
LOCAL_CXX_STL := none
LOCAL_MODULE_HOST_OS := darwin linux windows
include $(BUILD_HOST_SHARED_LIBRARY)


# Shared and static library for target
# ========================================================
include $(CLEAR_VARS)
LOCAL_MODULE := liblog
LOCAL_SRC_FILES := $(liblog_target_sources)
LOCAL_CFLAGS := -Werror -fvisibility=hidden $(liblog_cflags)
# AddressSanitizer runtime library depends on liblog.
LOCAL_SANITIZE := never
include $(BUILD_STATIC_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := liblog
LOCAL_WHOLE_STATIC_LIBRARIES := liblog
LOCAL_CFLAGS := -Werror -fvisibility=hidden $(liblog_cflags)

# TODO: This is to work around b/24465209. Remove after root cause is fixed
LOCAL_LDFLAGS_arm := -Wl,--hash-style=both

LOCAL_SANITIZE := never
LOCAL_CXX_STL := none

include $(BUILD_SHARED_LIBRARY)

include $(call first-makefiles-under,$(LOCAL_PATH))
