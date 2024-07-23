/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.car.internal.os;

import android.annotation.NonNull;
import android.car.builtin.os.ServiceManagerHelper;
import android.os.IBinder;
import android.os.RemoteException;

/**
 * An interface to inject fake
 * {@link android.car.builtin.os.ServiceManagerHelper}.
 */
public interface ServiceManager {
    /** Check {@link ServiceManagerHelper#getService(String)} */
    IBinder getService(String name);

    /** Check {@link ServiceManagerHelper#registerForNotifications} */
    void registerForNotifications(@NonNull String name,
            @NonNull ServiceManagerHelper.IServiceRegistrationCallback callback)
            throws RemoteException;
}
