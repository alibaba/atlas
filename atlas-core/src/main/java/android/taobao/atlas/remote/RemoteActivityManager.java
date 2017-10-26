package android.taobao.atlas.remote;

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
import android.taobao.atlas.runtime.RuntimeVariables;
import android.util.Log;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Created by guanjie on 2017/10/13.
 */

public class RemoteActivityManager {

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

    public static RemoteActivityManager obtain(Activity parent){
        return new RemoteActivityManager(parent);
    }

    private RemoteActivityManager(Activity parent){
        mParent = parent;
    }

    private HashMap<String,EmbeddedActivityRecord> mActivityRecords = new HashMap<>();
    private Activity mParent;

    public synchronized Activity getRemoteHost(IRemoteDelegator delegator) throws Exception{
        String bundleName = delegator.getTargetBundle();
        if(!mActivityRecords.containsKey(bundleName)){
            EmbeddedActivityRecord record = startEmbeddedActivity(bundleName);
            mActivityRecords.put(bundleName,record);
        }
        EmbeddedActivityRecord ad = mActivityRecords.get(bundleName);
        ad.activity.addBoundRemoteDelegator(delegator);
        return ad.activity;

    }

    public EmbeddedActivityRecord startEmbeddedActivity(String bundleName) throws Exception{
        EmbeddedActivityRecord activityRecord = new EmbeddedActivityRecord();
        activityRecord.id = "embedded_"+mParent.getClass().getSimpleName();
        Field mThemeResourceF = AndroidHack.findField(mParent,"mThemeResource");
        int mThemeResource = (Integer)mThemeResourceF.get(mParent);
        Intent intent = new Intent();
        intent.setClassName(mParent,EmbeddedActivity.class.getName());
        intent.putExtra("themeId",mThemeResource);
        intent.putExtra("bundleName",bundleName);
        ActivityInfo info = intent.resolveActivityInfo(mParent.getPackageManager(), PackageManager.GET_ACTIVITIES);
        activityRecord.activity = (EmbeddedActivity) ActivityThread_startActivityNow.invoke(AndroidHack.getActivityThread(),
                mParent, activityRecord.id, intent, info, activityRecord.activity, null, null);
        ((EmbeddedActivity)activityRecord.activity).parentActivityRef = new WeakReference<Activity>(mParent);
        activityRecord.activityInfo = info;
        return activityRecord;
    }

    private class EmbeddedActivityRecord extends Binder {
        String id;                // Unique name of this record.
        ActivityInfo activityInfo;      // Package manager info about activity.
        EmbeddedActivity activity;              // Currently instantiated activity.
        Bundle instanceState;           // Last retrieved freeze state.
        int curState;
    }

    public static class EmbeddedActivity extends FragmentActivity{
        public WeakReference<Activity> parentActivityRef;
        public List<IRemoteDelegator> mBoundRemoteItems = new ArrayList<>();

        public void addBoundRemoteDelegator(IRemoteDelegator delegator){
            if(!mBoundRemoteItems.contains(delegator)){
                mBoundRemoteItems.add(delegator);
            }
        }

        @Override
        protected void onCreate(@Nullable Bundle savedInstanceState) {
            int themeResource = getIntent().getIntExtra("themeId",0);
            String bundleName = getIntent().getStringExtra("bundleName");
            if(themeResource>0){
                setTheme(themeResource);
            }
            super.onCreate(savedInstanceState);
            RemoteContext context = new RemoteContext(getBaseContext(),Atlas.getInstance().getBundleClassLoader(bundleName));
            if(AtlasHacks.ContextThemeWrapper_mBase!=null && AtlasHacks.ContextThemeWrapper_mBase.getField()!=null){
                AtlasHacks.ContextThemeWrapper_mBase.set(this,context);
            }
            if (AtlasHacks.ContextThemeWrapper_mResources != null) {
                //AtlasHacks.ContextThemeWrapper_mResources.on(activity).set(RuntimeVariables.delegateResources);
                AtlasHacks.ContextThemeWrapper_mResources.set(this, RuntimeVariables.delegateResources);
            }
            AtlasHacks.ContextWrapper_mBase.set(this,context);
        }

        @Override
        public Object getSystemService(String name) {
            if(parentActivityRef!=null && parentActivityRef.get()!=null){
                return parentActivityRef.get().getSystemService(name);
            }else{
                Log.e("EmbeddActivity","parent Activity has finished");
                return null;
            }
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
