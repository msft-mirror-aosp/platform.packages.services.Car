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

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.AdditionalMatchers.or;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.builtin.view.DisplayHelper;
import android.content.ContentResolver;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.net.Uri;
import android.os.PowerManager;
import android.os.UserManager;
import android.platform.test.ravenwood.RavenwoodRule;
import android.view.Display;

import com.android.car.power.CarPowerManagementService;
import com.android.car.provider.Settings;
import com.android.car.user.CarUserService;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public final class DisplayInterfaceTest {

    private static final int MAIN_DISPLAY_ID = Display.DEFAULT_DISPLAY;
    private static final int DISTANT_DISPLAY_ID = 2;
    private static final int VIRTUAL_DISPLAY_ID = 3;
    private static final int OVERLAY_DISPLAY_ID = 4;

    private static final int GLOBAL_BRIGHTNESS = 125;
    private static final float DISPLAY_MANAGER_BRIGHTNESS_1 = 0.5f;
    private static final float DISPLAY_MANAGER_BRIGHTNESS_2 = 0.6f;

    private static final int MIN_SCREEN_BRIGHTNESS_SETTING = 1;
    private static final int MAX_SCREEN_BRIGHTNESS_SETTING = 255;

    @Rule
    public final RavenwoodRule mRavenwood = new RavenwoodRule.Builder()
            .setProvideMainThread(true).build();

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

    @Mock
    private DisplayInterface.DisplayTypeGetter mDisplayTypeGetter;

    @Mock
    private Settings mSettings;

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
        when(mDisplayTypeGetter.getDisplayType(display)).thenReturn(displayType);
    }

    @Before
    public void setUp() throws Exception {
        when(mSettings.getIntSystem(eq(mContentResolver), anyString())).thenReturn(
                GLOBAL_BRIGHTNESS);
        when(mSettings.getUriForSystem(anyString())).thenReturn(Uri.parse(""));
        when(mContext.createContextAsUser(any(), anyInt())).thenReturn(mContext);
        when(mContext.getContentResolver()).thenReturn(mContentResolver);
        when(mContext.getSystemService(DisplayManager.class)).thenReturn(mDisplayManager);
        when(mContext.getSystemService(PowerManager.class)).thenReturn(mPowerManager);
        when(mContext.getSystemService(UserManager.class)).thenReturn(mUserManager);
        when(mPowerManager.getMinimumScreenBrightnessSetting()).thenReturn(
                MIN_SCREEN_BRIGHTNESS_SETTING);
        when(mPowerManager.getMaximumScreenBrightnessSetting()).thenReturn(
                MAX_SCREEN_BRIGHTNESS_SETTING);

        addDisplay(mDisplay, MAIN_DISPLAY_ID, DisplayHelper.TYPE_INTERNAL);
        addDisplay(mDistantDisplay, DISTANT_DISPLAY_ID, DisplayHelper.TYPE_INTERNAL);
        addDisplay(mVirtualDisplay, VIRTUAL_DISPLAY_ID, DisplayHelper.TYPE_VIRTUAL);
        addDisplay(mOverlayDisplay, OVERLAY_DISPLAY_ID, DisplayHelper.TYPE_OVERLAY);
    }

    @Test
    public void testStartDisplayStateMonitoring_visibleBgUsersSupported() {
        when(mDisplayManager.getDisplays()).thenReturn(new Display[]{
                mDisplay, mDistantDisplay, mVirtualDisplay, mOverlayDisplay});
        when(mDisplay.getState()).thenReturn(Display.STATE_ON);
        when(mDistantDisplay.getState()).thenReturn(Display.STATE_ON);
        when(mDisplayManager.getBrightness(MAIN_DISPLAY_ID)).thenReturn(
                DISPLAY_MANAGER_BRIGHTNESS_1);
        when(mDisplayManager.getBrightness(DISTANT_DISPLAY_ID)).thenReturn(
                DISPLAY_MANAGER_BRIGHTNESS_2);

        createDisplayInterface(/* visibleBgUsersSupported= */ true);

        mDisplayInterface.startDisplayStateMonitoring();

        verify(mDisplayManager).registerDisplayListener(any(), isNull(), anyLong());
        verify(mCarUserService).addUserLifecycleListener(any(), any());
        var intCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(mCarPowerManagementService, times(2)).sendDisplayBrightness(
                or(eq(MAIN_DISPLAY_ID), eq(DISTANT_DISPLAY_ID)), intCaptor.capture());
        int brightness1 = intCaptor.getAllValues().get(0);
        int brightness2 = intCaptor.getAllValues().get(1);
        assertThat(brightness1).isNotEqualTo(0);
        assertThat(brightness2).isNotEqualTo(0);
        assertThat(brightness1).isNotEqualTo(brightness2);
    }

    @Test
    public void testStartDisplayStateMonitoring_visibleBgUsersNotSupported() {
        when(mDisplayManager.getDisplays()).thenReturn(new Display[]{mDisplay, mDistantDisplay});
        when(mDisplay.getState()).thenReturn(Display.STATE_ON);
        when(mDistantDisplay.getState()).thenReturn(Display.STATE_ON);

        createDisplayInterface(/* visibleBgUsersSupported= */ false);

        mDisplayInterface.startDisplayStateMonitoring();

        verify(mContentResolver).registerContentObserver(any(), eq(false), any());
        verify(mCarUserService).addUserLifecycleListener(any(), any());
        verify(mCarPowerManagementService, times(2)).sendDisplayBrightness(anyInt());
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
    public void testStopDisplayStateMonitoring_visibleBgUsersNotSupported() {
        when(mDisplayManager.getDisplays()).thenReturn(new Display[]{mDisplay});

        createDisplayInterface(/* visibleBgUsersSupported= */ false);
        mDisplayInterface.startDisplayStateMonitoring();
        mDisplayInterface.stopDisplayStateMonitoring();

        verify(mContentResolver).unregisterContentObserver(any());
        verify(mCarUserService).removeUserLifecycleListener(any());
    }

    private void createDisplayInterface(boolean visibleBgUsersSupported) {
        when(mUserManager.isVisibleBackgroundUsersSupported()).thenReturn(
                visibleBgUsersSupported);

        mDisplayInterface = new DisplayInterface.DefaultImpl(mContext, mWakeLockInterface,
                mSettings, mDisplayTypeGetter);
        mDisplayInterface.init(mCarPowerManagementService, mCarUserService);
    }
}
