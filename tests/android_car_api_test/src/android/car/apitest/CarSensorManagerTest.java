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

package android.car.apitest;

import android.car.Car;
import android.car.hardware.CarSensorManager;
import android.car.test.ApiCheckerRule.Builder;
import android.test.suitebuilder.annotation.MediumTest;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;

@MediumTest
public class CarSensorManagerTest extends CarApiTestBase {

    // TODO(b/242350638): add missing annotations, remove (on child bug of 242350638)
    @Override
    protected void configApiCheckerRule(Builder builder) {
        builder.disableAnnotationsCheck();
    }

    @Test
    public void testCreate() throws Exception {
        CarSensorManager carSensorManager = (CarSensorManager) getCar()
                .getCarManager(Car.SENSOR_SERVICE);
        assertThat(carSensorManager).isNotNull();
    }

}
