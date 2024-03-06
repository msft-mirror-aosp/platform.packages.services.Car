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

package android.car.user;

import android.annotation.NonNull;
import android.annotation.SystemApi;
import android.os.Parcelable;
import android.os.UserHandle;

import com.android.car.internal.util.DataClass;

/**
 * User stop request.
 *
 * @hide
 */
@DataClass(
        genParcelable = true,
        genConstructor = false,
        genAidl = true)
@SystemApi
public final class UserStopRequest implements Parcelable {

    private final @NonNull UserHandle mUserHandle;
    private final boolean mWithDelayedLocking;
    private final boolean mForce;

    /** Builder for {@link UserStopRequest}. */
    public static final class Builder {
        private final @NonNull UserHandle mUserHandle;
        // withDelayedLocking is true by default, as it is the most common use case.
        private boolean mWithDelayedLocking = true;
        private boolean mForce;

        public Builder(@NonNull UserHandle userHandle) {
            com.android.car.internal.util.AnnotationValidations.validate(
                    NonNull.class, /* ignored= */ null, userHandle);
            mUserHandle = userHandle;
        }

        /**
         * Sets the flag to stop the user with delayed locking.
         *
         * <p>The flag is {@code true} by default.
         */
        public @NonNull Builder withDelayedLocking(boolean value) {
            mWithDelayedLocking = value;
            return this;
        }

        /**
         * Sets the flag to force-stop the user.
         *
         * <p>The flag is {@code false} by default.
         */
        public @NonNull Builder setForce() {
            mForce = true;
            return this;
        }

        /** Builds and returns a {@link UserStopRequest}. */
        public @NonNull UserStopRequest build() {
            return new UserStopRequest(this);
        }
    }

    private UserStopRequest(Builder builder) {
        mUserHandle = builder.mUserHandle;
        mWithDelayedLocking = builder.mWithDelayedLocking;
        mForce = builder.mForce;
    }



    // Code below generated by codegen v1.0.23.
    //
    // DO NOT MODIFY!
    // CHECKSTYLE:OFF Generated code
    //
    // To regenerate run:
    // $ codegen $ANDROID_BUILD_TOP/packages/services/Car/car-lib/src/android/car/user/UserStopRequest.java
    //
    // To exclude the generated code from IntelliJ auto-formatting enable (one-time):
    //   Settings > Editor > Code Style > Formatter Control
    //@formatter:off


    @DataClass.Generated.Member
    public @NonNull UserHandle getUserHandle() {
        return mUserHandle;
    }

    @DataClass.Generated.Member
    public boolean isWithDelayedLocking() {
        return mWithDelayedLocking;
    }

    @DataClass.Generated.Member
    public boolean isForce() {
        return mForce;
    }

    @Override
    @DataClass.Generated.Member
    public void writeToParcel(@NonNull android.os.Parcel dest, int flags) {
        // You can override field parcelling by defining methods like:
        // void parcelFieldName(Parcel dest, int flags) { ... }

        byte flg = 0;
        if (mWithDelayedLocking) flg |= 0x2;
        if (mForce) flg |= 0x4;
        dest.writeByte(flg);
        dest.writeTypedObject(mUserHandle, flags);
    }

    @Override
    @DataClass.Generated.Member
    public int describeContents() { return 0; }

    /** @hide */
    @SuppressWarnings({"unchecked", "RedundantCast"})
    @DataClass.Generated.Member
    /* package-private */ UserStopRequest(@NonNull android.os.Parcel in) {
        // You can override field unparcelling by defining methods like:
        // static FieldType unparcelFieldName(Parcel in) { ... }

        byte flg = in.readByte();
        boolean withDelayedLocking = (flg & 0x2) != 0;
        boolean force = (flg & 0x4) != 0;
        UserHandle userHandle = (UserHandle) in.readTypedObject(UserHandle.CREATOR);

        this.mUserHandle = userHandle;
        com.android.car.internal.util.AnnotationValidations.validate(
                NonNull.class, null, mUserHandle);
        this.mWithDelayedLocking = withDelayedLocking;
        this.mForce = force;

        // onConstructed(); // You can define this method to get a callback
    }

    @DataClass.Generated.Member
    public static final @NonNull Parcelable.Creator<UserStopRequest> CREATOR
            = new Parcelable.Creator<UserStopRequest>() {
        @Override
        public UserStopRequest[] newArray(int size) {
            return new UserStopRequest[size];
        }

        @Override
        public UserStopRequest createFromParcel(@NonNull android.os.Parcel in) {
            return new UserStopRequest(in);
        }
    };

    @DataClass.Generated(
            time = 1679068284217L,
            codegenVersion = "1.0.23",
            sourceFile = "packages/services/Car/car-lib/src/android/car/user/UserStopRequest.java",
            inputSignatures = "private final @android.annotation.NonNull android.os.UserHandle mUserHandle\nprivate final  boolean mWithDelayedLocking\nprivate final  boolean mForce\nclass UserStopRequest extends java.lang.Object implements [android.os.Parcelable]\nprivate final @android.annotation.NonNull android.os.UserHandle mUserHandle\nprivate  boolean mWithDelayedLocking\nprivate  boolean mForce\npublic @android.car.annotation.ApiRequirements @android.annotation.NonNull android.car.user.UserStopRequest.Builder withDelayedLocking()\npublic @android.car.annotation.ApiRequirements @android.annotation.NonNull android.car.user.UserStopRequest.Builder setForce()\npublic @android.car.annotation.ApiRequirements @android.annotation.NonNull android.car.user.UserStopRequest build()\nclass Builder extends java.lang.Object implements []\n@com.android.car.internal.util.DataClass(genParcelable=true, genConstructor=false, genAidl=true)")
    @Deprecated
    private void __metadata() {}


    //@formatter:on
    // End of generated code

}
