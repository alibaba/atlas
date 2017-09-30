package com.taobao.android.builder.tasks.app.prepare;

import com.taobao.android.builder.tools.bundleinfo.model.BasicBundleInfo;
import java.util.List;

/**
 * Created by guanjie on 2017/9/23.
 */
public class BundleInfoSourceCreator {
    public StringBuffer createBundleInfoSourceStr(List<BasicBundleInfo> basicBundleInfos){
        StringBuffer buffer = new StringBuffer();
        buffer.append("package android.taobao.atlas.bundleInfo;\n" +
                "\n" +
                "import java.util.ArrayList;\n" +
                "import java.util.HashMap;\n" +
                "import java.util.LinkedHashMap;\n" +
                "import java.util.List;\n" +
                "\n" +
                "\n" +
                "public class AtlasBundleInfoGenerator {\n" +
                "    public static BundleListing generateBundleInfo(){\n" +
                "        LinkedHashMap<String,BundleListing.BundleInfo> bundleInfos = new LinkedHashMap<>();\n" +
                "        HashMap<String,Boolean> activities;\n" +
                "        HashMap<String,Boolean> services;\n" +
                "        HashMap<String,Boolean> receivers;\n" +
                "        HashMap<String,Boolean> providers;\n" +
                "        List<String> dependencies;\n" +
                "        BundleListing.BundleInfo info;\n" +
                "\n" +
                "        BundleListing listing = new BundleListing();\n" +
                "        listing.setBundles(bundleInfos);\n");

        if(basicBundleInfos!=null && basicBundleInfos.size()>0){
            for(BasicBundleInfo info : basicBundleInfos){
                buffer.append(generateBundleInfoItem(info));
            }
        }

        buffer.append("        return listing;\n" +
                "    }\n" +
                "}");
        return buffer;
    }

    private StringBuffer generateBundleInfoItem(BasicBundleInfo info){
        StringBuffer infoBuffer = new StringBuffer();
        infoBuffer.append("info = new BundleListing.BundleInfo();\n" +
                "        activities = new HashMap<>();\n" +
                "        services = new HashMap<>();\n" +
                "        receivers = new HashMap<>();\n" +
                "        providers = new HashMap<>();\n" +
                "        dependencies = new ArrayList<>();\n" +
                "        info.setActivities(activities);\n" +
                "        info.setServices(services);\n" +
                "        info.setReceivers(receivers);\n" +
                "        info.setContentProviders(providers);\n" +
                "        info.setDependency(dependencies);\n");

        infoBuffer.append(String.format("info.setUnique_tag(\"%s\");\n",info.getUnique_tag()));
        infoBuffer.append(String.format("info.setPkgName(\"%s\");\n",info.getPkgName()));
        infoBuffer.append(String.format("info.setIsInternal(%s);\n",info.getIsInternal()));
        infoBuffer.append(String.format("info.setApplicationName(\"%s\");\n",info.getApplicationName()));

        List<String> components = info.getActivities();
        if(components!=null && components.size()>0){
            for(String activity : components){
                infoBuffer.append(String.format("activities.put(\"%s\",Boolean.FALSE);\n",activity));
            }
        }
        components = info.getServices();
        if(components!=null && components.size()>0){
            for(String service : components){
                infoBuffer.append(String.format("services.put(\"%s\",Boolean.FALSE);\n",service));
            }
        }
        components = info.getReceivers();
        if(components!=null && components.size()>0){
            for(String receiver : components){
                infoBuffer.append(String.format("receivers.put(\"%s\",Boolean.FALSE);\n",receiver));
            }
        }
        components = info.getContentProviders();
        if(components!=null && components.size()>0){
            for(String provider : components){
                infoBuffer.append(String.format("providers.put(\"%s\",Boolean.FALSE);\n",provider));
            }
        }

        List<String> dependencies = info.getDependency();
        if(dependencies!=null && dependencies.size()>0){
            for(String dependency : dependencies){
                infoBuffer.append(String.format("dependencies.add(\"%s\");\n",dependency));
            }
        }
        infoBuffer.append("bundleInfos.put(info.getPkgName(),info);\n");
        return infoBuffer;

    }
}
