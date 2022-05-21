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

package android.car.vms;

import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.BOILERPLATE_CODE;

import android.annotation.Nullable;
import android.car.annotation.AddedInOrBefore;
import android.os.Parcel;
import android.os.Parcelable;

import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.util.DataClass;

import java.util.Arrays;

/**
 * Hidden data object used to communicate Vehicle Map Service publisher information on registration.
 *
 * @hide
 */
@DataClass(
        genEqualsHashCode = true,
        genAidl = true)
public class VmsProviderInfo implements Parcelable {
    private @Nullable final byte[] mDescription;



    // Code below generated by codegen v1.0.14.
    //
    // DO NOT MODIFY!
    // CHECKSTYLE:OFF Generated code
    //
    // To regenerate run:
    // $ codegen $ANDROID_BUILD_TOP/packages/services/Car/car-lib/src/android/car/vms/VmsProviderInfo.java
    //
    // To exclude the generated code from IntelliJ auto-formatting enable (one-time):
    //   Settings > Editor > Code Style > Formatter Control
    //@formatter:off


    @DataClass.Generated.Member
    public VmsProviderInfo(
            @Nullable byte[] description) {
        this.mDescription = description;

        // onConstructed(); // You can define this method to get a callback
    }

    @DataClass.Generated.Member
    @AddedInOrBefore(majorVersion = 33)
    public @Nullable byte[] getDescription() {
        return mDescription;
    }

    @Override
    @DataClass.Generated.Member
    @AddedInOrBefore(majorVersion = 33)
    public boolean equals(@Nullable Object o) {
        // You can override field equality logic by defining either of the methods like:
        // boolean fieldNameEquals(VmsProviderInfo other) { ... }
        // boolean fieldNameEquals(FieldType otherValue) { ... }

        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        @SuppressWarnings("unchecked")
        VmsProviderInfo that = (VmsProviderInfo) o;
        //noinspection PointlessBooleanExpression
        return true
                && Arrays.equals(mDescription, that.mDescription);
    }

    @Override
    @DataClass.Generated.Member
    @AddedInOrBefore(majorVersion = 33)
    public int hashCode() {
        // You can override field hashCode logic by defining methods like:
        // int fieldNameHashCode() { ... }

        int _hash = 1;
        _hash = 31 * _hash + Arrays.hashCode(mDescription);
        return _hash;
    }

    @Override
    @DataClass.Generated.Member
    @AddedInOrBefore(majorVersion = 33)
    public void writeToParcel(@android.annotation.NonNull Parcel dest, int flags) {
        // You can override field parcelling by defining methods like:
        // void parcelFieldName(Parcel dest, int flags) { ... }

        byte flg = 0;
        if (mDescription != null) flg |= 0x1;
        dest.writeByte(flg);
        if (mDescription != null) dest.writeByteArray(mDescription);
    }

    @Override
    @DataClass.Generated.Member
    @ExcludeFromCodeCoverageGeneratedReport(reason = BOILERPLATE_CODE)
    @AddedInOrBefore(majorVersion = 33)
    public int describeContents() { return 0; }

    /** @hide */
    @SuppressWarnings({"unchecked", "RedundantCast"})
    @DataClass.Generated.Member
    protected VmsProviderInfo(@android.annotation.NonNull Parcel in) {
        // You can override field unparcelling by defining methods like:
        // static FieldType unparcelFieldName(Parcel in) { ... }

        byte flg = in.readByte();
        byte[] description = (flg & 0x1) == 0 ? null : in.createByteArray();

        this.mDescription = description;

        // onConstructed(); // You can define this method to get a callback
    }

    @DataClass.Generated.Member
    @AddedInOrBefore(majorVersion = 33)
    public static final @android.annotation.NonNull Parcelable.Creator<VmsProviderInfo> CREATOR
            = new Parcelable.Creator<VmsProviderInfo>() {
        @Override
        public VmsProviderInfo[] newArray(int size) {
            return new VmsProviderInfo[size];
        }

        @Override
        public VmsProviderInfo createFromParcel(@android.annotation.NonNull Parcel in) {
            return new VmsProviderInfo(in);
        }
    };

    @DataClass.Generated(
            time = 1581406319319L,
            codegenVersion = "1.0.14",
            sourceFile = "packages/services/Car/car-lib/src/android/car/vms/VmsProviderInfo.java",
            inputSignatures = "private final @android.annotation.Nullable byte[] mDescription\nclass VmsProviderInfo extends java.lang.Object implements [android.os.Parcelable]\n@com.android.car.internal.util.DataClass(genEqualsHashCode=true, genAidl=true)")
    @Deprecated
    private void __metadata() {}


    //@formatter:on
    // End of generated code

}
