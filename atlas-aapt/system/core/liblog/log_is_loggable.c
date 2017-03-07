/*
** Copyright 2014, The Android Open Source Project
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

#include <ctype.h>
#include <pthread.h>
#include <stdlib.h>
#include <string.h>
#define _REALLY_INCLUDE_SYS__SYSTEM_PROPERTIES_H_
#include <sys/_system_properties.h>

#include <android/log.h>

#include "log_portability.h"

static pthread_mutex_t lock_loggable = PTHREAD_MUTEX_INITIALIZER;

static int lock()
{
    /*
     * If we trigger a signal handler in the middle of locked activity and the
     * signal handler logs a message, we could get into a deadlock state.
     */
    /*
     *  Any contention, and we can turn around and use the non-cached method
     * in less time than the system call associated with a mutex to deal with
     * the contention.
     */
    return pthread_mutex_trylock(&lock_loggable);
}

static void unlock()
{
    pthread_mutex_unlock(&lock_loggable);
}

struct cache {
    const prop_info *pinfo;
    uint32_t serial;
    unsigned char c;
};

static int check_cache(struct cache *cache)
{
    return cache->pinfo
        && __system_property_serial(cache->pinfo) != cache->serial;
}

#define BOOLEAN_TRUE 0xFF
#define BOOLEAN_FALSE 0xFE

static void refresh_cache(struct cache *cache, const char *key)
{
    char buf[PROP_VALUE_MAX];

    if (!cache->pinfo) {
        cache->pinfo = __system_property_find(key);
        if (!cache->pinfo) {
            return;
        }
    }
    cache->serial = __system_property_serial(cache->pinfo);
    __system_property_read(cache->pinfo, 0, buf);
    switch(buf[0]) {
    case 't': case 'T':
        cache->c = strcasecmp(buf + 1, "rue") ? buf[0] : BOOLEAN_TRUE;
        break;
    case 'f': case 'F':
        cache->c = strcasecmp(buf + 1, "alse") ? buf[0] : BOOLEAN_FALSE;
        break;
    default:
        cache->c = buf[0];
    }
}

static int __android_log_level(const char *tag, int default_prio)
{
    /* sizeof() is used on this array below */
    static const char log_namespace[] = "persist.log.tag.";
    static const size_t base_offset = 8; /* skip "persist." */
    /* calculate the size of our key temporary buffer */
    const size_t taglen = (tag && *tag) ? strlen(tag) : 0;
    /* sizeof(log_namespace) = strlen(log_namespace) + 1 */
    char key[sizeof(log_namespace) + taglen]; /* may be > PROPERTY_KEY_MAX */
    char *kp;
    size_t i;
    char c = 0;
    /*
     * Single layer cache of four properties. Priorities are:
     *    log.tag.<tag>
     *    persist.log.tag.<tag>
     *    log.tag
     *    persist.log.tag
     * Where the missing tag matches all tags and becomes the
     * system global default. We do not support ro.log.tag* .
     */
    static char last_tag[PROP_NAME_MAX];
    static uint32_t global_serial;
    /* some compilers erroneously see uninitialized use. !not_locked */
    uint32_t current_global_serial = 0;
    static struct cache tag_cache[2];
    static struct cache global_cache[2];
    int change_detected;
    int global_change_detected;
    int not_locked;

    strcpy(key, log_namespace);

    global_change_detected = change_detected = not_locked = lock();

    if (!not_locked) {
        /*
         *  check all known serial numbers to changes.
         */
        for (i = 0; i < (sizeof(tag_cache) / sizeof(tag_cache[0])); ++i) {
            if (check_cache(&tag_cache[i])) {
                change_detected = 1;
            }
        }
        for (i = 0; i < (sizeof(global_cache) / sizeof(global_cache[0])); ++i) {
            if (check_cache(&global_cache[i])) {
                global_change_detected = 1;
            }
        }

        current_global_serial = __system_property_area_serial();
        if (current_global_serial != global_serial) {
            change_detected = 1;
            global_change_detected = 1;
        }
    }

    if (taglen) {
        int local_change_detected = change_detected;
        if (!not_locked) {
            if (!last_tag[0]
                    || (last_tag[0] != tag[0])
                    || strncmp(last_tag + 1, tag + 1, sizeof(last_tag) - 1)) {
                /* invalidate log.tag.<tag> cache */
                for (i = 0; i < (sizeof(tag_cache) / sizeof(tag_cache[0])); ++i) {
                    tag_cache[i].pinfo = NULL;
                    tag_cache[i].c = '\0';
                }
                last_tag[0] = '\0';
                local_change_detected = 1;
            }
            if (!last_tag[0]) {
                strncpy(last_tag, tag, sizeof(last_tag));
            }
        }
        strcpy(key + sizeof(log_namespace) - 1, tag);

        kp = key;
        for (i = 0; i < (sizeof(tag_cache) / sizeof(tag_cache[0])); ++i) {
            struct cache *cache = &tag_cache[i];
            struct cache temp_cache;

            if (not_locked) {
                temp_cache.pinfo = NULL;
                temp_cache.c = '\0';
                cache = &temp_cache;
            }
            if (local_change_detected) {
                refresh_cache(cache, kp);
            }

            if (cache->c) {
                c = cache->c;
                break;
            }

            kp = key + base_offset;
        }
    }

    switch (toupper(c)) { /* if invalid, resort to global */
    case 'V':
    case 'D':
    case 'I':
    case 'W':
    case 'E':
    case 'F': /* Not officially supported */
    case 'A':
    case 'S':
    case BOOLEAN_FALSE: /* Not officially supported */
        break;
    default:
        /* clear '.' after log.tag */
        key[sizeof(log_namespace) - 2] = '\0';

        kp = key;
        for (i = 0; i < (sizeof(global_cache) / sizeof(global_cache[0])); ++i) {
            struct cache *cache = &global_cache[i];
            struct cache temp_cache;

            if (not_locked) {
                temp_cache = *cache;
                if (temp_cache.pinfo != cache->pinfo) { /* check atomic */
                    temp_cache.pinfo = NULL;
                    temp_cache.c = '\0';
                }
                cache = &temp_cache;
            }
            if (global_change_detected) {
                refresh_cache(cache, kp);
            }

            if (cache->c) {
                c = cache->c;
                break;
            }

            kp = key + base_offset;
        }
        break;
    }

    if (!not_locked) {
        global_serial = current_global_serial;
        unlock();
    }

    switch (toupper(c)) {
    case 'V': return ANDROID_LOG_VERBOSE;
    case 'D': return ANDROID_LOG_DEBUG;
    case 'I': return ANDROID_LOG_INFO;
    case 'W': return ANDROID_LOG_WARN;
    case 'E': return ANDROID_LOG_ERROR;
    case 'F': /* FALLTHRU */ /* Not officially supported */
    case 'A': return ANDROID_LOG_FATAL;
    case BOOLEAN_FALSE: /* FALLTHRU */ /* Not Officially supported */
    case 'S': return -1; /* ANDROID_LOG_SUPPRESS */
    }
    return default_prio;
}

LIBLOG_ABI_PUBLIC int __android_log_is_loggable(int prio, const char *tag,
                                                int default_prio)
{
    int logLevel = __android_log_level(tag, default_prio);
    return logLevel >= 0 && prio >= logLevel;
}

LIBLOG_HIDDEN int __android_log_is_debuggable()
{
    static uint32_t serial;
    static struct cache tag_cache;
    static const char key[] = "ro.debuggable";
    int ret;

    if (tag_cache.c) { /* ro property does not change after set */
        ret = tag_cache.c == '1';
    } else if (lock()) {
        struct cache temp_cache = { NULL, -1, '\0' };
        refresh_cache(&temp_cache, key);
        ret = temp_cache.c == '1';
    } else {
        int change_detected = check_cache(&tag_cache);
        uint32_t current_serial = __system_property_area_serial();
        if (current_serial != serial) {
            change_detected = 1;
        }
        if (change_detected) {
            refresh_cache(&tag_cache, key);
            serial = current_serial;
        }
        ret = tag_cache.c == '1';

        unlock();
    }

    return ret;
}

/*
 * For properties that are read often, but generally remain constant.
 * Since a change is rare, we will accept a trylock failure gracefully.
 * Use a separate lock from is_loggable to keep contention down b/25563384.
 */
struct cache2 {
    pthread_mutex_t lock;
    uint32_t serial;
    const char *key_persist;
    struct cache cache_persist;
    const char *key_ro;
    struct cache cache_ro;
    unsigned char (*const evaluate)(const struct cache2 *self);
};

static inline unsigned char do_cache2(struct cache2 *self)
{
    uint32_t current_serial;
    int change_detected;
    unsigned char c;

    if (pthread_mutex_trylock(&self->lock)) {
        /* We are willing to accept some race in this context */
        return self->evaluate(self);
    }

    change_detected = check_cache(&self->cache_persist)
                   || check_cache(&self->cache_ro);
    current_serial = __system_property_area_serial();
    if (current_serial != self->serial) {
        change_detected = 1;
    }
    if (change_detected) {
        refresh_cache(&self->cache_persist, self->key_persist);
        refresh_cache(&self->cache_ro, self->key_ro);
        self->serial = current_serial;
    }
    c = self->evaluate(self);

    pthread_mutex_unlock(&self->lock);

    return c;
}

static unsigned char evaluate_persist_ro(const struct cache2 *self)
{
    unsigned char c = self->cache_persist.c;

    if (c) {
        return c;
    }

    return self->cache_ro.c;
}

/*
 * Timestamp state generally remains constant, but can change at any time
 * to handle developer requirements.
 */
LIBLOG_ABI_PUBLIC clockid_t android_log_clockid()
{
    static struct cache2 clockid = {
        PTHREAD_MUTEX_INITIALIZER,
        0,
        "persist.logd.timestamp",
        { NULL, -1, '\0' },
        "ro.logd.timestamp",
        { NULL, -1, '\0' },
        evaluate_persist_ro
    };

    return (tolower(do_cache2(&clockid)) == 'm')
        ? CLOCK_MONOTONIC
        : CLOCK_REALTIME;
}

/*
 * Security state generally remains constant, but the DO must be able
 * to turn off logging should it become spammy after an attack is detected.
 */
static unsigned char evaluate_security(const struct cache2 *self)
{
    unsigned char c = self->cache_ro.c;

    return (c != BOOLEAN_FALSE) && c && (self->cache_persist.c == BOOLEAN_TRUE);
}

LIBLOG_ABI_PUBLIC int __android_log_security()
{
    static struct cache2 security = {
        PTHREAD_MUTEX_INITIALIZER,
        0,
        "persist.logd.security",
        { NULL, -1, BOOLEAN_FALSE },
        "ro.device_owner",
        { NULL, -1, BOOLEAN_FALSE },
        evaluate_security
    };

    return do_cache2(&security);
}
