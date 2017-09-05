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
        List<String> bundleNameList = new ArrayList<>();
        List<File> bundleFilePathList = new ArrayList<>();
        List<String> upgradeVersions = new ArrayList<>();
        List<Long> dexPatchVersions = new ArrayList<>();
        while (entries.hasNext()) {
            Map.Entry entry = (Map.Entry) entries.next();
            bundleNameList.add((String) entry.getKey());
            Pair<String, UpdateInfo.Item> bundlePair = (Pair<String, UpdateInfo.Item>) entry.getValue();
            if(bundlePair.second.reset){
                if (!updateInfo.dexPatch) {
                    upgradeVersions.add("-1");
                } else {
                    dexPatchVersions.add(Long.valueOf(-1));
                }
            }else {
                File bundleFile = new File(bundlePair.first);
                bundleFilePathList.add(bundleFile);
                if (!bundleFile.exists()) {
                    throw new BundleException("bundle input is wrong : " + bundleFilePathList);
                }
                if (!updateInfo.dexPatch) {
                    upgradeVersions.add(bundlePair.second.unitTag);
                } else {
                    dexPatchVersions.add(bundlePair.second.dexpatchVersion);
                }
            }
        }

        for (UpdateInfo.Item bundle : updateInfo.updateBundles) {
            if (!bundleNameList.contains(bundle.name) && AtlasBundleInfoManager.instance().isInternalBundle(bundle.name)) {
                if(bundle.inherit) {
                    bundleNameList.add(bundle.name);
                    bundleFilePathList.add(new File("inherit"));
                    if(!updateInfo.dexPatch){
                        upgradeVersions.add(bundle.unitTag);
                    }else{
                        dexPatchVersions.add(bundle.dexpatchVersion);
                    }
                }else{
                    throw new BundleException("bundle  " + bundle.name + " is error");
                }
            }
        }

        String[] bundleNameArray = bundleNameList.toArray(new String[bundleNameList.size()]);
        File[] bundleFilePathArray = bundleFilePathList.toArray(new File[bundleFilePathList.size()]);
        String[] updateVersionArray = upgradeVersions.toArray(new String[upgradeVersions.size()]);
        long[] dexPatchVersionArray = new long[dexPatchVersions.size()];
        for(int x=0;x<dexPatchVersions.size();x++){
            dexPatchVersionArray[x] = dexPatchVersions.get(x);
        }
        Framework.update(!updateInfo.dexPatch,bundleNameArray,bundleFilePathArray,updateVersionArray,dexPatchVersionArray,updateInfo.updateVersion,updateInfo.lowDisk);

    }
}
