package com.taobao.atlas.update.util;

import android.taobao.atlas.bundleInfo.AtlasBundleInfoManager;
import android.taobao.atlas.bundleInfo.BundleListing;
import android.taobao.atlas.framework.Atlas;
import android.taobao.atlas.runtime.RuntimeVariables;
import android.taobao.atlas.versionInfo.BaselineInfoManager;
import android.util.Pair;

import com.taobao.atlas.update.model.UpdateInfo;

import org.osgi.framework.BundleException;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;


public class PatchInstaller {

    private Map<String, Pair> mergeOutputs;
    private UpdateInfo updateInfo;

    public PatchInstaller(Map<String, Pair> mergeOutputs, UpdateInfo updateInfo) {
        this.mergeOutputs = mergeOutputs;
        this.updateInfo = updateInfo;
    }


    public void install() throws BundleException {

        if (mergeOutputs.isEmpty()) {
            throw new BundleException("merge bundles is empty");
        }
//
//        buildBundleInventory(updateInfo);
//
//        File fileDir = new File(RuntimeVariables.androidApplication.getFilesDir(), "bundlelisting");
//        String fileName = String.format("%s%s.json", new Object[]{"bundleInfo-", updateInfo.dexPatchVersion>0 ? updateInfo.dexPatchVersion+"" : updateInfo.updateVersion});
//
//        if (!fileDir.exists() || !new File(fileDir, fileName).exists()) {
//            throw new BundleException("bundle file list is empty");
//        }

        Iterator entries = mergeOutputs.entrySet().iterator();
        String[] packageName = new String[mergeOutputs.size()];
        File[] bundleFilePath = new File[mergeOutputs.size()];
        String[] versions = new String[mergeOutputs.size()];
        int index = 0;
        while (entries.hasNext()) {
            Map.Entry entry = (Map.Entry) entries.next();
            packageName[index] = (String) entry.getKey();
            Pair<String, String> bundlePair = (Pair<String, String>) entry.getValue();
            bundleFilePath[index] = new File(bundlePair.first);
            if (!bundleFilePath[index].exists()) {
                throw new BundleException("bundle input is wrong");
            }
            versions[index] = bundlePair.second;
            index++;
        }

        List<String> realInstalledBundle = Arrays.asList(packageName);
        for (UpdateInfo.Item bundle : updateInfo.updateBundles) {
            if (!realInstalledBundle.contains(bundle.name) && AtlasBundleInfoManager.instance().isInternalBundle(bundle.name)) {
                throw new BundleException("bundle  " + bundle.name + " is error");
            }
        }


        Atlas.getInstance().installOrUpdate(packageName, bundleFilePath, versions,updateInfo.dexPatchVersion);


        List<String> rollbackBundles = new ArrayList<String>();

        for (UpdateInfo.Item item : updateInfo.updateBundles) {
            if (item != null) {
                String bundleCodeVersion = item.version.split("@")[1];
                if (bundleCodeVersion.trim().equals("-1")) {
                    rollbackBundles.add(item.name);
                }

            }
        }
        if (rollbackBundles.size() > 0) {
            Atlas.getInstance().restoreBundle(rollbackBundles.toArray(new String[rollbackBundles.size()]));
        }


        saveBaselineInfo(updateInfo, realInstalledBundle);

    }

    private void saveBaselineInfo(UpdateInfo updateData, List<String> realInstalledBundleNames) {
        ArrayList<BaselineInfoManager.UpdateBundleInfo> updateInfos = new ArrayList<BaselineInfoManager.UpdateBundleInfo>();
        for (UpdateInfo.Item info : updateData.updateBundles) {
            if (realInstalledBundleNames.contains(info.name)) {
                BaselineInfoManager.UpdateBundleInfo item = new BaselineInfoManager.UpdateBundleInfo();
                item.name = info.name;
                item.version = info.version;
                item.size = 0 + "";
                updateInfos.add(item);
            }
        }
        try {
            if(updateData.dexPatchVersion>0) {
                BaselineInfoManager.instance().saveBaselineInfo(updateData.dexPatchVersion, updateInfos);
            }else{
                BaselineInfoManager.instance().saveBaselineInfo(updateData.updateVersion,updateInfos);
            }
        } catch (Throwable e) {

        }
    }


}
