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

package com.android.car.tool;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.javadoc.Javadoc;
import com.github.javaparser.javadoc.JavadocBlockTag;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A parser for VehiclePropertyIds.java.
 *
 * It will parse the vehicle property ID definitions, comments and annotations and generate property
 * config file.
 */
public final class VehiclePropertyIdsParser {

    private static final int CONFIG_FILE_SCHEMA_VERSION = 1;

    private static final String USAGE =
            "VehiclePropertyIdsParser [path_to_VehiclePropertyIds.java] [path_to_Car.java] "
            + "[output]";

    private static final String ACCESS_MODE_READ_LINK =
            "{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}";
    private static final String ACCESS_MODE_WRITE_LINK =
            "{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_WRITE}";
    private static final String ACCESS_MODE_READ_WRITE_LINK =
            "{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE}";

    // A map from property name to VHAL property ID if we use different property ID in car service
    // and in VHAL.
    private static final Map<String, Integer> VHAL_PROP_ID_MAP = Map.ofEntries(
            // VehicleProperty.VEHICLE_SPEED_DISPLAY_UNITS
            Map.entry("VEHICLE_SPEED_DISPLAY_UNITS", 0x11400605)
    );

    // A map to store permissions that are not defined in Car.java. It is not trivial to cross-ref
    // these so just hard-code them here.
    private static final Map<String, String> NON_CAR_PERMISSION_MAP = Map.ofEntries(
            Map.entry("ACCESS_FINE_LOCATION", "android.permission.ACCESS_FINE_LOCATION")
    );

    private static final class PropertyConfig {
        public String propertyName;
        public int propertyId;
        public String description = "";
        public PermissionType readPermission;
        public PermissionType writePermission;
        public boolean deprecated;
        public boolean systemApi;
        public boolean hide;
        public int vhalPropertyId;

        @Override
        public String toString() {
            StringBuilder s = new StringBuilder().append("PropertyConfig{")
                    .append("\n    propertyName: ").append(propertyName)
                    .append("\n    propertyId: ").append(propertyId)
                    .append("\n    description: ").append(description)
                    .append("\n    readPermission: ").append(readPermission)
                    .append("\n    writePermission: ").append(writePermission)
                    .append("\n    deprecated: ").append(deprecated)
                    .append("\n    hide: ").append(hide)
                    .append("\n    systemApi: ").append(systemApi);

            if (vhalPropertyId != 0) {
                s.append("\n    vhalPropertyId: ").append(vhalPropertyId);
            }

            return s.append("\n}").toString();
        }
    }

    private enum ACCESS_MODE {
        READ, WRITE, READ_WRITE
    }

    private static final class PermissionType {
        public String type;
        public String value;
        public List<PermissionType> subPermissions = new ArrayList<>();

        public OrderedJSONObject toJson() throws JSONException {
            OrderedJSONObject jsonPerm = new OrderedJSONObject();
            jsonPerm.put("type", type);
            if (type.equals("single")) {
                jsonPerm.put("value", value);
                return jsonPerm;
            }
            List<OrderedJSONObject> subObjects = new ArrayList<>();
            for (int i = 0; i < subPermissions.size(); i++) {
                subObjects.add(subPermissions.get(i).toJson());
            }
            jsonPerm.put("value", new JSONArray(subObjects));
            return jsonPerm;
        }
    };

    private static void setPermission(PropertyConfig config, ACCESS_MODE accessMode,
            PermissionType permission, boolean forRead, boolean forWrite) {
        if (forRead) {
            if (accessMode == ACCESS_MODE.READ || accessMode == ACCESS_MODE.READ_WRITE) {
                config.readPermission = permission;
            }
        }
        if (forWrite) {
            if (accessMode == ACCESS_MODE.WRITE || accessMode == ACCESS_MODE.READ_WRITE) {
                config.writePermission = permission;
            }
        }
    }

    private static String permNameToValue(String permName, Map<String, String> carPermissionMap) {
        String permStr = carPermissionMap.get(permName);
        if (permStr != null) {
            return permStr;
        }
        permStr = NON_CAR_PERMISSION_MAP.get(permName);
        if (permStr != null) {
            return permStr;
        }
        System.out.println("Permission: " + permName + " unknown, if it is not defined in"
                + " Car.java, you need to add it to NON_CAR_PERMISSION_MAP in parser");
        return null;
    }

    private static PermissionType parsePermAnnotation(AnnotationExpr annotation,
            Map<String, String> carPermissionMap) {
        PermissionType permission = new PermissionType();
        if (annotation.isSingleMemberAnnotationExpr()) {
            permission.type = "single";
            SingleMemberAnnotationExpr single =
                    annotation.asSingleMemberAnnotationExpr();
            Expression member = single.getMemberValue();
            String permName = permNameToValue(member.toString(), carPermissionMap);
            if (permName == null) {
                return null;
            }
            permission.value = permName;
            return permission;
        } else if (annotation.isNormalAnnotationExpr()) {
            NormalAnnotationExpr normal = annotation.asNormalAnnotationExpr();
            boolean any = false;
            String name = normal.getPairs().get(0).getName().toString();
            if (name.equals("anyOf")) {
                permission.type = "anyOf";
            } else if (name.equals("allOf")) {
                permission.type = "allOf";
            } else {
                return null;
            }
            ArrayInitializerExpr expr = normal.getPairs().get(0).getValue()
                    .asArrayInitializerExpr();
            for (Expression permExpr : expr.getValues()) {
                PermissionType subPermission = new PermissionType();
                subPermission.type = "single";
                String permName = permNameToValue(permExpr.toString(), carPermissionMap);
                if (permName == null) {
                    return null;
                }
                subPermission.value = permName;
                permission.subPermissions.add(subPermission);
            }
            return permission;
        }
        System.out.println("The permission annotation is not single or normal expression");
        return null;
    }

    private static void parseAndSetPermAnnotation(AnnotationExpr annotation, PropertyConfig config,
            ACCESS_MODE accessMode, boolean forRead, boolean forWrite,
            Map<String, String> carPermissionMap) {
        if (accessMode == null) {
            return;
        }
        PermissionType permission = parsePermAnnotation(annotation, carPermissionMap);
        if (permission == null) {
            System.out.println("Invalid RequiresPermission annotation: "
                        + annotation + " for property: " + config.propertyName);
            System.exit(1);
        }
        setPermission(config, accessMode, permission, forRead, forWrite);
    }

    // A hacky way to make the key in-order in the JSON object.
    private static final class OrderedJSONObject extends JSONObject{
        OrderedJSONObject() {
            super();
            try {
                Field map = JSONObject.class.getDeclaredField("nameValuePairs");
                map.setAccessible(true);
                map.set(this, new LinkedHashMap<>());
                map.setAccessible(false);
            } catch (IllegalAccessException | NoSuchFieldException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Main function.
     */
    public static void main(final String[] args) throws Exception {
        if (args.length < 3) {
            System.out.println(USAGE);
            System.exit(1);
        }
        String vehiclePropertyIdsJava = args[0];
        String carJava = args[1];
        String output = args[2];
        List<PropertyConfig> propertyConfigs = new ArrayList<>();

        CompilationUnit cu = StaticJavaParser.parse(new File(carJava));
        ClassOrInterfaceDeclaration carClass = cu.getClassByName("Car").get();

        List<FieldDeclaration> carFields = carClass.findAll(FieldDeclaration.class);
        Map<String, String> carPermissionMap = new HashMap<>();
        for (int i = 0; i < carFields.size(); i++) {
            FieldDeclaration fieldDecl = carFields.get(i).asFieldDeclaration();
            if (!fieldDecl.isPublic() || !fieldDecl.isStatic()) {
                continue;
            }
            VariableDeclarator valueDecl = fieldDecl.getVariables().get(0);
            String fieldName = valueDecl.getName().asString();
            if (!fieldName.startsWith("PERMISSION_")) {
                continue;
            }
            carPermissionMap.put("Car." + fieldName,
                    valueDecl.getInitializer().get().asStringLiteralExpr().asString());
        }

        cu = StaticJavaParser.parse(new File(vehiclePropertyIdsJava));
        ClassOrInterfaceDeclaration vehiclePropertyIdsClass =
                cu.getClassByName("VehiclePropertyIds").get();
        List<FieldDeclaration> variables = vehiclePropertyIdsClass.findAll(FieldDeclaration.class);
        for (int i = 0; i < variables.size(); i++) {
            ACCESS_MODE accessMode = null;
            PropertyConfig propertyConfig = new PropertyConfig();

            FieldDeclaration propertyDef = variables.get(i).asFieldDeclaration();
            if (!propertyDef.isPublic() || !propertyDef.isStatic()) {
                continue;
            }
            VariableDeclarator valueDecl = propertyDef.getVariables().get(0);
            String propertyName = valueDecl.getName().asString();

            if (propertyName.equals("INVALID")) {
                continue;
            }

            int propertyId = valueDecl.getInitializer().get().asIntegerLiteralExpr().asInt();
            propertyConfig.propertyName = propertyName;
            propertyConfig.propertyId = propertyId;

            if (VHAL_PROP_ID_MAP.get(propertyName) != null) {
                propertyConfig.vhalPropertyId = VHAL_PROP_ID_MAP.get(propertyName);
            }

            Optional<Comment> maybeComment = propertyDef.getComment();
            if (!maybeComment.isPresent()) {
                System.out.println("missing comment for property: " + propertyName);
                System.exit(1);
            }

            Javadoc doc = maybeComment.get().asJavadocComment().parse();
            List<JavadocBlockTag> blockTags = doc.getBlockTags();
            boolean deprecated = false;
            boolean hide = false;
            for (int j = 0; j < blockTags.size(); j++) {
                String commentTagName = blockTags.get(j).getTagName();
                if (commentTagName.equals("deprecated")) {
                    deprecated = true;
                }
                if (commentTagName.equals("hide")) {
                    hide = true;
                }
            }
            String docText = doc.toText();
            propertyConfig.description = (docText.split("\n"))[0];
            propertyConfig.deprecated = deprecated;
            propertyConfig.hide = hide;

            if (docText.indexOf(ACCESS_MODE_READ_WRITE_LINK) != -1) {
                accessMode = ACCESS_MODE.READ_WRITE;
            } else if (docText.indexOf(ACCESS_MODE_READ_LINK) != -1) {
                accessMode = ACCESS_MODE.READ;
            } else if (docText.indexOf(ACCESS_MODE_WRITE_LINK) != -1) {
                accessMode = ACCESS_MODE.WRITE;
            } else {
                if (!deprecated) {
                    System.out.println("missing access mode for property: " + propertyName);
                    System.exit(1);
                }
            }

            List<AnnotationExpr> annotations = propertyDef.getAnnotations();
            for (int j = 0; j < annotations.size(); j++) {
                AnnotationExpr annotation = annotations.get(j);
                String annotationName = annotation.getName().asString();
                if (annotationName.equals("RequiresPermission")) {
                    parseAndSetPermAnnotation(annotation, propertyConfig, accessMode,
                            /* forRead= */ true, /* forWrite= */ true, carPermissionMap);
                }
                if (annotationName.equals("RequiresPermission.Read")) {
                    AnnotationExpr requireAnnotation = annotation.asSingleMemberAnnotationExpr()
                            .getMemberValue().asAnnotationExpr();
                    parseAndSetPermAnnotation(requireAnnotation, propertyConfig, accessMode,
                            /* forRead= */ true, /* forWrite= */ false, carPermissionMap);
                }
                if (annotationName.equals("RequiresPermission.Write")) {
                    AnnotationExpr requireAnnotation = annotation.asSingleMemberAnnotationExpr()
                            .getMemberValue().asAnnotationExpr();
                    parseAndSetPermAnnotation(requireAnnotation, propertyConfig, accessMode,
                            /* forRead= */ false, /* forWrite= */ true, carPermissionMap);
                }
                if (annotationName.equals("SystemApi")) {
                    propertyConfig.systemApi = true;
                }
            }
            if (propertyConfig.systemApi || !propertyConfig.hide) {
                // We do not generate config for hidden APIs since they are not exposed to public.
                propertyConfigs.add(propertyConfig);
            }
        }

        JSONObject root = new JSONObject();
        root.put("version", CONFIG_FILE_SCHEMA_VERSION);
        JSONObject jsonProps = new OrderedJSONObject();
        root.put("properties", jsonProps);
        for (int i = 0; i < propertyConfigs.size(); i++) {
            JSONObject jsonProp = new OrderedJSONObject();
            PropertyConfig config = propertyConfigs.get(i);
            jsonProp.put("propertyName", config.propertyName);
            jsonProp.put("propertyId", config.propertyId);
            jsonProp.put("description", config.description);
            if (config.readPermission != null) {
                jsonProp.put("readPermission", config.readPermission.toJson());
            }
            if (config.writePermission != null) {
                jsonProp.put("writePermission", config.writePermission.toJson());
            }
            if (config.deprecated) {
                jsonProp.put("deprecated", config.deprecated);
            }
            if (config.systemApi) {
                jsonProp.put("systemApi", config.systemApi);
            }
            if (config.vhalPropertyId != 0) {
                jsonProp.put("vhalPropertyId", config.vhalPropertyId);
            }
            jsonProps.put(config.propertyName, jsonProp);
        }

        try (FileOutputStream outputStream = new FileOutputStream(output)) {
            outputStream.write(root.toString(2).getBytes());
        }
        System.out.println("Input: " + vehiclePropertyIdsJava
                + " successfully parsed. Output at: " + output);
    }
}