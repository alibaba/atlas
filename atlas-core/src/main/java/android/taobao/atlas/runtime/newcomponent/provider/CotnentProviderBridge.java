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
import android.taobao.atlas.runtime.newcomponent.BundleCompat;

import java.util.HashMap;

/**
 * Created by guanjie on 2017/4/10.
 */

public class CotnentProviderBridge extends ContentProvider{

    public static final String METHOD_INSTALLPROVIDER = "atlas_install_provider";
    public static final String PROVIDER_INFO_KEY = "info";
    public static final String PROVIDER_HOLDER_KEY = "holder";

    public static String AUTHORITY_TEMPLATE = "android.taobao.atlas.BridgeProvider_%s";
    private String processName;
    private HashMap<String,IActivityManager.ContentProviderHolder> providerRecord = new HashMap<>();

    public static IActivityManager.ContentProviderHolder getContentProvider(ProviderInfo info,boolean stable){
        String targetProcessName = info.processName!=null ? info.processName : RuntimeVariables.androidApplication.getPackageName();
        String currentProcessName = RuntimeVariables.getProcessName(RuntimeVariables.androidApplication);
        if(info.multiprocess || targetProcessName.equals(currentProcessName)){
            Object contentProviderHolder = AtlasHacks.ContentProviderHolder_constructor.getInstance(info);
            return (IActivityManager.ContentProviderHolder)contentProviderHolder;
        }else{
            //remote contentprovider

            return null;
        }
    }

    public static Uri accquireRemoteBridgeToken(String processName){
        return Uri.parse("content://"+String.format(AUTHORITY_TEMPLATE,processName));
    }

    public static Bundle transactProviderInstall(String processName,ProviderInfo info){
        Bundle extras = new Bundle();
        extras.putParcelable(INSTALLPROVIDER_INFO_KEY,info);
        ContentResolver contentResolver = RuntimeVariables.androidApplication.getContentResolver();
        Bundle bundle = contentResolver.call(accquireRemoteBridgeToken(processName),METHOD_INSTALLPROVIDER,null,extras);
        sdfsdfsd
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
            IActivityManager.ContentProviderHolder existingHolder = providerRecord.get(info.authority);
            if(existingHolder!=null){
                Bundle result = new Bundle();
                result.putParcelable(PROVIDER_HOLDER_KEY,existingHolder);
                return result;
            }else{
                //install contentprovider
                IActivityManager.ContentProviderHolder holder = (IActivityManager.ContentProviderHolder)AtlasHacks.ContentProviderHolder_constructor.getInstance(null);
                try {
                    Object activityThread = AndroidHack.getActivityThread();
                    if(activityThread!=null) {
                        IActivityManager.ContentProviderHolder newHolder = (IActivityManager.ContentProviderHolder)AtlasHacks.ActivityThread_installProvider.invoke(
                                activityThread,RuntimeVariables.androidApplication,holder,info,false,true,true);
                        Bundle result = new Bundle();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    return null;
                }

            }
        }
        return super.call(method, arg, extras);

    }

}
