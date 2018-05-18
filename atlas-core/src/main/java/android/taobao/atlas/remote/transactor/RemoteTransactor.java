package android.taobao.atlas.remote.transactor;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.taobao.atlas.bundleInfo.AtlasBundleInfoManager;
import android.taobao.atlas.bundleInfo.BundleListing;
import android.taobao.atlas.framework.Atlas;
import android.taobao.atlas.hack.AndroidHack;
import android.taobao.atlas.remote.IRemote;
import android.taobao.atlas.remote.IRemoteContext;
import android.taobao.atlas.remote.IRemoteTransactor;
import android.taobao.atlas.remote.RemoteActivityManager;
import android.taobao.atlas.remote.Util;
import android.taobao.atlas.runtime.BundleUtil;
import android.text.TextUtils;


public class RemoteTransactor implements IRemoteContext,IRemoteTransactor{

    public static RemoteTransactor crateRemoteTransactor(Activity activity,String key,String bundleName) throws Exception{
        RemoteTransactor remoteTransactor = new RemoteTransactor();
        remoteTransactor.targetBundleName = bundleName;
        if(activity!=null) {
            remoteTransactor.remoteActivity = RemoteActivityManager.obtain(activity).getRemoteHost(remoteTransactor);
        }
        final BundleListing.BundleInfo bi = AtlasBundleInfoManager.instance().getBundleInfo(bundleName);
        String viewClassName = bi.remoteTransactors.get(key);
        Class transactorClass = Atlas.getInstance().getBundleClassLoader(bundleName).loadClass(viewClassName);
        IRemote remote = (IRemote)transactorClass.newInstance();
        remoteTransactor.targetTransactor = remote;
        Util.findFieldFromInterface(remoteTransactor.targetTransactor,"remoteContext").set(remoteTransactor.targetTransactor,remoteTransactor);
        Util.findFieldFromInterface(remoteTransactor.targetTransactor,"realHost").set(remoteTransactor.targetTransactor,remoteTransactor.remoteActivity);
        return remoteTransactor;
    }

    private String targetBundleName;
    private Activity remoteActivity;
    private IRemote hostTransactor;
    private IRemote targetTransactor;

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
        return targetTransactor;
    }

    @Override
    public IRemote getHostTransactor() {
        return hostTransactor;
    }

    @Override
    public Bundle call(String commandName, Bundle args, IResponse callback) {
        return targetTransactor.call(commandName,args,callback);
    }

    @Override
    public <T> T getRemoteInterface(Class<T> interfaceClass,Bundle args) {
        return targetTransactor.getRemoteInterface(interfaceClass,args);
    }
}
