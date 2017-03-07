/*
 * Copyright (C) 2012 The Android Open Source Project
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

#include <stdbool.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <unistd.h>

#include <cutils/debugger.h>
#include <cutils/sockets.h>

#define LOG_TAG "DEBUG"
#include <log/log.h>

static int send_request(int sock_fd, void* msg_ptr, size_t msg_len) {
  int result = 0;
  if (TEMP_FAILURE_RETRY(write(sock_fd, msg_ptr, msg_len)) != (ssize_t) msg_len) {
    result = -1;
  } else {
    char ack;
    if (TEMP_FAILURE_RETRY(read(sock_fd, &ack, 1)) != 1) {
      result = -1;
    }
  }
  return result;
}

static int make_dump_request(debugger_action_t action, pid_t tid, int timeout_secs) {
  debugger_msg_t msg;
  memset(&msg, 0, sizeof(msg));
  msg.tid = tid;
  msg.action = action;

  int sock_fd = socket_local_client(DEBUGGER_SOCKET_NAME, ANDROID_SOCKET_NAMESPACE_ABSTRACT,
      SOCK_STREAM | SOCK_CLOEXEC);
  if (sock_fd < 0) {
    return -1;
  }

  if (timeout_secs > 0) {
    struct timeval tm;
    tm.tv_sec = timeout_secs;
    tm.tv_usec = 0;
    if (setsockopt(sock_fd, SOL_SOCKET, SO_RCVTIMEO, &tm, sizeof(tm)) == -1) {
      ALOGE("WARNING: Cannot set receive timeout value on socket: %s", strerror(errno));
    }

    if (setsockopt(sock_fd, SOL_SOCKET, SO_SNDTIMEO, &tm, sizeof(tm)) == -1) {
      ALOGE("WARNING: Cannot set send timeout value on socket: %s", strerror(errno));
    }
  }

  if (send_request(sock_fd, &msg, sizeof(msg)) < 0) {
    close(sock_fd);
    return -1;
  }

  return sock_fd;
}

int dump_backtrace_to_file(pid_t tid, int fd) {
  return dump_backtrace_to_file_timeout(tid, fd, 0);
}

int dump_backtrace_to_file_timeout(pid_t tid, int fd, int timeout_secs) {
  int sock_fd = make_dump_request(DEBUGGER_ACTION_DUMP_BACKTRACE, tid, timeout_secs);
  if (sock_fd < 0) {
    return -1;
  }

  /* Write the data read from the socket to the fd. */
  int result = 0;
  char buffer[1024];
  ssize_t n;
  while ((n = TEMP_FAILURE_RETRY(read(sock_fd, buffer, sizeof(buffer)))) > 0) {
    if (TEMP_FAILURE_RETRY(write(fd, buffer, n)) != n) {
      result = -1;
      break;
    }
  }
  close(sock_fd);
  return result;
}

int dump_tombstone(pid_t tid, char* pathbuf, size_t pathlen) {
  return dump_tombstone_timeout(tid, pathbuf, pathlen, 0);
}

int dump_tombstone_timeout(pid_t tid, char* pathbuf, size_t pathlen, int timeout_secs) {
  int sock_fd = make_dump_request(DEBUGGER_ACTION_DUMP_TOMBSTONE, tid, timeout_secs);
  if (sock_fd < 0) {
    return -1;
  }

  /* Read the tombstone file name. */
  char buffer[100]; /* This is larger than the largest tombstone path. */
  int result = 0;
  ssize_t n = TEMP_FAILURE_RETRY(read(sock_fd, buffer, sizeof(buffer) - 1));
  if (n <= 0) {
    result = -1;
  } else {
    if (pathbuf && pathlen) {
      if (n >= (ssize_t) pathlen) {
        n = pathlen - 1;
      }
      buffer[n] = '\0';
      memcpy(pathbuf, buffer, n + 1);
    }
  }
  close(sock_fd);
  return result;
}
