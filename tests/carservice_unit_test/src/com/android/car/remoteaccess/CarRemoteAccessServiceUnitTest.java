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

package com.android.car.remoteaccess;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.Car;
import android.car.VehiclePropertyIds;
import android.car.hardware.CarPropertyValue;
import android.car.remoteaccess.CarRemoteAccessManager;
import android.car.remoteaccess.ICarRemoteAccessCallback;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.content.res.Resources;
import android.hardware.automotive.vehicle.VehicleApPowerBootupReason;
import android.os.UserHandle;

import com.android.car.CarLocalServices;
import com.android.car.CarPropertyService;
import com.android.car.R;
import com.android.car.power.CarPowerManagementService;
import com.android.car.remoteaccess.hal.RemoteAccessHalCallback;
import com.android.car.remoteaccess.hal.RemoteAccessHalWrapper;
import com.android.car.systeminterface.SystemInterface;
import com.android.compatibility.common.util.PollingCheck;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@RunWith(MockitoJUnitRunner.class)
public final class CarRemoteAccessServiceUnitTest {

    private static final long WAIT_TIMEOUT_MS = 5000;
    private static final String WAKEUP_SERVICE_NAME = "android_wakeup_service";
    private static final String TEST_DEVICE_ID = "test_vehicle";
    private static final CarPropertyValue<Integer> PROP_SYSTEM_REMOTE_ACCESS =
            new CarPropertyValue<>(VehiclePropertyIds.AP_POWER_BOOTUP_REASON, /* areadId= */ 0,
                    CarPropertyValue.STATUS_AVAILABLE, /* timestampNanos= */ 0,
                    VehicleApPowerBootupReason.SYSTEM_REMOTE_ACCESS);
    private static final List<PackagePrepForTest> AVAILABLE_PACKAGES = new ArrayList<>();
    private static final String PERMISSION_NOT_GRANTED_PACKAGE = "life.is.beautiful";
    private static final String PERMISSION_GRANTED_PACKAGE_ONE = "we.are.the.world";
    private static final String PERMISSION_GRANTED_PACKAGE_TWO = "android.automotive.os";
    private static final String CLASS_NAME_ONE = "Hello";
    private static final String CLASS_NAME_TWO = "Best";

    static {
        AVAILABLE_PACKAGES.add(createPackagePrepForTest(PERMISSION_NOT_GRANTED_PACKAGE, "Happy",
                /* permissionGranted= */ false));
        AVAILABLE_PACKAGES.add(createPackagePrepForTest(PERMISSION_GRANTED_PACKAGE_ONE,
                CLASS_NAME_ONE, /* permissionGranted= */ true));
        AVAILABLE_PACKAGES.add(createPackagePrepForTest(PERMISSION_GRANTED_PACKAGE_TWO,
                CLASS_NAME_TWO, /* permissionGranted= */ true));
    }

    private CarRemoteAccessService mService;
    private ICarRemoteAccessCallbackImpl mRemoteAccessCallback;
    private CarPowerManagementService mOldCarPowerManagementService;
    private CarPropertyService mOldCarPropertyService;

    @Mock private Context mContext;
    @Mock private Resources mResources;
    @Mock private PackageManager mPackageManager;
    @Mock private RemoteAccessHalWrapper mRemoteAccessHal;
    @Mock private SystemInterface mSystemInterface;
    @Mock private CarPowerManagementService mCarPowerManagementService;
    @Mock private CarPropertyService mCarPropertyService;

    @Before
    public void setUp() {
        mOldCarPropertyService = CarLocalServices.getService(CarPropertyService.class);
        CarLocalServices.removeServiceForTest(CarPropertyService.class);
        CarLocalServices.addService(CarPropertyService.class, mCarPropertyService);
        mOldCarPowerManagementService = CarLocalServices.getService(
                CarPowerManagementService.class);
        CarLocalServices.removeServiceForTest(CarPowerManagementService.class);
        CarLocalServices.addService(CarPowerManagementService.class, mCarPowerManagementService);
        when(mCarPropertyService.getPropertySafe(VehiclePropertyIds.AP_POWER_BOOTUP_REASON, 0))
                .thenReturn(PROP_SYSTEM_REMOTE_ACCESS);
        when(mContext.getPackageManager()).thenReturn(mPackageManager);
        when(mContext.getResources()).thenReturn(mResources);
        when(mResources.getInteger(R.integer.config_allowedSystemUpTimeForRemoteAccess))
                .thenReturn(300);
        when(mRemoteAccessHal.getWakeupServiceName()).thenReturn(WAKEUP_SERVICE_NAME);
        when(mRemoteAccessHal.getDeviceId()).thenReturn(TEST_DEVICE_ID);
        when(mCarPowerManagementService.getLastShutdownState())
                .thenReturn(CarRemoteAccessManager.NEXT_POWER_STATE_OFF);
        mockPackageInfo();

        mService = new CarRemoteAccessService(mContext, mSystemInterface, mRemoteAccessHal);
        mService.init();
        mRemoteAccessCallback = new ICarRemoteAccessCallbackImpl();
    }

    @After
    public void tearDown() {
        CarLocalServices.removeServiceForTest(CarPowerManagementService.class);
        CarLocalServices.addService(CarPowerManagementService.class, mOldCarPowerManagementService);
        CarLocalServices.removeServiceForTest(CarPropertyService.class);
        CarLocalServices.addService(CarPropertyService.class, mOldCarPropertyService);
    }

    @Test
    public void testStartRemoteTaskClientService() {
        String[] packageNames = new String[]{PERMISSION_GRANTED_PACKAGE_ONE,
                PERMISSION_GRANTED_PACKAGE_TWO};
        String[] classNames = new String[]{CLASS_NAME_ONE, CLASS_NAME_TWO};
        ArgumentCaptor<Intent> intentCaptor = ArgumentCaptor.forClass(Intent.class);
        InOrder checkOrder = inOrder(mContext);

        for (int i = 0; i < packageNames.length; i++) {
            checkOrder.verify(mContext).bindServiceAsUser(intentCaptor.capture(),
                    any(ServiceConnection.class), anyInt(), any(UserHandle.class));
            Intent intent = intentCaptor.getValue();
            ComponentName component = intent.getComponent();

            assertWithMessage("Package name to start").that(component.getPackageName())
                    .isEqualTo(packageNames[i]);
            assertWithMessage("Class name to start").that(component.getClassName())
                    .isEqualTo(classNames[i]);
        }
    }

    @Test
    public void testAddCarRemoteTaskClient() throws Exception {
        String packageName = PERMISSION_GRANTED_PACKAGE_ONE;
        when(mPackageManager.getNameForUid(anyInt())).thenReturn(packageName);

        mService.addCarRemoteTaskClient(mRemoteAccessCallback);

        PollingCheck.check("onClientRegistrationUpdated should be called", WAIT_TIMEOUT_MS,
                () -> Objects.equals(mRemoteAccessCallback.getServiceName(), WAKEUP_SERVICE_NAME)
                        && Objects.equals(mRemoteAccessCallback.getDeviceId(), TEST_DEVICE_ID)
                        && mRemoteAccessCallback.getClientId() != null);
    }

    @Test
    public void testAddCarRemoteTaskClient_addTwice() throws Exception {
        String packageName = PERMISSION_GRANTED_PACKAGE_ONE;
        when(mPackageManager.getNameForUid(anyInt())).thenReturn(packageName);
        ICarRemoteAccessCallbackImpl secondCallback = new ICarRemoteAccessCallbackImpl();
        mService.addCarRemoteTaskClient(mRemoteAccessCallback);

        mService.addCarRemoteTaskClient(secondCallback);

        PollingCheck.check("onClientRegistrationUpdated should be called", WAIT_TIMEOUT_MS,
                () -> Objects.equals(secondCallback.getServiceName(), WAKEUP_SERVICE_NAME)
                        && Objects.equals(secondCallback.getDeviceId(), TEST_DEVICE_ID)
                        && secondCallback.getClientId() != null);
    }

    @Test
    public void testAddCarRemoteTaskClient_addMultipleClients() throws Exception {
        String packageNameOne = PERMISSION_GRANTED_PACKAGE_ONE;
        String packageNameTwo = PERMISSION_GRANTED_PACKAGE_TWO;
        when(mPackageManager.getNameForUid(anyInt())).thenReturn(packageNameOne)
                .thenReturn(packageNameTwo);
        ICarRemoteAccessCallbackImpl secondCallback = new ICarRemoteAccessCallbackImpl();

        mService.addCarRemoteTaskClient(mRemoteAccessCallback);
        mService.addCarRemoteTaskClient(secondCallback);

        PollingCheck.check("Two clients should have different client IDs", WAIT_TIMEOUT_MS,
                () -> {
                    String clientIdOne = mRemoteAccessCallback.getClientId();
                    String clientIdTwo = secondCallback.getClientId();
                    return clientIdOne != null && !clientIdOne.equals(clientIdTwo);
                });
    }

    @Test
    public void testRemoveCarRemoteTaskClient() throws Exception {
        String packageName = PERMISSION_GRANTED_PACKAGE_ONE;
        when(mPackageManager.getNameForUid(anyInt())).thenReturn(packageName);
        mService.addCarRemoteTaskClient(mRemoteAccessCallback);

        mService.removeCarRemoteTaskClient(mRemoteAccessCallback);
    }

    @Test
    public void testRemoveCarRemoteTaskClient_removeNotAddedClient() throws Exception {
        // Removing unregistered ICarRemoteAccessCallback is no-op.
        mService.removeCarRemoteTaskClient(mRemoteAccessCallback);
    }

    @Test
    public void testRemoteTaskRequested() throws Exception {
        RemoteAccessHalCallback halCallback = prepareCarRemoteTastClient();

        String clientId = mRemoteAccessCallback.getClientId();
        byte[] data = new byte[]{1, 2, 3, 4};
        halCallback.onRemoteTaskRequested(clientId, data);

        PollingCheck.check("onRemoteTaskRequested should be called", WAIT_TIMEOUT_MS,
                () -> mRemoteAccessCallback.getTaskId() != null);
        assertWithMessage("Data").that(mRemoteAccessCallback.getData()).asList()
                .containsExactlyElementsIn(new Byte[]{1, 2, 3, 4});
    }

    @Test
    public void testRemoteTaskRequested_removedClient() throws Exception {
        RemoteAccessHalCallback halCallback = prepareCarRemoteTastClient();
        String clientId = mRemoteAccessCallback.getClientId();
        mService.removeCarRemoteTaskClient(mRemoteAccessCallback);

        halCallback.onRemoteTaskRequested(clientId, /* data= */ null);

        assertWithMessage("Task ID").that(mRemoteAccessCallback.getTaskId()).isNull();
    }

    @Test
    public void testRemoteTaskRequested_clientRegisteredAfterRequest() throws Exception {
        RemoteAccessHalCallback halCallback = prepareCarRemoteTastClient();
        String clientId = mRemoteAccessCallback.getClientId();
        mService.removeCarRemoteTaskClient(mRemoteAccessCallback);
        halCallback.onRemoteTaskRequested(clientId, /* data= */ null);

        mService.addCarRemoteTaskClient(mRemoteAccessCallback);

        PollingCheck.check("onRemoteTaskRequested should be called", WAIT_TIMEOUT_MS,
                () -> mRemoteAccessCallback.getTaskId() != null);
    }

    @Test
    public void testRemoteTaskRequested_withTwoClientsRegistered() throws Exception {
        String packageNameOne = PERMISSION_GRANTED_PACKAGE_ONE;
        String packageNameTwo = PERMISSION_GRANTED_PACKAGE_TWO;
        when(mPackageManager.getNameForUid(anyInt())).thenReturn(packageNameOne)
                .thenReturn(packageNameTwo);
        ICarRemoteAccessCallbackImpl secondCallback = new ICarRemoteAccessCallbackImpl();
        mService.addCarRemoteTaskClient(mRemoteAccessCallback);
        mService.addCarRemoteTaskClient(secondCallback);
        PollingCheck.check("Client is registered", WAIT_TIMEOUT_MS,
                () -> mRemoteAccessCallback.getClientId() != null);
        PollingCheck.check("Client is registered", WAIT_TIMEOUT_MS,
                () -> secondCallback.getClientId() != null);
        String clientId = mRemoteAccessCallback.getClientId();
        mService.removeCarRemoteTaskClient(secondCallback);
        RemoteAccessHalCallback halCallback = mService.getRemoteAccessHalCallback();

        halCallback.onRemoteTaskRequested(clientId, /* data= */ null);

        PollingCheck.check("onRemoteTaskRequested should be called", WAIT_TIMEOUT_MS,
                () -> mRemoteAccessCallback.getTaskId() != null);
        assertWithMessage("Task ID").that(secondCallback.getTaskId()).isNull();
    }

    @Test
    public void testReportTaskDone_withPowerStateOff() throws Exception {
        prepareReportTaskDoneTest();
        String clientId = mRemoteAccessCallback.getClientId();
        String taskId = mRemoteAccessCallback.getTaskId();
        mService.setPowerStatePostTaskExecution(
                CarRemoteAccessManager.NEXT_POWER_STATE_OFF, /* runGarageMode= */ true);

        mService.reportRemoteTaskDone(clientId, taskId);

        verify(mCarPowerManagementService).requestShutdownAp(
                CarRemoteAccessManager.NEXT_POWER_STATE_OFF, /* runGarageMode= */ true);
    }

    @Test
    public void testReportRemoteTaskDone_withPowerStateS2R() throws Exception {
        prepareReportTaskDoneTest();
        String clientId = mRemoteAccessCallback.getClientId();
        String taskId = mRemoteAccessCallback.getTaskId();
        mService.setPowerStatePostTaskExecution(
                CarRemoteAccessManager.NEXT_POWER_STATE_SUSPEND_TO_RAM, /* runGarageMode= */ true);

        mService.reportRemoteTaskDone(clientId, taskId);

        verify(mCarPowerManagementService).requestShutdownAp(
                CarRemoteAccessManager.NEXT_POWER_STATE_SUSPEND_TO_RAM, /* runGarageMode= */ true);
    }

    @Test
    public void testReportRemoteTaskDone_withPowerStateOn() throws Exception {
        prepareReportTaskDoneTest();
        String clientId = mRemoteAccessCallback.getClientId();
        String taskId = mRemoteAccessCallback.getTaskId();
        mService.setPowerStatePostTaskExecution(
                CarRemoteAccessManager.NEXT_POWER_STATE_ON, /* runGarageMode= */ false);

        mService.reportRemoteTaskDone(clientId, taskId);

        verify(mCarPowerManagementService, never()).requestShutdownAp(
                CarRemoteAccessManager.NEXT_POWER_STATE_SUSPEND_TO_RAM, /* runGarageMode= */ true);
    }

    @Test
    public void testReportTaskDone_wrongTaskId() throws Exception {
        prepareReportTaskDoneTest();
        String clientId = mRemoteAccessCallback.getClientId();
        String wrongTaskId = mRemoteAccessCallback.getTaskId() + "_WRONG";

        assertThrows(IllegalArgumentException.class,
                () -> mService.reportRemoteTaskDone(clientId, wrongTaskId));
    }

    @Test
    public void testReportTaskDone_wrongClientId() throws Exception {
        prepareReportTaskDoneTest();
        String wrongClientId = mRemoteAccessCallback.getClientId() + "_WRONG";
        String taskId = mRemoteAccessCallback.getTaskId();

        assertThrows(IllegalArgumentException.class,
                () -> mService.reportRemoteTaskDone(wrongClientId, taskId));
    }

    private RemoteAccessHalCallback prepareCarRemoteTastClient() throws Exception {
        String packageName = PERMISSION_GRANTED_PACKAGE_ONE;
        when(mPackageManager.getNameForUid(anyInt())).thenReturn(packageName);
        RemoteAccessHalCallback halCallback = mService.getRemoteAccessHalCallback();
        mService.addCarRemoteTaskClient(mRemoteAccessCallback);
        PollingCheck.check("Client is registered", WAIT_TIMEOUT_MS,
                () -> mRemoteAccessCallback.getClientId() != null);
        return halCallback;
    }

    private void prepareReportTaskDoneTest() throws Exception {
        RemoteAccessHalCallback halCallback = prepareCarRemoteTastClient();
        String clientId = mRemoteAccessCallback.getClientId();
        halCallback.onRemoteTaskRequested(clientId, /* data= */ null);
        PollingCheck.check("onRemoteTaskRequested should be called", WAIT_TIMEOUT_MS,
                () -> mRemoteAccessCallback.getTaskId() != null);
    }

    private void mockPackageInfo() {
        List<ResolveInfo> resolveInfos = new ArrayList<>(AVAILABLE_PACKAGES.size());
        for (int i = 0; i < AVAILABLE_PACKAGES.size(); i++) {
            PackagePrepForTest packagePrep = AVAILABLE_PACKAGES.get(i);
            ResolveInfo resolveInfo = packagePrep.resolveInfo;
            resolveInfos.add(resolveInfo);
            String packageName = resolveInfo.serviceInfo.packageName;
            int permission = packagePrep.permissionGranted ? PackageManager.PERMISSION_GRANTED
                    : PackageManager.PERMISSION_DENIED;
            when(mPackageManager.checkPermission(Car.PERMISSION_USE_REMOTE_ACCESS, packageName))
                    .thenReturn(permission);
        }
        when(mPackageManager.queryIntentServices(any(), any())).thenReturn(resolveInfos);
        doAnswer(inv -> {
            Runnable runnable = inv.getArgument(0);
            runnable.run();
            return null;
        }).when(mSystemInterface)
                .scheduleActionForBootCompleted(any(Runnable.class), any(Duration.class));
    }

    private static PackagePrepForTest createPackagePrepForTest(String packageName, String className,
            boolean permissionGranted) {
        PackagePrepForTest packagePrep = new PackagePrepForTest();
        packagePrep.resolveInfo = new ResolveInfo();
        packagePrep.resolveInfo.serviceInfo = new ServiceInfo();
        packagePrep.resolveInfo.serviceInfo.packageName = packageName;
        packagePrep.resolveInfo.serviceInfo.name = className;
        packagePrep.permissionGranted = permissionGranted;
        return packagePrep;
    }

    private static final class PackagePrepForTest {
        public ResolveInfo resolveInfo;
        public boolean permissionGranted;
    }

    private static final class ICarRemoteAccessCallbackImpl extends ICarRemoteAccessCallback.Stub {
        private String mServiceName;
        private String mDeviceId;
        private String mClientId;
        private String mTaskId;
        private byte[] mData;
        private boolean mShutdownStarted;

        @Override
        public void onClientRegistrationUpdated(String serviceId, String deviceId,
                String clientId) {
            mServiceName = serviceId;
            mDeviceId = deviceId;
            mClientId = clientId;
        }

        @Override
        public void onClientRegistrationFailed() {
        }

        @Override
        public void onRemoteTaskRequested(String clientId, String taskId, byte[] data,
                int taskMaxDurationInSec) {
            mClientId = clientId;
            mTaskId = taskId;
            mData = data;
        }

        @Override
        public void onShutdownStarting() {
            mShutdownStarted = true;
        }

        public String getServiceName() {
            return mServiceName;
        }

        public String getDeviceId() {
            return mDeviceId;
        }

        public String getClientId() {
            return mClientId;
        }

        public String getTaskId() {
            return mTaskId;
        }

        public byte[] getData() {
            return mData;
        }
    }
}
