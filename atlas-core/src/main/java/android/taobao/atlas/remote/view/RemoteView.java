package android.taobao.atlas.remote.view;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.taobao.atlas.bundleInfo.AtlasBundleInfoManager;
import android.taobao.atlas.bundleInfo.BundleListing;
import android.taobao.atlas.hack.AndroidHack;
import android.taobao.atlas.remote.IRemote;
import android.taobao.atlas.remote.IRemoteContext;
import android.taobao.atlas.remote.IRemoteTransactor;
import android.taobao.atlas.remote.RemoteActivityManager;
import android.taobao.atlas.remote.Util;
import android.taobao.atlas.runtime.BundleUtil;
import android.text.TextUtils;
import android.view.View;
import android.widget.FrameLayout;

import java.lang.reflect.Constructor;

public class RemoteView extends FrameLayout implements IRemoteTransactor,IRemoteContext {

    public static RemoteView createRemoteView(Activity activity,String remoteViewKey,String bundleName) throws Exception{
        RemoteView remoteView = new RemoteView(activity);
        remoteView.targetBundleName = bundleName;
        Activity remoteActivity  = RemoteActivityManager.obtain(activity).getRemoteHost(remoteView);
        final BundleListing.BundleInfo bi = AtlasBundleInfoManager.instance().getBundleInfo(bundleName);
        String viewClassName = bi.remoteViews.get(remoteViewKey);
        Class viewClass = remoteActivity.getClassLoader().loadClass(viewClassName);
        Constructor cons = viewClass.getDeclaredConstructor(Context.class);
        cons.setAccessible(true);
        remoteView.targetView = (IRemote) cons.newInstance(remoteActivity);
        Util.findFieldFromInterface(remoteView.targetView,"remoteContext").set(remoteView.targetView,remoteView);
        Util.findFieldFromInterface(remoteView.targetView,"realHost").set(remoteView.targetView,remoteActivity);
        remoteView.addView((View)remoteView.targetView);
        return remoteView;
    }

    private IRemote targetView;
    private String targetBundleName;
    private IRemote hostTransactor;

    @Override
    public Bundle call(String commandName, Bundle args, IResponse callback) {
        if(!(targetView instanceof IRemote)){
            throw new IllegalAccessError("targetView is not an implementation of : RemoteTransactor");
        }else {
            return ((IRemote)targetView).call(commandName,args,callback);
        }
    }

    @Override
    public <T> T getRemoteInterface(Class<T> interfaceClass,Bundle args) {
        if(!(targetView instanceof IRemote)){
            throw new IllegalAccessError("targetView is not an implementation of : RemoteTransactor");
        }else {
            return ((IRemote)targetView).getRemoteInterface(interfaceClass,args);
        }
    }

    @Override
    public void registerHostTransactor(IRemote transactor) {
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
    public IRemote getHostTransactor() {
        return hostTransactor;
    }

    public RemoteView(@NonNull Context context) {
        super(context);
    }


}
