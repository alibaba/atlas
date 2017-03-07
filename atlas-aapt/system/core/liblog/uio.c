/*
 * Copyright (C) 2007-2014 The Android Open Source Project
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

#if defined(_WIN32)

#include <unistd.h>

#include <log/uio.h>

#include "log_portability.h"

LIBLOG_ABI_PUBLIC int readv(int fd, struct iovec *vecs, int count)
{
    int   total = 0;

    for ( ; count > 0; count--, vecs++ ) {
        char*  buf = vecs->iov_base;
        int    len = vecs->iov_len;

        while (len > 0) {
            int  ret = read( fd, buf, len );
            if (ret < 0) {
                if (total == 0)
                    total = -1;
                goto Exit;
            }
            if (ret == 0)
                goto Exit;

            total += ret;
            buf   += ret;
            len   -= ret;
        }
    }
Exit:
    return total;
}

LIBLOG_ABI_PUBLIC int writev(int fd, const struct iovec *vecs, int count)
{
    int   total = 0;

    for ( ; count > 0; count--, vecs++ ) {
        const char*  buf = vecs->iov_base;
        int          len = vecs->iov_len;

        while (len > 0) {
            int  ret = write( fd, buf, len );
            if (ret < 0) {
                if (total == 0)
                    total = -1;
                goto Exit;
            }
            if (ret == 0)
                goto Exit;

            total += ret;
            buf   += ret;
            len   -= ret;
        }
    }
Exit:
    return total;
}

#endif
