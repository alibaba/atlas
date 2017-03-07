/*
 * Copyright (C) 2008 The Android Open Source Project
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
 * Implementation of the user-space ashmem API for the simulator, which lacks
 * an ashmem-enabled kernel. See ashmem-dev.c for the real ashmem-based version.
 */

#include <errno.h>
#include <fcntl.h>
#include <limits.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <time.h>
#include <unistd.h>

#include <cutils/ashmem.h>
#include <utils/Compat.h>

#ifndef __unused
#define __unused __attribute__((__unused__))
#endif

int ashmem_create_region(const char *ignored __unused, size_t size)
{
    char template[PATH_MAX];
    snprintf(template, sizeof(template), "/tmp/android-ashmem-%d-XXXXXXXXX", getpid());
    int fd = mkstemp(template);
    if (fd == -1) return -1;

    unlink(template);

    if (TEMP_FAILURE_RETRY(ftruncate(fd, size)) == -1) {
      close(fd);
      return -1;
    }

    return fd;
}

int ashmem_set_prot_region(int fd __unused, int prot __unused)
{
    return 0;
}

int ashmem_pin_region(int fd __unused, size_t offset __unused, size_t len __unused)
{
    return ASHMEM_NOT_PURGED;
}

int ashmem_unpin_region(int fd __unused, size_t offset __unused, size_t len __unused)
{
    return ASHMEM_IS_UNPINNED;
}

int ashmem_get_size_region(int fd)
{
    struct stat buf;
    int result = fstat(fd, &buf);
    if (result == -1) {
        return -1;
    }

    /*
     * Check if this is an "ashmem" region.
     * TODO: This is very hacky, and can easily break.
     * We need some reliable indicator.
     */
    if (!(buf.st_nlink == 0 && S_ISREG(buf.st_mode))) {
        errno = ENOTTY;
        return -1;
    }

    return buf.st_size;
}
