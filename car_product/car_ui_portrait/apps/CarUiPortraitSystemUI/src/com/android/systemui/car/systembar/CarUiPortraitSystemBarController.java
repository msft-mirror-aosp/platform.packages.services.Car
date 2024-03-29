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

package com.android.systemui.car.systembar;

import android.content.Context;

import androidx.annotation.NonNull;

import com.android.car.docklib.DockViewController;
import com.android.car.docklib.ExcludedItemsProvider;
import com.android.car.docklib.view.DockView;
import com.android.systemui.car.CarServiceProvider;
import com.android.systemui.car.dock.BackgroundExcludedItemsProvider;
import com.android.systemui.car.statusbar.UserNameViewController;
import com.android.systemui.car.statusicon.StatusIconPanelViewController;
import com.android.systemui.settings.UserFileManager;
import com.android.systemui.settings.UserTracker;

import dagger.Lazy;

import java.io.File;
import java.util.Set;

import javax.inject.Provider;

/** A single class which controls the navigation bar views. */
public class CarUiPortraitSystemBarController extends CarSystemBarController {
    private BackgroundExcludedItemsProvider mBackgroundExcludedItemsProvider;

    public CarUiPortraitSystemBarController(
            Context context,
            UserTracker userTracker,
            CarSystemBarViewFactory carSystemBarViewFactory,
            CarServiceProvider carServiceProvider,
            ButtonSelectionStateController buttonSelectionStateController,
            Lazy<UserNameViewController> userNameViewControllerLazy,
            Lazy<MicPrivacyChipViewController> micPrivacyChipViewControllerLazy,
            Lazy<CameraPrivacyChipViewController> cameraPrivacyChipViewControllerLazy,
            ButtonRoleHolderController buttonRoleHolderController,
            SystemBarConfigs systemBarConfigs,
            Provider<StatusIconPanelViewController.Builder> panelControllerBuilderProvider,
            UserFileManager userFileManager
    ) {
        super(context, userTracker, carSystemBarViewFactory, carServiceProvider,
                buttonSelectionStateController, userNameViewControllerLazy,
                micPrivacyChipViewControllerLazy, cameraPrivacyChipViewControllerLazy,
                buttonRoleHolderController, systemBarConfigs, panelControllerBuilderProvider,
                userFileManager);
    }

    @Override
    protected DockViewController createDockViewController(
            DockView dockView,
            Context userContext,
            File dataFile
    ) {
        return new DockViewController(dockView, userContext, dataFile) {
            @Override
            public void destroy() {
                super.destroy();
                mBackgroundExcludedItemsProvider.destroy();
                mBackgroundExcludedItemsProvider = null;
            }

            @NonNull
            @Override
            public Set<ExcludedItemsProvider> getExcludedItemsProviders() {
                if (mBackgroundExcludedItemsProvider == null) {
                    mBackgroundExcludedItemsProvider =
                            new BackgroundExcludedItemsProvider(userContext);
                }
                Set<ExcludedItemsProvider> providers = super.getExcludedItemsProviders();
                providers.add(mBackgroundExcludedItemsProvider);
                return providers;
            }
        };
    }
}
