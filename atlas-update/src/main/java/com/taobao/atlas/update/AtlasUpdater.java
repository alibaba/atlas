package com.taobao.atlas.update;

import android.util.Log;

import com.taobao.atlas.dexmerge.MergeCallback;
import com.taobao.atlas.update.exception.MergeException;
import com.taobao.atlas.update.model.UpdateInfo;
import com.taobao.atlas.update.util.PatchCleaner;
import com.taobao.atlas.update.util.PatchInstaller;
import com.taobao.atlas.update.util.PatchMerger;

import org.osgi.framework.BundleException;

import java.io.File;
import java.io.IOException;

/**
 * Created by wuzhong on 2016/11/23.
 */

public class AtlasUpdater {

    /**
     * 更新主入口
     * @param updateInfo  更新的基础信息
     * @param patchFile   tpatch包
     * @throws MergeException
     * @throws BundleException
     */
    public static void update(UpdateInfo updateInfo, File patchFile) throws MergeException, BundleException {

        MergeCallback mergeCallback = new MergeCallback() {
            @Override
            public void onMergeResult(boolean result, String bundleName) {
                if (result) {
                    Log.d("[dexmerge]", "merge bundle " + bundleName + " success ");
                } else {
                    Log.e("[dexmerge]", "merge bundle " + bundleName + " fail ");
                }
            }
        };


        PatchMerger patchMerger = new PatchMerger(updateInfo, patchFile, mergeCallback);

        try {
            patchMerger.merge();
        } catch (IOException e) {
            e.printStackTrace();
        }

        PatchInstaller patchInstaller = new PatchInstaller(patchMerger.mergeOutputs, updateInfo);

        patchInstaller.install();

        new PatchCleaner().clearUpdatePath(updateInfo.workDir.getAbsolutePath());


    }


}
