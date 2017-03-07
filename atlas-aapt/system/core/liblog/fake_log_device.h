/*
 * Copyright (C) 2013 The Android Open Source Project
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

#ifndef _LIBLOG_FAKE_LOG_DEVICE_H
#define _LIBLOG_FAKE_LOG_DEVICE_H

#include <sys/types.h>

#include "log_portability.h"

struct iovec;

LIBLOG_HIDDEN int fakeLogOpen(const char *pathName, int flags);
LIBLOG_HIDDEN int fakeLogClose(int fd);
LIBLOG_HIDDEN ssize_t fakeLogWritev(int fd,
                                    const struct iovec* vector, int count);

#endif // _LIBLOG_FAKE_LOG_DEVICE_H
