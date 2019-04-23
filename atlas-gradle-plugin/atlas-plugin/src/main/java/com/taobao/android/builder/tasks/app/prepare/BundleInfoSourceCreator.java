package com.taobao.android.builder.tasks.app.prepare;

import com.taobao.android.builder.tools.bundleinfo.model.BasicBundleInfo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by guanjie on 2017/9/23.
 */
public class BundleInfoSourceCreator {
    public StringBuffer createBundleInfoSourceStr(List<BasicBundleInfo> basicBundleInfos){
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
                "    public static BundleListing generateBundleInfo(){\n" +
                "        LinkedHashMap<String,BundleListing.BundleInfo> bundleInfos = new LinkedHashMap<>();\n" +
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
        infoBuffer.append("info = new BundleListing.BundleInfo();\n");

        infoBuffer.append(String.format("info.pkgName = \"%s\";\n",info.getPkgName()));
        if(info.getApplicationName()!=null) {
            infoBuffer.append(String.format("info.applicationName = \"%s\";\n", info.getApplicationName()));
        }


        infoBuffer.append("bundleInfos.put(info.pkgName,info);\n");
        return infoBuffer;

    }
}
