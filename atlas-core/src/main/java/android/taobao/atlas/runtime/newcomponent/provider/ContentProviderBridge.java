package android.taobao.atlas.runtime.newcomponent.provider;

import android.app.IActivityManager;
import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.taobao.atlas.hack.AndroidHack;
import android.taobao.atlas.hack.AtlasHacks;
import android.taobao.atlas.runtime.RuntimeVariables;
import android.taobao.atlas.runtime.newcomponent.BridgeUtil;

import java.util.HashMap;

/**
 * Created by guanjie on 2017/4/10.
 */

public class ContentProviderBridge extends ContentProvider{

    public static final String METHOD_INSTALLPROVIDER = "atlas_install_provider";
    public static final String PROVIDER_INFO_KEY = "info";
    public static final String PROVIDER_HOLDER_KEY = "holder";
//    private String processName;
    private HashMap<String,IActivityManager.ContentProviderHolder> providerRecord = new HashMap<>();

    public static IActivityManager.ContentProviderHolder getContentProvider(ProviderInfo info){
        String targetProcessName = info.processName!=null ? info.processName : RuntimeVariables.androidApplication.getPackageName();
        String currentProcessName = RuntimeVariables.getProcessName(RuntimeVariables.androidApplication);
        if(info.multiprocess || targetProcessName.equals(currentProcessName)){
            return transactProviderInstall(currentProcessName,info);
        }else{
            //remote contentprovider
            return transactProviderInstall(info.processName,info);
        }
    }

    public static void completeRemoveProvider(){

    }

    public static void removeContentProvider(){
    }

    private static Uri accquireRemoteBridgeToken(String processName){
        return Uri.parse("content://"+ BridgeUtil.getBridgeComponent(BridgeUtil.TYPE_PROVIDERBRIDGE,processName));
    }

    private static IActivityManager.ContentProviderHolder transactProviderInstall(String processName,ProviderInfo info){
        Bundle extras = new Bundle();
        extras.putParcelable(PROVIDER_INFO_KEY,info);
        ContentResolver contentResolver = RuntimeVariables.androidApplication.getContentResolver();
        Bundle bundle = contentResolver.call(accquireRemoteBridgeToken(processName),METHOD_INSTALLPROVIDER,null,extras);
        IActivityManager.ContentProviderHolder holder = bundle.getParcelable(PROVIDER_HOLDER_KEY);
        return holder;
    }

    @Override
    public boolean onCreate() {
        return false;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if(method.equals(METHOD_INSTALLPROVIDER)){
            ProviderInfo info = extras.getParcelable(PROVIDER_INFO_KEY);
            if(info==null){
                return null;
            }
            IActivityManager.ContentProviderHolder holder = providerRecord.get(info.authority);
            if(holder==null){
                //install contentprovider
                IActivityManager.ContentProviderHolder origin = (IActivityManager.ContentProviderHolder)AtlasHacks.ContentProviderHolder_constructor.getInstance();
                try {
                    Object activityThread = AndroidHack.getActivityThread();
                    if(activityThread!=null) {
                        IActivityManager.ContentProviderHolder newHolder = (IActivityManager.ContentProviderHolder)AtlasHacks.ActivityThread_installProvider.invoke(
                                activityThread,RuntimeVariables.androidApplication,origin,info,false,true,true);
                        holder = newHolder;
                        providerRecord.put(info.authority,holder);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    return null;
                }
            }
            Bundle result = new Bundle();
            result.putParcelable(PROVIDER_HOLDER_KEY,holder);
            return result;
        }
        return super.call(method, arg, extras);

    }

}
