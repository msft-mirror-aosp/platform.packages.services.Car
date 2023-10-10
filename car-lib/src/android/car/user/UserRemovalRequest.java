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
import android.car.annotation.ApiRequirements;
import android.os.Parcelable;
import android.os.UserHandle;

import com.android.car.internal.util.AnnotationValidations;
import com.android.car.internal.util.DataClass;

import java.util.Objects;

/**
 * User removal request.
 *
 * @hide
 */
@DataClass(
        genParcelable = true,
        genConstructor = false,
        genAidl = true)
@SystemApi
public final class UserRemovalRequest implements Parcelable {

    private final @NonNull UserHandle mUserHandle;

    private UserRemovalRequest(Builder builder) {
        this.mUserHandle = builder.mUserHandle;
    }

    /** Builder for {@link UserRemovalRequest}. */
    public static final class Builder {
        private final @NonNull UserHandle mUserHandle;

        public Builder(@NonNull UserHandle userHandle) {
            mUserHandle = Objects.requireNonNull(userHandle, "User Handle is null");
        }

        /** Builds and returns a {@link UserRemovalRequest}. */
        @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.UPSIDE_DOWN_CAKE_0,
                minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
        public @NonNull UserRemovalRequest build() {
            return new UserRemovalRequest(this);
        }
    }

    @Override
    public String toString() {
        return "UserRemovalRequest { " + mUserHandle + " }";
    }



    // Code below generated by codegen v1.0.23.
    //
    // DO NOT MODIFY!
    // CHECKSTYLE:OFF Generated code
    //
    // To regenerate run:
    // $ codegen $ANDROID_BUILD_TOP/packages/services/Car/car-lib/src/android/car/user/UserRemovalRequest.java
    //
    // To exclude the generated code from IntelliJ auto-formatting enable (one-time):
    //   Settings > Editor > Code Style > Formatter Control
    //@formatter:off


    @DataClass.Generated.Member
    @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.UPSIDE_DOWN_CAKE_0,
            minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
    public @NonNull UserHandle getUserHandle() {
        return mUserHandle;
    }

    @Override
    @DataClass.Generated.Member
    @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.UPSIDE_DOWN_CAKE_0,
            minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
    public void writeToParcel(@NonNull android.os.Parcel dest, int flags) {
        // You can override field parcelling by defining methods like:
        // void parcelFieldName(Parcel dest, int flags) { ... }

        dest.writeTypedObject(mUserHandle, flags);
    }

    @Override
    @DataClass.Generated.Member
    @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.UPSIDE_DOWN_CAKE_0,
            minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
    public int describeContents() { return 0; }

    /** @hide */
    @SuppressWarnings({"unchecked", "RedundantCast"})
    @DataClass.Generated.Member
    protected UserRemovalRequest(@NonNull android.os.Parcel in) {
        // You can override field unparcelling by defining methods like:
        // static FieldType unparcelFieldName(Parcel in) { ... }

        UserHandle userHandle = (UserHandle) in.readTypedObject(UserHandle.CREATOR);

        this.mUserHandle = userHandle;
        AnnotationValidations.validate(
                NonNull.class, null, mUserHandle);

        // onConstructed(); // You can define this method to get a callback
    }

    @DataClass.Generated.Member
    @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.UPSIDE_DOWN_CAKE_0,
            minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
    public static final @NonNull Parcelable.Creator<UserRemovalRequest> CREATOR
            = new Parcelable.Creator<UserRemovalRequest>() {
        @Override
        @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.UPSIDE_DOWN_CAKE_0,
                minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
        public UserRemovalRequest[] newArray(int size) {
            return new UserRemovalRequest[size];
        }

        @Override
        @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.UPSIDE_DOWN_CAKE_0,
                minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
        public UserRemovalRequest createFromParcel(@NonNull android.os.Parcel in) {
            return new UserRemovalRequest(in);
        }
    };

    @DataClass.Generated(
            time = 1676675483517L,
            codegenVersion = "1.0.23",
            sourceFile = "packages/services/Car/car-lib/src/android/car/user/UserRemovalRequest.java",
            inputSignatures = "private final @android.annotation.NonNull android.os.UserHandle mUserHandle\nclass UserRemovalRequest extends java.lang.Object implements [android.os.Parcelable]\nprivate final @android.annotation.NonNull android.os.UserHandle mUserHandle\npublic @android.car.annotation.ApiRequirements @android.annotation.NonNull android.car.user.UserRemovalRequest build()\nclass Builder extends java.lang.Object implements []\n@com.android.car.internal.util.DataClass(genParcelable=true, genConstructor=false, genAidl=true)")
    @Deprecated
    private void __metadata() {}


    //@formatter:on
    // End of generated code

}
