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

import static com.android.car.audio.CarAudioUtils.DEFAULT_ACTIVATION_VOLUME;
import static com.android.car.audio.CarAudioUtils.isInvalidActivationPercentage;
import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.PRIVATE_CONSTRUCTOR;

import android.car.builtin.util.Slogf;
import android.hardware.automotive.audiocontrol.VolumeActivationConfiguration;
import android.hardware.automotive.audiocontrol.VolumeActivationConfigurationEntry;
import android.media.AudioDeviceInfo;
import android.media.audio.common.AudioDeviceDescription;
import android.media.audio.common.AudioDeviceType;

import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;

import java.util.List;

/**
 * Utility class for general audio control conversion methods
 */
class AudioControlZoneConverterUtils {

    private static final String TAG = AudioControlZoneConverterUtils.class.getSimpleName();

    @ExcludeFromCodeCoverageGeneratedReport(reason = PRIVATE_CONSTRUCTOR)
    private AudioControlZoneConverterUtils() {
        throw new UnsupportedOperationException();
    }

    static int convertToAudioDeviceInfoType(@AudioDeviceType int type, String connection) {
        switch (type) {
            case AudioDeviceType.OUT_SPEAKER:
                if (connection.equals(AudioDeviceDescription.CONNECTION_BT_A2DP)) {
                    return AudioDeviceInfo.TYPE_BLUETOOTH_A2DP;
                }
                if (connection.equals(AudioDeviceDescription.CONNECTION_BT_LE)) {
                    return AudioDeviceInfo.TYPE_BLE_SPEAKER;
                }
                return AudioDeviceInfo.TYPE_BUILTIN_SPEAKER;
            case AudioDeviceType.OUT_HEADPHONE:
                if (connection.equals(AudioDeviceDescription.CONNECTION_ANALOG)) {
                    return AudioDeviceInfo.TYPE_WIRED_HEADPHONES;
                }
                return AudioDeviceInfo.TYPE_BLUETOOTH_A2DP;
            case AudioDeviceType.OUT_HEADSET:
                return switch (connection) {
                    case AudioDeviceDescription.CONNECTION_ANALOG ->
                            AudioDeviceInfo.TYPE_WIRED_HEADSET;
                    case AudioDeviceDescription.CONNECTION_BT_LE ->
                            AudioDeviceInfo.TYPE_BLE_HEADSET;
                    case AudioDeviceDescription.CONNECTION_BT_SCO ->
                            AudioDeviceInfo.TYPE_BLUETOOTH_SCO;
                    default -> AudioDeviceInfo.TYPE_USB_HEADSET;
                };
            case AudioDeviceType.OUT_ACCESSORY:
                return AudioDeviceInfo.TYPE_USB_ACCESSORY;
            case AudioDeviceType.OUT_LINE_AUX:
                return AudioDeviceInfo.TYPE_AUX_LINE;
            case AudioDeviceType.OUT_BROADCAST:
                return AudioDeviceInfo.TYPE_BLE_BROADCAST;
            case AudioDeviceType.OUT_HEARING_AID:
                return AudioDeviceInfo.TYPE_HEARING_AID;
            case AudioDeviceType.OUT_DEVICE: // OUT_BUS and OUT_DEVICE are mapped to the same enum
            default:
                return switch (connection) {
                    case AudioDeviceDescription.CONNECTION_BT_A2DP ->
                            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP;
                    case AudioDeviceDescription.CONNECTION_IP_V4 -> AudioDeviceInfo.TYPE_IP;
                    case AudioDeviceDescription.CONNECTION_HDMI_ARC ->
                            AudioDeviceInfo.TYPE_HDMI_ARC;
                    case AudioDeviceDescription.CONNECTION_HDMI_EARC ->
                            AudioDeviceInfo.TYPE_HDMI_EARC;
                    case AudioDeviceDescription.CONNECTION_HDMI -> AudioDeviceInfo.TYPE_HDMI;
                    case AudioDeviceDescription.CONNECTION_ANALOG ->
                            AudioDeviceInfo.TYPE_LINE_ANALOG;
                    case AudioDeviceDescription.CONNECTION_USB -> AudioDeviceInfo.TYPE_USB_DEVICE;
                    case AudioDeviceDescription.CONNECTION_SPDIF ->
                            AudioDeviceInfo.TYPE_LINE_DIGITAL;
                    default -> AudioDeviceInfo.TYPE_BUS;
                };
        }
    }

    static CarActivationVolumeConfig convertVolumeActivationConfig(
            VolumeActivationConfiguration activationConfiguration) {
        if (activationConfiguration == null) {
            return DEFAULT_ACTIVATION_VOLUME;
        }
        List<VolumeActivationConfigurationEntry> entries =
                activationConfiguration.volumeActivationEntries;
        if (entries == null || entries.isEmpty()) {
            Slogf.e(TAG, "Empty volume activation entry for activation config "
                    + activationConfiguration.name);
            return DEFAULT_ACTIVATION_VOLUME;
        }
        //Currently car only supports a single configuration entry
        VolumeActivationConfigurationEntry entry =
                activationConfiguration.volumeActivationEntries.get(0);
        if (isInvalidActivationPercentage(entry.maxActivationVolumePercentage)) {
            Slogf.e(TAG, "Invalid volume max activation value, value must be between 0 and 100");
            return DEFAULT_ACTIVATION_VOLUME;
        }
        if (isInvalidActivationPercentage(entry.minActivationVolumePercentage)) {
            Slogf.e(TAG, "Invalid volume min activation value, value must be between 0 and 100");
            return DEFAULT_ACTIVATION_VOLUME;
        }
        int maxActivation = entry.maxActivationVolumePercentage;
        int minActivation = entry.minActivationVolumePercentage;
        if (maxActivation <= minActivation) {
            Slogf.e(TAG, "Invalid volume activation values, min = "
                    + minActivation + " greater than max = " + maxActivation
                    + " for activation config " + activationConfiguration.name);
            return DEFAULT_ACTIVATION_VOLUME;
        }
        int activationType;
        switch (entry.type) {
            case ON_PLAYBACK_CHANGED:
                activationType = CarActivationVolumeConfig.ACTIVATION_VOLUME_ON_PLAYBACK_CHANGED;
                break;
            case ON_SOURCE_CHANGED:
                activationType = CarActivationVolumeConfig.ACTIVATION_VOLUME_ON_SOURCE_CHANGED;
                break;
            case ON_BOOT:
                activationType = CarActivationVolumeConfig.ACTIVATION_VOLUME_ON_BOOT;
                break;
            default:
                Slogf.e(TAG, "Invalid activation type " + entry.type + " for "
                        + activationConfiguration.name);
                return DEFAULT_ACTIVATION_VOLUME;
        }
        return new CarActivationVolumeConfig(activationType, minActivation, maxActivation);
    }
}
