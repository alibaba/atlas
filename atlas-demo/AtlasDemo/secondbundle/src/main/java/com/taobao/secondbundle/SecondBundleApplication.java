package com.taobao.secondbundle;

import android.app.Application;

/**
 * 创建日期：2019/4/8 on 下午2:21
 * 描述:
 * 作者:zhayu.ll
 */
public class SecondBundleApplication extends Application {

    public static SecondBundleIniter secondBundleIniter;
    // here is the second bundle init method
    @Override
    public void onCreate() {
        super.onCreate();
        secondBundleIniter = new SecondBundleIniter();
        secondBundleIniter.init(this);

    }
}
