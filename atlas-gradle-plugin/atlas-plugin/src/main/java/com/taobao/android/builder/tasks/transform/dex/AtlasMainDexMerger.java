package com.taobao.android.builder.tasks.transform.dex;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.build.api.transform.*;
import com.android.build.gradle.internal.BuildCacheUtils;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.internal.api.AppVariantOutputContext;
import com.android.build.gradle.internal.pipeline.IntermediateFolderUtils;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.builder.core.ErrorReporter;
import com.android.builder.dexing.DexMergerTool;
import com.android.builder.dexing.DexingType;
import com.android.builder.utils.FileCache;
import com.android.ide.common.process.ProcessException;
import com.android.ide.common.process.ProcessOutput;
import com.android.ide.common.process.ProcessOutputHandler;
import com.android.prefs.AndroidLocation;
import com.android.utils.FileUtils;
import com.taobao.android.builder.tools.ReflectUtils;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;

import java.io.*;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * @author lilong
 * @create 2017-12-27 上午9:43
 */


public class AtlasMainDexMerger extends AtlasDexMerger {

    private static final String ID = "atlasmaindexmerge";


    public AtlasMainDexMerger(DexingType dexingType, FileCollection mainDexListFile, ErrorReporter errorReporter, DexMergerTool dexMerger, int minSdkVersion, boolean isDebuggable, AppVariantOutputContext appVariantOutputContext) {

        super(dexingType, mainDexListFile, errorReporter, dexMerger, minSdkVersion, isDebuggable, appVariantOutputContext);
        cacheHandler = new CacheHandler() {
            @Override
            public void handleMissActionResult(File outputDir, File in) throws IOException {
                File[] dexs = outputDir.listFiles((dir, name) -> name.endsWith(".dex"));
                if (dexs != null && dexs.length > 1) {
                    org.apache.commons.io.FileUtils.copyDirectory(outputDir,in);
//                    for (File file : dexs) {
//                        org.apache.commons.io.FileUtils.copyFileToDirectory(file, in.getParentFile());
//                    }

                } else if (dexs.length == 1) {
                    if (!in.getParentFile().exists()){
                        in.getParentFile().mkdirs();
                    }
                    Files.copy(dexs[0].toPath(), in.toPath());
                } else {
                    throw new IOException("no dex file merge out");
                }
            }

            @Override
            public void handleQueryResult(FileCache.QueryResult result, File outputDir, String bundleName) throws IOException {

                if (result.getQueryEvent().equals(FileCache.QueryEvent.HIT)) {
                    if (result.getCachedFile().exists() && result.getCachedFile().isDirectory()) {
                        logger.info("hit maindex dexmerge cache ->" + result.getCachedFile().getAbsolutePath());
                        org.apache.commons.io.FileUtils.copyDirectory(result.getCachedFile(), outputDir);
                    } else if (result.getCachedFile().exists() && result.getCachedFile().isFile()) {
//                        File[] dexs = result.getCachedFile().getParentFile().listFiles((dir, name) -> name.endsWith(".dex"));
//                        if (dexs != null && dexs.length > 0) {
//                            for (File dex : dexs) {
                                logger.info("hit maindex dexmerge cache ->" + result.getCachedFile().getAbsolutePath());
                                org.apache.commons.io.FileUtils.copyFile(result.getCachedFile(), new File(outputDir,CLASSES_DEX));
//                            }

//                        }
                    }
                } else {
                    logger.info("miss maindex dexmerge cache -> null");

                }
            }
        };
    }

    @Override
    public void merge(TransformInvocation transformInvocation) {

        if (dexMerger == DexMergerTool.D8) {
            logger.info("D8 is used to merge dex.");
        }

        TransformOutputProvider outputProvider = transformInvocation.getOutputProvider();
        Collection<TransformInput> transformInputs = transformInvocation.getInputs();

        List<File> mainDexFiles = new ArrayList<>();

        final File[][] mergeDexs = {new File[0]};

        File outputDir =
                getDexOutputLocation(outputProvider, "main", TransformManager.SCOPE_FULL_PROJECT);
        // this deletes and creates the dir for the output
        try {
            FileUtils.cleanOutputDir(outputDir);
        } catch (IOException e) {
            e.printStackTrace();
        }

        variantOutputContext.setDexMergeFolder(outputDir);

        transformInputs.forEach((TransformInput transformInput) -> {
            File file = (File) ReflectUtils.getField(transformInput, "optionalRootLocation");
            if (file != null && file.exists()) {
                mainDexFiles.addAll(org.apache.commons.io.FileUtils.listFiles(file, new String[]{"jar", "dex"}, true));
                mergeDexs[0] = file.listFiles(pathname -> pathname.getName().endsWith(".jar") || pathname.isDirectory());
            }
        });


        merge(mainDexFiles,outputDir,mergeDexs);

    }

    public void merge(List<File>mainDexFiles,File outputDir,File[][] mergeDexs) {
        sort(mainDexFiles);
        FileCache.Inputs buildCacheInputs =
                getBuildCacheInputs(
                        mainDexFiles, dexingType, dexMerger, mainDexListFile == null ? null : mainDexListFile.getSingleFile(), minSdkVersion, isDebuggable, "maindex", ID);
        ProcessOutput output = outputHandler.createOutput();
        FileCache fileCache = BuildCacheUtils.createBuildCacheIfEnabled(variantOutputContext.getVariantContext().getProject(), variantOutputContext.getScope().getGlobalScope().getProjectOptions());
        if (fileCache == null){
            try {
                fileCache = FileCache.getInstanceWithMultiProcessLocking(new File(AndroidLocation.getFolder(),"atlas-buildCache"));
            } catch (AndroidLocation.AndroidLocationException e) {
                e.printStackTrace();
            }
        }

        if (variantOutputContext.getVariantContext().getAtlasExtension().getTBuildConfig().getMergeBundlesDex()){
            allDexsArchives.addAll(Arrays.asList(mergeDexs[0]));
            return;
        }

        try {
            FileCache.QueryResult result = fileCache.createFileInCacheIfAbsent(
                    buildCacheInputs,
                    in -> {
                        List<ForkJoinTask<Void>> mergeTasks = new ArrayList();
                        if (dexingType == DexingType.NATIVE_MULTIDEX) {
                            mergeTasks.addAll(
                                    handleNativeMultiDex(
                                            Arrays.asList(mergeDexs[0]),
                                            output,
                                            outputDir,
                                            mainDexListFile == null ? null : mainDexListFile.getSingleFile()));
                        } else {
                            mergeTasks.addAll(
                                    handleLegacyAndMonoDex(
                                            Arrays.asList(mergeDexs[0]), output, outputDir, mainDexListFile == null ? null : mainDexListFile.getSingleFile()));
                        }

                        mergeTasks.forEach(voidForkJoinTask -> voidForkJoinTask.join());

                        cacheHandler.handleMissActionResult(outputDir, in);

                        if (output != null) {
                            try {
                                outputHandler.handleOutput(output);
                            } catch (ProcessException e) {
                                // ignore this one
                            }
                        }
                    });

            cacheHandler.handleQueryResult(result, outputDir, "maindex");


        }catch (Exception e){
            e.printStackTrace();
        }
    }





}
