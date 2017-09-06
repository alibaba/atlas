package com.taobao.atlas.complex.publicbundle;

import android.app.Application;
import android.util.Log;

import com.taobao.atlas.complex.base.middleware.Env;

/**
 * Created by zhongcang on 2017/9/6.
 * .
 */

public class PublicBundleApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        if (Env.DEBUG) Log.d(Env.TAG, "PublicBundleApplication onCreate: ");
        LibUtils.init(getBaseContext());
    }
}