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
package com.android.car.audio;

import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DUMP_INFO;

import android.annotation.Nullable;
import android.car.builtin.media.AudioManagerHelper;
import android.car.builtin.util.Slogf;
import android.car.media.CarAudioManager;
import android.car.media.CarVolumeGroupInfo;
import android.media.AudioAttributes;
import android.media.AudioDeviceAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioPlaybackConfiguration;
import android.util.ArraySet;
import android.util.SparseArray;

import com.android.car.CarLog;
import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.util.IndentingPrintWriter;
import com.android.internal.util.Preconditions;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * A class encapsulates an audio zone in car.
 *
 * An audio zone can contain multiple {@link CarVolumeGroup}s, and each zone has its own
 * {@link CarAudioFocus} instance. Additionally, there may be dedicated hardware volume keys
 * attached to each zone.
 *
 * See also the unified car_audio_configuration.xml
 */
public class CarAudioZone {

    private final int mId;
    private final String mName;
    private final List<CarVolumeGroup> mVolumeGroups;
    private final Set<String> mDeviceAddresses;
    private final CarAudioContext mCarAudioContext;
    private List<AudioDeviceAttributes> mInputAudioDevice;
    private final List<String> mGroupIdToNames = new ArrayList<>();

    CarAudioZone(CarAudioContext carAudioContext, String name, int id) {
        mCarAudioContext = Objects.requireNonNull(carAudioContext,
                "Car audio context can not be null");
        mName = name;
        mId = id;
        mVolumeGroups = new ArrayList<>();
        mInputAudioDevice = new ArrayList<>();
        mDeviceAddresses = new HashSet<>();
    }

    int getId() {
        return mId;
    }

    String getName() {
        return mName;
    }

    boolean isPrimaryZone() {
        return mId == CarAudioManager.PRIMARY_AUDIO_ZONE;
    }

    void addVolumeGroup(CarVolumeGroup volumeGroup) {
        mVolumeGroups.add(volumeGroup);
        mDeviceAddresses.addAll(volumeGroup.getAddresses());
        // Assuming index is the id, id to name is a bijective transformation, names are unique
        mGroupIdToNames.add(volumeGroup.getName());
    }

    @Nullable
    CarVolumeGroup getVolumeGroup(String groupName) {
        int groupId = mGroupIdToNames.indexOf(groupName);
        if (groupId < 0) {
            return null;
        }
        return getVolumeGroup(groupId);
    }

    CarVolumeGroup getVolumeGroup(int groupId) {
        Preconditions.checkArgumentInRange(groupId, 0, mVolumeGroups.size() - 1,
                "groupId(" + groupId + ") is out of range");
        return mVolumeGroups.get(groupId);
    }

    /**
     * @return Snapshot of available {@link AudioDeviceInfo}s in List.
     */
    List<AudioDeviceInfo> getAudioDeviceInfos() {
        final List<AudioDeviceInfo> devices = new ArrayList<>();
        for (CarVolumeGroup group : mVolumeGroups) {
            for (String address : group.getAddresses()) {
                devices.add(group.getCarAudioDeviceInfoForAddress(address).getAudioDeviceInfo());
            }
        }
        return devices;
    }

    List<AudioDeviceInfo> getAudioDeviceInfosSupportingDynamicMix() {
        List<AudioDeviceInfo> devices = new ArrayList<>();
        for (int index = 0; index <  mVolumeGroups.size(); index++) {
            CarVolumeGroup group = mVolumeGroups.get(index);
            List<String> addresses = group.getAddresses();
            for (int addressIndex = 0; addressIndex < addresses.size(); addressIndex++) {
                String address = addresses.get(addressIndex);
                CarAudioDeviceInfo info = group.getCarAudioDeviceInfoForAddress(address);
                if (info.canBeRoutedWithDynamicPolicyMix()) {
                    devices.add(info.getAudioDeviceInfo());
                }
            }
        }
        return devices;
    }

    int getVolumeGroupCount() {
        return mVolumeGroups.size();
    }

    /**
     * @return Snapshot of available {@link CarVolumeGroup}s in array.
     */
    CarVolumeGroup[] getVolumeGroups() {
        return mVolumeGroups.toArray(new CarVolumeGroup[0]);
    }

    /**
     * Constraints applied here for checking usage of Dynamic Mixes for routing:
     *
     * - One context with same AudioAttributes usage shall not be routed to 2 different devices
     * (Dynamic Mixes supports only match on usage, not on other AudioAttributes fields.
     *
     * - One address shall not appear in 2 groups. CarAudioService cannot establish Dynamic Routing
     * rules that address multiple groups.
     */
    boolean validateCanUseDynamicMixRouting(boolean useCoreAudioRouting) {
        ArraySet<String> addresses = new ArraySet<>();
        SparseArray<CarAudioDeviceInfo> usageToDevice = new SparseArray<>();
        for (int index = 0; index <  mVolumeGroups.size(); index++) {
            CarVolumeGroup group = mVolumeGroups.get(index);

            List<String> groupAddresses = group.getAddresses();
            // Due to AudioPolicy Dynamic Mixing limitation,  rules can be made only on usage and
            // not on audio attributes.
            // When using product strategies, AudioPolicy may not simply route on usage match.
            // Ensure that a given usage can reach a single device address to enable dynamic mix.
            // Otherwise, prevent from establishing rule if supporting Core Routing.
            // Returns false if Core Routing is not supported
            for (int addressIndex = 0; addressIndex < groupAddresses.size(); addressIndex++) {
                String address = groupAddresses.get(addressIndex);
                boolean canUseDynamicMixRoutingForAddress = true;
                CarAudioDeviceInfo info = group.getCarAudioDeviceInfoForAddress(address);
                List<Integer> usagesForAddress = group.getAllSupportedUsagesForAddress(address);

                if (!addresses.add(address)) {
                    if (useCoreAudioRouting) {
                        Slogf.w(CarLog.TAG_AUDIO, "Address %s appears in two groups, prevents"
                                + " from using dynamic policy mixes for routing" , address);
                        canUseDynamicMixRoutingForAddress = false;
                    }
                }
                for (int usageIndex = 0; usageIndex < usagesForAddress.size(); usageIndex++) {
                    int usage = usagesForAddress.get(usageIndex);
                    CarAudioDeviceInfo infoForAttr = usageToDevice.get(usage);
                    if (infoForAttr != null && !infoForAttr.getAddress().equals(address)) {
                        Slogf.e(CarLog.TAG_AUDIO, "Addresses %s and %s can be reached with same"
                                + " usage %s, prevent from using dynamic policy mixes.",
                                infoForAttr.getAddress(), address,
                                AudioManagerHelper.usageToXsdString(usage));
                        if (useCoreAudioRouting) {
                            canUseDynamicMixRoutingForAddress = false;
                            infoForAttr.resetCanBeRoutedWithDynamicPolicyMix();
                        } else {
                            return false;
                        }
                    } else {
                        usageToDevice.put(usage, info);
                    }
                }
                if (!canUseDynamicMixRoutingForAddress) {
                    info.resetCanBeRoutedWithDynamicPolicyMix();
                }
            }
        }
        return true;
    }

    /**
     * Constraints applied here:
     *
     * - One context should not appear in two groups if not relying on Core Audio for Volume
     * management. When using core Audio, mutual exclusive contexts may reach same devices,
     * AudioPolicyManager will apply the corresponding gain when the context is active on the common
     * device
     * - All contexts are assigned
     * - One device should not appear in two groups
     * - All gain controllers in the same group have same step value
     *
     * Note that it is fine that there are devices which do not appear in any group. Those devices
     * may be reserved for other purposes.
     * Step value validation is done in
     * {@link CarVolumeGroup.Builder#setDeviceInfoForContext(int, CarAudioDeviceInfo)}
     */
    boolean validateVolumeGroups(boolean useCoreAudioRouting) {
        ArraySet<Integer> contexts = new ArraySet<>();
        ArraySet<String> addresses = new ArraySet<>();
        for (int index = 0; index <  mVolumeGroups.size(); index++) {
            CarVolumeGroup group = mVolumeGroups.get(index);
            // One context should not appear in two groups
            int[] groupContexts = group.getContexts();
            for (int groupIndex = 0; groupIndex < groupContexts.length; groupIndex++) {
                int contextId = groupContexts[groupIndex];
                if (!contexts.add(contextId)) {
                    Slogf.e(CarLog.TAG_AUDIO, "Context %d appears in two groups", contextId);
                    return false;
                }
            }
            // One address should not appear in two groups
            List<String> groupAddresses = group.getAddresses();
            for (int addressIndex = 0; addressIndex < groupAddresses.size(); addressIndex++) {
                String address = groupAddresses.get(addressIndex);
                if (!addresses.add(address)) {
                    if (useCoreAudioRouting) {
                        continue;
                    }
                    Slogf.e(CarLog.TAG_AUDIO, "Address appears in two groups: " + address);
                    return false;
                }
            }
        }

        boolean allContextValidated = true;
        List<Integer> allContexts = mCarAudioContext.getAllContextsIds();
        for (int index = 0; index < allContexts.size(); index++) {
            if (!contexts.contains(allContexts.get(index))) {
                Slogf.e(CarLog.TAG_AUDIO, "Audio context %s is not assigned to a group",
                        mCarAudioContext.toString(allContexts.get(index)));
                allContextValidated = false;
            }
        }

        if (!allContextValidated) {
            return false;
        }

        List<Integer> contextList = new ArrayList<>(contexts);
        // All contexts are assigned
        if (!mCarAudioContext.validateAllAudioAttributesSupported(contextList)) {
            Slogf.e(CarLog.TAG_AUDIO, "Some audio attributes are not assigned to a group");
            return false;
        }
        return true;
    }

    void synchronizeCurrentGainIndex() {
        for (CarVolumeGroup group : mVolumeGroups) {
            group.setCurrentGainIndex(group.getCurrentGainIndex());
        }
    }

    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    void dump(IndentingPrintWriter writer) {
        writer.printf("CarAudioZone(%s:%d) isPrimary? %b\n", mName, mId, isPrimaryZone());
        writer.increaseIndent();
        for (CarVolumeGroup group : mVolumeGroups) {
            group.dump(writer);
        }

        writer.printf("Input Audio Device Addresses\n");
        writer.increaseIndent();
        for (AudioDeviceAttributes audioDevice : mInputAudioDevice) {
            writer.printf("Device Address(%s)\n", audioDevice.getAddress());
        }
        writer.decreaseIndent();
        writer.println();
        writer.decreaseIndent();
    }

    /**
     * Return the audio device address mapping to a car audio context
     */
    public String getAddressForContext(int audioContext) {
        mCarAudioContext.preconditionCheckAudioContext(audioContext);
        String deviceAddress = null;
        for (CarVolumeGroup volumeGroup : getVolumeGroups()) {
            deviceAddress = volumeGroup.getAddressForContext(audioContext);
            if (deviceAddress != null) {
                return deviceAddress;
            }
        }
        // This should not happen unless something went wrong.
        // Device address are unique per zone and all contexts are assigned in a zone.
        throw new IllegalStateException("Could not find output device in zone " + mId
                + " for audio context " + audioContext);
    }

    public AudioDeviceInfo getAudioDeviceForContext(int audioContext) {
        mCarAudioContext.preconditionCheckAudioContext(audioContext);
        for (CarVolumeGroup volumeGroup : getVolumeGroups()) {
            AudioDeviceInfo deviceInfo = volumeGroup.getAudioDeviceForContext(audioContext);
            if (deviceInfo != null) {
                return deviceInfo;
            }
        }
        // This should not happen unless something went wrong.
        // Device address are unique per zone and all contexts are assigned in a zone.
        throw new IllegalStateException("Could not find output device in zone " + mId
                + " for audio context " + audioContext);
    }

    /**
     * Update the volume groups for the new user
     * @param userId user id to update to
     */
    public void updateVolumeGroupsSettingsForUser(int userId) {
        for (CarVolumeGroup group : mVolumeGroups) {
            group.loadVolumesSettingsForUser(userId);
        }
    }

    void addInputAudioDevice(AudioDeviceAttributes device) {
        mInputAudioDevice.add(device);
    }

    List<AudioDeviceAttributes> getInputAudioDevices() {
        return mInputAudioDevice;
    }

    public List<AudioAttributes> findActiveAudioAttributesFromPlaybackConfigurations(
            List<AudioPlaybackConfiguration> configurations) {
        Objects.requireNonNull(configurations, "Audio playback configurations can not be null");
        List<AudioAttributes> audioAttributes = new ArrayList<>();
        for (int index = 0; index < configurations.size(); index++) {
            AudioPlaybackConfiguration configuration = configurations.get(index);
            if (configuration.isActive()) {
                if (isAudioDeviceInfoValidForZone(configuration.getAudioDeviceInfo())) {
                    // Note that address's context and the context actually supplied could be
                    // different
                    audioAttributes.add(configuration.getAudioAttributes());
                }
            }
        }
        return audioAttributes;
    }

    boolean isAudioDeviceInfoValidForZone(AudioDeviceInfo info) {
        return info != null
                && info.getAddress() != null
                && !info.getAddress().isEmpty()
                && containsDeviceAddress(info.getAddress());
    }

    private boolean containsDeviceAddress(String deviceAddress) {
        return mDeviceAddresses.contains(deviceAddress);
    }

    void onAudioGainChanged(List<Integer> halReasons, List<CarAudioGainConfigInfo> gains) {
        for (int index = 0; index < gains.size(); index++) {
            CarAudioGainConfigInfo gainInfo = gains.get(index);
            for (int groupIndex = 0; groupIndex < mVolumeGroups.size(); groupIndex++) {
                CarVolumeGroup group = mVolumeGroups.get(groupIndex);
                if (group.getAddresses().contains(gainInfo.getDeviceAddress())) {
                    group.onAudioGainChanged(halReasons, gainInfo);
                    break; // loop of CarVolumeGroup.
                }
            }
        }
    }

    /**
     * Returns the car audio context set for the car audio zone
     */
    public CarAudioContext getCarAudioContext() {
        return mCarAudioContext;
    }

    /**
     * Returns the car volume infos for all the volume groups in the audio zone
     */
    List<CarVolumeGroupInfo> getVolumeGroupInfos() {
        List<CarVolumeGroupInfo> groupInfos = new ArrayList<>(mVolumeGroups.size());
        for (int index = 0; index < mVolumeGroups.size(); index++) {
            groupInfos.add(mVolumeGroups.get(index).getCarVolumeGroupInfo());
        }

        return groupInfos;
    }
}
