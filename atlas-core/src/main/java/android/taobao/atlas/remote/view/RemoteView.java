package android.taobao.atlas.remote.view;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.taobao.atlas.bundleInfo.AtlasBundleInfoManager;
import android.taobao.atlas.bundleInfo.BundleListing;
import android.taobao.atlas.remote.IRemote;
import android.taobao.atlas.remote.IRemoteDelegator;
import android.taobao.atlas.remote.IRemoteTransactor;
import android.taobao.atlas.remote.RemoteActivityManager;
import android.taobao.atlas.remote.transactor.RemoteTransactor;
import android.taobao.atlas.runtime.BundleUtil;
import android.text.TextUtils;
import android.view.View;
import android.widget.FrameLayout;

import java.lang.reflect.Constructor;

/**
 * Created by guanjie on 2017/10/23.
 *
    RemoteView.RemoteViewFactory.createRemoteView(activity, intent, new RemoteView.OnRemoteViewStateListener() {
        @Override
        public void onViewCreated(RemoteView remoteView) {
            findViewById(R.id.container).addView(remoteView);
        }
        @Override
        public void onFailed(String errorInfo) {

        }
    });
 */

public class RemoteView extends FrameLayout implements IRemoteTransactor,IRemoteDelegator{

    public interface OnRemoteViewStateListener{
        void onViewCreated(RemoteView remoteView);

        void onFailed(String errorInfo);
    }

    public static class RemoteViewFactory{
        public static void createRemoteView(final Activity activity, Intent intent, final OnRemoteViewStateListener listener){
            final String key = intent.getComponent()!=null ? intent.getComponent().getClassName() :
                    intent.getAction();
            final String bundleName = AtlasBundleInfoManager.instance().getBundleForRemoteView(key);
            if(TextUtils.isEmpty(bundleName)){
                listener.onFailed("no such remote-view: "+intent);
            }
            BundleUtil.checkBundleStateAsync(bundleName, new Runnable() {
                @Override
                public void run() {
                    //success
                    try {
                        RemoteView remoteView = new RemoteView(activity);
                        remoteView.remoteActivity = RemoteActivityManager.obtain(activity).getRemoteHost(remoteView);
                        final BundleListing.BundleInfo bi = AtlasBundleInfoManager.instance().getBundleInfo(bundleName);
                        String viewClassName = bi.remoteViews.get(key);
                        Class viewClass = remoteView.remoteActivity.getClassLoader().loadClass(viewClassName);
                        Constructor cons = viewClass.getDeclaredConstructor(Context.class);
                        cons.setAccessible(true);
                        remoteView.targetView = (IRemote) cons.newInstance(remoteView.remoteActivity);
                        remoteView.targetBundleName = bundleName;
                        remoteView.addView((View)remoteView.targetView);
                        listener.onViewCreated(remoteView);
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

    private IRemote targetView;
    private String targetBundleName;
    private Activity remoteActivity;
    private IRemoteTransactor hostTransactor;

    @Override
    public Bundle call(String commandName, Bundle args, IResponse callback) {
        if(!(targetView instanceof IRemote)){
            throw new IllegalAccessError("targetView is not an implementation of : RemoteTransactor");
        }else {
            return ((IRemote)targetView).call(commandName,args,callback);
        }
    }

    @Override
    public void registerHostTransactor(IRemoteTransactor transactor) {
        hostTransactor = transactor;
    }

    @Override
    public String getTargetBundle() {
        return targetBundleName;
    }

    @Override
    public IRemote getRemoteTarget() {
        return targetView;
    }

    @Override
    public IRemoteTransactor getHostTransactor() {
        return hostTransactor;
    }

    public RemoteView(@NonNull Context context) {
        super(context);
    }
}
