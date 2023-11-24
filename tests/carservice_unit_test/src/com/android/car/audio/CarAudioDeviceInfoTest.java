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

package com.android.car.audio;

import static android.media.AudioFormat.CHANNEL_OUT_MONO;
import static android.media.AudioFormat.CHANNEL_OUT_QUAD;
import static android.media.AudioFormat.CHANNEL_OUT_STEREO;
import static android.media.AudioFormat.ENCODING_PCM_16BIT;

import static com.android.car.audio.CarAudioDeviceInfo.DEFAULT_SAMPLE_RATE;
import static com.android.car.audio.GainBuilder.MAX_GAIN;
import static com.android.car.audio.GainBuilder.MIN_GAIN;
import static com.android.car.audio.GainBuilder.STEP_SIZE;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.media.AudioDeviceAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioGain;
import android.media.AudioManager;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

@RunWith(AndroidJUnit4.class)
public class CarAudioDeviceInfoTest {

    private static final String TEST_ADDRESS = "test address";

    @Mock
    private AudioManager mAudioManager;

    @Test
    public void setAudioDeviceInfo_requiresNonNullGain() {
        AudioDeviceAttributes attributes = getMockAudioDevice();
        CarAudioDeviceInfo info = new CarAudioDeviceInfo(mAudioManager, attributes);
        AudioDeviceInfo audioDeviceInfo = mock(AudioDeviceInfo.class);
        when(audioDeviceInfo.getPort()).thenReturn(null);

        Throwable thrown = assertThrows(NullPointerException.class,
                () -> info.setAudioDeviceInfo(audioDeviceInfo));

        assertWithMessage("Null port exception")
                .that(thrown).hasMessageThat().contains("Audio device port");
    }

    @Test
    public void setAudioDeviceInfo_requiresJointModeGain() {
        AudioDeviceAttributes attributes = getMockAudioDevice();
        CarAudioDeviceInfo info = new CarAudioDeviceInfo(mAudioManager, attributes);
        AudioGain gainWithChannelMode = new GainBuilder().setMode(AudioGain.MODE_CHANNELS).build();
        AudioDeviceInfo audioDeviceInfo = getMockAudioDeviceInfo(
                new AudioGain[]{gainWithChannelMode});

        Throwable thrown = assertThrows(IllegalStateException.class,
                () -> info.setAudioDeviceInfo(audioDeviceInfo));

        assertWithMessage("Null gain exception")
                .that(thrown).hasMessageThat().contains("audio gain");
    }

    @Test
    public void setAudioDeviceInfo_requiresMaxGainLargerThanMin() {
        AudioDeviceAttributes attributes = getMockAudioDevice();
        CarAudioDeviceInfo info = new CarAudioDeviceInfo(mAudioManager, attributes);
        AudioGain gainWithChannelMode = new GainBuilder().setMaxValue(10).setMinValue(20).build();
        AudioDeviceInfo audioDeviceInfo = getMockAudioDeviceInfo(
                new AudioGain[]{gainWithChannelMode});

        Throwable thrown = assertThrows(IllegalArgumentException.class,
                () -> info.setAudioDeviceInfo(audioDeviceInfo));

        assertWithMessage("Min gain larger than max exception")
                .that(thrown).hasMessageThat().contains("lower than");
    }

    @Test
    public void setAudioDeviceInfo_requiresDefaultGainLargerThanMin() {
        AudioDeviceAttributes attributes = getMockAudioDevice();
        CarAudioDeviceInfo info = new CarAudioDeviceInfo(mAudioManager, attributes);
        AudioGain gainWithChannelMode = new GainBuilder().setDefaultValue(10).setMinValue(
                20).build();
        AudioDeviceInfo audioDeviceInfo = getMockAudioDeviceInfo(
                new AudioGain[]{gainWithChannelMode});

        Throwable thrown = assertThrows(IllegalArgumentException.class,
                () -> info.setAudioDeviceInfo(audioDeviceInfo));

        assertWithMessage("Default gain lower than min exception")
                .that(thrown).hasMessageThat().contains("not in range");
    }

    @Test
    public void setAudioDeviceInfo_requiresDefaultGainSmallerThanMax() {
        AudioDeviceAttributes attributes = getMockAudioDevice();
        CarAudioDeviceInfo info = new CarAudioDeviceInfo(mAudioManager, attributes);
        AudioGain gainWithChannelMode = new GainBuilder().setDefaultValue(15).setMaxValue(
                10).build();
        AudioDeviceInfo audioDeviceInfo = getMockAudioDeviceInfo(
                new AudioGain[]{gainWithChannelMode});

        Throwable thrown = assertThrows(IllegalArgumentException.class,
                () -> info.setAudioDeviceInfo(audioDeviceInfo));

        assertWithMessage("Default gain larger than max exception")
                .that(thrown).hasMessageThat().contains("not in range");
    }

    @Test
    public void setAudioDeviceInfo_requiresGainStepSizeFactorOfRange() {
        AudioDeviceAttributes attributes = getMockAudioDevice();
        CarAudioDeviceInfo info = new CarAudioDeviceInfo(mAudioManager, attributes);
        AudioGain gainWithChannelMode = new GainBuilder().setStepSize(7).build();
        AudioDeviceInfo audioDeviceInfo = getMockAudioDeviceInfo(
                new AudioGain[]{gainWithChannelMode});

        Throwable thrown = assertThrows(IllegalArgumentException.class,
                () -> info.setAudioDeviceInfo(audioDeviceInfo));

        assertWithMessage("Gain step not a factor of range exception")
                .that(thrown).hasMessageThat().contains("greater than min gain to max gain range");
    }

    @Test
    public void setAudioDeviceInfo_requiresGainStepSizeFactorOfRangeToDefault() {
        AudioDeviceAttributes attributes = getMockAudioDevice();
        CarAudioDeviceInfo info = new CarAudioDeviceInfo(mAudioManager, attributes);
        AudioGain gainWithChannelMode = new GainBuilder().setStepSize(7).setMaxValue(98).build();
        AudioDeviceInfo audioDeviceInfo = getMockAudioDeviceInfo(
                new AudioGain[]{gainWithChannelMode});

        Throwable thrown = assertThrows(IllegalArgumentException.class,
                () -> info.setAudioDeviceInfo(audioDeviceInfo));

        assertWithMessage("Default gain factor of step exception")
                .that(thrown).hasMessageThat()
                .contains("greater than min gain to default gain range");
    }

    @Test
    public void isActive_beforeSettingAudioDevice() {
        AudioDeviceAttributes attributes = getMockAudioDevice();
        CarAudioDeviceInfo info = new CarAudioDeviceInfo(mAudioManager, attributes);

        assertWithMessage("Default is active status").that(info.isActive())
                .isFalse();
    }

    @Test
    public void isActive_afterSettingDeviceInfo() {
        AudioDeviceAttributes attributes = getMockAudioDevice();
        CarAudioDeviceInfo info = new CarAudioDeviceInfo(mAudioManager, attributes);
        info.setAudioDeviceInfo(getMockAudioDeviceInfo());

        assertWithMessage("Is active status").that(info.isActive()).isTrue();
    }

    @Test
    public void isActive_afterResettingAudioDeviceToNull() {
        AudioDeviceAttributes attributes = getMockAudioDevice();
        CarAudioDeviceInfo info = new CarAudioDeviceInfo(mAudioManager, attributes);
        info.setAudioDeviceInfo(getMockAudioDeviceInfo());
        info.setAudioDeviceInfo(null);

        assertWithMessage("Is active status, after becoming inactive")
                .that(info.isActive()).isFalse();
    }

    @Test
    public void getSampleRate_withMultipleSampleRates_returnsMax() {
        AudioDeviceAttributes audioDevice = getMockAudioDevice();
        AudioDeviceInfo deviceInfo = getMockAudioDeviceInfo();
        int[] sampleRates = new int[]{48000, 96000, 16000, 8000};
        when(deviceInfo.getSampleRates()).thenReturn(sampleRates);
        CarAudioDeviceInfo info = new CarAudioDeviceInfo(mAudioManager, audioDevice);
        info.setAudioDeviceInfo(deviceInfo);


        int sampleRate = info.getSampleRate();

        assertWithMessage("Sample rate").that(sampleRate).isEqualTo(96000);
    }

    @Test
    public void getSampleRate_withNullSampleRate_returnsDefault() {
        AudioDeviceAttributes attributes = getMockAudioDevice();
        CarAudioDeviceInfo info = new CarAudioDeviceInfo(mAudioManager, attributes);

        int sampleRate = info.getSampleRate();

        assertWithMessage("Sample Rate").that(sampleRate).isEqualTo(DEFAULT_SAMPLE_RATE);
    }

    @Test
    public void getAddress_returnsValueFromDeviceInfo() {
        AudioDeviceAttributes attributes = getMockAudioDevice();
        CarAudioDeviceInfo info = new CarAudioDeviceInfo(mAudioManager, attributes);

        assertWithMessage("Device Info Address").that(info.getAddress()).isEqualTo(TEST_ADDRESS);
    }

    @Test
    public void getMaxGain_returnsValueFromDeviceInfo() {
        AudioDeviceAttributes attributes = getMockAudioDevice();
        CarAudioDeviceInfo info = new CarAudioDeviceInfo(mAudioManager, attributes);
        info.setAudioDeviceInfo(getMockAudioDeviceInfo());

        assertWithMessage("Device Info Max Gain")
                .that(info.getMaxGain()).isEqualTo(MAX_GAIN);
    }

    @Test
    public void getMinGain_returnsValueFromDeviceInfo() {
        AudioDeviceAttributes attributes = getMockAudioDevice();
        CarAudioDeviceInfo info = new CarAudioDeviceInfo(mAudioManager, attributes);
        info.setAudioDeviceInfo(getMockAudioDeviceInfo());

        assertWithMessage("Device Info Min Gain")
                .that(info.getMinGain()).isEqualTo(MIN_GAIN);
    }

    @Test
    public void getDefaultGain_returnsValueFromDeviceInfo() {
        AudioDeviceAttributes attributes = getMockAudioDevice();
        CarAudioDeviceInfo info = new CarAudioDeviceInfo(mAudioManager, attributes);
        info.setAudioDeviceInfo(getMockAudioDeviceInfo());

        assertWithMessage("Device Info Default Gain").that(info.getDefaultGain())
                .isEqualTo(GainBuilder.DEFAULT_GAIN);
    }

    @Test
    public void getStepValue_returnsValueFromDeviceInfo() {
        AudioDeviceAttributes attributes = getMockAudioDevice();
        CarAudioDeviceInfo info = new CarAudioDeviceInfo(mAudioManager, attributes);
        info.setAudioDeviceInfo(getMockAudioDeviceInfo());

        assertWithMessage("Device Info Step Vale").that(info.getStepValue())
                .isEqualTo(STEP_SIZE);
    }

    @Test
    public void getChannelCount_withNoChannelMasks_returnsOne() {
        AudioDeviceAttributes attributes = getMockAudioDevice();
        CarAudioDeviceInfo info = new CarAudioDeviceInfo(mAudioManager, attributes);
        info.setAudioDeviceInfo(getMockAudioDeviceInfo());

        int channelCount = info.getChannelCount();

        assertWithMessage("Channel Count").that(channelCount).isEqualTo(1);
    }

    @Test
    public void getChannelCount_withMultipleChannels_returnsHighestCount() {
        AudioDeviceAttributes audioDeviceAttribute = getMockAudioDevice();
        AudioDeviceInfo deviceInfo = getMockAudioDeviceInfo();
        when(deviceInfo.getChannelMasks()).thenReturn(new int[]{CHANNEL_OUT_STEREO,
                CHANNEL_OUT_QUAD, CHANNEL_OUT_MONO});
        CarAudioDeviceInfo info = new CarAudioDeviceInfo(mAudioManager, audioDeviceAttribute);
        info.setAudioDeviceInfo(deviceInfo);

        int channelCount = info.getChannelCount();

        assertWithMessage("Channel Count").that(channelCount).isEqualTo(4);
    }

    @Test
    public void getAudioDevice_returnsConstructorParameter() {
        AudioDeviceAttributes audioDevice = getMockAudioDevice();
        CarAudioDeviceInfo info = new CarAudioDeviceInfo(mAudioManager, audioDevice);

        assertWithMessage("Device Info Audio Device Attributes")
                .that(info.getAudioDevice()).isEqualTo(audioDevice);
    }

    @Test
    public void getEncodingFormat_returnsPCM16() {
        AudioDeviceAttributes attributes = getMockAudioDevice();
        CarAudioDeviceInfo info = new CarAudioDeviceInfo(mAudioManager, attributes);

        assertWithMessage("Device Info Audio Encoding Format")
                .that(info.getEncodingFormat()).isEqualTo(ENCODING_PCM_16BIT);
    }

    @Test
    public void defaultDynamicPolicyMix_enabled() {
        AudioDeviceAttributes attributes = getMockAudioDevice();
        CarAudioDeviceInfo info = new CarAudioDeviceInfo(mAudioManager, attributes);

        boolean initialState = info.canBeRoutedWithDynamicPolicyMix();
        assertWithMessage("Dynamic policy mix is enabled by default on Devices")
                .that(info.canBeRoutedWithDynamicPolicyMix())
                .isEqualTo(true);
    }

    @Test
    public void setGetCanBeRoutedWithDynamicPolicyMix() {
        AudioDeviceAttributes attributes = getMockAudioDevice();
        CarAudioDeviceInfo info = new CarAudioDeviceInfo(mAudioManager, attributes);

        info.resetCanBeRoutedWithDynamicPolicyMix();

        assertWithMessage("Setting and getting opposite from initial dynamic policy mix state")
                .that(info.canBeRoutedWithDynamicPolicyMix())
                .isEqualTo(false);
    }

    @Test
    public void resetGetCanBeRoutedWithDynamicPolicyMix_isSticky() {
        AudioDeviceAttributes attributes = getMockAudioDevice();
        CarAudioDeviceInfo info = new CarAudioDeviceInfo(mAudioManager, attributes);

        info.resetCanBeRoutedWithDynamicPolicyMix();
        // Setting twice, no-op, reset is fused.
        info.resetCanBeRoutedWithDynamicPolicyMix();

        assertWithMessage("Keeping state forever")
                .that(info.canBeRoutedWithDynamicPolicyMix())
                .isEqualTo(false);
    }

    private AudioDeviceInfo getMockAudioDeviceInfo() {
        AudioGain mockGain = new GainBuilder().build();
        return getMockAudioDeviceInfo(new AudioGain[]{mockGain});
    }

    private AudioDeviceInfo getMockAudioDeviceInfo(AudioGain[] gains) {
        return new AudioDeviceInfoBuilder()
                .setAddressName(TEST_ADDRESS)
                .setAudioGains(gains)
                .build();
    }

    private AudioDeviceAttributes getMockAudioDevice() {
        AudioDeviceAttributes attributeMock =  mock(AudioDeviceAttributes.class);
        when(attributeMock.getAddress()).thenReturn(TEST_ADDRESS);

        return attributeMock;
    }
}
