package com.taobao.android.builder.tools.classinject;

import com.taobao.android.builder.tools.bundleinfo.model.BasicBundleInfo;

import java.util.List;
import java.util.Set;

/**
 * Created by chenhjohn on 2017/4/28.
 */

public class FrameworkProperties {
    public List<BasicBundleInfo> bundleInfo;

    public String unit_tag;

    public boolean outApp = false;

    public String autoStartBundles;

    public String preLaunch;

    public String blackDialogActivity;
}
