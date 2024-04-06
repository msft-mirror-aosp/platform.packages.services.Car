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

import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.os.UserHandle;
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
import com.android.systemui.car.distantdisplay.activity.NavigationTaskViewWallpaperActivity;
import com.android.systemui.car.distantdisplay.activity.RootTaskViewWallpaperActivity;

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
     * Launches activity with given display id and intent
     */
    public void launchActivity(int displayId, Intent intent) {
        if (displayId == Display.INVALID_DISPLAY) {
            Log.e(TAG, "Invalid display Id");
            return;
        }
        ActivityOptions options = ActivityOptions.makeCustomAnimation(mContext, 0, 0);
        options.setLaunchDisplayId(displayId);
        mContext.startActivityAsUser(intent, options.toBundle(), UserHandle.CURRENT);
    }

    /**
     * Launches the wallpaper activity on a virtuall display which belongs to a surface.
     * This method will be called when a virtual display is created from the SrufaceHolderCallback.
     */
    public void launchWallpaper(String surface) {
        Log.i(TAG, "launchWallpaper");
        if (surface.equals(ROOT_SURFACE)) {
            launchActivity(getRootTaskDisplayId(),
                    RootTaskViewWallpaperActivity.createIntent(mContext));
        } else if (surface.equals(NAVIGATION_SURFACE)) {
            launchActivity(getNavigationTaskDisplayId(),
                    NavigationTaskViewWallpaperActivity.createIntent(mContext));
        } else {
            Log.e(TAG, "Invalid surfacename : " + surface);
        }
    }

    /**
     * Gets a SurfaceHolder object and returns a VirtualDisplay it belongs to.
     */
    public VirtualDisplay getVirtualDisplay(SurfaceHolder holder) {
        return sVirtualDisplays.get(holder);
    }

    /**
     * Builds a DB with SurfaceHolder and mapped VirtualDisplay.
     */
    public void putVirtualDisplay(SurfaceHolder holder, VirtualDisplay display) {
        if (sVirtualDisplays.get(holder) == null) {
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
                mController.putVirtualDisplay(holder, virtualDisplay);
                int displayId = virtualDisplay.getDisplay().getDisplayId();
                Log.i(TAG, "Created a virtual display for " + mSurfaceName + ". ID: " + displayId);
                mController.launchWallpaper(mSurfaceName);
            } else {
                Log.i(TAG, "SetSurface display_id: " + virtualDisplay.getDisplay().getDisplayId()
                        + ", surface name:" + mSurfaceName);
                virtualDisplay.setSurface(holder.getSurface());
            }
            int rootTaskDisplayId = mController.getRootTaskDisplayId();
            if (rootTaskDisplayId != Display.INVALID_DISPLAY) {
                notifyDisplayId("DistantDisplay_RootTaskViewDisplayId", rootTaskDisplayId);
            }
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            Log.i(TAG, "surfaceDestroyed, holder: " + holder + ", detaching surface from"
                    + " display, surface: " + holder.getSurface());
            VirtualDisplay virtualDisplay = mController.getVirtualDisplay(holder);
            if (virtualDisplay != null) {
                virtualDisplay.setSurface(null);
            }
        }

        private VirtualDisplay createVirtualDisplay(Surface surface, int width, int height) {
            DisplayMetrics metrics = mController.getDisplayMetrics();
            Log.i(TAG, "createVirtualDisplay, surface: " + surface + ", width: " + width
                    + "x" + height + " density: " + metrics.densityDpi);
            return mController.getDisplayManager().createVirtualDisplay(
                    /* projection= */ null, "DistantDisplay-" + mSurfaceName + "-VD",
                    width, height, metrics.densityDpi, surface,
                    VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY | VIRTUAL_DISPLAY_FLAG_SECURE,
                    /* callback= */
                    null, /* handler= */ null, "DistantDisplay-" + mSurfaceName);
        }

        private void notifyDisplayId(String action, int displayId) {
            Intent intent = new Intent(action);
            intent.putExtra("display_id", displayId);
            mContext.sendBroadcast(intent);
        }
    }
}
