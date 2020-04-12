// Copyright (C) 2019 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#include "StatusUtil.h"

namespace android {
namespace automotive {
namespace computepipe {
namespace runner {
namespace client_interface {
namespace aidl_client {

using ndk::ScopedAStatus;

ScopedAStatus ToNdkStatus(Status status) {
    switch (status) {
        case SUCCESS:
            return ScopedAStatus::ok();
        case INTERNAL_ERROR:
            return ScopedAStatus::fromExceptionCode(EX_TRANSACTION_FAILED);
        case INVALID_ARGUMENT:
            return ScopedAStatus::fromExceptionCode(EX_ILLEGAL_ARGUMENT);
        case FATAL_ERROR:
        default:
            return ScopedAStatus::fromExceptionCode(EX_TRANSACTION_FAILED);
    }
}


}  // namespace aidl_client
}  // namespace client_interface
}  // namespace runner
}  // namespace computepipe
}  // namespace automotive
}  // namespace android

