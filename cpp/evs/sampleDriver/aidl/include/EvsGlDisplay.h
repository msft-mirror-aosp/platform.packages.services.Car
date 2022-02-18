/*
 * Copyright (C) 2022 The Android Open Source Project
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

#ifndef CPP_EVS_SAMPLEDRIVER_AIDL_INCLUDE_EVSGLDISPLAY_H
#define CPP_EVS_SAMPLEDRIVER_AIDL_INCLUDE_EVSGLDISPLAY_H

#include "GlWrapper.h"

#include <aidl/android/hardware/automotive/evs/BnEvsDisplay.h>
#include <aidl/android/hardware/automotive/evs/BufferDesc.h>
#include <aidl/android/hardware/automotive/evs/DisplayDesc.h>
#include <aidl/android/hardware/automotive/evs/DisplayState.h>
#include <android/frameworks/automotive/display/1.0/IAutomotiveDisplayProxyService.h>

#include <semaphore.h>

#include <thread>

namespace aidl::android::hardware::automotive::evs::implementation {

namespace automotivedisplay = ::android::frameworks::automotive::display::V1_0;
namespace aidlevs = ::aidl::android::hardware::automotive::evs;

class EvsGlDisplay final : public ::aidl::android::hardware::automotive::evs::BnEvsDisplay {
public:
    // Methods from ::aidl::android::hardware::automotive::evs::IEvsDisplay follow.
    ::ndk::ScopedAStatus getDisplayInfo(aidlevs::DisplayDesc* _aidl_return) override;
    ::ndk::ScopedAStatus getDisplayState(aidlevs::DisplayState* _aidl_return) override;
    ::ndk::ScopedAStatus getTargetBuffer(aidlevs::BufferDesc* _aidl_return) override;
    ::ndk::ScopedAStatus returnTargetBufferForDisplay(const evs::BufferDesc& buffer) override;
    ::ndk::ScopedAStatus setDisplayState(aidlevs::DisplayState state) override;

    // Implementation details
    // TODO(b/170401743): using AIDL version when IAutomotiveDisplayProxyService is migrated.
    EvsGlDisplay(const ::android::sp<automotivedisplay::IAutomotiveDisplayProxyService>& service,
                 uint64_t displayId);
    virtual ~EvsGlDisplay() override;

    // This gets called if another caller "steals" ownership of the display
    void forceShutdown();

private:
    // A graphics buffer into which we'll store images.  This member variable
    // will be protected by semaphores.
    struct BufferRecord {
        ::aidl::android::hardware::graphics::common::HardwareBufferDescription description;
        buffer_handle_t handle;
        int fingerprint;
    } mBuffer;

    // State of a rendering thread
    enum RenderThreadStates {
        STOPPED = 0,
        STOPPING = 1,
        RUN = 2,
    };

    uint64_t mDisplayId;
    aidlevs::DisplayDesc mInfo;
    aidlevs::DisplayState mRequestedState GUARDED_BY(mLock) = aidlevs::DisplayState::NOT_VISIBLE;
    ::android::sp<automotivedisplay::IAutomotiveDisplayProxyService> mDisplayProxy;

    GlWrapper mGlWrapper;
    mutable std::mutex mLock;

    // This tells us whether or not our buffer is in use.  Protected by
    // semaphores.
    bool mFrameBusy = false;

    // Variables to synchronize a rendering thread w/ main and binder threads
    std::thread mRenderThread;
    std::atomic<int> mState = STOPPED;
    void renderFrames();
    bool initializeGlContextLocked() REQUIRES(mLock);

    sem_t mBufferReadyToUse;
    sem_t mBufferReadyToRender;
    sem_t mBufferDone;
};

}  // namespace aidl::android::hardware::automotive::evs::implementation

#endif  // CPP_EVS_SAMPLEDRIVER_AIDL_INCLUDE_EVSGLDISPLAY_H
