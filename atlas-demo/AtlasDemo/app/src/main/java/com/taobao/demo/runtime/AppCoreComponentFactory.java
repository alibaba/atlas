package com.taobao.demo.runtime;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentProvider;
import android.content.Intent;
import androidx.core.app.CoreComponentFactory;

/**
 * @ClassName AppCoreComponentFactory
 * @Description TODO
 * @Author zhayu.ll
 * @Date 2019-09-18 10:21
 * @Version 1.0
 */

public class AppCoreComponentFactory extends CoreComponentFactory {
    @SuppressLint("RestrictedApi")
    @Override
    public Activity instantiateActivity(ClassLoader cl, String className, Intent intent) throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        return super.instantiateActivity(cl, className, intent);
    }

    @SuppressLint("RestrictedApi")
    @Override
    public Application instantiateApplication(ClassLoader cl, String className) throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        return super.instantiateApplication(cl, className);
    }

    @SuppressLint("RestrictedApi")
    @Override
    public BroadcastReceiver instantiateReceiver(ClassLoader cl, String className, Intent intent) throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        return super.instantiateReceiver(cl, className, intent);
    }

    @SuppressLint("RestrictedApi")
    @Override
    public ContentProvider instantiateProvider(ClassLoader cl, String className) throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        return super.instantiateProvider(cl, className);
    }

    @Override
    public Service instantiateService(ClassLoader cl, String className, Intent intent) throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        return super.instantiateService(cl, className, intent);
    }
}
