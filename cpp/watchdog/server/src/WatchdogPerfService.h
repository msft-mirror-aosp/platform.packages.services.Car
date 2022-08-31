/*
 * Copyright (c) 2020, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef CPP_WATCHDOG_SERVER_SRC_WATCHDOGPERFSERVICE_H_
#define CPP_WATCHDOG_SERVER_SRC_WATCHDOGPERFSERVICE_H_

#include "LooperWrapper.h"
#include "ProcDiskStatsCollector.h"
#include "ProcStatCollector.h"
#include "UidStatsCollector.h"

#include <WatchdogProperties.sysprop.h>
#include <aidl/android/automotive/watchdog/internal/UserState.h>
#include <android-base/chrono_utils.h>
#include <android-base/result.h>
#include <cutils/multiuser.h>
#include <gtest/gtest_prod.h>
#include <utils/Errors.h>
#include <utils/Looper.h>
#include <utils/Mutex.h>
#include <utils/RefBase.h>
#include <utils/String16.h>
#include <utils/StrongPointer.h>
#include <utils/Vector.h>

#include <time.h>

#include <string>
#include <thread>  // NOLINT(build/c++11)
#include <unordered_set>

namespace android {
namespace automotive {
namespace watchdog {

// Forward declaration for testing use only.
namespace internal {

class WatchdogPerfServicePeer;

}  // namespace internal

constexpr std::chrono::seconds kDefaultPostSystemEventDurationSec = 30s;
constexpr std::chrono::seconds kDefaultUserSwitchTimeoutSec = 30s;
constexpr const char* kStartCustomCollectionFlag = "--start_perf";
constexpr const char* kEndCustomCollectionFlag = "--stop_perf";
constexpr const char* kIntervalFlag = "--interval";
constexpr const char* kMaxDurationFlag = "--max_duration";
constexpr const char* kFilterPackagesFlag = "--filter_packages";

enum SystemState {
    NORMAL_MODE = 0,
    GARAGE_MODE = 1,
};

/**
 * DataProcessor defines methods that must be implemented in order to process the data collected
 * by |WatchdogPerfService|.
 */
class DataProcessorInterface : virtual public android::RefBase {
public:
    DataProcessorInterface() {}
    virtual ~DataProcessorInterface() {}
    // Returns the name of the data processor.
    virtual std::string name() const = 0;
    // Callback to initialize the data processor.
    virtual android::base::Result<void> init() = 0;
    // Callback to terminate the data processor.
    virtual void terminate() = 0;
    // Callback to process the data collected during boot-time.
    virtual android::base::Result<void> onBoottimeCollection(
            time_t time, const android::wp<UidStatsCollectorInterface>& uidStatsCollector,
            const android::wp<ProcStatCollectorInterface>& procStatCollector) = 0;
    // Callback to process the data collected periodically post boot complete.
    virtual android::base::Result<void> onPeriodicCollection(
            time_t time, SystemState systemState,
            const android::wp<UidStatsCollectorInterface>& uidStatsCollector,
            const android::wp<ProcStatCollectorInterface>& procStatCollector) = 0;
    // Callback to process the data collected during user switch.
    virtual android::base::Result<void> onUserSwitchCollection(
            time_t time, userid_t from, userid_t to,
            const android::wp<UidStatsCollectorInterface>& uidStatsCollector,
            const android::wp<ProcStatCollectorInterface>& procStatCollector) = 0;
    /**
     * Callback to process the data collected on custom collection and filter the results only to
     * the specified |filterPackages|.
     */
    virtual android::base::Result<void> onCustomCollection(
            time_t time, SystemState systemState,
            const std::unordered_set<std::string>& filterPackages,
            const android::wp<UidStatsCollectorInterface>& uidStatsCollector,
            const android::wp<ProcStatCollectorInterface>& procStatCollector) = 0;
    /**
     * Callback to periodically monitor the collected data and trigger the given |alertHandler|
     * on detecting resource overuse.
     */
    virtual android::base::Result<void> onPeriodicMonitor(
            time_t time, const android::wp<ProcDiskStatsCollectorInterface>& procDiskStatsCollector,
            const std::function<void()>& alertHandler) = 0;
    // Callback to dump the boot-time collected and periodically collected data.
    virtual android::base::Result<void> onDump(int fd) const = 0;
    /**
     * Callback to dump the custom collected data. When fd == -1, clear the custom collection cache.
     */
    virtual android::base::Result<void> onCustomCollectionDump(int fd) = 0;
};

enum EventType {
    // WatchdogPerfService's state.
    INIT = 0,
    TERMINATED,

    // Collection events.
    BOOT_TIME_COLLECTION,
    PERIODIC_COLLECTION,
    USER_SWITCH_COLLECTION,
    CUSTOM_COLLECTION,

    // Monitor event.
    PERIODIC_MONITOR,

    LAST_EVENT,
};

enum SwitchMessage {
    /**
     * On receiving this message, collect the last boot-time record and start periodic collection
     * and monitor.
     */
    END_BOOTTIME_COLLECTION = EventType::LAST_EVENT + 1,

    /**
     * On receiving this message, collect the last user switch record and start periodic collection
     * and monitor.
     */
    END_USER_SWITCH_COLLECTION,

    /**
     * On receiving this message, ends custom collection, discard collected data and start periodic
     * collection and monitor.
     */
    END_CUSTOM_COLLECTION,
};

/**
 * WatchdogPerfServiceInterface collects performance data during boot-time and periodically post
 * boot complete. It exposes APIs that the main thread and binder service can call to start a
 * collection, switch the collection type, and generate collection dumps.
 */
class WatchdogPerfServiceInterface : virtual public MessageHandler {
public:
    // Register a data processor to process the data collected by |WatchdogPerfService|.
    virtual android::base::Result<void> registerDataProcessor(
            android::sp<DataProcessorInterface> processor) = 0;
    /**
     * Starts the boot-time collection in the looper handler on a new thread and returns
     * immediately. Must be called only once. Otherwise, returns an error.
     */
    virtual android::base::Result<void> start() = 0;
    // Terminates the collection thread and returns.
    virtual void terminate() = 0;
    // Sets the system state.
    virtual void setSystemState(SystemState systemState) = 0;
    // Ends the boot-time collection by switching to periodic collection after the post event
    // duration.
    virtual android::base::Result<void> onBootFinished() = 0;
    // Starts and ends the user switch collection depending on the user states received.
    virtual android::base::Result<void> onUserStateChange(
            userid_t userId,
            const aidl::android::automotive::watchdog::internal::UserState& userState) = 0;

    /**
     * Depending on the arguments, it either:
     * 1. Starts a custom collection.
     * 2. Or ends the current custom collection and dumps the collected data.
     * Returns any error observed during the dump generation.
     */
    virtual android::base::Result<void> onCustomCollection(int fd, const char** args,
                                                           uint32_t numArgs) = 0;
    // Generates a dump from the boot-time and periodic collection events.
    virtual android::base::Result<void> onDump(int fd) const = 0;
    // Dumps the help text.
    virtual bool dumpHelpText(int fd) const = 0;
};

class WatchdogPerfService final : public WatchdogPerfServiceInterface {
public:
    WatchdogPerfService() :
          mPostSystemEventDurationNs(std::chrono::duration_cast<std::chrono::nanoseconds>(
                  std::chrono::seconds(sysprop::postSystemEventDuration().value_or(
                          kDefaultPostSystemEventDurationSec.count())))),
          mUserSwitchTimeoutNs(std::chrono::duration_cast<std::chrono::nanoseconds>(
                  std::chrono::seconds(sysprop::userSwitchTimeout().value_or(
                          kDefaultUserSwitchTimeoutSec.count())))),
          mHandlerLooper(android::sp<LooperWrapper>::make()),
          mSystemState(NORMAL_MODE),
          mBoottimeCollection({}),
          mPeriodicCollection({}),
          mUserSwitchCollection({}),
          mCustomCollection({}),
          mPeriodicMonitor({}),
          mCurrCollectionEvent(EventType::INIT),
          mUidStatsCollector(android::sp<UidStatsCollector>::make()),
          mProcStatCollector(android::sp<ProcStatCollector>::make()),
          mProcDiskStatsCollector(android::sp<ProcDiskStatsCollector>::make()),
          mDataProcessors({}) {}

    android::base::Result<void> registerDataProcessor(
            android::sp<DataProcessorInterface> processor) override;

    android::base::Result<void> start() override;

    void terminate() override;

    void setSystemState(SystemState systemState) override;

    android::base::Result<void> onBootFinished() override;

    android::base::Result<void> onUserStateChange(
            userid_t userId,
            const aidl::android::automotive::watchdog::internal::UserState& userState) override;

    android::base::Result<void> onCustomCollection(int fd, const char** args,
                                                   uint32_t numArgs) override;

    android::base::Result<void> onDump(int fd) const override;

    bool dumpHelpText(int fd) const override;

private:
    struct EventMetadata {
        // Collection or monitor event.
        EventType eventType = EventType::LAST_EVENT;
        // Interval between subsequent events.
        std::chrono::nanoseconds interval = 0ns;
        // Used to calculate the uptime for next event.
        nsecs_t lastUptime = 0;
        // Filter the results only to the specified packages.
        std::unordered_set<std::string> filterPackages;

        std::string toString() const;
    };

    struct UserSwitchEventMetadata : WatchdogPerfService::EventMetadata {
        // User id of user being switched from.
        userid_t from = 0;
        // User id of user being switched to.
        userid_t to = 0;
    };

    // Dumps the collectors' status when they are disabled.
    android::base::Result<void> dumpCollectorsStatusLocked(int fd) const;

    /**
     * Starts a custom collection on the looper handler, temporarily stops the periodic collection
     * (won't discard the collected data), and returns immediately. Returns any error observed
     * during this process.
     * The custom collection happens once every |interval| seconds. When the |maxDuration| is
     * reached, the looper receives a message to end the collection, discards the collected data,
     * and starts the periodic collection. This is needed to ensure the custom collection doesn't
     * run forever when a subsequent |endCustomCollection| call is not received.
     * When |kFilterPackagesFlag| value specified, the results are filtered only to the specified
     * package names.
     */
    android::base::Result<void> startCustomCollection(
            std::chrono::nanoseconds interval, std::chrono::nanoseconds maxDuration,
            const std::unordered_set<std::string>& filterPackages);

    /**
     * Ends the current custom collection, generates a dump, sends a looper message to start the
     * periodic collection, and returns immediately. Returns an error when there is no custom
     * collection running or when a dump couldn't be generated from the custom collection.
     */
    android::base::Result<void> endCustomCollection(int fd);

    // Start a user switch collection.
    android::base::Result<void> startUserSwitchCollection();

    // Handles the messages received by the lopper.
    void handleMessage(const Message& message) override;

    // Processes the collection events received by |handleMessage|.
    android::base::Result<void> processCollectionEvent(EventMetadata* metadata);

    // Collects/processes the performance data for the current collection event.
    android::base::Result<void> collectLocked(EventMetadata* metadata);

    // Processes the monitor events received by |handleMessage|.
    android::base::Result<void> processMonitorEvent(EventMetadata* metadata);

    /**
     * Returns the metadata for the current collection based on |mCurrCollectionEvent|. Returns
     * nullptr on invalid collection event.
     */
    EventMetadata* currCollectionMetadataLocked();

    // Duration to extend a system event collection after the final signal is received.
    std::chrono::nanoseconds mPostSystemEventDurationNs;

    // Timeout duration for user switch collection in case final signal isn't received.
    std::chrono::nanoseconds mUserSwitchTimeoutNs;

    // Thread on which the actual collection happens.
    std::thread mCollectionThread;

    // Makes sure only one collection is running at any given time.
    mutable Mutex mMutex;

    // Handler looper to execute different collection events on the collection thread.
    android::sp<LooperWrapper> mHandlerLooper GUARDED_BY(mMutex);

    // Current system state.
    SystemState mSystemState GUARDED_BY(mMutex);

    // Info for the |EventType::BOOT_TIME_COLLECTION| collection event.
    EventMetadata mBoottimeCollection GUARDED_BY(mMutex);

    // Info for the |EventType::PERIODIC_COLLECTION| collection event.
    EventMetadata mPeriodicCollection GUARDED_BY(mMutex);

    // Info for the |EventType::USER_SWITCH_COLLECTION| collection event.
    UserSwitchEventMetadata mUserSwitchCollection GUARDED_BY(mMutex);

    // Info for the |EventType::CUSTOM_COLLECTION| collection event. The info is cleared at the end
    // of every custom collection.
    EventMetadata mCustomCollection GUARDED_BY(mMutex);

    // Info for the |EventType::PERIODIC_MONITOR| monitor event.
    EventMetadata mPeriodicMonitor GUARDED_BY(mMutex);

    // Tracks either the WatchdogPerfService's state or current collection event. Updated on
    // |start|, |onBootFinished|, |onUserStateChange|, |startCustomCollection|,
    // |endCustomCollection|, and |terminate|.
    EventType mCurrCollectionEvent GUARDED_BY(mMutex);

    // Collector for UID process and I/O stats.
    android::sp<UidStatsCollectorInterface> mUidStatsCollector GUARDED_BY(mMutex);

    // Collector/parser for `/proc/stat`.
    android::sp<ProcStatCollectorInterface> mProcStatCollector GUARDED_BY(mMutex);

    // Collector/parser for `/proc/diskstats` file.
    android::sp<ProcDiskStatsCollectorInterface> mProcDiskStatsCollector GUARDED_BY(mMutex);

    // Data processors for the collected performance data.
    std::vector<android::sp<DataProcessorInterface>> mDataProcessors GUARDED_BY(mMutex);

    // For unit tests.
    friend class internal::WatchdogPerfServicePeer;
    FRIEND_TEST(WatchdogPerfServiceTest, TestServiceStartAndTerminate);
};

}  // namespace watchdog
}  // namespace automotive
}  // namespace android

#endif  //  CPP_WATCHDOG_SERVER_SRC_WATCHDOGPERFSERVICE_H_
