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
package android.car.input;

import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.BOILERPLATE_CODE;

import android.annotation.NonNull;
import android.annotation.SystemApi;
import android.os.Parcel;
import android.os.Parcelable;

import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.util.AnnotationValidations;
import com.android.car.internal.util.DataClass;

import java.util.Arrays;

/**
 * {@code Parcelable} containing rotary input event.
 *
 * <p>A rotary input event can be either clockwise or counterclockwise and can contain more than 1
 * click. Each click has its own event time.
 *
 * @hide
 */
@SystemApi
@DataClass(
        genEqualsHashCode = true,
        genAidl = true)
public final class RotaryEvent implements Parcelable {
    /**
     * Represents the type of rotary event. This indicates which knob was rotated. For example, it
     * can be {@link CarInputManager#INPUT_TYPE_ROTARY_NAVIGATION}.
     */
    @CarInputManager.InputTypeEnum
    private final int mInputType;

    /**
     * Indicates if the event is clockwise (={@code true}) or counterclockwise (={@code false}).
     */
    private final boolean mClockwise;

    /**
     * Stores the event times of all clicks. Time used is uptime in milliseconds.
     * See {@link android.os.SystemClock#uptimeMillis()} for the definition of the time.
     *
     * <p>Timestamps are guaranteed to be monotonically increasing. If the input device cannot
     * capture timestamps for each click, all the timestamps will be the same.
     */
    @NonNull
    private final long[] mUptimeMillisForClicks;

    /**
     * Returns the number of clicks contained in this event.
     */
    public int getNumberOfClicks() {
        return mUptimeMillisForClicks.length;
    }

    /**
     * Returns the event time for the requested {@code clickIndex}. The time is recorded as
     * {@link android.os.SystemClock#uptimeMillis()}.
     *
     * @param clickIndex Index of click to check the time. It should be  in the range of 0 to
     * {@code getNumberOfClicks() - 1}.
     *
     * @return Event time
     */
    public long getUptimeMillisForClick(int clickIndex) {
        return mUptimeMillisForClicks[clickIndex];
    }

    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = BOILERPLATE_CODE)
    public String toString() {
        return new StringBuilder(128)
                .append("RotaryEvent{")
                .append("mInputType:")
                .append(mInputType)
                .append(",mClockwise:")
                .append(mClockwise)
                .append(",mUptimeMillisForClicks:")
                .append(Arrays.toString(mUptimeMillisForClicks))
                .append("}")
                .toString();
    }



    // Code below generated by codegen v1.0.23.
    //
    // DO NOT MODIFY!
    // CHECKSTYLE:OFF Generated code
    //
    // To regenerate run:
    // $ codegen $ANDROID_BUILD_TOP/packages/services/Car/car-lib/src/android/car/input/RotaryEvent.java
    //
    // To exclude the generated code from IntelliJ auto-formatting enable (one-time):
    //   Settings > Editor > Code Style > Formatter Control
    //@formatter:off


    /**
     * Creates a new RotaryEvent.
     *
     * @param inputType
     *   Represents the type of rotary event. This indicates which knob was rotated. For example, it
     *   can be {@link CarInputManager#INPUT_TYPE_ROTARY_NAVIGATION}.
     * @param clockwise
     *   Indicates if the event is clockwise (={@code true}) or counterclockwise (={@code false}).
     * @param uptimeMillisForClicks
     *   Stores the event times of all clicks. Time used is uptime in milliseconds.
     *   See {@link android.os.SystemClock#uptimeMillis()} for the definition of the time.
     *
     *   <p>Timestamps are guaranteed to be monotonically increasing. If the input device cannot
     *   capture timestamps for each click, all the timestamps will be the same.
     */
    @DataClass.Generated.Member
    public RotaryEvent(
            @CarInputManager.InputTypeEnum int inputType,
            boolean clockwise,
            @NonNull long[] uptimeMillisForClicks) {
        this.mInputType = inputType;
        AnnotationValidations.validate(
                CarInputManager.InputTypeEnum.class, null, mInputType);
        this.mClockwise = clockwise;
        this.mUptimeMillisForClicks = uptimeMillisForClicks;
        AnnotationValidations.validate(
                NonNull.class, null, mUptimeMillisForClicks);

        // onConstructed(); // You can define this method to get a callback
    }

    /**
     * Represents the type of rotary event. This indicates which knob was rotated. For example, it
     * can be {@link CarInputManager#INPUT_TYPE_ROTARY_NAVIGATION}.
     */
    @DataClass.Generated.Member
    public @CarInputManager.InputTypeEnum int getInputType() {
        return mInputType;
    }

    /**
     * Indicates if the event is clockwise (={@code true}) or counterclockwise (={@code false}).
     */
    @DataClass.Generated.Member
    public boolean isClockwise() {
        return mClockwise;
    }

    /**
     * Stores the event times of all clicks. Time used is uptime in milliseconds.
     * See {@link android.os.SystemClock#uptimeMillis()} for the definition of the time.
     *
     * <p>Timestamps are guaranteed to be monotonically increasing. If the input device cannot
     * capture timestamps for each click, all the timestamps will be the same.
     */
    @DataClass.Generated.Member
    public @NonNull long[] getUptimeMillisForClicks() {
        return mUptimeMillisForClicks;
    }

    @Override
    @DataClass.Generated.Member
    @ExcludeFromCodeCoverageGeneratedReport(reason = BOILERPLATE_CODE)
    public boolean equals(@android.annotation.Nullable Object o) {
        // You can override field equality logic by defining either of the methods like:
        // boolean fieldNameEquals(RotaryEvent other) { ... }
        // boolean fieldNameEquals(FieldType otherValue) { ... }

        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        @SuppressWarnings("unchecked")
        RotaryEvent that = (RotaryEvent) o;
        //noinspection PointlessBooleanExpression
        return true
                && mInputType == that.mInputType
                && mClockwise == that.mClockwise
                && Arrays.equals(mUptimeMillisForClicks, that.mUptimeMillisForClicks);
    }

    @Override
    @DataClass.Generated.Member
    @ExcludeFromCodeCoverageGeneratedReport(reason = BOILERPLATE_CODE)
    public int hashCode() {
        // You can override field hashCode logic by defining methods like:
        // int fieldNameHashCode() { ... }

        int _hash = 1;
        _hash = 31 * _hash + mInputType;
        _hash = 31 * _hash + Boolean.hashCode(mClockwise);
        _hash = 31 * _hash + Arrays.hashCode(mUptimeMillisForClicks);
        return _hash;
    }

    @Override
    @DataClass.Generated.Member
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        // You can override field parcelling by defining methods like:
        // void parcelFieldName(Parcel dest, int flags) { ... }

        byte flg = 0;
        if (mClockwise) flg |= 0x2;
        dest.writeByte(flg);
        dest.writeInt(mInputType);
        dest.writeLongArray(mUptimeMillisForClicks);
    }

    @Override
    @DataClass.Generated.Member
    @ExcludeFromCodeCoverageGeneratedReport(reason = BOILERPLATE_CODE)
    public int describeContents() { return 0; }

    /** @hide */
    @SuppressWarnings({"unchecked", "RedundantCast"})
    @DataClass.Generated.Member
    /* package-private */ RotaryEvent(@NonNull Parcel in) {
        // You can override field unparcelling by defining methods like:
        // static FieldType unparcelFieldName(Parcel in) { ... }

        byte flg = in.readByte();
        boolean clockwise = (flg & 0x2) != 0;
        int inputType = in.readInt();
        long[] uptimeMillisForClicks = in.createLongArray();

        this.mInputType = inputType;
        AnnotationValidations.validate(
                CarInputManager.InputTypeEnum.class, null, mInputType);
        this.mClockwise = clockwise;
        this.mUptimeMillisForClicks = uptimeMillisForClicks;
        AnnotationValidations.validate(
                NonNull.class, null, mUptimeMillisForClicks);

        // onConstructed(); // You can define this method to get a callback
    }

    @DataClass.Generated.Member
    public static final @NonNull Parcelable.Creator<RotaryEvent> CREATOR
            = new Parcelable.Creator<RotaryEvent>() {
        @Override
        public RotaryEvent[] newArray(int size) {
            return new RotaryEvent[size];
        }

        @Override
        public RotaryEvent createFromParcel(@NonNull Parcel in) {
            return new RotaryEvent(in);
        }
    };

    @DataClass.Generated(
            time = 1628099164166L,
            codegenVersion = "1.0.23",
            sourceFile = "packages/services/Car/car-lib/src/android/car/input/RotaryEvent.java",
            inputSignatures = "private final @android.car.input.CarInputManager.InputTypeEnum int mInputType\nprivate final  boolean mClockwise\nprivate final @android.annotation.NonNull long[] mUptimeMillisForClicks\npublic  int getNumberOfClicks()\npublic  long getUptimeMillisForClick(int)\npublic @java.lang.Override @com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport java.lang.String toString()\nclass RotaryEvent extends java.lang.Object implements [android.os.Parcelable]\n@com.android.car.internal.util.DataClass(genEqualsHashCode=true, genAidl=true)")
    @Deprecated
    @ExcludeFromCodeCoverageGeneratedReport(reason = BOILERPLATE_CODE)
    private void __metadata() {}


    //@formatter:on
    // End of generated code

}
