/*
 * Copyright (C) 2015 The Android Open Source Project
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


import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DUMP_INFO;

import android.car.builtin.os.ServiceManagerHelper;
import android.car.builtin.os.SystemPropertiesHelper;
import android.car.builtin.os.TraceHelper;
import android.car.builtin.util.Slogf;
import android.content.Intent;
import android.os.IBinder;
import android.os.Process;
import android.util.EventLog;

import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.common.EventLogTags;
import com.android.car.systeminterface.SystemInterface;
import com.android.car.util.LimitedTimingsTraceLog;

import java.io.FileDescriptor;
import java.io.PrintWriter;

/** Implementation of CarService */
public class CarServiceImpl extends ProxiedService {
    public static final String CAR_SERVICE_INIT_TIMING_TAG = "CAR.InitTiming";
    public static final int CAR_SERVICE_INIT_TIMING_MIN_DURATION_MS = 5;

    private ICarImpl mICarImpl;
    private VehicleStub mVehicle;

    private String mVehicleInterfaceName;

    private final VehicleDeathRecipient mVehicleDeathRecipient = new VehicleDeathRecipient();

    @Override
    public void onCreate() {
        LimitedTimingsTraceLog initTiming = new LimitedTimingsTraceLog(CAR_SERVICE_INIT_TIMING_TAG,
                TraceHelper.TRACE_TAG_CAR_SERVICE, CAR_SERVICE_INIT_TIMING_MIN_DURATION_MS);
        initTiming.traceBegin("CarService.onCreate");

        initTiming.traceBegin("getVehicle");
        mVehicle = new VehicleStub();
        initTiming.traceEnd(); // "getVehicle"

        EventLog.writeEvent(EventLogTags.CAR_SERVICE_CREATE, mVehicle.isValid() ? 1 : 0);

        if (!mVehicle.isValid()) {
            throw new IllegalStateException("Vehicle HAL service is not available.");
        }

        mVehicleInterfaceName = mVehicle.getInterfaceDescriptor();

        Slogf.i(CarLog.TAG_SERVICE, "Connected to " + mVehicleInterfaceName);
        EventLog.writeEvent(EventLogTags.CAR_SERVICE_CONNECTED, mVehicleInterfaceName);

        mICarImpl = new ICarImpl(this,
                getBuiltinPackageContext(),
                mVehicle,
                SystemInterface.Builder.defaultSystemInterface(this).build(),
                mVehicleInterfaceName);
        mICarImpl.init();

        mVehicle.linkToDeath(mVehicleDeathRecipient);

        ServiceManagerHelper.addService("car_service", mICarImpl);
        SystemPropertiesHelper.set("boot.car_service_created", "1");

        super.onCreate();

        initTiming.traceEnd(); // "CarService.onCreate"
    }

    // onDestroy is best-effort and might not get called on shutdown/reboot. As such it is not
    // suitable for permanently saving state or other need-to-happen operation. If you have a
    // cleanup task that you want to make sure happens on shutdown/reboot, see OnShutdownReboot.
    @Override
    public void onDestroy() {
        EventLog.writeEvent(EventLogTags.CAR_SERVICE_DESTROY, mVehicle.isValid() ? 1 : 0);
        Slogf.i(CarLog.TAG_SERVICE, "Service onDestroy");
        mICarImpl.release();

        mVehicle.unlinkToDeath(mVehicleDeathRecipient);
        mVehicle = null;

        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // keep it alive.
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mICarImpl;
    }

    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    protected void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        // historically, the way to get a dumpsys from CarService has been to use
        // "dumpsys activity service com.android.car/.CarService" - leaving this
        // as a forward to car_service makes the previously well-known command still work
        mICarImpl.dump(fd, writer, args);
    }

    private static class VehicleDeathRecipient implements IVehicleDeathRecipient {

        @Override
        public void serviceDied(long cookie) {
            EventLog.writeEvent(EventLogTags.CAR_SERVICE_VHAL_DIED, cookie);
            Slogf.wtf(CarLog.TAG_SERVICE, "***Vehicle HAL died. Car service will restart***");
            Process.killProcess(Process.myPid());
        }

        @Override
        public void binderDied() {
            EventLog.writeEvent(EventLogTags.CAR_SERVICE_VHAL_DIED, /*cookie=*/0);
            Slogf.wtf(CarLog.TAG_SERVICE, "***Vehicle HAL died. Car service will restart***");
            Process.killProcess(Process.myPid());
        }
    }
}
