package com.taobao.atlas.update;

import android.content.Context;
import android.taobao.atlas.versionInfo.BaselineInfoManager;
import android.text.TextUtils;
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
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by wuzhong on 2016/11/23.
 */

public class AtlasUpdater {

    private static boolean usePatchDivider = true;

    /**
     * 更新主入口
     * @param updateInfo  更新的基础信息
     * @param patchFile   tpatch包
     * @throws MergeException
     * @throws BundleException
     */
    public static void update(UpdateInfo updateInfo, File patchFile) throws MergeException, BundleException {

        if (null == updateInfo || updateInfo.dexPatch){
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

    public static void dividePatch(boolean divide) {
        usePatchDivider = divide;
    }


    public static void dexpatchUpdate(Context context, UpdateInfo updateInfo, File patchFile, final IDexpatchMonitor monitor) throws Exception {

        if (null == updateInfo || !updateInfo.dexPatch){
            return;
        }
        //检查版本，只有versionName相等
        //并所有bundle的dexpatchVersion都大于对应bundle的dexpatchbundle时，才会执行dexpatch操作
        String versionName = null;
        try {
            versionName = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (TextUtils.isEmpty(versionName) || !versionName.equals(updateInfo.baseVersion)) {
            return;
        }
        if (usePatchDivider) {
            updateInfo.updateBundles = DexPatchDivider.getColdPatchList(updateInfo.updateBundles);
        }

        Iterator<UpdateInfo.Item> itemIterator = updateInfo.updateBundles.iterator();
        while (itemIterator.hasNext()) {
            UpdateInfo.Item item = itemIterator.next();
            long bundleVersion = BaselineInfoManager.instance().getDexPatchBundleVersion(item.name);
            if (item.dexpatchVersion < bundleVersion || (item.dexpatchVersion == bundleVersion && !item.reset)) {
                itemIterator.remove();
            }
        }
        if (updateInfo.updateBundles.isEmpty()){
            return;
        }

        //开始merge
        PatchMerger patchMerger = null;
        try {
            patchMerger = new PatchMerger(updateInfo, patchFile, null);
            patchMerger.merge();

        if (patchMerger == null) {
            return;
        } else {
            List<UpdateInfo.Item> result = new ArrayList<>();
            for (int i = 0; i < updateInfo.updateBundles.size(); i++) {
                UpdateInfo.Item item = updateInfo.updateBundles.get(i);
                if (patchMerger.mergeOutputs.containsKey(item.name)) {//对于merge成功的bundle列表进行更新
                    Pair<String, UpdateInfo.Item> pair = patchMerger.mergeOutputs.get(item.name);
                    boolean succeed = new File(pair.first).exists();
                    if (succeed) {
                        result.add(item);
                    }
                    if (monitor != null) {
                        monitor.merge(succeed, item.name, item.dexpatchVersion, "");
                    }
                }
            }
            updateInfo.updateBundles = result;
        }

            PatchInstaller patchInstaller = new PatchInstaller(patchMerger.mergeOutputs, updateInfo);
            patchInstaller.install();
        } catch (Exception e) {
                throw e;
        }

        ConcurrentHashMap<String, Long> installList = BaselineInfoManager.instance().getDexPatchBundles();
        if (installList == null || installList.size() == 0) {
            return;
        }

        if (null != monitor){
            for (UpdateInfo.Item item : updateInfo.updateBundles){
                boolean succeed = installList.containsKey(item.name) && item.dexpatchVersion == installList.get(item.name);
                monitor.install(succeed,item.name, item.dexpatchVersion, "");
            }
        }

        PatchCleaner.clearUpdatePath(updateInfo.workDir.getAbsolutePath());
    }

    public interface IDexpatchMonitor {
        public void merge(boolean success, String bundleName, long version, String errMsg);
        public void install(boolean success, String bundleName, long version, String errMsg);
    }

}
