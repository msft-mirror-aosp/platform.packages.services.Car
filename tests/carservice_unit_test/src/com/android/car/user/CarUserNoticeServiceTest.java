/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.car.user;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.AppOpsManager;
import android.car.hardware.power.CarPowerManager;
import android.car.hardware.power.CarPowerManager.CarPowerStateListener;
import android.car.settings.CarSettings;
import android.car.test.mocks.AbstractExtendMockitoTestCase;
import android.car.user.CarUserManager;
import android.car.user.CarUserManager.UserLifecycleEvent;
import android.car.user.CarUserManager.UserLifecycleListener;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.UserHandle;
import android.provider.Settings;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.car.CarLocalServices;
import com.android.car.CarPowerManagementService;
import com.android.car.R;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class CarUserNoticeServiceTest extends AbstractExtendMockitoTestCase {

    @Mock
    private Context mMockContext;
    @Mock
    private Resources mMockedResources;
    @Mock
    private CarPowerManagementService mMockCarPowerManagementService;
    @Mock
    private CarUserService mMockCarUserService;
    @Mock
    private PowerManager mMockPowerManager;
    @Mock
    private AppOpsManager mMockAppOpsManager;
    @Mock
    private PackageManager mMockPackageManager;
    @Mock
    private CarPowerManager mCarPowerManager;

    @Captor
    private ArgumentCaptor<BroadcastReceiver> mDisplayBroadcastReceiver;

    @Captor
    private ArgumentCaptor<UserLifecycleListener> mUserLifecycleListenerArgumentCaptor;

    @Captor
    private ArgumentCaptor<CarPowerStateListener> mPowerStateListener;

    private CarUserNoticeService mCarUserNoticeService;

    private final Handler mHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onSessionBuilder(CustomMockitoSessionBuilder session) {
        session
            .mockStatic(CarLocalServices.class)
            .mockStatic(Settings.Secure.class);
    }

    /**
     * Initialize all of the objects with the @Mock annotation.
     */
    @Before
    public void setUpMocks() throws Exception {
        doReturn(mCarPowerManager).when(() -> CarLocalServices.createCarPowerManager(mMockContext));
        doReturn(mMockCarPowerManagementService)
                .when(() -> CarLocalServices.getService(CarPowerManagementService.class));
        doReturn(mCarPowerManager).when(() -> CarLocalServices.createCarPowerManager(mMockContext));
        doReturn(mMockCarUserService)
                .when(() -> CarLocalServices.getService(CarUserService.class));

        doReturn(1).when(() -> Settings.Secure.getIntForUser(any(),
                eq(CarSettings.Secure.KEY_ENABLE_INITIAL_NOTICE_SCREEN_TO_USER), anyInt(),
                anyInt()));

        doReturn(mMockedResources).when(mMockContext).getResources();
        doReturn(InstrumentationRegistry.getInstrumentation().getTargetContext()
                .getContentResolver())
                        .when(mMockContext).getContentResolver();
        doReturn("com.foo/.Blah").when(mMockedResources).getString(anyInt());
        doReturn(mMockPowerManager).when(mMockContext).getSystemService(PowerManager.class);
        doReturn(mMockAppOpsManager).when(mMockContext).getSystemService(AppOpsManager.class);
        doReturn(mMockPackageManager).when(mMockContext).getPackageManager();
        doReturn(1).when(mMockPackageManager).getPackageUidAsUser(any(), anyInt());
        mCarUserNoticeService = new CarUserNoticeService(mMockContext, mHandler);
        mCarUserNoticeService.init();
        verify(mMockCarUserService).addUserLifecycleListener(
                mUserLifecycleListenerArgumentCaptor.capture());
        verify(mMockContext).registerReceiver(mDisplayBroadcastReceiver.capture(),
                any(IntentFilter.class));
        verify(mCarPowerManager).setListener(mPowerStateListener.capture());
    }

    @Test
    public void featureDisabledTest() {
        Context mockContext = mock(Context.class);
        // if feature is disabled, Resources.getString will return an
        // empty string
        doReturn("").when(mMockedResources).getString(R.string.config_userNoticeUiService);
        doReturn(mMockedResources).when(mockContext).getResources();
        CarUserNoticeService carUserNoticeService = new CarUserNoticeService(mockContext);
        carUserNoticeService.init();
        verify(mockContext, never()).registerReceiver(any(), any());
    }

    @Test
    public void uiHiddenWhenBroadcastOffReceived() throws Exception {
        setUser();
        // reset UI
        setDisplayOff();
        CountDownLatch latch = mockUnbindService();
        sendBroadcast(Intent.ACTION_SCREEN_OFF);
        assetLatchCalled(latch);
    }

    @Test
    public void uiShownWhenBroadcastOnReceived() throws Exception {
        setUser();
        // reset UI
        setDisplayOff();
        CountDownLatch latch = mockUnbindService();
        sendBroadcast(Intent.ACTION_SCREEN_OFF);
        assetLatchCalled(latch);

        // send screen on broadcast
        setDisplayOn();
        latch = mockBindService();
        sendBroadcast(Intent.ACTION_SCREEN_ON);
        assetLatchCalled(latch);
    }

    @Test
    public void uiHiddenWhenPowerShutDown() throws Exception {
        setUser();
        // reset UI
        setDisplayOff();
        CountDownLatch latch = mockUnbindService();
        sendPowerStateChange(CarPowerManager.CarPowerStateListener.SHUTDOWN_PREPARE);
        assetLatchCalled(latch);
    }

    @Test
    public void uiShownWhenPowerOn() throws Exception {
        setUser();
        // reset UI
        setDisplayOff();
        CountDownLatch latch = mockUnbindService();
        sendPowerStateChange(CarPowerManager.CarPowerStateListener.SHUTDOWN_PREPARE);
        assetLatchCalled(latch);

        // send Power On
        setDisplayOn();
        latch = mockBindService();
        sendPowerStateChange(CarPowerManager.CarPowerStateListener.ON);
        assetLatchCalled(latch);
    }

    @Test
    public void uiNotShownIfKeyDisabled() throws Exception {
        setUser();
        // reset UI
        setDisplayOff();
        CountDownLatch latch = mockUnbindService();
        sendBroadcast(Intent.ACTION_SCREEN_OFF);
        assetLatchCalled(latch);

        // UI not shown if key is disabled
        setDisplayOn();
        latch = mockKeySettings(
                CarSettings.Secure.KEY_ENABLE_INITIAL_NOTICE_SCREEN_TO_USER, 0);
        sendBroadcast(Intent.ACTION_SCREEN_ON);
        assetLatchCalled(latch);
        // invoked only once, when user switched
        verify(mMockContext, times(1)).bindServiceAsUser(any(), any(), anyInt(), any());
    }

    private void assetLatchCalled(CountDownLatch latch) throws Exception {
        assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
    }

    private void switchUser(int userId) throws Exception {
        // Notify listeners about user switch.
        mUserLifecycleListenerArgumentCaptor.getValue().onEvent(new UserLifecycleEvent(
                CarUserManager.USER_LIFECYCLE_EVENT_TYPE_SWITCHING, userId));
    }

    private CountDownLatch mockBindService() {
        CountDownLatch latch = new CountDownLatch(1);
        when(mMockContext.bindServiceAsUser(any(), any(), anyInt(), any()))
                .thenAnswer(inv -> {
                    latch.countDown();
                    return true;
                });
        return latch;
    }

    private CountDownLatch mockUnbindService() {
        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(inv -> {
            latch.countDown();
            return null;
        }).when(mMockContext).unbindService(any());
        return latch;
    }

    private CountDownLatch mockKeySettings(String key, int value) {
        CountDownLatch latch = new CountDownLatch(1);
        when(Settings.Secure.getIntForUser(any(),
                eq(key), anyInt(),
                anyInt()))
                        .thenAnswer(inv -> {
                            latch.countDown();
                            return value;
                        });
        return latch;
    }

    private void setDisplayOn() {
        doReturn(true).when(mMockPowerManager).isInteractive();
    }

    private void setDisplayOff() {
        doReturn(false).when(mMockPowerManager).isInteractive();
    }

    private void sendBroadcast(String action) {
        Intent intent = new Intent();
        intent.setAction(action);
        mHandler.post(() -> mDisplayBroadcastReceiver.getValue().onReceive(mMockContext, intent));
    }

    private void sendPowerStateChange(int state) {
        mPowerStateListener.getValue().onStateChanged(state);
    }

    private void setUser() throws Exception {
        // switch user (required to set user)
        setDisplayOn();
        CountDownLatch latch = mockBindService();
        switchUser(UserHandle.MIN_SECONDARY_USER_ID);
        assetLatchCalled(latch);
    }
}
