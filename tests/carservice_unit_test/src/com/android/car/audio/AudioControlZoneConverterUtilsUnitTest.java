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

import static android.hardware.automotive.audiocontrol.VolumeInvocationType.ON_BOOT;
import static android.hardware.automotive.audiocontrol.VolumeInvocationType.ON_PLAYBACK_CHANGED;
import static android.hardware.automotive.audiocontrol.VolumeInvocationType.ON_SOURCE_CHANGED;

import static com.android.car.audio.CarActivationVolumeConfig.ACTIVATION_VOLUME_ON_BOOT;
import static com.android.car.audio.CarActivationVolumeConfig.ACTIVATION_VOLUME_ON_PLAYBACK_CHANGED;
import static com.android.car.audio.CarActivationVolumeConfig.ACTIVATION_VOLUME_ON_SOURCE_CHANGED;
import static com.android.car.audio.CarAudioUtils.DEFAULT_ACTIVATION_VOLUME;

import android.car.test.AbstractExpectableTestCase;
import android.hardware.automotive.audiocontrol.VolumeActivationConfiguration;
import android.hardware.automotive.audiocontrol.VolumeActivationConfigurationEntry;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;


@RunWith(AndroidJUnit4.class)
public class AudioControlZoneConverterUtilsUnitTest extends AbstractExpectableTestCase {

    public static final String TEST_ACTIVATION = "Test Activation";
    public static final int TEST_MIN_ACTIVATION = 10;
    public static final int TEST_MAX_ACTIVATION = 90;
    private static final CarActivationVolumeConfig TEST_ON_PLAY_ACTIVATION_CONFIG =
            new CarActivationVolumeConfig(ACTIVATION_VOLUME_ON_PLAYBACK_CHANGED,
                    TEST_MIN_ACTIVATION, TEST_MAX_ACTIVATION);
    private static final CarActivationVolumeConfig TEST_ON_SOURCE_ACTIVATION_CONFIG =
            new CarActivationVolumeConfig(ACTIVATION_VOLUME_ON_SOURCE_CHANGED,
                    TEST_MIN_ACTIVATION, TEST_MAX_ACTIVATION);
    private static final CarActivationVolumeConfig TEST_ON_BOOT_ACTIVATION_CONFIG =
            new CarActivationVolumeConfig(ACTIVATION_VOLUME_ON_BOOT,
                    TEST_MIN_ACTIVATION, TEST_MAX_ACTIVATION);
    private static final int INVALID_MIN_ACTIVATION = -1;
    private static final int INVALID_MAX_ACTIVATION = 101;

    @Test
    public void convertVolumeActivationConfig_withOnPlaybackActivation() {
        VolumeActivationConfiguration configuration =
                createVolumeActivationConfiguration(TEST_ACTIVATION, TEST_MIN_ACTIVATION,
                        TEST_MAX_ACTIVATION, ON_PLAYBACK_CHANGED);

        CarActivationVolumeConfig converted =
                AudioControlZoneConverterUtils.convertVolumeActivationConfig(configuration);

        expectWithMessage("Converted volume activation configuration with on playback activation")
                .that(converted).isEqualTo(TEST_ON_PLAY_ACTIVATION_CONFIG);
    }

    @Test
    public void convertVolumeActivationConfig_withOnSourceActivation() {
        VolumeActivationConfiguration configuration =
                createVolumeActivationConfiguration(TEST_ACTIVATION, TEST_MIN_ACTIVATION,
                        TEST_MAX_ACTIVATION, ON_SOURCE_CHANGED);

        CarActivationVolumeConfig converted =
                AudioControlZoneConverterUtils.convertVolumeActivationConfig(configuration);

        expectWithMessage("Converted volume activation configuration with on source activation")
                .that(converted).isEqualTo(TEST_ON_SOURCE_ACTIVATION_CONFIG);
    }

    @Test
    public void convertVolumeActivationConfig_withOnBootActivation() {
        VolumeActivationConfiguration configuration =
                createVolumeActivationConfiguration(TEST_ACTIVATION, TEST_MIN_ACTIVATION,
                        TEST_MAX_ACTIVATION, ON_BOOT);

        CarActivationVolumeConfig converted =
                AudioControlZoneConverterUtils.convertVolumeActivationConfig(configuration);

        expectWithMessage("Converted volume activation configuration with on boot activation")
                .that(converted).isEqualTo(TEST_ON_BOOT_ACTIVATION_CONFIG);
    }
    @Test
    public void convertVolumeActivationConfig_withInvalidActivation() {
        VolumeActivationConfiguration configuration =
                createVolumeActivationConfiguration(TEST_ACTIVATION, TEST_MIN_ACTIVATION,
                        TEST_MAX_ACTIVATION, /* activation= */ -1);

        CarActivationVolumeConfig converted =
                AudioControlZoneConverterUtils.convertVolumeActivationConfig(configuration);

        expectWithMessage("Converted volume activation configuration with on boot activation")
                .that(converted).isEqualTo(DEFAULT_ACTIVATION_VOLUME);
    }

    @Test
    public void convertVolumeActivationConfig_withEmptyActivationEntry() {
        VolumeActivationConfiguration configuration = new VolumeActivationConfiguration();
        configuration.name = TEST_ACTIVATION;
        configuration.volumeActivationEntries = List.of();

        CarActivationVolumeConfig converted =
                AudioControlZoneConverterUtils.convertVolumeActivationConfig(configuration);

        expectWithMessage("Converted volume activation configuration with on playback activation")
                .that(converted).isEqualTo(DEFAULT_ACTIVATION_VOLUME);
    }

    @Test
    public void convertVolumeActivationConfig_withInvalidMin() {
        VolumeActivationConfiguration configuration =
                createVolumeActivationConfiguration(TEST_ACTIVATION, INVALID_MIN_ACTIVATION,
                        TEST_MAX_ACTIVATION, ON_PLAYBACK_CHANGED);

        CarActivationVolumeConfig converted =
                AudioControlZoneConverterUtils.convertVolumeActivationConfig(configuration);

        expectWithMessage("Converted volume activation configuration with invalid min")
                .that(converted).isEqualTo(DEFAULT_ACTIVATION_VOLUME);
    }

    @Test
    public void convertVolumeActivationConfig_withInvalidMax() {
        VolumeActivationConfiguration configuration =
                createVolumeActivationConfiguration(TEST_ACTIVATION, TEST_MIN_ACTIVATION,
                        INVALID_MAX_ACTIVATION, ON_PLAYBACK_CHANGED);

        CarActivationVolumeConfig converted =
                AudioControlZoneConverterUtils.convertVolumeActivationConfig(configuration);

        expectWithMessage("Converted volume activation configuration with invalid max")
                .that(converted).isEqualTo(DEFAULT_ACTIVATION_VOLUME);
    }

    @Test
    public void convertVolumeActivationConfig_withInvalidMinMaxCombination() {
        VolumeActivationConfiguration configuration =
                createVolumeActivationConfiguration(TEST_ACTIVATION, TEST_MAX_ACTIVATION,
                        TEST_MIN_ACTIVATION, ON_PLAYBACK_CHANGED);

        CarActivationVolumeConfig converted =
                AudioControlZoneConverterUtils.convertVolumeActivationConfig(configuration);

        expectWithMessage("Converted volume activation configuration with invalid min and "
                + "max combination").that(converted).isEqualTo(DEFAULT_ACTIVATION_VOLUME);
    }

    private VolumeActivationConfiguration createVolumeActivationConfiguration(String name,
            int min, int max, int activation) {
        VolumeActivationConfiguration configuration = new VolumeActivationConfiguration();
        configuration.name = name;

        VolumeActivationConfigurationEntry entry = new VolumeActivationConfigurationEntry();
        entry.minActivationVolumePercentage = min;
        entry.maxActivationVolumePercentage = max;
        entry.type = activation;

        configuration.volumeActivationEntries = new ArrayList<>(1);
        configuration.volumeActivationEntries.add(entry);

        return configuration;
    }
}
