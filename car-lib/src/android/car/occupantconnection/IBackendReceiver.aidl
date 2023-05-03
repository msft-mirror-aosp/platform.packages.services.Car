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
import android.car.occupantconnection.IBackendConnectionResponder;
import android.car.occupantconnection.IPayloadCallback;
import android.car.occupantconnection.Payload;

/**
  * AIDL used by CarOccupantConnectionService to communicate to AbstractReceiverService.
  *
  * @hide
  */
oneway interface IBackendReceiver {

    void registerReceiver(in String receiverEndpointId, in IPayloadCallback callback);

    void unregisterReceiver(in String receiverEndpointId);

    void registerBackendConnectionResponder(in IBackendConnectionResponder responder);

    void onPayloadReceived(in CarOccupantZoneManager.OccupantZoneInfo senderZone,
        in Payload payload);

    void onConnectionInitiated(in CarOccupantZoneManager.OccupantZoneInfo senderZone,
        int senderAppState);

    void onConnected(in CarOccupantZoneManager.OccupantZoneInfo senderZone);

    void onConnectionCanceled(in CarOccupantZoneManager.OccupantZoneInfo senderZone);

    void onDisconnected(in CarOccupantZoneManager.OccupantZoneInfo senderZone);
}