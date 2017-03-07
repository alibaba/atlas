/*
 * Copyright 2016 The Android Open Source Project
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

#ifndef ANDROID_UI_HDR_CAPABILTIES_H
#define ANDROID_UI_HDR_CAPABILTIES_H

#include <binder/Parcelable.h>

namespace android {

class HdrCapabilities : public Parcelable
{
public:
    HdrCapabilities(const std::vector<int32_t /*android_hdr_t*/>& types,
            float maxLuminance, float maxAverageLuminance, float minLuminance)
      : mSupportedHdrTypes(types),
        mMaxLuminance(maxLuminance),
        mMaxAverageLuminance(maxAverageLuminance),
        mMinLuminance(minLuminance) {}

    // Make this move-constructable and move-assignable
    HdrCapabilities(HdrCapabilities&& other) = default;
    HdrCapabilities& operator=(HdrCapabilities&& other) = default;

    HdrCapabilities()
      : mSupportedHdrTypes(),
        mMaxLuminance(-1.0f),
        mMaxAverageLuminance(-1.0f),
        mMinLuminance(-1.0f) {}

    virtual ~HdrCapabilities() = default;

    const std::vector<int32_t /*android_hdr_t*/>& getSupportedHdrTypes() const {
        return mSupportedHdrTypes;
    }
    float getDesiredMaxLuminance() const { return mMaxLuminance; }
    float getDesiredMaxAverageLuminance() const { return mMaxAverageLuminance; }
    float getDesiredMinLuminance() const { return mMinLuminance; }

    // Parcelable interface
    virtual status_t writeToParcel(Parcel* parcel) const override;
    virtual status_t readFromParcel(const Parcel* parcel) override;

private:
    std::vector<int32_t /*android_hdr_t*/> mSupportedHdrTypes;
    float mMaxLuminance;
    float mMaxAverageLuminance;
    float mMinLuminance;
};

} // namespace android

#endif
