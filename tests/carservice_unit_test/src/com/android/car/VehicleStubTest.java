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

package com.android.car;

import static android.car.VehiclePropertyIds.HVAC_TEMPERATURE_SET;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.hardware.property.CarPropertyManager;
import android.car.test.mocks.JavaMockitoHelper;
import android.hardware.automotive.vehicle.GetValueRequest;
import android.hardware.automotive.vehicle.GetValueRequests;
import android.hardware.automotive.vehicle.GetValueResult;
import android.hardware.automotive.vehicle.GetValueResults;
import android.hardware.automotive.vehicle.IVehicle;
import android.hardware.automotive.vehicle.IVehicleCallback;
import android.hardware.automotive.vehicle.RawPropValues;
import android.hardware.automotive.vehicle.SetValueRequest;
import android.hardware.automotive.vehicle.SetValueRequests;
import android.hardware.automotive.vehicle.SetValueResult;
import android.hardware.automotive.vehicle.SetValueResults;
import android.hardware.automotive.vehicle.StatusCode;
import android.hardware.automotive.vehicle.SubscribeOptions;
import android.hardware.automotive.vehicle.V2_0.IVehicle.getCallback;
import android.hardware.automotive.vehicle.V2_0.IVehicle.getPropConfigsCallback;
import android.hardware.automotive.vehicle.V2_0.VehiclePropertyStatus;
import android.hardware.automotive.vehicle.VehiclePropConfig;
import android.hardware.automotive.vehicle.VehiclePropConfigs;
import android.hardware.automotive.vehicle.VehiclePropError;
import android.hardware.automotive.vehicle.VehiclePropErrors;
import android.hardware.automotive.vehicle.VehiclePropValue;
import android.hardware.automotive.vehicle.VehiclePropValues;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.IBinder.DeathRecipient;
import android.os.RemoteException;
import android.os.ServiceSpecificException;
import android.util.SparseArray;

import com.android.car.VehicleStub.GetVehicleStubAsyncRequest;
import com.android.car.hal.HalClientCallback;
import com.android.car.hal.HalPropConfig;
import com.android.car.hal.HalPropValue;
import com.android.car.hal.HalPropValueBuilder;
import com.android.car.hal.HidlHalPropConfig;
import com.android.car.internal.LargeParcelable;
import com.android.compatibility.common.util.PollingCheck;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.FileDescriptor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;

@RunWith(MockitoJUnitRunner.class)
public class VehicleStubTest {

    private static final int TEST_PROP = 1;
    private static final int TEST_ACCESS = 2;
    private static final float TEST_SAMPLE_RATE = 3.0f;
    private static final int TEST_VALUE = 3;
    private static final int TEST_AREA = 4;
    private static final int TEST_STATUS = 5;

    private static final int VHAL_PROP_SUPPORTED_PROPERTY_IDS = 0x11410F48;

    @Mock
    private IVehicle mAidlVehicle;
    @Mock
    private IBinder mAidlBinder;
    @Mock
    private android.hardware.automotive.vehicle.V2_0.IVehicle mHidlVehicle;
    @Mock
    private VehicleStub.VehicleStubCallbackInterface mGetVehicleStubAsyncCallback;

    private AidlVehicleStub mAidlVehicleStub;
    private VehicleStub mHidlVehicleStub;

    private final HandlerThread mHandlerThread = new HandlerThread(
            VehicleStubTest.class.getSimpleName());
    private Handler mHandler;

    private int[] getTestIntValues(int length) {
        int[] values = new int[length];
        for (int i = 0; i < length; i++) {
            values[i] = TEST_VALUE;
        }
        return values;
    }

    private GetVehicleStubAsyncRequest defaultVehicleStubAsyncRequest(HalPropValue value) {
        return new GetVehicleStubAsyncRequest(/* serviceRequestId=*/ 0, value,
                /* timeoutInMs= */ 1000);
    }

    @Before
    public void setUp() {
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());

        when(mAidlVehicle.asBinder()).thenReturn(mAidlBinder);

        mAidlVehicleStub = new AidlVehicleStub(mAidlVehicle, mHandlerThread);
        mHidlVehicleStub = new HidlVehicleStub(mHidlVehicle);

        assertThat(mAidlVehicleStub.isValid()).isTrue();
        assertThat(mHidlVehicleStub.isValid()).isTrue();
    }

    @After
    public void tearDown() {
        // Remove all pending messages in the handler queue.
        mHandlerThread.quitSafely();
    }

    @Test
    public void testGetInterfaceDescriptorHidl() throws Exception {
        mHidlVehicleStub.getInterfaceDescriptor();

        verify(mHidlVehicle).interfaceDescriptor();
    }

    @Test
    public void testGetInterfaceDescriptorAidl() throws Exception {
        mAidlVehicleStub.getInterfaceDescriptor();

        verify(mAidlBinder).getInterfaceDescriptor();
    }

    @Test(expected = IllegalStateException.class)
    public void testGetInterfaceDescriptorRemoteException() throws Exception {
        when(mAidlBinder.getInterfaceDescriptor()).thenThrow(new RemoteException());

        mAidlVehicleStub.getInterfaceDescriptor();
    }

    @Test
    public void testLinkToDeathHidl() throws Exception {
        IVehicleDeathRecipient recipient = mock(IVehicleDeathRecipient.class);

        mHidlVehicleStub.linkToDeath(recipient);

        verify(mHidlVehicle).linkToDeath(recipient, 0);
    }

    @Test
    public void testLinkToDeathAidl() throws Exception {
        IVehicleDeathRecipient recipient = mock(IVehicleDeathRecipient.class);

        mAidlVehicleStub.linkToDeath(recipient);

        verify(mAidlBinder).linkToDeath(recipient, 0);
    }

    @Test(expected = IllegalStateException.class)
    public void testLinkToDeathRemoteException() throws Exception {
        IVehicleDeathRecipient recipient = mock(IVehicleDeathRecipient.class);
        doThrow(new RemoteException()).when(mAidlBinder).linkToDeath(recipient, 0);

        mAidlVehicleStub.linkToDeath(recipient);
    }

    @Test
    public void testUnlinkToDeathHidl() throws Exception {
        IVehicleDeathRecipient recipient = mock(IVehicleDeathRecipient.class);

        mHidlVehicleStub.unlinkToDeath(recipient);

        verify(mHidlVehicle).unlinkToDeath(recipient);
    }

    @Test
    public void testUnlinkToDeathAidl() throws Exception {
        IVehicleDeathRecipient recipient = mock(IVehicleDeathRecipient.class);

        mAidlVehicleStub.unlinkToDeath(recipient);

        verify(mAidlBinder).unlinkToDeath(recipient, 0);
    }

    @Test
    public void testUnlinkToDeathRemoteException() throws Exception {
        IVehicleDeathRecipient recipient = mock(IVehicleDeathRecipient.class);
        doThrow(new RemoteException()).when(mHidlVehicle).unlinkToDeath(recipient);

        mHidlVehicleStub.unlinkToDeath(recipient);
    }

    @Test
    public void testGetAllPropConfigsHidl() throws Exception {
        ArrayList<android.hardware.automotive.vehicle.V2_0.VehiclePropConfig> hidlConfigs = new
                ArrayList<android.hardware.automotive.vehicle.V2_0.VehiclePropConfig>();
        android.hardware.automotive.vehicle.V2_0.VehiclePropConfig hidlConfig =
                new android.hardware.automotive.vehicle.V2_0.VehiclePropConfig();
        hidlConfig.prop = TEST_PROP;
        hidlConfig.access = TEST_ACCESS;
        hidlConfigs.add(hidlConfig);

        doAnswer(inv -> {
            getPropConfigsCallback callback = (getPropConfigsCallback) inv.getArgument(1);
            callback.onValues(StatusCode.INVALID_ARG, /* configs = */ null);
            return null;
        }).when(mHidlVehicle).getPropConfigs(
                eq(new ArrayList<>(Arrays.asList(VHAL_PROP_SUPPORTED_PROPERTY_IDS))), any());
        when(mHidlVehicle.getAllPropConfigs()).thenReturn(hidlConfigs);

        HalPropConfig[] configs = mHidlVehicleStub.getAllPropConfigs();

        assertThat(configs.length).isEqualTo(1);
        assertThat(configs[0].getPropId()).isEqualTo(TEST_PROP);
        assertThat(configs[0].getAccess()).isEqualTo(TEST_ACCESS);
    }

    @Test
    public void testGetAllPropConfigsHidlMultipleRequests() throws Exception {
        ArrayList<Integer> supportedPropIds = new ArrayList(Arrays.asList(
                VHAL_PROP_SUPPORTED_PROPERTY_IDS, 1, 2, 3, 4));
        int numConfigsPerRequest = 2;
        android.hardware.automotive.vehicle.V2_0.VehiclePropValue requestPropValue =
                new android.hardware.automotive.vehicle.V2_0.VehiclePropValue();
        requestPropValue.prop = VHAL_PROP_SUPPORTED_PROPERTY_IDS;

        SparseArray<android.hardware.automotive.vehicle.V2_0.VehiclePropConfig>
                expectedConfigsById = new SparseArray<>();
        for (int i = 0; i < supportedPropIds.size(); i++) {
            int propId = supportedPropIds.get(i);
            android.hardware.automotive.vehicle.V2_0.VehiclePropConfig config =
                    new android.hardware.automotive.vehicle.V2_0.VehiclePropConfig();
            config.prop = propId;
            if (propId == VHAL_PROP_SUPPORTED_PROPERTY_IDS) {
                config.configArray = new ArrayList(Arrays.asList(numConfigsPerRequest));
            }
            expectedConfigsById.put(propId, config);
        }

        // Return the supported IDs in get().
        doAnswer(inv -> {
            getCallback callback = (getCallback) inv.getArgument(1);
            android.hardware.automotive.vehicle.V2_0.VehiclePropValue propValue =
                    new android.hardware.automotive.vehicle.V2_0.VehiclePropValue();
            propValue.prop =
                    ((android.hardware.automotive.vehicle.V2_0.VehiclePropValue) inv.getArgument(0))
                    .prop;
            propValue.value.int32Values = supportedPropIds;
            callback.onValues(android.hardware.automotive.vehicle.V2_0.StatusCode.OK, propValue);
            return null;
        }).when(mHidlVehicle).get(eq(requestPropValue), any());

        // Return the appropriate configs in getPropConfigs().
        doAnswer(inv -> {
            ArrayList<Integer> requestPropIds = (ArrayList<Integer>) inv.getArgument(0);
            getPropConfigsCallback callback = (getPropConfigsCallback) inv.getArgument(1);
            ArrayList<android.hardware.automotive.vehicle.V2_0.VehiclePropConfig> configs =
                    new ArrayList<>();
            for (int j = 0; j < requestPropIds.size(); j++) {
                int propId = requestPropIds.get(j);
                configs.add(expectedConfigsById.get(propId));
            }
            callback.onValues(android.hardware.automotive.vehicle.V2_0.StatusCode.OK, configs);
            return null;
        }).when(mHidlVehicle).getPropConfigs(any(), any());

        HalPropConfig[] configs = mHidlVehicleStub.getAllPropConfigs();

        HalPropConfig[] expectedConfigs = new HalPropConfig[expectedConfigsById.size()];
        for (int i = 0; i < expectedConfigsById.size(); i++) {
            expectedConfigs[i] = new HidlHalPropConfig(expectedConfigsById.valueAt(i));
        }
        // Order does not matter.
        Comparator<HalPropConfig> configCmp =
                (config1, config2) -> (config1.getPropId() - config2.getPropId());
        Arrays.sort(configs, configCmp);
        Arrays.sort(expectedConfigs, configCmp);

        assertThat(configs.length).isEqualTo(expectedConfigs.length);
        for (int i = 0; i < configs.length; i++) {
            assertThat(configs[i].getPropId()).isEqualTo(expectedConfigs[i].getPropId());
        }
        verify(mHidlVehicle, never()).getAllPropConfigs();
        ArgumentCaptor<ArrayList<Integer>> captor = ArgumentCaptor.forClass(ArrayList.class);
        // The first request to check whether the property is supported.
        // Next 3 requests are sub requests.
        verify(mHidlVehicle, times(4)).getPropConfigs(captor.capture(), any());
    }

    @Test
    public void testGetAllPropConfigsHidlMultipleRequestsNoConfig() throws Exception {
        doAnswer(inv -> {
            getPropConfigsCallback callback = (getPropConfigsCallback) inv.getArgument(1);
            android.hardware.automotive.vehicle.V2_0.VehiclePropConfig config =
                    new android.hardware.automotive.vehicle.V2_0.VehiclePropConfig();
            // No config array.
            config.prop = VHAL_PROP_SUPPORTED_PROPERTY_IDS;
            callback.onValues(android.hardware.automotive.vehicle.V2_0.StatusCode.OK,
                    new ArrayList(Arrays.asList(config)));
            return null;
        }).when(mHidlVehicle).getPropConfigs(
                eq(new ArrayList<>(Arrays.asList(VHAL_PROP_SUPPORTED_PROPERTY_IDS))), any());

        assertThrows(IllegalArgumentException.class, () -> mHidlVehicleStub.getAllPropConfigs());
        verify(mHidlVehicle, never()).getAllPropConfigs();
    }

    @Test
    public void testGetAllPropConfigsHidlMultipleRequestsInvalidConfig() throws Exception {
        doAnswer(inv -> {
            getPropConfigsCallback callback = (getPropConfigsCallback) inv.getArgument(1);
            android.hardware.automotive.vehicle.V2_0.VehiclePropConfig config =
                    new android.hardware.automotive.vehicle.V2_0.VehiclePropConfig();
            // NumConfigsPerRequest is not a valid number.
            config.configArray = new ArrayList(Arrays.asList(0));
            config.prop = VHAL_PROP_SUPPORTED_PROPERTY_IDS;
            callback.onValues(android.hardware.automotive.vehicle.V2_0.StatusCode.OK,
                    new ArrayList(Arrays.asList(config)));
            return null;
        }).when(mHidlVehicle).getPropConfigs(
                eq(new ArrayList<>(Arrays.asList(VHAL_PROP_SUPPORTED_PROPERTY_IDS))), any());

        assertThrows(IllegalArgumentException.class, () -> mHidlVehicleStub.getAllPropConfigs());
        verify(mHidlVehicle, never()).getAllPropConfigs();
    }

    @Test
    public void testGetAllPropConfigsHidlMultipleRequestsGetValueInvalidArg() throws Exception {
        doAnswer(inv -> {
            getPropConfigsCallback callback = (getPropConfigsCallback) inv.getArgument(1);
            android.hardware.automotive.vehicle.V2_0.VehiclePropConfig config =
                    new android.hardware.automotive.vehicle.V2_0.VehiclePropConfig();
            config.configArray = new ArrayList(Arrays.asList(1));
            config.prop = VHAL_PROP_SUPPORTED_PROPERTY_IDS;
            callback.onValues(android.hardware.automotive.vehicle.V2_0.StatusCode.OK,
                    new ArrayList(Arrays.asList(config)));
            return null;
        }).when(mHidlVehicle).getPropConfigs(
                eq(new ArrayList<>(Arrays.asList(VHAL_PROP_SUPPORTED_PROPERTY_IDS))), any());

        doAnswer(inv -> {
            getCallback callback = (getCallback) inv.getArgument(1);
            callback.onValues(
                    android.hardware.automotive.vehicle.V2_0.StatusCode.INVALID_ARG,
                    /* configs= */ null);
            return null;
        }).when(mHidlVehicle).get(any(), any());

        assertThrows(ServiceSpecificException.class, () -> mHidlVehicleStub.getAllPropConfigs());
        verify(mHidlVehicle, never()).getAllPropConfigs();
    }

    @Test
    public void testGetAllPropConfigsHidlMultipleRequestsNoConfigReturned() throws Exception {
        doAnswer(inv -> {
            getPropConfigsCallback callback = (getPropConfigsCallback) inv.getArgument(1);
            callback.onValues(android.hardware.automotive.vehicle.V2_0.StatusCode.OK,
                    new ArrayList<android.hardware.automotive.vehicle.V2_0.VehiclePropConfig>());
            return null;
        }).when(mHidlVehicle).getPropConfigs(
                eq(new ArrayList<>(Arrays.asList(VHAL_PROP_SUPPORTED_PROPERTY_IDS))), any());
        when(mHidlVehicle.getAllPropConfigs()).thenReturn(
                new ArrayList<android.hardware.automotive.vehicle.V2_0.VehiclePropConfig>());

        mHidlVehicleStub.getAllPropConfigs();

        // Must fall back to getAllPropConfigs when no config is returned for
        // VHAL_PROP_SUPPORTED_PROPERTY_IDS;
        verify(mHidlVehicle).getAllPropConfigs();
    }

    @Test
    public void testGetAllPropConfigsHidlMultipleRequestsGetValueError() throws Exception {
        doAnswer(inv -> {
            getPropConfigsCallback callback = (getPropConfigsCallback) inv.getArgument(1);
            android.hardware.automotive.vehicle.V2_0.VehiclePropConfig config =
                    new android.hardware.automotive.vehicle.V2_0.VehiclePropConfig();
            config.configArray = new ArrayList(Arrays.asList(1));
            config.prop = VHAL_PROP_SUPPORTED_PROPERTY_IDS;
            callback.onValues(android.hardware.automotive.vehicle.V2_0.StatusCode.OK,
                    new ArrayList(Arrays.asList(config)));
            return null;
        }).when(mHidlVehicle).getPropConfigs(
                eq(new ArrayList<>(Arrays.asList(VHAL_PROP_SUPPORTED_PROPERTY_IDS))), any());

        doAnswer(inv -> {
            getCallback callback = (getCallback) inv.getArgument(1);
            callback.onValues(android.hardware.automotive.vehicle.V2_0.StatusCode.INTERNAL_ERROR,
                    null);
            return null;
        }).when(mHidlVehicle).get(any(), any());

        assertThrows(ServiceSpecificException.class, () -> mHidlVehicleStub.getAllPropConfigs());
        verify(mHidlVehicle, never()).getAllPropConfigs();
    }

    @Test
    public void testGetAllPropConfigsHidlMultipleRequestsGetValueUnavailable() throws Exception {
        doAnswer(inv -> {
            getPropConfigsCallback callback = (getPropConfigsCallback) inv.getArgument(1);
            android.hardware.automotive.vehicle.V2_0.VehiclePropConfig config =
                    new android.hardware.automotive.vehicle.V2_0.VehiclePropConfig();
            config.configArray = new ArrayList(Arrays.asList(1));
            config.prop = VHAL_PROP_SUPPORTED_PROPERTY_IDS;
            callback.onValues(android.hardware.automotive.vehicle.V2_0.StatusCode.OK,
                    new ArrayList(Arrays.asList(config)));
            return null;
        }).when(mHidlVehicle).getPropConfigs(
                eq(new ArrayList<>(Arrays.asList(VHAL_PROP_SUPPORTED_PROPERTY_IDS))), any());

        doAnswer(inv -> {
            getCallback callback = (getCallback) inv.getArgument(1);
            android.hardware.automotive.vehicle.V2_0.VehiclePropValue value =
                    new android.hardware.automotive.vehicle.V2_0.VehiclePropValue();
            value.status = VehiclePropertyStatus.UNAVAILABLE;
            callback.onValues(android.hardware.automotive.vehicle.V2_0.StatusCode.OK, value);
            return null;
        }).when(mHidlVehicle).get(any(), any());

        ServiceSpecificException exception = assertThrows(ServiceSpecificException.class,
                () -> mHidlVehicleStub.getAllPropConfigs());
        assertThat(exception.errorCode).isEqualTo(
                android.hardware.automotive.vehicle.V2_0.StatusCode.INTERNAL_ERROR);
        verify(mHidlVehicle, never()).getAllPropConfigs();
    }

    @Test
    public void testGetAllProdConfigsAidlSmallData() throws Exception {
        VehiclePropConfigs aidlConfigs = new VehiclePropConfigs();
        VehiclePropConfig aidlConfig = new VehiclePropConfig();
        aidlConfig.prop = TEST_PROP;
        aidlConfig.access = TEST_ACCESS;
        aidlConfigs.sharedMemoryFd = null;
        aidlConfigs.payloads = new VehiclePropConfig[]{aidlConfig};

        when(mAidlVehicle.getAllPropConfigs()).thenReturn(aidlConfigs);

        HalPropConfig[] configs = mAidlVehicleStub.getAllPropConfigs();

        assertThat(configs.length).isEqualTo(1);
        assertThat(configs[0].getPropId()).isEqualTo(TEST_PROP);
        assertThat(configs[0].getAccess()).isEqualTo(TEST_ACCESS);
    }

    @Test
    public void testGetAllPropConfigsAidlLargeData() throws Exception {
        int configSize = 1000;
        VehiclePropConfigs aidlConfigs = new VehiclePropConfigs();
        VehiclePropConfig aidlConfig = new VehiclePropConfig();
        aidlConfig.prop = TEST_PROP;
        aidlConfig.access = TEST_ACCESS;
        aidlConfigs.payloads = new VehiclePropConfig[configSize];
        for (int i = 0; i < configSize; i++) {
            aidlConfigs.payloads[i] = aidlConfig;
        }

        aidlConfigs = (VehiclePropConfigs) LargeParcelable.toLargeParcelable(aidlConfigs, () -> {
            VehiclePropConfigs newConfigs = new VehiclePropConfigs();
            newConfigs.payloads = new VehiclePropConfig[0];
            return newConfigs;
        });

        assertThat(aidlConfigs.sharedMemoryFd).isNotNull();

        when(mAidlVehicle.getAllPropConfigs()).thenReturn(aidlConfigs);

        HalPropConfig[] configs = mAidlVehicleStub.getAllPropConfigs();

        assertThat(configs.length).isEqualTo(configSize);
        for (int i = 0; i < configSize; i++) {
            assertThat(configs[i].getPropId()).isEqualTo(TEST_PROP);
            assertThat(configs[i].getAccess()).isEqualTo(TEST_ACCESS);
        }
    }

    @Test
    public void testSubscribeHidl() throws Exception {
        SubscribeOptions aidlOptions = new SubscribeOptions();
        aidlOptions.propId = TEST_PROP;
        aidlOptions.sampleRate = TEST_SAMPLE_RATE;
        android.hardware.automotive.vehicle.V2_0.SubscribeOptions hidlOptions =
                new android.hardware.automotive.vehicle.V2_0.SubscribeOptions();
        hidlOptions.propId = TEST_PROP;
        hidlOptions.sampleRate = TEST_SAMPLE_RATE;
        hidlOptions.flags = android.hardware.automotive.vehicle.V2_0.SubscribeFlags.EVENTS_FROM_CAR;

        HalClientCallback callback = mock(HalClientCallback.class);
        VehicleStub.SubscriptionClient client = mHidlVehicleStub.newSubscriptionClient(callback);

        client.subscribe(new SubscribeOptions[]{aidlOptions});

        verify(mHidlVehicle).subscribe(
                (android.hardware.automotive.vehicle.V2_0.IVehicleCallback.Stub) client,
                new ArrayList<android.hardware.automotive.vehicle.V2_0.SubscribeOptions>(
                        Arrays.asList(hidlOptions)));
    }

    @Test
    public void testSubscribeAidl() throws Exception {
        SubscribeOptions option = new SubscribeOptions();
        option.propId = TEST_PROP;
        option.sampleRate = TEST_SAMPLE_RATE;
        SubscribeOptions[] options = new SubscribeOptions[]{option};

        HalClientCallback callback = mock(HalClientCallback.class);
        VehicleStub.SubscriptionClient client = mAidlVehicleStub.newSubscriptionClient(callback);

        client.subscribe(options);

        verify(mAidlVehicle).subscribe((IVehicleCallback) client, options,
                /*maxSharedMemoryFileCount=*/2);
    }

    @Test
    public void testUnsubscribeHidl() throws Exception {
        HalClientCallback callback = mock(HalClientCallback.class);
        VehicleStub.SubscriptionClient client = mHidlVehicleStub.newSubscriptionClient(callback);

        client.unsubscribe(TEST_PROP);

        verify(mHidlVehicle).unsubscribe(
                (android.hardware.automotive.vehicle.V2_0.IVehicleCallback.Stub) client, TEST_PROP);
    }

    @Test
    public void testUnsubscribeAidl() throws Exception {
        HalClientCallback callback = mock(HalClientCallback.class);
        VehicleStub.SubscriptionClient client = mAidlVehicleStub.newSubscriptionClient(callback);

        client.unsubscribe(TEST_PROP);

        verify(mAidlVehicle).unsubscribe((IVehicleCallback) client, new int[]{TEST_PROP});
    }

    @Test
    public void testGetHidlAsync() throws Exception {
        doAnswer((invocation) -> {
            Object[] args = invocation.getArguments();
            android.hardware.automotive.vehicle.V2_0.VehiclePropValue propValue =
                    (android.hardware.automotive.vehicle.V2_0.VehiclePropValue) args[0];
            assertThat(propValue.prop).isEqualTo(TEST_PROP);
            android.hardware.automotive.vehicle.V2_0.IVehicle.getCallback callback =
                    (android.hardware.automotive.vehicle.V2_0.IVehicle.getCallback) args[1];
            callback.onValues(StatusCode.OK, propValue);
            return null;
        }).when(mHidlVehicle).get(any(), any());

        HalPropValueBuilder builder = new HalPropValueBuilder(/*isAidl=*/false);
        HalPropValue value = builder.build(TEST_PROP, 0, TEST_VALUE);

        GetVehicleStubAsyncRequest getVehicleStubAsyncRequest =
                defaultVehicleStubAsyncRequest(value);

        mHidlVehicleStub.getAsync(List.of(getVehicleStubAsyncRequest),
                mGetVehicleStubAsyncCallback);

        ArgumentCaptor<List<VehicleStub.GetVehicleStubAsyncResult>> argumentCaptor =
                ArgumentCaptor.forClass(List.class);

        verify(mGetVehicleStubAsyncCallback, timeout(1000)).onGetAsyncResults(
                argumentCaptor.capture());
        assertThat(argumentCaptor.getValue().get(0).getHalPropValue()).isEqualTo(value);
    }

    @Test
    public void testGetHidlAsyncError() throws Exception {
        doAnswer((invocation) -> {
            Object[] args = invocation.getArguments();
            android.hardware.automotive.vehicle.V2_0.VehiclePropValue propValue =
                    (android.hardware.automotive.vehicle.V2_0.VehiclePropValue) args[0];
            assertThat(propValue.prop).isEqualTo(TEST_PROP);
            android.hardware.automotive.vehicle.V2_0.IVehicle.getCallback callback =
                    (android.hardware.automotive.vehicle.V2_0.IVehicle.getCallback) args[1];
            callback.onValues(StatusCode.INVALID_ARG, propValue);
            return null;
        }).when(mHidlVehicle).get(any(), any());

        HalPropValueBuilder builder = new HalPropValueBuilder(/*isAidl=*/false);
        HalPropValue value = builder.build(TEST_PROP, 0, TEST_VALUE);
        GetVehicleStubAsyncRequest getVehicleStubAsyncRequest =
                defaultVehicleStubAsyncRequest(value);
        ArgumentCaptor<List<VehicleStub.GetVehicleStubAsyncResult>> argumentCaptor =
                ArgumentCaptor.forClass(List.class);

        mHidlVehicleStub.getAsync(List.of(getVehicleStubAsyncRequest),
                mGetVehicleStubAsyncCallback);

        verify(mGetVehicleStubAsyncCallback, timeout(1000)).onGetAsyncResults(
                argumentCaptor.capture());
        assertThat(argumentCaptor.getValue().get(0).getErrorCode()).isEqualTo(
                CarPropertyManager.STATUS_ERROR_INTERNAL_ERROR);
    }

    @Test
    public void testGetHidlAsyncTryAgainError() throws Exception {
        doAnswer((invocation) -> {
            Object[] args = invocation.getArguments();
            android.hardware.automotive.vehicle.V2_0.VehiclePropValue propValue =
                    (android.hardware.automotive.vehicle.V2_0.VehiclePropValue) args[0];
            assertThat(propValue.prop).isEqualTo(TEST_PROP);
            android.hardware.automotive.vehicle.V2_0.IVehicle.getCallback callback =
                    (android.hardware.automotive.vehicle.V2_0.IVehicle.getCallback) args[1];
            callback.onValues(StatusCode.TRY_AGAIN, propValue);
            return null;
        }).when(mHidlVehicle).get(any(), any());

        HalPropValueBuilder builder = new HalPropValueBuilder(/*isAidl=*/false);
        HalPropValue value = builder.build(TEST_PROP, 0, TEST_VALUE);
        GetVehicleStubAsyncRequest getVehicleStubAsyncRequest =
                defaultVehicleStubAsyncRequest(value);
        ArgumentCaptor<List<VehicleStub.GetVehicleStubAsyncResult>> argumentCaptor =
                ArgumentCaptor.forClass(List.class);

        mHidlVehicleStub.getAsync(List.of(getVehicleStubAsyncRequest),
                mGetVehicleStubAsyncCallback);

        verify(mGetVehicleStubAsyncCallback, timeout(1000)).onGetAsyncResults(
                argumentCaptor.capture());
        assertThat(argumentCaptor.getValue().get(0).getErrorCode()).isEqualTo(
                VehicleStub.STATUS_TRY_AGAIN);
    }

    @Test
    public void testGetHidl() throws Exception {
        doAnswer((invocation) -> {
            Object[] args = invocation.getArguments();
            android.hardware.automotive.vehicle.V2_0.VehiclePropValue propValue =
                    (android.hardware.automotive.vehicle.V2_0.VehiclePropValue) args[0];
            assertThat(propValue.prop).isEqualTo(TEST_PROP);
            android.hardware.automotive.vehicle.V2_0.IVehicle.getCallback callback =
                    (android.hardware.automotive.vehicle.V2_0.IVehicle.getCallback) args[1];
            callback.onValues(StatusCode.OK, propValue);
            return null;
        }).when(mHidlVehicle).get(any(), any());

        HalPropValueBuilder builder = new HalPropValueBuilder(/*isAidl=*/false);
        HalPropValue value = builder.build(TEST_PROP, 0, TEST_VALUE);

        HalPropValue gotValue = mHidlVehicleStub.get(value);

        assertThat(gotValue).isEqualTo(value);
    }

    @Test(expected = ServiceSpecificException.class)
    public void testGetHidlError() throws Exception {
        doAnswer((invocation) -> {
            Object[] args = invocation.getArguments();
            android.hardware.automotive.vehicle.V2_0.VehiclePropValue propValue =
                    (android.hardware.automotive.vehicle.V2_0.VehiclePropValue) args[0];
            assertThat(propValue.prop).isEqualTo(TEST_PROP);
            android.hardware.automotive.vehicle.V2_0.IVehicle.getCallback callback =
                    (android.hardware.automotive.vehicle.V2_0.IVehicle.getCallback) args[1];
            callback.onValues(StatusCode.INVALID_ARG, propValue);
            return null;
        }).when(mHidlVehicle).get(any(), any());

        HalPropValueBuilder builder = new HalPropValueBuilder(/*isAidl=*/false);
        HalPropValue value = builder.build(TEST_PROP, 0, TEST_VALUE);

        mHidlVehicleStub.get(value);
    }

    @Test
    public void testGetAidlSmallData() throws Exception {
        doAnswer((invocation) -> {
            Object[] args = invocation.getArguments();
            GetValueRequests requests = (GetValueRequests) args[1];
            assertThat(requests.payloads.length).isEqualTo(1);
            GetValueRequest request = requests.payloads[0];
            assertThat(request.requestId).isEqualTo(0);
            assertThat(request.prop.prop).isEqualTo(TEST_PROP);
            IVehicleCallback.Stub callback = (IVehicleCallback.Stub) args[0];

            GetValueResults results = createGetValueResults(StatusCode.OK, requests.payloads);

            callback.onGetValues(results);
            return null;
        }).when(mAidlVehicle).getValues(any(), any());

        HalPropValueBuilder builder = new HalPropValueBuilder(/*isAidl=*/true);
        HalPropValue value = builder.build(TEST_PROP, 0, TEST_VALUE);

        HalPropValue gotValue = mAidlVehicleStub.get(value);

        assertThat(gotValue).isEqualTo(value);
        assertThat(mAidlVehicleStub.countPendingRequests()).isEqualTo(0);
    }

    @Test
    public void testGetAidlLargeData() throws Exception {
        int dataSize = 2000;
        doAnswer((invocation) -> {
            Object[] args = invocation.getArguments();
            GetValueRequests requests = (GetValueRequests) args[1];
            assertThat(requests.payloads.length).isEqualTo(0);
            assertThat(requests.sharedMemoryFd).isNotNull();
            requests = (GetValueRequests)
                    LargeParcelable.reconstructStableAIDLParcelable(
                            requests, /*keepSharedMemory=*/false);
            assertThat(requests.payloads.length).isEqualTo(1);
            GetValueRequest request = requests.payloads[0];

            GetValueResults results = createGetValueResults(StatusCode.OK, requests.payloads);

            results = (GetValueResults) LargeParcelable.toLargeParcelable(
                    results, () -> {
                        GetValueResults newResults = new GetValueResults();
                        newResults.payloads = new GetValueResult[0];
                        return newResults;
                    });

            assertThat(results.sharedMemoryFd).isNotNull();

            IVehicleCallback.Stub callback = (IVehicleCallback.Stub) args[0];
            callback.onGetValues(results);
            return null;
        }).when(mAidlVehicle).getValues(any(), any());

        HalPropValueBuilder builder = new HalPropValueBuilder(/*isAidl=*/true);
        HalPropValue value = builder.build(TEST_PROP, 0, 0, 0, getTestIntValues(dataSize));

        HalPropValue gotValue = mAidlVehicleStub.get(value);

        assertThat(gotValue).isEqualTo(value);
        assertThat(mAidlVehicleStub.countPendingRequests()).isEqualTo(0);
    }

    @Test(expected = ServiceSpecificException.class)
    public void testGetAidlError() throws Exception {
        doAnswer((invocation) -> {
            Object[] args = invocation.getArguments();
            GetValueRequests requests = (GetValueRequests) args[1];
            assertThat(requests.payloads.length).isEqualTo(1);
            GetValueRequest request = requests.payloads[0];
            assertThat(request.requestId).isEqualTo(0);
            assertThat(request.prop.prop).isEqualTo(TEST_PROP);
            IVehicleCallback.Stub callback = (IVehicleCallback.Stub) args[0];

            GetValueResults results = createGetValueResults(StatusCode.INVALID_ARG,
                    requests.payloads);

            callback.onGetValues(results);
            return null;
        }).when(mAidlVehicle).getValues(any(), any());

        HalPropValueBuilder builder = new HalPropValueBuilder(/*isAidl=*/true);
        HalPropValue value = builder.build(TEST_PROP, 0, TEST_VALUE);

        mAidlVehicleStub.get(value);
        assertThat(mAidlVehicleStub.countPendingRequests()).isEqualTo(0);
    }

    @Test
    public void testGetAidlAsyncCallback() throws Exception {
        doAnswer((invocation) -> {
            Object[] args = invocation.getArguments();
            GetValueRequests requests = (GetValueRequests) args[1];
            assertThat(requests.payloads.length).isEqualTo(1);
            GetValueRequest request = requests.payloads[0];
            assertThat(request.requestId).isEqualTo(0);
            assertThat(request.prop.prop).isEqualTo(TEST_PROP);
            IVehicleCallback.Stub callback = (IVehicleCallback.Stub) args[0];

            GetValueResults results = createGetValueResults(StatusCode.OK, requests.payloads);

            // Call callback after 100ms.
            mHandler.postDelayed(() -> {
                try {
                    callback.onGetValues(results);
                } catch (RemoteException e) {
                    // ignore.
                }
            }, /* delayMillis= */ 100);
            return null;
        }).when(mAidlVehicle).getValues(any(), any());

        HalPropValueBuilder builder = new HalPropValueBuilder(/*isAidl=*/true);
        HalPropValue value = builder.build(TEST_PROP, 0, TEST_VALUE);

        HalPropValue gotValue = mAidlVehicleStub.get(value);

        assertThat(gotValue).isEqualTo(value);
        assertThat(mAidlVehicleStub.countPendingRequests()).isEqualTo(0);
    }

    private GetValueResults createGetValueResults(int status, GetValueRequest[] requests) {
        GetValueResults results = new GetValueResults();
        results.payloads = new GetValueResult[requests.length];
        for (int i = 0; i < requests.length; i++) {
            GetValueResult result = new GetValueResult();
            result.status = status;
            if (status == StatusCode.OK) {
                result.prop = requests[i].prop;
            }
            result.requestId = requests[i].requestId;
            results.payloads[i] = result;
        }
        return results;
    }

    private HalPropValue testAidlHvacPropValue() {
        HalPropValueBuilder builder = new HalPropValueBuilder(/*isAidl=*/true);
        return builder.build(HVAC_TEMPERATURE_SET, 0, 17.0f);
    }

    @Test
    public void testGetAsyncAidlCallback() throws RemoteException {
        doAnswer((invocation) -> {
            Object[] args = invocation.getArguments();
            GetValueRequests requests = (GetValueRequests) args[1];
            assertThat(requests.payloads.length).isEqualTo(1);
            GetValueRequest request = requests.payloads[0];
            assertThat(request.requestId).isEqualTo(0);
            assertThat(request.prop.prop).isEqualTo(HVAC_TEMPERATURE_SET);
            IVehicleCallback.Stub callback = (IVehicleCallback.Stub) args[0];
            GetValueResults results = createGetValueResults(StatusCode.OK, requests.payloads);

            // Call callback after 100ms.
            mHandler.postDelayed(() -> {
                try {
                    callback.onGetValues(results);
                } catch (RemoteException e) {
                    // ignore.
                }
            }, /* delayMillis= */ 100);
            return null;
        }).when(mAidlVehicle).getValues(any(), any());

        HalPropValue value = testAidlHvacPropValue();

        GetVehicleStubAsyncRequest getVehicleStubAsyncRequest =
                defaultVehicleStubAsyncRequest(value);

        mAidlVehicleStub.getAsync(List.of(getVehicleStubAsyncRequest),
                mGetVehicleStubAsyncCallback);

        ArgumentCaptor<List<VehicleStub.GetVehicleStubAsyncResult>> argumentCaptor =
                ArgumentCaptor.forClass(List.class);

        verify(mGetVehicleStubAsyncCallback, timeout(1000)).onGetAsyncResults(
                argumentCaptor.capture());
        assertThat(argumentCaptor.getValue().get(0).getHalPropValue()).isEqualTo(value);
    }

    @Test
    public void testGetAsyncAidlError() throws RemoteException {
        doAnswer((invocation) -> {
            Object[] args = invocation.getArguments();
            GetValueRequests requests = (GetValueRequests) args[1];
            assertThat(requests.payloads.length).isEqualTo(1);
            GetValueRequest request = requests.payloads[0];
            assertThat(request.requestId).isEqualTo(0);
            assertThat(request.prop.prop).isEqualTo(HVAC_TEMPERATURE_SET);
            IVehicleCallback.Stub callback = (IVehicleCallback.Stub) args[0];

            GetValueResults results = createGetValueResults(StatusCode.INTERNAL_ERROR,
                    requests.payloads);

            // Call callback after 100ms.
            mHandler.postDelayed(() -> {
                try {
                    callback.onGetValues(results);
                } catch (RemoteException e) {
                    // ignore.
                }
            }, /* delayMillis= */ 100);
            return null;
        }).when(mAidlVehicle).getValues(any(), any());

        HalPropValue value = testAidlHvacPropValue();

        GetVehicleStubAsyncRequest getVehicleStubAsyncRequest =
                defaultVehicleStubAsyncRequest(value);

        mAidlVehicleStub.getAsync(List.of(getVehicleStubAsyncRequest),
                mGetVehicleStubAsyncCallback);

        ArgumentCaptor<List<VehicleStub.GetVehicleStubAsyncResult>> argumentCaptor =
                ArgumentCaptor.forClass(List.class);

        verify(mGetVehicleStubAsyncCallback, timeout(1000)).onGetAsyncResults(
                argumentCaptor.capture());
        assertThat(argumentCaptor.getValue().get(0).getErrorCode()).isEqualTo(
                CarPropertyManager.STATUS_ERROR_INTERNAL_ERROR);
    }

    private void createGetAsyncAidlException(int errorCode) throws Exception {
        HalPropValueBuilder builder = new HalPropValueBuilder(/* isAidl= */ true);
        HalPropValue value = builder.build(HVAC_TEMPERATURE_SET, 0, 17.0f);

        GetVehicleStubAsyncRequest getVehicleStubAsyncRequest =
                defaultVehicleStubAsyncRequest(value);
        ServiceSpecificException exception = new ServiceSpecificException(
                errorCode);
        doThrow(exception).when(mAidlVehicle).getValues(any(), any());

        mAidlVehicleStub.getAsync(List.of(getVehicleStubAsyncRequest),
                mGetVehicleStubAsyncCallback);
    }

    @Test
    public void testGetAsyncAidlServiceSpecificExceptionInternalError() throws Exception {
        createGetAsyncAidlException(StatusCode.INTERNAL_ERROR);

        ArgumentCaptor<List<VehicleStub.GetVehicleStubAsyncResult>> argumentCaptor =
                ArgumentCaptor.forClass(List.class);

        verify(mGetVehicleStubAsyncCallback, timeout(1000)).onGetAsyncResults(
                argumentCaptor.capture());
        assertThat(argumentCaptor.getValue().get(0).getErrorCode()).isEqualTo(
                CarPropertyManager.STATUS_ERROR_INTERNAL_ERROR);
    }

    @Test
    public void testGetAsyncAidlServiceSpecificExceptionTryAgainError() throws Exception {
        createGetAsyncAidlException(StatusCode.TRY_AGAIN);

        ArgumentCaptor<List<VehicleStub.GetVehicleStubAsyncResult>> argumentCaptor =
                ArgumentCaptor.forClass(List.class);

        verify(mGetVehicleStubAsyncCallback, timeout(1000)).onGetAsyncResults(
                argumentCaptor.capture());
        assertThat(argumentCaptor.getValue().get(0).getErrorCode()).isEqualTo(
                VehicleStub.STATUS_TRY_AGAIN);
    }

    @Test
    public void testGetAsyncAidlServiceSpecificExceptionNotAvailable() throws Exception {
        createGetAsyncAidlException(StatusCode.NOT_AVAILABLE);

        ArgumentCaptor<List<VehicleStub.GetVehicleStubAsyncResult>> argumentCaptor =
                ArgumentCaptor.forClass(List.class);

        verify(mGetVehicleStubAsyncCallback, timeout(1000)).onGetAsyncResults(
                argumentCaptor.capture());
        assertThat(argumentCaptor.getValue().get(0).getErrorCode()).isEqualTo(
                CarPropertyManager.STATUS_ERROR_NOT_AVAILABLE);
    }

    private void postOnBinderDied(CountDownLatch latch, DeathRecipient deathRecipient) {
        mHandler.post(() -> {
            deathRecipient.binderDied();
            latch.countDown();
        });
    }

    private void mockGetValues(List<IVehicleCallback.Stub> callbackWrapper,
            List<GetValueResults> resultsWrapper) throws Exception {
        doAnswer((invocation) -> {
            Object[] args = invocation.getArguments();
            GetValueRequests requests = (GetValueRequests) args[1];
            IVehicleCallback.Stub callback = (IVehicleCallback.Stub) args[0];
            GetValueResults results = createGetValueResults(StatusCode.OK, requests.payloads);
            callbackWrapper.add(callback);
            resultsWrapper.add(results);
            return null;
        }).when(mAidlVehicle).getValues(any(), any());
    }

    private void triggerCallback(List<IVehicleCallback.Stub> callbackWrapper,
            List<GetValueResults> resultsWrapper) throws Exception {
        assertWithMessage("callback wrapper").that(callbackWrapper).hasSize(1);
        assertWithMessage("results wrapper").that(resultsWrapper).hasSize(1);
        callbackWrapper.get(0).onGetValues(resultsWrapper.get(0));
    }

    // Test that the client information is cleaned-up and client callback will not be called if
    // client's binder died after the getAsync request.
    @Test
    public void testGetAsyncAidlBinderDiedAfterRegisterCallback() throws Exception {
        List<IVehicleCallback.Stub> callbackWrapper = new ArrayList<>();
        List<GetValueResults> resultsWrapper = new ArrayList<>();
        List<DeathRecipient> recipientWrapper = new ArrayList<>();
        mockGetValues(callbackWrapper, resultsWrapper);
        doAnswer((invocation) -> {
            recipientWrapper.add((DeathRecipient) invocation.getArguments()[0]);
            return null;
        }).when(mGetVehicleStubAsyncCallback).linkToDeath(any());

        HalPropValue value = testAidlHvacPropValue();

        GetVehicleStubAsyncRequest getVehicleStubAsyncRequest =
                defaultVehicleStubAsyncRequest(value);

        // Send the getAsync request.
        mAidlVehicleStub.getAsync(List.of(getVehicleStubAsyncRequest),
                mGetVehicleStubAsyncCallback);

        verify(mGetVehicleStubAsyncCallback).linkToDeath(any());

        CountDownLatch latch = new CountDownLatch(1);
        // After sending the request, the client died. Must call the binderDied from a different
        // thread.
        postOnBinderDied(latch, recipientWrapper.get(0));
        JavaMockitoHelper.await(latch, /* timeoutMs= */ 1000);

        // Trigger the callback after the binder is dead.
        triggerCallback(callbackWrapper, resultsWrapper);

        verify(mGetVehicleStubAsyncCallback, never()).onGetAsyncResults(any());
    }

    // Test that the client information is cleaned-up and client callback will not be called if
    // client's binder died while getAsync is adding the callbacks.
    @Test
    public void testGetAsyncAidlBinderDiedWhileRegisterCallback() throws Exception {
        List<IVehicleCallback.Stub> callbackWrapper = new ArrayList<>();
        List<GetValueResults> resultsWrapper = new ArrayList<>();
        mockGetValues(callbackWrapper, resultsWrapper);
        CountDownLatch latch = new CountDownLatch(1);
        doAnswer((invocation) -> {
            // After linkToDeath is called, the client died immediately. Must call the binderDied
            // from a different thread.
            postOnBinderDied(latch, ((DeathRecipient) invocation.getArguments()[0]));
            return null;
        }).when(mGetVehicleStubAsyncCallback).linkToDeath(any());

        HalPropValue value = testAidlHvacPropValue();

        GetVehicleStubAsyncRequest getVehicleStubAsyncRequest =
                defaultVehicleStubAsyncRequest(value);

        // Send the getAsync request.
        mAidlVehicleStub.getAsync(List.of(getVehicleStubAsyncRequest),
                mGetVehicleStubAsyncCallback);

        verify(mGetVehicleStubAsyncCallback).linkToDeath(any());
        // Make sure binderDied is called.
        JavaMockitoHelper.await(latch, /* timeoutMs= */ 1000);

        // Make sure we have finished registering the callback and the client is also died before
        // we send the callback out.
        // This will trigger the callback.
        triggerCallback(callbackWrapper, resultsWrapper);

        verify(mGetVehicleStubAsyncCallback, never()).onGetAsyncResults(any());
    }

    // Test that the client callback will not be called if client's binder already died before
    // getAsync is called.
    @Test
    public void testGetAsyncAidlBinderDiedBeforeRegisterCallback() throws Exception {
        doThrow(new RemoteException()).when(mGetVehicleStubAsyncCallback).linkToDeath(any());

        HalPropValue value = testAidlHvacPropValue();

        GetVehicleStubAsyncRequest getVehicleStubAsyncRequest =
                defaultVehicleStubAsyncRequest(value);

        // Send the getAsync request.
        assertThrows(IllegalStateException.class, () -> {
            mAidlVehicleStub.getAsync(List.of(getVehicleStubAsyncRequest),
                    mGetVehicleStubAsyncCallback);
        });
    }

    @Test
    public void testGetAsyncAidlTimeout() throws Exception {
        List<IVehicleCallback.Stub> callbackWrapper = new ArrayList<>();
        List<GetValueResults> resultsWrapper = new ArrayList<>();
        mockGetValues(callbackWrapper, resultsWrapper);

        HalPropValue value = testAidlHvacPropValue();

        GetVehicleStubAsyncRequest getVehicleStubAsyncRequest1 = new GetVehicleStubAsyncRequest(
                /* serviceRequestId= */ 0, value, /* timeoutInMs= */ 10);
        GetVehicleStubAsyncRequest getVehicleStubAsyncRequest2 = new GetVehicleStubAsyncRequest(
                /* serviceRequestId= */ 1, value, /* timeoutInMs= */ 10);

        // Send the getAsync request.
        mAidlVehicleStub.getAsync(List.of(getVehicleStubAsyncRequest1, getVehicleStubAsyncRequest2),
                mGetVehicleStubAsyncCallback);

        verify(mGetVehicleStubAsyncCallback, timeout(1000)).onRequestsTimeout(List.of(0, 1));

        // Trigger the callback.
        triggerCallback(callbackWrapper, resultsWrapper);

        // If the requests already timed-out, we must not receive any results.
        verify(mGetVehicleStubAsyncCallback, never()).onGetAsyncResults(any());
    }

    // Test that if the request finished before the timeout, the timeout callback must not be
    // called.
    @Test
    public void testGetAsyncAidlTimeoutMustNotTiggerAfterRequestFinished() throws Exception {
        doAnswer((invocation) -> {
            Object[] args = invocation.getArguments();
            GetValueRequests requests = (GetValueRequests) args[1];
            IVehicleCallback.Stub callback = (IVehicleCallback.Stub) args[0];
            GetValueResults results = createGetValueResults(StatusCode.OK, requests.payloads);

            try {
                callback.onGetValues(results);
            } catch (RemoteException e) {
                // ignore.
            }
            return null;
        }).when(mAidlVehicle).getValues(any(), any());

        HalPropValue value = testAidlHvacPropValue();

        GetVehicleStubAsyncRequest getVehicleStubAsyncRequest1 = new GetVehicleStubAsyncRequest(
                /* serviceRequestId= */ 0, value, /* timeoutInMs= */ 100);
        GetVehicleStubAsyncRequest getVehicleStubAsyncRequest2 = new GetVehicleStubAsyncRequest(
                /* serviceRequestId= */ 1, value, /* timeoutInMs= */ 100);

        mAidlVehicleStub.getAsync(List.of(getVehicleStubAsyncRequest1, getVehicleStubAsyncRequest2),
                mGetVehicleStubAsyncCallback);

        // Our callback is called synchronously so we can verify immediately.
        verify(mGetVehicleStubAsyncCallback).onGetAsyncResults(any());
        verify(mGetVehicleStubAsyncCallback, after(500).never()).onRequestsTimeout(any());
    }

    @Test
    public void testGetAsyncAidlOneRequestTimeoutOneFinish() throws Exception {
        doAnswer((invocation) -> {
            Object[] args = invocation.getArguments();
            GetValueRequests requests = (GetValueRequests) args[1];
            IVehicleCallback.Stub callback = (IVehicleCallback.Stub) args[0];
            // Only generate result for the first request.
            GetValueResults results = createGetValueResults(
                    StatusCode.OK, new GetValueRequest[]{requests.payloads[0]});

            try {
                callback.onGetValues(results);
            } catch (RemoteException e) {
                // ignore.
            }
            return null;
        }).when(mAidlVehicle).getValues(any(), any());

        HalPropValue value = testAidlHvacPropValue();

        GetVehicleStubAsyncRequest getVehicleStubAsyncRequest1 = new GetVehicleStubAsyncRequest(
                /* serviceRequestId= */ 0, value, /* timeoutInMs= */ 10);
        GetVehicleStubAsyncRequest getVehicleStubAsyncRequest2 = new GetVehicleStubAsyncRequest(
                /* serviceRequestId= */ 1, value, /* timeoutInMs= */ 10);

        // Send the getAsync request.
        mAidlVehicleStub.getAsync(List.of(getVehicleStubAsyncRequest1, getVehicleStubAsyncRequest2),
                mGetVehicleStubAsyncCallback);

        // Request 0 has result.
        ArgumentCaptor<List<VehicleStub.GetVehicleStubAsyncResult>> argumentCaptor =
                ArgumentCaptor.forClass(List.class);
        // Our callback is called synchronously so we can verify immediately.
        verify(mGetVehicleStubAsyncCallback).onGetAsyncResults(argumentCaptor.capture());
        assertThat(argumentCaptor.getValue().size()).isEqualTo(1);
        assertThat(argumentCaptor.getValue().get(0).getServiceRequestId()).isEqualTo(0);
        // Request 1 timed-out.
        verify(mGetVehicleStubAsyncCallback, timeout(1000)).onRequestsTimeout(List.of(1));
    }

    @Test
    public void testGetSyncAidlTimeout() throws Exception {
        mAidlVehicleStub.setSyncOpTimeoutInMs(100);
        doAnswer((invocation) -> {
            Object[] args = invocation.getArguments();
            GetValueRequests requests = (GetValueRequests) args[1];
            assertThat(requests.payloads.length).isEqualTo(1);
            GetValueRequest request = requests.payloads[0];
            assertThat(request.requestId).isEqualTo(0);
            assertThat(request.prop.prop).isEqualTo(TEST_PROP);
            IVehicleCallback.Stub callback = (IVehicleCallback.Stub) args[0];

            GetValueResults results = createGetValueResults(StatusCode.OK, requests.payloads);

            // Call callback after 200ms.
            mHandler.postDelayed(() -> {
                try {
                    callback.onGetValues(results);
                } catch (RemoteException e) {
                    // ignore.
                }
            }, /* delayMillis= */ 200);
            return null;
        }).when(mAidlVehicle).getValues(any(), any());

        HalPropValueBuilder builder = new HalPropValueBuilder(/*isAidl=*/true);
        HalPropValue value = builder.build(TEST_PROP, 0, TEST_VALUE);

        ServiceSpecificException exception = assertThrows(ServiceSpecificException.class, () -> {
            mAidlVehicleStub.get(value);
        });
        assertThat(exception.errorCode).isEqualTo(StatusCode.INTERNAL_ERROR);
        assertThat(exception.getMessage()).contains("request timeout");

        PollingCheck.check("callback is not called", 1000, () -> {
            return !mHandler.hasMessagesOrCallbacks();
        });

        assertThat(mAidlVehicleStub.countPendingRequests()).isEqualTo(0);
    }

    @Test
    public void testSetHidl() throws Exception {
        HalPropValueBuilder builder = new HalPropValueBuilder(/*isAidl=*/false);
        HalPropValue value = builder.build(TEST_PROP, 0, TEST_VALUE);
        android.hardware.automotive.vehicle.V2_0.VehiclePropValue propValue =
                new android.hardware.automotive.vehicle.V2_0.VehiclePropValue();
        propValue.prop = TEST_PROP;
        propValue.value.int32Values.add(TEST_VALUE);

        when(mHidlVehicle.set(propValue)).thenReturn(StatusCode.OK);

        mHidlVehicleStub.set(value);
    }

    @Test
    public void testSetHidlError() throws Exception {
        HalPropValueBuilder builder = new HalPropValueBuilder(/*isAidl=*/false);
        HalPropValue value = builder.build(TEST_PROP, 0, TEST_VALUE);
        android.hardware.automotive.vehicle.V2_0.VehiclePropValue propValue =
                new android.hardware.automotive.vehicle.V2_0.VehiclePropValue();
        propValue.prop = TEST_PROP;
        propValue.value.int32Values.add(TEST_VALUE);

        when(mHidlVehicle.set(propValue)).thenReturn(StatusCode.INVALID_ARG);

        ServiceSpecificException exception = assertThrows(ServiceSpecificException.class, () -> {
            mHidlVehicleStub.set(value);
        });
        assertThat(exception.errorCode).isEqualTo(StatusCode.INVALID_ARG);
    }

    @Test
    public void testSetAidlSmallData() throws Exception {
        doAnswer((invocation) -> {
            Object[] args = invocation.getArguments();
            SetValueRequests requests = (SetValueRequests) args[1];
            assertThat(requests.payloads.length).isEqualTo(1);
            SetValueRequest request = requests.payloads[0];
            assertThat(request.requestId).isEqualTo(0);
            assertThat(request.value.prop).isEqualTo(TEST_PROP);
            IVehicleCallback.Stub callback = (IVehicleCallback.Stub) args[0];

            SetValueResults results = new SetValueResults();
            SetValueResult result = new SetValueResult();
            result.status = StatusCode.OK;
            result.requestId = request.requestId;
            results.payloads = new SetValueResult[]{result};

            callback.onSetValues(results);
            return null;
        }).when(mAidlVehicle).setValues(any(), any());

        HalPropValueBuilder builder = new HalPropValueBuilder(/*isAidl=*/true);
        HalPropValue value = builder.build(TEST_PROP, 0, TEST_VALUE);

        mAidlVehicleStub.set(value);

        assertThat(mAidlVehicleStub.countPendingRequests()).isEqualTo(0);
    }

    @Test
    public void testSetAidlLargeData() throws Exception {
        int dataSize = 2000;
        doAnswer((invocation) -> {
            Object[] args = invocation.getArguments();
            SetValueRequests requests = (SetValueRequests) args[1];
            assertThat(requests.payloads.length).isEqualTo(0);
            assertThat(requests.sharedMemoryFd).isNotNull();
            requests = (SetValueRequests)
                    LargeParcelable.reconstructStableAIDLParcelable(
                            requests, /*keepSharedMemory=*/false);
            assertThat(requests.payloads.length).isEqualTo(1);
            SetValueRequest request = requests.payloads[0];

            SetValueResults results = new SetValueResults();
            SetValueResult result = new SetValueResult();
            result.status = StatusCode.OK;
            result.requestId = request.requestId;
            results.payloads = new SetValueResult[]{result};

            IVehicleCallback.Stub callback = (IVehicleCallback.Stub) args[0];
            callback.onSetValues(results);
            return null;
        }).when(mAidlVehicle).setValues(any(), any());

        HalPropValueBuilder builder = new HalPropValueBuilder(/*isAidl=*/true);
        HalPropValue value = builder.build(TEST_PROP, 0, 0, 0, getTestIntValues(dataSize));

        mAidlVehicleStub.set(value);

        assertThat(mAidlVehicleStub.countPendingRequests()).isEqualTo(0);
    }

    @Test(expected = ServiceSpecificException.class)
    public void testSetAidlError() throws Exception {
        doAnswer((invocation) -> {
            Object[] args = invocation.getArguments();
            SetValueRequests requests = (SetValueRequests) args[1];
            assertThat(requests.payloads.length).isEqualTo(1);
            SetValueRequest request = requests.payloads[0];
            assertThat(request.requestId).isEqualTo(0);
            assertThat(request.value.prop).isEqualTo(TEST_PROP);
            IVehicleCallback.Stub callback = (IVehicleCallback.Stub) args[0];

            SetValueResults results = new SetValueResults();
            SetValueResult result = new SetValueResult();
            result.status = StatusCode.INVALID_ARG;
            result.requestId = request.requestId;
            results.payloads = new SetValueResult[]{result};

            callback.onSetValues(results);
            return null;
        }).when(mAidlVehicle).setValues(any(), any());

        HalPropValueBuilder builder = new HalPropValueBuilder(/*isAidl=*/true);
        HalPropValue value = builder.build(TEST_PROP, 0, TEST_VALUE);

        mAidlVehicleStub.set(value);

        assertThat(mAidlVehicleStub.countPendingRequests()).isEqualTo(0);
    }

    @Test
    public void testSetAidlAsyncCallback() throws Exception {
        doAnswer((invocation) -> {
            Object[] args = invocation.getArguments();
            SetValueRequests requests = (SetValueRequests) args[1];
            assertThat(requests.payloads.length).isEqualTo(1);
            SetValueRequest request = requests.payloads[0];
            assertThat(request.requestId).isEqualTo(0);
            assertThat(request.value.prop).isEqualTo(TEST_PROP);
            IVehicleCallback.Stub callback = (IVehicleCallback.Stub) args[0];

            SetValueResults results = new SetValueResults();
            SetValueResult result = new SetValueResult();
            result.status = StatusCode.OK;
            result.requestId = request.requestId;
            results.payloads = new SetValueResult[]{result};

            // Call callback after 100ms.
            mHandler.postDelayed(() -> {
                try {
                    callback.onSetValues(results);
                } catch (RemoteException e) {
                    // ignore.
                }
            }, /* delayMillis= */ 100);
            return null;
        }).when(mAidlVehicle).setValues(any(), any());

        HalPropValueBuilder builder = new HalPropValueBuilder(/*isAidl=*/true);
        HalPropValue value = builder.build(TEST_PROP, 0, TEST_VALUE);

        mAidlVehicleStub.set(value);

        assertThat(mAidlVehicleStub.countPendingRequests()).isEqualTo(0);
    }

    @Test
    public void testSetAidlTimeout() throws Exception {
        mAidlVehicleStub.setSyncOpTimeoutInMs(100);
        doAnswer((invocation) -> {
            Object[] args = invocation.getArguments();
            SetValueRequests requests = (SetValueRequests) args[1];
            assertThat(requests.payloads.length).isEqualTo(1);
            SetValueRequest request = requests.payloads[0];
            assertThat(request.requestId).isEqualTo(0);
            assertThat(request.value.prop).isEqualTo(TEST_PROP);
            IVehicleCallback.Stub callback = (IVehicleCallback.Stub) args[0];

            SetValueResults results = new SetValueResults();
            SetValueResult result = new SetValueResult();
            result.status = StatusCode.OK;
            result.requestId = request.requestId;
            results.payloads = new SetValueResult[]{result};

            // Call callback after 200ms.
            mHandler.postDelayed(() -> {
                try {
                    callback.onSetValues(results);
                } catch (RemoteException e) {
                    // ignore.
                }
            }, /* delayMillis= */ 200);
            return null;
        }).when(mAidlVehicle).setValues(any(), any());

        HalPropValueBuilder builder = new HalPropValueBuilder(/*isAidl=*/true);
        HalPropValue value = builder.build(TEST_PROP, 0, TEST_VALUE);

        ServiceSpecificException exception = assertThrows(ServiceSpecificException.class, () -> {
            mAidlVehicleStub.set(value);
        });
        assertThat(exception.errorCode).isEqualTo(StatusCode.INTERNAL_ERROR);
        assertThat(exception.getMessage()).contains("request timeout");

        PollingCheck.check("callback is not called", 1000, () -> {
            return !mHandler.hasMessagesOrCallbacks();
        });

        assertThat(mAidlVehicleStub.countPendingRequests()).isEqualTo(0);
    }

    @Test
    public void testHidlVehicleCallbackOnPropertyEvent() throws Exception {
        HalClientCallback callback = mock(HalClientCallback.class);
        VehicleStub.SubscriptionClient client = mHidlVehicleStub.newSubscriptionClient(callback);
        android.hardware.automotive.vehicle.V2_0.IVehicleCallback.Stub hidlCallback =
                (android.hardware.automotive.vehicle.V2_0.IVehicleCallback.Stub) client;
        android.hardware.automotive.vehicle.V2_0.VehiclePropValue propValue =
                new android.hardware.automotive.vehicle.V2_0.VehiclePropValue();
        propValue.prop = TEST_PROP;
        propValue.value.int32Values.add(TEST_VALUE);
        HalPropValueBuilder builder = new HalPropValueBuilder(/*isAidl=*/false);
        HalPropValue halPropValue = builder.build(TEST_PROP, 0, TEST_VALUE);

        hidlCallback.onPropertyEvent(
                new ArrayList<android.hardware.automotive.vehicle.V2_0.VehiclePropValue>(
                        Arrays.asList(propValue)));

        verify(callback).onPropertyEvent(new ArrayList<HalPropValue>(Arrays.asList(halPropValue)));
    }

    @Test
    public void testHidlVehicleCallbackOnPropertySetError() throws Exception {
        HalClientCallback callback = mock(HalClientCallback.class);
        VehicleStub.SubscriptionClient client = mHidlVehicleStub.newSubscriptionClient(callback);
        android.hardware.automotive.vehicle.V2_0.IVehicleCallback.Stub hidlCallback =
                (android.hardware.automotive.vehicle.V2_0.IVehicleCallback.Stub) client;
        VehiclePropError error = new VehiclePropError();
        error.propId = TEST_PROP;
        error.areaId = TEST_AREA;
        error.errorCode = TEST_STATUS;

        hidlCallback.onPropertySetError(TEST_STATUS, TEST_PROP, TEST_AREA);

        verify(callback).onPropertySetError(new ArrayList<VehiclePropError>(Arrays.asList(error)));
    }

    @Test
    public void testAidlVehicleCallbackOnPropertyEventSmallData() throws Exception {
        HalClientCallback callback = mock(HalClientCallback.class);
        VehicleStub.SubscriptionClient client = mAidlVehicleStub.newSubscriptionClient(callback);
        IVehicleCallback aidlCallback = (IVehicleCallback) client;
        VehiclePropValues propValues = new VehiclePropValues();
        VehiclePropValue propValue = new VehiclePropValue();
        propValue.prop = TEST_PROP;
        propValue.value = new RawPropValues();
        propValue.value.int32Values = new int[]{TEST_VALUE};
        propValues.payloads = new VehiclePropValue[]{propValue};
        HalPropValueBuilder builder = new HalPropValueBuilder(/*isAidl=*/true);
        HalPropValue halPropValue = builder.build(TEST_PROP, 0, TEST_VALUE);

        aidlCallback.onPropertyEvent(propValues, /*sharedMemoryFileCount=*/0);

        verify(callback).onPropertyEvent(new ArrayList<HalPropValue>(Arrays.asList(halPropValue)));
    }

    @Test
    public void testAidlVehicleCallbackOnPropertyEventLargeData() throws Exception {
        HalClientCallback callback = mock(HalClientCallback.class);
        VehicleStub.SubscriptionClient client = mAidlVehicleStub.newSubscriptionClient(callback);
        IVehicleCallback aidlCallback = (IVehicleCallback) client;
        VehiclePropValues propValues = new VehiclePropValues();
        VehiclePropValue propValue = new VehiclePropValue();
        propValue.prop = TEST_PROP;
        int dataSize = 2000;
        int[] intValues = getTestIntValues(dataSize);
        propValue.value = new RawPropValues();
        propValue.value.int32Values = intValues;
        propValues.payloads = new VehiclePropValue[]{propValue};
        propValues = (VehiclePropValues) LargeParcelable.toLargeParcelable(propValues, () -> {
            VehiclePropValues newValues = new VehiclePropValues();
            newValues.payloads = new VehiclePropValue[0];
            return newValues;
        });
        assertThat(propValues.sharedMemoryFd).isNotNull();

        HalPropValueBuilder builder = new HalPropValueBuilder(/*isAidl=*/true);
        HalPropValue halPropValue = builder.build(TEST_PROP, 0, 0, 0, intValues);

        aidlCallback.onPropertyEvent(propValues, /*sharedMemoryFileCount=*/0);

        verify(callback).onPropertyEvent(new ArrayList<HalPropValue>(Arrays.asList(halPropValue)));
    }

    @Test
    public void testAidlVehicleCallbackOnPropertySetErrorSmallData() throws Exception {
        HalClientCallback callback = mock(HalClientCallback.class);
        VehicleStub.SubscriptionClient client = mAidlVehicleStub.newSubscriptionClient(callback);
        IVehicleCallback aidlCallback = (IVehicleCallback) client;
        VehiclePropErrors errors = new VehiclePropErrors();
        VehiclePropError error = new VehiclePropError();
        error.propId = TEST_PROP;
        error.areaId = TEST_AREA;
        error.errorCode = TEST_STATUS;
        errors.payloads = new VehiclePropError[]{error};

        aidlCallback.onPropertySetError(errors);

        verify(callback).onPropertySetError(new ArrayList<VehiclePropError>(Arrays.asList(error)));
    }

    @Test
    public void testAidlVehicleCallbackOnPropertySetErrorLargeData() throws Exception {
        HalClientCallback callback = mock(HalClientCallback.class);
        VehicleStub.SubscriptionClient client = mAidlVehicleStub.newSubscriptionClient(callback);
        IVehicleCallback aidlCallback = (IVehicleCallback) client;
        VehiclePropErrors errors = new VehiclePropErrors();
        VehiclePropError error = new VehiclePropError();
        error.propId = TEST_PROP;
        error.areaId = TEST_AREA;
        error.errorCode = TEST_STATUS;
        int errorCount = 1000;
        errors.payloads = new VehiclePropError[errorCount];
        for (int i = 0; i < errorCount; i++) {
            errors.payloads[i] = error;
        }
        errors = (VehiclePropErrors) LargeParcelable.toLargeParcelable(errors, () -> {
            VehiclePropErrors newErrors = new VehiclePropErrors();
            newErrors.payloads = new VehiclePropError[0];
            return newErrors;
        });
        assertThat(errors.sharedMemoryFd).isNotNull();

        ArrayList<VehiclePropError> expectErrors = new ArrayList<VehiclePropError>(errorCount);
        for (int i = 0; i < errorCount; i++) {
            expectErrors.add(error);
        }

        aidlCallback.onPropertySetError(errors);

        verify(callback).onPropertySetError(expectErrors);
    }

    @Test
    public void testDumpHidl() throws Exception {
        ArrayList<String> options = new ArrayList<>();
        FileDescriptor fd = mock(FileDescriptor.class);

        mHidlVehicleStub.dump(fd, options);

        verify(mHidlVehicle).debug(any(), eq(options));
    }

    @Test
    public void testDumpAidl() throws Exception {
        ArrayList<String> options = new ArrayList<>();
        FileDescriptor fd = mock(FileDescriptor.class);

        mAidlVehicleStub.dump(fd, options);

        verify(mAidlBinder).dump(eq(fd), eq(new String[0]));
    }
}
