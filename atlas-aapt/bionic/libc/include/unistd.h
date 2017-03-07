/*
 * Copyright (C) 2008 The Android Open Source Project
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *  * Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *  * Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS
 * OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 * OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 */

#ifndef _UNISTD_H_
#define _UNISTD_H_

#include <stddef.h>
#include <sys/cdefs.h>
#include <sys/types.h>
#include <sys/select.h>
#include <sys/sysconf.h>

#include <bits/lockf.h>
#include <bits/posix_limits.h>

__BEGIN_DECLS

#define STDIN_FILENO	0
#define STDOUT_FILENO	1
#define STDERR_FILENO	2

#define F_OK 0
#define X_OK 1
#define W_OK 2
#define R_OK 4

#define SEEK_SET 0
#define SEEK_CUR 1
#define SEEK_END 2

#define _PC_FILESIZEBITS 0
#define _PC_LINK_MAX 1
#define _PC_MAX_CANON 2
#define _PC_MAX_INPUT 3
#define _PC_NAME_MAX 4
#define _PC_PATH_MAX 5
#define _PC_PIPE_BUF 6
#define _PC_2_SYMLINKS 7
#define _PC_ALLOC_SIZE_MIN 8
#define _PC_REC_INCR_XFER_SIZE 9
#define _PC_REC_MAX_XFER_SIZE 10
#define _PC_REC_MIN_XFER_SIZE 11
#define _PC_REC_XFER_ALIGN 12
#define _PC_SYMLINK_MAX 13
#define _PC_CHOWN_RESTRICTED 14
#define _PC_NO_TRUNC 15
#define _PC_VDISABLE 16
#define _PC_ASYNC_IO 17
#define _PC_PRIO_IO 18
#define _PC_SYNC_IO 19

extern char** environ;

extern __noreturn void _exit(int __status);

extern pid_t  fork(void);
extern pid_t  vfork(void);
extern pid_t  getpid(void);
extern pid_t  gettid(void) __pure2;
extern pid_t  getpgid(pid_t __pid);
extern int    setpgid(pid_t __pid, pid_t __pgid);
extern pid_t  getppid(void);
extern pid_t  getpgrp(void);
extern int    setpgrp(void);
extern pid_t  getsid(pid_t __pid) __INTRODUCED_IN(21);
extern pid_t  setsid(void);

extern int execv(const char* __path, char* const* __argv);
extern int execvp(const char* __file, char* const* __argv);
extern int execvpe(const char* __file, char* const* __argv, char* const* __envp)
  __INTRODUCED_IN(21);
extern int execve(const char* __file, char* const* __argv, char* const* __envp);
extern int execl(const char* __path, const char* __arg0, ...);
extern int execlp(const char* __file, const char* __arg0, ...);
extern int execle(const char* __path, const char* __arg0, ...);

extern int nice(int __incr);

extern int setuid(uid_t __uid);
extern uid_t getuid(void);
extern int seteuid(uid_t __uid);
extern uid_t geteuid(void);
extern int setgid(gid_t __gid);
extern gid_t getgid(void);
extern int setegid(gid_t __gid);
extern gid_t getegid(void);
extern int getgroups(int __size, gid_t* __list);
extern int setgroups(size_t __size, const gid_t* __list);
extern int setreuid(uid_t __ruid, uid_t __euid);
extern int setregid(gid_t __rgid, gid_t __egid);
extern int setresuid(uid_t __ruid, uid_t __euid, uid_t __suid);
extern int setresgid(gid_t __rgid, gid_t __egid, gid_t __sgid);
extern int getresuid(uid_t* __ruid, uid_t* __euid, uid_t* __suid);
extern int getresgid(gid_t* __rgid, gid_t* __egid, gid_t* __sgid);
extern char* getlogin(void);

extern long fpathconf(int __fd, int __name);
extern long pathconf(const char* __path, int __name);

extern int access(const char* __path, int __mode);
extern int faccessat(int __dirfd, const char* __path, int __mode, int __flags)
  __INTRODUCED_IN(21);
extern int link(const char* __oldpath, const char* __newpath);
extern int linkat(int __olddirfd, const char* __oldpath, int __newdirfd,
                  const char* __newpath, int __flags) __INTRODUCED_IN(21);
extern int unlink(const char* __path);
extern int unlinkat(int __dirfd, const char* __path, int __flags);
extern int chdir(const char* __path);
extern int fchdir(int __fd);
extern int rmdir(const char* __path);
extern int pipe(int* __pipefd);
#if defined(__USE_GNU)
extern int pipe2(int* __pipefd, int __flags) __INTRODUCED_IN(9);
#endif
extern int chroot(const char* __path);
extern int symlink(const char* __oldpath, const char* __newpath);
extern int symlinkat(const char* __oldpath, int __newdirfd,
                     const char* __newpath) __INTRODUCED_IN(21);
extern ssize_t readlink(const char* __path, char* __buf, size_t __bufsiz);
extern ssize_t readlinkat(int __dirfd, const char* __path, char* __buf,
                          size_t __bufsiz) __INTRODUCED_IN(21);
extern int chown(const char* __path, uid_t __owner, gid_t __group);
extern int fchown(int __fd, uid_t __owner, gid_t __group);
extern int fchownat(int __dirfd, const char* __path, uid_t __owner,
                    gid_t __group, int __flags);
extern int lchown(const char* __path, uid_t __owner, gid_t __group);
extern char* getcwd(char* __buf, size_t __size);

extern int sync(void);

extern int close(int __fd);

extern ssize_t read(int __fd, void* __buf, size_t __count);
extern ssize_t write(int __fd, const void* __buf, size_t __count);

extern int dup(int __oldfd);
extern int dup2(int __oldfd, int __newfd);
extern int dup3(int __oldfd, int __newfd, int __flags) __INTRODUCED_IN(21);
extern int fcntl(int __fd, int __cmd, ...);
extern int ioctl(int __fd, int __request, ...);
extern int fsync(int __fd);
extern int fdatasync(int __fd) __INTRODUCED_IN(9);

#if defined(__USE_FILE_OFFSET64)
extern off_t lseek(int __fd, off_t __offset, int __whence) __RENAME(lseek64);
#else
extern off_t lseek(int __fd, off_t __offset, int __whence);
#endif

extern off64_t lseek64(int __fd, off64_t __offset, int __whence);

#if defined(__USE_FILE_OFFSET64) && __ANDROID_API__ >= 21
extern int truncate(const char* __path, off_t __length) __RENAME(truncate64);
extern ssize_t pread(int __fd, void* __buf, size_t __count, off_t __offset)
  __RENAME(pread64);
extern ssize_t pwrite(int __fd, const void* __buf, size_t __count,
                      off_t __offset) __RENAME(pwrite64);
extern int ftruncate(int __fd, off_t __length) __RENAME(ftruncate64);
#else
extern int truncate(const char* __path, off_t __length);
extern ssize_t pread(int __fd, void* __buf, size_t __count, off_t __offset);
extern ssize_t pwrite(int __fd, const void* __buf, size_t __count,
                      off_t __offset);
extern int ftruncate(int __fd, off_t __length);
#endif

extern int truncate64(const char* __path, off64_t __length) __INTRODUCED_IN(21);
extern ssize_t pread64(int __fd, void* __buf, size_t __count, off64_t __offset) __INTRODUCED_IN(21);
extern ssize_t pwrite64(int __fd, const void* __buf, size_t __count,
                        off64_t __offset) __INTRODUCED_IN(21);
extern int ftruncate64(int __fd, off64_t __length) __INTRODUCED_IN(21);

extern int pause(void);
extern unsigned int alarm(unsigned int __seconds);
extern unsigned int sleep(unsigned int __seconds);
extern int usleep(useconds_t __usec);

int gethostname(char* __name, size_t __len);
int sethostname(const char* __name, size_t __len);

extern void* __brk(void* __addr);
extern int brk(void* __addr);
extern void* sbrk(ptrdiff_t __increment);

extern int getopt(int __argc, char* const* __argv, const char* __argstring);
extern char* optarg;
extern int optind, opterr, optopt;

extern int isatty(int __fd);
extern char* ttyname(int __fd);
extern int ttyname_r(int __fd, char* __buf, size_t __buflen) __INTRODUCED_IN(8);

extern int acct(const char* __filepath);

long sysconf(int __name);

#if __ANDROID_API__ >= 21
int getpagesize(void);
#else
__inline__ int getpagesize(void) {
  return sysconf(_SC_PAGESIZE);
}
#endif

long syscall(long __number, ...);

extern int daemon(int __nochdir, int __noclose);

#if defined(__arm__) || (defined(__mips__) && !defined(__LP64__))
extern int cacheflush(long __addr, long __nbytes, long __cache);
    /* __attribute__((deprecated("use __builtin___clear_cache instead"))); */
#endif

extern pid_t tcgetpgrp(int __fd);
extern int tcsetpgrp(int __fd, pid_t __pid);

/* Used to retry syscalls that can return EINTR. */
#define TEMP_FAILURE_RETRY(exp) ({         \
    __typeof__(exp) _rc;                   \
    do {                                   \
        _rc = (exp);                       \
    } while (_rc == -1 && errno == EINTR); \
    _rc; })

/* TODO(unified-headers): Factor out all the FORTIFY features. */
extern char* __getcwd_chk(char*, size_t, size_t);
__errordecl(__getcwd_dest_size_error, "getcwd called with size bigger than destination");
extern char* __getcwd_real(char*, size_t) __RENAME(getcwd);

extern ssize_t __pread_chk(int, void*, size_t, off_t, size_t);
__errordecl(__pread_dest_size_error, "pread called with size bigger than destination");
__errordecl(__pread_count_toobig_error, "pread called with count > SSIZE_MAX");
extern ssize_t __pread_real(int, void*, size_t, off_t) __RENAME(pread);

extern ssize_t __pread64_chk(int, void*, size_t, off64_t, size_t);
__errordecl(__pread64_dest_size_error, "pread64 called with size bigger than destination");
__errordecl(__pread64_count_toobig_error, "pread64 called with count > SSIZE_MAX");
extern ssize_t __pread64_real(int, void*, size_t, off64_t) __RENAME(pread64);

extern ssize_t __pwrite_chk(int, const void*, size_t, off_t, size_t);
__errordecl(__pwrite_dest_size_error, "pwrite called with size bigger than destination");
__errordecl(__pwrite_count_toobig_error, "pwrite called with count > SSIZE_MAX");
extern ssize_t __pwrite_real(int, const void*, size_t, off_t) __RENAME(pwrite);

extern ssize_t __pwrite64_chk(int, const void*, size_t, off64_t, size_t);
__errordecl(__pwrite64_dest_size_error, "pwrite64 called with size bigger than destination");
__errordecl(__pwrite64_count_toobig_error, "pwrite64 called with count > SSIZE_MAX");
extern ssize_t __pwrite64_real(int, const void*, size_t, off64_t) __RENAME(pwrite64);

extern ssize_t __read_chk(int, void*, size_t, size_t);
__errordecl(__read_dest_size_error, "read called with size bigger than destination");
__errordecl(__read_count_toobig_error, "read called with count > SSIZE_MAX");
extern ssize_t __read_real(int, void*, size_t) __RENAME(read);

extern ssize_t __write_chk(int, const void*, size_t, size_t);
__errordecl(__write_dest_size_error, "write called with size bigger than destination");
__errordecl(__write_count_toobig_error, "write called with count > SSIZE_MAX");
extern ssize_t __write_real(int, const void*, size_t) __RENAME(write);

extern ssize_t __readlink_chk(const char*, char*, size_t, size_t);
__errordecl(__readlink_dest_size_error, "readlink called with size bigger than destination");
__errordecl(__readlink_size_toobig_error, "readlink called with size > SSIZE_MAX");
extern ssize_t __readlink_real(const char*, char*, size_t) __RENAME(readlink);

extern ssize_t __readlinkat_chk(int dirfd, const char*, char*, size_t, size_t);
__errordecl(__readlinkat_dest_size_error, "readlinkat called with size bigger than destination");
__errordecl(__readlinkat_size_toobig_error, "readlinkat called with size > SSIZE_MAX");
extern ssize_t __readlinkat_real(int dirfd, const char*, char*, size_t) __RENAME(readlinkat);

#if defined(__BIONIC_FORTIFY)

__BIONIC_FORTIFY_INLINE
char* getcwd(char* buf, size_t size) {
    size_t bos = __bos(buf);

#if defined(__clang__)
    /*
     * Work around LLVM's incorrect __builtin_object_size implementation here
     * to avoid needing the workaround in the __getcwd_chk ABI forever.
     *
     * https://llvm.org/bugs/show_bug.cgi?id=23277
     */
    if (buf == NULL) {
        bos = __BIONIC_FORTIFY_UNKNOWN_SIZE;
    }
#else
    if (bos == __BIONIC_FORTIFY_UNKNOWN_SIZE) {
        return __getcwd_real(buf, size);
    }

    if (__builtin_constant_p(size) && (size > bos)) {
        __getcwd_dest_size_error();
    }

    if (__builtin_constant_p(size) && (size <= bos)) {
        return __getcwd_real(buf, size);
    }
#endif

    return __getcwd_chk(buf, size, bos);
}

#if defined(__USE_FILE_OFFSET64)
#define __PREAD_PREFIX(x) __pread64_ ## x
#else
#define __PREAD_PREFIX(x) __pread_ ## x
#endif

__BIONIC_FORTIFY_INLINE
ssize_t pread(int fd, void* buf, size_t count, off_t offset) {
    size_t bos = __bos0(buf);

#if !defined(__clang__)
    if (__builtin_constant_p(count) && (count > SSIZE_MAX)) {
        __PREAD_PREFIX(count_toobig_error)();
    }

    if (bos == __BIONIC_FORTIFY_UNKNOWN_SIZE) {
        return __PREAD_PREFIX(real)(fd, buf, count, offset);
    }

    if (__builtin_constant_p(count) && (count > bos)) {
        __PREAD_PREFIX(dest_size_error)();
    }

    if (__builtin_constant_p(count) && (count <= bos)) {
        return __PREAD_PREFIX(real)(fd, buf, count, offset);
    }
#endif

    return __PREAD_PREFIX(chk)(fd, buf, count, offset, bos);
}

__BIONIC_FORTIFY_INLINE
ssize_t pread64(int fd, void* buf, size_t count, off64_t offset) {
    size_t bos = __bos0(buf);

#if !defined(__clang__)
    if (__builtin_constant_p(count) && (count > SSIZE_MAX)) {
        __pread64_count_toobig_error();
    }

    if (bos == __BIONIC_FORTIFY_UNKNOWN_SIZE) {
        return __pread64_real(fd, buf, count, offset);
    }

    if (__builtin_constant_p(count) && (count > bos)) {
        __pread64_dest_size_error();
    }

    if (__builtin_constant_p(count) && (count <= bos)) {
        return __pread64_real(fd, buf, count, offset);
    }
#endif

    return __pread64_chk(fd, buf, count, offset, bos);
}

#if defined(__USE_FILE_OFFSET64)
#define __PWRITE_PREFIX(x) __pwrite64_ ## x
#else
#define __PWRITE_PREFIX(x) __pwrite_ ## x
#endif

__BIONIC_FORTIFY_INLINE
ssize_t pwrite(int fd, const void* buf, size_t count, off_t offset) {
    size_t bos = __bos0(buf);

#if !defined(__clang__)
    if (__builtin_constant_p(count) && (count > SSIZE_MAX)) {
        __PWRITE_PREFIX(count_toobig_error)();
    }

    if (bos == __BIONIC_FORTIFY_UNKNOWN_SIZE) {
        return __PWRITE_PREFIX(real)(fd, buf, count, offset);
    }

    if (__builtin_constant_p(count) && (count > bos)) {
        __PWRITE_PREFIX(dest_size_error)();
    }

    if (__builtin_constant_p(count) && (count <= bos)) {
        return __PWRITE_PREFIX(real)(fd, buf, count, offset);
    }
#endif

    return __PWRITE_PREFIX(chk)(fd, buf, count, offset, bos);
}

__BIONIC_FORTIFY_INLINE
ssize_t pwrite64(int fd, const void* buf, size_t count, off64_t offset) {
    size_t bos = __bos0(buf);

#if !defined(__clang__)
    if (__builtin_constant_p(count) && (count > SSIZE_MAX)) {
        __pwrite64_count_toobig_error();
    }

    if (bos == __BIONIC_FORTIFY_UNKNOWN_SIZE) {
        return __pwrite64_real(fd, buf, count, offset);
    }

    if (__builtin_constant_p(count) && (count > bos)) {
        __pwrite64_dest_size_error();
    }

    if (__builtin_constant_p(count) && (count <= bos)) {
        return __pwrite64_real(fd, buf, count, offset);
    }
#endif

    return __pwrite64_chk(fd, buf, count, offset, bos);
}

__BIONIC_FORTIFY_INLINE
ssize_t read(int fd, void* buf, size_t count) {
    size_t bos = __bos0(buf);

#if !defined(__clang__)
    if (__builtin_constant_p(count) && (count > SSIZE_MAX)) {
        __read_count_toobig_error();
    }

    if (bos == __BIONIC_FORTIFY_UNKNOWN_SIZE) {
        return __read_real(fd, buf, count);
    }

    if (__builtin_constant_p(count) && (count > bos)) {
        __read_dest_size_error();
    }

    if (__builtin_constant_p(count) && (count <= bos)) {
        return __read_real(fd, buf, count);
    }
#endif

    return __read_chk(fd, buf, count, bos);
}

__BIONIC_FORTIFY_INLINE
ssize_t write(int fd, const void* buf, size_t count) {
    size_t bos = __bos0(buf);

#if !defined(__clang__)
#if 0 /* work around a false positive due to a missed optimization */
    if (__builtin_constant_p(count) && (count > SSIZE_MAX)) {
        __write_count_toobig_error();
    }
#endif

    if (bos == __BIONIC_FORTIFY_UNKNOWN_SIZE) {
        return __write_real(fd, buf, count);
    }

    if (__builtin_constant_p(count) && (count > bos)) {
        __write_dest_size_error();
    }

    if (__builtin_constant_p(count) && (count <= bos)) {
        return __write_real(fd, buf, count);
    }
#endif

    return __write_chk(fd, buf, count, bos);
}

__BIONIC_FORTIFY_INLINE
ssize_t readlink(const char* path, char* buf, size_t size) {
    size_t bos = __bos(buf);

#if !defined(__clang__)
    if (__builtin_constant_p(size) && (size > SSIZE_MAX)) {
        __readlink_size_toobig_error();
    }

    if (bos == __BIONIC_FORTIFY_UNKNOWN_SIZE) {
        return __readlink_real(path, buf, size);
    }

    if (__builtin_constant_p(size) && (size > bos)) {
        __readlink_dest_size_error();
    }

    if (__builtin_constant_p(size) && (size <= bos)) {
        return __readlink_real(path, buf, size);
    }
#endif

    return __readlink_chk(path, buf, size, bos);
}

__BIONIC_FORTIFY_INLINE
ssize_t readlinkat(int dirfd, const char* path, char* buf, size_t size) {
    size_t bos = __bos(buf);

#if !defined(__clang__)
    if (__builtin_constant_p(size) && (size > SSIZE_MAX)) {
        __readlinkat_size_toobig_error();
    }

    if (bos == __BIONIC_FORTIFY_UNKNOWN_SIZE) {
        return __readlinkat_real(dirfd, path, buf, size);
    }

    if (__builtin_constant_p(size) && (size > bos)) {
        __readlinkat_dest_size_error();
    }

    if (__builtin_constant_p(size) && (size <= bos)) {
        return __readlinkat_real(dirfd, path, buf, size);
    }
#endif

    return __readlinkat_chk(dirfd, path, buf, size, bos);
}

#endif /* defined(__BIONIC_FORTIFY) */

__END_DECLS

#endif /* _UNISTD_H_ */
