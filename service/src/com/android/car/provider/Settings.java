/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.car.provider;

import android.content.ContentResolver;
import android.net.Uri;
import android.provider.Settings.SettingNotFoundException;

/**
 * An interface to stub methods from {@link android.provider.Settings}.
 */
public interface Settings {
    /**
     * @see android.provider.Settings.System#getInt
     */
    int systemGetInt(ContentResolver cr, String name) throws SettingNotFoundException;

    /**
     * @see android.provider.Settings.System#getUriFor
     */
    Uri systemGetUriFor(String name);

    /**
     * The default real implementation.
     */
    class DefaultImpl implements Settings {
        @Override
        public int systemGetInt(ContentResolver cr, String name) throws SettingNotFoundException {
            return android.provider.Settings.System.getInt(cr, name);
        }

        @Override
        public Uri systemGetUriFor(String name) {
            return android.provider.Settings.System.getUriFor(name);
        }
    }
}
