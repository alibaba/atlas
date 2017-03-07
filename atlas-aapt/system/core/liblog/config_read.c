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

#include "config_read.h"
#include "logger.h"

LIBLOG_HIDDEN struct listnode __android_log_transport_read =
    { &__android_log_transport_read, &__android_log_transport_read };
LIBLOG_HIDDEN struct listnode __android_log_persist_read =
    { &__android_log_persist_read, &__android_log_persist_read };

static void __android_log_add_transport(
        struct listnode *list, struct android_log_transport_read *transport) {
    size_t i;

    /* Try to keep one functioning transport for each log buffer id */
    for (i = LOG_ID_MIN; i < LOG_ID_MAX; i++) {
        struct android_log_transport_read *transp;

        if (list_empty(list)) {
            if (!transport->available || ((*transport->available)(i) >= 0)) {
                list_add_tail(list, &transport->node);
                return;
            }
        } else {
            read_transport_for_each(transp, list) {
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

LIBLOG_HIDDEN void __android_log_config_read() {
#if (FAKE_LOG_DEVICE == 0)
    extern struct android_log_transport_read logdLoggerRead;
    extern struct android_log_transport_read pmsgLoggerRead;

    __android_log_add_transport(&__android_log_transport_read, &logdLoggerRead);
    __android_log_add_transport(&__android_log_persist_read, &pmsgLoggerRead);
#endif
}
