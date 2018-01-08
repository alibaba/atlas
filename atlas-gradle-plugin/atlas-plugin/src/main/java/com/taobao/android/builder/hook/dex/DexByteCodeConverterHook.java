package com.taobao.android.builder.hook.dex;

import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.internal.api.AppVariantContext;
import com.android.build.gradle.internal.api.AppVariantOutputContext;
import com.android.build.gradle.internal.api.AwbTransform;
import com.android.build.gradle.internal.api.VariantContext;
import com.android.builder.core.*;
import com.android.builder.dexing.DexArchiveMergerException;
import com.android.builder.dexing.DexingType;
import com.android.builder.sdk.TargetInfo;
import com.android.ide.common.blame.Message;
import com.android.ide.common.blame.ParsingProcessOutputHandler;
import com.android.ide.common.blame.parser.DexParser;
import com.android.ide.common.blame.parser.ToolOutputParser;
import com.android.ide.common.internal.WaitableExecutor;
import com.android.ide.common.process.*;
import com.android.utils.ILogger;
import com.android.utils.NdkUtils;
import com.google.common.collect.Lists;
import com.taobao.android.builder.AtlasBuildContext;
import com.taobao.android.builder.dependency.AtlasDependencyTree;
import com.taobao.android.builder.dependency.model.AwbBundle;
import com.taobao.android.builder.tools.FileNameUtils;
import com.taobao.android.builder.tools.MD5Util;
import com.taobao.android.builder.tools.cache.FileCacheCenter;
import com.taobao.android.builder.tools.cache.FileCacheException;
import org.apache.commons.compress.compressors.FileNameUtil;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


/**
 * @author lilong
 * @create 2017-05-12 Morning in the afternoon
 */

public class DexByteCodeConverterHook extends DexByteCodeConverter {

    private VariantContext variantContext;

    private AppVariantOutputContext variantOutputContext;

    private ILogger logger;

    private String type = "main-dex-dx";

    Collection<File> inputFile = new ArrayList<>();

    Pattern pattern = Pattern.compile("\\d+.dex");

    private WaitableExecutor waitableExecutor = WaitableExecutor.useGlobalSharedThreadPool();

    ForkJoinPool mainforkJoinPool = null;


    public DexByteCodeConverterHook(VariantContext variantContext, AppVariantOutputContext variantOutputContext, ILogger logger, TargetInfo targetInfo, JavaProcessExecutor javaProcessExecutor, boolean verboseExec, ErrorReporter errorReporter) {
        super(logger, targetInfo, javaProcessExecutor, verboseExec, errorReporter);
        this.variantContext = variantContext;
        this.variantOutputContext = variantOutputContext;
        this.logger = logger;
    }


    //    @Override
//    public void runDexer(DexProcessBuilder builder, DexOptions dexOptions, ProcessOutputHandler processOutputHandler) throws ProcessException, IOException, InterruptedException {
//        builder.addInputs(inputFile);
//        super.runDexer(builder,dexOptions,processOutputHandler);
//
//
//    }
    @Override
    public void convertByteCode(Collection<File> inputs, File outDexFolder, boolean multidex, File mainDexList, DexOptions dexOptions, ProcessOutputHandler processOutputHandler, int minSdkVersion) throws IOException, InterruptedException, ProcessException {
        AtlasDependencyTree atlasDependencyTree = AtlasBuildContext.androidDependencyTrees.get(
                variantContext.getVariantName());

        if (null != atlasDependencyTree) {

            ProcessOutputHandler outputHandler =
                    new ParsingProcessOutputHandler(
                            new ToolOutputParser(new DexParser(), Message.Kind.ERROR, LoggerWrapper.getLogger(DexByteCodeConverterHook.class)),
                            new ToolOutputParser(new DexParser(), LoggerWrapper.getLogger(DexByteCodeConverterHook.class)),
                            AtlasBuildContext.androidBuilderMap.get(variantContext.getProject()).getErrorReporter());


            for (final AwbBundle awbBundle : atlasDependencyTree.getAwbBundles()) {
                waitableExecutor.execute((Callable<Void>) () -> {
                            try {

                                long start = System.currentTimeMillis();
                                //create dex
                                File dexOutputFile = ((AppVariantContext) variantContext).getAwbDexOutput(awbBundle.getName());
                                if (dexOutputFile.exists()) {
                                    FileUtils.cleanDirectory(dexOutputFile);
                                } else {
                                    FileUtils.forceMkdir(dexOutputFile);
                                }

                                // if some of our .jar input files exist, just reset the inputDir to null
                                AwbTransform awbTransform = variantOutputContext.getAwbTransformMap()
                                        .get(awbBundle.getName());
                                List<File> inputFiles = new ArrayList<File>();
                                inputFiles.addAll(awbTransform.getInputFiles());
                                inputFiles.addAll(awbTransform.getInputLibraries());
                                if (null != awbTransform.getInputDir()) {
                                    inputFiles.add(awbTransform.getInputDir());
                                }
                                AtlasBuildContext.androidBuilderMap.get(variantContext.getProject())
                                        .convertByteCode(inputFiles,
                                                dexOutputFile,
                                                false,
                                                null,
                                                dexOptions,
                                                outputHandler, true);

                            } catch (Exception e) {

                            }
                            return null;


                        }
                );
            }
        }

        File tempDexFolder = null;

        inputFile = AtlasBuildContext.atlasMainDexHelper.getAllMainDexJars();

        if (!multidex) {
            waitableExecutor.execute((Callable<Void>) () -> {
                DexByteCodeConverterHook.super.convertByteCode(inputs, outDexFolder, multidex, mainDexList, dexOptions, processOutputHandler, minSdkVersion);
                return null;
            });
        } else {

            tempDexFolder = variantOutputContext.getMainDexOutDir();
            if (tempDexFolder.exists()){
                FileUtils.cleanDirectory(tempDexFolder);
            }
            for (File file : inputFile) {

                String key = null;
                try {
                     key= MD5Util.getMD5(dexOptions.getAdditionalParameters().toArray(new String[0]).toString());
                } catch (Exception e) {
                    e.printStackTrace();
                }
                try {
                    key = MD5Util.getMD5(MD5Util.getFileMD5(file)+key);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                File outPutFolder = new File(tempDexFolder, FilenameUtils.getBaseName(file.getName()));
                if (!outPutFolder.exists()){
                    outPutFolder.mkdirs();
                }
                File outDexFile = new File(outPutFolder,"classes.dex");

                try {
                    FileCacheCenter.fetchFile(type,key,false,false,outDexFile);
                } catch (FileCacheException e) {
                    e.printStackTrace();
                }
                if (outDexFile.exists()){
                    logger.info("hit cache dexFile:"+outDexFile.getAbsolutePath());

                    continue;
                }

                logger.info("input dexFile:"+file.getAbsolutePath());



                DexByteCodeConverterHook.super.convertByteCode(Arrays.asList(file), outPutFolder, false, mainDexList, dexOptions, processOutputHandler, minSdkVersion);

                if (outDexFile.exists()) {
                        try {
                            logger.info("cache dexFile:"+outDexFile.getAbsolutePath());
                            FileCacheCenter.cacheFile(type, key, outDexFile, false);
                        } catch (FileCacheException e) {
                            e.printStackTrace();
                        }
                    }

                }

            List<Path> dexPaths = new ArrayList<>();
            Collection<File> dexFiles = FileUtils.listFiles(tempDexFolder,new String[]{"dex"},true);
            if (dexFiles != null) {
                dexPaths = dexFiles.stream().map(file -> file.toPath()).collect(Collectors.toList());
            }
            mainforkJoinPool = new ForkJoinPool();
            AtlasDexArchiveMerger atlasDexArchiveMerger = new AtlasDexArchiveMerger(mainforkJoinPool);
            try {
                atlasDexArchiveMerger.mergeDexArchives(dexPaths, outDexFolder.toPath(), mainDexList.toPath(), DexingType.LEGACY_MULTIDEX);
            } catch (DexArchiveMergerException e) {
                e.printStackTrace();
            }

        }

         waitableExecutor.waitForTasksWithQuickFail(true);

        if (mainforkJoinPool != null) {
            mainforkJoinPool.awaitTermination(1, TimeUnit.MINUTES);
            FileUtils.deleteDirectory(tempDexFolder);
        }
    }

    @Override
    public void runDexer(DexProcessBuilder builder, DexOptions dexOptions, ProcessOutputHandler processOutputHandler) throws ProcessException, IOException, InterruptedException {
        if (builder.getInputs() == null || builder.getInputs().size() == 0){
            return;
        }
        super.runDexer(builder, dexOptions, processOutputHandler);
    }
}
