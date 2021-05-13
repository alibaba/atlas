

package com.taobao.android.builder.tasks.app.prepare;

import com.squareup.javapoet.*;
import com.taobao.android.builder.tools.bundleinfo.model.BasicBundleInfo;
import org.apache.commons.lang3.StringUtils;

import javax.lang.model.element.Modifier;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BundleInfoSourceCreator {

//    public StringBuffer createBundleInfoSourceStr(List<BasicBundleInfo> basicBundleInfos, boolean appBundleEnabled, boolean flexaEnabled) {
//        StringBuffer buffer = new StringBuffer();
//        buffer.append("package com.android.tools.bundleInfo;\n" +
//                "\n" +
//                "import java.util.ArrayList;\n" +
//                "import java.util.HashMap;\n" +
//                "import java.util.LinkedHashMap;\n" +
//                "import java.util.List;\n" +
//                "\n" +
//                "\n" +
//                "public class BundleInfoGenerator {\n" +
//                "public static Boolean appBundleEnabled = " + appBundleEnabled + ";\n" +
//                "public static Boolean flexaEnabled = " + flexaEnabled + ";\n" +
//
//                "    public static BundleListing generateBundleInfo(){\n" +
//                "        LinkedHashMap<String,BundleListing.BundleInfo> bundleInfos = new LinkedHashMap<>();\n" +
//                "        HashMap<String,Boolean> activities;\n" +
//                "        HashMap<String,Boolean> services;\n" +
//                "        HashMap<String,Boolean> receivers;\n" +
//                "        HashMap<String,Boolean> providers;\n" +
//                "        BundleListing.BundleInfo info;\n" +
//                "\n" +
//                "        BundleListing listing = new BundleListing();\n" +
//                "        listing.bundles = bundleInfos;\n");
//
//        if (basicBundleInfos != null && basicBundleInfos.size() > 0) {
//            for (BasicBundleInfo info : basicBundleInfos) {
//                buffer.append(generateBundleInfoItem(info));
//            }
//        }
//
//        buffer.append("        return listing;\n" +
//                "    }\n" +
//                "}");
//        return buffer;
//    }
//
//    private StringBuffer generateBundleInfoItem(BasicBundleInfo info) {
//        StringBuffer infoBuffer = new StringBuffer();
//        infoBuffer.append("info = new BundleListing.BundleInfo();\n" +
//                "        activities = new HashMap<>();\n" +
//                "        services = new HashMap<>();\n" +
//                "        receivers = new HashMap<>();\n" +
//                "        providers = new HashMap<>();\n" +
//                "        info.activities = activities;\n" +
//                "        info.services = services;\n" +
//                "        info.receivers = receivers;\n" +
//                "        info.contentProviders = providers;\n");
//
//        infoBuffer.append(String.format("info.pkgName = \"%s\";\n", info.getPkgName()));
//        infoBuffer.append(String.format("info.dynamicFeature = %s;\n", info.getDynamicFeature()));
//        if (info.getFeatureName() != null) {
//            infoBuffer.append(String.format("info.featureName =  \"%s\";\n", info.getFeatureName()));
//        }
//
//        if (info.getApplicationName() != null) {
//            infoBuffer.append(String.format("info.applicationName = \"%s\";\n", info.getApplicationName()));
//        }
//
//        List<String> components = info.getActivities();
//        if (components != null && components.size() > 0) {
//            for (String activity : components) {
//                infoBuffer.append(String.format("activities.put(\"%s\",Boolean.FALSE);\n", activity));
//            }
//        }
//        components = info.getServices();
//        if (components != null && components.size() > 0) {
//            for (String service : components) {
//                infoBuffer.append(String.format("services.put(\"%s\",Boolean.FALSE);\n", service));
//            }
//        }
//        components = info.getReceivers();
//        if (components != null && components.size() > 0) {
//            for (String receiver : components) {
//                infoBuffer.append(String.format("receivers.put(\"%s\",Boolean.FALSE);\n", receiver));
//            }
//        }
//        components = info.getContentProviders();
//        if (components != null && components.size() > 0) {
//            for (String provider : components) {
//                infoBuffer.append(String.format("providers.put(\"%s\",Boolean.FALSE);\n", provider));
//            }
//        }
//
//
//        infoBuffer.append("bundleInfos.put(info.pkgName,info);\n");
//        return infoBuffer;
//
//    }


    public void createBundleInfo(File outDir, List<BasicBundleInfo> basicBundleInfos, boolean appBundleEnabled, boolean flexaEnabled) {
        ClassName bundleListing = ClassName.get("com.android.tools.bundleInfo", "BundleListing");
        ClassName bundleInfo = ClassName.get("com.android.tools.bundleInfo", "BundleListing.BundleInfo");
        ClassName linkedHashMap = ClassName.get("java.util", "LinkedHashMap");
        ClassName hashMap = ClassName.get("java.util", "HashMap");

        TypeName bundleInfos = ParameterizedTypeName.get(linkedHashMap, ClassName.get("java.lang", "String"), bundleInfo);
        TypeName activities = ParameterizedTypeName.get(hashMap, ClassName.get("java.lang", "String"), ClassName.get("java.lang", "Boolean"));
        TypeName services = ParameterizedTypeName.get(hashMap, ClassName.get("java.lang", "String"), ClassName.get("java.lang", "Boolean"));
        TypeName receivers = ParameterizedTypeName.get(hashMap, ClassName.get("java.lang", "String"), ClassName.get("java.lang", "Boolean"));
        TypeName providers = ParameterizedTypeName.get(hashMap, ClassName.get("java.lang", "String"), ClassName.get("java.lang", "Boolean"));

        MethodSpec.Builder builder = MethodSpec.methodBuilder("generateBundleInfo")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(bundleListing)
                .addStatement("$T bundleListing = new $T()", bundleListing, bundleListing)
                .addStatement("$T bundleInfos = new $T<>()", bundleInfos, linkedHashMap)
                .addStatement("bundleListing.bundles = bundleInfos")
                .addStatement("$T bundleInfo = new $T()", bundleInfo, bundleInfo)
                .addStatement("$T activities = new $T<>()", activities, hashMap)
                .addStatement("$T services = new $T<>()", services, hashMap)
                .addStatement("$T receivers = new $T<>()", receivers, hashMap)
                .addStatement("$T providers = new $T<>()", providers, hashMap);

        if (basicBundleInfos != null && basicBundleInfos.size() > 0) {
            for (BasicBundleInfo info : basicBundleInfos) {

                builder.addStatement("bundleInfo = new $T()", bundleInfo)
                        .addStatement("activities = new $T<>()", hashMap)
                        .addStatement("services = new $T<>()", hashMap)
                        .addStatement("receivers = new $T<>()", hashMap)
                        .addStatement("providers = new $T<>()", hashMap)
                        .addStatement("bundleInfo.activities = activities ")
                        .addStatement("bundleInfo.services = services ")
                        .addStatement("bundleInfo.receivers = receivers ")
                        .addStatement("bundleInfo.contentProviders = providers ")
                        .addStatement("bundleInfo.pkgName =$S", info.getPkgName())
                        .addStatement("bundleInfo.dynamicFeature=$L", info.getDynamicFeature());
                if (StringUtils.isNotEmpty(info.getFeatureName())) {
                    builder.addStatement("bundleInfo.featureName = $S", info.getFeatureName());
                }
                if (StringUtils.isNotEmpty(info.getApplicationName())) {
                    builder.addStatement("bundleInfo.applicationName = $S", info.getApplicationName());
                }
                List<String> components = info.getActivities();
                if (components != null && components.size() > 0) {
                    for (String activity : components) {
                        builder.addStatement("activities.put($S,$L)", activity, Boolean.FALSE);

                    }
                }
                components = info.getServices();
                if (components != null && components.size() > 0) {
                    for (String service : components) {
                        builder.addStatement("services.put($S,$L)", service, Boolean.FALSE);
                    }
                }
                components = info.getReceivers();
                if (components != null && components.size() > 0) {
                    for (String receiver : components) {
                        builder.addStatement("receivers.put($S,$L)", receiver, Boolean.FALSE);

                    }
                }
                components = info.getContentProviders();
                if (components != null && components.size() > 0) {
                    for (String provider : components) {
                        builder.addStatement("providers.put($S,$L)", provider, Boolean.FALSE);
                    }
                }

                builder.addStatement("bundleInfos.put($S,bundleInfo)", info.getPkgName());


            }
        }

        builder.addStatement("return bundleListing");

        MethodSpec methodSpec = builder.build();

        FieldSpec appBundleEnabledFieldSpec = FieldSpec.builder(Boolean.class, "appBundleEnabled")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .initializer("$L", appBundleEnabled)
                .build();

        FieldSpec flexaEnabledFieldSpec = FieldSpec.builder(Boolean.class, "flexaEnabled")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .initializer("$L", flexaEnabled)
                .build();
        TypeSpec bundleInfoGenerator = TypeSpec.classBuilder("BundleInfoGenerator")
                .addModifiers(Modifier.PUBLIC)
                .addField(appBundleEnabledFieldSpec)
                .addField(flexaEnabledFieldSpec)
                .addMethod(methodSpec)
                .build();
        JavaFile javaFile = JavaFile.builder("com.android.tools.bundleInfo", bundleInfoGenerator)
                .build();

        try {
            javaFile.writeTo(outDir.toPath());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


}

