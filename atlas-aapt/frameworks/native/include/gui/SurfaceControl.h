/*
 * Copyright (C) 2007 The Android Open Source Project
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

#ifndef ANDROID_GUI_SURFACE_CONTROL_H
#define ANDROID_GUI_SURFACE_CONTROL_H

#include <stdint.h>
#include <sys/types.h>

#include <utils/KeyedVector.h>
#include <utils/RefBase.h>
#include <utils/threads.h>

#include <ui/FrameStats.h>
#include <ui/PixelFormat.h>
#include <ui/Region.h>

#include <gui/ISurfaceComposerClient.h>

namespace android {

// ---------------------------------------------------------------------------

class IGraphicBufferProducer;
class Surface;
class SurfaceComposerClient;

// ---------------------------------------------------------------------------

class SurfaceControl : public RefBase
{
public:
    static bool isValid(const sp<SurfaceControl>& surface) {
        return (surface != 0) && surface->isValid();
    }

    bool isValid() {
        return mHandle!=0 && mClient!=0;
    }

    static bool isSameSurface(
            const sp<SurfaceControl>& lhs, const sp<SurfaceControl>& rhs);

    // release surface data from java
    void        clear();

    // disconnect any api that's connected
    void        disconnect();

    status_t    setLayerStack(uint32_t layerStack);
    status_t    setLayer(uint32_t layer);
    status_t    setPosition(float x, float y);
    status_t    setSize(uint32_t w, uint32_t h);
    status_t    hide();
    status_t    show();
    status_t    setFlags(uint32_t flags, uint32_t mask);
    status_t    setTransparentRegionHint(const Region& transparent);
    status_t    setAlpha(float alpha=1.0f);
    status_t    setMatrix(float dsdx, float dtdx, float dsdy, float dtdy);
    status_t    setCrop(const Rect& crop);
    status_t    setFinalCrop(const Rect& crop);

    // If the size changes in this transaction, position updates specified
    // in this transaction will not complete until a buffer of the new size
    // arrives.
    status_t    setPositionAppliesWithResize();

    // Defers applying any changes made in this transaction until the Layer
    // identified by handle reaches the given frameNumber
    status_t deferTransactionUntil(sp<IBinder> handle, uint64_t frameNumber);

    // Set an override scaling mode as documented in <system/window.h>
    // the override scaling mode will take precedence over any client
    // specified scaling mode. -1 will clear the override scaling mode.
    status_t setOverrideScalingMode(int32_t overrideScalingMode);

    static status_t writeSurfaceToParcel(
            const sp<SurfaceControl>& control, Parcel* parcel);

    sp<Surface> getSurface() const;
    sp<IBinder> getHandle() const;

    status_t clearLayerFrameStats() const;
    status_t getLayerFrameStats(FrameStats* outStats) const;

private:
    // can't be copied
    SurfaceControl& operator = (SurfaceControl& rhs);
    SurfaceControl(const SurfaceControl& rhs);

    friend class SurfaceComposerClient;
    friend class Surface;

    SurfaceControl(
            const sp<SurfaceComposerClient>& client,
            const sp<IBinder>& handle,
            const sp<IGraphicBufferProducer>& gbp);

    ~SurfaceControl();

    status_t validate() const;
    void destroy();

    sp<SurfaceComposerClient>   mClient;
    sp<IBinder>                 mHandle;
    sp<IGraphicBufferProducer>  mGraphicBufferProducer;
    mutable Mutex               mLock;
    mutable sp<Surface>         mSurfaceData;
};

}; // namespace android

#endif // ANDROID_GUI_SURFACE_CONTROL_H
