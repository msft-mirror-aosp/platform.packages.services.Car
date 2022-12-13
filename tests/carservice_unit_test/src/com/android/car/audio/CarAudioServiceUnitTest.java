/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static android.car.Car.PERMISSION_CAR_CONTROL_AUDIO_SETTINGS;
import static android.car.Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME;
import static android.car.media.CarAudioManager.AUDIO_FEATURE_DYNAMIC_ROUTING;
import static android.car.media.CarAudioManager.AUDIO_FEATURE_VOLUME_GROUP_MUTING;
import static android.car.media.CarAudioManager.INVALID_AUDIO_ZONE;
import static android.car.media.CarAudioManager.INVALID_VOLUME_GROUP_ID;
import static android.car.media.CarAudioManager.PRIMARY_AUDIO_ZONE;
import static android.car.test.mocks.AndroidMockitoHelper.mockContextCheckCallingOrSelfPermission;
import static android.content.pm.PackageManager.PERMISSION_DENIED;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.media.AudioAttributes.USAGE_ALARM;
import static android.media.AudioAttributes.USAGE_ANNOUNCEMENT;
import static android.media.AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY;
import static android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE;
import static android.media.AudioAttributes.USAGE_ASSISTANCE_SONIFICATION;
import static android.media.AudioAttributes.USAGE_ASSISTANT;
import static android.media.AudioAttributes.USAGE_CALL_ASSISTANT;
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
import static android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION_SIGNALLING;
import static android.media.AudioDeviceInfo.TYPE_BUILTIN_MIC;
import static android.media.AudioDeviceInfo.TYPE_FM_TUNER;
import static android.media.AudioManager.AUDIOFOCUS_GAIN;
import static android.media.AudioManager.AUDIOFOCUS_LOSS;
import static android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
import static android.media.AudioManager.EXTRA_VOLUME_STREAM_TYPE;
import static android.media.AudioManager.FLAG_FROM_KEY;
import static android.media.AudioManager.FLAG_SHOW_UI;
import static android.media.AudioManager.MASTER_MUTE_CHANGED_ACTION;
import static android.media.AudioManager.STREAM_MUSIC;
import static android.media.AudioManager.SUCCESS;
import static android.media.AudioManager.VOLUME_CHANGED_ACTION;
import static android.os.Build.VERSION.SDK_INT;

import static com.android.car.R.bool.audioPersistMasterMuteState;
import static com.android.car.R.bool.audioUseCarVolumeGroupMuting;
import static com.android.car.R.bool.audioUseDynamicRouting;
import static com.android.car.R.bool.audioUseHalDuckingSignals;
import static com.android.car.R.integer.audioVolumeAdjustmentContextsVersion;
import static com.android.car.R.integer.audioVolumeKeyEventTimeoutMs;
import static com.android.car.audio.CarAudioService.CAR_DEFAULT_AUDIO_ATTRIBUTE;
import static com.android.car.audio.GainBuilder.DEFAULT_GAIN;
import static com.android.car.audio.GainBuilder.MAX_GAIN;
import static com.android.car.audio.GainBuilder.MIN_GAIN;
import static com.android.car.audio.GainBuilder.STEP_SIZE;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import android.car.Car;
import android.car.CarOccupantZoneManager;
import android.car.ICarOccupantZoneCallback;
import android.car.builtin.media.AudioManagerHelper;
import android.car.builtin.media.AudioManagerHelper.AudioPatchInfo;
import android.car.builtin.os.UserManagerHelper;
import android.car.media.CarAudioPatchHandle;
import android.car.media.CarVolumeGroupInfo;
import android.car.settings.CarSettings;
import android.car.test.mocks.AbstractExtendedMockitoTestCase;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.hardware.automotive.audiocontrol.IAudioControl;
import android.media.AudioAttributes;
import android.media.AudioDeviceAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFocusInfo;
import android.media.AudioGain;
import android.media.AudioManager;
import android.media.IAudioService;
import android.media.audiopolicy.AudioPolicy;
import android.net.Uri;
import android.os.IBinder;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;

import com.android.car.CarLocalServices;
import com.android.car.CarOccupantZoneService;
import com.android.car.R;
import com.android.car.audio.hal.AudioControlFactory;
import com.android.car.audio.hal.AudioControlWrapper;
import com.android.car.audio.hal.AudioControlWrapper.AudioControlDeathRecipient;
import com.android.car.audio.hal.AudioControlWrapperAidl;
import com.android.car.oem.CarOemProxyService;
import com.android.car.test.utils.TemporaryFile;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.InputStream;

@RunWith(MockitoJUnitRunner.class)
public final class CarAudioServiceUnitTest extends AbstractExtendedMockitoTestCase {
    private static final String TAG = CarAudioServiceUnitTest.class.getSimpleName();
    private static final int VOLUME_KEY_EVENT_TIMEOUT_MS = 3000;
    private static final int AUDIO_CONTEXT_PRIORITY_LIST_VERSION_ONE = 1;
    private static final int AUDIO_CONTEXT_PRIORITY_LIST_VERSION_TWO = 2;
    private static final String MEDIA_TEST_DEVICE = "media_bus_device";
    private static final String NAVIGATION_TEST_DEVICE = "navigation_bus_device";
    private static final String CALL_TEST_DEVICE = "call_bus_device";
    private static final String NOTIFICATION_TEST_DEVICE = "notification_bus_device";
    private static final String VOICE_TEST_DEVICE = "voice_bus_device";
    private static final String RING_TEST_DEVICE = "ring_bus_device";
    private static final String ALARM_TEST_DEVICE = "alarm_bus_device";
    private static final String SYSTEM_BUS_DEVICE = "system_bus_device";
    private static final String SECONDARY_TEST_DEVICE = "secondary_zone_bus";
    private static final String PRIMARY_ZONE_MICROPHONE_ADDRESS = "Built-In Mic";
    private static final String PRIMARY_ZONE_FM_TUNER_ADDRESS = "FM Tuner";
    // From the car audio configuration file in /res/raw/car_audio_configuration.xml
    private static final int SECONDARY_ZONE_ID = 1;
    private static final int OUT_OF_RANGE_ZONE = SECONDARY_ZONE_ID + 1;
    private static final int PRIMARY_ZONE_VOLUME_GROUP_COUNT = 4;
    private static final int SECONDARY_ZONE_VOLUME_GROUP_COUNT = 1;
    private static final int SECONDARY_ZONE_VOLUME_GROUP_ID = SECONDARY_ZONE_VOLUME_GROUP_COUNT - 1;
    private static final int TEST_PRIMARY_GROUP = 0;
    private static final int TEST_SECONDARY_GROUP = 1;
    private static final int TEST_PRIMARY_GROUP_INDEX = 0;
    private static final int TEST_FLAGS = 0;
    private static final float TEST_VALUE = -.75f;
    private static final float INVALID_TEST_VALUE = -1.5f;

    private static final String PROPERTY_RO_ENABLE_AUDIO_PATCH =
            "ro.android.car.audio.enableaudiopatch";

    private static final int MEDIA_APP_UID = 1086753;
    private static final String MEDIA_CLIENT_ID = "media-client-id";
    private static final String MEDIA_PACKAGE_NAME = "com.android.car.audio";
    private static final int MEDIA_EMPTY_FLAG = 0;
    private static final String REGISTRATION_ID = "meh";
    private static final int MEDIA_VOLUME_GROUP_ID = 0;
    private static final int NAVIGATION_VOLUME_GROUP_ID = 1;
    private static final int INVALID_USAGE = -1;
    private static final int INVALID_AUDIO_FEATURE = -1;
    private static final int TEST_DRIVER_USER_ID = 10;
    private static final int TEST_USER_ID = 11;
    private static final int TEST_USER_ID_SECONDARY = 12;

    private static final CarVolumeGroupInfo TEST_PRIMARY_VOLUME_INFO =
            new CarVolumeGroupInfo.Builder("group id " + TEST_PRIMARY_GROUP, PRIMARY_AUDIO_ZONE,
                    TEST_PRIMARY_GROUP).setMuted(true).setVolumeGain(DEFAULT_GAIN).build();

    private static final CarVolumeGroupInfo TEST_SECONDARY_VOLUME_INFO =
            new CarVolumeGroupInfo.Builder("group id " + TEST_SECONDARY_GROUP, PRIMARY_AUDIO_ZONE,
                    TEST_SECONDARY_GROUP).setMuted(true).setVolumeGain(DEFAULT_GAIN).build();

    private CarAudioService mCarAudioService;
    @Mock
    private Context mMockContext;
    @Mock
    private TelephonyManager mMockTelephonyManager;
    @Mock
    private AudioManager mAudioManager;
    @Mock
    private Resources mMockResources;
    @Mock
    private ContentResolver mMockContentResolver;
    @Mock
    IBinder mBinder;
    @Mock
    IBinder mVolumeCallbackBinder;
    @Mock
    IAudioControl mAudioControl;
    @Mock
    private PackageManager mMockPackageManager;
    @Mock
    private CarOccupantZoneService mMockOccupantZoneService;
    @Mock
    private CarOemProxyService mMockCarOemProxyService;
    @Mock
    private IAudioService mMockAudioService;
    @Mock
    private Uri mNavSettingUri;
    @Mock
    private AudioControlWrapperAidl mAudioControlWrapperAidl;
    @Mock
    private CarVolumeCallbackHandler mCarVolumeCallbackHandler;

    private boolean mPersistMasterMute = true;
    private boolean mUseDynamicRouting = true;
    private boolean mUseHalAudioDucking = true;
    private boolean mUseCarVolumeGroupMuting = true;

    private TemporaryFile mTemporaryAudioConfigurationFile;
    private TemporaryFile mTemporaryAudioConfigurationWithoutZoneMappingFile;
    private Context mContext;
    private AudioDeviceInfo mMicrophoneInputDevice;
    private AudioDeviceInfo mFmTunerInputDevice;
    private AudioDeviceInfo mMediaOutputDevice;
    @Captor
    private ArgumentCaptor<BroadcastReceiver> mVolumeReceiverCaptor;

    public CarAudioServiceUnitTest() {
        super(CarAudioService.TAG);
    }

    @Override
    protected void onSessionBuilder(CustomMockitoSessionBuilder session) {
        session
                .spyStatic(AudioManagerHelper.class)
                .spyStatic(AudioControlWrapperAidl.class)
                .spyStatic(AudioControlFactory.class)
                .spyStatic(SystemProperties.class)
                .spyStatic(ServiceManager.class);
    }

    @Before
    public void setUp() throws Exception {
        mContext = ApplicationProvider.getApplicationContext();

        try (InputStream configurationStream = mContext.getResources().openRawResource(
                R.raw.car_audio_configuration)) {
            mTemporaryAudioConfigurationFile = new TemporaryFile("xml");
            mTemporaryAudioConfigurationFile.write(new String(configurationStream.readAllBytes()));
            Log.i(TAG, "Temporary Car Audio Configuration File Location: "
                    + mTemporaryAudioConfigurationFile.getPath());
        }

        try (InputStream configurationStream = mContext.getResources().openRawResource(
                R.raw.car_audio_configuration_without_zone_mapping)) {
            mTemporaryAudioConfigurationWithoutZoneMappingFile = new TemporaryFile("xml");
            mTemporaryAudioConfigurationWithoutZoneMappingFile
                    .write(new String(configurationStream.readAllBytes()));
            Log.i(TAG, "Temporary Car Audio Configuration without Zone mapping File Location: "
                    + mTemporaryAudioConfigurationWithoutZoneMappingFile.getPath());
        }

        mockGrantCarControlAudioSettingsPermission();

        setupAudioControlHAL();
        setupService();

        when(Settings.Secure.getUriFor(
                CarSettings.Secure.KEY_AUDIO_FOCUS_NAVIGATION_REJECTED_DURING_CALL))
                .thenReturn(mNavSettingUri);
    }

    @After
    public void tearDown() throws Exception {
        mTemporaryAudioConfigurationFile.close();
        mTemporaryAudioConfigurationWithoutZoneMappingFile.close();
        CarLocalServices.removeServiceForTest(CarOemProxyService.class);
        CarLocalServices.removeServiceForTest(CarOccupantZoneService.class);
    }

    private void setupAudioControlHAL() {
        when(mBinder.queryLocalInterface(anyString())).thenReturn(mAudioControl);
        doReturn(mBinder).when(AudioControlWrapperAidl::getService);
        when(mAudioControlWrapperAidl.supportsFeature(
                AudioControlWrapper.AUDIOCONTROL_FEATURE_AUDIO_DUCKING)).thenReturn(true);
        when(mAudioControlWrapperAidl.supportsFeature(
                AudioControlWrapper.AUDIOCONTROL_FEATURE_AUDIO_FOCUS)).thenReturn(true);
        when(mAudioControlWrapperAidl.supportsFeature(
                AudioControlWrapper.AUDIOCONTROL_FEATURE_AUDIO_GAIN_CALLBACK)).thenReturn(true);
        when(mAudioControlWrapperAidl.supportsFeature(
                AudioControlWrapper.AUDIOCONTROL_FEATURE_AUDIO_GROUP_MUTING)).thenReturn(true);
        doReturn(mAudioControlWrapperAidl)
                .when(() -> AudioControlFactory.newAudioControl());
    }

    private void setupService() throws Exception {
        when(mMockContext.getSystemService(Context.TELECOM_SERVICE))
                .thenReturn(mMockTelephonyManager);
        when(mMockContext.getPackageManager()).thenReturn(mMockPackageManager);
        doReturn(true)
                .when(() -> AudioManagerHelper
                        .setAudioDeviceGain(any(), any(), anyInt(), anyBoolean()));
        doReturn(true)
                .when(() -> SystemProperties.getBoolean(PROPERTY_RO_ENABLE_AUDIO_PATCH, false));

        CarLocalServices.removeServiceForTest(CarOccupantZoneService.class);
        CarLocalServices.addService(CarOccupantZoneService.class, mMockOccupantZoneService);

        CarLocalServices.removeServiceForTest(CarOemProxyService.class);
        CarLocalServices.addService(CarOemProxyService.class, mMockCarOemProxyService);

        setupAudioManager();

        setupResources();

        mCarAudioService =
                new CarAudioService(mMockContext,
                        mTemporaryAudioConfigurationFile.getFile().getAbsolutePath(),
                        mCarVolumeCallbackHandler);
    }

    private void setupAudioManager() throws Exception {
        AudioDeviceInfo[] outputDevices = generateOutputDeviceInfos();
        AudioDeviceInfo[] inputDevices = generateInputDeviceInfos();
        when(mAudioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS))
                .thenReturn(outputDevices);
        when(mAudioManager.getDevices(AudioManager.GET_DEVICES_INPUTS))
               .thenReturn(inputDevices);
        when(mMockContext.getSystemService(Context.AUDIO_SERVICE)).thenReturn(mAudioManager);

        when(mAudioManager.registerAudioPolicy(any())).thenAnswer(invocation -> {
            AudioPolicy policy = (AudioPolicy) invocation.getArguments()[0];
            policy.setRegistration(REGISTRATION_ID);
            return SUCCESS;
        });

        IBinder mockBinder = mock(IBinder.class);
        when(mockBinder.queryLocalInterface(any())).thenReturn(mMockAudioService);
        doReturn(mockBinder).when(() -> ServiceManager.getService(Context.AUDIO_SERVICE));
    }

    private void setupResources() {
        when(mMockContext.getContentResolver()).thenReturn(mMockContentResolver);
        when(mMockContext.createContextAsUser(any(), anyInt())).thenReturn(mMockContext);
        when(mMockContext.getResources()).thenReturn(mMockResources);
        when(mMockResources.getBoolean(audioUseDynamicRouting)).thenReturn(mUseDynamicRouting);
        when(mMockResources.getInteger(audioVolumeKeyEventTimeoutMs))
                .thenReturn(VOLUME_KEY_EVENT_TIMEOUT_MS);
        when(mMockResources.getBoolean(audioUseHalDuckingSignals)).thenReturn(mUseHalAudioDucking);
        when(mMockResources.getBoolean(audioUseCarVolumeGroupMuting))
                .thenReturn(mUseCarVolumeGroupMuting);
        when(mMockResources.getInteger(audioVolumeAdjustmentContextsVersion))
                .thenReturn(AUDIO_CONTEXT_PRIORITY_LIST_VERSION_TWO);
        when(mMockResources.getBoolean(audioPersistMasterMuteState)).thenReturn(mPersistMasterMute);
    }

    @Test
    public void constructor_withNullContext_fails() {
        NullPointerException thrown =
                assertThrows(NullPointerException.class, () -> new CarAudioService(null));

        expectWithMessage("Car Audio Service Construction")
                .that(thrown).hasMessageThat().contains("Context");
    }

    @Test
    public void constructor_withNullContextAndNullPath_fails() {
        NullPointerException thrown =
                assertThrows(NullPointerException.class,
                        () -> new CarAudioService(/* context= */null,
                                /* audioConfigurationPath= */ null,
                                /* carVolumeCallbackHandler= */ null));

        expectWithMessage("Car Audio Service Construction")
                .that(thrown).hasMessageThat().contains("Context");
    }

    @Test
    public void constructor_withInvalidVolumeConfiguration_fails() {
        when(mMockResources.getInteger(audioVolumeAdjustmentContextsVersion))
                .thenReturn(AUDIO_CONTEXT_PRIORITY_LIST_VERSION_ONE);

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> new CarAudioService(mMockContext));

        expectWithMessage("Car Audio Service Construction")
                .that(thrown).hasMessageThat()
                .contains("requires audioVolumeAdjustmentContextsVersion 2");
    }

    @Test
    public void getAudioZoneIds_withBaseConfiguration_returnAllTheZones() {
        mCarAudioService.init();

        expectWithMessage("Car Audio Service Zones")
                .that(mCarAudioService.getAudioZoneIds())
                .asList().containsExactly(PRIMARY_AUDIO_ZONE, SECONDARY_ZONE_ID);
    }

    @Test
    public void getVolumeGroupCount_onPrimaryZone_returnsAllGroups() {
        mCarAudioService.init();

        expectWithMessage("Primary zone car volume group count")
                .that(mCarAudioService.getVolumeGroupCount(PRIMARY_AUDIO_ZONE))
                .isEqualTo(PRIMARY_ZONE_VOLUME_GROUP_COUNT);
    }

    @Test
    public void getVolumeGroupCount_onPrimaryZone__withNonDynamicRouting_returnsAllGroups() {
        when(mMockResources.getBoolean(audioUseDynamicRouting))
                .thenReturn(/* useDynamicRouting= */ false);
        CarAudioService nonDynamicAudioService = new CarAudioService(mMockContext,
                mTemporaryAudioConfigurationFile.getFile().getAbsolutePath(),
                mCarVolumeCallbackHandler);
        nonDynamicAudioService.init();

        expectWithMessage("Non dynamic routing primary zone car volume group count")
                .that(nonDynamicAudioService.getVolumeGroupCount(PRIMARY_AUDIO_ZONE))
                .isEqualTo(CarAudioDynamicRouting.STREAM_TYPES.length);
    }

    @Test
    public void getVolumeGroupIdForUsage_forMusicUsage() {
        mCarAudioService.init();

        expectWithMessage("Primary zone's media car volume group id")
                .that(mCarAudioService.getVolumeGroupIdForUsage(PRIMARY_AUDIO_ZONE, USAGE_MEDIA))
                .isEqualTo(MEDIA_VOLUME_GROUP_ID);
    }

    @Test
    public void getVolumeGroupIdForUsage_withNonDynamicRouting_forMusicUsage() {
        when(mMockResources.getBoolean(audioUseDynamicRouting))
                .thenReturn(/* useDynamicRouting= */ false);
        CarAudioService nonDynamicAudioService = new CarAudioService(mMockContext,
                mTemporaryAudioConfigurationFile.getFile().getAbsolutePath(),
                mCarVolumeCallbackHandler);
        nonDynamicAudioService.init();

        expectWithMessage("Non dynamic routing primary zone's media car volume group id")
                .that(nonDynamicAudioService.getVolumeGroupIdForUsage(PRIMARY_AUDIO_ZONE,
                        USAGE_MEDIA)).isEqualTo(MEDIA_VOLUME_GROUP_ID);
    }

    @Test
    public void getVolumeGroupIdForUsage_forNavigationUsage() {
        mCarAudioService.init();

        expectWithMessage("Primary zone's navigation car volume group id")
                .that(mCarAudioService.getVolumeGroupIdForUsage(PRIMARY_AUDIO_ZONE,
                        USAGE_ASSISTANCE_NAVIGATION_GUIDANCE))
                .isEqualTo(NAVIGATION_VOLUME_GROUP_ID);
    }

    @Test
    public void getVolumeGroupIdForUsage_withNonDynamicRouting_forNavigationUsage() {
        when(mMockResources.getBoolean(audioUseDynamicRouting))
                .thenReturn(/* useDynamicRouting= */ false);
        CarAudioService nonDynamicAudioService = new CarAudioService(mMockContext,
                mTemporaryAudioConfigurationFile.getFile().getAbsolutePath(),
                mCarVolumeCallbackHandler);
        nonDynamicAudioService.init();

        expectWithMessage("Non dynamic routing primary zone's navigation car volume group id")
                .that(nonDynamicAudioService.getVolumeGroupIdForUsage(PRIMARY_AUDIO_ZONE,
                        USAGE_ASSISTANCE_NAVIGATION_GUIDANCE))
                .isEqualTo(INVALID_VOLUME_GROUP_ID);
    }

    @Test
    public void getVolumeGroupIdForUsage_forInvalidUsage_returnsInvalidGroupId() {
        mCarAudioService.init();

        expectWithMessage("Primary zone's invalid car volume group id")
                .that(mCarAudioService.getVolumeGroupIdForUsage(PRIMARY_AUDIO_ZONE, INVALID_USAGE))
                .isEqualTo(INVALID_VOLUME_GROUP_ID);
    }

    @Test
    public void
            getVolumeGroupIdForUsage_forInvalidUsage_withNonDynamicRouting_returnsInvalidGroupId() {
        when(mMockResources.getBoolean(audioUseDynamicRouting))
                .thenReturn(/* useDynamicRouting= */ false);
        CarAudioService nonDynamicAudioService = new CarAudioService(mMockContext,
                mTemporaryAudioConfigurationFile.getFile().getAbsolutePath(),
                mCarVolumeCallbackHandler);
        nonDynamicAudioService.init();

        expectWithMessage("Non dynamic routing primary zone's invalid car volume group id")
                .that(nonDynamicAudioService.getVolumeGroupIdForUsage(PRIMARY_AUDIO_ZONE,
                        INVALID_USAGE)).isEqualTo(INVALID_VOLUME_GROUP_ID);
    }

    @Test
    public void getVolumeGroupIdForUsage_forUnknownUsage_returnsMediaGroupId() {
        mCarAudioService.init();

        expectWithMessage("Primary zone's unknown car volume group id")
                .that(mCarAudioService.getVolumeGroupIdForUsage(PRIMARY_AUDIO_ZONE, USAGE_UNKNOWN))
                .isEqualTo(MEDIA_VOLUME_GROUP_ID);
    }

    @Test
    public void getVolumeGroupIdForUsage_forVirtualUsage_returnsInvalidGroupId() {
        mCarAudioService.init();

        expectWithMessage("Primary zone's virtual car volume group id")
                .that(mCarAudioService.getVolumeGroupIdForUsage(PRIMARY_AUDIO_ZONE,
                        AudioManagerHelper.getUsageVirtualSource()))
                .isEqualTo(INVALID_VOLUME_GROUP_ID);
    }

    @Test
    public void getVolumeGroupCount_onSecondaryZone_returnsAllGroups() {
        mCarAudioService.init();

        expectWithMessage("Secondary Zone car volume group count")
                .that(mCarAudioService.getVolumeGroupCount(SECONDARY_ZONE_ID))
                .isEqualTo(SECONDARY_ZONE_VOLUME_GROUP_COUNT);
    }

    @Test
    public void getUsagesForVolumeGroupId_forMusicContext() {
        mCarAudioService.init();


        expectWithMessage("Primary zone's music car volume group id usages")
                .that(mCarAudioService.getUsagesForVolumeGroupId(PRIMARY_AUDIO_ZONE,
                        MEDIA_VOLUME_GROUP_ID)).asList()
                .containsExactly(USAGE_UNKNOWN, USAGE_GAME, USAGE_MEDIA, USAGE_ANNOUNCEMENT,
                        USAGE_NOTIFICATION, USAGE_NOTIFICATION_EVENT);
    }

    @Test
    public void getUsagesForVolumeGroupId_forSystemContext() {
        mCarAudioService.init();
        int systemVolumeGroup =
                mCarAudioService.getVolumeGroupIdForUsage(PRIMARY_AUDIO_ZONE, USAGE_EMERGENCY);

        expectWithMessage("Primary zone's system car volume group id usages")
                .that(mCarAudioService.getUsagesForVolumeGroupId(PRIMARY_AUDIO_ZONE,
                        systemVolumeGroup)).asList().containsExactly(USAGE_ALARM, USAGE_EMERGENCY,
                        USAGE_SAFETY, USAGE_VEHICLE_STATUS, USAGE_ASSISTANCE_SONIFICATION);
    }

    @Test
    public void getUsagesForVolumeGroupId_onSecondaryZone_forSingleVolumeGroupId_returnAllUsages() {
        mCarAudioService.init();

        expectWithMessage("Secondary Zone's car volume group id usages")
                .that(mCarAudioService.getUsagesForVolumeGroupId(SECONDARY_ZONE_ID,
                        SECONDARY_ZONE_VOLUME_GROUP_ID))
                .asList().containsExactly(USAGE_UNKNOWN, USAGE_MEDIA,
                        USAGE_VOICE_COMMUNICATION, USAGE_VOICE_COMMUNICATION_SIGNALLING,
                        USAGE_ALARM, USAGE_NOTIFICATION, USAGE_NOTIFICATION_RINGTONE,
                        USAGE_NOTIFICATION_EVENT, USAGE_ASSISTANCE_ACCESSIBILITY,
                        USAGE_ASSISTANCE_NAVIGATION_GUIDANCE, USAGE_ASSISTANCE_SONIFICATION,
                        USAGE_GAME, USAGE_ASSISTANT, USAGE_CALL_ASSISTANT, USAGE_EMERGENCY,
                        USAGE_ANNOUNCEMENT, USAGE_SAFETY, USAGE_VEHICLE_STATUS);
    }

    @Test
    public void getUsagesForVolumeGroupId_withoutDynamicRouting() {
        when(mMockResources.getBoolean(audioUseDynamicRouting))
                .thenReturn(/* useDynamicRouting= */ false);
        CarAudioService nonDynamicAudioService = new CarAudioService(mMockContext,
                mTemporaryAudioConfigurationFile.getFile().getAbsolutePath(),
                mCarVolumeCallbackHandler);
        nonDynamicAudioService.init();

        expectWithMessage("Media car volume group id without dynamic routing").that(
                nonDynamicAudioService.getUsagesForVolumeGroupId(PRIMARY_AUDIO_ZONE,
                MEDIA_VOLUME_GROUP_ID)).asList()
                .containsExactly(CarAudioDynamicRouting.STREAM_TYPE_USAGES[MEDIA_VOLUME_GROUP_ID]);
    }

    @Test
    public void createAudioPatch_onMediaOutputDevice_failsForConfigurationMissing() {
        mCarAudioService.init();

        doReturn(false)
                .when(() -> SystemProperties.getBoolean(PROPERTY_RO_ENABLE_AUDIO_PATCH, false));

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> mCarAudioService
                        .createAudioPatch(PRIMARY_ZONE_FM_TUNER_ADDRESS,
                                USAGE_MEDIA, DEFAULT_GAIN));

        expectWithMessage("FM and Media Audio Patch Exception")
                .that(thrown).hasMessageThat().contains("Audio Patch APIs not enabled");
    }

    @Test
    public void createAudioPatch_onMediaOutputDevice_failsForMissingPermission() {
        mCarAudioService.init();

        mockDenyCarControlAudioSettingsPermission();

        SecurityException thrown = assertThrows(SecurityException.class,
                () -> mCarAudioService
                        .createAudioPatch(PRIMARY_ZONE_FM_TUNER_ADDRESS,
                                USAGE_MEDIA, DEFAULT_GAIN));

        expectWithMessage("FM and Media Audio Patch Permission Exception")
                .that(thrown).hasMessageThat().contains(PERMISSION_CAR_CONTROL_AUDIO_SETTINGS);
    }

    @Test
    public void createAudioPatch_onMediaOutputDevice_succeeds() {
        mCarAudioService.init();

        mockGrantCarControlAudioSettingsPermission();
        doReturn(false)
                .when(() -> SystemProperties.getBoolean(PROPERTY_RO_ENABLE_AUDIO_PATCH, true));
        doReturn(new AudioPatchInfo(PRIMARY_ZONE_FM_TUNER_ADDRESS, MEDIA_TEST_DEVICE, 0))
                .when(() -> AudioManagerHelper
                        .createAudioPatch(mFmTunerInputDevice, mMediaOutputDevice, DEFAULT_GAIN));

        CarAudioPatchHandle audioPatch = mCarAudioService
                .createAudioPatch(PRIMARY_ZONE_FM_TUNER_ADDRESS, USAGE_MEDIA, DEFAULT_GAIN);

        expectWithMessage("Audio Patch Sink Address")
                .that(audioPatch.getSinkAddress()).isEqualTo(MEDIA_TEST_DEVICE);
        expectWithMessage("Audio Patch Source Address")
                .that(audioPatch.getSourceAddress()).isEqualTo(PRIMARY_ZONE_FM_TUNER_ADDRESS);
        expectWithMessage("Audio Patch Handle")
                .that(audioPatch.getHandleId()).isEqualTo(0);
    }

    @Test
    public void releaseAudioPatch_failsForConfigurationMissing() {
        mCarAudioService.init();

        doReturn(false)
                .when(() -> SystemProperties.getBoolean(PROPERTY_RO_ENABLE_AUDIO_PATCH, false));
        CarAudioPatchHandle carAudioPatchHandle =
                new CarAudioPatchHandle(0, PRIMARY_ZONE_FM_TUNER_ADDRESS, MEDIA_TEST_DEVICE);

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> mCarAudioService.releaseAudioPatch(carAudioPatchHandle));

        expectWithMessage("Release FM and Media Audio Patch Exception")
                .that(thrown).hasMessageThat().contains("Audio Patch APIs not enabled");
    }

    @Test
    public void releaseAudioPatch_failsForMissingPermission() {
        mCarAudioService.init();

        mockDenyCarControlAudioSettingsPermission();
        CarAudioPatchHandle carAudioPatchHandle =
                new CarAudioPatchHandle(0, PRIMARY_ZONE_FM_TUNER_ADDRESS, MEDIA_TEST_DEVICE);

        SecurityException thrown = assertThrows(SecurityException.class,
                () -> mCarAudioService.releaseAudioPatch(carAudioPatchHandle));

        expectWithMessage("FM and Media Audio Patch Permission Exception")
                .that(thrown).hasMessageThat().contains(PERMISSION_CAR_CONTROL_AUDIO_SETTINGS);
    }

    @Test
    public void releaseAudioPatch_forNullSourceAddress_throwsNullPointerException() {
        mCarAudioService.init();
        mockGrantCarControlAudioSettingsPermission();
        doReturn(new AudioPatchInfo(PRIMARY_ZONE_FM_TUNER_ADDRESS, MEDIA_TEST_DEVICE, 0))
                .when(() -> AudioManagerHelper
                        .createAudioPatch(mFmTunerInputDevice, mMediaOutputDevice, DEFAULT_GAIN));

        CarAudioPatchHandle audioPatch = mock(CarAudioPatchHandle.class);
        when(audioPatch.getSourceAddress()).thenReturn(null);

        NullPointerException thrown = assertThrows(NullPointerException.class,
                () -> mCarAudioService.releaseAudioPatch(audioPatch));

        expectWithMessage("Release audio patch for null source address "
                + "and sink address Null Exception")
                .that(thrown).hasMessageThat()
                .contains("Source Address can not be null for patch id 0");
    }

    @Test
    public void releaseAudioPatch_failsForNullPatch() {
        mCarAudioService.init();

        assertThrows(NullPointerException.class,
                () -> mCarAudioService.releaseAudioPatch(null));
    }

    @Test
    public void setZoneIdForUid_withoutRoutingPermission_fails() {
        mCarAudioService.init();

        mockDenyCarControlAudioSettingsPermission();

        SecurityException thrown = assertThrows(SecurityException.class,
                () -> mCarAudioService.setZoneIdForUid(OUT_OF_RANGE_ZONE, MEDIA_APP_UID));

        expectWithMessage("Set Zone for UID Permission Exception")
                .that(thrown).hasMessageThat()
                .contains(Car.PERMISSION_CAR_CONTROL_AUDIO_SETTINGS);
    }

    @Test
    public void setZoneIdForUid_withoutDynamicRouting_fails() {
        when(mMockResources.getBoolean(audioUseDynamicRouting))
                .thenReturn(/* useDynamicRouting= */ false);
        CarAudioService nonDynamicAudioService = new CarAudioService(mMockContext,
                mTemporaryAudioConfigurationFile.getFile().getAbsolutePath(),
                mCarVolumeCallbackHandler);
        nonDynamicAudioService.init();

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> nonDynamicAudioService.setZoneIdForUid(PRIMARY_AUDIO_ZONE, MEDIA_APP_UID));

        expectWithMessage("Set Zone for UID Dynamic Configuration Exception")
                .that(thrown).hasMessageThat()
                .contains("Dynamic routing is required");
    }

    @Test
    public void setZoneIdForUid_withInvalidZone_fails() {
        mCarAudioService.init();

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> mCarAudioService.setZoneIdForUid(INVALID_AUDIO_ZONE, MEDIA_APP_UID));

        expectWithMessage("Set Zone for UID Invalid Zone Exception")
                .that(thrown).hasMessageThat()
                .contains("Invalid audio zone Id " + INVALID_AUDIO_ZONE);
    }

    @Test
    public void setZoneIdForUid_withOutOfRangeZone_fails() {
        mCarAudioService.init();

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> mCarAudioService.setZoneIdForUid(OUT_OF_RANGE_ZONE, MEDIA_APP_UID));

        expectWithMessage("Set Zone for UID Zone Out of Range Exception")
                .that(thrown).hasMessageThat()
                .contains("Invalid audio zone Id " + OUT_OF_RANGE_ZONE);
    }

    @Test
    public void setZoneIdForUid_withZoneAudioMapping_fails() {
        mCarAudioService.init();

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> mCarAudioService.setZoneIdForUid(PRIMARY_AUDIO_ZONE, MEDIA_APP_UID));

        expectWithMessage("Set Zone for UID With Audio Zone Mapping Exception")
                .that(thrown).hasMessageThat()
                .contains("UID based routing is not supported while using occupant zone mapping");
    }

    @Test
    public void setZoneIdForUid_withValidZone_succeeds() throws Exception {
        when(mMockAudioService.setUidDeviceAffinity(any(), anyInt(), any(), any()))
                .thenReturn(SUCCESS);
        CarAudioService noZoneMappingAudioService = new CarAudioService(mMockContext,
                mTemporaryAudioConfigurationWithoutZoneMappingFile.getFile().getAbsolutePath(),
                mCarVolumeCallbackHandler);
        noZoneMappingAudioService.init();

        boolean results = noZoneMappingAudioService
                .setZoneIdForUid(SECONDARY_ZONE_ID, MEDIA_APP_UID);

        expectWithMessage("Set Zone for UID Status").that(results).isTrue();
    }

    @Test
    public void setZoneIdForUid_onDifferentZones_succeeds() throws Exception {
        when(mMockAudioService.setUidDeviceAffinity(any(), anyInt(), any(), any()))
                .thenReturn(SUCCESS);
        CarAudioService noZoneMappingAudioService = new CarAudioService(mMockContext,
                mTemporaryAudioConfigurationWithoutZoneMappingFile.getFile().getAbsolutePath(),
                mCarVolumeCallbackHandler);
        noZoneMappingAudioService.init();

        noZoneMappingAudioService
                .setZoneIdForUid(SECONDARY_ZONE_ID, MEDIA_APP_UID);

        boolean results = noZoneMappingAudioService
                .setZoneIdForUid(PRIMARY_AUDIO_ZONE, MEDIA_APP_UID);

        expectWithMessage("Set Zone for UID For Different Zone")
                .that(results).isTrue();
    }

    @Test
    public void setZoneIdForUid_onDifferentZones_withAudioFocus_succeeds() throws Exception {
        when(mMockAudioService.setUidDeviceAffinity(any(), anyInt(), any(), any()))
                .thenReturn(SUCCESS);
        CarAudioService noZoneMappingAudioService = new CarAudioService(mMockContext,
                mTemporaryAudioConfigurationWithoutZoneMappingFile.getFile().getAbsolutePath(),
                mCarVolumeCallbackHandler);
        noZoneMappingAudioService.init();
        AudioFocusInfo audioFocusInfo = createAudioFocusInfoForMedia();

        noZoneMappingAudioService
                .setZoneIdForUid(SECONDARY_ZONE_ID, MEDIA_APP_UID);

        noZoneMappingAudioService
                .requestAudioFocusForTest(audioFocusInfo, AUDIOFOCUS_REQUEST_GRANTED);

        boolean results = noZoneMappingAudioService
                .setZoneIdForUid(PRIMARY_AUDIO_ZONE, MEDIA_APP_UID);

        expectWithMessage("Set Zone for UID For Different Zone with Audio Focus")
                .that(results).isTrue();
    }

    @Test
    public void getZoneIdForUid_withoutMappedUid_succeeds() throws Exception {
        when(mMockAudioService.setUidDeviceAffinity(any(), anyInt(), any(), any()))
                .thenReturn(SUCCESS);
        CarAudioService noZoneMappingAudioService = new CarAudioService(mMockContext,
                mTemporaryAudioConfigurationWithoutZoneMappingFile.getFile().getAbsolutePath(),
                mCarVolumeCallbackHandler);
        noZoneMappingAudioService.init();

        int zoneId = noZoneMappingAudioService
                .getZoneIdForUid(MEDIA_APP_UID);

        expectWithMessage("Get Zone for Non Mapped UID")
                .that(zoneId).isEqualTo(PRIMARY_AUDIO_ZONE);
    }

    @Test
    public void getZoneIdForUid_succeeds() throws Exception {
        when(mMockAudioService.setUidDeviceAffinity(any(), anyInt(), any(), any()))
                .thenReturn(SUCCESS);
        CarAudioService noZoneMappingAudioService = new CarAudioService(mMockContext,
                mTemporaryAudioConfigurationWithoutZoneMappingFile.getFile().getAbsolutePath(),
                mCarVolumeCallbackHandler);
        noZoneMappingAudioService.init();

        noZoneMappingAudioService
                .setZoneIdForUid(SECONDARY_ZONE_ID, MEDIA_APP_UID);

        int zoneId = noZoneMappingAudioService
                .getZoneIdForUid(MEDIA_APP_UID);

        expectWithMessage("Get Zone for UID Zone Id")
                .that(zoneId).isEqualTo(SECONDARY_ZONE_ID);
    }

    @Test
    public void getZoneIdForUid_afterSwitchingZones_succeeds() throws Exception {
        when(mMockAudioService.setUidDeviceAffinity(any(), anyInt(), any(), any()))
                .thenReturn(SUCCESS);
        CarAudioService noZoneMappingAudioService = new CarAudioService(mMockContext,
                mTemporaryAudioConfigurationWithoutZoneMappingFile.getFile().getAbsolutePath(),
                mCarVolumeCallbackHandler);
        noZoneMappingAudioService.init();

        noZoneMappingAudioService
                .setZoneIdForUid(SECONDARY_ZONE_ID, MEDIA_APP_UID);

        noZoneMappingAudioService
                .setZoneIdForUid(PRIMARY_AUDIO_ZONE, MEDIA_APP_UID);

        int zoneId = noZoneMappingAudioService
                .getZoneIdForUid(MEDIA_APP_UID);

        expectWithMessage("Get Zone for UID Zone Id")
                .that(zoneId).isEqualTo(PRIMARY_AUDIO_ZONE);
    }

    @Test
    public void clearZoneIdForUid_withoutRoutingPermission_fails() {
        mCarAudioService.init();

        mockDenyCarControlAudioSettingsPermission();

        SecurityException thrown = assertThrows(SecurityException.class,
                () -> mCarAudioService.clearZoneIdForUid(MEDIA_APP_UID));

        expectWithMessage("Clear Zone for UID Permission Exception")
                .that(thrown).hasMessageThat()
                .contains(Car.PERMISSION_CAR_CONTROL_AUDIO_SETTINGS);
    }

    @Test
    public void clearZoneIdForUid_withoutDynamicRouting_fails() {
        when(mMockResources.getBoolean(audioUseDynamicRouting))
                .thenReturn(/* useDynamicRouting= */ false);
        CarAudioService nonDynamicAudioService = new CarAudioService(mMockContext,
                mTemporaryAudioConfigurationFile.getFile().getAbsolutePath(),
                mCarVolumeCallbackHandler);
        nonDynamicAudioService.init();

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> nonDynamicAudioService.clearZoneIdForUid(MEDIA_APP_UID));

        expectWithMessage("Clear Zone for UID Dynamic Configuration Exception")
                .that(thrown).hasMessageThat()
                .contains("Dynamic routing is required");
    }

    @Test
    public void clearZoneIdForUid_withZoneAudioMapping_fails() {
        mCarAudioService.init();

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> mCarAudioService.clearZoneIdForUid(MEDIA_APP_UID));

        expectWithMessage("Clear Zone for UID Audio Zone Mapping Exception")
                .that(thrown).hasMessageThat()
                .contains("UID based routing is not supported while using occupant zone mapping");
    }

    @Test
    public void clearZoneIdForUid_forNonMappedUid_succeeds() throws Exception {
        CarAudioService noZoneMappingAudioService = new CarAudioService(mMockContext,
                mTemporaryAudioConfigurationWithoutZoneMappingFile.getFile().getAbsolutePath(),
                mCarVolumeCallbackHandler);
        noZoneMappingAudioService.init();

        boolean status = noZoneMappingAudioService
                .clearZoneIdForUid(MEDIA_APP_UID);

        expectWithMessage("Clear Zone for UID Audio Zone without Mapping")
                .that(status).isTrue();
    }

    @Test
    public void clearZoneIdForUid_forMappedUid_succeeds() throws Exception {
        when(mMockAudioService.setUidDeviceAffinity(any(), anyInt(), any(), any()))
                .thenReturn(SUCCESS);
        CarAudioService noZoneMappingAudioService = new CarAudioService(mMockContext,
                mTemporaryAudioConfigurationWithoutZoneMappingFile.getFile().getAbsolutePath(),
                mCarVolumeCallbackHandler);
        noZoneMappingAudioService.init();

        noZoneMappingAudioService
                .setZoneIdForUid(SECONDARY_ZONE_ID, MEDIA_APP_UID);

        boolean status = noZoneMappingAudioService.clearZoneIdForUid(MEDIA_APP_UID);

        expectWithMessage("Clear Zone for UID Audio Zone with Mapping")
                .that(status).isTrue();
    }

    @Test
    public void getZoneIdForUid_afterClearedUidMapping_returnsDefaultZone() throws Exception {
        when(mMockAudioService.setUidDeviceAffinity(any(), anyInt(), any(), any()))
                .thenReturn(SUCCESS);
        CarAudioService noZoneMappingAudioService = new CarAudioService(mMockContext,
                mTemporaryAudioConfigurationWithoutZoneMappingFile.getFile().getAbsolutePath(),
                mCarVolumeCallbackHandler);
        noZoneMappingAudioService.init();

        noZoneMappingAudioService
                .setZoneIdForUid(SECONDARY_ZONE_ID, MEDIA_APP_UID);

        noZoneMappingAudioService.clearZoneIdForUid(MEDIA_APP_UID);

        int zoneId = noZoneMappingAudioService.getZoneIdForUid(MEDIA_APP_UID);

        expectWithMessage("Get Zone for UID Audio Zone with Cleared Mapping")
                .that(zoneId).isEqualTo(PRIMARY_AUDIO_ZONE);
    }

    @Test
    public void setGroupVolume_withoutPermission_fails() {
        mCarAudioService.init();

        mockDenyCarControlAudioVolumePermission();

        SecurityException thrown = assertThrows(SecurityException.class,
                () -> mCarAudioService.setGroupVolume(PRIMARY_AUDIO_ZONE, TEST_PRIMARY_GROUP,
                        TEST_PRIMARY_GROUP_INDEX, TEST_FLAGS));

        expectWithMessage("Set Volume Group Permission Exception")
                .that(thrown).hasMessageThat()
                .contains(Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME);
    }

    @Test
    public void setGroupVolume_withDynamicRoutingDisabled() {
        when(mMockResources.getBoolean(audioUseDynamicRouting))
                .thenReturn(/* useDynamicRouting= */ false);
        CarAudioService nonDynamicAudioService = new CarAudioService(mMockContext,
                mTemporaryAudioConfigurationFile.getFile().getAbsolutePath(),
                mCarVolumeCallbackHandler);
        nonDynamicAudioService.init();

        nonDynamicAudioService.setGroupVolume(
                PRIMARY_AUDIO_ZONE, TEST_PRIMARY_GROUP, TEST_PRIMARY_GROUP_INDEX, TEST_FLAGS);

        verify(mAudioManager).setStreamVolume(
                CarAudioDynamicRouting.STREAM_TYPES[TEST_PRIMARY_GROUP],
                TEST_PRIMARY_GROUP_INDEX,
                TEST_FLAGS);
    }

    @Test
    public void getOutputDeviceAddressForUsage_forMusicUsage() {
        mCarAudioService.init();

        String mediaDeviceAddress =
                mCarAudioService.getOutputDeviceAddressForUsage(PRIMARY_AUDIO_ZONE, USAGE_MEDIA);

        expectWithMessage("Media usage audio device address")
                .that(mediaDeviceAddress).isEqualTo(MEDIA_TEST_DEVICE);
    }

    @Test
    public void getOutputDeviceAddressForUsage_withNonDynamicRouting_forMediaUsage_fails() {
        when(mMockResources.getBoolean(audioUseDynamicRouting))
                .thenReturn(/* useDynamicRouting= */ false);
        CarAudioService nonDynamicAudioService = new CarAudioService(mMockContext,
                mTemporaryAudioConfigurationFile.getFile().getAbsolutePath(),
                mCarVolumeCallbackHandler);
        nonDynamicAudioService.init();

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> nonDynamicAudioService
                        .getOutputDeviceAddressForUsage(PRIMARY_AUDIO_ZONE, USAGE_MEDIA));

        expectWithMessage("Non dynamic routing media usage audio device address exception")
                .that(thrown).hasMessageThat().contains("Dynamic routing is required");
    }

    @Test
    public void getOutputDeviceAddressForUsage_forNavigationUsage() {
        mCarAudioService.init();

        String mediaDeviceAddress =
                mCarAudioService.getOutputDeviceAddressForUsage(PRIMARY_AUDIO_ZONE,
                        USAGE_ASSISTANCE_NAVIGATION_GUIDANCE);

        expectWithMessage("Navigation usage audio device address")
                .that(mediaDeviceAddress).isEqualTo(NAVIGATION_TEST_DEVICE);
    }

    @Test
    public void getOutputDeviceAddressForUsage_forInvalidUsage_fails() {
        mCarAudioService.init();

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () ->
                mCarAudioService.getOutputDeviceAddressForUsage(PRIMARY_AUDIO_ZONE,
                        INVALID_USAGE));

        expectWithMessage("Invalid usage audio device address exception")
                .that(thrown).hasMessageThat().contains("Invalid audio attribute " + INVALID_USAGE);
    }

    @Test
    public void getOutputDeviceAddressForUsage_forVirtualUsage_fails() {
        mCarAudioService.init();

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () ->
                mCarAudioService.getOutputDeviceAddressForUsage(PRIMARY_AUDIO_ZONE,
                        AudioManagerHelper.getUsageVirtualSource()));

        expectWithMessage("Invalid context audio device address exception")
                .that(thrown).hasMessageThat()
                .contains("invalid");
    }

    @Test
    public void getOutputDeviceAddressForUsage_onSecondaryZone_forMusicUsage() {
        mCarAudioService.init();

        String mediaDeviceAddress =
                mCarAudioService.getOutputDeviceAddressForUsage(SECONDARY_ZONE_ID, USAGE_MEDIA);

        expectWithMessage("Media usage audio device address for secondary zone")
                .that(mediaDeviceAddress).isEqualTo(SECONDARY_TEST_DEVICE);
    }

    @Test
    public void getSuggestedAudioContextForPrimaryZone() {
        mCarAudioService.init();
        int defaultAudioContext = mCarAudioService.getCarAudioContext()
                .getContextForAudioAttribute(CAR_DEFAULT_AUDIO_ATTRIBUTE);

        expectWithMessage("Suggested audio context for primary zone")
                .that(mCarAudioService.getSuggestedAudioContextForPrimaryZone())
                .isEqualTo(defaultAudioContext);
    }

    @Test
    public void isVolumeGroupMuted_noSetVolumeGroupMute() {
        mCarAudioService.init();

        expectWithMessage("Volume group mute for default state")
                .that(mCarAudioService.isVolumeGroupMuted(PRIMARY_AUDIO_ZONE, TEST_PRIMARY_GROUP))
                .isFalse();
    }

    @Test
    public void isVolumeGroupMuted_setVolumeGroupMuted_isFalse() {
        mCarAudioService.init();
        mCarAudioService.setVolumeGroupMute(PRIMARY_AUDIO_ZONE, TEST_PRIMARY_GROUP,
                /* mute= */ true, TEST_FLAGS);

        mCarAudioService.setVolumeGroupMute(PRIMARY_AUDIO_ZONE, TEST_PRIMARY_GROUP,
                /* mute= */ false, TEST_FLAGS);

        expectWithMessage("Volume group muted after mute and unmute")
                .that(mCarAudioService.isVolumeGroupMuted(PRIMARY_AUDIO_ZONE, TEST_PRIMARY_GROUP))
                .isFalse();
    }

    @Test
    public void isVolumeGroupMuted_setVolumeGroupMuted_isTrue() {
        mCarAudioService.init();

        mCarAudioService.setVolumeGroupMute(PRIMARY_AUDIO_ZONE, TEST_PRIMARY_GROUP,
                /* mute= */ true, TEST_FLAGS);
        expectWithMessage("Volume group muted after mute")
                .that(mCarAudioService.isVolumeGroupMuted(PRIMARY_AUDIO_ZONE, TEST_PRIMARY_GROUP))
                .isTrue();
    }

    @Test
    public void isVolumeGroupMuted_withVolumeGroupMutingDisabled() {
        when(mMockResources.getBoolean(audioUseCarVolumeGroupMuting))
                .thenReturn(false);
        CarAudioService nonVolumeGroupMutingAudioService = new CarAudioService(mMockContext,
                mTemporaryAudioConfigurationFile.getFile().getAbsolutePath(),
                mCarVolumeCallbackHandler);
        nonVolumeGroupMutingAudioService.init();

        expectWithMessage("Volume group for disabled volume group muting")
                .that(nonVolumeGroupMutingAudioService.isVolumeGroupMuted(
                        PRIMARY_AUDIO_ZONE, TEST_PRIMARY_GROUP))
                .isFalse();
    }

    @Test
    public void getGroupMaxVolume_forPrimaryZone() {
        mCarAudioService.init();

        expectWithMessage("Group max volume for primary audio zone and group")
                .that(mCarAudioService.getGroupMaxVolume(PRIMARY_AUDIO_ZONE, TEST_PRIMARY_GROUP))
                .isEqualTo((MAX_GAIN - MIN_GAIN) / STEP_SIZE);
    }

    @Test
    public void getGroupMinVolume_forPrimaryZone() {
        mCarAudioService.init();

        expectWithMessage("Group Min Volume for primary audio zone and group")
                .that(mCarAudioService.getGroupMinVolume(PRIMARY_AUDIO_ZONE, TEST_PRIMARY_GROUP))
                .isEqualTo(0);
    }

    @Test
    public void getGroupCurrentVolume_forPrimaryZone() {
        mCarAudioService.init();

        expectWithMessage("Current group volume for primary audio zone and group")
                .that(mCarAudioService.getGroupVolume(PRIMARY_AUDIO_ZONE, TEST_PRIMARY_GROUP))
                .isEqualTo((DEFAULT_GAIN - MIN_GAIN) / STEP_SIZE);
    }

    @Test
    public void getGroupMaxVolume_withNoDynamicRouting() {
        when(mMockResources.getBoolean(audioUseDynamicRouting))
                .thenReturn(/* useDynamicRouting= */ false);
        CarAudioService nonDynamicAudioService = new CarAudioService(mMockContext,
                mTemporaryAudioConfigurationFile.getFile().getAbsolutePath(),
                mCarVolumeCallbackHandler);
        nonDynamicAudioService.init();

        nonDynamicAudioService.getGroupMaxVolume(PRIMARY_AUDIO_ZONE, TEST_PRIMARY_GROUP);

        verify(mAudioManager).getStreamMaxVolume(
                CarAudioDynamicRouting.STREAM_TYPES[TEST_PRIMARY_GROUP]);
    }

    @Test
    public void getGroupMinVolume_withNoDynamicRouting() {
        when(mMockResources.getBoolean(audioUseDynamicRouting))
                .thenReturn(/* useDynamicRouting= */ false);
        CarAudioService nonDynamicAudioService = new CarAudioService(mMockContext,
                mTemporaryAudioConfigurationFile.getFile().getAbsolutePath(),
                mCarVolumeCallbackHandler);
        nonDynamicAudioService.init();

        nonDynamicAudioService.getGroupMinVolume(PRIMARY_AUDIO_ZONE, TEST_PRIMARY_GROUP);

        verify(mAudioManager).getStreamMinVolume(
                CarAudioDynamicRouting.STREAM_TYPES[TEST_PRIMARY_GROUP]);
    }

    @Test
    public void getGroupCurrentVolume_withNoDynamicRouting() {
        when(mMockResources.getBoolean(audioUseDynamicRouting))
                .thenReturn(/* useDynamicRouting= */ false);
        CarAudioService nonDynamicAudioService = new CarAudioService(mMockContext,
                mTemporaryAudioConfigurationFile.getFile().getAbsolutePath(),
                mCarVolumeCallbackHandler);
        nonDynamicAudioService.init();

        nonDynamicAudioService.getGroupVolume(PRIMARY_AUDIO_ZONE, TEST_PRIMARY_GROUP);

        verify(mAudioManager).getStreamVolume(
                CarAudioDynamicRouting.STREAM_TYPES[TEST_PRIMARY_GROUP]);
    }

    @Test
    public void setBalanceTowardRight_nonNullValue() {
        mCarAudioService.init();

        mCarAudioService.setBalanceTowardRight(TEST_VALUE);

        verify(mAudioControlWrapperAidl).setBalanceTowardRight(TEST_VALUE);
    }

    @Test
    public void setBalanceTowardRight_throws() {
        mCarAudioService.init();

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, ()
                -> mCarAudioService.setBalanceTowardRight(INVALID_TEST_VALUE));

        expectWithMessage("Out of bounds balance")
                .that(thrown).hasMessageThat()
                .contains(String.format("Balance is out of range of [%f, %f]", -1f, 1f));
    }

    @Test
    public void setFadeTowardFront_nonNullValue() {
        mCarAudioService.init();

        mCarAudioService.setFadeTowardFront(TEST_VALUE);

        verify(mAudioControlWrapperAidl).setFadeTowardFront(TEST_VALUE);
    }

    @Test
    public void setFadeTowardFront_throws() {
        mCarAudioService.init();

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, ()
                -> mCarAudioService.setFadeTowardFront(INVALID_TEST_VALUE));

        expectWithMessage("Out of bounds fade")
                .that(thrown).hasMessageThat()
                .contains(String.format("Fade is out of range of [%f, %f]", -1f, 1f));
    }

    @Test
    public void isAudioFeatureEnabled_forDynamicRouting() {
        mCarAudioService.init();

        expectWithMessage("Dynamic routing audio feature")
                .that(mCarAudioService.isAudioFeatureEnabled(AUDIO_FEATURE_DYNAMIC_ROUTING))
                .isEqualTo(mUseDynamicRouting);
    }

    @Test
    public void isAudioFeatureEnabled_forDisabledDynamicRouting() {
        when(mMockResources.getBoolean(audioUseDynamicRouting))
                .thenReturn(/* useDynamicRouting= */ false);
        CarAudioService nonDynamicAudioService = new CarAudioService(mMockContext,
                mTemporaryAudioConfigurationFile.getFile().getAbsolutePath(),
                mCarVolumeCallbackHandler);
        nonDynamicAudioService.init();

        expectWithMessage("Disabled dynamic routing audio feature")
                .that(nonDynamicAudioService.isAudioFeatureEnabled(AUDIO_FEATURE_DYNAMIC_ROUTING))
                .isFalse();
    }

    @Test
    public void isAudioFeatureEnabled_forVolumeGroupMuting() {
        mCarAudioService.init();

        expectWithMessage("Group muting audio feature")
                .that(mCarAudioService.isAudioFeatureEnabled(AUDIO_FEATURE_VOLUME_GROUP_MUTING))
                .isEqualTo(mUseCarVolumeGroupMuting);
    }

    @Test
    public void isAudioFeatureEnabled_forDisabledVolumeGroupMuting() {
        when(mMockResources.getBoolean(audioUseCarVolumeGroupMuting)).thenReturn(false);
        CarAudioService nonVolumeGroupMutingAudioService = new CarAudioService(mMockContext,
                mTemporaryAudioConfigurationFile.getFile().getAbsolutePath(),
                mCarVolumeCallbackHandler);
        nonVolumeGroupMutingAudioService.init();

        expectWithMessage("Disabled group muting audio feature")
                .that(nonVolumeGroupMutingAudioService
                        .isAudioFeatureEnabled(AUDIO_FEATURE_VOLUME_GROUP_MUTING))
                .isFalse();
    }

    @Test
    public void isAudioFeatureEnabled_forUnrecognizableAudioFeature_throws() {
        mCarAudioService.init();

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> mCarAudioService.isAudioFeatureEnabled(INVALID_AUDIO_FEATURE));

        expectWithMessage("Unknown audio feature")
                .that(thrown).hasMessageThat()
                .contains("Unknown Audio Feature type: " + INVALID_AUDIO_FEATURE);
    }

    @Test
    public void onOccupantZoneConfigChanged_noUserAssignedToPrimaryZone() throws Exception {
        mCarAudioService.init();
        when(mMockOccupantZoneService.getDriverUserId()).thenReturn(UserManagerHelper.USER_NULL);
        when(mMockOccupantZoneService.getUserForOccupant(anyInt()))
                .thenReturn(UserManagerHelper.USER_NULL);
        ICarOccupantZoneCallback callback = getOccupantZoneCallback();
        int prevUserId = mCarAudioService.getUserIdForZone(PRIMARY_AUDIO_ZONE);

        callback.onOccupantZoneConfigChanged(CarOccupantZoneManager.ZONE_CONFIG_CHANGE_FLAG_USER);

        expectWithMessage("User ID before config changed")
                .that(mCarAudioService.getUserIdForZone(PRIMARY_AUDIO_ZONE))
                .isEqualTo(prevUserId);
    }

    @Test
    public void onOccupantZoneConfigChanged_userAssignedToPrimaryZone() throws Exception {
        mCarAudioService.init();
        when(mMockOccupantZoneService.getDriverUserId()).thenReturn(TEST_DRIVER_USER_ID);
        when(mMockOccupantZoneService.getUserForOccupant(anyInt()))
                .thenReturn(TEST_USER_ID);
        ICarOccupantZoneCallback callback = getOccupantZoneCallback();

        callback.onOccupantZoneConfigChanged(CarOccupantZoneManager.ZONE_CONFIG_CHANGE_FLAG_USER);

        expectWithMessage("User ID after config changed")
                .that(mCarAudioService.getUserIdForZone(PRIMARY_AUDIO_ZONE))
                .isEqualTo(TEST_USER_ID);
    }

    @Test
    public void onOccupantZoneConfigChanged_afterResettingUser_returnNoUser() throws Exception {
        mCarAudioService.init();
        when(mMockOccupantZoneService.getDriverUserId()).thenReturn(TEST_DRIVER_USER_ID);
        when(mMockOccupantZoneService.getUserForOccupant(anyInt()))
                .thenReturn(TEST_USER_ID);
        ICarOccupantZoneCallback callback = getOccupantZoneCallback();
        callback.onOccupantZoneConfigChanged(CarOccupantZoneManager.ZONE_CONFIG_CHANGE_FLAG_USER);
        when(mMockOccupantZoneService.getUserForOccupant(anyInt()))
                .thenReturn(UserManagerHelper.USER_NULL);

        callback.onOccupantZoneConfigChanged(CarOccupantZoneManager.ZONE_CONFIG_CHANGE_FLAG_USER);

        expectWithMessage("User ID config changed to null")
                .that(mCarAudioService.getUserIdForZone(PRIMARY_AUDIO_ZONE))
                .isEqualTo(UserManagerHelper.USER_NULL);
    }

    @Test
    public void onOccupantZoneConfigChanged_noOccupantZoneMapping() throws Exception {
        CarAudioService noZoneMappingAudioService = new CarAudioService(mMockContext,
                mTemporaryAudioConfigurationWithoutZoneMappingFile.getFile().getAbsolutePath(),
                mCarVolumeCallbackHandler);
        noZoneMappingAudioService.init();
        ICarOccupantZoneCallback callback = getOccupantZoneCallback();

        callback.onOccupantZoneConfigChanged(CarOccupantZoneManager.ZONE_CONFIG_CHANGE_FLAG_USER);

        verify(mMockOccupantZoneService, never()).getUserForOccupant(anyInt());
    }

    @Test
    public void onOccupantZoneConfigChanged_noOccupantZoneMapping_alreadyAssigned()
            throws Exception {
        CarAudioService noZoneMappingAudioService = new CarAudioService(mMockContext,
                mTemporaryAudioConfigurationWithoutZoneMappingFile.getFile().getAbsolutePath(),
                mCarVolumeCallbackHandler);
        when(mMockOccupantZoneService.getDriverUserId()).thenReturn(TEST_DRIVER_USER_ID);
        noZoneMappingAudioService.init();
        ICarOccupantZoneCallback callback = getOccupantZoneCallback();

        callback.onOccupantZoneConfigChanged(CarOccupantZoneManager.ZONE_CONFIG_CHANGE_FLAG_USER);
        callback.onOccupantZoneConfigChanged(CarOccupantZoneManager.ZONE_CONFIG_CHANGE_FLAG_USER);

        verify(mMockOccupantZoneService, never()).getUserForOccupant(anyInt());
        expectWithMessage("Occupant Zone for primary zone")
                .that(noZoneMappingAudioService.getUserIdForZone(PRIMARY_AUDIO_ZONE))
                .isEqualTo(TEST_DRIVER_USER_ID);
    }

    @Test
    public void onOccupantZoneConfigChanged_multipleZones() throws Exception {
        mCarAudioService.init();
        when(mMockOccupantZoneService.getDriverUserId()).thenReturn(TEST_DRIVER_USER_ID);
        when(mMockOccupantZoneService.getUserForOccupant(anyInt()))
                .thenReturn(TEST_USER_ID, TEST_USER_ID_SECONDARY);
        ICarOccupantZoneCallback callback = getOccupantZoneCallback();

        callback.onOccupantZoneConfigChanged(CarOccupantZoneManager.ZONE_CONFIG_CHANGE_FLAG_USER);

        expectWithMessage("User ID for primary and secondary zone after config changed")
                .that(mCarAudioService.getUserIdForZone(PRIMARY_AUDIO_ZONE))
                .isNotEqualTo(mCarAudioService.getUserIdForZone(SECONDARY_ZONE_ID));
        expectWithMessage("Secondary user ID config changed")
                .that(mCarAudioService.getUserIdForZone(SECONDARY_ZONE_ID))
                .isEqualTo(TEST_USER_ID_SECONDARY);
    }

    @Test
    public void serviceDied_registersAudioGainCallback() {
        mCarAudioService.init();
        ArgumentCaptor<AudioControlDeathRecipient> captor =
                ArgumentCaptor.forClass(AudioControlDeathRecipient.class);
        verify(mAudioControlWrapperAidl).linkToDeath(captor.capture());
        AudioControlDeathRecipient runnable = captor.getValue();
        reset(mAudioControlWrapperAidl);

        runnable.serviceDied();

        verify(mAudioControlWrapperAidl).registerAudioGainCallback(any());
    }

    @Test
    public void serviceDied_registersFocusListener() {
        mCarAudioService.init();
        ArgumentCaptor<AudioControlDeathRecipient> captor =
                ArgumentCaptor.forClass(AudioControlDeathRecipient.class);
        verify(mAudioControlWrapperAidl).linkToDeath(captor.capture());
        AudioControlDeathRecipient runnable = captor.getValue();
        reset(mAudioControlWrapperAidl);

        runnable.serviceDied();

        verify(mAudioControlWrapperAidl).registerFocusListener(any());
    }

    private ICarOccupantZoneCallback getOccupantZoneCallback() {
        ArgumentCaptor<ICarOccupantZoneCallback> captor =
                ArgumentCaptor.forClass(ICarOccupantZoneCallback.class);
        verify(mMockOccupantZoneService).registerCallback(captor.capture());
        return captor.getValue();
    }

    @Test
    public void getVolumeGroupIdForAudioContext_forPrimaryGroup() {
        mCarAudioService.init();

        expectWithMessage("Volume group ID for primary audio zone")
                .that(mCarAudioService.getVolumeGroupIdForAudioContext(PRIMARY_AUDIO_ZONE,
                        CarAudioContext.MUSIC))
                .isEqualTo(TEST_PRIMARY_GROUP_INDEX);
    }

    @Test
    public void getInputDevicesForZoneId_primaryZone() {
        mCarAudioService.init();

        expectWithMessage("Get input device for primary zone id")
                .that(mCarAudioService.getInputDevicesForZoneId(PRIMARY_AUDIO_ZONE))
                .containsExactly(new AudioDeviceAttributes(mMicrophoneInputDevice));
    }

    @Test
    public void getExternalSources_forSingleDevice() {
        mCarAudioService.init();
        AudioDeviceInfo[] inputDevices = generateInputDeviceInfos();

        expectWithMessage("External input device addresses")
                .that(mCarAudioService.getExternalSources())
                .asList().containsExactly(inputDevices[1].getAddress());
    }

    @Test
    public void setAudioEnabled_forEnabledVolumeGroupMuting() {
        mCarAudioService.init();

        mCarAudioService.setAudioEnabled(/* enabled= */ true);

        verify(mAudioControlWrapperAidl).onDevicesToMuteChange(any());
    }

    @Test
    public void setAudioEnabled_forDisabledVolumeGroupMuting() {
        when(mMockResources.getBoolean(audioUseCarVolumeGroupMuting)).thenReturn(false);
        CarAudioService nonVolumeGroupMutingAudioService = new CarAudioService(mMockContext,
                mTemporaryAudioConfigurationFile.getFile().getAbsolutePath(),
                mCarVolumeCallbackHandler);
        nonVolumeGroupMutingAudioService.init();

        nonVolumeGroupMutingAudioService.setAudioEnabled(/* enabled= */ true);

        verify(mAudioControlWrapperAidl, never()).onDevicesToMuteChange(any());
    }

    @Test
    public void registerVolumeCallback_verifyCallbackHandler() {
        mCarAudioService.init();

        mCarAudioService.registerVolumeCallback(mVolumeCallbackBinder);

        verify(mCarVolumeCallbackHandler).registerCallback(mVolumeCallbackBinder);
    }

    @Test
    public void unregisterVolumeCallback_verifyCallbackHandler() {
        mCarAudioService.init();

        mCarAudioService.unregisterVolumeCallback(mVolumeCallbackBinder);

        verify(mCarVolumeCallbackHandler).unregisterCallback(mVolumeCallbackBinder);
    }

    @Test
    public void getMutedVolumeGroups_forInvalidZone() {
        mCarAudioService.init();

        expectWithMessage("Muted volume groups for invalid zone")
                .that(mCarAudioService.getMutedVolumeGroups(INVALID_AUDIO_ZONE))
                .isEmpty();
    }

    @Test
    public void getMutedVolumeGroups_whenVolumeGroupMuteNotSupported() {
        when(mMockResources.getBoolean(audioUseCarVolumeGroupMuting)).thenReturn(false);
        CarAudioService nonVolumeGroupMutingAudioService = new CarAudioService(mMockContext,
                mTemporaryAudioConfigurationFile.getFile().getAbsolutePath(),
                mCarVolumeCallbackHandler);
        nonVolumeGroupMutingAudioService.init();

        expectWithMessage("Muted volume groups with disable mute feature")
                .that(nonVolumeGroupMutingAudioService.getMutedVolumeGroups(PRIMARY_AUDIO_ZONE))
                .isEmpty();
    }

    @Test
    public void getMutedVolumeGroups_withMutedGroups() {
        mCarAudioService.init();
        mCarAudioService.setVolumeGroupMute(PRIMARY_AUDIO_ZONE, TEST_PRIMARY_GROUP,
                /* muted= */ true, TEST_FLAGS);
        mCarAudioService.setVolumeGroupMute(PRIMARY_AUDIO_ZONE, TEST_SECONDARY_GROUP,
                /* muted= */ true, TEST_FLAGS);

        expectWithMessage("Muted volume groups")
                .that(mCarAudioService.getMutedVolumeGroups(PRIMARY_AUDIO_ZONE))
                .containsExactly(TEST_PRIMARY_VOLUME_INFO, TEST_SECONDARY_VOLUME_INFO);
    }

    @Test
    public void getMutedVolumeGroups_afterUnmuting() {
        mCarAudioService.init();
        mCarAudioService.setVolumeGroupMute(PRIMARY_AUDIO_ZONE, TEST_PRIMARY_GROUP,
                /* muted= */ true, TEST_FLAGS);
        mCarAudioService.setVolumeGroupMute(PRIMARY_AUDIO_ZONE, TEST_SECONDARY_GROUP,
                /* muted= */ true, TEST_FLAGS);
        mCarAudioService.setVolumeGroupMute(PRIMARY_AUDIO_ZONE, TEST_PRIMARY_GROUP,
                /* muted= */ false, TEST_FLAGS);

        expectWithMessage("Muted volume groups after unmuting one group")
                .that(mCarAudioService.getMutedVolumeGroups(PRIMARY_AUDIO_ZONE))
                .containsExactly(TEST_SECONDARY_VOLUME_INFO);
    }

    @Test
    public void getMutedVolumeGroups_withMutedGroupsForDifferentZone() {
        mCarAudioService.init();
        mCarAudioService.setVolumeGroupMute(PRIMARY_AUDIO_ZONE, TEST_PRIMARY_GROUP,
                /* muted= */ true, TEST_FLAGS);
        mCarAudioService.setVolumeGroupMute(PRIMARY_AUDIO_ZONE, TEST_SECONDARY_GROUP,
                /* muted= */ true, TEST_FLAGS);

        expectWithMessage("Muted volume groups for secondary zone")
                .that(mCarAudioService.getMutedVolumeGroups(SECONDARY_ZONE_ID)).isEmpty();
    }

    @Test
    public void onReceive_forLegacy_noCallToOnVolumeGroupChanged() {
        when(mMockResources.getBoolean(audioUseDynamicRouting))
                .thenReturn(false);
        CarAudioService nonDynamicAudioService = new CarAudioService(mMockContext,
                mTemporaryAudioConfigurationFile.getFile().getAbsolutePath(),
                mCarVolumeCallbackHandler);
        nonDynamicAudioService.init();
        mVolumeReceiverCaptor = ArgumentCaptor.forClass(BroadcastReceiver.class);
        verify(mMockContext).registerReceiver(mVolumeReceiverCaptor.capture(), any(), anyInt());
        BroadcastReceiver receiver = mVolumeReceiverCaptor.getValue();
        Intent intent = new Intent(VOLUME_CHANGED_ACTION);

        receiver.onReceive(mMockContext, intent);

        verify(mCarVolumeCallbackHandler, never())
                .onVolumeGroupChange(anyInt(), anyInt(), anyInt());
    }

    @Test
    public void onReceive_forLegacy_forStreamMusic() {
        when(mMockResources.getBoolean(audioUseDynamicRouting))
                .thenReturn(false);
        CarAudioService nonDynamicAudioService = new CarAudioService(mMockContext,
                mTemporaryAudioConfigurationFile.getFile().getAbsolutePath(),
                mCarVolumeCallbackHandler);
        nonDynamicAudioService.init();
        verify(mMockContext).registerReceiver(mVolumeReceiverCaptor.capture(), any(), anyInt());
        BroadcastReceiver receiver = mVolumeReceiverCaptor.getValue();
        Intent intent = new Intent(VOLUME_CHANGED_ACTION)
                .putExtra(EXTRA_VOLUME_STREAM_TYPE, STREAM_MUSIC);

        receiver.onReceive(mMockContext, intent);

        verify(mCarVolumeCallbackHandler).onVolumeGroupChange(
                eq(PRIMARY_AUDIO_ZONE), anyInt(), eq(FLAG_FROM_KEY | FLAG_SHOW_UI));
    }

    @Test
    public void onReceive_forLegacy_onMuteChanged() {
        when(mMockResources.getBoolean(audioUseDynamicRouting))
                .thenReturn(false);
        CarAudioService nonDynamicAudioService = new CarAudioService(mMockContext,
                mTemporaryAudioConfigurationFile.getFile().getAbsolutePath(),
                mCarVolumeCallbackHandler);
        nonDynamicAudioService.init();
        ArgumentCaptor<BroadcastReceiver> captor =
                ArgumentCaptor.forClass(BroadcastReceiver.class);
        verify(mMockContext).registerReceiver(captor.capture(), any(), anyInt());
        BroadcastReceiver receiver = captor.getValue();
        Intent intent = new Intent();
        intent.setAction(MASTER_MUTE_CHANGED_ACTION);

        receiver.onReceive(mMockContext, intent);

        verify(mCarVolumeCallbackHandler)
                .onMasterMuteChanged(eq(PRIMARY_AUDIO_ZONE), eq(FLAG_FROM_KEY | FLAG_SHOW_UI));
    }

    @Test
    public void getVolumeGroupInfosForZone() {
        mCarAudioService.init();
        int groupCount = mCarAudioService.getVolumeGroupCount(PRIMARY_AUDIO_ZONE);

        CarVolumeGroupInfo[] infos =
                mCarAudioService.getVolumeGroupInfosForZone(PRIMARY_AUDIO_ZONE);

        for (int index = 0; index < groupCount; index++) {
            CarVolumeGroupInfo info = mCarAudioService
                    .getVolumeGroupInfo(PRIMARY_AUDIO_ZONE, index);
            expectWithMessage("Car volume group infos for primary zone and info %s", info)
                    .that(infos).asList().contains(info);
        }
    }

    @Test
    public void getVolumeGroupInfosForZone_forDynamicRoutingDisabled() {
        when(mMockResources.getBoolean(audioUseDynamicRouting))
                .thenReturn(/* useDynamicRouting= */ false);
        CarAudioService nonDynamicAudioService = new CarAudioService(mMockContext,
                mTemporaryAudioConfigurationFile.getFile().getAbsolutePath(),
                mCarVolumeCallbackHandler);
        nonDynamicAudioService.init();

        CarVolumeGroupInfo[] infos =
                nonDynamicAudioService.getVolumeGroupInfosForZone(PRIMARY_AUDIO_ZONE);

        expectWithMessage("Car volume group infos with dynamic routing disabled")
                .that(infos).isEmpty();
    }

    @Test
    public void getVolumeGroupInfosForZone_size() {
        mCarAudioService.init();
        int groupCount = mCarAudioService.getVolumeGroupCount(PRIMARY_AUDIO_ZONE);

        CarVolumeGroupInfo[] infos =
                mCarAudioService.getVolumeGroupInfosForZone(PRIMARY_AUDIO_ZONE);

        expectWithMessage("Car volume group infos size for primary zone")
                .that(infos).hasLength(groupCount);
    }

    @Test
    public void getVolumeGroupInfosForZone_forInvalidZone() {
        mCarAudioService.init();

        IllegalArgumentException thrown =
                assertThrows(IllegalArgumentException.class, () ->
                        mCarAudioService.getVolumeGroupInfosForZone(INVALID_AUDIO_ZONE));

        expectWithMessage("Exception for volume group infos size for invalid zone")
                .that(thrown).hasMessageThat().contains("audio zone Id");
    }

    @Test
    public void getVolumeGroupInfo() {
        CarVolumeGroupInfo testVolumeGroupInfo =
                new CarVolumeGroupInfo.Builder(TEST_PRIMARY_VOLUME_INFO).setMuted(false).build();
        mCarAudioService.init();

        expectWithMessage("Car volume group info for primary zone")
                .that(mCarAudioService.getVolumeGroupInfo(PRIMARY_AUDIO_ZONE, TEST_PRIMARY_GROUP))
                .isEqualTo(testVolumeGroupInfo);
    }

    @Test
    public void getVolumeGroupInfo_forInvalidZone() {
        mCarAudioService.init();

        IllegalArgumentException thrown =
                assertThrows(IllegalArgumentException.class, () ->
                        mCarAudioService.getVolumeGroupInfo(INVALID_AUDIO_ZONE,
                                TEST_PRIMARY_GROUP));

        expectWithMessage("Exception for volume group info size for invalid zone")
                .that(thrown).hasMessageThat().contains("audio zone Id");
    }

    @Test
    public void getVolumeGroupInfo_forInvalidGroup() {
        mCarAudioService.init();

        IllegalArgumentException thrown =
                assertThrows(IllegalArgumentException.class, () ->
                        mCarAudioService.getVolumeGroupInfo(INVALID_AUDIO_ZONE,
                                TEST_PRIMARY_GROUP));

        expectWithMessage("Exception for volume groups info size for invalid group id")
                .that(thrown).hasMessageThat().contains("audio zone Id");
    }

    @Test
    public void getVolumeGroupInfo_forGroupOverRange() {
        mCarAudioService.init();
        int groupCount = mCarAudioService.getVolumeGroupCount(PRIMARY_AUDIO_ZONE);

        IllegalArgumentException thrown =
                assertThrows(IllegalArgumentException.class, () ->
                        mCarAudioService.getVolumeGroupInfo(INVALID_AUDIO_ZONE,
                                groupCount));

        expectWithMessage("Exception for volume groups info size for out of range group")
                .that(thrown).hasMessageThat().contains("audio zone Id");
    }

    private void mockGrantCarControlAudioSettingsPermission() {
        mockContextCheckCallingOrSelfPermission(mMockContext,
                PERMISSION_CAR_CONTROL_AUDIO_SETTINGS, PERMISSION_GRANTED);
    }

    private void mockDenyCarControlAudioSettingsPermission() {
        mockContextCheckCallingOrSelfPermission(mMockContext,
                PERMISSION_CAR_CONTROL_AUDIO_SETTINGS, PERMISSION_DENIED);
    }

    private void mockDenyCarControlAudioVolumePermission() {
        mockContextCheckCallingOrSelfPermission(mMockContext,
                PERMISSION_CAR_CONTROL_AUDIO_VOLUME, PERMISSION_DENIED);
    }

    private AudioDeviceInfo[] generateInputDeviceInfos() {
        mMicrophoneInputDevice = new AudioDeviceInfoBuilder()
                .setAddressName(PRIMARY_ZONE_MICROPHONE_ADDRESS)
                .setType(TYPE_BUILTIN_MIC)
                .setIsSource(true)
                .build();
        mFmTunerInputDevice = new AudioDeviceInfoBuilder()
                .setAddressName(PRIMARY_ZONE_FM_TUNER_ADDRESS)
                .setType(TYPE_FM_TUNER)
                .setIsSource(true)
                .build();
        return new AudioDeviceInfo[]{mMicrophoneInputDevice, mFmTunerInputDevice};
    }

    private AudioDeviceInfo[] generateOutputDeviceInfos() {
        mMediaOutputDevice = new AudioDeviceInfoBuilder()
                .setAudioGains(new AudioGain[] {new GainBuilder().build()})
                .setAddressName(MEDIA_TEST_DEVICE)
                .build();
        return new AudioDeviceInfo[] {
                mMediaOutputDevice,
                new AudioDeviceInfoBuilder()
                        .setAudioGains(new AudioGain[] {new GainBuilder().build()})
                        .setAddressName(NAVIGATION_TEST_DEVICE)
                        .build(),
                new AudioDeviceInfoBuilder()
                        .setAudioGains(new AudioGain[] {new GainBuilder().build()})
                        .setAddressName(CALL_TEST_DEVICE)
                        .build(),
                new AudioDeviceInfoBuilder()
                        .setAudioGains(new AudioGain[] {new GainBuilder().build()})
                        .setAddressName(SYSTEM_BUS_DEVICE)
                        .build(),
                new AudioDeviceInfoBuilder()
                        .setAudioGains(new AudioGain[] {new GainBuilder().build()})
                        .setAddressName(NOTIFICATION_TEST_DEVICE)
                        .build(),
                new AudioDeviceInfoBuilder()
                        .setAudioGains(new AudioGain[] {new GainBuilder().build()})
                        .setAddressName(VOICE_TEST_DEVICE)
                        .build(),
                new AudioDeviceInfoBuilder()
                        .setAudioGains(new AudioGain[] {new GainBuilder().build()})
                        .setAddressName(RING_TEST_DEVICE)
                        .build(),
                new AudioDeviceInfoBuilder()
                        .setAudioGains(new AudioGain[] {new GainBuilder().build()})
                        .setAddressName(ALARM_TEST_DEVICE)
                        .build(),
                new AudioDeviceInfoBuilder()
                        .setAudioGains(new AudioGain[] {new GainBuilder().build()})
                        .setAddressName(SECONDARY_TEST_DEVICE)
                        .build(),
        };
    }

    private static AudioFocusInfo createAudioFocusInfoForMedia() {
        AudioAttributes.Builder builder = new AudioAttributes.Builder();
        builder.setUsage(USAGE_MEDIA);

        return new AudioFocusInfo(builder.build(), MEDIA_APP_UID, MEDIA_CLIENT_ID,
                MEDIA_PACKAGE_NAME, AUDIOFOCUS_GAIN, AUDIOFOCUS_LOSS, MEDIA_EMPTY_FLAG, SDK_INT);
    }
}
