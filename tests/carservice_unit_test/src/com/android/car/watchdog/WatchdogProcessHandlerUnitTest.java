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

package com.android.car.watchdog;

import static android.car.watchdog.CarWatchdogManager.TIMEOUT_CRITICAL;

import static com.android.car.internal.common.CommonConstants.INVALID_PID;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.automotive.watchdog.internal.ICarWatchdogServiceForSystem;
import android.automotive.watchdog.internal.ProcessIdentifier;
import android.car.test.mocks.AbstractExtendedMockitoTestCase;
import android.car.watchdog.ICarWatchdogServiceCallback;
import android.car.watchdoglib.CarWatchdogDaemonHelper;
import android.os.RemoteException;

import com.android.car.CarLocalServices;
import com.android.car.CarServiceHelperWrapper;
import com.android.car.internal.ICarServiceHelper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.List;

/**
 * <p>This class contains unit tests for the {@link WatchdogProcessHandler}.
 */
@RunWith(MockitoJUnitRunner.class)
public class WatchdogProcessHandlerUnitTest extends AbstractExtendedMockitoTestCase {
    private static final int MAX_WAIT_TIME_MS = 3000;
    private static final int INVALID_SESSION_ID = -1;
    @Mock
    private CarWatchdogDaemonHelper mMockCarWatchdogDaemonHelper;
    @Mock
    private ICarServiceHelper.Stub mMockCarServiceHelper;
    @Captor
    private ArgumentCaptor<List<ProcessIdentifier>> mProcessIdentifiersCaptor;
    private WatchdogProcessHandler mWatchdogProcessHandler;
    private ICarWatchdogServiceForSystem mWatchdogServiceForSystemImpl;

    public WatchdogProcessHandlerUnitTest() {
        super(CarWatchdogService.TAG);
    }

    @Override
    protected void onSessionBuilder(CustomMockitoSessionBuilder builder) {
        builder.spyStatic(CarLocalServices.class);
    }

    @Before
    public void setUp() throws Exception {
        mWatchdogProcessHandler = new WatchdogProcessHandler(mWatchdogServiceForSystemImpl,
                mMockCarWatchdogDaemonHelper);
        mWatchdogProcessHandler.init();
        CarServiceHelperWrapper wrapper = CarServiceHelperWrapper.create();
        wrapper.setCarServiceHelper(mMockCarServiceHelper);
    }

    /**
     * Releases resources.
     */
    @After
    public void tearDown() throws Exception {
        CarLocalServices.removeServiceForTest(CarServiceHelperWrapper.class);
    }

    // TODO(b/288468494): Look into adding tests for prepareHealthCheck and updateUserState.

    @Test
    public void testPostHealthCheckMessage() throws Exception {
        mWatchdogProcessHandler.postHealthCheckMessage(123456);

        verify(mMockCarWatchdogDaemonHelper,
                timeout(MAX_WAIT_TIME_MS)).tellCarWatchdogServiceAlive(
                eq(mWatchdogServiceForSystemImpl), mProcessIdentifiersCaptor.capture(), eq(123456));
        assertWithMessage("clients not responding").that(
                mProcessIdentifiersCaptor.getValue().size()).isEqualTo(0);
    }

    @Test
    public void testRegisterClient() throws Exception {
        TestClient client = new TestClient();

        mWatchdogProcessHandler.registerClient(client, TIMEOUT_CRITICAL);

        assertWithMessage("Critical timeout client count").that(
                mWatchdogProcessHandler.getClientCount(TIMEOUT_CRITICAL)).isEqualTo(1);

        mWatchdogProcessHandler.postHealthCheckMessage(123456);

        // Checking client health is asynchronous, so wait at most 1 second.
        verify(mMockCarWatchdogDaemonHelper,
                timeout(1000)).tellCarWatchdogServiceAlive(
                eq(mWatchdogServiceForSystemImpl), any(), eq(123456));

        assertThat(client.getLastSessionId()).isNotEqualTo(INVALID_SESSION_ID);
    }

    @Test
    public void testUnregisterClient() throws Exception {
        TestClient client = new TestClient();

        mWatchdogProcessHandler.registerClient(client, TIMEOUT_CRITICAL);

        assertWithMessage("Critical timeout client count").that(
                mWatchdogProcessHandler.getClientCount(TIMEOUT_CRITICAL)).isEqualTo(1);

        mWatchdogProcessHandler.unregisterClient(client);

        assertWithMessage("Critical timeout client count").that(
                mWatchdogProcessHandler.getClientCount(TIMEOUT_CRITICAL)).isEqualTo(0);

        mWatchdogProcessHandler.postHealthCheckMessage(123456);

        assertThat(client.getLastSessionId()).isEqualTo(INVALID_SESSION_ID);
    }

    @Test
    public void testGoodClientHealthCheck() throws Exception {
        testClientHealthCheck(new TestClient(), 0);
    }

    @Test
    public void testBadClientHealthCheck() throws Exception {
        testClientHealthCheck(new BadTestClient(), 1);
    }

    @Test
    public void testAsyncFetchAidlVhalPid() throws Exception {
        int vhalPid = 15687;
        when(mMockCarServiceHelper.fetchAidlVhalPid()).thenReturn(vhalPid);

        mWatchdogProcessHandler.asyncFetchAidlVhalPid();

        verify(mMockCarWatchdogDaemonHelper, timeout(MAX_WAIT_TIME_MS)).onAidlVhalPidFetched(
                vhalPid);
    }

    @Test
    public void testAsyncFetchAidlWithInvalidPid() throws Exception {
        when(mMockCarServiceHelper.fetchAidlVhalPid()).thenReturn(INVALID_PID);

        mWatchdogProcessHandler.asyncFetchAidlVhalPid();

        verify(mMockCarServiceHelper, timeout(MAX_WAIT_TIME_MS)).fetchAidlVhalPid();
        // Shouldn't respond to car watchdog daemon when invalid pid is returned by
        // the system_server.
        verify(mMockCarWatchdogDaemonHelper, never()).onAidlVhalPidFetched(INVALID_PID);
    }

    @Test
    public void testAsyncFetchAidlVhalPidWithDaemonRemoteException() throws Exception {
        int vhalPid = 15687;
        when(mMockCarServiceHelper.fetchAidlVhalPid()).thenReturn(vhalPid);
        doThrow(RemoteException.class).when(mMockCarWatchdogDaemonHelper)
                .onAidlVhalPidFetched(anyInt());

        mWatchdogProcessHandler.asyncFetchAidlVhalPid();

        verify(mMockCarServiceHelper, timeout(MAX_WAIT_TIME_MS)).fetchAidlVhalPid();
        verify(mMockCarWatchdogDaemonHelper, timeout(MAX_WAIT_TIME_MS)).onAidlVhalPidFetched(
                vhalPid);
    }

    @Test
    public void testControlProcessHealthCheckEnabled() throws Exception {
        mWatchdogProcessHandler.controlProcessHealthCheck(true);

        verify(mMockCarWatchdogDaemonHelper).controlProcessHealthCheck(eq(true));
    }

    @Test
    public void testControlProcessHealthCheckDisabled() throws Exception {
        mWatchdogProcessHandler.controlProcessHealthCheck(false);

        verify(mMockCarWatchdogDaemonHelper).controlProcessHealthCheck(eq(false));
    }

    @Test
    public void testControlProcessHealthCheckWithDisconnectedDaemon() throws Exception {
        doThrow(IllegalStateException.class).when(
                mMockCarWatchdogDaemonHelper).controlProcessHealthCheck(true);

        assertThrows(IllegalStateException.class,
                () -> mWatchdogProcessHandler.controlProcessHealthCheck(true));
    }

    private void testClientHealthCheck(TestClient client, int badClientCount) throws Exception {
        mWatchdogProcessHandler.registerClient(client, TIMEOUT_CRITICAL);

        mWatchdogProcessHandler.postHealthCheckMessage(123456);

        verify(mMockCarWatchdogDaemonHelper,
                timeout(MAX_WAIT_TIME_MS)).tellCarWatchdogServiceAlive(
                eq(mWatchdogServiceForSystemImpl), mProcessIdentifiersCaptor.capture(), eq(123456));

        assertThat(mProcessIdentifiersCaptor.getValue()).isEmpty();

        mWatchdogProcessHandler.postHealthCheckMessage(987654);

        verify(mMockCarWatchdogDaemonHelper,
                timeout(MAX_WAIT_TIME_MS)).tellCarWatchdogServiceAlive(
                eq(mWatchdogServiceForSystemImpl), mProcessIdentifiersCaptor.capture(), eq(987654));

        assertThat(mProcessIdentifiersCaptor.getValue().size()).isEqualTo(badClientCount);
    }

    private class TestClient extends ICarWatchdogServiceCallback.Stub {
        protected int mLastSessionId = INVALID_SESSION_ID;

        @Override
        public void onCheckHealthStatus(int sessionId, int timeout) {
            mLastSessionId = sessionId;
            mWatchdogProcessHandler.tellClientAlive(this, sessionId);
        }

        @Override
        public void onPrepareProcessTermination() {
        }

        public int getLastSessionId() {
            return mLastSessionId;
        }
    }

    private final class BadTestClient extends TestClient {
        @Override
        public void onCheckHealthStatus(int sessionId, int timeout) {
            mLastSessionId = sessionId;
            // This client doesn't respond to CarWatchdogService.
        }
    }
}
