package com.taobao.checker;

import com.taobao.android.reader.BundleListing;
import com.taobao.update.UpdateInfo;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * @author lilong
 * @create 2017-08-15 上午11:41
 */

public class PatchChecker implements Checker{
    private UpdateInfo updateInfo;
    private File file;
    private LinkedHashMap<String,BundleListing.BundleInfo> bundleInfos = new LinkedHashMap<>();

    public PatchChecker(UpdateInfo updateInfo, LinkedHashMap linkedHashMap, File patchFile) {
        this.updateInfo = updateInfo;
        this.file = patchFile;
        this.bundleInfos = linkedHashMap;
    }

    public List<ReasonMsg> check() throws IOException {
        List<ReasonMsg>reasonMsgs = new ArrayList<>();
        List<String>changedBundles = new ArrayList<>();
        if (bundleInfos == null){
            System.out.println("skip check "+file.getName()+" bundleInfos is null!");
            reasonMsgs.add(new ReasonMsg(ReasonType.SUCCESS, file.getName()));
            return reasonMsgs;
        }
        for (Map.Entry entry:bundleInfos.entrySet()){
            BundleListing.BundleInfo bundleInfo = (BundleListing.BundleInfo) entry.getValue();
            if (bundleInfo.getCurrent_unique_tag()!= null) {
                if (!bundleInfo.getCurrent_unique_tag().equals(bundleInfo.getUnique_tag())) {
                    changedBundles.add(bundleInfo.getPkgName());
                }
            }
        }
        if (!file.exists()){
            reasonMsgs.add(new ReasonMsg(ReasonType.ERROR3,file.getName()));
        }
        ZipFile zipFile = new ZipFile(file);
        for (UpdateInfo.Item item:updateInfo.updateBundles){
            Enumeration<? extends ZipEntry>enumeration =  zipFile.entries();
            if (!item.name.equals("com.taobao.maindex")&&!changedBundles.contains(item.name)){
                reasonMsgs.add(new ReasonMsg(ReasonType.ERROR7,item.name));
            }
            if (item.unitTag!= null &&!item.name.equals("com.taobao.maindex")&&!item.unitTag.equals(bundleInfos.get(item.name).getCurrent_unique_tag())){
                reasonMsgs.add(new ReasonMsg(ReasonType.ERROR5,item.name));
            }
            if (item.unitTag!= null &&!item.name.equals("com.taobao.maindex")&&!item.srcUnitTag.equals(bundleInfos.get(item.name).getUnique_tag())){
                reasonMsgs.add(new ReasonMsg(ReasonType.ERROR8,item.name));
            }
            if (item.srcUnitTag!=null && item.srcUnitTag.equals(item.unitTag)){
                reasonMsgs.add(new ReasonMsg(ReasonType.ERROR2,item.name));
            }
            boolean contains = false;
            while (enumeration.hasMoreElements()){
                    ZipEntry zipEntry = enumeration.nextElement();
                    String name = zipEntry.getName();
                    if (name.startsWith("lib"+item.name.replace(".","_"))){
                        contains = true;
                        break;
                    }
                }
                if (!contains){
                    reasonMsgs.add(new ReasonMsg(ReasonType.ERROR1,item.name));
                }
            changedBundles.remove(item.name);
        }

        if (changedBundles.size() > 0){
            for (String bundleName:changedBundles){
                reasonMsgs.add(new ReasonMsg(ReasonType.ERROR6,bundleName));
            }
        }
        if (reasonMsgs.size() == 0) {
            reasonMsgs.add(new ReasonMsg(ReasonType.SUCCESS, file.getName()));
        }
        return reasonMsgs;

    }


}
