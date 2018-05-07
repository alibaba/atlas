package android.taobao.atlas.runtime.newcomponent;

import android.app.IServiceConnection;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ProviderInfo;
import android.os.IBinder;
import android.taobao.atlas.runtime.RuntimeVariables;
import android.taobao.atlas.runtime.newcomponent.activity.ActivityBridge;
import android.taobao.atlas.runtime.newcomponent.provider.ContentProviderBridge;
import android.taobao.atlas.runtime.newcomponent.receiver.ReceiverBridge;

/**
 * Created by guanjie on 2017/4/3.
 */

public class AdditionalActivityManagerProxy{

    private static AdditionalActivityManagerProxy sAdditionalActivityManagerProxy;


    public synchronized static AdditionalActivityManagerProxy get(){
        if(sAdditionalActivityManagerProxy ==null){
            sAdditionalActivityManagerProxy = new AdditionalActivityManagerProxy();
        }
        return sAdditionalActivityManagerProxy;
    }

    public void startRegisterReceivers(Context context){
        if(RuntimeVariables.getProcessName(context)
                .equals(context.getPackageName())) {
            ReceiverBridge.registerAdditionalReceiver();
        }
    }

    public ComponentName startService(Intent service) {
        return AdditionalActivityManagerNative.startService(service);
    }

    public boolean stopService(Intent service){
        return AdditionalActivityManagerNative.stopService(service);
    }

    public int bindService(IBinder token, Intent service, String resolveType, IServiceConnection connection) {
        return AdditionalActivityManagerNative.bindService(token,service,resolveType,connection);
    }

    public boolean unbindService(IServiceConnection conn) {
        return AdditionalActivityManagerNative.unbindService(conn);
    }

    public Object getContentProvider(ProviderInfo info){
        return ContentProviderBridge.getContentProvider(info);
    }

    public static void handleActivityStack(final Intent intent, final ActivityInfo info, final ActivityBridge.OnIntentPreparedObserver observer){
        AdditionalActivityManagerNative.handleActivityStack(intent,info,observer);
    }

    public static void notifyonReceived(final Intent intent, final ActivityInfo info){
        AdditionalActivityManagerNative.notifyonReceived(intent,info);
    }

}
