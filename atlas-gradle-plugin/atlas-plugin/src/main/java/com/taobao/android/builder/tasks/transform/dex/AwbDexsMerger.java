package com.taobao.android.builder.tasks.transform.dex;

import com.android.build.api.transform.TransformInvocation;
import com.android.build.gradle.internal.BuildCacheUtils;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.internal.api.AppVariantOutputContext;
import com.android.build.gradle.internal.api.AwbTransform;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.builder.core.ErrorReporter;
import com.android.builder.dexing.DexMergerTool;
import com.android.builder.dexing.DexerTool;
import com.android.builder.dexing.DexingType;
import com.android.builder.utils.FileCache;
import com.android.ide.common.process.ProcessException;
import com.android.ide.common.process.ProcessOutput;
import com.android.ide.common.process.ProcessOutputHandler;
import com.android.prefs.AndroidLocation;
import com.android.utils.FileUtils;
import com.taobao.android.builder.dependency.model.AwbBundle;
import org.gradle.api.file.FileCollection;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * AwbDexsMerger
 *
 * @author zhayu.ll
 * @date 18/2/9
 */
public class AwbDexsMerger extends AtlasDexMerger {

    private static final String ID = "atlasbundledexmerge";

    private AtomicInteger atomicInteger = new AtomicInteger();

    private File mainDexOut = null;


    public AwbDexsMerger(DexingType dexingType, FileCollection mainDexListFile, ErrorReporter errorReporter, DexMergerTool dexMerger, int minSdkVersion, boolean isDebuggable, AppVariantOutputContext appVariantOutputContext) {
        super(dexingType, mainDexListFile, errorReporter, dexMerger, minSdkVersion, isDebuggable, appVariantOutputContext);
            cacheHandler = new CacheHandler() {
            @Override
            public void handleMissActionResult(File outputDir, File in) throws IOException {
                File[] dexs = outputDir.listFiles((dir, name) -> name.endsWith(".dex"));
                if (dexs != null && dexs.length > 1) {
                    for (File file : dexs) {
                        org.apache.commons.io.FileUtils.copyFileToDirectory(file, in.getParentFile());
                    }

                } else if (dexs.length == 1) {

                    Files.copy(dexs[0].toPath(), in.toPath());
                } else {
                    throw new IOException("no dex file merge out");
                }
            }

            @Override
            public void handleQueryResult(FileCache.QueryResult result, File outDir, String bundleName) throws IOException {

                if (result.getQueryEvent().equals(FileCache.QueryEvent.HIT)) {
                    logger.info("hit dexmerge cache " + bundleName + "->" + result.getCachedFile().getAbsolutePath());
                    if (result.getCachedFile().exists()) {
                        org.apache.commons.io.FileUtils.copyFile(result.getCachedFile(), new File(outDir, CLASSES_DEX));
                    }
                } else {
                    logger.info("miss dexmerge cache " + bundleName + "-> null");

                }
            }
        };
    }



    @Override
    public void merge(TransformInvocation transformInvocation) {

        mainDexOut = getDexOutputLocation(transformInvocation.getOutputProvider(),"main", TransformManager.SCOPE_FULL_PROJECT);
        if (!mainDexOut.exists() || !mainDexOut.isDirectory()){
            return;
        }
        atomicInteger.set(org.apache.commons.io.FileUtils.listFiles(mainDexOut,new String[]{"dex"},true).size());
        for (AwbTransform awbTransform : variantOutputContext.getAwbTransformMap().values()) {
           merge(awbTransform.getAwbBundle());
        }

    }

    public void merge(AwbBundle awbBundle){
        File file = variantOutputContext.getVariantContext().getAwbDexAchiveOutput(awbBundle);
        List<File> awbDexFiles = new ArrayList<>();
        if (!file.exists() || !file.isDirectory()){
            return;
        }
        awbDexFiles.addAll(org.apache.commons.io.FileUtils.listFiles(file, new String[]{"jar", "dex"}, true));
        File[] mergeDexs = file.listFiles(pathname -> pathname.getName().endsWith(".jar") || pathname.isDirectory());
        sort(awbDexFiles);
        File outPutFolder = variantOutputContext.getAwbDexOutput(awbBundle.getName());
        try {
            FileUtils.cleanOutputDir(outPutFolder);
        } catch (IOException e) {
            e.printStackTrace();
        }
        FileCache.Inputs buildCacheInputs = getBuildCacheInputs(awbDexFiles, dexingType, dexMerger, null, minSdkVersion, isDebuggable, awbBundle.getName(), ID);
        ProcessOutput output = outputHandler.createOutput();
        FileCache fileCache = BuildCacheUtils.createBuildCacheIfEnabled(variantOutputContext.getVariantContext().getProject(), variantOutputContext.getScope().getGlobalScope().getProjectOptions());
        if (fileCache == null){
            try {
                fileCache = FileCache.getInstanceWithMultiProcessLocking(new File(AndroidLocation.getFolder(),"atlas-buildCache"));
            } catch (AndroidLocation.AndroidLocationException e) {
                e.printStackTrace();
            }
        }
        if (variantOutputContext.getVariantContext().getAtlasExtension().getTBuildConfig().getMergeBundlesDex() && !awbBundle.isRemote && awbBundle.isMBundle){
            allDexsArchives.addAll(Arrays.asList(mergeDexs));
            return;
        }
        try {
            FileCache.QueryResult result = fileCache.createFileInCacheIfAbsent(
                    buildCacheInputs,
                    in -> {
                        List<ForkJoinTask<Void>> mergeTasks = new ArrayList<ForkJoinTask<Void>>();
                        mergeTasks.addAll(
                                handleLegacyAndMonoDex(
                                        Arrays.asList(mergeDexs), output, outPutFolder, null));
                        mergeTasks.forEach(voidForkJoinTask -> voidForkJoinTask.join());

                        cacheHandler.handleMissActionResult(outPutFolder, in);
                        if (output != null) {
                            try {
                                outputHandler.handleOutput(output);
                            } catch (ProcessException e) {
                                // ignore this one
                            }
                        }
                    }

            );

            cacheHandler.handleQueryResult(result, outPutFolder, awbBundle.getName());



            if (awbBundle.isMBundle){
                org.apache.commons.io.FileUtils.moveFile(new File(outPutFolder, CLASSES_DEX),new File(mainDexOut,"classes"+atomicInteger.incrementAndGet()+".dex"));
            }

        } catch (Exception e) {
            e.printStackTrace();

        }
    }
}
