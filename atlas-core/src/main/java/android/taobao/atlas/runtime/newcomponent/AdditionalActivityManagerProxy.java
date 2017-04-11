package android.taobao.atlas.runtime.newcomponent;

import android.app.IActivityManager;
import android.app.IServiceConnection;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ProviderInfo;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;

/**
 * Created by guanjie on 2017/4/3.
 */

public class AdditionalActivityManagerProxy extends Handler{

    public final int BIND_SERVICE_MSG = 1;
    public final int UNBIND_SERVICE_MSG = 2;
    public final int START_SERVICE_MSG = 3;
    public final int STOP_SERVICE_MSG = 4;

    private static AdditionalActivityManagerProxy sAdditionalActivityManagerProxy;
    private static HandlerThread shandlerThread;
    private Handler sMainHandler;


    static{
        shandlerThread = new HandlerThread("atlas_service_manager");
        shandlerThread.start();
    }

    public synchronized static AdditionalActivityManagerProxy get(){
        if(sAdditionalActivityManagerProxy ==null){
            sAdditionalActivityManagerProxy = new AdditionalActivityManagerProxy();
        }
        return sAdditionalActivityManagerProxy;
    }


    public AdditionalActivityManagerProxy(){
        super(shandlerThread.getLooper());
        sMainHandler = new Handler(Looper.getMainLooper());
    }

    public ComponentName startService(Intent service) {
        return null;
    }

    public boolean stopService(Intent service){
        return false;
    }

    public int bindService(IBinder token,
                               Intent service, String resolvedType, IServiceConnection connection) {
        return 1;
    }

    /**
     *
     * @param info
     * @return IActivityManager.ContentProviderHolder
     */
    public IActivityManager.ContentProviderHolder getContentProvider(ProviderInfo info){

    }

    public boolean unbindService(IServiceConnection conn) {
        return true;
    }

    @Override
    public void handleMessage(Message msg) {
        switch(msg.what){
            case BIND_SERVICE_MSG:
                break;
            case UNBIND_SERVICE_MSG:
                break;
            case START_SERVICE_MSG:
                break;
            case STOP_SERVICE_MSG:
                break;
            default:
                break;
        }
    }

}
