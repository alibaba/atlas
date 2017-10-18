package android.taobao.atlas.remote.fragment;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.res.Configuration;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentHostCallback;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.LoaderManager;
import android.taobao.atlas.bundleInfo.AtlasBundleInfoManager;
import android.taobao.atlas.bundleInfo.BundleListing;
import android.taobao.atlas.hack.AndroidHack;
import android.taobao.atlas.runtime.BundleUtil;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Created by guanjie on 2017/10/12.
    RemoteFragment.RemoteFragmentFactory.createRemoteFragment(activity, intent, new RemoteFragment.OnRemoteFragmentStateListener() {
        @Override
        public void onFragmentCreated(RemoteFragment fragment) {
            FragmentTransaction transaction  = getSupportFragmentManager().beginTransaction();
            transaction.add(containerViewId,fragment).commit();
        }
        @Override
        public void onFailed(String errorInfo) {

        }
    });
 */
public class RemoteFragment extends Fragment {

    public interface OnRemoteFragmentStateListener{
        void onFragmentCreated(RemoteFragment fragment);

        void onFailed(String errorInfo);
    }

    public static class RemoteFragmentFactory{

        public static void createRemoteFragment(final Activity rFragmentHost, Intent intent, final OnRemoteFragmentStateListener listener){
            final String fragmentKey = intent.getComponent().getClassName()!=null ? intent.getComponent().getClassName() :
                    intent.getAction();
            final String bundleName = AtlasBundleInfoManager.instance().getBundleForRemoteFragment(fragmentKey);
            if(TextUtils.isEmpty(bundleName)){
                listener.onFailed("no such remote fragment: "+intent);
            }
            BundleUtil.checkBundleStateAsync(bundleName, new Runnable() {
                @Override
                public void run() {
                    //success
                    try {
                        RemoteFragment remoteFragment = new RemoteFragment();
                        RemoteContextManager.obtain(rFragmentHost).prepareRemoteFragment(remoteFragment,bundleName);
                        final BundleListing.BundleInfo bi = AtlasBundleInfoManager.instance().getBundleInfo(bundleName);
                        String fragmentClazzName = bi.remoteFragments.get(fragmentKey);
                        remoteFragment.targetFragment = (Fragment)remoteFragment.remoteContext.getClassLoader().loadClass(fragmentClazzName).newInstance();
                        remoteFragment.targetBundleName = bundleName;
                        listener.onFragmentCreated(remoteFragment);
                    } catch (Exception e) {
                        e.printStackTrace();
                        listener.onFailed(e.getCause().toString());
                    }
                }
            }, new Runnable() {
                @Override
                public void run() {
                    //fail
                    listener.onFailed("install bundle failed: "+bundleName);
                }
            });
        }
    }

    public Fragment targetFragment;
    public String targetBundleName;
    public Activity remoteActivity;
    public RemoteContextManager.RemoteContext remoteContext;
    public Field mCalled ;



    private FragmentHostCallback getFragmentHostCallback(FragmentHostCallback callback){
        try {
            Class HostCallbacksClz = Class.forName("android.support.v4.app.FragmentActivity$HostCallbacks");
            Constructor constructor = HostCallbacksClz.getDeclaredConstructor(FragmentActivity.class);
            constructor.setAccessible(true);
            Object hostCallbacks = constructor.newInstance(remoteActivity);
            Field[] fields = HostCallbacksClz.getSuperclass().getDeclaredFields();
            for(Field field : fields){
                field.setAccessible(true);
                if(field.getName().equals("mActivity")){
                    field.set(hostCallbacks,remoteActivity);
                }else if(field.getName().equals("mContext")){
                    field.set(hostCallbacks,remoteContext);
                }else{
                    field.set(hostCallbacks,field.get(callback));
                }
            }
            return (FragmentHostCallback) hostCallbacks;
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }

    }

    @Override
    public String toString() {
        return targetFragment.toString();
    }

    @Override
    public void setArguments(Bundle args) {
        targetFragment.setArguments(args);
    }

    @Override
    public void setInitialSavedState(SavedState state) {
        targetFragment.setInitialSavedState(state);
    }

    @Override
    public void setTargetFragment(Fragment fragment, int requestCode) {
        super.setTargetFragment(fragment, requestCode);
        targetFragment.setTargetFragment(fragment,requestCode);
    }

    @Override
    public Context getContext() {
        return remoteContext;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        Field mHost;
        try {
            mCalled = getClass().getSuperclass().getDeclaredField("mCalled");
            mCalled.setAccessible(true);
            mHost = AndroidHack.findField(targetFragment,"mHost");
            Field mOriginalHost = getClass().getSuperclass().getDeclaredField("mHost");
            mOriginalHost.setAccessible(true);
            mHost.set(targetFragment,getFragmentHostCallback((FragmentHostCallback) mOriginalHost.get(this)));
            Field mFragmentManager = AndroidHack.findField(targetFragment,"mFragmentManager");
            mFragmentManager.set(targetFragment,getFragmentManager());
            Field mCalled = AndroidHack.findField(targetFragment,"mCalled");
            mCalled.set(targetFragment,false);
            targetFragment.onAttach(remoteActivity);
            FragmentManager manager = getChildFragmentManager();
            Field mChildFragmentManager = AndroidHack.findField(targetFragment,"mChildFragmentManager");
            mChildFragmentManager.set(targetFragment,manager);
            if(!(Boolean)mCalled.get(targetFragment)){
                throw new RuntimeException("Fragment " + targetFragment
                        + " did not call through to super.onAttach()");
            }
            Field mIndexF = getClass().getSuperclass().getDeclaredField("mIndex");
            mIndexF.setAccessible(true);
            Field mWhoF = getClass().getSuperclass().getDeclaredField("mWho");
            mWhoF.setAccessible(true);
            int index = (Integer) mIndexF.get(this);
            String who = (String)mWhoF.get(this);
            Field targetIndexF = AndroidHack.findField(targetFragment,"mIndex");
            Field targetWhoF = AndroidHack.findField(targetFragment,"mWho");
            targetIndexF.set(targetFragment,index);
            targetWhoF.set(targetFragment,who);
            Field tagF = AndroidHack.findField(targetFragment,"mTag");
            tagF.set(targetFragment,getTag());
            Method performCreate = AndroidHack.findMethod(targetFragment,"performCreate",Bundle.class);
            performCreate.invoke(targetFragment,(Bundle)null);

        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        try {
            inflater = LayoutInflater.from(remoteContext);
            View view =  targetFragment.onCreateView(inflater,container,savedInstanceState);
            if(view!=null){
                Field mInnerView = AndroidHack.findField(targetFragment,"mInnerView");
                Field mView = AndroidHack.findField(targetFragment,"mView");
                mInnerView.set(targetFragment,view);
                mView.set(targetFragment,view);
                Field mHidden = AndroidHack.findField(this,"mHidden");
                mHidden.set(this,targetFragment.isHidden());
            }
            return view;
        } catch (Throwable e) {
           throw new RuntimeException(e);
        }
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        targetFragment.onViewCreated(view,savedInstanceState);
    }

    @Override
    public void onStart() {
        Field mState = null;
        try {
            mCalled.set(this,true);
            mState = AndroidHack.findField(targetFragment,"mState");
            mState.set(targetFragment,4);
            targetFragment.onStart();
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        Field mState = null;
        try {
            mState = AndroidHack.findField(targetFragment,"mState");
            mState.set(targetFragment,5);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
        targetFragment.onResume();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        targetFragment.onSaveInstanceState(outState);
    }


    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        targetFragment.onConfigurationChanged(newConfig);
    }

    @Override
    public void onPause() {
        Field mState = null;
        try {
            mState = AndroidHack.findField(targetFragment,"mState");
            mState.set(targetFragment,4);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
        targetFragment.onPause();
    }

    @Override
    public void onStop() {
        super.onStop();
        Field mState = null;
        try {
            mState = AndroidHack.findField(targetFragment,"mState");
            mState.set(targetFragment,3);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
        targetFragment.onStop();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        targetFragment.onLowMemory();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        Field mState = null;
        try {
            mState = AndroidHack.findField(targetFragment,"mState");
            mState.set(targetFragment,1);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
        targetFragment.onDestroyView();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Field mState = null;
        try {
            mState = AndroidHack.findField(targetFragment,"mState");
            mState.set(targetFragment,0);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
        targetFragment.onDestroy();
    }

    @Override
    public void onDetach() {
        super.onDetach();
        targetFragment.onDetach();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        targetFragment.onHiddenChanged(hidden);
    }

    @Override
    public void setRetainInstance(boolean retain) {
        super.setRetainInstance(retain);
        targetFragment.setRetainInstance(retain);
    }

    @Override
    public void setHasOptionsMenu(boolean hasMenu) {
        super.setHasOptionsMenu(hasMenu);
        targetFragment.setHasOptionsMenu(hasMenu);
    }

    @Override
    public void setMenuVisibility(boolean menuVisible) {
        targetFragment.setMenuVisibility(menuVisible);
    }

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        targetFragment.setUserVisibleHint(isVisibleToUser);
    }

    @Override
    public boolean getUserVisibleHint() {
        return targetFragment.getUserVisibleHint();
    }

    @Override
    public LoaderManager getLoaderManager() {
        return targetFragment.getLoaderManager();
    }

    @Override
    public void startActivity(Intent intent) {
        targetFragment.startActivity(intent);
    }

    @Override
    public void startActivity(Intent intent, @Nullable Bundle options) {
        targetFragment.startActivity(intent, options);
    }

    @Override
    public void startActivityForResult(Intent intent, int requestCode) {
        targetFragment.startActivityForResult(intent, requestCode);
    }

    @Override
    public void startActivityForResult(Intent intent, int requestCode, @Nullable Bundle options) {
        targetFragment.startActivityForResult(intent, requestCode, options);
    }

    @Override
    public void startIntentSenderForResult(IntentSender intent, int requestCode, @Nullable Intent fillInIntent, int flagsMask, int flagsValues, int extraFlags, Bundle options) throws IntentSender.SendIntentException {
        targetFragment.startIntentSenderForResult(intent, requestCode, fillInIntent, flagsMask, flagsValues, extraFlags, options);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        targetFragment.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        targetFragment.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    public boolean shouldShowRequestPermissionRationale(@NonNull String permission) {
        return targetFragment.shouldShowRequestPermissionRationale(permission);
    }

    @Override
    public LayoutInflater getLayoutInflater(Bundle savedInstanceState) {
        return LayoutInflater.from(remoteContext);
    }

    @Override
    public void onInflate(Context context, AttributeSet attrs, Bundle savedInstanceState) {
        throw new RuntimeException("remote fragment can not be inflated from xml");
    }


    @Override
    public void onAttachFragment(Fragment childFragment) {
        super.onAttachFragment(childFragment);
        targetFragment.onAttachFragment(childFragment);
    }

    @Override
    public Animation onCreateAnimation(int transit, boolean enter, int nextAnim) {
        return targetFragment.onCreateAnimation(transit, enter, nextAnim);
    }

    @Nullable
    @Override
    public View getView() {
        return super.getView();
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        targetFragment.onActivityCreated(null);
    }

    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
        targetFragment.onViewStateRestored(savedInstanceState);
    }

    @Override
    public void onMultiWindowModeChanged(boolean isInMultiWindowMode) {
        targetFragment.onMultiWindowModeChanged(isInMultiWindowMode);
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode) {
        targetFragment.onPictureInPictureModeChanged(isInPictureInPictureMode);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        targetFragment.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        targetFragment.onPrepareOptionsMenu(menu);
    }

    @Override
    public void onDestroyOptionsMenu() {
        targetFragment.onDestroyOptionsMenu();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return targetFragment.onOptionsItemSelected(item);
    }

    @Override
    public void onOptionsMenuClosed(Menu menu) {
        targetFragment.onOptionsMenuClosed(menu);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        targetFragment.onCreateContextMenu(menu, v, menuInfo);
    }

    @Override
    public void registerForContextMenu(View view) {
        targetFragment.registerForContextMenu(view);
    }

    @Override
    public void unregisterForContextMenu(View view) {
        targetFragment.unregisterForContextMenu(view);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        return targetFragment.onContextItemSelected(item);
    }

    @Override
    public void setEnterSharedElementCallback(android.support.v4.app.SharedElementCallback callback) {
        super.setEnterSharedElementCallback(callback);
        targetFragment.setEnterSharedElementCallback(callback);
    }

    @Override
    public void setExitSharedElementCallback(android.support.v4.app.SharedElementCallback callback) {
        super.setExitSharedElementCallback(callback);
        targetFragment.setExitSharedElementCallback(callback);

    }

    @Override
    public void setEnterTransition(Object transition) {
        targetFragment.setEnterTransition(transition);
    }

    @Override
    public Object getEnterTransition() {
        return targetFragment.getEnterTransition();
    }

    @Override
    public void setReturnTransition(Object transition) {
        targetFragment.setReturnTransition(transition);

    }

    @Override
    public Object getReturnTransition() {
        return targetFragment.getReturnTransition();
    }

    @Override
    public void setExitTransition(Object transition) {
        targetFragment.setExitTransition(transition);
    }

    @Override
    public Object getExitTransition() {
        return targetFragment.getExitTransition();
    }

    @Override
    public void setReenterTransition(Object transition) {
        targetFragment.setReenterTransition(transition);
    }

    @Override
    public Object getReenterTransition() {
        return targetFragment.getReenterTransition();
    }

    @Override
    public void setSharedElementEnterTransition(Object transition) {
        targetFragment.setSharedElementEnterTransition(transition);
    }

    @Override
    public Object getSharedElementEnterTransition() {
        return targetFragment.getSharedElementEnterTransition();
    }

    @Override
    public void setSharedElementReturnTransition(Object transition) {
        targetFragment.setSharedElementReturnTransition(transition);
    }

    @Override
    public Object getSharedElementReturnTransition() {
        return targetFragment.getSharedElementReturnTransition();
    }

    @Override
    public void setAllowEnterTransitionOverlap(boolean allow) {
        targetFragment.setAllowEnterTransitionOverlap(allow);
    }

    @Override
    public boolean getAllowEnterTransitionOverlap() {
        return targetFragment.getAllowEnterTransitionOverlap();
    }

    @Override
    public void setAllowReturnTransitionOverlap(boolean allow) {
        targetFragment.setAllowReturnTransitionOverlap(allow);
    }

    @Override
    public boolean getAllowReturnTransitionOverlap() {
        return targetFragment.getAllowReturnTransitionOverlap();
    }

    @Override
    public void postponeEnterTransition() {
        targetFragment.postponeEnterTransition();
    }

    @Override
    public void startPostponedEnterTransition() {
        targetFragment.startPostponedEnterTransition();
    }

    @Override
    public void dump(String prefix, FileDescriptor fd, PrintWriter writer, String[] args) {
        super.dump(prefix, fd, writer, args);
    }
}
