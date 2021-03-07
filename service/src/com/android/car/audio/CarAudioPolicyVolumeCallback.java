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

import static android.util.Log.DEBUG;
import static android.util.Log.VERBOSE;

import static com.android.car.CarLog.TAG_AUDIO;

import android.annotation.NonNull;
import android.car.media.CarAudioManager;
import android.media.AudioManager;
import android.media.audiopolicy.AudioPolicy;
import android.media.audiopolicy.AudioPolicy.Builder;
import android.util.Log;
import android.util.Slog;

import com.android.internal.annotations.VisibleForTesting;

import java.util.Objects;

final class CarAudioPolicyVolumeCallback extends AudioPolicy.AudioPolicyVolumeCallback{
    private final CarAudioService mCarAudioService;
    private final AudioManager mAudioManager;

    static void addVolumeCallbackToPolicy(@NonNull Builder policyBuilder,
            @NonNull CarAudioService carAudioService,
            @NonNull AudioManager audioManager) {
        Objects.requireNonNull(policyBuilder, "AudioPolicy.Builder cannot be null");
        policyBuilder.setAudioPolicyVolumeCallback(
                new CarAudioPolicyVolumeCallback(carAudioService, audioManager));
        if (Log.isLoggable(TAG_AUDIO, DEBUG)) {
            Slog.d(TAG_AUDIO, "Registered car audio policy volume callback");
        }
    }

    @VisibleForTesting
    CarAudioPolicyVolumeCallback(@NonNull CarAudioService carAudioService,
            @NonNull AudioManager audioManager) {
        Objects.requireNonNull(carAudioService, "CarAudioService cannot be null");
        Objects.requireNonNull(audioManager, "AudioManager cannot be null");
        mCarAudioService = carAudioService;
        mAudioManager = audioManager;
    }

    @Override
    public void onVolumeAdjustment(int adjustment) {
        @CarAudioContext.AudioContext int suggestedContext =
                mCarAudioService.getSuggestedAudioContextForPrimaryZone();

        int zoneId = CarAudioManager.PRIMARY_AUDIO_ZONE;
        int groupId = mCarAudioService.getVolumeGroupIdForAudioContext(zoneId, suggestedContext);

        if (Log.isLoggable(TAG_AUDIO, VERBOSE)) {
            Slog.v(TAG_AUDIO, "onVolumeAdjustment: "
                    + AudioManager.adjustToString(adjustment) + " suggested audio context: "
                    + CarAudioContext.toString(suggestedContext) + " suggested volume group: "
                    + groupId);
        }

        final int currentVolume = mCarAudioService.getGroupVolume(zoneId, groupId);
        final int flags = AudioManager.FLAG_FROM_KEY | AudioManager.FLAG_SHOW_UI;
        switch (adjustment) {
            case AudioManager.ADJUST_LOWER:
                int minValue = Math.max(currentVolume - 1,
                        mCarAudioService.getGroupMinVolume(zoneId, groupId));
                mCarAudioService.setGroupVolume(zoneId, groupId, minValue, flags);
                break;
            case AudioManager.ADJUST_RAISE:
                int maxValue = Math.min(currentVolume + 1,
                        mCarAudioService.getGroupMaxVolume(zoneId, groupId));
                mCarAudioService.setGroupVolume(zoneId, groupId, maxValue, flags);
                break;
            case AudioManager.ADJUST_MUTE:
            case AudioManager.ADJUST_UNMUTE:
                setMute(adjustment == AudioManager.ADJUST_MUTE, flags);
                break;
            case AudioManager.ADJUST_TOGGLE_MUTE:
                toggleMute(flags);
                break;
            case AudioManager.ADJUST_SAME:
            default:
                break;
        }
    }

    private void toggleMute(int flags) {
        setMute(!mAudioManager.isMasterMute(), flags);
    }

    private void setMute(boolean mute, int flags) {
        mCarAudioService.setMasterMute(mute, flags);
    }
}
