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

/*
 * this header is used to define signal constants and names;
 * it might be included several times
 */

#ifndef __BIONIC_SIGDEF
#error __BIONIC_SIGDEF not defined
#endif

__BIONIC_SIGDEF(SIGHUP,    "Hangup")
__BIONIC_SIGDEF(SIGINT,    "Interrupt")
__BIONIC_SIGDEF(SIGQUIT,   "Quit")
__BIONIC_SIGDEF(SIGILL,    "Illegal instruction")
__BIONIC_SIGDEF(SIGTRAP,   "Trap")
__BIONIC_SIGDEF(SIGABRT,   "Aborted")
#ifdef SIGEMT
__BIONIC_SIGDEF(SIGEMT,    "EMT")
#endif
__BIONIC_SIGDEF(SIGFPE,    "Floating point exception")
__BIONIC_SIGDEF(SIGKILL,   "Killed")
__BIONIC_SIGDEF(SIGBUS,    "Bus error")
__BIONIC_SIGDEF(SIGSEGV,   "Segmentation fault")
__BIONIC_SIGDEF(SIGPIPE,   "Broken pipe")
__BIONIC_SIGDEF(SIGALRM,   "Alarm clock")
__BIONIC_SIGDEF(SIGTERM,   "Terminated")
__BIONIC_SIGDEF(SIGUSR1,   "User signal 1")
__BIONIC_SIGDEF(SIGUSR2,   "User signal 2")
__BIONIC_SIGDEF(SIGCHLD,   "Child exited")
__BIONIC_SIGDEF(SIGPWR,    "Power failure")
__BIONIC_SIGDEF(SIGWINCH,  "Window size changed")
__BIONIC_SIGDEF(SIGURG,    "Urgent I/O condition")
__BIONIC_SIGDEF(SIGIO,     "I/O possible")
__BIONIC_SIGDEF(SIGSTOP,   "Stopped (signal)")
__BIONIC_SIGDEF(SIGTSTP,   "Stopped")
__BIONIC_SIGDEF(SIGCONT,   "Continue")
__BIONIC_SIGDEF(SIGTTIN,   "Stopped (tty input)")
__BIONIC_SIGDEF(SIGTTOU,   "Stopped (tty output)")
__BIONIC_SIGDEF(SIGVTALRM, "Virtual timer expired")
__BIONIC_SIGDEF(SIGPROF,   "Profiling timer expired")
__BIONIC_SIGDEF(SIGXCPU,   "CPU time limit exceeded")
__BIONIC_SIGDEF(SIGXFSZ,   "File size limit exceeded")
#if defined(SIGSTKFLT)
__BIONIC_SIGDEF(SIGSTKFLT, "Stack fault")
#endif
__BIONIC_SIGDEF(SIGSYS,    "Bad system call")

#undef __BIONIC_SIGDEF
