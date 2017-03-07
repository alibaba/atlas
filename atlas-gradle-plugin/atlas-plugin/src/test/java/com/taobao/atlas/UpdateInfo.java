//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package com.taobao.atlas;

import com.taobao.android.object.BuildPatchInfos;
import com.taobao.android.object.PatchBundleInfo;
import com.taobao.android.object.PatchInfo;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class UpdateInfo implements Serializable {
    public String baseVersion;
    public String updateVersion;
    public List<UpdateInfo.Item> updateBundles;
    public File workDir;

    public UpdateInfo() {
    }

    public UpdateInfo(BuildPatchInfos patchInfos) {
        UpdateInfo updateInfo = this;
        updateInfo.baseVersion = patchInfos.getBaseVersion();
        updateInfo.updateVersion = patchInfos.getPatches().get(0).getPatchVersion();

        PatchInfo patchInfo = patchInfos.getPatches().get(0);

        List<UpdateInfo.Item> items = new ArrayList<Item>();
        for (PatchBundleInfo patchBundleInfo : patchInfo.getBundles()) {
            UpdateInfo.Item item = new UpdateInfo.Item();
            items.add(item);
            item.dependency = patchBundleInfo.getDependency();
            item.isMainDex = patchBundleInfo.getMainBundle();
            item.name = patchBundleInfo.getPkgName();
//            item.srcVersion = patchBundleInfo.getVersion();
            item.version = updateInfo.baseVersion + "@" + patchBundleInfo.getVersion();
        }
        updateInfo.updateBundles = items;
    }

    public static class Item implements Serializable {
        public boolean isMainDex;
        public String name;
        public String version;
        public String srcVersion;
        public List<String> dependency;

        public Item() {
        }
    }
}
