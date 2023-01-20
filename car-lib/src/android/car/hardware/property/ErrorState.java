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

package android.car.hardware.property;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.SystemApi;
import android.car.annotation.ApiRequirements;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Used to enumerate the possible error states. For Android 14, {@code ErrorState} is used by ADAS
 * STATE properties in {@link android.car.VehiclePropertyIds}, but its use may be expanded in future
 * releases.
 *
 * <p>This list of states may be extended in future releases to include additional states.
 * @hide
 */
@SystemApi
public final class ErrorState {

    /**
     * This state is used as an alternative to any {@code ErrorState} value that is not defined in
     * the platform. Ideally, implementations of vehicle properties should not use this state. The
     * framework can use this field to remain backwards compatible if this enum is extended to
     * include additional states.
     */
    @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.UPSIDE_DOWN_CAKE_0,
            minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
    public static final int OTHER_ERROR_STATE = -1;

    @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.UPSIDE_DOWN_CAKE_0,
            minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
    public static final int NOT_AVAILABLE_DISABLED = -2;

    @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.UPSIDE_DOWN_CAKE_0,
            minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
    public static final int NOT_AVAILABLE_SPEED_LOW = -3;

    @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.UPSIDE_DOWN_CAKE_0,
            minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
    public static final int NOT_AVAILABLE_SPEED_HIGH = -4;

    @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.UPSIDE_DOWN_CAKE_0,
            minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
    public static final int NOT_AVAILABLE_SAFETY = -5;

    private ErrorState() {}

    /**
     * Returns a user-friendly representation of an {@code ErrorState}.
     */
    @NonNull
    @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.UPSIDE_DOWN_CAKE_0,
            minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
    public static String toString(@ErrorStateInt int errorState) {
        switch (errorState) {
            case OTHER_ERROR_STATE:
                return "OTHER_ERROR_STATE";
            case NOT_AVAILABLE_DISABLED:
                return "NOT_AVAILABLE_DISABLED";
            case NOT_AVAILABLE_SPEED_LOW:
                return "NOT_AVAILABLE_SPEED_LOW";
            case NOT_AVAILABLE_SPEED_HIGH:
                return "NOT_AVAILABLE_SPEED_HIGH";
            case NOT_AVAILABLE_SAFETY:
                return "NOT_AVAILABLE_SAFETY";
            default:
                return "0x" + Integer.toHexString(errorState);
        }
    }

    /** @hide */
    @IntDef({OTHER_ERROR_STATE, NOT_AVAILABLE_DISABLED, NOT_AVAILABLE_SPEED_LOW,
            NOT_AVAILABLE_SPEED_HIGH, NOT_AVAILABLE_SAFETY})
    @Retention(RetentionPolicy.SOURCE)
    public @interface ErrorStateInt {}
}

