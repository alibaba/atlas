package com.taobao.android.tools;

import com.alibaba.fastjson.JSON;
import com.android.utils.Pair;
import com.taobao.android.inputs.TpatchInput;
import com.taobao.android.object.BuildPatchInfos;
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
import java.util.concurrent.Callable;

/**
 * @author lilong
 * @create 2017-11-03 上午10:13
 */

public class DexPatchTool extends TPatchTool {


    @Override
    public void doBundlePatch(File newBundleFile, File baseBundleFile, File patchTmpDir, String bundleName, File destPatchBundleDir, File newBundleUnzipFolder, File baseBundleUnzipFolder) throws Exception {
        CommandUtils.exec(patchTmpDir,
                "unzip " + newBundleFile.getAbsolutePath() + " -d " + newBundleUnzipFolder.getAbsolutePath());
        CommandUtils.exec(patchTmpDir, "unzip " + baseBundleFile.getAbsolutePath() + " -d " + baseBundleUnzipFolder
                .getAbsolutePath());
        File destDex = new File(destPatchBundleDir, DEX_NAME);
        File tmpDexFolder = new File(patchTmpDir, bundleName + "-dex");
        createBundleDexPatch(newBundleUnzipFolder,
                baseBundleUnzipFolder,
                destDex,
                tmpDexFolder,
                false);
    }

    @Override
    public PatchFile doPatch() throws Exception {

        TpatchInput tpatchInput = (TpatchInput) input;
        DexPatchFile tpatchFile = new DexPatchFile();
        tpatchFile.diffJson = new File(((TpatchInput) input).outPatchDir, "diff.json");
        tpatchFile.patchInfo = new File(((TpatchInput) input).outPatchDir, "patchInfo.json");
        final File patchTmpDir = new File(((TpatchInput) input).outPatchDir, "dexpatch-tmp");
        final File mainDiffFolder = new File(patchTmpDir, ((TpatchInput)input).mainBundleName);
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
        if ((((TpatchInput) input).splitDiffBundle != null)) {
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
                File mianDiffDestDex = new File(mainDiffFolder, DEX_NAME);
                File tmpDexFile = new File(patchTmpDir, ((TpatchInput)input).mainBundleName + "-dex");
                createBundleDexPatch(newApkUnzipFolder,
                        baseApkUnzipFolder,
                        mianDiffDestDex,
                        tmpDexFile,
                        true);

                // 是否保留主bundle的资源文件
                if (isRetainMainBundleRes()) {
                    copyMainBundleResources(newApkUnzipFolder,
                            baseApkUnzipFolder,
                            new File(patchTmpDir, ((TpatchInput)input).mainBundleName));
                }
                return true;
            }
        });

        for (final File soFile : soFiles) {
            System.out.println("do patch:" + soFile.getAbsolutePath());
            final String relativePath = PathUtils.toRelative(newApkUnzipFolder,
                    soFile.getAbsolutePath());
            if (null != ((TpatchInput) input).notIncludeFiles && pathMatcher.match(((TpatchInput)input).notIncludeFiles, relativePath)) {
                continue;
            }
            executorServicesHelper.submitTask(taskName, new Callable<Boolean>() {

                @Override
                public Boolean call() throws Exception {
                    File destFile = new File(patchTmpDir, ((TpatchInput)input).mainBundleName + "/" +
                            relativePath);
                    File baseSoFile = new File(baseApkUnzipFolder, relativePath);
                    if (isBundleFile(soFile)) {
                        processBundleFiles(soFile, baseSoFile, patchTmpDir);

                    } else if (isFileModify(soFile, baseSoFile)) { FileUtils.copyFile(soFile, destFile); }

                    return true;
                }
            });
        }

        executorServicesHelper.waitTaskCompleted(taskName);
        executorServicesHelper.stop();
        Profiler.release();

        Profiler.enter("ziptpatchfile");
        // zip file
        File patchFile = createDexPatchFile(((TpatchInput) input).outPatchDir, patchTmpDir);
        tpatchFile.patchFile = patchFile;
        PatchInfo curPatchInfo = createBasePatchInfo(patchFile);

        Profiler.release();
        

        Profiler.enter("writejson");
        BuildPatchInfos buildPatchInfos = new BuildPatchInfos();
        buildPatchInfos.getPatches().add(curPatchInfo);
        buildPatchInfos.setBaseVersion(input.baseApkBo.getVersionName());
        buildPatchInfos.setDiffBundleDex(true);

        FileUtils.writeStringToFile(((TpatchInput) input).outPutJson, JSON.toJSONString(buildPatchInfos));
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
        FileUtils.copyFileToDirectory(tpatchFile.diffJson, ((TpatchInput) input).outPatchDir.getParentFile(), true);
        if (newApkFileExist) {
            FileUtils.copyFileToDirectory(input.newApkBo.getApkFile(), ((TpatchInput) input).outPatchDir.getParentFile(), true);
        }
        Profiler.release();
        logger.warning(Profiler.dump());
        return tpatchFile;
    }

    private File createDexPatchFile(File outPatchDir, File patchTmpDir) throws IOException {
        File mainBundleFoder = new File(patchTmpDir, ((TpatchInput)input).mainBundleName);
        File mainBundleFile = new File(patchTmpDir, ((TpatchInput)input).mainBundleName + ".so");
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
}
