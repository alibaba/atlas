package com.taobao.atlas.update;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.taobao.atlas.patch.AtlasHotPatchManager;
import android.taobao.atlas.versionInfo.BaselineInfoManager;
import com.taobao.atlas.update.model.UpdateInfo;
import com.taobao.atlas.update.model.UpdateInfo.Item;

/**
 * Created by zhongcang on 2017/11/7.
 */

public class DexPatchDivider {

    /**
     * @param allItem 所有bundle patch数据
     * @return hotPatch的数据
     */
    public static List<UpdateInfo.Item> getHotPatchList(List<Item> allItem) {
        List<Item> resultList = new ArrayList<>(allItem.size());
        Map<String, Long> installMap = AtlasHotPatchManager.getInstance().getAllInstallPatch();
        if (null == installMap) {
            installMap = new HashMap<>(1);
        }
        for (Item item : allItem) {
            if (item.patchType != Item.PATCH_DEX_HOT
                && item.patchType != Item.PATCH_DEX_C_AND_H) {
                continue;
            }
            Long version = installMap.get(item.name);

            if (item.hotPatchVersion == -1) {
                //本地已经安装过，并且处于未回滚状态
                if (null != version && version != -1) {
                    resultList.add(Item.makeCopy(item));
                }
                continue;
            }
            if (null == version || item.hotPatchVersion > version) {
                resultList.add(Item.makeCopy(item));
            }
        }
        return resultList;
    }

    /**
     * @param allItem 所有bundle patch数据
     * @return 普通DexPatch (cold)的数据
     */
    public static List<UpdateInfo.Item> getColdPatchList(List<UpdateInfo.Item> allItem) {
        List<Item> resultList = new ArrayList<>(allItem.size());
        Map<String, Long> installMap = BaselineInfoManager.instance().getDexPatchBundles();
        if (null == installMap) {
            installMap = new HashMap<>(1);
        }
        for (Item item : allItem) {
            if (item.patchType != Item.PATCH_DEX_COLD
                && item.patchType != Item.PATCH_DEX_C_AND_H) {
                continue;
            }
            Long version = installMap.get(item.name);

            if (item.dexpatchVersion == -1) {
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

    //public boolean dividePatch(UpdateInfo info, File patchFile) {
    //    if (null == info || null == patchFile) {
    //        return false;
    //    }
    //    boolean success;
    //    ZipFile patchZip = null;
    //    try {
    //        patchZip = new ZipFile(patchFile);
    //        for (UpdateInfo.Item item : info.updateBundles) {
    //            if ("com.taobao.maindex".equals(item.name)) {
    //                ZipEntry entry = patchZip.getEntry("libcom_taobao_maindex.so");
    //                if (null != entry) {
    //                    mColdPatchList.add(Item.makeCopy(item));
    //                }
    //                entry = patchZip.getEntry(mDexHotName);
    //                if (null != entry) {
    //                    mHotPatchList.add(Item.makeCopy(item));
    //                }
    //            } else {
    //                String entryName = String.format("%s%s", "lib", item.name.replace(".", "_"));
    //                ZipEntry entry = patchZip.getEntry(entryName + "/" + mDexColdName);
    //                if (null != entry) {
    //                    mColdPatchList.add(UpdateInfo.Item.makeCopy(item));
    //                }
    //                entry = patchZip.getEntry(entryName + "/" + mDexHotName);
    //                if (null != entry) {
    //                    mHotPatchList.add(UpdateInfo.Item.makeCopy(item));
    //                }
    //            }
    //        }
    //        success = true;
    //    } catch (IOException e) {
    //        e.printStackTrace();
    //        success = false;
    //    } finally {
    //        IOUtil.quietClose(patchZip);
    //    }
    //    return success;
    //}
}

/**
 * Created by zhongcang on 2017/11/7.
 *
 * 把搅在一起的tpatch，拆成两个独立的patch(hotPatch,coldPatch)
 */

//
//package com.taobao.atlas.update;
//
//    import java.io.File;
//    import java.io.FileInputStream;
//    import java.io.FileOutputStream;
//    import java.io.IOException;
//    import java.io.InputStream;
//    import java.io.OutputStream;
//    import java.util.HashMap;
//    import java.util.LinkedList;
//    import java.util.List;
//    import java.util.Map;
//    import java.util.zip.ZipEntry;
//    import java.util.zip.ZipFile;
//    import java.util.zip.ZipOutputStream;
//
//    import android.taobao.atlas.util.IOUtil;
//    import com.taobao.atlas.update.model.UpdateInfo;
//    import com.taobao.atlas.update.model.UpdateInfo.Item;
//    import com.taobao.dex.util.FileUtils;
//

//
//public class PatchDivider {
//    private final String mDexHotName = "hotfix.dex";
//    private final String mDexColdName = "class.dex";
//    private String mWorkDir;
//
//    private List<UpdateInfo.Item> mHotPatchList = new LinkedList<>();
//    private List<UpdateInfo.Item> mColdPatchList = new LinkedList<>();
//
//    public List<UpdateInfo.Item> getHotPatchList() {
//        return mHotPatchList;
//    }
//
//    public List<UpdateInfo.Item> getColdPatchList() {
//        return mColdPatchList;
//    }
//
//    public boolean dividePatch(UpdateInfo info, File patchFile) {
//        boolean success = false;
//        if (null == info || null == patchFile) {
//            return false;
//        }
//        mWorkDir = info.workDir.getAbsolutePath();
//
//        ZipFile patchZip = null;
//        ZipOutputStream outHotPatch = null, outColdPatch = null;
//        try {
//            patchZip = new ZipFile(patchFile);
//            outHotPatch = new ZipOutputStream(new FileOutputStream(FileUtils.makeNewFile(
//                mWorkDir + "/" + info.updateVersion + "@" + info.baseVersion + "hot.tpatch"
//            )));
//            outColdPatch = new ZipOutputStream(new FileOutputStream(FileUtils.makeNewFile(
//                mWorkDir + "/" + info.updateVersion + "@" + info.baseVersion + "cold.tpatch"
//            )));
//            for (UpdateInfo.Item item : info.updateBundles) {
//                if ("com.taobao.maindex".equals(item.name)) {
//                    divideMainDex(item, patchZip, outColdPatch, outHotPatch);
//                } else {
//                    String entryName = String.format("%s%s", "lib", item.name.replace(".", "_"));
//                    ZipEntry entry = patchZip.getEntry(entryName + "/" + mDexColdName);
//                    if (null != entry) {
//                        mColdPatchList.add(UpdateInfo.Item.makeCopy(item));
//                        outColdPatch.putNextEntry(entry);
//                        IOUtil.copyStream(patchZip.getInputStream(entry), outColdPatch);
//                    }
//                    entry = patchZip.getEntry(entryName + "/" + mDexHotName);
//                    if (null != entry) {
//                        mHotPatchList.add(UpdateInfo.Item.makeCopy(item));
//                        outHotPatch.putNextEntry(entry);
//                        IOUtil.copyStream(patchZip.getInputStream(entry), outHotPatch);
//                    }
//                }
//            }
//            success = true;
//        } catch (IOException e) {
//            e.printStackTrace();
//            success = false;
//        } finally {
//            IOUtil.quietClose(outColdPatch);
//            IOUtil.quietClose(outHotPatch);
//            IOUtil.quietClose(patchZip);
//        }
//        return success;
//    }
//
//    private void divideMainDex(UpdateInfo.Item mainDexInfo, ZipFile patchZip, ZipOutputStream hotPatchOut,
//                               ZipOutputStream coldPatchOut) throws IOException {
//        File originMainDexZip = releaseMainDex(patchZip,
//            String.format("%s%s.so", "lib", mainDexInfo.name.replace(".", "_")));
//        if (null == originMainDexZip) {
//            throw new IOException("release mainDex error !");
//        }
//        ZipFile originZip = null;
//        InputStream hotMainDexIn = null;
//        InputStream coldMainDexIn = null;
//        try {
//            originZip = new ZipFile(originMainDexZip);
//            ZipEntry entry = originZip.getEntry(mDexHotName);
//            if (null != entry) {
//                mHotPatchList.add(Item.makeCopy(mainDexInfo));
//                File hotMainDex = FileUtils.makeNewFile(mWorkDir + "/maindex_hot.zip");
//                zipEntry(originZip.getInputStream(entry), entry.getName(), hotMainDex);
//                hotPatchOut.putNextEntry(new ZipEntry("libcom_taobao_maindex.so"));
//                hotMainDexIn = new FileInputStream(hotMainDex);
//                IOUtil.copyStream(hotMainDexIn, hotPatchOut);
//            }
//            entry = originZip.getEntry(mDexColdName);
//            if (null != entry) {
//                mColdPatchList.add(Item.makeCopy(mainDexInfo));
//                File coldMainDex = FileUtils.makeNewFile(mWorkDir + "/maindex_cold.zip");
//                zipEntry(originZip.getInputStream(entry), entry.getName(), coldMainDex);
//                hotPatchOut.putNextEntry(new ZipEntry("libcom_taobao_maindex.so"));
//                coldMainDexIn = new FileInputStream(coldMainDex);
//                IOUtil.copyStream(coldMainDexIn, hotPatchOut);
//            }
//        } catch (IOException e) {
//            e.printStackTrace();
//        } finally {
//            IOUtil.quietClose(hotMainDexIn);
//            IOUtil.quietClose(coldMainDexIn);
//            IOUtil.quietClose(originZip);
//        }
//    }
//
//    private File releaseMainDex(ZipFile patchZip, String entryName) {
//        File tmpMainDex;
//        OutputStream mainDexOut = null;
//        try {
//            tmpMainDex = FileUtils.makeNewFile(mWorkDir + "/dexpatch_maindex.zip");
//            mainDexOut = new FileOutputStream(tmpMainDex);
//            IOUtil.copyStream(patchZip.getInputStream(patchZip.getEntry(entryName)), mainDexOut);
//        } catch (IOException e) {
//            e.printStackTrace();
//            tmpMainDex = null;
//        } finally {
//            IOUtil.quietClose(mainDexOut);
//        }
//        return tmpMainDex;
//    }
//
//    private void zipEntry(InputStream entryInput, String newEntryName, File newZip) {
//        ZipOutputStream out = null;
//        try {
//            out = new ZipOutputStream(new FileOutputStream(newZip));
//            ZipEntry newEntry = new ZipEntry(newEntryName);
//            out.putNextEntry(newEntry);
//            IOUtil.copyStream(entryInput, out);
//        } catch (IOException e) {
//            e.printStackTrace();
//        } finally {
//            IOUtil.quietClose(out);
//        }
//    }
//
//    private Map<String, File> divideMainDex(File originMainDex) {
//        Map<String, File> result = new HashMap<>(2);
//        ZipFile zip = null;
//        try {
//            zip = new ZipFile(originMainDex);
//            File targetHotMainDex = new File(mWorkDir + "/dexpatch_main_hot.zip");
//            File targetColdMainDex = new File(mWorkDir + "/dexpatch_main_cold.zip");
//            copyTo(zip, mDexHotName, targetHotMainDex);
//            copyTo(zip, mDexColdName, targetColdMainDex);
//            if (targetColdMainDex.exists()) {
//                result.put(mDexColdName, targetColdMainDex);
//            }
//            if (targetHotMainDex.exists()) {
//                result.put(mDexHotName, targetHotMainDex);
//            }
//        } catch (IOException e) {
//            e.printStackTrace();
//        } finally {
//            IOUtil.quietClose(zip);
//        }
//        return result;
//    }
//
//    private void copyTo(ZipFile zip, String entryName, File target) throws IOException {
//        OutputStream out = null;
//        try {
//            if (target.exists()) {
//                target.delete();
//            }
//            ZipEntry entry = zip.getEntry(entryName);
//            if (null != entry) {
//                target.createNewFile();
//                out = new FileOutputStream(target);
//                IOUtil.copyStream(zip.getInputStream(entry), out);
//            }
//        } catch (IOException e) {
//            throw e;
//        } finally {
//            IOUtil.quietClose(out);
//        }
//    }
//}