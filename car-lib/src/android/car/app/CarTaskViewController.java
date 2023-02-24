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

package android.car.app;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.RequiresPermission;
import android.annotation.SystemApi;
import android.app.Activity;
import android.car.Car;
import android.car.annotation.ApiRequirements;
import android.car.builtin.app.ActivityManagerHelper;
import android.car.builtin.util.Slogf;
import android.os.RemoteException;
import android.os.UserManager;
import android.util.Dumpable;
import android.util.Log;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * This class is used for creating task views & is created on a per activity basis.
 * @hide
 */
@SystemApi
public final class CarTaskViewController {
    private static final String TAG = CarTaskViewController.class.getSimpleName();
    static final boolean DBG = Slogf.isLoggable(TAG, Log.DEBUG);

    private final ICarSystemUIProxy mService;
    private final Activity mHostActivity;
    private final List<ControlledRemoteCarTaskView> mControlledRemoteCarTaskViews =
            new ArrayList<>();
    private final CarTaskViewInputInterceptor mTaskViewInputInterceptor;

    /**
     * @param service the binder interface to communicate with the car system UI.
     * @param hostActivity the activity that will be hosting the taskviews.
     * @hide
     */
    CarTaskViewController(@NonNull ICarSystemUIProxy service, @NonNull Activity hostActivity) {
        mService = service;
        mHostActivity = hostActivity;

        mHostActivity.addDumpable(mDumper);
        mTaskViewInputInterceptor = new CarTaskViewInputInterceptor(hostActivity, this);
    }

    /**
     * Creates a new {@link ControlledRemoteCarTaskView}.
     *
     * @param callbackExecutor the executor to get the {@link ControlledRemoteCarTaskViewCallback}
     *                         on.
     * @param controlledRemoteCarTaskViewCallback the callback to monitor the
     *                                             {@link ControlledRemoteCarTaskView} related
     *                                             events.
     */
    @RequiresPermission(Car.PERMISSION_REGISTER_CAR_SYSTEM_UI_PROXY)
    @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.UPSIDE_DOWN_CAKE_0,
            minPlatformVersion = ApiRequirements.PlatformVersion.UPSIDE_DOWN_CAKE_0)
    public void createControlledRemoteCarTaskView(
            @NonNull ControlledRemoteCarTaskViewConfig controlledRemoteCarTaskViewConfig,
            @NonNull Executor callbackExecutor,
            @NonNull ControlledRemoteCarTaskViewCallback controlledRemoteCarTaskViewCallback) {
        ControlledRemoteCarTaskView taskViewClient =
                new ControlledRemoteCarTaskView(
                        mHostActivity,
                        controlledRemoteCarTaskViewConfig,
                        callbackExecutor,
                        controlledRemoteCarTaskViewCallback,
                        /* carTaskViewController= */ this,
                        mHostActivity.getSystemService(UserManager.class));

        try {
            ICarTaskViewHost host = mService.createCarTaskView(taskViewClient.mICarTaskViewClient);
            taskViewClient.setRemoteHost(host);
            mControlledRemoteCarTaskViews.add(taskViewClient);

            if (controlledRemoteCarTaskViewConfig.mShouldCaptureGestures
                    || controlledRemoteCarTaskViewConfig.mShouldCaptureLongPress) {
                mTaskViewInputInterceptor.init();
            }
        } catch (RemoteException e) {
            Slogf.e(TAG, "Unable to create task view.", e);
        }
    }

    /**
     * Releases all the resources held by the taskviews associated with this controller.
     */
    @RequiresPermission(Car.PERMISSION_REGISTER_CAR_SYSTEM_UI_PROXY)
    @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.UPSIDE_DOWN_CAKE_0,
            minPlatformVersion = ApiRequirements.PlatformVersion.UPSIDE_DOWN_CAKE_0)
    public void release() {
        for (RemoteCarTaskView carTaskView : mControlledRemoteCarTaskViews) {
            carTaskView.release();
        }
        mControlledRemoteCarTaskViews.clear();
        mTaskViewInputInterceptor.release();
    }

    /**
     * Brings all the embedded tasks to the front.
     */
    @RequiresPermission(Car.PERMISSION_REGISTER_CAR_SYSTEM_UI_PROXY)
    @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.UPSIDE_DOWN_CAKE_0,
            minPlatformVersion = ApiRequirements.PlatformVersion.UPSIDE_DOWN_CAKE_0)
    public void showEmbeddedTasks() {
        for (RemoteCarTaskView carTaskView : mControlledRemoteCarTaskViews) {
            // TODO(b/267314188): Add a new method in ICarSystemUI to call
            // showEmbeddedTask in a single WCT for multiple tasks.
            carTaskView.showEmbeddedTask();
        }
    }

    boolean isHostVisible() {
        return ActivityManagerHelper.isVisible(mHostActivity);
    }

    List<ControlledRemoteCarTaskView> getControlledRemoteCarTaskViews() {
        return mControlledRemoteCarTaskViews;
    }

    private final Dumpable mDumper = new Dumpable() {
        private static final String INDENTATION = "  ";

        @NonNull
        @Override
        public String getDumpableName() {
            return TAG;
        }

        @Override
        public void dump(@NonNull PrintWriter writer, @Nullable String[] args) {
            writer.println("ControlledRemoteCarTaskViews: ");
            for (ControlledRemoteCarTaskView taskView : mControlledRemoteCarTaskViews) {
                writer.println(INDENTATION + taskView.toString(/* withBounds= */ true));
            }
        }
    };
}
