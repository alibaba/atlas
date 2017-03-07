# Build the unit tests.
LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

# Target unit test.

include $(CLEAR_VARS)
LOCAL_MODULE := JniInvocation_test
LOCAL_CLANG := true
LOCAL_SRC_FILES := JniInvocation_test.cpp
LOCAL_SHARED_LIBRARIES := libnativehelper
include $(BUILD_NATIVE_TEST)

# Host unit test.

include $(CLEAR_VARS)
LOCAL_MODULE := JniInvocation_test
LOCAL_CLANG := true
LOCAL_SRC_FILES := JniInvocation_test.cpp
LOCAL_SHARED_LIBRARIES := libnativehelper
include $(BUILD_HOST_NATIVE_TEST)
