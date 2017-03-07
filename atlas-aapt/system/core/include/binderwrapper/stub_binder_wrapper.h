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

#ifndef SYSTEM_CORE_INCLUDE_BINDERWRAPPER_STUB_BINDER_WRAPPER_H_
#define SYSTEM_CORE_INCLUDE_BINDERWRAPPER_STUB_BINDER_WRAPPER_H_

#include <map>
#include <string>
#include <vector>

#include <base/macros.h>
#include <binder/Binder.h>
#include <binder/IBinder.h>
#include <binderwrapper/binder_wrapper.h>

namespace android {

// Stub implementation of BinderWrapper for testing.
//
// Example usage:
//
// First, assuming a base IFoo binder interface, create a stub class that
// derives from BnFoo to implement the receiver side of the communication:
//
//   class StubFoo : public BnFoo {
//    public:
//     ...
//     status_t doSomething(int arg) override {
//       // e.g. save passed-in value for later inspection by tests.
//       return OK;
//     }
//   };
//
// Next, from your test code, inject a StubBinderManager either directly or by
// inheriting from the BinderTestBase class:
//
//   StubBinderWrapper* wrapper = new StubBinderWrapper();
//   BinderWrapper::InitForTesting(wrapper);  // Takes ownership.
//
// Also from your test, create a StubFoo and register it with the wrapper:
//
//   StubFoo* foo = new StubFoo();
//   sp<IBinder> binder(foo);
//   wrapper->SetBinderForService("foo", binder);
//
// The code being tested can now use the wrapper to get the stub and call it:
//
//   sp<IBinder> binder = BinderWrapper::Get()->GetService("foo");
//   CHECK(binder.get());
//   sp<IFoo> foo = interface_cast<IFoo>(binder);
//   CHECK_EQ(foo->doSomething(3), OK);
//
// To create a local BBinder object, production code can call
// CreateLocalBinder(). Then, a test can get the BBinder's address via
// local_binders() to check that they're passed as expected in binder calls.
//
class StubBinderWrapper : public BinderWrapper {
 public:
  StubBinderWrapper();
  ~StubBinderWrapper() override;

  const std::vector<sp<BBinder>>& local_binders() const {
    return local_binders_;
  }
  void clear_local_binders() { local_binders_.clear(); }

  void set_calling_uid(uid_t uid) { calling_uid_ = uid; }
  void set_calling_pid(pid_t pid) { calling_pid_ = pid; }

  // Sets the binder to return when |service_name| is passed to GetService() or
  // WaitForService().
  void SetBinderForService(const std::string& service_name,
                           const sp<IBinder>& binder);

  // Returns the binder previously registered for |service_name| via
  // RegisterService(), or null if the service hasn't been registered.
  sp<IBinder> GetRegisteredService(const std::string& service_name) const;

  // Run the calback in |death_callbacks_| corresponding to |binder|.
  void NotifyAboutBinderDeath(const sp<IBinder>& binder);

  // BinderWrapper:
  sp<IBinder> GetService(const std::string& service_name) override;
  bool RegisterService(const std::string& service_name,
                       const sp<IBinder>& binder) override;
  sp<BBinder> CreateLocalBinder() override;
  bool RegisterForDeathNotifications(const sp<IBinder>& binder,
                                     const base::Closure& callback) override;
  bool UnregisterForDeathNotifications(const sp<IBinder>& binder) override;
  uid_t GetCallingUid() override;
  pid_t GetCallingPid() override;

 private:
  using ServiceMap = std::map<std::string, sp<IBinder>>;

  // Map from service name to associated binder handle. Used by GetService() and
  // WaitForService().
  ServiceMap services_to_return_;

  // Map from service name to associated binder handle. Updated by
  // RegisterService().
  ServiceMap registered_services_;

  // Local binders returned by CreateLocalBinder().
  std::vector<sp<BBinder>> local_binders_;

  // Map from binder handle to the callback that should be invoked on binder
  // death.
  std::map<sp<IBinder>, base::Closure> death_callbacks_;

  // Values to return from GetCallingUid() and GetCallingPid();
  uid_t calling_uid_;
  pid_t calling_pid_;

  DISALLOW_COPY_AND_ASSIGN(StubBinderWrapper);
};

}  // namespace android

#endif  // SYSTEM_CORE_INCLUDE_BINDERWRAPPER_STUB_BINDER_WRAPPER_H_
