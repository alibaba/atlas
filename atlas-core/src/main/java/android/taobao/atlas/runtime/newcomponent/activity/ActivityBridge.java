package android.taobao.atlas.runtime.newcomponent.activity;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import android.taobao.atlas.runtime.ActivityTaskMgr;
import android.taobao.atlas.runtime.newcomponent.AdditionalPackageManager;
import android.taobao.atlas.runtime.newcomponent.BridgeUtil;
import java.lang.reflect.Field;
import java.util.List;

/**
 * Created by guanjie on 2017/4/12.
 */

public class ActivityBridge {

    public static void wrapperActivityIntentIfNeed(Intent intent) {
        List<ResolveInfo> infos = AdditionalPackageManager.getInstance().queryIntentActivities(intent);
        if (infos != null && infos.get(0).activityInfo != null) {
            Intent wrappIntent = wrapperOriginalIntent(intent,BridgeUtil.getBridgeComponent(BridgeUtil.TYPE_ACTIVITYBRIDGE,infos.get(0).activityInfo.processName));
            ActivityTaskMgr.getInstance().handleActivityStack(infos.get(0).activityInfo.name, wrappIntent, intent.getFlags(), infos.get(0).activityInfo.launchMode);
        }
    }

    public static void processActivityIntentIfNeed(Object activityclientrecord) {
        try {
            Class ActivityClientRecord_Class = Class.forName("android.app.ActivityThread$ActivityClientRecord");
            Field intent_Field = ActivityClientRecord_Class.getDeclaredField("intent");
            intent_Field.setAccessible(true);
            Intent intent = (Intent) intent_Field.get(activityclientrecord);
            if (intent.getComponent() != null && intent.getComponent().getClassName().startsWith(BridgeUtil.PROXY_PREFIX)){
                Field activityInfo_Field = ActivityClientRecord_Class.getDeclaredField("activityInfo");
                activityInfo_Field.setAccessible(true);
                ActivityInfo info = AdditionalPackageManager.getInstance().getNewComponentInfo(intent.getComponent(),ActivityInfo.class);
                activityInfo_Field.set(activityclientrecord, info);
                intent_Field.set(activityclientrecord,unWrapperOriginalIntent(intent));
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }

    }

    private static Intent wrapperOriginalIntent(Intent origin,String delegatActivityname){
        Intent wrappIntent = new Intent();
        wrappIntent.addFlags(origin.getFlags());
        wrappIntent.setClassName(wrappIntent.getComponent().getPackageName(),delegatActivityname);
        wrappIntent.putExtra("originalIntent",origin);
        return wrappIntent;
    }

    private static Intent unWrapperOriginalIntent(Intent wrapper){
        return wrapper.getParcelableExtra("originalIntent");
    }

}
