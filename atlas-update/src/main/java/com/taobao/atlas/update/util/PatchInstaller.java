package com.taobao.atlas.update.util;

import android.taobao.atlas.bundleInfo.AtlasBundleInfoManager;
import android.taobao.atlas.bundleInfo.BundleListing;
import android.taobao.atlas.framework.Atlas;
import android.taobao.atlas.framework.Framework;
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

    private Map<String, Pair<String,UpdateInfo.Item>> mergeOutputs;
    private UpdateInfo updateInfo;

    public PatchInstaller(Map<String, Pair<String,UpdateInfo.Item>> mergeOutputs, UpdateInfo updateInfo) {
        this.mergeOutputs = mergeOutputs;
        this.updateInfo = updateInfo;
    }


    public void install() throws BundleException {

        if (mergeOutputs.isEmpty()) {
            throw new BundleException("merge bundles is empty");
        }

        Iterator entries = mergeOutputs.entrySet().iterator();
        String[] bundleNameList = new String[mergeOutputs.size()];
        File[] bundleFilePathList = new File[mergeOutputs.size()];
        String[] upgradeVersions = new String[mergeOutputs.size()];
        long[] dexPatchVersions = new long[mergeOutputs.size()];
        int index = 0;
        while (entries.hasNext()) {
            Map.Entry entry = (Map.Entry) entries.next();
            bundleNameList[index] = (String) entry.getKey();
            Pair<String, UpdateInfo.Item> bundlePair = (Pair<String, UpdateInfo.Item>) entry.getValue();
            if(bundlePair.second.reset){
                if (!updateInfo.dexPatch) {
                    upgradeVersions[index] = "-1";
                } else {
                    dexPatchVersions[index] = -1;
                }
            }else {
                bundleFilePathList[index] = new File(bundlePair.first);
                if (!bundleFilePathList[index].exists()) {
                    throw new BundleException("bundle input is wrong : " + bundleFilePathList);
                }
                if (!updateInfo.dexPatch) {
                    upgradeVersions[index] = bundlePair.second.unitTag;
                } else {
                    dexPatchVersions[index] = bundlePair.second.dexpatchVersion;
                }
            }
            index++;
        }

        List<String> realInstalledBundle = Arrays.asList(bundleNameList);
        for (UpdateInfo.Item bundle : updateInfo.updateBundles) {
            if (!realInstalledBundle.contains(bundle.name) && AtlasBundleInfoManager.instance().isInternalBundle(bundle.name)) {
                throw new BundleException("bundle  " + bundle.name + " is error");
            }
        }
        Framework.update(!updateInfo.dexPatch,bundleNameList,bundleFilePathList,upgradeVersions,dexPatchVersions,updateInfo.updateVersion,updateInfo.lowDisk);

    }
}
