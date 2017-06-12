package com.taobao.android;

import android.app.Activity;
import android.app.Application;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.support.v4.app.FragmentActivity;
import android.taobao.atlas.bundleInfo.AtlasBundleInfoManager;
import android.taobao.atlas.framework.Atlas;
import android.taobao.atlas.framework.BundleImpl;
import android.taobao.atlas.framework.Framework;
import android.taobao.atlas.runtime.ActivityTaskMgr;
import android.taobao.atlas.runtime.BundleUtil;
import android.taobao.atlas.runtime.RuntimeVariables;
import android.taobao.atlas.util.StringUtils;
import android.text.TextUtils;
import android.util.Log;
import android.view.ViewGroup;

/**
 * Created by guanjie on 16/11/16.
 */

public class ActivityGroupDelegate {

    private static final String STATES_KEY = "android:states";


    private LocalActivityManager mLocalActivityManager;
    private FragmentActivity mActivity;

    public LocalActivityManager getLocalActivityManager(){
        return mLocalActivityManager;
    }

    public ActivityGroupDelegate(FragmentActivity activity, Bundle bundle){
        mActivity = activity;
        try {
            mLocalActivityManager = new LocalActivityManager(activity, true);
        }catch(Throwable e){
            throw new RuntimeException(e);
        }
//        Bundle states = bundle != null
//                ? (Bundle) bundle.getBundle(STATES_KEY) : null;
        mLocalActivityManager.dispatchCreate(null);
        activity.getApplication().registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity activity, Bundle bundle) {
            }

            @Override
            public void onActivityStarted(Activity activity) {
            }

            @Override
            public void onActivityResumed(Activity activity) {
                if(mActivity == activity) {
                    mLocalActivityManager.dispatchResume();
                }
            }

            @Override
            public void onActivityPaused(Activity activity) {
                if(mActivity == activity) {
                    mLocalActivityManager.dispatchPause(mActivity.isFinishing());
                }
            }

            @Override
            public void onActivityStopped(Activity activity) {
                if(mActivity == activity) {
                    mLocalActivityManager.dispatchStop();
                }
            }

            @Override
            public void onActivitySaveInstanceState(Activity activity, Bundle bundle) {
                if(mActivity == activity) {
                    Bundle state = mLocalActivityManager.saveInstanceState();
                    if (state != null) {
                        bundle.putBundle(STATES_KEY, state);
                    }
                }
            }

            @Override
            public void onActivityDestroyed(Activity activity) {
                if(mActivity == activity) {
                    mLocalActivityManager.removeAllActivities();
                }
            }
        });
    }

//    public HashMap<String,Object> onRetainNonConfigurationChildInstances() {
//        if(dispatchRetainNonConfigurationInstanceMethod!=null) {
//            try {
//                return (HashMap<String, Object>) dispatchRetainNonConfigurationInstanceMethod.invoke(mLocalActivityManager);
//            } catch (Throwable e) {
//                e.printStackTrace();
//            }
//        }
//        return null;
//    }

//    private static Method dispatchRetainNonConfigurationInstanceMethod = null;
//    private static Method onActivityResultMethod = null;
//    private static Method noteStateNotSavedMethod = null;
//    private static Method findFragmentByWhoMethod = null;
//    static{
//        try {
//            dispatchRetainNonConfigurationInstanceMethod = LocalActivityManager.class.getDeclaredMethod("dispatchRetainNonConfigurationInstance");
//            dispatchRetainNonConfigurationInstanceMethod.setAccessible(true);
//            onActivityResultMethod = Activity.class.getDeclaredMethod("onActivityResult",int.class,int.class,Intent.class);
//            onActivityResultMethod.setAccessible(true);
//            noteStateNotSavedMethod = Class.forName("android.support.v4.app.FragmentManagerImpl").
//                    getDeclaredMethod("noteStateNotSaved");
//            noteStateNotSavedMethod.setAccessible(true);
//            findFragmentByWhoMethod = Class.forName("android.support.v4.app.FragmentManagerImpl").
//                    getDeclaredMethod("findFragmentByWho",String.class);
//            findFragmentByWhoMethod.setAccessible(true);
//        } catch (Throwable e) {
//            e.printStackTrace();
//        }
//
//    }


    public void startChildActivity(ViewGroup container, String key, Intent intent){
        //移除内容部分全部的View
        container.removeAllViews();
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        Activity contentActivity = mLocalActivityManager.getActivity(key);
        if(contentActivity!=null) {
            container.addView(
                    mLocalActivityManager.getActivity(key)
                            .getWindow().getDecorView(),
                    new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT));
            mLocalActivityManager.switchToChildActivity(key);
        }else{
            execStartChildActivityInternal(container, key, intent);
        }
    }

    private void performLaunchChildActivity(ViewGroup container,String key,Intent intent ){
        if(intent==null){
            Log.e("ActivityGroupDelegate","intent is null stop performLaunchChildActivity");
            return ;
        }
        mLocalActivityManager.startActivity(key,intent);
        container.addView(
                mLocalActivityManager.getActivity(key)
                        .getWindow().getDecorView(),
                new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));
    }

    public void execStartChildActivityInternal(ViewGroup container,String key, Intent intent){
        String packageName = null;
        String componentName = null ;
        Context context = container.getContext();
        if (intent.getComponent() != null) {
            packageName = intent.getComponent().getPackageName();
            componentName = intent.getComponent().getClassName();
        } else {
            ResolveInfo resolveInfo = context.getPackageManager().resolveActivity(intent, 0);
            if (resolveInfo != null && resolveInfo.activityInfo != null) {
                packageName = resolveInfo.activityInfo.packageName;
                componentName = resolveInfo.activityInfo.name;
            }
        }
        if (componentName == null){
            Log.e("ActivityGroupDelegate","can not find componentName");
        }
        if (!StringUtils.equals(context.getPackageName(), packageName)) {
            Log.e("ActivityGroupDelegate","childActivity can not be external Activity");
        }

        String bundleName = AtlasBundleInfoManager.instance().getBundleForComponet(componentName);
        if(!TextUtils.isEmpty(bundleName)){
            BundleImpl impl = (BundleImpl) Atlas.getInstance().getBundle(bundleName);
            if(impl!=null&&impl.checkValidate()) {
                performLaunchChildActivity(container,key,intent);
            }else {
                if(ActivityTaskMgr.getInstance().peekTopActivity()!=null && Looper.getMainLooper().getThread().getId()==Thread.currentThread().getId()) {
                    asyncStartActivity(container,key,bundleName,intent);
                }else{
                    performLaunchChildActivity(container,key,intent);
                }
            }
        }else{
            // Try to get class from system Classloader
            try {
                Class<?> clazz = null;
                clazz = Framework.getSystemClassLoader().loadClass(componentName);
                if (clazz != null) {
                    performLaunchChildActivity(container,key,intent);
                }
            } catch (ClassNotFoundException e) {
                Log.e("ActivityGroupDelegate","",e);
            }
        }

    }

    private void asyncStartActivity(final ViewGroup container,final String key,final String bundleName,final Intent intent){
        final Activity current = ActivityTaskMgr.getInstance().peekTopActivity();
        final Dialog dialog = current!=null ? RuntimeVariables.alertDialogUntilBundleProcessed(current,bundleName) : null;
        if(current!=null && dialog==null){
            throw new RuntimeException("alertDialogUntilBundleProcessed can not return null");
        }
        final int currentActivitySize = ActivityTaskMgr.getInstance().sizeOfActivityStack();
        final BundleUtil.CancelableTask successTask = new BundleUtil.CancelableTask(new Runnable() {
            @Override
            public void run() {
                if (current == ActivityTaskMgr.getInstance().peekTopActivity() || currentActivitySize==ActivityTaskMgr.getInstance().sizeOfActivityStack()+1) {
                    performLaunchChildActivity(container,key,intent);
                }

                if (dialog != null && current != null && !current.isFinishing()) {
                    try {
                        if(dialog.isShowing())
                            dialog.dismiss();
                    }catch (Throwable e){}
                }
            }
        });
        final BundleUtil.CancelableTask failedTask = new BundleUtil.CancelableTask(new Runnable() {
            @Override
            public void run() {
                if (current == ActivityTaskMgr.getInstance().peekTopActivity()) {

                }

                if (dialog != null && current != null && !current.isFinishing()) {
                    try {
                        if(dialog.isShowing())
                            dialog.dismiss();
                    }catch(Throwable e){}
                }
            }
        });

        if(dialog!=null) {
            dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
                    successTask.cancel();
                    failedTask.cancel();
                }
            });
            if(Atlas.getInstance().getBundle(bundleName)==null || Build.VERSION.SDK_INT<22) {
                if (dialog != null && current != null && !current.isFinishing() && !dialog.isShowing()) {
                    try {
                        dialog.show();
                    } catch (Throwable e) {
                    }
                }
            }
            BundleUtil.checkBundleStateAsync(bundleName, successTask, failedTask);
        }
    }
}
