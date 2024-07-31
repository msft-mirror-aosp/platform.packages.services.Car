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

package com.android.car.systeminterface;

import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_SWITCHING;
import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_UNLOCKED;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import static org.mockito.AdditionalMatchers.or;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.ActivityManager;
import android.car.builtin.view.DisplayHelper;
import android.car.test.mocks.AbstractExtendedMockitoTestCase;
import android.car.test.mocks.AbstractExtendedMockitoTestCase.CustomMockitoSessionBuilder;
import android.car.user.CarUserManager.UserLifecycleEvent;
import android.car.user.CarUserManager.UserLifecycleListener;
import android.content.ContentResolver;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.hardware.display.DisplayManager.DisplayListener;
import android.os.PowerManager;
import android.os.UserManager;
import android.view.Display;

import com.android.car.power.CarPowerManagementService;
import com.android.car.user.CarUserService;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;

@RunWith(MockitoJUnitRunner.class)
public final class DisplayInterfaceTest extends AbstractExtendedMockitoTestCase {

    private static final int MAIN_DISPLAY_ID = Display.DEFAULT_DISPLAY;
    private static final int DISTANT_DISPLAY_ID = 2;
    private static final int VIRTUAL_DISPLAY_ID = 3;
    private static final int OVERLAY_DISPLAY_ID = 4;

    private static final int GLOBAL_BRIGHTNESS = 125;
    private static final int GLOBAL_BRIGHTNESS_2 = 130;
    private static final float DISPLAY_MANAGER_BRIGHTNESS_1 = 0.5f;
    private static final float DISPLAY_MANAGER_BRIGHTNESS_2 = 0.6f;

    private static final int MIN_SCREEN_BRIGHTNESS_SETTING = 1;
    private static final int MAX_SCREEN_BRIGHTNESS_SETTING = 255;

    private static final int DISPLAY_TYPE_OVERLAY = 4;

    @Mock
    private Context mContext;

    @Mock
    private WakeLockInterface mWakeLockInterface;

    @Mock
    private CarPowerManagementService mCarPowerManagementService;

    @Mock
    private DisplayManager mDisplayManager;

    @Mock
    private ContentResolver mContentResolver;

    @Mock
    private CarUserService mCarUserService;

    @Mock
    private PowerManager mPowerManager;

    @Mock
    private UserManager mUserManager;

    @Override
    protected void onSessionBuilder(CustomMockitoSessionBuilder session) {
        session.spyStatic(DisplayHelper.class).spyStatic(ActivityManager.class);
    }

    @Mock
    private Display mDisplay;
    @Mock
    private Display mDistantDisplay;
    @Mock
    private Display mVirtualDisplay;
    @Mock
    private Display mOverlayDisplay;

    private DisplayInterface.DefaultImpl mDisplayInterface;

    private void addDisplay(Display display, int displayId, int displayType) {
        when(display.getDisplayId()).thenReturn(displayId);
        when(mDisplayManager.getDisplay(displayId)).thenReturn(display);
        doReturn(displayType).when(() -> DisplayHelper.getType(display));
    }

    @Before
    public void setUp() {
        when(mContext.createContextAsUser(any(), anyInt())).thenReturn(mContext);
        when(mContext.getContentResolver()).thenReturn(mContentResolver);
        when(mContext.getSystemService(DisplayManager.class)).thenReturn(mDisplayManager);
        when(mContext.getSystemService(PowerManager.class)).thenReturn(mPowerManager);
        when(mContext.getSystemService(UserManager.class)).thenReturn(mUserManager);
        when(mPowerManager.getMinimumScreenBrightnessSetting()).thenReturn(
                MIN_SCREEN_BRIGHTNESS_SETTING);
        when(mPowerManager.getMaximumScreenBrightnessSetting()).thenReturn(
                MAX_SCREEN_BRIGHTNESS_SETTING);
        doReturn(0).when(() -> ActivityManager.getCurrentUser());

        addDisplay(mDisplay, MAIN_DISPLAY_ID, DisplayHelper.TYPE_INTERNAL);
        addDisplay(mDistantDisplay, DISTANT_DISPLAY_ID, DisplayHelper.TYPE_INTERNAL);
        addDisplay(mVirtualDisplay, VIRTUAL_DISPLAY_ID, DisplayHelper.TYPE_VIRTUAL);
        addDisplay(mOverlayDisplay, OVERLAY_DISPLAY_ID, DISPLAY_TYPE_OVERLAY);
    }


    @Test
    public void testStartDisplayStateMonitoring_multiDisplayControlSupported() {
        when(mDisplayManager.getDisplays()).thenReturn(new Display[]{mDisplay, mDistantDisplay});
        when(mDisplay.getState()).thenReturn(Display.STATE_ON);
        when(mDistantDisplay.getState()).thenReturn(Display.STATE_ON);

        createDisplayInterface(/* visibleBgUsersSupported= */ false);

        mDisplayInterface.startDisplayStateMonitoring();

        verify(mDisplayManager).registerDisplayListener(any(), isNull(), anyLong());
        verify(mCarUserService).addUserLifecycleListener(any(), any());
        // If multi display brightness is supported, even if MUMD is not enabled, we must refresh
        // all display's brightness.
        verify(mCarPowerManagementService, times(2)).sendDisplayBrightness(
                or(eq(MAIN_DISPLAY_ID), eq(DISTANT_DISPLAY_ID)), anyInt());
    }

    @Test
    public void testStopDisplayStateMonitoring_visibleBgUsersSupported() {
        when(mDisplayManager.getDisplays()).thenReturn(new Display[]{mDisplay});
        when(mDisplayManager.getBrightness(anyInt())).thenReturn(DISPLAY_MANAGER_BRIGHTNESS_1);

        createDisplayInterface(/* visibleBgUsersSupported= */ true);
        mDisplayInterface.startDisplayStateMonitoring();
        mDisplayInterface.stopDisplayStateMonitoring();

        verify(mDisplayManager).unregisterDisplayListener(any());
        verify(mCarUserService).removeUserLifecycleListener(any());
    }

    @Test
    public void testOnUserSwitchEvent_notUserSwitchingEvent() {
        when(mDisplayManager.getDisplays()).thenReturn(new Display[]{mDisplay});

        createDisplayInterface(/* visibleBgUsersSupported= */ false);
        mDisplayInterface.startDisplayStateMonitoring();
        var userLifecycleListenerCaptor = ArgumentCaptor.forClass(UserLifecycleListener.class);

        verify(mCarUserService).addUserLifecycleListener(any(),
                userLifecycleListenerCaptor.capture());
        clearInvocations(mCarPowerManagementService);

        userLifecycleListenerCaptor.getValue().onEvent(
                new UserLifecycleEvent(USER_LIFECYCLE_EVENT_TYPE_UNLOCKED, /* to= */ 0));

        verify(mCarPowerManagementService, never()).sendDisplayBrightness(anyInt(), anyInt());
    }

    @Test
    public void testOnUserSwitchEvent_newUserNotDriver() {
        when(mDisplayManager.getDisplays()).thenReturn(new Display[]{mDisplay});

        createDisplayInterface(/* visibleBgUsersSupported= */ false);
        mDisplayInterface.startDisplayStateMonitoring();
        var userLifecycleListenerCaptor = ArgumentCaptor.forClass(UserLifecycleListener.class);

        verify(mCarUserService).addUserLifecycleListener(any(),
                userLifecycleListenerCaptor.capture());
        clearInvocations(mCarPowerManagementService);

        userLifecycleListenerCaptor.getValue().onEvent(
                new UserLifecycleEvent(USER_LIFECYCLE_EVENT_TYPE_SWITCHING, /* to= */ 1));

        verify(mCarPowerManagementService, never()).sendDisplayBrightness(anyInt(), anyInt());
    }

    @Test
    public void testOnUserSwitchEvent() {
        when(mDisplayManager.getDisplays()).thenReturn(new Display[]{mDisplay, mDistantDisplay});

        createDisplayInterface(/* visibleBgUsersSupported= */ false);
        mDisplayInterface.startDisplayStateMonitoring();

        var userLifecycleListenerCaptor = ArgumentCaptor.forClass(UserLifecycleListener.class);
        verify(mCarUserService).addUserLifecycleListener(any(),
                userLifecycleListenerCaptor.capture());
        clearInvocations(mCarPowerManagementService);

        userLifecycleListenerCaptor.getValue().onEvent(
                new UserLifecycleEvent(USER_LIFECYCLE_EVENT_TYPE_SWITCHING, /* to= */ 0));

        verify(mCarPowerManagementService, times(2)).sendDisplayBrightness(
                or(eq(MAIN_DISPLAY_ID), eq(DISTANT_DISPLAY_ID)), anyInt());
    }

    @Test
    public void testOnDisplayBrightnessChangeFromVhal_ForMainDisplay() {
        when(mDisplayManager.getDisplays()).thenReturn(new Display[]{mDisplay, mDistantDisplay});

        createDisplayInterface(/* visibleBgUsersSupported= */ false);
        mDisplayInterface.onDisplayBrightnessChangeFromVhal(MAIN_DISPLAY_ID,
                /* percentBright= */ 50);

        verify(mDisplayManager).setBrightness(eq(MAIN_DISPLAY_ID), anyFloat());
    }

    @Test
    public void testOnDisplayBrightnessChangeFromVhal_ForDistantDisplay() {
        when(mDisplayManager.getDisplays()).thenReturn(new Display[]{mDisplay, mDistantDisplay});

        createDisplayInterface(/* visibleBgUsersSupported= */ false);
        mDisplayInterface.onDisplayBrightnessChangeFromVhal(DISTANT_DISPLAY_ID,
                /* percentBright= */ 50);

        verify(mDisplayManager).setBrightness(eq(DISTANT_DISPLAY_ID), anyFloat());
    }

    @Test
    public void testDisplayBrightnessChangeFromDisplayManager() throws Exception {
        when(mDisplayManager.getDisplays()).thenReturn(new Display[]{mDisplay, mDistantDisplay});
        when(mDisplay.getState()).thenReturn(Display.STATE_ON);
        when(mDistantDisplay.getState()).thenReturn(Display.STATE_ON);

        createDisplayInterface(/* visibleBgUsersSupported= */ false);

        mDisplayInterface.startDisplayStateMonitoring();

        var displayListenerCaptor = ArgumentCaptor.forClass(DisplayListener.class);
        verify(mDisplayManager).registerDisplayListener(displayListenerCaptor.capture(), isNull(),
                anyLong());
        clearInvocations(mCarPowerManagementService);

        // Simulate user changes the global brightness setting to GLOBAL_BRIGHTNESS_2.
        when(mDisplayManager.getBrightness(anyInt())).thenReturn(DISPLAY_MANAGER_BRIGHTNESS_2);
        displayListenerCaptor.getValue().onDisplayChanged(MAIN_DISPLAY_ID);

        verify(mCarPowerManagementService).sendDisplayBrightness(eq(MAIN_DISPLAY_ID), anyInt());
    }

    @Test
    public void testDisplayBrightnessChangeFromDisplayManager_ignoreRecentChange()
            throws Exception {
        when(mDisplayManager.getDisplays()).thenReturn(new Display[]{mDisplay, mDistantDisplay});
        when(mDisplay.getState()).thenReturn(Display.STATE_ON);
        when(mDistantDisplay.getState()).thenReturn(Display.STATE_ON);

        createDisplayInterface(/* visibleBgUsersSupported= */ false);

        mDisplayInterface.startDisplayStateMonitoring();

        var displayListenerCaptor = ArgumentCaptor.forClass(DisplayListener.class);
        verify(mDisplayManager).registerDisplayListener(displayListenerCaptor.capture(), isNull(),
                anyLong());
        clearInvocations(mCarPowerManagementService);

        // This variable stores the brightness settings. Need to use a list to be
        // effective final.
        ArrayList<Float> brightness = new ArrayList<>();

        doAnswer((inv) -> {
            brightness.add(inv.getArgument(1));
            return null;
        }).when(mDisplayManager).setBrightness(eq(MAIN_DISPLAY_ID), anyFloat());
        when(mDisplayManager.getBrightness(eq(MAIN_DISPLAY_ID))).thenAnswer((inv) -> {
            return brightness.get(0);
        });

        // Simulate a brightness change from VHAL.
        mDisplayInterface.onDisplayBrightnessChangeFromVhal(MAIN_DISPLAY_ID,
                /* percentBright= */ 50);
        // This should trigger an onDisplayChanged event.
        displayListenerCaptor.getValue().onDisplayChanged(MAIN_DISPLAY_ID);

        // Because the brightness event is caused by VHAL, so we must ignore it
        // and not report back to VHAL again.
        verify(mCarPowerManagementService, never()).sendDisplayBrightness(anyInt(), anyInt());
    }

    private void createDisplayInterface(boolean visibleBgUsersSupported) {
        when(mUserManager.isVisibleBackgroundUsersSupported()).thenReturn(
                visibleBgUsersSupported);

        mDisplayInterface = new DisplayInterface.DefaultImpl(mContext, mWakeLockInterface);
        mDisplayInterface.init(mCarPowerManagementService, mCarUserService);
    }
}
