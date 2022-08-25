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

import static android.media.AudioAttributes.USAGE_ALARM;
import static android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE;
import static android.media.AudioAttributes.USAGE_EMERGENCY;
import static android.media.AudioAttributes.USAGE_GAME;
import static android.media.AudioAttributes.USAGE_MEDIA;
import static android.media.AudioAttributes.USAGE_NOTIFICATION;
import static android.media.AudioAttributes.USAGE_SAFETY;
import static android.media.AudioAttributes.USAGE_VIRTUAL_SOURCE;

import static com.android.car.audio.CarAudioContext.CALL;
import static com.android.car.audio.CarAudioContext.EMERGENCY;
import static com.android.car.audio.CarAudioContext.INVALID;
import static com.android.car.audio.CarAudioContext.MUSIC;
import static com.android.car.audio.CarAudioContext.NAVIGATION;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.hardware.audio.common.PlaybackTrackMetadata;
import android.media.AudioAttributes;
import android.media.AudioFocusInfo;
import android.media.AudioManager;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RunWith(AndroidJUnit4.class)
public class CarDuckingUtilsTest {
    private static final String MEDIA_ADDRESS = "media";
    private static final String EMERGENCY_ADDRESS = "emergency";
    private static final String CALL_ADDRESS = "call";
    private static final String NAVIGATION_ADDRESS = "navigation";

    private static final int ZONE_ID = 0;

    @Test
    public void sContextsToDuck_verifyNoCycles() {
        for (int i = 0; i < CarDuckingUtils.sContextsToDuck.size(); i++) {
            int startingContext = CarDuckingUtils.sContextsToDuck.keyAt(i);
            int[] duckedContexts = CarDuckingUtils.sContextsToDuck.valueAt(i);
            List<Integer> contextsToVisit = Arrays.stream(duckedContexts).boxed()
                    .collect(Collectors.toList());
            Set<Integer> visitedContexts = new HashSet<>(startingContext);

            while (contextsToVisit.size() > 0) {
                int contextToVisit = contextsToVisit.remove(0);
                if (visitedContexts.contains(contextToVisit)) {
                    continue;
                }
                visitedContexts.add(contextToVisit);

                int[] duckedContextsToVisit = CarDuckingUtils.sContextsToDuck.get(contextToVisit);
                for (int duckedContext : duckedContextsToVisit) {
                    assertWithMessage("A cycle exists where %s can duck itself via %s",
                            CarAudioContext.toString(startingContext),
                            CarAudioContext.toString(contextToVisit)
                    ).that(duckedContext).isNotEqualTo(startingContext);

                    if (!visitedContexts.contains(duckedContext)) {
                        contextsToVisit.add(duckedContext);
                    }
                }
            }
        }
    }

    @Test
    public void sContextsToDuck_verifyContextsDontDuckThemselves() {
        for (int i = 0; i < CarDuckingUtils.sContextsToDuck.size(); i++) {
            int context = CarDuckingUtils.sContextsToDuck.keyAt(i);
            int[] contextsToDuck = CarDuckingUtils.sContextsToDuck.valueAt(i);

            assertWithMessage("Context " + CarAudioContext.toString(context) + " ducks itself")
                    .that(contextsToDuck)
                    .asList()
                    .doesNotContain(context);
        }
    }

    @Test
    public void getAudioAttributesHoldingFocus_withNoHolders_returnsEmptyArray() {
        List<AudioAttributes> audioAttributesList =
                CarDuckingUtils.getAudioAttributesHoldingFocus(new ArrayList<>());

        assertWithMessage("Audio attribute list")
                .that(audioAttributesList).isEmpty();
    }

    @Test
    public void getAudioAttributesHoldingFocus_removesDuplicateUsages() {
        List<AudioAttributes> audioAttributes = new ArrayList<>(/* initialCapacity= */ 3);
        audioAttributes.add(CarAudioContext.getAudioAttributeFromUsage(USAGE_NOTIFICATION));
        audioAttributes.add(CarAudioContext.getAudioAttributeFromUsage(USAGE_MEDIA));
        audioAttributes.add(CarAudioContext.getAudioAttributeFromUsage(USAGE_NOTIFICATION));
        List<AudioFocusInfo> focusHolders =
                generateAudioFocusInfoForAudioAttributes(audioAttributes);

        List<AudioAttributes> attributesHoldingFocus =
                CarDuckingUtils.getAudioAttributesHoldingFocus(focusHolders);

        assertWithMessage(" Attributes holding focus")
                .that(attributesHoldingFocus)
                .containsExactly(CarAudioContext.getAudioAttributeFromUsage(USAGE_NOTIFICATION),
                        CarAudioContext.getAudioAttributeFromUsage(USAGE_MEDIA));
    }

    @Test
    public void getAudioAttributesHoldingFocus_includesSystemAudioAttributes() {
        List<AudioAttributes> audioAttributes = new ArrayList<>(/* initialCapacity= */ 3);
        audioAttributes.add(CarAudioContext.getAudioAttributeFromUsage(USAGE_MEDIA));
        audioAttributes.add(CarAudioContext.getAudioAttributeFromUsage(USAGE_SAFETY));
        audioAttributes.add(CarAudioContext.getAudioAttributeFromUsage(USAGE_EMERGENCY));
        List<AudioFocusInfo> focusHolders =
                generateAudioFocusInfoForAudioAttributes(audioAttributes);

        List<AudioAttributes> audioAttributesHoldingFocus =
                CarDuckingUtils.getAudioAttributesHoldingFocus(focusHolders);

        assertWithMessage("Attributes holding focus")
                .that(audioAttributesHoldingFocus).containsExactly(
                        CarAudioContext.getAudioAttributeFromUsage(USAGE_MEDIA),
                        CarAudioContext.getAudioAttributeFromUsage(USAGE_SAFETY),
                        CarAudioContext.getAudioAttributeFromUsage(USAGE_EMERGENCY));
    }

    @Test
    public void getAddressesToDuck_withOneUsage_returnsEmptyList() {
        CarAudioZone mockZone = generateAudioZoneMock();
        List<AudioAttributes> audioAttributes = List.of(
                CarAudioContext.getAudioAttributeFromUsage(USAGE_MEDIA),
                CarAudioContext.getAudioAttributeFromUsage(USAGE_GAME));

        List<String> addresses = CarDuckingUtils.getAddressesToDuck(audioAttributes, mockZone);

        assertThat(addresses).isEmpty();
    }

    @Test
    public void getAddressesToDuck_withMultipleUsagesForTheSameContext_returnsEmptyList() {
        CarAudioZone mockZone = generateAudioZoneMock();
        List<AudioAttributes> audioAttributes = List.of(
                CarAudioContext.getAudioAttributeFromUsage(USAGE_MEDIA),
                CarAudioContext.getAudioAttributeFromUsage(USAGE_GAME));

        List<String> addresses = CarDuckingUtils.getAddressesToDuck(audioAttributes, mockZone);

        assertThat(addresses).isEmpty();
    }

    @Test
    public void getAddressesToDuck_onlyReturnsDevicesForUsagesHoldingFocus() {
        CarAudioZone mockZone = generateAudioZoneMock();
        List<AudioAttributes> audioAttributes = List.of(
                CarAudioContext.getAudioAttributeFromUsage(USAGE_MEDIA),
                CarAudioContext.getAudioAttributeFromUsage(USAGE_SAFETY),
                CarAudioContext.getAudioAttributeFromUsage(USAGE_ASSISTANCE_NAVIGATION_GUIDANCE));

        List<String> addresses = CarDuckingUtils.getAddressesToDuck(audioAttributes, mockZone);

        assertThat(addresses).containsExactly(MEDIA_ADDRESS, NAVIGATION_ADDRESS);
    }

    @Test
    public void getAddressesToDuck_doesNotConsidersInvalidUsage() {
        CarAudioZone mockZone = generateAudioZoneMock();
        List<AudioAttributes> audioAttributes = List.of(
                CarAudioContext.getAudioAttributeFromUsage(USAGE_VIRTUAL_SOURCE));

        List<String> addresses = CarDuckingUtils.getAddressesToDuck(audioAttributes, mockZone);

        assertThat(addresses).isEmpty();
    }

    @Test
    public void getAddressesToDuck_withDuckedAndUnduckedContextsSharingDevice_excludesThatDevice() {
        CarAudioZone mockZone = generateAudioZoneMock();
        when(mockZone.getAddressForContext(CarAudioContext.SAFETY)).thenReturn(NAVIGATION_ADDRESS);
        List<AudioAttributes> audioAttributes = List.of(
                CarAudioContext.getAudioAttributeFromUsage(USAGE_MEDIA),
                CarAudioContext.getAudioAttributeFromUsage(USAGE_SAFETY),
                CarAudioContext.getAudioAttributeFromUsage(USAGE_ASSISTANCE_NAVIGATION_GUIDANCE));

        List<String> addresses = CarDuckingUtils.getAddressesToDuck(audioAttributes, mockZone);

        assertThat(addresses).containsExactly(MEDIA_ADDRESS);
    }

    @Test
    public void getAddressesToDuck_withDuckedContextsSharingADevice_includesAddressOnce() {
        CarAudioZone mockZone = generateAudioZoneMock();
        when(mockZone.getAddressForContext(CarAudioContext.ALARM)).thenReturn(MEDIA_ADDRESS);
        List<AudioAttributes> audioAttributes = List.of(
                CarAudioContext.getAudioAttributeFromUsage(USAGE_MEDIA),
                CarAudioContext.getAudioAttributeFromUsage(USAGE_SAFETY),
                CarAudioContext.getAudioAttributeFromUsage(USAGE_ALARM));

        List<String> addresses = CarDuckingUtils.getAddressesToDuck(audioAttributes, mockZone);

        assertThat(addresses).containsExactly(MEDIA_ADDRESS);
    }


    @Test
    public void getAddressesToUnduck_onlyReturnsAddressesThatWerePreviouslyDucked() {
        List<String> oldAddressesToDuck = List.of("one", "three", "four");
        List<String> addressesToDuck = List.of("one", "two");

        List<String> result = CarDuckingUtils.getAddressesToUnduck(addressesToDuck,
                oldAddressesToDuck);

        assertThat(result).containsExactly("three", "four");
    }

    @Test
    public void getAddressesToUnduck_withNoPreviouslyDuckedAddresses_returnsEmptyArray() {
        List<String> oldAddressesToDuck = new ArrayList<>();
        List<String> addressesToDuck = List.of("one", "two");

        List<String> result = CarDuckingUtils.getAddressesToUnduck(addressesToDuck,
                oldAddressesToDuck);

        assertThat(result).isEmpty();
    }

    @Test
    public void generateDuckingInfo_succeed() {
        CarAudioZone mockZone = generateAudioZoneMock();
        List<AudioAttributes> audioAttributes = new ArrayList<>(/* initialCapacity= */ 3);
        audioAttributes.add(CarAudioContext.getAudioAttributeFromUsage(USAGE_MEDIA));
        audioAttributes.add(CarAudioContext.getAudioAttributeFromUsage(USAGE_SAFETY));
        audioAttributes.add(CarAudioContext
                .getAudioAttributeFromUsage(USAGE_ASSISTANCE_NAVIGATION_GUIDANCE));
        List<AudioAttributes> audioAttributesWithoutSafety =
                new ArrayList<>(/* initialCapacity= */ 2);
        audioAttributesWithoutSafety.add(CarAudioContext.getAudioAttributeFromUsage(USAGE_MEDIA));
        audioAttributesWithoutSafety.add(CarAudioContext
                .getAudioAttributeFromUsage(USAGE_ASSISTANCE_NAVIGATION_GUIDANCE));
        List<AudioAttributes> audioAttributesWithOnlyMedia =
                new ArrayList<>(/* initialCapacity= */ 1);
        audioAttributesWithOnlyMedia.add(CarAudioContext.getAudioAttributeFromUsage(USAGE_MEDIA));

        List<AudioFocusInfo> focusHolders =
                generateAudioFocusInfoForAudioAttributes(audioAttributes);
        List<PlaybackTrackMetadata> playbackTrackMetadataHoldingFocus =
                CarHalAudioUtils.audioAttributesToMetadatas(audioAttributes, mockZone);

        CarDuckingInfo duckingInfo =
                CarDuckingUtils.generateDuckingInfo(
                        getEmptyCarDuckingInfo(), focusHolders, mockZone);

        assertThat(duckingInfo.getZoneId()).isEqualTo(ZONE_ID);
        assertThat(duckingInfo.getAddressesToDuck())
                .containsExactly(MEDIA_ADDRESS, NAVIGATION_ADDRESS);
        assertThat(duckingInfo.getAddressesToUnduck()).isEmpty();
        assertThat(duckingInfo.getPlaybackMetaDataHoldingFocus())
                .containsExactlyElementsIn(playbackTrackMetadataHoldingFocus);

        // Then decimate safety
        focusHolders = generateAudioFocusInfoForAudioAttributes(audioAttributesWithoutSafety);
        playbackTrackMetadataHoldingFocus =
                CarHalAudioUtils.audioAttributesToMetadatas(audioAttributesWithoutSafety,
                        mockZone);

        CarDuckingInfo duckingInfo1 =
                CarDuckingUtils.generateDuckingInfo(duckingInfo, focusHolders, mockZone);

        assertThat(duckingInfo1.getZoneId()).isEqualTo(ZONE_ID);
        assertThat(duckingInfo1.getAddressesToDuck()).containsExactly(MEDIA_ADDRESS);
        assertThat(duckingInfo1.getAddressesToUnduck()).containsExactly(NAVIGATION_ADDRESS);
        assertThat(duckingInfo1.getPlaybackMetaDataHoldingFocus())
                .containsExactlyElementsIn(playbackTrackMetadataHoldingFocus);

        // Then decimate nav
        focusHolders = generateAudioFocusInfoForAudioAttributes(audioAttributesWithOnlyMedia);
        playbackTrackMetadataHoldingFocus =
                CarHalAudioUtils.audioAttributesToMetadatas(audioAttributesWithOnlyMedia, mockZone);

        CarDuckingInfo duckingInfo2 =
                CarDuckingUtils.generateDuckingInfo(duckingInfo1, focusHolders, mockZone);

        assertThat(duckingInfo2.getZoneId()).isEqualTo(ZONE_ID);
        assertThat(duckingInfo2.getAddressesToDuck()).isEmpty();
        assertThat(duckingInfo2.getAddressesToUnduck()).containsExactly(MEDIA_ADDRESS);
        assertThat(duckingInfo2.getPlaybackMetaDataHoldingFocus())
                .containsExactlyElementsIn(playbackTrackMetadataHoldingFocus);

        // back to none holding focus
        focusHolders = new ArrayList<AudioFocusInfo>();
        playbackTrackMetadataHoldingFocus =
                CarHalAudioUtils.audioAttributesToMetadatas(audioAttributesWithOnlyMedia, mockZone);

        CarDuckingInfo duckingInfo3 =
                CarDuckingUtils.generateDuckingInfo(duckingInfo2, focusHolders, mockZone);

        assertThat(duckingInfo3.getZoneId()).isEqualTo(ZONE_ID);
        assertThat(duckingInfo3.getAddressesToDuck()).isEmpty();
        assertThat(duckingInfo3.getAddressesToUnduck()).isEmpty();
        assertThat(duckingInfo3.getPlaybackMetaDataHoldingFocus()).isEmpty();
    }

    private static AudioFocusInfo generateAudioFocusInfoForAudioAttributes(
            AudioAttributes audioAttributes) {
        return new AudioFocusInfo(audioAttributes, /* clientUid= */ 0,
                "client_id", "package.name",
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT, /* lossReceived= */ 0,
                /* flags= */ 0, /* sdk= */  0);
    }

    private static List<AudioFocusInfo> generateAudioFocusInfoForAudioAttributes(
            List<AudioAttributes> audioAttributes) {
        List<AudioFocusInfo> audioFocusInfos = new ArrayList<>(audioAttributes.size());
        for (int index = 0; index < audioAttributes.size(); index++) {
            audioFocusInfos
                    .add(generateAudioFocusInfoForAudioAttributes(audioAttributes.get(index)));
        }
        return audioFocusInfos;
    }

    private static CarAudioZone generateAudioZoneMock() {
        CarAudioZone mockZone = mock(CarAudioZone.class);
        when(mockZone.getAddressForContext(MUSIC)).thenReturn(MEDIA_ADDRESS);
        when(mockZone.getAddressForContext(EMERGENCY)).thenReturn(EMERGENCY_ADDRESS);
        when(mockZone.getAddressForContext(CALL)).thenReturn(CALL_ADDRESS);
        when(mockZone.getAddressForContext(NAVIGATION)).thenReturn(NAVIGATION_ADDRESS);
        when(mockZone.getAddressForContext(INVALID)).thenThrow(new IllegalArgumentException());
        when(mockZone.getAddressForContext(NAVIGATION)).thenReturn(NAVIGATION_ADDRESS);

        return mockZone;
    }

    private CarDuckingInfo getEmptyCarDuckingInfo() {
        return new CarDuckingInfo(
                ZONE_ID,
                new ArrayList<String>(),
                new ArrayList<String>(),
                new ArrayList<PlaybackTrackMetadata>());
    }
}
