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
import static android.media.AudioAttributes.USAGE_ALARM;
import static android.media.AudioAttributes.USAGE_ANNOUNCEMENT;
import static android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE;
import static android.media.AudioAttributes.USAGE_ASSISTANCE_SONIFICATION;
import static android.media.AudioAttributes.USAGE_ASSISTANT;
import static android.media.AudioAttributes.USAGE_EMERGENCY;
import static android.media.AudioAttributes.USAGE_GAME;
import static android.media.AudioAttributes.USAGE_MEDIA;
import static android.media.AudioAttributes.USAGE_NOTIFICATION;
import static android.media.AudioAttributes.USAGE_NOTIFICATION_EVENT;
import static android.media.AudioAttributes.USAGE_NOTIFICATION_RINGTONE;
import static android.media.AudioAttributes.USAGE_SAFETY;
import static android.media.AudioAttributes.USAGE_UNKNOWN;
import static android.media.AudioAttributes.USAGE_VEHICLE_STATUS;
import static android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION;
import static android.media.audio.common.AudioUsage.ALARM;
import static android.media.audio.common.AudioUsage.ANNOUNCEMENT;
import static android.media.audio.common.AudioUsage.ASSISTANCE_ACCESSIBILITY;
import static android.media.audio.common.AudioUsage.ASSISTANCE_NAVIGATION_GUIDANCE;
import static android.media.audio.common.AudioUsage.ASSISTANCE_SONIFICATION;
import static android.media.audio.common.AudioUsage.ASSISTANT;
import static android.media.audio.common.AudioUsage.CALL_ASSISTANT;
import static android.media.audio.common.AudioUsage.EMERGENCY;
import static android.media.audio.common.AudioUsage.GAME;
import static android.media.audio.common.AudioUsage.MEDIA;
import static android.media.audio.common.AudioUsage.NOTIFICATION;
import static android.media.audio.common.AudioUsage.NOTIFICATION_EVENT;
import static android.media.audio.common.AudioUsage.NOTIFICATION_TELEPHONY_RINGTONE;
import static android.media.audio.common.AudioUsage.SAFETY;
import static android.media.audio.common.AudioUsage.UNKNOWN;
import static android.media.audio.common.AudioUsage.VEHICLE_STATUS;
import static android.media.audio.common.AudioUsage.VOICE_COMMUNICATION;
import static android.media.audio.common.AudioUsage.VOICE_COMMUNICATION_SIGNALLING;

import static com.android.car.audio.CarAudioContext.getAudioAttributeFromUsage;
import static com.android.car.audio.CarAudioDeviceInfoTestUtils.ALARM_TEST_DEVICE;
import static com.android.car.audio.CarAudioDeviceInfoTestUtils.CALL_TEST_DEVICE;
import static com.android.car.audio.CarAudioDeviceInfoTestUtils.MEDIA_TEST_DEVICE;
import static com.android.car.audio.CarAudioDeviceInfoTestUtils.NAVIGATION_TEST_DEVICE;
import static com.android.car.audio.CarAudioDeviceInfoTestUtils.NOTIFICATION_TEST_DEVICE;
import static com.android.car.audio.CarAudioDeviceInfoTestUtils.RING_TEST_DEVICE;
import static com.android.car.audio.CarAudioDeviceInfoTestUtils.SYSTEM_BUS_DEVICE;
import static com.android.car.audio.CarAudioDeviceInfoTestUtils.VOICE_TEST_DEVICE;
import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.BOILERPLATE_CODE;

import android.car.builtin.media.AudioManagerHelper;
import android.hardware.automotive.audiocontrol.AudioZone;
import android.hardware.automotive.audiocontrol.AudioZoneConfig;
import android.hardware.automotive.audiocontrol.AudioZoneContext;
import android.hardware.automotive.audiocontrol.AudioZoneContextInfo;
import android.hardware.automotive.audiocontrol.DeviceToContextEntry;
import android.hardware.automotive.audiocontrol.VolumeActivationConfiguration;
import android.hardware.automotive.audiocontrol.VolumeActivationConfigurationEntry;
import android.hardware.automotive.audiocontrol.VolumeGroupConfig;
import android.media.AudioAttributes;
import android.media.AudioFocusInfo;
import android.media.AudioManager;
import android.media.audio.common.AudioDevice;
import android.media.audio.common.AudioDeviceAddress;
import android.media.audio.common.AudioDeviceDescription;
import android.media.audio.common.AudioDeviceType;
import android.media.audio.common.AudioGain;
import android.media.audio.common.AudioGainMode;
import android.media.audio.common.AudioPort;
import android.media.audio.common.AudioPortDeviceExt;
import android.media.audio.common.AudioPortExt;
import android.os.Build;
import android.util.ArrayMap;

import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;

import java.util.ArrayList;
import java.util.List;


@ExcludeFromCodeCoverageGeneratedReport(reason = BOILERPLATE_CODE)
public final class CarAudioTestUtils {

    private static final String PACKAGE_NAME = "com.android.car.audio";
    private static final int AUDIOFOCUS_FLAG = 0;

    public static final AudioAttributes TEST_UNKNOWN_USAGE_ATTRIBUTE =
            getAudioAttributeFromUsage(USAGE_UNKNOWN);
    public static final AudioAttributes TEST_GAME_USAGE_ATTRIBUTE =
            getAudioAttributeFromUsage(USAGE_GAME);
    public static final AudioAttributes TEST_CALL_ATTRIBUTE =
            getAudioAttributeFromUsage(USAGE_VOICE_COMMUNICATION);
    public static final AudioAttributes TEST_RINGER_ATTRIBUTE =
            getAudioAttributeFromUsage(USAGE_NOTIFICATION_RINGTONE);
    public static final AudioAttributes TEST_MEDIA_ATTRIBUTE =
            getAudioAttributeFromUsage(USAGE_MEDIA);
    public static final AudioAttributes TEST_EMERGENCY_ATTRIBUTE =
            getAudioAttributeFromUsage(USAGE_EMERGENCY);
    public static final AudioAttributes TEST_INVALID_ATTRIBUTE =
            getAudioAttributeFromUsage(AudioManagerHelper
                    .getUsageVirtualSource());
    public static final AudioAttributes TEST_NAVIGATION_ATTRIBUTE =
            getAudioAttributeFromUsage(
                    USAGE_ASSISTANCE_NAVIGATION_GUIDANCE);
    public static final AudioAttributes TEST_ASSISTANT_ATTRIBUTE =
            getAudioAttributeFromUsage(USAGE_ASSISTANT);
    public static final AudioAttributes TEST_ALARM_ATTRIBUTE =
            getAudioAttributeFromUsage(USAGE_ALARM);
    public static final AudioAttributes TEST_NOTIFICATION_ATTRIBUTE =
            getAudioAttributeFromUsage(USAGE_NOTIFICATION);
    public static final AudioAttributes TEST_SYSTEM_ATTRIBUTE =
            getAudioAttributeFromUsage(USAGE_ASSISTANCE_SONIFICATION);
    public static final AudioAttributes TEST_VEHICLE_ATTRIBUTE =
            getAudioAttributeFromUsage(USAGE_VEHICLE_STATUS);
    public static final AudioAttributes TEST_ANNOUNCEMENT_ATTRIBUTE =
            getAudioAttributeFromUsage(USAGE_ANNOUNCEMENT);
    public static final AudioAttributes TEST_SAFETY_ATTRIBUTE =
            getAudioAttributeFromUsage(USAGE_SAFETY);
    public static final AudioAttributes TEST_NOTIFICATION_EVENT_ATTRIBUTE =
            getAudioAttributeFromUsage(USAGE_NOTIFICATION_EVENT);

    public static final String TEST_ACTIVATION = "Test Activation";
    public static final int TEST_MIN_ACTIVATION = 10;
    public static final int TEST_MAX_ACTIVATION = 90;

    private static final CarAudioContextInfo TEST_CONTEXT_INFO_MUSIC =
            new CarAudioContextInfo(new AudioAttributes[] {
                    getAudioAttributeFromUsage(AudioAttributes.USAGE_UNKNOWN),
                    getAudioAttributeFromUsage(AudioAttributes.USAGE_GAME),
                    getAudioAttributeFromUsage(AudioAttributes.USAGE_MEDIA)
            }, "MUSIC", 1);

    private static final CarAudioContextInfo TEST_CONTEXT_INFO_NAVIGATION =
            new CarAudioContextInfo(new AudioAttributes[] {
                    getAudioAttributeFromUsage(AudioAttributes
                            .USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
            }, "NAVIGATION", 2);

    private static final CarAudioContextInfo TEST_CONTEXT_INFO_VOICE_COMMAND =
            new CarAudioContextInfo(new AudioAttributes[] {
                    getAudioAttributeFromUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY),
                    getAudioAttributeFromUsage(AudioAttributes.USAGE_ASSISTANT)
            }, "VOICE_COMMAND", 3);

    private static final CarAudioContextInfo TEST_CONTEXT_INFO_CALL_RING =
            new CarAudioContextInfo(new AudioAttributes[] {
                    getAudioAttributeFromUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
            }, "CALL_RING", 4);

    private static final CarAudioContextInfo TEST_CONTEXT_INFO_CALL =
            new CarAudioContextInfo(new AudioAttributes[] {
                    getAudioAttributeFromUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION),
                    getAudioAttributeFromUsage(AudioAttributes.USAGE_CALL_ASSISTANT),
                    getAudioAttributeFromUsage(AudioAttributes
                            .USAGE_VOICE_COMMUNICATION_SIGNALLING),
            }, "CALL", 5);

    private static final CarAudioContextInfo TEST_CONTEXT_INFO_ALARM =
            new CarAudioContextInfo(new AudioAttributes[]{
                    getAudioAttributeFromUsage(AudioAttributes.USAGE_ALARM)
            }, "ALARM", 6);

    private static final CarAudioContextInfo TEST_CONTEXT_INFO_NOTIFICATION =
            new CarAudioContextInfo(new AudioAttributes[]{
                    getAudioAttributeFromUsage(AudioAttributes.USAGE_NOTIFICATION),
                    getAudioAttributeFromUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
            }, "NOTIFICATION", 7);

    private static final CarAudioContextInfo TEST_CONTEXT_INFO_SYSTEM_SOUND =
            new CarAudioContextInfo(new AudioAttributes[]{
                    getAudioAttributeFromUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            }, "SYSTEM_SOUND", 8);

    private static final CarAudioContextInfo TEST_CONTEXT_INFO_EMERGENCY =
            new CarAudioContextInfo(new AudioAttributes[]{
                    getAudioAttributeFromUsage(AudioAttributes.USAGE_EMERGENCY)
            }, "EMERGENCY", 9);

    private static final CarAudioContextInfo TEST_CONTEXT_INFO_SAFETY =
            new CarAudioContextInfo(new AudioAttributes[]{
                    getAudioAttributeFromUsage(AudioAttributes.USAGE_SAFETY)
            }, "SAFETY", 10);

    private static final CarAudioContextInfo TEST_CONTEXT_INFO_VEHICLE_STATUS =
            new CarAudioContextInfo(new AudioAttributes[]{
                    getAudioAttributeFromUsage(AudioAttributes.USAGE_VEHICLE_STATUS)
            }, "VEHICLE_STATUS", 11);

    private static final CarAudioContextInfo TEST_CONTEXT_INFO_ANNOUNCEMENT =
            new CarAudioContextInfo(new AudioAttributes[]{
                    getAudioAttributeFromUsage(AudioAttributes.USAGE_ANNOUNCEMENT)
            }, "ANNOUNCEMENT", 12);

    public static final CarAudioContext TEST_CREATED_CAR_AUDIO_CONTEXT =
            new CarAudioContext(List.of(TEST_CONTEXT_INFO_MUSIC, TEST_CONTEXT_INFO_NAVIGATION,
                    TEST_CONTEXT_INFO_VOICE_COMMAND, TEST_CONTEXT_INFO_CALL_RING,
                    TEST_CONTEXT_INFO_CALL, TEST_CONTEXT_INFO_ALARM, TEST_CONTEXT_INFO_NOTIFICATION,
                    TEST_CONTEXT_INFO_SYSTEM_SOUND, TEST_CONTEXT_INFO_EMERGENCY,
                    TEST_CONTEXT_INFO_SAFETY, TEST_CONTEXT_INFO_VEHICLE_STATUS,
                    TEST_CONTEXT_INFO_ANNOUNCEMENT),
                    /* useCoreAudioRouting= */ false);

    public static final CarAudioContext TEST_CAR_AUDIO_CONTEXT =
            new CarAudioContext(CarAudioContext.getAllContextsInfo(),
                    /* useCoreAudioRouting= */ false);

    static final String MUSIC_CONTEXT = "MUSIC";
    static final String NAVIGATION_CONTEXT = "NAVIGATION";
    static final String VOICE_COMMAND_CONTEXT = "VOICE_COMMAND";
    static final String RING_CONTEXT = "CALL_RING";
    static final String NOTIFICATION_CONTEXT = "NOTIFICATION";
    static final String INVALID_CONTEXT_NAME = "no context name";
    static final int MUSIC_CONTEXT_ID = 1;
    static final int NAVIGATION_CONTEXT_ID = 2;
    static final int VOICE_COMMAND_CONTEXT_ID = 3;
    static final int RING_CONTEXT_ID = 4;
    static final int NOTIFICATION_CONTEXT_ID = 7;

    static final AudioGain[] GAINS = new AudioGain[] {
            new AudioGain() {{
                mode = AudioGainMode.JOINT;
                minValue = 0;
                maxValue = 100;
                defaultValue = 50;
                stepValue = 2;
            }}
    };
    public static final String ANNOUNCEMENT_CONTEXT = "ANNOUNCEMENT";
    public static final int ANNOUNCEMENT_CONTEXT_ID = 12;
    public static final String CALL_CONTEXT = "CALL";
    public static final int CALL_CONTEXT_ID = 5;
    public static final String ALARM_CONTEXT = "ALARM";
    public static final int ALARM_CONTEXT_ID = 6;
    public static final String SYSTEM_CONTEXT = "SYSTEM_SOUND";
    public static final int SYSTEM_CONTEXT_ID = 8;
    public static final String EMERGENCY_CONTEXT = "EMERGENCY";
    public static final int EMERGENCY_CONTEXT_ID = 9;
    public static final String SAFETY_CONTEXT = "SAFETY";
    public static final int SAFETY_CONTEXT_ID = 10;
    public static final String VEHICLE_STATUS_CONTEXT = "VEHICLE_STATUS";
    public static final int VEHICLE_STATUS_CONTEXT_ID = 11;

    public static final String PRIMARY_ZONE_NAME = "primary zone";

    static final int MEDIA_VOLUME_GROUP_ID = 0;
    static final String MEDIA_VOLUME_NAME = "media_volume";
    static final int NAVIGATION_AND_VOICE_VOLUME_GROUP_ID = 1;
    static final String NAVIGATION_AND_VOICE_VOLUME_NAME = "navigation_voice_volume";
    static final int TELEPHONY_VOLUME_GROUP_ID = 2;
    static final String TELEPHONY_VOLUME_NAME = "telephony_volume";
    static final int SYSTEM_VOLUME_GROUP_ID = 3;
    static final String SYSTEM_VOLUME_NAME = "system_volume";

    private CarAudioTestUtils() {
        throw new UnsupportedOperationException();
    }

    static AudioFocusInfo getInfo(AudioAttributes audioAttributes, String clientId, int gainType,
            boolean acceptsDelayedFocus, boolean pauseInsteadOfDucking, int uid) {
        int flags = AUDIOFOCUS_FLAG;
        if (acceptsDelayedFocus) {
            flags |= AudioManager.AUDIOFOCUS_FLAG_DELAY_OK;
        }
        if (pauseInsteadOfDucking) {
            flags |= AudioManager.AUDIOFOCUS_FLAG_PAUSES_ON_DUCKABLE_LOSS;
        }
        return new AudioFocusInfo(audioAttributes, uid, clientId, PACKAGE_NAME,
                gainType, AudioManager.AUDIOFOCUS_NONE,
                flags, Build.VERSION.SDK_INT);
    }

    /**
     * Creates an external audio port device
     *
     * @param type Type of audio device (e.g. BUS)
     * @param connection Connection for the audio device
     * @param address Device address
     * @return Created audio device
     */
    public static AudioPortDeviceExt createAudioPortDeviceExt(int type, String connection,
                                                              String address) {
        AudioPortDeviceExt deviceExt = new AudioPortDeviceExt();
        deviceExt.device = new AudioDevice();
        deviceExt.device.type = new AudioDeviceDescription();
        deviceExt.device.type.type = type;
        deviceExt.device.type.connection = connection;
        deviceExt.device.address = AudioDeviceAddress.id(address);
        return deviceExt;
    }

    /**
     * Creates an audio port for testing
     *
     * @param id ID of the audio port device
     * @param name Name of the audio port
     * @param gains Gains of the audio port
     * @param deviceExt External device represented by the port
     *
     * @return Created audio device port
     */
    public static AudioPort createAudioPort(int id, String name, AudioGain[] gains,
                                            AudioPortDeviceExt deviceExt) {
        AudioPort audioPort = new AudioPort();
        audioPort.id = id;
        audioPort.name = name;
        audioPort.gains = gains;
        audioPort.ext = AudioPortExt.device(deviceExt);
        return audioPort;
    }

    static AudioZoneContext createHALAudioContext() {
        AudioZoneContext context = new AudioZoneContext();
        context.audioContextInfos = new ArrayList<>();
        context.audioContextInfos.add(createAudioZoneContextInfo(new int[]{UNKNOWN, GAME, MEDIA},
                MUSIC_CONTEXT, MUSIC_CONTEXT_ID));
        context.audioContextInfos.add(createAudioZoneContextInfo(
                new int[]{ASSISTANCE_NAVIGATION_GUIDANCE}, NAVIGATION_CONTEXT,
                NAVIGATION_CONTEXT_ID));
        context.audioContextInfos.add(createAudioZoneContextInfo(
                new int[]{ASSISTANCE_ACCESSIBILITY, ASSISTANT}, VOICE_COMMAND_CONTEXT,
                VOICE_COMMAND_CONTEXT_ID));
        context.audioContextInfos.add(createAudioZoneContextInfo(
                new int[]{NOTIFICATION_TELEPHONY_RINGTONE}, RING_CONTEXT, RING_CONTEXT_ID));
        context.audioContextInfos.add(createAudioZoneContextInfo(
                new int[]{VOICE_COMMUNICATION, CALL_ASSISTANT, VOICE_COMMUNICATION_SIGNALLING},
                CALL_CONTEXT, CALL_CONTEXT_ID));
        context.audioContextInfos.add(createAudioZoneContextInfo(new int[]{ALARM}, ALARM_CONTEXT,
                ALARM_CONTEXT_ID));
        context.audioContextInfos.add(createAudioZoneContextInfo(
                new int[]{NOTIFICATION, NOTIFICATION_EVENT}, NOTIFICATION_CONTEXT,
                NOTIFICATION_CONTEXT_ID));
        context.audioContextInfos.add(createAudioZoneContextInfo(
                new int[]{ASSISTANCE_SONIFICATION}, SYSTEM_CONTEXT, SYSTEM_CONTEXT_ID));
        context.audioContextInfos.add(createAudioZoneContextInfo(
                new int[]{EMERGENCY}, EMERGENCY_CONTEXT, EMERGENCY_CONTEXT_ID));
        context.audioContextInfos.add(createAudioZoneContextInfo(
                new int[]{SAFETY}, SAFETY_CONTEXT, SAFETY_CONTEXT_ID));
        context.audioContextInfos.add(createAudioZoneContextInfo(
                new int[]{VEHICLE_STATUS}, VEHICLE_STATUS_CONTEXT, VEHICLE_STATUS_CONTEXT_ID));
        context.audioContextInfos.add(createAudioZoneContextInfo(
                new int[]{ANNOUNCEMENT}, ANNOUNCEMENT_CONTEXT, ANNOUNCEMENT_CONTEXT_ID));
        return context;
    }

    static AudioZoneConfig createDefaultAudioZoneConfig() {
        var mediaBusPort = createBusAudioPort(MEDIA_TEST_DEVICE, 10, "media_port");
        var mediaBusContextEntry = createDeviceToContextEntry(mediaBusPort,
                List.of(MUSIC_CONTEXT, ANNOUNCEMENT_CONTEXT));
        var notificationBusPort = createBusAudioPort(NOTIFICATION_TEST_DEVICE, 11,
                "notification_port");
        var notificationBusContextEntry = createDeviceToContextEntry(notificationBusPort,
                List.of(NOTIFICATION_CONTEXT));
        var mediaVolume = createVolumeGroupConfig(MEDIA_VOLUME_GROUP_ID, MEDIA_VOLUME_NAME,
                List.of(mediaBusContextEntry, notificationBusContextEntry));

        return getAudioZoneConfig(List.of(mediaVolume, getNavAndVoiceVolumeGroupConfig(),
                getTelephonyVolumeGroupConfig(), getSystemVolumeGroupConfig()),
                /* isDefault= */ true, "primary zone config 1");
    }

    static AudioZoneConfig createBluetoothAudioZoneConfig() {
        var btPortDevice = createAudioPortDeviceExt(AudioDeviceType.OUT_HEADPHONE,
                AudioDeviceDescription.CONNECTION_BT_A2DP, /* address= */ "");
        var btAudioPort = createAudioPort(10, /* name= */ "", GAINS, btPortDevice);
        var mediaBTContextEntry = createDeviceToContextEntry(btAudioPort,
                List.of(MUSIC_CONTEXT, ANNOUNCEMENT_CONTEXT, NOTIFICATION_CONTEXT));
        var mediaVolume = createVolumeGroupConfig(MEDIA_VOLUME_GROUP_ID, MEDIA_VOLUME_NAME,
                List.of(mediaBTContextEntry));

        return getAudioZoneConfig(List.of(mediaVolume, getNavAndVoiceVolumeGroupConfig(),
                getTelephonyVolumeGroupConfig(), getSystemVolumeGroupConfig()),
                /* isDefault= */ false, "bluetooth zone config 1");
    }

    static AudioZoneConfig createUSBAudioZoneConfig() {
        var btPortDevice = createAudioPortDeviceExt(AudioDeviceType.OUT_HEADPHONE,
                AudioDeviceDescription.CONNECTION_USB, /* address= */ "");
        var btAudioPort = createAudioPort(10, /* name= */ "", GAINS, btPortDevice);
        var mediaBTContextEntry = createDeviceToContextEntry(btAudioPort,
                List.of(MUSIC_CONTEXT, ANNOUNCEMENT_CONTEXT, NOTIFICATION_CONTEXT));
        var mediaVolume = createVolumeGroupConfig(MEDIA_VOLUME_GROUP_ID, MEDIA_VOLUME_NAME,
                List.of(mediaBTContextEntry));

        return getAudioZoneConfig(List.of(mediaVolume, getNavAndVoiceVolumeGroupConfig(),
                        getTelephonyVolumeGroupConfig(), getSystemVolumeGroupConfig()),
                /* isDefault= */ false, "USB zone config 1");
    }

    private static VolumeGroupConfig getSystemVolumeGroupConfig() {
        var alarmBusPort = createBusAudioPort(ALARM_TEST_DEVICE, 17, "alarm_port");
        var alarmBusContextEntry = createDeviceToContextEntry(alarmBusPort, List.of(ALARM_CONTEXT));
        var systemBusPort = createBusAudioPort(SYSTEM_BUS_DEVICE, 18, "system_port");
        var systemBusContextEntry = createDeviceToContextEntry(systemBusPort,
                List.of(SYSTEM_CONTEXT, EMERGENCY_CONTEXT, SAFETY_CONTEXT, VEHICLE_STATUS_CONTEXT));
        return createVolumeGroupConfig(SYSTEM_VOLUME_GROUP_ID, /* groupName= */ "",
                List.of(alarmBusContextEntry, systemBusContextEntry));
    }

    private static VolumeGroupConfig getTelephonyVolumeGroupConfig() {
        var callBusPort = createBusAudioPort(CALL_TEST_DEVICE, 15, "call_port");
        var callBusContextEntry = createDeviceToContextEntry(callBusPort,
                List.of(CALL_CONTEXT));
        var ringBusPort = createBusAudioPort(RING_TEST_DEVICE, 16, "ring_port");
        var ringBusContextEntry = createDeviceToContextEntry(ringBusPort, List.of(RING_CONTEXT));
        return createVolumeGroupConfig(TELEPHONY_VOLUME_GROUP_ID,
                TELEPHONY_VOLUME_NAME, List.of(callBusContextEntry, ringBusContextEntry));
    }

    private static VolumeGroupConfig getNavAndVoiceVolumeGroupConfig() {
        var navBusPort = createBusAudioPort(NAVIGATION_TEST_DEVICE, 13, "nav_port");
        var navBusContextEntry = createDeviceToContextEntry(navBusPort,
                List.of(NAVIGATION_CONTEXT));
        var voiceBusPort = createBusAudioPort(VOICE_TEST_DEVICE, 14, "voice_port");
        var voiceBusContextEntry = createDeviceToContextEntry(voiceBusPort,
                List.of(VOICE_COMMAND_CONTEXT));
        return createVolumeGroupConfig(NAVIGATION_AND_VOICE_VOLUME_GROUP_ID,
                NAVIGATION_AND_VOICE_VOLUME_NAME,
                List.of(navBusContextEntry, voiceBusContextEntry));
    }

    private static AudioZoneConfig getAudioZoneConfig(List<VolumeGroupConfig> groups,
            boolean isDefault, String configName) {
        var primaryZoneConfig = new AudioZoneConfig();
        primaryZoneConfig.name = configName;
        primaryZoneConfig.isDefault = isDefault;
        primaryZoneConfig.volumeGroups = groups;
        return primaryZoneConfig;
    }

    static AudioZone createPrimaryAudioZone() {
        var audioZone = new AudioZone();
        audioZone.id = AudioZone.PRIMARY_AUDIO_ZONE;
        audioZone.name = PRIMARY_ZONE_NAME;
        audioZone.occupantZoneId = 1;
        audioZone.audioZoneContext = createHALAudioContext();
        audioZone.audioZoneConfigs = List.of(createDefaultAudioZoneConfig(),
                createBluetoothAudioZoneConfig(), createUSBAudioZoneConfig());
        return audioZone;
    }

    static VolumeGroupConfig createVolumeGroupConfig(int groupId, String groupName,
            List<DeviceToContextEntry> contextEntries) {
        var volumeGroupConfig = new VolumeGroupConfig();
        volumeGroupConfig.id = groupId;
        volumeGroupConfig.name = groupName;
        volumeGroupConfig.activationConfiguration =
                createVolumeActivationConfiguration(TEST_ACTIVATION, TEST_MIN_ACTIVATION,
                        TEST_MAX_ACTIVATION, ON_BOOT);
        volumeGroupConfig.carAudioRoutes = contextEntries;
        return volumeGroupConfig;
    }

    static VolumeActivationConfiguration createVolumeActivationConfiguration(String name,
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

    static AudioPort createBusAudioPort(String portAddress, int portId, String portName) {
        var busPortDevice = createAudioPortDeviceExt(AudioDeviceType.OUT_BUS, /* connection= */ "",
                portAddress);
        return createAudioPort(portId, portName, GAINS, busPortDevice);
    }

    static DeviceToContextEntry createDeviceToContextEntry(AudioPort audioPort,
            List<String> contextList) {
        var entry = new DeviceToContextEntry();
        entry.device = audioPort;
        entry.contextNames = new ArrayList<>(contextList);
        return entry;
    }

    static ArrayMap<String, Integer> createCarAudioContextNameToIdMap(CarAudioContext context) {
        var contextNameToId = new ArrayMap<String, Integer>(context.getContextsInfo().size());
        for (int c = 0; c < context.getContextsInfo().size(); c++) {
            var info = context.getContextsInfo().get(c);
            contextNameToId.put(info.getName(), info.getId());
        }
        return contextNameToId;
    }

    static Iterable<Integer> getContextForVolumeGroupConfig(VolumeGroupConfig groupConfig,
            ArrayMap<String, Integer> contextNameToId)  {
        var contexts = new ArrayList<Integer>();
        for (int c = 0; c < groupConfig.carAudioRoutes.size(); c++) {
            var route = groupConfig.carAudioRoutes.get(c);
            contexts.addAll(getContextForAudioRoute(route, contextNameToId));
        }
        return contexts;
    }

    private static List<Integer> getContextForAudioRoute(DeviceToContextEntry route,
            ArrayMap<String, Integer> contextNameToId) {
        var contexts = new ArrayList<Integer>();
        for (int c = 0; c < route.contextNames.size(); c++) {
            var name = route.contextNames.get(c);
            int id = contextNameToId.getOrDefault(name, CarAudioContext.getInvalidContext());
            contexts.add(id);
        }
        return contexts;
    }

    private static AudioZoneContextInfo createAudioZoneContextInfo(int[] usages, String name,
            int id) {
        AudioZoneContextInfo info = new AudioZoneContextInfo();
        info.id = id;
        info.name = name;
        info.audioAttributes = createListOfHALAudioAttributes(usages);
        return info;
    }

    static List<android.media.audio.common.AudioAttributes>
            createListOfHALAudioAttributes(int[] usages) {
        var audioAttributes =
                new ArrayList<android.media.audio.common.AudioAttributes>(usages.length);
        for (int usage : usages) {
            android.media.audio.common.AudioAttributes
                    attributes = new android.media.audio.common.AudioAttributes();
            attributes.usage = usage;
            audioAttributes.add(attributes);
        }
        return audioAttributes;
    }
}
