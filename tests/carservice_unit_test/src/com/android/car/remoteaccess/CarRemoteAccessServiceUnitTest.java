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

import static com.android.car.remoteaccess.RemoteAccessStorage.RemoteAccessDbHelper.DATABASE_NAME;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.spyOn;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.Car;
import android.car.hardware.power.CarPowerManager;
import android.car.hardware.power.ICarPowerStateListener;
import android.car.remoteaccess.CarRemoteAccessManager;
import android.car.remoteaccess.ICarRemoteAccessCallback;
import android.car.remoteaccess.RemoteTaskClientRegistrationInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.content.res.Resources;
import android.os.SystemClock;
import android.os.UserHandle;
import android.util.Log;

import androidx.test.InstrumentationRegistry;

import com.android.car.CarLocalServices;
import com.android.car.CarServiceUtils;
import com.android.car.R;
import com.android.car.hal.PowerHalService;
import com.android.car.power.CarPowerManagementService;
import com.android.car.remoteaccess.RemoteAccessStorage.ClientIdEntry;
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

import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@RunWith(MockitoJUnitRunner.class)
public final class CarRemoteAccessServiceUnitTest {

    private static final String TAG = CarRemoteAccessServiceUnitTest.class.getSimpleName();
    private static final long WAIT_TIMEOUT_MS = 5000;
    private static final long ALLOWED_SYSTEM_UP_TIME_FOR_TESTING_MS = 5000;
    private static final String WAKEUP_SERVICE_NAME = "android_wakeup_service";
    private static final String TEST_VEHICLE_ID = "test_vehicle";
    private static final String TEST_PROCESSOR_ID = "test_processor";
    private static final String PERMISSION_NOT_GRANTED_PACKAGE = "life.is.beautiful";
    private static final String PERMISSION_GRANTED_PACKAGE_ONE = "we.are.the.world";
    private static final String PERMISSION_GRANTED_PACKAGE_TWO = "android.automotive.os";
    private static final int UID_PERMISSION_NOT_GRANTED_PACKAGE = 1;
    private static final int UID_PERMISSION_GRANTED_PACKAGE_ONE = 2;
    private static final int UID_PERMISSION_GRANTED_PACKAGE_TWO = 3;
    private static final String CLASS_NAME_ONE = "Hello";
    private static final String CLASS_NAME_TWO = "Best";
    private static final List<PackagePrepForTest> AVAILABLE_PACKAGES = List.of(
            createPackagePrepForTest(PERMISSION_NOT_GRANTED_PACKAGE, "Happy",
                    /* permissionGranted= */ false,
                    UID_PERMISSION_NOT_GRANTED_PACKAGE),
            createPackagePrepForTest(PERMISSION_GRANTED_PACKAGE_ONE,
                    CLASS_NAME_ONE, /* permissionGranted= */ true,
                    UID_PERMISSION_GRANTED_PACKAGE_ONE),
            createPackagePrepForTest(PERMISSION_GRANTED_PACKAGE_TWO,
                    CLASS_NAME_TWO, /* permissionGranted= */ true,
                    UID_PERMISSION_GRANTED_PACKAGE_TWO)
    );
    private static final List<ClientIdEntry> PERSISTENT_CLIENTS = List.of(
            new ClientIdEntry("12345", System.currentTimeMillis(), "we.are.the.world"),
            new ClientIdEntry("98765", System.currentTimeMillis(), "android.automotive.os")
    );

    private CarRemoteAccessService mService;
    private ICarRemoteAccessCallbackImpl mRemoteAccessCallback;
    private CarPowerManagementService mOldCarPowerManagementService;
    private File mDatabaseFile;
    private Context mContext;
    private RemoteAccessStorage mRemoteAccessStorage;

    @Mock private Resources mResources;
    @Mock private PackageManager mPackageManager;
    @Mock private RemoteAccessHalWrapper mRemoteAccessHal;
    @Mock private SystemInterface mSystemInterface;
    @Mock private CarPowerManagementService mCarPowerManagementService;
    @Mock private PowerHalService mPowerHalService;
    @Mock private CarRemoteAccessService.CarRemoteAccessServiceDep mDep;

    @Before
    public void setUp() {
        mOldCarPowerManagementService = CarLocalServices.getService(
                CarPowerManagementService.class);
        CarLocalServices.removeServiceForTest(CarPowerManagementService.class);
        CarLocalServices.addService(CarPowerManagementService.class, mCarPowerManagementService);

        mContext = InstrumentationRegistry.getTargetContext().createDeviceProtectedStorageContext();
        spyOn(mContext);

        // doReturn().when() pattern is necessary because mContext is spied.
        doReturn(mPackageManager).when(mContext).getPackageManager();
        doReturn(mResources).when(mContext).getResources();
        mDatabaseFile = mContext.getDatabasePath(DATABASE_NAME);
        when(mResources.getInteger(R.integer.config_allowedSystemUptimeForRemoteAccess))
                .thenReturn(300);
        when(mRemoteAccessHal.getWakeupServiceName()).thenReturn(WAKEUP_SERVICE_NAME);
        when(mRemoteAccessHal.getVehicleId()).thenReturn(TEST_VEHICLE_ID);
        when(mRemoteAccessHal.getProcessorId()).thenReturn(TEST_PROCESSOR_ID);
        when(mCarPowerManagementService.getLastShutdownState())
                .thenReturn(CarRemoteAccessManager.NEXT_POWER_STATE_OFF);
        when(mSystemInterface.getSystemCarDir()).thenReturn(mDatabaseFile.getParentFile());
        mockPackageInfo();
        setVehicleInUse(/* inUse= */ false);

        when(mPackageManager.getNameForUid(UID_PERMISSION_NOT_GRANTED_PACKAGE)).thenReturn(
                PERMISSION_NOT_GRANTED_PACKAGE);
        when(mPackageManager.getNameForUid(UID_PERMISSION_GRANTED_PACKAGE_ONE)).thenReturn(
                PERMISSION_GRANTED_PACKAGE_ONE);
        when(mPackageManager.getNameForUid(UID_PERMISSION_GRANTED_PACKAGE_TWO)).thenReturn(
                PERMISSION_GRANTED_PACKAGE_TWO);

        mRemoteAccessCallback = new ICarRemoteAccessCallbackImpl();
        mRemoteAccessStorage = new RemoteAccessStorage(mContext, mSystemInterface);
        mService = new CarRemoteAccessService(mContext, mSystemInterface, mPowerHalService,
                mDep, mRemoteAccessHal, mRemoteAccessStorage,
                ALLOWED_SYSTEM_UP_TIME_FOR_TESTING_MS);
    }

    @After
    public void tearDown() {
        mService.release();
        CarServiceUtils.finishAllHandlerTasks();

        CarLocalServices.removeServiceForTest(CarPowerManagementService.class);
        CarLocalServices.addService(CarPowerManagementService.class, mOldCarPowerManagementService);

        if (!mDatabaseFile.delete()) {
            Log.e(TAG, "Failed to delete the database file: " + mDatabaseFile.getAbsolutePath());
        }
    }

    @Test
    public void testStartRemoteTaskClientService() {
        String[] packageNames = new String[]{PERMISSION_GRANTED_PACKAGE_ONE,
                PERMISSION_GRANTED_PACKAGE_TWO};
        String[] classNames = new String[]{CLASS_NAME_ONE, CLASS_NAME_TWO};
        ArgumentCaptor<Intent> intentCaptor = ArgumentCaptor.forClass(Intent.class);
        InOrder checkOrder = inOrder(mContext);

        mService.init();

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
    public void testCarRemoteAccessServiceInit() throws Exception {
        mService.init();

        verify(mRemoteAccessHal, timeout(1000)).notifyApStateChange(
                /* isReadyForRemoteTask= */ true, /* isWakeupRequired= */ false);
    }

    @Test
    public void testCarRemoteAccessServiceInit_retryNotifyApState() throws Exception {
        when(mRemoteAccessHal.notifyApStateChange(anyBoolean(), anyBoolean())).thenReturn(false);

        mService.init();

        verify(mRemoteAccessHal, timeout(1500).times(10)).notifyApStateChange(
                /* isReadyForRemoteTask= */ true, /* isWakeupRequired= */ false);
    }


    @Test
    public void testAddCarRemoteTaskClient() throws Exception {
        when(mDep.getCallingUid()).thenReturn(UID_PERMISSION_GRANTED_PACKAGE_ONE);
        mService.init();

        mService.addCarRemoteTaskClient(mRemoteAccessCallback);

        PollingCheck.check("onClientRegistrationUpdated should be called", WAIT_TIMEOUT_MS,
                () -> Objects.equals(mRemoteAccessCallback.getServiceName(), WAKEUP_SERVICE_NAME)
                        && Objects.equals(mRemoteAccessCallback.getVehicleId(), TEST_VEHICLE_ID)
                        && Objects.equals(mRemoteAccessCallback.getProcessorId(), TEST_PROCESSOR_ID)
                        && mRemoteAccessCallback.getClientId() != null);
    }

    @Test
    public void testAddCarRemoteTaskClient_addTwice() throws Exception {
        when(mDep.getCallingUid()).thenReturn(UID_PERMISSION_GRANTED_PACKAGE_ONE);
        ICarRemoteAccessCallbackImpl secondCallback = new ICarRemoteAccessCallbackImpl();
        mService.init();
        mService.addCarRemoteTaskClient(mRemoteAccessCallback);

        mService.addCarRemoteTaskClient(secondCallback);

        PollingCheck.check("onClientRegistrationUpdated should be called", WAIT_TIMEOUT_MS,
                () -> Objects.equals(secondCallback.getServiceName(), WAKEUP_SERVICE_NAME)
                        && Objects.equals(secondCallback.getVehicleId(), TEST_VEHICLE_ID)
                        && Objects.equals(secondCallback.getProcessorId(), TEST_PROCESSOR_ID)
                        && secondCallback.getClientId() != null);
    }

    @Test
    public void testAddCarRemoteTaskClient_addMultipleClients() throws Exception {
        when(mDep.getCallingUid()).thenReturn(UID_PERMISSION_GRANTED_PACKAGE_ONE)
                .thenReturn(UID_PERMISSION_GRANTED_PACKAGE_TWO);
        ICarRemoteAccessCallbackImpl secondCallback = new ICarRemoteAccessCallbackImpl();
        mService.init();

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
    public void testAddCarRemoteTaskClient_persistentClientId() throws Exception {
        String packageName = PERSISTENT_CLIENTS.get(0).uidName;
        String expectedClientId = PERSISTENT_CLIENTS.get(0).clientId;
        when(mDep.getCallingUid()).thenReturn(1234);
        when(mPackageManager.getNameForUid(1234)).thenReturn(packageName);
        setupDatabase();
        mService.init();

        mService.addCarRemoteTaskClient(mRemoteAccessCallback);

        PollingCheck.check("onClientRegistrationUpdated should be called", WAIT_TIMEOUT_MS,
                () -> Objects.equals(mRemoteAccessCallback.getServiceName(), WAKEUP_SERVICE_NAME)
                        && Objects.equals(mRemoteAccessCallback.getVehicleId(), TEST_VEHICLE_ID)
                        && Objects.equals(mRemoteAccessCallback.getProcessorId(), TEST_PROCESSOR_ID)
                        && Objects.equals(mRemoteAccessCallback.getClientId(), expectedClientId));
    }

    @Test
    public void testRemoveCarRemoteTaskClient() throws Exception {
        when(mDep.getCallingUid()).thenReturn(UID_PERMISSION_GRANTED_PACKAGE_ONE);
        mService.init();
        mService.addCarRemoteTaskClient(mRemoteAccessCallback);

        mService.removeCarRemoteTaskClient(mRemoteAccessCallback);
    }

    @Test
    public void testRemoveCarRemoteTaskClient_removeNotAddedClient() throws Exception {
        mService.init();
        // Removing unregistered ICarRemoteAccessCallback is no-op.
        mService.removeCarRemoteTaskClient(mRemoteAccessCallback);
    }

    @Test
    public void testRemoteTaskRequested() throws Exception {
        mService.init();
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
        mService.init();
        RemoteAccessHalCallback halCallback = prepareCarRemoteTastClient();
        String clientId = mRemoteAccessCallback.getClientId();
        mService.removeCarRemoteTaskClient(mRemoteAccessCallback);

        halCallback.onRemoteTaskRequested(clientId, /* data= */ null);

        assertWithMessage("Task ID").that(mRemoteAccessCallback.getTaskId()).isNull();
    }

    @Test
    public void testRemoteTaskRequested_clientRegisteredAfterRequest() throws Exception {
        mService.init();
        RemoteAccessHalCallback halCallback = prepareCarRemoteTastClient();
        String clientId = mRemoteAccessCallback.getClientId();
        mService.removeCarRemoteTaskClient(mRemoteAccessCallback);
        halCallback.onRemoteTaskRequested(clientId, /* data= */ null);

        mService.addCarRemoteTaskClient(mRemoteAccessCallback);

        PollingCheck.check("onRemoteTaskRequested should be called", WAIT_TIMEOUT_MS,
                () -> mRemoteAccessCallback.getTaskId() != null);
    }

    @Test
    public void testRemoteTaskRequested_persistentClientRegisteredAfterRequest() throws Exception {
        String packageName = PERSISTENT_CLIENTS.get(0).uidName;
        String clientId = PERSISTENT_CLIENTS.get(0).clientId;
        when(mDep.getCallingUid()).thenReturn(1234);
        when(mPackageManager.getNameForUid(1234)).thenReturn(packageName);
        RemoteAccessHalCallback halCallback = mService.getRemoteAccessHalCallback();
        setupDatabase();
        mService.init();

        halCallback.onRemoteTaskRequested(clientId, /* data= */ null);
        SystemClock.sleep(500);
        mService.addCarRemoteTaskClient(mRemoteAccessCallback);

        PollingCheck.check("onRemoteTaskRequested should be called", WAIT_TIMEOUT_MS,
                () -> mRemoteAccessCallback.getTaskId() != null);
    }

    @Test
    public void testRemoteTaskRequested_withTwoClientsRegistered() throws Exception {
        when(mDep.getCallingUid()).thenReturn(UID_PERMISSION_GRANTED_PACKAGE_ONE)
                .thenReturn(UID_PERMISSION_GRANTED_PACKAGE_TWO);
        ICarRemoteAccessCallbackImpl secondCallback = new ICarRemoteAccessCallbackImpl();
        mService.init();
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
        mService.init();
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
        mService.init();
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
        mService.init();
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
    public void testReportRemoteTaskDone_vehicleInUse() throws Exception {
        setVehicleInUse(/* inUse= */ true);
        mService.init();
        prepareReportTaskDoneTest();
        String clientId = mRemoteAccessCallback.getClientId();
        String taskId = mRemoteAccessCallback.getTaskId();
        mService.setPowerStatePostTaskExecution(
                CarRemoteAccessManager.NEXT_POWER_STATE_OFF, /* runGarageMode= */ false);

        mService.reportRemoteTaskDone(clientId, taskId);

        verify(mCarPowerManagementService, never()).requestShutdownAp(
                CarRemoteAccessManager.NEXT_POWER_STATE_SUSPEND_TO_RAM, /* runGarageMode= */ true);
    }

    @Test
    public void testReportTaskDone_wrongTaskId() throws Exception {
        mService.init();
        prepareReportTaskDoneTest();
        String clientId = mRemoteAccessCallback.getClientId();
        String wrongTaskId = mRemoteAccessCallback.getTaskId() + "_WRONG";

        assertThrows(IllegalArgumentException.class,
                () -> mService.reportRemoteTaskDone(clientId, wrongTaskId));
    }

    @Test
    public void testReportTaskDone_wrongClientId() throws Exception {
        mService.init();
        prepareReportTaskDoneTest();
        String wrongClientId = mRemoteAccessCallback.getClientId() + "_WRONG";
        String taskId = mRemoteAccessCallback.getTaskId();

        assertThrows(IllegalArgumentException.class,
                () -> mService.reportRemoteTaskDone(wrongClientId, taskId));
    }

    @Test
    public void testNotifyApPowerState_waitForVhal() throws Exception {
        mService.init();
        ICarPowerStateListener powerStateListener = getCarPowerStateListener();
        verify(mRemoteAccessHal, timeout(1000)).notifyApStateChange(anyBoolean(), anyBoolean());

        powerStateListener.onStateChanged(CarPowerManager.STATE_WAIT_FOR_VHAL, 0);
        // notifyApStateChange is also called when initializaing CarRemoteAccessService.
        verify(mRemoteAccessHal, times(2)).notifyApStateChange(
                /* isReadyForRemoteTask= */ true, /* isWakeupRequired= */ false);
        verify(mCarPowerManagementService, never())
                .finished(eq(CarPowerManager.STATE_WAIT_FOR_VHAL), any());
    }

    @Test
    public void testNotifyApPowerState_shutdownPrepare() throws Exception {
        mService.init();
        ICarPowerStateListener powerStateListener = getCarPowerStateListener();
        verify(mRemoteAccessHal, timeout(1000)).notifyApStateChange(anyBoolean(), anyBoolean());

        powerStateListener.onStateChanged(CarPowerManager.STATE_SHUTDOWN_PREPARE, 0);
        // TODO(b/268810241): Restore isWakeupRequired to false.
        verify(mRemoteAccessHal).notifyApStateChange(/* isReadyForRemoteTask= */ false,
                /* isWakeupRequired= */ true);
        verify(mCarPowerManagementService).finished(eq(CarPowerManager.STATE_SHUTDOWN_PREPARE),
                any());
    }

    @Test
    public void testNotifyApPowerState_postShutdownEnter() throws Exception {
        mService.init();
        ICarPowerStateListener powerStateListener = getCarPowerStateListener();
        verify(mRemoteAccessHal, timeout(1000)).notifyApStateChange(anyBoolean(), anyBoolean());

        powerStateListener.onStateChanged(CarPowerManager.STATE_POST_SHUTDOWN_ENTER, 0);
        verify(mRemoteAccessHal).notifyApStateChange(/* isReadyForRemoteTask= */ false,
                /* isWakeupRequired= */ true);
        verify(mCarPowerManagementService).finished(eq(CarPowerManager.STATE_POST_SHUTDOWN_ENTER),
                any());
    }

    @Test
    public void testWrappingUpCarRemoteAccessServiceAfterAllowedTime() throws Exception {
        mService.init();
        mService.setPowerStatePostTaskExecution(CarRemoteAccessManager.NEXT_POWER_STATE_OFF,
                /* runGarageMode= */ false);
        SystemClock.sleep(ALLOWED_SYSTEM_UP_TIME_FOR_TESTING_MS);

        verify(mCarPowerManagementService, timeout(WAIT_TIMEOUT_MS))
                .requestShutdownAp(CarRemoteAccessManager.NEXT_POWER_STATE_OFF,
                        /* runGarageMode= */ false);
    }

    @Test
    public void testWrappingUpCarRemoteAccessServiceAfterAllowedTime_vehicleInUse()
            throws Exception {
        setVehicleInUse(/* inUse= */ true);
        mService.init();
        mService.setPowerStatePostTaskExecution(CarRemoteAccessManager.NEXT_POWER_STATE_OFF,
                /* runGarageMode= */ false);
        SystemClock.sleep(ALLOWED_SYSTEM_UP_TIME_FOR_TESTING_MS);

        verify(mCarPowerManagementService, never())
                .requestShutdownAp(CarRemoteAccessManager.NEXT_POWER_STATE_OFF,
                        /* runGarageMode= */ false);
    }

    private ICarPowerStateListener getCarPowerStateListener() {
        ArgumentCaptor<ICarPowerStateListener> internalListenerCaptor =
                ArgumentCaptor.forClass(ICarPowerStateListener.class);
        verify(mCarPowerManagementService).registerListenerWithCompletion(
                internalListenerCaptor.capture());
        return internalListenerCaptor.getValue();
    }

    private RemoteAccessHalCallback prepareCarRemoteTastClient() throws Exception {
        when(mDep.getCallingUid()).thenReturn(UID_PERMISSION_GRANTED_PACKAGE_ONE);
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
        when(mPackageManager.queryIntentServicesAsUser(any(), anyInt(), any()))
                .thenReturn(resolveInfos);
        doAnswer(inv -> {
            Runnable runnable = inv.getArgument(0);
            runnable.run();
            return null;
        }).when(mSystemInterface)
                .scheduleActionForBootCompleted(any(Runnable.class), any(Duration.class));
    }

    private static PackagePrepForTest createPackagePrepForTest(String packageName, String className,
            boolean permissionGranted, int uid) {
        PackagePrepForTest packagePrep = new PackagePrepForTest();
        packagePrep.resolveInfo = new ResolveInfo();
        packagePrep.resolveInfo.serviceInfo = new ServiceInfo();
        packagePrep.resolveInfo.serviceInfo.packageName = packageName;
        packagePrep.resolveInfo.serviceInfo.name = className;
        packagePrep.resolveInfo.serviceInfo.applicationInfo = new ApplicationInfo();
        packagePrep.resolveInfo.serviceInfo.applicationInfo.uid = uid;
        packagePrep.permissionGranted = permissionGranted;
        return packagePrep;
    }

    private static final class PackagePrepForTest {
        public ResolveInfo resolveInfo;
        public boolean permissionGranted;
    }

    private void setVehicleInUse(boolean inUse) {
        when(mPowerHalService.isVehicleInUse()).thenReturn(inUse);
    }

    private void setupDatabase() {
        for (int i = 0; i < PERSISTENT_CLIENTS.size(); i++) {
            ClientIdEntry entry = PERSISTENT_CLIENTS.get(i);
            mRemoteAccessStorage.updateClientId(entry);
        }
    }

    private static final class ICarRemoteAccessCallbackImpl extends ICarRemoteAccessCallback.Stub {
        private String mServiceName;
        private String mVehicleId;
        private String mProcessorId;
        private String mClientId;
        private String mTaskId;
        private byte[] mData;
        private boolean mShutdownStarted;

        @Override
        public void onClientRegistrationUpdated(RemoteTaskClientRegistrationInfo info) {
            mServiceName = info.getServiceId();
            mVehicleId = info.getVehicleId();
            mProcessorId = info.getProcessorId();
            mClientId = info.getClientId();
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

        public String getVehicleId() {
            return mVehicleId;
        }

        public String getProcessorId() {
            return mProcessorId;
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
