package com.taobao.android.reader;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.LinkedHashMap;

/**
 * @author lilong
 * @create 2017-08-15 下午1:43
 */

public class BundleListingUtil {

    public static LinkedHashMap<String,BundleListing.BundleInfo> parseArray(String listingStr,LinkedHashMap<String,BundleListing.BundleInfo>currentBundleInfo) throws Exception{
        LinkedHashMap<String,BundleListing.BundleInfo> infos= new LinkedHashMap<>();
        JSONArray array = JSON.parseArray(listingStr);
        for(int x=0; x<array.size(); x++){
            JSONObject object = array.getJSONObject(x);
            BundleListing.BundleInfo info = new BundleListing.BundleInfo();
            info.setName(object.getString("name"));
            info.setPkgName(object.getString("pkgName"));
            info.setApplicationName(object.getString("applicationName"));
            info.setVersion(object.getString("version"));
            info.setDesc(object.getString("desc"));
            info.setUrl(object.getString("url"));
            info.setMd5(object.getString("md5"));
            String uniqueTag = object.getString("unique_tag");
            if(StringUtils.isEmpty(uniqueTag)){
                throw new IOException("uniqueTag is empty");
            }
            info.setUnique_tag(object.getString("unique_tag"));
            if (currentBundleInfo==null) {
                info.setCurrent_unique_tag(info.getUnique_tag());
            }else {
                if (currentBundleInfo.get(info.getPkgName())!= null){
                    info.setCurrent_unique_tag(currentBundleInfo.get(info.getPkgName()).getUnique_tag());
                }
            }

            infos.put(info.getPkgName(),info);

        }
        return infos.size()>0 ? infos : null;
    }
}
