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

package android.car.occupantconnection;

import android.car.CarOccupantZoneManager;
import android.car.occupantconnection.IOccupantZoneStateCallback;
import android.car.occupantconnection.IConnectionRequestCallback;
import android.car.occupantconnection.IConnectionStateCallback;
import android.car.occupantconnection.IPayloadCallback;
import android.car.occupantconnection.Payload;
import android.content.pm.PackageInfo;
import android.os.IBinder;

import java.util.List;

/** @hide */
interface ICarOccupantConnection {

    // The following callbacks are used by CarRemoteDeviceManager.
    void registerOccupantZoneStateCallback(in IOccupantZoneStateCallback callback);
    void unregisterOccupantZoneStateCallback();

    PackageInfo getEndpointPackageInfo(in CarOccupantZoneManager.OccupantZoneInfo occupantZone);

    // The following callbacks are used by CarOccupantConnectionManager.
    void registerReceiver(in String receiverEndpointId, in IPayloadCallback callback);
    void unregisterReceiver(in String receiverEndpointId);

    void requestConnection(in CarOccupantZoneManager.OccupantZoneInfo receiverZone,
        in IConnectionRequestCallback callback);
    void cancelConnection(in CarOccupantZoneManager.OccupantZoneInfo receiverZone);

    void sendPayload(in CarOccupantZoneManager.OccupantZoneInfo receiverZone,
        in Payload payload);

    void disconnect(in CarOccupantZoneManager.OccupantZoneInfo receiverZone);

    boolean isConnected(in CarOccupantZoneManager.OccupantZoneInfo receiverZone);

    void registerConnectionStateCallback(in CarOccupantZoneManager.OccupantZoneInfo receiverZone,
        in IConnectionStateCallback callback);
    void unregisterConnectionStateCallback(in CarOccupantZoneManager.OccupantZoneInfo receiverZone);
}
