/*
 * Copyright 2019 The Android Open Source Project
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

#ifndef ANDROID_AUTOMOTIVE_COMPUTEPIPE_ROUTER_CLIENT_HANDLE
#define ANDROID_AUTOMOTIVE_COMPUTEPIPE_ROUTER_CLIENT_HANDLE

#include <cstdint>

namespace android {
namespace automotive {
namespace computepipe {
namespace router {

class ClientHandle {
  public:
    virtual uint32_t getClientId() = 0;
    virtual bool isAlive() = 0;
    virtual ~ClientHandle() {
    }
};

}  // namespace router
}  // namespace computepipe
}  // namespace automotive
}  // namespace android

#endif
