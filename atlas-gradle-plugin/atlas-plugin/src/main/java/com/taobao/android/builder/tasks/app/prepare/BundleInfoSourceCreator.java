package com.taobao.android.builder.tasks.app.prepare;

import com.taobao.android.builder.tools.bundleinfo.model.BasicBundleInfo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by guanjie on 2017/9/23.
 */
public class BundleInfoSourceCreator {
    private boolean supportRemote = true;
    public StringBuffer createBundleInfoSourceStr(List<BasicBundleInfo> basicBundleInfos,boolean supportRemotecomponent){
        supportRemote = supportRemotecomponent;
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
                "        HashMap<String,String> remoteFragments;\n" +
                "        HashMap<String,String> remoteViews;\n" +
                "        HashMap<String,String> remoteTransactors;\n" +
                "        List<String> dependencies;\n" +
                "        BundleListing.BundleInfo info;\n" +
                "\n" +
                "        BundleListing listing = new BundleListing();\n" +
                "        listing.bundles = bundleInfos;\n");

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
                "        remoteFragments = new HashMap<>();\n" +
                "        remoteViews = new HashMap<>();\n" +
                "        remoteTransactors = new HashMap<>();\n" +
                "        dependencies = new ArrayList<>();\n" +
                "        info.activities = activities;\n" +
                "        info.services = services;\n" +
                "        info.receivers = receivers;\n" +
                "        info.contentProviders = providers;\n" +
                ( supportRemote ?
                "        info.remoteFragments = remoteFragments;\n" +
                "        info.remoteViews = remoteViews;\n" +
                "        info.remoteTransactors = remoteTransactors;\n" : "")+
                "        info.dependency = dependencies;\n");

        infoBuffer.append(String.format("info.unique_tag = \"%s\";\n",info.getUnique_tag()));
        infoBuffer.append(String.format("info.pkgName = \"%s\";\n",info.getPkgName()));
        infoBuffer.append(String.format("info.isInternal = %s;\n",info.getIsInternal()));
        infoBuffer.append(String.format("info.isMBundle = %s;\n",info.getIsMBundle()));
        if(info.getApplicationName()!=null) {
            infoBuffer.append(String.format("info.applicationName = \"%s\";\n", info.getApplicationName()));
        }

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

        HashMap<String,String> remoteFragments= info.getRemoteFragments();
        if(remoteFragments!=null){
            for (Map.Entry<String, String> entry : remoteFragments.entrySet()) {
                infoBuffer.append(String.format("remoteFragments.put(\"%s\",\"%s\");\n",entry.getKey(),entry.getValue()));
            }
        }

        HashMap<String,String> remoteViews= info.getRemoteViews();
        if(remoteViews!=null){
            for (Map.Entry<String, String> entry : remoteViews.entrySet()) {
                infoBuffer.append(String.format("remoteViews.put(\"%s\",\"%s\");\n",entry.getKey(),entry.getValue()));
            }
        }

        HashMap<String,String> remoteTransactors= info.getRemoteTransactors();
        if(remoteTransactors!=null){
            for (Map.Entry<String, String> entry : remoteTransactors.entrySet()) {
                infoBuffer.append(String.format("remoteTransactors.put(\"%s\",\"%s\");\n",entry.getKey(),entry.getValue()));
            }
        }

        List<String> dependencies = info.getDependency();
        if(dependencies!=null && dependencies.size()>0){
            for(String dependency : dependencies){
                if(dependency!=null) {
                    infoBuffer.append(String.format("dependencies.add(\"%s\");\n", dependency));
                }
            }
        }
        infoBuffer.append("bundleInfos.put(info.pkgName,info);\n");
        return infoBuffer;

    }
}
