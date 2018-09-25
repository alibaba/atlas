package com.taobao.atlas.update;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipFile;

import android.content.Context;
import android.taobao.atlas.patch.AtlasHotPatchManager;
import android.taobao.atlas.runtime.RuntimeVariables;
import android.taobao.atlas.util.IOUtil;
import android.taobao.atlas.versionInfo.BaselineInfoManager;
import android.text.TextUtils;
import android.util.Pair;
import com.taobao.atlas.update.AtlasUpdater.IDexpatchMonitor;
import com.taobao.atlas.update.model.UpdateInfo;
import com.taobao.atlas.update.model.UpdateInfo.Item;
import com.taobao.atlas.update.util.PatchCleaner;
import com.taobao.atlas.update.util.PatchInstaller;
import com.taobao.atlas.update.util.PatchMerger;

/**
 * Created by zhongcang on 2017/11/7.
 * .
 */

public class DexPatchUpdater {

    public static void installHotPatch(String updateVersion,List<UpdateInfo.Item> updateList, File patchFile,IDexpatchMonitor monitor) throws Exception{
        if (null == updateList || updateList.isEmpty()){
            return;
        }
        ZipFile patchZip = null;
        try {
            patchZip = new ZipFile(patchFile);
            HashMap<String, Pair<Long, InputStream>> updateBundles = new HashMap<>(updateList.size());
            for (UpdateInfo.Item bundle : updateList) {
                String entryName = "com.taobao.maindex".equals(bundle.name)
                    ? "hot.dex"
                    : String.format("%s%s/hot.dex", "lib", bundle.name.replace(".", "_"));
                InputStream entryIn = patchZip.getInputStream(patchZip.getEntry(entryName));
                updateBundles.put(bundle.name, new Pair<>(bundle.dexpatchVersion, entryIn));
            }
            AtlasHotPatchManager.getInstance().installHotFixPatch(updateVersion, updateBundles);
        } catch (IOException e) {
            e.printStackTrace();
            throw e;
        } finally {
            IOUtil.quietClose(patchZip);
        }
        if (null != monitor) {
            Map<String, Long> hotPatchInstall = AtlasHotPatchManager.getInstance().getAllInstallPatch();
            if (null == hotPatchInstall) {
                hotPatchInstall = new HashMap<>(1);
            }
            for (UpdateInfo.Item item : updateList) {
                boolean success = hotPatchInstall.containsKey(item.name);
                monitor.install(success, item.name, item.dexpatchVersion, "");
            }
        }
    }

    public static void installColdPatch(UpdateInfo updateInfo, File patchFile,IDexpatchMonitor monitor) throws Exception{
        if (null == updateInfo.updateBundles || updateInfo.updateBundles.isEmpty()){
            return;
        }
        PatchMerger patchMerger = null;
        try {
            patchMerger = new PatchMerger(updateInfo, patchFile, null);
            patchMerger.merge();

            List<UpdateInfo.Item> result = new ArrayList<>();
            for (int i = 0; i < updateInfo.updateBundles.size(); i++) {
                UpdateInfo.Item item = updateInfo.updateBundles.get(i);
                if (patchMerger.mergeOutputs.containsKey(item.name)) {
                    //对于merge成功的bundle列表进行更新
                    Pair<String, Item> pair = patchMerger.mergeOutputs.get(item.name);
                    boolean succeed = new File(pair.first).exists();
                    if (item.reset){
                        result.add(item);
                        succeed = true;
                    }
                    if (succeed) {
                        result.add(item);
                    }
                    if (monitor != null) {
                        monitor.merge(succeed, item.name, item.dexpatchVersion, "");
                    }
                }
            }
            updateInfo.updateBundles = result;

            PatchInstaller patchInstaller = new PatchInstaller(patchMerger.mergeOutputs, updateInfo);
            patchInstaller.install();
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        } finally {
            PatchCleaner.clearUpdatePath(updateInfo.workDir.getAbsolutePath());
        }

        ConcurrentHashMap<String, Long> installList = BaselineInfoManager.instance().getDexPatchBundles();
        if (installList == null || installList.size() == 0) {
            return;
        }

        if (null != monitor) {
            for (UpdateInfo.Item item : updateInfo.updateBundles) {
                boolean succeed = installList.containsKey(item.name) && item.dexpatchVersion == installList.get(
                    item.name);
                monitor.install(succeed, item.name, item.dexpatchVersion, "");
                if (!succeed && item.reset){
                    monitor.install(true, item.name, item.dexpatchVersion, "");
                }
            }
        }
    }



    /**
     * @return hotPatch的数据
     */
    public static List<UpdateInfo.Item> filterNeedHotPatchList(List<Item> hotPatchList) {
        List<Item> resultList = new ArrayList<>(hotPatchList.size());
        Map<String, Long> installMap = AtlasHotPatchManager.getInstance().getAllInstallPatch();
        if (null == installMap) {
            installMap = new HashMap<>(1);
        }
        for (Item item : hotPatchList) {
            if (item.patchType != Item.PATCH_DEX_HOT) {
                continue;
            }
            Long version = installMap.get(item.name);
            if (null == version || item.dexpatchVersion > version) {
                resultList.add(Item.makeCopy(item));
            }
        }
        return resultList;
    }


    /**
     * @return 普通DexPatch (cold)的数据
     */
    public static List<UpdateInfo.Item> filterNeedColdPatchList(List<Item> coldPatchList) {
        List<Item> resultList = new ArrayList<>(coldPatchList.size());
        Map<String, Long> installMap = BaselineInfoManager.instance().getDexPatchBundles();
        if (null == installMap) {
            installMap = new HashMap<>(1);
        }
        for (Item item : coldPatchList) {
            if (item.patchType != Item.PATCH_DEX_COLD) {
                continue;
            }
            Long version = installMap.get(item.name);

            if (item.reset) {
                //本地已经安装过，并且处于未回滚状态
                if (null != version && version != -1) {
                    resultList.add(Item.makeCopy(item));
                }
                continue;
            }
            if (null == version || item.dexpatchVersion > version) {
                resultList.add(Item.makeCopy(item));
            }
        }
        return resultList;
    }

}