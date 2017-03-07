/*
 * Copyright (C) 2010 The Android Open Source Project
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

#define LOG_TAG "AsynchronousCloseMonitor"

#include "AsynchronousCloseMonitor.h"
#include "cutils/log.h"

#include <errno.h>
#include <signal.h>
#include <string.h>

/**
 * We use an intrusive doubly-linked list to keep track of blocked threads.
 * This gives us O(1) insertion and removal, and means we don't need to do any allocation.
 * (The objects themselves are stack-allocated.)
 * Waking potentially-blocked threads when a file descriptor is closed is O(n) in the total number
 * of blocked threads (not the number of threads actually blocked on the file descriptor in
 * question). For now at least, this seems like a good compromise for Android.
 */
static pthread_mutex_t blockedThreadListMutex = PTHREAD_MUTEX_INITIALIZER;
static AsynchronousCloseMonitor* blockedThreadList = NULL;

/**
 * The specific signal chosen here is arbitrary, but bionic needs to know so that SIGRTMIN
 * starts at a higher value.
 */
static const int BLOCKED_THREAD_SIGNAL = __SIGRTMIN + 2;

static void blockedThreadSignalHandler(int /*signal*/) {
    // Do nothing. We only sent this signal for its side-effect of interrupting syscalls.
}

void AsynchronousCloseMonitor::init() {
    // Ensure that the signal we send interrupts system calls but doesn't kill threads.
    // Using sigaction(2) lets us ensure that the SA_RESTART flag is not set.
    // (The whole reason we're sending this signal is to unblock system calls!)
    struct sigaction sa;
    memset(&sa, 0, sizeof(sa));
    sa.sa_handler = blockedThreadSignalHandler;
    sa.sa_flags = 0;
    int rc = sigaction(BLOCKED_THREAD_SIGNAL, &sa, NULL);
    if (rc == -1) {
        ALOGE("setting blocked thread signal handler failed: %s", strerror(errno));
    }
}

void AsynchronousCloseMonitor::signalBlockedThreads(int fd) {
    ScopedPthreadMutexLock lock(&blockedThreadListMutex);
    for (AsynchronousCloseMonitor* it = blockedThreadList; it != NULL; it = it->mNext) {
        if (it->mFd == fd) {
            it->mSignaled = true;
            pthread_kill(it->mThread, BLOCKED_THREAD_SIGNAL);
            // Keep going, because there may be more than one thread...
        }
    }
}

bool AsynchronousCloseMonitor::wasSignaled() const {
    return mSignaled;
}

AsynchronousCloseMonitor::AsynchronousCloseMonitor(int fd) {
    ScopedPthreadMutexLock lock(&blockedThreadListMutex);
    // Who are we, and what are we waiting for?
    mThread = pthread_self();
    mFd = fd;
    mSignaled = false;
    // Insert ourselves at the head of the intrusive doubly-linked list...
    mPrev = NULL;
    mNext = blockedThreadList;
    if (mNext != NULL) {
        mNext->mPrev = this;
    }
    blockedThreadList = this;
}

AsynchronousCloseMonitor::~AsynchronousCloseMonitor() {
    ScopedPthreadMutexLock lock(&blockedThreadListMutex);
    // Unlink ourselves from the intrusive doubly-linked list...
    if (mNext != NULL) {
        mNext->mPrev = mPrev;
    }
    if (mPrev == NULL) {
        blockedThreadList = mNext;
    } else {
        mPrev->mNext = mNext;
    }
}
