package com.taobao.atlas.base;

import android.content.Context;
import android.taobao.atlas.runtime.AtlasPreLauncher;
import android.util.Log;

/**
 * Created by zhongcang on 2017/9/5.
 * .
 */

public class PreLaunch implements AtlasPreLauncher {
    @Override
    public void initBeforeAtlas(Context context) {
        Log.d(Env.TAG, "initBeforeAtlas ");
    }
}