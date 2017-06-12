package com.taobao.android.builder.tools.classinject;

import com.taobao.android.builder.tools.bundleinfo.model.BasicBundleInfo;

import java.util.List;

/**
 * Created by chenhjohn on 2017/4/28.
 */

public class FrameworkProperties {
    public List<BasicBundleInfo> bundleInfo;

    public boolean outApp = false;

    public String autoStartBundles;

    public String preLaunch;
}
