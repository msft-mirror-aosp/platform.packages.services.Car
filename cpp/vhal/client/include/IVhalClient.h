/*
 * Copyright (c) 2022, The Android Open Source Project
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

#ifndef CPP_VHAL_CLIENT_INCLUDE_IVHALCLIENT_H_
#define CPP_VHAL_CLIENT_INCLUDE_IVHALCLIENT_H_

#include "IHalPropConfig.h"
#include "IHalPropValue.h"

#include <aidl/android/hardware/automotive/vehicle/StatusCode.h>
#include <aidl/android/hardware/automotive/vehicle/SubscribeOptions.h>
#include <android-base/result.h>

namespace android {
namespace frameworks {
namespace automotive {
namespace vhal {

struct HalPropError {
    int32_t propId;
    int32_t areaId;
    ::aidl::android::hardware::automotive::vehicle::StatusCode status;
};

// ISubscriptionCallback is a general interface to delivery property events caused by subscription.
class ISubscriptionCallback {
public:
    virtual ~ISubscriptionCallback() = default;
    /**
     * Called when new property events happen.
     */
    virtual void onPropertyEvent(const std::vector<std::unique_ptr<IHalPropValue>>& values) = 0;

    /**
     * Called when property set errors happen.
     */
    virtual void onPropertySetError(const std::vector<HalPropError>& errors) = 0;
};

// ISubscriptionCallback is a client that could be used to subscribe/unsubscribe.
class ISubscriptionClient {
public:
    virtual ~ISubscriptionClient() = default;
    virtual ::android::base::Result<void> subscribe(
            const std::vector<::aidl::android::hardware::automotive::vehicle::SubscribeOptions>&
                    options) = 0;

    virtual ::android::base::Result<void> unsubscribe(const std::vector<int32_t>& propIds) = 0;
};

// IVhalClient is a thread-safe client for AIDL or HIDL VHAL backend.
class IVhalClient {
public:
    // Wait for VHAL service and create a client. Return nullptr if failed to connect to VHAL.
    static std::shared_ptr<IVhalClient> create();

    // Try to get the VHAL service and create a client. Return nullptr if failed to connect to VHAL.
    static std::shared_ptr<IVhalClient> tryCreate();

    // The default timeout for callbacks.
    constexpr static int64_t DEFAULT_TIMEOUT_IN_SEC = 10;

    virtual ~IVhalClient() = default;

    using GetValueCallbackFunc =
            std::function<void(::android::base::Result<std::unique_ptr<IHalPropValue>>)>;
    using SetValueCallbackFunc = std::function<void(::android::base::Result<void>)>;
    using OnBinderDiedCallbackFunc = std::function<void()>;

    virtual std::unique_ptr<IHalPropValue> createHalPropValue(int32_t propId) = 0;

    virtual std::unique_ptr<IHalPropValue> createHalPropValue(int32_t propId, int32_t areaId) = 0;

    virtual void getValue(const IHalPropValue& requestValue,
                          std::shared_ptr<GetValueCallbackFunc> callback) = 0;

    virtual void setValue(const IHalPropValue& value,
                          std::shared_ptr<SetValueCallbackFunc> callback) = 0;

    virtual ::android::base::Result<void> addOnBinderDiedCallback(
            std::shared_ptr<OnBinderDiedCallbackFunc> callback) = 0;

    virtual ::android::base::Result<void> removeOnBinderDiedCallback(
            std::shared_ptr<OnBinderDiedCallbackFunc> callback) = 0;

    virtual ::android::base::Result<std::vector<std::unique_ptr<IHalPropConfig>>>
    getAllPropConfigs() = 0;

    virtual ::android::base::Result<std::vector<std::unique_ptr<IHalPropConfig>>> getPropConfigs(
            std::vector<int32_t> propIds) = 0;

    virtual std::unique_ptr<ISubscriptionClient> getSubscriptionClient(
            std::shared_ptr<ISubscriptionCallback> callback) = 0;
};

}  // namespace vhal
}  // namespace automotive
}  // namespace frameworks
}  // namespace android

#endif  // CPP_VHAL_CLIENT_INCLUDE_IVHALCLIENT_H_
