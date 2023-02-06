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

package com.android.car;

import android.annotation.Nullable;
import android.car.builtin.os.ServiceManagerHelper;
import android.car.builtin.util.Slogf;
import android.car.hardware.property.CarPropertyManager;
import android.car.util.concurrent.AndroidFuture;
import android.hardware.automotive.vehicle.GetValueRequest;
import android.hardware.automotive.vehicle.GetValueRequests;
import android.hardware.automotive.vehicle.GetValueResult;
import android.hardware.automotive.vehicle.GetValueResults;
import android.hardware.automotive.vehicle.IVehicle;
import android.hardware.automotive.vehicle.IVehicleCallback;
import android.hardware.automotive.vehicle.SetValueRequest;
import android.hardware.automotive.vehicle.SetValueRequests;
import android.hardware.automotive.vehicle.SetValueResult;
import android.hardware.automotive.vehicle.SetValueResults;
import android.hardware.automotive.vehicle.StatusCode;
import android.hardware.automotive.vehicle.SubscribeOptions;
import android.hardware.automotive.vehicle.VehiclePropConfig;
import android.hardware.automotive.vehicle.VehiclePropConfigs;
import android.hardware.automotive.vehicle.VehiclePropError;
import android.hardware.automotive.vehicle.VehiclePropErrors;
import android.hardware.automotive.vehicle.VehiclePropValue;
import android.hardware.automotive.vehicle.VehiclePropValues;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.RemoteException;
import android.os.ServiceSpecificException;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.LongSparseArray;

import com.android.car.hal.AidlHalPropConfig;
import com.android.car.hal.HalPropConfig;
import com.android.car.hal.HalPropValue;
import com.android.car.hal.HalPropValueBuilder;
import com.android.car.hal.VehicleHalCallback;
import com.android.car.internal.LargeParcelable;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

import java.io.FileDescriptor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

final class AidlVehicleStub extends VehicleStub {

    private static final String AIDL_VHAL_SERVICE =
            "android.hardware.automotive.vehicle.IVehicle/default";
    // default timeout: 10s
    private static final long DEFAULT_TIMEOUT_MS = 10_000;

    private final IVehicle mAidlVehicle;
    private final HalPropValueBuilder mPropValueBuilder;
    private final GetSetValuesCallback mGetSetValuesCallback;
    private final HandlerThread mHandlerThread;
    private final Handler mHandler;
    private final AtomicLong mRequestId = new AtomicLong(0);
    private final Object mLock = new Object();
    // PendingSyncRequestPool is thread-safe.
    private final PendingSyncRequestPool<GetValueResult> mPendingSyncGetValueRequestPool =
            new PendingSyncRequestPool<>();
    private final PendingSyncRequestPool<SetValueResult> mPendingSyncSetValueRequestPool =
            new PendingSyncRequestPool<>();
    // PendingAsyncRequestPool is thread-safe.
    private final PendingAsyncRequestPool mPendingAsyncRequestPool = new PendingAsyncRequestPool();

    // This might be modifed during tests.
    private long mSyncOpTimeoutInMs = DEFAULT_TIMEOUT_MS;

    private static class AsyncRequestInfo {
        private final int mServiceRequestId;
        private final VehicleStubCallbackInterface mClientCallback;

        private AsyncRequestInfo(int serviceRequestId,
                VehicleStubCallbackInterface clientCallback) {
            mServiceRequestId = serviceRequestId;
            mClientCallback = clientCallback;
        }

        public int getServiceRequestId() {
            return mServiceRequestId;
        }

        public VehicleStubCallbackInterface getClientCallback() {
            return mClientCallback;
        }
    }

    AidlVehicleStub() {
        this(getAidlVehicle());
    }

    @VisibleForTesting
    AidlVehicleStub(IVehicle aidlVehicle) {
        this(aidlVehicle,
                CarServiceUtils.getHandlerThread(AidlVehicleStub.class.getSimpleName()));
    }

    @VisibleForTesting
    AidlVehicleStub(IVehicle aidlVehicle, HandlerThread handlerThread) {
        mAidlVehicle = aidlVehicle;
        mPropValueBuilder = new HalPropValueBuilder(/*isAidl=*/true);
        mHandlerThread = handlerThread;
        mHandler = new Handler(mHandlerThread.getLooper());
        mGetSetValuesCallback = new GetSetValuesCallback();
    }

    /**
     * Sets the timeout for getValue/setValue requests in milliseconds.
     */
    @VisibleForTesting
    void setSyncOpTimeoutInMs(long timeoutMs) {
        mSyncOpTimeoutInMs = timeoutMs;
    }

    @VisibleForTesting
    int countPendingRequests() {
        synchronized (mLock) {
            return mPendingAsyncRequestPool.size()
                    + mPendingSyncGetValueRequestPool.size()
                    + mPendingSyncSetValueRequestPool.size();
        }
    }

    /**
     * Checks whether we are connected to AIDL VHAL: {@code true} or HIDL VHAL: {@code false}.
     */
    @Override
    public boolean isAidlVhal() {
        return true;
    }

    /**
     * Gets a HalPropValueBuilder that could be used to build a HalPropValue.
     *
     * @return a builder to build HalPropValue.
     */
    @Override
    public HalPropValueBuilder getHalPropValueBuilder() {
        return mPropValueBuilder;
    }

    /**
     * Returns whether this vehicle stub is connecting to a valid vehicle HAL.
     *
     * @return Whether this vehicle stub is connecting to a valid vehicle HAL.
     */
    @Override
    public boolean isValid() {
        return mAidlVehicle != null;
    }

    /**
     * Gets the interface descriptor for the connecting vehicle HAL.
     *
     * @return the interface descriptor.
     * @throws IllegalStateException If unable to get the descriptor.
     */
    @Override
    public String getInterfaceDescriptor() throws IllegalStateException {
        try {
            return mAidlVehicle.asBinder().getInterfaceDescriptor();
        } catch (RemoteException e) {
            throw new IllegalStateException("Unable to get Vehicle HAL interface descriptor", e);
        }
    }

    /**
     * Registers a death recipient that would be called when vehicle HAL died.
     *
     * @param recipient A death recipient.
     * @throws IllegalStateException If unable to register the death recipient.
     */
    @Override
    public void linkToDeath(IVehicleDeathRecipient recipient) throws IllegalStateException {
        try {
            mAidlVehicle.asBinder().linkToDeath(recipient, /*flag=*/ 0);
        } catch (RemoteException e) {
            throw new IllegalStateException("Failed to linkToDeath Vehicle HAL");
        }
    }

    /**
     * Unlinks a previously linked death recipient.
     *
     * @param recipient A previously linked death recipient.
     */
    @Override
    public void unlinkToDeath(IVehicleDeathRecipient recipient) {
        mAidlVehicle.asBinder().unlinkToDeath(recipient, /*flag=*/ 0);
    }

    /**
     * Gets all property configs.
     *
     * @return All the property configs.
     * @throws RemoteException if the remote operation fails.
     * @throws ServiceSpecificException if VHAL returns service specific error.
     */
    @Override
    public HalPropConfig[] getAllPropConfigs()
            throws RemoteException, ServiceSpecificException {
        VehiclePropConfigs propConfigs = (VehiclePropConfigs)
                LargeParcelable.reconstructStableAIDLParcelable(
                        mAidlVehicle.getAllPropConfigs(), /* keepSharedMemory= */ false);
        VehiclePropConfig[] payloads = propConfigs.payloads;
        int size = payloads.length;
        HalPropConfig[] configs = new HalPropConfig[size];
        for (int i = 0; i < size; i++) {
            configs[i] = new AidlHalPropConfig(payloads[i]);
        }
        return configs;
    }

    /**
     * Gets a new {@code SubscriptionClient} that could be used to subscribe/unsubscribe.
     *
     * @param callback A callback that could be used to receive events.
     * @return a {@code SubscriptionClient} that could be used to subscribe/unsubscribe.
     */
    @Override
    public SubscriptionClient newSubscriptionClient(VehicleHalCallback callback) {
        return new AidlSubscriptionClient(callback, mPropValueBuilder);
    }

    /**
     * Gets a property.
     *
     * @param requestedPropValue The property to get.
     * @return The vehicle property value.
     * @throws RemoteException if the remote operation fails.
     * @throws ServiceSpecificException if VHAL returns service specific error.
     */
    @Override
    @Nullable
    public HalPropValue get(HalPropValue requestedPropValue)
            throws RemoteException, ServiceSpecificException {
        return getOrSetSync(requestedPropValue, mPendingSyncGetValueRequestPool,
                new AsyncGetRequestsHandler(),
                (result) -> {
                    if (result.status != StatusCode.OK) {
                        throw new ServiceSpecificException(result.status,
                                "failed to get value for " + printPropIdAreaId(requestedPropValue));
                    }
                    if (result.prop == null) {
                        return null;
                    }
                    return mPropValueBuilder.build(result.prop);
                });
    }

    /**
     * Sets a property.
     *
     * @param requestedPropValue The property to set.
     * @throws RemoteException if the remote operation fails.
     * @throws ServiceSpecificException if VHAL returns service specific error.
     */
    @Override
    public void set(HalPropValue requestedPropValue) throws RemoteException,
            ServiceSpecificException {
        getOrSetSync(requestedPropValue, mPendingSyncSetValueRequestPool,
                new AsyncSetRequestsHandler(),
                (result) -> {
                    if (result.status != StatusCode.OK) {
                        throw new ServiceSpecificException(result.status,
                                "failed to set value for " + printPropIdAreaId(requestedPropValue));
                    }
                    return null;
                });
    }

    @Override
    public void getAsync(List<AsyncGetSetRequest> getVehicleStubAsyncRequests,
            VehicleStubCallbackInterface getCallback) {
        getOrSetAsync(getVehicleStubAsyncRequests, getCallback, new AsyncGetRequestsHandler(),
                new AsyncGetResultsHandler(mPropValueBuilder));
    }

    @Override
    public void setAsync(List<AsyncGetSetRequest> setVehicleStubAsyncRequests,
            VehicleStubCallbackInterface setCallback) {
        getOrSetAsync(setVehicleStubAsyncRequests, setCallback, new AsyncSetRequestsHandler(),
                new AsyncSetResultsHandler());
    }

    @Override
    public void dump(FileDescriptor fd, List<String> args) throws RemoteException {
        mAidlVehicle.asBinder().dump(fd, args.toArray(new String[args.size()]));
    }

    // Get all the VHAL request IDs according to the service request IDs and remove them from
    // pending requests map.
    @Override
    public void cancelRequests(List<Integer> serviceRequestIds) {
        mPendingAsyncRequestPool.cancelRequests(serviceRequestIds);
    }

    /**
     * A thread-safe pending sync request pool.
     */
    private static final class PendingSyncRequestPool<VhalResultType> {
        private final Object mSyncRequestPoolLock = new Object();
        @GuardedBy("mSyncRequestPoolLock")
        private final LongSparseArray<AndroidFuture<VhalResultType>>
                mPendingRequestsByVhalRequestId = new LongSparseArray();

        AndroidFuture<VhalResultType> addRequest(long vhalRequestId) {
            synchronized (mSyncRequestPoolLock) {
                AndroidFuture<VhalResultType> resultFuture = new AndroidFuture();
                mPendingRequestsByVhalRequestId.put(vhalRequestId, resultFuture);
                return resultFuture;
            }
        }

        @Nullable AndroidFuture<VhalResultType> finishRequestIfFound(long vhalRequestId) {
            synchronized (mSyncRequestPoolLock) {
                AndroidFuture<VhalResultType> pendingRequest =
                        mPendingRequestsByVhalRequestId.get(vhalRequestId);
                mPendingRequestsByVhalRequestId.remove(vhalRequestId);
                return pendingRequest;
            }
        }

        int size() {
            synchronized (mSyncRequestPoolLock) {
                return mPendingRequestsByVhalRequestId.size();
            }
        }
    }

    /**
     * A thread-safe pending async request pool.
     */
    private static final class PendingAsyncRequestPool {
        private final Object mAsyncRequestPoolLock = new Object();
        @GuardedBy("mAsyncRequestPoolLock")
        private final LongSparseArray<AsyncRequestInfo>
                mPendingRequestsByVhalRequestId = new LongSparseArray();

        void addRequest(long vhalRequestId, AsyncRequestInfo requestInfo) {
            synchronized (mAsyncRequestPoolLock) {
                mPendingRequestsByVhalRequestId.put(vhalRequestId, requestInfo);
            }
        }

        @Nullable AsyncRequestInfo finishRequestIfFound(long vhalRequestId) {
            synchronized (mAsyncRequestPoolLock) {
                AsyncRequestInfo requestInfo = mPendingRequestsByVhalRequestId.get(vhalRequestId);
                mPendingRequestsByVhalRequestId.remove(vhalRequestId);
                return requestInfo;
            }
        }

        int size() {
            synchronized (mAsyncRequestPoolLock) {
                return mPendingRequestsByVhalRequestId.size();
            }
        }

        boolean contains(long vhalRequestId) {
            synchronized (mAsyncRequestPoolLock) {
                return mPendingRequestsByVhalRequestId.get(vhalRequestId) != null;
            }
        }

        void cancelRequests(List<Integer> serviceRequestIds) {
            Set<Integer> serviceRequestIdsSet = new ArraySet<>(serviceRequestIds);
            List<Long> vhalRequestIdsToCancel = new ArrayList<>();
            synchronized (mAsyncRequestPoolLock) {
                for (int i = 0; i < mPendingRequestsByVhalRequestId.size(); i++) {
                    int serviceRequestId = mPendingRequestsByVhalRequestId.valueAt(i)
                            .getServiceRequestId();
                    if (serviceRequestIdsSet.contains(serviceRequestId)) {
                        vhalRequestIdsToCancel.add(mPendingRequestsByVhalRequestId.keyAt(i));
                    }
                }
                for (int i = 0; i < vhalRequestIdsToCancel.size(); i++) {
                    long vhalRequestIdToCancel = vhalRequestIdsToCancel.get(i);
                    Slogf.w(CarLog.TAG_SERVICE, "the request for VHAL request ID: %d is cancelled",
                            vhalRequestIdToCancel);
                    mPendingRequestsByVhalRequestId.remove(vhalRequestIdToCancel);
                }
            }
        }

        void removeRequestsForCallback(VehicleStubCallbackInterface callback) {
            synchronized (mAsyncRequestPoolLock) {
                List<Long> requestIdsToRemove = new ArrayList<>();

                for (int i = 0; i < mPendingRequestsByVhalRequestId.size(); i++) {
                    if (mPendingRequestsByVhalRequestId.valueAt(i).getClientCallback()
                            == callback) {
                        requestIdsToRemove.add(mPendingRequestsByVhalRequestId.keyAt(i));
                    }
                }

                for (int i = 0; i < requestIdsToRemove.size(); i++) {
                    mPendingRequestsByVhalRequestId.delete(requestIdsToRemove.get(i));
                }
            }
        }
    }

    /**
     * An abstract interface for handling async get/set value requests from vehicle stub.
     */
    private abstract static class AsyncRequestsHandler<VhalRequestType, VhalRequestsType> {
        protected LongSparseArray<List<Long>> mVhalRequestIdsByTimeoutInMs =
                new LongSparseArray<>();

        /**
         * Preallocsate size array for storing VHAL requests.
         */
        abstract void allocateVhalRequestSize(int size);

        /**
         * Add a vhal request to be sent later.
         */
        abstract void addVhalRequest(long vhalRequestId, HalPropValue halPropValue,
                long timeoutInMs);

        /**
         * Get the list of stored request items.
         */
        abstract VhalRequestType[] getRequestItems();

        /**
         * Send the prepared requests to VHAL.
         */
        abstract void sendRequestsToVhal(IVehicle iVehicle, GetSetValuesCallback callbackForVhal)
                throws RemoteException, ServiceSpecificException;

        /**
         * Get the request ID for the request.
         */
        abstract long getVhalRequestId(VhalRequestType vhalRequest);

        LongSparseArray<List<Long>> getVhalRequestIdsByTimeout() {
            return mVhalRequestIdsByTimeoutInMs;
        }

        protected void storeVhalRequestIdByTimeout(long vhalRequestId, long timeoutInMs) {
            if (timeoutInMs == 0) {
                return;
            }
            if (mVhalRequestIdsByTimeoutInMs.get(timeoutInMs) == null) {
                mVhalRequestIdsByTimeoutInMs.put(timeoutInMs, new ArrayList<Long>());
            }
            mVhalRequestIdsByTimeoutInMs.get(timeoutInMs).add(vhalRequestId);
        }
    }

    /**
     * An abstract class to handle async get/set value results from VHAL.
     */
    private abstract static class AsyncResultsHandler<VhalResultType, VehicleStubResultType> {
        protected Map<VehicleStubCallbackInterface, List<VehicleStubResultType>> mCallbackToResults;

        /**
         * Add an error result to be sent to vehicleStub through the callback later.
         */
        abstract void addErrorResult(VehicleStubCallbackInterface callback, int serviceRequestId,
                int errorCode);
        /**
         * Add a VHAL result to be sent to vehicleStub through the callback later.
         */
        abstract void addVhalResult(VehicleStubCallbackInterface callback, int serviceRequestId,
                VhalResultType result);
        /**
         * Send all the stored results to vehicleStub.
         */
        abstract void callVehicleStubCallback();

        /**
         * Get the request ID for the result.
         */
        abstract long getVhalRequestId(VhalResultType vhalRequest);

        protected void addVehicleStubResult(VehicleStubCallbackInterface callback,
                VehicleStubResultType vehicleStubResult) {
            if (mCallbackToResults.get(callback) == null) {
                mCallbackToResults.put(callback, new ArrayList<>());
            }
            mCallbackToResults.get(callback).add(vehicleStubResult);
        }
    }

    @Nullable
    private static IVehicle getAidlVehicle() {
        try {
            return IVehicle.Stub.asInterface(
                    ServiceManagerHelper.waitForDeclaredService(AIDL_VHAL_SERVICE));
        } catch (RuntimeException e) {
            Slogf.w(CarLog.TAG_SERVICE, "Failed to get \"" + AIDL_VHAL_SERVICE + "\" service", e);
        }
        return null;
    }

    private class AidlSubscriptionClient extends IVehicleCallback.Stub
            implements SubscriptionClient {
        private final VehicleHalCallback mCallback;
        private final HalPropValueBuilder mBuilder;

        AidlSubscriptionClient(VehicleHalCallback callback, HalPropValueBuilder builder) {
            mCallback = callback;
            mBuilder = builder;
        }

        @Override
        public void onGetValues(GetValueResults responses) throws RemoteException {
            // We use GetSetValuesCallback for getValues and setValues operation.
            throw new UnsupportedOperationException(
                    "onGetValues should never be called on AidlSubscriptionClient");
        }

        @Override
        public void onSetValues(SetValueResults responses) throws RemoteException {
            // We use GetSetValuesCallback for getValues and setValues operation.
            throw new UnsupportedOperationException(
                    "onSetValues should never be called on AidlSubscriptionClient");
        }

        @Override
        public void onPropertyEvent(VehiclePropValues propValues, int sharedMemoryFileCount)
                throws RemoteException {
            VehiclePropValues origPropValues = (VehiclePropValues)
                    LargeParcelable.reconstructStableAIDLParcelable(propValues,
                            /* keepSharedMemory= */ false);
            ArrayList<HalPropValue> values = new ArrayList<>(origPropValues.payloads.length);
            for (VehiclePropValue value : origPropValues.payloads) {
                values.add(mBuilder.build(value));
            }
            mCallback.onPropertyEvent(values);
        }

        @Override
        public void onPropertySetError(VehiclePropErrors errors) throws RemoteException {
            VehiclePropErrors origErrors = (VehiclePropErrors)
                    LargeParcelable.reconstructStableAIDLParcelable(errors,
                            /* keepSharedMemory= */ false);
            ArrayList<VehiclePropError> errorList = new ArrayList<>(origErrors.payloads.length);
            for (VehiclePropError error : origErrors.payloads) {
                errorList.add(error);
            }
            mCallback.onPropertySetError(errorList);
        }

        @Override
        public void subscribe(SubscribeOptions[] options)
                throws RemoteException, ServiceSpecificException {
            mAidlVehicle.subscribe(this, options, /* maxSharedMemoryFileCount= */ 2);
        }

        @Override
        public void unsubscribe(int prop) throws RemoteException, ServiceSpecificException {
            mAidlVehicle.unsubscribe(this, new int[]{prop});
        }

        @Override
        public String getInterfaceHash() {
            return IVehicleCallback.HASH;
        }

        @Override
        public int getInterfaceVersion() {
            return IVehicleCallback.VERSION;
        }
    }

    private void onGetValues(GetValueResults responses) {
        GetValueResults origResponses = (GetValueResults)
                LargeParcelable.reconstructStableAIDLParcelable(responses,
                        /* keepSharedMemory= */ false);
        onGetSetValues(origResponses.payloads, new AsyncGetResultsHandler(mPropValueBuilder),
                mPendingSyncGetValueRequestPool);
    }

    private void onSetValues(SetValueResults responses) {
        SetValueResults origResponses = (SetValueResults)
                LargeParcelable.reconstructStableAIDLParcelable(responses,
                        /* keepSharedMemory= */ false);
        onGetSetValues(origResponses.payloads, new AsyncSetResultsHandler(),
                mPendingSyncSetValueRequestPool);
    }

    /**
     * A generic function for {@link onGetValues} / {@link onSetValues}.
     */
    private <VhalResultType> void onGetSetValues(VhalResultType[] vhalResults,
            AsyncResultsHandler asyncResultsHandler,
            PendingSyncRequestPool<VhalResultType> pendingSyncRequestPool) {
        synchronized (mLock) {
            for (VhalResultType result : vhalResults) {
                long vhalRequestId = asyncResultsHandler.getVhalRequestId(result);
                if (!mPendingAsyncRequestPool.contains(vhalRequestId)) {
                    // If we cannot find the request Id in the async map, we assume it is for a
                    // sync request.
                    completePendingSyncRequestLocked(pendingSyncRequestPool, vhalRequestId, result);
                    continue;
                }

                AsyncRequestInfo requestInfo = mPendingAsyncRequestPool.finishRequestIfFound(
                        vhalRequestId);
                if (requestInfo == null) {
                    Slogf.w(CarLog.TAG_SERVICE,
                            "No pending request for ID: %s, possibly already timed out, "
                            + "or cancelled, or the client already died", vhalRequestId);
                    continue;
                }
                asyncResultsHandler.addVhalResult(requestInfo.getClientCallback(),
                        requestInfo.getServiceRequestId(), result);
            }
        }
        asyncResultsHandler.callVehicleStubCallback();
    }

    private static int convertHalToCarPropertyManagerError(int errorCode) {
        switch (errorCode) {
            case StatusCode.NOT_AVAILABLE:
            case StatusCode.NOT_AVAILABLE_DISABLED:
            case StatusCode.NOT_AVAILABLE_SPEED_LOW:
            case StatusCode.NOT_AVAILABLE_SPEED_HIGH:
            case StatusCode.NOT_AVAILABLE_POOR_VISIBILITY:
            case StatusCode.NOT_AVAILABLE_SAFETY:
                return CarPropertyManager.STATUS_ERROR_NOT_AVAILABLE;
            case StatusCode.TRY_AGAIN:
                return STATUS_TRY_AGAIN;
            default:
                return CarPropertyManager.STATUS_ERROR_INTERNAL_ERROR;
        }
    }

    private static String printPropIdAreaId(HalPropValue value) {
        return "propID: " + value.getPropId() + ", areaID: " + value.getAreaId();
    }

    private final class GetSetValuesCallback extends IVehicleCallback.Stub {

        @Override
        public void onGetValues(GetValueResults responses) throws RemoteException {
            AidlVehicleStub.this.onGetValues(responses);
        }

        @Override
        public void onSetValues(SetValueResults responses) throws RemoteException {
            AidlVehicleStub.this.onSetValues(responses);
        }

        @Override
        public void onPropertyEvent(VehiclePropValues propValues, int sharedMemoryFileCount)
                throws RemoteException {
            throw new UnsupportedOperationException(
                    "GetSetValuesCallback only support onGetValues or onSetValues");
        }

        @Override
        public void onPropertySetError(VehiclePropErrors errors) throws RemoteException {
            throw new UnsupportedOperationException(
                    "GetSetValuesCallback only support onGetValues or onSetValues");
        }

        @Override
        public String getInterfaceHash() {
            return IVehicleCallback.HASH;
        }

        @Override
        public int getInterfaceVersion() {
            return IVehicleCallback.VERSION;
        }
    }

    private void addTimeoutHandlers(AsyncRequestsHandler asyncRequestsHandler) {
        LongSparseArray<List<Long>> vhalRequestIdsByTimeoutInMs =
                asyncRequestsHandler.getVhalRequestIdsByTimeout();
        for (int i = 0; i < vhalRequestIdsByTimeoutInMs.size(); i++) {
            long timeoutInMs = vhalRequestIdsByTimeoutInMs.keyAt(i);
            List<Long> vhalRequestIds = vhalRequestIdsByTimeoutInMs.valueAt(i);
            mHandler.postDelayed(() -> {
                requestsTimedout(vhalRequestIds);
            }, timeoutInMs);
        }
    }

    private void requestsTimedout(List<Long> vhalRequestIds) {
        Map<VehicleStubCallbackInterface, List<Integer>> timedoutServiceRequestIdsByCallback =
                new ArrayMap<>();
        synchronized (mLock) {
            for (int i = 0; i < vhalRequestIds.size(); i++) {
                long vhalRequestId = vhalRequestIds.get(i);
                AsyncRequestInfo requestInfo = mPendingAsyncRequestPool.finishRequestIfFound(
                        vhalRequestId);
                if (requestInfo == null) {
                    // We already finished the request or the callback is already dead, ignore.
                    continue;
                }
                VehicleStubCallbackInterface getAsyncCallback = requestInfo.getClientCallback();
                if (timedoutServiceRequestIdsByCallback.get(getAsyncCallback) == null) {
                    timedoutServiceRequestIdsByCallback.put(getAsyncCallback, new ArrayList<>());
                }
                timedoutServiceRequestIdsByCallback.get(getAsyncCallback).add(
                        requestInfo.getServiceRequestId());
            }
        }

        for (VehicleStubCallbackInterface callback : timedoutServiceRequestIdsByCallback.keySet()) {
            callback.onRequestsTimeout(timedoutServiceRequestIdsByCallback.get(callback));
        }
    }

    /**
     * Mark a pending sync get/set property request as complete and deliver the result.
     */
    @GuardedBy("mLock")
    private <VhalResultType> void completePendingSyncRequestLocked(
            PendingSyncRequestPool<VhalResultType> pendingSyncRequestPool, long vhalRequestId,
            VhalResultType result) {
        AndroidFuture<VhalResultType> pendingRequest =
                pendingSyncRequestPool.finishRequestIfFound(vhalRequestId);
        if (pendingRequest == null) {
            Slogf.w(CarLog.TAG_SERVICE, "No pending request for ID: " + vhalRequestId
                    + ", possibly already timed out");
            return;
        }
        mHandler.post(() -> {
            // This might fail if the request already timed out.
            pendingRequest.complete(result);
        });
    }

    private static final class AsyncGetRequestsHandler
            extends AsyncRequestsHandler<GetValueRequest, GetValueRequests> {
        private GetValueRequest[] mVhalRequestItems;
        private int mIndex;

        @Override
        public void allocateVhalRequestSize(int size) {
            mVhalRequestItems = new GetValueRequest[size];
        }

        @Override
        public void addVhalRequest(long vhalRequestId, HalPropValue halPropValue,
                long timeoutInMs) {
            mVhalRequestItems[mIndex] = new GetValueRequest();
            mVhalRequestItems[mIndex].requestId = vhalRequestId;
            mVhalRequestItems[mIndex].prop = (VehiclePropValue) halPropValue.toVehiclePropValue();
            mIndex++;
            storeVhalRequestIdByTimeout(vhalRequestId, timeoutInMs);
        }

        @Override
        public GetValueRequest[] getRequestItems() {
            return mVhalRequestItems;
        }

        @Override
        public void sendRequestsToVhal(IVehicle iVehicle, GetSetValuesCallback callbackForVhal)
                throws RemoteException, ServiceSpecificException {
            GetValueRequests largeParcelableRequest = new GetValueRequests();
            largeParcelableRequest.payloads = mVhalRequestItems;
            largeParcelableRequest = (GetValueRequests) LargeParcelable.toLargeParcelable(
                    largeParcelableRequest, () -> {
                        GetValueRequests newRequests = new GetValueRequests();
                        newRequests.payloads = new GetValueRequest[0];
                        return newRequests;
            });
            iVehicle.getValues(callbackForVhal, largeParcelableRequest);
        }

        @Override
        public long getVhalRequestId(GetValueRequest request) {
            return request.requestId;
        }
    }

    private static final class AsyncSetRequestsHandler
            extends AsyncRequestsHandler<SetValueRequest, SetValueRequests> {
        private SetValueRequest[] mVhalRequestItems;
        private int mIndex;

        @Override
        public void allocateVhalRequestSize(int size) {
            mVhalRequestItems = new SetValueRequest[size];
        }

        @Override
        public void addVhalRequest(long vhalRequestId, HalPropValue halPropValue,
                long timeoutInMs) {
            mVhalRequestItems[mIndex] = new SetValueRequest();
            mVhalRequestItems[mIndex].requestId = vhalRequestId;
            mVhalRequestItems[mIndex].value = (VehiclePropValue) halPropValue.toVehiclePropValue();
            mIndex++;
            storeVhalRequestIdByTimeout(vhalRequestId, timeoutInMs);
        }

        @Override
        public SetValueRequest[] getRequestItems() {
            return mVhalRequestItems;
        }

        @Override
        public void sendRequestsToVhal(IVehicle iVehicle, GetSetValuesCallback callbackForVhal)
                throws RemoteException, ServiceSpecificException {
            SetValueRequests largeParcelableRequest = new SetValueRequests();
            largeParcelableRequest.payloads = mVhalRequestItems;
            largeParcelableRequest = (SetValueRequests) LargeParcelable.toLargeParcelable(
                    largeParcelableRequest, () -> {
                        SetValueRequests newRequests = new SetValueRequests();
                        newRequests.payloads = new SetValueRequest[0];
                        return newRequests;
            });
            iVehicle.setValues(callbackForVhal, largeParcelableRequest);
        }

        @Override
        public long getVhalRequestId(SetValueRequest request) {
            return request.requestId;
        }
    }

    private static final class AsyncGetResultsHandler extends
            AsyncResultsHandler<GetValueResult, GetVehicleStubAsyncResult> {
        private HalPropValueBuilder mPropValueBuilder;

        AsyncGetResultsHandler(HalPropValueBuilder propValueBuilder) {
            mPropValueBuilder = propValueBuilder;
            mCallbackToResults = new ArrayMap<VehicleStubCallbackInterface,
                    List<GetVehicleStubAsyncResult>>();
        }

        @Override
        void addErrorResult(VehicleStubCallbackInterface callback, int serviceRequestId,
                int errorCode) {
            addVehicleStubResult(callback, new GetVehicleStubAsyncResult(serviceRequestId,
                    errorCode));
        }

        @Override
        void addVhalResult(VehicleStubCallbackInterface callback, int serviceRequestId,
                GetValueResult result) {
            addVehicleStubResult(callback, toVehicleStubResult(serviceRequestId, result));

        }

        @Override
        void callVehicleStubCallback() {
            for (Map.Entry<VehicleStubCallbackInterface, List<GetVehicleStubAsyncResult>> entry :
                    mCallbackToResults.entrySet()) {
                entry.getKey().onGetAsyncResults(entry.getValue());
            }
        }

        @Override
        long getVhalRequestId(GetValueResult result) {
            return result.requestId;
        }

        private GetVehicleStubAsyncResult toVehicleStubResult(int serviceRequestId,
                GetValueResult vhalResult) {
            if (vhalResult.status != StatusCode.OK) {
                return new GetVehicleStubAsyncResult(serviceRequestId,
                        convertHalToCarPropertyManagerError(vhalResult.status));
            } else if (vhalResult.prop == null) {
                // If status is OKAY but no property is returned, treat it as not_available.
                return new GetVehicleStubAsyncResult(serviceRequestId,
                        CarPropertyManager.STATUS_ERROR_NOT_AVAILABLE);
            }
            return new GetVehicleStubAsyncResult(serviceRequestId,
                    mPropValueBuilder.build(vhalResult.prop));
        }
    }

    private static final class AsyncSetResultsHandler extends
            AsyncResultsHandler<SetValueResult, SetVehicleStubAsyncResult> {
        AsyncSetResultsHandler() {
            mCallbackToResults = new ArrayMap<VehicleStubCallbackInterface,
                    List<SetVehicleStubAsyncResult>>();
        }

        @Override
        void addErrorResult(VehicleStubCallbackInterface callback, int serviceRequestId,
                int errorCode) {
            addVehicleStubResult(callback, new SetVehicleStubAsyncResult(serviceRequestId,
                    errorCode));
        }

        @Override
        void addVhalResult(VehicleStubCallbackInterface callback, int serviceRequestId,
                SetValueResult result) {
            addVehicleStubResult(callback, toVehicleStubResult(serviceRequestId, result));

        }

        @Override
        void callVehicleStubCallback() {
            for (Map.Entry<VehicleStubCallbackInterface, List<SetVehicleStubAsyncResult>> entry :
                    mCallbackToResults.entrySet()) {
                entry.getKey().onSetAsyncResults(entry.getValue());
            }
        }

        @Override
        long getVhalRequestId(SetValueResult result) {
            return result.requestId;
        }

        private SetVehicleStubAsyncResult toVehicleStubResult(int serviceRequestId,
                SetValueResult vhalResult) {
            if (vhalResult.status != StatusCode.OK) {
                return new SetVehicleStubAsyncResult(serviceRequestId,
                        convertHalToCarPropertyManagerError(vhalResult.status));
            }
            return new SetVehicleStubAsyncResult(serviceRequestId);
        }
    }

    /**
     * Generic function for {@link get} or {@link set}.
     */
    private <VhalResultType> HalPropValue getOrSetSync(
            HalPropValue requestedPropValue,
            PendingSyncRequestPool<VhalResultType> pendingSyncRequestPool,
            AsyncRequestsHandler requestsHandler,
            Function<VhalResultType, HalPropValue> resultHandler)
            throws RemoteException, ServiceSpecificException {
        long vhalRequestId = mRequestId.getAndIncrement();
        int propId = requestedPropValue.getPropId();

        AndroidFuture<VhalResultType> resultFuture = pendingSyncRequestPool.addRequest(
                vhalRequestId);

        requestsHandler.allocateVhalRequestSize(1);
        requestsHandler.addVhalRequest(vhalRequestId, requestedPropValue, /* timeoutInMs= */ 0);
        requestsHandler.sendRequestsToVhal(mAidlVehicle, mGetSetValuesCallback);

        try {
            VhalResultType result = resultFuture.get(mSyncOpTimeoutInMs,
                    TimeUnit.MILLISECONDS);
            return resultHandler.apply(result);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // Restore the interrupted status
            throw new ServiceSpecificException(StatusCode.INTERNAL_ERROR,
                    "thread interrupted, possibly exiting the thread");
        } catch (ExecutionException e) {
            throw new ServiceSpecificException(StatusCode.INTERNAL_ERROR,
                    "failed to resolve future, error: " + e);
        } catch (TimeoutException e) {
            throw new ServiceSpecificException(StatusCode.INTERNAL_ERROR,
                    "get/set value request timeout for: " + printPropIdAreaId(requestedPropValue));
        } finally {
            pendingSyncRequestPool.finishRequestIfFound(vhalRequestId);
        }
    }

    /**
     * Generic function for {@link getAsync} or {@link setAsync}.
     */
    private <VhalRequestType, VhalRequestsType> void getOrSetAsync(
            List<AsyncGetSetRequest> vehicleStubAsyncRequests,
            VehicleStubCallbackInterface vehicleStubCallback,
            AsyncRequestsHandler<VhalRequestType, VhalRequestsType> asyncRequestsHandler,
            AsyncResultsHandler asyncResultsHandler) {
        prepareAndConvertAsyncRequests(vehicleStubAsyncRequests, vehicleStubCallback,
                asyncRequestsHandler);

        try {
            asyncRequestsHandler.sendRequestsToVhal(mAidlVehicle, mGetSetValuesCallback);
        } catch (RemoteException e) {
            handleAsyncExceptionFromVhal(asyncRequestsHandler, vehicleStubCallback,
                    CarPropertyManager.STATUS_ERROR_INTERNAL_ERROR, asyncResultsHandler);
            return;
        } catch (ServiceSpecificException e) {
            handleAsyncExceptionFromVhal(asyncRequestsHandler, vehicleStubCallback,
                    convertHalToCarPropertyManagerError(e.errorCode), asyncResultsHandler);
            return;
        }

        // Register the timeout handlers for the added requests. Only register if the requests
        // are sent successfully.
        addTimeoutHandlers(asyncRequestsHandler);
    }

    /**
     * Prepare an async get/set request from client and convert it to vhal requests.
     *
     * <p> It does the following things:
     * <ul>
     * <li> Add a client callback death listener which will clear the pending requests when client
     * died
     * <li> Store the async requests to a pending request map.
     * <li> For each client request, generate a unique VHAL request ID and convert the request to
     * VHAL request type.
     * <li> Stores the time-out information for each request into a map so that we can register
     * timeout handlers later.
     * <li> Convert the vhal request items to a single large parcelable class.
     */
    private <VhalRequestType, VhalRequestsType> void prepareAndConvertAsyncRequests(
                    List<AsyncGetSetRequest> vehicleStubRequests,
                    VehicleStubCallbackInterface clientCallback,
                    AsyncRequestsHandler<VhalRequestType, VhalRequestsType> asyncRequestsHandler) {
        asyncRequestsHandler.allocateVhalRequestSize(vehicleStubRequests.size());
        synchronized (mLock) {
            // Add the death recipient so that all client info for a dead callback will be cleaned
            // up. Note that this must be in the same critical section as the following code to
            // store the client info into the map. This makes sure that even if the client is
            // died half way while adding the client info, it will wait until all the clients are
            // added and then remove them all.
            try {
                clientCallback.linkToDeath(() -> {
                    // This function will be invoked from a different thread. It needs to be
                    // guarded by a lock so that the whole 'prepareAndConvertAsyncRequests' finishes
                    // before we remove the callback.
                    synchronized (mLock) {
                        mPendingAsyncRequestPool.removeRequestsForCallback(clientCallback);
                    }
                });
            } catch (RemoteException e) {
                // The binder is already died.
                throw new IllegalStateException("Failed to link callback to death recipient, the "
                        + "client maybe already died");
            }

            for (int i = 0; i < vehicleStubRequests.size(); i++) {
                AsyncGetSetRequest vehicleStubRequest = vehicleStubRequests.get(i);
                long vhalRequestId = mRequestId.getAndIncrement();
                AsyncRequestInfo requestInfo = new AsyncRequestInfo(
                        vehicleStubRequest.getServiceRequestId(), clientCallback);
                mPendingAsyncRequestPool.addRequest(vhalRequestId, requestInfo);

                long timeoutInMs = vehicleStubRequest.getTimeoutInMs();
                asyncRequestsHandler.addVhalRequest(vhalRequestId,
                        vehicleStubRequest.getHalPropValue(), timeoutInMs);
            }
        }

    }

    /**
     * Callback to deliver async get/set error results back to the client.
     *
     * <p>When an exception is received, the callback delivers the error results on the same thread
     * where the caller is.
     */
    private <VhalRequestType, VhalRequestsType> void handleAsyncExceptionFromVhal(
            AsyncRequestsHandler<VhalRequestType, VhalRequestsType> asyncRequestsHandler,
            VehicleStubCallbackInterface vehicleStubCallback, int errorCode,
            AsyncResultsHandler asyncResultsHandler) {
        Slogf.w(CarLog.TAG_SERVICE,
                "Received RemoteException or ServiceSpecificException from VHAL. VHAL is likely "
                        + "dead, error code: %d", errorCode);
        synchronized (mLock) {
            VhalRequestType[] requests = asyncRequestsHandler.getRequestItems();
            for (int i = 0; i < requests.length; i++) {
                long vhalRequestId = asyncRequestsHandler.getVhalRequestId(requests[i]);
                AsyncRequestInfo requestInfo = mPendingAsyncRequestPool.finishRequestIfFound(
                        vhalRequestId);
                if (requestInfo == null) {
                    Slogf.w(CarLog.TAG_SERVICE,
                            "No pending request for ID: %s, possibly already timed out or "
                            + "the client already died", vhalRequestId);
                    continue;
                }
                asyncResultsHandler.addErrorResult(
                        vehicleStubCallback, requestInfo.getServiceRequestId(), errorCode);
            }
        }
        asyncResultsHandler.callVehicleStubCallback();
    }

}
