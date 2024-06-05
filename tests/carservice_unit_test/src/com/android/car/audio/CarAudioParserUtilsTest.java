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

package com.android.car.audio;

import static org.junit.Assert.assertThrows;

import android.car.test.AbstractExpectableTestCase;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class CarAudioParserUtilsTest extends AbstractExpectableTestCase {

    @Test
    public void parsePositiveLongAttributeWithInvalidLongString() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> CarAudioParserUtils.parsePositiveLongAttribute(/* attribute*/ "testAttribute",
                        /* longString= */ "5.0"));

        expectWithMessage("Parsing invalid long string exception").that(thrown).hasMessageThat()
                .contains("must be a positive long");
    }
}
