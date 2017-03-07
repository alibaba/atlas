/*
 * Copyright (C) 2005 The Android Open Source Project
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

#define LOG_TAG "misc"

//
// Miscellaneous utility functions.
//
#include <utils/misc.h>
#include <utils/Log.h>

#include <sys/stat.h>
#include <string.h>
#include <stdio.h>

#if !defined(_WIN32)
# include <pthread.h>
#endif

#include <utils/Vector.h>

using namespace android;

namespace android {

struct sysprop_change_callback_info {
    sysprop_change_callback callback;
    int priority;
};

#if !defined(_WIN32)
static pthread_mutex_t gSyspropMutex = PTHREAD_MUTEX_INITIALIZER;
static Vector<sysprop_change_callback_info>* gSyspropList = NULL;
#endif

void add_sysprop_change_callback(sysprop_change_callback cb, int priority) {
#if !defined(_WIN32)
    pthread_mutex_lock(&gSyspropMutex);
    if (gSyspropList == NULL) {
        gSyspropList = new Vector<sysprop_change_callback_info>();
    }
    sysprop_change_callback_info info;
    info.callback = cb;
    info.priority = priority;
    bool added = false;
    for (size_t i=0; i<gSyspropList->size(); i++) {
        if (priority >= gSyspropList->itemAt(i).priority) {
            gSyspropList->insertAt(info, i);
            added = true;
            break;
        }
    }
    if (!added) {
        gSyspropList->add(info);
    }
    pthread_mutex_unlock(&gSyspropMutex);
#endif
}

void report_sysprop_change() {
#if !defined(_WIN32)
    pthread_mutex_lock(&gSyspropMutex);
    Vector<sysprop_change_callback_info> listeners;
    if (gSyspropList != NULL) {
        listeners = *gSyspropList;
    }
    pthread_mutex_unlock(&gSyspropMutex);

    //ALOGI("Reporting sysprop change to %d listeners", listeners.size());
    for (size_t i=0; i<listeners.size(); i++) {
        listeners[i].callback();
    }
#endif
}

}; // namespace android
