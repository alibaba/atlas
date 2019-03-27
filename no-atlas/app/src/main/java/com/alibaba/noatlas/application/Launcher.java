package com.alibaba.noatlas.application;

import android.app.Application;
import com.android.alibaba.ip.server.InstantPatcher;

/**
 * 创建日期：2019/3/27 on 下午4:01
 * 描述:
 * 作者:zhayu.ll
 */
public class Launcher extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        InstantPatcher.create(this).applyPatch();
    }
}
