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
package com.chassis.car.ui.plugin.toolbar;

import android.view.View;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;

import com.android.car.ui.plugin.oemapis.toolbar.ProgressBarControllerOEMV1;

class ProgressBarController implements ProgressBarControllerOEMV1 {
    private final ProgressBar mProgressBar;

    ProgressBarController(@NonNull ProgressBar progressBar) {
        mProgressBar = progressBar;
    }

    @Override
    public void setVisible(boolean visible) {
        mProgressBar.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    @Override
    public void setIndeterminate(boolean indeterminate) {
        mProgressBar.setIndeterminate(indeterminate);
    }

    @Override
    public void setMax(int max) {
        mProgressBar.setMax(max);
    }

    @Override
    public void setMin(int min) {
        mProgressBar.setMin(min);
    }

    @Override
    public void setProgress(int progress) {
        mProgressBar.setProgress(progress);
    }
}
