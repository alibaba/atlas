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

#include <errno.h>
#include <fcntl.h>
#include <limits.h>
#include <pthread.h>
#include <stdatomic.h>
#include <stdbool.h>
#include <stdlib.h>
#include <string.h>
#include <sys/types.h>
#include <cutils/compiler.h>
#include <cutils/properties.h>
#include <cutils/trace.h>

#define LOG_TAG "cutils-trace"
#include <log/log.h>

/**
 * Maximum size of a message that can be logged to the trace buffer.
 * Note this message includes a tag, the pid, and the string given as the name.
 * Names should be kept short to get the most use of the trace buffer.
 */
#define ATRACE_MESSAGE_LENGTH 1024

atomic_bool             atrace_is_ready      = ATOMIC_VAR_INIT(false);
int                     atrace_marker_fd     = -1;
uint64_t                atrace_enabled_tags  = ATRACE_TAG_NOT_READY;
static bool             atrace_is_debuggable = false;
static atomic_bool      atrace_is_enabled    = ATOMIC_VAR_INIT(true);
static pthread_once_t   atrace_once_control  = PTHREAD_ONCE_INIT;
static pthread_mutex_t  atrace_tags_mutex    = PTHREAD_MUTEX_INITIALIZER;

// Set whether this process is debuggable, which determines whether
// application-level tracing is allowed when the ro.debuggable system property
// is not set to '1'.
void atrace_set_debuggable(bool debuggable)
{
    atrace_is_debuggable = debuggable;
    atrace_update_tags();
}

// Set whether tracing is enabled in this process.  This is used to prevent
// the Zygote process from tracing.
void atrace_set_tracing_enabled(bool enabled)
{
    atomic_store_explicit(&atrace_is_enabled, enabled, memory_order_release);
    atrace_update_tags();
}

// Check whether the given command line matches one of the comma-separated
// values listed in the app_cmdlines property.
static bool atrace_is_cmdline_match(const char* cmdline)
{
    int count = property_get_int32("debug.atrace.app_number", 0);

    char buf[PROPERTY_KEY_MAX];
    char value[PROPERTY_VALUE_MAX];

    for (int i = 0; i < count; i++) {
        snprintf(buf, sizeof(buf), "debug.atrace.app_%d", i);
        property_get(buf, value, "");
        if (strcmp(value, cmdline) == 0) {
            return true;
        }
    }

    return false;
}

// Determine whether application-level tracing is enabled for this process.
static bool atrace_is_app_tracing_enabled()
{
    bool sys_debuggable = false;
    char value[PROPERTY_VALUE_MAX];
    bool result = false;

    // Check whether the system is debuggable.
    property_get("ro.debuggable", value, "0");
    if (value[0] == '1') {
        sys_debuggable = true;
    }

    if (sys_debuggable || atrace_is_debuggable) {
        // Check whether tracing is enabled for this process.
        FILE * file = fopen("/proc/self/cmdline", "re");
        if (file) {
            char cmdline[4096];
            if (fgets(cmdline, sizeof(cmdline), file)) {
                result = atrace_is_cmdline_match(cmdline);
            } else {
                ALOGE("Error reading cmdline: %s (%d)", strerror(errno), errno);
            }
            fclose(file);
        } else {
            ALOGE("Error opening /proc/self/cmdline: %s (%d)", strerror(errno),
                    errno);
        }
    }

    return result;
}

// Read the sysprop and return the value tags should be set to
static uint64_t atrace_get_property()
{
    char value[PROPERTY_VALUE_MAX];
    char *endptr;
    uint64_t tags;

    property_get("debug.atrace.tags.enableflags", value, "0");
    errno = 0;
    tags = strtoull(value, &endptr, 0);
    if (value[0] == '\0' || *endptr != '\0') {
        ALOGE("Error parsing trace property: Not a number: %s", value);
        return 0;
    } else if (errno == ERANGE || tags == ULLONG_MAX) {
        ALOGE("Error parsing trace property: Number too large: %s", value);
        return 0;
    }

    // Only set the "app" tag if this process was selected for app-level debug
    // tracing.
    if (atrace_is_app_tracing_enabled()) {
        tags |= ATRACE_TAG_APP;
    } else {
        tags &= ~ATRACE_TAG_APP;
    }

    return (tags | ATRACE_TAG_ALWAYS) & ATRACE_TAG_VALID_MASK;
}

// Update tags if tracing is ready. Useful as a sysprop change callback.
void atrace_update_tags()
{
    uint64_t tags;
    if (CC_UNLIKELY(atomic_load_explicit(&atrace_is_ready, memory_order_acquire))) {
        if (atomic_load_explicit(&atrace_is_enabled, memory_order_acquire)) {
            tags = atrace_get_property();
            pthread_mutex_lock(&atrace_tags_mutex);
            atrace_enabled_tags = tags;
            pthread_mutex_unlock(&atrace_tags_mutex);
        } else {
            // Tracing is disabled for this process, so we simply don't
            // initialize the tags.
            pthread_mutex_lock(&atrace_tags_mutex);
            atrace_enabled_tags = ATRACE_TAG_NOT_READY;
            pthread_mutex_unlock(&atrace_tags_mutex);
        }
    }
}

static void atrace_init_once()
{
    atrace_marker_fd = open("/sys/kernel/debug/tracing/trace_marker", O_WRONLY | O_CLOEXEC);
    if (atrace_marker_fd == -1) {
        ALOGE("Error opening trace file: %s (%d)", strerror(errno), errno);
        atrace_enabled_tags = 0;
        goto done;
    }

    atrace_enabled_tags = atrace_get_property();

done:
    atomic_store_explicit(&atrace_is_ready, true, memory_order_release);
}

void atrace_setup()
{
    pthread_once(&atrace_once_control, atrace_init_once);
}

void atrace_begin_body(const char* name)
{
    char buf[ATRACE_MESSAGE_LENGTH];

    int len = snprintf(buf, sizeof(buf), "B|%d|%s", getpid(), name);
    if (len >= (int) sizeof(buf)) {
        ALOGW("Truncated name in %s: %s\n", __FUNCTION__, name);
        len = sizeof(buf) - 1;
    }
    write(atrace_marker_fd, buf, len);
}

#define WRITE_MSG(format_begin, format_end, pid, name, value) { \
    char buf[ATRACE_MESSAGE_LENGTH]; \
    int len = snprintf(buf, sizeof(buf), format_begin "%s" format_end, pid, \
        name, value); \
    if (len >= (int) sizeof(buf)) { \
        /* Given the sizeof(buf), and all of the current format buffers, \
         * it is impossible for name_len to be < 0 if len >= sizeof(buf). */ \
        int name_len = strlen(name) - (len - sizeof(buf)) - 1; \
        /* Truncate the name to make the message fit. */ \
        ALOGW("Truncated name in %s: %s\n", __FUNCTION__, name); \
        len = snprintf(buf, sizeof(buf), format_begin "%.*s" format_end, pid, \
            name_len, name, value); \
    } \
    write(atrace_marker_fd, buf, len); \
}

void atrace_async_begin_body(const char* name, int32_t cookie)
{
    WRITE_MSG("S|%d|", "|%" PRId32, getpid(), name, cookie);
}

void atrace_async_end_body(const char* name, int32_t cookie)
{
    WRITE_MSG("F|%d|", "|%" PRId32, getpid(), name, cookie);
}

void atrace_int_body(const char* name, int32_t value)
{
    WRITE_MSG("C|%d|", "|%" PRId32, getpid(), name, value);
}

void atrace_int64_body(const char* name, int64_t value)
{
    WRITE_MSG("C|%d|", "|%" PRId64, getpid(), name, value);
}
