/*
 * Copyright (C) 2007 The Android Open Source Project
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

package com.android.car.internal.util;

import android.util.ArrayMap;

import java.util.HashMap;

// Copy from frameworks/base/core/java/com/google/android/collect

/**
 * Provides static methods for creating mutable {@code Maps} instances easily.
 *
 * @hide
 */
public class Maps {
    /**
     * Creates a {@code HashMap} instance.
     *
     * @return a newly-created, initially-empty {@code HashMap}
     */
    public static <K, V> HashMap<K, V> newHashMap() {
        return new HashMap<K, V>();
    }

    /**
     * Creates a {@code ArrayMap} instance.
     */
    public static <K, V> ArrayMap<K, V> newArrayMap() {
        return new ArrayMap<K, V>();
    }
}
