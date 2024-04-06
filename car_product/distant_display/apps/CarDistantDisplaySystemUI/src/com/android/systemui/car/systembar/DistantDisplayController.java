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
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.media.MediaDescription;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.MediaSessionManager.OnActiveSessionsChangedListener;
import android.util.Log;
import android.view.Display;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.systemui.R;
import com.android.systemui.car.distantdisplay.common.DistantDisplayQcItem;
import com.android.systemui.car.distantdisplay.common.TaskViewController;
import com.android.systemui.car.distantdisplay.util.AppCategoryDetector;
import com.android.systemui.car.qc.DistantDisplayControlsUpdateListener;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.settings.UserTracker;

import java.util.Optional;

import javax.inject.Inject;

/**
 * A Controller that observes to foreground tasks and media sessions to provide controls for distant
 * display quick controls panel.
 **/
@SysUISingleton
public class DistantDisplayController {
    public static final String TAG = DistantDisplayController.class.getSimpleName();

    private final PackageManager mPackageManager;
    private final MediaSessionManager mMediaSessionManager;
    private final Context mContext;
    private final UserTracker mUserTracker;
    private final TaskViewController mTaskViewController;
    private final Drawable mDistantDisplayDrawable;
    private final Drawable mDefaultDisplayDrawable;
    @Nullable
    private Intent mTopIntentOnDefaultDisplay;
    @Nullable
    private Intent mTopIntentOnDistantDisplay;
    private StatusChangeListener mStatusChangeListener;
    private DistantDisplayControlsUpdateListener mDistantDisplayControlsUpdateListener;
    private AppCategoryDetector mAppCategoryDetector;

    /**
     * Interface for listeners to register for status changes based on Media Session and TaskStack
     * Changes.
     **/
    public interface StatusChangeListener {
        /**
         * Callback triggered for display changes of the task.
         */
        void onDisplayChanged(int displayId);

        /**
         * Callback triggered for visibility changes.
         */
        void onVisibilityChanged(boolean visible);
    }

    private final TaskViewController.Callback mTaskViewControllerCallback =
            new TaskViewController.Callback() {
                @Override
                public void topAppOnDisplayChanged(int displayId, Intent intent) {
                    int distantDisplayId = mTaskViewController.getDistantDisplayId();
                    if (displayId == Display.DEFAULT_DISPLAY) {
                        mTopIntentOnDefaultDisplay = intent;
                        updateButtonState();

                    } else if (distantDisplayId != Display.INVALID_DISPLAY
                            && displayId == distantDisplayId) {
                        mTopIntentOnDistantDisplay = intent;
                        updateButtonState();
                    }
                }
            };

    private final OnActiveSessionsChangedListener mOnActiveSessionsChangedListener =
            controllers -> {
                updateButtonState();
            };

    private final UserTracker.Callback mUserChangedCallback = new UserTracker.Callback() {
        @Override
        public void onUserChanged(int newUser, @NonNull Context userContext) {

            mMediaSessionManager.removeOnActiveSessionsChangedListener(
                    mOnActiveSessionsChangedListener);

            mMediaSessionManager.addOnActiveSessionsChangedListener(null,
                    mUserTracker.getUserHandle(),
                    mContext.getMainExecutor(), mOnActiveSessionsChangedListener);
        }
    };

    @Inject
    public DistantDisplayController(Context context, UserTracker userTracker,
            TaskViewController taskViewController) {
        mContext = context;
        mUserTracker = userTracker;
        mTaskViewController = taskViewController;
        mPackageManager = context.getPackageManager();
        mDistantDisplayDrawable = mContext.getResources().getDrawable(
                R.drawable.ic_distant_display_nav, /* theme= */ null);
        mDefaultDisplayDrawable = mContext.getResources().getDrawable(
                R.drawable.ic_default_display_nav, /* theme= */ null);
        mMediaSessionManager = mContext.getSystemService(MediaSessionManager.class);
        mMediaSessionManager.addOnActiveSessionsChangedListener(/* notificationListener= */ null,
                userTracker.getUserHandle(),
                mContext.getMainExecutor(), mOnActiveSessionsChangedListener);
        mUserTracker.addCallback(mUserChangedCallback, context.getMainExecutor());
        mTaskViewController.addCallback(mTaskViewControllerCallback);
        mAppCategoryDetector = new AppCategoryDetector(mContext);
    }

    /**
     * Sets a listener that needs to be notified of controls change available in status panel.
     **/
    public void setDistantDisplayControlStatusInfoListener(
            StatusChangeListener statusChangeListener) {
        mStatusChangeListener = statusChangeListener;
        updateButtonState();
    }

    /**
     * Removes a DistantDisplayControlStatusInfoListener.
     **/
    public void removeDistantDisplayControlStatusInfoListener() {
        mStatusChangeListener = null;
    }

    public void setDistantDisplayControlsUpdateListener(
            DistantDisplayControlsUpdateListener distantDisplayControlsUpdateListener) {
        mDistantDisplayControlsUpdateListener = distantDisplayControlsUpdateListener;
    }

    /**
     * @return A metadata of current app on distant/default display
     * {@link com.android.systemui.car.distantdisplay.common.DistantDisplayQcItem}
     */
    public DistantDisplayQcItem getMetadata() {
        Optional<MediaController> optionalMediaController =
                getMediaControllerFromActiveMediaSession();
        if (optionalMediaController.isEmpty()) return null;

        MediaController mediaController = optionalMediaController.get();
        String packageName = mediaController.getPackageName();
        Drawable mediaAppIcon = null;
        String mediaAppName = "";
        try {
            ApplicationInfo applicationInfo = mPackageManager.getApplicationInfoAsUser(packageName,
                    PackageManager.GET_META_DATA, mUserTracker.getUserId());
            mediaAppName = (String) applicationInfo.loadLabel(mPackageManager);
            mediaAppIcon = applicationInfo.loadIcon(mPackageManager);
        } catch (PackageManager.NameNotFoundException e) {
            logIfDebuggable("Error retrieving application info");
        }
        MediaDescription mediaDescription = mediaController.getMetadata().getDescription();
        String mediaTitle = (String) mediaDescription.getTitle();
        return new DistantDisplayQcItem.Builder()
                .setTitle(mediaTitle)
                .setSubtitle(mediaAppName)
                .setIcon(mediaAppIcon)
                .build();
    }

    /**
     * @return Controls to move content between display
     * {@link com.android.systemui.car.distantdisplay.common.DistantDisplayQcItem}
     */
    public DistantDisplayQcItem getControls() {
        if (!isComponentRestricted(mTopIntentOnDistantDisplay)) {
            return new DistantDisplayQcItem.Builder()
                    .setTitle(mContext.getString(R.string.qc_bring_back_to_default_display_title))
                    .setIcon(mDefaultDisplayDrawable)
                    .setActionHandler((item, context, intent) ->
                            mTaskViewController.moveTaskFromDistantDisplay())
                    .build();
        } else if (!isComponentRestricted(mTopIntentOnDefaultDisplay)) {
            return new DistantDisplayQcItem.Builder()
                    .setTitle(mContext.getString(R.string.qc_send_to_pano_title))
                    .setIcon(mDistantDisplayDrawable)
                    .setActionHandler((item, context, intent) ->
                            mTaskViewController.moveTaskToDistantDisplay())
                    .build();
        } else {
            return null;
        }
    }

    private Optional<MediaController> getMediaControllerFromActiveMediaSession() {
        String foregroundMediaPackage;
        if (!isComponentRestricted(mTopIntentOnDistantDisplay)
                && isVideoApp(mTopIntentOnDistantDisplay)) {
            foregroundMediaPackage = mTopIntentOnDistantDisplay.getPackage();
        } else if (!isComponentRestricted(mTopIntentOnDefaultDisplay)
                && isVideoApp(mTopIntentOnDefaultDisplay)) {
            foregroundMediaPackage = mTopIntentOnDefaultDisplay.getPackage();
        } else {
            return Optional.empty();
        }

        return mMediaSessionManager.getActiveSessionsForUser(/* notificationListener= */ null,
                        mUserTracker.getUserHandle())
                .stream()
                .filter(
                        mediaController -> foregroundMediaPackage
                                .equals(mediaController.getPackageName()))
                .findFirst();
    }

    private boolean isVideoApp(Intent intent) {
        if (intent == null || intent.getPackage() == null) return false;
        return AppCategoryDetector.isVideoApp(mUserTracker.getUserContext().getPackageManager(),
                intent.getPackage());
    }

    private boolean isComponentRestricted(@Nullable Intent intent) {
        if (intent == null) return true;
        return mAppCategoryDetector.isComponentRestricted(intent.getComponent());
    }

    private void updateButtonState() {
        if (mStatusChangeListener == null) return;

        if (!isComponentRestricted(mTopIntentOnDistantDisplay)) {
            int distantDisplayId = mTaskViewController.getDistantDisplayId();
            if (distantDisplayId != Display.INVALID_DISPLAY) {
                mStatusChangeListener.onDisplayChanged(distantDisplayId);
            }
            mStatusChangeListener.onVisibilityChanged(true);
        } else if (!isComponentRestricted(mTopIntentOnDefaultDisplay)) {
            mStatusChangeListener.onDisplayChanged(Display.DEFAULT_DISPLAY);
            mStatusChangeListener.onVisibilityChanged(true);
        } else {
            mStatusChangeListener.onVisibilityChanged(false);
        }

        if (mDistantDisplayControlsUpdateListener != null) {
            mDistantDisplayControlsUpdateListener.onControlsChanged();
        }
    }

    private static void logIfDebuggable(String message) {
        Log.d(TAG, message);
    }
}
