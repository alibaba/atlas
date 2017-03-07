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

#include "config_write.h"
#include "logger.h"

LIBLOG_HIDDEN struct listnode __android_log_transport_write =
    { &__android_log_transport_write, &__android_log_transport_write };
LIBLOG_HIDDEN struct listnode __android_log_persist_write =
    { &__android_log_persist_write, &__android_log_persist_write};

static void __android_log_add_transport(
        struct listnode *list, struct android_log_transport_write *transport) {
    size_t i;

    /* Try to keep one functioning transport for each log buffer id */
    for (i = LOG_ID_MIN; i < LOG_ID_MAX; i++) {
        struct android_log_transport_write *transp;

        if (list_empty(list)) {
            if (!transport->available || ((*transport->available)(i) >= 0)) {
                list_add_tail(list, &transport->node);
                return;
            }
        } else {
            write_transport_for_each(transp, list) {
                if (!transp->available) {
                    return;
                }
                if (((*transp->available)(i) < 0) &&
                        (!transport->available ||
                            ((*transport->available)(i) >= 0))) {
                    list_add_tail(list, &transport->node);
                    return;
                }
            }
        }
    }
}

LIBLOG_HIDDEN void __android_log_config_write() {
#if (FAKE_LOG_DEVICE == 0)
    extern struct android_log_transport_write logdLoggerWrite;
    extern struct android_log_transport_write pmsgLoggerWrite;

    __android_log_add_transport(&__android_log_transport_write, &logdLoggerWrite);
    __android_log_add_transport(&__android_log_persist_write, &pmsgLoggerWrite);
#else
    extern struct android_log_transport_write fakeLoggerWrite;

    __android_log_add_transport(&__android_log_transport_write, &fakeLoggerWrite);
#endif
}
