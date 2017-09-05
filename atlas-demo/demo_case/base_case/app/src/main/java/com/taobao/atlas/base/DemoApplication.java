package com.taobao.atlas.base;

import android.app.Application;
import android.content.Context;
import android.util.Log;

/**
 * Created by zhongcang on 2017/9/5.
 * .
 */

public class DemoApplication extends Application {
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        Log.d(Env.TAG, "DemoApplication attachBaseContext");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(Env.TAG, "DemoApplication  onCreate");
    }
}
