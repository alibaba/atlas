package com.taobao.android.inputs;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * @author lilong
 * @create 2017-11-02 下午11:58
 */

public class ApatchInput extends BaseInput {

    public  String replaceAnnotation = "Lcom/alipay/euler/andfix/annotation/MethodReplace;";

    public boolean onlyIncludeModifyBundle = true;
    public String filterPath;                                 // 类白名单路径

    public  File mappingFile;

    public String andfixMainBundleName = "com_taobao_maindex";

    public String projectArtifactId;

    public Map<String,String> mappingMap = new HashMap<String, String>();



}
