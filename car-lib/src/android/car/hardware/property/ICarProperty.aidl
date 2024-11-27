/*
 * Copyright (C) 2016 The Android Open Source Project
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

package android.car.hardware.property;

import android.car.hardware.CarPropertyConfig;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.property.ICarPropertyEventListener;

import com.android.car.internal.property.AsyncPropertyServiceRequest;
import com.android.car.internal.property.AsyncPropertyServiceRequestList;
import com.android.car.internal.property.CarPropertyConfigList;
import com.android.car.internal.property.CarSubscription;
import com.android.car.internal.property.GetPropertyConfigListResult;
import com.android.car.internal.property.IAsyncPropertyResultCallback;
import com.android.car.internal.property.ISupportedValuesChangeCallback;
import com.android.car.internal.property.MinMaxSupportedPropertyValue;
import com.android.car.internal.property.PropIdAreaId;
import com.android.car.internal.property.RawPropertyValue;

/**
 * @hide
 */
interface ICarProperty {

    void registerListener(in List<CarSubscription> carSubscription,
                in ICarPropertyEventListener callback);

    void unregisterListener(int propId, in ICarPropertyEventListener callback);

    CarPropertyConfigList getPropertyList();

    CarPropertyValue getProperty(int prop, int zone);

    void setProperty(in CarPropertyValue prop, in ICarPropertyEventListener callback);

    String getReadPermission(int propId);

    String getWritePermission(int propId);

    GetPropertyConfigListResult getPropertyConfigList(in int[] propIds);

    /**
     * Gets CarPropertyValues asynchronously.
     */
    void getPropertiesAsync(in AsyncPropertyServiceRequestList asyncPropertyServiceRequests,
                in IAsyncPropertyResultCallback asyncPropertyResultCallback,
                long timeoutInMs);

    /**
     * Cancel on-going async requests.
     *
     * @param serviceRequestIds A list of async get/set property request IDs.
     */
    void cancelRequests(in int[] serviceRequestIds);

    /**
     * Sets CarPropertyValues asynchronously.
     */
    void setPropertiesAsync(in AsyncPropertyServiceRequestList asyncPropertyServiceRequests,
                in IAsyncPropertyResultCallback asyncPropertyResultCallback,
                long timeoutInMs);

    /**
     * Returns the property IDs that are supported but the caller does not have read permission for.
     */
    int[] getSupportedNoReadPermPropIds(in int[] propIds);

    /**
     * Returns whether the property is supported and the caller only has write permission, but
     * no read permission for the property.
     */
    boolean isSupportedAndHasWritePermissionOnly(int propId);

    /**
     * Gets the current values for [propId, areaId]s and deliver them to the client as
     * initial value events.
     */
    void getAndDispatchInitialValue(in List<PropIdAreaId> propIdAreaIds,
            in ICarPropertyEventListener callback);

    /**
     * Gets the currently min/max supported value.
     *
     * @return The currently supported min/max value.
     * @throws IllegalArgumentException if [propertyId, areaId] is not supported.
     * @throws SecurityException if the caller does not have read and does not have write access
     *      for the property.
     * @throws ServiceSpecificException If VHAL returns error.
     */
    MinMaxSupportedPropertyValue getMinMaxSupportedValue(int propertyId, int areaId);

    /**
     * Gets the currently supported values list.
     *
     * <p>The returned supported value list is in sorted ascending order if the property is of
     * type int32, int64 or float.
     *
     * @return The currently supported values list.
     * @throws IllegalArgumentException if [propertyId, areaId] is not supported.
     * @throws SecurityException if the caller does not have read and does not have write access
     *      for the property.
     * @throws ServiceSpecificException If VHAL returns error.
     */
    @nullable List<RawPropertyValue> getSupportedValuesList(int propertyId, int areaId);

    /**
     * Registers the callback to be called when the min/max supported value or supported values
     * list change.
     *
     * @throws IllegalArgumentException if one of the [propertyId, areaId]s are not supported.
     * @throws SecurityException if the caller does not have read and does not have write access
     *      for any of the requested property.
     * @throws ServiceSpecificException If VHAL returns error.
     */
    void registerSupportedValuesChangeCallback(in List<PropIdAreaId> propIdAreaIds,
                in ISupportedValuesChangeCallback callback);
}
