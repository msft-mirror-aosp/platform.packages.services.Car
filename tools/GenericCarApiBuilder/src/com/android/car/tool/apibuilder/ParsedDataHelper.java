/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.car.tool.apibuilder;

import com.android.car.tool.data.ClassData;
import com.android.car.tool.data.ConstructorData;
import com.android.car.tool.data.FieldData;
import com.android.car.tool.data.MethodData;
import com.android.car.tool.data.PackageData;
import com.android.car.tool.data.ParsedData;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ParsedDataHelper {

    public static List<String> getClassNamesOnly(ParsedData parsedData) {
        List<String> classes = new ArrayList<>();
        parsedData.packages.values().forEach((packageData) -> packageData.classes.values()
                .forEach((classData) -> classes.add(classData.useableClassName)));
        return classes;
    }

    public static List<String> getAddedInOrBeforeApisOnly(ParsedData parsedData) {
        List<String> fieldsAndMethods = new ArrayList<>();
        parsedData.packages.values().forEach((packageData) -> packageData.classes.values()
                .forEach((classData) -> classData.fields.values().forEach(
                        (field) -> {
                            if (field.annotationData.hasAddedInOrBefore) {
                                fieldsAndMethods.add(packageData.packageName + "."
                                        + classData.onlyClassName + "." + field.fieldName);
                            }
                        })));
        parsedData.packages.values().forEach((packageData) -> packageData.classes.values()
                .forEach((classData) -> classData.methods.values().forEach(
                        (method) -> {
                            if (method.annotationData.hasAddedInOrBefore) {
                                fieldsAndMethods.add(packageData.packageName + "."
                                        + classData.onlyClassName + "." + method.methodName);
                            }
                        })));
        return fieldsAndMethods;
    }

    public static List<String> getHiddenApisOnly(ParsedData parsedData) {
        List<String> fieldsAndMethods = new ArrayList<>();
        parsedData.packages.values().forEach((packageData) -> packageData.classes.values()
                .forEach((classData) -> classData.fields.values().forEach(
                        (field) -> {
                            if ((!field.annotationData.isSystemApi && field.isHidden)
                                    || (classData.isClassHidden
                                            && !classData.annotationData.isSystemApi)) {
                                fieldsAndMethods
                                        .add(formatFieldString(packageData, classData, field));
                            }
                        })));
        parsedData.packages.values().forEach((packageData) -> packageData.classes.values()
                .forEach((classData) -> classData.methods.values().forEach(
                        (method) -> {
                            if ((!method.annotationData.isSystemApi && method.isHidden)
                                    || (classData.isClassHidden
                                            && !classData.annotationData.isSystemApi)) {
                                fieldsAndMethods
                                        .add(formatMethodString(packageData, classData, method));
                            }
                        })));
        return fieldsAndMethods;
    }

    public static List<String> getHiddenApisWithHiddenConstructor(ParsedData parsedData) {
        List<String> allHiddenApis = getHiddenApisOnly(parsedData);

        parsedData.packages.values().forEach((packageData) -> packageData.classes.values()
                .forEach((classData) -> classData.constructors.values()
                        .forEach((constructorData) -> {
                            if ((constructorData.isHidden
                                    && !constructorData.annotationData.isSystemApi)
                                    || (classData.isClassHidden
                                            && !classData.annotationData.isSystemApi)) {
                                allHiddenApis.add(formatConstructorString(packageData, classData,
                                        constructorData));
                            }
                        })));

        return allHiddenApis;
    }

    public static List<String> getAllApis(ParsedData parsedData) {
        List<String> fieldsAndMethods = new ArrayList<>();
        parsedData.packages.values().forEach((packageData) -> packageData.classes.values()
                .forEach((classData) -> classData.fields.values().forEach(
                        (field) -> {
                            fieldsAndMethods.add(formatFieldString(packageData, classData, field));
                        })));
        parsedData.packages.values().forEach((packageData) -> packageData.classes.values()
                .forEach((classData) -> classData.methods.values().forEach(
                        (method) -> {
                            fieldsAndMethods
                                    .add(formatMethodString(packageData, classData, method));
                        })));
        return fieldsAndMethods;
    }

    public static List<String> getAllApisWithConstructor(ParsedData parsedData) {
        List<String> allApis = getAllApis(parsedData);

        parsedData.packages.values().forEach((packageData) -> packageData.classes.values()
                .forEach((classData) -> classData.constructors.values()
                        .forEach((constructorData) -> {
                            allApis.add(formatConstructorString(packageData, classData,
                                    constructorData));
                        })));

        return allApis;
    }

    // TODO(b/278759600): add tests for assertPlatformVersionAtLeast check
    public static List<String> checkAssertPlatformVersionAtLeast(
            ParsedData parsedData) {
        List<String> apis = new ArrayList<>();
        parsedData.packages.values().forEach((packageData) -> packageData.classes.values()
                .forEach((classData) -> classData.methods.values().forEach(
                        (method) -> {
                            // Only check that assertPlatformVersionAtLeast is present for APIs
                            // added after TIRAMISU_x.
                            if (!method.annotationData.hasApiRequirementAnnotation
                                    || method.annotationData.minPlatformVersion.contains(
                                    "TIRAMISU") || method.firstBodyStatement == null) {
                                return;
                            }
                            // Check that assertPlatformVersionAtLeast is called and that it has
                            // the correct
                            // version as its argument.
                            if (method.firstBodyStatement.getName().asString().contains(
                                    "assertPlatformVersionAtLeast")
                                    && Objects.equals(method.firstBodyStatement.getArgument(
                                            0).asNameExpr().getNameAsString(),
                                    method.annotationData.minPlatformVersion)) {
                                return;
                            }
                            apis.add(formatMethodString(packageData, classData, method));
                        })));

        return apis;
    }

    public static List<String> getApisWithVersion(ParsedData parsedData) {
        List<String> apisWithVersion = new ArrayList<>();

        parsedData.packages.values().forEach((packageData) -> packageData.classes.values()
                .forEach((classData) -> classData.fields.values().forEach(
                        (field) -> {
                            String minCarVersion = "";
                            if (field.annotationData.hasAddedInAnnotation) {
                                minCarVersion = field.annotationData.addedInPlatformVersion;
                            } else if (field.annotationData.hasAddedInOrBefore) {
                                minCarVersion =
                                        "TIRAMISU_" + field.annotationData.addedInPlatformVersion;
                            } else {
                                minCarVersion = field.annotationData.minCarVersion;
                            }
                            apisWithVersion.add(
                                    formatFieldString(packageData, classData, field) + " | "
                                            + minCarVersion);
                        })));
        parsedData.packages.values().forEach((packageData) -> packageData.classes.values()
                .forEach((classData) -> classData.methods.values().forEach(
                        (method) -> {
                            String minCarVersion = "";
                            if (method.annotationData.hasAddedInAnnotation) {
                                minCarVersion = method.annotationData.addedInPlatformVersion;
                            } else if (method.annotationData.hasAddedInOrBefore) {
                                minCarVersion =
                                        "TIRAMISU_" + method.annotationData.addedInPlatformVersion;
                            } else {
                                minCarVersion = method.annotationData.minCarVersion;
                            }
                            apisWithVersion.add(
                                    formatMethodString(packageData, classData, method) + " | "
                                            + minCarVersion);
                        })));

        return apisWithVersion;
    }

    /**
     * Gives incorrect usage of requires APIs.
     */
    // TODO(b/277617236): add tests for this
    public static List<String> getIncorrectRequiresApiUsage(ParsedData parsedData) {
        List<String> incorrectRequiresApiUsage = new ArrayList<>();
        parsedData.packages.values().forEach((packageData) -> packageData.classes.values()
                .forEach((classData) -> {
                    if (classData.annotationData.hasRequiresApiAnnotation) {
                        incorrectRequiresApiUsage.add(classData.useableClassName + " "
                                + classData.annotationData.requiresApiVersion);
                    }
                }));
        parsedData.packages.values().forEach((packageData) -> packageData.classes.values()
                .forEach((classData) -> classData.methods.values().forEach(
                        (method) -> {
                            if (method.annotationData.hasRequiresApiAnnotation) {
                                incorrectRequiresApiUsage.add(classData.useableClassName + "."
                                        + method.methodName
                                        + " " + method.annotationData.requiresApiVersion);
                            }
                        })));
        return incorrectRequiresApiUsage;
    }

    private static String formatMethodString(PackageData packageData, ClassData classData,
            MethodData method) {
        return packageData.packageName + " "
                + classData.onlyClassName + " " + method.returnType + " "
                + method.fullMethodname;
    }

    private static String formatFieldString(PackageData packageData, ClassData classData,
            FieldData field) {
        return packageData.packageName + " "
                + classData.onlyClassName + " " + field.fieldType + " "
                + field.fieldName;
    }

    private static String formatConstructorString(PackageData packageData, ClassData classData,
            ConstructorData constructorData) {
        return packageData.packageName + " "
                + classData.onlyClassName + " "
                + constructorData.constructorName + " "
                + constructorData.fullConstructorName;
    }
}
