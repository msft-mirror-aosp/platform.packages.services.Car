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

package com.android.car.portraitlauncher.controlbar;

import static android.content.pm.ActivityInfo.CONFIG_UI_MODE;
import static android.window.DisplayAreaOrganizer.FEATURE_DEFAULT_TASK_CONTAINER;

import static com.android.car.portraitlauncher.panel.TaskViewPanelStateChangeReason.ON_MEDIA_INTENT;

import android.annotation.Nullable;
import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.LinearLayout;

import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;

import com.android.car.carlauncher.homescreen.HomeCardModule;
import com.android.car.carlauncher.homescreen.audio.IntentHandler;
import com.android.car.carlauncher.homescreen.audio.media.MediaIntentRouter;
import com.android.car.portraitlauncher.R;
import com.android.car.portraitlauncher.homeactivities.TaskCategoryManager;

import java.lang.reflect.InvocationTargetException;
import java.util.Set;
import java.util.function.Function;

/**
 * Launcher activity that shows only the control bar fragment.
 */
public class ControlBarActivity extends FragmentActivity {
    private static final String TAG = ControlBarActivity.class.getSimpleName();
    private static final boolean DBG = Build.IS_DEBUGGABLE;

    private final Configuration mConfiguration = new Configuration();

    private boolean mIsApplicationPanelOpen;
    private ActivityManager.RunningTaskInfo mCurrentTaskInApplicationPanel;
    private LinearLayout mControlBarArea;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.control_bar_activity);

        mControlBarArea = findViewById(R.id.control_bar_area);

        initializeCards();

        IntentHandler mediaIntentHandler = new ControlBarActivityIntentHandler(
                TaskCategoryManager::isMediaApp, ON_MEDIA_INTENT);

        MediaIntentRouter.getInstance().registerMediaIntentHandler(mediaIntentHandler);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        int diff = mConfiguration.updateFrom(newConfig);
        if (DBG) {
            Log.d(TAG, "onConfigurationChanged with diff =" + diff);
        }
        if ((diff & CONFIG_UI_MODE) == 0) {
            return;
        }

        initializeCards();

        Drawable background = getResources().getDrawable(R.drawable.control_bar_background,
                getTheme());
        mControlBarArea.setBackground(background);
    }

    private void initializeCards() {
        Set<HomeCardModule> homeCardModules = new androidx.collection.ArraySet<>();
        for (String providerClassName : getResources().getStringArray(
                R.array.config_homeCardModuleClasses)) {
            try {
                HomeCardModule cardModule = Class.forName(providerClassName).asSubclass(
                        HomeCardModule.class).getDeclaredConstructor().newInstance();
                cardModule.setViewModelProvider(new ViewModelProvider(/* owner= */ this));
                homeCardModules.add(cardModule);
            } catch (IllegalAccessException | InstantiationException | ClassNotFoundException
                     | InvocationTargetException | NoSuchMethodException e) {
                Log.w(TAG, "Unable to create HomeCardProvider class " + providerClassName, e);
            }
        }
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        for (HomeCardModule cardModule : homeCardModules) {
            transaction.replace(cardModule.getCardResId(), cardModule.getCardView().getFragment());
        }
        transaction.commitNow();
    }

    private class ControlBarActivityIntentHandler implements IntentHandler {
        private final Function<ActivityManager.RunningTaskInfo, Boolean> mTaskChecker;
        private final String mReason;

        ControlBarActivityIntentHandler(
                Function<ActivityManager.RunningTaskInfo, Boolean> taskChecker,
                String reason) {
            mTaskChecker = taskChecker;
            mReason = reason;
        }

        @Override
        public void handleIntent(@Nullable Intent intent) {
            if (DBG) {
                Log.d(TAG, "handleIntent mCurrentTaskInApplicationPanel: "
                        + mCurrentTaskInApplicationPanel
                        + ", incoming intent = "
                        + intent);
            }

            if (mTaskChecker.apply(mCurrentTaskInApplicationPanel) && mIsApplicationPanelOpen) {
                return;
            }

            if (intent != null) {
                intent.setFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT);
                ActivityOptions options = ActivityOptions.makeBasic();
                options.setLaunchTaskDisplayAreaFeatureId(FEATURE_DEFAULT_TASK_CONTAINER);
                startActivity(intent, options.toBundle());
            }
        }
    }
}
