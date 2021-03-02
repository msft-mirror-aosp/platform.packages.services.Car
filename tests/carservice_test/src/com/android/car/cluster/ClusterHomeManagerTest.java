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

package com.android.car.cluster;

import static android.car.VehiclePropertyIds.CLUSTER_DISPLAY_STATE;
import static android.car.VehiclePropertyIds.CLUSTER_SWITCH_UI;

import static com.android.car.hal.ClusterHalService.DISPLAY_ON;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.car.Car;
import android.car.cluster.ClusterHomeManager;
import android.car.cluster.ClusterState;
import android.hardware.automotive.vehicle.V2_0.VehiclePropValue;
import android.hardware.automotive.vehicle.V2_0.VehicleProperty;
import android.hardware.automotive.vehicle.V2_0.VehiclePropertyAccess;
import android.hardware.automotive.vehicle.V2_0.VehiclePropertyChangeMode;
import android.hardware.display.DisplayManager;
import android.os.SystemClock;
import android.util.Log;
import android.util.SparseArray;
import android.view.Display;
import android.view.DisplayAddress;

import com.android.car.MockedCarTestBase;
import com.android.car.vehiclehal.VehiclePropValueBuilder;
import com.android.car.vehiclehal.test.MockedVehicleHal;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class ClusterHomeManagerTest extends MockedCarTestBase {
    private static final String TAG = ClusterHomeManagerTest.class.getSimpleName();
    private static final long TEST_TIMEOUT_MS = 5_000;

    private static final int UI_TYPE_1 = 1;
    private static final int UI_TYPE_2 = 2;
    private static final byte[] UI_AVAILABILITY = new byte[] {(byte) 1, (byte) 0, (byte) 1};

    private static final int WIDTH = 800;
    private static final int HEIGHT = 600;
    private static final int INSET_LEFT = 20;
    private static final int INSET_TOP = 10;
    private static final int INSET_RIGHT = 780;
    private static final int INSET_BOTTOM = 590;

    private static final String[] ENABLED_OPTIONAL_FEATURES = {
            Car.CLUSTER_HOME_SERVICE
    };

    private ClusterHomeManager mClusterHomeManager;
    private final ClusterPropertyHandler mPropertyHandler = new ClusterPropertyHandler();
    private final CountDownLatch mPropertySetReady = new CountDownLatch(1);
    private final CountDownLatch mCallbackReceived = new CountDownLatch(1);

    private ClusterHomeCallbackImpl mClusterHomeCallback = new ClusterHomeCallbackImpl();
    private ClusterState mState;
    private int mChanges = 0;
    private byte[] mNavigationState;

    @Rule
    public final TestName mTestName = new TestName();

    private boolean isNoHalPropertyTest() {
        return mTestName.getMethodName().contains("testNoHalProperty");
    }

    @Override
    protected synchronized void configureMockedHal() {
        if (isNoHalPropertyTest()) return;

        addProperty(VehicleProperty.CLUSTER_SWITCH_UI)
                .setChangeMode(VehiclePropertyChangeMode.ON_CHANGE).build();
        addProperty(VehicleProperty.CLUSTER_DISPLAY_STATE)
                .setChangeMode(VehiclePropertyChangeMode.ON_CHANGE).build();
        addProperty(VehicleProperty.CLUSTER_REPORT_STATE, mPropertyHandler)
                .setAccess(VehiclePropertyAccess.WRITE)
                .setChangeMode(VehiclePropertyChangeMode.ON_CHANGE).build();
        addProperty(VehicleProperty.CLUSTER_REQUEST_DISPLAY, mPropertyHandler)
                .setAccess(VehiclePropertyAccess.WRITE)
                .setChangeMode(VehiclePropertyChangeMode.ON_CHANGE).build();
        addProperty(VehicleProperty.CLUSTER_NAVIGATION_STATE_LEGACY, mPropertyHandler)
                .setAccess(VehiclePropertyAccess.WRITE)
                .setChangeMode(VehiclePropertyChangeMode.ON_CHANGE).build();
    }

    @Override
    protected synchronized void configureResourceOverrides(
            MockedCarTestBase.MockResources resources) {
        super.configureResourceOverrides(resources);
        StringBuilder occupantDisplayMapping = new StringBuilder();
        occupantDisplayMapping.append("displayPort=");
        occupantDisplayMapping.append(getDefaultDisplayPort());
        occupantDisplayMapping.append(",displayType=INSTRUMENT_CLUSTER,occupantZoneId=0");
        resources.overrideResource(com.android.car.R.array.config_occupant_display_mapping,
                new String[] {occupantDisplayMapping.toString()});
        resources.overrideResource(com.android.car.R.array.config_allowed_optional_car_features,
                ENABLED_OPTIONAL_FEATURES);
    }

    private int getDefaultDisplayPort() {
        DisplayManager displayManager = getTestContext().getSystemService(DisplayManager.class);
        Display defaultDisplay = displayManager.getDisplay(Display.DEFAULT_DISPLAY);
        DisplayAddress address = (DisplayAddress.Physical) defaultDisplay.getAddress();
        if (!(address instanceof DisplayAddress.Physical)) {
            throw new IllegalStateException("Default display is not a physical display");
        }
        DisplayAddress.Physical physicalAddress = (DisplayAddress.Physical) address;
        return physicalAddress.getPort();
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mClusterHomeManager = (ClusterHomeManager) getCar().getCarManager(Car.CLUSTER_HOME_SERVICE);
        if (!isNoHalPropertyTest() && mClusterHomeManager != null) {
            mClusterHomeManager.registerClusterHomeCallback(
                    getContext().getMainExecutor(), mClusterHomeCallback);
        }
    }

    @Override
    public void tearDown() throws Exception {
        if (!isNoHalPropertyTest() && mClusterHomeManager != null) {
            mClusterHomeManager.unregisterClusterHomeCallback(mClusterHomeCallback);
        }
        super.tearDown();
    }

    @Test
    public void testClusterSwitchUi() throws InterruptedException {
        getMockedVehicleHal().injectEvent(createSwitchUiEvent(UI_TYPE_2));
        mCallbackReceived.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);

        assertThat(mState).isNotNull();
        assertThat(mState.uiType).isEqualTo(UI_TYPE_2);
        assertThat(mChanges).isEqualTo(ClusterHomeManager.CONFIG_UI_TYPE);
    }

    @Test
    public void testClusterState() throws InterruptedException {
        getMockedVehicleHal().injectEvent(createDisplayStateEvent(
                DISPLAY_ON, WIDTH, HEIGHT, INSET_LEFT, INSET_TOP, INSET_RIGHT, INSET_BOTTOM));
        mCallbackReceived.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);

        assertThat(mState).isNotNull();
        assertThat(mState.on).isEqualTo(true);
        assertThat(mState.width).isEqualTo(WIDTH);
        assertThat(mState.height).isEqualTo(HEIGHT);
        assertThat(mState.insets.left).isEqualTo(INSET_LEFT);
        assertThat(mState.insets.top).isEqualTo(INSET_TOP);
        assertThat(mState.insets.right).isEqualTo(INSET_RIGHT);
        assertThat(mState.insets.bottom).isEqualTo(INSET_BOTTOM);
        assertThat(mChanges).isEqualTo(ClusterHomeManager.CONFIG_DISPLAY_ON_OFF
                | ClusterHomeManager.CONFIG_DISPLAY_SIZE
                | ClusterHomeManager.CONFIG_DISPLAY_INSETS);

        ClusterState state2 = mClusterHomeManager.getClusterState();
        assertThat(state2).isNotNull();
        // The class generated from aidl doesn't have the proper equals() method yet.
        assertThat(state2.on).isEqualTo(mState.on);
        assertThat(state2.width).isEqualTo(mState.width);
        assertThat(state2.height).isEqualTo(mState.height);
        assertThat(state2.insets).isEqualTo(mState.insets);
    }

    @Test
    public void testClusterReportState() throws InterruptedException {
        mClusterHomeManager.reportState(UI_TYPE_1, UI_TYPE_2, UI_AVAILABILITY);
        mPropertySetReady.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);

        VehiclePropValue value = mPropertyHandler.peek(VehicleProperty.CLUSTER_REPORT_STATE);
        assertThat(value.prop).isEqualTo(VehicleProperty.CLUSTER_REPORT_STATE);
        assertThat(value.value.int32Values.subList(7, 9)).containsExactly(UI_TYPE_1, UI_TYPE_2);
        assertThat(value.value.bytes).containsExactly(
                (Byte) UI_AVAILABILITY[0], (Byte) UI_AVAILABILITY[1], (Byte) UI_AVAILABILITY[2]);
    }

    @Test
    public void testClusterRequestDisplay() throws InterruptedException {
        mClusterHomeManager.requestDisplay(UI_TYPE_1);
        mPropertySetReady.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);

        VehiclePropValue value = mPropertyHandler.peek(VehicleProperty.CLUSTER_REQUEST_DISPLAY);
        assertThat(value.prop).isEqualTo(VehicleProperty.CLUSTER_REQUEST_DISPLAY);
        assertThat(value.value.int32Values).containsExactly(UI_TYPE_1);
    }

    @Test
    public void testNoHalProperty_throwsException() {
        assertThrows(IllegalStateException.class,
                () -> mClusterHomeManager.reportState(UI_TYPE_1, UI_TYPE_2, UI_AVAILABILITY));
        assertThrows(IllegalStateException.class,
                () -> mClusterHomeManager.requestDisplay(UI_TYPE_1));
        assertThrows(IllegalStateException.class,
                () -> mClusterHomeManager.registerClusterHomeCallback(
                        getContext().getMainExecutor(), mClusterHomeCallback));
        assertThrows(IllegalStateException.class,
                () -> mClusterHomeManager.unregisterClusterHomeCallback(mClusterHomeCallback));
    }

    private class ClusterPropertyHandler implements MockedVehicleHal.VehicleHalPropertyHandler {
        SparseArray<VehiclePropValue> mMap = new SparseArray<>();

        @Override
        public synchronized void onPropertySet(VehiclePropValue value) {
            mMap.put(value.prop, value);
            mPropertySetReady.countDown();
        }

        @Override
        public synchronized VehiclePropValue onPropertyGet(VehiclePropValue value) {
            VehiclePropValue currentValue = mMap.get(value.prop);
            // VNS will call get method when subscribe is called, just return empty value.
            return currentValue != null ? currentValue : value;
        }

        public synchronized VehiclePropValue peek(int property) {
            return mMap.get(property);
        }

        @Override
        public synchronized void onPropertySubscribe(int property, float sampleRate) {
            Log.d(TAG, "onPropertySubscribe property " + property + " sampleRate " + sampleRate);
            if (mMap.get(property) == null) {
                Log.d(TAG, "onPropertySubscribe add placeholder property: " + property);
                VehiclePropValue placeholderValue = VehiclePropValueBuilder.newBuilder(property)
                        .setAreaId(0)
                        .setTimestamp(SystemClock.elapsedRealtimeNanos())
                        .addIntValue(1)
                        .addFloatValue(1)
                        .build();
                mMap.put(property, placeholderValue);
            }
        }

        @Override
        public synchronized void onPropertyUnsubscribe(int property) {
            Log.d(TAG, "onPropertyUnSubscribe property " + property);
        }
    }

    private class ClusterHomeCallbackImpl implements ClusterHomeManager.ClusterHomeCallback {
        public void onClusterStateChanged(ClusterState state, int changes) {
            mState = state;
            mChanges = changes;
            mCallbackReceived.countDown();
        }
        public void onNavigationState(byte[] navigationState) {
            mNavigationState = navigationState;
            mCallbackReceived.countDown();
        }
    }

    private static VehiclePropValue createSwitchUiEvent(int uiType) {
        VehiclePropValue event = new VehiclePropValue();
        event.prop = CLUSTER_SWITCH_UI;
        event.value.int32Values.add(uiType);
        return event;
    }

    private static VehiclePropValue createDisplayStateEvent(int onOff, int width, int height,
            int left, int top, int right, int bottom) {
        VehiclePropValue event = new VehiclePropValue();
        event.prop = CLUSTER_DISPLAY_STATE;
        event.value.int32Values.add(onOff);
        event.value.int32Values.add(width);
        event.value.int32Values.add(height);
        event.value.int32Values.add(left);
        event.value.int32Values.add(top);
        event.value.int32Values.add(right);
        event.value.int32Values.add(bottom);
        return event;
    }
}
