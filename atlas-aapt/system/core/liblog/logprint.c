/*
**
** Copyright 2006-2014, The Android Open Source Project
**
** Licensed under the Apache License, Version 2.0 (the "License");
** you may not use this file except in compliance with the License.
** You may obtain a copy of the License at
**
**     http://www.apache.org/licenses/LICENSE-2.0
**
** Unless required by applicable law or agreed to in writing, software
** distributed under the License is distributed on an "AS IS" BASIS,
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
** See the License for the specific language governing permissions and
** limitations under the License.
*/

#define _GNU_SOURCE /* for asprintf */

#include <arpa/inet.h>
#include <assert.h>
#include <ctype.h>
#include <errno.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <inttypes.h>
#include <sys/param.h>

#include <cutils/list.h>
#include <log/logd.h>
#include <log/logprint.h>
#include <private/android_filesystem_config.h>

#include "log_portability.h"

#define MS_PER_NSEC 1000000
#define US_PER_NSEC 1000

typedef struct FilterInfo_t {
    char *mTag;
    android_LogPriority mPri;
    struct FilterInfo_t *p_next;
} FilterInfo;

struct AndroidLogFormat_t {
    android_LogPriority global_pri;
    FilterInfo *filters;
    AndroidLogPrintFormat format;
    bool colored_output;
    bool usec_time_output;
    bool printable_output;
    bool year_output;
    bool zone_output;
    bool epoch_output;
    bool monotonic_output;
    bool uid_output;
};

/*
 *  gnome-terminal color tags
 *    See http://misc.flogisoft.com/bash/tip_colors_and_formatting
 *    for ideas on how to set the forground color of the text for xterm.
 *    The color manipulation character stream is defined as:
 *      ESC [ 3 8 ; 5 ; <color#> m
 */
#define ANDROID_COLOR_BLUE     75
#define ANDROID_COLOR_DEFAULT 231
#define ANDROID_COLOR_GREEN    40
#define ANDROID_COLOR_ORANGE  166
#define ANDROID_COLOR_RED     196
#define ANDROID_COLOR_YELLOW  226

static FilterInfo * filterinfo_new(const char * tag, android_LogPriority pri)
{
    FilterInfo *p_ret;

    p_ret = (FilterInfo *)calloc(1, sizeof(FilterInfo));
    p_ret->mTag = strdup(tag);
    p_ret->mPri = pri;

    return p_ret;
}

/* balance to above, filterinfo_free left unimplemented */

/*
 * Note: also accepts 0-9 priorities
 * returns ANDROID_LOG_UNKNOWN if the character is unrecognized
 */
static android_LogPriority filterCharToPri (char c)
{
    android_LogPriority pri;

    c = tolower(c);

    if (c >= '0' && c <= '9') {
        if (c >= ('0'+ANDROID_LOG_SILENT)) {
            pri = ANDROID_LOG_VERBOSE;
        } else {
            pri = (android_LogPriority)(c - '0');
        }
    } else if (c == 'v') {
        pri = ANDROID_LOG_VERBOSE;
    } else if (c == 'd') {
        pri = ANDROID_LOG_DEBUG;
    } else if (c == 'i') {
        pri = ANDROID_LOG_INFO;
    } else if (c == 'w') {
        pri = ANDROID_LOG_WARN;
    } else if (c == 'e') {
        pri = ANDROID_LOG_ERROR;
    } else if (c == 'f') {
        pri = ANDROID_LOG_FATAL;
    } else if (c == 's') {
        pri = ANDROID_LOG_SILENT;
    } else if (c == '*') {
        pri = ANDROID_LOG_DEFAULT;
    } else {
        pri = ANDROID_LOG_UNKNOWN;
    }

    return pri;
}

static char filterPriToChar (android_LogPriority pri)
{
    switch (pri) {
        case ANDROID_LOG_VERBOSE:       return 'V';
        case ANDROID_LOG_DEBUG:         return 'D';
        case ANDROID_LOG_INFO:          return 'I';
        case ANDROID_LOG_WARN:          return 'W';
        case ANDROID_LOG_ERROR:         return 'E';
        case ANDROID_LOG_FATAL:         return 'F';
        case ANDROID_LOG_SILENT:        return 'S';

        case ANDROID_LOG_DEFAULT:
        case ANDROID_LOG_UNKNOWN:
        default:                        return '?';
    }
}

static int colorFromPri (android_LogPriority pri)
{
    switch (pri) {
        case ANDROID_LOG_VERBOSE:       return ANDROID_COLOR_DEFAULT;
        case ANDROID_LOG_DEBUG:         return ANDROID_COLOR_BLUE;
        case ANDROID_LOG_INFO:          return ANDROID_COLOR_GREEN;
        case ANDROID_LOG_WARN:          return ANDROID_COLOR_ORANGE;
        case ANDROID_LOG_ERROR:         return ANDROID_COLOR_RED;
        case ANDROID_LOG_FATAL:         return ANDROID_COLOR_RED;
        case ANDROID_LOG_SILENT:        return ANDROID_COLOR_DEFAULT;

        case ANDROID_LOG_DEFAULT:
        case ANDROID_LOG_UNKNOWN:
        default:                        return ANDROID_COLOR_DEFAULT;
    }
}

static android_LogPriority filterPriForTag(
        AndroidLogFormat *p_format, const char *tag)
{
    FilterInfo *p_curFilter;

    for (p_curFilter = p_format->filters
            ; p_curFilter != NULL
            ; p_curFilter = p_curFilter->p_next
    ) {
        if (0 == strcmp(tag, p_curFilter->mTag)) {
            if (p_curFilter->mPri == ANDROID_LOG_DEFAULT) {
                return p_format->global_pri;
            } else {
                return p_curFilter->mPri;
            }
        }
    }

    return p_format->global_pri;
}

/**
 * returns 1 if this log line should be printed based on its priority
 * and tag, and 0 if it should not
 */
LIBLOG_ABI_PUBLIC int android_log_shouldPrintLine (
        AndroidLogFormat *p_format,
        const char *tag,
        android_LogPriority pri)
{
    return pri >= filterPriForTag(p_format, tag);
}

LIBLOG_ABI_PUBLIC AndroidLogFormat *android_log_format_new()
{
    AndroidLogFormat *p_ret;

    p_ret = calloc(1, sizeof(AndroidLogFormat));

    p_ret->global_pri = ANDROID_LOG_VERBOSE;
    p_ret->format = FORMAT_BRIEF;
    p_ret->colored_output = false;
    p_ret->usec_time_output = false;
    p_ret->printable_output = false;
    p_ret->year_output = false;
    p_ret->zone_output = false;
    p_ret->epoch_output = false;
    p_ret->monotonic_output = android_log_clockid() == CLOCK_MONOTONIC;
    p_ret->uid_output = false;

    return p_ret;
}

static list_declare(convertHead);

LIBLOG_ABI_PUBLIC void android_log_format_free(AndroidLogFormat *p_format)
{
    FilterInfo *p_info, *p_info_old;

    p_info = p_format->filters;

    while (p_info != NULL) {
        p_info_old = p_info;
        p_info = p_info->p_next;

        free(p_info_old);
    }

    free(p_format);

    /* Free conversion resource, can always be reconstructed */
    while (!list_empty(&convertHead)) {
        struct listnode *node = list_head(&convertHead);
        list_remove(node);
        free(node);
    }
}

LIBLOG_ABI_PUBLIC int android_log_setPrintFormat(
        AndroidLogFormat *p_format,
        AndroidLogPrintFormat format)
{
    switch (format) {
    case FORMAT_MODIFIER_COLOR:
        p_format->colored_output = true;
        return 0;
    case FORMAT_MODIFIER_TIME_USEC:
        p_format->usec_time_output = true;
        return 0;
    case FORMAT_MODIFIER_PRINTABLE:
        p_format->printable_output = true;
        return 0;
    case FORMAT_MODIFIER_YEAR:
        p_format->year_output = true;
        return 0;
    case FORMAT_MODIFIER_ZONE:
        p_format->zone_output = !p_format->zone_output;
        return 0;
    case FORMAT_MODIFIER_EPOCH:
        p_format->epoch_output = true;
        return 0;
    case FORMAT_MODIFIER_MONOTONIC:
        p_format->monotonic_output = true;
        return 0;
    case FORMAT_MODIFIER_UID:
        p_format->uid_output = true;
        return 0;
    default:
        break;
    }
    p_format->format = format;
    return 1;
}

static const char tz[] = "TZ";
static const char utc[] = "UTC";

/**
 * Returns FORMAT_OFF on invalid string
 */
LIBLOG_ABI_PUBLIC AndroidLogPrintFormat android_log_formatFromString(
        const char * formatString)
{
    static AndroidLogPrintFormat format;

    if (strcmp(formatString, "brief") == 0) format = FORMAT_BRIEF;
    else if (strcmp(formatString, "process") == 0) format = FORMAT_PROCESS;
    else if (strcmp(formatString, "tag") == 0) format = FORMAT_TAG;
    else if (strcmp(formatString, "thread") == 0) format = FORMAT_THREAD;
    else if (strcmp(formatString, "raw") == 0) format = FORMAT_RAW;
    else if (strcmp(formatString, "time") == 0) format = FORMAT_TIME;
    else if (strcmp(formatString, "threadtime") == 0) format = FORMAT_THREADTIME;
    else if (strcmp(formatString, "long") == 0) format = FORMAT_LONG;
    else if (strcmp(formatString, "color") == 0) format = FORMAT_MODIFIER_COLOR;
    else if (strcmp(formatString, "usec") == 0) format = FORMAT_MODIFIER_TIME_USEC;
    else if (strcmp(formatString, "printable") == 0) format = FORMAT_MODIFIER_PRINTABLE;
    else if (strcmp(formatString, "year") == 0) format = FORMAT_MODIFIER_YEAR;
    else if (strcmp(formatString, "zone") == 0) format = FORMAT_MODIFIER_ZONE;
    else if (strcmp(formatString, "epoch") == 0) format = FORMAT_MODIFIER_EPOCH;
    else if (strcmp(formatString, "monotonic") == 0) format = FORMAT_MODIFIER_MONOTONIC;
    else if (strcmp(formatString, "uid") == 0) format = FORMAT_MODIFIER_UID;
    else {
        extern char *tzname[2];
        static const char gmt[] = "GMT";
        char *cp = getenv(tz);
        if (cp) {
            cp = strdup(cp);
        }
        setenv(tz, formatString, 1);
        /*
         * Run tzset here to determine if the timezone is legitimate. If the
         * zone is GMT, check if that is what was asked for, if not then
         * did not match any on the system; report an error to caller.
         */
        tzset();
        if (!tzname[0]
                || ((!strcmp(tzname[0], utc)
                        || !strcmp(tzname[0], gmt)) /* error? */
                    && strcasecmp(formatString, utc)
                    && strcasecmp(formatString, gmt))) { /* ok */
            if (cp) {
                setenv(tz, cp, 1);
            } else {
                unsetenv(tz);
            }
            tzset();
            format = FORMAT_OFF;
        } else {
            format = FORMAT_MODIFIER_ZONE;
        }
        free(cp);
    }

    return format;
}

/**
 * filterExpression: a single filter expression
 * eg "AT:d"
 *
 * returns 0 on success and -1 on invalid expression
 *
 * Assumes single threaded execution
 */

LIBLOG_ABI_PUBLIC int android_log_addFilterRule(
        AndroidLogFormat *p_format,
        const char *filterExpression)
{
    size_t tagNameLength;
    android_LogPriority pri = ANDROID_LOG_DEFAULT;

    tagNameLength = strcspn(filterExpression, ":");

    if (tagNameLength == 0) {
        goto error;
    }

    if(filterExpression[tagNameLength] == ':') {
        pri = filterCharToPri(filterExpression[tagNameLength+1]);

        if (pri == ANDROID_LOG_UNKNOWN) {
            goto error;
        }
    }

    if(0 == strncmp("*", filterExpression, tagNameLength)) {
        /*
         * This filter expression refers to the global filter
         * The default level for this is DEBUG if the priority
         * is unspecified
         */
        if (pri == ANDROID_LOG_DEFAULT) {
            pri = ANDROID_LOG_DEBUG;
        }

        p_format->global_pri = pri;
    } else {
        /*
         * for filter expressions that don't refer to the global
         * filter, the default is verbose if the priority is unspecified
         */
        if (pri == ANDROID_LOG_DEFAULT) {
            pri = ANDROID_LOG_VERBOSE;
        }

        char *tagName;

/*
 * Presently HAVE_STRNDUP is never defined, so the second case is always taken
 * Darwin doesn't have strnup, everything else does
 */
#ifdef HAVE_STRNDUP
        tagName = strndup(filterExpression, tagNameLength);
#else
        /* a few extra bytes copied... */
        tagName = strdup(filterExpression);
        tagName[tagNameLength] = '\0';
#endif /*HAVE_STRNDUP*/

        FilterInfo *p_fi = filterinfo_new(tagName, pri);
        free(tagName);

        p_fi->p_next = p_format->filters;
        p_format->filters = p_fi;
    }

    return 0;
error:
    return -1;
}


/**
 * filterString: a comma/whitespace-separated set of filter expressions
 *
 * eg "AT:d *:i"
 *
 * returns 0 on success and -1 on invalid expression
 *
 * Assumes single threaded execution
 *
 */

LIBLOG_ABI_PUBLIC int android_log_addFilterString(
        AndroidLogFormat *p_format,
        const char *filterString)
{
    char *filterStringCopy = strdup (filterString);
    char *p_cur = filterStringCopy;
    char *p_ret;
    int err;

    /* Yes, I'm using strsep */
    while (NULL != (p_ret = strsep(&p_cur, " \t,"))) {
        /* ignore whitespace-only entries */
        if(p_ret[0] != '\0') {
            err = android_log_addFilterRule(p_format, p_ret);

            if (err < 0) {
                goto error;
            }
        }
    }

    free (filterStringCopy);
    return 0;
error:
    free (filterStringCopy);
    return -1;
}

/**
 * Splits a wire-format buffer into an AndroidLogEntry
 * entry allocated by caller. Pointers will point directly into buf
 *
 * Returns 0 on success and -1 on invalid wire format (entry will be
 * in unspecified state)
 */
LIBLOG_ABI_PUBLIC int android_log_processLogBuffer(
        struct logger_entry *buf,
        AndroidLogEntry *entry)
{
    entry->tv_sec = buf->sec;
    entry->tv_nsec = buf->nsec;
    entry->uid = -1;
    entry->pid = buf->pid;
    entry->tid = buf->tid;

    /*
     * format: <priority:1><tag:N>\0<message:N>\0
     *
     * tag str
     *   starts at buf->msg+1
     * msg
     *   starts at buf->msg+1+len(tag)+1
     *
     * The message may have been truncated by the kernel log driver.
     * When that happens, we must null-terminate the message ourselves.
     */
    if (buf->len < 3) {
        /*
         * An well-formed entry must consist of at least a priority
         * and two null characters
         */
        fprintf(stderr, "+++ LOG: entry too small\n");
        return -1;
    }

    int msgStart = -1;
    int msgEnd = -1;

    int i;
    char *msg = buf->msg;
    struct logger_entry_v2 *buf2 = (struct logger_entry_v2 *)buf;
    if (buf2->hdr_size) {
        msg = ((char *)buf2) + buf2->hdr_size;
        if (buf2->hdr_size >= sizeof(struct logger_entry_v4)) {
            entry->uid = ((struct logger_entry_v4 *)buf)->uid;
        }
    }
    for (i = 1; i < buf->len; i++) {
        if (msg[i] == '\0') {
            if (msgStart == -1) {
                msgStart = i + 1;
            } else {
                msgEnd = i;
                break;
            }
        }
    }

    if (msgStart == -1) {
        /* +++ LOG: malformed log message, DYB */
        for (i = 1; i < buf->len; i++) {
            /* odd characters in tag? */
            if ((msg[i] <= ' ') || (msg[i] == ':') || (msg[i] >= 0x7f)) {
                msg[i] = '\0';
                msgStart = i + 1;
                break;
            }
        }
        if (msgStart == -1) {
            msgStart = buf->len - 1; /* All tag, no message, print truncates */
        }
    }
    if (msgEnd == -1) {
        /* incoming message not null-terminated; force it */
        msgEnd = buf->len - 1; /* may result in msgEnd < msgStart */
        msg[msgEnd] = '\0';
    }

    entry->priority = msg[0];
    entry->tag = msg + 1;
    entry->message = msg + msgStart;
    entry->messageLen = (msgEnd < msgStart) ? 0 : (msgEnd - msgStart);

    return 0;
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
    uint32_t low, high;

    low = src[0] | (src[1] << 8) | (src[2] << 16) | (src[3] << 24);
    high = src[4] | (src[5] << 8) | (src[6] << 16) | (src[7] << 24);
    return ((uint64_t) high << 32) | (uint64_t) low;
}


/*
 * Recursively convert binary log data to printable form.
 *
 * This needs to be recursive because you can have lists of lists.
 *
 * If we run out of room, we stop processing immediately.  It's important
 * for us to check for space on every output element to avoid producing
 * garbled output.
 *
 * Returns 0 on success, 1 on buffer full, -1 on failure.
 */
static int android_log_printBinaryEvent(const unsigned char** pEventData,
    size_t* pEventDataLen, char** pOutBuf, size_t* pOutBufLen)
{
    const unsigned char* eventData = *pEventData;
    size_t eventDataLen = *pEventDataLen;
    char* outBuf = *pOutBuf;
    size_t outBufLen = *pOutBufLen;
    unsigned char type;
    size_t outCount;
    int result = 0;

    if (eventDataLen < 1)
        return -1;
    type = *eventData++;
    eventDataLen--;

    switch (type) {
    case EVENT_TYPE_INT:
        /* 32-bit signed int */
        {
            int ival;

            if (eventDataLen < 4)
                return -1;
            ival = get4LE(eventData);
            eventData += 4;
            eventDataLen -= 4;

            outCount = snprintf(outBuf, outBufLen, "%d", ival);
            if (outCount < outBufLen) {
                outBuf += outCount;
                outBufLen -= outCount;
            } else {
                /* halt output */
                goto no_room;
            }
        }
        break;
    case EVENT_TYPE_LONG:
        /* 64-bit signed long */
        {
            uint64_t lval;

            if (eventDataLen < 8)
                return -1;
            lval = get8LE(eventData);
            eventData += 8;
            eventDataLen -= 8;

            outCount = snprintf(outBuf, outBufLen, "%" PRId64, lval);
            if (outCount < outBufLen) {
                outBuf += outCount;
                outBufLen -= outCount;
            } else {
                /* halt output */
                goto no_room;
            }
        }
        break;
    case EVENT_TYPE_FLOAT:
        /* float */
        {
            uint32_t ival;
            float fval;

            if (eventDataLen < 4)
                return -1;
            ival = get4LE(eventData);
            fval = *(float*)&ival;
            eventData += 4;
            eventDataLen -= 4;

            outCount = snprintf(outBuf, outBufLen, "%f", fval);
            if (outCount < outBufLen) {
                outBuf += outCount;
                outBufLen -= outCount;
            } else {
                /* halt output */
                goto no_room;
            }
        }
        break;
    case EVENT_TYPE_STRING:
        /* UTF-8 chars, not NULL-terminated */
        {
            unsigned int strLen;

            if (eventDataLen < 4)
                return -1;
            strLen = get4LE(eventData);
            eventData += 4;
            eventDataLen -= 4;

            if (eventDataLen < strLen)
                return -1;

            if (strLen < outBufLen) {
                memcpy(outBuf, eventData, strLen);
                outBuf += strLen;
                outBufLen -= strLen;
            } else if (outBufLen > 0) {
                /* copy what we can */
                memcpy(outBuf, eventData, outBufLen);
                outBuf += outBufLen;
                outBufLen -= outBufLen;
                goto no_room;
            }
            eventData += strLen;
            eventDataLen -= strLen;
            break;
        }
    case EVENT_TYPE_LIST:
        /* N items, all different types */
        {
            unsigned char count;
            int i;

            if (eventDataLen < 1)
                return -1;

            count = *eventData++;
            eventDataLen--;

            if (outBufLen > 0) {
                *outBuf++ = '[';
                outBufLen--;
            } else {
                goto no_room;
            }

            for (i = 0; i < count; i++) {
                result = android_log_printBinaryEvent(&eventData, &eventDataLen,
                        &outBuf, &outBufLen);
                if (result != 0)
                    goto bail;

                if (i < count-1) {
                    if (outBufLen > 0) {
                        *outBuf++ = ',';
                        outBufLen--;
                    } else {
                        goto no_room;
                    }
                }
            }

            if (outBufLen > 0) {
                *outBuf++ = ']';
                outBufLen--;
            } else {
                goto no_room;
            }
        }
        break;
    default:
        fprintf(stderr, "Unknown binary event type %d\n", type);
        return -1;
    }

bail:
    *pEventData = eventData;
    *pEventDataLen = eventDataLen;
    *pOutBuf = outBuf;
    *pOutBufLen = outBufLen;
    return result;

no_room:
    result = 1;
    goto bail;
}

/**
 * Convert a binary log entry to ASCII form.
 *
 * For convenience we mimic the processLogBuffer API.  There is no
 * pre-defined output length for the binary data, since we're free to format
 * it however we choose, which means we can't really use a fixed-size buffer
 * here.
 */
LIBLOG_ABI_PUBLIC int android_log_processBinaryLogBuffer(
        struct logger_entry *buf,
        AndroidLogEntry *entry,
        const EventTagMap *map,
        char *messageBuf, int messageBufLen)
{
    size_t inCount;
    unsigned int tagIndex;
    const unsigned char* eventData;

    entry->tv_sec = buf->sec;
    entry->tv_nsec = buf->nsec;
    entry->priority = ANDROID_LOG_INFO;
    entry->uid = -1;
    entry->pid = buf->pid;
    entry->tid = buf->tid;

    /*
     * Pull the tag out, fill in some additional details based on incoming
     * buffer version (v3 adds lid, v4 adds uid).
     */
    eventData = (const unsigned char*) buf->msg;
    struct logger_entry_v2 *buf2 = (struct logger_entry_v2 *)buf;
    if (buf2->hdr_size) {
        eventData = ((unsigned char *)buf2) + buf2->hdr_size;
        if ((buf2->hdr_size >= sizeof(struct logger_entry_v3)) &&
                (((struct logger_entry_v3 *)buf)->lid == LOG_ID_SECURITY)) {
            entry->priority = ANDROID_LOG_WARN;
        }
        if (buf2->hdr_size >= sizeof(struct logger_entry_v4)) {
            entry->uid = ((struct logger_entry_v4 *)buf)->uid;
        }
    }
    inCount = buf->len;
    if (inCount < 4)
        return -1;
    tagIndex = get4LE(eventData);
    eventData += 4;
    inCount -= 4;

    if (map != NULL) {
        entry->tag = android_lookupEventTag(map, tagIndex);
    } else {
        entry->tag = NULL;
    }

    /*
     * If we don't have a map, or didn't find the tag number in the map,
     * stuff a generated tag value into the start of the output buffer and
     * shift the buffer pointers down.
     */
    if (entry->tag == NULL) {
        int tagLen;

        tagLen = snprintf(messageBuf, messageBufLen, "[%d]", tagIndex);
        entry->tag = messageBuf;
        messageBuf += tagLen+1;
        messageBufLen -= tagLen+1;
    }

    /*
     * Format the event log data into the buffer.
     */
    char* outBuf = messageBuf;
    size_t outRemaining = messageBufLen-1;      /* leave one for nul byte */
    int result;
    result = android_log_printBinaryEvent(&eventData, &inCount, &outBuf,
                &outRemaining);
    if (result < 0) {
        fprintf(stderr, "Binary log entry conversion failed\n");
        return -1;
    } else if (result == 1) {
        if (outBuf > messageBuf) {
            /* leave an indicator */
            *(outBuf-1) = '!';
        } else {
            /* no room to output anything at all */
            *outBuf++ = '!';
            outRemaining--;
        }
        /* pretend we ate all the data */
        inCount = 0;
    }

    /* eat the silly terminating '\n' */
    if (inCount == 1 && *eventData == '\n') {
        eventData++;
        inCount--;
    }

    if (inCount != 0) {
        fprintf(stderr,
            "Warning: leftover binary log data (%zu bytes)\n", inCount);
    }

    /*
     * Terminate the buffer.  The NUL byte does not count as part of
     * entry->messageLen.
     */
    *outBuf = '\0';
    entry->messageLen = outBuf - messageBuf;
    assert(entry->messageLen == (messageBufLen-1) - outRemaining);

    entry->message = messageBuf;

    return 0;
}

/*
 * One utf8 character at a time
 *
 * Returns the length of the utf8 character in the buffer,
 * or -1 if illegal or truncated
 *
 * Open coded from libutils/Unicode.cpp, borrowed from utf8_length(),
 * can not remove from here because of library circular dependencies.
 * Expect one-day utf8_character_length with the same signature could
 * _also_ be part of libutils/Unicode.cpp if its usefullness needs to
 * propagate globally.
 */
LIBLOG_WEAK ssize_t utf8_character_length(const char *src, size_t len)
{
    const char *cur = src;
    const char first_char = *cur++;
    static const uint32_t kUnicodeMaxCodepoint = 0x0010FFFF;
    int32_t mask, to_ignore_mask;
    size_t num_to_read;
    uint32_t utf32;

    if ((first_char & 0x80) == 0) { /* ASCII */
        return first_char ? 1 : -1;
    }

    /*
     * (UTF-8's character must not be like 10xxxxxx,
     *  but 110xxxxx, 1110xxxx, ... or 1111110x)
     */
    if ((first_char & 0x40) == 0) {
        return -1;
    }

    for (utf32 = 1, num_to_read = 1, mask = 0x40, to_ignore_mask = 0x80;
         num_to_read < 5 && (first_char & mask);
         num_to_read++, to_ignore_mask |= mask, mask >>= 1) {
        if (num_to_read > len) {
            return -1;
        }
        if ((*cur & 0xC0) != 0x80) { /* can not be 10xxxxxx? */
            return -1;
        }
        utf32 = (utf32 << 6) + (*cur++ & 0b00111111);
    }
    /* "first_char" must be (110xxxxx - 11110xxx) */
    if (num_to_read >= 5) {
        return -1;
    }
    to_ignore_mask |= mask;
    utf32 |= ((~to_ignore_mask) & first_char) << (6 * (num_to_read - 1));
    if (utf32 > kUnicodeMaxCodepoint) {
        return -1;
    }
    return num_to_read;
}

/*
 * Convert to printable from message to p buffer, return string length. If p is
 * NULL, do not copy, but still return the expected string length.
 */
static size_t convertPrintable(char *p, const char *message, size_t messageLen)
{
    char *begin = p;
    bool print = p != NULL;

    while (messageLen) {
        char buf[6];
        ssize_t len = sizeof(buf) - 1;
        if ((size_t)len > messageLen) {
            len = messageLen;
        }
        len = utf8_character_length(message, len);

        if (len < 0) {
            snprintf(buf, sizeof(buf),
                     ((messageLen > 1) && isdigit(message[1]))
                         ? "\\%03o"
                         : "\\%o",
                     *message & 0377);
            len = 1;
        } else {
            buf[0] = '\0';
            if (len == 1) {
                if (*message == '\a') {
                    strcpy(buf, "\\a");
                } else if (*message == '\b') {
                    strcpy(buf, "\\b");
                } else if (*message == '\t') {
                    strcpy(buf, "\t"); // Do not escape tabs
                } else if (*message == '\v') {
                    strcpy(buf, "\\v");
                } else if (*message == '\f') {
                    strcpy(buf, "\\f");
                } else if (*message == '\r') {
                    strcpy(buf, "\\r");
                } else if (*message == '\\') {
                    strcpy(buf, "\\\\");
                } else if ((*message < ' ') || (*message & 0x80)) {
                    snprintf(buf, sizeof(buf), "\\%o", *message & 0377);
                }
            }
            if (!buf[0]) {
                strncpy(buf, message, len);
                buf[len] = '\0';
            }
        }
        if (print) {
            strcpy(p, buf);
        }
        p += strlen(buf);
        message += len;
        messageLen -= len;
    }
    return p - begin;
}

static char *readSeconds(char *e, struct timespec *t)
{
    unsigned long multiplier;
    char *p;
    t->tv_sec = strtoul(e, &p, 10);
    if (*p != '.') {
        return NULL;
    }
    t->tv_nsec = 0;
    multiplier = NS_PER_SEC;
    while (isdigit(*++p) && (multiplier /= 10)) {
        t->tv_nsec += (*p - '0') * multiplier;
    }
    return p;
}

static struct timespec *sumTimespec(struct timespec *left,
                                    struct timespec *right)
{
    left->tv_nsec += right->tv_nsec;
    left->tv_sec += right->tv_sec;
    if (left->tv_nsec >= (long)NS_PER_SEC) {
        left->tv_nsec -= NS_PER_SEC;
        left->tv_sec += 1;
    }
    return left;
}

static struct timespec *subTimespec(struct timespec *result,
                                    struct timespec *left,
                                    struct timespec *right)
{
    result->tv_nsec = left->tv_nsec - right->tv_nsec;
    result->tv_sec = left->tv_sec - right->tv_sec;
    if (result->tv_nsec < 0) {
        result->tv_nsec += NS_PER_SEC;
        result->tv_sec -= 1;
    }
    return result;
}

static long long nsecTimespec(struct timespec *now)
{
    return (long long)now->tv_sec * NS_PER_SEC + now->tv_nsec;
}

static void convertMonotonic(struct timespec *result,
                             const AndroidLogEntry *entry)
{
    struct listnode *node;
    struct conversionList {
        struct listnode node; /* first */
        struct timespec time;
        struct timespec convert;
    } *list, *next;
    struct timespec time, convert;

    /* If we do not have a conversion list, build one up */
    if (list_empty(&convertHead)) {
        bool suspended_pending = false;
        struct timespec suspended_monotonic = { 0, 0 };
        struct timespec suspended_diff = { 0, 0 };

        /*
         * Read dmesg for _some_ synchronization markers and insert
         * Anything in the Android Logger before the dmesg logging span will
         * be highly suspect regarding the monotonic time calculations.
         */
        FILE *p = popen("/system/bin/dmesg", "re");
        if (p) {
            char *line = NULL;
            size_t len = 0;
            while (getline(&line, &len, p) > 0) {
                static const char suspend[] = "PM: suspend entry ";
                static const char resume[] = "PM: suspend exit ";
                static const char healthd[] = "healthd";
                static const char battery[] = ": battery ";
                static const char suspended[] = "Suspended for ";
                struct timespec monotonic;
                struct tm tm;
                char *cp, *e = line;
                bool add_entry = true;

                if (*e == '<') {
                    while (*e && (*e != '>')) {
                        ++e;
                    }
                    if (*e != '>') {
                        continue;
                    }
                }
                if (*e != '[') {
                    continue;
                }
                while (*++e == ' ') {
                    ;
                }
                e = readSeconds(e, &monotonic);
                if (!e || (*e != ']')) {
                    continue;
                }

                if ((e = strstr(e, suspend))) {
                    e += sizeof(suspend) - 1;
                } else if ((e = strstr(line, resume))) {
                    e += sizeof(resume) - 1;
                } else if (((e = strstr(line, healthd)))
                        && ((e = strstr(e + sizeof(healthd) - 1, battery)))) {
                    /* NB: healthd is roughly 150us late, worth the price to
                     * deal with ntp-induced or hardware clock drift. */
                    e += sizeof(battery) - 1;
                } else if ((e = strstr(line, suspended))) {
                    e += sizeof(suspended) - 1;
                    e = readSeconds(e, &time);
                    if (!e) {
                        continue;
                    }
                    add_entry = false;
                    suspended_pending = true;
                    suspended_monotonic = monotonic;
                    suspended_diff = time;
                } else {
                    continue;
                }
                if (add_entry) {
                    /* look for "????-??-?? ??:??:??.????????? UTC" */
                    cp = strstr(e, " UTC");
                    if (!cp || ((cp - e) < 29) || (cp[-10] != '.')) {
                        continue;
                    }
                    e = cp - 29;
                    cp = readSeconds(cp - 10, &time);
                    if (!cp) {
                        continue;
                    }
                    cp = strptime(e, "%Y-%m-%d %H:%M:%S.", &tm);
                    if (!cp) {
                        continue;
                    }
                    cp = getenv(tz);
                    if (cp) {
                        cp = strdup(cp);
                    }
                    setenv(tz, utc, 1);
                    time.tv_sec = mktime(&tm);
                    if (cp) {
                        setenv(tz, cp, 1);
                        free(cp);
                    } else {
                        unsetenv(tz);
                    }
                    list = calloc(1, sizeof(struct conversionList));
                    list_init(&list->node);
                    list->time = time;
                    subTimespec(&list->convert, &time, &monotonic);
                    list_add_tail(&convertHead, &list->node);
                }
                if (suspended_pending && !list_empty(&convertHead)) {
                    list = node_to_item(list_tail(&convertHead),
                                        struct conversionList, node);
                    if (subTimespec(&time,
                                    subTimespec(&time,
                                                &list->time,
                                                &list->convert),
                                    &suspended_monotonic)->tv_sec > 0) {
                        /* resume, what is convert factor before? */
                        subTimespec(&convert, &list->convert, &suspended_diff);
                    } else {
                        /* suspend */
                        convert = list->convert;
                    }
                    time = suspended_monotonic;
                    sumTimespec(&time, &convert);
                    /* breakpoint just before sleep */
                    list = calloc(1, sizeof(struct conversionList));
                    list_init(&list->node);
                    list->time = time;
                    list->convert = convert;
                    list_add_tail(&convertHead, &list->node);
                    /* breakpoint just after sleep */
                    list = calloc(1, sizeof(struct conversionList));
                    list_init(&list->node);
                    list->time = time;
                    sumTimespec(&list->time, &suspended_diff);
                    list->convert = convert;
                    sumTimespec(&list->convert, &suspended_diff);
                    list_add_tail(&convertHead, &list->node);
                    suspended_pending = false;
                }
            }
            pclose(p);
        }
        /* last entry is our current time conversion */
        list = calloc(1, sizeof(struct conversionList));
        list_init(&list->node);
        clock_gettime(CLOCK_REALTIME, &list->time);
        clock_gettime(CLOCK_MONOTONIC, &convert);
        clock_gettime(CLOCK_MONOTONIC, &time);
        /* Correct for instant clock_gettime latency (syscall or ~30ns) */
        subTimespec(&time, &convert, subTimespec(&time, &time, &convert));
        /* Calculate conversion factor */
        subTimespec(&list->convert, &list->time, &time);
        list_add_tail(&convertHead, &list->node);
        if (suspended_pending) {
            /* manufacture a suspend @ point before */
            subTimespec(&convert, &list->convert, &suspended_diff);
            time = suspended_monotonic;
            sumTimespec(&time, &convert);
            /* breakpoint just after sleep */
            list = calloc(1, sizeof(struct conversionList));
            list_init(&list->node);
            list->time = time;
            sumTimespec(&list->time, &suspended_diff);
            list->convert = convert;
            sumTimespec(&list->convert, &suspended_diff);
            list_add_head(&convertHead, &list->node);
            /* breakpoint just before sleep */
            list = calloc(1, sizeof(struct conversionList));
            list_init(&list->node);
            list->time = time;
            list->convert = convert;
            list_add_head(&convertHead, &list->node);
        }
    }

    /* Find the breakpoint in the conversion list */
    list = node_to_item(list_head(&convertHead), struct conversionList, node);
    next = NULL;
    list_for_each(node, &convertHead) {
        next = node_to_item(node, struct conversionList, node);
        if (entry->tv_sec < next->time.tv_sec) {
            break;
        } else if (entry->tv_sec == next->time.tv_sec) {
            if (entry->tv_nsec < next->time.tv_nsec) {
                break;
            }
        }
        list = next;
    }

    /* blend time from one breakpoint to the next */
    convert = list->convert;
    if (next) {
        unsigned long long total, run;

        total = nsecTimespec(subTimespec(&time, &next->time, &list->time));
        time.tv_sec = entry->tv_sec;
        time.tv_nsec = entry->tv_nsec;
        run = nsecTimespec(subTimespec(&time, &time, &list->time));
        if (run < total) {
            long long crun;

            float f = nsecTimespec(subTimespec(&time, &next->convert, &convert));
            f *= run;
            f /= total;
            crun = f;
            convert.tv_sec += crun / (long long)NS_PER_SEC;
            if (crun < 0) {
                convert.tv_nsec -= (-crun) % NS_PER_SEC;
                if (convert.tv_nsec < 0) {
                    convert.tv_nsec += NS_PER_SEC;
                    convert.tv_sec -= 1;
                }
            } else {
                convert.tv_nsec += crun % NS_PER_SEC;
                if (convert.tv_nsec >= (long)NS_PER_SEC) {
                    convert.tv_nsec -= NS_PER_SEC;
                    convert.tv_sec += 1;
                }
            }
        }
    }

    /* Apply the correction factor */
    result->tv_sec = entry->tv_sec;
    result->tv_nsec = entry->tv_nsec;
    subTimespec(result, result, &convert);
}

/**
 * Formats a log message into a buffer
 *
 * Uses defaultBuffer if it can, otherwise malloc()'s a new buffer
 * If return value != defaultBuffer, caller must call free()
 * Returns NULL on malloc error
 */

LIBLOG_ABI_PUBLIC char *android_log_formatLogLine (
        AndroidLogFormat *p_format,
        char *defaultBuffer,
        size_t defaultBufferSize,
        const AndroidLogEntry *entry,
        size_t *p_outLength)
{
#if !defined(_WIN32)
    struct tm tmBuf;
#endif
    struct tm* ptm;
    char timeBuf[64]; /* good margin, 23+nul for msec, 26+nul for usec */
    char prefixBuf[128], suffixBuf[128];
    char priChar;
    int prefixSuffixIsHeaderFooter = 0;
    char *ret;
    time_t now;
    unsigned long nsec;

    priChar = filterPriToChar(entry->priority);
    size_t prefixLen = 0, suffixLen = 0;
    size_t len;

    /*
     * Get the current date/time in pretty form
     *
     * It's often useful when examining a log with "less" to jump to
     * a specific point in the file by searching for the date/time stamp.
     * For this reason it's very annoying to have regexp meta characters
     * in the time stamp.  Don't use forward slashes, parenthesis,
     * brackets, asterisks, or other special chars here.
     *
     * The caller may have affected the timezone environment, this is
     * expected to be sensitive to that.
     */
    now = entry->tv_sec;
    nsec = entry->tv_nsec;
    if (p_format->monotonic_output) {
        // prevent convertMonotonic from being called if logd is monotonic
        if (android_log_clockid() != CLOCK_MONOTONIC) {
            struct timespec time;
            convertMonotonic(&time, entry);
            now = time.tv_sec;
            nsec = time.tv_nsec;
        }
    }
    if (now < 0) {
        nsec = NS_PER_SEC - nsec;
    }
    if (p_format->epoch_output || p_format->monotonic_output) {
        ptm = NULL;
        snprintf(timeBuf, sizeof(timeBuf),
                 p_format->monotonic_output ? "%6lld" : "%19lld",
                 (long long)now);
    } else {
#if !defined(_WIN32)
        ptm = localtime_r(&now, &tmBuf);
#else
        ptm = localtime(&now);
#endif
        strftime(timeBuf, sizeof(timeBuf),
                 &"%Y-%m-%d %H:%M:%S"[p_format->year_output ? 0 : 3],
                 ptm);
    }
    len = strlen(timeBuf);
    if (p_format->usec_time_output) {
        len += snprintf(timeBuf + len, sizeof(timeBuf) - len,
                        ".%06ld", nsec / US_PER_NSEC);
    } else {
        len += snprintf(timeBuf + len, sizeof(timeBuf) - len,
                        ".%03ld", nsec / MS_PER_NSEC);
    }
    if (p_format->zone_output && ptm) {
        strftime(timeBuf + len, sizeof(timeBuf) - len, " %z", ptm);
    }

    /*
     * Construct a buffer containing the log header and log message.
     */
    if (p_format->colored_output) {
        prefixLen = snprintf(prefixBuf, sizeof(prefixBuf), "\x1B[38;5;%dm",
                             colorFromPri(entry->priority));
        prefixLen = MIN(prefixLen, sizeof(prefixBuf));
        suffixLen = snprintf(suffixBuf, sizeof(suffixBuf), "\x1B[0m");
        suffixLen = MIN(suffixLen, sizeof(suffixBuf));
    }

    char uid[16];
    uid[0] = '\0';
    if (p_format->uid_output) {
        if (entry->uid >= 0) {
            const struct android_id_info *info = android_ids;
            size_t i;

            for (i = 0; i < android_id_count; ++i) {
                if (info->aid == (unsigned int)entry->uid) {
                    break;
                }
                ++info;
            }
            if ((i < android_id_count) && (strlen(info->name) <= 5)) {
                 snprintf(uid, sizeof(uid), "%5s:", info->name);
            } else {
                 // Not worth parsing package list, names all longer than 5
                 snprintf(uid, sizeof(uid), "%5d:", entry->uid);
            }
        } else {
            snprintf(uid, sizeof(uid), "      ");
        }
    }

    switch (p_format->format) {
        case FORMAT_TAG:
            len = snprintf(prefixBuf + prefixLen, sizeof(prefixBuf) - prefixLen,
                "%c/%-8s: ", priChar, entry->tag);
            strcpy(suffixBuf + suffixLen, "\n");
            ++suffixLen;
            break;
        case FORMAT_PROCESS:
            len = snprintf(suffixBuf + suffixLen, sizeof(suffixBuf) - suffixLen,
                "  (%s)\n", entry->tag);
            suffixLen += MIN(len, sizeof(suffixBuf) - suffixLen);
            len = snprintf(prefixBuf + prefixLen, sizeof(prefixBuf) - prefixLen,
                "%c(%s%5d) ", priChar, uid, entry->pid);
            break;
        case FORMAT_THREAD:
            len = snprintf(prefixBuf + prefixLen, sizeof(prefixBuf) - prefixLen,
                "%c(%s%5d:%5d) ", priChar, uid, entry->pid, entry->tid);
            strcpy(suffixBuf + suffixLen, "\n");
            ++suffixLen;
            break;
        case FORMAT_RAW:
            prefixBuf[prefixLen] = 0;
            len = 0;
            strcpy(suffixBuf + suffixLen, "\n");
            ++suffixLen;
            break;
        case FORMAT_TIME:
            len = snprintf(prefixBuf + prefixLen, sizeof(prefixBuf) - prefixLen,
                "%s %c/%-8s(%s%5d): ", timeBuf, priChar, entry->tag,
                uid, entry->pid);
            strcpy(suffixBuf + suffixLen, "\n");
            ++suffixLen;
            break;
        case FORMAT_THREADTIME:
            ret = strchr(uid, ':');
            if (ret) {
                *ret = ' ';
            }
            len = snprintf(prefixBuf + prefixLen, sizeof(prefixBuf) - prefixLen,
                "%s %s%5d %5d %c %-8s: ", timeBuf,
                uid, entry->pid, entry->tid, priChar, entry->tag);
            strcpy(suffixBuf + suffixLen, "\n");
            ++suffixLen;
            break;
        case FORMAT_LONG:
            len = snprintf(prefixBuf + prefixLen, sizeof(prefixBuf) - prefixLen,
                "[ %s %s%5d:%5d %c/%-8s ]\n",
                timeBuf, uid, entry->pid, entry->tid, priChar, entry->tag);
            strcpy(suffixBuf + suffixLen, "\n\n");
            suffixLen += 2;
            prefixSuffixIsHeaderFooter = 1;
            break;
        case FORMAT_BRIEF:
        default:
            len = snprintf(prefixBuf + prefixLen, sizeof(prefixBuf) - prefixLen,
                "%c/%-8s(%s%5d): ", priChar, entry->tag, uid, entry->pid);
            strcpy(suffixBuf + suffixLen, "\n");
            ++suffixLen;
            break;
    }

    /* snprintf has a weird return value.   It returns what would have been
     * written given a large enough buffer.  In the case that the prefix is
     * longer then our buffer(128), it messes up the calculations below
     * possibly causing heap corruption.  To avoid this we double check and
     * set the length at the maximum (size minus null byte)
     */
    prefixLen += len;
    if (prefixLen >= sizeof(prefixBuf)) {
        prefixLen = sizeof(prefixBuf) - 1;
        prefixBuf[sizeof(prefixBuf) - 1] = '\0';
    }
    if (suffixLen >= sizeof(suffixBuf)) {
        suffixLen = sizeof(suffixBuf) - 1;
        suffixBuf[sizeof(suffixBuf) - 2] = '\n';
        suffixBuf[sizeof(suffixBuf) - 1] = '\0';
    }

    /* the following code is tragically unreadable */

    size_t numLines;
    char *p;
    size_t bufferSize;
    const char *pm;

    if (prefixSuffixIsHeaderFooter) {
        /* we're just wrapping message with a header/footer */
        numLines = 1;
    } else {
        pm = entry->message;
        numLines = 0;

        /*
         * The line-end finding here must match the line-end finding
         * in for ( ... numLines...) loop below
         */
        while (pm < (entry->message + entry->messageLen)) {
            if (*pm++ == '\n') numLines++;
        }
        /* plus one line for anything not newline-terminated at the end */
        if (pm > entry->message && *(pm-1) != '\n') numLines++;
    }

    /*
     * this is an upper bound--newlines in message may be counted
     * extraneously
     */
    bufferSize = (numLines * (prefixLen + suffixLen)) + 1;
    if (p_format->printable_output) {
        /* Calculate extra length to convert non-printable to printable */
        bufferSize += convertPrintable(NULL, entry->message, entry->messageLen);
    } else {
        bufferSize += entry->messageLen;
    }

    if (defaultBufferSize >= bufferSize) {
        ret = defaultBuffer;
    } else {
        ret = (char *)malloc(bufferSize);

        if (ret == NULL) {
            return ret;
        }
    }

    ret[0] = '\0';       /* to start strcat off */

    p = ret;
    pm = entry->message;

    if (prefixSuffixIsHeaderFooter) {
        strcat(p, prefixBuf);
        p += prefixLen;
        if (p_format->printable_output) {
            p += convertPrintable(p, entry->message, entry->messageLen);
        } else {
            strncat(p, entry->message, entry->messageLen);
            p += entry->messageLen;
        }
        strcat(p, suffixBuf);
        p += suffixLen;
    } else {
        do {
            const char *lineStart;
            size_t lineLen;
            lineStart = pm;

            /* Find the next end-of-line in message */
            while (pm < (entry->message + entry->messageLen)
                    && *pm != '\n') pm++;
            lineLen = pm - lineStart;

            strcat(p, prefixBuf);
            p += prefixLen;
            if (p_format->printable_output) {
                p += convertPrintable(p, lineStart, lineLen);
            } else {
                strncat(p, lineStart, lineLen);
                p += lineLen;
            }
            strcat(p, suffixBuf);
            p += suffixLen;

            if (*pm == '\n') pm++;
        } while (pm < (entry->message + entry->messageLen));
    }

    if (p_outLength != NULL) {
        *p_outLength = p - ret;
    }

    return ret;
}

/**
 * Either print or do not print log line, based on filter
 *
 * Returns count bytes written
 */

LIBLOG_ABI_PUBLIC int android_log_printLogLine(
        AndroidLogFormat *p_format,
        int fd,
        const AndroidLogEntry *entry)
{
    int ret;
    char defaultBuffer[512];
    char *outBuffer = NULL;
    size_t totalLen;

    outBuffer = android_log_formatLogLine(p_format, defaultBuffer,
            sizeof(defaultBuffer), entry, &totalLen);

    if (!outBuffer)
        return -1;

    do {
        ret = write(fd, outBuffer, totalLen);
    } while (ret < 0 && errno == EINTR);

    if (ret < 0) {
        fprintf(stderr, "+++ LOG: write failed (errno=%d)\n", errno);
        ret = 0;
        goto done;
    }

    if (((size_t)ret) < totalLen) {
        fprintf(stderr, "+++ LOG: write partial (%d of %d)\n", ret,
                (int)totalLen);
        goto done;
    }

done:
    if (outBuffer != defaultBuffer) {
        free(outBuffer);
    }

    return ret;
}
