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
package com.android.car.internal.util;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.car.PlatformVersion;
import android.car.PlatformVersionMismatchException;

import org.junit.Test;

public class VersionUtilsTest {

    @Test
    public void testMismatchVersionException() {
        assertThrows(PlatformVersionMismatchException.class,
                () -> VersionUtils.assertPlatformVersionAtLeast(
                        PlatformVersion.forMajorAndMinorVersions(Integer.MAX_VALUE,
                                Integer.MAX_VALUE)));
    }

    @Test
    public void testNoExceptionForCorrectVersion() {
        VersionUtils.assertPlatformVersionAtLeast(
                PlatformVersion.forMajorAndMinorVersions(33, 0));
    }

    @Test
    public void testIsPlatformVersionAtLeastSuccess() {
        assertThat(VersionUtils.isPlatformVersionAtLeast(
                PlatformVersion.forMajorAndMinorVersions(33, 0))).isTrue();
    }

    @Test
    public void testIsPlatformVersionAtLeastFailure() {
        assertThat(VersionUtils.isPlatformVersionAtLeast(
                PlatformVersion.forMajorAndMinorVersions(Integer.MAX_VALUE, Integer.MAX_VALUE)))
                        .isFalse();
    }

}
