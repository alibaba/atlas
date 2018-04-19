package android.taobao.atlas.runtime.newcomponent.activity;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import android.taobao.atlas.runtime.ActivityTaskMgr;
import android.taobao.atlas.runtime.InstrumentationHook;
import android.taobao.atlas.runtime.RuntimeVariables;
import android.taobao.atlas.runtime.newcomponent.AdditionalActivityManagerProxy;
import android.taobao.atlas.runtime.newcomponent.AdditionalPackageManager;
import android.taobao.atlas.runtime.newcomponent.BridgeUtil;
import android.taobao.atlas.util.StringUtils;

import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by guanjie on 2017/4/12.
 */

public class ActivityBridge {

    public static Instrumentation.ActivityResult execStartActivity(Intent intent, final InstrumentationHook.ExecStartActivityCallback startActivityRunnable) {
        List<ResolveInfo> infos = AdditionalPackageManager.getInstance().queryIntentActivities(intent);
        if (infos != null && infos.get(0).activityInfo != null) {
            String delegateComponentName = BridgeUtil.getBridgeName(BridgeUtil.TYPE_ACTIVITYBRIDGE,infos.get(0).activityInfo.processName);
            Intent wrappIntent = wrapperOriginalIntent(intent,delegateComponentName);
            if(infos.get(0).activityInfo.processName.equals(RuntimeVariables.getProcessName(RuntimeVariables.androidApplication))) {
                handleActivityStack(infos.get(0).activityInfo,wrappIntent);
                return startActivityRunnable.execStartActivity(wrappIntent);
            }else{
                AdditionalActivityManagerProxy.handleActivityStack(wrappIntent,infos.get(0).activityInfo,new OnIntentPreparedObserver(){
                    @Override
                    public void onPrepared(final Intent intent) {
                        startActivityRunnable.execStartActivity(intent);
                    }
                });
                return null;
            }
        }else{
            return startActivityRunnable.execStartActivity(intent);
        }
    }

    public static void processActivityIntentIfNeed(Object activityclientrecord) {
        try {
            Class ActivityClientRecord_Class = Class.forName("android.app.ActivityThread$ActivityClientRecord");
            Field intent_Field = ActivityClientRecord_Class.getDeclaredField("intent");
            intent_Field.setAccessible(true);
            Intent intent = (Intent) intent_Field.get(activityclientrecord);
            if (intent.getComponent() != null && intent.getComponent().getClassName().startsWith(String.format("%s%s",BridgeUtil.COMPONENT_PACKAGE,BridgeUtil.PROXY_PREFIX))){
                Field activityInfo_Field = ActivityClientRecord_Class.getDeclaredField("activityInfo");
                activityInfo_Field.setAccessible(true);
                Intent originalIntent = unWrapperOriginalIntent(intent);
                ActivityInfo info = AdditionalPackageManager.getInstance().getNewComponentInfo(originalIntent.getComponent(),ActivityInfo.class);
                activityInfo_Field.set(activityclientrecord, info);
                intent_Field.set(activityclientrecord,originalIntent);
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }

    }

    public static void handleActivityStack(ActivityInfo info, Intent intent) {
        // Handle launchMode with singleTop, and flag with FLAG_ACTIVITY_SINGLE_TOP
        int launchMode = info.launchMode;
        int flag = intent.getFlags();
        String launchActivityName = info.name;

        String prevActivityName = null;
        List<WeakReference<Activity>> activityList = ActivityTaskMgr.getInstance().getActivityList();

        if (activityList.size() > 0) {
            Activity lastActivity = activityList.get(activityList.size() - 1).get();
            prevActivityName = lastActivity.getClass().getName();
        }

        if (StringUtils.equals(prevActivityName, launchActivityName)
                && (launchMode == ActivityInfo.LAUNCH_SINGLE_TOP || (flag & Intent.FLAG_ACTIVITY_SINGLE_TOP) == Intent.FLAG_ACTIVITY_SINGLE_TOP)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        } else if (launchMode == ActivityInfo.LAUNCH_SINGLE_TASK || launchMode == ActivityInfo.LAUNCH_SINGLE_INSTANCE
                || (flag & Intent.FLAG_ACTIVITY_CLEAR_TOP) == Intent.FLAG_ACTIVITY_CLEAR_TOP) {
            int i;
            boolean isExist = false;
            for (i = 0; i < activityList.size(); i++) {
                WeakReference<Activity> ref = activityList.get(i);
                if (ref!=null && ref.get()!=null && ref.get().getClass().getName().equals(launchActivityName)) {
                    isExist = true;
                    break;
                }
            }
            if (isExist) {
                for (WeakReference<Activity> act : activityList.subList(i + 1, activityList.size())) {
                    if(act!=null && act.get()!=null) {
                        act.get().finish();
                    }
                }
                activityList.subList(i + 1, activityList.size()).clear();
                intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            }
        }
    }

    private static Intent wrapperOriginalIntent(Intent origin,String delegatActivityname){
        Intent wrappIntent = new Intent();
        wrappIntent.addFlags(origin.getFlags());
        wrappIntent.setClassName(origin.getComponent().getPackageName(),delegatActivityname);
        wrappIntent.putExtra("originalIntent",origin);
        return wrappIntent;
    }

    private static Intent unWrapperOriginalIntent(Intent wrapper){
        return wrapper.getParcelableExtra("originalIntent");
    }

    public static void handleNewIntent(Object newIntentData) {

        try {
            Class newIntentData_clazz = Class.forName("android.app.ActivityThread$NewIntentData");
            Class refrenceIntent_clazz = Class.forName("com.android.internal.content.ReferrerIntent");
            Field intent_Field = newIntentData_clazz.getDeclaredField("intents");
            intent_Field.setAccessible(true);
            List<Intent>oldIntents = (List<Intent>) intent_Field.get(newIntentData);
            List<Intent>newIntents = new ArrayList<>();
            if (oldIntents!= null && oldIntents.size() > 0){
                for (Intent intent:oldIntents) {
                    if (intent.getComponent() != null && intent.getComponent().getClassName().startsWith(String.format("%s%s", BridgeUtil.COMPONENT_PACKAGE, BridgeUtil.PROXY_PREFIX))) {
                        Intent originalIntent = unWrapperOriginalIntent(intent);
                        Constructor constructor = refrenceIntent_clazz.getDeclaredConstructor(new Class[]{Intent.class,String.class});
                        constructor.setAccessible(true);
                        Intent o = (Intent) constructor.newInstance(originalIntent,RuntimeVariables.androidApplication.getPackageName());
                        newIntents.add(o);
                        intent_Field.set(newIntentData,newIntents);
                    }
                }
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }


    }


    public interface OnIntentPreparedObserver{
        public void onPrepared(Intent intent);
    }

}
