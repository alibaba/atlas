package android.taobao.atlas.runtime.newcomponent.service;

import android.app.IServiceConnection;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;
import android.taobao.atlas.runtime.RuntimeVariables;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

/**
 * Created by guanjie on 2017/4/5.
 */

public class ServiceBridge {

    private static volatile ServiceBridge sInstance;
    private IDelegateBinder mRemoteDelegate;
    private CountDownLatch mCountDownLatch;
    private String processName;
    private Intent targetIntent;

    HashMap<Intent,IServiceConnection> mActiveServiceInfo = new HashMap<>();

    private ServiceBridge(String processname) {
        this.processName = processname;
        connectBinderPoolService(processname);
    }

    public static ServiceBridge getInstance(String processName) {     // 2
        if (sInstance == null) {
            synchronized (ServiceBridge.class) {
                if (sInstance == null) {
                    sInstance = new ServiceBridge(processName);
                }
            }
        }
        return sInstance;
    }

    private synchronized void connectBinderPoolService(String processName) {
        mCountDownLatch = new CountDownLatch(1);
        if(targetIntent==null){
            targetIntent = createDelegateServiceIntent(processName);
        }
        RuntimeVariables.androidApplication.bindService(targetIntent, mBinderPoolConnection,
                Context.BIND_AUTO_CREATE);
        try {
            mCountDownLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private Intent createDelegateServiceIntent(String processName){
        String prefix = RuntimeVariables.androidApplication+":";
        String serviceTag = null;
        if(processName.startsWith(prefix)){
            serviceTag = processName.substring(prefix.length(),processName.length());
        }else{
            serviceTag = processName.replace(".","_");
        }
        String targetServiceName = String.format("android.taobao.atlas.runtime.newcomponent.service.AtlasDelegateService_For_%s",serviceTag);
        Intent service = new Intent();
        service.setClassName(RuntimeVariables.androidApplication,targetServiceName);
        return service;
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

    private ServiceConnection mBinderPoolConnection = new ServiceConnection() {   // 5

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

    private IBinder.DeathRecipient mBinderPoolDeathRecipient = new IBinder.DeathRecipient() {    // 6
        @Override
        public void binderDied() {
            mRemoteDelegate.asBinder().unlinkToDeath(mBinderPoolDeathRecipient, 0);
            mRemoteDelegate = null;
            connectBinderPoolService(processName);
        }
    };
}
