/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.car;

import static android.car.Car.CAR_REMOTE_DEVICE_SERVICE;
import static android.car.Car.PERMISSION_MANAGE_REMOTE_DEVICE;
import static android.car.CarOccupantZoneManager.OCCUPANT_TYPE_DRIVER;
import static android.car.VehicleAreaSeat.SEAT_ROW_1_LEFT;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.car.Car;
import android.car.CarOccupantZoneManager.OccupantZoneInfo;
import android.car.CarRemoteDeviceManager;
import android.content.Context;
import android.os.Handler;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Objects;

/**
 * This class contains security permission tests for {@link CarRemoteDeviceManager}' system
 * APIs.
 */
@RunWith(AndroidJUnit4.class)
public final class CarRemoteDeviceManagerPermissionTest {
    private final Context mContext =
            InstrumentationRegistry.getInstrumentation().getTargetContext();

    private CarRemoteDeviceManager mCarRemoteDeviceManager;
    private OccupantZoneInfo mReceiverZone;

    @Before
    public void setUp() {
        Car car = Objects.requireNonNull(Car.createCar(mContext, (Handler) null));
        mCarRemoteDeviceManager = (CarRemoteDeviceManager) car.getCarManager(
                CAR_REMOTE_DEVICE_SERVICE);
        mReceiverZone = new OccupantZoneInfo(/* zoneId= */ 0, OCCUPANT_TYPE_DRIVER,
                SEAT_ROW_1_LEFT);
    }

    @Test
    public void testRegisterOccupantZoneStateCallback() {
        Exception e = assertThrows(SecurityException.class,
                () -> mCarRemoteDeviceManager.registerOccupantZoneStateCallback(Runnable::run,
                        (occupantZone, occupantZoneStates) -> {}));

        assertThat(e).hasMessageThat().contains(PERMISSION_MANAGE_REMOTE_DEVICE);
    }

    @Test
    public void testGetEndpointPackageInfo() {
        Exception e = assertThrows(SecurityException.class,
                () -> mCarRemoteDeviceManager.getEndpointPackageInfo(mReceiverZone));

        assertThat(e).hasMessageThat().contains(PERMISSION_MANAGE_REMOTE_DEVICE);
    }

    @Test
    public void testControlOccupantZonePower() {
        Exception e = assertThrows(SecurityException.class,
                () -> mCarRemoteDeviceManager.controlOccupantZonePower(mReceiverZone,
                        /* powerOn= */ true));

        assertThat(e).hasMessageThat().contains(PERMISSION_MANAGE_REMOTE_DEVICE);
    }

    @Test
    public void testIsOccupantZonePowerOn() {
        Exception e = assertThrows(SecurityException.class,
                () -> mCarRemoteDeviceManager.isOccupantZonePowerOn(mReceiverZone));

        assertThat(e).hasMessageThat().contains(PERMISSION_MANAGE_REMOTE_DEVICE);
    }
}
