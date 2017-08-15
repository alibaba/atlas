package com.taobao.android.reader;

import org.jf.dexlib2.iface.Field;
import org.jf.dexlib2.iface.value.StringEncodedValue;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author lilong
 * @create 2017-08-15 下午1:26
 */

public class AtlasFrameworkPropertiesReader implements Reader {
    private Reader reader;
    private Map map;

    public AtlasFrameworkPropertiesReader(Reader reader,LinkedHashMap map) {
        if (reader instanceof FieldReader){
            this.reader = reader;
            this.map = map;
        }
    }
    public LinkedHashMap<String,BundleListing.BundleInfo>read(String className,String memberName) throws Exception {
        if (reader!= null) {
            Field field = (Field) reader.read(className, memberName);
            StringEncodedValue stringEncodedValue = (StringEncodedValue) field.getInitialValue();
            String bundleInfos = stringEncodedValue.getValue();
            return BundleListingUtil.parseArray(bundleInfos, (LinkedHashMap<String, BundleListing.BundleInfo>) map);
        }
        return null;
    }
}
