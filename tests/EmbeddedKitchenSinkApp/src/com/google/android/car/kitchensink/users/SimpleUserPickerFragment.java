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
package com.google.android.car.kitchensink.users;

import android.annotation.Nullable;
import android.app.ActivityManager;
import android.app.IActivityManager;
import android.car.Car;
import android.car.CarOccupantZoneManager;
import android.car.CarOccupantZoneManager.OccupantZoneInfo;
import android.car.user.CarUserManager;
import android.car.user.UserCreationResult;
import android.car.util.concurrent.AsyncFuture;
import android.content.Context;
import android.content.pm.UserInfo;
import android.graphics.Color;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.os.Process;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.UserManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.Display;
import android.view.DisplayAddress;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import com.google.android.car.kitchensink.R;
import com.google.android.car.kitchensink.UserPickerActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class SimpleUserPickerFragment extends Fragment {

    private static final String TAG = SimpleUserPickerFragment.class.getSimpleName();

    private static final int ERROR_MESSAGE = 0;
    private static final int WARN_MESSAGE = 1;
    private static final int INFO_MESSAGE = 2;

    private static final long TIMEOUT_MS = 5_000;

    private SpinnerWrapper mUsersSpinner;
    private SpinnerWrapper mDisplaysSpinner;

    private Button mStartUserButton;
    private Button mStopUserButton;
    private Button mSwitchUserButton;
    private Button mCreateUserButton;

    private TextView mDisplayIdText;
    private TextView mUserOnDisplayText;
    private TextView mUserIdText;
    private TextView mZoneInfoText;
    private TextView mStatusMessageText;
    private EditText mNewUserNameText;

    private ActivityManager mActivityManager;
    private UserManager mUserManager;
    private DisplayManager mDisplayManager;
    private CarOccupantZoneManager mZoneManager;
    private CarUserManager mCarUserManager;

    // The logical display to which the view's window has been attached.
    private Display mDisplayAttached;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        return inflater.inflate(R.layout.simple_user_picker, container, false);
    }

    public void onViewCreated(View view, Bundle savedInstanceState) {
        mActivityManager = getContext().getSystemService(ActivityManager.class);
        mUserManager = getContext().getSystemService(UserManager.class);
        mDisplayManager = getContext().getSystemService(DisplayManager.class);

        Car car = ((UserPickerActivity) getHost()).getCar();
        mZoneManager = car.getCarManager(CarOccupantZoneManager.class);
        mZoneManager.registerOccupantZoneConfigChangeListener(
                new UserAssignmentChangeListener());

        mCarUserManager = car.getCarManager(CarUserManager.class);

        mDisplayAttached = getContext().getDisplay();
        int driverDisplayId = mZoneManager.getDisplayIdForDriver(
                CarOccupantZoneManager.DISPLAY_TYPE_MAIN);
        Log.i(TAG, "driver display id: " + driverDisplayId);
        boolean isPassengerView = mDisplayAttached != null
                && mDisplayAttached.getDisplayId() != driverDisplayId;

        mDisplayIdText = view.findViewById(R.id.textView_display_id);
        mUserOnDisplayText = view.findViewById(R.id.textView_user_on_display);
        mUserIdText = view.findViewById(R.id.textView_state);
        mZoneInfoText = view.findViewById(R.id.textView_zoneinfo);
        updateTextInfo();

        mNewUserNameText = view.findViewById(R.id.new_user_name);

        mUsersSpinner = SpinnerWrapper.create(getContext(),
                view.findViewById(R.id.spinner_users), getUnassignedUsers());
        mDisplaysSpinner = SpinnerWrapper.create(getContext(),
                view.findViewById(R.id.spinner_displays), getDisplays());
        if (isPassengerView) {
            view.findViewById(R.id.textView_displays).setVisibility(View.GONE);
            view.findViewById(R.id.spinner_displays).setVisibility(View.GONE);
        }

        mStartUserButton = view.findViewById(R.id.button_start_user);
        mStartUserButton.setOnClickListener(v -> startUser());
        if (isPassengerView) {
            mStartUserButton.setVisibility(View.GONE);
        }

        mStopUserButton = view.findViewById(R.id.button_stop_user);
        mStopUserButton.setOnClickListener(v -> stopUser());
        if (!isPassengerView) {
            mStopUserButton.setVisibility(View.GONE);
        }

        mSwitchUserButton = view.findViewById(R.id.button_switch_user);
        mSwitchUserButton.setOnClickListener(v -> switchUser());
        if (!isPassengerView) {
            mSwitchUserButton.setVisibility(View.GONE);
        }

        mCreateUserButton = view.findViewById(R.id.button_create_user);
        mCreateUserButton.setOnClickListener(v -> createUser());

        mStatusMessageText = view.findViewById(R.id.status_message_text_view);
    }

    private final class UserAssignmentChangeListener implements
            CarOccupantZoneManager.OccupantZoneConfigChangeListener {
        @Override
        public void onOccupantZoneConfigChanged(int changeFlags) {
            Log.d(TAG, "onOccupantZoneConfigChanged changeFlags=" + changeFlags);
            if ((changeFlags & CarOccupantZoneManager.ZONE_CONFIG_CHANGE_FLAG_USER) == 0) {
                return;
            }

            mUsersSpinner.updateEntries(getUnassignedUsers());
            updateTextInfo();
        }
    }

    private void updateTextInfo() {
        int displayId = mDisplayAttached.getDisplayId();
        OccupantZoneInfo zoneInfo = getOccupantZoneForDisplayId(displayId);
        int userId = mZoneManager.getUserForOccupant(zoneInfo);
        mDisplayIdText.setText("DisplayId: " + displayId + " ZoneId: " + zoneInfo.zoneId);
        String userString = userId == CarOccupantZoneManager.INVALID_USER_ID
                ? "unassigned" : Integer.toString(userId);
        mUserOnDisplayText.setText("User on display: " + userString);

        int currentUserId = ActivityManager.getCurrentUser();
        int myUserId = UserHandle.myUserId();
        mUserIdText.setText("Current userId: " + currentUserId + " myUserId:" + myUserId);
        StringBuilder zoneStateBuilder = new StringBuilder();
        zoneStateBuilder.append("Zone-User-Displays: ");
        List<CarOccupantZoneManager.OccupantZoneInfo> zonelist = mZoneManager.getAllOccupantZones();
        for (CarOccupantZoneManager.OccupantZoneInfo zone : zonelist) {
            zoneStateBuilder.append(zone.zoneId);
            zoneStateBuilder.append("-");
            int user = mZoneManager.getUserForOccupant(zone);
            if (user == UserHandle.USER_NULL) {
                zoneStateBuilder.append("unassigned");
            } else {
                zoneStateBuilder.append(user);
            }
            zoneStateBuilder.append("-");
            List<Display> displays = mZoneManager.getAllDisplaysForOccupant(zone);
            for (Display display : displays) {
                zoneStateBuilder.append(display.getDisplayId());
                zoneStateBuilder.append(",");
            }
            zoneStateBuilder.append(":");
        }
        mZoneInfoText.setText(zoneStateBuilder.toString());
    }

    // startUser starts a selected user on a selected secondary display.
    private void startUser() {
        int userId = getSelectedUser();
        if (userId == UserHandle.USER_NULL) {
            return;
        }

        int displayId = getSelectedDisplay();
        if (displayId == Display.INVALID_DISPLAY) {
            return;
        }

        // Start the user on display.
        Log.i(TAG, "start user: " + userId + " in background on secondary display " + displayId);
        boolean started = mActivityManager.startUserInBackgroundOnSecondaryDisplay(
                userId, displayId);
        if (!started) {
            setMessage(ERROR_MESSAGE, "Cannot start user " + userId + " on display " + displayId);
            return;
        }

        setMessage(INFO_MESSAGE,
                "Started user " + userId + " on display " + displayId);
        mUsersSpinner.updateEntries(getUnassignedUsers());
        updateTextInfo();
    }

    // stopUser stops the visible user on this secondary display.
    private void stopUser() {
        if (mDisplayAttached == null) {
            setMessage(ERROR_MESSAGE,
                    "Cannot obtain the display attached to the view to get occupant zone");
            return;
        }

        int displayId = mDisplayAttached.getDisplayId();
        OccupantZoneInfo zoneInfo = getOccupantZoneForDisplayId(displayId);
        if (zoneInfo == null) {
            setMessage(ERROR_MESSAGE,
                    "Cannot find occupant zone info associated with display " + displayId);
            return;
        }

        int userId = mZoneManager.getUserForOccupant(zoneInfo);
        if (userId == CarOccupantZoneManager.INVALID_USER_ID) {
            setMessage(ERROR_MESSAGE,
                    "Cannot find the user assigned to the occupant zone " + zoneInfo.zoneId);
            return;
        }

        int currentUser = ActivityManager.getCurrentUser();
        if (userId == currentUser) {
            setMessage(WARN_MESSAGE, "Can not change current user");
            return;
        }

        if (!mUserManager.isUserRunning(userId)) {
            setMessage(WARN_MESSAGE, "User " + userId + " is already stopped");
            return;
        }

        // Unassign the user from the occupant zone.
        // TODO(b/253264316): See if we can move it to CarUserService.
        int result = mZoneManager.unassignOccupantZone(zoneInfo);
        if (result != CarOccupantZoneManager.USER_ASSIGNMENT_RESULT_OK) {
            setMessage(ERROR_MESSAGE, "failed to unassign user " + userId + " from occupant zone "
                            + zoneInfo.zoneId);
            return;
        }

        IActivityManager am = ActivityManager.getService();
        Log.i(TAG, "stop user:" + userId);
        try {
            // Use stopUserWithDelayedLocking instead of stopUser to make the call more efficient.
            am.stopUserWithDelayedLocking(userId, /* force= */ false, /* callback= */ null);
        } catch (RemoteException e) {
            setMessage(ERROR_MESSAGE, "Cannot stop user " + userId, e);
            return;
        }

        setMessage(INFO_MESSAGE, "Stopped user " + userId);
        mUsersSpinner.updateEntries(getUnassignedUsers());
        updateTextInfo();
    }

    private void switchUser() {
        // Pick an unassigned user to switch to on this display.
        int userId = getSelectedUser();
        if (userId == UserHandle.USER_NULL) {
            setMessage(ERROR_MESSAGE, "Invalid user");
            return;
        }

        if (mDisplayAttached == null) {
            setMessage(ERROR_MESSAGE,
                    "Cannot obtain the display attached to the view to get occupant zone");
            return;
        }

        int displayId = mDisplayAttached.getDisplayId();

        Log.i(TAG, "start user: " + userId + " in background on secondary display: " + displayId);
        boolean started = mActivityManager.startUserInBackgroundOnSecondaryDisplay(
                userId, displayId);
        if (!started) {
            setMessage(ERROR_MESSAGE,
                    "Cannot start user " + userId + " on secondary display " + displayId);
            return;
        }

        setMessage(INFO_MESSAGE, "Switched to user " + userId + " on display " + displayId);
        mUsersSpinner.updateEntries(getUnassignedUsers());
        updateTextInfo();
    }

    private void createUser() {
        String name = mNewUserNameText.getText().toString();
        if (TextUtils.isEmpty(name)) {
            setMessage(ERROR_MESSAGE, "Cannot create user without a name");
            return;
        }

        AsyncFuture<UserCreationResult> future = mCarUserManager.createUser(name, /* flags= */ 0);
        setMessage(INFO_MESSAGE, "Creating full secondary user with name " + name + " ...");

        UserCreationResult result = null;
        try {
            result = future.get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (result == null) {
                Log.e(TAG, "Timed out creating user after " + TIMEOUT_MS + "ms...");
                setMessage(ERROR_MESSAGE, "Timed out creating user after " + TIMEOUT_MS + "ms...");
                return;
            }
        } catch (InterruptedException e) {
            Log.e(TAG, "Interrupted waiting for future " + future, e);
            Thread.currentThread().interrupt();
            setMessage(ERROR_MESSAGE, "Interrupted while creating user");
            return;
        } catch (Exception e) {
            Log.e(TAG, "Exception getting future " + future, e);
            setMessage(ERROR_MESSAGE, "Encountered Exception while creating user " + name);
            return;
        }

        StringBuilder message = new StringBuilder();
        if (result.isSuccess()) {
            message.append("User created: ").append(result.getUser().toString());
            setMessage(INFO_MESSAGE, message.toString());
            mUsersSpinner.updateEntries(getUnassignedUsers());
        } else {
            int status = result.getStatus();
            message.append("Failed with code ").append(status).append('(')
                    .append(UserCreationResult.statusToString(status)).append(')');
            message.append("\nFull result: ").append(result);
            String error = result.getErrorMessage();
            if (error != null) {
                message.append("\nError message: ").append(error);
            }
            setMessage(ERROR_MESSAGE, message.toString());
        }
    }

    // TODO(b/248608281): Use API from CarOccupantZoneManager for convenience.
    @Nullable
    private OccupantZoneInfo getOccupantZoneForDisplayId(int displayId) {
        List<OccupantZoneInfo> occupantZoneInfos = mZoneManager.getAllOccupantZones();
        for (int index = 0; index < occupantZoneInfos.size(); index++) {
            OccupantZoneInfo occupantZoneInfo = occupantZoneInfos.get(index);
            List<Display> displays = mZoneManager.getAllDisplaysForOccupant(
                    occupantZoneInfo);
            for (int displayIndex = 0; displayIndex < displays.size(); displayIndex++) {
                if (displays.get(displayIndex).getDisplayId() == displayId) {
                    return occupantZoneInfo;
                }
            }
        }
        return null;
    }

    private void setMessage(int messageType, String title, Exception e) {
        StringBuilder messageTextBuilder = new StringBuilder()
                .append(title)
                .append(": ")
                .append(e.getMessage());
        setMessage(messageType, messageTextBuilder.toString());
    }

    private void setMessage(int messageType, String message) {
        int textColor;
        switch (messageType) {
            case ERROR_MESSAGE:
                Log.e(TAG, message);
                textColor = Color.RED;
                break;
            case WARN_MESSAGE:
                Log.w(TAG, message);
                textColor = Color.YELLOW;
                break;
            case INFO_MESSAGE:
            default:
                Log.i(TAG, message);
                textColor = Color.GREEN;
        }
        mStatusMessageText.setTextColor(textColor);
        mStatusMessageText.setText(message);
    }

    private int getSelectedDisplay() {
        String displayStr = mDisplaysSpinner.getSelectedEntry();
        if (displayStr == null) {
            Log.w(TAG, "getSelectedDisplay, no display selected", new RuntimeException());
            return Display.INVALID_DISPLAY;
        }
        return Integer.parseInt(displayStr.split(",")[0]);
    }

    private int getSelectedUser() {
        String userStr = mUsersSpinner.getSelectedEntry();
        if (userStr == null) {
            Log.w(TAG, "getSelectedUser, user not selected", new RuntimeException());
            return UserHandle.USER_NULL;
        }
        return Integer.parseInt(userStr.split(",")[0]);
    }

    // format: id,type
    private ArrayList<String> getUnassignedUsers() {
        ArrayList<String> users = new ArrayList<>();
        List<UserInfo> aliveUsers = mUserManager.getAliveUsers();
        List<UserHandle> visibleUsers = mUserManager.getVisibleUsers();
        // Exclude visible users and only show unassigned users.
        for (int i = 0; i < aliveUsers.size(); ++i) {
            UserInfo u = aliveUsers.get(i);
            if (!u.userType.equals(UserManager.USER_TYPE_FULL_SECONDARY)) {
                continue;
            }

            if (!isIncluded(u.id, visibleUsers)) {
                users.add(Integer.toString(u.id) + "," + u.name);
            }
        }

        return users;
    }

    // format: displayId,[P,]?,address]
    private ArrayList<String> getDisplays() {
        ArrayList<String> displays = new ArrayList<>();
        Display[] disps = mDisplayManager.getDisplays();
        int uidSelf = Process.myUid();
        for (Display disp : disps) {
            if (!disp.hasAccess(uidSelf)) {
                continue;
            }
            StringBuilder builder = new StringBuilder()
                    .append(disp.getDisplayId())
                    .append(",");
            DisplayAddress address = disp.getAddress();
            if (address instanceof  DisplayAddress.Physical) {
                builder.append("P,");
            }
            builder.append(address);
            displays.add(builder.toString());
        }
        return displays;
    }

    private static boolean isIncluded(int userId, List<UserHandle> visibleUsers) {
        for (int i = 0; i < visibleUsers.size(); ++i) {
            if (userId == visibleUsers.get(i).getIdentifier()) {
                return true;
            }
        }
        return false;
    }

    private static final class SpinnerWrapper {
        private final Spinner mSpinner;
        private final ArrayList<String> mEntries;
        private final ArrayAdapter<String> mAdapter;

        private static SpinnerWrapper create(Context context, Spinner spinner,
                ArrayList<String> entries) {
            SpinnerWrapper wrapper = new SpinnerWrapper(context, spinner, entries);
            wrapper.init();
            return wrapper;
        }

        private SpinnerWrapper(Context context, Spinner spinner, ArrayList<String> entries) {
            mSpinner = spinner;
            mEntries = new ArrayList<>(entries);
            mAdapter = new ArrayAdapter<String>(context, android.R.layout.simple_spinner_item,
                    mEntries);
        }

        private void init() {
            mAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            mSpinner.setAdapter(mAdapter);
        }

        private void updateEntries(ArrayList<String> entries) {
            mEntries.clear();
            mEntries.addAll(entries);
            mAdapter.notifyDataSetChanged();
        }

        @Nullable
        private String getSelectedEntry() {
            return (String) mSpinner.getSelectedItem();
        }
    }
}
