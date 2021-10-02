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

import android.os.PersistableBundle;

import com.android.car.telemetry.AtomsProto;
import com.android.car.telemetry.StatsLogProto;

import java.util.ArrayList;
import java.util.List;

/**
 * Class for converting event metric data to {@link PersistableBundle} compatible format.
 */
public class EventMetricDataConverter {
    static final String ELAPSED_TIME_NANOS = "elapsed_timestamp_nanos";

    /**
     * Converts a list of {@link StatsLogProto.EventMetricData} to {@link PersistableBundle}
     * format such that along with the elapsed time array each field of the atom has an associated
     * array containing the field's data in order received, matching the elapsed time array order.
     *
     * Example:
     * {
     *   elapsed_timestamp_nanos: [32948395739, 45623453646, ...]
     *   uid: [1000, 1100, ...]
     *   ...
     * }
     * @param eventDataList the list of {@link StatsLogProto.EventMetricData} to be converted.
     * @return {@link PersistableBundle} that holds the converted values.
     * @throws StatsConversionException if atom field mismatch or can't convert dimension value.
     */
    static PersistableBundle convertEventDataList(
                List<StatsLogProto.EventMetricData> eventDataList)
                throws StatsConversionException {
        long[] elapsedTimes = new long[eventDataList.size()];
        List<AtomsProto.Atom> atoms = new ArrayList<>(eventDataList.size());
        for (int i = 0; i < eventDataList.size(); ++i) {
            elapsedTimes[i] = eventDataList.get(i).getElapsedTimestampNanos();
            atoms.add(eventDataList.get(i).getAtom());
        }
        PersistableBundle bundle = AtomListConverter.convert(atoms, null, null, null);
        bundle.putLongArray(ELAPSED_TIME_NANOS, elapsedTimes);
        return bundle;
    }
}
