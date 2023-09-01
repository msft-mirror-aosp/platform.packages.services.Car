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

import static com.android.car.internal.util.VersionUtils.assertPlatformVersionAtLeastU;

import android.annotation.MainThread;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.RequiresApi;
import android.annotation.RequiresPermission;
import android.annotation.SystemApi;
import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.PendingIntent;
import android.car.Car;
import android.car.annotation.ApiRequirements;
import android.car.builtin.util.Slogf;
import android.car.builtin.view.ViewHelper;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Build;
import android.graphics.Region;
import android.os.UserManager;
import android.view.Display;
import android.view.SurfaceControl;

import java.util.concurrent.Executor;

/**
 * A {@link ControlledRemoteCarTaskView} should be used when the launch intent of the task is known
 * before hand.
 *
 * The underlying task will be restarted if it is crashed depending on the
 * {@code autoRestartOnCrash}.
 *
 * <p>It should be preferred when:
 * <ul>
 *     <li>The underlying task is meant to be started by the host and be there forever.</li>
 * </ul>
 *
 * @hide
 */
@SystemApi
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
public final class ControlledRemoteCarTaskView extends RemoteCarTaskView {
    private static final String TAG = ControlledRemoteCarTaskView.class.getSimpleName();

    private final Executor mCallbackExecutor;
    private final ControlledRemoteCarTaskViewCallback mCallback;
    private final UserManager mUserManager;
    private final CarTaskViewController mCarTaskViewController;
    private final Context mContext;
    private final ControlledRemoteCarTaskViewConfig mConfig;
    private final Rect mTmpRect = new Rect();

    private ActivityManager.RunningTaskInfo mTaskInfo;

    final ICarTaskViewClient mICarTaskViewClient = new ICarTaskViewClient.Stub() {
        @Override
        public void onTaskAppeared(ActivityManager.RunningTaskInfo taskInfo, SurfaceControl leash) {
            mTaskInfo = taskInfo;
            updateWindowBounds();
            if (taskInfo.taskDescription != null) {
                ViewHelper.seResizeBackgroundColor(
                        ControlledRemoteCarTaskView.this,
                        taskInfo.taskDescription.getBackgroundColor());
            }
            ControlledRemoteCarTaskView.this.onTaskAppeared(taskInfo, leash);
        }

        @Override
        public void onTaskInfoChanged(ActivityManager.RunningTaskInfo taskInfo) {
            if (taskInfo.taskDescription != null) {
                ViewHelper.seResizeBackgroundColor(
                        ControlledRemoteCarTaskView.this,
                        taskInfo.taskDescription.getBackgroundColor());
            }
            ControlledRemoteCarTaskView.this.onTaskInfoChanged(taskInfo);
        }

        @Override
        public void onTaskVanished(ActivityManager.RunningTaskInfo taskInfo) {
            mTaskInfo = null;
            ControlledRemoteCarTaskView.this.onTaskVanished(taskInfo);
        }

        @Override
        public void setResizeBackgroundColor(SurfaceControl.Transaction t, int color) {
            ViewHelper.seResizeBackgroundColor(ControlledRemoteCarTaskView.this, color);
        }

        @Override
        public Rect getCurrentBoundsOnScreen() {
            ViewHelper.getBoundsOnScreen(ControlledRemoteCarTaskView.this, mTmpRect);
            return mTmpRect;
        }
    };

    ControlledRemoteCarTaskView(
            @NonNull Context context,
            ControlledRemoteCarTaskViewConfig config,
            @NonNull Executor callbackExecutor,
            @NonNull ControlledRemoteCarTaskViewCallback callback,
            CarTaskViewController carTaskViewController,
            @NonNull UserManager userManager) {
        super(context);
        mContext = context;
        mConfig = config;
        mCallbackExecutor = callbackExecutor;
        mCallback = callback;
        mCarTaskViewController = carTaskViewController;
        mUserManager = userManager;

        mCallbackExecutor.execute(() -> mCallback.onTaskViewCreated(this));
    }

    /**
     * Starts the underlying activity, specified as part of
     * {@link CarTaskViewController#createControlledRemoteCarTaskView(Executor, Intent, boolean,
     * ControlledRemoteCarTaskViewCallback)}.
     */
    @RequiresPermission(Car.PERMISSION_REGISTER_CAR_SYSTEM_UI_PROXY)
    @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.UPSIDE_DOWN_CAKE_0,
            minPlatformVersion = ApiRequirements.PlatformVersion.UPSIDE_DOWN_CAKE_0)
    @MainThread
    public void startActivity() {
        assertPlatformVersionAtLeastU();
        if (!mUserManager.isUserUnlocked()) {
            if (CarTaskViewController.DBG) {
                Slogf.d(TAG, "Can't start activity due to user is isn't unlocked");
            }
            return;
        }

        // Don't start activity when the display is off. This can happen when the taskview is not
        // attached to a window.
        if (getDisplay() == null) {
            Slogf.w(TAG, "Can't start activity because display is not available in "
                    + "taskview yet.");
            return;
        }
        // Don't start activity when the display is off for ActivityVisibilityTests.
        if (getDisplay().getState() != Display.STATE_ON) {
            Slogf.w(TAG, "Can't start activity due to the display is off");
            return;
        }

        ActivityOptions options = ActivityOptions.makeCustomAnimation(mContext,
                /* enterResId= */ 0, /* exitResId= */ 0);
        Rect launchBounds = new Rect();
        ViewHelper.getBoundsOnScreen(this, launchBounds);
        launchBounds.set(launchBounds);
        if (CarTaskViewController.DBG) {
            Slogf.d(TAG, "Starting (" + mConfig.mActivityIntent.getComponent() + ") on "
                    + launchBounds);
        }
        Intent fillInIntent = null;
        if ((mConfig.mActivityIntent.getFlags() & Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS) != 0) {
            fillInIntent = new Intent().addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        }
        startActivity(
                PendingIntent.getActivity(mContext, /* requestCode= */ 0,
                        mConfig.mActivityIntent,
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT),
                fillInIntent, options, launchBounds);
    }

    @Override
    void onInitialized() {
        mContext.getMainExecutor().execute(() -> {
            startActivity();
        });
        mCallbackExecutor.execute(() -> mCallback.onTaskViewInitialized());
    }

    @Override
    void onReleased() {
        mTaskInfo = null;
        mCallbackExecutor.execute(() -> mCallback.onTaskViewReleased());
        mCarTaskViewController.onRemoteCarTaskViewReleased(this);
    }

    @Override
    void onTaskAppeared(ActivityManager.RunningTaskInfo taskInfo, SurfaceControl leash) {
        super.onTaskAppeared(taskInfo, leash);
        mCallbackExecutor.execute(() -> mCallback.onTaskAppeared(taskInfo));
    }

    @Override
    void onTaskInfoChanged(ActivityManager.RunningTaskInfo taskInfo) {
        super.onTaskInfoChanged(taskInfo);
        mCallbackExecutor.execute(() -> mCallback.onTaskInfoChanged(taskInfo));
    }

    @RequiresPermission(Car.PERMISSION_REGISTER_CAR_SYSTEM_UI_PROXY)
    @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.UPSIDE_DOWN_CAKE_0,
            minPlatformVersion = ApiRequirements.PlatformVersion.UPSIDE_DOWN_CAKE_0)
    @Override
    @MainThread
    public void showEmbeddedTask() {
        assertPlatformVersionAtLeastU();
        super.showEmbeddedTask();
        if (getTaskInfo() == null) {
            if (CarTaskViewController.DBG) {
                Slogf.d(TAG, "Embedded task not available, starting it now.");
            }
            startActivity();
            return;
        }
    }

    @Override
    void onTaskVanished(ActivityManager.RunningTaskInfo taskInfo) {
        super.onTaskVanished(taskInfo);
        if (mConfig.mShouldAutoRestartOnTaskRemoval && mCarTaskViewController.isHostVisible()) {
            // onTaskVanished can be called when the host is in the background. In this case
            // embedded activity should not be started.
            Slogf.i(TAG, "Restarting task " + taskInfo.baseActivity
                    + " in ControlledCarTaskView");
            startActivity();
        }
        mCallbackExecutor.execute(() -> mCallback.onTaskVanished(taskInfo));
    }

    /**
     * @return the {@link android.app.ActivityManager.RunningTaskInfo} of the task currently
     * running in the TaskView.
     */
    @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.UPSIDE_DOWN_CAKE_0,
            minPlatformVersion = ApiRequirements.PlatformVersion.UPSIDE_DOWN_CAKE_0)
    @MainThread
    @Override
    @Nullable
    public ActivityManager.RunningTaskInfo getTaskInfo() {
        return mTaskInfo;
    }

    ControlledRemoteCarTaskViewConfig getConfig() {
        return mConfig;
    }

    @Override
    public String toString() {
        return toString(/* withBounds= */ false);
    }

    String toString(boolean withBounds) {
        if (withBounds) {
            ViewHelper.getBoundsOnScreen(this, mTmpRect);
        }
        return TAG + " {\n"
                + "  config=" + mConfig + "\n"
                + "  taskId=" + (getTaskInfo() == null ? "null" : getTaskInfo().taskId) + "\n"
                + (withBounds ? ("  boundsOnScreen=" + mTmpRect) : "")
                + "}\n";
    }

    // Since SurfaceView is public, these methods need to be overridden. Details in b/296680464.
    @Override
    @RequiresPermission(Car.PERMISSION_REGISTER_CAR_SYSTEM_UI_PROXY)
    @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.UPSIDE_DOWN_CAKE_0,
            minPlatformVersion = ApiRequirements.PlatformVersion.UPSIDE_DOWN_CAKE_0)
    @MainThread
    public void addInsets(int index, int type, @NonNull Rect frame) {
        super.addInsets(index, type, frame);
    }

    @Override
    @RequiresPermission(Car.PERMISSION_REGISTER_CAR_SYSTEM_UI_PROXY)
    @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.UPSIDE_DOWN_CAKE_0,
            minPlatformVersion = ApiRequirements.PlatformVersion.UPSIDE_DOWN_CAKE_0)
    public void removeInsets(int index, int type) {
        super.removeInsets(index, type);
    }

    @Override
    @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.UPSIDE_DOWN_CAKE_0,
            minPlatformVersion = ApiRequirements.PlatformVersion.UPSIDE_DOWN_CAKE_0)
    @MainThread
    public void release() {
        super.release();
    }

    @Override
    @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.UPSIDE_DOWN_CAKE_0,
            minPlatformVersion = ApiRequirements.PlatformVersion.UPSIDE_DOWN_CAKE_0)
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
    }


    @Override
    @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.UPSIDE_DOWN_CAKE_0,
            minPlatformVersion = ApiRequirements.PlatformVersion.UPSIDE_DOWN_CAKE_0)
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
    }

    @Override
    @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.UPSIDE_DOWN_CAKE_0,
            minPlatformVersion = ApiRequirements.PlatformVersion.UPSIDE_DOWN_CAKE_0)
    @MainThread
    public boolean isInitialized() {
        return super.isInitialized();
    }

    @Override
    @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.UPSIDE_DOWN_CAKE_0,
            minPlatformVersion = ApiRequirements.PlatformVersion.UPSIDE_DOWN_CAKE_0)
    @MainThread
    public void setObscuredTouchRegion(@NonNull Region obscuredRegion) {
        super.setObscuredTouchRegion(obscuredRegion);
    }

    @Override
    @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.UPSIDE_DOWN_CAKE_0,
            minPlatformVersion = ApiRequirements.PlatformVersion.UPSIDE_DOWN_CAKE_0)
    @MainThread
    public void setObscuredTouchRect(@NonNull Rect obscuredRect) {
        super.setObscuredTouchRect(obscuredRect);
    }

    @Override
    @RequiresPermission(Car.PERMISSION_REGISTER_CAR_SYSTEM_UI_PROXY)
    @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.UPSIDE_DOWN_CAKE_0,
            minPlatformVersion = ApiRequirements.PlatformVersion.UPSIDE_DOWN_CAKE_0)
    @MainThread
    public void updateWindowBounds() {
        super.updateWindowBounds();
    }
}
