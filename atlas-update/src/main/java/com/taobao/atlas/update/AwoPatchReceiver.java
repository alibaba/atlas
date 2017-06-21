package com.taobao.atlas.update;

import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.List;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.ResolveInfo;
import android.taobao.atlas.framework.Framework;
import android.taobao.atlas.runtime.RuntimeVariables;
import android.taobao.atlas.util.ApkUtils;
import android.util.Log;
import android.widget.Toast;

/**
 * Created by guanjie on 2017/1/23.
 */

public class AwoPatchReceiver extends BroadcastReceiver{

    public static String ATLAS_DEBUG_DIRECTORY;
    public static final String PATCH_ACTION = "com.taobao.atlas.intent.PATCH_APP";
    public static final String ROLLBACK_ACTION = "com.taobao.atlas.intent.ROLLBACK_PATCH";

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
        if (!Framework.isDeubgMode()) {
            return;
        }
        if (context.getApplicationContext().getPackageName().equals(intent.getStringExtra("pkg"))) {
            if (intent.getAction().equals(PATCH_ACTION)) {
                Toast.makeText(context.getApplicationContext(), "DebugPatch安装中,请稍后...", Toast.LENGTH_LONG).show();
                doPatch();
                restart();
            }else if (intent.getAction().equals(ROLLBACK_ACTION)) {
                Toast.makeText(context.getApplicationContext(), "动态部署回滚,请稍后...", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void doPatch(){
        try {
            File debugDirectory = new File(ATLAS_DEBUG_DIRECTORY);
            File[] bundles = debugDirectory.listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String filename) {
                    if (filename.endsWith(".so")) {
                        return true;
                    }
                    return false;
                }
            });
            if(bundles!=null && bundles.length>0){
                for(int x=0;x<bundles.length;x++){
                    PackageInfo pkgInfo = RuntimeVariables.androidApplication.getPackageManager().getPackageArchiveInfo(bundles[0].getAbsolutePath(), 0);
                    String packageName = pkgInfo.packageName;
                    File debug_storage_bundle_dir = new File(RuntimeVariables.androidApplication.getExternalFilesDir("debug_storage"),packageName);
                    if(!debug_storage_bundle_dir.exists()){
                        debug_storage_bundle_dir.mkdirs();
                    }
                    File targetPatchZip = new File(debug_storage_bundle_dir,"patch.zip");
                    if(targetPatchZip.exists()){
                        targetPatchZip.delete();
                    }
                    bundles[0].renameTo(targetPatchZip);
                    if(!targetPatchZip.exists()){
                        ApkUtils.copyInputStreamToFile(new FileInputStream(bundles[0]),targetPatchZip);
                    }
                    if(!targetPatchZip.exists()){
                        throw new IOException("move "+bundles[0]+"failed");
                    }
                }
            }
        }catch(Throwable e){
            e.printStackTrace();
        }
    }

    private void restart() {
        Intent
            intent = RuntimeVariables.androidApplication.getPackageManager().getLaunchIntentForPackage(RuntimeVariables.androidApplication.getPackageName());
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP |
                Intent.FLAG_ACTIVITY_NEW_TASK);
        ResolveInfo info = RuntimeVariables.androidApplication.getPackageManager().resolveActivity(intent, 0);
        if (info != null) {
            Log.d("PatchReceiver", info.activityInfo.name);
        } else {
            Log.d("PatchReceiver", "no activity");

        }
        // RuntimeVariables.androidApplication.startActivity(intent);
        kill();
        android.os.Process.killProcess(android.os.Process.myPid());
        // System.exit(0);
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
