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

package com.android.car.hal;

import static android.car.VehiclePropertyIds.CLUSTER_DISPLAY_STATE;
import static android.car.VehiclePropertyIds.CLUSTER_NAVIGATION_STATE_LEGACY;
import static android.car.VehiclePropertyIds.CLUSTER_REPORT_STATE;
import static android.car.VehiclePropertyIds.CLUSTER_REQUEST_DISPLAY;
import static android.car.VehiclePropertyIds.CLUSTER_SWITCH_UI;
import static android.car.test.util.VehicleHalTestingHelper.newSubscribableConfig;

import static com.android.car.hal.ClusterHalService.DONT_CARE;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.graphics.Insets;
import android.hardware.automotive.vehicle.V2_0.VehiclePropValue;

import com.android.car.hal.ClusterHalService.ClusterHalEventCallback;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;

@RunWith(MockitoJUnitRunner.class)
public class ClusterHalServiceTest {
    private static final int NOT_ASSIGNED = -1;

    private static final int ON = 1;
    private static final int UI_TYPE_1 = 1;
    private static final int UI_TYPE_2 = 2;
    private static final byte[] UI_AVAILABILITY = new byte[] {(byte) 1, (byte) 1, (byte) 0};

    private static final int WIDTH = 800;
    private static final int HEIGHT = 600;
    private static final int INSET_LEFT = 20;
    private static final int INSET_TOP = 10;
    private static final int INSET_RIGHT = 780;
    private static final int INSET_BOTTOM = 590;

    @Mock
    VehicleHal mVehicleHal;
    @Captor
    ArgumentCaptor<VehiclePropValue> mPropCaptor;

    private ClusterHalService mClusterHalService;

    int mUiType = NOT_ASSIGNED;
    int mOnOff = NOT_ASSIGNED;
    int mWidth = NOT_ASSIGNED;
    int mHeight = NOT_ASSIGNED;
    Insets mInsets = null;

    private final ClusterHalEventCallback mHalEventListener = new ClusterHalEventCallback() {
        public void onSwitchUi(int uiType) {
            mUiType = uiType;
        }

        public void onDisplayState(int onOff, int width, int height, Insets insets) {
            mOnOff = onOff;
            mWidth = width;
            mHeight = height;
            mInsets = insets;
        }
    };

    @Before
    public void setUp() {
        mClusterHalService = new ClusterHalService(mVehicleHal);
        mClusterHalService.takeProperties(Arrays.asList(
                newSubscribableConfig(CLUSTER_SWITCH_UI),
                newSubscribableConfig(CLUSTER_DISPLAY_STATE),
                newSubscribableConfig(CLUSTER_REPORT_STATE),
                newSubscribableConfig(CLUSTER_REQUEST_DISPLAY),
                newSubscribableConfig(CLUSTER_NAVIGATION_STATE_LEGACY)));

        mClusterHalService.init();
        mClusterHalService.setCallback(mHalEventListener);
    }

    @After
    public void tearDown() {
        mClusterHalService.release();
        mClusterHalService = null;
    }

    @Test
    public void testInit_subscribeProperty() {
        assertThat(mClusterHalService.isCoreSupported()).isTrue();
        verify(mVehicleHal).subscribeProperty(mClusterHalService, CLUSTER_SWITCH_UI);
        verify(mVehicleHal).subscribeProperty(mClusterHalService, CLUSTER_DISPLAY_STATE);
    }

    @Test
    public void testTakeProperties_noProperties() {
        mClusterHalService.takeProperties(Arrays.asList());
        assertThat(mClusterHalService.isCoreSupported()).isFalse();
        assertThat(mClusterHalService.isNavigationStateSupported()).isFalse();
    }

    @Test
    public void testTakeProperties_doNotTakePartialProperties() {
        mClusterHalService.takeProperties(Arrays.asList(
                newSubscribableConfig(CLUSTER_SWITCH_UI),
                newSubscribableConfig(CLUSTER_DISPLAY_STATE),
                newSubscribableConfig(CLUSTER_REPORT_STATE)));
        assertThat(mClusterHalService.isCoreSupported()).isFalse();
        assertThat(mClusterHalService.isNavigationStateSupported()).isFalse();
    }

    @Test
    public void testTakeProperties_coreProperties() {
        mClusterHalService.takeProperties(Arrays.asList(
                newSubscribableConfig(CLUSTER_SWITCH_UI),
                newSubscribableConfig(CLUSTER_DISPLAY_STATE),
                newSubscribableConfig(CLUSTER_REPORT_STATE),
                newSubscribableConfig(CLUSTER_REQUEST_DISPLAY)));
        assertThat(mClusterHalService.isCoreSupported()).isTrue();
        assertThat(mClusterHalService.isNavigationStateSupported()).isFalse();
    }

    @Test
    public void testTakeProperties_fullProperties() {
        mClusterHalService.takeProperties(Arrays.asList(
                newSubscribableConfig(CLUSTER_SWITCH_UI),
                newSubscribableConfig(CLUSTER_DISPLAY_STATE),
                newSubscribableConfig(CLUSTER_REPORT_STATE),
                newSubscribableConfig(CLUSTER_REQUEST_DISPLAY),
                newSubscribableConfig(CLUSTER_NAVIGATION_STATE_LEGACY)));
        assertThat(mClusterHalService.isCoreSupported()).isTrue();
        assertThat(mClusterHalService.isNavigationStateSupported()).isTrue();
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

    @Test
    public void testOnSwitchUi() {
        mClusterHalService.onHalEvents(Arrays.asList(
                createSwitchUiEvent(UI_TYPE_1)));

        assertThat(mUiType).isEqualTo(UI_TYPE_1);
    }

    @Test
    public void testOnSwitchUi_noListener() {
        mClusterHalService.setCallback(null);

        mClusterHalService.onHalEvents(Arrays.asList(
                createSwitchUiEvent(UI_TYPE_1)));

        assertThat(mUiType).isEqualTo(NOT_ASSIGNED);
    }

    @Test
    public void testOnSwitchUi_noProperties() {
        mClusterHalService.takeProperties(Arrays.asList());

        mClusterHalService.onHalEvents(Arrays.asList(
                createSwitchUiEvent(UI_TYPE_1)));

        assertThat(mUiType).isEqualTo(NOT_ASSIGNED);
    }

    @Test
    public void testOnDisplayState() {
        mClusterHalService.onHalEvents(Arrays.asList(
                createDisplayStateEvent(ON, WIDTH, HEIGHT,
                        INSET_LEFT, INSET_TOP, INSET_RIGHT, INSET_BOTTOM)));

        assertThat(mOnOff).isEqualTo(ON);
        assertThat(mWidth).isEqualTo(WIDTH);
        assertThat(mHeight).isEqualTo(HEIGHT);
        assertThat(mInsets.left).isEqualTo(INSET_LEFT);
        assertThat(mInsets.top).isEqualTo(INSET_TOP);
        assertThat(mInsets.right).isEqualTo(INSET_RIGHT);
        assertThat(mInsets.bottom).isEqualTo(INSET_BOTTOM);
    }

    @Test
    public void testOnDisplayState_DontAcceptPartialDontCare_Height() {
        mClusterHalService.onHalEvents(Arrays.asList(
                createDisplayStateEvent(ON, WIDTH, DONT_CARE,
                        INSET_LEFT, INSET_TOP, INSET_RIGHT, INSET_BOTTOM)));

        assertThat(mOnOff).isEqualTo(ON);
        assertThat(mWidth).isEqualTo(NOT_ASSIGNED);
        assertThat(mHeight).isEqualTo(NOT_ASSIGNED);
        assertThat(mInsets.left).isEqualTo(INSET_LEFT);
        assertThat(mInsets.top).isEqualTo(INSET_TOP);
        assertThat(mInsets.right).isEqualTo(INSET_RIGHT);
        assertThat(mInsets.bottom).isEqualTo(INSET_BOTTOM);
    }

    @Test
    public void testOnDisplayState_DontAcceptPartialDontCare_Inset() {
        mClusterHalService.onHalEvents(Arrays.asList(
                createDisplayStateEvent(ON, WIDTH, HEIGHT,
                        INSET_LEFT, INSET_TOP, INSET_RIGHT, DONT_CARE)));

        assertThat(mOnOff).isEqualTo(ON);
        assertThat(mWidth).isEqualTo(WIDTH);
        assertThat(mHeight).isEqualTo(HEIGHT);
        assertThat(mInsets).isNull();
    }

    @Test
    public void testOnDisplayState_noListener() {
        mClusterHalService.setCallback(null);

        mClusterHalService.onHalEvents(Arrays.asList(
                createDisplayStateEvent(ON, WIDTH, HEIGHT,
                        INSET_LEFT, INSET_TOP, INSET_RIGHT, INSET_BOTTOM)));

        assertThat(mOnOff).isEqualTo(NOT_ASSIGNED);
        assertThat(mWidth).isEqualTo(NOT_ASSIGNED);
        assertThat(mHeight).isEqualTo(NOT_ASSIGNED);
        assertThat(mInsets).isNull();
    }

    @Test
    public void testOnDisplayState_noProperties() {
        mClusterHalService.takeProperties(Arrays.asList());

        mClusterHalService.onHalEvents(Arrays.asList(
                createDisplayStateEvent(ON, WIDTH, HEIGHT,
                        INSET_LEFT, INSET_TOP, INSET_RIGHT, INSET_BOTTOM)));

        assertThat(mOnOff).isEqualTo(NOT_ASSIGNED);
        assertThat(mWidth).isEqualTo(NOT_ASSIGNED);
        assertThat(mHeight).isEqualTo(NOT_ASSIGNED);
        assertThat(mInsets).isNull();
    }

    @Test
    public void testReportState() {
        mClusterHalService.reportState(ON, WIDTH, HEIGHT,
                Insets.of(INSET_LEFT, INSET_TOP, INSET_RIGHT, INSET_BOTTOM),
                UI_TYPE_1, UI_TYPE_2, UI_AVAILABILITY);

        verify(mVehicleHal).set(mPropCaptor.capture());
        VehiclePropValue prop = mPropCaptor.getValue();
        assertThat(prop.prop).isEqualTo(CLUSTER_REPORT_STATE);
        assertThat(prop.value.int32Values).containsExactly(ON, WIDTH, HEIGHT,
                INSET_LEFT, INSET_TOP, INSET_RIGHT, INSET_BOTTOM, UI_TYPE_1, UI_TYPE_2);
        assertThat(prop.value.bytes).containsExactly(
                (Byte) UI_AVAILABILITY[0], (Byte) UI_AVAILABILITY[1], (Byte) UI_AVAILABILITY[2]);
    }

    @Test
    public void testReportState_noProperties() {
        mClusterHalService.takeProperties(Arrays.asList());

        mClusterHalService.reportState(ON, WIDTH, HEIGHT,
                Insets.of(INSET_LEFT, INSET_TOP, INSET_RIGHT, INSET_BOTTOM),
                UI_TYPE_1, UI_TYPE_2, UI_AVAILABILITY);

        verify(mVehicleHal, times(0)).set(mPropCaptor.capture());
    }

    @Test
    public void testRequestDisplay() {
        mClusterHalService.requestDisplay(UI_TYPE_2);

        verify(mVehicleHal).set(mPropCaptor.capture());
        VehiclePropValue prop = mPropCaptor.getValue();
        assertThat(prop.prop).isEqualTo(CLUSTER_REQUEST_DISPLAY);
        assertThat(prop.value.int32Values).containsExactly(UI_TYPE_2);
    }

    @Test
    public void testRequestDisplay_noProperties() {
        mClusterHalService.takeProperties(Arrays.asList());

        mClusterHalService.requestDisplay(UI_TYPE_2);

        verify(mVehicleHal, times(0)).set(mPropCaptor.capture());
    }

    @Test
    public void testSendNavigationState() {
        mClusterHalService.sendNavigationState(new byte[]{1, 2, 3, 4});

        verify(mVehicleHal).set(mPropCaptor.capture());
        VehiclePropValue prop = mPropCaptor.getValue();
        assertThat(prop.prop).isEqualTo(CLUSTER_NAVIGATION_STATE_LEGACY);
        assertThat(prop.value.bytes).containsExactly((byte) 1, (byte) 2, (byte) 3, (byte) 4);
    }

    @Test
    public void testSendNavigationState_noProperties() {
        mClusterHalService.takeProperties(Arrays.asList());

        mClusterHalService.sendNavigationState(new byte[]{1, 2, 3, 4});

        verify(mVehicleHal, times(0)).set(mPropCaptor.capture());
    }
}
