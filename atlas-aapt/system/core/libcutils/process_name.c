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

#include <fcntl.h>
#include <stdlib.h>
#include <string.h>
#if defined(__linux__)
#include <sys/prctl.h>
#endif
#include <sys/stat.h>
#include <sys/types.h>
#include <unistd.h>

#include <cutils/process_name.h>
#if defined(__ANDROID__)
#include <cutils/properties.h>
#endif

#define PROCESS_NAME_DEVICE "/sys/qemu_trace/process_name"

static const char* process_name = "unknown";
#if defined(__ANDROID__)
static int running_in_emulator = -1;
#endif

void set_process_name(const char* new_name) {
#if defined(__ANDROID__)
    char  propBuf[PROPERTY_VALUE_MAX];
#endif

    if (new_name == NULL) {
        return;
    }

    // We never free the old name. Someone else could be using it.
    int len = strlen(new_name);
    char* copy = (char*) malloc(len + 1);
    strcpy(copy, new_name);
    process_name = (const char*) copy;

#if defined(__linux__)
    if (len < 16) {
        prctl(PR_SET_NAME, (unsigned long) new_name, 0, 0, 0);
    } else {
        prctl(PR_SET_NAME, (unsigned long) new_name + len - 15, 0, 0, 0);
    }
#endif

#if defined(__ANDROID__)
    // If we know we are not running in the emulator, then return.
    if (running_in_emulator == 0) {
        return;
    }

    // If the "running_in_emulator" variable has not been initialized,
    // then do it now.
    if (running_in_emulator == -1) {
        property_get("ro.kernel.qemu", propBuf, "");
        if (propBuf[0] == '1') {
            running_in_emulator = 1;
        } else {
            running_in_emulator = 0;
            return;
        }
    }

    // If the emulator was started with the "-trace file" command line option
    // then we want to record the process name in the trace even if we are
    // not currently tracing instructions (so that we will know the process
    // name when we do start tracing instructions).  We do not need to execute
    // this code if we are just running in the emulator without the "-trace"
    // command line option, but we don't know that here and this function
    // isn't called frequently enough to bother optimizing that case.
    int fd = open(PROCESS_NAME_DEVICE, O_RDWR);
    if (fd < 0)
        return;
    write(fd, process_name, strlen(process_name) + 1);
    close(fd);
#endif
}

const char* get_process_name(void) {
    return process_name;
}
