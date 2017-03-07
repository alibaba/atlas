/*
 * Copyright (C) 2016 The Android Open Source Project
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
#include <inttypes.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include <log/log.h>
#include <log/logger.h>

#include "log_portability.h"

#define MAX_EVENT_PAYLOAD (LOGGER_ENTRY_MAX_PAYLOAD - sizeof(int32_t))

typedef struct {
    uint32_t tag;
    unsigned pos; /* Read/write position into buffer */
    unsigned count[ANDROID_MAX_LIST_NEST_DEPTH + 1]; /* Number of elements   */
    unsigned list[ANDROID_MAX_LIST_NEST_DEPTH + 1];  /* pos for list counter */
    unsigned list_nest_depth;
    unsigned len; /* Length or raw buffer. */
    bool overflow;
    bool list_stop; /* next call decrement list_nest_depth and issue a stop */
    enum {
        kAndroidLoggerRead = 1,
        kAndroidLoggerWrite = 2,
    } read_write_flag;
    uint8_t storage[LOGGER_ENTRY_MAX_PAYLOAD];
} android_log_context_internal;

LIBLOG_ABI_PUBLIC android_log_context create_android_logger(uint32_t tag) {
    size_t needed, i;
    android_log_context_internal *context;

    context = calloc(1, sizeof(android_log_context_internal));
    if (!context) {
        return NULL;
    }
    context->tag = tag;
    context->read_write_flag = kAndroidLoggerWrite;
    needed = sizeof(uint8_t) + sizeof(uint8_t);
    if ((context->pos + needed) > MAX_EVENT_PAYLOAD) {
        context->overflow = true;
    }
    /* Everything is a list */
    context->storage[context->pos + 0] = EVENT_TYPE_LIST;
    context->list[0] = context->pos + 1;
    context->pos += needed;

    return (android_log_context)context;
}

LIBLOG_ABI_PUBLIC android_log_context create_android_log_parser(
        const char *msg,
        size_t len) {
    android_log_context_internal *context;
    size_t i;

    context = calloc(1, sizeof(android_log_context_internal));
    if (!context) {
        return NULL;
    }
    len = (len <= MAX_EVENT_PAYLOAD) ? len : MAX_EVENT_PAYLOAD;
    context->len = len;
    memcpy(context->storage, msg, len);
    context->read_write_flag = kAndroidLoggerRead;

    return (android_log_context)context;
}

LIBLOG_ABI_PUBLIC int android_log_destroy(android_log_context *ctx) {
    android_log_context_internal *context;

    context = (android_log_context_internal *)*ctx;
    if (!context) {
        return -EBADF;
    }
    memset(context, 0, sizeof(*context));
    free(context);
    *ctx = NULL;
    return 0;
}

LIBLOG_ABI_PUBLIC int android_log_write_list_begin(android_log_context ctx) {
    size_t needed;
    android_log_context_internal *context;

    context = (android_log_context_internal *)ctx;
    if (!context ||
            (kAndroidLoggerWrite != context->read_write_flag)) {
        return -EBADF;
    }
    if (context->list_nest_depth > ANDROID_MAX_LIST_NEST_DEPTH) {
        context->overflow = true;
        return -EOVERFLOW;
    }
    needed = sizeof(uint8_t) + sizeof(uint8_t);
    if ((context->pos + needed) > MAX_EVENT_PAYLOAD) {
        context->overflow = true;
        return -EIO;
    }
    context->count[context->list_nest_depth]++;
    context->list_nest_depth++;
    if (context->list_nest_depth > ANDROID_MAX_LIST_NEST_DEPTH) {
        context->overflow = true;
        return -EOVERFLOW;
    }
    if (context->overflow) {
        return -EIO;
    }
    context->storage[context->pos + 0] = EVENT_TYPE_LIST;
    context->storage[context->pos + 1] = 0;
    context->list[context->list_nest_depth] = context->pos + 1;
    context->count[context->list_nest_depth] = 0;
    context->pos += needed;
    return 0;
}

static inline void copy4LE(uint8_t *buf, uint32_t val)
{
    buf[0] = val & 0xFF;
    buf[1] = (val >> 8) & 0xFF;
    buf[2] = (val >> 16) & 0xFF;
    buf[3] = (val >> 24) & 0xFF;
}

LIBLOG_ABI_PUBLIC int android_log_write_int32(android_log_context ctx,
                                              int32_t value) {
    size_t needed;
    android_log_context_internal *context;

    context = (android_log_context_internal *)ctx;
    if (!context || (kAndroidLoggerWrite != context->read_write_flag)) {
        return -EBADF;
    }
    if (context->overflow) {
        return -EIO;
    }
    needed = sizeof(uint8_t) + sizeof(value);
    if ((context->pos + needed) > MAX_EVENT_PAYLOAD) {
        context->overflow = true;
        return -EIO;
    }
    context->count[context->list_nest_depth]++;
    context->storage[context->pos + 0] = EVENT_TYPE_INT;
    copy4LE(&context->storage[context->pos + 1], value);
    context->pos += needed;
    return 0;
}

static inline void copy8LE(uint8_t *buf, uint64_t val)
{
    buf[0] = val & 0xFF;
    buf[1] = (val >> 8) & 0xFF;
    buf[2] = (val >> 16) & 0xFF;
    buf[3] = (val >> 24) & 0xFF;
    buf[4] = (val >> 32) & 0xFF;
    buf[5] = (val >> 40) & 0xFF;
    buf[6] = (val >> 48) & 0xFF;
    buf[7] = (val >> 56) & 0xFF;
}

LIBLOG_ABI_PUBLIC int android_log_write_int64(android_log_context ctx,
                                              int64_t value) {
    size_t needed;
    android_log_context_internal *context;

    context = (android_log_context_internal *)ctx;
    if (!context || (kAndroidLoggerWrite != context->read_write_flag)) {
        return -EBADF;
    }
    if (context->overflow) {
        return -EIO;
    }
    needed = sizeof(uint8_t) + sizeof(value);
    if ((context->pos + needed) > MAX_EVENT_PAYLOAD) {
        context->overflow = true;
        return -EIO;
    }
    context->count[context->list_nest_depth]++;
    context->storage[context->pos + 0] = EVENT_TYPE_LONG;
    copy8LE(&context->storage[context->pos + 1], value);
    context->pos += needed;
    return 0;
}

LIBLOG_ABI_PUBLIC int android_log_write_string8_len(android_log_context ctx,
                                                    const char *value,
                                                    size_t maxlen) {
    size_t needed;
    ssize_t len;
    android_log_context_internal *context;

    context = (android_log_context_internal *)ctx;
    if (!context || (kAndroidLoggerWrite != context->read_write_flag)) {
        return -EBADF;
    }
    if (context->overflow) {
        return -EIO;
    }
    if (!value) {
        value = "";
    }
    len = strnlen(value, maxlen);
    needed = sizeof(uint8_t) + sizeof(int32_t) + len;
    if ((context->pos + needed) > MAX_EVENT_PAYLOAD) {
        /* Truncate string for delivery */
        len = MAX_EVENT_PAYLOAD - context->pos - 1 - sizeof(int32_t);
        if (len <= 0) {
            context->overflow = true;
            return -EIO;
        }
    }
    context->count[context->list_nest_depth]++;
    context->storage[context->pos + 0] = EVENT_TYPE_STRING;
    copy4LE(&context->storage[context->pos + 1], len);
    if (len) {
        memcpy(&context->storage[context->pos + 5], value, len);
    }
    context->pos += needed;
    return len;
}

LIBLOG_ABI_PUBLIC int android_log_write_string8(android_log_context ctx,
                                                const char *value) {
    return android_log_write_string8_len(ctx, value, MAX_EVENT_PAYLOAD);
}

LIBLOG_ABI_PUBLIC int android_log_write_float32(android_log_context ctx,
                                                float value) {
    size_t needed;
    uint32_t ivalue;
    android_log_context_internal *context;

    context = (android_log_context_internal *)ctx;
    if (!context || (kAndroidLoggerWrite != context->read_write_flag)) {
        return -EBADF;
    }
    if (context->overflow) {
        return -EIO;
    }
    needed = sizeof(uint8_t) + sizeof(ivalue);
    if ((context->pos + needed) > MAX_EVENT_PAYLOAD) {
        context->overflow = true;
        return -EIO;
    }
    ivalue = *(uint32_t *)&value;
    context->count[context->list_nest_depth]++;
    context->storage[context->pos + 0] = EVENT_TYPE_FLOAT;
    copy4LE(&context->storage[context->pos + 1], ivalue);
    context->pos += needed;
    return 0;
}

LIBLOG_ABI_PUBLIC int android_log_write_list_end(android_log_context ctx) {
    android_log_context_internal *context;

    context = (android_log_context_internal *)ctx;
    if (!context || (kAndroidLoggerWrite != context->read_write_flag)) {
        return -EBADF;
    }
    if (context->list_nest_depth > ANDROID_MAX_LIST_NEST_DEPTH) {
        context->overflow = true;
        context->list_nest_depth--;
        return -EOVERFLOW;
    }
    if (!context->list_nest_depth) {
        context->overflow = true;
        return -EOVERFLOW;
    }
    if (context->list[context->list_nest_depth] <= 0) {
        context->list_nest_depth--;
        context->overflow = true;
        return -EOVERFLOW;
    }
    context->storage[context->list[context->list_nest_depth]] =
        context->count[context->list_nest_depth];
    context->list_nest_depth--;
    return 0;
}

/*
 * Logs the list of elements to the event log.
 */
LIBLOG_ABI_PUBLIC int android_log_write_list(android_log_context ctx,
                                             log_id_t id) {
    android_log_context_internal *context;
    const char *msg;
    ssize_t len;

    if ((id != LOG_ID_EVENTS) && (id != LOG_ID_SECURITY)) {
        return -EINVAL;
    }

    context = (android_log_context_internal *)ctx;
    if (!context || (kAndroidLoggerWrite != context->read_write_flag)) {
        return -EBADF;
    }
    if (context->list_nest_depth) {
        return -EIO;
    }
    /* NB: if there was overflow, then log is truncated. Nothing reported */
    context->storage[1] = context->count[0];
    len = context->len = context->pos;
    msg = (const char *)context->storage;
    /* it'snot a list */
    if (context->count[0] <= 1) {
        len -= sizeof(uint8_t) + sizeof(uint8_t);
        if (len < 0) {
            len = 0;
        }
        msg += sizeof(uint8_t) + sizeof(uint8_t);
    }
    return (id == LOG_ID_EVENTS) ?
        __android_log_bwrite(context->tag, msg, len) :
        __android_log_security_bwrite(context->tag, msg, len);
}

/*
 * Extract a 4-byte value from a byte stream.
 */
static inline uint32_t get4LE(const uint8_t* src)
{
    return src[0] | (src[1] << 8) | (src[2] << 16) | (src[3] << 24);
}

/*
 * Extract an 8-byte value from a byte stream.
 */
static inline uint64_t get8LE(const uint8_t* src)
{
    uint32_t low = src[0] | (src[1] << 8) | (src[2] << 16) | (src[3] << 24);
    uint32_t high = src[4] | (src[5] << 8) | (src[6] << 16) | (src[7] << 24);
    return ((uint64_t) high << 32) | (uint64_t) low;
}

/*
 * Gets the next element. Parsing errors result in an EVENT_TYPE_UNKNOWN type.
 * If there is nothing to process, the complete field is set to non-zero. If
 * an EVENT_TYPE_UNKNOWN type is returned once, and the caller does not check
 * this and continues to call this function, the behavior is undefined
 * (although it won't crash).
 */
static android_log_list_element android_log_read_next_internal(
        android_log_context ctx, int peek) {
    android_log_list_element elem;
    unsigned pos;
    android_log_context_internal *context;

    context = (android_log_context_internal *)ctx;

    memset(&elem, 0, sizeof(elem));

    /* Nothing to parse from this context, so return complete. */
    if (!context || (kAndroidLoggerRead != context->read_write_flag) ||
            (context->list_nest_depth > ANDROID_MAX_LIST_NEST_DEPTH) ||
            (context->count[context->list_nest_depth] >=
                (MAX_EVENT_PAYLOAD / (sizeof(uint8_t) + sizeof(uint8_t))))) {
        elem.type = EVENT_TYPE_UNKNOWN;
        if (context &&
                (context->list_stop ||
                ((context->list_nest_depth <= ANDROID_MAX_LIST_NEST_DEPTH) &&
                    !context->count[context->list_nest_depth]))) {
            elem.type = EVENT_TYPE_LIST_STOP;
        }
        elem.complete = true;
        return elem;
    }

    /*
     * Use a different variable to update the position in case this
     * operation is a "peek".
     */
    pos = context->pos;
    if (context->list_stop) {
        elem.type = EVENT_TYPE_LIST_STOP;
        elem.complete = !context->count[0] && (!context->list_nest_depth ||
            ((context->list_nest_depth == 1) && !context->count[1]));
        if (!peek) {
            /* Suck in superfluous stop */
            if (context->storage[pos] == EVENT_TYPE_LIST_STOP) {
                context->pos = pos + 1;
            }
            if (context->list_nest_depth) {
                --context->list_nest_depth;
                if (context->count[context->list_nest_depth]) {
                    context->list_stop = false;
                }
            } else {
                context->list_stop = false;
            }
        }
        return elem;
    }
    if ((pos + 1) > context->len) {
        elem.type = EVENT_TYPE_UNKNOWN;
        elem.complete = true;
        return elem;
    }

    elem.type = context->storage[pos++];
    switch ((int)elem.type) {
    case EVENT_TYPE_FLOAT:
        /* Rely on union to translate elem.data.int32 into elem.data.float32 */
        /* FALLTHRU */
    case EVENT_TYPE_INT:
        elem.len = sizeof(int32_t);
        if ((pos + elem.len) > context->len) {
            elem.type = EVENT_TYPE_UNKNOWN;
            return elem;
        }
        elem.data.int32 = get4LE(&context->storage[pos]);
        /* common tangeable object suffix */
        pos += elem.len;
        elem.complete = !context->list_nest_depth && !context->count[0];
        if (!peek) {
            if (!context->count[context->list_nest_depth] ||
                    !--(context->count[context->list_nest_depth])) {
                context->list_stop = true;
            }
            context->pos = pos;
        }
        return elem;

    case EVENT_TYPE_LONG:
        elem.len = sizeof(int64_t);
        if ((pos + elem.len) > context->len) {
            elem.type = EVENT_TYPE_UNKNOWN;
            return elem;
        }
        elem.data.int64 = get8LE(&context->storage[pos]);
        /* common tangeable object suffix */
        pos += elem.len;
        elem.complete = !context->list_nest_depth && !context->count[0];
        if (!peek) {
            if (!context->count[context->list_nest_depth] ||
                    !--(context->count[context->list_nest_depth])) {
                context->list_stop = true;
            }
            context->pos = pos;
        }
        return elem;

    case EVENT_TYPE_STRING:
        if ((pos + sizeof(int32_t)) > context->len) {
            elem.type = EVENT_TYPE_UNKNOWN;
            elem.complete = true;
            return elem;
        }
        elem.len = get4LE(&context->storage[pos]);
        pos += sizeof(int32_t);
        if ((pos + elem.len) > context->len) {
            elem.len = context->len - pos; /* truncate string */
            elem.complete = true;
            if (!elem.len) {
                elem.type = EVENT_TYPE_UNKNOWN;
                return elem;
            }
        }
        elem.data.string = (char *)&context->storage[pos];
        /* common tangeable object suffix */
        pos += elem.len;
        elem.complete = !context->list_nest_depth && !context->count[0];
        if (!peek) {
            if (!context->count[context->list_nest_depth] ||
                    !--(context->count[context->list_nest_depth])) {
                context->list_stop = true;
            }
            context->pos = pos;
        }
        return elem;

    case EVENT_TYPE_LIST:
        if ((pos + sizeof(uint8_t)) > context->len) {
            elem.type = EVENT_TYPE_UNKNOWN;
            elem.complete = true;
            return elem;
        }
        elem.complete = context->list_nest_depth >= ANDROID_MAX_LIST_NEST_DEPTH;
        if (peek) {
            return elem;
        }
        if (context->count[context->list_nest_depth]) {
            context->count[context->list_nest_depth]--;
        }
        context->list_stop = !context->storage[pos];
        context->list_nest_depth++;
        if (context->list_nest_depth <= ANDROID_MAX_LIST_NEST_DEPTH) {
            context->count[context->list_nest_depth] = context->storage[pos];
        }
        context->pos = pos + sizeof(uint8_t);
        return elem;

    case EVENT_TYPE_LIST_STOP: /* Suprise Newline terminates lists. */
        if (!peek) {
            context->pos = pos;
        }
        elem.type = EVENT_TYPE_UNKNOWN;
        elem.complete = !context->list_nest_depth;
        if (context->list_nest_depth > 0) {
            elem.type = EVENT_TYPE_LIST_STOP;
            if (!peek) {
                context->list_nest_depth--;
            }
        }
        return elem;

    default:
        elem.type = EVENT_TYPE_UNKNOWN;
        return elem;
    }
}

LIBLOG_ABI_PUBLIC android_log_list_element android_log_read_next(
        android_log_context ctx) {
    return android_log_read_next_internal(ctx, 0);
}

LIBLOG_ABI_PUBLIC android_log_list_element android_log_peek_next(
        android_log_context ctx) {
    return android_log_read_next_internal(ctx, 1);
}
