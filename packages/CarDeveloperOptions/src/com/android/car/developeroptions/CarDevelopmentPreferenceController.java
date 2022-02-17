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

package com.android.car.developeroptions;

import android.annotation.Nullable;
import android.content.Context;

import androidx.preference.Preference;

import com.android.settings.core.PreferenceControllerMixin;
import com.android.settingslib.development.DeveloperOptionsPreferenceController;

/**
 * Parent class for Car feature preferences.*
 */
public abstract class CarDevelopmentPreferenceController extends
        DeveloperOptionsPreferenceController implements Preference.OnPreferenceChangeListener,
        PreferenceControllerMixin {

    abstract String getPreferenceTitle();
    @Nullable
    abstract String getPreferenceSummary();

    CarDevelopmentPreferenceController(Context context) {
        super(context);
    }
}
