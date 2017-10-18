package android.taobao.atlas.remote.fragment;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.taobao.atlas.framework.Atlas;
import android.taobao.atlas.hack.AndroidHack;
import android.taobao.atlas.hack.AtlasHacks;
import android.taobao.atlas.hack.Hack;
import java.lang.ref.WeakReference;
import java.util.HashMap;

/**
 * Created by guanjie on 2017/10/13.
 */

public class RemoteContextManager {

    private static Hack.HackedMethod ActivityThread_startActivityNow;
    private static Hack.HackedClass  NonConfigurationInstances;

    static{
        try {
            NonConfigurationInstances = Hack.into("android.app.Activity$NonConfigurationInstances");
            ActivityThread_startActivityNow = AtlasHacks.ActivityThread.method("startActivityNow", Activity.class, String.class,
                    Intent.class, ActivityInfo.class, IBinder.class, Bundle.class, NonConfigurationInstances.getmClass());
        }catch(Throwable e){
            throw new RuntimeException(e);
        }
    }

    public static RemoteContextManager obtain(Activity parent){
        return new RemoteContextManager(parent);
    }

    private RemoteContextManager(Activity parent){
        mParent = parent;
    }

    private HashMap<String,EmbeddedActivityRecord> mActivityRecords = new HashMap<>();
    private Activity mParent;

    public synchronized void prepareRemoteFragment(RemoteFragment fragment,String bundleName) throws Exception{
        if(!mActivityRecords.containsKey(bundleName)){
            EmbeddedActivityRecord record = startEmbeddedActivity();
            mActivityRecords.put(bundleName,record);
        }
        EmbeddedActivityRecord ad = mActivityRecords.get(bundleName);
        fragment.remoteContext = new RemoteContext(ad.activity,Atlas.getInstance().getBundleClassLoader(bundleName));
        fragment.remoteActivity = ad.activity;

    }


    public EmbeddedActivityRecord startEmbeddedActivity() throws Exception{
        EmbeddedActivityRecord activityRecord = new EmbeddedActivityRecord();
        activityRecord.id = "embedded_"+mParent.getClass().getSimpleName();
        Intent intent = new Intent();
        intent.setClassName(mParent,EmbeddedActivity.class.getName());
        ActivityInfo info = intent.resolveActivityInfo(mParent.getPackageManager(), PackageManager.GET_ACTIVITIES);
        activityRecord.activity = (Activity) ActivityThread_startActivityNow.invoke(AndroidHack.getActivityThread(),
                mParent, activityRecord.id, intent, info, activityRecord.activity, null, null);
        ((EmbeddedActivity)activityRecord.activity).parentActivityRef = new WeakReference<Activity>(mParent);
        activityRecord.activityInfo = info;
        return activityRecord;
    }

    private class EmbeddedActivityRecord extends Binder {
        String id;                // Unique name of this record.
        ActivityInfo activityInfo;      // Package manager info about activity.
        Activity activity;              // Currently instantiated activity.
        Bundle instanceState;           // Last retrieved freeze state.
        int curState;
    }

    public static class EmbeddedActivity extends FragmentActivity{
        public WeakReference<Activity> parentActivityRef;

        @Override
        protected void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
        }

        @Override
        public void startActivityForResult(Intent intent, int requestCode) {
            if(parentActivityRef.get()!=null) {
                parentActivityRef.get().startActivityForResult(intent, requestCode);
            }
        }

        @Override
        public void startActivityFromFragment(Fragment fragment, Intent intent, int requestCode) {
            if(parentActivityRef.get()!=null) {
                ((FragmentActivity)parentActivityRef.get()).startActivityFromFragment(fragment, intent, requestCode);
            }
        }

        @Override
        public void startActivityFromFragment(Fragment fragment, Intent intent, int requestCode, @Nullable Bundle options) {
            if(parentActivityRef!=null) {
                ((FragmentActivity)parentActivityRef.get()).startActivityFromFragment(fragment, intent, requestCode, options);
            }
        }



    }

    public static class RemoteContext extends ContextWrapper{

        private ClassLoader classLoader;
        public RemoteContext(Context base,ClassLoader remoteClassLoader) {
            super(base);
            classLoader = remoteClassLoader;
        }

        @Override
        public ClassLoader getClassLoader() {
            return classLoader;
        }
    }
}
