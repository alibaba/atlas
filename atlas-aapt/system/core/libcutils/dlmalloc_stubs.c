/*
 * Copyright (C) 2007 The Android Open Source Project
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

#include "log/log.h"

#define UNUSED __attribute__((__unused__))

/*
 * Stubs for functions defined in bionic/libc/bionic/dlmalloc.c. These
 * are used in host builds, as the host libc will not contain these
 * functions.
 */
void dlmalloc_inspect_all(void(*handler)(void*, void *, size_t, void*) UNUSED,
                          void* arg UNUSED)
{
  ALOGW("Called host unimplemented stub: dlmalloc_inspect_all");
}

int dlmalloc_trim(size_t unused UNUSED)
{
  ALOGW("Called host unimplemented stub: dlmalloc_trim");
  return 0;
}
