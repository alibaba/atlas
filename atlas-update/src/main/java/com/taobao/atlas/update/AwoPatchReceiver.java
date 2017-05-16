package com.taobao.atlas.update;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.taobao.atlas.framework.Framework;
import android.taobao.atlas.runtime.RuntimeVariables;
import android.taobao.atlas.versionInfo.BaselineInfoManager;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;
import com.taobao.atlas.update.exception.MergeException;
import com.taobao.atlas.update.model.UpdateInfo;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.osgi.framework.BundleException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Created by guanjie on 2017/1/23.
 */

public class AwoPatchReceiver extends BroadcastReceiver{

    public static final String UPDATE_JSON = "{\"baseVersion\":\"%s\",\"updateBundles\":[{\"dependency\":[],\"isMainDex\":%s,\"name\":\"%s\",\"version\":\"%s@%s\"}],\"updateVersion\":\"%s\"}";
    public static String ATLAS_DEBUG_DIRECTORY;
    public static final String PATCH_ACTION = "com.taobao.atlas.intent.PATCH_APP";
    public static final String DEX_PATCH_ACTION = "com.taobao.atlas.intent.DEX_PATCH_APP";
    public static final String ROLLBACK_ACTION = "com.taobao.atlas.intent.ROLLBACK_PATCH";
    public static final String DEXROLLBACK_ACTION = "com.taobao.atlas.intent.ROLLBACK_DEX_PATCH";


    static{
        try {
            ATLAS_DEBUG_DIRECTORY = RuntimeVariables.androidApplication.getExternalFilesDir("atlas-debug").getAbsolutePath();
        } catch (Exception e) {
            ATLAS_DEBUG_DIRECTORY = "/sdcard/Android/data/" + RuntimeVariables.androidApplication.getPackageName() + "/files/atlas-debug";
        }
        if(!new File(ATLAS_DEBUG_DIRECTORY).exists()){
            new File(ATLAS_DEBUG_DIRECTORY).mkdirs();
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if(intent.getAction().equals(DEX_PATCH_ACTION) && intent.getBooleanExtra("dexpatch",false)){
            // dexpatch online
            /**
             *  Intent i  =  new Intent();
                i.putExtra("dexpatch",true);
                i.putExtra("patch_location","file path");
                i.putExtra("patch_info","json str");
             */
            final String tpatchLocation = intent.getStringExtra("patch_location");
            final String tpatchInfo     = intent.getStringExtra("patch_info");
            new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(Void... params) {
                    if(!TextUtils.isEmpty(tpatchLocation) && !TextUtils.isEmpty(tpatchInfo)){
                        UpdateInfo info = parseObject(tpatchInfo);
                        try {
                            AtlasUpdater.update(info,new File(tpatchLocation));
                        } catch (MergeException e) {
                            e.printStackTrace();
                        } catch (BundleException e) {
                            e.printStackTrace();
                        }
                    }
                    return null;
                }
            }.execute();

        }else {
            if (!Framework.isDeubgMode()) {
                return;
            }
            if (context.getApplicationContext().getPackageName().equals(intent.getStringExtra("pkg"))) {

                if (intent.getAction().equals(PATCH_ACTION)) {
                    Toast.makeText(context.getApplicationContext(), "动态部署安装中,请稍后...", Toast.LENGTH_LONG).show();
                    new PatchTask(intent.getAction()).execute();
                } else if (intent.getAction().equals(DEX_PATCH_ACTION)) {
                    Toast.makeText(context.getApplicationContext(), "DexPath安装中,请稍后...", Toast.LENGTH_LONG).show();
                    new PatchTask(intent.getAction()).execute();
                } else if (intent.getAction().equals(ROLLBACK_ACTION)) {
                    Toast.makeText(context.getApplicationContext(), "动态部署回滚,请稍后...", Toast.LENGTH_LONG).show();
                    new PatchTask(intent.getAction()).execute();
                } else if (intent.getAction().equals(DEXROLLBACK_ACTION)) {
                    Toast.makeText(context.getApplicationContext(), "DexPatch回滚,请稍后...", Toast.LENGTH_LONG).show();
                    new PatchTask(intent.getAction()).execute();
                }
            }
        }
    }

    public static class PatchTask extends AsyncTask<Void,Void,Void>{

        private String mPatchAction;

        public PatchTask(String action){
            mPatchAction = action;
        }
        @Override
        protected Void doInBackground(Void[] params) {
            try {
                wrapperPatchAndInstall(mPatchAction);
                Log.d("PatchReceiver", RuntimeVariables.androidApplication.toString());
                Intent intent = RuntimeVariables.androidApplication.getPackageManager().getLaunchIntentForPackage(RuntimeVariables.androidApplication.getPackageName());
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP |
                        Intent.FLAG_ACTIVITY_NEW_TASK);
                ResolveInfo info = RuntimeVariables.androidApplication.getPackageManager().resolveActivity(intent, 0);
                if (info != null) {
                    Log.d("PatchReceiver", info.activityInfo.name);
                } else {
                    Log.d("PatchReceiver", "no activity");

                }
                RuntimeVariables.androidApplication.startActivity(intent);
                kill();
                System.exit(0);
            }catch(Throwable e){
                e.printStackTrace();
            }
            return null;
        }

        private void wrapperPatchAndInstall(String action){
            if(action.equals(ROLLBACK_ACTION)){
                BaselineInfoManager.instance().rollback();
                return;
            }else if(action.equals(DEXROLLBACK_ACTION)){
                return;
            }
            File debugDirectory = new File(ATLAS_DEBUG_DIRECTORY);
            if (debugDirectory.exists()) {
                File[] bundles = debugDirectory.listFiles(new FilenameFilter() {
                    @Override
                    public boolean accept(File dir, String filename) {
                        if (filename.endsWith(".so")) {
                            return true;
                        }
                        return false;
                    }
                });
                File[] tpatchs = debugDirectory.listFiles(new FilenameFilter() {
                    @Override
                    public boolean accept(File dir, String filename) {
                        if(filename.endsWith(".tpatch")){
                            return true;
                        }
                        return false;
                    }
                });

                UpdateInfo info = null;
                File tpatchFile = null;

                if(bundles!=null && bundles.length>0){
                    // so patch
                    if(bundles.length>1){
                        for(File item : bundles){
                            item.delete();
                        }
                        new Handler(Looper.getMainLooper()).post(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(RuntimeVariables.androidApplication,"不支持同时patch多个文件，取消本次patch,请重新执行快速构建",Toast.LENGTH_LONG).show();
                            }
                        });
                        return;
                    }
                    String pkgname;
                    boolean isMainDex;
                    if (bundles[0].getName().startsWith("kernal") || bundles[0].getName().contains("com_taobao_mainDex")) {
                        //主dexbundle
                        pkgname = "com.taobao.maindex";
                        isMainDex = true;
                    } else {
                        //业务bundle
                        isMainDex = false;
                        PackageInfo pkgInfo = RuntimeVariables.androidApplication.getPackageManager().getPackageArchiveInfo(bundles[0].getAbsolutePath(), 0);
                        if (info != null) {
                            pkgname = pkgInfo.applicationInfo.packageName;
                        } else {
                            String fileName = bundles[0].getName();
                            fileName = fileName.substring(3, fileName.length() - 3);
                            pkgname = fileName.replace("_", ".");
                        }
                    }
                    String baseVersion = null;
                    try {
                        baseVersion = RuntimeVariables.androidApplication.getPackageManager().getPackageInfo(
                                RuntimeVariables.androidApplication.getPackageName(),0).versionName;
                    } catch (PackageManager.NameNotFoundException e) {
                        e.printStackTrace();
                    }
                    if(baseVersion==null){
                        Log.e("AwoPatchReceiver","get baesVersion failed");
                        bundles[0].delete();
                        return;
                    }
                    String newVersion  = baseVersion+"0.5";
                    String realJSON = String.format(UPDATE_JSON,
                            baseVersion,isMainDex,pkgname,baseVersion,newVersion,newVersion);
                    File tPatch = new File(ATLAS_DEBUG_DIRECTORY,String.format("patch-%s@%s.tpatch",newVersion,baseVersion));
                    try {
                        OutputStream os = new FileOutputStream(tPatch.getAbsolutePath());
                        ZipOutputStream zos = new ZipOutputStream(os);
                        zos.putNextEntry(new ZipEntry(isMainDex ? "libcom_taobao_maindex.so" : bundles[0].getName()));
                        FileInputStream fis = new FileInputStream(bundles[0]);
                        int j =  0;
                        byte[] buffer = new byte[512];
                        while((j = fis.read(buffer)) !=-1){
                            zos.write(buffer,0,j);
                        }
                        fis.close();
                        zos.closeEntry();
                        zos.close();
                    } catch (Throwable e) {
                        e.printStackTrace();
                        new Handler(Looper.getMainLooper()).post(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(RuntimeVariables.androidApplication,"tPatch文件合成失败，请重试",Toast.LENGTH_LONG).show();
                            }
                        });
                    }

                    bundles[0].delete();

                    //start install
                    info = parseObject(realJSON);
                    tpatchFile = tPatch;
                    if(action.equals(DEX_PATCH_ACTION)) {
                        info.dexPatch = true;
                    }
                }else if(tpatchs!=null && tpatchs.length>0){
                    // tpatch
                    if(tpatchs.length>1){
                        for(File item : tpatchs){
                            item.delete();
                        }
                        new Handler(Looper.getMainLooper()).post(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(RuntimeVariables.androidApplication,"不支持同时patch多个tpatch文件，取消本次patch,请重新执行快速构建",Toast.LENGTH_LONG).show();
                            }
                        });
                        return;
                    }
                    tpatchFile = tpatchs[0];
                    File updateInfofile = new File(tpatchs[0].getParent(),tpatchs[0].getName().substring(0,tpatchs[0].getName().length()-7)+".json");
                    String jsonStr = getFromFile(updateInfofile.getAbsolutePath());
                    info = parseObject(jsonStr);
                }


                try {
                    AtlasUpdater.update(info, tpatchFile);
                } catch (MergeException e) {
                    e.printStackTrace();
                } catch (BundleException e) {
                    e.printStackTrace();
                }
            }
        }

        private String getFromFile(String fileName){
            File file = new File(fileName);
            Long fileLength = file.length();
            byte[] filecontent = new byte[fileLength.intValue()];
            try {
                FileInputStream in = new FileInputStream(file);
                in.read(filecontent);
                in.close();
                return new String(filecontent,"UTF-8");
            } catch (Throwable e) {
                e.printStackTrace();
            }
            return null;
        }

        private void kill() {
            try {
                ActivityManager am = (ActivityManager) RuntimeVariables.androidApplication.getSystemService(Context.ACTIVITY_SERVICE);
                List<ActivityManager.RunningAppProcessInfo> a = am.getRunningAppProcesses();
                for (int i = 0; i < a.size(); i++) {
                    ActivityManager.RunningAppProcessInfo b = a.get(i);
                    if (b.processName.startsWith(RuntimeVariables.androidApplication.getPackageName()+":")) {
                        android.os.Process.killProcess(b.pid);
                        continue;
                    }
                }
            } catch (Exception e) {

            }
        }


    }

    public static UpdateInfo parseObject(String updateJsonStr){
        if(updateJsonStr==null){
            return null;
        }
        UpdateInfo info = new UpdateInfo();
        info.updateBundles =  new ArrayList<UpdateInfo.Item>();
        try {
            JSONObject json = new JSONObject(updateJsonStr);
            info.baseVersion = json.getString("baseVersion");
            info.updateVersion = json.getString("updateVersion");

            JSONArray updateBundlesArray = json.getJSONArray("updateBundles");
            for(int x=0; x<updateBundlesArray.length(); x++){
                JSONObject updateBundleInfo = updateBundlesArray.getJSONObject(x);
                UpdateInfo.Item item = new UpdateInfo.Item();
                item.isMainDex = updateBundleInfo.getBoolean("isMainDex");
                item.name = updateBundleInfo.getString("name");
                item.unitTag = updateBundleInfo.getString("unitTag");
                JSONArray array = updateBundleInfo.optJSONArray("dependency");
                if(array!=null && array.length()>0) {
                    List<String> dependencies = new ArrayList<String>();
                    for(int n=0; n<array.length(); n++){
                        dependencies.add(dependencies.get(n));
                    }
                    item.dependency = dependencies;
                }
                info.updateBundles.add(item);
            }
            return info;
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }

}
