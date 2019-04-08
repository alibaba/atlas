package com.taobao.secondbundle;

import android.app.Application;

/**
 * 创建日期：2019/4/8 on 下午2:25
 * 描述:
 * 作者:zhayu.ll
 */
public class SecondBundleIniter {


    private Application application;


    public void init(Application application){

        this.application = application;
    }


    public Application getApplication(){

        return application;
    }
}
