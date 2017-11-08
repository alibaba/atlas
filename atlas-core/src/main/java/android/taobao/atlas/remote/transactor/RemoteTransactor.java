package android.taobao.atlas.remote.transactor;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.taobao.atlas.bundleInfo.AtlasBundleInfoManager;
import android.taobao.atlas.bundleInfo.BundleListing;
import android.taobao.atlas.framework.Atlas;
import android.taobao.atlas.remote.IRemote;
import android.taobao.atlas.remote.IRemoteDelegator;
import android.taobao.atlas.remote.IRemoteTransactor;
import android.taobao.atlas.remote.RemoteActivityManager;
import android.taobao.atlas.runtime.BundleUtil;
import android.text.TextUtils;


/**
 * Created by guanjie on 2017/10/25.
 *
 *  RemoteTransactor.RemoteTransactorFactory.createRemoteTransactor(activity, intent, new RemoteTransactor.OnRemoteTransactorStateListener(){
        @Override
        public void onTransactorCreated(RemoteTransactor transactor) {
            transactor.call("exec_fun1", bundle, new IResponse() {
                @Override
                public void OnResponse(Bundle bundle) {

                }
            });
        }
        @Override
        public void onFailed(String errorInfo) {

        }
    });
 */

public class RemoteTransactor implements IRemoteDelegator,IRemoteTransactor{

    public interface OnRemoteTransactorStateListener{
        void onTransactorCreated(RemoteTransactor remoteTransactor);

        void onFailed(String errorInfo);
    }

    public static class RemoteTransactorFactory{
        public static void createRemoteTransactor(final Activity activity, Intent intent, final RemoteTransactor.OnRemoteTransactorStateListener listener){
            final String key = intent.getComponent()!=null ? intent.getComponent().getClassName() :
                    intent.getAction();
            final String bundleName = AtlasBundleInfoManager.instance().getBundleForRemoteTransactor(key);
            if(TextUtils.isEmpty(bundleName)){
                listener.onFailed("no such remote-transactor: "+intent);
            }
            BundleUtil.checkBundleStateAsync(bundleName, new Runnable() {
                @Override
                public void run() {
                    //success
                    try {
                        RemoteTransactor remoteTransactor = new RemoteTransactor();
                        if(activity!=null) {
                            remoteTransactor.remoteActivity = RemoteActivityManager.obtain(activity).getRemoteHost(remoteTransactor);
                        }
                        final BundleListing.BundleInfo bi = AtlasBundleInfoManager.instance().getBundleInfo(bundleName);
                        String viewClassName = bi.remoteTransactors.get(key);
                        Class transactorClass = Atlas.getInstance().getBundleClassLoader(bundleName).loadClass(viewClassName);
                        remoteTransactor.targetTransactor = (IRemote)transactorClass.newInstance();
                        remoteTransactor.targetBundleName = bundleName;
                        listener.onTransactorCreated(remoteTransactor);
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
}
