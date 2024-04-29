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
package com.android.systemui.car.distantdisplay.common;

import static android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY;
import static android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_SECURE;

import android.content.Context;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.util.ArrayMap;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.constraintlayout.widget.ConstraintLayout;

import com.android.systemui.R;

/**
 * VirtualDisplay controller that manages the implementation and management of virtual displays.
 */
public class VirtualDisplayController {
    public static final String TAG = VirtualDisplayController.class.getSimpleName();
    private static final String ROOT_SURFACE = "RootViewSurface";
    private static final String NAVIGATION_SURFACE = "NavigationViewSurface";
    private final Context mContext;
    private final DisplayManager mDisplayManager;
    private final SurfaceHolderCallback mNavigationViewCallback;
    private final SurfaceHolderCallback mRootViewCallback;
    private final LayoutInflater mInflater;
    private SurfaceHolder mNavigationSurfaceHolder;
    private SurfaceHolder mRootSurfaceHolder;

    private static ArrayMap<SurfaceHolder, VirtualDisplay> sVirtualDisplays = new ArrayMap<>();
    private static ArrayMap<String, String> sUniqueIds = new ArrayMap<>();

    private int getVirtualDisplayId(SurfaceHolder holder) {
        VirtualDisplay display = getVirtualDisplay(holder);
        if (display == null) {
            Log.e(TAG, "Could not find virtual display with given holder: " + holder);
            return Display.INVALID_DISPLAY;
        }
        return display.getDisplay().getDisplayId();
    }

    public VirtualDisplayController(Context context) {
        mContext = context;
        mDisplayManager = mContext.getSystemService(DisplayManager.class);
        mNavigationViewCallback = new SurfaceHolderCallback(this, NAVIGATION_SURFACE, mContext);
        mRootViewCallback = new SurfaceHolderCallback(this, ROOT_SURFACE, mContext);
        mInflater = mContext.getSystemService(LayoutInflater.class);
    }

    /**
     * Initializes layouts and attaches SurfaceHolderCallback
     */
    public void initialize(ViewGroup container) {
        Log.i(TAG, "initializing layout and attach SurfaceHolderCallbacks");
        FrameLayout navigationViewLayout = container.findViewById(R.id.navigationView);
        FrameLayout rootVeiwLayout = container.findViewById(R.id.rootView);

        View navigationViewContainer = mInflater
                .inflate(R.layout.car_distant_display_navigation_surfaceview, null);
        ConstraintLayout navigationView = navigationViewContainer.findViewById(R.id.container);
        SurfaceView view = navigationViewContainer.findViewById(R.id.content);
        mNavigationSurfaceHolder = view.getHolder();
        mNavigationSurfaceHolder.addCallback(mNavigationViewCallback);
        navigationViewLayout.addView(navigationView);

        View rootViewContainer = mInflater
                .inflate(R.layout.car_distant_display_root_surfaceview, null);
        ConstraintLayout rootView = rootViewContainer.findViewById(R.id.container);
        SurfaceView view1 = rootViewContainer.findViewById(R.id.content);
        mRootSurfaceHolder = view1.getHolder();
        mRootSurfaceHolder.addCallback(mRootViewCallback);
        rootVeiwLayout.addView(rootView);

        // The uniqueId will be passed when creating a virtual display. It will be used for
        // displayUniqueId in config_occupant_zones as virtual:com.android.systemui:${uniqueId}
        // like virtual:com.android.systemui:DistantDisplay
        sUniqueIds.put(ROOT_SURFACE, "DistantDisplay");
        sUniqueIds.put(NAVIGATION_SURFACE, "NavigationDisplay");
    }

    /**
     * Provides a DisplayManager.
     * This method will be called from the SurfaceHolderCallback to create a virtual display.
     */
    public DisplayManager getDisplayManager() {
        return mDisplayManager;
    }

    /**
     * Gets display metrics.
     * This method will be called from the SurfaceHolderCallback to create a virtual display.
     */
    public DisplayMetrics getDisplayMetrics() {
        return mContext.getResources().getDisplayMetrics();
    }

    /**
     * Gets a SurfaceHolder object and returns a VirtualDisplay it belongs to.
     */
    public VirtualDisplay getVirtualDisplay(SurfaceHolder holder) {
        return sVirtualDisplays.get(holder);
    }

    /**
     * Removes an existing SurfaceHolder from the virtual display map.
     */
    public VirtualDisplay removeVirtualDisplay(SurfaceHolder holder) {
        Log.i(TAG, "removeVirtualDisplay for SurfaceHolder [" + holder + "]");
        return sVirtualDisplays.remove(holder);
    }

    /**
     * Builds a DB with SurfaceHolder and mapped VirtualDisplay.
     */
    public void putVirtualDisplay(SurfaceHolder holder, VirtualDisplay display) {
        if (sVirtualDisplays.get(holder) == null) {
            Log.i(TAG, "putVirtualDisplay for SurfaceHolder [" + holder + "]: [" + display + "]");
            sVirtualDisplays.put(holder, display);
        } else {
            Log.w(TAG, "surface holder with VD already exists.");
        }
    }

    /**
     * Gets the virtual display id for RootTaskView display
     */
    public int getRootTaskDisplayId() {
        if (mRootSurfaceHolder == null) {
            Log.e(TAG, "mRootSurfaceHolder is not initialized");
            return Display.INVALID_DISPLAY;
        }
        return getVirtualDisplayId(mRootSurfaceHolder);
    }

    /**
     * Gets the virtual display id for NavigationTaskView display
     */
    public int getNavigationTaskDisplayId() {
        if (mNavigationSurfaceHolder == null) {
            Log.e(TAG, "mNavigationSurfaceHolder is not initialized");
            return Display.INVALID_DISPLAY;
        }
        return getVirtualDisplayId(mNavigationSurfaceHolder);
    }

    /**
     * SurfaceHolder.Callback to create virtual display with a given SurfaceHolder.
     * It updates the virtual display map to be used when moving the activities between main and
     * distant display.
     */
    public static class SurfaceHolderCallback implements SurfaceHolder.Callback {

        public static final String TAG = SurfaceHolderCallback.class.getSimpleName();
        private final Context mContext;
        private final String mSurfaceName;
        private final VirtualDisplayController mController;

        public SurfaceHolderCallback(
                    VirtualDisplayController controller, String name, Context context) {
            Log.i(TAG, "Create SurfaceHolderCallback for " + name);
            mContext = context;
            mSurfaceName = name;
            mController = controller;
        }

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            Log.i(TAG, "surfaceCreated, holder: " + holder);
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            Log.i(TAG, "surfaceChanged, holder: " + holder + ", size:" + width + "x" + height
                    + ", format:" + format);

            VirtualDisplay virtualDisplay = mController.getVirtualDisplay(holder);
            if (virtualDisplay == null) {
                virtualDisplay = createVirtualDisplay(holder.getSurface(), width, height);
                int displayId = virtualDisplay.getDisplay().getDisplayId();
                Log.i(TAG, "Created a virtual display for " + mSurfaceName + ". ID: " + displayId);
                notifyDisplayId(mSurfaceName, displayId);
                mController.putVirtualDisplay(holder, virtualDisplay);
            } else {
                Log.i(TAG, "SetSurface display_id: " + virtualDisplay.getDisplay().getDisplayId()
                        + ", surface name:" + mSurfaceName);
                virtualDisplay.setSurface(holder.getSurface());
            }
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            Log.i(TAG, "surfaceDestroyed, holder: " + holder + ", detaching surface from"
                    + " display, surface: " + holder.getSurface());
            VirtualDisplay virtualDisplay = mController.getVirtualDisplay(holder);
            if (virtualDisplay != null) {
                virtualDisplay.setSurface(null);
                virtualDisplay.release();
                mController.removeVirtualDisplay(holder);
            }
        }

        private VirtualDisplay createVirtualDisplay(Surface surface, int width, int height) {
            DisplayMetrics metrics = mController.getDisplayMetrics();
            String mUniqueId = sUniqueIds.get(mSurfaceName);
            if (mUniqueId == null) {
                mUniqueId = "DistantDisplay-" + mSurfaceName;
            }
            Log.i(TAG, "createVirtualDisplay, surface: " + surface + ", width: " + width
                    + "x" + height + " density: " + metrics.densityDpi
                    + ", uniqueId: " + mUniqueId);
            return mController.getDisplayManager().createVirtualDisplay(
                    /* projection= */ null, "DistantDisplay-" + mSurfaceName + "-VD",
                    width, height, metrics.densityDpi, surface,
                    VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY | VIRTUAL_DISPLAY_FLAG_SECURE,
                    /* callback= */
                    null, /* handler= */ null, mUniqueId);
        }

        private void notifyDisplayId(String surfaceName, int displayId) {
            if (!surfaceName.equals(ROOT_SURFACE) && !surfaceName.equals(NAVIGATION_SURFACE)) {
                Log.e(TAG, "Invalid surfaceName: " + surfaceName);
                return;
            }
            Intent intent = new Intent("DistantDisplay_DisplayId");
            intent.putExtra("surface_name", surfaceName);
            intent.putExtra("display_id", displayId);
            mContext.sendBroadcast(intent);
        }
    }
}
