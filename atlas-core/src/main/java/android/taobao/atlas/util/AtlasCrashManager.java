package android.taobao.atlas.util;

import android.taobao.atlas.runtime.RuntimeVariables;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Created by guanjie on 2017/4/18.
 */

public class AtlasCrashManager {

    private static Thread.UncaughtExceptionHandler defaultUnCaughtExceptionHandler;

    public static void forceStopAppWhenCrashed(){
        defaultUnCaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new CrashHandler());
    }

    public static class CrashHandler implements Thread.UncaughtExceptionHandler{
        @Override
        public void uncaughtException(Thread thread, Throwable ex) {
            //force stop pkg
            try {
                Log.e("CrashManager","force stop");
                Field mPMField = RuntimeVariables.androidApplication.getPackageManager().getClass().getDeclaredField("mPM");
                mPMField.setAccessible(true);
                Object mPM = mPMField.get(RuntimeVariables.androidApplication.getPackageManager());
                Method setPackageStoppedState = mPM.getClass().getDeclaredMethod("setPackageStoppedState",String.class,boolean.class,int.class);
                setPackageStoppedState.setAccessible(true);
                setPackageStoppedState.invoke(mPM,RuntimeVariables.androidApplication.getPackageName(),true,RuntimeVariables.androidApplication.getApplicationInfo().uid);
            } catch (Throwable e) {
                e.printStackTrace();
            }

            if(defaultUnCaughtExceptionHandler!=null) {
                defaultUnCaughtExceptionHandler.uncaughtException(thread, ex);
            }
        }
    }
}
