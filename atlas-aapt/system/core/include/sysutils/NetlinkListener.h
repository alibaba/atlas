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
#ifndef _NETLINKLISTENER_H
#define _NETLINKLISTENER_H

#include "SocketListener.h"

class NetlinkEvent;

class NetlinkListener : public SocketListener {
    char mBuffer[64 * 1024] __attribute__((aligned(4)));
    int mFormat;

public:
    static const int NETLINK_FORMAT_ASCII = 0;
    static const int NETLINK_FORMAT_BINARY = 1;
    static const int NETLINK_FORMAT_BINARY_UNICAST = 2;

#if 1
    /* temporary version until we can get Motorola to update their
     * ril.so.  Their prebuilt ril.so is using this private class
     * so changing the NetlinkListener() constructor breaks their ril.
     */
    NetlinkListener(int socket);
    NetlinkListener(int socket, int format);
#else
    NetlinkListener(int socket, int format = NETLINK_FORMAT_ASCII);
#endif
    virtual ~NetlinkListener() {}

protected:
    virtual bool onDataAvailable(SocketClient *cli);
    virtual void onEvent(NetlinkEvent *evt) = 0;
};

#endif
