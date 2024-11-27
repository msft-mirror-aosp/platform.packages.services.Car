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

package com.android.car.audio;

import static android.car.media.CarAudioManager.PRIMARY_AUDIO_ZONE;
import static android.media.AudioManager.GET_DEVICES_INPUTS;
import static android.media.AudioManager.GET_DEVICES_OUTPUTS;

import static com.android.car.audio.CarAudioTestUtils.SECONDARY_ZONE_ID;
import static com.android.car.audio.CarAudioTestUtils.createPrimaryAudioZone;
import static com.android.car.audio.CarAudioTestUtils.createSecondaryAudioZone;
import static com.android.car.audio.CoreAudioRoutingUtils.getCoreAudioZone;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.when;

import android.car.test.mocks.AbstractExtendedMockitoTestCase;
import android.hardware.automotive.audiocontrol.AudioDeviceConfiguration;
import android.hardware.automotive.audiocontrol.AudioZone;
import android.hardware.automotive.audiocontrol.RoutingDeviceConfiguration;
import android.media.AudioDeviceInfo;

import com.android.car.audio.hal.AudioControlWrapper;
import com.android.car.internal.util.LocalLog;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.List;

@RunWith(MockitoJUnitRunner.class)
public final class CarAudioZonesHelperAudioControlHALUnitTest
        extends AbstractExtendedMockitoTestCase {

    private static final int TEST_PRIMARY_ZONE_OCCUPANT_ID = 1;
    private static final int TEST_SECONDARY_ZONE_OCCUPANT_ID = 2;

    @Mock
    private CarAudioSettings mCarAudioSettings;
    @Mock
    private AudioManagerWrapper mAudioManager;
    @Mock
    private LocalLog mServiceLog;
    @Mock
    private AudioControlWrapper mAudioControlWrapper;
    private boolean mUseAudioFadeManager = true;

    private final List<AudioZone> mHALAudioZones = new ArrayList<>();

    private final CarAudioDeviceInfoTestUtils mDeviceTestUtils = new CarAudioDeviceInfoTestUtils();
    private final AudioDeviceConfiguration mAudioDeviceConfig = new AudioDeviceConfiguration();

    @Override
    protected void clearInlineMocks(String when) {
        super.clearInlineMocks(when);
    }

    @Override
    protected void onSessionBuilder(CustomMockitoSessionBuilder session) {
        session.spyStatic(AudioManagerWrapper.class);
    }

    @Before
    public void setUp() {
        doReturn(CoreAudioRoutingUtils.getProductStrategies())
                .when(AudioManagerWrapper::getAudioProductStrategies);

        var outputDevice = mDeviceTestUtils.generateOutputDeviceInfos();
        when(mAudioManager.getDevices(GET_DEVICES_OUTPUTS)).thenReturn(outputDevice);
        when(mAudioManager.getDevices(GET_DEVICES_INPUTS)).thenReturn(new AudioDeviceInfo[0]);

        when(mAudioControlWrapper.supportsFeature(
                AudioControlWrapper.AUDIOCONTROL_FEATURE_AUDIO_CONFIGURATION)).thenReturn(true);
        when(mAudioControlWrapper.getCarAudioZones()).thenReturn(mHALAudioZones);
        mAudioDeviceConfig.routingConfig = RoutingDeviceConfiguration.DYNAMIC_AUDIO_ROUTING;
        when(mAudioControlWrapper.getAudioDeviceConfiguration()).thenReturn(mAudioDeviceConfig);
    }

    @Test
    public void constructor_withNullAudioControlHAL() {
        AudioControlWrapper audioControl = null;

        var thrown = assertThrows(NullPointerException.class,
                () -> new CarAudioZonesHelperAudioControlHAL(audioControl, mAudioManager,
                        mCarAudioSettings, mServiceLog, mUseAudioFadeManager));

        expectWithMessage("Exception for null audio control HAL").that(thrown)
                .hasMessageThat().contains("Audio control HAL");
    }

    @Test
    public void constructor_withNullAudioManager() {
        AudioManagerWrapper audioManager = null;

        var thrown = assertThrows(NullPointerException.class,
                () -> new CarAudioZonesHelperAudioControlHAL(mAudioControlWrapper, audioManager,
                        mCarAudioSettings, mServiceLog, mUseAudioFadeManager));

        expectWithMessage("Exception for null audio manager").that(thrown)
                .hasMessageThat().contains("Audio manager");
    }

    @Test
    public void constructor_withNullAudioSettings() {
        CarAudioSettings audioSettings = null;

        var thrown = assertThrows(NullPointerException.class,
                () -> new CarAudioZonesHelperAudioControlHAL(mAudioControlWrapper, mAudioManager,
                        audioSettings, mServiceLog, mUseAudioFadeManager));

        expectWithMessage("Exception for null car audio settings").that(thrown)
                .hasMessageThat().contains("Car audio settings");
    }

    @Test
    public void constructor_withNullServiceLogs() {
        LocalLog serviceLogs = null;

        var thrown = assertThrows(NullPointerException.class,
                () -> new CarAudioZonesHelperAudioControlHAL(mAudioControlWrapper, mAudioManager,
                        mCarAudioSettings, serviceLogs, mUseAudioFadeManager));

        expectWithMessage("Exception for null car audio logs").that(thrown)
                .hasMessageThat().contains("Car audio log");
    }

    @Test
    public void loadAudioZones_withUnsupportedOperationFromHal() {
        when(mAudioControlWrapper.supportsFeature(
                AudioControlWrapper.AUDIOCONTROL_FEATURE_AUDIO_CONFIGURATION)).thenReturn(false);
        CarAudioZonesHelperAudioControlHAL helper = createAudioZonesHelper();

        var audioZones = helper.loadAudioZones();

        expectWithMessage("Loaded audio zones with unsupported feature from HAL")
                .that(audioZones.size()).isEqualTo(0);
    }

    @Test
    public void loadAudioZones_withDefaultDeviceConfig() {
        mAudioDeviceConfig.routingConfig = RoutingDeviceConfiguration.DEFAULT_AUDIO_ROUTING;
        CarAudioZonesHelperAudioControlHAL helper = createAudioZonesHelper();

        var audioZones = helper.loadAudioZones();

        expectWithMessage("Loaded audio zones with default audio routing")
                .that(audioZones.size()).isEqualTo(0);
    }

    @Test
    public void loadAudioZones_withNullZonesFromHal() {
        when(mAudioControlWrapper.getCarAudioZones()).thenReturn(null);
        CarAudioZonesHelperAudioControlHAL helper = createAudioZonesHelper();

        var audioZones = helper.loadAudioZones();

        expectWithMessage("Loaded audio zones with null audio zones from HAL")
                .that(audioZones.size()).isEqualTo(0);
    }

    @Test
    public void loadAudioZones_withEmptyZonesFromHal() {
        mHALAudioZones.clear();
        CarAudioZonesHelperAudioControlHAL helper = createAudioZonesHelper();

        var audioZones = helper.loadAudioZones();

        expectWithMessage("Loaded audio zones with null audio zones from HAL")
                .that(audioZones.size()).isEqualTo(0);
    }

    @Test
    public void loadAudioZones_withNullZoneInZonesFromHal() {
        mHALAudioZones.add(null);
        CarAudioZonesHelperAudioControlHAL helper = createAudioZonesHelper();

        var audioZones = helper.loadAudioZones();

        expectWithMessage("Loaded audio zones with null audio zone in audio zones from HAL")
                .that(audioZones.size()).isEqualTo(0);
    }

    @Test
    public void loadAudioZones_withRepeatingZonesFromHal() {
        mHALAudioZones.add(createPrimaryAudioZone());
        mHALAudioZones.add(createPrimaryAudioZone());
        CarAudioZonesHelperAudioControlHAL helper = createAudioZonesHelper();

        var audioZones = helper.loadAudioZones();

        expectWithMessage("Loaded audio zones with repeating audio zones from HAL")
                .that(audioZones.size()).isEqualTo(0);
    }

    @Test
    public void loadAudioZones_withValidPrimaryZonesFromHal() {
        mHALAudioZones.add(createPrimaryAudioZone());
        CarAudioZonesHelperAudioControlHAL helper = createAudioZonesHelper();

        var audioZones = helper.loadAudioZones();

        expectWithMessage("Loaded audio zones with valid primary zone from HAL")
                .that(audioZones.size()).isEqualTo(1);
        var primaryZone = audioZones.get(PRIMARY_AUDIO_ZONE);
        assertWithMessage("Loaded primary audio zone").that(primaryZone).isNotNull();
        expectWithMessage("Loaded primary audio zone configs")
                .that(primaryZone.getAllCarAudioZoneConfigs()).hasSize(3);
    }

    @Test
    public void loadAudioZones_withValidSecondaryZoneFromHal() {
        mHALAudioZones.add(createPrimaryAudioZone());
        mHALAudioZones.add(createSecondaryAudioZone());
        CarAudioZonesHelperAudioControlHAL helper = createAudioZonesHelper();

        var audioZones = helper.loadAudioZones();

        expectWithMessage("Loaded audio zones with valid secondary zone from HAL")
                .that(audioZones.size()).isEqualTo(2);
        var secondaryZone = audioZones.get(SECONDARY_ZONE_ID);
        assertWithMessage("Loaded secondary audio zone").that(secondaryZone).isNotNull();
        expectWithMessage("Loaded secondary audio zone configs")
                .that(secondaryZone.getAllCarAudioZoneConfigs()).hasSize(2);
    }

    @Test
    public void loadAudioZones_withoutPrimaryZoneFromHal() {
        mHALAudioZones.add(createSecondaryAudioZone());
        CarAudioZonesHelperAudioControlHAL helper = createAudioZonesHelper();

        var audioZones = helper.loadAudioZones();

        expectWithMessage("Loaded audio zones without primary zone")
                .that(audioZones.size()).isEqualTo(0);
    }

    @Test
    public void loadAudioZones_withInvalidUseCoreVolumeConfiguration() {
        mHALAudioZones.add(createPrimaryAudioZone());
        mAudioDeviceConfig.useCoreAudioVolume = true;
        CarAudioZonesHelperAudioControlHAL helper = createAudioZonesHelper();

        var audioZones = helper.loadAudioZones();

        expectWithMessage("Loaded audio zones with invalid use core volume configuration")
                .that(audioZones.size()).isEqualTo(0);
    }

    @Test
    public void loadAudioZones_withCoreAudioRoutingAndValidPrimaryZonesFromHal() {
        mHALAudioZones.add(getCoreAudioZone());
        mAudioDeviceConfig.routingConfig =
                RoutingDeviceConfiguration.CONFIGURABLE_AUDIO_ENGINE_ROUTING;
        mAudioDeviceConfig.useCoreAudioVolume = true;
        CarAudioZonesHelperAudioControlHAL helper = createAudioZonesHelper();

        var audioZones = helper.loadAudioZones();

        expectWithMessage("Loaded audio zones with core audio routing")
                .that(audioZones.size()).isEqualTo(1);
        var primaryZone = audioZones.get(PRIMARY_AUDIO_ZONE);
        assertWithMessage("Loaded primary audio zone with core audio routing")
                .that(primaryZone).isNotNull();
        expectWithMessage("Loaded primary audio zone configs with core audio routing")
                .that(primaryZone.getAllCarAudioZoneConfigs()).hasSize(1);
    }

    @Test
    public void getCarAudioZoneIdToOccupantZoneIdMapping_withPrimaryZoneMapped() {
        var primaryZone = createPrimaryAudioZone();
        primaryZone.occupantZoneId = TEST_PRIMARY_ZONE_OCCUPANT_ID;
        mHALAudioZones.add(primaryZone);
        CarAudioZonesHelperAudioControlHAL helper = createAudioZonesHelper();
        helper.loadAudioZones();

        var audioZoneIdToOccupantZoneId = helper.getCarAudioZoneIdToOccupantZoneIdMapping();

        expectWithMessage("Loaded audio zones to occupant zone mapping")
                .that(audioZoneIdToOccupantZoneId.size()).isEqualTo(1);
        expectWithMessage("Loaded primary occupant zone mapping").that(audioZoneIdToOccupantZoneId
                .get(PRIMARY_AUDIO_ZONE)).isEqualTo(TEST_PRIMARY_ZONE_OCCUPANT_ID);
    }

    @Test
    public void getCarAudioZoneIdToOccupantZoneIdMapping_withPrimaryAndSecondaryZoneMapped() {
        var primaryZone = createPrimaryAudioZone();
        primaryZone.occupantZoneId = TEST_PRIMARY_ZONE_OCCUPANT_ID;
        var secondaryZone = createSecondaryAudioZone();
        secondaryZone.occupantZoneId = TEST_SECONDARY_ZONE_OCCUPANT_ID;
        mHALAudioZones.add(primaryZone);
        mHALAudioZones.add(secondaryZone);
        CarAudioZonesHelperAudioControlHAL helper = createAudioZonesHelper();
        helper.loadAudioZones();

        var audioZoneIdToOccupantZoneId = helper.getCarAudioZoneIdToOccupantZoneIdMapping();

        expectWithMessage("Loaded audio zones to occupant zone mapping with primary and secondary"
                + " zones").that(audioZoneIdToOccupantZoneId.size()).isEqualTo(2);
        expectWithMessage("Loaded primary occupant zone with both primary and secondary zones")
                .that(audioZoneIdToOccupantZoneId.get(PRIMARY_AUDIO_ZONE))
                .isEqualTo(TEST_PRIMARY_ZONE_OCCUPANT_ID);
        expectWithMessage("Loaded secondary  occupant zone mapping with both primary and secondary"
                + " zones").that(audioZoneIdToOccupantZoneId.get(SECONDARY_ZONE_ID))
                .isEqualTo(TEST_SECONDARY_ZONE_OCCUPANT_ID);
    }

    @Test
    public void getCarAudioZoneIdToOccupantZoneIdMapping_withLoadFailures() {
        var secondaryZone = createSecondaryAudioZone();
        secondaryZone.occupantZoneId = TEST_SECONDARY_ZONE_OCCUPANT_ID;
        mHALAudioZones.add(secondaryZone);
        CarAudioZonesHelperAudioControlHAL helper = createAudioZonesHelper();
        helper.loadAudioZones();

        var audioZoneIdToOccupantZoneId = helper.getCarAudioZoneIdToOccupantZoneIdMapping();

        expectWithMessage("Loaded audio zones to occupant zone mapping with load zones failure")
                .that(audioZoneIdToOccupantZoneId.size()).isEqualTo(0);

    }

    private CarAudioZonesHelperAudioControlHAL createAudioZonesHelper() {
        return new CarAudioZonesHelperAudioControlHAL(mAudioControlWrapper, mAudioManager,
                mCarAudioSettings, mServiceLog, mUseAudioFadeManager);
    }
}
