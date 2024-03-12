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

package android.car.watchdog;

import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.BOILERPLATE_CODE;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.Parcelable;
import android.os.UserHandle;

import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.util.AnnotationValidations;
import com.android.car.internal.util.DataClass;

/**
 * Resource overuse stats for a package.
 */
@DataClass(genToString = true, genHiddenBuilder = true)
public final class ResourceOveruseStats implements Parcelable {
    /**
     * Name of the package, whose stats are recorded in the below fields.
     *
     * NOTE: For packages that share a UID, the package name will be the shared package name because
     *       the stats are aggregated for all packages under the shared UID.
     */
    private @NonNull String mPackageName;

    /**
     * User handle, whose stats are recorded in the below fields.
     */
    private @NonNull UserHandle mUserHandle;

    /*
     * I/O overuse stats for the package. If the package didn't opt-in to receive I/O overuse stats
     * or the package doesn't have I/O overuse stats, this value will be null.
     */
    private @Nullable IoOveruseStats mIoOveruseStats = null;



    // Code below generated by codegen v1.0.23.
    //
    // DO NOT MODIFY!
    // CHECKSTYLE:OFF Generated code
    //
    // To regenerate run:
    // $ codegen $ANDROID_BUILD_TOP/packages/services/Car/car-lib/src/android/car/watchdog/ResourceOveruseStats.java
    // Added AddedInOrBefore or ApiRequirement Annotation manually
    //
    // To exclude the generated code from IntelliJ auto-formatting enable (one-time):
    //   Settings > Editor > Code Style > Formatter Control
    //@formatter:off


    @DataClass.Generated.Member
    /* package-private */ ResourceOveruseStats(
            @NonNull String packageName,
            @NonNull UserHandle userHandle,
            @Nullable IoOveruseStats ioOveruseStats) {
        this.mPackageName = packageName;
        AnnotationValidations.validate(
                NonNull.class, null, mPackageName);
        this.mUserHandle = userHandle;
        AnnotationValidations.validate(
                NonNull.class, null, mUserHandle);
        this.mIoOveruseStats = ioOveruseStats;

        // onConstructed(); // You can define this method to get a callback
    }

    /**
     * Name of the package, whose stats are recorded in the below fields.
     *
     * NOTE: For packages that share a UID, the package name will be the shared package name because
     *       the stats are aggregated for all packages under the shared UID.
     */
    @DataClass.Generated.Member
    public @NonNull String getPackageName() {
        return mPackageName;
    }

    /**
     * User handle, whose stats are recorded in the below fields.
     */
    @DataClass.Generated.Member
    public @NonNull UserHandle getUserHandle() {
        return mUserHandle;
    }

    @DataClass.Generated.Member
    public @Nullable IoOveruseStats getIoOveruseStats() {
        return mIoOveruseStats;
    }

    @Override
    @DataClass.Generated.Member
    public String toString() {
        // You can override field toString logic by defining methods like:
        // String fieldNameToString() { ... }

        return "ResourceOveruseStats { " +
                "packageName = " + mPackageName + ", " +
                "userHandle = " + mUserHandle + ", " +
                "ioOveruseStats = " + mIoOveruseStats +
        " }";
    }

    @Override
    @DataClass.Generated.Member
    public void writeToParcel(@NonNull android.os.Parcel dest, int flags) {
        // You can override field parcelling by defining methods like:
        // void parcelFieldName(Parcel dest, int flags) { ... }

        byte flg = 0;
        if (mIoOveruseStats != null) flg |= 0x4;
        dest.writeByte(flg);
        dest.writeString(mPackageName);
        dest.writeTypedObject(mUserHandle, flags);
        if (mIoOveruseStats != null) dest.writeTypedObject(mIoOveruseStats, flags);
    }

    @Override
    @DataClass.Generated.Member
    @ExcludeFromCodeCoverageGeneratedReport(reason = BOILERPLATE_CODE)
    public int describeContents() { return 0; }

    /** @hide */
    @SuppressWarnings({"unchecked", "RedundantCast"})
    @DataClass.Generated.Member
    /* package-private */ ResourceOveruseStats(@NonNull android.os.Parcel in) {
        // You can override field unparcelling by defining methods like:
        // static FieldType unparcelFieldName(Parcel in) { ... }

        byte flg = in.readByte();
        String packageName = in.readString();
        UserHandle userHandle = (UserHandle) in.readTypedObject(UserHandle.CREATOR);
        IoOveruseStats ioOveruseStats = (flg & 0x4) == 0 ? null : (IoOveruseStats) in.readTypedObject(IoOveruseStats.CREATOR);

        this.mPackageName = packageName;
        AnnotationValidations.validate(
                NonNull.class, null, mPackageName);
        this.mUserHandle = userHandle;
        AnnotationValidations.validate(
                NonNull.class, null, mUserHandle);
        this.mIoOveruseStats = ioOveruseStats;

        // onConstructed(); // You can define this method to get a callback
    }

    @DataClass.Generated.Member
    public static final @NonNull Parcelable.Creator<ResourceOveruseStats> CREATOR
            = new Parcelable.Creator<ResourceOveruseStats>() {
        @Override
        public ResourceOveruseStats[] newArray(int size) {
            return new ResourceOveruseStats[size];
        }

        @Override
        public ResourceOveruseStats createFromParcel(@NonNull android.os.Parcel in) {
            return new ResourceOveruseStats(in);
        }
    };

    /**
     * A builder for {@link ResourceOveruseStats}
     * @hide
     */
    @SuppressWarnings("WeakerAccess")
    @DataClass.Generated.Member
    public static final class Builder {

        private @NonNull String mPackageName;
        private @NonNull UserHandle mUserHandle;
        private @Nullable IoOveruseStats mIoOveruseStats;

        private long mBuilderFieldsSet = 0L;

        /**
         * Creates a new Builder.
         *
         * @param packageName
         *   Name of the package, whose stats are recorded in the below fields.
         *
         *   NOTE: For packages that share a UID, the package name will be the shared package name because
         *         the stats are aggregated for all packages under the shared UID.
         * @param userHandle
         *   User handle, whose stats are recorded in the below fields.
         */
        public Builder(
                @NonNull String packageName,
                @NonNull UserHandle userHandle) {
            mPackageName = packageName;
            AnnotationValidations.validate(
                    NonNull.class, null, mPackageName);
            mUserHandle = userHandle;
            AnnotationValidations.validate(
                    NonNull.class, null, mUserHandle);
        }

        /**
         * Name of the package, whose stats are recorded in the below fields.
         *
         * NOTE: For packages that share a UID, the package name will be the shared package name because
         *       the stats are aggregated for all packages under the shared UID.
         */
        @DataClass.Generated.Member
        public @NonNull Builder setPackageName(@NonNull String value) {
            checkNotUsed();
            mBuilderFieldsSet |= 0x1;
            mPackageName = value;
            return this;
        }

        /**
         * User handle, whose stats are recorded in the below fields.
         */
        @DataClass.Generated.Member
        public @NonNull Builder setUserHandle(@NonNull UserHandle value) {
            checkNotUsed();
            mBuilderFieldsSet |= 0x2;
            mUserHandle = value;
            return this;
        }

        @DataClass.Generated.Member
        public @NonNull Builder setIoOveruseStats(@NonNull IoOveruseStats value) {
            checkNotUsed();
            mBuilderFieldsSet |= 0x4;
            mIoOveruseStats = value;
            return this;
        }

        /** Builds the instance. This builder should not be touched after calling this! */
        public @NonNull ResourceOveruseStats build() {
            checkNotUsed();
            mBuilderFieldsSet |= 0x8; // Mark builder used

            if ((mBuilderFieldsSet & 0x4) == 0) {
                mIoOveruseStats = null;
            }
            ResourceOveruseStats o = new ResourceOveruseStats(
                    mPackageName,
                    mUserHandle,
                    mIoOveruseStats);
            return o;
        }

        private void checkNotUsed() {
            if ((mBuilderFieldsSet & 0x8) != 0) {
                throw new IllegalStateException(
                        "This Builder should not be reused. Use a new Builder instance instead");
            }
        }
    }

    @DataClass.Generated(
            time = 1628099343131L,
            codegenVersion = "1.0.23",
            sourceFile = "packages/services/Car/car-lib/src/android/car/watchdog/ResourceOveruseStats.java",
            inputSignatures = "private @android.annotation.NonNull java.lang.String mPackageName\nprivate @android.annotation.NonNull android.os.UserHandle mUserHandle\nprivate @android.annotation.Nullable android.car.watchdog.IoOveruseStats mIoOveruseStats\nclass ResourceOveruseStats extends java.lang.Object implements [android.os.Parcelable]\n@com.android.car.internal.util.DataClass(genToString=true, genHiddenBuilder=true)")
    @Deprecated
    @ExcludeFromCodeCoverageGeneratedReport(reason = BOILERPLATE_CODE)
    private void __metadata() {}


    //@formatter:on
    // End of generated code

}
