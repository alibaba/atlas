/*
 * Copyright (C) 2007-2016 The Android Open Source Project
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

/*
 * Some OS specific dribs and drabs (locking etc).
 */

#if !defined(_WIN32)
#include <pthread.h>
#endif

#include <private/android_filesystem_config.h>

#include "logger.h"

LIBLOG_HIDDEN uid_t __android_log_uid()
{
#if defined(_WIN32)
    return AID_SYSTEM;
#else
    static uid_t last_uid = AID_ROOT; /* logd *always* starts up as AID_ROOT */

    if (last_uid == AID_ROOT) { /* have we called to get the UID yet? */
        last_uid = getuid();
    }
    return last_uid;
#endif
}

LIBLOG_HIDDEN pid_t __android_log_pid()
{
    static pid_t last_pid = (pid_t) -1;

    if (last_pid == (pid_t) -1) {
        last_pid = getpid();
    }
    return last_pid;
}

#if !defined(_WIN32)
static pthread_mutex_t log_init_lock = PTHREAD_MUTEX_INITIALIZER;
#endif

LIBLOG_HIDDEN void __android_log_lock()
{
#if !defined(_WIN32)
    /*
     * If we trigger a signal handler in the middle of locked activity and the
     * signal handler logs a message, we could get into a deadlock state.
     */
    pthread_mutex_lock(&log_init_lock);
#endif
}

LIBLOG_HIDDEN int __android_log_trylock()
{
#if !defined(_WIN32)
    return pthread_mutex_trylock(&log_init_lock);
#else
    return 0;
#endif
}

LIBLOG_HIDDEN void __android_log_unlock()
{
#if !defined(_WIN32)
    pthread_mutex_unlock(&log_init_lock);
#endif
}
