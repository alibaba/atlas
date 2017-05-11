package com.taobao.atlas.update;

import android.taobao.atlas.versionInfo.BaselineInfoManager;
import android.util.Log;
import android.util.Pair;

import com.taobao.atlas.dexmerge.MergeCallback;
import com.taobao.atlas.update.exception.MergeException;
import com.taobao.atlas.update.model.UpdateInfo;
import com.taobao.atlas.update.util.PatchCleaner;
import com.taobao.atlas.update.util.PatchInstaller;
import com.taobao.atlas.update.util.PatchMerger;

import org.osgi.framework.BundleException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

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

        PatchCleaner.clearUpdatePath(updateInfo.workDir.getAbsolutePath());


    }


    public static void dexpatchUpdate(UpdateInfo updateInfo, File patchFile, final IDexpatchMonitor monitor){

        PatchMerger patchMerger = null;
        try {
            patchMerger = new PatchMerger(updateInfo, patchFile, null);
            patchMerger.merge();
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (patchMerger == null) {
            return;
        } else {
            List<UpdateInfo.Item> result = new ArrayList<>();
            for (int i = 0; i < updateInfo.updateBundles.size(); i++) {
                UpdateInfo.Item item = updateInfo.updateBundles.get(i);
                if (patchMerger.mergeOutputs.containsKey(item.name)) {//对于merge成功的bundle列表进行更新
                    Pair<String, UpdateInfo.Item> pair = patchMerger.mergeOutputs.get(item.name);
                    if (new File(pair.first).exists()) {
                        result.add(item);
                        monitor.merge(true, item.name, item.dexPatchVersion, "");
                    } else {
                        if (monitor != null) {
                            monitor.merge(false, item.name, item.dexPatchVersion, "");
                        }
                    }
                }
            }
            updateInfo.updateBundles = result;
        }


        try {
            PatchInstaller patchInstaller = new PatchInstaller(patchMerger.mergeOutputs, updateInfo);
            patchInstaller.install();
        } catch (BundleException e) {
            e.printStackTrace();
        }

        ConcurrentHashMap<String, Long> installList = BaselineInfoManager.instance().getDexPatchBundles();
        if (installList == null || installList.size() == 0) {
            return;
        } else {
            for (int j = 0; j < updateInfo.updateBundles.size(); j++) {
                UpdateInfo.Item item = updateInfo.updateBundles.get(j);
                if (installList.containsKey(item.name)) {
                    if (item.dexPatchVersion == installList.get(item.name)) {
                        if (monitor != null) monitor.install(true, item.name, item.dexPatchVersion, "");
                    } else {
                        if (monitor != null) monitor.install(false, item.name, item.dexPatchVersion, "");
                    }
                } else {
                    if (monitor != null) monitor.install(false, item.name, item.dexPatchVersion, "");
                }
            }
        }

        PatchCleaner.clearUpdatePath(updateInfo.workDir.getAbsolutePath());
    }

    public interface IDexpatchMonitor {
        public void merge(boolean success, String bundleName, long version, String errMsg);
        public void install(boolean success, String bundleName, long version, String errMsg);
    }

}
