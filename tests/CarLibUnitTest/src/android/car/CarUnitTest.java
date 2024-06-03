/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.car;

import static android.car.Car.CAR_SERVICE_BINDER_SERVICE_NAME;
import static android.car.feature.Flags.FLAG_DISPLAY_COMPATIBILITY;
import static android.car.feature.Flags.FLAG_PERSIST_AP_SETTINGS;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.car.Car.CarBuilder;
import android.car.Car.CarBuilder.ServiceManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;
import android.platform.test.ravenwood.RavenwoodRule;
import android.util.Pair;

import com.android.car.internal.ICarServiceHelper;
import com.android.internal.annotations.GuardedBy;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * Unit test for Car API.
 */
@RunWith(MockitoJUnitRunner.Silent.class)
@EnableFlags({FLAG_PERSIST_AP_SETTINGS, FLAG_DISPLAY_COMPATIBILITY})
public final class CarUnitTest {

    private static final String TAG = CarUnitTest.class.getSimpleName();
    private static final String PKG_NAME = "Bond.James.Bond";
    private static final int DEFAULT_TIMEOUT_MS = 1000;

    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();
    @Rule
    public final RavenwoodRule mRavenwood = new RavenwoodRule.Builder().setProvideMainThread(true)
            .build();

    @Mock
    private Context mContext;
    @Mock
    private ServiceConnection mServiceConnectionListener;
    @Mock
    private ComponentName mCarServiceComponentName;
    @Mock
    private PackageManager mPackageManager;
    @Mock
    private ServiceManager mServiceManager;

    private HandlerThread mEventHandlerThread;
    private Handler mEventHandler;
    private Handler mMainHandler;
    private CarBuilder mCarBuilder;

    private final Object mLock = new Object();
    @GuardedBy("mLock")
    private final List<ServiceConnection> mBindServiceConnections = new ArrayList<>();
    @GuardedBy("mLock")
    private boolean mCarServiceRegistered;

    // It is tricky to mock this. So create placeholder version instead.
    private static final class FakeService extends ICar.Stub {

        @Override
        public void setSystemServerConnections(ICarServiceHelper helper,
                ICarResultReceiver receiver) throws RemoteException {
        }

        @Override
        public boolean isFeatureEnabled(String featureName) {
            return false;
        }

        @Override
        public int enableFeature(String featureName) {
            return Car.FEATURE_REQUEST_SUCCESS;
        }

        @Override
        public int disableFeature(String featureName) {
            return Car.FEATURE_REQUEST_SUCCESS;
        }

        @Override
        public List<String> getAllEnabledFeatures() {
            return Collections.EMPTY_LIST;
        }

        @Override
        public List<String> getAllPendingDisabledFeatures() {
            return Collections.EMPTY_LIST;
        }

        @Override
        public List<String> getAllPendingEnabledFeatures() {
            return Collections.EMPTY_LIST;
        }

        @Override
        public String getCarManagerClassForFeature(String featureName) {
            return null;
        }

        @Override
        public IBinder getCarService(java.lang.String serviceName) {
            return null;
        }

        @Override
        public int getCarConnectionType() {
            return 0;
        }
    };

    private final FakeService mService = new FakeService();


    private static final class LifecycleListener implements Car.CarServiceLifecycleListener {
        private final Object mLock = new Object();
        @GuardedBy("mLock")
        private final ArrayList<Pair<Car, Boolean>> mEvents = new ArrayList<>();

        @Override
        public void onLifecycleChanged(Car car, boolean ready) {
            synchronized (mLock) {
                assertThat(Looper.getMainLooper()).isEqualTo(Looper.myLooper());
                mEvents.add(new Pair<>(car, ready));
                mLock.notifyAll();
            }
        }

        void waitForEvent(int count, int timeoutInMs) throws InterruptedException {
            synchronized (mLock) {
                while (mEvents.size() < count) {
                    mLock.wait(timeoutInMs);
                }
            }
        }

        void assertOneListenerCallAndClear(Car expectedCar, boolean ready) {
            synchronized (mLock) {
                assertThat(mEvents).containsExactly(new Pair<>(expectedCar, ready));
                mEvents.clear();
            }
        }
    }

    private final LifecycleListener mLifecycleListener = new LifecycleListener();

    @Before
    public void setUp() {
        mEventHandlerThread = new HandlerThread("CarTestEvent");
        mEventHandlerThread.start();
        mEventHandler = new Handler(mEventHandlerThread.getLooper());
        mMainHandler = new Handler(Looper.getMainLooper());
        // Inject mServiceManager as a dependency for creating Car.
        mCarBuilder = new CarBuilder().setServiceManager(mServiceManager);

        when(mContext.getPackageName()).thenReturn(PKG_NAME);
        when(mContext.getPackageManager()).thenReturn(mPackageManager);
        when(mPackageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)).thenReturn(true);
        setupFakeServiceManager();
    }

    @After
    public void tearDown() {
        mEventHandlerThread.quitSafely();
    }

    private void setupFakeServiceManager() {
        when(mContext.bindService(any(), any(), anyInt())).thenAnswer((inv) -> {
            ServiceConnection serviceConnection = inv.getArgument(1);

            synchronized (mLock) {
                if (mCarServiceRegistered) {
                    mMainHandler.post(() -> serviceConnection.onServiceConnected(
                            mCarServiceComponentName,  mService));
                }
                mBindServiceConnections.add(serviceConnection);
            }

            return true;
        });

        when(mServiceManager.getService(CAR_SERVICE_BINDER_SERVICE_NAME))
                .thenAnswer((inv) -> {
                    synchronized (mLock) {
                        if (mCarServiceRegistered) {
                            return mService;
                        }
                        return null;
                    }
                });
    }

    private void setCarServiceRegistered() {
        synchronized (mLock) {
            mCarServiceRegistered = true;
            for (int i = 0; i < mBindServiceConnections.size(); i++) {
                var serviceConnection = mBindServiceConnections.get(i);
                mMainHandler.post(() -> serviceConnection.onServiceConnected(
                        mCarServiceComponentName, mService));
            }
        }
    }

    private void setCarServiceDisconnected() {
        synchronized (mLock) {
            mCarServiceRegistered = false;
            for (int i = 0; i < mBindServiceConnections.size(); i++) {
                var serviceConnection = mBindServiceConnections.get(i);
                mMainHandler.post(() -> serviceConnection.onServiceDisconnected(
                        mCarServiceComponentName));
            }
        }
    }

    @Test
    public void testCreateCar_Context_ServiceConnection_Handler() {
        Car car = Car.createCar(mContext, mServiceConnectionListener, mEventHandler);

        assertThat(car).isNotNull();

        car.connect();

        assertThat(car.isConnecting()).isTrue();

        setCarServiceRegistered();

        verify(mServiceConnectionListener, timeout(DEFAULT_TIMEOUT_MS)).onServiceConnected(
                mCarServiceComponentName, mService);
        assertThat(car.isConnected()).isTrue();

        car.disconnect();
        assertThat(car.isConnected()).isFalse();
    }

    @Test
    public void testCreateCar_Context_ServiceConnection_Handler_CarServiceRegistered() {
        setCarServiceRegistered();

        Car car = Car.createCar(mContext, mServiceConnectionListener, mEventHandler);

        assertThat(car).isNotNull();

        car.connect();

        verify(mServiceConnectionListener, timeout(DEFAULT_TIMEOUT_MS)).onServiceConnected(
                mCarServiceComponentName, mService);
        assertThat(car.isConnected()).isTrue();

        car.disconnect();
        assertThat(car.isConnected()).isFalse();
    }

    @Test
    public void testCreateCar_Context_CarServiceRegistered() {
        setCarServiceRegistered();

        Car car = mCarBuilder.createCar(mContext);

        assertThat(car).isNotNull();
        assertThat(car.isConnected()).isTrue();

        car.disconnect();
        assertThat(car.isConnected()).isFalse();
    }

    @Test
    public void testCreateCar_Context_CarServiceNeverRegistered_Timeout() {
        // This should timeout.
        Car car = mCarBuilder.createCar(mContext);

        assertThat(car).isNull();
    }

    @Test
    public void testCreateCar_Context_CarServiceRegisteredLater_BeforeTimeout() {
        // Car service is registered after 200ms.
        mEventHandler.postDelayed(() -> setCarServiceRegistered(), 200);

        // This should block until car service is registered.
        Car car = mCarBuilder.createCar(mContext);

        assertThat(car).isNotNull();
        assertThat(car.isConnected()).isTrue();
        verify(mContext).bindService(any(), any(), anyInt());

        car.disconnect();
        assertThat(car.isConnected()).isFalse();
    }

    @Test
    public void testCreateCar_Context_WaitForever_Lclistener_CarServiceRegisteredLater()
            throws Exception {
        // Car service is registered after 200ms.
        mEventHandler.postDelayed(() -> setCarServiceRegistered(), 200);

        Car car = mCarBuilder.createCar(mContext, null,
                Car.CAR_WAIT_TIMEOUT_WAIT_FOREVER, mLifecycleListener);

        assertThat(car).isNotNull();
        assertThat(car.isConnected()).isTrue();
        verify(mContext).bindService(any(), any(), anyInt());
        mLifecycleListener.assertOneListenerCallAndClear(car, true);

        // Just call these to guarantee that nothing crashes with these call.
        ServiceConnection serviceConnection;
        synchronized (mLock) {
            serviceConnection = mBindServiceConnections.get(0);
        }
        var cdLatch = new CountDownLatch(1);
        mMainHandler.post(() -> {
            serviceConnection.onServiceConnected(new ComponentName("", ""), mService);
            serviceConnection.onServiceDisconnected(new ComponentName("", ""));
            cdLatch.countDown();
        });
        cdLatch.await(DEFAULT_TIMEOUT_MS, MILLISECONDS);
    }

    @Test
    public void testCreateCar_Context_WaitForever_Lclistener_ConnectCrashRestart()
            throws Exception {
        // Car service is registered after 100ms.
        mEventHandler.postDelayed(() -> setCarServiceRegistered(), 100);

        Car car = mCarBuilder.createCar(mContext, null,
                Car.CAR_WAIT_TIMEOUT_WAIT_FOREVER, mLifecycleListener);

        assertThat(car).isNotNull();
        assertThat(car.isConnected()).isTrue();
        mLifecycleListener.assertOneListenerCallAndClear(car, true);

        // Fake crash.
        mEventHandler.post(() -> setCarServiceDisconnected());
        mLifecycleListener.waitForEvent(1, DEFAULT_TIMEOUT_MS);

        mLifecycleListener.assertOneListenerCallAndClear(car, false);
        assertThat(car.isConnected()).isFalse();

        // fake restart
        mEventHandler.post(() -> setCarServiceRegistered());
        mLifecycleListener.waitForEvent(1, DEFAULT_TIMEOUT_MS);

        mLifecycleListener.assertOneListenerCallAndClear(car, true);
        assertThat(car.isConnected()).isTrue();
    }

    @Test
    public void testCreateCar_Context_WaitForever_Lclistener_CarServiceAlreadyRegistered()
            throws Exception {
        setCarServiceRegistered();

        var cdLatch = new CountDownLatch(1);
        mMainHandler.post(() -> {
            Car car = mCarBuilder.createCar(mContext, null,
                    Car.CAR_WAIT_TIMEOUT_WAIT_FOREVER, mLifecycleListener);

            assertThat(car).isNotNull();
            assertThat(car.isConnected()).isTrue();
            verify(mContext, times(1)).bindService(any(), any(), anyInt());

            // mLifecycleListener should have been called as this is main thread.
            mLifecycleListener.assertOneListenerCallAndClear(car, true);
            cdLatch.countDown();
        });
        cdLatch.await(DEFAULT_TIMEOUT_MS, MILLISECONDS);
    }

    @Test
    public void testCreateCar_Context_CarServiceRegisteredLater_InvokeFromMain()
            throws Exception {
        // Car service is registered after 200ms.
        mEventHandler.postDelayed(() -> setCarServiceRegistered(), 200);

        var cdLatch = new CountDownLatch(1);
        mMainHandler.post(() -> {
            Car car = mCarBuilder.createCar(mContext, null,
                    Car.CAR_WAIT_TIMEOUT_WAIT_FOREVER, mLifecycleListener);

            assertThat(car).isNotNull();
            assertThat(car.isConnected()).isTrue();
            verify(mContext, times(1)).bindService(any(), any(), anyInt());

            // mLifecycleListener should have been called as this is main thread.
            mLifecycleListener.assertOneListenerCallAndClear(car, true);
            cdLatch.countDown();
        });
        cdLatch.await(DEFAULT_TIMEOUT_MS, MILLISECONDS);
    }
}
