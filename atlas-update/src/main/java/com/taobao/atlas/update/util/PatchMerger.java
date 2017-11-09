package com.taobao.atlas.update.util;

import android.os.Build;
import android.taobao.atlas.bundleInfo.AtlasBundleInfoManager;
import android.taobao.atlas.framework.Atlas;
import android.taobao.atlas.framework.BundleImpl;
import android.taobao.atlas.framework.Framework;
import android.taobao.atlas.framework.bundlestorage.BundleArchive;
import android.taobao.atlas.runtime.RuntimeVariables;
import android.taobao.atlas.util.ApkUtils;
import android.taobao.atlas.util.IOUtil;
import android.taobao.atlas.util.WrapperUtil;
import android.taobao.atlas.versionInfo.BaselineInfoManager;
import android.text.TextUtils;
import android.util.Pair;

import com.taobao.atlas.dexmerge.DexMergeClient;
import com.taobao.atlas.dexmerge.MergeCallback;
import com.taobao.atlas.dexmerge.MergeObject;
import com.taobao.atlas.update.exception.MergeException;
import com.taobao.atlas.update.model.UpdateInfo;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Created by guanjie on 15/10/8.
 */
public class PatchMerger {

    private UpdateInfo updateInfo;
    private File patchFile;
    private MergeCallback mergeCallback;
    private ZipFile apkZip;
    private static int BUFFEREDSIZE = 1024;

    public Map<String, Pair<String,UpdateInfo.Item>> mergeOutputs = new HashMap<>();
    private static boolean supportMerge;
    private static String MAIN_DEX = "com.taobao.maindex";
    private boolean lowDisk = false;
    static {
        supportMerge = Build.VERSION.SDK_INT < 21;
    }


    public PatchMerger(UpdateInfo updateInfo, File patchFile, MergeCallback mergeCallback) throws MergeException {
        this.updateInfo = updateInfo;
        this.patchFile = patchFile;
        this.mergeCallback = mergeCallback;
        if(updateInfo.lowDisk){
            lowDisk = true;
        }else{
            lowDisk = false;
        }
        try {
            this.apkZip = new ZipFile(RuntimeVariables.androidApplication.getApplicationInfo().sourceDir);
        } catch (IOException e) {
            throw new MergeException(e);
        }


    }

    public void merge() throws MergeException, IOException {

        try {
            File outputDirectory = updateInfo.workDir;
            File oringnalDir = new File(outputDirectory.getParentFile(), "orignal_" + updateInfo.baseVersion);
            if (!outputDirectory.exists()){
                outputDirectory.mkdirs();
            }
            if (!oringnalDir.exists()){
                oringnalDir.mkdirs();
            }
            ZipFile patchZip = new ZipFile(patchFile);
            Pair<File, String>[] updateBundles = new Pair[updateInfo.updateBundles.size()];
            List<MergeObject> toMergeList = new ArrayList<MergeObject>();
            //find original bundle and store in pair struct
            for (int x = 0; x < updateInfo.updateBundles.size(); x++) {
                UpdateInfo.Item item = updateInfo.updateBundles.get(x);
                if(item.inherit){
                    continue;
                }
                String bundleName = item.name;
                String entryNamePrefix = String.format("%s%s", "lib", bundleName.replace(".", "_"));
                String entryName = entryNamePrefix + ".so";
                ZipEntry entry = patchZip.getEntry(entryName);
                if (patchZip.getEntry(entryName) != null && !supportMerge(bundleName)) {
                    //全量部署
                    File targetBundle = new File(outputDirectory, entryName);
                    OutputStream outputStream = new FileOutputStream(targetBundle);
                    InputStream inputStream = patchZip.getInputStream(entry);
                    IOUtil.copyStream(inputStream, outputStream);
                    mergeOutputs.put(bundleName, new Pair<>(targetBundle.getAbsolutePath(), item));
                } else {
                    if(item.reset){
                        //回滚到基线的bundle
                        mergeOutputs.put(bundleName, new Pair<>("", item));
                    }else {
                        //差量部署
//                        File baselineBundle = findOriginalBundleFile(bundleName, oringnalDir.getAbsolutePath(), item);
                        File originalBundle = findOriginalBundleFile(bundleName, oringnalDir.getAbsolutePath(), item);
                        if (originalBundle != null && originalBundle.exists()) {
                            updateBundles[x] = new Pair<File, String>(originalBundle, bundleName);
                            File targetBundle = new File(outputDirectory, "lib" + bundleName.replace(".", "_") + ".so");
                            if (targetBundle.exists()) {
                                targetBundle.delete();
                            }
                            toMergeList.add(new MergeObject(updateBundles[x].first.getAbsolutePath(), updateBundles[x].second, targetBundle.getAbsolutePath()));
                            mergeOutputs.put(bundleName, new Pair<>(targetBundle.getAbsolutePath(), item));
                        }
                    }
                }
            }

            if (toMergeList.size() > 0) {

                DexMergeClient dexMergeClient = getMergeClient();

                boolean mergeFinish = dexMergeClient.dexMerge(patchFile.getAbsolutePath(),toMergeList, true);

                dexMergeClient.unPrepare();

                if (!updateInfo.dexPatch && !mergeFinish) {
                    throw new MergeException("merge failed!");
                }
            }


        } catch (Exception e){
            e.printStackTrace();
            throw new MergeException("merge failed!");
        } finally{
            if (apkZip != null) {
                try {
                    apkZip.close();
                } catch (Throwable e) {
                }
            }

        }

    }



    /**
     * be not call in main thread
     */


    /**
     * get original bundle
     *
     * @param bundleName
     * @return
     */
    public File findOriginalBundleFile(String bundleName, String bundleDirIfNeedCreate, UpdateInfo.Item item) throws IOException {
        if("com.taobao.dynamic.test".equals(bundleName)){
            return getOriginalBundleFromApk(bundleName,bundleDirIfNeedCreate);
        }
        if (bundleName.equals(MAIN_DEX)){
            if(!updateInfo.dexPatch) {
                return new File(RuntimeVariables.androidApplication.getApplicationInfo().sourceDir);
            }else{
                String maindexVersion = BaselineInfoManager.instance().getBaseBundleVersion("com.taobao.maindex");
                String currentVersionName = WrapperUtil.getPackageInfo(RuntimeVariables.androidApplication).versionName;
                if(currentVersionName.equals(updateInfo.baseVersion)) {
                    if (!TextUtils.isEmpty(maindexVersion)) {
                        File old = Framework.getInstalledBundle("com.taobao.maindex",maindexVersion);
                        if (old.exists()) {
                            return old;
                        }
                    } else if (updateInfo.baseVersion.equals(RuntimeVariables.sInstalledVersionName)) {
                        return new File(RuntimeVariables.androidApplication.getApplicationInfo().sourceDir);
                    }
                }
                throw new IllegalStateException("src version can not be null");
            }
        }

        if (TextUtils.isEmpty(item.srcUnitTag)) {
            throw new IllegalStateException("src version can not be null");
        }

        File oldBundle = null;
        BundleImpl impl = (BundleImpl) Atlas.getInstance().getBundle(bundleName);
        if (impl != null && !BaselineInfoManager.instance().isDexPatched(bundleName)) {
            String path = impl.getArchive().getCurrentRevision().getRevisionDir().getAbsolutePath();
            if(!path.contains(BundleArchive.DEXPATCH_DIR) && AtlasBundleInfoManager.instance().getBundleInfo(bundleName).getUnique_tag().equals(item.srcUnitTag)){
                oldBundle = impl.getArchive().getArchiveFile();
            }
        } else {
            oldBundle = Framework.getInstalledBundle(bundleName, item.srcUnitTag);
        }

        if (oldBundle == null && AtlasBundleInfoManager.instance().getBundleInfo(bundleName).getUnique_tag().equals(item.srcUnitTag) && !BaselineInfoManager.instance().isUpdated(bundleName)) {
            oldBundle = getOriginalBundleFromApk(bundleName,bundleDirIfNeedCreate);
        }
        if(oldBundle!=null || !AtlasBundleInfoManager.instance().isInternalBundle(bundleName)) {
            return oldBundle;
        }else{
            throw new IOException("can not find valid src bundle of " + bundleName);
        }
    }

    private File getOriginalBundleFromApk(String bundleName,String bundleDirIfNeedCreate) throws IOException{
        String oldBundleFileName = String.format("lib%s.so", bundleName.replace(".", "_"));
        File libDir = new File(RuntimeVariables.androidApplication.getFilesDir().getParentFile(), "lib");
        File oldBundle = new File(libDir,oldBundleFileName);
        if(!oldBundle.exists()){
            oldBundle = new File(RuntimeVariables.androidApplication.getApplicationInfo().nativeLibraryDir,oldBundleFileName);
        }
        if (oldBundle.exists()) {
            return oldBundle;
        } else {
            InputStream oldBundleStream = null;
            try {
                oldBundleStream = RuntimeVariables.originalResources.getAssets().open(oldBundleFileName);
            }catch (Throwable e){}
            if(oldBundleStream==null) {
                if (apkZip == null) {
                    apkZip = new ZipFile(RuntimeVariables.androidApplication.getApplicationInfo().sourceDir);
                }
                String entryName = String.format("lib/armeabi/%s", oldBundleFileName);
                if (apkZip.getEntry(entryName) != null) {
                    oldBundleStream = apkZip.getInputStream(apkZip.getEntry(entryName));
//                    return oldBundle;
                }
            }
            if(oldBundleStream!= null){
                oldBundle = new File(bundleDirIfNeedCreate, oldBundleFileName);
                ApkUtils.copyInputStreamToFile(oldBundleStream, oldBundle);
            }
        }
        return oldBundle;
    }

    private DexMergeClient getMergeClient() {
        DexMergeClient mergeClient = new DexMergeClient(mergeCallback);
        boolean result = mergeClient.prepare();
        if (result) {
            return mergeClient;
        }
        throw new RuntimeException("prepare client error");
    }


    private boolean supportMerge(String bundleName) {

        return (lowDisk || supportMerge)&&bundleName.equals(MAIN_DEX);

    }
}