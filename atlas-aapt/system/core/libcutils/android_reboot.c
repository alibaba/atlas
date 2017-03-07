/*
 * Copyright 2011, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <errno.h>
#include <fcntl.h>
#include <mntent.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/cdefs.h>
#include <sys/mount.h>
#include <sys/reboot.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <sys/types.h>
#include <unistd.h>

#include <cutils/android_reboot.h>
#include <cutils/klog.h>
#include <cutils/list.h>

#define TAG "android_reboot"
#define READONLY_CHECK_MS 5000
#define READONLY_CHECK_TIMES 50

typedef struct {
    struct listnode list;
    struct mntent entry;
} mntent_list;

static bool has_mount_option(const char* opts, const char* opt_to_find)
{
  bool ret = false;
  char* copy = NULL;
  char* opt;
  char* rem;

  while ((opt = strtok_r(copy ? NULL : (copy = strdup(opts)), ",", &rem))) {
      if (!strcmp(opt, opt_to_find)) {
          ret = true;
          break;
      }
  }

  free(copy);
  return ret;
}

static bool is_block_device(const char* fsname)
{
    return !strncmp(fsname, "/dev/block", 10);
}

/* Find all read+write block devices in /proc/mounts and add them to
 * |rw_entries|.
 */
static void find_rw(struct listnode* rw_entries)
{
    FILE* fp;
    struct mntent* mentry;

    if ((fp = setmntent("/proc/mounts", "r")) == NULL) {
        KLOG_WARNING(TAG, "Failed to open /proc/mounts.\n");
        return;
    }
    while ((mentry = getmntent(fp)) != NULL) {
        if (is_block_device(mentry->mnt_fsname) &&
            has_mount_option(mentry->mnt_opts, "rw")) {
            mntent_list* item = (mntent_list*)calloc(1, sizeof(mntent_list));
            item->entry = *mentry;
            item->entry.mnt_fsname = strdup(mentry->mnt_fsname);
            item->entry.mnt_dir = strdup(mentry->mnt_dir);
            item->entry.mnt_type = strdup(mentry->mnt_type);
            item->entry.mnt_opts = strdup(mentry->mnt_opts);
            list_add_tail(rw_entries, &item->list);
        }
    }
    endmntent(fp);
}

static void free_entries(struct listnode* entries)
{
    struct listnode* node;
    struct listnode* n;
    list_for_each_safe(node, n, entries) {
        mntent_list* item = node_to_item(node, mntent_list, list);
        free(item->entry.mnt_fsname);
        free(item->entry.mnt_dir);
        free(item->entry.mnt_type);
        free(item->entry.mnt_opts);
        free(item);
    }
}

static mntent_list* find_item(struct listnode* rw_entries, const char* fsname_to_find)
{
    struct listnode* node;
    list_for_each(node, rw_entries) {
        mntent_list* item = node_to_item(node, mntent_list, list);
        if (!strcmp(item->entry.mnt_fsname, fsname_to_find)) {
            return item;
        }
    }
    return NULL;
}

/* Remounting filesystems read-only is difficult when there are files
 * opened for writing or pending deletes on the filesystem.  There is
 * no way to force the remount with the mount(2) syscall.  The magic sysrq
 * 'u' command does an emergency remount read-only on all writable filesystems
 * that have a block device (i.e. not tmpfs filesystems) by calling
 * emergency_remount(), which knows how to force the remount to read-only.
 * Unfortunately, that is asynchronous, and just schedules the work and
 * returns.  The best way to determine if it is done is to read /proc/mounts
 * repeatedly until there are no more writable filesystems mounted on
 * block devices.
 */
static void remount_ro(void (*cb_on_remount)(const struct mntent*))
{
    int fd, cnt;
    FILE* fp;
    struct mntent* mentry;
    struct listnode* node;

    list_declare(rw_entries);
    list_declare(ro_entries);

    sync();
    find_rw(&rw_entries);

    /* Trigger the remount of the filesystems as read-only,
     * which also marks them clean.
     */
    fd = TEMP_FAILURE_RETRY(open("/proc/sysrq-trigger", O_WRONLY));
    if (fd < 0) {
        KLOG_WARNING(TAG, "Failed to open sysrq-trigger.\n");
        /* TODO: Try to remount each rw parition manually in readonly mode.
         * This may succeed if no process is using the partition.
         */
        goto out;
    }
    if (TEMP_FAILURE_RETRY(write(fd, "u", 1)) != 1) {
        close(fd);
        KLOG_WARNING(TAG, "Failed to write to sysrq-trigger.\n");
        /* TODO: The same. Manually remount the paritions. */
        goto out;
    }
    close(fd);

    /* Now poll /proc/mounts till it's done */
    cnt = 0;
    while (cnt < READONLY_CHECK_TIMES) {
        if ((fp = setmntent("/proc/mounts", "r")) == NULL) {
            /* If we can't read /proc/mounts, just give up. */
            KLOG_WARNING(TAG, "Failed to open /proc/mounts.\n");
            goto out;
        }
        while ((mentry = getmntent(fp)) != NULL) {
            if (!is_block_device(mentry->mnt_fsname) ||
                !has_mount_option(mentry->mnt_opts, "ro")) {
                continue;
            }
            mntent_list* item = find_item(&rw_entries, mentry->mnt_fsname);
            if (item) {
                /* |item| has now been ro remounted. */
                list_remove(&item->list);
                list_add_tail(&ro_entries, &item->list);
            }
        }
        endmntent(fp);
        if (list_empty(&rw_entries)) {
            /* All rw block devices are now readonly. */
            break;
        }
        TEMP_FAILURE_RETRY(
            usleep(READONLY_CHECK_MS * 1000 / READONLY_CHECK_TIMES));
        cnt++;
    }

    list_for_each(node, &rw_entries) {
        mntent_list* item = node_to_item(node, mntent_list, list);
        KLOG_WARNING(TAG, "Failed to remount %s in readonly mode.\n",
                     item->entry.mnt_fsname);
    }

    if (cb_on_remount) {
        list_for_each(node, &ro_entries) {
            mntent_list* item = node_to_item(node, mntent_list, list);
            cb_on_remount(&item->entry);
        }
    }

out:
    free_entries(&rw_entries);
    free_entries(&ro_entries);
}

int android_reboot_with_callback(
    int cmd, int flags __unused, const char *arg,
    void (*cb_on_remount)(const struct mntent*))
{
    int ret;
    remount_ro(cb_on_remount);
    switch (cmd) {
        case ANDROID_RB_RESTART:
            ret = reboot(RB_AUTOBOOT);
            break;

        case ANDROID_RB_POWEROFF:
            ret = reboot(RB_POWER_OFF);
            break;

        case ANDROID_RB_RESTART2:
            ret = syscall(__NR_reboot, LINUX_REBOOT_MAGIC1, LINUX_REBOOT_MAGIC2,
                           LINUX_REBOOT_CMD_RESTART2, arg);
            break;

        default:
            ret = -1;
    }

    return ret;
}

int android_reboot(int cmd, int flags, const char *arg)
{
    return android_reboot_with_callback(cmd, flags, arg, NULL);
}
