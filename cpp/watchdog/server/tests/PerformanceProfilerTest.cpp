/*
 * Copyright 2020 The Android Open Source Project
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

#include "MockProcStatCollector.h"
#include "MockUidStatsCollector.h"
#include "MockWatchdogServiceHelper.h"
#include "PackageInfoTestUtils.h"
#include "PerformanceProfiler.h"

#include <WatchdogProperties.sysprop.h>
#include <android-base/file.h>
#include <gmock/gmock.h>
#include <utils/RefBase.h>

#include <sys/types.h>
#include <unistd.h>

#include <string>
#include <type_traits>
#include <vector>

#include <carwatchdog_daemon_dump.pb.h>
#include <performance_stats.pb.h>

namespace android {
namespace automotive {
namespace watchdog {

namespace {

using ::aidl::android::automotive::watchdog::internal::ResourceStats;
using ::android::RefBase;
using ::android::sp;
using ::android::base::ReadFdToString;
using ::android::base::Result;
using ::android::util::ProtoReader;
using ::google::protobuf::RepeatedPtrField;
using ::testing::_;
using ::testing::AllOf;
using ::testing::ElementsAreArray;
using ::testing::Eq;
using ::testing::ExplainMatchResult;
using ::testing::Field;
using ::testing::IsSubsetOf;
using ::testing::Matcher;
using ::testing::Property;
using ::testing::Return;
using ::testing::Test;
using ::testing::UnorderedElementsAreArray;
using ::testing::VariantWith;

using time_point_ms = std::chrono::time_point<std::chrono::system_clock, std::chrono::milliseconds>;

constexpr int kTestTopNStatsPerCategory = 5;
constexpr int kTestTopNStatsPerSubcategory = 5;
constexpr int kTestMaxUserSwitchEvents = 3;
constexpr std::chrono::seconds kTestSystemEventDataCacheDurationSec = 60s;
const auto kTestNow = std::chrono::time_point_cast<std::chrono::milliseconds>(
        std::chrono::system_clock::from_time_t(1'683'270'000));

MATCHER_P(IoStatsViewEq, expected, "") {
    return ExplainMatchResult(AllOf(Field("bytes", &UserPackageStats::IoStatsView::bytes,
                                          ElementsAreArray(expected.bytes)),
                                    Field("fsync", &UserPackageStats::IoStatsView::fsync,
                                          ElementsAreArray(expected.fsync))),
                              arg, result_listener);
}

MATCHER_P(ProcessValueEq, expected, "") {
    return ExplainMatchResult(AllOf(Field("comm",
                                          &UserPackageStats::ProcSingleStatsView::ProcessValue::
                                                  comm,
                                          Eq(expected.comm)),
                                    Field("value",
                                          &UserPackageStats::ProcSingleStatsView::ProcessValue::
                                                  value,
                                          Eq(expected.value))),
                              arg, result_listener);
}

MATCHER_P(ProcSingleStatsViewEq, expected, "") {
    std::vector<Matcher<const UserPackageStats::ProcSingleStatsView::ProcessValue&>>
            processValueMatchers;
    processValueMatchers.reserve(expected.topNProcesses.size());
    for (const auto& processValue : expected.topNProcesses) {
        processValueMatchers.push_back(ProcessValueEq(processValue));
    }
    return ExplainMatchResult(AllOf(Field("value", &UserPackageStats::ProcSingleStatsView::value,
                                          Eq(expected.value)),
                                    Field("topNProcesses",
                                          &UserPackageStats::ProcSingleStatsView::topNProcesses,
                                          ElementsAreArray(processValueMatchers))),
                              arg, result_listener);
}

MATCHER_P(ProcessCpuValueEq, expected, "") {
    return ExplainMatchResult(AllOf(Field("comm",
                                          &UserPackageStats::ProcCpuStatsView::ProcessCpuValue::
                                                  comm,
                                          Eq(expected.comm)),
                                    Field("cpuTimeMs",
                                          &UserPackageStats::ProcCpuStatsView::ProcessCpuValue::
                                                  cpuTimeMs,
                                          Eq(expected.cpuTimeMs)),
                                    Field("cpuCycles",
                                          &UserPackageStats::ProcCpuStatsView::ProcessCpuValue::
                                                  cpuCycles,
                                          Eq(expected.cpuCycles))),
                              arg, result_listener);
}

MATCHER_P(ProcCpuStatsViewEq, expected, "") {
    std::vector<Matcher<const UserPackageStats::ProcCpuStatsView::ProcessCpuValue&>>
            processValueMatchers;
    processValueMatchers.reserve(expected.topNProcesses.size());
    for (const auto& processValue : expected.topNProcesses) {
        processValueMatchers.push_back(ProcessCpuValueEq(processValue));
    }
    return ExplainMatchResult(AllOf(Field("cpuTimeMs",
                                          &UserPackageStats::ProcCpuStatsView::cpuTimeMs,
                                          Eq(expected.cpuTimeMs)),
                                    Field("cpuCycles",
                                          &UserPackageStats::ProcCpuStatsView::cpuCycles,
                                          Eq(expected.cpuCycles)),
                                    Field("topNProcesses",
                                          &UserPackageStats::ProcCpuStatsView::topNProcesses,
                                          ElementsAreArray(processValueMatchers))),
                              arg, result_listener);
}

MATCHER_P(UserPackageStatsEq, expected, "") {
    const auto uidMatcher = Field("uid", &UserPackageStats::uid, Eq(expected.uid));
    const auto packageNameMatcher =
            Field("genericPackageName", &UserPackageStats::genericPackageName,
                  Eq(expected.genericPackageName));
    return std::visit(
            [&](const auto& statsView) -> bool {
                using T = std::decay_t<decltype(statsView)>;
                if constexpr (std::is_same_v<T, UserPackageStats::IoStatsView>) {
                    return ExplainMatchResult(AllOf(uidMatcher, packageNameMatcher,
                                                    Field("statsView:IoStatsView",
                                                          &UserPackageStats::statsView,
                                                          VariantWith<
                                                                  UserPackageStats::IoStatsView>(
                                                                  IoStatsViewEq(statsView)))),
                                              arg, result_listener);
                } else if constexpr (std::is_same_v<T, UserPackageStats::ProcSingleStatsView>) {
                    return ExplainMatchResult(AllOf(uidMatcher, packageNameMatcher,
                                                    Field("statsView:ProcSingleStatsView",
                                                          &UserPackageStats::statsView,
                                                          VariantWith<UserPackageStats::
                                                                              ProcSingleStatsView>(
                                                                  ProcSingleStatsViewEq(
                                                                          statsView)))),
                                              arg, result_listener);
                } else if constexpr (std::is_same_v<T, UserPackageStats::ProcCpuStatsView>) {
                    return ExplainMatchResult(AllOf(uidMatcher, packageNameMatcher,
                                                    Field("statsView:ProcCpuStatsView",
                                                          &UserPackageStats::statsView,
                                                          VariantWith<UserPackageStats::
                                                                              ProcCpuStatsView>(
                                                                  ProcCpuStatsViewEq(statsView)))),
                                              arg, result_listener);
                }
                *result_listener << "Unexpected variant in UserPackageStats::stats";
                return false;
            },
            expected.statsView);
}

MATCHER_P(UserPackageSummaryStatsEq, expected, "") {
    const auto& userPackageStatsMatchers = [&](const std::vector<UserPackageStats>& stats) {
        std::vector<Matcher<const UserPackageStats&>> matchers;
        for (const auto& curStats : stats) {
            matchers.push_back(UserPackageStatsEq(curStats));
        }
        return ElementsAreArray(matchers);
    };
    const auto& totalIoStatsArrayMatcher = [&](const int64_t expected[][UID_STATES]) {
        std::vector<Matcher<const int64_t[UID_STATES]>> matchers;
        for (int i = 0; i < METRIC_TYPES; ++i) {
            matchers.push_back(ElementsAreArray(expected[i], UID_STATES));
        }
        return ElementsAreArray(matchers);
    };
    return ExplainMatchResult(AllOf(Field("topNCpuTimes", &UserPackageSummaryStats::topNCpuTimes,
                                          userPackageStatsMatchers(expected.topNCpuTimes)),
                                    Field("topNIoReads", &UserPackageSummaryStats::topNIoReads,
                                          userPackageStatsMatchers(expected.topNIoReads)),
                                    Field("topNIoWrites", &UserPackageSummaryStats::topNIoWrites,
                                          userPackageStatsMatchers(expected.topNIoWrites)),
                                    Field("topNIoBlocked", &UserPackageSummaryStats::topNIoBlocked,
                                          userPackageStatsMatchers(expected.topNIoBlocked)),
                                    Field("topNMajorFaults",
                                          &UserPackageSummaryStats::topNMajorFaults,
                                          userPackageStatsMatchers(expected.topNMajorFaults)),
                                    Field("totalIoStats", &UserPackageSummaryStats::totalIoStats,
                                          totalIoStatsArrayMatcher(expected.totalIoStats)),
                                    Field("taskCountByUid",
                                          &UserPackageSummaryStats::taskCountByUid,
                                          IsSubsetOf(expected.taskCountByUid)),
                                    Field("totalCpuTimeMillis",
                                          &UserPackageSummaryStats::totalCpuTimeMillis,
                                          Eq(expected.totalCpuTimeMillis)),
                                    Field("totalCpuCycles",
                                          &UserPackageSummaryStats::totalCpuCycles,
                                          Eq(expected.totalCpuCycles)),
                                    Field("totalMajorFaults",
                                          &UserPackageSummaryStats::totalMajorFaults,
                                          Eq(expected.totalMajorFaults)),
                                    Field("majorFaultsPercentChange",
                                          &UserPackageSummaryStats::majorFaultsPercentChange,
                                          Eq(expected.majorFaultsPercentChange))),
                              arg, result_listener);
}

MATCHER_P(SystemSummaryStatsEq, expected, "") {
    return ExplainMatchResult(AllOf(Field("cpuIoWaitTimeMillis",
                                          &SystemSummaryStats::cpuIoWaitTimeMillis,
                                          Eq(expected.cpuIoWaitTimeMillis)),
                                    Field("cpuIdleTimeMillis",
                                          &SystemSummaryStats::cpuIdleTimeMillis,
                                          Eq(expected.cpuIdleTimeMillis)),
                                    Field("totalCpuTimeMillis",
                                          &SystemSummaryStats::totalCpuTimeMillis,
                                          Eq(expected.totalCpuTimeMillis)),
                                    Field("totalCpuCycles", &SystemSummaryStats::totalCpuCycles,
                                          Eq(expected.totalCpuCycles)),
                                    Field("contextSwitchesCount",
                                          &SystemSummaryStats::contextSwitchesCount,
                                          Eq(expected.contextSwitchesCount)),
                                    Field("ioBlockedProcessCount",
                                          &SystemSummaryStats::ioBlockedProcessCount,
                                          Eq(expected.ioBlockedProcessCount)),
                                    Field("totalProcessCount",
                                          &SystemSummaryStats::totalProcessCount,
                                          Eq(expected.totalProcessCount))),
                              arg, result_listener);
}

MATCHER_P(PerfStatsRecordEq, expected, "") {
    return ExplainMatchResult(AllOf(Field(&PerfStatsRecord::systemSummaryStats,
                                          SystemSummaryStatsEq(expected.systemSummaryStats)),
                                    Field(&PerfStatsRecord::userPackageSummaryStats,
                                          UserPackageSummaryStatsEq(
                                                  expected.userPackageSummaryStats))),
                              arg, result_listener);
}

const std::vector<Matcher<const PerfStatsRecord&>> constructPerfStatsRecordMatchers(
        const std::vector<PerfStatsRecord>& records) {
    std::vector<Matcher<const PerfStatsRecord&>> matchers;
    for (const auto& record : records) {
        matchers.push_back(PerfStatsRecordEq(record));
    }
    return matchers;
}

MATCHER_P(CollectionInfoEq, expected, "") {
    return ExplainMatchResult(AllOf(Field("maxCacheSize", &CollectionInfo::maxCacheSize,
                                          Eq(expected.maxCacheSize)),
                                    Field("records", &CollectionInfo::records,
                                          ElementsAreArray(constructPerfStatsRecordMatchers(
                                                  expected.records)))),
                              arg, result_listener);
}

MATCHER_P(UserSwitchCollectionInfoEq, expected, "") {
    return ExplainMatchResult(AllOf(Field("from", &UserSwitchCollectionInfo::from,
                                          Eq(expected.from)),
                                    Field("to", &UserSwitchCollectionInfo::to, Eq(expected.to)),
                                    Field("maxCacheSize", &UserSwitchCollectionInfo::maxCacheSize,
                                          Eq(expected.maxCacheSize)),
                                    Field("records", &UserSwitchCollectionInfo::records,
                                          ElementsAreArray(constructPerfStatsRecordMatchers(
                                                  expected.records)))),
                              arg, result_listener);
}

MATCHER_P(UserSwitchCollectionsEq, expected, "") {
    std::vector<Matcher<const UserSwitchCollectionInfo&>> userSwitchCollectionMatchers;
    for (const auto& curCollection : expected) {
        userSwitchCollectionMatchers.push_back(UserSwitchCollectionInfoEq(curCollection));
    }
    return ExplainMatchResult(ElementsAreArray(userSwitchCollectionMatchers), arg, result_listener);
}

int countOccurrences(std::string str, std::string subStr) {
    size_t pos = 0;
    int occurrences = 0;
    while ((pos = str.find(subStr, pos)) != std::string::npos) {
        ++occurrences;
        pos += subStr.length();
    }
    return occurrences;
}

std::tuple<std::vector<UidStats>, UserPackageSummaryStats> sampleUidStats(int multiplier = 1) {
    /* The number of returned sample stats are less that the top N stats per category/sub-category.
     * The top N stats per category/sub-category is set to % during test setup. Thus, the default
     * testing behavior is # reported stats < top N stats.
     */
    const auto int64Multiplier = [&](int64_t bytes) -> int64_t {
        return static_cast<int64_t>(bytes * multiplier);
    };
    const auto uint64Multiplier = [&](uint64_t count) -> uint64_t {
        return static_cast<uint64_t>(count * multiplier);
    };
    std::vector<UidStats>
            uidStats{{.packageInfo = constructPackageInfo("mount", 1009),
                      .cpuTimeMillis = int64Multiplier(50),
                      .ioStats = {/*fgRdBytes=*/0,
                                  /*bgRdBytes=*/int64Multiplier(14'000),
                                  /*fgWrBytes=*/0,
                                  /*bgWrBytes=*/int64Multiplier(16'000),
                                  /*fgFsync=*/0, /*bgFsync=*/int64Multiplier(100)},
                      .procStats = {.cpuTimeMillis = int64Multiplier(50),
                                    .cpuCycles = 4000,
                                    .totalMajorFaults = uint64Multiplier(11'000),
                                    .totalTasksCount = 1,
                                    .ioBlockedTasksCount = 1,
                                    .processStatsByPid =
                                            {{/*pid=*/100,
                                              {/*comm=*/"disk I/O", /*startTime=*/234,
                                               /*cpuTimeMillis=*/int64Multiplier(50),
                                               /*totalCpuCycles=*/4000,
                                               /*totalMajorFaults=*/uint64Multiplier(11'000),
                                               /*totalTasksCount=*/1,
                                               /*ioBlockedTasksCount=*/1,
                                               /*cpuCyclesByTid=*/{{100, 4000}}}}}}},
                     {.packageInfo =
                              constructPackageInfo("com.google.android.car.kitchensink", 1002001),
                      .cpuTimeMillis = int64Multiplier(60),
                      .ioStats = {/*fgRdBytes=*/0,
                                  /*bgRdBytes=*/int64Multiplier(3'400),
                                  /*fgWrBytes=*/0,
                                  /*bgWrBytes=*/int64Multiplier(6'700),
                                  /*fgFsync=*/0,
                                  /*bgFsync=*/int64Multiplier(200)},
                      .procStats = {.cpuTimeMillis = int64Multiplier(50),
                                    .cpuCycles = 10'000,
                                    .totalMajorFaults = uint64Multiplier(22'445),
                                    .totalTasksCount = 5,
                                    .ioBlockedTasksCount = 3,
                                    .processStatsByPid =
                                            {{/*pid=*/1000,
                                              {/*comm=*/"KitchenSinkApp", /*startTime=*/467,
                                               /*cpuTimeMillis=*/int64Multiplier(25),
                                               /*totalCpuCycles=*/4000,
                                               /*totalMajorFaults=*/uint64Multiplier(12'345),
                                               /*totalTasksCount=*/2,
                                               /*ioBlockedTasksCount=*/1,
                                               /*cpuCyclesByTid=*/{{1000, 4000}}}},
                                             {/*pid=*/1001,
                                              {/*comm=*/"CTS", /*startTime=*/789,
                                               /*cpuTimeMillis=*/int64Multiplier(25),
                                               /*totalCpuCycles=*/5000,
                                               /*totalMajorFaults=*/uint64Multiplier(10'100),
                                               /*totalTasksCount=*/3,
                                               /*ioBlockedTasksCount=*/2,
                                               /*cpuCyclesByTid=*/{{1001, 3000}, {1002, 2000}}}}}}},
                     {.packageInfo = constructPackageInfo("", 1012345),
                      .cpuTimeMillis = int64Multiplier(100),
                      .ioStats = {/*fgRdBytes=*/int64Multiplier(1'000),
                                  /*bgRdBytes=*/int64Multiplier(4'200),
                                  /*fgWrBytes=*/int64Multiplier(300),
                                  /*bgWrBytes=*/int64Multiplier(5'600),
                                  /*fgFsync=*/int64Multiplier(600),
                                  /*bgFsync=*/int64Multiplier(300)},
                      .procStats = {.cpuTimeMillis = int64Multiplier(100),
                                    .cpuCycles = 50'000,
                                    .totalMajorFaults = uint64Multiplier(50'900),
                                    .totalTasksCount = 4,
                                    .ioBlockedTasksCount = 2,
                                    .processStatsByPid =
                                            {{/*pid=*/2345,
                                              {/*comm=*/"MapsApp", /*startTime=*/6789,
                                               /*cpuTimeMillis=*/int64Multiplier(100),
                                               /*totalCpuCycles=*/50'000,
                                               /*totalMajorFaults=*/uint64Multiplier(50'900),
                                               /*totalTasksCount=*/4,
                                               /*ioBlockedTasksCount=*/2,
                                               /*cpuCyclesByTid=*/{{2345, 50'000}}}}}}},
                     {.packageInfo = constructPackageInfo("com.google.radio", 1015678),
                      .cpuTimeMillis = 0,
                      .ioStats = {/*fgRdBytes=*/0,
                                  /*bgRdBytes=*/0,
                                  /*fgWrBytes=*/0,
                                  /*bgWrBytes=*/0,
                                  /*fgFsync=*/0, /*bgFsync=*/0},
                      .procStats = {.cpuTimeMillis = 0,
                                    .cpuCycles = 0,
                                    .totalMajorFaults = 0,
                                    .totalTasksCount = 4,
                                    .ioBlockedTasksCount = 0,
                                    .processStatsByPid = {
                                            {/*pid=*/2345,
                                             {/*comm=*/"RadioApp", /*startTime=*/19789,
                                              /*cpuTimeMillis=*/0,
                                              /*totalCpuCycles=*/0,
                                              /*totalMajorFaults=*/0,
                                              /*totalTasksCount=*/4,
                                              /*ioBlockedTasksCount=*/0,
                                              /*cpuCyclesByTid=*/{}}}}}}};

    UserPackageSummaryStats userPackageSummaryStats{
            .topNCpuTimes = {{1012345, "1012345",
                              UserPackageStats::ProcCpuStatsView{int64Multiplier(100),
                                                                 50'000,
                                                                 {{"MapsApp", int64Multiplier(100),
                                                                   50'000}}}},
                             {1002001, "com.google.android.car.kitchensink",
                              UserPackageStats::ProcCpuStatsView{int64Multiplier(60),
                                                                 10'000,
                                                                 {{"CTS", int64Multiplier(25),
                                                                   5000},
                                                                  {"KitchenSinkApp",
                                                                   int64Multiplier(25), 4000}}}},
                             {1009, "mount",
                              UserPackageStats::ProcCpuStatsView{int64Multiplier(50),
                                                                 4000,
                                                                 {{"disk I/O", int64Multiplier(50),
                                                                   4000}}}}},
            .topNIoReads = {{1009, "mount",
                             UserPackageStats::IoStatsView{{0, int64Multiplier(14'000)},
                                                           {0, int64Multiplier(100)}}},
                            {1012345, "1012345",
                             UserPackageStats::IoStatsView{{int64Multiplier(1'000),
                                                            int64Multiplier(4'200)},
                                                           {int64Multiplier(600),
                                                            int64Multiplier(300)}}},
                            {1002001, "com.google.android.car.kitchensink",
                             UserPackageStats::IoStatsView{{0, int64Multiplier(3'400)},
                                                           {0, int64Multiplier(200)}}}},
            .topNIoWrites =
                    {{1009, "mount",
                      UserPackageStats::IoStatsView{{0, int64Multiplier(16'000)},
                                                    {0, int64Multiplier(100)}}},
                     {1002001, "com.google.android.car.kitchensink",
                      UserPackageStats::IoStatsView{{0, int64Multiplier(6'700)},
                                                    {0, int64Multiplier(200)}}},
                     {1012345, "1012345",
                      UserPackageStats::IoStatsView{{int64Multiplier(300), int64Multiplier(5'600)},
                                                    {int64Multiplier(600), int64Multiplier(300)}}}},
            .topNIoBlocked =
                    {{1002001, "com.google.android.car.kitchensink",
                      UserPackageStats::ProcSingleStatsView{3,
                                                            {{"CTS", 2}, {"KitchenSinkApp", 1}}}},
                     {1012345, "1012345",
                      UserPackageStats::ProcSingleStatsView{2, {{"MapsApp", 2}}}},
                     {1009, "mount", UserPackageStats::ProcSingleStatsView{1, {{"disk I/O", 1}}}}},
            .topNMajorFaults =
                    {{1012345, "1012345",
                      UserPackageStats::ProcSingleStatsView{uint64Multiplier(50'900),
                                                            {{"MapsApp",
                                                              uint64Multiplier(50'900)}}}},
                     {1002001, "com.google.android.car.kitchensink",
                      UserPackageStats::ProcSingleStatsView{uint64Multiplier(22'445),
                                                            {{"KitchenSinkApp",
                                                              uint64Multiplier(12'345)},
                                                             {"CTS", uint64Multiplier(10'100)}}}},
                     {1009, "mount",
                      UserPackageStats::ProcSingleStatsView{uint64Multiplier(11'000),
                                                            {{"disk I/O",
                                                              uint64Multiplier(11'000)}}}}},
            .totalIoStats = {{int64Multiplier(1'000), int64Multiplier(21'600)},
                             {int64Multiplier(300), int64Multiplier(28'300)},
                             {int64Multiplier(600), int64Multiplier(600)}},
            .taskCountByUid = {{1009, 1}, {1002001, 5}, {1012345, 4}},
            .totalCpuTimeMillis = int64Multiplier(48'376),
            .totalCpuCycles = 64'000,
            .totalMajorFaults = uint64Multiplier(84'345),
            .majorFaultsPercentChange = 0.0,
    };
    return std::make_tuple(uidStats, userPackageSummaryStats);
}

std::tuple<ProcStatInfo, SystemSummaryStats> sampleProcStat(int multiplier = 1) {
    const auto int64Multiplier = [&](int64_t bytes) -> int64_t {
        return static_cast<int64_t>(bytes * multiplier);
    };
    const auto uint64Multiplier = [&](uint64_t bytes) -> uint64_t {
        return static_cast<uint64_t>(bytes * multiplier);
    };
    const auto uint32Multiplier = [&](uint32_t bytes) -> uint32_t {
        return static_cast<uint32_t>(bytes * multiplier);
    };
    ProcStatInfo procStatInfo{/*stats=*/{int64Multiplier(2'900), int64Multiplier(7'900),
                                         int64Multiplier(4'900), int64Multiplier(8'900),
                                         /*ioWaitTimeMillis=*/int64Multiplier(5'900),
                                         int64Multiplier(6'966), int64Multiplier(7'980), 0, 0,
                                         int64Multiplier(2'930)},
                              /*ctxtSwitches=*/uint64Multiplier(500),
                              /*runnableCnt=*/uint32Multiplier(100),
                              /*ioBlockedCnt=*/uint32Multiplier(57)};
    SystemSummaryStats systemSummaryStats{/*cpuIoWaitTimeMillis=*/int64Multiplier(5'900),
                                          /*cpuIdleTimeMillis=*/int64Multiplier(8'900),
                                          /*totalCpuTimeMillis=*/int64Multiplier(48'376),
                                          /*totalCpuCycles=*/64'000,
                                          /*contextSwitchesCount=*/uint64Multiplier(500),
                                          /*ioBlockedProcessCount=*/uint32Multiplier(57),
                                          /*totalProcessCount=*/uint32Multiplier(157)};
    return std::make_tuple(procStatInfo, systemSummaryStats);
}

MATCHER_P(UserPackageInfoProtoEq, expected, "") {
    return ExplainMatchResult(AllOf(Property("user_id", &UserPackageInfo::user_id,
                                             static_cast<int>(multiuser_get_user_id(expected.uid))),
                                    Property("package_name", &UserPackageInfo::package_name,
                                             expected.genericPackageName)),
                              arg, result_listener);
}

MATCHER_P(CpuStatsProtoEq, expected, "") {
    return ExplainMatchResult(AllOf(Property("cpu_time_millis",
                                             &PackageCpuStats_CpuStats::cpu_time_millis,
                                             expected.cpuTimeMs),
                                    Property("cpu_cycles", &PackageCpuStats_CpuStats::cpu_cycles,
                                             expected.cpuCycles)),
                              arg, result_listener);
}

MATCHER_P(ProcessCpuStatsProtoEq, expected, "") {
    return ExplainMatchResult(AllOf(Property("command", &PackageCpuStats_ProcessCpuStats::command,
                                             expected.comm),
                                    Property("cpu_stats",
                                             &PackageCpuStats_ProcessCpuStats::cpu_stats,
                                             CpuStatsProtoEq(expected))),
                              arg, result_listener);
}

MATCHER_P(PackageCpuStatsProtoEq, expected, "") {
    const auto& procCpuStatsView =
            std::get_if<UserPackageStats::ProcCpuStatsView>(&expected.statsView);
    std::vector<Matcher<const PackageCpuStats_ProcessCpuStats&>> processCpuStatsMatchers;
    for (const auto& expectedProcessCpuValue : procCpuStatsView->topNProcesses) {
        processCpuStatsMatchers.push_back(ProcessCpuStatsProtoEq(expectedProcessCpuValue));
    }
    return ExplainMatchResult(AllOf(Property("user_package_info",
                                             &PackageCpuStats::user_package_info,
                                             UserPackageInfoProtoEq(expected)),
                                    Property("cpu_stats", &PackageCpuStats::cpu_stats,
                                             CpuStatsProtoEq(*procCpuStatsView)),
                                    Property("process_cpu_stats",
                                             &PackageCpuStats::process_cpu_stats,
                                             ElementsAreArray(processCpuStatsMatchers))),
                              arg, result_listener);
}

MATCHER_P4(StorageIoStatsProtoEq, fgBytes, fgFsync, bgBytes, byFsync, "") {
    return ExplainMatchResult(AllOf(Property("fg_bytes", &StorageIoStats::fg_bytes, fgBytes),
                                    Property("fg_fsync", &StorageIoStats::fg_fsync, fgFsync),
                                    Property("bg_bytes", &StorageIoStats::bg_bytes, bgBytes),
                                    Property("bg_fsync", &StorageIoStats::bg_fsync, byFsync)),
                              arg, result_listener);
}

MATCHER_P(PackageStorageIoStatsProtoEq, expected, "") {
    const auto& ioStatsView = std::get_if<UserPackageStats::IoStatsView>(&expected.statsView);
    return ExplainMatchResult(AllOf(Property("user_package_info",
                                             &PackageStorageIoStats::user_package_info,
                                             UserPackageInfoProtoEq(expected)),
                                    Property("storage_io_stats",
                                             &PackageStorageIoStats::storage_io_stats,
                                             StorageIoStatsProtoEq(ioStatsView->bytes[FOREGROUND],
                                                                   ioStatsView->fsync[FOREGROUND],
                                                                   ioStatsView->bytes[BACKGROUND],
                                                                   ioStatsView
                                                                           ->fsync[BACKGROUND]))),
                              arg, result_listener);
}

MATCHER_P(ProcessTaskStateStatsProtoEq, expected, "") {
    return ExplainMatchResult(AllOf(Property("command",
                                             &PackageTaskStateStats_ProcessTaskStateStats::command,
                                             expected.comm),
                                    Property("io_blocked_task_count",
                                             &PackageTaskStateStats_ProcessTaskStateStats::
                                                     io_blocked_task_count,
                                             expected.value)),
                              arg, result_listener);
}

MATCHER_P2(PackageTaskStateStatsProtoEq, expected, taskCountByUid, "") {
    const auto& procSingleStatsView =
            std::get_if<UserPackageStats::ProcSingleStatsView>(&expected.statsView);
    std::vector<Matcher<const PackageTaskStateStats_ProcessTaskStateStats&>>
            processTaskStateStatsMatchers;
    for (const auto& expectedProcessValue : procSingleStatsView->topNProcesses) {
        processTaskStateStatsMatchers.push_back(ProcessTaskStateStatsProtoEq(expectedProcessValue));
    }
    return ExplainMatchResult(AllOf(Property("user_package_info",
                                             &PackageTaskStateStats::user_package_info,
                                             UserPackageInfoProtoEq(expected)),
                                    Property("io_blocked_task_count",
                                             &PackageTaskStateStats::io_blocked_task_count,
                                             procSingleStatsView->value),
                                    Property("total_task_count",
                                             &PackageTaskStateStats::total_task_count,
                                             taskCountByUid.at(expected.uid)),
                                    Property("process_task_state_stats",
                                             &PackageTaskStateStats::process_task_state_stats,
                                             ElementsAreArray(processTaskStateStatsMatchers))),
                              arg, result_listener);
}

MATCHER_P(PackageMajorPageFaultsProtoEq, expected, "") {
    const auto& procSingleStatsView =
            std::get_if<UserPackageStats::ProcSingleStatsView>(&expected.statsView);
    return ExplainMatchResult(AllOf(Property("user_package_info",
                                             &PackageMajorPageFaults::user_package_info,
                                             UserPackageInfoProtoEq(expected)),
                                    Property("major_page_faults_count",
                                             &PackageMajorPageFaults::major_page_faults_count,
                                             procSingleStatsView->value)),
                              arg, result_listener);
}

MATCHER_P(DateProtoEq, expected, "") {
    return ExplainMatchResult(AllOf(Property("year", &Date::year, expected.tm_year + 1900),
                                    Property("month", &Date::month, expected.tm_mon),
                                    Property("day", &Date::day, expected.tm_mday)),
                              arg, result_listener);
}

MATCHER_P2(TimeOfDayProtoEq, expected, nowTimeMs, "") {
    return ExplainMatchResult(AllOf(Property("hours", &TimeOfDay::hours, expected.tm_hour),
                                    Property("minutes", &TimeOfDay::minutes, expected.tm_min),
                                    Property("seconds", &TimeOfDay::seconds, expected.tm_sec),
                                    Property("millis", &TimeOfDay::millis, nowTimeMs.count())),
                              arg, result_listener);
}

MATCHER_P2(SystemWideStatsProtoEq, userPackageSummaryStats, systemSummaryStats, "") {
    return ExplainMatchResult(AllOf(Property("io_wait_time_millis",
                                             &SystemWideStats::io_wait_time_millis,
                                             systemSummaryStats.cpuIoWaitTimeMillis),
                                    Property("idle_cpu_time_millis",
                                             &SystemWideStats::idle_cpu_time_millis,
                                             systemSummaryStats.cpuIdleTimeMillis),
                                    Property("total_cpu_time_millis",
                                             &SystemWideStats::total_cpu_time_millis,
                                             systemSummaryStats.totalCpuTimeMillis),
                                    Property("total_cpu_cycles", &SystemWideStats::total_cpu_cycles,
                                             systemSummaryStats.totalCpuCycles),
                                    Property("total_context_switches",
                                             &SystemWideStats::total_context_switches,
                                             systemSummaryStats.contextSwitchesCount),
                                    Property("total_io_blocked_processes",
                                             &SystemWideStats::total_io_blocked_processes,
                                             systemSummaryStats.ioBlockedProcessCount),
                                    Property("total_major_page_faults",
                                             &SystemWideStats::total_major_page_faults,
                                             userPackageSummaryStats.totalMajorFaults),
                                    Property("total_storage_io_stats",
                                             &SystemWideStats::total_storage_io_stats,
                                             StorageIoStatsProtoEq(userPackageSummaryStats
                                                                           .totalIoStats
                                                                                   [WRITE_BYTES]
                                                                                   [FOREGROUND],
                                                                   userPackageSummaryStats
                                                                           .totalIoStats
                                                                                   [FSYNC_COUNT]
                                                                                   [FOREGROUND],
                                                                   userPackageSummaryStats
                                                                           .totalIoStats
                                                                                   [WRITE_BYTES]
                                                                                   [BACKGROUND],
                                                                   userPackageSummaryStats
                                                                           .totalIoStats
                                                                                   [FSYNC_COUNT]
                                                                                   [BACKGROUND]))),
                              arg, result_listener);
}

MATCHER_P3(StatsRecordProtoEq, userPackageSummaryStats, systemSummaryStats, nowMs, "") {
    struct tm timeinfo;
    memset(&timeinfo, 0, sizeof(timeinfo));
    auto dateTime = std::chrono::system_clock::to_time_t(nowMs);
    auto nowTimeMs = std::chrono::duration_cast<std::chrono::milliseconds>(
            nowMs - std::chrono::system_clock::from_time_t(dateTime));
    localtime_r(&dateTime, &timeinfo);

    std::vector<Matcher<const PackageCpuStats&>> packageCpuStatsMatchers;
    for (const auto& expectedProcCpuStatsView : userPackageSummaryStats.topNCpuTimes) {
        packageCpuStatsMatchers.push_back(PackageCpuStatsProtoEq(expectedProcCpuStatsView));
    }
    std::vector<Matcher<const PackageStorageIoStats&>> packageStorageReadIoStatsMatchers;
    for (const auto& expectedPackageStorageReadIoStat : userPackageSummaryStats.topNIoReads) {
        packageStorageReadIoStatsMatchers.push_back(
                PackageStorageIoStatsProtoEq(expectedPackageStorageReadIoStat));
    }
    std::vector<Matcher<const PackageStorageIoStats&>> packageStorageWriteIoStatsMatchers;
    for (const auto& expectedPackageStorageWriteIoStat : userPackageSummaryStats.topNIoWrites) {
        packageStorageWriteIoStatsMatchers.push_back(
                PackageStorageIoStatsProtoEq(expectedPackageStorageWriteIoStat));
    }
    std::vector<Matcher<const PackageTaskStateStats&>> packageTaskStateStatsMatchers;
    for (const auto& expectedPackageTaskStateStat : userPackageSummaryStats.topNIoBlocked) {
        packageTaskStateStatsMatchers.push_back(
                PackageTaskStateStatsProtoEq(expectedPackageTaskStateStat,
                                             userPackageSummaryStats.taskCountByUid));
    }
    std::vector<Matcher<const PackageMajorPageFaults&>> packageMajorPageFaultsMatchers;
    for (const auto& expectedPackageMajorPageFault : userPackageSummaryStats.topNMajorFaults) {
        packageMajorPageFaultsMatchers.push_back(
                PackageMajorPageFaultsProtoEq(expectedPackageMajorPageFault));
    }
    return ExplainMatchResult(AllOf(Property("date", &StatsRecord::date, DateProtoEq(timeinfo)),
                                    Property("time", &StatsRecord::time,
                                             TimeOfDayProtoEq(timeinfo, nowTimeMs)),
                                    Property("system_wide_stats", &StatsRecord::system_wide_stats,
                                             SystemWideStatsProtoEq(userPackageSummaryStats,
                                                                    systemSummaryStats)),
                                    Property("package_cpu_stats", &StatsRecord::package_cpu_stats,
                                             ElementsAreArray(packageCpuStatsMatchers)),
                                    Property("package_storage_io_read_stats",
                                             &StatsRecord::package_storage_io_read_stats,
                                             ElementsAreArray(packageStorageReadIoStatsMatchers)),
                                    Property("package_storage_io_read_stats",
                                             &StatsRecord::package_storage_io_write_stats,
                                             ElementsAreArray(packageStorageWriteIoStatsMatchers)),
                                    Property("package_task_state_stats",
                                             &StatsRecord::package_task_state_stats,
                                             ElementsAreArray(packageTaskStateStatsMatchers)),
                                    Property("package_major_page_faults",
                                             &StatsRecord::package_major_page_faults,
                                             ElementsAreArray(packageMajorPageFaultsMatchers))),
                              arg, result_listener);
}
}  // namespace

namespace internal {

class PerformanceProfilerPeer final : public RefBase {
public:
    explicit PerformanceProfilerPeer(sp<PerformanceProfiler> collector) : mCollector(collector) {}

    PerformanceProfilerPeer() = delete;
    ~PerformanceProfilerPeer() {
        mCollector->terminate();
        mCollector.clear();
    }

    Result<void> init() { return mCollector->init(); }

    void setTopNStatsPerCategory(int value) { mCollector->mTopNStatsPerCategory = value; }

    void setTopNStatsPerSubcategory(int value) { mCollector->mTopNStatsPerSubcategory = value; }

    void setMaxUserSwitchEvents(int value) { mCollector->mMaxUserSwitchEvents = value; }

    void setSystemEventDataCacheDuration(std::chrono::seconds value) {
        mCollector->mSystemEventDataCacheDurationSec = value;
    }

    void setSendResourceUsageStatsEnabled(bool enable) {
        mCollector->mDoSendResourceUsageStats = enable;
    }

    const CollectionInfo& getBoottimeCollectionInfo() {
        Mutex::Autolock lock(mCollector->mMutex);
        return mCollector->mBoottimeCollection;
    }

    const CollectionInfo& getPeriodicCollectionInfo() {
        Mutex::Autolock lock(mCollector->mMutex);
        return mCollector->mPeriodicCollection;
    }

    const std::vector<UserSwitchCollectionInfo>& getUserSwitchCollectionInfos() {
        Mutex::Autolock lock(mCollector->mMutex);
        return mCollector->mUserSwitchCollections;
    }

    const CollectionInfo& getWakeUpCollectionInfo() {
        Mutex::Autolock lock(mCollector->mMutex);
        return mCollector->mWakeUpCollection;
    }

    const CollectionInfo& getCustomCollectionInfo() {
        Mutex::Autolock lock(mCollector->mMutex);
        return mCollector->mCustomCollection;
    }

private:
    sp<PerformanceProfiler> mCollector;
};

}  // namespace internal

class PerformanceProfilerTest : public Test {
protected:
    void SetUp() override {
        mMockUidStatsCollector = sp<MockUidStatsCollector>::make();
        mMockProcStatCollector = sp<MockProcStatCollector>::make();
        mCollector = sp<PerformanceProfiler>::make();
        mCollectorPeer = sp<internal::PerformanceProfilerPeer>::make(mCollector);
        ASSERT_RESULT_OK(mCollectorPeer->init());
        mCollectorPeer->setTopNStatsPerCategory(kTestTopNStatsPerCategory);
        mCollectorPeer->setTopNStatsPerSubcategory(kTestTopNStatsPerSubcategory);
        mCollectorPeer->setMaxUserSwitchEvents(kTestMaxUserSwitchEvents);
        mCollectorPeer->setSystemEventDataCacheDuration(kTestSystemEventDataCacheDurationSec);
        mCollectorPeer->setSendResourceUsageStatsEnabled(true);
    }

    void TearDown() override {
        mMockUidStatsCollector.clear();
        mMockProcStatCollector.clear();
        mCollector.clear();
        mCollectorPeer.clear();
    }

    void checkDumpContents(int wantedEmptyCollectionInstances) {
        TemporaryFile dump;
        ASSERT_RESULT_OK(mCollector->onDump(dump.fd));

        checkDumpFd(wantedEmptyCollectionInstances, dump.fd);
    }

    void checkCustomDumpContents() {
        TemporaryFile dump;
        ASSERT_RESULT_OK(mCollector->onCustomCollectionDump(dump.fd));

        checkDumpFd(/*wantedEmptyCollectionInstances=*/0, dump.fd);
    }

    std::string toString(util::ProtoOutputStream* proto) {
        std::string content;
        content.reserve(proto->size());
        sp<ProtoReader> reader = proto->data();
        while (reader->hasNext()) {
            content.push_back(reader->next());
        }
        return content;
    }

private:
    void checkDumpFd(int wantedEmptyCollectionInstances, int fd) {
        lseek(fd, 0, SEEK_SET);
        std::string dumpContents;
        ASSERT_TRUE(ReadFdToString(fd, &dumpContents));
        ASSERT_FALSE(dumpContents.empty());

        ASSERT_EQ(countOccurrences(dumpContents, kEmptyCollectionMessage),
                  wantedEmptyCollectionInstances)
                << "Dump contents: " << dumpContents;
    }

protected:
    sp<MockUidStatsCollector> mMockUidStatsCollector;
    sp<MockProcStatCollector> mMockProcStatCollector;
    sp<PerformanceProfiler> mCollector;
    sp<internal::PerformanceProfilerPeer> mCollectorPeer;
};

TEST_F(PerformanceProfilerTest, TestOnBoottimeCollection) {
    const auto [uidStats, userPackageSummaryStats] = sampleUidStats();
    const auto [procStatInfo, systemSummaryStats] = sampleProcStat();

    EXPECT_CALL(*mMockUidStatsCollector, deltaStats()).WillOnce(Return(uidStats));
    EXPECT_CALL(*mMockProcStatCollector, deltaStats()).WillOnce(Return(procStatInfo));

    ResourceStats actualResourceStats = {};
    ASSERT_RESULT_OK(mCollector->onBoottimeCollection(kTestNow, mMockUidStatsCollector,
                                                      mMockProcStatCollector,
                                                      &actualResourceStats));

    const auto actual = mCollectorPeer->getBoottimeCollectionInfo();

    const CollectionInfo expected{
            .maxCacheSize = std::numeric_limits<std::size_t>::max(),
            .records = {{
                    .systemSummaryStats = systemSummaryStats,
                    .userPackageSummaryStats = userPackageSummaryStats,
            }},
    };

    EXPECT_THAT(actual, CollectionInfoEq(expected))
            << "Boottime collection info doesn't match.\nExpected:\n"
            << expected.toString() << "\nActual:\n"
            << actual.toString();

    ASSERT_NO_FATAL_FAILURE(checkDumpContents(/*wantedEmptyCollectionInstances=*/3))
            << "Periodic, wake-up and user-switch collections shouldn't be reported";
}

TEST_F(PerformanceProfilerTest, TestOnWakeUpCollection) {
    const auto [uidStats, userPackageSummaryStats] = sampleUidStats();
    const auto [procStatInfo, systemSummaryStats] = sampleProcStat();

    EXPECT_CALL(*mMockUidStatsCollector, deltaStats()).WillOnce(Return(uidStats));
    EXPECT_CALL(*mMockProcStatCollector, deltaStats()).WillOnce(Return(procStatInfo));

    ASSERT_RESULT_OK(mCollector->onWakeUpCollection(kTestNow, mMockUidStatsCollector,
                                                    mMockProcStatCollector));

    const auto actual = mCollectorPeer->getWakeUpCollectionInfo();

    const CollectionInfo expected{
            .maxCacheSize = std::numeric_limits<std::size_t>::max(),
            .records = {{
                    .systemSummaryStats = systemSummaryStats,
                    .userPackageSummaryStats = userPackageSummaryStats,
            }},
    };

    EXPECT_THAT(actual, CollectionInfoEq(expected))
            << "Wake-up collection info doesn't match.\nExpected:\n"
            << expected.toString() << "\nActual:\n"
            << actual.toString();

    ASSERT_NO_FATAL_FAILURE(checkDumpContents(/*wantedEmptyCollectionInstances=*/3))
            << "Boot-time, periodic, and user-switch collections shouldn't be reported";
}

TEST_F(PerformanceProfilerTest, TestOnSystemStartup) {
    const auto [uidStats, userPackageSummaryStats] = sampleUidStats();
    const auto [procStatInfo, systemSummaryStats] = sampleProcStat();

    EXPECT_CALL(*mMockUidStatsCollector, deltaStats()).WillRepeatedly(Return(uidStats));
    EXPECT_CALL(*mMockProcStatCollector, deltaStats()).WillRepeatedly(Return(procStatInfo));

    ResourceStats resourceStats = {};
    ASSERT_RESULT_OK(mCollector->onBoottimeCollection(kTestNow, mMockUidStatsCollector,
                                                      mMockProcStatCollector, &resourceStats));
    ASSERT_RESULT_OK(mCollector->onWakeUpCollection(kTestNow, mMockUidStatsCollector,
                                                    mMockProcStatCollector));

    auto actualBoottimeCollection = mCollectorPeer->getBoottimeCollectionInfo();
    auto actualWakeUpCollection = mCollectorPeer->getWakeUpCollectionInfo();

    EXPECT_THAT(actualBoottimeCollection.records.size(), 1)
            << "Boot-time collection records is empty.";
    EXPECT_THAT(actualWakeUpCollection.records.size(), 1) << "Wake-up collection records is empty.";

    ASSERT_RESULT_OK(mCollector->onSystemStartup());

    actualBoottimeCollection = mCollectorPeer->getBoottimeCollectionInfo();
    actualWakeUpCollection = mCollectorPeer->getWakeUpCollectionInfo();

    EXPECT_THAT(actualBoottimeCollection.records.size(), 0)
            << "Boot-time collection records is not empty.";
    EXPECT_THAT(actualWakeUpCollection.records.size(), 0)
            << "Wake-up collection records is not empty.";
}

TEST_F(PerformanceProfilerTest, TestOnUserSwitchCollection) {
    auto [uidStats, userPackageSummaryStats] = sampleUidStats();
    auto [procStatInfo, systemSummaryStats] = sampleProcStat();

    EXPECT_CALL(*mMockUidStatsCollector, deltaStats()).WillOnce(Return(uidStats));
    EXPECT_CALL(*mMockProcStatCollector, deltaStats()).WillOnce(Return(procStatInfo));

    ASSERT_RESULT_OK(mCollector->onUserSwitchCollection(kTestNow, 100, 101, mMockUidStatsCollector,
                                                        mMockProcStatCollector));

    const auto& actualInfos = mCollectorPeer->getUserSwitchCollectionInfos();
    const auto& actual = actualInfos[0];

    UserSwitchCollectionInfo expected{
            {
                    .maxCacheSize = std::numeric_limits<std::size_t>::max(),
                    .records = {{
                            .systemSummaryStats = systemSummaryStats,
                            .userPackageSummaryStats = userPackageSummaryStats,
                    }},
            },
            .from = 100,
            .to = 101,
    };

    EXPECT_THAT(actualInfos.size(), 1);

    EXPECT_THAT(actual, UserSwitchCollectionInfoEq(expected))
            << "User switch collection info doesn't match.\nExpected:\n"
            << expected.toString() << "\nActual:\n"
            << actual.toString();

    // Continuation of the previous user switch collection
    std::vector<UidStats> nextUidStats = {
            {.packageInfo = constructPackageInfo("mount", 1009),
             .cpuTimeMillis = 0,  // No TopNCpuTimes will be registered
             .ioStats = {/*fgRdBytes=*/0,
                         /*bgRdBytes=*/5'000,
                         /*fgWrBytes=*/0,
                         /*bgWrBytes=*/3'000,
                         /*fgFsync=*/0, /*bgFsync=*/50},
             .procStats = {.cpuTimeMillis = 50,
                           .cpuCycles = 3'500,
                           .totalMajorFaults = 6'000,
                           .totalTasksCount = 1,
                           .ioBlockedTasksCount = 2,
                           .processStatsByPid = {{/*pid=*/100,
                                                  {/*comm=*/"disk I/O", /*startTime=*/234,
                                                   /*cpuTimeMillis=*/50,
                                                   /*totalCpuCycle=*/3'500,
                                                   /*totalMajorFaults=*/6'000,
                                                   /*totalTasksCount=*/1,
                                                   /*ioBlockedTasksCount=*/2,
                                                   /*cpuCyclesByTid=*/{{100, 3'500}}}}}}}};

    UserPackageSummaryStats nextUserPackageSummaryStats = {
            .topNIoReads = {{1009, "mount", UserPackageStats::IoStatsView{{0, 5'000}, {0, 50}}}},
            .topNIoWrites = {{1009, "mount", UserPackageStats::IoStatsView{{0, 3'000}, {0, 50}}}},
            .topNIoBlocked = {{1009, "mount",
                               UserPackageStats::ProcSingleStatsView{2, {{"disk I/O", 2}}}}},
            .topNMajorFaults = {{1009, "mount",
                                 UserPackageStats::ProcSingleStatsView{6'000,
                                                                       {{"disk I/O", 6'000}}}}},
            .totalIoStats = {{0, 5'000}, {0, 3'000}, {0, 50}},
            .taskCountByUid = {{1009, 1}},
            .totalCpuTimeMillis = 48'376,
            .totalCpuCycles = 3'500,
            .totalMajorFaults = 6'000,
            .majorFaultsPercentChange = (6'000.0 - 84'345.0) / 84'345.0 * 100.0,
    };

    ProcStatInfo nextProcStatInfo = procStatInfo;
    SystemSummaryStats nextSystemSummaryStats = systemSummaryStats;

    nextProcStatInfo.contextSwitchesCount = 300;
    nextSystemSummaryStats.totalCpuCycles = 3'500;
    nextSystemSummaryStats.contextSwitchesCount = 300;

    EXPECT_CALL(*mMockUidStatsCollector, deltaStats()).WillOnce(Return(nextUidStats));
    EXPECT_CALL(*mMockProcStatCollector, deltaStats()).WillOnce(Return(nextProcStatInfo));

    ASSERT_RESULT_OK(mCollector->onUserSwitchCollection(kTestNow + std::chrono::seconds(2), 100,
                                                        101, mMockUidStatsCollector,
                                                        mMockProcStatCollector));

    auto& continuationActualInfos = mCollectorPeer->getUserSwitchCollectionInfos();
    auto& continuationActual = continuationActualInfos[0];

    expected = {
            {
                    .maxCacheSize = std::numeric_limits<std::size_t>::max(),
                    .records = {{.systemSummaryStats = systemSummaryStats,
                                 .userPackageSummaryStats = userPackageSummaryStats},
                                {.systemSummaryStats = nextSystemSummaryStats,
                                 .userPackageSummaryStats = nextUserPackageSummaryStats}},
            },
            .from = 100,
            .to = 101,
    };

    EXPECT_THAT(continuationActualInfos.size(), 1);

    EXPECT_THAT(continuationActual, UserSwitchCollectionInfoEq(expected))
            << "User switch collection info after continuation doesn't match.\nExpected:\n"
            << expected.toString() << "\nActual:\n"
            << actual.toString();

    ASSERT_NO_FATAL_FAILURE(checkDumpContents(/*wantedEmptyCollectionInstances=*/3))
            << "Boot-time, wake-up and periodic collections shouldn't be reported";
}

TEST_F(PerformanceProfilerTest, TestUserSwitchCollectionsMaxCacheSize) {
    auto [uidStats, userPackageSummaryStats] = sampleUidStats();
    auto [procStatInfo, systemSummaryStats] = sampleProcStat();

    EXPECT_CALL(*mMockUidStatsCollector, deltaStats()).WillRepeatedly(Return(uidStats));
    EXPECT_CALL(*mMockProcStatCollector, deltaStats()).WillRepeatedly(Return(procStatInfo));

    std::vector<UserSwitchCollectionInfo> expectedEvents;
    for (userid_t userId = 100; userId < 100 + kTestMaxUserSwitchEvents; ++userId) {
        expectedEvents.push_back({
                {
                        .maxCacheSize = std::numeric_limits<std::size_t>::max(),
                        .records = {{
                                .systemSummaryStats = systemSummaryStats,
                                .userPackageSummaryStats = userPackageSummaryStats,
                        }},
                },
                .from = userId,
                .to = userId + 1,
        });
    }

    for (userid_t userId = 100; userId < 100 + kTestMaxUserSwitchEvents; ++userId) {
        ASSERT_RESULT_OK(mCollector->onUserSwitchCollection(kTestNow, userId, userId + 1,
                                                            mMockUidStatsCollector,
                                                            mMockProcStatCollector));
    }

    const auto& actual = mCollectorPeer->getUserSwitchCollectionInfos();

    EXPECT_THAT(actual.size(), kTestMaxUserSwitchEvents);

    EXPECT_THAT(actual, UserSwitchCollectionsEq(expectedEvents))
            << "User switch collection infos don't match.";

    // Add new user switch event with max cache size. The oldest user switch event should be dropped
    // and the new one added to the cache.
    userid_t userId = 100 + kTestMaxUserSwitchEvents;

    expectedEvents.push_back({
            {
                    .maxCacheSize = std::numeric_limits<std::size_t>::max(),
                    .records = {{
                            .systemSummaryStats = systemSummaryStats,
                            .userPackageSummaryStats = userPackageSummaryStats,
                    }},
            },
            .from = userId,
            .to = userId + 1,
    });
    expectedEvents.erase(expectedEvents.begin());

    ASSERT_RESULT_OK(mCollector->onUserSwitchCollection(kTestNow, userId, userId + 1,
                                                        mMockUidStatsCollector,
                                                        mMockProcStatCollector));

    const auto& actualInfos = mCollectorPeer->getUserSwitchCollectionInfos();

    EXPECT_THAT(actualInfos.size(), kTestMaxUserSwitchEvents);

    EXPECT_THAT(actualInfos, UserSwitchCollectionsEq(expectedEvents))
            << "User switch collection infos don't match.";
}

TEST_F(PerformanceProfilerTest, TestOnPeriodicCollection) {
    const auto [uidStats, userPackageSummaryStats] = sampleUidStats();
    const auto [procStatInfo, systemSummaryStats] = sampleProcStat();

    EXPECT_CALL(*mMockUidStatsCollector, deltaStats()).WillOnce(Return(uidStats));
    EXPECT_CALL(*mMockProcStatCollector, deltaStats()).WillOnce(Return(procStatInfo));

    ResourceStats actualResourceStats = {};
    ASSERT_RESULT_OK(mCollector->onPeriodicCollection(kTestNow, SystemState::NORMAL_MODE,
                                                      mMockUidStatsCollector,
                                                      mMockProcStatCollector,
                                                      &actualResourceStats));

    const auto actual = mCollectorPeer->getPeriodicCollectionInfo();

    const CollectionInfo expected{
            .maxCacheSize = static_cast<size_t>(sysprop::periodicCollectionBufferSize().value_or(
                    kDefaultPeriodicCollectionBufferSize)),
            .records = {{
                    .systemSummaryStats = systemSummaryStats,
                    .userPackageSummaryStats = userPackageSummaryStats,
            }},
    };

    EXPECT_THAT(actual, CollectionInfoEq(expected))
            << "Periodic collection info doesn't match.\nExpected:\n"
            << expected.toString() << "\nActual:\n"
            << actual.toString();

    ASSERT_NO_FATAL_FAILURE(checkDumpContents(/*wantedEmptyCollectionInstances=*/3))
            << "Boot-time, wake-up and user-switch collections shouldn't be reported";
}

TEST_F(PerformanceProfilerTest, TestOnCustomCollectionWithoutPackageFilter) {
    const auto [uidStats, userPackageSummaryStats] = sampleUidStats();
    const auto [procStatInfo, systemSummaryStats] = sampleProcStat();

    EXPECT_CALL(*mMockUidStatsCollector, deltaStats()).WillOnce(Return(uidStats));
    EXPECT_CALL(*mMockProcStatCollector, deltaStats()).WillOnce(Return(procStatInfo));

    ResourceStats actualResourceStats = {};
    ASSERT_RESULT_OK(mCollector->onCustomCollection(kTestNow, SystemState::NORMAL_MODE, {},
                                                    mMockUidStatsCollector, mMockProcStatCollector,
                                                    &actualResourceStats));

    const auto actual = mCollectorPeer->getCustomCollectionInfo();

    CollectionInfo expected{
            .maxCacheSize = std::numeric_limits<std::size_t>::max(),
            .records = {{
                    .systemSummaryStats = systemSummaryStats,
                    .userPackageSummaryStats = userPackageSummaryStats,
            }},
    };

    EXPECT_THAT(actual, CollectionInfoEq(expected))
            << "Custom collection info doesn't match.\nExpected:\n"
            << expected.toString() << "\nActual:\n"
            << actual.toString();

    ASSERT_NO_FATAL_FAILURE(checkCustomDumpContents()) << "Custom collection should be reported";

    TemporaryFile customDump;
    ASSERT_RESULT_OK(mCollector->onCustomCollectionDump(customDump.fd));

    // Should clear the cache.
    ASSERT_RESULT_OK(mCollector->onCustomCollectionDump(-1));

    expected.records.clear();
    const CollectionInfo& emptyCollectionInfo = mCollectorPeer->getCustomCollectionInfo();
    EXPECT_THAT(emptyCollectionInfo, CollectionInfoEq(expected))
            << "Custom collection should be cleared.";
}

TEST_F(PerformanceProfilerTest, TestOnCustomCollectionWithPackageFilter) {
    // Filter by package name should ignore this limit with package filter.
    mCollectorPeer->setTopNStatsPerCategory(1);

    const auto [uidStats, _] = sampleUidStats();
    const auto [procStatInfo, systemSummaryStats] = sampleProcStat();

    EXPECT_CALL(*mMockUidStatsCollector, deltaStats()).WillOnce(Return(uidStats));
    EXPECT_CALL(*mMockProcStatCollector, deltaStats()).WillOnce(Return(procStatInfo));

    ResourceStats resourceStats = {};
    ASSERT_RESULT_OK(mCollector->onCustomCollection(kTestNow, SystemState::NORMAL_MODE,
                                                    {"mount", "com.google.android.car.kitchensink"},
                                                    mMockUidStatsCollector, mMockProcStatCollector,
                                                    &resourceStats));

    const auto actual = mCollectorPeer->getCustomCollectionInfo();

    UserPackageSummaryStats userPackageSummaryStats{
            .topNCpuTimes =
                    {{1009, "mount",
                      UserPackageStats::ProcCpuStatsView{50, 4'000, {{"disk I/O", 50, 4'000}}}},
                     {1002001, "com.google.android.car.kitchensink",
                      UserPackageStats::ProcCpuStatsView{60,
                                                         10'000,
                                                         {{"CTS", 25, 5'000},
                                                          {"KitchenSinkApp", 25, 4'000}}}}},
            .topNIoReads = {{1009, "mount", UserPackageStats::IoStatsView{{0, 14'000}, {0, 100}}},
                            {1002001, "com.google.android.car.kitchensink",
                             UserPackageStats::IoStatsView{{0, 3'400}, {0, 200}}}},
            .topNIoWrites = {{1009, "mount", UserPackageStats::IoStatsView{{0, 16'000}, {0, 100}}},
                             {1002001, "com.google.android.car.kitchensink",
                              UserPackageStats::IoStatsView{{0, 6'700}, {0, 200}}}},
            .topNIoBlocked =
                    {{1009, "mount", UserPackageStats::ProcSingleStatsView{1, {{"disk I/O", 1}}}},
                     {1002001, "com.google.android.car.kitchensink",
                      UserPackageStats::ProcSingleStatsView{3,
                                                            {{"CTS", 2}, {"KitchenSinkApp", 1}}}}},
            .topNMajorFaults = {{1009, "mount",
                                 UserPackageStats::ProcSingleStatsView{11'000,
                                                                       {{"disk I/O", 11'000}}}},
                                {1002001, "com.google.android.car.kitchensink",
                                 UserPackageStats::ProcSingleStatsView{22'445,
                                                                       {{"KitchenSinkApp", 12'345},
                                                                        {"CTS", 10'100}}}}},
            .totalIoStats = {{1000, 21'600}, {300, 28'300}, {600, 600}},
            .taskCountByUid = {{1009, 1}, {1002001, 5}},
            .totalCpuTimeMillis = 48'376,
            .totalCpuCycles = 64'000,
            .totalMajorFaults = 84'345,
            .majorFaultsPercentChange = 0.0,
    };

    CollectionInfo expected{
            .maxCacheSize = std::numeric_limits<std::size_t>::max(),
            .records = {{
                    .systemSummaryStats = systemSummaryStats,
                    .userPackageSummaryStats = userPackageSummaryStats,
            }},
    };

    EXPECT_THAT(actual, CollectionInfoEq(expected))
            << "Custom collection info doesn't match.\nExpected:\n"
            << expected.toString() << "\nActual:\n"
            << actual.toString();

    ASSERT_NO_FATAL_FAILURE(checkCustomDumpContents()) << "Custom collection should be reported";

    TemporaryFile customDump;
    ASSERT_RESULT_OK(mCollector->onCustomCollectionDump(customDump.fd));

    // Should clear the cache.
    ASSERT_RESULT_OK(mCollector->onCustomCollectionDump(-1));

    expected.records.clear();
    const CollectionInfo& emptyCollectionInfo = mCollectorPeer->getCustomCollectionInfo();
    EXPECT_THAT(emptyCollectionInfo, CollectionInfoEq(expected))
            << "Custom collection should be cleared.";
}

TEST_F(PerformanceProfilerTest, TestOnPeriodicCollectionWithTrimmingStatsAfterTopN) {
    mCollectorPeer->setTopNStatsPerCategory(1);
    mCollectorPeer->setTopNStatsPerSubcategory(1);

    const auto [uidStats, _] = sampleUidStats();
    const auto [procStatInfo, systemSummaryStats] = sampleProcStat();

    EXPECT_CALL(*mMockUidStatsCollector, deltaStats()).WillOnce(Return(uidStats));
    EXPECT_CALL(*mMockProcStatCollector, deltaStats()).WillOnce(Return(procStatInfo));

    ResourceStats resourceStats = {};
    ASSERT_RESULT_OK(mCollector->onPeriodicCollection(kTestNow, SystemState::NORMAL_MODE,
                                                      mMockUidStatsCollector,
                                                      mMockProcStatCollector, &resourceStats));

    const auto actual = mCollectorPeer->getPeriodicCollectionInfo();

    UserPackageSummaryStats userPackageSummaryStats{
            .topNCpuTimes =
                    {{1012345, "1012345",
                      UserPackageStats::ProcCpuStatsView{100, 50'000, {{"MapsApp", 100, 50'000}}}}},
            .topNIoReads = {{1009, "mount", UserPackageStats::IoStatsView{{0, 14'000}, {0, 100}}}},
            .topNIoWrites = {{1009, "mount", UserPackageStats::IoStatsView{{0, 16'000}, {0, 100}}}},
            .topNIoBlocked = {{1002001, "com.google.android.car.kitchensink",
                               UserPackageStats::ProcSingleStatsView{3, {{"CTS", 2}}}}},
            .topNMajorFaults = {{1012345, "1012345",
                                 UserPackageStats::ProcSingleStatsView{50'900,
                                                                       {{"MapsApp", 50'900}}}}},
            .totalIoStats = {{1000, 21'600}, {300, 28'300}, {600, 600}},
            .taskCountByUid = {{1009, 1}, {1002001, 5}, {1012345, 4}},
            .totalCpuTimeMillis = 48'376,
            .totalCpuCycles = 64'000,
            .totalMajorFaults = 84'345,
            .majorFaultsPercentChange = 0.0,
    };

    const CollectionInfo expected{
            .maxCacheSize = static_cast<size_t>(sysprop::periodicCollectionBufferSize().value_or(
                    kDefaultPeriodicCollectionBufferSize)),
            .records = {{
                    .systemSummaryStats = systemSummaryStats,
                    .userPackageSummaryStats = userPackageSummaryStats,
            }},
    };

    EXPECT_THAT(actual, CollectionInfoEq(expected))
            << "Periodic collection info doesn't match.\nExpected:\n"
            << expected.toString() << "\nActual:\n"
            << actual.toString();

    ASSERT_NO_FATAL_FAILURE(checkDumpContents(/*wantedEmptyCollectionInstances=*/3))
            << "Boot-time, wake-up and user-switch collections shouldn't be reported";
}

TEST_F(PerformanceProfilerTest, TestConsecutiveOnPeriodicCollection) {
    const auto [firstUidStats, firstUserPackageSummaryStats] = sampleUidStats();
    const auto [firstProcStatInfo, firstSystemSummaryStats] = sampleProcStat();

    EXPECT_CALL(*mMockUidStatsCollector, deltaStats()).WillOnce(Return(firstUidStats));
    EXPECT_CALL(*mMockProcStatCollector, deltaStats()).WillOnce(Return(firstProcStatInfo));

    ResourceStats actualResourceStats = {};
    ASSERT_RESULT_OK(mCollector->onPeriodicCollection(kTestNow, SystemState::NORMAL_MODE,
                                                      mMockUidStatsCollector,
                                                      mMockProcStatCollector,
                                                      &actualResourceStats));

    auto [secondUidStats, secondUserPackageSummaryStats] = sampleUidStats(/*multiplier=*/2);
    const auto [secondProcStatInfo, secondSystemSummaryStats] = sampleProcStat(/*multiplier=*/2);

    secondUserPackageSummaryStats.majorFaultsPercentChange =
            (static_cast<double>(secondUserPackageSummaryStats.totalMajorFaults -
                                 firstUserPackageSummaryStats.totalMajorFaults) /
             static_cast<double>(firstUserPackageSummaryStats.totalMajorFaults)) *
            100.0;

    EXPECT_CALL(*mMockUidStatsCollector, deltaStats()).WillOnce(Return(secondUidStats));
    EXPECT_CALL(*mMockProcStatCollector, deltaStats()).WillOnce(Return(secondProcStatInfo));

    ASSERT_RESULT_OK(mCollector->onPeriodicCollection(kTestNow, SystemState::NORMAL_MODE,
                                                      mMockUidStatsCollector,
                                                      mMockProcStatCollector,
                                                      &actualResourceStats));

    const auto actual = mCollectorPeer->getPeriodicCollectionInfo();

    const CollectionInfo expected{
            .maxCacheSize = static_cast<size_t>(sysprop::periodicCollectionBufferSize().value_or(
                    kDefaultPeriodicCollectionBufferSize)),
            .records = {{.systemSummaryStats = firstSystemSummaryStats,
                         .userPackageSummaryStats = firstUserPackageSummaryStats},
                        {.systemSummaryStats = secondSystemSummaryStats,
                         .userPackageSummaryStats = secondUserPackageSummaryStats}},
    };

    EXPECT_THAT(actual, CollectionInfoEq(expected))
            << "Periodic collection info doesn't match.\nExpected:\n"
            << expected.toString() << "\nActual:\n"
            << actual.toString();

    ASSERT_NO_FATAL_FAILURE(checkDumpContents(/*wantedEmptyCollectionInstances=*/3))
            << "Boot-time, wake-up and user-switch collection shouldn't be reported";
}

TEST_F(PerformanceProfilerTest, TestBoottimeCollectionCacheEviction) {
    const auto [uidStats, userPackageSummaryStats] = sampleUidStats();
    const auto [procStatInfo, systemSummaryStats] = sampleProcStat();

    EXPECT_CALL(*mMockUidStatsCollector, deltaStats()).WillRepeatedly(Return(uidStats));
    EXPECT_CALL(*mMockProcStatCollector, deltaStats()).WillRepeatedly(Return(procStatInfo));

    ResourceStats resourceStats = {};
    ASSERT_RESULT_OK(mCollector->onBoottimeCollection(kTestNow, mMockUidStatsCollector,
                                                      mMockProcStatCollector, &resourceStats));

    auto actual = mCollectorPeer->getBoottimeCollectionInfo();

    EXPECT_THAT(actual.records.size(), 1) << "Boot-time collection info doesn't have size 1";

    // Call |onPeriodicCollection| 1 hour past the last boot-time collection event.
    ASSERT_RESULT_OK(
            mCollector->onPeriodicCollection(kTestNow + kTestSystemEventDataCacheDurationSec,
                                             SystemState::NORMAL_MODE, mMockUidStatsCollector,
                                             mMockProcStatCollector, &resourceStats));

    actual = mCollectorPeer->getBoottimeCollectionInfo();

    EXPECT_THAT(actual.records.empty(), true) << "Boot-time collection info records are not empty";
}

TEST_F(PerformanceProfilerTest, TestWakeUpCollectionCacheEviction) {
    const auto [uidStats, userPackageSummaryStats] = sampleUidStats();
    const auto [procStatInfo, systemSummaryStats] = sampleProcStat();

    EXPECT_CALL(*mMockUidStatsCollector, deltaStats()).WillRepeatedly(Return(uidStats));
    EXPECT_CALL(*mMockProcStatCollector, deltaStats()).WillRepeatedly(Return(procStatInfo));

    ASSERT_RESULT_OK(mCollector->onWakeUpCollection(kTestNow, mMockUidStatsCollector,
                                                    mMockProcStatCollector));

    auto actual = mCollectorPeer->getWakeUpCollectionInfo();

    EXPECT_THAT(actual.records.size(), 1) << "Wake-up collection info doesn't have size 1";

    ResourceStats resourceStats = {};

    // Call |onPeriodicCollection| 1 hour past the last wake-up collection event.
    ASSERT_RESULT_OK(
            mCollector->onPeriodicCollection(kTestNow + kTestSystemEventDataCacheDurationSec,
                                             SystemState::NORMAL_MODE, mMockUidStatsCollector,
                                             mMockProcStatCollector, &resourceStats));

    actual = mCollectorPeer->getWakeUpCollectionInfo();

    EXPECT_THAT(actual.records.empty(), true) << "Wake-up collection info records are not empty";
}

TEST_F(PerformanceProfilerTest, TestUserSwitchCollectionCacheEviction) {
    auto [uidStats, userPackageSummaryStats] = sampleUidStats();
    auto [procStatInfo, systemSummaryStats] = sampleProcStat();

    EXPECT_CALL(*mMockUidStatsCollector, deltaStats()).WillRepeatedly(Return(uidStats));
    EXPECT_CALL(*mMockProcStatCollector, deltaStats()).WillRepeatedly(Return(procStatInfo));

    auto updatedNow = kTestNow;

    for (userid_t userId = 100; userId < 100 + kTestMaxUserSwitchEvents; ++userId) {
        ASSERT_RESULT_OK(mCollector->onUserSwitchCollection(updatedNow, userId, userId + 1,
                                                            mMockUidStatsCollector,
                                                            mMockProcStatCollector));
        updatedNow += kTestSystemEventDataCacheDurationSec;
    }

    const auto& actual = mCollectorPeer->getUserSwitchCollectionInfos();

    EXPECT_THAT(actual.size(), kTestMaxUserSwitchEvents);

    updatedNow = kTestNow + kTestSystemEventDataCacheDurationSec;
    ResourceStats resourceStats = {};
    for (int i = 1; i <= kTestMaxUserSwitchEvents; ++i) {
        ASSERT_RESULT_OK(mCollector->onPeriodicCollection(updatedNow, SystemState::NORMAL_MODE,
                                                          mMockUidStatsCollector,
                                                          mMockProcStatCollector, &resourceStats));

        const auto& actual = mCollectorPeer->getUserSwitchCollectionInfos();

        EXPECT_THAT(actual.size(), kTestMaxUserSwitchEvents - i)
                << "User-switch collection size is incorrect";

        updatedNow += kTestSystemEventDataCacheDurationSec;
    }
}

TEST_F(PerformanceProfilerTest, TestOnDumpProto) {
    auto [uidStats, userPackageSummaryStats] = sampleUidStats();
    auto [procStatInfo, systemSummaryStats] = sampleProcStat();

    EXPECT_CALL(*mMockUidStatsCollector, deltaStats()).WillRepeatedly(Return(uidStats));
    EXPECT_CALL(*mMockProcStatCollector, deltaStats()).WillRepeatedly(Return(procStatInfo));

    DataProcessorInterface::CollectionIntervals collectionIntervals =
            {.mBoottimeIntervalMillis = std::chrono::milliseconds(1),
             .mPeriodicIntervalMillis = std::chrono::milliseconds(10),
             .mUserSwitchIntervalMillis = std::chrono::milliseconds(100),
             .mWakeUpIntervalMillis = std::chrono::milliseconds(1000),
             .mCustomIntervalMillis = std::chrono::milliseconds(10000)};

    ResourceStats actualResourceStats = {};

    ASSERT_RESULT_OK(mCollector->onPeriodicCollection(kTestNow, SystemState::NORMAL_MODE,
                                                      mMockUidStatsCollector,
                                                      mMockProcStatCollector,
                                                      &actualResourceStats));

    ASSERT_RESULT_OK(mCollector->onBoottimeCollection(kTestNow, mMockUidStatsCollector,
                                                      mMockProcStatCollector,
                                                      &actualResourceStats));

    ASSERT_RESULT_OK(mCollector->onWakeUpCollection(kTestNow, mMockUidStatsCollector,
                                                    mMockProcStatCollector));

    ASSERT_RESULT_OK(mCollector->onCustomCollection(kTestNow, SystemState::NORMAL_MODE, {},
                                                    mMockUidStatsCollector, mMockProcStatCollector,
                                                    &actualResourceStats));

    util::ProtoOutputStream proto;
    mCollector->onDumpProto(collectionIntervals, proto);

    PerformanceProfilerDump performanceProfilerDump;
    ASSERT_TRUE(performanceProfilerDump.ParseFromString(toString(&proto)));

    PerformanceStats performanceStats = performanceProfilerDump.performance_stats();
    auto bootTimeStats = performanceStats.boot_time_stats();
    EXPECT_EQ(bootTimeStats.collection_interval_millis(), 1);
    for (auto& record : bootTimeStats.records()) {
        EXPECT_THAT(record,
                    StatsRecordProtoEq(userPackageSummaryStats, systemSummaryStats, kTestNow));
    }

    auto wakeUpStats = performanceStats.wake_up_stats();
    EXPECT_EQ(wakeUpStats.collection_interval_millis(), 1000);
    for (auto& record : wakeUpStats.records()) {
        EXPECT_THAT(record,
                    StatsRecordProtoEq(userPackageSummaryStats, systemSummaryStats, kTestNow));
    }

    auto lastNMinutesStats = performanceStats.last_n_minutes_stats();
    EXPECT_EQ(lastNMinutesStats.collection_interval_millis(), 10);
    for (auto& record : lastNMinutesStats.records()) {
        EXPECT_THAT(record,
                    StatsRecordProtoEq(userPackageSummaryStats, systemSummaryStats, kTestNow));
    }

    auto customCollectionStats = performanceStats.custom_collection_stats();
    EXPECT_EQ(customCollectionStats.collection_interval_millis(), 10000);
    for (auto& record : customCollectionStats.records()) {
        EXPECT_THAT(record,
                    StatsRecordProtoEq(userPackageSummaryStats, systemSummaryStats, kTestNow));
    }
}

}  // namespace watchdog
}  // namespace automotive
}  // namespace android
