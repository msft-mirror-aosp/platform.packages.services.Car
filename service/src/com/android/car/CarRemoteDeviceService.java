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

package com.android.car;

import static android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND;
import static android.car.CarOccupantZoneManager.INVALID_USER_ID;
import static android.car.CarOccupantZoneManager.ZONE_CONFIG_CHANGE_FLAG_USER;
import static android.car.CarRemoteDeviceManager.FLAG_CLIENT_INSTALLED;
import static android.car.CarRemoteDeviceManager.FLAG_CLIENT_IN_FOREGROUND;
import static android.car.CarRemoteDeviceManager.FLAG_CLIENT_RUNNING;
import static android.car.CarRemoteDeviceManager.FLAG_CLIENT_SAME_SIGNATURE;
import static android.car.CarRemoteDeviceManager.FLAG_CLIENT_SAME_VERSION;
import static android.car.CarRemoteDeviceManager.FLAG_OCCUPANT_ZONE_CONNECTION_READY;
import static android.car.CarRemoteDeviceManager.FLAG_OCCUPANT_ZONE_POWER_ON;
import static android.car.CarRemoteDeviceManager.FLAG_OCCUPANT_ZONE_SCREEN_UNLOCKED;
import static android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES;

import static com.android.car.CarServiceUtils.assertPermission;
import static com.android.car.CarServiceUtils.checkCalledByPackage;
import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DEBUGGING_CODE;
import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DUMP_INFO;

import android.annotation.IntDef;
import android.annotation.Nullable;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.car.Car;
import android.car.CarOccupantZoneManager.OccupantZoneInfo;
import android.car.CarRemoteDeviceManager.AppState;
import android.car.CarRemoteDeviceManager.OccupantZoneState;
import android.car.ICarOccupantZoneCallback;
import android.car.builtin.app.ActivityManagerHelper.ProcessObserverCallback;
import android.car.builtin.util.Slogf;
import android.car.occupantconnection.ICarRemoteDevice;
import android.car.occupantconnection.IStateCallback;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;
import android.util.SparseArray;

import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.util.BinderKeyValueContainer;
import com.android.car.internal.util.IndentingPrintWriter;
import com.android.car.occupantconnection.ClientId;
import com.android.car.power.CarPowerManagementService;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Service to implement APIs defined in {@link android.car.CarRemoteDeviceManager}.
 * <p>
 * In this class, a discovering client refers to a client that has registered an {@link
 * IStateCallback}, and discovered apps refer to the peer apps of the discovering client.
 * <p>
 * This class can monitor the states of occupant zones in the car and the peer clients in
 * those occupant zones. There are 3 {@link OccupantZoneState}s:
 * <ul>
 *   <li> {@link android.car.CarRemoteDeviceManager#FLAG_OCCUPANT_ZONE_POWER_ON} is updated by the
 *        DisplayListener. TODO(b/257117236).
 *   <li> TODO(b/257117236): implement FLAG_OCCUPANT_ZONE_SCREEN_UNLOCKED.
 *   <li> {@link android.car.CarRemoteDeviceManager#FLAG_OCCUPANT_ZONE_CONNECTION_READY} is updated
 *        by the ICarOccupantZoneCallback.
 * </ul>
 * There are 5 {@link AppState}s:
 * <ul>
 *   <li> App install states ({@link android.car.CarRemoteDeviceManager#FLAG_CLIENT_INSTALLED},
 *        {@link android.car.CarRemoteDeviceManager#FLAG_CLIENT_SAME_VERSION},
 *        {@link android.car.CarRemoteDeviceManager#FLAG_CLIENT_SAME_SIGNATURE}) are updated by the
 *        PackageChangeReceiver.
 *   <li> App running states ({@link android.car.CarRemoteDeviceManager#FLAG_CLIENT_RUNNING},
 *        {@link android.car.CarRemoteDeviceManager#FLAG_CLIENT_IN_FOREGROUND}) are updated by the
 *        ProcessRunningStateCallback. Note: these states won't be updated for apps that share the
 *        same user ID through the "sharedUserId" mechanism.
 * </ul>
 */
public class CarRemoteDeviceService extends ICarRemoteDevice.Stub implements
        CarServiceBase {

    private static final String TAG = CarRemoteDeviceService.class.getSimpleName();
    private static final boolean DBG = Slogf.isLoggable(TAG, Log.DEBUG);
    private static final String INDENTATION_2 = "  ";
    private static final String INDENTATION_4 = "    ";

    private static final int PROCESS_NOT_RUNNING = 0;
    private static final int PROCESS_RUNNING_IN_BACKGROUND = 1;
    private static final int PROCESS_RUNNING_IN_FOREGROUND = 2;

    @IntDef(flag = false, prefix = {"PROCESS_"}, value = {
            PROCESS_NOT_RUNNING,
            PROCESS_RUNNING_IN_BACKGROUND,
            PROCESS_RUNNING_IN_FOREGROUND
    })
    @Retention(RetentionPolicy.SOURCE)
    @interface ProcessRunningState {
    }

    @VisibleForTesting
    @AppState
    static final int INITIAL_APP_STATE = 0;
    @VisibleForTesting
    @OccupantZoneState
    static final int INITIAL_OCCUPANT_ZONE_STATE = 0;

    private final Object mLock = new Object();
    private final Context mContext;
    private final CarOccupantZoneService mOccupantZoneService;
    private final CarPowerManagementService mPowerManagementService;
    private final SystemActivityMonitoringService mSystemActivityMonitoringService;
    private final ActivityManager mActivityManager;
    private final UserManager mUserManager;

    /** A map of discovering client to its callback. */
    @GuardedBy("mLock")
    private final BinderKeyValueContainer<ClientId, IStateCallback> mCallbackMap;

    /** A map of client app to its {@link AppState}. */
    @GuardedBy("mLock")
    private final ArrayMap<ClientId, Integer> mAppStateMap;

    /**
     * A map of occupant zone to its {@link OccupantZoneState}. Its keys are all the occupant
     * zones on this SoC and will never change after initialization.
     */
    @GuardedBy("mLock")
    private final ArrayMap<OccupantZoneInfo, Integer> mOccupantZoneStateMap;

    /** A map of secondary user (non-system user) ID to PerUserInfo. */
    @GuardedBy("mLock")
    private final SparseArray<PerUserInfo> mPerUserInfoMap;

    private final ProcessObserverCallback mProcessObserver = new ProcessObserver();

    private final class PackageChangeReceiver extends BroadcastReceiver {

        /** The user ID that this receiver registered as. */
        private final int mUserId;
        /** The occupant zone that the user runs in. */
        private final OccupantZoneInfo mOccupantZone;

        @VisibleForTesting
        PackageChangeReceiver(int userId, OccupantZoneInfo occupantZone) {
            super();
            this.mUserId = userId;
            this.mOccupantZone = occupantZone;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            String packageName = intent.getData().getSchemeSpecificPart();
            synchronized (mLock) {
                if (!isDiscoveringLocked(packageName)) {
                    // There is no peer client discovering this app, so ignore its install/uninstall
                    // event.
                    if (DBG) {
                        Slogf.v(TAG, "Ignore package change for %s as user %d because there is no "
                                + "peer client discovering this app", packageName, mUserId);
                    }
                    return;
                }
                ClientId clientId = new ClientId(mOccupantZone, mUserId, packageName);
                if (Intent.ACTION_PACKAGE_ADDED.equals(intent.getAction())) {
                    Slogf.v(TAG, "%s was installed", clientId);
                    @AppState int newState = calculateAppStateLocked(clientId);
                    setAppStateLocked(clientId, newState, /* callbackToNotify= */ null);
                } else if (Intent.ACTION_PACKAGE_REMOVED.equals(intent.getAction())) {
                    Slogf.v(TAG, "%s was uninstalled", clientId);
                    setAppStateLocked(clientId, INITIAL_APP_STATE, /* callbackToNotify= */ null);
                }
            }
        }
    }

    private final class ProcessObserver extends ProcessObserverCallback {
        @Override
        public void onForegroundActivitiesChanged(int pid, int uid, boolean foregroundActivities) {
            handleProcessRunningStateChange(uid, foregroundActivities
                    ? PROCESS_RUNNING_IN_FOREGROUND
                    : PROCESS_RUNNING_IN_BACKGROUND);
        }

        @Override
        public void onProcessDied(int pid, int uid) {
            handleProcessRunningStateChange(uid, PROCESS_NOT_RUNNING);
        }
    }

    /** Wrapper class for objects that are specific to a non-system user. */
    @VisibleForTesting
    static final class PerUserInfo {

        /** The occupant zone that the user runs in. */
        public final OccupantZoneInfo zone;

        /** The Context of the user. Used to register and unregister the receiver. */
        public final Context context;

        /** The PackageManager of the user. */
        public final PackageManager pm;

        /** The PackageChangeReceiver. Used to listen to package install/uninstall events. */
        public final BroadcastReceiver receiver;

        @VisibleForTesting
        PerUserInfo(OccupantZoneInfo zone, Context context, PackageManager pm,
                BroadcastReceiver receiver) {
            this.zone = zone;
            this.context = context;
            this.pm = pm;
            this.receiver = receiver;
        }
    }

    public CarRemoteDeviceService(Context context,
            CarOccupantZoneService occupantZoneService,
            CarPowerManagementService powerManagementService,
            SystemActivityMonitoringService systemActivityMonitoringService) {
        this(context, occupantZoneService, powerManagementService, systemActivityMonitoringService,
                context.getSystemService(ActivityManager.class),
                context.getSystemService(UserManager.class),
                /* perUserInfoMap= */ new SparseArray<>(),
                /* callbackMap= */ new BinderKeyValueContainer<>(),
                /* appStateMap= */ new ArrayMap<>(),
                /* occupantZoneStateMap= */ new ArrayMap<>());
    }

    @VisibleForTesting
    CarRemoteDeviceService(Context context,
            CarOccupantZoneService occupantZoneService,
            CarPowerManagementService powerManagementService,
            SystemActivityMonitoringService systemActivityMonitoringService,
            ActivityManager activityManager,
            UserManager userManager,
            SparseArray<PerUserInfo> perUserInfoMap,
            BinderKeyValueContainer<ClientId, IStateCallback> callbackMap,
            ArrayMap<ClientId, Integer> appStateMap,
            ArrayMap<OccupantZoneInfo, Integer> occupantZoneStateMap) {
        mContext = context;
        mOccupantZoneService = occupantZoneService;
        mPowerManagementService = powerManagementService;
        mSystemActivityMonitoringService = systemActivityMonitoringService;
        mActivityManager = activityManager;
        mUserManager = userManager;
        mPerUserInfoMap = perUserInfoMap;
        mCallbackMap = callbackMap;
        mAppStateMap = appStateMap;
        mOccupantZoneStateMap = occupantZoneStateMap;
    }

    @Override
    public void init() {
        initAllOccupantZones();
        registerOccupantZoneCallback();
        initAssignedUsers();
    }

    @Override
    public void release() {
        // TODO(b/257117236): implement this method.
    }

    /** Run `adb shell dumpsys car_service --services CarRemoteDeviceService` to dump. */
    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public void dump(IndentingPrintWriter writer) {
        writer.println("*CarRemoteDeviceService*");
        synchronized (mLock) {
            writer.printf("%smCallbackMap:\n", INDENTATION_2);
            for (int i = 0; i < mCallbackMap.size(); i++) {
                ClientId discoveringClient = mCallbackMap.keyAt(i);
                IStateCallback callback = mCallbackMap.valueAt(i);
                writer.printf("%s%s, callback:%s\n", INDENTATION_4, discoveringClient, callback);
            }
            writer.printf("%smAppStateMap:\n", INDENTATION_2);
            for (int i = 0; i < mAppStateMap.size(); i++) {
                ClientId client = mAppStateMap.keyAt(i);
                @AppState int state = mAppStateMap.valueAt(i);
                writer.printf("%s%s, state:%s\n", INDENTATION_4, client, appStateToString(state));
            }
            writer.printf("%smOccupantZoneStateMap:\n", INDENTATION_2);
            for (int i = 0; i < mOccupantZoneStateMap.size(); i++) {
                OccupantZoneInfo occupantZone = mOccupantZoneStateMap.keyAt(i);
                @OccupantZoneState int state = mOccupantZoneStateMap.valueAt(i);
                writer.printf("%s%s, state:%s\n", INDENTATION_4, occupantZone,
                        occupantZoneStateToString(state));
            }
            writer.printf("%smPerUserInfoMap:\n", INDENTATION_2);
            for (int i = 0; i < mPerUserInfoMap.size(); i++) {
                int userId = mPerUserInfoMap.keyAt(i);
                PerUserInfo info = mPerUserInfoMap.valueAt(i);
                writer.printf("%suserId:%s, %s, %s, %s, %s\n", INDENTATION_4, userId, info.zone,
                        info.context, info.pm, info.receiver);
            }
        }
    }

    @Override
    public void registerStateCallback(String packageName, IStateCallback callback) {
        assertPermission(mContext, Car.PERMISSION_MANAGE_REMOTE_DEVICE);
        checkCalledByPackage(mContext, packageName);

        ClientId discoveringClient = getCallingClientId(packageName);
        synchronized (mLock) {
            assertNoDuplicateCallbackLock(discoveringClient);
            boolean firstDiscoverer = mCallbackMap.size() == 0;
            mCallbackMap.put(discoveringClient, callback);
            // Notify the discoverer of the latest states.
            updateAllOccupantZoneStateLocked(callback);
            updateAllAppStateWithPackageNameLocked(discoveringClient.packageName, callback);

            if (firstDiscoverer) {
                mSystemActivityMonitoringService.registerProcessObserverCallback(mProcessObserver);
            }
        }
    }

    @Override
    public void unregisterStateCallback(String packageName) {
        assertPermission(mContext, Car.PERMISSION_MANAGE_REMOTE_DEVICE);
        checkCalledByPackage(mContext, packageName);

        ClientId discoveringClient = getCallingClientId(packageName);
        synchronized (mLock) {
            assertHasCallbackLock(discoveringClient);
            mCallbackMap.remove(discoveringClient);
            if (mCallbackMap.size() == 0) {
                mSystemActivityMonitoringService.unregisterProcessObserverCallback(
                        mProcessObserver);
            }
            // If this discoverer is the last discoverer with the package name, remove the app state
            // of all the apps with the package name.
            if (!isDiscoveringLocked(packageName)) {
                clearAllAppStateWithPackageNameLocked(packageName);
            }
        }
    }

    @Override
    public PackageInfo getEndpointPackageInfo(int occupantZoneId, String packageName) {
        assertPermission(mContext, Car.PERMISSION_MANAGE_REMOTE_DEVICE);
        checkCalledByPackage(mContext, packageName);

        int userId = mOccupantZoneService.getUserForOccupant(occupantZoneId);
        if (userId == INVALID_USER_ID) {
            Slogf.e(TAG, "Failed to get PackageInfo of %s in occupant zone %d because it has no "
                    + "user assigned", packageName, occupantZoneId);
            return null;
        }
        synchronized (mLock) {
            return getPackageInfoAsUserLocked(packageName, userId, GET_SIGNING_CERTIFICATES);
        }
    }

    @Override
    public void setOccupantZonePower(OccupantZoneInfo occupantZone, boolean powerOn) {
        assertPermission(mContext, Car.PERMISSION_MANAGE_REMOTE_DEVICE);

        int[] displayIds = mOccupantZoneService.getAllDisplaysForOccupantZone(occupantZone.zoneId);
        for (int id : displayIds) {
            mPowerManagementService.setDisplayPowerState(id, powerOn);
        }
    }

    @Override
    public boolean isOccupantZonePowerOn(OccupantZoneInfo occupantZone) {
        assertPermission(mContext, Car.PERMISSION_MANAGE_REMOTE_DEVICE);

        return mOccupantZoneService.areDisplaysOnForOccupantZone(occupantZone.zoneId);
    }

    private void initAllOccupantZones() {
        List<OccupantZoneInfo> allOccupantZones = mOccupantZoneService.getAllOccupantZones();
        synchronized (mLock) {
            for (int i = 0; i < allOccupantZones.size(); i++) {
                OccupantZoneInfo occupantZone = allOccupantZones.get(i);
                @OccupantZoneState int initialState = calculateOccupantZoneState(occupantZone);
                Slogf.v(TAG, "The state of %s is initialized to %s", occupantZone,
                        occupantZoneStateToString(initialState));
                mOccupantZoneStateMap.put(occupantZone, initialState);
            }
        }
    }

    private void registerOccupantZoneCallback() {
        mOccupantZoneService.registerCallback(new ICarOccupantZoneCallback.Stub() {
            @Override
            public void onOccupantZoneConfigChanged(int flags) {
                if ((flags & ZONE_CONFIG_CHANGE_FLAG_USER) == 0) {
                    return;
                }
                Slogf.i(TAG, "User changed in occupant zones");
                synchronized (mLock) {
                    for (int i = 0; i < mOccupantZoneStateMap.size(); i++) {
                        OccupantZoneInfo occupantZone = mOccupantZoneStateMap.keyAt(i);
                        int oldUserId = getAssignedUserLocked(occupantZone);
                        int newUserId =
                                mOccupantZoneService.getUserForOccupant(occupantZone.zoneId);
                        Slogf.i(TAG, "In %s, old user was %d, new user is %d",
                                occupantZone, oldUserId, newUserId);
                        boolean hasOldUser = (oldUserId != INVALID_USER_ID);
                        boolean hasNewUser = isNonSystemUser(newUserId);

                        if ((!hasOldUser && !hasNewUser) || (oldUserId == newUserId)) {
                            // No secondary user change in this occupant zone, so do nothing.
                            continue;
                        }
                        if (hasOldUser && !hasNewUser) {
                            // The old user was unassigned.
                            handleUserUnassignedLocked(oldUserId, occupantZone);
                            continue;
                        }
                        if (!hasOldUser && hasNewUser) {
                            // The new user was assigned.
                            handleUserAssignedLocked(newUserId, occupantZone);
                            continue;
                        }
                        // The old user switched to a different new user.
                        handleUserSwitchedLocked(oldUserId, newUserId, occupantZone);
                    }
                }
            }
        });
    }

    private void handleProcessRunningStateChange(int uid, @ProcessRunningState int newState) {
        UserHandle userHandle = UserHandle.getUserHandleForUid(uid);
        if (userHandle.isSystem()) {
            if (DBG) {
                Slogf.v(TAG, "Skip ProcessRunningState change for process with uid %d because "
                        + "the process runs as system user", uid);
            }
            return;
        }
        synchronized (mLock) {
            String packageName = getUniquePackageNameByUidLocked(uid);
            if (packageName == null) {
                return;
            }
            Slogf.e(TAG, "%s 's running state changed to %d", packageName, newState);
            if (!isDiscoveringLocked(packageName)) {
                // There is no peer client discovering this app, so ignore its running state change
                // event.
                if (DBG) {
                    Slogf.v(TAG, "Skip ProcessRunningState change for %s because there is no peer "
                            + "client discovering this app", packageName);
                }
                return;
            }
            int userId = userHandle.getIdentifier();
            PerUserInfo userInfo = mPerUserInfoMap.get(userId);
            OccupantZoneInfo occupantZone;
            if (userInfo == null) {
                // This shouldn't happen, but let's try another way to get the occupant zone.
                Slogf.e(TAG, "No PerUserInfo for user %d", userId);
                occupantZone = mOccupantZoneService.getOccupantZoneForUser(userHandle);
                if (occupantZone == null) {
                    // This shouldn't happen. Let's log an error.
                    Slogf.e(TAG, "No occupant zone for user %d", userId);
                    return;
                }
            } else {
                occupantZone = userInfo.zone;
            }
            ClientId clientId = new ClientId(occupantZone, userId, packageName);
            @AppState int newAppState =
                    convertProcessRunningStateToAppStateLocked(packageName, userId, newState);
            setAppStateLocked(clientId, newAppState, /* callbackToNotify= */ null);
        }
    }

    private void initAssignedUsers() {
        synchronized (mLock) {
            for (int i = 0; i < mOccupantZoneStateMap.size(); i++) {
                OccupantZoneInfo occupantZone = mOccupantZoneStateMap.keyAt(i);
                int userId = mOccupantZoneService.getUserForOccupant(occupantZone.zoneId);
                Slogf.v(TAG, "User ID of %s is %d ", occupantZone, userId);
                if (!isNonSystemUser(userId)) {
                    continue;
                }
                initAssignedUserLocked(userId, occupantZone);
            }
        }
    }

    /**
     * Initializes PerUserInfo for the given user, and registers a PackageChangeReceiver for the
     * given user.
     */
    @GuardedBy("mLock")
    private void initAssignedUserLocked(int userId, OccupantZoneInfo occupantZone) {
        if (!isNonSystemUser(userId)) {
            Slogf.w(TAG, "%s is assigned to user %d", occupantZone, userId);
            return;
        }

        // Init PackageChangeReceiver.
        PackageChangeReceiver receiver = new PackageChangeReceiver(userId, occupantZone);
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addDataScheme("package");

        // Init user Context.
        Context userContext = mContext.createContextAsUser(UserHandle.of(userId), /* flags= */ 0);
        if (userContext == null) {
            Slogf.e(TAG, "Failed to create Context as user %d", userId);
            return;
        }
        Slogf.v(TAG, "registerReceiver() as user %d", userId);

        // Register PackageChangeReceiver.
        userContext.registerReceiver(receiver, filter);

        // Init PackageManager.
        PackageManager pm = userContext.getPackageManager();
        if (pm == null) {
            Slogf.e(TAG, "Failed to create PackageManager as user %d", userId);
            return;
        }

        PerUserInfo userInfo = new PerUserInfo(occupantZone, userContext, pm, receiver);
        mPerUserInfoMap.put(userId, userInfo);
    }

    /**
     * Removes PerUserInfo of the given user, and unregisters the PackageChangeReceiver for the
     * given user. This method is called when the given {@code userId} is unassigned.
     */
    @GuardedBy("mLock")
    private void removeUnassignedUserLocked(int userId) {
        PerUserInfo userInfo = mPerUserInfoMap.get(userId);
        if (userInfo == null) {
            Slogf.e(TAG, "Failed to find PerUserInfo of user %d", userId);
            return;
        }
        Slogf.v(TAG, "unregisterReceiver() as user %d", userId);
        userInfo.context.unregisterReceiver(userInfo.receiver);

        mPerUserInfoMap.remove(userId);
    }

    @GuardedBy("mLock")
    private void handleUserUnassignedLocked(int userId, OccupantZoneInfo occupantZone) {
        Slogf.v(TAG, "User %d was unassigned in %s", userId, occupantZone);
        removeUnassignedUserLocked(userId);
        updateOccupantZoneStateLocked(occupantZone, /* callbackToNotify= */ null);
        clearAllAppStateAsUserLocked(userId);
    }

    @GuardedBy("mLock")
    private void handleUserAssignedLocked(int userId, OccupantZoneInfo occupantZone) {
        Slogf.v(TAG, "User %d was assigned in %s", userId, occupantZone);
        initAssignedUserLocked(userId, occupantZone);
        updateOccupantZoneStateLocked(occupantZone, /* callbackToNotify= */ null);
        updateAllAppStateForNewUserLocked(userId, occupantZone);
    }

    @GuardedBy("mLock")
    private void handleUserSwitchedLocked(int oldUserId, int newUserId,
            OccupantZoneInfo occupantZone) {
        Slogf.v(TAG, "User %d was switched to %d in %s", oldUserId, newUserId, occupantZone);
        removeUnassignedUserLocked(oldUserId);
        clearAllAppStateAsUserLocked(oldUserId);

        initAssignedUserLocked(newUserId, occupantZone);
        updateAllAppStateForNewUserLocked(newUserId, occupantZone);

        updateOccupantZoneStateLocked(occupantZone, /* callbackToNotify= */ null);
    }

    private ClientId getCallingClientId(String packageName) {
        UserHandle callingUserHandle = Binder.getCallingUserHandle();
        int callingUserId = callingUserHandle.getIdentifier();
        OccupantZoneInfo occupantZone =
                mOccupantZoneService.getOccupantZoneForUser(callingUserHandle);
        // Note: the occupantZone is not null because the calling user must be a valid user.
        return new ClientId(occupantZone, callingUserId, packageName);
    }

    /**
     * Updates the states of all the occupant zones, notifies the newly registered callback
     * {@code callbackToNotify} of the latest state if it is not {@code null}, and notifies other
     * callbacks of the latest state if the state has changed.
     */
    @GuardedBy("mLock")
    private void updateAllOccupantZoneStateLocked(@Nullable IStateCallback callbackToNotify) {
        for (int i = 0; i < mOccupantZoneStateMap.size(); i++) {
            OccupantZoneInfo occupantZone = mOccupantZoneStateMap.keyAt(i);
            updateOccupantZoneStateLocked(occupantZone, callbackToNotify);
        }
    }

    /**
     * Updates the state of the given occupant zone, notifies the newly registered callback
     * {@code callbackToNotify} of the latest state if it is not {@code null}, and notifies other
     * callbacks of the latest state if the state has changed.
     */
    @GuardedBy("mLock")
    private void updateOccupantZoneStateLocked(OccupantZoneInfo occupantZone,
            @Nullable IStateCallback callbackToNotify) {
        @OccupantZoneState int oldState = mOccupantZoneStateMap.get(occupantZone);
        @OccupantZoneState int newState = calculateOccupantZoneState(occupantZone);
        boolean stateChanged = (oldState != newState);
        if (!stateChanged && callbackToNotify == null) {
            Slogf.v(TAG, "Skip updateOccupantZoneStateLocked() for %s because OccupantZoneState"
                    + " stays the same and there is no newly registered callback", occupantZone);
            return;
        }
        Slogf.v(TAG, "The state of %s is changed from %s to %s", occupantZone,
                occupantZoneStateToString(oldState), occupantZoneStateToString(newState));
        mOccupantZoneStateMap.put(occupantZone, newState);

        for (int i = 0; i < mCallbackMap.size(); i++) {
            ClientId discoveringClient = mCallbackMap.keyAt(i);
            // Don't notify discovering clients that are running in this occupant zone.
            if (discoveringClient.occupantZone.equals(occupantZone)) {
                continue;
            }
            IStateCallback callback = mCallbackMap.valueAt(i);
            // If the callback is newly registered, invoke it anyway. Otherwise, invoke it only
            // when the state has changed
            if ((callback == callbackToNotify) || stateChanged) {
                try {
                    callback.onOccupantZoneStateChanged(occupantZone, newState);
                } catch (RemoteException e) {
                    Slogf.e(TAG, "Failed to notify %s of OccupantZoneState change",
                            discoveringClient);
                }
            }
        }
    }

    @VisibleForTesting
    @OccupantZoneState
    int calculateOccupantZoneState(OccupantZoneInfo occupantZone) {
        @OccupantZoneState int occupantZoneState = INITIAL_OCCUPANT_ZONE_STATE;
        if (isPowerOn(occupantZone)) {
            occupantZoneState |= FLAG_OCCUPANT_ZONE_POWER_ON;
            if (isScreenUnlocked(occupantZone)) {
                occupantZoneState |= FLAG_OCCUPANT_ZONE_SCREEN_UNLOCKED;
            }
            if (isConnectionReady(occupantZone)) {
                occupantZoneState |= FLAG_OCCUPANT_ZONE_CONNECTION_READY;
            }
        }
        return occupantZoneState;
    }

    private boolean isPowerOn(OccupantZoneInfo occupantZone) {
        return mOccupantZoneService.areDisplaysOnForOccupantZone(occupantZone.zoneId);
    }

    private boolean isScreenUnlocked(OccupantZoneInfo occupantZone) {
        // TODO(b/257117236): implement this method.
        return false;
    }

    /**
     * Returns {@code true} if the given {@code occupantZone} is ready to handle connection request.
     * Returns {@code false} otherwise.
     * <p>
     * If the {@code occupantZone} is on the same SoC as the caller occupant zone, connection ready
     * means the user is ready. If the {@code occupantZone} is on another SoC, connection ready
     * means user ready and internet connection from the caller occupant zone to the {@code
     * occupantZone} is good. User ready means the user has been allocated to the occupant zone,
     * is actively running, and is unlocked.
     */
    // TODO(b/257118327): support multi-SoC.
    private boolean isConnectionReady(OccupantZoneInfo occupantZone) {
        int userId = mOccupantZoneService.getUserForOccupant(occupantZone.zoneId);
        Slogf.v(TAG, "User ID of %s is %d now", occupantZone, userId);
        if (!isNonSystemUser(userId)) {
            return false;
        }
        UserHandle userHandle = UserHandle.of(userId);
        return mUserManager.isUserRunning(userHandle) && mUserManager.isUserUnlocked(userHandle);
    }

    /**
     * Updates the {@link AppState} of all the apps with the given {@code packageName}, notifies
     * the newly registered callback {@code callbackToNotify} of the latest state, and notifies
     * other callbacks of the latest state if the state has changed.
     */
    @GuardedBy("mLock")
    private void updateAllAppStateWithPackageNameLocked(String packageName,
            IStateCallback callbackToNotify) {
        for (int i = 0; i < mPerUserInfoMap.size(); i++) {
            int userId = mPerUserInfoMap.keyAt(i);
            OccupantZoneInfo occupantZone = mPerUserInfoMap.valueAt(i).zone;
            ClientId discoveredClient = new ClientId(occupantZone, userId, packageName);
            @AppState int newState = calculateAppStateLocked(discoveredClient);
            setAppStateLocked(discoveredClient, newState, callbackToNotify);
        }
    }

    /**
     * Updates the {@link AppState} of all the clients that run as {@code userId}, and notifies
     * the discoverers of the state change. This method is invoked when a new user is assigned to
     * the given occupant zone.
     */
    @GuardedBy("mLock")
    private void updateAllAppStateForNewUserLocked(int userId, OccupantZoneInfo occupantZone) {
        Set<String> updatedApps = new ArraySet<>();
        for (int i = 0; i < mCallbackMap.size(); i++) {
            ClientId discoveringClient = mCallbackMap.keyAt(i);
            // For a given package name, there might be several discoverers (peer clients that have
            // registered a callback), but we only need to update the state of the changed client
            // once.
            if (updatedApps.contains(discoveringClient.packageName)) {
                continue;
            }
            updatedApps.add(discoveringClient.packageName);

            ClientId clientId = new ClientId(occupantZone, userId, discoveringClient.packageName);
            @AppState int newAppState = calculateAppStateLocked(clientId);
            setAppStateLocked(clientId, newAppState, /* callbackToNotify= */ null);
        }
    }

    /**
     * Clears the {@link AppState} of all the apps that run as {@code userId}.
     * This method is called when the given {@code userId} is unassigned for the occupantZone,
     * for which the discoverers are already notified, so there is no need to notify the discoverers
     * in this method.
     */
    @GuardedBy("mLock")
    private void clearAllAppStateAsUserLocked(int userId) {
        for (int i = 0; i < mAppStateMap.size(); i++) {
            ClientId clientId = mAppStateMap.keyAt(i);
            if (clientId.userId == userId) {
                mAppStateMap.removeAt(i);
            }
        }
    }

    /**
     * Clears the {@link AppState} of all the apps that have the given {@code packageName}.
     * This method is called when the last discoverer with the package name is unregistered , so
     * there is no need to notify the discoverers in this method.
     */
    @GuardedBy("mLock")
    private void clearAllAppStateWithPackageNameLocked(String packageName) {
        for (int i = 0; i < mAppStateMap.size(); i++) {
            ClientId clientId = mAppStateMap.keyAt(i);
            if (clientId.packageName.equals(packageName)) {
                mAppStateMap.removeAt(i);
            }
        }
    }

    @GuardedBy("mLock")
    private PackageManager getPackageManagerAsUserLocked(int userId) {
        PerUserInfo userInfo = mPerUserInfoMap.get(userId);
        if (userInfo == null) {
            Slogf.e(TAG, "Failed to get PackageManager as user %d because the user is not"
                    + " assigned to an occupant zone yet", userId);
            return null;
        }
        return userInfo.pm;
    }

    @GuardedBy("mLock")
    private void assertNoDuplicateCallbackLock(ClientId discoveredClient) {
        if (mCallbackMap.containsKey(discoveredClient)) {
            throw new IllegalStateException("The client already registered a StateCallback: "
                    + discoveredClient);
        }
    }

    @GuardedBy("mLock")
    private void assertHasCallbackLock(ClientId discoveredClient) {
        if (!mCallbackMap.containsKey(discoveredClient)) {
            throw new IllegalStateException("The client has no StateCallback registered: "
                    + discoveredClient);
        }
    }

    /**
     * Returns {@code true} if there is a client with the {@code packageName} has registered an
     * {@link IStateCallback}.
     */
    @GuardedBy("mLock")
    private boolean isDiscoveringLocked(String packageName) {
        for (int i = 0; i < mCallbackMap.size(); i++) {
            ClientId discoveringClient = mCallbackMap.keyAt(i);
            if (discoveringClient.packageName.equals(packageName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Sets the {@link AppState} of the given client, notifies the newly registered callback
     * {@code callbackToNotify} of the latest state if it is not {@code null}, and notifies other
     * peer discoverers of the latest state if the state has changed.
     */
    @GuardedBy("mLock")
    private void setAppStateLocked(ClientId discoveredClient, @AppState int newState,
            @Nullable IStateCallback callbackToNotify) {
        Integer oldAppState = mAppStateMap.get(discoveredClient);
        boolean stateChanged = (oldAppState == null || oldAppState.intValue() != newState);
        if (!stateChanged && callbackToNotify == null) {
            Slogf.v(TAG, "Skip setAppStateLocked() because AppState stays the same and there"
                    + " is no newly registered callback");
            return;
        }
        Slogf.v(TAG, "The app state of %s is set from %s to %s", discoveredClient,
                oldAppState == null ? "null" : appStateToString(oldAppState),
                appStateToString(newState));
        mAppStateMap.put(discoveredClient, newState);

        // Notify its peer clients that are discovering.
        for (int i = 0; i < mCallbackMap.size(); i++) {
            ClientId discoveringClient = mCallbackMap.keyAt(i);
            // A peer client is a client that has the same package name but runs as another user.
            // If it is not a peer client, skip it.
            if (!discoveringClient.packageName.equals(discoveredClient.packageName)
                    || discoveringClient.userId == discoveredClient.userId) {
                continue;
            }
            IStateCallback callback = mCallbackMap.valueAt(i);
            // If the callback is newly registered, invoke it anyway. Otherwise, invoke it only
            // when the state has changed
            if (callback == callbackToNotify || stateChanged) {
                try {
                    callback.onAppStateChanged(discoveredClient.occupantZone, newState);
                } catch (RemoteException e) {
                    Slogf.e(TAG, "Failed to notify %d of AppState change", discoveringClient);
                }
            }
        }
    }

    @GuardedBy("mLock")
    @AppState
    private int calculateAppStateLocked(ClientId clientId) {
        @AppState int appState = INITIAL_APP_STATE;
        if (isAppInstalledAsUserLocked(clientId.packageName, clientId.userId)) {
            appState |= FLAG_CLIENT_INSTALLED;
            // In single-SoC model, the peer client is guaranteed to have the same
            // signing info and long version code.
            // TODO(b/257118327): support multiple-SoC.
            appState |= FLAG_CLIENT_SAME_VERSION | FLAG_CLIENT_SAME_SIGNATURE;

            RunningAppProcessInfo info =
                    getRunningAppProcessInfoAsUserLocked(clientId.packageName, clientId.userId);
            if (isAppRunning(info)) {
                appState |= FLAG_CLIENT_RUNNING;
                if (isAppRunningInForeground(info)) {
                    appState |= FLAG_CLIENT_IN_FOREGROUND;
                }
            }
        }
        return appState;
    }

    @GuardedBy("mLock")
    private int getAssignedUserLocked(OccupantZoneInfo occupantZone) {
        for (int i = 0; i < mPerUserInfoMap.size(); i++) {
            if (occupantZone.equals(mPerUserInfoMap.valueAt(i).zone)) {
                return mPerUserInfoMap.keyAt(i);
            }
        }
        return INVALID_USER_ID;
    }

    /**
     * This method is an unlocked version of {@link #calculateAppStateLocked} and is used for
     * testing only.
     */
    @AppState
    @VisibleForTesting
    int calculateAppState(ClientId clientId) {
        synchronized (mLock) {
            return calculateAppStateLocked(clientId);
        }
    }

    @GuardedBy("mLock")
    private boolean isAppInstalledAsUserLocked(String packageName, int userId) {
        return getPackageInfoAsUserLocked(packageName, userId, /* flags= */ 0) != null;
    }

    @GuardedBy("mLock")
    private PackageInfo getPackageInfoAsUserLocked(String packageName, int userId, int flags) {
        PackageManager pm = getPackageManagerAsUserLocked(userId);
        if (pm == null) {
            return null;
        }
        try {
            return pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags));
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    @GuardedBy("mLock")
    private RunningAppProcessInfo getRunningAppProcessInfoAsUserLocked(String packageName,
            int userId) {
        List<RunningAppProcessInfo> infos = mActivityManager.getRunningAppProcesses();
        if (infos == null) {
            return null;
        }
        for (int i = 0; i < infos.size(); i++) {
            RunningAppProcessInfo processInfo = infos.get(i);
            if (processInfo.processName.equals(packageName)) {
                UserHandle processUserHandle = UserHandle.getUserHandleForUid(processInfo.uid);
                if (processUserHandle.getIdentifier() == userId) {
                    return processInfo;
                }
            }
        }
        return null;
    }

    @GuardedBy("mLock")
    private String getUniquePackageNameByUidLocked(int uid) {
        int userId = UserHandle.getUserHandleForUid(uid).getIdentifier();
        PerUserInfo userInfo = mPerUserInfoMap.get(userId);
        if (userInfo == null) {
            // This should never happen, but let's be cautious and log an error.
            Slogf.wtf(TAG, "No PerUserInfo for user %d", userId);
            return null;
        }
        String[] packageNames = userInfo.pm.getPackagesForUid(uid);
        if (packageNames == null) {
            return null;
        }
        if (packageNames.length == 1) {
            return packageNames[0];
        }
        // packageNames.length can't be 0.
        // Multiple package names means multiple apps share the same user ID through the
        // "sharedUserId" mechanism. However, "sharedUserId" mechanism is deprecated in
        // API level 29, so let's log an error.
        Slogf.i(TAG, "Failed to get the package name by uid! Apps shouldn't use sharedUserId"
                + " because it's deprecated in API level 29: %s", Arrays.toString(packageNames));
        return null;
    }

    @GuardedBy("mLock")
    @AppState
    private int convertProcessRunningStateToAppStateLocked(String packageName, int userId,
            @ProcessRunningState int state) {
        // Note: In single-SoC model, the peer client is guaranteed to have the same
        // signing info and long version code.
        // TODO(b/257118327): support multiple-SoC.
        switch (state) {
            case PROCESS_RUNNING_IN_BACKGROUND:
                return FLAG_CLIENT_INSTALLED | FLAG_CLIENT_SAME_VERSION | FLAG_CLIENT_SAME_SIGNATURE
                        | FLAG_CLIENT_RUNNING;
            case PROCESS_RUNNING_IN_FOREGROUND:
                return FLAG_CLIENT_INSTALLED | FLAG_CLIENT_SAME_VERSION | FLAG_CLIENT_SAME_SIGNATURE
                        | FLAG_CLIENT_RUNNING | FLAG_CLIENT_IN_FOREGROUND;
            case PROCESS_NOT_RUNNING:
                return isAppInstalledAsUserLocked(packageName, userId)
                        ? FLAG_CLIENT_INSTALLED | FLAG_CLIENT_SAME_VERSION
                        | FLAG_CLIENT_SAME_SIGNATURE
                        : INITIAL_APP_STATE;

        }
        throw new IllegalArgumentException("Undefined ProcessRunningState: " + state);
    }

    private static boolean isAppRunning(RunningAppProcessInfo info) {
        return info != null;
    }

    private static boolean isAppRunningInForeground(RunningAppProcessInfo info) {
        return info != null && info.importance == IMPORTANCE_FOREGROUND;
    }

    /** Returns {@code true} if the given user is a valid user and is not the system user. */
    private static boolean isNonSystemUser(int userId) {
        return userId != INVALID_USER_ID && !UserHandle.of(userId).isSystem();
    }

    @ExcludeFromCodeCoverageGeneratedReport(reason = DEBUGGING_CODE)
    private static String occupantZoneStateToString(@OccupantZoneState int state) {
        boolean powerOn = (state & FLAG_OCCUPANT_ZONE_POWER_ON) != 0;
        boolean screenUnlocked = (state & FLAG_OCCUPANT_ZONE_SCREEN_UNLOCKED) != 0;
        boolean connectionReady = (state & FLAG_OCCUPANT_ZONE_CONNECTION_READY) != 0;
        return new StringBuilder(64)
                .append("[")
                .append(powerOn ? "on, " : "off, ")
                .append(screenUnlocked ? "unlocked, " : "locked, ")
                .append(connectionReady ? "ready" : "not-ready")
                .append("]")
                .toString();

    }

    @ExcludeFromCodeCoverageGeneratedReport(reason = DEBUGGING_CODE)
    private static String appStateToString(@AppState int state) {
        boolean installed = (state & FLAG_CLIENT_INSTALLED) != 0;
        boolean sameVersion = (state & FLAG_CLIENT_SAME_VERSION) != 0;
        boolean sameSignature = (state & FLAG_CLIENT_SAME_SIGNATURE) != 0;
        boolean running = (state & FLAG_CLIENT_RUNNING) != 0;
        boolean inForeground = (state & FLAG_CLIENT_IN_FOREGROUND) != 0;
        return new StringBuilder(64)
                .append("[")
                .append(installed ? "installed, " : "not-installed, ")
                .append(sameVersion ? "same-versio, " : "different-version, ")
                .append(sameSignature ? "same-signature, " : "different-signature, ")
                .append(!running
                        ? "not-running"
                        : (inForeground ? "foreground" : "background"))
                .append("]")
                .toString();
    }
}
