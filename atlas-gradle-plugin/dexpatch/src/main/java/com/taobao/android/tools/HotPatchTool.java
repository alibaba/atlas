package com.taobao.android.tools;

import com.taobao.android.differ.dex.BundleDiffResult;
import com.taobao.android.inputs.HotPatchInput;
import com.taobao.android.object.DexDiffInfo;
import org.apache.commons.io.FileUtils;

import java.io.*;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author lilong
 * @create 2017-11-07 下午2:16
 */

public class HotPatchTool extends DexPatchTool {


        private Set<String>hotClassList = new HashSet<>();

        @Override
        public File createBundleDexPatch(File newApkUnzipFolder,
                File baseApkUnzipFolder,
                File destDex,
                File tmpDexFile,
        boolean mainDex) throws Exception {
            if (input instanceof HotPatchInput){
                readClassFile(((HotPatchInput) input).hotClassListFile);
            }
            boolean hasDexPatch = false;
            boolean hasHotDexPatch = false;
            // 比较主bundle的dex
            if (!tmpDexFile.exists()) {
                tmpDexFile.mkdirs();
            }
            String bundleName = null;
            if (mainDex){
                bundleName = "com.taobao.maindex";
            }else {
                bundleName = baseApkUnzipFolder.getName().substring(3).replace("_", ".");
            }
            List<File> baseDexFiles = getFolderDexFiles(baseApkUnzipFolder);
            List<File> newDexFiles = getFolderDexFiles(newApkUnzipFolder);
            File dexDiffFile = new File(tmpDexFile, "diff.dex");
            File hotDiffDexFile = new File(dexDiffFile.getParentFile(),"hot-diff.dex");
            File hotdestDexFile = null;
            if (mainDex) {
                hotdestDexFile = new File(destDex.getParentFile().getParentFile(), "hot.dex");
            }else {
                hotdestDexFile = new File(destDex.getParentFile(), "hot.dex");

            }
            PatchDexTool dexTool = new HotDexPatchDexTool(baseDexFiles,
                    newDexFiles,
                    DEFAULT_API_LEVEL,
                    null,
                    mainDex);
            dexTool.setExculdeClasses(((HotPatchInput)input).excludeClasses);
            dexTool.setPatchClassList(hotClassList);
            DexDiffInfo dexDiffInfo = dexTool.createPatchDex(dexDiffFile);
            if (dexDiffFile.exists()) {
                hasDexPatch = true;
                BundleDiffResult bundleDiffResult = new BundleDiffResult();
                bundleDiffResult.setBundleName(bundleName);
                bundleDiffResults.add(bundleDiffResult);
                diffPatchInfos.add(bundleDiffResult);
                dexDiffInfo.save(bundleDiffResult);
                FileUtils.copyFile(dexDiffFile, destDex);
            }
            if (hotDiffDexFile.exists()){
                hasHotDexPatch = true;
                FileUtils.copyFile(hotDiffDexFile, hotdestDexFile);

            }
            if (hasDexPatch&&!hasHotDexPatch){
                bundleTypes.put(bundleName,1);
            }else if (hasDexPatch && hasHotDexPatch){
                bundleTypes.put(bundleName,3);
            }else if (!hasDexPatch && hasHotDexPatch){
                bundleTypes.put(bundleName,2);

            }
            FileUtils.deleteDirectory(tmpDexFile);

            return destDex;
           }

    private void readClassFile(File hotClassListFile) throws IOException {
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(new FileInputStream(hotClassListFile)));
        String line = null;
        while ((line = bufferedReader.readLine())!= null){
            hotClassList.add(line);
        }
    }
}
