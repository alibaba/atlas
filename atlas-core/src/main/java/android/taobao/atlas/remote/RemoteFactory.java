package android.taobao.atlas.remote;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.taobao.atlas.bundleInfo.AtlasBundleInfoManager;
import android.taobao.atlas.bundleInfo.BundleListing;
import android.taobao.atlas.remote.fragment.RemoteFragment;
import android.taobao.atlas.remote.transactor.RemoteTransactor;
import android.taobao.atlas.remote.view.RemoteView;
import android.taobao.atlas.runtime.BundleUtil;
import android.text.TextUtils;
import android.view.View;

import java.lang.reflect.Constructor;

/**
 * Created by guanjie on 2017/11/11.
 */

public class RemoteFactory {

    public interface OnRemoteStateListener<T extends IRemoteContext>{
        void onRemotePrepared(T remote);

        void onFailed(String errorInfo);
    }

    public static <T extends IRemoteContext> void requestRemote(final Class<T> remoteClass,final Activity activity,
                                                                final Intent intent, final OnRemoteStateListener listener){

        final String key = intent.getComponent()!=null ? intent.getComponent().getClassName() :
                intent.getAction();
        String tempBundleName = null;
        if(remoteClass == RemoteView.class){
            tempBundleName = AtlasBundleInfoManager.instance().getBundleForRemoteView(key);
        }else if(remoteClass == RemoteTransactor.class){
            tempBundleName = AtlasBundleInfoManager.instance().getBundleForRemoteTransactor(key);
        }else if(remoteClass == RemoteFragment.class){
            tempBundleName = AtlasBundleInfoManager.instance().getBundleForRemoteFragment(key);
        }
        final String bundleName = tempBundleName;
        if(TextUtils.isEmpty(bundleName)){
            listener.onFailed("no match remote-item with intent : "+intent);
        }
        BundleUtil.checkBundleStateAsync(bundleName, new Runnable() {
            @Override
            public void run() {
                //success
                try {
                    if(remoteClass == RemoteView.class){
                        RemoteView view = RemoteView.createRemoteView(activity,key,bundleName);
                        listener.onRemotePrepared(view);
                    }else if(remoteClass == RemoteTransactor.class){
                        RemoteTransactor transactor = RemoteTransactor.crateRemoteTransactor(activity,key,bundleName);
                        listener.onRemotePrepared(transactor);
                    }else if(remoteClass == RemoteFragment.class){
                        RemoteFragment fragment = RemoteFragment.createRemoteFragment(activity,key,bundleName);
                        listener.onRemotePrepared(fragment);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    listener.onFailed(e.getCause() == null ? e.toString() : e.getCause().toString());
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
