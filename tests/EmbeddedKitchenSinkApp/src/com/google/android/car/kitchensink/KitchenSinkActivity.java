/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.google.android.car.kitchensink;

import android.annotation.Nullable;
import android.car.Car;
import android.car.CarProjectionManager;
import android.car.hardware.CarSensorManager;
import android.car.hardware.hvac.CarHvacManager;
import android.car.hardware.power.CarPowerManager;
import android.car.hardware.property.CarPropertyManager;
import android.car.os.CarPerformanceManager;
import android.car.telemetry.CarTelemetryManager;
import android.car.watchdog.CarWatchdogManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.car.kitchensink.activityresolver.ActivityResolverFragment;
import com.google.android.car.kitchensink.admin.DevicePolicyFragment;
import com.google.android.car.kitchensink.alertdialog.AlertDialogTestFragment;
import com.google.android.car.kitchensink.assistant.CarAssistantFragment;
import com.google.android.car.kitchensink.audio.AudioTestFragment;
import com.google.android.car.kitchensink.audio.CarAudioInputTestFragment;
import com.google.android.car.kitchensink.audiorecorder.AudioRecorderTestFragment;
import com.google.android.car.kitchensink.backup.BackupAndRestoreFragment;
import com.google.android.car.kitchensink.bluetooth.BluetoothHeadsetFragment;
import com.google.android.car.kitchensink.bluetooth.BluetoothUuidFragment;
import com.google.android.car.kitchensink.bluetooth.MapMceTestFragment;
import com.google.android.car.kitchensink.carboard.KeyboardTestFragment;
import com.google.android.car.kitchensink.cluster.InstrumentClusterFragment;
import com.google.android.car.kitchensink.connectivity.ConnectivityFragment;
import com.google.android.car.kitchensink.cube.CubesTestFragment;
import com.google.android.car.kitchensink.diagnostic.DiagnosticTestFragment;
import com.google.android.car.kitchensink.display.DisplayInfoFragment;
import com.google.android.car.kitchensink.display.DisplayMirroringFragment;
import com.google.android.car.kitchensink.display.VirtualDisplayFragment;
import com.google.android.car.kitchensink.experimental.ExperimentalFeatureTestFragment;
import com.google.android.car.kitchensink.hotword.CarMultiConcurrentHotwordTestFragment;
import com.google.android.car.kitchensink.hvac.HvacTestFragment;
import com.google.android.car.kitchensink.insets.WindowInsetsFullScreenFragment;
import com.google.android.car.kitchensink.mainline.CarMainlineFragment;
import com.google.android.car.kitchensink.notification.NotificationFragment;
import com.google.android.car.kitchensink.orientation.OrientationTestFragment;
import com.google.android.car.kitchensink.os.CarPerformanceTestFragment;
import com.google.android.car.kitchensink.packageinfo.PackageInfoFragment;
import com.google.android.car.kitchensink.power.PowerTestFragment;
import com.google.android.car.kitchensink.projection.ProjectionFragment;
import com.google.android.car.kitchensink.property.PropertyTestFragment;
import com.google.android.car.kitchensink.qc.QCViewerFragment;
import com.google.android.car.kitchensink.rotary.RotaryFragment;
import com.google.android.car.kitchensink.sensor.SensorsTestFragment;
import com.google.android.car.kitchensink.storagelifetime.StorageLifetimeFragment;
import com.google.android.car.kitchensink.storagevolumes.StorageVolumesFragment;
import com.google.android.car.kitchensink.systembars.SystemBarsFragment;
import com.google.android.car.kitchensink.systemfeatures.SystemFeaturesFragment;
import com.google.android.car.kitchensink.telemetry.CarTelemetryTestFragment;
import com.google.android.car.kitchensink.touch.TouchTestFragment;
import com.google.android.car.kitchensink.users.ProfileUserFragment;
import com.google.android.car.kitchensink.users.SimpleUserPickerFragment;
import com.google.android.car.kitchensink.users.UserFragment;
import com.google.android.car.kitchensink.users.UserRestrictionsFragment;
import com.google.android.car.kitchensink.vehiclectrl.VehicleCtrlFragment;
import com.google.android.car.kitchensink.volume.VolumeTestFragment;
import com.google.android.car.kitchensink.watchdog.CarWatchdogTestFragment;
import com.google.android.car.kitchensink.weblinks.WebLinksTestFragment;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class KitchenSinkActivity extends FragmentActivity {
    private static final String TAG = "KitchenSinkActivity";
    private static final String LAST_FRAGMENT_TAG = "lastFragmentTag";
    private static final String DEFAULT_FRAGMENT_TAG = "";
    private RecyclerView mMenu;
    private LinearLayout mHeader;
    private Button mMenuButton;
    private View mKitchenContent;
    private String mLastFragmentTag = DEFAULT_FRAGMENT_TAG;
    @Nullable
    private Fragment mLastFragment;

    public static final String DUMP_ARG_CMD = "cmd";
    public static final String DUMP_ARG_FRAGMENT = "fragment";
    public static final String DUMP_ARG_QUIET = "quiet";

    private interface ClickHandler {
        void onClick();
    }

    private static abstract class MenuEntry implements ClickHandler {
        abstract String getText();

        public void dump(String prefix, FileDescriptor fd, PrintWriter writer, String[] args) {
            writer.printf("%s doesn't implement dump()\n", this);
        }
    }

    private final class OnClickMenuEntry extends MenuEntry {
        private final String mText;
        private final ClickHandler mClickHandler;

        OnClickMenuEntry(String text, ClickHandler clickHandler) {
            mText = text;
            mClickHandler = clickHandler;
        }

        @Override
        String getText() {
            return mText;
        }

        @Override
        public void onClick() {
            toggleMenuVisibility();
            mClickHandler.onClick();
        }
    }

    private final class FragmentMenuEntry<T extends Fragment> extends MenuEntry {
        private final class FragmentClassOrInstance<T extends Fragment> {
            final Class<T> mClazz;
            T mFragment = null;

            FragmentClassOrInstance(Class<T> clazz) {
                mClazz = clazz;
            }

            T getFragment() {
                if (mFragment == null) {
                    try {
                        mFragment = mClazz.newInstance();
                    } catch (InstantiationException | IllegalAccessException e) {
                        Log.e(TAG, "unable to create fragment", e);
                    }
                }
                return mFragment;
            }
        }

        private final String mText;
        private final FragmentClassOrInstance<T> mFragment;

        FragmentMenuEntry(String text, Class<T> clazz) {
            mText = text;
            mFragment = new FragmentClassOrInstance<>(clazz);
        }

        @Override
        String getText() {
            return mText;
        }

        @Override
        public void onClick() {
            Fragment fragment = mFragment.getFragment();
            if (fragment != null) {
                KitchenSinkActivity.this.showFragment(fragment);
                toggleMenuVisibility();
                mLastFragmentTag = fragment.getTag();
            } else {
                Log.e(TAG, "cannot show fragment for " + getText());
            }
        }

        @Override
        public void dump(String prefix, FileDescriptor fd, PrintWriter writer, String[] args) {
            Fragment fragment = mFragment.getFragment();
            if (fragment != null) {
                fragment.dump(prefix, fd, writer, args);
            } else {
                writer.printf("Cannot dump %s\n", getText());
            }
        }
    }

    private final List<MenuEntry> mMenuEntries = Arrays.asList(
            new FragmentMenuEntry("activity resolver", ActivityResolverFragment.class),
            new FragmentMenuEntry("alert window", AlertDialogTestFragment.class),
            new FragmentMenuEntry("assistant", CarAssistantFragment.class),
            new FragmentMenuEntry(AudioTestFragment.FRAGMENT_NAME, AudioTestFragment.class),
            new FragmentMenuEntry(AudioRecorderTestFragment.FRAGMENT_NAME,
                    AudioRecorderTestFragment.class),
            new FragmentMenuEntry(CarAudioInputTestFragment.FRAGMENT_NAME,
                    CarAudioInputTestFragment.class),
            new FragmentMenuEntry("Hotword", CarMultiConcurrentHotwordTestFragment.class),
            new FragmentMenuEntry("B&R", BackupAndRestoreFragment.class),
            new FragmentMenuEntry("BT headset", BluetoothHeadsetFragment.class),
            new FragmentMenuEntry("BT messaging", MapMceTestFragment.class),
            new FragmentMenuEntry("BT Uuids", BluetoothUuidFragment.class),
            new FragmentMenuEntry("carapi", CarApiTestFragment.class),
            new FragmentMenuEntry("carboard", KeyboardTestFragment.class),
            new FragmentMenuEntry("connectivity", ConnectivityFragment.class),
            new FragmentMenuEntry("cubes test", CubesTestFragment.class),
            new FragmentMenuEntry("device policy", DevicePolicyFragment.class),
            new FragmentMenuEntry("diagnostic", DiagnosticTestFragment.class),
            new FragmentMenuEntry("display info", DisplayInfoFragment.class),
            new FragmentMenuEntry("display mirroring", DisplayMirroringFragment.class),
            new FragmentMenuEntry("experimental feature", ExperimentalFeatureTestFragment.class),
            new FragmentMenuEntry("hvac", HvacTestFragment.class),
            new FragmentMenuEntry("inst cluster", InstrumentClusterFragment.class),
            new FragmentMenuEntry("mainline", CarMainlineFragment.class),
            new FragmentMenuEntry("notification", NotificationFragment.class),
            new FragmentMenuEntry("orientation test", OrientationTestFragment.class),
            new FragmentMenuEntry("package info", PackageInfoFragment.class),
            new FragmentMenuEntry("performance", CarPerformanceTestFragment.class),
            new FragmentMenuEntry("power test", PowerTestFragment.class),
            new FragmentMenuEntry("profile_user", ProfileUserFragment.class),
            new FragmentMenuEntry("projection", ProjectionFragment.class),
            new FragmentMenuEntry("property test", PropertyTestFragment.class),
            new FragmentMenuEntry("qc viewer", QCViewerFragment.class),
            new FragmentMenuEntry("rotary", RotaryFragment.class),
            new FragmentMenuEntry("sensors", SensorsTestFragment.class),
            new FragmentMenuEntry("user picker", SimpleUserPickerFragment.class),
            new FragmentMenuEntry("storage lifetime", StorageLifetimeFragment.class),
            new FragmentMenuEntry("storage volumes", StorageVolumesFragment.class),
            new FragmentMenuEntry("system bars", SystemBarsFragment.class),
            new FragmentMenuEntry("system features", SystemFeaturesFragment.class),
            new FragmentMenuEntry("telemetry", CarTelemetryTestFragment.class),
            new FragmentMenuEntry("touch test", TouchTestFragment.class),
            new FragmentMenuEntry("users", UserFragment.class),
            new FragmentMenuEntry("user restrictions", UserRestrictionsFragment.class),
            new FragmentMenuEntry("vehicle ctrl", VehicleCtrlFragment.class),
            new FragmentMenuEntry(VirtualDisplayFragment.FRAGMENT_NAME,
                    VirtualDisplayFragment.class),
            new FragmentMenuEntry("volume test", VolumeTestFragment.class),
            new FragmentMenuEntry("watchdog", CarWatchdogTestFragment.class),
            new FragmentMenuEntry("web links", WebLinksTestFragment.class),
            new FragmentMenuEntry("window insets full screen",
                    WindowInsetsFullScreenFragment.class));

    private Car mCarApi;
    private CarHvacManager mHvacManager;
    private CarPowerManager mPowerManager;
    private CarPropertyManager mPropertyManager;
    private CarSensorManager mSensorManager;
    private CarProjectionManager mCarProjectionManager;
    private CarTelemetryManager mCarTelemetryManager;
    private CarWatchdogManager mCarWatchdogManager;
    private CarPerformanceManager mCarPerformanceManager;
    private Object mPropertyManagerReady = new Object();

    public KitchenSinkActivity() {
        mMenuEntries.sort(Comparator.comparing(MenuEntry::getText));
    }

    public CarHvacManager getHvacManager() {
        return mHvacManager;
    }

    public CarPowerManager getPowerManager() {
        return mPowerManager;
    }

    public CarPropertyManager getPropertyManager() {
        return mPropertyManager;
    }

    public CarSensorManager getSensorManager() {
        return mSensorManager;
    }

    public CarProjectionManager getProjectionManager() {
        return mCarProjectionManager;
    }

    public CarTelemetryManager getCarTelemetryManager() {
        return mCarTelemetryManager;
    }

    public CarWatchdogManager getCarWatchdogManager() {
        return mCarWatchdogManager;
    }

    public CarPerformanceManager getPerformanceManager() {
        return mCarPerformanceManager;
    }

    /* Open any tab directly:
     * adb shell am force-stop com.google.android.car.kitchensink
     * adb shell am 'start -n com.google.android.car.kitchensink/.KitchenSinkActivity \
     *     --es select "connectivity"'
     *
     * Test car watchdog:
     * adb shell am force-stop com.google.android.car.kitchensink
     * adb shell am start -n com.google.android.car.kitchensink/.KitchenSinkActivity \
     *     --es "watchdog" "[timeout] [not_respond_after] [inactive_main_after] [verbose]"
     * - timeout: critical | moderate | normal
     * - not_respond_after: after the given seconds, the client will not respond to car watchdog
     *                      (-1 for making the client respond always)
     * - inactive_main_after: after the given seconds, the main thread will not be responsive
     *                        (-1 for making the main thread responsive always)
     * - verbose: whether to output verbose logs (default: false)
     */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Log.i(TAG, "onNewIntent");
        Bundle extras = intent.getExtras();
        if (extras == null) {
            return;
        }
        String watchdog = extras.getString("watchdog");
        if (watchdog != null) {
            CarWatchdogClient.start(getCar(), watchdog);
        }
        String select = extras.getString("select");
        if (select != null) {
            Log.d(TAG, "Trying to launch entry '" + select + "'");
            mMenuEntries.stream().filter(me -> select.equals(me.getText()))
                    .findAny().ifPresent(me -> me.onClick());
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.kitchen_activity);

        // Connection to Car Service does not work for non-automotive yet.
        if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            initCarApi();
        }

        mKitchenContent = findViewById(R.id.kitchen_content);

        mMenu = findViewById(R.id.menu);
        mMenu.setAdapter(new MenuAdapter(this));
        mMenu.setLayoutManager(new GridLayoutManager(this, 4));

        mMenuButton = findViewById(R.id.menu_button);
        mMenuButton.setOnClickListener(view -> toggleMenuVisibility());

        mHeader = findViewById(R.id.header);

        Log.i(TAG, "onCreate");
        onNewIntent(getIntent());
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        // The app is being started for the first time.
        if (savedInstanceState == null) {
            return;
        }

        // The app is being reloaded, restores the last fragment UI.
        mLastFragmentTag = savedInstanceState.getString(LAST_FRAGMENT_TAG);
        if (!DEFAULT_FRAGMENT_TAG.equals(mLastFragmentTag)) {
            toggleMenuVisibility();
        }
    }

    private void toggleMenuVisibility() {
        boolean menuVisible = mMenu.getVisibility() == View.VISIBLE;
        mMenu.setVisibility(menuVisible ? View.GONE : View.VISIBLE);
        int contentVisibility = menuVisible ? View.VISIBLE : View.GONE;
        mKitchenContent.setVisibility(contentVisibility);
        mMenuButton.setText(menuVisible ? "Show KitchenSink Menu" : "Hide KitchenSink Menu");
        if (mLastFragment != null) {
            mLastFragment.onHiddenChanged(!menuVisible);
        }
    }

    /**
     * Sets the visibility of the header that's shown on all fragments.
     */
    public void setHeaderVisibility(boolean visible) {
        mHeader.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    /**
     * Adds a view to the main header (which by default contains the "show/ hide KS menu" button).
     */
    public void addHeaderView(View view) {
        Log.d(TAG, "Adding header view: " + view);
        mHeader.addView(view, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void initCarApi() {
        if (mCarApi != null && mCarApi.isConnected()) {
            mCarApi.disconnect();
            mCarApi = null;
        }
        mCarApi = Car.createCar(this, null, Car.CAR_WAIT_TIMEOUT_WAIT_FOREVER,
                (Car car, boolean ready) -> {
                    if (ready) {
                        initManagers(car);
                    }
                });
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.i(TAG, "onStart");
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        Log.i(TAG, "onRestart");
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.i(TAG, "onResume");
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putString(LAST_FRAGMENT_TAG, mLastFragmentTag);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.i(TAG, "onPause");
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.i(TAG, "onStop");
    }

    @Override
    protected void onDestroy() {
        if (mCarApi != null) {
            mCarApi.disconnect();
        }
        Log.i(TAG, "onDestroy");
        super.onDestroy();
    }

    @Override
    public void dump(String prefix, FileDescriptor fd, PrintWriter writer, String[] args) {
        boolean skipParentState = false;
        if (args != null && args.length > 0) {
            Log.v(TAG, "dump: args=" + Arrays.toString(args));
            String arg = args[0];
            switch (arg) {
                case DUMP_ARG_CMD:
                    String[] cmdArgs = new String[args.length - 1];
                    System.arraycopy(args, 1, cmdArgs, 0, args.length - 1);
                    new KitchenSinkShellCommand(this, writer, cmdArgs).run();
                    return;
                case DUMP_ARG_FRAGMENT:
                    if (args.length < 2) {
                        writer.println("Missing fragment name");
                        return;
                    }
                    String select = args[1];
                    Optional<MenuEntry> entry = mMenuEntries.stream()
                            .filter(me -> select.equals(me.getText())).findAny();
                    if (entry.isPresent()) {
                        String[] strippedArgs = new String[args.length - 2];
                        System.arraycopy(args, 2, strippedArgs, 0, strippedArgs.length);
                        entry.get().dump(prefix, fd, writer, strippedArgs);
                    } else {
                        writer.printf("No entry called '%s'\n", select);
                    }
                    return;
                case DUMP_ARG_QUIET:
                    skipParentState = true;
                    break;
                default:
                    Log.v(TAG, "dump(): unknown arg, calling super(): " + Arrays.toString(args));
            }
        }
        String innerPrefix = prefix;
        if (!skipParentState) {
            writer.printf("%sCustom state:\n", prefix);
            innerPrefix = prefix + prefix;
        }
        writer.printf("%smLastFragmentTag: %s\n", innerPrefix, mLastFragmentTag);
        writer.printf("%smLastFragment: %s\n", innerPrefix, mLastFragment);
        writer.printf("%sHeader views: %d\n", innerPrefix, mHeader.getChildCount());

        if (skipParentState) {
            Log.v(TAG, "dump(): skipping parent state");
            return;
        }
        writer.println();

        super.dump(prefix, fd, writer, args);
    }

    private void showFragment(Fragment fragment) {
        if (mLastFragment != fragment) {
            Log.v(TAG, "showFragment(): from " + mLastFragment + " to " + fragment);
        } else {
            Log.v(TAG, "showFragment(): showing " + fragment + " again");
        }
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.kitchen_content, fragment)
                .commit();
        mLastFragment = fragment;
    }

    private void initManagers(Car car) {
        synchronized (mPropertyManagerReady) {
            mHvacManager = (CarHvacManager) car.getCarManager(
                    android.car.Car.HVAC_SERVICE);
            mPowerManager = (CarPowerManager) car.getCarManager(
                    android.car.Car.POWER_SERVICE);
            mPropertyManager = (CarPropertyManager) car.getCarManager(
                    android.car.Car.PROPERTY_SERVICE);
            mSensorManager = (CarSensorManager) car.getCarManager(
                    android.car.Car.SENSOR_SERVICE);
            mCarProjectionManager =
                    (CarProjectionManager) car.getCarManager(Car.PROJECTION_SERVICE);
            mCarTelemetryManager =
                    (CarTelemetryManager) car.getCarManager(Car.CAR_TELEMETRY_SERVICE);
            mCarWatchdogManager =
                    (CarWatchdogManager) car.getCarManager(Car.CAR_WATCHDOG_SERVICE);
            mCarPerformanceManager =
                    (CarPerformanceManager) car.getCarManager(Car.CAR_PERFORMANCE_SERVICE);
            mPropertyManagerReady.notifyAll();
        }
    }

    public Car getCar() {
        return mCarApi;
    }

    private final class MenuAdapter extends RecyclerView.Adapter<ItemViewHolder> {

        private final LayoutInflater mLayoutInflator;

        MenuAdapter(Context context) {
            mLayoutInflator = LayoutInflater.from(context);
        }

        @Override
        public ItemViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = mLayoutInflator.inflate(R.layout.menu_item, parent, false);
            return new ItemViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ItemViewHolder holder, int position) {
            holder.mTitle.setText(mMenuEntries.get(position).getText());
            holder.mTitle.setOnClickListener(v -> mMenuEntries.get(position).onClick());
        }

        @Override
        public int getItemCount() {
            return mMenuEntries.size();
        }
    }

    private final class ItemViewHolder extends RecyclerView.ViewHolder {
        TextView mTitle;

        ItemViewHolder(View itemView) {
            super(itemView);
            mTitle = itemView.findViewById(R.id.title);
        }
    }

    // Use AsyncTask to refresh Car*Manager after car service connected
    public void requestRefreshManager(final Runnable r, final Handler h) {
        final AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... unused) {
                synchronized (mPropertyManagerReady) {
                    while (!mCarApi.isConnected()) {
                        try {
                            mPropertyManagerReady.wait();
                        } catch (InterruptedException e) {
                            return null;
                        }
                    }
                }
                return null;
            }

            @Override
            protected void onPostExecute(Void unused) {
                h.post(r);
            }
        };
        task.execute();
    }
}
