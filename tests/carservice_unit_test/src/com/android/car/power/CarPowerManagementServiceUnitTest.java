/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.car.power;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.fail;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertThrows;

import android.app.ActivityManager;
import android.car.Car;
import android.car.hardware.power.CarPowerPolicyFilter;
import android.car.hardware.power.ICarPowerPolicyChangeListener;
import android.car.hardware.power.PowerComponent;
import android.car.test.mocks.AbstractExtendedMockitoTestCase;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.UserInfo;
import android.content.res.Resources;
import android.frameworks.automotive.powerpolicy.internal.ICarPowerPolicySystemNotification;
import android.frameworks.automotive.powerpolicy.internal.PolicyState;
import android.hardware.automotive.vehicle.V2_0.VehicleApPowerStateReq;
import android.hardware.automotive.vehicle.V2_0.VehicleApPowerStateShutdownParam;
import android.os.UserManager;
import android.sysprop.CarProperties;
import android.test.suitebuilder.annotation.SmallTest;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.car.CarLocalServices;
import com.android.car.CarServiceUtils;
import com.android.car.R;
import com.android.car.hal.MockedPowerHalService;
import com.android.car.hal.PowerHalService;
import com.android.car.hal.PowerHalService.PowerState;
import com.android.car.systeminterface.DisplayInterface;
import com.android.car.systeminterface.IOInterface;
import com.android.car.systeminterface.SystemInterface;
import com.android.car.systeminterface.SystemStateInterface;
import com.android.car.systeminterface.WakeLockInterface;
import com.android.car.test.utils.TemporaryDirectory;
import com.android.car.test.utils.TemporaryFile;
import com.android.car.user.CarUserService;
import com.android.internal.app.IVoiceInteractionManagerService;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@SmallTest
public class CarPowerManagementServiceUnitTest extends AbstractExtendedMockitoTestCase {
    private static final String TAG = CarPowerManagementServiceUnitTest.class.getSimpleName();
    private static final long WAIT_TIMEOUT_MS = 2000;
    private static final long WAIT_TIMEOUT_LONG_MS = 5000;
    private static final int WAKE_UP_DELAY = 100;
    private static final String NONSILENT_STRING = "0";

    private static final int CURRENT_USER_ID = 42;
    private static final int CURRENT_GUEST_ID = 108; // must be different than CURRENT_USER_ID;
    private static final int NEW_GUEST_ID = 666;

    private final MockDisplayInterface mDisplayInterface = new MockDisplayInterface();
    private final MockSystemStateInterface mSystemStateInterface = new MockSystemStateInterface();
    private final MockWakeLockInterface mWakeLockInterface = new MockWakeLockInterface();
    private final MockIOInterface mIOInterface = new MockIOInterface();
    private final PowerSignalListener mPowerSignalListener = new PowerSignalListener();
    @Spy
    private final Context mContext = InstrumentationRegistry.getInstrumentation().getContext();

    private MockedPowerHalService mPowerHal;
    private SystemInterface mSystemInterface;
    private CarPowerManagementService mService;
    private CompletableFuture<Void> mFuture;
    private SilentModeController mSilentModeController;
    private TemporaryFile mSilentModeFile;
    private FakeCarPowerPolicyDaemon mPowerPolicyDaemon;

    @Mock
    private UserManager mUserManager;
    @Mock
    private Resources mResources;
    @Mock
    private CarUserService mUserService;
    @Mock
    private IVoiceInteractionManagerService mVoiceInteractionManagerService;
    @Mock
    private PowerComponentHandler mPowerComponentHandler;

    @Override
    protected void onSessionBuilder(CustomMockitoSessionBuilder session) {
        session
            .spyStatic(ActivityManager.class)
            .spyStatic(CarProperties.class);
    }

    @Before
    public void setUp() throws Exception {
        mPowerHal = new MockedPowerHalService(true /*isPowerStateSupported*/,
                true /*isDeepSleepAllowed*/, true /*isTimedWakeupAllowed*/);
        mSystemInterface = SystemInterface.Builder.defaultSystemInterface(mContext)
            .withDisplayInterface(mDisplayInterface)
            .withSystemStateInterface(mSystemStateInterface)
            .withWakeLockInterface(mWakeLockInterface)
            .withIOInterface(mIOInterface).build();

        setCurrentUser(CURRENT_USER_ID, /* isGuest= */ false);
        setService();
    }

    @After
    public void tearDown() throws Exception {
        if (mService != null) {
            mService.release();
        }
        CarLocalServices.removeServiceForTest(CarPowerManagementService.class);
        CarLocalServices.removeServiceForTest(SilentModeController.class);
        CarServiceUtils.finishAllHandlerTasks();
        mIOInterface.tearDown();
    }

    /**
     * Helper method to create mService and initialize a test case
     */
    private void setService() throws Exception {
        when(mResources.getInteger(R.integer.maxGarageModeRunningDurationInSecs))
                .thenReturn(900);
        Log.i(TAG, "setService(): overridden overlay properties: "
                + ", maxGarageModeRunningDurationInSecs="
                + mResources.getInteger(R.integer.maxGarageModeRunningDurationInSecs));
        mSilentModeFile = new TemporaryFile(TAG);
        mSilentModeFile.write(NONSILENT_STRING);
        mSilentModeController = new SilentModeController(mContext, mSystemInterface,
                mVoiceInteractionManagerService, mSilentModeFile.getPath().toString());
        mPowerPolicyDaemon = new FakeCarPowerPolicyDaemon();
        CarLocalServices.addService(SilentModeController.class, mSilentModeController);
        mService = new CarPowerManagementService(mContext, mResources, mPowerHal, mSystemInterface,
                mUserManager, mUserService, mPowerPolicyDaemon, mPowerComponentHandler);
        CarLocalServices.addService(CarPowerManagementService.class, mService);
        mService.init();
        mSilentModeController.init();
        mService.setShutdownTimersForTest(0, 0);
        mPowerHal.setSignalListener(mPowerSignalListener);
        mService.scheduleNextWakeupTime(WAKE_UP_DELAY);
        assertStateReceived(MockedPowerHalService.SET_WAIT_FOR_VHAL, 0);
    }

    @Test
    public void testDisplayOn() throws Exception {
        // start with display off
        mSystemInterface.setDisplayState(false);
        mDisplayInterface.waitForDisplayOff(WAIT_TIMEOUT_MS);
        // Transition to ON state
        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.ON, 0));

        // display should be turned on as it started with off state.
        mDisplayInterface.waitForDisplayOn(WAIT_TIMEOUT_MS);
    }

    @Test
    public void testShutdown() throws Exception {
        // Transition to ON state
        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.ON, 0));
        mDisplayInterface.waitForDisplayOn(WAIT_TIMEOUT_MS);

        mPowerHal.setCurrentPowerState(
                new PowerState(
                        VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                        VehicleApPowerStateShutdownParam.SHUTDOWN_ONLY));
        assertStateReceivedForShutdownOrSleepWithPostpone(PowerHalService.SET_SHUTDOWN_START);
        assertThat(mService.garageModeShouldExitImmediately()).isFalse();
        mDisplayInterface.waitForDisplayOff(WAIT_TIMEOUT_MS);
        mPowerSignalListener.waitForShutdown(WAIT_TIMEOUT_MS);
        // Send the finished signal
        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.FINISHED, 0));
        mSystemStateInterface.waitForShutdown(WAIT_TIMEOUT_MS);
    }

    @Test
    public void testShutdownImmediately() throws Exception {
        // Transition to ON state
        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.ON, 0));
        mDisplayInterface.waitForDisplayOn(WAIT_TIMEOUT_MS);

        mPowerHal.setCurrentPowerState(
                new PowerState(
                        VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                        VehicleApPowerStateShutdownParam.SHUTDOWN_IMMEDIATELY));
        // Since modules have to manually schedule next wakeup, we should not schedule next wakeup
        // To test module behavior, we need to actually implement mock listener module.
        assertStateReceivedForShutdownOrSleepWithPostpone(PowerHalService.SET_SHUTDOWN_START, 0);
        assertThat(mService.garageModeShouldExitImmediately()).isTrue();
        mDisplayInterface.waitForDisplayOff(WAIT_TIMEOUT_MS);
        mPowerSignalListener.waitForShutdown(WAIT_TIMEOUT_MS);
        // Send the finished signal
        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.FINISHED, 0));
        mSystemStateInterface.waitForShutdown(WAIT_TIMEOUT_MS);
    }

    @Test
    public void testSuspend() throws Exception {
        // Start in the ON state
        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.ON, 0));
        mDisplayInterface.waitForDisplayOn(WAIT_TIMEOUT_MS);
        // Request suspend
        mPowerHal.setCurrentPowerState(
                new PowerState(
                        VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                        VehicleApPowerStateShutdownParam.CAN_SLEEP));
        // Verify suspend
        assertStateReceivedForShutdownOrSleepWithPostpone(PowerHalService.SET_DEEP_SLEEP_ENTRY);
        assertThat(mService.garageModeShouldExitImmediately()).isFalse();
    }

    @Test
    public void testShutdownOnSuspend() throws Exception {
        // Start in the ON state
        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.ON, 0));
        mDisplayInterface.waitForDisplayOn(WAIT_TIMEOUT_MS);
        // Tell it to shutdown
        mService.requestShutdownOnNextSuspend();
        // Request suspend
        mPowerHal.setCurrentPowerState(
                new PowerState(
                        VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                        VehicleApPowerStateShutdownParam.CAN_SLEEP));
        // Verify shutdown
        assertStateReceivedForShutdownOrSleepWithPostpone(PowerHalService.SET_SHUTDOWN_START);
        mPowerSignalListener.waitForShutdown(WAIT_TIMEOUT_MS);
        // Send the finished signal
        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.FINISHED, 0));
        mSystemStateInterface.waitForShutdown(WAIT_TIMEOUT_MS);
        // Cancel the shutdown
        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.CANCEL_SHUTDOWN, 0));
        assertStateReceivedForShutdownOrSleepWithPostpone(PowerHalService.SET_SHUTDOWN_CANCELLED);

        // Request suspend again
        mPowerHal.setCurrentPowerState(
                new PowerState(
                        VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                        VehicleApPowerStateShutdownParam.CAN_SLEEP));
        // Verify suspend
        assertStateReceivedForShutdownOrSleepWithPostpone(PowerHalService.SET_DEEP_SLEEP_ENTRY);
    }

    @Test
    public void testShutdownCancel() throws Exception {
        // Start in the ON state
        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.ON, 0));
        mDisplayInterface.waitForDisplayOn(WAIT_TIMEOUT_MS);
        // Start shutting down
        mPowerHal.setCurrentPowerState(
                new PowerState(
                        VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                        VehicleApPowerStateShutdownParam.SHUTDOWN_IMMEDIATELY));
        assertStateReceivedForShutdownOrSleepWithPostpone(PowerHalService.SET_SHUTDOWN_START, 0);
        // Cancel the shutdown
        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.CANCEL_SHUTDOWN, 0));
        assertStateReceivedForShutdownOrSleepWithPostpone(PowerHalService.SET_SHUTDOWN_CANCELLED);
        // Go to suspend
        mPowerHal.setCurrentPowerState(
                new PowerState(
                        VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                        VehicleApPowerStateShutdownParam.CAN_SLEEP));
        assertStateReceivedForShutdownOrSleepWithPostpone(PowerHalService.SET_DEEP_SLEEP_ENTRY);
    }

    @Test
    public void testSleepImmediately() throws Exception {
        // Transition to ON state
        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.ON, 0));
        mDisplayInterface.waitForDisplayOn(WAIT_TIMEOUT_MS);

        mPowerHal.setCurrentPowerState(
                new PowerState(
                        VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                        VehicleApPowerStateShutdownParam.SLEEP_IMMEDIATELY));
        assertStateReceivedForShutdownOrSleepWithPostpone(PowerHalService.SET_DEEP_SLEEP_ENTRY, 0);
        assertThat(mService.garageModeShouldExitImmediately()).isTrue();
        mPowerSignalListener.waitForSleepEntry(WAIT_TIMEOUT_MS);

        // Send the finished signal from HAL to CPMS
        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.FINISHED, 0));
        mSystemStateInterface.waitForSleepEntryAndWakeup(WAIT_TIMEOUT_MS);
        assertStateReceived(PowerHalService.SET_DEEP_SLEEP_EXIT, 0);
        mPowerSignalListener.waitForSleepExit(WAIT_TIMEOUT_MS);
    }

    @Test
    public void testShutdownWithProcessing() throws Exception {
        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.SHUTDOWN_PREPARE, 0));
        assertStateReceivedForShutdownOrSleepWithPostpone(PowerHalService.SET_SHUTDOWN_START);
        mPowerSignalListener.waitForShutdown(WAIT_TIMEOUT_MS);
        // Send the finished signal
        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.FINISHED, 0));
        mSystemStateInterface.waitForShutdown(WAIT_TIMEOUT_MS);
    }

    @Test
    public void testSleepEntryAndWakeup() throws Exception {
        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                VehicleApPowerStateShutdownParam.CAN_SLEEP));
        assertStateReceivedForShutdownOrSleepWithPostpone(PowerHalService.SET_DEEP_SLEEP_ENTRY);
        mPowerSignalListener.waitForSleepEntry(WAIT_TIMEOUT_MS);
        // Send the finished signal from HAL to CPMS
        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.FINISHED, 0));
        mSystemStateInterface.waitForSleepEntryAndWakeup(WAIT_TIMEOUT_MS);
        assertStateReceived(PowerHalService.SET_DEEP_SLEEP_EXIT, 0);
        mPowerSignalListener.waitForSleepExit(WAIT_TIMEOUT_MS);
    }

    /**
     * This test case tests the same scenario as {@link #testUserSwitchingOnResume_differentUser()},
     * but indirectly triggering {@code switchUserOnResumeIfNecessary()} through HAL events.
     */
    @Test
    public void testSleepEntryAndWakeUpForProcessing() throws Exception {
        // Speed up the polling for power state transitions
        mService.setShutdownTimersForTest(10, 40);

        suspendAndResume();
    }

    @Test
    public void testDefinePowerPolicyFromCommand() throws Exception {
        String policyId = "policy_id_valid";
        String errMsg = mService.definePowerPolicy(policyId, new String[]{"AUDIO", "BLUETOOTH"},
                new String[]{"WIFI"});
        assertThat(errMsg).isNull();
        assertThat(mPowerPolicyDaemon.getLastDefinedPolicyId()).isEqualTo(policyId);

        errMsg = mService.definePowerPolicy(policyId, new String[]{"AUDIO", "BLUTTOOTH"},
                new String[]{"WIFI", "NFC"});
        assertThat(errMsg).isNotNull();

        errMsg = mService.definePowerPolicy(policyId, new String[]{"AUDIO", "INVALID_COMPONENT"},
                new String[]{"WIFI"});
        assertThat(errMsg).isNotNull();
    }

    @Test
    public void testApplyPowerPolicy() throws Exception {
        grantPowerPolicyPermission();
        // Power policy which doesn't change any components.
        String policyId = "policy_id_no_changes";
        mService.definePowerPolicy(policyId, new String[0], new String[0]);
        ArgumentCaptor<android.car.hardware.power.CarPowerPolicy> captor =
                ArgumentCaptor.forClass(
                        android.car.hardware.power.CarPowerPolicy.class);

        mService.applyPowerPolicy(policyId);

        android.car.hardware.power.CarPowerPolicy policy = mService.getCurrentPowerPolicy();
        assertThat(policy.policyId).isEqualTo(policyId);
        assertThat(mPowerPolicyDaemon.getLastNotifiedPolicyId()).isEqualTo(policyId);
        verify(mPowerComponentHandler).applyPowerPolicy(captor.capture());
        assertThat(captor.getValue().policyId).isEqualTo(policyId);
    }

    @Test
    public void testApplyInvalidPowerPolicy() {
        grantPowerPolicyPermission();
        // Power policy which doesn't exist.
        String policyId = "policy_id_not_available";

        assertThrows(IllegalArgumentException.class, () -> mService.applyPowerPolicy(policyId));
    }

    @Test
    public void testRegisterPowerPolicyChangeListener() throws Exception {
        grantPowerPolicyPermission();
        String policyId = "policy_id_enable_audio_disable_wifi";
        mService.definePowerPolicy(policyId, new String[]{"AUDIO"}, new String[]{"WIFI"});
        MockedPowerPolicyChangeListener listenerAudio = new MockedPowerPolicyChangeListener();
        MockedPowerPolicyChangeListener listenerWifi = new MockedPowerPolicyChangeListener();
        MockedPowerPolicyChangeListener listenerLocation = new MockedPowerPolicyChangeListener();
        CarPowerPolicyFilter filterAudio =
                new CarPowerPolicyFilter(new int[]{PowerComponent.AUDIO});
        CarPowerPolicyFilter filterWifi = new CarPowerPolicyFilter(new int[]{PowerComponent.WIFI});
        CarPowerPolicyFilter filterLocation =
                new CarPowerPolicyFilter(new int[]{PowerComponent.LOCATION});

        mService.registerPowerPolicyChangeListener(listenerAudio, filterAudio);
        mService.registerPowerPolicyChangeListener(listenerWifi, filterWifi);
        mService.applyPowerPolicy(policyId);

        assertThat(listenerAudio.getCurrentPowerPolicy().policyId).isEqualTo(policyId);
        assertThat(listenerWifi.getCurrentPowerPolicy().policyId).isEqualTo(policyId);
        assertThat(listenerLocation.getCurrentPowerPolicy()).isNull();
    }

    @Test
    public void testUnregisterPowerPolicyChangeListener() throws Exception {
        grantPowerPolicyPermission();
        String policyId = "policy_id_enable_audio_disable_wifi";
        mService.definePowerPolicy(policyId, new String[]{"AUDIO"}, new String[]{"WIFI"});
        MockedPowerPolicyChangeListener listenerAudio = new MockedPowerPolicyChangeListener();
        CarPowerPolicyFilter filterAudio =
                new CarPowerPolicyFilter(new int[]{PowerComponent.AUDIO});

        mService.registerPowerPolicyChangeListener(listenerAudio, filterAudio);
        mService.unregisterPowerPolicyChangeListener(listenerAudio);
        mService.applyPowerPolicy(policyId);

        assertThat(listenerAudio.getCurrentPowerPolicy()).isNull();
    }

    private void suspendAndResume() throws Exception {
        Log.d(TAG, "suspend()");
        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                VehicleApPowerStateShutdownParam.CAN_SLEEP));
        mDisplayInterface.waitForDisplayOff(WAIT_TIMEOUT_MS);
        assertStateReceivedForShutdownOrSleepWithPostpone(PowerHalService.SET_DEEP_SLEEP_ENTRY);
        assertVoiceInteractionDisabled();
        mPowerSignalListener.waitForSleepEntry(WAIT_TIMEOUT_MS);
        verify(mUserService).onSuspend();

        // Send the finished signal
        Log.d(TAG, "resume()");
        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.FINISHED, 0));
        mSystemStateInterface.setWakeupCausedByTimer(true);
        mSystemStateInterface.waitForSleepEntryAndWakeup(WAIT_TIMEOUT_MS);
        assertStateReceived(PowerHalService.SET_DEEP_SLEEP_EXIT, 0);
        mPowerSignalListener.waitForSleepExit(WAIT_TIMEOUT_MS);
        mService.scheduleNextWakeupTime(WAKE_UP_DELAY);
        // second processing after wakeup
        assertThat(mDisplayInterface.getDisplayState()).isFalse();

        mService.setStateForTesting(/* isBooting= */ false);

        mSilentModeFile.write(NONSILENT_STRING); // Wake non-silently
        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.ON, 0));
        mDisplayInterface.waitForDisplayOn(WAIT_TIMEOUT_MS);
        // Should wait until Handler has finished ON processing.
        CarServiceUtils.runOnLooperSync(mService.getHandlerThread().getLooper(), () -> { });

        verify(mUserService).onResume();

        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                VehicleApPowerStateShutdownParam.CAN_SLEEP));
        assertStateReceivedForShutdownOrSleepWithPostpone(PowerHalService.SET_DEEP_SLEEP_ENTRY);
        mPowerSignalListener.waitForSleepEntry(WAIT_TIMEOUT_MS);

        verify(mUserService, times(2)).onSuspend();

        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.FINISHED, 0));
        // PM will shutdown system as it was not woken-up due timer and it is not power on.
        mSystemStateInterface.setWakeupCausedByTimer(false);
        mSystemStateInterface.waitForSleepEntryAndWakeup(WAIT_TIMEOUT_MS);
        // Since we just woke up from shutdown, wake up time will be 0
        assertStateReceived(PowerHalService.SET_DEEP_SLEEP_EXIT, 0);
        assertVoiceInteractionEnabled();
        assertThat(mDisplayInterface.getDisplayState()).isFalse();
    }

    private void assertStateReceived(int expectedState, int expectedParam) throws Exception {
        int[] state = mPowerHal.waitForSend(WAIT_TIMEOUT_MS);
        assertThat(state[0]).isEqualTo(expectedState);
        assertThat(state[1]).isEqualTo(expectedParam);
    }

    private void assertStateReceivedForShutdownOrSleepWithPostpone(int lastState,
            int expectedSecondParameter)
            throws Exception {
        while (true) {
            if (mFuture != null && !mFuture.isDone()) {
                mFuture.complete(null);
            }
            int[] state = mPowerHal.waitForSend(WAIT_TIMEOUT_LONG_MS);
            if (state[0] == PowerHalService.SET_SHUTDOWN_POSTPONE) {
                continue;
            }
            if (state[0] == lastState) {
                assertThat(state[1]).isEqualTo(expectedSecondParameter);
                return;
            }
        }
    }

    private void assertStateReceivedForShutdownOrSleepWithPostpone(int lastState) throws Exception {
        int expectedSecondParameter =
                (lastState == MockedPowerHalService.SET_DEEP_SLEEP_ENTRY
                        || lastState == MockedPowerHalService.SET_SHUTDOWN_START)
                        ? WAKE_UP_DELAY : 0;
        assertStateReceivedForShutdownOrSleepWithPostpone(lastState, expectedSecondParameter);
    }

    private void assertVoiceInteractionEnabled() throws Exception {
        verify(mVoiceInteractionManagerService,
                timeout(WAIT_TIMEOUT_LONG_MS).atLeastOnce()).setDisabled(false);
    }

    private void assertVoiceInteractionDisabled() throws Exception {
        verify(mVoiceInteractionManagerService,
                timeout(WAIT_TIMEOUT_LONG_MS).atLeastOnce()).setDisabled(true);
    }

    private static void waitForSemaphore(Semaphore semaphore, long timeoutMs)
            throws InterruptedException {
        if (!semaphore.tryAcquire(timeoutMs, TimeUnit.MILLISECONDS)) {
            throw new IllegalStateException("timeout");
        }
    }

    private UserInfo setCurrentUser(int userId, boolean isGuest) {
        mockGetCurrentUser(userId);
        final UserInfo userInfo = new UserInfo();
        userInfo.id = userId;
        userInfo.userType = isGuest
                ? UserManager.USER_TYPE_FULL_GUEST
                : UserManager.USER_TYPE_FULL_SECONDARY;
        Log.v(TAG, "UM.getUserInfo("  + userId + ") will return " + userInfo.toFullString());
        when(mUserManager.getUserInfo(userId)).thenReturn(userInfo);
        return userInfo;
    }

    private void grantPowerPolicyPermission() {
        doReturn(PackageManager.PERMISSION_GRANTED).when(mContext)
                .checkCallingOrSelfPermission(Car.PERMISSION_CONTROL_CAR_POWER_POLICY);
        doReturn(PackageManager.PERMISSION_GRANTED).when(mContext)
                .checkCallingOrSelfPermission(Car.PERMISSION_READ_CAR_POWER_POLICY);
    }

    private static final class MockDisplayInterface implements DisplayInterface {
        private boolean mDisplayOn = true;
        private final Semaphore mDisplayStateWait = new Semaphore(0);

        @Override
        public void setDisplayBrightness(int brightness) {}

        @Override
        public synchronized void setDisplayState(boolean on) {
            mDisplayOn = on;
            mDisplayStateWait.release();
        }

        public synchronized boolean getDisplayState() {
            return mDisplayOn;
        }

        private void waitForDisplayOn(long timeoutMs) throws Exception {
            waitForDisplayState(true, timeoutMs);
        }

        private void waitForDisplayOff(long timeoutMs) throws Exception {
            waitForDisplayState(false, timeoutMs);
        }

        private void waitForDisplayState(boolean desiredState, long timeoutMs) throws Exception {
            int nTries = 0;
            while (mDisplayOn != desiredState) {
                if (nTries > 5) throw new IllegalStateException("timeout");
                waitForSemaphore(mDisplayStateWait, timeoutMs);
                nTries++;
            }
        }

        @Override
        public void startDisplayStateMonitoring(CarPowerManagementService service) {}

        @Override
        public void stopDisplayStateMonitoring() {}

        @Override
        public void refreshDisplayBrightness() {}
    }

    private static final class MockSystemStateInterface implements SystemStateInterface {
        private final Semaphore mShutdownWait = new Semaphore(0);
        private final Semaphore mSleepWait = new Semaphore(0);
        private final Semaphore mSleepExitWait = new Semaphore(0);
        private boolean mWakeupCausedByTimer = false;

        @Override
        public void shutdown() {
            mShutdownWait.release();
        }

        public void waitForShutdown(long timeoutMs) throws Exception {
            waitForSemaphore(mShutdownWait, timeoutMs);
        }

        @Override
        public boolean enterDeepSleep() {
            mSleepWait.release();
            try {
                mSleepExitWait.acquire();
            } catch (InterruptedException e) {
            }
            return true;
        }

        public void waitForSleepEntryAndWakeup(long timeoutMs) throws Exception {
            waitForSemaphore(mSleepWait, timeoutMs);
            mSleepExitWait.release();
        }

        @Override
        public void scheduleActionForBootCompleted(Runnable action, Duration delay) {}

        @Override
        public boolean isWakeupCausedByTimer() {
            Log.i(TAG, "isWakeupCausedByTimer:" + mWakeupCausedByTimer);
            return mWakeupCausedByTimer;
        }

        public synchronized void setWakeupCausedByTimer(boolean set) {
            mWakeupCausedByTimer = set;
        }

        @Override
        public boolean isSystemSupportingDeepSleep() {
            return true;
        }
    }

    private static final class MockWakeLockInterface implements WakeLockInterface {

        @Override
        public void releaseAllWakeLocks() {}

        @Override
        public void switchToPartialWakeLock() {}

        @Override
        public void switchToFullWakeLock() {}
    }

    private static final class MockIOInterface implements IOInterface {
        private TemporaryDirectory mFilesDir;

        @Override
        public File getSystemCarDir() {
            if (mFilesDir == null) {
                try {
                    mFilesDir = new TemporaryDirectory(TAG);
                } catch (IOException e) {
                    Log.e(TAG, "failed to create temporary directory", e);
                    fail("failed to create temporary directory. exception was: " + e);
                }
            }
            return mFilesDir.getDirectory();
        }

        public void tearDown() {
            if (mFilesDir != null) {
                try {
                    mFilesDir.close();
                } catch (Exception e) {
                    Log.w(TAG, "could not remove temporary directory", e);
                }
            }
        }
    }

    private class PowerSignalListener implements MockedPowerHalService.SignalListener {
        private final Semaphore mShutdownWait = new Semaphore(0);
        private final Semaphore mSleepEntryWait = new Semaphore(0);
        private final Semaphore mSleepExitWait = new Semaphore(0);

        public void waitForSleepExit(long timeoutMs) throws Exception {
            waitForSemaphore(mSleepExitWait, timeoutMs);
        }

        public void waitForShutdown(long timeoutMs) throws Exception {
            waitForSemaphore(mShutdownWait, timeoutMs);
        }

        public void waitForSleepEntry(long timeoutMs) throws Exception {
            waitForSemaphore(mSleepEntryWait, timeoutMs);
        }

        @Override
        public void sendingSignal(int signal) {
            if (signal == PowerHalService.SET_SHUTDOWN_START) {
                mShutdownWait.release();
                return;
            }
            if (signal == PowerHalService.SET_DEEP_SLEEP_ENTRY) {
                mSleepEntryWait.release();
                return;
            }
            if (signal == PowerHalService.SET_DEEP_SLEEP_EXIT) {
                mSleepExitWait.release();
                return;
            }
        }
    }

    static final class FakeCarPowerPolicyDaemon extends ICarPowerPolicySystemNotification.Stub {
        private String mLastNofitiedPolicyId;
        private String mLastDefinedPolicyId;

        @Override
        public PolicyState notifyCarServiceReady() {
            // do nothing
            return null;
        }

        @Override
        public void notifyPowerPolicyChange(String policyId) {
            mLastNofitiedPolicyId = policyId;
        }

        @Override
        public void notifyPowerPolicyDefinition(String policyId, String[] enabledComponents,
                String[] disabledComponents) {
            mLastDefinedPolicyId = policyId;
        }

        public String getLastNotifiedPolicyId() {
            return mLastNofitiedPolicyId;
        }

        public String getLastDefinedPolicyId() {
            return mLastDefinedPolicyId;
        }
    }

    private final class MockedPowerPolicyChangeListener extends ICarPowerPolicyChangeListener.Stub {
        private android.car.hardware.power.CarPowerPolicy mCurrentPowerPolicy;

        @Override
        public void onPolicyChanged(android.car.hardware.power.CarPowerPolicy policy) {
            mCurrentPowerPolicy = policy;
        }

        public android.car.hardware.power.CarPowerPolicy getCurrentPowerPolicy() {
            return mCurrentPowerPolicy;
        }
    }
}
