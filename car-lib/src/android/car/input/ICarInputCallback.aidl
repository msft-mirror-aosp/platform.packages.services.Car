/*
 * Copyright (C) 2020 The Android Open Source Project
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
package android.car.input;

import android.car.input.CustomInputEvent;
import android.car.input.RotaryEvent;
import android.view.KeyEvent;

/**
 * Binder API for Input Service.
 *
 * @hide
 */
oneway interface ICarInputCallback {
    void onKeyEvents(int targetDisplayType, in List<KeyEvent> keyEvents) = 1;
    void onRotaryEvents(int targetDisplayType, in List<RotaryEvent> events) = 2;
    void onCaptureStateChanged(int targetDisplayType, in int[] activeInputTypes) = 3;
    void onCustomInputEvents(int targetDisplayType, in List<CustomInputEvent> events) = 4;
}
