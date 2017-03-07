/*
 * Copyright (C) 2006 The Android Open Source Project
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

#define LOG_TAG "properties"
// #define LOG_NDEBUG 0

#include <stdlib.h>
#include <string.h>
#include <ctype.h>
#include <unistd.h>
#include <cutils/sockets.h>
#include <errno.h>
#include <assert.h>

#include <cutils/properties.h>
#include <stdbool.h>
#include <inttypes.h>
#include <log/log.h>

int8_t property_get_bool(const char *key, int8_t default_value) {
    if (!key) {
        return default_value;
    }

    int8_t result = default_value;
    char buf[PROPERTY_VALUE_MAX] = {'\0',};

    int len = property_get(key, buf, "");
    if (len == 1) {
        char ch = buf[0];
        if (ch == '0' || ch == 'n') {
            result = false;
        } else if (ch == '1' || ch == 'y') {
            result = true;
        }
    } else if (len > 1) {
         if (!strcmp(buf, "no") || !strcmp(buf, "false") || !strcmp(buf, "off")) {
            result = false;
        } else if (!strcmp(buf, "yes") || !strcmp(buf, "true") || !strcmp(buf, "on")) {
            result = true;
        }
    }

    return result;
}

// Convert string property to int (default if fails); return default value if out of bounds
static intmax_t property_get_imax(const char *key, intmax_t lower_bound, intmax_t upper_bound,
        intmax_t default_value) {
    if (!key) {
        return default_value;
    }

    intmax_t result = default_value;
    char buf[PROPERTY_VALUE_MAX] = {'\0',};
    char *end = NULL;

    int len = property_get(key, buf, "");
    if (len > 0) {
        int tmp = errno;
        errno = 0;

        // Infer base automatically
        result = strtoimax(buf, &end, /*base*/0);
        if ((result == INTMAX_MIN || result == INTMAX_MAX) && errno == ERANGE) {
            // Over or underflow
            result = default_value;
            ALOGV("%s(%s,%" PRIdMAX ") - overflow", __FUNCTION__, key, default_value);
        } else if (result < lower_bound || result > upper_bound) {
            // Out of range of requested bounds
            result = default_value;
            ALOGV("%s(%s,%" PRIdMAX ") - out of range", __FUNCTION__, key, default_value);
        } else if (end == buf) {
            // Numeric conversion failed
            result = default_value;
            ALOGV("%s(%s,%" PRIdMAX ") - numeric conversion failed",
                    __FUNCTION__, key, default_value);
        }

        errno = tmp;
    }

    return result;
}

int64_t property_get_int64(const char *key, int64_t default_value) {
    return (int64_t)property_get_imax(key, INT64_MIN, INT64_MAX, default_value);
}

int32_t property_get_int32(const char *key, int32_t default_value) {
    return (int32_t)property_get_imax(key, INT32_MIN, INT32_MAX, default_value);
}

#define _REALLY_INCLUDE_SYS__SYSTEM_PROPERTIES_H_
#include <sys/_system_properties.h>

int property_set(const char *key, const char *value)
{
    return __system_property_set(key, value);
}

int property_get(const char *key, char *value, const char *default_value)
{
    int len;

    len = __system_property_get(key, value);
    if(len > 0) {
        return len;
    }
    if(default_value) {
        len = strlen(default_value);
        if (len >= PROPERTY_VALUE_MAX) {
            len = PROPERTY_VALUE_MAX - 1;
        }
        memcpy(value, default_value, len);
        value[len] = '\0';
    }
    return len;
}

struct property_list_callback_data
{
    void (*propfn)(const char *key, const char *value, void *cookie);
    void *cookie;
};

static void property_list_callback(const prop_info *pi, void *cookie)
{
    char name[PROP_NAME_MAX];
    char value[PROP_VALUE_MAX];
    struct property_list_callback_data *data = cookie;

    __system_property_read(pi, name, value);
    data->propfn(name, value, data->cookie);
}

int property_list(
        void (*propfn)(const char *key, const char *value, void *cookie),
        void *cookie)
{
    struct property_list_callback_data data = { propfn, cookie };
    return __system_property_foreach(property_list_callback, &data);
}
