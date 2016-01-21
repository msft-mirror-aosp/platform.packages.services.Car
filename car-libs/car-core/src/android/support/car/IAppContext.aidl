/*
 * Copyright (C) 2015 The Android Open Source Project
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

package android.support.car;

import android.support.car.IAppContextListener;

/** {@CompatibilityApi} */
interface IAppContext {
    int getVersion() = 0;
    void registerContextListener(int clientVersion, IAppContextListener listener, int filter) = 1;
    void unregisterContextListener(IAppContextListener listener) = 2;
    int getActiveAppContexts() = 3;
    /** listener used as a token */
    boolean isOwningContext(IAppContextListener listener, int context) = 4;
    /** listener used as a token */
    void setActiveContexts(IAppContextListener listener, int contexts) = 5;
    /** listener used as a token */
    void resetActiveContexts(IAppContextListener listener, int contexts) = 6;
}
