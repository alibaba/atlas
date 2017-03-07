/*
 * Copyright (C) 2012 The Android Open Source Project
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

#include <cutils/multiuser.h>

userid_t multiuser_get_user_id(uid_t uid) {
    return uid / MULTIUSER_APP_PER_USER_RANGE;
}

appid_t multiuser_get_app_id(uid_t uid) {
    return uid % MULTIUSER_APP_PER_USER_RANGE;
}

uid_t multiuser_get_uid(userid_t userId, appid_t appId) {
    return userId * MULTIUSER_APP_PER_USER_RANGE + (appId % MULTIUSER_APP_PER_USER_RANGE);
}

appid_t multiuser_get_shared_app_gid(uid_t id) {
  return MULTIUSER_FIRST_SHARED_APPLICATION_GID + (id % MULTIUSER_APP_PER_USER_RANGE)
          - MULTIUSER_FIRST_APPLICATION_UID;

}
