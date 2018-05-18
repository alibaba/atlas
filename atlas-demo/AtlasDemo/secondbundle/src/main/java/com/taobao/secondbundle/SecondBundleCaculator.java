package com.taobao.secondbundle;

import android.content.ComponentCallbacks2;
import android.content.ContentProvider;
import android.content.res.Configuration;
import android.os.Bundle;
import android.taobao.atlas.remote.HostTransactor;
import android.taobao.atlas.remote.IRemote;
import android.taobao.atlas.runtime.ActivityLifeCycleObserver;
import android.taobao.atlas.runtime.RuntimeVariables;

import com.taobao.middleware.ICaculator;

/**
 * Created by guanjie on 2017/12/8.
 */

public class SecondBundleCaculator implements IRemote{

    public SecondBundleCaculator(){
        RuntimeVariables.androidApplication.registerComponentCallbacks(new ComponentCallbacks2() {
            @Override
            public void onTrimMemory(int level) {
                if(level  == ContentProvider.TRIM_MEMORY_UI_HIDDEN){
                    HostTransactor remote = HostTransactor.get(SecondBundleCaculator.this);
                    if(remote!=null) {
                        remote.call("SLEEP_NOTIFY", null, null);
                    }
                }
            }

            @Override
            public void onConfigurationChanged(Configuration newConfig) {

            }

            @Override
            public void onLowMemory() {

            }
        });
    }

    @Override
    public Bundle call(String s, Bundle bundle, IResponse iResponse) {
        if(s.equalsIgnoreCase("sum")){
            int a = bundle.getInt("num1");
            int b = bundle.getInt("num2");

            bundle.putInt("result",a+b);
            return bundle;

        }
        return null;
    }

    @Override
    public <T> T getRemoteInterface(Class<T> aClass, Bundle bundle) {
        if(aClass == ICaculator.class){
            T instance = (T)new ICaculator(){
                @Override
                public int sum(int a, int b) {
                    return a+b;
                }
            };
            return instance;
        }
        return null;
    }
}
