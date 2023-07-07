/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.automotive.watchdog.internal;

/**
 * Structure that describes the CPU usage stats for a process.
 */
parcelable ProcessCpuUsageStats {
    /**
     * PID of the process whose stats are stored in the below fields.
     */
    int pid;

    /**
     * Name of the process.
     */
    @utf8InCpp String name;

    /**
     * CPU time (in milliseconds) of the process during the latest collection.
     */
    long cpuTimeMillis;

    /**
     * CPU cycles of the process during the latest collection.
     */
    long cpuCycles;
}