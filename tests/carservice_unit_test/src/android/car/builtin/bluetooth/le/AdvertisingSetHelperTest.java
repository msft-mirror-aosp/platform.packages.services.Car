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

package android.car.builtin.bluetooth.le;

import static org.mockito.Mockito.verify;

import android.bluetooth.le.AdvertisingSet;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public final class AdvertisingSetHelperTest {

    @Mock
    private AdvertisingSet mMockAdvertisingSet;

    @Test
    public void getOwnAddress_delegate() {
        AdvertisingSetHelper.getOwnAddress(mMockAdvertisingSet);

        verify(mMockAdvertisingSet).getOwnAddress();
    }
}
