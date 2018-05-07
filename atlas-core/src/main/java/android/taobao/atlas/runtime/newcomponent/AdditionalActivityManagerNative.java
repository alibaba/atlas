package android.taobao.atlas.runtime.newcomponent;

import android.app.IServiceConnection;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.content.pm.ServiceInfo;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.RemoteException;
import android.taobao.atlas.runtime.RuntimeVariables;
import android.taobao.atlas.runtime.newcomponent.activity.ActivityBridge;
import android.taobao.atlas.runtime.newcomponent.service.IDelegateBinder;
import android.util.Log;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

/**
 * Created by guanjie on 2017/4/5.
 */

public class AdditionalActivityManagerNative {

    private static HandlerThread shandlerThread;
    private static Handler sServicehandler;
    private static ConcurrentHashMap<String,AdditionalActivityManagerNative> sBridges = new ConcurrentHashMap<>();

    private IDelegateBinder mRemoteDelegate;
    private CountDownLatch mCountDownLatch;
    private String processName;
    private Intent targetIntent;

    HashMap<Intent,IServiceConnection> mActiveServiceInfo = new HashMap<>();

    static {
        shandlerThread = new HandlerThread("atlas_service_manager");
        shandlerThread.start();
        sServicehandler = new Handler(shandlerThread.getLooper());

    }

    private AdditionalActivityManagerNative(String processname) {
        this.processName = processname;
    }

    private static AdditionalActivityManagerNative obtain(String processName) {
        if (sBridges.get(processName) == null) {
            synchronized (AdditionalActivityManagerNative.class) {
                if (sBridges.get(processName) == null) {
                    AdditionalActivityManagerNative bridge = new AdditionalActivityManagerNative(processName);
                    sBridges.put(processName,bridge);
                }
            }
        }
        return sBridges.get(processName);
    }

    public IDelegateBinder getRemoteDelegate(){
        if(mRemoteDelegate!=null && mRemoteDelegate.asBinder().isBinderAlive()){
            return mRemoteDelegate;
        }else{
            connectDelegateService(processName);
            return mRemoteDelegate;
        }
    }

    private synchronized void connectDelegateService(String processName) {
        if(mRemoteDelegate!=null && mRemoteDelegate.asBinder().isBinderAlive()){
            return ;
        }
        mCountDownLatch = new CountDownLatch(1);
        if(targetIntent==null){
            Intent service = new Intent();
            String delegateComponentName = BridgeUtil.getBridgeName(BridgeUtil.TYPE_SERVICEBRIDGE,processName);
            service.setClassName(RuntimeVariables.androidApplication, delegateComponentName);
            targetIntent = service;
        }
        RuntimeVariables.androidApplication.bindService(targetIntent, mDelegateConnection,
                Context.BIND_AUTO_CREATE);
        try {
            mCountDownLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static ComponentName startService(final Intent service) {
        final ServiceInfo info = AdditionalPackageManager.getInstance().getNewComponentInfo(service.getComponent(),ServiceInfo.class);
        if(info==null){
            Log.e("ServiceBridge","can't find startservice | serviceinfo for intent: "+service.toString());
            return null;
        }
        sServicehandler.post(new Runnable() {
            @Override
            public void run() {
                String processName = info.processName;
                try {
                    AdditionalActivityManagerNative.obtain(processName).getRemoteDelegate().startService(service,info);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        });
        return service.getComponent();
    }

    public static boolean stopService(final Intent service){
        final ServiceInfo info = AdditionalPackageManager.getInstance().getNewComponentInfo(service.getComponent(),ServiceInfo.class);
        if(info==null){
            Log.e("ServiceBridge","can't stopService | serviceinfo for intent: "+service.toString());
            return false;
        }
        sServicehandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    String processName = info.processName;
                    AdditionalActivityManagerNative.obtain(processName).getRemoteDelegate().stopService(service);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        });
        return true;
    }

    public static int bindService(final IBinder token, final Intent service, final String resolvedType, final IServiceConnection connection) {
        final ServiceInfo info = AdditionalPackageManager.getInstance().getNewComponentInfo(service.getComponent(),ServiceInfo.class);
        if(info==null){
            Log.e("ServiceBridge","can't bindService | serviceinfo for intent: "+service.toString());
            return 0;
        }
        sServicehandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    String processName = info.processName;
                    AdditionalActivityManagerNative.obtain(processName).mActiveServiceInfo.put(service,connection);
                    AdditionalActivityManagerNative.obtain(processName).getRemoteDelegate().bindService(service,token,connection);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        });
        return 1;
    }

    public static boolean unbindService(final IServiceConnection conn) {
        String tmp = null;
        for (Map.Entry<String,AdditionalActivityManagerNative> entry : sBridges.entrySet()) {
            if(entry.getValue().mActiveServiceInfo.containsValue(conn)){
                tmp = entry.getKey();
            }
        }
        final String processOfRemoteService = tmp;
        if(processOfRemoteService!=null) {
            sServicehandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        AdditionalActivityManagerNative.obtain(processOfRemoteService).mRemoteDelegate.unbindService(conn);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
            });
            return true;
        }else{
            return false;
        }
    }

    public static void handleActivityStack(final Intent intent, final ActivityInfo info, final ActivityBridge.OnIntentPreparedObserver observer){
        sServicehandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    Intent result = AdditionalActivityManagerNative.obtain(info.processName).getRemoteDelegate().handleActivityStack(intent,info);
                    if(observer!=null){
                        observer.onPrepared(result);
                    }
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public static void notifyonReceived(final Intent intent, final ActivityInfo info){
        sServicehandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    AdditionalActivityManagerNative.obtain(info.processName).getRemoteDelegate().handleReceiver(intent,info);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    /**
     * process maybe killed,so active service need be restarted
     * @param delegate
     */
    private void recoverActiveServie(IDelegateBinder delegate){
        if(mActiveServiceInfo.size()>0){
            //do recover
            Iterator iter = mActiveServiceInfo.entrySet().iterator();
            while (iter.hasNext()) {
                Map.Entry<Intent,IServiceConnection> entry = (Map.Entry<Intent,IServiceConnection>) iter.next();
                Intent intent = entry.getKey();
                IServiceConnection connection = entry.getValue();
                if(connection!=null){
                    //service has created
                    try {
                        delegate.bindService(intent,null,connection);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }else{
                    try {
                        delegate.startService(intent,null);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    private ServiceConnection mDelegateConnection = new ServiceConnection() {

        @Override
        public void onServiceDisconnected(ComponentName name) {

        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mRemoteDelegate = IDelegateBinder.Stub.asInterface(service);
            try {
                mRemoteDelegate.asBinder().linkToDeath(mBinderPoolDeathRecipient, 0);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
            recoverActiveServie(mRemoteDelegate);
            mCountDownLatch.countDown();
        }
    };

    private IBinder.DeathRecipient mBinderPoolDeathRecipient = new IBinder.DeathRecipient() {
        @Override
        public void binderDied() {
            Log.w("ServiceBridge", "DelegateService " + mRemoteDelegate + " died unexpectedly");

            mRemoteDelegate.asBinder().unlinkToDeath(mBinderPoolDeathRecipient, 0);
            mRemoteDelegate = null;
            connectDelegateService(processName);
        }
    };
}
