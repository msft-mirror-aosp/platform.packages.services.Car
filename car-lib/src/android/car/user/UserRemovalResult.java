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

package android.car.user;

import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.BOILERPLATE_CODE;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.SystemApi;
import android.car.annotation.AddedInOrBefore;
import android.os.Parcelable;

import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.util.DataClass;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * User remove result.
 *
 * @hide
 */
@DataClass(
        genToString = true,
        genHiddenConstructor = true,
        genHiddenConstDefs = true)
@SystemApi
public final class UserRemovalResult implements Parcelable, OperationResult {

    /**
     * When user remove is successful.
     */
    @Status
    @AddedInOrBefore(majorVersion = 33)
    public static final int STATUS_SUCCESSFUL = CommonResults.STATUS_SUCCESSFUL;

    /**
     * When user remove fails for Android. Hal user is not removed.
     */
    @Status
    @AddedInOrBefore(majorVersion = 33)
    public static final int STATUS_ANDROID_FAILURE = CommonResults.STATUS_ANDROID_FAILURE;

    /**
     * When user remove fails due to invalid arguments passed to method. Hal user is not removed.
     */
    @Status
    @AddedInOrBefore(majorVersion = 33)
    public static final int STATUS_INVALID_REQUEST = CommonResults.STATUS_INVALID_REQUEST;

    /**
     * When user to remove doesn't exits.
     */
    @Status
    @AddedInOrBefore(majorVersion = 33)
    public static final int STATUS_USER_DOES_NOT_EXIST = CommonResults.LAST_COMMON_STATUS + 1;

    /**
     * When last admin user successfully removed.
     */
    @Status
    @AddedInOrBefore(majorVersion = 33)
    public static final int STATUS_SUCCESSFUL_LAST_ADMIN_REMOVED =
            CommonResults.LAST_COMMON_STATUS + 2;

    /**
     * When the user is set as ephemeral so that it is scheduled for removal. This occurs when the
     * user can't be immediately removed, such as when the current user is being removed.
     */
    @Status
    @AddedInOrBefore(majorVersion = 33)
    public static final int STATUS_SUCCESSFUL_SET_EPHEMERAL =
            CommonResults.LAST_COMMON_STATUS + 3;

    /**
     * When last admin user has been set as ephemeral so that it is scheduled for removal. This
     * occurs when the user can't be immediately removed, such as when the current user is being
     * removed.
     */
    @Status
    @AddedInOrBefore(majorVersion = 33)
    public static final int STATUS_SUCCESSFUL_LAST_ADMIN_SET_EPHEMERAL =
            CommonResults.LAST_COMMON_STATUS + 4;

    /**
     * Gets the user switch result status.
     *
     * @return either {@link UserRemovalResult#STATUS_SUCCESSFUL},
     *         {@link UserRemovalResult#STATUS_ANDROID_FAILURE},
     *         {@link UserRemovalResult#STATUS_INVALID_REQUEST},
     *         {@link UserRemovalResult#STATUS_USER_DOES_NOT_EXIST}, or
     *         {@link UserRemovalResult#STATUS_SUCCESSFUL_LAST_ADMIN_REMOVED}, or
     *         {@link UserRemovalResult#STATUS_SUCCESSFUL_SET_EPHEMERAL}, or
     *         {@link UserRemovalResult#STATUS_SUCCESSFUL_LAST_ADMIN_SET_EPHEMERAL}.
     */
    private final @Status int mStatus;

    @Override
    @AddedInOrBefore(majorVersion = 33)
    public boolean isSuccess() {
        return mStatus == STATUS_SUCCESSFUL || mStatus == STATUS_SUCCESSFUL_LAST_ADMIN_REMOVED
                || mStatus == STATUS_SUCCESSFUL_SET_EPHEMERAL
                || mStatus == STATUS_SUCCESSFUL_LAST_ADMIN_SET_EPHEMERAL;
    }



    // Code below generated by codegen v1.0.23.
    //
    // DO NOT MODIFY!
    // CHECKSTYLE:OFF Generated code
    //
    // To regenerate run:
    // $ codegen $ANDROID_BUILD_TOP/packages/services/Car/car-lib/src/android/car/user/UserRemovalResult.java
    // Added AddedInOrBefore or ApiRequirement Annotation manually
    //
    // To exclude the generated code from IntelliJ auto-formatting enable (one-time):
    //   Settings > Editor > Code Style > Formatter Control
    //@formatter:off


    /** @hide */
    @IntDef(prefix = "STATUS_", value = {
        STATUS_SUCCESSFUL,
        STATUS_ANDROID_FAILURE,
        STATUS_INVALID_REQUEST,
        STATUS_USER_DOES_NOT_EXIST,
        STATUS_SUCCESSFUL_LAST_ADMIN_REMOVED,
        STATUS_SUCCESSFUL_SET_EPHEMERAL,
        STATUS_SUCCESSFUL_LAST_ADMIN_SET_EPHEMERAL
    })
    @Retention(RetentionPolicy.SOURCE)
    @DataClass.Generated.Member
    public @interface Status {}

    @DataClass.Generated.Member
    @AddedInOrBefore(majorVersion = 33)
    @ExcludeFromCodeCoverageGeneratedReport(reason = BOILERPLATE_CODE)
    @NonNull
    public static String statusToString(@Status int value) {
        switch (value) {
            case STATUS_SUCCESSFUL:
                    return "STATUS_SUCCESSFUL";
            case STATUS_ANDROID_FAILURE:
                    return "STATUS_ANDROID_FAILURE";
            case STATUS_INVALID_REQUEST:
                    return "STATUS_INVALID_REQUEST";
            case STATUS_USER_DOES_NOT_EXIST:
                    return "STATUS_USER_DOES_NOT_EXIST";
            case STATUS_SUCCESSFUL_LAST_ADMIN_REMOVED:
                    return "STATUS_SUCCESSFUL_LAST_ADMIN_REMOVED";
            case STATUS_SUCCESSFUL_SET_EPHEMERAL:
                    return "STATUS_SUCCESSFUL_SET_EPHEMERAL";
            case STATUS_SUCCESSFUL_LAST_ADMIN_SET_EPHEMERAL:
                    return "STATUS_SUCCESSFUL_LAST_ADMIN_SET_EPHEMERAL";
            default: return Integer.toHexString(value);
        }
    }

    /**
     * Creates a new UserRemovalResult.
     *
     * @param status
     *   Gets the user switch result status.
     *
     *   @return either {@link UserRemovalResult#STATUS_SUCCESSFUL},
     *           {@link UserRemovalResult#STATUS_ANDROID_FAILURE},
     *           {@link UserRemovalResult#STATUS_INVALID_REQUEST},
     *           {@link UserRemovalResult#STATUS_USER_DOES_NOT_EXIST}, or
     *           {@link UserRemovalResult#STATUS_SUCCESSFUL_LAST_ADMIN_REMOVED}, or
     *           {@link UserRemovalResult#STATUS_SUCCESSFUL_SET_EPHEMERAL}, or
     *           {@link UserRemovalResult#STATUS_SUCCESSFUL_LAST_ADMIN_SET_EPHEMERAL}.
     * @hide
     */
    @DataClass.Generated.Member
    public UserRemovalResult(
            @Status int status) {
        this.mStatus = status;

        if (!(mStatus == STATUS_SUCCESSFUL)
                && !(mStatus == STATUS_ANDROID_FAILURE)
                && !(mStatus == STATUS_INVALID_REQUEST)
                && !(mStatus == STATUS_USER_DOES_NOT_EXIST)
                && !(mStatus == STATUS_SUCCESSFUL_LAST_ADMIN_REMOVED)
                && !(mStatus == STATUS_SUCCESSFUL_SET_EPHEMERAL)
                && !(mStatus == STATUS_SUCCESSFUL_LAST_ADMIN_SET_EPHEMERAL)) {
            throw new java.lang.IllegalArgumentException(
                    "status was " + mStatus + " but must be one of: "
                            + "STATUS_SUCCESSFUL(" + STATUS_SUCCESSFUL + "), "
                            + "STATUS_ANDROID_FAILURE(" + STATUS_ANDROID_FAILURE + "), "
                            + "STATUS_INVALID_REQUEST(" + STATUS_INVALID_REQUEST + "), "
                            + "STATUS_USER_DOES_NOT_EXIST(" + STATUS_USER_DOES_NOT_EXIST + "), "
                            + "STATUS_SUCCESSFUL_LAST_ADMIN_REMOVED(" + STATUS_SUCCESSFUL_LAST_ADMIN_REMOVED + "), "
                            + "STATUS_SUCCESSFUL_SET_EPHEMERAL(" + STATUS_SUCCESSFUL_SET_EPHEMERAL + "), "
                            + "STATUS_SUCCESSFUL_LAST_ADMIN_SET_EPHEMERAL(" + STATUS_SUCCESSFUL_LAST_ADMIN_SET_EPHEMERAL + ")");
        }


        // onConstructed(); // You can define this method to get a callback
    }

    /**
     * Gets the user switch result status.
     *
     * @return either {@link UserRemovalResult#STATUS_SUCCESSFUL},
     *         {@link UserRemovalResult#STATUS_ANDROID_FAILURE},
     *         {@link UserRemovalResult#STATUS_INVALID_REQUEST},
     *         {@link UserRemovalResult#STATUS_USER_DOES_NOT_EXIST}, or
     *         {@link UserRemovalResult#STATUS_SUCCESSFUL_LAST_ADMIN_REMOVED}, or
     *         {@link UserRemovalResult#STATUS_SUCCESSFUL_SET_EPHEMERAL}, or
     *         {@link UserRemovalResult#STATUS_SUCCESSFUL_LAST_ADMIN_SET_EPHEMERAL}.
     */
    @DataClass.Generated.Member
    @AddedInOrBefore(majorVersion = 33)
    public @Status int getStatus() {
        return mStatus;
    }

    @Override
    @DataClass.Generated.Member
    public String toString() {
        // You can override field toString logic by defining methods like:
        // String fieldNameToString() { ... }

        return "UserRemovalResult { " +
                "status = " + statusToString(mStatus) +
        " }";
    }

    @Override
    @DataClass.Generated.Member
    @AddedInOrBefore(majorVersion = 33)
    public void writeToParcel(@android.annotation.NonNull android.os.Parcel dest, int flags) {
        // You can override field parcelling by defining methods like:
        // void parcelFieldName(Parcel dest, int flags) { ... }

        dest.writeInt(mStatus);
    }

    @Override
    @DataClass.Generated.Member
    @ExcludeFromCodeCoverageGeneratedReport(reason = BOILERPLATE_CODE)
    @AddedInOrBefore(majorVersion = 33)
    public int describeContents() { return 0; }

    /** @hide */
    @SuppressWarnings({"unchecked", "RedundantCast"})
    @DataClass.Generated.Member
    /* package-private */ UserRemovalResult(@android.annotation.NonNull android.os.Parcel in) {
        // You can override field unparcelling by defining methods like:
        // static FieldType unparcelFieldName(Parcel in) { ... }

        int status = in.readInt();

        this.mStatus = status;

        if (!(mStatus == STATUS_SUCCESSFUL)
                && !(mStatus == STATUS_ANDROID_FAILURE)
                && !(mStatus == STATUS_INVALID_REQUEST)
                && !(mStatus == STATUS_USER_DOES_NOT_EXIST)
                && !(mStatus == STATUS_SUCCESSFUL_LAST_ADMIN_REMOVED)
                && !(mStatus == STATUS_SUCCESSFUL_SET_EPHEMERAL)
                && !(mStatus == STATUS_SUCCESSFUL_LAST_ADMIN_SET_EPHEMERAL)) {
            throw new java.lang.IllegalArgumentException(
                    "status was " + mStatus + " but must be one of: "
                            + "STATUS_SUCCESSFUL(" + STATUS_SUCCESSFUL + "), "
                            + "STATUS_ANDROID_FAILURE(" + STATUS_ANDROID_FAILURE + "), "
                            + "STATUS_INVALID_REQUEST(" + STATUS_INVALID_REQUEST + "), "
                            + "STATUS_USER_DOES_NOT_EXIST(" + STATUS_USER_DOES_NOT_EXIST + "), "
                            + "STATUS_SUCCESSFUL_LAST_ADMIN_REMOVED(" + STATUS_SUCCESSFUL_LAST_ADMIN_REMOVED + "), "
                            + "STATUS_SUCCESSFUL_SET_EPHEMERAL(" + STATUS_SUCCESSFUL_SET_EPHEMERAL + "), "
                            + "STATUS_SUCCESSFUL_LAST_ADMIN_SET_EPHEMERAL(" + STATUS_SUCCESSFUL_LAST_ADMIN_SET_EPHEMERAL + ")");
        }


        // onConstructed(); // You can define this method to get a callback
    }

    @DataClass.Generated.Member
    @AddedInOrBefore(majorVersion = 33)
    public static final @android.annotation.NonNull Parcelable.Creator<UserRemovalResult> CREATOR
            = new Parcelable.Creator<UserRemovalResult>() {
        @Override
        public UserRemovalResult[] newArray(int size) {
            return new UserRemovalResult[size];
        }

        @Override
        public UserRemovalResult createFromParcel(@android.annotation.NonNull android.os.Parcel in) {
            return new UserRemovalResult(in);
        }
    };

    @DataClass.Generated(
            time = 1671745402351L,
            codegenVersion = "1.0.23",
            sourceFile = "packages/services/Car/car-lib/src/android/car/user/UserRemovalResult.java",
            inputSignatures = "public static final @android.car.user.UserRemovalResult.Status @android.car.annotation.AddedInOrBefore int STATUS_SUCCESSFUL\npublic static final @android.car.user.UserRemovalResult.Status @android.car.annotation.AddedInOrBefore int STATUS_ANDROID_FAILURE\npublic static final @android.car.user.UserRemovalResult.Status @android.car.annotation.AddedInOrBefore int STATUS_INVALID_REQUEST\npublic static final @android.car.user.UserRemovalResult.Status @android.car.annotation.AddedInOrBefore int STATUS_USER_DOES_NOT_EXIST\npublic static final @android.car.user.UserRemovalResult.Status @android.car.annotation.AddedInOrBefore int STATUS_SUCCESSFUL_LAST_ADMIN_REMOVED\npublic static final @android.car.user.UserRemovalResult.Status @android.car.annotation.AddedInOrBefore int STATUS_SUCCESSFUL_SET_EPHEMERAL\npublic static final @android.car.user.UserRemovalResult.Status @android.car.annotation.AddedInOrBefore int STATUS_SUCCESSFUL_LAST_ADMIN_SET_EPHEMERAL\nprivate final @android.car.user.UserRemovalResult.Status int mStatus\npublic @java.lang.Override @android.car.annotation.AddedInOrBefore boolean isSuccess()\nclass UserRemovalResult extends java.lang.Object implements [android.os.Parcelable, android.car.user.OperationResult]\n@com.android.car.internal.util.DataClass(genToString=true, genHiddenConstructor=true, genHiddenConstDefs=true)")
    @Deprecated
    private void __metadata() {}


    //@formatter:on
    // End of generated code

}
