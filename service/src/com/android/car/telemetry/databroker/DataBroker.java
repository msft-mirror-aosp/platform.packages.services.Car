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

package com.android.car.telemetry.databroker;

import android.os.Bundle;

import com.android.car.telemetry.TelemetryProto;

import java.util.List;

/** Interface for the data path. Handles data forwarding from publishers to subscribers */
public interface DataBroker {

    /**
     * ScriptResultListener is the listener for script results.
     */
    interface ScriptResultListener {
        /**
         * Notifies listener of script result.
         *
         * @param scriptResult the script result.
         */
        void onScriptResult(Bundle scriptResult);
    }

    /**
     * Updates the set of publishers whose data can be consumed by subscribers.
     *
     * @param allowedPublishers the types publisher data source that can be consumed. The class is
     *                          generated by Protobuf oneof fields.
     * @see
     * <a href="https://developers.google.com/protocol-buffers/docs/reference/java-generated#oneof-fields">Oneof
     * Fields Generated Code</a>
     */
    void enablePublishers(List<TelemetryProto.Publisher.PublisherCase> allowedPublishers);

    /**
     * Adds an active {@link com.android.car.telemetry.TelemetryProto.MetricsConfig} that is pending
     * execution. When updating the MetricsConfig to a newer version, the caller must call
     * {@link #removeMetricsConfiguration(TelemetryProto.MetricsConfig)} first to clear the old
     * MetricsConfig.
     * TODO(b/191378559): Define behavior when metricsConfig contains invalid config
     *
     * @param metricsConfig to be added and queued for execution.
     * @return true for success, false for failure.
     */
    boolean addMetricsConfiguration(TelemetryProto.MetricsConfig metricsConfig);

    /**
     * Removes a {@link com.android.car.telemetry.TelemetryProto.MetricsConfig} and all its
     * relevant subscriptions.
     *
     * @param metricsConfig to be removed from DataBroker.
     * @return true for success, false for failure.
     */
    boolean removeMetricsConfiguration(TelemetryProto.MetricsConfig metricsConfig);
}
