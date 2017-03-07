/*
 * Copyright (C) 2015 The Android Open Source Project
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

#ifndef SYSTEM_CORE_INCLUDE_BINDERWRAPPER_BINDER_WRAPPER_H_
#define SYSTEM_CORE_INCLUDE_BINDERWRAPPER_BINDER_WRAPPER_H_

#include <sys/types.h>

#include <string>

#include <base/callback.h>
#include <utils/StrongPointer.h>

namespace android {

class BBinder;
class IBinder;

// Wraps libbinder to make it testable.
// NOTE: Static methods of this class are not thread-safe.
class BinderWrapper {
 public:
  virtual ~BinderWrapper() {}

  // Creates and initializes the singleton (using a wrapper that communicates
  // with the real binder system).
  static void Create();

  // Initializes |wrapper| as the singleton, taking ownership of it. Tests that
  // want to inject their own wrappers should call this instead of Create().
  static void InitForTesting(BinderWrapper* wrapper);

  // Destroys the singleton. Must be called before calling Create() or
  // InitForTesting() a second time.
  static void Destroy();

  // Returns the singleton instance previously created by Create() or set by
  // InitForTesting().
  static BinderWrapper* Get();

  // Returns the singleton instance if it was previously created by Create() or
  // set by InitForTesting(), or creates a new one by calling Create().
  static BinderWrapper* GetOrCreateInstance();

  // Gets the binder for communicating with the service identified by
  // |service_name|, returning null immediately if it doesn't exist.
  virtual sp<IBinder> GetService(const std::string& service_name) = 0;

  // Registers |binder| as |service_name| with the service manager.
  virtual bool RegisterService(const std::string& service_name,
                               const sp<IBinder>& binder) = 0;

  // Creates a local binder object.
  virtual sp<BBinder> CreateLocalBinder() = 0;

  // Registers |callback| to be invoked when |binder| dies. If another callback
  // is currently registered for |binder|, it will be replaced.
  virtual bool RegisterForDeathNotifications(
      const sp<IBinder>& binder,
      const base::Closure& callback) = 0;

  // Unregisters the callback, if any, for |binder|.
  virtual bool UnregisterForDeathNotifications(const sp<IBinder>& binder) = 0;

  // When called while in a transaction, returns the caller's UID or PID.
  virtual uid_t GetCallingUid() = 0;
  virtual pid_t GetCallingPid() = 0;

 private:
  static BinderWrapper* instance_;
};

}  // namespace android

#endif  // SYSTEM_CORE_INCLUDE_BINDERWRAPPER_BINDER_WRAPPER_H_
