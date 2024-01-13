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

package com.android.systemui.car.systembar;

import android.content.Context;

import androidx.annotation.Nullable;

import com.android.systemui.broadcast.BroadcastDispatcher;
import com.android.systemui.car.CarServiceProvider;
import com.android.systemui.car.privacy.CameraPrivacyElementsProviderImpl;
import com.android.systemui.car.privacy.MicPrivacyElementsProviderImpl;
import com.android.systemui.car.qc.SystemUIQCViewController;
import com.android.systemui.car.statusbar.UserNameViewController;
import com.android.systemui.car.statusicon.ui.DistantDisplayStatusIconController;
import com.android.systemui.settings.UserTracker;
import com.android.systemui.statusbar.policy.ConfigurationController;

import dagger.Lazy;

import javax.inject.Provider;

/** A single class which controls the navigation bar views in distant display. */
public class DistantDisplayCarSystemBarController extends CarSystemBarController {
    private final DistantDisplayStatusIconController mDistantDisplayStatusIconController;

    public DistantDisplayCarSystemBarController(Context context,
            UserTracker userTracker,
            CarSystemBarViewFactory carSystemBarViewFactory,
            CarServiceProvider carServiceProvider,
            BroadcastDispatcher broadcastDispatcher,
            ConfigurationController configurationController,
            ButtonSelectionStateController buttonSelectionStateController,
            Lazy<UserNameViewController> userNameViewControllerLazy,
            Lazy<MicPrivacyChipViewController> micPrivacyChipViewControllerLazy,
            Lazy<CameraPrivacyChipViewController> cameraPrivacyChipViewControllerLazy,
            ButtonRoleHolderController buttonRoleHolderController,
            SystemBarConfigs systemBarConfigs,
            Provider<SystemUIQCViewController> qcViewControllerProvider,
            Lazy<MicPrivacyElementsProviderImpl> micPrivacyElementsProvider,
            Lazy<CameraPrivacyElementsProviderImpl> cameraPrivacyElementsProvider,
            DistantDisplayStatusIconController distantDisplayStatusIconController) {
        super(context, userTracker, carSystemBarViewFactory, carServiceProvider,
                broadcastDispatcher,
                configurationController, buttonSelectionStateController, userNameViewControllerLazy,
                micPrivacyChipViewControllerLazy, cameraPrivacyChipViewControllerLazy,
                buttonRoleHolderController, systemBarConfigs, qcViewControllerProvider,
                micPrivacyElementsProvider, cameraPrivacyElementsProvider);
        mDistantDisplayStatusIconController = distantDisplayStatusIconController;
    }

    @Nullable
    @Override
    public CarSystemBarView getTopBar(boolean isSetUp) {
        CarSystemBarView topSystemBarView = super.getTopBar(isSetUp);

        if (topSystemBarView != null) {
            mDistantDisplayStatusIconController.addDistantDisplayButtonView(topSystemBarView);
        }

        return topSystemBarView;
    }

    @Override
    public void removeAll() {
        super.removeAll();
        mDistantDisplayStatusIconController.onDestroy();
    }
}
