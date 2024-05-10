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

package com.android.car.internal.property;

import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.BOILERPLATE_CODE;

import android.annotation.Nullable;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.property.CarPropertyManager.GetPropertyRequest;
import android.car.hardware.property.CarPropertyManager.SetPropertyRequest;
import android.os.Parcelable;

import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.util.DataClass;

/**
 * A request for {@link CarPropertyService.getPropertiesAsync} or
 * {@link CarPropertyService.setPropertiesAsync}
 *
 * @hide
 */
@DataClass(genConstructor = false)
@ExcludeFromCodeCoverageGeneratedReport(reason = BOILERPLATE_CODE)
public final class AsyncPropertyServiceRequest implements Parcelable {
    private final int mRequestId;
    private final int mPropertyId;
    private final int mAreaId;
    // The property value to set. Ignored for get request.
    @Nullable
    private final CarPropertyValue mCarPropertyValue;
    // The update rate in HZ for listening to new property update event for async set. Ignored for
    // get request.
    private float mUpdateRateHz;
    // Whether to listen for property update event before calling success callback for async set.
    // Ignored for get request.
    private boolean mWaitForPropertyUpdate;

    /**
     * Creates an async get request.
     */
    public static AsyncPropertyServiceRequest newGetAsyncRequest(
            GetPropertyRequest getPropertyRequest) {
        return new AsyncPropertyServiceRequest(getPropertyRequest.getRequestId(),
                getPropertyRequest.getPropertyId(), getPropertyRequest.getAreaId());
    }

    /**
     * Creates an async set request.
     */
    public static AsyncPropertyServiceRequest newSetAsyncRequest(
            SetPropertyRequest setPropertyRequest) {
        int propertyId = setPropertyRequest.getPropertyId();
        int areaId = setPropertyRequest.getAreaId();
        AsyncPropertyServiceRequest request = new AsyncPropertyServiceRequest(
                setPropertyRequest.getRequestId(), propertyId, areaId,
                new CarPropertyValue(propertyId, areaId, setPropertyRequest.getValue()));
        request.setUpdateRateHz(setPropertyRequest.getUpdateRateHz());
        request.setWaitForPropertyUpdate(setPropertyRequest.isWaitForPropertyUpdate());
        return request;
    }

    /**
     * Creates an async get request, for test only.
     */
    public AsyncPropertyServiceRequest(int requestId, int propertyId, int areaId) {
        this(requestId, propertyId, areaId, /* carPropertyValue= */ null);
    }

    /**
     * Creates an async set request, for test only.
     */
    public AsyncPropertyServiceRequest(int requestId, int propertyId, int areaId,
            @Nullable CarPropertyValue carPropertyValue) {
        mRequestId = requestId;
        mPropertyId = propertyId;
        mAreaId = areaId;
        mCarPropertyValue = carPropertyValue;
        mWaitForPropertyUpdate = true;
    }

    /**
     * Sets the update rate in HZ for listening to new property update event.
     */
    public void setUpdateRateHz(float updateRateHz) {
        mUpdateRateHz = updateRateHz;
    }

    /**
     * Sets whether to wait for property update before calling async set's success callback.
     */
    public void setWaitForPropertyUpdate(boolean waitForPropertyUpdate) {
        mWaitForPropertyUpdate = waitForPropertyUpdate;
    }



    // Code below generated by codegen v1.0.23.
    //
    // DO NOT MODIFY!
    // CHECKSTYLE:OFF Generated code
    //
    // To regenerate run:
    // $ codegen $ANDROID_BUILD_TOP/packages/services/Car/car-lib/src/com/android/car/internal/property/AsyncPropertyServiceRequest.java
    //
    // To exclude the generated code from IntelliJ auto-formatting enable (one-time):
    //   Settings > Editor > Code Style > Formatter Control
    //@formatter:off

    @DataClass.Generated.Member
    public int getRequestId() {
        return mRequestId;
    }

    @DataClass.Generated.Member
    public int getPropertyId() {
        return mPropertyId;
    }

    @DataClass.Generated.Member
    public int getAreaId() {
        return mAreaId;
    }

    @DataClass.Generated.Member
    public @Nullable CarPropertyValue getCarPropertyValue() {
        return mCarPropertyValue;
    }

    @DataClass.Generated.Member
    public float getUpdateRateHz() {
        return mUpdateRateHz;
    }

    @DataClass.Generated.Member
    public boolean isWaitForPropertyUpdate() {
        return mWaitForPropertyUpdate;
    }

    @Override
    @DataClass.Generated.Member
    public void writeToParcel(@android.annotation.NonNull android.os.Parcel dest, int flags) {
        // You can override field parcelling by defining methods like:
        // void parcelFieldName(Parcel dest, int flags) { ... }

        byte flg = 0;
        if (mWaitForPropertyUpdate) flg |= 0x20;
        if (mCarPropertyValue != null) flg |= 0x8;
        dest.writeByte(flg);
        dest.writeInt(mRequestId);
        dest.writeInt(mPropertyId);
        dest.writeInt(mAreaId);
        if (mCarPropertyValue != null) dest.writeTypedObject(mCarPropertyValue, flags);
        dest.writeFloat(mUpdateRateHz);
    }

    @Override
    @DataClass.Generated.Member
    public int describeContents() { return 0; }

    /** @hide */
    @SuppressWarnings({"unchecked", "RedundantCast"})
    @DataClass.Generated.Member
    /* package-private */ AsyncPropertyServiceRequest(@android.annotation.NonNull android.os.Parcel in) {
        // You can override field unparcelling by defining methods like:
        // static FieldType unparcelFieldName(Parcel in) { ... }

        byte flg = in.readByte();
        boolean waitForPropertyUpdate = (flg & 0x20) != 0;
        int requestId = in.readInt();
        int propertyId = in.readInt();
        int areaId = in.readInt();
        CarPropertyValue carPropertyValue = (flg & 0x8) == 0 ? null : (CarPropertyValue) in.readTypedObject(CarPropertyValue.CREATOR);
        float updateRateHz = in.readFloat();

        this.mRequestId = requestId;
        this.mPropertyId = propertyId;
        this.mAreaId = areaId;
        this.mCarPropertyValue = carPropertyValue;
        this.mUpdateRateHz = updateRateHz;
        this.mWaitForPropertyUpdate = waitForPropertyUpdate;

        // onConstructed(); // You can define this method to get a callback
    }

    @DataClass.Generated.Member
    public static final @android.annotation.NonNull Parcelable.Creator<AsyncPropertyServiceRequest> CREATOR
            = new Parcelable.Creator<AsyncPropertyServiceRequest>() {
        @Override
        public AsyncPropertyServiceRequest[] newArray(int size) {
            return new AsyncPropertyServiceRequest[size];
        }

        @Override
        public AsyncPropertyServiceRequest createFromParcel(@android.annotation.NonNull android.os.Parcel in) {
            return new AsyncPropertyServiceRequest(in);
        }
    };

    @DataClass.Generated(
            time = 1678739854323L,
            codegenVersion = "1.0.23",
            sourceFile = "packages/services/Car/car-lib/src/com/android/car/internal/property/AsyncPropertyServiceRequest.java",
            inputSignatures = "private final  int mRequestId\nprivate final  int mPropertyId\nprivate final  int mAreaId\nprivate final @android.annotation.Nullable android.car.hardware.CarPropertyValue mCarPropertyValue\nprivate  float mUpdateRateHz\nprivate  boolean mWaitForPropertyUpdate\npublic static  com.android.car.internal.property.AsyncPropertyServiceRequest newGetAsyncRequest(android.car.hardware.Property.GetPropertyRequest)\npublic static  com.android.car.internal.property.AsyncPropertyServiceRequest newSetAsyncRequest(setPropertyRequest)\npublic  void setUpdateRateHz(float)\npublic  void setWaitForPropertyUpdate(boolean)\nclass AsyncPropertyServiceRequest extends java.lang.Object implements [android.os.Parcelable]\n@com.android.car.internal.util.DataClass(genConstructor=false)")
    @Deprecated
    private void __metadata() {}


    //@formatter:on
    // End of generated code

}
