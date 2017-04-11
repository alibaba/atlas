package android.taobao.atlas.runtime.newcomponent.service;

import android.app.IActivityManager;
import android.app.IServiceConnection;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ProviderInfo;
import android.content.pm.ServiceInfo;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.RemoteException;
import android.taobao.atlas.hack.AndroidHack;
import android.taobao.atlas.hack.AtlasHacks;
import android.taobao.atlas.runtime.ContextImplHook;
import android.taobao.atlas.runtime.RuntimeVariables;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Created by guanjie on 2017/4/2.
 */

public class AdditionActivityManagerService extends Service{

    private ServiceDispatcherImpl dispatcher = new ServiceDispatcherImpl();

    @Override
    public IBinder onBind(Intent intent) {
        return dispatcher;
    }

    public static class ServiceDispatcherImpl extends IDelegateBinder.Stub{

        private HashMap<AdditionalServiceRecord,Service> mActivateServices = new HashMap<>();

        @Override
        public IBinder startService(Intent serviceIntent,ServiceInfo info) throws RemoteException {
            AdditionalServiceRecord record = retriveServiceByComponent(serviceIntent.getComponent());
            if(record == null) {
                //create new service
                record = handleCreateService(serviceIntent.getComponent());
            }
            record.calledStart = true;
            if(record!=null) {
                handleServiceArgs(serviceIntent, record);
            }
            return record;
        }

        @Override
        public IBinder bindService(Intent serviceIntent,IBinder activityToken,IServiceConnection conn) throws RemoteException {
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
                conn.connected(record.component,binder);
            }
            return record;
        }

        @Override
        public boolean unbindService(IServiceConnection conn) throws RemoteException {
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
        public IActivityManager.ContentProviderHolder getContentProvider(ProviderInfo cpi) throws RemoteException {
            try {
                Object activityThread = AndroidHack.getActivityThread();
                Method installProvider = activityThread.getClass().getDeclaredMethod("installProvider",
                        Context.class,IActivityManager.ContentProviderHolder.class,ProviderInfo.class,boolean.class,boolean.class,boolean.class);
                return (IActivityManager.ContentProviderHolder)installProvider.invoke(activityThread,RuntimeVariables.androidApplication,null,cpi,false,true,true);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }

        private AdditionalServiceRecord handleCreateService(ComponentName componentName){
            try {
                Class serviceClazz = RuntimeVariables.delegateClassLoader.loadClass(componentName.getClassName());
                Service service = (Service)serviceClazz.newInstance();

                Object contextImpl = null;
                Object activityThread = AndroidHack.getActivityThread();
                Object loadedApk =  AndroidHack.getLoadedApk(RuntimeVariables.androidApplication,activityThread,RuntimeVariables.androidApplication.getPackageName());

                if(Build.VERSION.SDK_INT>=21) {
                    contextImpl = AtlasHacks.ContextImpl_createAppContext.invoke(AtlasHacks.ContextImpl.getmClass(),activityThread,loadedApk);
                }else{
                    contextImpl = AtlasHacks.ContextImpl.getmClass().newInstance();
                    AtlasHacks.ContextImpl_init.invoke(contextImpl, loadedApk,null,activityThread);
                }
                Object gDefault = null;
                if(Build.VERSION.SDK_INT<25) {
                    AtlasHacks.ActivityManagerNative_gDefault.get(AtlasHacks.ActivityManagerNative.getmClass());
                }else{
                    AtlasHacks.ActivityManagerNative_getDefault.invoke(AtlasHacks.ActivityManagerNative.getmClass());
                }
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
