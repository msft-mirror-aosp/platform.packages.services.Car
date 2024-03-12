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

package android.car.oem;

import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.BOILERPLATE_CODE;
import static com.android.car.internal.util.AnnotationValidations.validate;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SystemApi;
import android.car.media.CarVolumeGroupInfo;
import android.os.Parcel;
import android.os.Parcelable;

import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.util.DataClass;

import java.util.Objects;

/**
 * Class to encapsulate car volume group evaluation Results
 *
 * @hide
 */
@SystemApi
@DataClass(
        genToString = true,
        genHiddenConstructor = true,
        genHiddenConstDefs = true,
        genBuilder = true,
        genEqualsHashCode = true)
public final class OemCarVolumeChangeInfo implements Parcelable {

    private final boolean mVolumeChanged;
    @Nullable
    private final CarVolumeGroupInfo mChangedVolumeGroup;

    // Code below generated by codegen v1.0.23.
    //
    // DO NOT MODIFY!
    // CHECKSTYLE:OFF Generated code
    //
    // To regenerate run:
    // $ codegen $ANDROID_BUILD_TOP/packages/services/Car/car-lib/src/android/car/oem
    // /OemCarVolumeChangeInfo.java
    // Added AddedInOrBefore or ApiRequirement Annotation manually
    //
    // To exclude the generated code from IntelliJ auto-formatting enable (one-time):
    //   Settings > Editor > Code Style > Formatter Control
    //@formatter:off


    /**
     * Creates a new OemCarVolumeChangeInfo.
     *
     * @hide
     */
    @DataClass.Generated.Member
    public OemCarVolumeChangeInfo(
            boolean volumeChanged,
            @Nullable CarVolumeGroupInfo changedVolumeGroup) {
        this.mVolumeChanged = volumeChanged;
        this.mChangedVolumeGroup = changedVolumeGroup;

        // onConstructed(); // You can define this method to get a callback
    }

    /**
     * @return {@code true} if the any volume group changed.
     *
     * <p>The actual changed in volume status will be in the volume group info see
     * {@link #getChangedVolumeGroup}
     */
    @DataClass.Generated.Member
    public boolean isVolumeChanged() {
        return mVolumeChanged;
    }

    /**
     * @return corresponding change in volume.
     *
     * <p>A corresponding change will be determined by comparing the corresponding state of the
     * volume group info and the value return here. Thus the volume group value should be changed,
     * for example, for a request to evaluate {@code AudioManager#ADJUST_UNMUTE} the volume group
     * info's mute state should be unmuted.
     */
    @DataClass.Generated.Member
    public @Nullable CarVolumeGroupInfo getChangedVolumeGroup() {
        return mChangedVolumeGroup;
    }

    @Override
    @DataClass.Generated.Member
    @ExcludeFromCodeCoverageGeneratedReport(reason = BOILERPLATE_CODE)
    public String toString() {
        // You can override field toString logic by defining methods like:
        // String fieldNameToString() { ... }

        return "OemCarVolumeChangeInfo { " +
                "volumeChanged = " + mVolumeChanged + ", " +
                "changedVolumeGroup = " + mChangedVolumeGroup +
        " }";
    }

    @Override
    @DataClass.Generated.Member
    public boolean equals(@Nullable Object o) {
        // You can override field equality logic by defining either of the methods like:
        // boolean fieldNameEquals(OemCarVolumeChangeInfo other) { ... }
        // boolean fieldNameEquals(FieldType otherValue) { ... }

        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        @SuppressWarnings("unchecked")
        OemCarVolumeChangeInfo that = (OemCarVolumeChangeInfo) o;
        //noinspection PointlessBooleanExpression
        return mVolumeChanged == that.mVolumeChanged
                && Objects.equals(mChangedVolumeGroup, that.mChangedVolumeGroup);
    }

    @Override
    @DataClass.Generated.Member
    public int hashCode() {
        // You can override field hashCode logic by defining methods like:
        // int fieldNameHashCode() { ... }

        int _hash = 1;
        _hash = 31 * _hash + Boolean.hashCode(mVolumeChanged);
        _hash = 31 * _hash + Objects.hashCode(mChangedVolumeGroup);
        return _hash;
    }

    @Override
    @DataClass.Generated.Member
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        // You can override field parcelling by defining methods like:
        // void parcelFieldName(Parcel dest, int flags) { ... }

        byte flg = 0;
        if (mVolumeChanged) flg = (byte) (flg | 0x1);
        if (mChangedVolumeGroup != null) flg = (byte) (flg | 0x2);
        dest.writeByte(flg);
        if (mChangedVolumeGroup != null) dest.writeTypedObject(mChangedVolumeGroup, flags);
    }

    @Override
    @DataClass.Generated.Member
    public int describeContents() { return 0; }

    /** @hide */
    @SuppressWarnings({"unchecked", "RedundantCast"})
    @DataClass.Generated.Member
    /* package-private */ OemCarVolumeChangeInfo(@NonNull Parcel in) {
        // You can override field unparcelling by defining methods like:
        // static FieldType unparcelFieldName(Parcel in) { ... }

        byte flg = in.readByte();
        boolean volumeChanged = (flg & 0x1) != 0;
        CarVolumeGroupInfo _changedVolumeGroup = (flg & 0x2) == 0
                ? null : (CarVolumeGroupInfo) in.readTypedObject(CarVolumeGroupInfo.CREATOR);

        this.mVolumeChanged = volumeChanged;
        this.mChangedVolumeGroup = _changedVolumeGroup;

        // onConstructed(); // You can define this method to get a callback
    }

    @DataClass.Generated.Member
    public static final @NonNull Parcelable.Creator<OemCarVolumeChangeInfo> CREATOR
            = new Parcelable.Creator<OemCarVolumeChangeInfo>() {
        @Override
        public OemCarVolumeChangeInfo[] newArray(int size) {
            return new OemCarVolumeChangeInfo[size];
        }

        @Override
        public OemCarVolumeChangeInfo createFromParcel(@NonNull Parcel in) {
            return new OemCarVolumeChangeInfo(in);
        }
    };

    /**
     * Helper information to respond when there is no volume change.
     */
    @NonNull
    public static final OemCarVolumeChangeInfo EMPTY_OEM_VOLUME_CHANGE =
            new OemCarVolumeChangeInfo(/* volumeChanged= */ false, /* changedVolumeGroup= */ null);

    /**
     * A builder for {@link OemCarVolumeChangeInfo}
     */
    @SuppressWarnings("WeakerAccess")
    @DataClass.Generated.Member
    public static final class Builder {

        private boolean mVolumeChanged;
        private @Nullable CarVolumeGroupInfo changedVolumeGroup;

        private long mBuilderFieldsSet = 0L;

        /**
         * Creates a new Builder
         */
        public Builder(boolean volumeChanged) {
            mVolumeChanged = volumeChanged;
        }

        @DataClass.Generated.Member
        public @NonNull Builder setChangedVolumeGroup(@NonNull CarVolumeGroupInfo value) {
            validate(NonNull.class, null, value);
            checkNotUsed();
            mBuilderFieldsSet |= 0x2;
            changedVolumeGroup = value;
            return this;
        }

        /** Builds the instance. This builder should not be touched after calling this! */
        public @NonNull OemCarVolumeChangeInfo build() {
            checkNotUsed();
            mBuilderFieldsSet |= 0x4; // Mark builder used

            OemCarVolumeChangeInfo o = new OemCarVolumeChangeInfo(
                    mVolumeChanged,
                    changedVolumeGroup);
            return o;
        }

        private void checkNotUsed() {
            if ((mBuilderFieldsSet & 0x4) != 0) {
                throw new IllegalStateException(
                        "This Builder should not be reused. Use a new Builder instance instead");
            }
        }
    }

    @SuppressWarnings("unused")
    @DataClass.Generated(
            time = 1669943373159L,
            codegenVersion = "1.0.23",
            sourceFile = "packages/services/Car/car-lib/src/android/car/oem"
                    + "/OemCarVolumeChangeInfo.java",
            inputSignatures ="private final  boolean mVolumeChanged\n"
                    + "private final @Nullable android.car.media.CarVolumeGroupInfo "
                    + "changedVolumeGroup\nclass OemCarVolumeChangeInfo extends java.lang.Object"
                    + " implements [android.os.Parcelable]\n@com.android.car.internal.util"
                    + ".DataClass(genToString=true, genHiddenConstructor=true, "
                    + "genHiddenConstDefs=true, genBuilder=true, genEqualsHashCode=true)")
    @Deprecated
    private void __metadata() {}


    //@formatter:on
    // End of generated code

}
