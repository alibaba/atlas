package android.taobao.atlas.util;

import android.taobao.atlas.runtime.ActivityTaskMgr;
import android.taobao.atlas.runtime.RuntimeVariables;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Created by guanjie on 2017/4/18.
 */

public class AtlasBackGroundCrashManager{

    private static Thread.UncaughtExceptionHandler defaultUnCaughtExceptionHandler;

    public static void forceStopAppWhenCrashed(){
        defaultUnCaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new CrashHandler());
    }

    public static class CrashHandler implements Thread.UncaughtExceptionHandler{
        @Override
        public void uncaughtException(Thread thread, Throwable ex) {
            if(ActivityTaskMgr.getInstance().isActivityStackEmpty()){
                //force stop pkg
                try {
                    Field mPMField = RuntimeVariables.androidApplication.getPackageManager().getClass().getDeclaredField("mPM");
                    mPMField.setAccessible(true);
                    Object mPM = mPMField.get(RuntimeVariables.androidApplication.getPackageManager());
                    Method setPackageStoppedState = mPM.getClass().getDeclaredMethod("setPackageStoppedState",String.class,boolean.class,int.class);
                    setPackageStoppedState.setAccessible(true);
                    setPackageStoppedState.invoke(RuntimeVariables.androidApplication.getPackageName(),true,RuntimeVariables.androidApplication.getApplicationInfo().uid);
                } catch (Throwable e) {
                    e.printStackTrace();
                }

            }
            if(defaultUnCaughtExceptionHandler!=null) {
                defaultUnCaughtExceptionHandler.uncaughtException(thread, ex);
            }
        }
    }
}
