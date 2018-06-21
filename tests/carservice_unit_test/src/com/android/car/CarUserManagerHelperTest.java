/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.app.ActivityManager;
import android.car.user.CarUserManagerHelper;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.UserInfo;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.UserManager;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * This class contains unit tests for the {@link CarUserManagerHelper}.
 * It tests that {@link CarUserManagerHelper} does the right thing for user management flows.
 *
 * The following mocks are used:
 * 1. {@link Context} provides system services and resources.
 * 2. {@link UserManager} provides dummy users and user info.
 * 3. {@link ActivityManager} to verify user switch is invoked.
 * 4. {@link CarUserManagerHelper.OnUsersUpdateListener} registers a listener for user updates.
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class CarUserManagerHelperTest {
    @Mock
    private Context mContext;
    @Mock
    private UserManager mUserManager;
    @Mock
    private ActivityManager mActivityManager;
    @Mock
    private CarUserManagerHelper.OnUsersUpdateListener mTestListener;

    private CarUserManagerHelper mCarUserManagerHelper;
    private UserInfo mCurrentProcessUser;
    private UserInfo mSystemUser;
    private String mGuestUserName = "testGuest";
    private String mTestUserName = "testUser";
    private int mForegroundUserId;
    private UserInfo mForegroundUser;

    @Before
    public void setUpMocksAndVariables() throws Exception {
        MockitoAnnotations.initMocks(this);
        doReturn(mUserManager).when(mContext).getSystemService(Context.USER_SERVICE);
        doReturn(mActivityManager).when(mContext).getSystemService(Context.ACTIVITY_SERVICE);
        doReturn(InstrumentationRegistry.getTargetContext().getResources())
                .when(mContext).getResources();
        doReturn(mContext).when(mContext).getApplicationContext();
        mCarUserManagerHelper = new CarUserManagerHelper(mContext);

        mCurrentProcessUser = createUserInfoForId(UserHandle.myUserId());
        mSystemUser = createUserInfoForId(UserHandle.USER_SYSTEM);
        doReturn(mCurrentProcessUser).when(mUserManager).getUserInfo(UserHandle.myUserId());

        // Get the ID of the foreground user running this test.
        // We cannot mock the foreground user since getCurrentUser is static.
        // We cannot rely on foreground_id != system_id, they could be the same user.
        mForegroundUserId = ActivityManager.getCurrentUser();
        mForegroundUser = createUserInfoForId(mForegroundUserId);

        // Restore the non-headless state before every test. Individual tests can set the property
        // to true to test the headless system user scenario.
        SystemProperties.set("android.car.systemuser.headless", "false");
    }

    @Test
    public void checkIsSystemUser() {
        UserInfo testInfo = new UserInfo();

        testInfo.id = UserHandle.USER_SYSTEM;
        assertThat(mCarUserManagerHelper.isSystemUser(testInfo)).isTrue();

        testInfo.id = UserHandle.USER_SYSTEM + 2; // Make it different than system id.
        assertThat(mCarUserManagerHelper.isSystemUser(testInfo)).isFalse();
    }

    // System user will not be returned when calling get all users.
    @Test
    public void testHeadlessUser0GetAllUsers_NotReturnSystemUser() {
        SystemProperties.set("android.car.systemuser.headless", "true");
        UserInfo otherUser1 = createUserInfoForId(10);
        UserInfo otherUser2 = createUserInfoForId(11);
        UserInfo otherUser3 = createUserInfoForId(12);

        List<UserInfo> testUsers = new ArrayList<>();
        testUsers.add(mSystemUser);
        testUsers.add(otherUser1);
        testUsers.add(otherUser2);
        testUsers.add(otherUser3);

        doReturn(testUsers).when(mUserManager).getUsers(true);

        assertThat(mCarUserManagerHelper.getAllUsers()).hasSize(3);
        assertThat(mCarUserManagerHelper.getAllUsers())
                .containsExactly(otherUser1, otherUser2, otherUser3);
    }

    @Test
    public void testGetAllSwitchableUsers() {
        // Create two non-foreground users.
        UserInfo user1 = createUserInfoForId(mForegroundUserId + 1);
        UserInfo user2 = createUserInfoForId(mForegroundUserId + 2);

        List<UserInfo> testUsers = Arrays.asList(mForegroundUser, user1, user2);

        doReturn(new ArrayList<>(testUsers)).when(mUserManager).getUsers(true);

        // Should return all 3 users.
        assertThat(mCarUserManagerHelper.getAllUsers()).hasSize(3);

        // Should return all non-foreground users.
        assertThat(mCarUserManagerHelper.getAllSwitchableUsers()).hasSize(2);
        assertThat(mCarUserManagerHelper.getAllSwitchableUsers()).containsExactly(user1, user2);
    }

    @Test
    public void testUserCanBeRemoved() {
        UserInfo testInfo = new UserInfo();

        // System user cannot be removed.
        testInfo.id = UserHandle.USER_SYSTEM;
        assertThat(mCarUserManagerHelper.canUserBeRemoved(testInfo)).isFalse();

        testInfo.id = UserHandle.USER_SYSTEM + 2; // Make it different than system id.
        assertThat(mCarUserManagerHelper.canUserBeRemoved(testInfo)).isTrue();
    }

    @Test
    public void testCurrentProcessCanAddUsers() {
        doReturn(false).when(mUserManager)
            .hasUserRestriction(UserManager.DISALLOW_ADD_USER);
        assertThat(mCarUserManagerHelper.canCurrentProcessAddUsers()).isTrue();

        doReturn(true).when(mUserManager)
            .hasUserRestriction(UserManager.DISALLOW_ADD_USER);
        assertThat(mCarUserManagerHelper.canCurrentProcessAddUsers()).isFalse();
    }

    @Test
    public void testCurrentProcessCanRemoveUsers() {
        doReturn(false).when(mUserManager)
            .hasUserRestriction(UserManager.DISALLOW_REMOVE_USER);
        assertThat(mCarUserManagerHelper.canCurrentProcessRemoveUsers()).isTrue();

        doReturn(true).when(mUserManager)
            .hasUserRestriction(UserManager.DISALLOW_REMOVE_USER);
        assertThat(mCarUserManagerHelper.canCurrentProcessRemoveUsers()).isFalse();
    }

    @Test
    public void testCurrentProcessCanSwitchUsers() {
        doReturn(false).when(mUserManager)
            .hasUserRestriction(UserManager.DISALLOW_USER_SWITCH);
        assertThat(mCarUserManagerHelper.canCurrentProcessSwitchUsers()).isTrue();

        doReturn(true).when(mUserManager)
            .hasUserRestriction(UserManager.DISALLOW_USER_SWITCH);
        assertThat(mCarUserManagerHelper.canCurrentProcessSwitchUsers()).isFalse();
    }

    @Test
    public void testCurrentGuestProcessCannotModifyAccounts() {
        assertThat(mCarUserManagerHelper.canCurrentProcessModifyAccounts()).isTrue();

        doReturn(true).when(mUserManager).isGuestUser();

        assertThat(mCarUserManagerHelper.canCurrentProcessModifyAccounts()).isFalse();
    }

    @Test
    public void testCurrentDemoProcessCannotModifyAccounts() {
        assertThat(mCarUserManagerHelper.canCurrentProcessModifyAccounts()).isTrue();

        doReturn(true).when(mUserManager).isDemoUser();

        assertThat(mCarUserManagerHelper.canCurrentProcessModifyAccounts()).isFalse();
    }

    @Test
    public void testCurrentDisallowModifyAccountsProcessIsEnforced() {
        assertThat(mCarUserManagerHelper.canCurrentProcessModifyAccounts()).isTrue();

        doReturn(true).when(mUserManager)
                .hasUserRestriction(UserManager.DISALLOW_MODIFY_ACCOUNTS);

        assertThat(mCarUserManagerHelper.canCurrentProcessModifyAccounts()).isFalse();
    }

    @Test
    public void testCreateNewAdminUser() {
        // Make sure current user is admin, since only admins can create other admins.
        doReturn(true).when(mUserManager).isAdminUser();

        // Verify createUser on UserManager gets called.
        mCarUserManagerHelper.createNewAdminUser(mTestUserName);
        verify(mUserManager).createUser(mTestUserName, UserInfo.FLAG_ADMIN);

        doReturn(null).when(mUserManager).createUser(mTestUserName, UserInfo.FLAG_ADMIN);
        assertThat(mCarUserManagerHelper.createNewAdminUser(mTestUserName)).isNull();

        UserInfo newUser = new UserInfo();
        newUser.name = mTestUserName;
        doReturn(newUser).when(mUserManager).createUser(mTestUserName, UserInfo.FLAG_ADMIN);
        assertThat(mCarUserManagerHelper.createNewAdminUser(mTestUserName)).isEqualTo(newUser);
    }

    @Test
    public void testAdminsCanCreateAdmins() {
        String newAdminName = "Test new admin";
        UserInfo expectedAdmin = new UserInfo();
        expectedAdmin.name = newAdminName;
        doReturn(expectedAdmin).when(mUserManager).createUser(newAdminName, UserInfo.FLAG_ADMIN);

        // Admins can create other admins.
        doReturn(true).when(mUserManager).isAdminUser();
        UserInfo actualAdmin = mCarUserManagerHelper.createNewAdminUser(newAdminName);
        assertThat(actualAdmin).isEqualTo(expectedAdmin);
    }

    @Test
    public void testNonAdminsCanNotCreateAdmins() {
        String newAdminName = "Test new admin";
        UserInfo expectedAdmin = new UserInfo();
        expectedAdmin.name = newAdminName;
        doReturn(expectedAdmin).when(mUserManager).createUser(newAdminName, UserInfo.FLAG_ADMIN);

        // Test that non-admins cannot create new admins.
        doReturn(false).when(mUserManager).isAdminUser(); // Current user non-admin.
        assertThat(mCarUserManagerHelper.createNewAdminUser(newAdminName)).isNull();
    }

    @Test
    public void testSystemUserCanCreateAdmins() {
        String newAdminName = "Test new admin";
        UserInfo expectedAdmin = new UserInfo();
        expectedAdmin.name = newAdminName;

        doReturn(expectedAdmin).when(mUserManager).createUser(newAdminName, UserInfo.FLAG_ADMIN);

        // System user can create admins.
        doReturn(true).when(mUserManager).isSystemUser();
        UserInfo actualAdmin = mCarUserManagerHelper.createNewAdminUser(newAdminName);
        assertThat(actualAdmin).isEqualTo(expectedAdmin);
    }

    @Test
    public void testCreateNewNonAdminUser() {
        // Verify createUser on UserManager gets called.
        mCarUserManagerHelper.createNewNonAdminUser(mTestUserName);
        verify(mUserManager).createUser(mTestUserName, 0);

        doReturn(null).when(mUserManager).createUser(mTestUserName, 0);
        assertThat(mCarUserManagerHelper.createNewNonAdminUser(mTestUserName)).isNull();

        UserInfo newUser = new UserInfo();
        newUser.name = mTestUserName;
        doReturn(newUser).when(mUserManager).createUser(mTestUserName, 0);
        assertThat(mCarUserManagerHelper.createNewNonAdminUser(mTestUserName)).isEqualTo(newUser);
    }

    @Test
    public void testCannotRemoveSystemUser() {
        assertThat(mCarUserManagerHelper.removeUser(mSystemUser, mGuestUserName)).isFalse();
    }

    @Test
    public void testAdminsCanRemoveOtherUsers() {
        int idToRemove = mCurrentProcessUser.id + 2;
        UserInfo userToRemove = createUserInfoForId(idToRemove);

        doReturn(true).when(mUserManager).removeUser(idToRemove);

        // If Admin is removing non-current, non-system user, simply calls removeUser.
        doReturn(true).when(mUserManager).isAdminUser();
        assertThat(mCarUserManagerHelper.removeUser(userToRemove, mGuestUserName)).isTrue();
        verify(mUserManager).removeUser(idToRemove);
    }

    @Test
    public void testNonAdminsCanNotRemoveOtherUsers() {
        UserInfo otherUser = createUserInfoForId(mCurrentProcessUser.id + 2);

        // Make current user non-admin.
        doReturn(false).when(mUserManager).isAdminUser();

        // Mock so that removeUser always pretends it's successful.
        doReturn(true).when(mUserManager).removeUser(anyInt());

        // If Non-Admin is trying to remove someone other than themselves, they should fail.
        assertThat(mCarUserManagerHelper.removeUser(otherUser, mGuestUserName)).isFalse();
        verify(mUserManager, never()).removeUser(otherUser.id);
    }

    @Test
    public void testRemoveLastActiveUser() {
        // Cannot remove system user.
        assertThat(mCarUserManagerHelper.removeUser(mSystemUser, mGuestUserName)).isFalse();

        UserInfo adminInfo = new UserInfo(/* id= */10, "admin", UserInfo.FLAG_ADMIN);
        List<UserInfo> users = new ArrayList<UserInfo>();
        users.add(adminInfo);

        doReturn(users).when(mUserManager).getUsers(true);

        assertThat(mCarUserManagerHelper.removeUser(adminInfo, mGuestUserName))
            .isEqualTo(false);
    }

    @Test
    public void testSwitchToGuest() {
        mCarUserManagerHelper.startNewGuestSession(mGuestUserName);
        verify(mUserManager).createGuest(mContext, mGuestUserName);

        UserInfo guestInfo = new UserInfo(/* id= */21, mGuestUserName, UserInfo.FLAG_GUEST);
        doReturn(guestInfo).when(mUserManager).createGuest(mContext, mGuestUserName);
        mCarUserManagerHelper.startNewGuestSession(mGuestUserName);
        verify(mActivityManager).switchUser(21);
    }

    @Test
    public void testGetUserIcon() {
        mCarUserManagerHelper.getUserIcon(mCurrentProcessUser);
        verify(mUserManager).getUserIcon(mCurrentProcessUser.id);
    }

    @Test
    public void testScaleUserIcon() {
        Bitmap fakeIcon = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
        Drawable scaledIcon = mCarUserManagerHelper.scaleUserIcon(fakeIcon, 300);
        assertThat(scaledIcon.getIntrinsicWidth()).isEqualTo(300);
        assertThat(scaledIcon.getIntrinsicHeight()).isEqualTo(300);
    }

    @Test
    public void testSetUserName() {
        UserInfo testInfo = createUserInfoForId(mCurrentProcessUser.id + 3);
        String newName = "New Test Name";
        mCarUserManagerHelper.setUserName(testInfo, newName);
        verify(mUserManager).setUserName(mCurrentProcessUser.id + 3, newName);
    }

    @Test
    public void testIsCurrentProcessSystemUser() {
        doReturn(true).when(mUserManager).isAdminUser();
        assertThat(mCarUserManagerHelper.isCurrentProcessAdminUser()).isTrue();

        doReturn(false).when(mUserManager).isAdminUser();
        assertThat(mCarUserManagerHelper.isCurrentProcessAdminUser()).isFalse();
    }

    @Test
    public void testAssignAdminPrivileges() {
        int userId = 30;
        UserInfo testInfo = createUserInfoForId(userId);

        // Test that non-admins cannot assign admin privileges.
        doReturn(false).when(mUserManager).isAdminUser(); // Current user non-admin.
        mCarUserManagerHelper.assignAdminPrivileges(testInfo);
        verify(mUserManager, never()).setUserAdmin(userId);

        // Admins can assign admin privileges.
        doReturn(true).when(mUserManager).isAdminUser();
        mCarUserManagerHelper.assignAdminPrivileges(testInfo);
        verify(mUserManager).setUserAdmin(userId);
    }

    @Test
    public void testSetUserRestriction() {
        int userId = 20;
        UserInfo testInfo = createUserInfoForId(userId);

        mCarUserManagerHelper.setUserRestriction(
                testInfo, UserManager.DISALLOW_ADD_USER, /* enable= */ true);
        verify(mUserManager).setUserRestriction(
                UserManager.DISALLOW_ADD_USER, true, UserHandle.of(userId));

        mCarUserManagerHelper.setUserRestriction(
                testInfo, UserManager.DISALLOW_REMOVE_USER, /* enable= */ false);
        verify(mUserManager).setUserRestriction(
                UserManager.DISALLOW_REMOVE_USER, false, UserHandle.of(userId));
    }

    @Test
    public void testDefaultNonAdminRestrictions() {
        String testUserName = "Test User";
        int userId = 20;
        UserInfo newNonAdmin = createUserInfoForId(userId);

        doReturn(newNonAdmin).when(mUserManager).createUser(testUserName, /* flags= */ 0);

        mCarUserManagerHelper.createNewNonAdminUser(testUserName);

        verify(mUserManager).setUserRestriction(
                UserManager.DISALLOW_FACTORY_RESET, /* enable= */ true, UserHandle.of(userId));
        verify(mUserManager).setUserRestriction(
                UserManager.DISALLOW_SMS, /* enable= */ false, UserHandle.of(userId));
        verify(mUserManager).setUserRestriction(
                UserManager.DISALLOW_OUTGOING_CALLS, /* enable= */ false, UserHandle.of(userId));
    }

    @Test
    public void testAssigningAdminPrivilegesRemovesNonAdminRestrictions() {
        int testUserId = 30;
        boolean restrictionEnabled = false;
        UserInfo testInfo = createUserInfoForId(testUserId);

        // Only admins can assign privileges.
        doReturn(true).when(mUserManager).isAdminUser();

        mCarUserManagerHelper.assignAdminPrivileges(testInfo);

        verify(mUserManager).setUserRestriction(
                UserManager.DISALLOW_FACTORY_RESET, restrictionEnabled, UserHandle.of(testUserId));
    }

    @Test
    public void testRegisterUserChangeReceiver() {
        mCarUserManagerHelper.registerOnUsersUpdateListener(mTestListener);

        ArgumentCaptor<BroadcastReceiver> receiverCaptor =
                ArgumentCaptor.forClass(BroadcastReceiver.class);
        ArgumentCaptor<UserHandle> handleCaptor = ArgumentCaptor.forClass(UserHandle.class);
        ArgumentCaptor<IntentFilter> filterCaptor = ArgumentCaptor.forClass(IntentFilter.class);
        ArgumentCaptor<String> permissionCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Handler> handlerCaptor = ArgumentCaptor.forClass(Handler.class);

        verify(mContext).registerReceiverAsUser(
                receiverCaptor.capture(),
                handleCaptor.capture(),
                filterCaptor.capture(),
                permissionCaptor.capture(),
                handlerCaptor.capture());

        // Verify we're listening to Intents from ALL users.
        assertThat(handleCaptor.getValue()).isEqualTo(UserHandle.ALL);

        // Verify the presence of each intent in the filter.
        // Verify the exact number of filters. Every time a new intent is added, this test should
        // get updated.
        assertThat(filterCaptor.getValue().countActions()).isEqualTo(6);
        assertThat(filterCaptor.getValue().hasAction(Intent.ACTION_USER_REMOVED)).isTrue();
        assertThat(filterCaptor.getValue().hasAction(Intent.ACTION_USER_ADDED)).isTrue();
        assertThat(filterCaptor.getValue().hasAction(Intent.ACTION_USER_INFO_CHANGED)).isTrue();
        assertThat(filterCaptor.getValue().hasAction(Intent.ACTION_USER_SWITCHED)).isTrue();
        assertThat(filterCaptor.getValue().hasAction(Intent.ACTION_USER_STOPPED)).isTrue();
        assertThat(filterCaptor.getValue().hasAction(Intent.ACTION_USER_UNLOCKED)).isTrue();

        // Verify that calling the receiver calls the listener.
        receiverCaptor.getValue().onReceive(mContext, new Intent());
        verify(mTestListener).onUsersUpdate();

        assertThat(permissionCaptor.getValue()).isNull();
        assertThat(handlerCaptor.getValue()).isNull();

        // Unregister the receiver.
        mCarUserManagerHelper.unregisterOnUsersUpdateListener();
        verify(mContext).unregisterReceiver(receiverCaptor.getValue());
    }

    @Test
    public void testGetInitialUserWithValidLastActiveUser() {
        SystemProperties.set("android.car.systemuser.headless", "true");
        int lastActiveUserId = 12;

        UserInfo otherUser1 = createUserInfoForId(lastActiveUserId - 2);
        UserInfo otherUser2 = createUserInfoForId(lastActiveUserId - 1);
        UserInfo otherUser3 = createUserInfoForId(lastActiveUserId);

        List<UserInfo> testUsers = new ArrayList<>();
        testUsers.add(mSystemUser);
        testUsers.add(otherUser1);
        testUsers.add(otherUser2);
        testUsers.add(otherUser3);

        mCarUserManagerHelper.setLastActiveUser(
                lastActiveUserId, /* skipGlobalSettings= */ true);
        doReturn(testUsers).when(mUserManager).getUsers(true);

        assertThat(mCarUserManagerHelper.getInitialUser()).isEqualTo(lastActiveUserId);
    }

    @Test
    public void testGetInitialUserWithNonExistLastActiveUser() {
        SystemProperties.set("android.car.systemuser.headless", "true");
        int lastActiveUserId = 12;

        UserInfo otherUser1 = createUserInfoForId(lastActiveUserId - 2);
        UserInfo otherUser2 = createUserInfoForId(lastActiveUserId - 1);

        List<UserInfo> testUsers = new ArrayList<>();
        testUsers.add(mSystemUser);
        testUsers.add(otherUser1);
        testUsers.add(otherUser2);

        mCarUserManagerHelper.setLastActiveUser(
                lastActiveUserId, /* skipGlobalSettings= */ true);
        doReturn(testUsers).when(mUserManager).getUsers(true);

        assertThat(mCarUserManagerHelper.getInitialUser()).isEqualTo(lastActiveUserId - 2);
    }

    private UserInfo createUserInfoForId(int id) {
        UserInfo userInfo = new UserInfo();
        userInfo.id = id;
        return userInfo;
    }
}
