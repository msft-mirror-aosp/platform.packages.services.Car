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
import android.car.annotation.AddedInOrBefore;
import android.os.Parcel;
import android.os.Parcelable;

import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.util.DataClass;

/**
 * {@code Parcelable} containing custom input event.
 *
 * <p>A custom input event representing HW_CUSTOM_INPUT event defined in
 * {@code hardware/interfaces/automotive/vehicle/2.0/types.hal}.
 *
 * @hide
 */
@SystemApi
@DataClass(
        genEqualsHashCode = true,
        genAidl = true)
public final class CustomInputEvent implements Parcelable {

    // The following constant values must be in sync with the ones defined in
    // {@code hardware/interfaces/automotive/vehicle/2.0/types.hal}
    @AddedInOrBefore(majorVersion = 33)
    public static final int INPUT_CODE_F1 = 1001;
    @AddedInOrBefore(majorVersion = 33)
    public static final int INPUT_CODE_F2 = 1002;
    @AddedInOrBefore(majorVersion = 33)
    public static final int INPUT_CODE_F3 = 1003;
    @AddedInOrBefore(majorVersion = 33)
    public static final int INPUT_CODE_F4 = 1004;
    @AddedInOrBefore(majorVersion = 33)
    public static final int INPUT_CODE_F5 = 1005;
    @AddedInOrBefore(majorVersion = 33)
    public static final int INPUT_CODE_F6 = 1006;
    @AddedInOrBefore(majorVersion = 33)
    public static final int INPUT_CODE_F7 = 1007;
    @AddedInOrBefore(majorVersion = 33)
    public static final int INPUT_CODE_F8 = 1008;
    @AddedInOrBefore(majorVersion = 33)
    public static final int INPUT_CODE_F9 = 1009;
    @AddedInOrBefore(majorVersion = 33)
    public static final int INPUT_CODE_F10 = 1010;

    private final int mInputCode;

    private final int mTargetDisplayType;
    private final int mRepeatCounter;


    // Code below generated by codegen v1.0.15.
    //
    // Note: Fields and methods related to INPUT_CODE were manually edited in order to remove the
    // value range constraints in #mInputCode.
    //
    // CHECKSTYLE:OFF Generated code
    // To regenerate run:
    // $ codegen --to-string $ANDROID_BUILD_TOP/packages/services/Car/car-lib/src/android/car
    // /input/CustomInputEvent.java
    // Added AddedInOrBefore or ApiRequirement Annotation manually
    //
    // To exclude the generated code from IntelliJ auto-formatting enable (one-time):
    //   Settings > Editor > Code Style > Formatter Control
    //@formatter:off

    @DataClass.Generated.Member
    @NonNull
    @AddedInOrBefore(majorVersion = 33)
    public static String inputCodeToString(int value) {
        return Integer.toString(value);
    }

    @DataClass.Generated.Member
    public CustomInputEvent(
            int inputCode,
            int targetDisplayType,
            int repeatCounter) {

        this.mInputCode = inputCode;
        this.mTargetDisplayType = targetDisplayType;
        this.mRepeatCounter = repeatCounter;

        // onConstructed(); // You can define this method to get a callback
    }

    @DataClass.Generated.Member
    @AddedInOrBefore(majorVersion = 33)
    public int getInputCode() {
        return mInputCode;
    }

    @DataClass.Generated.Member
    @AddedInOrBefore(majorVersion = 33)
    public int getTargetDisplayType() {
        return mTargetDisplayType;
    }

    @DataClass.Generated.Member
    @AddedInOrBefore(majorVersion = 33)
    public int getRepeatCounter() {
        return mRepeatCounter;
    }

    @Override
    @DataClass.Generated.Member
    @ExcludeFromCodeCoverageGeneratedReport(reason = BOILERPLATE_CODE)
    @AddedInOrBefore(majorVersion = 33)
    public String toString() {
        // You can override field toString logic by defining methods like:
        // String fieldNameToString() { ... }

        return "CustomInputEvent { " +
                "inputCode = " + mInputCode + ", " +
                "targetDisplayType = " + mTargetDisplayType + ", " +
                "repeatCounter = " + mRepeatCounter +
                " }";
    }

    @Override
    @DataClass.Generated.Member
    @ExcludeFromCodeCoverageGeneratedReport(reason = BOILERPLATE_CODE)
    @AddedInOrBefore(majorVersion = 33)
    public boolean equals(@android.annotation.Nullable Object o) {
        // You can override field equality logic by defining either of the methods like:
        // boolean fieldNameEquals(CustomInputEvent other) { ... }
        // boolean fieldNameEquals(FieldType otherValue) { ... }

        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        @SuppressWarnings("unchecked")
        CustomInputEvent that = (CustomInputEvent) o;
        //noinspection PointlessBooleanExpression
        return true
                && mInputCode == that.mInputCode
                && mTargetDisplayType == that.mTargetDisplayType
                && mRepeatCounter == that.mRepeatCounter;
    }

    @Override
    @DataClass.Generated.Member
    @ExcludeFromCodeCoverageGeneratedReport(reason = BOILERPLATE_CODE)
    @AddedInOrBefore(majorVersion = 33)
    public int hashCode() {
        // You can override field hashCode logic by defining methods like:
        // int fieldNameHashCode() { ... }

        int _hash = 1;
        _hash = 31 * _hash + mInputCode;
        _hash = 31 * _hash + mTargetDisplayType;
        _hash = 31 * _hash + mRepeatCounter;
        return _hash;
    }

    @Override
    @DataClass.Generated.Member
    @AddedInOrBefore(majorVersion = 33)
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        // You can override field parcelling by defining methods like:
        // void parcelFieldName(Parcel dest, int flags) { ... }

        dest.writeInt(mInputCode);
        dest.writeInt(mTargetDisplayType);
        dest.writeInt(mRepeatCounter);
    }

    @Override
    @DataClass.Generated.Member
    @ExcludeFromCodeCoverageGeneratedReport(reason = BOILERPLATE_CODE)
    @AddedInOrBefore(majorVersion = 33)
    public int describeContents() {
        return 0;
    }

    /** @hide */
    @SuppressWarnings({"unchecked", "RedundantCast"})
    @DataClass.Generated.Member
    /* package-private */ CustomInputEvent(@NonNull Parcel in) {
        // You can override field unparcelling by defining methods like:
        // static FieldType unparcelFieldName(Parcel in) { ... }

        this.mInputCode = in.readInt();
        this.mTargetDisplayType = in.readInt();
        this.mRepeatCounter = in.readInt();

        // onConstructed(); // You can define this method to get a callback
    }

    @DataClass.Generated.Member
    @AddedInOrBefore(majorVersion = 33)
    public static final @NonNull
    Parcelable.Creator<CustomInputEvent> CREATOR
            = new Parcelable.Creator<CustomInputEvent>() {
        @Override
        public CustomInputEvent[] newArray(int size) {
            return new CustomInputEvent[size];
        }

        @Override
        public CustomInputEvent createFromParcel(@NonNull Parcel in) {
            return new CustomInputEvent(in);
        }
    };

    @DataClass.Generated(
            time = 1600715769152L,
            codegenVersion = "1.0.15",
            sourceFile = "packages/services/Car/car-lib/src/android/car/input/CustomInputEvent"
                    + ".java",
            inputSignatures = "public static final  int INPUT_CODE_F1\npublic static final  int "
                    + "INPUT_CODE_F2\npublic static final  int INPUT_CODE_F3\npublic static final"
                    + "  int INPUT_CODE_F4\npublic static final  int INPUT_CODE_F5\npublic static"
                    + " final  int INPUT_CODE_F6\npublic static final  int INPUT_CODE_F7\npublic "
                    + "static final  int INPUT_CODE_F8\npublic static final  int "
                    + "INPUT_CODE_F9\npublic static final  int INPUT_CODE_F10\nprivate final "
                    + "@android.car.input.CustomInputEvent.InputCode int mInputCode\nprivate "
                    + "final  int mTargetDisplayType\nprivate final  int mRepeatCounter\nclass "
                    + "CustomInputEvent extends java.lang.Object implements [android.os"
                    + ".Parcelable]\n@com.android.car.internal.util.DataClass(genEqualsHashCode=true,"
                    + " genAidl=true)")
    @Deprecated
    @ExcludeFromCodeCoverageGeneratedReport(reason = BOILERPLATE_CODE)
    private void __metadata() {
    }

    //@formatter:on
    // End of generated code
}
