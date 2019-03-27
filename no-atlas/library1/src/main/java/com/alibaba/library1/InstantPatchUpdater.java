package com.alibaba.library1;

import android.content.Context;
import android.content.pm.PackageManager;
import com.android.alibaba.ip.common.PatchInfo;
import com.android.alibaba.ip.common.PatchResult;
import com.android.alibaba.ip.server.InstantPatcher;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;

/**
 * 创建日期：2019/3/27 on 下午3:54
 * 描述:
 * 作者:zhayu.ll
 */
public class InstantPatchUpdater {

    private Context c;

    public InstantPatchUpdater(Context context) {
        this.c = context;
    }

    public PatchResult update() throws PackageManager.NameNotFoundException, IOException {

        if (c.getExternalFilesDir("instantpatch") == null || !c.getExternalFilesDir("instantpatch").exists()){
            return null;
        }

        if (c.getExternalFilesDir("instantpatch").listFiles() == null){
            return null;
        }

        String patchFile = c.getExternalFilesDir("instantpatch").list(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith(".ipatch");
            }
        })[0];

        if (patchFile == null || !new File(patchFile).exists()) {
            PatchInfo patchInfo = new PatchInfo();
            patchInfo.baseVersion = c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionName;
            patchInfo.patchVersion = 10;
            patchInfo.priority = 0;
            PatchResult pr = InstantPatcher.create(c).handlePatches(patchFile, patchInfo);

            return pr;

        }
        return null;
    }
}
