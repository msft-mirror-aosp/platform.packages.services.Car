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
import android.annotation.Nullable;
import android.annotation.SystemApi;
import android.car.annotation.ApiRequirements;
import android.os.Parcelable;

import com.android.car.internal.util.DataClass;

import java.util.Objects;

/**
 * User creation request.
 *
 * @hide
 */
@DataClass(
        genParcelable = true,
        genConstructor = false,
        genAidl = true)
@SystemApi
public final class UserCreationRequest implements Parcelable {

    private final @Nullable String mName;
    private final boolean mAdmin;
    private final boolean mGuest;
    private final boolean mEphemeral;

    private UserCreationRequest(Builder builder) {
        this.mName = builder.mName;
        this.mAdmin = builder.mAdmin;
        this.mGuest = builder.mGuest;
        this.mEphemeral = builder.mEphemeral;
    }

    /** Builder for {@link UserCreationRequest}. */
    public static final class Builder {
        private String mName;
        private boolean mAdmin;
        private boolean mGuest;
        private boolean mEphemeral;

        /**
         * Sets user name.
         */
        @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.UPSIDE_DOWN_CAKE_0,
                minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
        public @NonNull Builder setName(@NonNull String name) {
            mName = Objects.requireNonNull(name, "Name should not be null.");
            return this;
        }

        /**
         * Sets user as an admin user.
         */
        @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.UPSIDE_DOWN_CAKE_0,
                minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
        public @NonNull Builder setAdmin() {
            mAdmin = true;
            return this;
        }

        /**
         * Sets user as a guest user.
         */
        @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.UPSIDE_DOWN_CAKE_0,
                minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
        public @NonNull Builder setGuest() {
            mGuest = true;
            return this;
        }

        /**
         * Sets user as a Ephemeral user.
         */
        @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.UPSIDE_DOWN_CAKE_0,
                minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
        public @NonNull Builder setEphemeral() {
            mEphemeral = true;
            return this;
        }

        /** Builds and returns a {@link UserCreationRequest}. */
        @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.UPSIDE_DOWN_CAKE_0,
                minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
        public @NonNull UserCreationRequest build() {
            if (mGuest && mAdmin) {
                // Guest can't be admin user.
                throw new IllegalArgumentException("Guest user can't be admin");
            }

            return new UserCreationRequest(this);
        }
    }




    // Code below generated by codegen v1.0.23.
    //
    // DO NOT MODIFY!
    // CHECKSTYLE:OFF Generated code
    //
    // To regenerate run:
    // $ codegen $ANDROID_BUILD_TOP/packages/services/Car/car-lib/src/android/car/user/UserCreationRequest.java
    //
    // To exclude the generated code from IntelliJ auto-formatting enable (one-time):
    //   Settings > Editor > Code Style > Formatter Control
    //@formatter:off


    @DataClass.Generated.Member
    @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.UPSIDE_DOWN_CAKE_0,
            minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
    public @Nullable String getName() {
        return mName;
    }

    @DataClass.Generated.Member
    @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.UPSIDE_DOWN_CAKE_0,
            minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
    public boolean isAdmin() {
        return mAdmin;
    }

    @DataClass.Generated.Member
    @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.UPSIDE_DOWN_CAKE_0,
            minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
    public boolean isGuest() {
        return mGuest;
    }

    @DataClass.Generated.Member
    @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.UPSIDE_DOWN_CAKE_0,
            minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
    public boolean isEphemeral() {
        return mEphemeral;
    }

    @Override
    @DataClass.Generated.Member
    @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.UPSIDE_DOWN_CAKE_0,
            minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
    public void writeToParcel(@NonNull android.os.Parcel dest, int flags) {
        // You can override field parcelling by defining methods like:
        // void parcelFieldName(Parcel dest, int flags) { ... }

        byte flg = 0;
        if (mAdmin) flg |= 0x2;
        if (mGuest) flg |= 0x4;
        if (mEphemeral) flg |= 0x8;
        if (mName != null) flg |= 0x1;
        dest.writeByte(flg);
        if (mName != null) dest.writeString(mName);
    }

    @Override
    @DataClass.Generated.Member
    @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.UPSIDE_DOWN_CAKE_0,
            minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
    public int describeContents() { return 0; }

    /** @hide */
    @SuppressWarnings({"unchecked", "RedundantCast"})
    @DataClass.Generated.Member
    /* package-private */ UserCreationRequest(@NonNull android.os.Parcel in) {
        // You can override field unparcelling by defining methods like:
        // static FieldType unparcelFieldName(Parcel in) { ... }

        byte flg = in.readByte();
        boolean admin = (flg & 0x2) != 0;
        boolean guest = (flg & 0x4) != 0;
        boolean ephemeral = (flg & 0x8) != 0;
        String name = (flg & 0x1) == 0 ? null : in.readString();

        this.mName = name;
        this.mAdmin = admin;
        this.mGuest = guest;
        this.mEphemeral = ephemeral;

        // onConstructed(); // You can define this method to get a callback
    }

    @DataClass.Generated.Member
    @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.UPSIDE_DOWN_CAKE_0,
            minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
    public static final @NonNull Parcelable.Creator<UserCreationRequest> CREATOR
            = new Parcelable.Creator<UserCreationRequest>() {
        @Override
        public UserCreationRequest[] newArray(int size) {
            return new UserCreationRequest[size];
        }

        @Override
        public UserCreationRequest createFromParcel(@NonNull android.os.Parcel in) {
            return new UserCreationRequest(in);
        }
    };

    @DataClass.Generated(
            time = 1677736413834L,
            codegenVersion = "1.0.23",
            sourceFile = "packages/services/Car/car-lib/src/android/car/user/UserCreationRequest.java",
            inputSignatures = "private final @android.annotation.Nullable java.lang.String mName\nprivate final  boolean mAdmin\nprivate final  boolean mGuest\nprivate final  boolean mEphemeral\nclass UserCreationRequest extends java.lang.Object implements [android.os.Parcelable]\nprivate  java.lang.String mName\nprivate  boolean mAdmin\nprivate  boolean mGuest\nprivate  boolean mEphemeral\npublic @android.car.annotation.ApiRequirements @android.annotation.NonNull android.car.user.UserCreationRequest.Builder setName(java.lang.String)\npublic @android.car.annotation.ApiRequirements @android.annotation.NonNull android.car.user.UserCreationRequest.Builder setAdmin()\npublic @android.car.annotation.ApiRequirements @android.annotation.NonNull android.car.user.UserCreationRequest.Builder setGuest()\npublic @android.car.annotation.ApiRequirements @android.annotation.NonNull android.car.user.UserCreationRequest.Builder setEphemeral()\npublic @android.car.annotation.ApiRequirements @android.annotation.NonNull android.car.user.UserCreationRequest build()\nclass Builder extends java.lang.Object implements []\n@com.android.car.internal.util.DataClass(genParcelable=true, genConstructor=false, genAidl=true)")
    @Deprecated
    private void __metadata() {}


    //@formatter:on
    // End of generated code

}
