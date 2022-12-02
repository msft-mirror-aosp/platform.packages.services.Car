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

package com.android.car.portraitlauncher.homeactivities;

import static android.view.InsetsState.ITYPE_BOTTOM_GENERIC_OVERLAY;
import static android.view.InsetsState.ITYPE_TOP_GENERIC_OVERLAY;
import static android.view.View.GONE;
import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;
import static android.view.WindowManager.LayoutParams.PRIVATE_FLAG_TRUSTED_OVERLAY;
import static android.window.DisplayAreaOrganizer.FEATURE_DEFAULT_TASK_CONTAINER;

import static com.android.car.caruiportrait.common.service.CarUiPortraitService.MSG_FG_TASK_VIEW_READY;
import static com.android.car.caruiportrait.common.service.CarUiPortraitService.MSG_HIDE_SYSTEM_BAR_FOR_IMMERSIVE;
import static com.android.car.caruiportrait.common.service.CarUiPortraitService.MSG_IMMERSIVE_MODE_REQUESTED;
import static com.android.car.caruiportrait.common.service.CarUiPortraitService.MSG_REGISTER_CLIENT;
import static com.android.car.caruiportrait.common.service.CarUiPortraitService.MSG_ROOT_TASK_VIEW_VISIBILITY_CHANGE;
import static com.android.car.caruiportrait.common.service.CarUiPortraitService.MSG_SUW_IN_PROGRESS;
import static com.android.car.caruiportrait.common.service.CarUiPortraitService.MSG_UNREGISTER_CLIENT;

import android.annotation.IntDef;
import android.annotation.Nullable;
import android.app.ActivityManager;
import android.app.TaskInfo;
import android.app.TaskStackListener;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.graphics.Region;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.ArraySet;
import android.util.Log;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Transformation;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.core.view.WindowCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;

import com.android.car.carlauncher.CarLauncherUtils;
import com.android.car.carlauncher.CarTaskView;
import com.android.car.carlauncher.ControlledCarTaskViewCallbacks;
import com.android.car.carlauncher.LaunchRootCarTaskViewCallbacks;
import com.android.car.carlauncher.SemiControlledCarTaskViewCallbacks;
import com.android.car.carlauncher.TaskViewManager;
import com.android.car.carlauncher.homescreen.HomeCardModule;
import com.android.car.carlauncher.taskstack.TaskStackChangeListeners;
import com.android.car.caruiportrait.common.service.CarUiPortraitService;
import com.android.car.portraitlauncher.R;

import java.util.List;
import java.util.Set;

/**
 * This home screen has maps running in the background hosted in a TaskView. At the bottom, there
 * is a control bar view that will display information regarding media/dialer. Any other
 * application other than assistant voice activity will be launched in the rootTaskView that is
 * displayed in the lower half of the screen.
 *
 * —--------------------------------------------------------------------
 * |                              Status Bar                            |
 * —--------------------------------------------------------------------
 * |                                                                    |
 * |                                                                    |
 * |                                                                    |
 * |                             MAPS(Task View)                        |
 * |                                                                    |
 * |                                                                    |
 * |                                                                    |
 * |                                                                    |
 * —--------------------------------------------------------------------
 * |                                                                    |
 * |                                                                    |
 * |                                                                    |
 * |                      App Space (Root Task View)                    |
 * |                      (This layer is above maps)                    |
 * |                                                                    |
 * |                                                                    |
 * |                                                                    |
 * |                                                                    |
 * —--------------------------------------------------------------------
 * |                          Control Bar                               |
 * |                                                                    |
 * —--------------------------------------------------------------------
 * |                             Nav Bar                                |
 * —--------------------------------------------------------------------
 *
 * In total this Activity has 2 TaskViews.
 * Background Task view:
 * - It only contains maps app.
 * - Maps app is manually started in this taskview.
 *
 * RootTaskView:
 * - It acts as the default container. Which means all the apps will run inside it by default.
 *
 * Note: RootTaskView always overlap over the Background TaskView.
 */
public final class CarUiPortraitHomeScreen extends FragmentActivity {
    public static final String TAG = CarUiPortraitHomeScreen.class.getSimpleName();
    private static final boolean DBG = Build.IS_DEBUGGABLE;

    private static final int ANIMATION_DURATION_MS = 300;

    private static final int STATE_OPEN = 1;
    private static final int STATE_CLOSE = 2;
    private static final int STATE_FULL_WITH_SYS_BAR = 3;
    private static final int STATE_FULL_WITHOUT_SYS_BAR = 4;

    @IntDef({STATE_OPEN,
            STATE_CLOSE,
            STATE_FULL_WITH_SYS_BAR,
            STATE_FULL_WITHOUT_SYS_BAR})
    private @interface RootAppAreaState {
    }

    @RootAppAreaState
    private int mRootAppAreaState = STATE_CLOSE;
    private int mGripBarHeight;
    private int mStatusBarHeight;
    private int mBackgroundAppAreaHeightWhenCollapsed;
    private int mTitleBarDragThreshold;
    private FrameLayout mContainer;
    private View mRootAppAreaContainer;
    private View mGripBar;
    private View mGripBarView;
    private ViewGroup mBackgroundAppArea;
    private ViewGroup mRootAppArea;
    private ImageView mImmersiveButtonView;
    // TODO(b/257353761): Deprecate mIsRootTaskViewFullScreen with isFullScreen once the use case
    // is sorted out.
    private boolean mIsRootTaskViewFullScreen;
    private boolean mShouldSetInsetsOnBackgroundTaskView;
    private Drawable mChevronUpDrawable;
    private Drawable mChevronDownDrawable;
    private View mControlBarView;
    private TaskViewManager mTaskViewManager;
    // All the TaskViews & corresponding helper instance variables.
    private CarTaskView mBackgroundTaskView;
    private CarTaskView mFullScreenTaskView;
    public Set<ComponentName> mFullScreenActivities;
    public Set<ComponentName> mDrawerActivities;
    private CarTaskView mRootTaskView;
    private boolean mIsAnimating;
    private ComponentName mBackgroundActivityComponent;
    private ArraySet<ComponentName> mIgnoreOpeningRootTaskViewComponentsSet;
    private Set<HomeCardModule> mHomeCardModules;
    private boolean mIsRootPanelInitialized;
    private int mNavBarHeight;
    private int mControlBarHeightMinusCornerRadius;
    private int mCornerRadius;
    private boolean mIsSUWInProgress;

    /** Messenger for communicating with {@link CarUiPortraitService}. */
    private Messenger mService = null;
    /** Flag indicating whether or not {@link CarUiPortraitService} is bounded. */
    private boolean mIsBound;
    private boolean mLastFrontActivityIsFullScreenActivity;

    /**
     * All messages from {@link CarUiPortraitService} are received in this handler.
     */
    private final Messenger mMessenger = new Messenger(new IncomingHandler());

    /**
     * Class for interacting with the main interface of the {@link CarUiPortraitService}.
     */
    private final ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            // Communicating with our service through an IDL interface, so get a client-side
            // representation of that from the raw service object.
            mService = new Messenger(service);

            // Register to the service.
            try {
                Message msg = Message.obtain(null, MSG_REGISTER_CLIENT);
                msg.replyTo = mMessenger;
                mService.send(msg);
            } catch (RemoteException e) {
                // In this case the service has crashed before we could even
                // do anything with it; we can count on soon being
                // disconnected (and then reconnected if it can be restarted)
                // so there is no need to do anything here.
                Log.w(TAG, "can't connect to CarUiPortraitService: ", e);
            }
        }

        public void onServiceDisconnected(ComponentName className) {
            mService = null;
        }
    };
    // This listener lets us know when actives are added and removed from any of the display regions
    // we care about, so we can trigger the opening and closing of the app containers as needed.
    private final TaskStackListener mTaskStackListener = new TaskStackListener() {
        @Override
        public void onTaskMovedToFront(ActivityManager.RunningTaskInfo taskInfo)
                throws RemoteException {
            CarUiPortraitHomeScreen.this.updateRootTaskViewVisibility(taskInfo);
            mLastFrontActivityIsFullScreenActivity = mFullScreenActivities.contains(
                    taskInfo.baseActivity);
        }

        /**
         * Called whenever IActivityManager.startActivity is called on an activity that is already
         * running, but the task is either brought to the front or a new Intent is delivered to it.
         *
         * @param task information about the task the activity was relaunched into
         * @param homeTaskVisible whether or not the home task is visible
         * @param clearedTask whether or not the launch activity also cleared the task as a part of
         * starting
         * @param wasVisible whether the activity was visible before the restart attempt
         */
        @Override
        public void onActivityRestartAttempt(ActivityManager.RunningTaskInfo task,
                boolean homeTaskVisible, boolean clearedTask, boolean wasVisible)
                throws RemoteException {
            super.onActivityRestartAttempt(task, homeTaskVisible, clearedTask, wasVisible);
            if (task.baseIntent == null || task.baseIntent.getComponent() == null
                    || mIsAnimating) {
                return;
            }

            if (!wasVisible) {
                return;
            }

            if (mBackgroundActivityComponent.equals(task.baseActivity)) {
                return;
            }

            logIfDebuggable("Update UI state on app restart attempt, task = " + task);
            // Toggle between STATE_OPEN and STATE_CLOSE when any task is in foreground and a new
            // Intent is sent to start the same task. In this case it's needed to toggle the root
            // task view when notification or AppGrid is in foreground and onClick on nav bar
            // buttons should close/open it.
            updateUIState(
                    mRootAppAreaState == STATE_OPEN ? STATE_CLOSE : STATE_OPEN,
                    /* animate = */ true);
        }
    };

    /**
     * Only resize the size of rootTaskView when SUW is in progress. This is to resize the height of
     * rootTaskView after status bar hide on SUW start.
     */
    private final View.OnLayoutChangeListener mHomeScreenLayoutChangeListener =
            (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                int newHeight = bottom - top;
                int oldHeight = oldBottom - oldTop;
                if (oldHeight == newHeight) {
                    return;
                }
                logIfDebuggable("container height change from " + oldHeight + " to " + newHeight);
                if (mIsSUWInProgress) {
                    makeRootAppAreaContainerSameHeightAsHomeScreen();
                }
            };

    private static void logIfDebuggable(String message) {
        if (DBG) {
            Log.d(TAG, message);
        }
    }

    void updateVoicePlateActivityMap() {
        Context currentUserContext = createContextAsUser(
                UserHandle.of(ActivityManager.getCurrentUser()), /* flags= */ 0);

        Intent voiceIntent = new Intent(Intent.ACTION_VOICE_ASSIST, /* uri= */ null);
        List<ResolveInfo> result = currentUserContext.getPackageManager().queryIntentActivities(
                voiceIntent, PackageManager.MATCH_ALL);

        for (ResolveInfo info : result) {
            if (mFullScreenActivities.add(info.activityInfo.getComponentName())) {
                logIfDebuggable("adding the following component to show on fullscreen: "
                        + info.activityInfo.getComponentName());
            }
        }
    }

    private final View.OnLayoutChangeListener mControlBarOnLayoutChangeListener =
            (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                if (bottom == oldBottom && top == oldTop) {
                    return;
                }
                mControlBarHeightMinusCornerRadius =
                        mControlBarView.getHeight() - mCornerRadius;
                updateBottomOverlap(mRootAppAreaState);

                if (mShouldSetInsetsOnBackgroundTaskView) {
                    return;
                }
                if (mBackgroundAppArea != null) {
                    ViewGroup.MarginLayoutParams upperAppAreaLayoutParams =
                            (ViewGroup.MarginLayoutParams) mBackgroundAppArea.getLayoutParams();
                    if (upperAppAreaLayoutParams != null) {
                        upperAppAreaLayoutParams.setMargins(/* left= */ 0, /* top= */
                                0, /* right= */ 0, mControlBarHeightMinusCornerRadius);
                    }
                }
                if (mRootAppAreaContainer != null) {
                    mRootAppAreaContainer.setPadding(/* start= */ 0, /* top= */ 0, /* end= */ 0,
                            mControlBarHeightMinusCornerRadius);
                }
            };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.car_ui_portrait_launcher);
        // Make the window fullscreen as GENERIC_OVERLAYS are supplied to the background task view
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        // Activity is running fullscreen to allow background task to bleed behind status bar
        int identifier = getResources().getIdentifier("navigation_bar_height", "dimen", "android");
        mNavBarHeight = identifier > 0 ? getResources().getDimensionPixelSize(identifier) : 0;
        mGripBarHeight = (int) getResources().getDimension(R.dimen.grip_bar_height);
        mCornerRadius = (int) getResources().getDimension(R.dimen.corner_radius);

        mContainer = findViewById(R.id.container);
        mContainer.addOnLayoutChangeListener(mHomeScreenLayoutChangeListener);
        setHomeScreenBottomMargin(mNavBarHeight);

        mBackgroundAppAreaHeightWhenCollapsed =
                (int) getResources().getDimension(R.dimen.upper_app_area_collapsed_height);
        mTitleBarDragThreshold =
                (int) getResources().getDimension(
                        R.dimen.title_bar_display_area_touch_drag_threshold);
        mRootAppAreaContainer = findViewById(R.id.lower_app_area_container);
        mRootAppArea = findViewById(R.id.lower_app_area);
        mBackgroundAppArea = findViewById(R.id.upper_app_area);
        mBackgroundActivityComponent = ComponentName.unflattenFromString(getResources().getString(
                R.string.config_backgroundActivity));
        mFullScreenActivities = convertToComponentNames(getResources()
                .getStringArray(R.array.config_fullScreenActivities));
        mDrawerActivities = convertToComponentNames(getResources()
                .getStringArray(R.array.config_drawerActivities));
        mShouldSetInsetsOnBackgroundTaskView = getResources().getBoolean(
                R.bool.config_setInsetsOnUpperTaskView);
        mGripBar = findViewById(R.id.grip_bar);
        mGripBarView = findViewById(R.id.grip_bar_view);

        mChevronUpDrawable = getDrawable(R.drawable.ic_chevron_up);
        mChevronDownDrawable = getDrawable(R.drawable.ic_chevron_down);

        mImmersiveButtonView = findViewById(R.id.immersive_button);
        if (mImmersiveButtonView != null) {
            mImmersiveButtonView.setImageDrawable(
                    mIsRootTaskViewFullScreen ? mChevronDownDrawable
                            : mChevronUpDrawable);
            mImmersiveButtonView.setOnClickListener(v -> {
                if (mIsRootTaskViewFullScreen) {
                    // notify systemUI to show bars
                    mIsRootTaskViewFullScreen = false;
                    updateUIState(STATE_OPEN, true);
                    mControlBarView.setVisibility(VISIBLE);
                    notifySystemUI(MSG_HIDE_SYSTEM_BAR_FOR_IMMERSIVE, boolToInt(false));
                } else {
                    // notify systemUI to hide bars
                    mControlBarView.setVisibility(INVISIBLE);
                    mIsRootTaskViewFullScreen = true;
                    notifySystemUI(MSG_HIDE_SYSTEM_BAR_FOR_IMMERSIVE, boolToInt(true));
                }
                mImmersiveButtonView.setImageDrawable(
                        mIsRootTaskViewFullScreen ? mChevronDownDrawable
                                : mChevronUpDrawable);
            });
        }
        mStatusBarHeight = getResources().getDimensionPixelSize(
                com.android.internal.R.dimen.status_bar_height);

        mControlBarView = findViewById(R.id.control_bar_area);
        mControlBarHeightMinusCornerRadius = mControlBarView.getHeight() - mCornerRadius;
        mControlBarView.addOnLayoutChangeListener(mControlBarOnLayoutChangeListener);
        String[] ignoreOpeningForegroundDACmp = getResources().getStringArray(
                R.array.config_ignoreOpeningForegroundDA);
        mIgnoreOpeningRootTaskViewComponentsSet = new ArraySet<>();
        for (String component : ignoreOpeningForegroundDACmp) {
            ComponentName componentName = ComponentName.unflattenFromString(component);
            mIgnoreOpeningRootTaskViewComponentsSet.add(componentName);
        }

        // Setting as trusted overlay to let touches pass through.
        getWindow().addPrivateFlags(PRIVATE_FLAG_TRUSTED_OVERLAY);
        // To pass touches to the underneath task.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL);

        mContainer.addOnLayoutChangeListener(
                (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                    boolean widthChanged = (right - left) != (oldRight - oldLeft);
                    boolean heightChanged = (bottom - top) != (oldBottom - oldTop);

                    if (widthChanged || heightChanged) {
                        onContainerDimensionsChanged();
                    }
                });

        // If we happen to be resurfaced into a multi display mode we skip launching content
        // in the activity view as we will get recreated anyway.
        if (isInMultiWindowMode() || isInPictureInPictureMode()) {
            return;
        }

        if (mTaskViewManager == null) {
            mTaskViewManager = new TaskViewManager(this, getMainThreadHandler());
        }

        if (mRootAppArea != null) {
            setUpRootTaskView(mRootAppArea);
        }

        if (mBackgroundAppArea != null) {
            setUpBackgroundTaskView(mBackgroundAppArea);
        }

        ViewGroup fullscreenContainer = findViewById(R.id.fullscreen_container);
        if (fullscreenContainer != null) {
            setUpFullScreenTaskView(fullscreenContainer);
        }

        requireViewById(android.R.id.content).post(() -> {
            mIsRootPanelInitialized = true;
            updateUIState(STATE_CLOSE, /* animate = */ false);
        });

        TaskStackChangeListeners.getInstance().registerTaskStackListener(mTaskStackListener);

        final float[] yValueWhenDownPressed = new float[1];
        final float[] rootAppAreaContainerInitialYVal = new float[1];
        findViewById(R.id.grip_bar).setOnTouchListener(new View.OnTouchListener() {
            final FrameLayout.LayoutParams mRootAppAreaParams =
                    (FrameLayout.LayoutParams) mRootAppAreaContainer.getLayoutParams();
            int mTopMargin = mRootAppAreaParams.topMargin;

            @Override
            public boolean onTouch(View view, MotionEvent event) {
                switch (event.getAction()) {

                    case MotionEvent.ACTION_DOWN:
                        yValueWhenDownPressed[0] = (int) event.getRawY();
                        rootAppAreaContainerInitialYVal[0] = (int) mRootAppAreaContainer.getY();

                        onContainerDimensionsChanged();
                        mTopMargin = mRootAppAreaParams.topMargin;

                        mGripBar.post(() -> updateBottomOverlap(STATE_OPEN));
                        break;
                    case MotionEvent.ACTION_MOVE:
                        int currY = (int) event.getRawY();
                        mIsAnimating = true;
                        int diff = currY - (int) yValueWhenDownPressed[0];
                        if (diff < 0) {
                            diff = 0;
                        }

                        mRootAppAreaParams.topMargin =
                                (int) rootAppAreaContainerInitialYVal[0] + diff;

                        mRootAppAreaContainer.setLayoutParams(mRootAppAreaParams);
                        break;
                    case MotionEvent.ACTION_UP:
                        float yEndVal = event.getRawY();
                        if (yEndVal > mContainer.getMeasuredHeight()) {
                            yEndVal = mContainer.getMeasuredHeight();
                        }

                        if (yEndVal > mTitleBarDragThreshold) {
                            mRootAppAreaParams.topMargin = mContainer.getMeasuredHeight();
                            mRootAppAreaState = STATE_CLOSE;
                            notifySystemUI(MSG_ROOT_TASK_VIEW_VISIBILITY_CHANGE, boolToInt(false));
                            mRootAppAreaContainer.setLayoutParams(mRootAppAreaParams);
                        } else {
                            mRootAppAreaParams.topMargin = mTopMargin;
                            mRootAppAreaState = STATE_OPEN;
                            mRootAppAreaContainer.setLayoutParams(mRootAppAreaParams);
                        }
                        mRootTaskView.setZOrderOnTop(false);
                        mIsAnimating = false;
                        // Do this on animation end
                        mGripBar.post(() -> updateBottomOverlap(mRootAppAreaState));
                        break;
                    default:
                        return false;
                }
                return true;
            }
        });

        updateVoicePlateActivityMap();
        initializeCards();
        doBindService();
    }

    private static ArraySet<ComponentName> convertToComponentNames(String[] componentStrings) {
        ArraySet<ComponentName> componentNames = new ArraySet<>(componentStrings.length);
        for (int i = componentStrings.length - 1; i >= 0; i--) {
            componentNames.add(ComponentName.unflattenFromString(componentStrings[i]));
        }
        return componentNames;
    }

    private void initializeCards() {
        mHomeCardModules = new androidx.collection.ArraySet<>();
        for (String providerClassName : getResources().getStringArray(
                R.array.config_homeCardModuleClasses)) {
            try {
                long reflectionStartTime = System.currentTimeMillis();
                HomeCardModule cardModule = (HomeCardModule) Class.forName(
                        providerClassName).newInstance();
                cardModule.setViewModelProvider(new ViewModelProvider(/* owner= */this));
                mHomeCardModules.add(cardModule);
                if (DBG) {
                    long reflectionTime = System.currentTimeMillis() - reflectionStartTime;
                    logIfDebuggable(
                            "Initialization of HomeCardModule class " + providerClassName
                                    + " took " + reflectionTime + " ms");
                }
            } catch (IllegalAccessException | InstantiationException
                     | ClassNotFoundException e) {
                Log.w(TAG, "Unable to create HomeCardProvider class " + providerClassName, e);
            }
        }
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        for (HomeCardModule cardModule : mHomeCardModules) {
            transaction.replace(cardModule.getCardResId(), cardModule.getCardView());
        }
        transaction.commitNow();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        initializeCards();
        refreshGrabBar();
    }

    private void refreshGrabBar() {
        Drawable background = getResources().getDrawable(R.drawable.title_bar_background);
        mGripBar.setBackground(background);
    }

    @Override
    protected void onDestroy() {
        mTaskViewManager = null;
        mRootTaskView = null;
        mBackgroundTaskView = null;
        mFullScreenTaskView = null;
        TaskStackChangeListeners.getInstance().unregisterTaskStackListener(mTaskStackListener);
        doUnbindService();
        super.onDestroy();
    }

    private void updateRootTaskViewVisibility(TaskInfo taskInfo) {
        logIfDebuggable("Task moved to front: " + taskInfo);

        if (!shouldTaskShowOnRootTaskView(taskInfo)) {
            logIfDebuggable("Not showing task in rootTaskView");
            if (isRootTaskViewEmpty()) {
                logIfDebuggable("Close the rootTaskView since it's empty");
                updateUIState(STATE_CLOSE, true);
            }
            return;
        }
        logIfDebuggable("Showing task in task view");
        // check if the foreground DA is visible to the user. If not, make it visible.
        if (mRootAppAreaState == STATE_CLOSE) {
            updateUIState(STATE_OPEN, /* animate = */ true);
        }
        // just let the task launch and don't change the state of the foreground DA.
    }

    private boolean isRootTaskViewEmpty() {
        TaskInfo taskInfo = mRootTaskView.getTaskInfo();
        logIfDebuggable("TaskInfo in rootTaskView = " + taskInfo);

        return taskInfo == null
                || taskInfo.baseActivity == null
                || !taskInfo.isRunning
                || !taskInfo.isVisible;
    }

    private boolean shouldTaskShowOnRootTaskView(TaskInfo taskInfo) {
        if (taskInfo.baseIntent == null || taskInfo.baseIntent.getComponent() == null
                || mIsAnimating) {
            logIfDebuggable("Should not show on root task view since task is null");
            return false;
        }

        ComponentName componentName = taskInfo.baseIntent.getComponent();

        // fullscreen activities will open in a separate task view which will show on top most
        // z-layer, that should not change the state of the root task view.
        if (mFullScreenActivities.contains(taskInfo.baseActivity)) {
            logIfDebuggable("Should not show on root task view since task is full screen activity");
            return false;
        }

        if (mDrawerActivities.contains(taskInfo.baseActivity)
                && mLastFrontActivityIsFullScreenActivity) {
            logIfDebuggable("Don't open drawer activity after full screen task hide");
            return false;
        }

        boolean isBackgroundApp = mBackgroundActivityComponent.equals(componentName);
        if (isBackgroundApp) {
            logIfDebuggable("Should not show on root task view since task is background activity");
            // we don't want to change the state of the root task view when background
            // task are launched or brought to front.
            return false;
        }

        // Any task that does NOT meet all the below criteria should be ignored.
        // 1. displayAreaFeatureId should be FEATURE_DEFAULT_TASK_CONTAINER
        // 2. should be visible
        // 3. for the current user ONLY. System user launches some tasks on cluster that should
        //    not affect the state of the foreground DA
        // 4. any task that is manually defined to be ignored
        return taskInfo.displayAreaFeatureId == FEATURE_DEFAULT_TASK_CONTAINER
                && taskInfo.userId == ActivityManager.getCurrentUser()
                && !shouldIgnoreOpeningForegroundDA(taskInfo);
    }

    boolean shouldIgnoreOpeningForegroundDA(TaskInfo taskInfo) {
        return taskInfo.baseIntent != null && mIgnoreOpeningRootTaskViewComponentsSet.contains(
                taskInfo.baseIntent.getComponent());
    }

    // TODO(b/257353761): refactor this method for better readbility
    private void updateUIState(int newRootAppAreaState, boolean animate) {
        logIfDebuggable(
                "updating UI state to: " + newRootAppAreaState + ", with animate = " + animate);

        if (!mIsRootPanelInitialized) {
            logIfDebuggable("Root panel hasn't inited");
            return;
        }
        int rootAppAreaTopMargin = 0;
        Runnable onAnimationEnd = null;
        // Change grip bar visibility before animationEnd for better animation
        mGripBar.setVisibility(newRootAppAreaState == STATE_FULL_WITHOUT_SYS_BAR ? GONE : VISIBLE);
        mControlBarView.setVisibility(
                newRootAppAreaState == STATE_FULL_WITHOUT_SYS_BAR ? GONE : VISIBLE);
        if (newRootAppAreaState == STATE_OPEN) {
            rootAppAreaTopMargin = mBackgroundAppAreaHeightWhenCollapsed - mGripBarHeight;

            // Animate the root app area first and then change the background app area size to avoid
            // black patch on screen.
            onAnimationEnd = () -> {
                notifySystemUI(MSG_HIDE_SYSTEM_BAR_FOR_IMMERSIVE, boolToInt(false));
                notifySystemUI(MSG_ROOT_TASK_VIEW_VISIBILITY_CHANGE, boolToInt(true));
                mGripBar.post(() -> updateBottomOverlap(STATE_OPEN));
                mRootTaskView.setZOrderOnTop(false);
                mIsAnimating = false;
            };
        } else if (newRootAppAreaState == STATE_CLOSE) {
            rootAppAreaTopMargin = mContainer.getMeasuredHeight();

            // Change the background app area's size to full-screen first and then animate the
            // root app
            // area to avoid black patch on screen.
            mGripBar.post(() -> updateBottomOverlap(STATE_CLOSE));

            onAnimationEnd = () -> {
                notifySystemUI(MSG_HIDE_SYSTEM_BAR_FOR_IMMERSIVE, boolToInt(false));
                notifySystemUI(MSG_ROOT_TASK_VIEW_VISIBILITY_CHANGE, boolToInt(false));
                mRootTaskView.setZOrderOnTop(false);
                mIsAnimating = false;
            };
        } else if (newRootAppAreaState == STATE_FULL_WITHOUT_SYS_BAR) {
            // Animate the root app area first and then change the background app area size to avoid
            // black patch on screen.
            onAnimationEnd = () -> {
                notifySystemUI(MSG_HIDE_SYSTEM_BAR_FOR_IMMERSIVE, boolToInt(false));
                notifySystemUI(MSG_ROOT_TASK_VIEW_VISIBILITY_CHANGE, boolToInt(true));
                mGripBar.post(() -> updateBottomOverlap(STATE_FULL_WITHOUT_SYS_BAR));
                mIsAnimating = false;
                setHomeScreenBottomMargin(0);
            };
        } else {
            // newRootAppAreaState == STATE_FULL_WITH_SYS_BAR
            rootAppAreaTopMargin = 0;

            // Animate the root app area first and then change the background app area size to avoid
            // black patch on screen.
            onAnimationEnd = () -> {
                notifySystemUI(MSG_ROOT_TASK_VIEW_VISIBILITY_CHANGE, boolToInt(true));
                notifySystemUI(MSG_HIDE_SYSTEM_BAR_FOR_IMMERSIVE, boolToInt(false));
                mGripBar.post(() -> updateBottomOverlap(STATE_FULL_WITH_SYS_BAR));
                mIsAnimating = false;
            };
        }
        logIfDebuggable("Root App Area top margin = " + rootAppAreaTopMargin);
        if (animate) {
            mIsAnimating = true;
            Animation animation = createAnimationForRootAppArea(rootAppAreaTopMargin,
                    onAnimationEnd);
            mRootAppAreaContainer.startAnimation(animation);
        } else {
            if (isFullScreen(newRootAppAreaState)) {
                logIfDebuggable("Make rootTaskView as big as Home Screen");
                makeRootAppAreaContainerSameHeightAsHomeScreen();
            } else {
                logIfDebuggable("Set the rootTaskView height with rootAppAreaTopMargin = "
                        + rootAppAreaTopMargin);
                resetRootTaskViewToDefaultHeight(rootAppAreaTopMargin);
            }
            onAnimationEnd.run();
        }

        mRootAppAreaState = newRootAppAreaState;
        mIsRootTaskViewFullScreen = isFullScreen(mRootAppAreaState);
    }

    private static boolean isFullScreen(int state) {
        return (state == STATE_FULL_WITHOUT_SYS_BAR || state == STATE_FULL_WITH_SYS_BAR);
    }

    private void makeRootAppAreaContainerSameHeightAsHomeScreen() {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams)
                mRootAppAreaContainer.getLayoutParams();
        // Set margin and padding to 0 so the SUW shows full screen
        logIfDebuggable("The height of mContainer is " + mContainer.getMeasuredHeight());
        lp.height = mContainer.getMeasuredHeight();
        lp.topMargin = 0;
        lp.bottomMargin = 0;
        mRootAppAreaContainer.setLayoutParams(lp);
        mRootAppAreaContainer.setPadding(0, 0, 0, 0);
    }

    private void resetRootTaskViewToDefaultHeight(int rootAppAreaTopMargin) {
        logIfDebuggable(
                "resetRootTaskViewToDefaultHeight with top margin = " + rootAppAreaTopMargin);

        FrameLayout.LayoutParams rootAppAreaParams =
                (FrameLayout.LayoutParams) mRootAppAreaContainer.getLayoutParams();
        rootAppAreaParams.height = mContainer.getMeasuredHeight()
                - mBackgroundAppAreaHeightWhenCollapsed + mGripBarHeight;
        rootAppAreaParams.topMargin = rootAppAreaTopMargin;

        mRootAppAreaContainer.setLayoutParams(rootAppAreaParams);

        setHomeScreenBottomMargin(mNavBarHeight);
        // Set bottom padding to be height of controller bar, so they won't overlap
        int rootAppAreaContainerBottomPadding =
                getApplicationContext().getResources().getDimensionPixelSize(
                        R.dimen.control_bar_height);
        mRootAppAreaContainer.setPadding(0, 0, 0, mControlBarHeightMinusCornerRadius);
    }

    private void setHomeScreenBottomMargin(int bottomMargin) {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) mContainer.getLayoutParams();
        lp.bottomMargin = bottomMargin;
        mContainer.setLayoutParams(lp);
    }

    private Animation createAnimationForRootAppArea(int newTop, Runnable onAnimationEnd) {
        logIfDebuggable("createAnimationForRowerAppArea, new top = " + newTop);
        FrameLayout.LayoutParams rootAppAreaParams =
                (FrameLayout.LayoutParams) mRootAppAreaContainer.getLayoutParams();
        Animation animation = new Animation() {
            @Override
            protected void applyTransformation(float interpolatedTime, Transformation t) {
                setTopMarginForView(
                        rootAppAreaParams.topMargin - (int) ((rootAppAreaParams.topMargin
                                - newTop) * interpolatedTime), mRootAppAreaContainer,
                        rootAppAreaParams);
            }
        };
        animation.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                onAnimationEnd.run();
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
            }
        });

        animation.setInterpolator(new DecelerateInterpolator());
        animation.setDuration(ANIMATION_DURATION_MS);
        return animation;
    }

    private void setTopMarginForView(int newTop, View view, FrameLayout.LayoutParams lp) {
        lp.topMargin = newTop;
        view.setLayoutParams(lp);
    }

    private void updateBottomOverlap(int newRootAppAreaState) {
        logIfDebuggable(
                "updateBottomOverlap with new root app area state = " + newRootAppAreaState);
        if (mBackgroundTaskView == null) {
            return;
        }

        Rect backgroundAppAreaBounds = new Rect();
        mBackgroundTaskView.getBoundsOnScreen(backgroundAppAreaBounds);

        Rect gripBarBounds = new Rect();
        mGripBar.getBoundsOnScreen(gripBarBounds);

        Region obscuredRegion = new Region(gripBarBounds.left, gripBarBounds.top,
                gripBarBounds.right, gripBarBounds.bottom);
        Rect controlBarBounds = new Rect();
        if (!isFullScreen(newRootAppAreaState)) {
            mControlBarView.getBoundsOnScreen(controlBarBounds);
            obscuredRegion.union(controlBarBounds);
        }

        // Use setObscuredTouchRect on all the taskviews that overlap with the grip bar.
        mBackgroundTaskView.setObscuredTouchRegion(obscuredRegion);
        mFullScreenTaskView.setObscuredTouchRegion(obscuredRegion);
        if (newRootAppAreaState == STATE_OPEN) {
            // Set control bar bounds as obscured region on RootTaskview when AppGrid launcher is
            // open.
            mRootTaskView.setObscuredTouchRect(controlBarBounds);
            applyBottomInsetsToBackgroundTaskView(
                    mRootAppAreaContainer.getHeight(),
                    backgroundAppAreaBounds);
        } else if (isFullScreen(newRootAppAreaState)) {
            mRootTaskView.setObscuredTouchRect(null);
            applyBottomInsetsToBackgroundTaskView(mNavBarHeight, backgroundAppAreaBounds);
        } else {
            // rootAppAreaState == STATE_CLOSE
            mRootTaskView.setObscuredTouchRect(null);
            applyBottomInsetsToBackgroundTaskView(mControlBarView.getHeight(),
                    backgroundAppAreaBounds);
        }
    }

    private void applyBottomInsetsToBackgroundTaskView(int bottomOverlap, Rect appAreaBounds) {
        if (mShouldSetInsetsOnBackgroundTaskView) {
            Rect bottomInsets = new Rect(appAreaBounds.left, appAreaBounds.bottom - bottomOverlap,
                    appAreaBounds.right, appAreaBounds.bottom);
            Rect topInsets = new Rect(appAreaBounds.left, appAreaBounds.top, appAreaBounds.right,
                    mStatusBarHeight);

            logIfDebuggable(
                    "Applying bottom insets: " + bottomInsets + " top insets: " + topInsets);
            mBackgroundTaskView.setInsets(new SparseArray<Rect>() {
                {
                    append(ITYPE_BOTTOM_GENERIC_OVERLAY, bottomInsets);
                    append(ITYPE_TOP_GENERIC_OVERLAY, topInsets);
                }
            });
        }
    }

    private void onContainerDimensionsChanged() {
        // Call updateUIState for the current state to recalculate the UI
        updateUIState(mRootAppAreaState, /* animate = */ false);
        if (mBackgroundTaskView != null) {
            mBackgroundTaskView.post(() -> mBackgroundTaskView.onLocationChanged());
        }
        if (mRootTaskView != null) {
            mRootTaskView.post(() -> mRootTaskView.onLocationChanged());
        }
    }

    private void setUpBackgroundTaskView(ViewGroup parent) {
        mTaskViewManager.createControlledCarTaskView(getMainExecutor(),
                CarLauncherUtils.getMapsIntent(getApplicationContext()),
                true,
                new ControlledCarTaskViewCallbacks() {
                    @Override
                    public void onTaskViewCreated(CarTaskView taskView) {
                        mBackgroundTaskView = taskView;
                        parent.addView(mBackgroundTaskView);
                    }

                    @Override
                    public void onTaskViewReady() {
                    }
                }
        );
    }

    private void setUpRootTaskView(ViewGroup parent) {
        mTaskViewManager.createLaunchRootTaskView(getMainExecutor(),
                new LaunchRootCarTaskViewCallbacks() {
                    @Override
                    public void onTaskViewCreated(CarTaskView taskView) {
                        mRootTaskView = taskView;
                        parent.addView(mRootTaskView);
                    }

                    @Override
                    public void onTaskViewReady() {
                        logIfDebuggable("Foreground Task View is ready");
                        notifySystemUI(MSG_FG_TASK_VIEW_READY, boolToInt(true));
                    }
                });
    }

    private void setUpFullScreenTaskView(ViewGroup parent) {
        mTaskViewManager.createSemiControlledTaskView(getMainExecutor(),
                new SemiControlledCarTaskViewCallbacks() {
                    @Override
                    public boolean shouldStartInTaskView(TaskInfo taskInfo) {
                        if (taskInfo.baseActivity == null) {
                            return false;
                        }
                        return mFullScreenActivities.contains(taskInfo.baseActivity);
                    }

                    @Override
                    public void onTaskViewCreated(CarTaskView taskView) {
                        mFullScreenTaskView = taskView;
                        mFullScreenTaskView.setZOrderOnTop(true);
                        parent.addView(mFullScreenTaskView);
                    }

                    @Override
                    public void onTaskViewReady() {
                    }
                });
    }

    private void onImmersiveModeRequested(boolean requested) {
        logIfDebuggable("onImmersiveModeRequested = " + requested);
        if (mGripBarView != null) {
            mGripBarView.setVisibility(requested ? GONE : View.VISIBLE);
        }
        if (mImmersiveButtonView != null) {
            mImmersiveButtonView.setVisibility(requested ? View.VISIBLE : GONE);
        }

        if (!requested) {
            updateUIState(mRootAppAreaState, false);
        }
    }

    /**
     * Handler of incoming messages from service.
     */
    class IncomingHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_IMMERSIVE_MODE_REQUESTED:
                    // TODO(b/257353761): replace this with updateUIState once the use case is
                    //  sorted out.
                    onImmersiveModeRequested(intToBool(msg.arg1));
                    break;
                case MSG_SUW_IN_PROGRESS:
                    mIsSUWInProgress = intToBool(msg.arg1);
                    logIfDebuggable("Get intent about the SUW is " + mIsSUWInProgress);
                    if (mIsSUWInProgress) {
                        updateUIState(STATE_FULL_WITHOUT_SYS_BAR, false);
                    } else {
                        updateUIState(STATE_CLOSE, false);
                    }
                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    }

    void doBindService() {
        // Establish a connection with {@link CarUiPortraitService}. We use an explicit class
        // name because there is no reason to be able to let other applications replace our
        // component.
        bindService(new Intent(this, CarUiPortraitService.class), mConnection,
                Context.BIND_AUTO_CREATE);
        mIsBound = true;
    }

    void doUnbindService() {
        if (mIsBound) {
            if (mService != null) {
                try {
                    Message msg = Message.obtain(null, MSG_UNREGISTER_CLIENT);
                    msg.replyTo = mMessenger;
                    mService.send(msg);
                } catch (RemoteException e) {
                    Log.w(TAG, "can't unregister to CarUiPortraitService: ", e);
                }
            }

            // Detach our existing connection.
            unbindService(mConnection);
            mIsBound = false;
        }
    }

    private void notifySystemUI(int key, int value) {
        Message msg = Message.obtain(null, key, value, 0);
        try {
            if (mService != null) {
                mService.send(msg);
            }
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    private static int boolToInt(Boolean b) {
        return b ? 1 : 0;
    }

    private static boolean intToBool(int val) {
        return val == 1;
    }
}
