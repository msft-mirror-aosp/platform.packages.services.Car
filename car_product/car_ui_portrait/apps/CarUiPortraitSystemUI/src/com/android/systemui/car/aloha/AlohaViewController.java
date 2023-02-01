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

package com.android.systemui.car.aloha;

import com.android.systemui.R;
import com.android.systemui.car.hvac.CarUiPortraitTemperatureControlView;
import com.android.systemui.car.hvac.HvacController;
import com.android.systemui.car.window.OverlayViewController;
import com.android.systemui.car.window.OverlayViewGlobalStateController;
import com.android.systemui.dagger.SysUISingleton;

import javax.inject.Inject;

/**
 * Controller for {@link R.layout#aloha_screen}.
 */
@SysUISingleton
public class AlohaViewController extends OverlayViewController {

    private HvacController mHvacController;

    @Inject
    public AlohaViewController(
            OverlayViewGlobalStateController overlayViewGlobalStateController,
            HvacController controller) {
        super(R.id.aloha_screen_stub, overlayViewGlobalStateController);

        mHvacController = controller;
    }

    @Override
    protected void onFinishInflate() {
        getLayout().findViewById(R.id.aloha_action_button).setOnClickListener(v -> {
            stop();
        });

        CarUiPortraitTemperatureControlView view =
                (CarUiPortraitTemperatureControlView) getLayout().findViewById(R.id.driver_hvac);

        mHvacController.registerHvacViews(view);
    }
}

