/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.car.vms;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import android.car.userlib.CarUserManagerHelper;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.UserHandle;

import androidx.test.filters.SmallTest;

import com.android.car.hal.VmsHalService;
import com.android.car.user.CarUserService;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@SmallTest
public class VmsClientManagerTest {
    private static final String HAL_CLIENT_NAME = "VmsHalClient";
    private static final String SYSTEM_CLIENT_NAME =
            "com.google.android.apps.vms.test/com.google.android.apps.vms.test.VmsSystemClient U=0";
    private static final String USER_CLIENT_NAME =
            "com.google.android.apps.vms.test/com.google.android.apps.vms.test.VmsUserClient U=10";
    @Rule
    public MockitoRule mMockitoRule = MockitoJUnit.rule();
    @Mock
    private Context mContext;
    @Mock
    private PackageManager mPackageManager;
    @Mock
    private Resources mResources;

    @Mock
    private CarUserService mUserService;
    @Mock
    private CarUserManagerHelper mUserManager;
    private int mUserId;

    @Mock
    private VmsHalService mHal;
    private IBinder mHalClient;

    @Mock
    private VmsClientManager.ConnectionListener mConnectionListener;
    private VmsClientManager mClientManager;

    @Captor
    private ArgumentCaptor<ServiceConnection> mConnectionCaptor;

    @Before
    public void setUp() {
        resetContext();
        when(mPackageManager.isPackageAvailable(any())).thenReturn(true);

        when(mResources.getInteger(
                com.android.car.R.integer.millisecondsBeforeRebindToVmsPublisher)).thenReturn(
                5);
        when(mResources.getStringArray(
                com.android.car.R.array.vmsPublisherSystemClients)).thenReturn(
                new String[]{
                        "com.google.android.apps.vms.test/.VmsSystemClient"
                });
        when(mResources.getStringArray(
                com.android.car.R.array.vmsPublisherUserClients)).thenReturn(
                new String[]{
                        "com.google.android.apps.vms.test/.VmsUserClient"
                });

        mUserId = 10;
        when(mUserManager.getCurrentForegroundUserId()).thenAnswer((invocation) -> mUserId);

        mHalClient = new Binder();
        when(mHal.getPublisherClient()).thenReturn(mHalClient);

        mClientManager = new VmsClientManager(mContext, mUserService, mUserManager, mHal);
        mClientManager.registerConnectionListener(mConnectionListener);
        verify(mConnectionListener).onClientConnected(HAL_CLIENT_NAME, mHalClient);
        reset(mConnectionListener);
    }

    @After
    public void tearDown() throws Exception {
        Thread.sleep(10); // Time to allow for delayed rebinds to settle
        verify(mContext, atLeast(0)).getResources();
        verify(mContext, atLeast(0)).getPackageManager();
        verifyNoMoreInteractions(mContext);
    }

    @Test
    public void testInit() {
        mClientManager.init();

        // Verify registration of system user unlock listener
        verify(mUserService).runOnUser0Unlock(mClientManager.mSystemUserUnlockedListener);

        // Verify registration of user switch receiver
        ArgumentCaptor<IntentFilter> userFilterCaptor = ArgumentCaptor.forClass(IntentFilter.class);
        verify(mContext).registerReceiverAsUser(eq(mClientManager.mUserSwitchReceiver),
                eq(UserHandle.ALL), userFilterCaptor.capture(), isNull(), isNull());
        IntentFilter userEventFilter = userFilterCaptor.getValue();
        assertEquals(2, userEventFilter.countActions());
        assertTrue(userEventFilter.hasAction(Intent.ACTION_USER_SWITCHED));
        assertTrue(userEventFilter.hasAction(Intent.ACTION_USER_UNLOCKED));
    }

    @Test
    public void testRelease() {
        mClientManager.release();

        // Verify user switch receiver is unregistered
        verify(mContext).unregisterReceiver(mClientManager.mUserSwitchReceiver);
    }

    @Test
    public void testRegisterConnectionListener() {
        VmsClientManager.ConnectionListener listener =
                Mockito.mock(VmsClientManager.ConnectionListener.class);
        mClientManager.registerConnectionListener(listener);
        verify(listener).onClientConnected(HAL_CLIENT_NAME, mHalClient);
    }

    @Test
    public void testRegisterConnectionListener_AfterClientsConnected() {
        IBinder systemBinder = bindSystemClient();
        IBinder userBinder = bindUserClient();

        VmsClientManager.ConnectionListener listener =
                Mockito.mock(VmsClientManager.ConnectionListener.class);
        mClientManager.registerConnectionListener(listener);
        verify(listener).onClientConnected(HAL_CLIENT_NAME, mHalClient);
        verify(listener).onClientConnected(eq(SYSTEM_CLIENT_NAME), eq(systemBinder));
        verify(listener).onClientConnected(eq(USER_CLIENT_NAME), eq(userBinder));
    }

    @Test
    public void testSystemUserUnlocked() {
        notifySystemUserUnlocked();
        notifySystemUserUnlocked();

        // Multiple events should only trigger a single bind, when successful
        verifySystemBind(1);
    }

    @Test
    public void testSystemUserUnlocked_BindFailed() {
        when(mContext.bindServiceAsUser(any(), any(), anyInt(), any(), any())).thenReturn(false);
        notifySystemUserUnlocked();
        notifySystemUserUnlocked();

        // Failure state will trigger another attempt on event
        verifySystemBind(2);
    }

    @Test
    public void testSystemUserUnlocked_BindException() {
        when(mContext.bindServiceAsUser(any(), any(), anyInt(), any(), any())).thenThrow(
                new SecurityException());
        notifySystemUserUnlocked();
        notifySystemUserUnlocked();

        // Failure state will trigger another attempt on event
        verifySystemBind(2);
    }

    @Test
    public void testUserSwitched() {
        notifyUserSwitched();
        notifyUserSwitched();

        // Multiple events should only trigger a single bind, when successful
        verifySystemBind(1);
        verifyUserBind(1);
    }

    @Test
    public void testUserSwitched_BindFailed() {
        when(mContext.bindServiceAsUser(any(), any(), anyInt(), any(), any()))
                .thenReturn(false);
        notifyUserSwitched();
        notifyUserSwitched();

        // Failure state will trigger another attempt
        verifySystemBind(2);
        verifyUserBind(2);
    }

    @Test
    public void testUserSwitched_UserBindFailed() {
        when(mContext.bindServiceAsUser(any(), any(), anyInt(), any(), eq(UserHandle.of(mUserId))))
                .thenReturn(false);
        notifyUserSwitched();
        notifyUserSwitched();

        verifySystemBind(1);
        verifyUserBind(2); // Failure state will trigger another attempt
    }

    @Test
    public void testUserSwitched_SystemBindFailed() {
        when(mContext.bindServiceAsUser(any(), any(), anyInt(), any(), eq(UserHandle.SYSTEM)))
                .thenReturn(false);
        notifyUserSwitched();
        notifyUserSwitched();

        verifySystemBind(2); // Failure state will trigger another attempt
        verifyUserBind(1);
    }

    @Test
    public void testUserSwitched_BindException() {
        when(mContext.bindServiceAsUser(any(), any(), anyInt(), any(), any()))
                .thenThrow(new SecurityException());
        notifyUserSwitched();
        notifyUserSwitched();

        // Failure state will trigger another attempt
        verifySystemBind(2);
        verifyUserBind(2);
    }

    @Test
    public void testUserSwitched_UserBindException() {
        when(mContext.bindServiceAsUser(any(), any(), anyInt(), any(), eq(UserHandle.of(mUserId))))
                .thenThrow(new SecurityException());
        notifyUserSwitched();
        notifyUserSwitched();

        verifySystemBind(1);
        verifyUserBind(2); // Failure state will trigger another attempt
    }

    @Test
    public void testUserSwitched_SystemBindException() {
        when(mContext.bindServiceAsUser(any(), any(), anyInt(), any(), eq(UserHandle.SYSTEM)))
                .thenThrow(new SecurityException());
        notifyUserSwitched();
        notifyUserSwitched();

        verifySystemBind(2); // Failure state will trigger another attempt
        verifyUserBind(1);
    }

    @Test
    public void testUserUnlocked() {
        notifyUserUnlocked();
        notifyUserUnlocked();

        // Multiple events should only trigger a single bind, when successful
        verifySystemBind(1);
        verifyUserBind(1);
    }

    @Test
    public void testUserUnlocked_BindFailed() {
        when(mContext.bindServiceAsUser(any(), any(), anyInt(), any(), any()))
                .thenReturn(false);
        notifyUserUnlocked();
        notifyUserUnlocked();

        // Failure state will trigger another attempt
        verifySystemBind(2);
        verifyUserBind(2);
    }

    @Test
    public void testUserUnlocked_UserBindFailed() {
        when(mContext.bindServiceAsUser(any(), any(), anyInt(), any(), eq(UserHandle.of(mUserId))))
                .thenReturn(false);
        notifyUserUnlocked();
        notifyUserUnlocked();

        verifySystemBind(1);
        verifyUserBind(2); // Failure state will trigger another attempt
    }


    @Test
    public void testUserUnlocked_SystemBindFailed() {
        when(mContext.bindServiceAsUser(any(), any(), anyInt(), any(), eq(UserHandle.SYSTEM)))
                .thenReturn(false);
        notifyUserUnlocked();
        notifyUserUnlocked();

        verifySystemBind(2); // Failure state will trigger another attempt
        verifyUserBind(1);
    }

    @Test
    public void testUserUnlocked_BindException() {
        when(mContext.bindServiceAsUser(any(), any(), anyInt(), any(), any()))
                .thenThrow(new SecurityException());
        notifyUserUnlocked();
        notifyUserUnlocked();

        // Failure state will trigger another attempt
        verifySystemBind(2);
        verifyUserBind(2);
    }

    @Test
    public void testUserUnlocked_UserBindException() {
        when(mContext.bindServiceAsUser(any(), any(), anyInt(), any(), eq(UserHandle.of(mUserId))))
                .thenThrow(new SecurityException());
        notifyUserUnlocked();
        notifyUserUnlocked();

        verifySystemBind(1);
        verifyUserBind(2); // Failure state will trigger another attempt
    }

    @Test
    public void testUserUnlocked_SystemBindException() {
        when(mContext.bindServiceAsUser(any(), any(), anyInt(), any(), eq(UserHandle.SYSTEM)))
                .thenThrow(new SecurityException());
        notifyUserUnlocked();
        notifyUserUnlocked();

        verifySystemBind(2); // Failure state will trigger another attempt
        verifyUserBind(1);
    }

    @Test
    public void testUserSwitchedAndUnlocked() {
        notifyUserSwitched();
        notifyUserUnlocked();

        // Multiple events should only trigger a single bind, when successful
        verifySystemBind(1);
        verifyUserBind(1);
    }

    @Test
    public void testUserSwitchedToSystemUser() {
        mUserId = UserHandle.USER_SYSTEM;
        notifyUserSwitched();

        verifySystemBind(1);
        // User processes will not be bound for system user
        verifyUserBind(0);
    }

    @Test
    public void testUnregisterConnectionListener() {
        mClientManager.unregisterConnectionListener(mConnectionListener);
        notifySystemUserUnlocked();
        verifySystemBind(1);

        ServiceConnection connection = mConnectionCaptor.getValue();
        connection.onServiceConnected(null, new Binder());
        verifyZeroInteractions(mConnectionListener);
    }

    @Test
    public void testOnSystemServiceConnected() {
        IBinder binder = bindSystemClient();
        verify(mConnectionListener).onClientConnected(eq(SYSTEM_CLIENT_NAME), eq(binder));
    }

    private IBinder bindSystemClient() {
        notifySystemUserUnlocked();
        verifySystemBind(1);
        resetContext();

        IBinder binder = new Binder();
        ServiceConnection connection = mConnectionCaptor.getValue();
        connection.onServiceConnected(null, binder);
        return binder;
    }

    @Test
    public void testOnUserServiceConnected() {
        IBinder binder = bindUserClient();
        verify(mConnectionListener).onClientConnected(eq(USER_CLIENT_NAME), eq(binder));
    }

    private IBinder bindUserClient() {
        notifyUserSwitched();
        verifyUserBind(1);
        resetContext();

        IBinder binder = new Binder();
        ServiceConnection connection = mConnectionCaptor.getValue();
        connection.onServiceConnected(null, binder);
        return binder;
    }

    @Test
    public void testOnSystemServiceDisconnected() throws Exception {
        notifySystemUserUnlocked();
        verifySystemBind(1);
        resetContext();

        ServiceConnection connection = mConnectionCaptor.getValue();
        connection.onServiceConnected(null, new Binder());
        connection.onServiceDisconnected(null);

        verify(mContext).unbindService(connection);
        verify(mConnectionListener).onClientDisconnected(eq(SYSTEM_CLIENT_NAME));

        Thread.sleep(10);
        verifySystemBind(1);
    }

    @Test
    public void testOnSystemServiceDisconnected_ServiceNotConnected() throws Exception {
        notifySystemUserUnlocked();
        verifySystemBind(1);
        resetContext();

        ServiceConnection connection = mConnectionCaptor.getValue();
        connection.onServiceDisconnected(null);

        verify(mContext).unbindService(connection);
        verifyZeroInteractions(mConnectionListener);

        Thread.sleep(10);
        verifySystemBind(1);
    }

    @Test
    public void testOnUserServiceDisconnected() throws Exception {
        notifyUserSwitched();
        verifyUserBind(1);
        resetContext();

        ServiceConnection connection = mConnectionCaptor.getValue();
        connection.onServiceConnected(null, new Binder());
        connection.onServiceDisconnected(null);

        verify(mContext).unbindService(connection);
        verify(mConnectionListener).onClientDisconnected(eq(USER_CLIENT_NAME));

        Thread.sleep(10);
        verifyUserBind(1);
    }

    @Test
    public void testOnUserServiceDisconnected_ServiceNotConnected() throws Exception {
        notifyUserSwitched();
        verifyUserBind(1);
        resetContext();

        ServiceConnection connection = mConnectionCaptor.getValue();
        connection.onServiceDisconnected(null);

        verify(mContext).unbindService(connection);
        verifyZeroInteractions(mConnectionListener);

        Thread.sleep(10);
        verifyUserBind(1);
    }

    @Test
    public void testOnUserSwitched_UserChange() {
        notifyUserSwitched();
        verifyUserBind(1);
        ServiceConnection connection = mConnectionCaptor.getValue();
        connection.onServiceConnected(null, new Binder());
        resetContext();
        reset(mConnectionListener);

        mUserId = 11;
        notifyUserSwitched();

        verify(mContext).unbindService(connection);
        verify(mConnectionListener).onClientDisconnected(eq(USER_CLIENT_NAME));
        verifyUserBind(1);
    }

    @Test
    public void testOnUserSwitched_UserChange_ToSystemUser() {
        notifyUserUnlocked();
        verifySystemBind(1);
        notifyUserSwitched();
        verifyUserBind(1);
        ServiceConnection connection = mConnectionCaptor.getValue();
        connection.onServiceConnected(null, new Binder());
        resetContext();
        reset(mConnectionListener);

        mUserId = UserHandle.USER_SYSTEM;
        notifyUserSwitched();

        verify(mContext).unbindService(connection);
        verify(mConnectionListener).onClientDisconnected(eq(USER_CLIENT_NAME));
        // User processes will not be bound for system user
        verifyUserBind(0);
    }

    @Test
    public void testOnUserSwitched_UserChange_ServiceNotConnected() {
        notifyUserSwitched();
        verifyUserBind(1);
        ServiceConnection connection = mConnectionCaptor.getValue();
        resetContext();

        mUserId = 11;
        notifyUserSwitched();

        verify(mContext).unbindService(connection);
        verifyUserBind(1);
    }

    @Test
    public void testOnUserUnlocked_UserChange() {
        notifyUserUnlocked();
        verifyUserBind(1);
        ServiceConnection connection = mConnectionCaptor.getValue();
        connection.onServiceConnected(null, new Binder());
        resetContext();
        reset(mConnectionListener);

        mUserId = 11;
        notifyUserUnlocked();

        verify(mContext).unbindService(connection);
        verify(mConnectionListener).onClientDisconnected(eq(USER_CLIENT_NAME));
        verifyUserBind(1);
    }

    @Test
    public void testOnUserUnlocked_UserChange_ToSystemUser() {
        notifySystemUserUnlocked();
        verifySystemBind(1);
        notifyUserUnlocked();
        verifyUserBind(1);
        ServiceConnection connection = mConnectionCaptor.getValue();
        connection.onServiceConnected(null, new Binder());
        resetContext();
        reset(mConnectionListener);

        mUserId = UserHandle.USER_SYSTEM;
        notifyUserUnlocked();

        verify(mContext).unbindService(connection);
        verify(mConnectionListener).onClientDisconnected(eq(USER_CLIENT_NAME));
        // User processes will not be bound for system user
        verifyUserBind(0);
    }

    @Test
    public void testOnUserUnlocked_UserChange_ServiceNotConnected() {
        notifyUserUnlocked();
        verifyUserBind(1);
        ServiceConnection connection = mConnectionCaptor.getValue();
        resetContext();

        mUserId = 11;
        notifyUserUnlocked();

        verify(mContext).unbindService(connection);
        verifyUserBind(1);
    }

    private void resetContext() {
        reset(mContext);
        when(mContext.getPackageManager()).thenReturn(mPackageManager);
        when(mContext.bindServiceAsUser(any(), any(), anyInt(), any(), any())).thenReturn(true);
        when(mContext.getResources()).thenReturn(mResources);
    }

    private void notifySystemUserUnlocked() {
        mClientManager.mSystemUserUnlockedListener.run();
    }

    private void notifyUserSwitched() {
        mClientManager.mUserSwitchReceiver.onReceive(mContext,
                new Intent(Intent.ACTION_USER_SWITCHED));
    }

    private void notifyUserUnlocked() {
        mClientManager.mUserSwitchReceiver.onReceive(mContext,
                new Intent(Intent.ACTION_USER_UNLOCKED));
    }

    private void verifySystemBind(int times) {
        verifyBind(times, "com.google.android.apps.vms.test/.VmsSystemClient",
                UserHandle.SYSTEM);
    }

    private void verifyUserBind(int times) {
        verifyBind(times, "com.google.android.apps.vms.test/.VmsUserClient",
                UserHandle.of(mUserId));
    }

    private void verifyBind(int times, String componentName,
            UserHandle user) {
        ArgumentCaptor<Intent> intentCaptor = ArgumentCaptor.forClass(Intent.class);
        Intent expectedService = new Intent();
        expectedService.setComponent(ComponentName.unflattenFromString(componentName));
        verify(mContext, times(times)).bindServiceAsUser(
                argThat((service) -> service.filterEquals(expectedService)),
                mConnectionCaptor.capture(),
                eq(Context.BIND_AUTO_CREATE), any(Handler.class), eq(user));
    }
}
