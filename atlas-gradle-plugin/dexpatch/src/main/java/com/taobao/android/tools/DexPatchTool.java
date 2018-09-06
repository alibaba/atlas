package com.taobao.android.tools;

import com.alibaba.fastjson.JSON;
import com.android.utils.Pair;
import com.google.common.collect.Lists;
import com.taobao.android.differ.dex.BundleDiffResult;
import com.taobao.android.inputs.DexPatchInput;
import com.taobao.android.inputs.TpatchInput;
import com.taobao.android.object.BuildPatchInfos;
import com.taobao.android.object.DexDiffInfo;
import com.taobao.android.object.PatchInfo;
import com.taobao.android.outputs.DexPatchFile;
import com.taobao.android.outputs.PatchFile;
import com.taobao.android.task.ExecutorServicesHelper;
import com.taobao.android.tpatch.model.BundleBO;
import com.taobao.android.tpatch.utils.MD5Util;
import com.taobao.android.tpatch.utils.PathUtils;
import com.taobao.android.utils.CommandUtils;
import com.taobao.android.utils.Profiler;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.TrueFileFilter;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * @author lilong
 * @create 2017-11-03 上午10:13
 */

public class DexPatchTool extends TPatchTool {


    @Override
    public PatchFile doPatch() throws Exception {

        TpatchInput tpatchInput = (TpatchInput) input;
        DexPatchFile tpatchFile = new DexPatchFile();
        tpatchFile.diffJson = new File(tpatchInput.outPatchDir, "diff.json");
        tpatchFile.patchInfo = new File(tpatchInput.outPatchDir, "patchInfo.json");
        final File patchTmpDir = new File(tpatchInput.outPatchDir, "dexpatch-tmp");
        final File mainDiffFolder = new File(patchTmpDir, tpatchInput.mainBundleName);
        patchTmpDir.mkdirs();
        FileUtils.cleanDirectory(patchTmpDir);
        mainDiffFolder.mkdirs();
        Profiler.release();
        Profiler.enter("unzip apks");
        // unzip apk
        File unzipFolder = unzipApk(((TpatchInput) input).outPatchDir);
        final File newApkUnzipFolder = new File(unzipFolder, NEW_APK_UNZIP_NAME);
        final File baseApkUnzipFolder = new File(unzipFolder, BASE_APK_UNZIP_NAME);
        Profiler.release();
        ExecutorServicesHelper executorServicesHelper = new ExecutorServicesHelper();
        String taskName = "diffBundleTask";
        //

        Collection<File> soFiles = FileUtils.listFiles(newApkUnzipFolder, new String[] {"so"}, true);

        //process remote bumdle
        if (tpatchInput.splitDiffBundle != null) {
            for (final Pair<BundleBO, BundleBO> bundle : ((TpatchInput) input).splitDiffBundle) {
                executorServicesHelper.submitTask(taskName, new Callable<Boolean>() {
                    @Override
                    public Boolean call() throws Exception {
                        processBundleFiles(bundle.getSecond().getBundleFile(), bundle.getFirst().getBundleFile(), patchTmpDir);
                        return true;
                    }
                });
            }
        }


        Profiler.enter("awbspatch");

        executorServicesHelper.submitTask(taskName, new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                // 得到主bundle的dex diff文件
                createBundleDexPatch(newApkUnzipFolder,
                        baseApkUnzipFolder,
                        mainDiffFolder,
                        true);

                return true;
            }
        });

        for (final File soFile : soFiles) {
            System.out.println("do dexpatch:" + soFile.getAbsolutePath());
            final String relativePath = PathUtils.toRelative(newApkUnzipFolder,
                    soFile.getAbsolutePath());
            if (null != tpatchInput.notIncludeFiles && pathMatcher.match(tpatchInput.notIncludeFiles, relativePath)) {
                continue;
            }
            executorServicesHelper.submitTask(taskName, new Callable<Boolean>() {

                @Override
                public Boolean call() throws Exception {
                    File destFile = new File(patchTmpDir, tpatchInput.mainBundleName + "/" +
                            relativePath);
                    File baseSoFile = new File(baseApkUnzipFolder, relativePath);
                    if (isBundleFile(soFile)) {
                        processBundleFiles(soFile, baseSoFile, patchTmpDir);

                    }

                    return true;
                }
            });
        }

        executorServicesHelper.waitTaskCompleted(taskName);
        executorServicesHelper.stop();
        Profiler.release();

        Profiler.enter("ziptpatchfile");
        // zip file
        File patchFile = createDexPatchFile(tpatchInput.outPatchDir, patchTmpDir);
        tpatchFile.patchFile = patchFile;
        if (!patchFile.exists()){
            return null;
        }
        PatchInfo curPatchInfo = createBasePatchInfo(patchFile);


        Profiler.release();
        

        Profiler.enter("writejson");
        BuildPatchInfos buildPatchInfos = new BuildPatchInfos();
        buildPatchInfos.getPatches().add(curPatchInfo);
        buildPatchInfos.setBaseVersion(input.baseApkBo.getVersionName());
        buildPatchInfos.setDiffBundleDex(true);

        FileUtils.writeStringToFile(tpatchInput.outPutJson, JSON.toJSONString(buildPatchInfos));
        // 删除临时的目录
        FileUtils.deleteDirectory(patchTmpDir);
        apkDiff.setBaseApkVersion(input.baseApkBo.getVersionName());
        apkDiff.setNewApkVersion(input.newApkBo.getVersionName());
        apkDiff.setBundleDiffResults(bundleDiffResults);
        boolean newApkFileExist = input.newApkBo.getApkFile().exists() && input.newApkBo.getApkFile().isFile();
        if (newApkFileExist) {
            apkDiff.setNewApkMd5(MD5Util.getFileMD5String(input.newApkBo.getApkFile()));
        }
        apkDiff.setFileName(input.newApkBo.getApkName());
        apkPatchInfos.setBaseApkVersion(input.baseApkBo.getVersionName());
        apkPatchInfos.setNewApkVersion(input.newApkBo.getVersionName());
        apkPatchInfos.setBundleDiffResults(diffPatchInfos);
        apkPatchInfos.setFileName(patchFile.getName());
        apkPatchInfos.setNewApkMd5(MD5Util.getFileMD5String(patchFile));
        FileUtils.writeStringToFile(tpatchFile.diffJson, JSON.toJSONString(apkDiff));
        FileUtils.writeStringToFile(tpatchFile.patchInfo, JSON.toJSONString(apkPatchInfos));
        FileUtils.copyFileToDirectory(tpatchFile.diffJson, tpatchInput.outPatchDir.getParentFile(), true);
        if (newApkFileExist) {
            FileUtils.copyFileToDirectory(input.newApkBo.getApkFile(), tpatchInput.outPatchDir.getParentFile(), true);
        }
        Profiler.release();
        logger.warning(Profiler.dump());
        return tpatchFile;
    }

    private File createDexPatchFile(File outPatchDir, File patchTmpDir) throws IOException {
        File mainBundleFoder = new File(patchTmpDir, ((TpatchInput)input).mainBundleName);
        File mainBundleFile = new File(patchTmpDir, "lib"+((TpatchInput)input).mainBundleName.replace(".","_") + ".so");
        if (FileUtils.listFiles(mainBundleFoder, TrueFileFilter.INSTANCE, TrueFileFilter.INSTANCE)
                .size() > 0) {
            CommandUtils.exec(mainBundleFoder, "zip -r " + mainBundleFile.getAbsolutePath() + " . -x */ -x .*");
        }
        FileUtils.deleteDirectory(mainBundleFoder);

        // 再压缩各自的bundle
        File patchFile = null;

        patchFile = new File(outPatchDir,
                input.newApkBo.getVersionName() + "@" + input.baseApkBo.getVersionName() + ".tpatch");
        if (patchFile.exists()) {
            FileUtils.deleteQuietly(patchFile);
        }
        //        zipBundle(patchTmpDir, patchFile);
        CommandUtils.exec(patchTmpDir, "zip -r " + patchFile.getAbsolutePath() + " . -x */ -x .*");
        FileUtils.deleteDirectory(patchTmpDir);
        return patchFile;

    }

    @Override
    public boolean isRetainMainBundleRes() {

        return false;
    }


    @Override
    public void doBundleResPatch(String bundleName, File destPatchBundleDir, File newBundleUnzipFolder, File baseBundleUnzipFolder) throws IOException {

    }

    @Override
    public List<File> createBundleDexPatch(File newApkUnzipFolder,
                                           File baseApkUnzipFolder,
                                           File mainDiffFolder,
                                           boolean mainDex) throws Exception {
        List<File> dexs = Lists.newArrayList();
        // 比较主bundle的dex

        String bundleName = null;
        if (mainDex) {
            bundleName = "com.taobao.maindex";
        } else {
            bundleName = baseApkUnzipFolder.getName().substring(3).replace("_", ".");
        }
        List<File> baseDexFiles = getFolderDexFiles(baseApkUnzipFolder);
        List<File> newDexFiles = getFolderDexFiles(newApkUnzipFolder);
        File dexDiffFile = new File(mainDiffFolder, TPatchTool.DEX_NAME);
        PatchDexTool dexTool = new DexPatchDexTool(baseDexFiles,
                newDexFiles,
                DEFAULT_API_LEVEL,
                null,
                mainDex);
        dexTool.setNewPatch(((DexPatchInput)input).newPatch);
        dexTool.setExculdeClasses(((DexPatchInput)input).excludeClasses);
        dexTool.setPatchClasses(((DexPatchInput)input).patchClasses);

        DexDiffInfo dexDiffInfo = dexTool.createPatchDex(mainDiffFolder);
        if (dexDiffFile.exists()) {
            dexs.add(dexDiffFile);
            BundleDiffResult bundleDiffResult = new BundleDiffResult();
            bundleDiffResult.setBundleName(bundleName);
            bundleDiffResults.add(bundleDiffResult);
            diffPatchInfos.add(bundleDiffResult);
            dexDiffInfo.save(bundleDiffResult);
        }
        if (dexs.size() > 0) {
            bundleTypes.put(bundleName,1);
        }

        return dexs;
    }
}
