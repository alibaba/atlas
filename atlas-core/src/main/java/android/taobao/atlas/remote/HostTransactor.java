package android.taobao.atlas.remote;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import java.util.HashMap;

/**
 * Created by guanjie on 2017/10/24.
 */

public class HostTransactor implements IRemoteTransactor {

    private static HashMap<IRemote,HostTransactor> sHostTransactors = new HashMap<>();

    public static HostTransactor get(IRemote remoteItem){
        if(sHostTransactors.containsKey(remoteItem)){
            return sHostTransactors.get(remoteItem);
        }
        RemoteActivityManager.EmbeddedActivity activity = (RemoteActivityManager.EmbeddedActivity) ((View)remoteItem).getContext();
        if(activity.mBoundRemoteItems!=null){
            for(int x=0; x<activity.mBoundRemoteItems.size(); x++){
                IRemoteContext delegator = activity.mBoundRemoteItems.get(x);
                if(delegator.getRemoteTarget() == remoteItem){
                    if(delegator.getHostTransactor()==null){
                        Log.e("HostTransactor","no host-transactor,maybe has not been registered");
                    }
                    HostTransactor transactor =  new HostTransactor(delegator.getHostTransactor(),activity);
                    sHostTransactors.put(remoteItem,transactor);
                    return transactor;
                }
            }
        }
        Log.e("HostTransactor","impossible error");
        return null;
    }



    private final IRemote host;
    private final Activity          embeddedActivity;

    private HostTransactor(IRemote remote,Activity activity){
        host = remote;
        embeddedActivity = activity;
    }

    @Override
    public Bundle call(String commandName, Bundle args, IResponse callback) {
        if(host!=null) {
            return host.call(commandName, args, callback);
        }else{
            Log.e("HostTransactor","no real transactor");
            return null;
        }
    }

    @Override
    public <T> T getRemoteInterface(Class<T> interfaceClass,Bundle args) {
        if(host!=null) {
            return host.getRemoteInterface(interfaceClass,args);
        }else{
            Log.e("HostTransactor","no real transactor");
            return null;
        }
    }

    public Activity getDelegateActivity(){
        return embeddedActivity;
    }
}
