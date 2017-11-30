package android.taobao.atlas.runtime.newcomponent.service;

import android.app.IServiceConnection;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ServiceInfo;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.taobao.atlas.hack.AndroidHack;
import android.taobao.atlas.hack.AtlasHacks;
import android.taobao.atlas.hack.Hack;
import android.taobao.atlas.runtime.ContextImplHook;
import android.taobao.atlas.runtime.RuntimeVariables;
import android.taobao.atlas.runtime.newcomponent.activity.ActivityBridge;
import android.taobao.atlas.runtime.newcomponent.receiver.ReceiverBridge;
import android.util.Log;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Created by guanjie on 2017/4/2.
 */

public class BaseDelegateService extends Service{

    private ServiceDispatcherImpl dispatcher = new ServiceDispatcherImpl();

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return dispatcher;
    }

    public static class ServiceDispatcherImpl extends IDelegateBinder.Stub{

        private Handler mMainHandler;
        private HashMap<AdditionalServiceRecord,Service> mActivateServices = new HashMap<>();

        public ServiceDispatcherImpl(){
            mMainHandler = new Handler(Looper.getMainLooper());
        }

        @Override
        public IBinder startService(final Intent serviceIntent, ServiceInfo info) throws RemoteException {
            Log.e("BaseDelegateService","startService");
            mMainHandler.post(new Runnable() {
                @Override
                public void run() {
                    AdditionalServiceRecord record = retriveServiceByComponent(serviceIntent.getComponent());
                    if(record == null) {
                        //create new service
                        record = handleCreateService(serviceIntent.getComponent());
                    }
                    record.calledStart = true;
                    if(record!=null) {
                        handleServiceArgs(serviceIntent, record);
                    }
                }
            });
            return null;
        }

        @Override
        public IBinder bindService(final Intent serviceIntent, IBinder activityToken, final IServiceConnection conn) throws RemoteException {
            Log.e("BaseDelegateService","bindService");
            mMainHandler.post(new Runnable() {
                @Override
                public void run() {
                    AdditionalServiceRecord record = retriveServiceByComponent(serviceIntent.getComponent());
                    if(record == null) {
                        //create new service
                        record = handleCreateService(serviceIntent.getComponent());
                    }
                    if(record !=null ){
                        IBinder binder = mActivateServices.get(record).onBind(serviceIntent);
                        if(!record.activeConnections.contains(conn)){
                            record.activeConnections.add(conn);
                        }
                        try {
                            conn.connected(record.component,binder);
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                    }
                }
            });
            return null;
        }

        @Override
        public boolean unbindService(IServiceConnection conn) throws RemoteException {
            Log.e("BaseDelegateService","unbindService");
            Iterator iter = mActivateServices.entrySet().iterator();
            AdditionalServiceRecord record = null;
            while (iter.hasNext()) {
                Map.Entry<AdditionalServiceRecord,Service> entry = (Map.Entry<AdditionalServiceRecord,Service>) iter.next();
                AdditionalServiceRecord tmpRecord = entry.getKey();
                if(tmpRecord.activeConnections.contains(conn)){
                    //service has created
                    record = tmpRecord;
                    break;
                }
            }
            if(record!=null){
                record.activeConnections.remove(conn);
                if(record.activeConnections.size()==0){
                    if(!record.calledStart || (record.calledStart && record.delayStop)){
                        //service can stop
                        Service service = mActivateServices.remove(record);
                        service.onDestroy();
                        return true;
                    }
                }
            }
            return false;
        }

        @Override
        public int stopService(Intent serviceIntent) throws RemoteException {
            Log.e("BaseDelegateService","stopService");
            AdditionalServiceRecord record = retriveServiceByComponent(serviceIntent.getComponent());
            if(record!=null){
                if(record.activeConnections.size()>0){
                    record.delayStop = true;
                }else{
                    mActivateServices.get(record).onDestroy();
                }
            }
            return 0;
        }

        @Override
        public Intent handleActivityStack(Intent intent,ActivityInfo info) throws RemoteException {
            ActivityBridge.handleActivityStack(info,intent);
            return intent;
        }

        @Override
        public void handleReceiver(final Intent intent, final ActivityInfo info) throws RemoteException {
            mMainHandler.post(new Runnable() {
                @Override
                public void run() {
                    ReceiverBridge.postOnReceived(intent,info);
                }
            });
        }

        private AdditionalServiceRecord handleCreateService(ComponentName componentName){
            try {
                Class serviceClazz = RuntimeVariables.delegateClassLoader.loadClass(componentName.getClassName());
                Service service = (Service)serviceClazz.newInstance();

                Object contextImpl = null;
                Object activityThread = AndroidHack.getActivityThread();
                Object loadedApk =  AndroidHack.getLoadedApk(RuntimeVariables.androidApplication,activityThread,RuntimeVariables.androidApplication.getPackageName());
                try {
                    Hack.HackedMethod ContextImpl_createAppContext = AtlasHacks.ContextImpl.method("createAppContext", AtlasHacks.ActivityThread.getmClass(), AtlasHacks.LoadedApk.getmClass());
                    if (ContextImpl_createAppContext.getMethod() != null) {
                        contextImpl = ContextImpl_createAppContext.invoke(AtlasHacks.ContextImpl.getmClass(), activityThread, loadedApk);
                    }
                }catch(Throwable e){
                    Hack.HackedMethod ContextImpl_init = AtlasHacks.ContextImpl.method("init",AtlasHacks.LoadedApk.getmClass(), IBinder.class,AtlasHacks.ActivityThread.getmClass());
                    Constructor cons = AtlasHacks.ContextImpl.getmClass().getDeclaredConstructor();
                    cons.setAccessible(true);
                    contextImpl = cons.newInstance();
                    ContextImpl_init.invoke(contextImpl, loadedApk,null,activityThread);
                }

                Object gDefault = null;
                if(Build.VERSION.SDK_INT>25 || (Build.VERSION.SDK_INT==25&&Build.VERSION.PREVIEW_SDK_INT>0)){
                    gDefault=AtlasHacks.ActivityManager_IActivityManagerSingleton.get(AtlasHacks.ActivityManager.getmClass());
                }else{
                    gDefault=AtlasHacks.ActivityManagerNative_gDefault.get(AtlasHacks.ActivityManagerNative.getmClass());
                }
                gDefault = AtlasHacks.Singleton_mInstance.get(gDefault);
                AtlasHacks.ContextImpl_setOuterContext.invoke(contextImpl,service);
                ContextImplHook hook = new ContextImplHook((Context) contextImpl,serviceClazz.getClassLoader());

                //create binder
                AdditionalServiceRecord record = new AdditionalServiceRecord(componentName);
                AtlasHacks.Service_attach.invoke(service,hook,activityThread,serviceClazz.getName(),
                        record,RuntimeVariables.androidApplication, gDefault);
                service.onCreate();
                mActivateServices.put(record,service);
                return record;
            } catch (Throwable e) {
                e.printStackTrace();
                return null;
            }
        }

        private void handleServiceArgs(Intent serviceIntent,IBinder token){
            Service service = mActivateServices.get(token);
            if(service!=null){
                if(serviceIntent!=null) {
                    serviceIntent.setExtrasClassLoader(service.getClassLoader());
                }
                service.onStartCommand(serviceIntent,Service.START_FLAG_RETRY,0);
            }
        }

        private AdditionalServiceRecord retriveServiceByComponent(ComponentName component){
            Iterator iter = mActivateServices.entrySet().iterator();
            while (iter.hasNext()) {
                Map.Entry<AdditionalServiceRecord,Service> entry = (Map.Entry<AdditionalServiceRecord,Service>) iter.next();
                AdditionalServiceRecord tmpRecord = entry.getKey();
                if(tmpRecord.component.equals(component)){
                    //service has created
                    return tmpRecord;
                }
            }
            return null;
        }

    }


    public static class AdditionalServiceRecord extends Binder {

        final ComponentName component;
        public final ArrayList<IServiceConnection> activeConnections = new ArrayList<>();
        public boolean delayStop = false;
        public boolean calledStart = false;

        public AdditionalServiceRecord(ComponentName componentName){
            this.component = componentName;
        }
    }
}
