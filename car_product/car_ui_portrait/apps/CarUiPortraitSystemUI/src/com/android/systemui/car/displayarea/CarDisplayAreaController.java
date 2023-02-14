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

package com.android.systemui.car.displayarea;

import static com.android.car.caruiportrait.common.service.CarUiPortraitService.INTENT_EXTRA_HIDE_SYSTEM_BAR_FOR_IMMERSIVE_MODE;
import static com.android.car.caruiportrait.common.service.CarUiPortraitService.INTENT_EXTRA_IS_IMMERSIVE_MODE_REQUESTED;
import static com.android.car.caruiportrait.common.service.CarUiPortraitService.INTENT_EXTRA_SUW_IN_PROGRESS;
import static com.android.car.caruiportrait.common.service.CarUiPortraitService.REQUEST_FROM_LAUNCHER;
import static com.android.car.caruiportrait.common.service.CarUiPortraitService.REQUEST_FROM_SYSTEM_UI;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.os.Build;
import android.os.UserHandle;
import android.util.Log;
import android.window.DisplayAreaOrganizer;

import com.android.systemui.R;
import com.android.systemui.car.CarDeviceProvisionedController;
import com.android.systemui.car.CarDeviceProvisionedListener;
import com.android.systemui.car.CarServiceProvider;
import com.android.systemui.statusbar.CommandQueue;
import com.android.systemui.statusbar.policy.ConfigurationController;
import com.android.systemui.wm.CarUiPortraitDisplaySystemBarsController;
import com.android.wm.shell.common.ShellExecutor;

import javax.inject.Inject;

/**
 * Controls the bounds of the home background, audio bar and application displays. This is a
 * singleton class as there should be one controller used to register and control the DA's
 */
public class CarDisplayAreaController implements ConfigurationController.ConfigurationListener,
        CommandQueue.Callbacks {

    private static final String TAG = "CarDisplayAreaController";
    private static final boolean DEBUG = Build.IS_DEBUGGABLE;
    private final DisplayAreaOrganizer mOrganizer;
    private final CarFullscreenTaskListener mCarFullscreenTaskListener;

    private final ShellExecutor mShellExecutor;
    private final CarDeviceProvisionedController mCarDeviceProvisionedController;
    private final CarUiPortraitDisplaySystemBarsController mCarUiDisplaySystemBarsController;

    private final ComponentName mNotificationCenterComponent;
    private final Context mApplicationContext;
    private final CarServiceProvider mCarServiceProvider;

    private final CarUiPortraitDisplaySystemBarsController.Callback
            mCarUiPortraitDisplaySystemBarsControllerCallback =
            new CarUiPortraitDisplaySystemBarsController.Callback() {
                @Override
                public void onImmersiveRequestedChanged(ComponentName componentName,
                        boolean requested) {
                    Intent intent = new Intent(REQUEST_FROM_SYSTEM_UI);
                    intent.putExtra(INTENT_EXTRA_IS_IMMERSIVE_MODE_REQUESTED, requested);
                    mApplicationContext.sendBroadcastAsUser(intent,
                            new UserHandle(ActivityManager.getCurrentUser()));
                }

                @Override
                public void onImmersiveStateChanged(boolean immersive) {

                }
            };

    private final CarDeviceProvisionedListener mCarDeviceProvisionedListener =
            new CarDeviceProvisionedListener() {
                @Override
                public void onUserSetupInProgressChanged() {
                    boolean userSetupInProgress = mCarDeviceProvisionedController
                            .isCurrentUserSetupInProgress();
                    Intent intent = new Intent(REQUEST_FROM_SYSTEM_UI);
                    intent.putExtra(INTENT_EXTRA_SUW_IN_PROGRESS, userSetupInProgress);
                    mApplicationContext.sendBroadcastAsUser(intent,
                            new UserHandle(ActivityManager.getCurrentUser()));

                    mCarUiDisplaySystemBarsController.requestImmersiveModeForSUW(
                            mApplicationContext.getDisplayId(), userSetupInProgress);

                }
            };

    /**
     * Initializes the controller
     */
    @Inject
    public CarDisplayAreaController(Context applicationContext,
            CarFullscreenTaskListener carFullscreenTaskListener,
            ShellExecutor shellExecutor,
            CarServiceProvider carServiceProvider,
            DisplayAreaOrganizer organizer,
            CarUiPortraitDisplaySystemBarsController carUiPortraitDisplaySystemBarsController,
            CommandQueue commandQueue,
            CarDeviceProvisionedController deviceProvisionedController) {
        mApplicationContext = applicationContext;
        mOrganizer = organizer;
        mShellExecutor = shellExecutor;
        mCarServiceProvider = carServiceProvider;
        mCarFullscreenTaskListener = carFullscreenTaskListener;
        Resources resources = applicationContext.getResources();
        mNotificationCenterComponent = ComponentName.unflattenFromString(resources.getString(
                R.string.config_notificationCenterActivity));

        mCarUiDisplaySystemBarsController = carUiPortraitDisplaySystemBarsController;
        mCarDeviceProvisionedController = deviceProvisionedController;
        mCarUiDisplaySystemBarsController.registerCallback(mApplicationContext.getDisplayId(),
                mCarUiPortraitDisplaySystemBarsControllerCallback);

        commandQueue.addCallback(this);
    }

    @Override
    public void animateCollapsePanels(int flags, boolean force) {
        Intent homeActivityIntent = new Intent(Intent.ACTION_MAIN);
        homeActivityIntent.addCategory(Intent.CATEGORY_HOME);
        homeActivityIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mApplicationContext.startActivityAsUser(homeActivityIntent, UserHandle.CURRENT);
    }

    private static void logIfDebuggable(String message) {
        if (DEBUG) {
            Log.d(TAG, message);
        }
    }

    @Override
    public void animateExpandNotificationsPanel() {
        Intent intent = new Intent();
        intent.setComponent(mNotificationCenterComponent);
        mApplicationContext.startActivityAsUser(intent, UserHandle.CURRENT);
    }

    /** Registers the DA organizer. */
    public void register() {
        mCarDeviceProvisionedController.addCallback(mCarDeviceProvisionedListener);
        BroadcastReceiver immersiveModeChangeReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.hasExtra(INTENT_EXTRA_HIDE_SYSTEM_BAR_FOR_IMMERSIVE_MODE)) {
                    boolean hideSystemBar = intent.getBooleanExtra(
                            INTENT_EXTRA_HIDE_SYSTEM_BAR_FOR_IMMERSIVE_MODE, false);
                    mCarUiDisplaySystemBarsController.requestImmersiveMode(
                            mApplicationContext.getDisplayId(), hideSystemBar);
                }
            }
        };
        mApplicationContext.registerReceiverForAllUsers(immersiveModeChangeReceiver,
                new IntentFilter(REQUEST_FROM_LAUNCHER), null, null, Context.RECEIVER_EXPORTED);
    }
}
