package com.taobao.atlas.update;

import java.io.File;
import java.io.IOException;
import java.util.List;

import android.content.Context;
import android.taobao.atlas.runtime.RuntimeVariables;
import android.text.TextUtils;
import android.util.Log;
import com.taobao.atlas.dexmerge.MergeCallback;
import com.taobao.atlas.update.exception.MergeException;
import com.taobao.atlas.update.model.UpdateInfo;
import com.taobao.atlas.update.model.UpdateInfo.Item;
import com.taobao.atlas.update.util.PatchCleaner;
import com.taobao.atlas.update.util.PatchInstaller;
import com.taobao.atlas.update.util.PatchMerger;
import org.osgi.framework.BundleException;

/**
 * Created by wuzhong on 2016/11/23.
 */

public class AtlasUpdater {

    /**
     * 更新主入口
     *
     * @param updateInfo 更新的基础信息
     * @param patchFile  tpatch包
     * @throws MergeException
     * @throws BundleException
     */
    public static void update(UpdateInfo updateInfo, File patchFile) throws MergeException, BundleException {

        if (null == updateInfo || updateInfo.dexPatch) {
            //with dexPatch,please call "dexPatchUpdate"
            return;
        }

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

    public static void dexpatchUpdate(Context context, UpdateInfo updateInfo, File patchFile,
                                      final IDexpatchMonitor coldMonitor, boolean enableHot,
                                      IDexpatchMonitor hotMonitor)
        throws Exception {

        if (null == updateInfo || !updateInfo.dexPatch) {
            return;
        }
        String versionName = null;
        try {
            Context c = RuntimeVariables.androidApplication;
            versionName = c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (TextUtils.isEmpty(versionName) || !versionName.equals(updateInfo.baseVersion)) {
            return;
        }

        if (enableHot) {
            List<UpdateInfo.Item> needPatchHotBundles = DexPatchUpdater.filterNeedHotPatchList(
                UpdateBundleDivider.dividePatchInfo(updateInfo.updateBundles, Item.PATCH_DEX_HOT)
            );
            DexPatchUpdater.installHotPatch(updateInfo.updateVersion, needPatchHotBundles, patchFile, hotMonitor);
        }
        updateInfo.updateBundles = DexPatchUpdater.filterNeedColdPatchList(
            UpdateBundleDivider.dividePatchInfo(updateInfo.updateBundles, Item.PATCH_DEX_COLD)
        );
        DexPatchUpdater.installColdPatch(updateInfo, patchFile, coldMonitor);
    }

    public static void dexpatchUpdate(Context context, UpdateInfo updateInfo, File patchFile,
                                      final IDexpatchMonitor monitor) throws Exception {
        dexpatchUpdate(context, updateInfo, patchFile, monitor, false, null);
    }

    public interface IDexpatchMonitor {
        public void merge(boolean success, String bundleName, long version, String errMsg);

        public void install(boolean success, String bundleName, long version, String errMsg);
    }

}
