package android.taobao.atlas.remote.transactor;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.taobao.atlas.bundleInfo.AtlasBundleInfoManager;
import android.taobao.atlas.bundleInfo.BundleListing;
import android.taobao.atlas.framework.Atlas;
import android.taobao.atlas.remote.IRemote;
import android.taobao.atlas.remote.IRemoteContext;
import android.taobao.atlas.remote.IRemoteTransactor;
import android.taobao.atlas.remote.RemoteActivityManager;
import android.taobao.atlas.runtime.BundleUtil;
import android.text.TextUtils;


public class RemoteTransactor implements IRemoteContext,IRemoteTransactor{

    public static RemoteTransactor crateRemoteTransactor(Activity activity,String key,String bundleName) throws Exception{
        RemoteTransactor remoteTransactor = new RemoteTransactor();
        if(activity!=null) {
            remoteTransactor.remoteActivity = RemoteActivityManager.obtain(activity).getRemoteHost(remoteTransactor);
        }
        final BundleListing.BundleInfo bi = AtlasBundleInfoManager.instance().getBundleInfo(bundleName);
        String viewClassName = bi.remoteTransactors.get(key);
        Class transactorClass = Atlas.getInstance().getBundleClassLoader(bundleName).loadClass(viewClassName);
        remoteTransactor.targetTransactor = (IRemote)transactorClass.newInstance();
        remoteTransactor.targetBundleName = bundleName;
        return remoteTransactor;
    }

    private String targetBundleName;
    private Activity remoteActivity;
    private IRemoteTransactor hostTransactor;
    private IRemote targetTransactor;

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
        return targetTransactor;
    }

    @Override
    public IRemoteTransactor getHostTransactor() {
        return hostTransactor;
    }

    @Override
    public Bundle call(String commandName, Bundle args, IResponse callback) {
        return targetTransactor.call(commandName,args,callback);
    }

    @Override
    public <T> T getRemoteInterface(Class<T> interfaceClass) {
        return targetTransactor.getRemoteInterface(interfaceClass);
    }
}
