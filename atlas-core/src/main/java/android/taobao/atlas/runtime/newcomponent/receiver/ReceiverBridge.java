package android.taobao.atlas.runtime.newcomponent.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import android.os.Handler;
import android.os.Looper;
import android.taobao.atlas.runtime.RuntimeVariables;
import android.taobao.atlas.runtime.newcomponent.AdditionalActivityManagerProxy;
import android.taobao.atlas.runtime.newcomponent.AdditionalPackageManager;

import java.util.List;

/**
 * Created by guanjie on 2017/4/12.
 */

public class ReceiverBridge {

    private static DelegateReceiver receiver;
    private static Handler sMainHandler  = new Handler(Looper.getMainLooper());


    public synchronized static void registerAdditionalReceiver() {
        if(receiver == null){
            receiver = new DelegateReceiver();
            IntentFilter additionalFilter = AdditionalPackageManager.getInstance().getAdditionIntentFilter();
            RuntimeVariables.androidApplication.registerReceiver(receiver,additionalFilter);
        }
    }

    public static void postOnReceived(final Intent intent,final ActivityInfo info){
        sMainHandler.post(new Runnable() {
            @Override
            public void run() {
                try{
                    Class clazz = RuntimeVariables.androidApplication.getClassLoader().loadClass(info.name);
                    BroadcastReceiver receiver = (BroadcastReceiver) clazz.newInstance();
                    receiver.onReceive(RuntimeVariables.androidApplication,intent);
                }catch(Throwable e){
                    e.printStackTrace();
                }
            }
        });
    }

    public static class DelegateReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if(intent==null){
                return;
            }
            List<ResolveInfo> infos = AdditionalPackageManager.getInstance().queryIntentReceivers(intent);
            if(infos!=null){
                for(ResolveInfo info : infos){
                    if(info.activityInfo.processName.equals(context.getPackageName())){
                        // main process
                        postOnReceived(intent,info.activityInfo);
                    }else{
                       // remote process
                        AdditionalActivityManagerProxy.notifyonReceived(intent,info.activityInfo);
                    }
                }
            }
        }
    }
}
