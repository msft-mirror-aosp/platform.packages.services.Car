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

package com.android.car.telemetry.publisher;

import android.app.StatsManager.StatsUnavailableException;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.LongSparseArray;
import android.util.Slog;

import com.android.car.CarLog;
import com.android.car.telemetry.StatsLogProto;
import com.android.car.telemetry.StatsdConfigProto;
import com.android.car.telemetry.StatsdConfigProto.StatsdConfig;
import com.android.car.telemetry.TelemetryProto;
import com.android.car.telemetry.TelemetryProto.Publisher.PublisherCase;
import com.android.car.telemetry.databroker.DataSubscriber;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.Preconditions;

import com.google.protobuf.InvalidProtocolBufferException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Publisher for {@link TelemetryProto.StatsPublisher}.
 *
 * <p>The publisher adds subscriber configurations in StatsD and they persist between reboots and
 * CarTelemetryService restarts. Please use {@link #removeAllDataSubscribers} or
 * {@link #removeDataSubscriber} to clean-up these configs from StatsD store.
 *
 * <p>Thread-safe.
 */
public class StatsPublisher extends AbstractPublisher {
    // These IDs are used in StatsdConfig and ConfigMetricsReport.
    @VisibleForTesting
    static final int APP_START_MEMORY_STATE_CAPTURED_ATOM_MATCHER_ID = 1;
    @VisibleForTesting
    static final int APP_START_MEMORY_STATE_CAPTURED_EVENT_METRIC_ID = 2;

    // The atom ids are from frameworks/proto_logging/stats/atoms.proto
    @VisibleForTesting
    static final int ATOM_APP_START_MEMORY_STATE_CAPTURED_ID = 55;

    private static final Duration PULL_REPORTS_PERIOD = Duration.ofMinutes(10);

    private static final String SHARED_PREF_CONFIG_KEY_PREFIX = "statsd-publisher-config-id-";
    private static final String SHARED_PREF_CONFIG_VERSION_PREFIX =
            "statsd-publisher-config-version-";

    // TODO(b/197766340): remove unnecessary lock
    private final Object mLock = new Object();

    private final StatsManagerProxy mStatsManager;
    private final SharedPreferences mSharedPreferences;
    private final Handler mHandler;

    // True if the publisher is periodically pulling reports from StatsD.
    private final AtomicBoolean mIsPullingReports = new AtomicBoolean(false);

    /** Assign the method to {@link Runnable}, otherwise the handler fails to remove it. */
    private final Runnable mPullReportsPeriodically = this::pullReportsPeriodically;

    // LongSparseArray is memory optimized, but they can be bit slower for more
    // than 100 items. We're expecting much less number of subscribers, so these data structures
    // are ok.
    // Maps config_key to the set of DataSubscriber.
    @GuardedBy("mLock")
    private final LongSparseArray<DataSubscriber> mConfigKeyToSubscribers = new LongSparseArray<>();

    StatsPublisher(StatsManagerProxy statsManager, SharedPreferences sharedPreferences) {
        this(statsManager, sharedPreferences, new Handler(Looper.myLooper()));
    }

    @VisibleForTesting
    StatsPublisher(
            StatsManagerProxy statsManager, SharedPreferences sharedPreferences, Handler handler) {
        mStatsManager = statsManager;
        mSharedPreferences = sharedPreferences;
        mHandler = handler;
    }

    @Override
    public void addDataSubscriber(DataSubscriber subscriber) {
        TelemetryProto.Publisher publisherParam = subscriber.getPublisherParam();
        Preconditions.checkArgument(
                publisherParam.getPublisherCase() == PublisherCase.STATS,
                "Subscribers only with StatsPublisher are supported by this class.");

        synchronized (mLock) {
            long configKey = addStatsConfigLocked(subscriber);
            mConfigKeyToSubscribers.put(configKey, subscriber);
        }

        if (!mIsPullingReports.getAndSet(true)) {
            mHandler.postDelayed(mPullReportsPeriodically, PULL_REPORTS_PERIOD.toMillis());
        }
    }

    private void pullReportsPeriodically() {
        for (long configKey : getActiveConfigKeys()) {
            try {
                StatsLogProto.ConfigMetricsReportList report =
                        StatsLogProto.ConfigMetricsReportList.parseFrom(
                                mStatsManager.getReports(configKey));
                // TODO(b/197269115): parse the report
                Slog.i(CarLog.TAG_TELEMETRY, "Received reports: " + report.getReportsCount());
                if (report.getReportsCount() > 0) {
                    Bundle data = new Bundle();
                    // TODO(b/197269115): parse the report
                    data.putInt("reportsCount", report.getReportsCount());
                    mConfigKeyToSubscribers.get(configKey).push(data);
                }
            } catch (StatsUnavailableException e) {
                // TODO(b/189143813): retry if stats is not available
            } catch (InvalidProtocolBufferException e) {
                // This case should never happen.
                Slog.w(CarLog.TAG_TELEMETRY,
                        "Failed to parse report from statsd, configKey=" + configKey);
            }
        }

        if (mIsPullingReports.get()) {
            mHandler.postDelayed(mPullReportsPeriodically, PULL_REPORTS_PERIOD.toMillis());
        }
    }

    private List<Long> getActiveConfigKeys() {
        ArrayList<Long> result = new ArrayList<>();
        synchronized (mLock) {
            mSharedPreferences.getAll().forEach((key, value) -> {
                if (!key.startsWith(SHARED_PREF_CONFIG_KEY_PREFIX)) {
                    return;
                }
                result.add((long) value);
            });
        }
        return result;
    }

    /**
     * Removes the subscriber from the publisher and removes StatsdConfig from StatsD service.
     * If StatsdConfig is present in Statsd, it removes it even if the subscriber is not present
     * in the publisher (it happens when subscriber was added before and CarTelemetryService was
     * restarted and lost publisher state).
     */
    @Override
    public void removeDataSubscriber(DataSubscriber subscriber) {
        TelemetryProto.Publisher publisherParam = subscriber.getPublisherParam();
        if (publisherParam.getPublisherCase() != PublisherCase.STATS) {
            Slog.w(CarLog.TAG_TELEMETRY,
                    "Expected STATS publisher, but received "
                            + publisherParam.getPublisherCase().name());
            return;
        }
        synchronized (mLock) {
            long configKey = removeStatsConfigLocked(subscriber);
            mConfigKeyToSubscribers.remove(configKey);
        }

        if (mConfigKeyToSubscribers.size() == 0) {
            mIsPullingReports.set(false);
            mHandler.removeCallbacks(mPullReportsPeriodically);
        }
    }

    /** Removes all the subscribers from the publisher removes StatsdConfigs from StatsD service. */
    @Override
    public void removeAllDataSubscribers() {
        synchronized (mLock) {
            SharedPreferences.Editor editor = mSharedPreferences.edit();
            mSharedPreferences.getAll().forEach((key, value) -> {
                if (!key.startsWith(SHARED_PREF_CONFIG_KEY_PREFIX)) {
                    return;
                }
                long configKey = (long) value;
                try {
                    mStatsManager.removeConfig(configKey);
                    String sharedPrefVersion = buildSharedPrefConfigVersionKey(configKey);
                    editor.remove(key).remove(sharedPrefVersion);
                } catch (StatsUnavailableException e) {
                    Slog.w(CarLog.TAG_TELEMETRY, "Failed to remove config " + configKey, e);
                    // TODO(b/189143813): if StatsManager is not ready, retry N times and hard fail
                    //                    after by notifying DataBroker.
                }
            });
            editor.apply();
            mConfigKeyToSubscribers.clear();
        }
        mIsPullingReports.set(false);
        mHandler.removeCallbacks(mPullReportsPeriodically);
    }

    @Override
    public boolean hasDataSubscriber(DataSubscriber subscriber) {
        TelemetryProto.Publisher publisherParam = subscriber.getPublisherParam();
        if (publisherParam.getPublisherCase() != PublisherCase.STATS) {
            return false;
        }
        long configKey = buildConfigKey(subscriber);
        synchronized (mLock) {
            return mConfigKeyToSubscribers.indexOfKey(configKey) >= 0;
        }
    }

    /**
     * Returns the key for SharedPreferences to store/retrieve configKey associated with the
     * subscriber.
     */
    private static String buildSharedPrefConfigKey(DataSubscriber subscriber) {
        return SHARED_PREF_CONFIG_KEY_PREFIX + subscriber.getMetricsConfig().getName() + "-"
                + subscriber.getSubscriber().getHandler();
    }

    /**
     * Returns the key for SharedPreferences to store/retrieve {@link TelemetryProto.MetricsConfig}
     * version associated with the configKey (which is generated per DataSubscriber).
     */
    private static String buildSharedPrefConfigVersionKey(long configKey) {
        return SHARED_PREF_CONFIG_VERSION_PREFIX + configKey;
    }

    /**
     * This method can be called even if StatsdConfig was added to StatsD service before. It stores
     * previously added config_keys in the shared preferences and only updates StatsD when
     * the MetricsConfig (of CarTelemetryService) has a new version.
     */
    @GuardedBy("mLock")
    private long addStatsConfigLocked(DataSubscriber subscriber) {
        String sharedPrefConfigKey = buildSharedPrefConfigKey(subscriber);
        long configKey = buildConfigKey(subscriber);
        // Store MetricsConfig (of CarTelemetryService) version per handler_function.
        String sharedPrefVersion = buildSharedPrefConfigVersionKey(configKey);
        StatsdConfig config = buildStatsdConfig(subscriber, configKey);
        if (mSharedPreferences.contains(sharedPrefVersion)) {
            int currentVersion = mSharedPreferences.getInt(sharedPrefVersion, 0);
            if (currentVersion < subscriber.getMetricsConfig().getVersion()) {
                // TODO(b/189143813): remove old version from StatsD
                Slog.d(CarLog.TAG_TELEMETRY, "Removing old config from StatsD");
            } else {
                // Ignore if the MetricsConfig version is current or older.
                return configKey;
            }
        }
        try {
            // It doesn't throw exception if the StatsdConfig is invalid. But it shouldn't happen,
            // as we generate well-tested StatsdConfig in this service.
            mStatsManager.addConfig(configKey, config.toByteArray());
            mSharedPreferences.edit()
                    .putInt(sharedPrefVersion, subscriber.getMetricsConfig().getVersion())
                    .putLong(sharedPrefConfigKey, configKey)
                    .apply();
        } catch (StatsUnavailableException e) {
            Slog.w(CarLog.TAG_TELEMETRY, "Failed to add config" + configKey, e);
            // TODO(b/189143813): if StatsManager is not ready, retry N times and hard fail after
            //                    by notifying DataBroker.
        }
        return configKey;
    }

    /** Removes StatsdConfig and returns configKey. */
    @GuardedBy("mLock")
    private long removeStatsConfigLocked(DataSubscriber subscriber) {
        String sharedPrefConfigKey = buildSharedPrefConfigKey(subscriber);
        long configKey = buildConfigKey(subscriber);
        // Store MetricsConfig (of CarTelemetryService) version per handler_function.
        String sharedPrefVersion = buildSharedPrefConfigVersionKey(configKey);
        try {
            mStatsManager.removeConfig(configKey);
            mSharedPreferences.edit().remove(sharedPrefVersion).remove(sharedPrefConfigKey).apply();
        } catch (StatsUnavailableException e) {
            Slog.w(CarLog.TAG_TELEMETRY, "Failed to remove config " + configKey, e);
            // TODO(b/189143813): if StatsManager is not ready, retry N times and hard fail after
            //                    by notifying DataBroker.
        }
        return configKey;
    }

    /**
     * Builds StatsdConfig id (aka config_key) using subscriber handler name.
     *
     * <p>StatsD uses ConfigKey struct to uniquely identify StatsdConfigs. StatsD ConfigKey consists
     * of two parts: client uid and config_key number. The StatsdConfig is added to StatsD from
     * CarService - which has uid=1000. Currently there is no client under uid=1000 and there will
     * not be config_key collision.
     */
    private static long buildConfigKey(DataSubscriber subscriber) {
        // Not to be confused with statsd metric, this one is a global CarTelemetry metric name.
        String metricConfigName = subscriber.getMetricsConfig().getName();
        String handlerFnName = subscriber.getSubscriber().getHandler();
        return HashUtils.sha256(metricConfigName + "-" + handlerFnName);
    }

    /** Builds {@link StatsdConfig} proto for given subscriber. */
    @VisibleForTesting
    static StatsdConfig buildStatsdConfig(DataSubscriber subscriber, long configId) {
        TelemetryProto.StatsPublisher.SystemMetric metric =
                subscriber.getPublisherParam().getStats().getSystemMetric();
        if (metric == TelemetryProto.StatsPublisher.SystemMetric.APP_START_MEMORY_STATE_CAPTURED) {
            return StatsdConfig.newBuilder()
                    // This id is not used in StatsD, but let's make it the same as config_key
                    // just in case.
                    .setId(configId)
                    .addAtomMatcher(StatsdConfigProto.AtomMatcher.newBuilder()
                            // The id must be unique within StatsdConfig/matchers
                            .setId(APP_START_MEMORY_STATE_CAPTURED_ATOM_MATCHER_ID)
                            .setSimpleAtomMatcher(StatsdConfigProto.SimpleAtomMatcher.newBuilder()
                                    .setAtomId(ATOM_APP_START_MEMORY_STATE_CAPTURED_ID)))
                    .addEventMetric(StatsdConfigProto.EventMetric.newBuilder()
                            // The id must be unique within StatsdConfig/metrics
                            .setId(APP_START_MEMORY_STATE_CAPTURED_EVENT_METRIC_ID)
                            .setWhat(APP_START_MEMORY_STATE_CAPTURED_ATOM_MATCHER_ID))
                    .addAllowedLogSource("AID_SYSTEM")
                    .build();
        } else {
            throw new IllegalArgumentException("Unsupported metric " + metric.name());
        }
    }
}
