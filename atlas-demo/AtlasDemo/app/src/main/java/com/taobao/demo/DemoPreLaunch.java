package com.taobao.demo;

import android.content.Context;
import android.taobao.atlas.runtime.AtlasPreLauncher;
import android.util.Log;

/**
 * Created by wuzhong on 2017/3/28.
 */

public class DemoPreLaunch implements AtlasPreLauncher {
    @Override
    public void initBeforeAtlas(Context context) {
        Log.d("prelaunch", "prelaunch invokded");
    }
}
