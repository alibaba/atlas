/*
 * Copyright (C) 2016 The Android Open Source Project
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

#ifndef VIDEO_API_H_

#define VIDEO_API_H_

namespace android {

/**
 * Structure describing a media image (frame)
 * Currently only supporting YUV
 * @deprecated. Use MediaImage2 instead
 */
struct MediaImage {
    enum Type {
        MEDIA_IMAGE_TYPE_UNKNOWN = 0,
        MEDIA_IMAGE_TYPE_YUV,
    };

    enum PlaneIndex {
        Y = 0,
        U,
        V,
        MAX_NUM_PLANES
    };

    Type mType;
    uint32_t mNumPlanes;              // number of planes
    uint32_t mWidth;                  // width of largest plane (unpadded, as in nFrameWidth)
    uint32_t mHeight;                 // height of largest plane (unpadded, as in nFrameHeight)
    uint32_t mBitDepth;               // useable bit depth
    struct PlaneInfo {
        uint32_t mOffset;             // offset of first pixel of the plane in bytes
                                      // from buffer offset
        uint32_t mColInc;             // column increment in bytes
        uint32_t mRowInc;             // row increment in bytes
        uint32_t mHorizSubsampling;   // subsampling compared to the largest plane
        uint32_t mVertSubsampling;    // subsampling compared to the largest plane
    };
    PlaneInfo mPlane[MAX_NUM_PLANES];
};

/**
 * Structure describing a media image (frame)
 */
struct __attribute__ ((__packed__)) MediaImage2 {
    enum Type : uint32_t {
        MEDIA_IMAGE_TYPE_UNKNOWN = 0,
        MEDIA_IMAGE_TYPE_YUV,
        MEDIA_IMAGE_TYPE_YUVA,
        MEDIA_IMAGE_TYPE_RGB,
        MEDIA_IMAGE_TYPE_RGBA,
        MEDIA_IMAGE_TYPE_Y,
    };

    enum PlaneIndex : uint32_t {
        Y = 0,
        U = 1,
        V = 2,
        R = 0,
        G = 1,
        B = 2,
        A = 3,
        MAX_NUM_PLANES = 4,
    };

    Type mType;
    uint32_t mNumPlanes;              // number of planes
    uint32_t mWidth;                  // width of largest plane (unpadded, as in nFrameWidth)
    uint32_t mHeight;                 // height of largest plane (unpadded, as in nFrameHeight)
    uint32_t mBitDepth;               // useable bit depth (always MSB)
    uint32_t mBitDepthAllocated;      // bits per component (must be 8 or 16)

    struct __attribute__ ((__packed__)) PlaneInfo {
        uint32_t mOffset;             // offset of first pixel of the plane in bytes
                                      // from buffer offset
        int32_t mColInc;              // column increment in bytes
        int32_t mRowInc;              // row increment in bytes
        uint32_t mHorizSubsampling;   // subsampling compared to the largest plane
        uint32_t mVertSubsampling;    // subsampling compared to the largest plane
    };
    PlaneInfo mPlane[MAX_NUM_PLANES];

    void initFromV1(const MediaImage&); // for internal use only
};

static_assert(sizeof(MediaImage2::PlaneInfo) == 20, "wrong struct size");
static_assert(sizeof(MediaImage2) == 104, "wrong struct size");

/**
 * Aspects of color.
 */

// NOTE: this structure is expected to grow in the future if new color aspects are
// added to codec bitstreams. OMX component should not require a specific nSize
// though could verify that nSize is at least the size of the structure at the
// time of implementation. All new fields will be added at the end of the structure
// ensuring backward compatibility.
struct __attribute__ ((__packed__)) ColorAspects {
    // this is in sync with the range values in graphics.h
    enum Range : uint32_t {
        RangeUnspecified,
        RangeFull,
        RangeLimited,
        RangeOther = 0xff,
    };

    enum Primaries : uint32_t {
        PrimariesUnspecified,
        PrimariesBT709_5,       // Rec.ITU-R BT.709-5 or equivalent
        PrimariesBT470_6M,      // Rec.ITU-R BT.470-6 System M or equivalent
        PrimariesBT601_6_625,   // Rec.ITU-R BT.601-6 625 or equivalent
        PrimariesBT601_6_525,   // Rec.ITU-R BT.601-6 525 or equivalent
        PrimariesGenericFilm,   // Generic Film
        PrimariesBT2020,        // Rec.ITU-R BT.2020 or equivalent
        PrimariesOther = 0xff,
    };

    // this partially in sync with the transfer values in graphics.h prior to the transfers
    // unlikely to be required by Android section
    enum Transfer : uint32_t {
        TransferUnspecified,
        TransferLinear,         // Linear transfer characteristics
        TransferSRGB,           // sRGB or equivalent
        TransferSMPTE170M,      // SMPTE 170M or equivalent (e.g. BT.601/709/2020)
        TransferGamma22,        // Assumed display gamma 2.2
        TransferGamma28,        // Assumed display gamma 2.8
        TransferST2084,         // SMPTE ST 2084 for 10/12/14/16 bit systems
        TransferHLG,            // ARIB STD-B67 hybrid-log-gamma

        // transfers unlikely to be required by Android
        TransferSMPTE240M = 0x40, // SMPTE 240M
        TransferXvYCC,          // IEC 61966-2-4
        TransferBT1361,         // Rec.ITU-R BT.1361 extended gamut
        TransferST428,          // SMPTE ST 428-1
        TransferOther = 0xff,
    };

    enum MatrixCoeffs : uint32_t {
        MatrixUnspecified,
        MatrixBT709_5,          // Rec.ITU-R BT.709-5 or equivalent
        MatrixBT470_6M,         // KR=0.30, KB=0.11 or equivalent
        MatrixBT601_6,          // Rec.ITU-R BT.601-6 625 or equivalent
        MatrixSMPTE240M,        // SMPTE 240M or equivalent
        MatrixBT2020,           // Rec.ITU-R BT.2020 non-constant luminance
        MatrixBT2020Constant,   // Rec.ITU-R BT.2020 constant luminance
        MatrixOther = 0xff,
    };

    // this is in sync with the standard values in graphics.h
    enum Standard : uint32_t {
        StandardUnspecified,
        StandardBT709,                  // PrimariesBT709_5 and MatrixBT709_5
        StandardBT601_625,              // PrimariesBT601_6_625 and MatrixBT601_6
        StandardBT601_625_Unadjusted,   // PrimariesBT601_6_625 and KR=0.222, KB=0.071
        StandardBT601_525,              // PrimariesBT601_6_525 and MatrixBT601_6
        StandardBT601_525_Unadjusted,   // PrimariesBT601_6_525 and MatrixSMPTE240M
        StandardBT2020,                 // PrimariesBT2020 and MatrixBT2020
        StandardBT2020Constant,         // PrimariesBT2020 and MatrixBT2020Constant
        StandardBT470M,                 // PrimariesBT470_6M and MatrixBT470_6M
        StandardFilm,                   // PrimariesGenericFilm and KR=0.253, KB=0.068
        StandardOther = 0xff,
    };

    Range mRange;                // IN/OUT
    Primaries mPrimaries;        // IN/OUT
    Transfer mTransfer;          // IN/OUT
    MatrixCoeffs mMatrixCoeffs;  // IN/OUT
};

static_assert(sizeof(ColorAspects) == 16, "wrong struct size");

/**
 * HDR Metadata.
 */

// HDR Static Metadata Descriptor as defined by CTA-861-3.
struct __attribute__ ((__packed__)) HDRStaticInfo {
    // Static_Metadata_Descriptor_ID
    enum ID : uint8_t {
        kType1 = 0, // Static Metadata Type 1
    } mID;

    struct __attribute__ ((__packed__)) Primaries1 {
        // values are in units of 0.00002
        uint16_t x;
        uint16_t y;
    };

    // Static Metadata Descriptor Type 1
    struct __attribute__ ((__packed__)) Type1 {
        Primaries1 mR; // display primary 0
        Primaries1 mG; // display primary 1
        Primaries1 mB; // display primary 2
        Primaries1 mW; // white point
        uint16_t mMaxDisplayLuminance; // in cd/m^2
        uint16_t mMinDisplayLuminance; // in 0.0001 cd/m^2
        uint16_t mMaxContentLightLevel; // in cd/m^2
        uint16_t mMaxFrameAverageLightLevel; // in cd/m^2
    };

    union {
         Type1 sType1;
    };
};

static_assert(sizeof(HDRStaticInfo::Primaries1) == 4, "wrong struct size");
static_assert(sizeof(HDRStaticInfo::Type1) == 24, "wrong struct size");
static_assert(sizeof(HDRStaticInfo) == 25, "wrong struct size");

#ifdef STRINGIFY_ENUMS

inline static const char *asString(MediaImage::Type i, const char *def = "??") {
    switch (i) {
        case MediaImage::MEDIA_IMAGE_TYPE_UNKNOWN: return "Unknown";
        case MediaImage::MEDIA_IMAGE_TYPE_YUV:     return "YUV";
        default:                                   return def;
    }
}

inline static const char *asString(MediaImage::PlaneIndex i, const char *def = "??") {
    switch (i) {
        case MediaImage::Y: return "Y";
        case MediaImage::U: return "U";
        case MediaImage::V: return "V";
        default:            return def;
    }
}

inline static const char *asString(MediaImage2::Type i, const char *def = "??") {
    switch (i) {
        case MediaImage2::MEDIA_IMAGE_TYPE_UNKNOWN: return "Unknown";
        case MediaImage2::MEDIA_IMAGE_TYPE_YUV:     return "YUV";
        case MediaImage2::MEDIA_IMAGE_TYPE_YUVA:    return "YUVA";
        case MediaImage2::MEDIA_IMAGE_TYPE_RGB:     return "RGB";
        case MediaImage2::MEDIA_IMAGE_TYPE_RGBA:    return "RGBA";
        case MediaImage2::MEDIA_IMAGE_TYPE_Y:       return "Y";
        default:                                    return def;
    }
}

inline static char asChar2(
        MediaImage2::PlaneIndex i, MediaImage2::Type j, char def = '?') {
    const char *planes = asString(j, NULL);
    // handle unknown values
    if (j == MediaImage2::MEDIA_IMAGE_TYPE_UNKNOWN || planes == NULL || i >= strlen(planes)) {
        return def;
    }
    return planes[i];
}

inline static const char *asString(ColorAspects::Range i, const char *def = "??") {
    switch (i) {
        case ColorAspects::RangeUnspecified: return "Unspecified";
        case ColorAspects::RangeFull:        return "Full";
        case ColorAspects::RangeLimited:     return "Limited";
        case ColorAspects::RangeOther:       return "Other";
        default:                             return def;
    }
}

inline static const char *asString(ColorAspects::Primaries i, const char *def = "??") {
    switch (i) {
        case ColorAspects::PrimariesUnspecified: return "Unspecified";
        case ColorAspects::PrimariesBT709_5:     return "BT709_5";
        case ColorAspects::PrimariesBT470_6M:    return "BT470_6M";
        case ColorAspects::PrimariesBT601_6_625: return "BT601_6_625";
        case ColorAspects::PrimariesBT601_6_525: return "BT601_6_525";
        case ColorAspects::PrimariesGenericFilm: return "GenericFilm";
        case ColorAspects::PrimariesBT2020:      return "BT2020";
        case ColorAspects::PrimariesOther:       return "Other";
        default:                                 return def;
    }
}

inline static const char *asString(ColorAspects::Transfer i, const char *def = "??") {
    switch (i) {
        case ColorAspects::TransferUnspecified: return "Unspecified";
        case ColorAspects::TransferLinear:      return "Linear";
        case ColorAspects::TransferSRGB:        return "SRGB";
        case ColorAspects::TransferSMPTE170M:   return "SMPTE170M";
        case ColorAspects::TransferGamma22:     return "Gamma22";
        case ColorAspects::TransferGamma28:     return "Gamma28";
        case ColorAspects::TransferST2084:      return "ST2084";
        case ColorAspects::TransferHLG:         return "HLG";
        case ColorAspects::TransferSMPTE240M:   return "SMPTE240M";
        case ColorAspects::TransferXvYCC:       return "XvYCC";
        case ColorAspects::TransferBT1361:      return "BT1361";
        case ColorAspects::TransferST428:       return "ST428";
        case ColorAspects::TransferOther:       return "Other";
        default:                                return def;
    }
}

inline static const char *asString(ColorAspects::MatrixCoeffs i, const char *def = "??") {
    switch (i) {
        case ColorAspects::MatrixUnspecified:    return "Unspecified";
        case ColorAspects::MatrixBT709_5:        return "BT709_5";
        case ColorAspects::MatrixBT470_6M:       return "BT470_6M";
        case ColorAspects::MatrixBT601_6:        return "BT601_6";
        case ColorAspects::MatrixSMPTE240M:      return "SMPTE240M";
        case ColorAspects::MatrixBT2020:         return "BT2020";
        case ColorAspects::MatrixBT2020Constant: return "BT2020Constant";
        case ColorAspects::MatrixOther:          return "Other";
        default:                                 return def;
    }
}

inline static const char *asString(ColorAspects::Standard i, const char *def = "??") {
    switch (i) {
        case ColorAspects::StandardUnspecified:          return "Unspecified";
        case ColorAspects::StandardBT709:                return "BT709";
        case ColorAspects::StandardBT601_625:            return "BT601_625";
        case ColorAspects::StandardBT601_625_Unadjusted: return "BT601_625_Unadjusted";
        case ColorAspects::StandardBT601_525:            return "BT601_525";
        case ColorAspects::StandardBT601_525_Unadjusted: return "BT601_525_Unadjusted";
        case ColorAspects::StandardBT2020:               return "BT2020";
        case ColorAspects::StandardBT2020Constant:       return "BT2020Constant";
        case ColorAspects::StandardBT470M:               return "BT470M";
        case ColorAspects::StandardFilm:                 return "Film";
        case ColorAspects::StandardOther:                return "Other";
        default:                                         return def;
    }
}

#endif

}  // namespace android

#endif  // VIDEO_API_H_
