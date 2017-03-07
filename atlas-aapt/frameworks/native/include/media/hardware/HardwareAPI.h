/*
 * Copyright (C) 2009 The Android Open Source Project
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

#ifndef HARDWARE_API_H_

#define HARDWARE_API_H_

#include <media/hardware/OMXPluginBase.h>
#include <media/hardware/MetadataBufferType.h>
#include <system/window.h>
#include <utils/RefBase.h>

#include "VideoAPI.h"

#include <OMX_Component.h>

namespace android {

// This structure is used to enable Android native buffer use for either
// graphic buffers or secure buffers.
//
// TO CONTROL ANDROID GRAPHIC BUFFER USAGE:
//
// A pointer to this struct is passed to the OMX_SetParameter when the extension
// index for the 'OMX.google.android.index.enableAndroidNativeBuffers' extension
// is given.
//
// When Android native buffer use is disabled for a port (the default state),
// the OMX node should operate as normal, and expect UseBuffer calls to set its
// buffers.  This is the mode that will be used when CPU access to the buffer is
// required.
//
// When Android native buffer use has been enabled for a given port, the video
// color format for the port is to be interpreted as an Android pixel format
// rather than an OMX color format.  Enabling Android native buffers may also
// change how the component receives the native buffers.  If store-metadata-mode
// is enabled on the port, the component will receive the buffers as specified
// in the section below. Otherwise, unless the node supports the
// 'OMX.google.android.index.useAndroidNativeBuffer2' extension, it should
// expect to receive UseAndroidNativeBuffer calls (via OMX_SetParameter) rather
// than UseBuffer calls for that port.
//
// TO CONTROL ANDROID SECURE BUFFER USAGE:
//
// A pointer to this struct is passed to the OMX_SetParameter when the extension
// index for the 'OMX.google.android.index.allocateNativeHandle' extension
// is given.
//
// When native handle use is disabled for a port (the default state),
// the OMX node should operate as normal, and expect AllocateBuffer calls to
// return buffer pointers. This is the mode that will be used for non-secure
// buffers if component requires allocate buffers instead of use buffers.
//
// When native handle use has been enabled for a given port, the component
// shall allocate native_buffer_t objects containing  that can be passed between
// processes using binder. This is the mode that will be used for secure buffers.
// When an OMX component allocates native handle for buffers, it must close and
// delete that handle when it frees those buffers. Even though pBuffer will point
// to a native handle, nFilledLength, nAllocLength and nOffset will correspond
// to the data inside the opaque buffer.
struct EnableAndroidNativeBuffersParams {
    OMX_U32 nSize;
    OMX_VERSIONTYPE nVersion;
    OMX_U32 nPortIndex;
    OMX_BOOL enable;
};

typedef struct EnableAndroidNativeBuffersParams AllocateNativeHandleParams;

// A pointer to this struct is passed to OMX_SetParameter() when the extension index
// "OMX.google.android.index.storeMetaDataInBuffers" or
// "OMX.google.android.index.storeANWBufferInMetadata" is given.
//
// When meta data is stored in the video buffers passed between OMX clients
// and OMX components, interpretation of the buffer data is up to the
// buffer receiver, and the data may or may not be the actual video data, but
// some information helpful for the receiver to locate the actual data.
// The buffer receiver thus needs to know how to interpret what is stored
// in these buffers, with mechanisms pre-determined externally. How to
// interpret the meta data is outside of the scope of this parameter.
//
// Currently, this is used to pass meta data from video source (camera component, for instance) to
// video encoder to avoid memcpying of input video frame data, as well as to pass dynamic output
// buffer to video decoder. To do this, bStoreMetaData is set to OMX_TRUE.
//
// If bStoreMetaData is set to false, real YUV frame data will be stored in input buffers, and
// the output buffers contain either real YUV frame data, or are themselves native handles as
// directed by enable/use-android-native-buffer parameter settings.
// In addition, if no OMX_SetParameter() call is made on a port with the corresponding extension
// index, the component should not assume that the client is not using metadata mode for the port.
//
// If the component supports this using the "OMX.google.android.index.storeANWBufferInMetadata"
// extension and bStoreMetaData is set to OMX_TRUE, data is passed using the VideoNativeMetadata
// layout as defined below. Each buffer will be accompanied by a fence. The fence must signal
// before the buffer can be used (e.g. read from or written into). When returning such buffer to
// the client, component must provide a new fence that must signal before the returned buffer can
// be used (e.g. read from or written into). The component owns the incoming fenceFd, and must close
// it when fence has signaled. The client will own and close the returned fence file descriptor.
//
// If the component supports this using the "OMX.google.android.index.storeMetaDataInBuffers"
// extension and bStoreMetaData is set to OMX_TRUE, data is passed using VideoGrallocMetadata
// (the layout of which is the VideoGrallocMetadata defined below). Camera input can be also passed
// as "CameraSource", the layout of which is vendor dependent.
//
// Metadata buffers are registered with the component using UseBuffer calls, or can be allocated
// by the component for encoder-metadata-output buffers.
struct StoreMetaDataInBuffersParams {
    OMX_U32 nSize;
    OMX_VERSIONTYPE nVersion;
    OMX_U32 nPortIndex;
    OMX_BOOL bStoreMetaData;
};

// Meta data buffer layout used to transport output frames to the decoder for
// dynamic buffer handling.
struct VideoGrallocMetadata {
    MetadataBufferType eType;               // must be kMetadataBufferTypeGrallocSource
#ifdef OMX_ANDROID_COMPILE_AS_32BIT_ON_64BIT_PLATFORMS
    OMX_PTR pHandle;
#else
    buffer_handle_t pHandle;
#endif
};

// Legacy name for VideoGrallocMetadata struct.
struct VideoDecoderOutputMetaData : public VideoGrallocMetadata {};

struct VideoNativeMetadata {
    MetadataBufferType eType;               // must be kMetadataBufferTypeANWBuffer
#ifdef OMX_ANDROID_COMPILE_AS_32BIT_ON_64BIT_PLATFORMS
    OMX_PTR pBuffer;
#else
    struct ANativeWindowBuffer* pBuffer;
#endif
    int nFenceFd;                           // -1 if unused
};

// Meta data buffer layout for passing a native_handle to codec
struct VideoNativeHandleMetadata {
    MetadataBufferType eType;               // must be kMetadataBufferTypeNativeHandleSource

#ifdef OMX_ANDROID_COMPILE_AS_32BIT_ON_64BIT_PLATFORMS
    OMX_PTR pHandle;
#else
    native_handle_t *pHandle;
#endif
};

// A pointer to this struct is passed to OMX_SetParameter() when the extension
// index "OMX.google.android.index.prepareForAdaptivePlayback" is given.
//
// This method is used to signal a video decoder, that the user has requested
// seamless resolution change support (if bEnable is set to OMX_TRUE).
// nMaxFrameWidth and nMaxFrameHeight are the dimensions of the largest
// anticipated frames in the video.  If bEnable is OMX_FALSE, no resolution
// change is expected, and the nMaxFrameWidth/Height fields are unused.
//
// If the decoder supports dynamic output buffers, it may ignore this
// request.  Otherwise, it shall request resources in such a way so that it
// avoids full port-reconfiguration (due to output port-definition change)
// during resolution changes.
//
// DO NOT USE THIS STRUCTURE AS IT WILL BE REMOVED.  INSTEAD, IMPLEMENT
// METADATA SUPPORT FOR VIDEO DECODERS.
struct PrepareForAdaptivePlaybackParams {
    OMX_U32 nSize;
    OMX_VERSIONTYPE nVersion;
    OMX_U32 nPortIndex;
    OMX_BOOL bEnable;
    OMX_U32 nMaxFrameWidth;
    OMX_U32 nMaxFrameHeight;
};

// A pointer to this struct is passed to OMX_SetParameter when the extension
// index for the 'OMX.google.android.index.useAndroidNativeBuffer' extension is
// given.  This call will only be performed if a prior call was made with the
// 'OMX.google.android.index.enableAndroidNativeBuffers' extension index,
// enabling use of Android native buffers.
struct UseAndroidNativeBufferParams {
    OMX_U32 nSize;
    OMX_VERSIONTYPE nVersion;
    OMX_U32 nPortIndex;
    OMX_PTR pAppPrivate;
    OMX_BUFFERHEADERTYPE **bufferHeader;
    const sp<ANativeWindowBuffer>& nativeBuffer;
};

// A pointer to this struct is passed to OMX_GetParameter when the extension
// index for the 'OMX.google.android.index.getAndroidNativeBufferUsage'
// extension is given.  The usage bits returned from this query will be used to
// allocate the Gralloc buffers that get passed to the useAndroidNativeBuffer
// command.
struct GetAndroidNativeBufferUsageParams {
    OMX_U32 nSize;              // IN
    OMX_VERSIONTYPE nVersion;   // IN
    OMX_U32 nPortIndex;         // IN
    OMX_U32 nUsage;             // OUT
};

// An enum OMX_COLOR_FormatAndroidOpaque to indicate an opaque colorformat
// is declared in media/stagefright/openmax/OMX_IVCommon.h
// This will inform the encoder that the actual
// colorformat will be relayed by the GRalloc Buffers.
// OMX_COLOR_FormatAndroidOpaque  = 0x7F000001,

// A pointer to this struct is passed to OMX_SetParameter when the extension
// index for the 'OMX.google.android.index.prependSPSPPSToIDRFrames' extension
// is given.
// A successful result indicates that future IDR frames will be prefixed by
// SPS/PPS.
struct PrependSPSPPSToIDRFramesParams {
    OMX_U32 nSize;
    OMX_VERSIONTYPE nVersion;
    OMX_BOOL bEnable;
};

// A pointer to this struct is passed to OMX_GetParameter when the extension
// index for the 'OMX.google.android.index.describeColorFormat'
// extension is given.  This method can be called from any component state
// other than invalid.  The color-format, frame width/height, and stride/
// slice-height parameters are ones that are associated with a raw video
// port (input or output), but the stride/slice height parameters may be
// incorrect. bUsingNativeBuffers is OMX_TRUE if native android buffers will
// be used (while specifying this color format).
//
// The component shall fill out the MediaImage structure that
// corresponds to the described raw video format, and the potentially corrected
// stride and slice-height info.
//
// The behavior is slightly different if bUsingNativeBuffers is OMX_TRUE,
// though most implementations can ignore this difference. When using native buffers,
// the component may change the configured color format to an optimized format.
// Additionally, when allocating these buffers for flexible usecase, the framework
// will set the SW_READ/WRITE_OFTEN usage flags. In this case (if bUsingNativeBuffers
// is OMX_TRUE), the component shall fill out the MediaImage information for the
// scenario when these SW-readable/writable buffers are locked using gralloc_lock.
// Note, that these buffers may also be locked using gralloc_lock_ycbcr, which must
// be supported for vendor-specific formats.
//
// For non-YUV packed planar/semiplanar image formats, or if bUsingNativeBuffers
// is OMX_TRUE and the component does not support this color format with native
// buffers, the component shall set mNumPlanes to 0, and mType to MEDIA_IMAGE_TYPE_UNKNOWN.

// @deprecated: use DescribeColorFormat2Params
struct DescribeColorFormat2Params;
struct DescribeColorFormatParams {
    OMX_U32 nSize;
    OMX_VERSIONTYPE nVersion;
    // input: parameters from OMX_VIDEO_PORTDEFINITIONTYPE
    OMX_COLOR_FORMATTYPE eColorFormat;
    OMX_U32 nFrameWidth;
    OMX_U32 nFrameHeight;
    OMX_U32 nStride;
    OMX_U32 nSliceHeight;
    OMX_BOOL bUsingNativeBuffers;

    // output: fill out the MediaImage fields
    MediaImage sMediaImage;

    DescribeColorFormatParams(const DescribeColorFormat2Params&); // for internal use only
};

// A pointer to this struct is passed to OMX_GetParameter when the extension
// index for the 'OMX.google.android.index.describeColorFormat2'
// extension is given. This is operationally the same as DescribeColorFormatParams
// but can be used for HDR and RGBA/YUVA formats.
struct DescribeColorFormat2Params {
    OMX_U32 nSize;
    OMX_VERSIONTYPE nVersion;
    // input: parameters from OMX_VIDEO_PORTDEFINITIONTYPE
    OMX_COLOR_FORMATTYPE eColorFormat;
    OMX_U32 nFrameWidth;
    OMX_U32 nFrameHeight;
    OMX_U32 nStride;
    OMX_U32 nSliceHeight;
    OMX_BOOL bUsingNativeBuffers;

    // output: fill out the MediaImage2 fields
    MediaImage2 sMediaImage;

    void initFromV1(const DescribeColorFormatParams&); // for internal use only
};

// A pointer to this struct is passed to OMX_SetParameter or OMX_GetParameter
// when the extension index for the
// 'OMX.google.android.index.configureVideoTunnelMode' extension is  given.
// If the extension is supported then tunneled playback mode should be supported
// by the codec. If bTunneled is set to OMX_TRUE then the video decoder should
// operate in "tunneled" mode and output its decoded frames directly to the
// sink. In this case nAudioHwSync is the HW SYNC ID of the audio HAL Output
// stream to sync the video with. If bTunneled is set to OMX_FALSE, "tunneled"
// mode should be disabled and nAudioHwSync should be ignored.
// OMX_GetParameter is used to query tunneling configuration. bTunneled should
// return whether decoder is operating in tunneled mode, and if it is,
// pSidebandWindow should contain the codec allocated sideband window handle.
struct ConfigureVideoTunnelModeParams {
    OMX_U32 nSize;              // IN
    OMX_VERSIONTYPE nVersion;   // IN
    OMX_U32 nPortIndex;         // IN
    OMX_BOOL bTunneled;         // IN/OUT
    OMX_U32 nAudioHwSync;       // IN
    OMX_PTR pSidebandWindow;    // OUT
};

// Color space description (aspects) parameters.
// This is passed via OMX_SetConfig or OMX_GetConfig to video encoders and decoders when the
// 'OMX.google.android.index.describeColorAspects' extension is given. Component SHALL behave
// as described below if it supports this extension.
//
// bDataSpaceChanged and bRequestingDataSpace is assumed to be OMX_FALSE unless noted otherwise.
//
// VIDEO ENCODERS: the framework uses OMX_SetConfig to specify color aspects of the coded video.
// This may happen:
//   a) before the component transitions to idle state
//   b) before the input frame is sent via OMX_EmptyThisBuffer in executing state
//   c) during execution, just before an input frame with a different color aspect information
//      is sent.
//
// The framework also uses OMX_GetConfig to
//   d) verify the color aspects that will be written to the stream
//   e) (optional) verify the color aspects that should be reported to the container for a
//      given dataspace/pixelformat received
//
// 1. Encoders SHOULD maintain an internal color aspect state, initialized to Unspecified values.
//    This represents the values that will be written into the bitstream.
// 2. Upon OMX_SetConfig, they SHOULD update their internal state to the aspects received
//    (including Unspecified values). For specific aspect values that are not supported by the
//    codec standard, encoders SHOULD substitute Unspecified values; or they MAY use a suitable
//    alternative (e.g. to suggest the use of BT.709 EOTF instead of SMPTE 240M.)
// 3. OMX_GetConfig SHALL return the internal state (values that will be written).
// 4. OMX_SetConfig SHALL always succeed before receiving the first frame. It MAY fail afterwards,
//    but only if the configured values would change AND the component does not support updating the
//    color information to those values mid-stream. If component supports updating a portion of
//    the color information, those values should be updated in the internal state, and OMX_SetConfig
//    SHALL succeed. Otherwise, the internal state SHALL remain intact and OMX_SetConfig SHALL fail
//    with OMX_ErrorUnsupportedSettings.
// 5. When the framework receives an input frame with an unexpected dataspace, it will query
//    encoders for the color aspects that should be reported to the container using OMX_GetConfig
//    with bDataSpaceChanged set to OMX_TRUE, and nPixelFormat/nDataSpace containing the new
//    format/dataspace values. This allows vendors to use extended dataspace during capture and
//    composition (e.g. screenrecord) - while performing color-space conversion inside the encoder -
//    and encode and report a different color-space information in the bitstream/container.
//    sColorAspects contains the requested color aspects by the client for reference, which may
//    include aspects not supported by the encoding. This is used together with guidance for
//    dataspace selection; see 6. below.
//
// VIDEO DECODERS: the framework uses OMX_SetConfig to specify the default color aspects to use
// for the video.
// This may happen:
//   a) before the component transitions to idle state
//   b) during execution, when the resolution or the default color aspects change.
//
// The framework also uses OMX_GetConfig to
//   c) get the final color aspects reported by the coded bitstream after taking the default values
//      into account.
//
// 1. Decoders should maintain two color aspect states - the default state as reported by the
//    framework, and the coded state as reported by the bitstream - as each state can change
//    independently from the other.
// 2. Upon OMX_SetConfig, it SHALL update its default state regardless of whether such aspects
//    could be supplied by the component bitstream. (E.g. it should blindly support all enumeration
//    values, even unknown ones, and the Other value). This SHALL always succeed.
// 3. Upon OMX_GetConfig, the component SHALL return the final color aspects by replacing
//    Unspecified coded values with the default values. This SHALL always succeed.
// 4. Whenever the component processes color aspect information in the bitstream even with an
//    Unspecified value, it SHOULD update its internal coded state with that information just before
//    the frame with the new information would be outputted, and the component SHALL signal an
//    OMX_EventPortSettingsChanged event with data2 set to the extension index.
// NOTE: Component SHOULD NOT signal a separate event purely for color aspect change, if it occurs
//    together with a port definition (e.g. size) or crop change.
// 5. If the aspects a component encounters in the bitstream cannot be represented with enumeration
//    values as defined below, the component SHALL set those aspects to Other. Restricted values in
//    the bitstream SHALL be treated as defined by the relevant bitstream specifications/standards,
//    or as Unspecified, if not defined.
//
// BOTH DECODERS AND ENCODERS: the framework uses OMX_GetConfig during idle and executing state to
//   f) (optional) get guidance for the dataspace to set for given color aspects, by setting
//      bRequestingDataSpace to OMX_TRUE. The component SHALL return OMX_ErrorUnsupportedSettings
//      IF it does not support this request.
//
// 6. This is an information request that can happen at any time, independent of the normal
//    configuration process. This allows vendors to use extended dataspace during capture, playback
//    and composition - while performing color-space conversion inside the component. Component
//    SHALL set the desired dataspace into nDataSpace. Otherwise, it SHALL return
//    OMX_ErrorUnsupportedSettings to let the framework choose a nearby standard dataspace.
//
// 6.a. For encoders, this query happens before the first frame is received using surface encoding.
//    This allows the encoder to use a specific dataspace for the color aspects (e.g. because the
//    device supports additional dataspaces, or because it wants to perform color-space extension
//    to facilitate a more optimal rendering/capture pipeline.).
//
// 6.b. For decoders, this query happens before the first frame, and every time the color aspects
//    change, while using surface buffers. This allows the decoder to use a specific dataspace for
//    the color aspects (e.g. because the device supports additional dataspaces, or because it wants
//    to perform color-space extension by inline color-space conversion to facilitate a more optimal
//    rendering pipeline.).
//
// Note: the size of sAspects may increase in the future by additional fields.
// Implementations SHOULD NOT require a certain size.
struct DescribeColorAspectsParams {
    OMX_U32 nSize;                 // IN
    OMX_VERSIONTYPE nVersion;      // IN
    OMX_U32 nPortIndex;            // IN
    OMX_BOOL bRequestingDataSpace; // IN
    OMX_BOOL bDataSpaceChanged;    // IN
    OMX_U32 nPixelFormat;          // IN
    OMX_U32 nDataSpace;            // OUT
    ColorAspects sAspects;         // IN/OUT
};

// HDR color description parameters.
// This is passed via OMX_SetConfig or OMX_GetConfig to video encoders and decoders when the
// 'OMX.google.android.index.describeHDRColorInfo' extension is given and an HDR stream
// is detected.  Component SHALL behave as described below if it supports this extension.
//
// Currently, only Static Metadata Descriptor Type 1 support is required.
//
// VIDEO ENCODERS: the framework uses OMX_SetConfig to specify the HDR static information of the
// coded video.
// This may happen:
//   a) before the component transitions to idle state
//   b) before the input frame is sent via OMX_EmptyThisBuffer in executing state
//   c) during execution, just before an input frame with a different HDR static
//      information is sent.
//
// The framework also uses OMX_GetConfig to
//   d) verify the HDR static information that will be written to the stream.
//
// 1. Encoders SHOULD maintain an internal HDR static info data, initialized to Unspecified values.
//    This represents the values that will be written into the bitstream.
// 2. Upon OMX_SetConfig, they SHOULD update their internal state to the info received
//    (including Unspecified values). For specific parameters that are not supported by the
//    codec standard, encoders SHOULD substitute Unspecified values. NOTE: no other substitution
//    is allowed.
// 3. OMX_GetConfig SHALL return the internal state (values that will be written).
// 4. OMX_SetConfig SHALL always succeed before receiving the first frame if the encoder is
//    configured into an HDR compatible profile. It MAY fail with OMX_ErrorUnsupportedSettings error
//    code if it is not configured into such a profile, OR if the configured values would change
//    AND the component does not support updating the HDR static information mid-stream. If the
//    component supports updating a portion of the information, those values should be updated in
//    the internal state, and OMX_SetConfig SHALL succeed. Otherwise, the internal state SHALL
//    remain intact.
//
// VIDEO DECODERS: the framework uses OMX_SetConfig to specify the default HDR static information
// to use for the video.
//   a) This only happens if the client supplies this information, in which case it occurs before
//      the component transitions to idle state.
//   b) This may also happen subsequently if the default HDR static information changes.
//
// The framework also uses OMX_GetConfig to
//   c) get the final HDR static information reported by the coded bitstream after taking the
//      default values into account.
//
// 1. Decoders should maintain two HDR static information structures - the default values as
//    reported by the framework, and the coded values as reported by the bitstream - as each
//    structure can change independently from the other.
// 2. Upon OMX_SetConfig, it SHALL update its default structure regardless of whether such static
//    parameters could be supplied by the component bitstream. (E.g. it should blindly support all
//    parameter values, even seemingly illegal ones). This SHALL always succeed.
//  Note: The descriptor ID used in sInfo may change in subsequent calls. (although for now only
//    Type 1 support is required.)
// 3. Upon OMX_GetConfig, the component SHALL return the final HDR static information by replacing
//    Unspecified coded values with the default values. This SHALL always succeed. This may be
//    provided using any supported descriptor ID (currently only Type 1) with the goal of expressing
//    the most of the available static information.
// 4. Whenever the component processes HDR static information in the bitstream even ones with
//    Unspecified parameters, it SHOULD update its internal coded structure with that information
//    just before the frame with the new information would be outputted, and the component SHALL
//    signal an OMX_EventPortSettingsChanged event with data2 set to the extension index.
// NOTE: Component SHOULD NOT signal a separate event purely for HDR static info change, if it
//    occurs together with a port definition (e.g. size), color aspect or crop change.
// 5. If certain parameters of the HDR static information encountered in the bitstream cannot be
//    represented using sInfo, the component SHALL use the closest representation.
//
// Note: the size of sInfo may increase in the future by supporting additional descriptor types.
// Implementations SHOULD NOT require a certain size.
struct DescribeHDRStaticInfoParams {
    OMX_U32 nSize;                 // IN
    OMX_VERSIONTYPE nVersion;      // IN
    OMX_U32 nPortIndex;            // IN
    HDRStaticInfo sInfo;           // IN/OUT
};

}  // namespace android

extern android::OMXPluginBase *createOMXPlugin();

#endif  // HARDWARE_API_H_
