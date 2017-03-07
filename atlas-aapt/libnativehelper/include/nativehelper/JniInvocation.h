/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef JNI_INVOCATION_H_included
#define JNI_INVOCATION_H_included

#include <jni.h>

// JniInvocation adds a layer of indirection for applications using
// the JNI invocation API to allow the JNI implementation to be
// selected dynamically. Apps can specify a specific implementation to
// be used by calling InitJniInvocation. If this is not done, the
// library will chosen based on the value of Android system property
// persist.sys.dalvik.vm.lib on the device, and otherwise fall back to
// a hard-coded default implementation.
class JniInvocation {
 public:
  JniInvocation();

  ~JniInvocation();

  // Initialize JNI invocation API. library should specifiy a valid
  // shared library for opening via dlopen providing a JNI invocation
  // implementation, or null to allow defaulting via
  // persist.sys.dalvik.vm.lib.
  bool Init(const char* library);

  // Exposes which library is actually loaded from the given name. The
  // buffer of size PROPERTY_VALUE_MAX will be used to load the system
  // property for the default library, if necessary. If no buffer is
  // provided, the fallback value will be used.
  static const char* GetLibrary(const char* library, char* buffer);

 private:

  bool FindSymbol(void** pointer, const char* symbol);

  static JniInvocation& GetJniInvocation();

  jint JNI_GetDefaultJavaVMInitArgs(void* vmargs);
  jint JNI_CreateJavaVM(JavaVM** p_vm, JNIEnv** p_env, void* vm_args);
  jint JNI_GetCreatedJavaVMs(JavaVM** vms, jsize size, jsize* vm_count);

  static JniInvocation* jni_invocation_;

  void* handle_;
  jint (*JNI_GetDefaultJavaVMInitArgs_)(void*);
  jint (*JNI_CreateJavaVM_)(JavaVM**, JNIEnv**, void*);
  jint (*JNI_GetCreatedJavaVMs_)(JavaVM**, jsize, jsize*);

  friend jint JNI_GetDefaultJavaVMInitArgs(void* vm_args);
  friend jint JNI_CreateJavaVM(JavaVM** p_vm, JNIEnv** p_env, void* vm_args);
  friend jint JNI_GetCreatedJavaVMs(JavaVM** vms, jsize size, jsize* vm_count);
};

#endif  // JNI_INVOCATION_H_included
