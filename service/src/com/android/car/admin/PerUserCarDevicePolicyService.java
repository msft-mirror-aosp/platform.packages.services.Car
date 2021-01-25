/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static com.android.car.admin.CarDevicePolicyService.DEBUG;

import android.annotation.IntDef;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.DebugUtils;
import android.util.IndentingPrintWriter;
import android.util.Slog;

import com.android.internal.annotations.GuardedBy;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

// TODO(b/175057848) add unit tests

/**
 * User-specific {@code CarDevicePolicyManagerService}.
 */
public final class PerUserCarDevicePolicyService {

    private static final String TAG = PerUserCarDevicePolicyService.class.getSimpleName();

    private static final String PREFIX_NEW_USER_DISCLAIMER_STATUS = "NEW_USER_DISCLAIMER_STATUS_";

    // TODO(b/175057848) must be public because of DebugUtils.constantToString()
    public static final int NEW_USER_DISCLAIMER_STATUS_NEVER_RECEIVED = 0;
    public static final int NEW_USER_DISCLAIMER_STATUS_RECEIVED = 1;
    public static final int NEW_USER_DISCLAIMER_STATUS_NOTIFICATION_SENT = 2;
    public static final int NEW_USER_DISCLAIMER_STATUS_SHOWN = 3;
    public static final int NEW_USER_DISCLAIMER_STATUS_ACKED = 4;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef(flag = false, prefix = { PREFIX_NEW_USER_DISCLAIMER_STATUS }, value = {
            NEW_USER_DISCLAIMER_STATUS_NEVER_RECEIVED,
            NEW_USER_DISCLAIMER_STATUS_NOTIFICATION_SENT,
            NEW_USER_DISCLAIMER_STATUS_RECEIVED,
            NEW_USER_DISCLAIMER_STATUS_SHOWN,
            NEW_USER_DISCLAIMER_STATUS_ACKED
    })
    public @interface NewUserDisclaimerStatus {}

    private static final Object SLOCK = new Object();

    @GuardedBy("SLOCK")
    private static PerUserCarDevicePolicyService sInstance;

    private final Context mContext;

    @GuardedBy("sLock")
    @NewUserDisclaimerStatus
    private int mNewUserDisclaimerStatus = NEW_USER_DISCLAIMER_STATUS_NEVER_RECEIVED;

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (DEBUG) {
                Slog.d(TAG, "Received intent on user " + mContext.getUserId() + ": " + intent);
            }
            switch(intent.getAction()) {
                case DevicePolicyManager.ACTION_SHOW_NEW_USER_DISCLAIMER:
                    setUserDisclaimerStatus(NEW_USER_DISCLAIMER_STATUS_RECEIVED);
                    showNewUserDisclaimer();
                    break;
                default:
                    Slog.w(TAG, "received unexpected intent: " + intent);
            }
        }
    };

    /**
     * Gests the singleton instance, creating it if necessary.
     */
    public static PerUserCarDevicePolicyService getInstance(Context context) {
        synchronized (SLOCK) {
            if (sInstance == null) {
                sInstance = new PerUserCarDevicePolicyService(context.getApplicationContext());
                if (DEBUG) Slog.d(TAG, "Created instance: " + sInstance);
            }

            return sInstance;
        }
    }

    private PerUserCarDevicePolicyService(Context context) {
        mContext = context;
    }

    /**
     * Register a broadcast receiver to receive the proper events.
     */
    public void registerBroadcastReceiver() {
        if (DEBUG) Slog.d(TAG, "registering BroadcastReceiver");

        mContext.registerReceiver(mBroadcastReceiver, new IntentFilter(
                DevicePolicyManager.ACTION_SHOW_NEW_USER_DISCLAIMER));
    }

    /**
     * Callback for when the service is not needed anymore.
     */
    public void onDestroy() {
        synchronized (SLOCK) {
            sInstance = null;
        }
        if (DEBUG) Slog.d(TAG, "unregistering BroadcastReceiver");
        mContext.unregisterReceiver(mBroadcastReceiver);
    }

    /**
     * Dump its contents.
     */
    public void dump(IndentingPrintWriter pw) {
        synchronized (SLOCK) {
            pw.printf("mNewUserDisclaimerStatus: %s\n",
                    newUserDisclaimerStatusToString(mNewUserDisclaimerStatus));
        }
    }

    void setShown() {
        setUserDisclaimerStatus(NEW_USER_DISCLAIMER_STATUS_SHOWN);
    }

    void setAcknowledged() {
        setUserDisclaimerStatus(NEW_USER_DISCLAIMER_STATUS_ACKED);
        NewUserDisclaimerActivity.cancelNotification(mContext);

        DevicePolicyManager dpm = mContext.getSystemService(DevicePolicyManager.class);
        dpm.resetNewUserDisclaimer();
    }

    private void showNewUserDisclaimer() {
        // TODO(b/175057848) persist status so it's shown again if car service crashes?
        NewUserDisclaimerActivity.showNotification(mContext);
        setUserDisclaimerStatus(NEW_USER_DISCLAIMER_STATUS_NOTIFICATION_SENT);
    }

    private void setUserDisclaimerStatus(@NewUserDisclaimerStatus int status) {
        synchronized (SLOCK) {
            if (DEBUG) {
                Slog.d(TAG, "Changinging status from "
                        + newUserDisclaimerStatusToString(mNewUserDisclaimerStatus) + " to "
                        + newUserDisclaimerStatusToString(status));
            }
            mNewUserDisclaimerStatus = status;
        }
    }

    private String newUserDisclaimerStatusToString(@NewUserDisclaimerStatus int status) {
        return DebugUtils.constantToString(PerUserCarDevicePolicyService.class,
                PREFIX_NEW_USER_DISCLAIMER_STATUS, status);
    }
}
