

package com.taobao.android.builder.tasks.app.prepare;

import com.taobao.android.builder.tools.bundleinfo.model.BasicBundleInfo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BundleInfoSourceCreator {

    public StringBuffer createBundleInfoSourceStr(List<BasicBundleInfo> basicBundleInfos, boolean appBundleEnabled, boolean pluginApkEnabled) {
        StringBuffer buffer = new StringBuffer();
        buffer.append("package com.android.tools.bundleInfo;\n" +
                "\n" +
                "import java.util.ArrayList;\n" +
                "import java.util.HashMap;\n" +
                "import java.util.LinkedHashMap;\n" +
                "import java.util.List;\n" +
                "\n" +
                "\n" +
                "public class BundleInfoGenerator {\n" +
                "public static Boolean appBundleEnabled = "+appBundleEnabled+";\n"+
                "public static Boolean pluginApkEnabled = "+pluginApkEnabled+";\n"+

                "    public static BundleListing generateBundleInfo(){\n" +
                "        LinkedHashMap<String,BundleListing.BundleInfo> bundleInfos = new LinkedHashMap<>();\n" +
                "        HashMap<String,Boolean> activities;\n" +
                "        HashMap<String,Boolean> services;\n" +
                "        HashMap<String,Boolean> receivers;\n" +
                "        HashMap<String,Boolean> providers;\n" +
                "        BundleListing.BundleInfo info;\n" +
                "\n" +
                "        BundleListing listing = new BundleListing();\n" +
                "        listing.bundles = bundleInfos;\n");

        if (basicBundleInfos != null && basicBundleInfos.size() > 0) {
            for (BasicBundleInfo info : basicBundleInfos) {
                buffer.append(generateBundleInfoItem(info));
            }
        }

        buffer.append("        return listing;\n" +
                "    }\n" +
                "}");
        return buffer;
    }

    private StringBuffer generateBundleInfoItem(BasicBundleInfo info) {
        StringBuffer infoBuffer = new StringBuffer();
        infoBuffer.append("info = new BundleListing.BundleInfo();\n" +
                "        activities = new HashMap<>();\n" +
                "        services = new HashMap<>();\n" +
                "        receivers = new HashMap<>();\n" +
                "        providers = new HashMap<>();\n" +
                "        info.activities = activities;\n" +
                "        info.services = services;\n" +
                "        info.receivers = receivers;\n" +
                "        info.contentProviders = providers;\n");

        infoBuffer.append(String.format("info.pkgName = \"%s\";\n", info.getPkgName()));
        infoBuffer.append(String.format("info.dynamicFeature = %s;\n", info.getDynamicFeature()));
        if (info.getFeatureName() != null) {
            infoBuffer.append(String.format("info.featureName =  \"%s\";\n", info.getFeatureName()));
        }

        if (info.getApplicationName() != null) {
            infoBuffer.append(String.format("info.applicationName = \"%s\";\n", info.getApplicationName()));
        }

        List<String> components = info.getActivities();
        if (components != null && components.size() > 0) {
            for (String activity : components) {
                infoBuffer.append(String.format("activities.put(\"%s\",Boolean.FALSE);\n", activity));
            }
        }
        components = info.getServices();
        if (components != null && components.size() > 0) {
            for (String service : components) {
                infoBuffer.append(String.format("services.put(\"%s\",Boolean.FALSE);\n", service));
            }
        }
        components = info.getReceivers();
        if (components != null && components.size() > 0) {
            for (String receiver : components) {
                infoBuffer.append(String.format("receivers.put(\"%s\",Boolean.FALSE);\n", receiver));
            }
        }
        components = info.getContentProviders();
        if (components != null && components.size() > 0) {
            for (String provider : components) {
                infoBuffer.append(String.format("providers.put(\"%s\",Boolean.FALSE);\n", provider));
            }
        }


        infoBuffer.append("bundleInfos.put(info.pkgName,info);\n");
        return infoBuffer;

    }
}

