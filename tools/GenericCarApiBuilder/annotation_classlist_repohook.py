#!/usr/bin/env python3
#  Copyright (C) 2022 The Android Open Source Project
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.
import sys
import os
import subprocess

rootDir = os.getenv("ANDROID_BUILD_TOP")
if (rootDir is None):
    # env variable is not set. Then use the arg passed as Git root
    rootDir = sys.argv[1]

# Generate class list using tool
java_cmd = "java -jar " + rootDir + "/packages/services/Car/tools/GenericCarApiBuilder" \
                                    "/GenericCarApiBuilder.jar --print-classes-only " \
                                    "--ANDROID-BUILD-TOP " + rootDir
new_class_list = subprocess.check_output(java_cmd, shell=True).decode('utf-8').strip().split("\n")

# Read current class list
car_api = rootDir + "/packages/services/Car/tests/carservice_unit_test/res/raw/car_api_classes.txt"
car_built_in_api = rootDir + "/packages/services/Car/tests/carservice_unit_test/res/raw" \
                             "/car_built_in_api_classes.txt"
existing_class_list = []
with open(car_api) as f:
    existing_class_list.extend(f.read().splitlines())
with open(car_built_in_api) as f:
    existing_class_list.extend(f.read().splitlines())


# Find the diff in both class list
extra_new_classes = [i for i in new_class_list if i not in existing_class_list]
extra_deleted_classes = [i for i in existing_class_list if i not in new_class_list]

# Print error is there is any class added or removed without changing test
error = ""
if len(extra_deleted_classes) > 0:
    error = error + "Following Classes are deleted \n" + "\n".join(extra_deleted_classes)
if len(extra_new_classes) > 0:
    error = error + "\n\nFollowing new classes are added \n" + "\n".join(extra_new_classes)

if error != "":
    print(error)
    print("\nRun following command to generate classlist for annotation test")
    print("cd $ANDROID_BUILD_TOP && m -j GenericCarApiBuilder && GenericCarApiBuilder "
          "--update-classes-for-test")
    print("\nThen run following test to make sure classes are properly annotated")
    print("atest CarServiceUnitTest:android.car.AnnotationTest")
    sys.exit(1)

# Class list is okay. Check if any hidden API is modified or removed.
java_cmd = "java -jar " + rootDir + "/packages/services/Car/tools/GenericCarApiBuilder" \
                                    "/GenericCarApiBuilder.jar --print-hidden-api-for-test " \
                                    "--ANDROID-BUILD-TOP " + rootDir
new_hidden_apis = subprocess.check_output(java_cmd, shell=True).decode('utf-8').strip().split("\n")

# Determine all remaining hidden, system or public APIs:
java_cmd = "java -jar " + rootDir + "/packages/services/Car/tools/GenericCarApiBuilder" \
                                    "/GenericCarApiBuilder.jar --print-shortform-full-api-for-test " \
                                    "--ANDROID-BUILD-TOP " + rootDir
all_apis = subprocess.check_output(java_cmd, shell=True).decode('utf-8').strip().split("\n")

# read existing hidden APIs
previous_hidden_apis_path = rootDir + "/packages/services/Car/tests/carservice_unit_test/res/raw" \
                             "/car_hidden_apis.txt"

# TODO(b/263402554): Ideally the file `car_hidden_apis_current_release` should be a list of
# the hidden APIs of the previous release, because we want to compare with hidden APIs
# already exposed in previous releases. For example, if some hidden API is added in T-QPR
# and removed in master, then we should be able to figure it out. Also accordingly, we have
# to update this file for each release. There could be multiple such files for different releases.
previous_hidden_apis_current_release_path = rootDir + "/packages/services/Car/tests/carservice_unit_test/res/raw" \
                                                      "/car_hidden_apis_current_release.txt"

previous_hidden_apis = []
with open(previous_hidden_apis_path) as f:
    previous_hidden_apis.extend(f.read().splitlines())

previous_hidden_apis_current_release = []
with open(previous_hidden_apis_current_release_path) as f:
    previous_hidden_apis_current_release.extend(f.read().splitlines())

# All new_hidden_apis should be in previous_hidden_apis. There can be some entry in previous_hidden_apis
# which is not in new_hidden_apis. It is okay as some APIs might have been promoted.
modified_or_added_hidden_api = []
for api in new_hidden_apis:
    if api not in previous_hidden_apis:
        modified_or_added_hidden_api.append(api)

# TODO(b/266849922): Add a pre-submit test to also check for added or modified hidden apis,
# since one could also bypass the repohook tool using --no-verify.
if len(modified_or_added_hidden_api) > 0:
    print("\nHidden APIs should not be added or modified. Following Hidden APIs are modified:")
    print("\n".join(modified_or_added_hidden_api))
    sys.exit(1)

# Hidden APIs should not be removed. Check that any of the previously hidden apis still exist in the remaining apis.
# This is different from hidden APIs that were upgraded to system or public APIs.
removed_hidden_api = []
for api in previous_hidden_apis_current_release:
    if api not in all_apis:
        removed_hidden_api.append(api)

if len(removed_hidden_api) > 0:
    print("\nHidden APIs should not be removed. Following Hidden APIs were removed:")
    print("\n".join(removed_hidden_api))
    sys.exit(1)
