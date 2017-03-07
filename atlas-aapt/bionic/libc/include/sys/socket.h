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

#ifndef _SYS_SOCKET_H_
#define _SYS_SOCKET_H_

#include <sys/cdefs.h>
#include <sys/types.h>
#include <linux/socket.h>

#include <asm/fcntl.h>
#include <asm/socket.h>
#include <linux/sockios.h>
#include <linux/uio.h>
#include <linux/types.h>
#include <linux/compiler.h>

__BEGIN_DECLS

#define sockaddr_storage __kernel_sockaddr_storage
typedef unsigned short sa_family_t;

struct timespec;

#ifdef __mips__
#define SOCK_DGRAM      1
#define SOCK_STREAM     2
#else
#define SOCK_STREAM     1
#define SOCK_DGRAM      2
#endif
#define SOCK_RAW        3
#define SOCK_RDM        4
#define SOCK_SEQPACKET  5
#define SOCK_DCCP       6
#define SOCK_PACKET     10

#define SOCK_CLOEXEC O_CLOEXEC
#define SOCK_NONBLOCK O_NONBLOCK

enum {
  SHUT_RD = 0,
#define SHUT_RD         SHUT_RD
  SHUT_WR,
#define SHUT_WR         SHUT_WR
  SHUT_RDWR
#define SHUT_RDWR       SHUT_RDWR
};

struct sockaddr {
  sa_family_t sa_family;
  char sa_data[14];
};

struct linger {
  int l_onoff;
  int l_linger;
};

struct msghdr {
  void* msg_name;
  socklen_t msg_namelen;
  struct iovec* msg_iov;
  size_t msg_iovlen;
  void* msg_control;
  size_t msg_controllen;
  int msg_flags;
};

struct mmsghdr {
  struct msghdr msg_hdr;
  unsigned int msg_len;
};

struct cmsghdr {
  size_t cmsg_len;
  int cmsg_level;
  int cmsg_type;
};

#define CMSG_NXTHDR(mhdr, cmsg) __cmsg_nxthdr((mhdr), (cmsg))
#define CMSG_ALIGN(len) ( ((len)+sizeof(long)-1) & ~(sizeof(long)-1) )
#define CMSG_DATA(cmsg) (((unsigned char*)(cmsg) + CMSG_ALIGN(sizeof(struct cmsghdr))))
#define CMSG_SPACE(len) (CMSG_ALIGN(sizeof(struct cmsghdr)) + CMSG_ALIGN(len))
#define CMSG_LEN(len) (CMSG_ALIGN(sizeof(struct cmsghdr)) + (len))
#define CMSG_FIRSTHDR(msg) \
  ((msg)->msg_controllen >= sizeof(struct cmsghdr) \
   ? (struct cmsghdr*) (msg)->msg_control : (struct cmsghdr*) NULL)
#define CMSG_OK(mhdr, cmsg) ((cmsg)->cmsg_len >= sizeof(struct cmsghdr) &&   (cmsg)->cmsg_len <= (unsigned long)   ((mhdr)->msg_controllen -   ((char*)(cmsg) - (char*)(mhdr)->msg_control)))

struct cmsghdr* __cmsg_nxthdr(struct msghdr*, struct cmsghdr*);

#define SCM_RIGHTS 0x01
#define SCM_CREDENTIALS 0x02
#define SCM_SECURITY 0x03

struct ucred {
  pid_t pid;
  uid_t uid;
  gid_t gid;
};

#define AF_UNSPEC 0
#define AF_UNIX 1
#define AF_LOCAL 1
#define AF_INET 2
#define AF_AX25 3
#define AF_IPX 4
#define AF_APPLETALK 5
#define AF_NETROM 6
#define AF_BRIDGE 7
#define AF_ATMPVC 8
#define AF_X25 9
#define AF_INET6 10
#define AF_ROSE 11
#define AF_DECnet 12
#define AF_NETBEUI 13
#define AF_SECURITY 14
#define AF_KEY 15
#define AF_NETLINK 16
#define AF_ROUTE AF_NETLINK
#define AF_PACKET 17
#define AF_ASH 18
#define AF_ECONET 19
#define AF_ATMSVC 20
#define AF_RDS 21
#define AF_SNA 22
#define AF_IRDA 23
#define AF_PPPOX 24
#define AF_WANPIPE 25
#define AF_LLC 26
#define AF_CAN 29
#define AF_TIPC 30
#define AF_BLUETOOTH 31
#define AF_IUCV 32
#define AF_RXRPC 33
#define AF_ISDN 34
#define AF_PHONET 35
#define AF_IEEE802154 36
#define AF_CAIF 37
#define AF_ALG 38
#define AF_NFC 39
#define AF_VSOCK 40
#define AF_MAX 41

#define PF_UNSPEC AF_UNSPEC
#define PF_UNIX AF_UNIX
#define PF_LOCAL AF_LOCAL
#define PF_INET AF_INET
#define PF_AX25 AF_AX25
#define PF_IPX AF_IPX
#define PF_APPLETALK AF_APPLETALK
#define PF_NETROM AF_NETROM
#define PF_BRIDGE AF_BRIDGE
#define PF_ATMPVC AF_ATMPVC
#define PF_X25 AF_X25
#define PF_INET6 AF_INET6
#define PF_ROSE AF_ROSE
#define PF_DECnet AF_DECnet
#define PF_NETBEUI AF_NETBEUI
#define PF_SECURITY AF_SECURITY
#define PF_KEY AF_KEY
#define PF_NETLINK AF_NETLINK
#define PF_ROUTE AF_ROUTE
#define PF_PACKET AF_PACKET
#define PF_ASH AF_ASH
#define PF_ECONET AF_ECONET
#define PF_ATMSVC AF_ATMSVC
#define PF_RDS AF_RDS
#define PF_SNA AF_SNA
#define PF_IRDA AF_IRDA
#define PF_PPPOX AF_PPPOX
#define PF_WANPIPE AF_WANPIPE
#define PF_LLC AF_LLC
#define PF_CAN AF_CAN
#define PF_TIPC AF_TIPC
#define PF_BLUETOOTH AF_BLUETOOTH
#define PF_IUCV AF_IUCV
#define PF_RXRPC AF_RXRPC
#define PF_ISDN AF_ISDN
#define PF_PHONET AF_PHONET
#define PF_IEEE802154 AF_IEEE802154
#define PF_CAIF AF_CAIF
#define PF_ALG AF_ALG
#define PF_NFC AF_NFC
#define PF_VSOCK AF_VSOCK
#define PF_MAX AF_MAX

#define SOMAXCONN 128

#define MSG_OOB 1
#define MSG_PEEK 2
#define MSG_DONTROUTE 4
#define MSG_TRYHARD 4
#define MSG_CTRUNC 8
#define MSG_PROBE 0x10
#define MSG_TRUNC 0x20
#define MSG_DONTWAIT 0x40
#define MSG_EOR 0x80
#define MSG_WAITALL 0x100
#define MSG_FIN 0x200
#define MSG_SYN 0x400
#define MSG_CONFIRM 0x800
#define MSG_RST 0x1000
#define MSG_ERRQUEUE 0x2000
#define MSG_NOSIGNAL 0x4000
#define MSG_MORE 0x8000
#define MSG_WAITFORONE 0x10000
#define MSG_FASTOPEN 0x20000000
#define MSG_CMSG_CLOEXEC 0x40000000
#define MSG_EOF MSG_FIN
#define MSG_CMSG_COMPAT 0

#define SOL_IP 0
#define SOL_TCP 6
#define SOL_UDP 17
#define SOL_IPV6 41
#define SOL_ICMPV6 58
#define SOL_SCTP 132
#define SOL_RAW 255
#define SOL_IPX 256
#define SOL_AX25 257
#define SOL_ATALK 258
#define SOL_NETROM 259
#define SOL_ROSE 260
#define SOL_DECNET 261
#define SOL_X25 262
#define SOL_PACKET 263
#define SOL_ATM 264
#define SOL_AAL 265
#define SOL_IRDA 266
#define SOL_NETBEUI 267
#define SOL_LLC 268
#define SOL_DCCP 269
#define SOL_NETLINK 270
#define SOL_TIPC 271

#define IPX_TYPE 1

#ifdef __i386__
# define __socketcall extern __attribute__((__cdecl__))
#else
# define __socketcall extern
#endif

__socketcall int accept(int, struct sockaddr*, socklen_t*);
__socketcall int accept4(int, struct sockaddr*, socklen_t*, int);
__socketcall int bind(int, const struct sockaddr*, int);
__socketcall int connect(int, const struct sockaddr*, socklen_t);
__socketcall int getpeername(int, struct sockaddr*, socklen_t*);
__socketcall int getsockname(int, struct sockaddr*, socklen_t*);
__socketcall int getsockopt(int, int, int, void*, socklen_t*);
__socketcall int listen(int, int);
__socketcall int recvmmsg(int, struct mmsghdr*, unsigned int, int, const struct timespec*);
__socketcall int recvmsg(int, struct msghdr*, int);
__socketcall int sendmmsg(int, const struct mmsghdr*, unsigned int, int);
__socketcall int sendmsg(int, const struct msghdr*, int);
__socketcall int setsockopt(int, int, int, const void*, socklen_t);
__socketcall int shutdown(int, int);
__socketcall int socket(int, int, int);
__socketcall int socketpair(int, int, int, int*);

extern ssize_t send(int, const void*, size_t, int);
extern ssize_t recv(int, void*, size_t, int);

__socketcall ssize_t sendto(int, const void*, size_t, int, const struct sockaddr*, socklen_t);
__socketcall ssize_t recvfrom(int, void*, size_t, int, const struct sockaddr*, socklen_t*);

__errordecl(__recvfrom_error, "recvfrom called with size bigger than buffer");
extern ssize_t __recvfrom_chk(int, void*, size_t, size_t, int, const struct sockaddr*, socklen_t*);
extern ssize_t __recvfrom_real(int, void*, size_t, int, const struct sockaddr*, socklen_t*) __RENAME(recvfrom);

#if defined(__BIONIC_FORTIFY)

__BIONIC_FORTIFY_INLINE
ssize_t recvfrom(int fd, void* buf, size_t len, int flags, const struct sockaddr* src_addr, socklen_t* addr_len) {
  size_t bos = __bos0(buf);

#if !defined(__clang__)
  if (bos == __BIONIC_FORTIFY_UNKNOWN_SIZE) {
    return __recvfrom_real(fd, buf, len, flags, src_addr, addr_len);
  }

  if (__builtin_constant_p(len) && (len <= bos)) {
    return __recvfrom_real(fd, buf, len, flags, src_addr, addr_len);
  }

  if (__builtin_constant_p(len) && (len > bos)) {
    __recvfrom_error();
  }
#endif

  return __recvfrom_chk(fd, buf, len, bos, flags, src_addr, addr_len);
}

__BIONIC_FORTIFY_INLINE
ssize_t recv(int socket, void* buf, size_t len, int flags) {
  return recvfrom(socket, buf, len, flags, NULL, 0);
}

#endif /* __BIONIC_FORTIFY */

#undef __socketcall

__END_DECLS

#endif /* _SYS_SOCKET_H */
