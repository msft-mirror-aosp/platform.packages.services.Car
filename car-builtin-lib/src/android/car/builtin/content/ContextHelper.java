/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.car.builtin.content;

import android.annotation.NonNull;
import android.annotation.SystemApi;
import android.content.Context;

/**
 * Helper for {@link Context}.
 *
 * @hide
 */
@SystemApi(client = SystemApi.Client.MODULE_LIBRARIES)
public final class ContextHelper {
    private ContextHelper() {
        throw new UnsupportedOperationException();
    }

    /** Returns display id relevant for the context */
    public static int getDisplayId(@NonNull Context context) {
        return context.getDisplayId();
    }
}
