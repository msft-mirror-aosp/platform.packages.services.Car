/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.car.hardware.property;

import static java.lang.Integer.toHexString;

import android.car.VehiclePropertyIds;

/**
 * Exception thrown when device that associated with the vehicle property is temporarily
 * not available. It's likely that retrying will be successful.
 */
public class PropertyNotAvailableAndRetryException extends IllegalStateException {
    PropertyNotAvailableAndRetryException(int propertyId, int areaId) {
        super("Property ID: " + VehiclePropertyIds.toString(propertyId) + " area ID: 0x"
                + toHexString(areaId)
                + " - is temporarily not available. Try the operation later.");
    }
}
