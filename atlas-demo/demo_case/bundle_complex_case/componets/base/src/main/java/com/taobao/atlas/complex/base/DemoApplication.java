package com.taobao.atlas.complex.base;

import android.app.Application;
import android.content.Context;
import android.util.Log;

import com.taobao.atlas.complex.base.middleware.Env;


/**
 * Created by zhongcnag on 2017/9/5.
 * .
 */

public class DemoApplication extends Application {
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        if (Env.DEBUG) Log.d(Env.TAG, "attachBaseContext: ");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (Env.DEBUG) Log.d(Env.TAG, "onCreate: ");
    }
}
