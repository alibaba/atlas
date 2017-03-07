/*
** Copyright 2011, The Android Open Source Project
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

// #define LOG_NDEBUG 0

#define LOG_TAG "qtaguid"

#include <errno.h>
#include <fcntl.h>
#include <inttypes.h>
#include <pthread.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>

#include <cutils/qtaguid.h>
#include <log/log.h>

static const char* CTRL_PROCPATH = "/proc/net/xt_qtaguid/ctrl";
static const int CTRL_MAX_INPUT_LEN = 128;
static const char *GLOBAL_PACIFIER_PARAM = "/sys/module/xt_qtaguid/parameters/passive";
static const char *TAG_PACIFIER_PARAM = "/sys/module/xt_qtaguid/parameters/tag_tracking_passive";

/*
 * One per proccess.
 * Once the device is open, this process will have its socket tags tracked.
 * And on exit or untimely death, all socket tags will be removed.
 * A process can only open /dev/xt_qtaguid once.
 * It should not close it unless it is really done with all the socket tags.
 * Failure to open it will be visible when socket tagging will be attempted.
 */
static int resTrackFd = -1;
pthread_once_t resTrackInitDone = PTHREAD_ONCE_INIT;

/* Only call once per process. */
void qtaguid_resTrack(void) {
    resTrackFd = TEMP_FAILURE_RETRY(open("/dev/xt_qtaguid", O_RDONLY));
    if (resTrackFd >=0) {
        TEMP_FAILURE_RETRY(fcntl(resTrackFd, F_SETFD, FD_CLOEXEC));
    }
}

/*
 * Returns:
 *   0 on success.
 *   -errno on failure.
 */
static int write_ctrl(const char *cmd) {
    int fd, res, savedErrno;

    ALOGV("write_ctrl(%s)", cmd);

    fd = TEMP_FAILURE_RETRY(open(CTRL_PROCPATH, O_WRONLY));
    if (fd < 0) {
        return -errno;
    }

    res = TEMP_FAILURE_RETRY(write(fd, cmd, strlen(cmd)));
    if (res < 0) {
        savedErrno = errno;
    } else {
        savedErrno = 0;
    }
    if (res < 0) {
        // ALOGV is enough because all the callers also log failures
        ALOGV("Failed write_ctrl(%s) res=%d errno=%d", cmd, res, savedErrno);
    }
    close(fd);
    return -savedErrno;
}

static int write_param(const char *param_path, const char *value) {
    int param_fd;
    int res;

    param_fd = TEMP_FAILURE_RETRY(open(param_path, O_WRONLY));
    if (param_fd < 0) {
        return -errno;
    }
    res = TEMP_FAILURE_RETRY(write(param_fd, value, strlen(value)));
    if (res < 0) {
        return -errno;
    }
    close(param_fd);
    return 0;
}

int qtaguid_tagSocket(int sockfd, int tag, uid_t uid) {
    char lineBuf[CTRL_MAX_INPUT_LEN];
    int res;
    uint64_t kTag = ((uint64_t)tag << 32);

    pthread_once(&resTrackInitDone, qtaguid_resTrack);

    snprintf(lineBuf, sizeof(lineBuf), "t %d %" PRIu64 " %d", sockfd, kTag, uid);

    ALOGV("Tagging socket %d with tag %" PRIx64 "{%u,0} for uid %d", sockfd, kTag, tag, uid);

    res = write_ctrl(lineBuf);
    if (res < 0) {
        ALOGI("Tagging socket %d with tag %" PRIx64 "(%d) for uid %d failed errno=%d",
             sockfd, kTag, tag, uid, res);
    }

    return res;
}

int qtaguid_untagSocket(int sockfd) {
    char lineBuf[CTRL_MAX_INPUT_LEN];
    int res;

    ALOGV("Untagging socket %d", sockfd);

    snprintf(lineBuf, sizeof(lineBuf), "u %d", sockfd);
    res = write_ctrl(lineBuf);
    if (res < 0) {
        ALOGI("Untagging socket %d failed errno=%d", sockfd, res);
    }

    return res;
}

int qtaguid_setCounterSet(int counterSetNum, uid_t uid) {
    char lineBuf[CTRL_MAX_INPUT_LEN];
    int res;

    ALOGV("Setting counters to set %d for uid %d", counterSetNum, uid);

    snprintf(lineBuf, sizeof(lineBuf), "s %d %d", counterSetNum, uid);
    res = write_ctrl(lineBuf);
    return res;
}

int qtaguid_deleteTagData(int tag, uid_t uid) {
    char lineBuf[CTRL_MAX_INPUT_LEN];
    int cnt = 0, res = 0;
    uint64_t kTag = (uint64_t)tag << 32;

    ALOGV("Deleting tag data with tag %" PRIx64 "{%d,0} for uid %d", kTag, tag, uid);

    pthread_once(&resTrackInitDone, qtaguid_resTrack);

    snprintf(lineBuf, sizeof(lineBuf), "d %" PRIu64 " %d", kTag, uid);
    res = write_ctrl(lineBuf);
    if (res < 0) {
        ALOGI("Deleting tag data with tag %" PRIx64 "/%d for uid %d failed with cnt=%d errno=%d",
             kTag, tag, uid, cnt, errno);
    }

    return res;
}

int qtaguid_setPacifier(int on) {
    const char *value;

    value = on ? "Y" : "N";
    if (write_param(GLOBAL_PACIFIER_PARAM, value) < 0) {
        return -errno;
    }
    if (write_param(TAG_PACIFIER_PARAM, value) < 0) {
        return -errno;
    }
    return 0;
}
