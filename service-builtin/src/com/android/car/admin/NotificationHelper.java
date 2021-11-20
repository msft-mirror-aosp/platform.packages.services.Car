/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.car.admin;

import static android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS;

import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.BOILERPLATE_CODE;

import android.annotation.NonNull;
import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.car.ICarResultReceiver;
import android.car.builtin.util.Slogf;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.SparseArray;

import com.android.car.R;
import com.android.car.admin.ui.ManagedDeviceTextView;
import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.Preconditions;

import java.util.List;
import java.util.Objects;

// TODO(b/196947649): move this class to CarSettings or at least to some common package (not
// car-admin-ui-lib)
/**
 * Helper for notification-related tasks
 */
public final class NotificationHelper {
    // TODO: Move these constants to a common place. Right now a copy of these is present in
    // CarSettings' FactoryResetActivity.
    public static final String EXTRA_FACTORY_RESET_CALLBACK = "factory_reset_callback";
    public static final int FACTORY_RESET_NOTIFICATION_ID = 42;
    public static final int NEW_USER_DISCLAIMER_NOTIFICATION_ID = 108;

    /*
     * NOTE: IDs in the range {@code [RESOURCE_OVERUSE_NOTIFICATION_BASE_ID,
     * RESOURCE_OVERUSE_NOTIFICATION_BASE_ID + RESOURCE_OVERUSE_NOTIFICATION_MAX_OFFSET)} are
     * reserved for car watchdog's resource overuse notifications.
     */
    /** Base notification id for car watchdog's resource overuse notifications. */
    public static final int RESOURCE_OVERUSE_NOTIFICATION_BASE_ID = 150;

    /** Maximum notification offset for car watchdog's resource overuse notifications. */
    public static final int RESOURCE_OVERUSE_NOTIFICATION_MAX_OFFSET = 20;

    public static final String ACTION_RESOURCE_OVERUSE_DISABLE_APP =
            "com.android.car.watchdog.ACTION_RESOURCE_OVERUSE_DISABLE_APP";
    public static final String CAR_SERVICE_PACKAGE_NAME = "com.android.car";
    @VisibleForTesting
    public static final String CHANNEL_ID_DEFAULT = "channel_id_default";
    @VisibleForTesting
    public static final String CHANNEL_ID_HIGH = "channel_id_high";
    private static final boolean DEBUG = false;
    @VisibleForTesting
    static final String TAG = NotificationHelper.class.getSimpleName();

    /**
     * Creates a notification (and its notification channel) for the given importance type, setting
     * its name to be {@code Android System}.
     *
     * @param context context for showing the notification
     * @param importance notification importance. Currently only
     * {@link NotificationManager.IMPORTANCE_HIGH} is supported.
     */
    @NonNull
    public static Notification.Builder newNotificationBuilder(Context context,
            @NotificationManager.Importance int importance) {
        Objects.requireNonNull(context, "context cannot be null");

        String channelId, importanceName;
        switch (importance) {
            case NotificationManager.IMPORTANCE_DEFAULT:
                channelId = CHANNEL_ID_DEFAULT;
                importanceName = context.getString(R.string.importance_default);
                break;
            case NotificationManager.IMPORTANCE_HIGH:
                channelId = CHANNEL_ID_HIGH;
                importanceName = context.getString(R.string.importance_high);
                break;
            default:
                throw new IllegalArgumentException("Unsupported importance: " + importance);
        }
        NotificationManager notificationMgr = context.getSystemService(NotificationManager.class);
        notificationMgr.createNotificationChannel(
                new NotificationChannel(channelId, importanceName, importance));

        Bundle extras = new Bundle();
        extras.putString(Notification.EXTRA_SUBSTITUTE_APP_NAME,
                context.getString(com.android.internal.R.string.android_system_label));

        return new Notification.Builder(context, channelId).addExtras(extras);
    }

    @ExcludeFromCodeCoverageGeneratedReport(reason = BOILERPLATE_CODE,
            details = "private constructor")
    private NotificationHelper() {
        throw new UnsupportedOperationException("Contains only static methods");
    }

    /**
     * Shows the user disclaimer notification.
     */
    public static void showUserDisclaimerNotification(int userId, Context context) {
        // TODO(b/175057848) persist status so it's shown again if car service crashes?
        PendingIntent pendingIntent = getPendingUserDisclaimerIntent(context, /* extraFlags= */ 0,
                userId);

        Notification notification = NotificationHelper
                .newNotificationBuilder(context, NotificationManager.IMPORTANCE_DEFAULT)
                // TODO(b/177552737): Use a better icon?
                .setSmallIcon(R.drawable.car_ic_mode)
                .setContentTitle(context.getString(R.string.new_user_managed_notification_title))
                .setContentText(ManagedDeviceTextView.getManagedDeviceText(context))
                .setCategory(Notification.CATEGORY_CAR_INFORMATION)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();

        if (DEBUG) {
            Slogf.d(TAG, "Showing new managed notification (id "
                    + NEW_USER_DISCLAIMER_NOTIFICATION_ID + " on user " + context.getUser());
        }
        context.getSystemService(NotificationManager.class)
                .notifyAsUser(TAG, NEW_USER_DISCLAIMER_NOTIFICATION_ID,
                        notification, UserHandle.of(userId));
    }

    /**
     * Cancels the user disclaimer notification.
     */
    public static void cancelUserDisclaimerNotification(int userId, Context context) {
        if (DEBUG) {
            Slogf.d(TAG, "Canceling notification " + NEW_USER_DISCLAIMER_NOTIFICATION_ID
                    + " for user " + context.getUser());
        }
        context.getSystemService(NotificationManager.class)
                .cancelAsUser(TAG, NEW_USER_DISCLAIMER_NOTIFICATION_ID, UserHandle.of(userId));
        getPendingUserDisclaimerIntent(context, PendingIntent.FLAG_UPDATE_CURRENT, userId).cancel();
    }

    /**
     * Creates and returns the PendingIntent for User Disclaimer notification.
     */
    @VisibleForTesting
    public static PendingIntent getPendingUserDisclaimerIntent(Context context, int extraFlags,
            int userId) {
        return PendingIntent
                .getActivityAsUser(context, NEW_USER_DISCLAIMER_NOTIFICATION_ID,
                new Intent().setComponent(ComponentName.unflattenFromString(
                        context.getString(R.string.config_newUserDisclaimerActivity)
                )),
                PendingIntent.FLAG_IMMUTABLE | extraFlags, null, UserHandle.of(userId));
    }

    /**
     * Shows the user car watchdog's resource overuse notifications.
     */
    public static void showResourceOveruseNotificationsAsUser(Context context,
            UserHandle userHandle, List<String> headsUpNotificationPackages,
            List<String> notificationCenterPackages, int idStartOffset) {
        Preconditions.checkArgument(userHandle.getIdentifier() >= 0,
                "Must provide the user handle for a specific user");

        SparseArray<List<String>> packagesByImportance = new SparseArray<>(2);
        packagesByImportance.put(NotificationManager.IMPORTANCE_HIGH, headsUpNotificationPackages);
        packagesByImportance.put(NotificationManager.IMPORTANCE_DEFAULT,
                notificationCenterPackages);
        showResourceOveruseNotificationsAsUser(context, userHandle, packagesByImportance,
                idStartOffset);
    }

    /**
     * Sends the notification warning the user about the factory reset.
     */
    public static void showFactoryResetNotification(Context context, ICarResultReceiver callback) {
        // The factory request is received by CarService - which runs on system user - but the
        // notification will be sent to all users.
        UserHandle currentUser = UserHandle.of(ActivityManager.getCurrentUser());

        ComponentName factoryResetActivity = ComponentName.unflattenFromString(
                context.getString(R.string.config_factoryResetActivity));
        @SuppressWarnings("deprecation")
        Intent intent = new Intent()
                .setComponent(factoryResetActivity)
                .putExtra(EXTRA_FACTORY_RESET_CALLBACK, callback.asBinder());
        PendingIntent pendingIntent = PendingIntent.getActivityAsUser(context,
                FACTORY_RESET_NOTIFICATION_ID, intent, PendingIntent.FLAG_IMMUTABLE,
                /* options= */ null, currentUser);

        Notification notification = NotificationHelper
                .newNotificationBuilder(context, NotificationManager.IMPORTANCE_HIGH)
                .setSmallIcon(R.drawable.car_ic_warning)
                .setColor(context.getColor(R.color.red_warning))
                .setContentTitle(context.getString(R.string.factory_reset_notification_title))
                .setContentText(context.getString(R.string.factory_reset_notification_text))
                .setCategory(Notification.CATEGORY_CAR_WARNING)
                .setOngoing(true)
                .addAction(/* icon= */ 0,
                        context.getString(R.string.factory_reset_notification_button),
                        pendingIntent)
                .build();

        Slogf.i(TAG, "Showing factory reset notification on all users");
        context.getSystemService(NotificationManager.class)
                .notifyAsUser(TAG, FACTORY_RESET_NOTIFICATION_ID, notification, UserHandle.ALL);
    }

    private static void showResourceOveruseNotificationsAsUser(Context context, UserHandle user,
            SparseArray<List<String>> packagesByImportance, int idStartOffset) {
        PackageManager packageManager = context.getPackageManager();
        NotificationManager notificationManager =
                context.getSystemService(NotificationManager.class);

        CharSequence titleTemplate = context.getText(R.string.resource_overuse_notification_title);
        String textPrioritizeApp =
                context.getString(R.string.resource_overuse_notification_text_prioritize_app);
        String textDisableApp =
                context.getString(R.string.resource_overuse_notification_text_disable_app) + " "
                        + textPrioritizeApp;
        String textUninstallApp =
                context.getString(R.string.resource_overuse_notification_text_uninstall_app) + " "
                        + textPrioritizeApp;
        String actionTitlePrioritizeApp =
                context.getString(R.string.resource_overuse_notification_button_prioritize_app);
        String actionTitleDisableApp =
                context.getString(R.string.resource_overuse_notification_button_disable_app);
        String actionTitleUninstallApp =
                context.getString(R.string.resource_overuse_notification_button_uninstall_app);

        for (int i = 0; i < packagesByImportance.size(); i++) {
            int importance = packagesByImportance.keyAt(i);
            List<String> packages = packagesByImportance.valueAt(i);
            for (int pkgIdx = 0; pkgIdx < packages.size(); pkgIdx++) {
                int idOffset = (idStartOffset + pkgIdx) % RESOURCE_OVERUSE_NOTIFICATION_MAX_OFFSET;
                int notificationId = RESOURCE_OVERUSE_NOTIFICATION_BASE_ID + idOffset;
                String packageName = packages.get(pkgIdx);
                String text = textUninstallApp;
                String negativeActionText = actionTitleUninstallApp;

                CharSequence appName;
                PendingIntent positiveActionPendingIntent;
                PendingIntent negativeActionPendingIntent;
                try {
                    ApplicationInfo info = packageManager.getApplicationInfoAsUser(packageName,
                            /* flags= */ 0, user);
                    appName = info.loadLabel(packageManager);
                    negativeActionPendingIntent = positiveActionPendingIntent =
                            getAppSettingsPendingIntent(context, user, packageName, notificationId);
                    // Apps with SYSTEM flag are considered bundled apps by car settings and
                    // bundled apps have disable button rather than uninstall button.
                    if ((info.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                        text = textDisableApp;
                        negativeActionText = actionTitleDisableApp;
                        negativeActionPendingIntent = getDisableAppPendingIntent(context, user,
                                packageName, notificationId);
                    }
                } catch (PackageManager.NameNotFoundException e) {
                    Slogf.e(TAG, e, "Package '%s' not found for user %s", packageName, user);
                    continue;
                }
                Notification notification = NotificationHelper
                        .newNotificationBuilder(context, importance)
                        .setSmallIcon(R.drawable.car_ic_warning)
                        .setContentTitle(TextUtils.expandTemplate(titleTemplate, appName))
                        .setContentText(text)
                        .setCategory(Notification.CATEGORY_CAR_WARNING)
                        .addAction(new Notification.Action.Builder(/* icon= */ null,
                                actionTitlePrioritizeApp, positiveActionPendingIntent).build())
                        .addAction(new Notification.Action.Builder(/* icon= */ null,
                                negativeActionText, negativeActionPendingIntent).build())
                        .build();

                notificationManager.notifyAsUser(TAG, notificationId, notification, user);

                if (DEBUG) {
                    Slogf.d(TAG,
                            "Sent user notification (id %d) for resource overuse for "
                                    + "user %s.\nNotification { App name: %s, Importance: %d, "
                                    + "Description: %s, Positive button text: %s, Negative button "
                                    + "text: %s }",
                            notificationId, user, appName, importance, text,
                            actionTitlePrioritizeApp, negativeActionText);
                }
            }
            idStartOffset += packages.size();
        }
    }

    // TODO(b/205900458): Send the intent to CarWatchdogService, where once the notification is
    //  received it should be dismissed. Pass the notification id to the intent as an extra.
    private static PendingIntent getAppSettingsPendingIntent(Context context, UserHandle user,
            String packageName, int notificationId) {
        Intent intent = new Intent(ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:" + packageName));
        return PendingIntent.getActivityAsUser(context, notificationId, intent,
                PendingIntent.FLAG_IMMUTABLE, /* options= */ null, user);
    }

    // TODO(b/205900458): Pass the notification id to the intent as an extra.
    private static PendingIntent getDisableAppPendingIntent(Context context, UserHandle user,
            String packageName, int notificationId) {
        Intent intent = new Intent(ACTION_RESOURCE_OVERUSE_DISABLE_APP)
                .putExtra(Intent.EXTRA_PACKAGE_NAME, packageName)
                .putExtra(Intent.EXTRA_USER, user)
                .setPackage(context.getPackageName())
                .setFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY);
        return PendingIntent.getBroadcastAsUser(context, notificationId, intent,
                PendingIntent.FLAG_IMMUTABLE, user);
    }
}
