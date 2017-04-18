package android.taobao.atlas.runtime.newcomponent;

import android.app.IActivityManager;
import android.app.IServiceConnection;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ProviderInfo;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.taobao.atlas.runtime.RuntimeVariables;
import android.taobao.atlas.runtime.newcomponent.provider.ContentProviderBridge;
import android.taobao.atlas.runtime.newcomponent.receiver.ReceiverBridge;
import android.taobao.atlas.runtime.newcomponent.service.ServiceBridge;

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
        return ServiceBridge.startService(service);
    }

    public boolean stopService(Intent service){
        return ServiceBridge.stopService(service);
    }

    public int bindService(IBinder token, Intent service, String resolveType, IServiceConnection connection) {
        return ServiceBridge.bindService(token,service,resolveType,connection);
    }

    public boolean unbindService(IServiceConnection conn) {
        return ServiceBridge.unbindService(conn);
    }

    public Object getContentProvider(ProviderInfo info){
        return ContentProviderBridge.getContentProvider(info);
    }

}
