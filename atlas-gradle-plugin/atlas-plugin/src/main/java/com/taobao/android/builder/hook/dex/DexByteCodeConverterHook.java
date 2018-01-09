package com.taobao.android.builder.hook.dex;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.internal.api.AppVariantContext;
import com.android.build.gradle.internal.api.AppVariantOutputContext;
import com.android.build.gradle.internal.api.AwbTransform;
import com.android.build.gradle.internal.api.VariantContext;
import com.android.builder.core.*;
import com.android.builder.dexing.DexArchiveMergerException;
import com.android.builder.dexing.DexingType;
import com.android.builder.internal.compiler.DexWrapper;
import com.android.builder.sdk.TargetInfo;
import com.android.builder.utils.PerformanceUtils;
import com.android.ide.common.blame.Message;
import com.android.ide.common.blame.ParsingProcessOutputHandler;
import com.android.ide.common.blame.parser.DexParser;
import com.android.ide.common.blame.parser.ToolOutputParser;
import com.android.ide.common.internal.WaitableExecutor;
import com.android.ide.common.process.*;
import com.android.utils.ILogger;
import com.android.utils.NdkUtils;
import com.google.common.base.Joiner;
import com.google.common.base.Stopwatch;
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
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
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

    private Boolean mIsDexInProcess = null;

    private final TargetInfo mTargetInfo;

    private AppVariantOutputContext variantOutputContext;

    private final JavaProcessExecutor mJavaProcessExecutor;

    private List<Future> futureList = new ArrayList<>();

    private static final Object LOCK_FOR_DEX = new Object();

    private static final AtomicInteger DEX_PROCESS_COUNT = new AtomicInteger(4);


    private static ExecutorService sDexExecutorService = null;


    private ILogger logger;

    private String type = "main-dex-dx-1.0";

    Collection<File> inputFile = new ArrayList<>();

    Pattern pattern = Pattern.compile("\\d+.dex");

    private WaitableExecutor waitableExecutor = WaitableExecutor.useGlobalSharedThreadPool();

    ForkJoinPool mainforkJoinPool = null;


    public DexByteCodeConverterHook(VariantContext variantContext, AppVariantOutputContext variantOutputContext, ILogger logger, TargetInfo targetInfo, JavaProcessExecutor javaProcessExecutor, boolean verboseExec, ErrorReporter errorReporter) {
        super(logger, targetInfo, javaProcessExecutor, verboseExec, errorReporter);
        this.variantContext = variantContext;
        this.variantOutputContext = variantOutputContext;
        this.logger = logger;
        this.mTargetInfo = targetInfo;
        this.mJavaProcessExecutor = javaProcessExecutor;
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
            if (tempDexFolder.exists()) {
                FileUtils.cleanDirectory(tempDexFolder);
            }

            initDexExecutorService(dexOptions);

            for (File file : inputFile) {

                String key = null;
                String key2 = null;
                try {
                    key = MD5Util.getMD5(dexOptions.getAdditionalParameters().toString());
                } catch (Exception e) {
                    e.printStackTrace();
                }
                try {
                    key = MD5Util.getMD5(MD5Util.getFileMD5(file) + key);
                    key2 = MD5Util.getMD5(MD5Util.getFileMD5(file) + key + 2);

                } catch (Exception e) {
                    e.printStackTrace();
                }
                File outPutFolder = new File(tempDexFolder, FilenameUtils.getBaseName(file.getName()));
                if (!outPutFolder.exists()) {
                    outPutFolder.mkdirs();
                }
                File outDexFile = new File(outPutFolder, "classes.dex");
                File outDexFile2 = new File(outPutFolder, "classes2.dex");


                try {
                    FileCacheCenter.fetchFile(type, key, false, false, outDexFile);
                    FileCacheCenter.fetchFile(type, key2, false, false, outDexFile2);

                } catch (FileCacheException e) {
                    e.printStackTrace();
                }
                if (outDexFile.exists()) {
                    logger.info("hit cache dexFile:" + outDexFile.getAbsolutePath());
                    continue;
                }

                DexByteCodeConverterHook.super.convertByteCode(Arrays.asList(file), outPutFolder, true, mainDexList, dexOptions, processOutputHandler, minSdkVersion);

                if (outDexFile.exists()) {
                    try {
                        logger.info("cache dexFile:" + outDexFile.getAbsolutePath());
                        FileCacheCenter.cacheFile(type, key, outDexFile, false);
                    } catch (FileCacheException e) {
                        e.printStackTrace();
                    }
                }

                if (outDexFile2.exists()) {
                    try {
                        logger.info("cache dexFile:" + outDexFile2.getAbsolutePath());
                        FileCacheCenter.cacheFile(type, key2, outDexFile2, false);
                    } catch (FileCacheException e) {
                        e.printStackTrace();
                    }
                }
            }
            try {

                for (Future future : futureList) {
                    future.get();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }


            List<Path> dexPaths = new ArrayList<>();
            Collection<File> dexFiles = FileUtils.listFiles(tempDexFolder, new String[]{"dex"}, true);
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
        if (tempDexFolder.exists()) {
            FileUtils.deleteDirectory(tempDexFolder);
        }
        waitableExecutor.waitForTasksWithQuickFail(true);

    }

    @Override
    public void runDexer(DexProcessBuilder builder, DexOptions dexOptions, ProcessOutputHandler processOutputHandler) throws ProcessException, IOException, InterruptedException {
        if (builder.getInputs() == null || builder.getInputs().size() == 0) {
            return;
        }
        if (dexOptions.getAdditionalParameters().contains("--no-optimize")) {
            logger.warning(DefaultDexOptions.OPTIMIZE_WARNING);
        }

        Future future = null;
        if (shouldDexInProcess(dexOptions)) {
            future = dexInProcess(builder, dexOptions, processOutputHandler);
        } else {
            future = dexOutOfProcess(builder, dexOptions, processOutputHandler);
        }

        if (future != null) {
            futureList.add(future);
        }

    }

    private void initDexExecutorService(@NonNull DexOptions dexOptions) {
        synchronized (LOCK_FOR_DEX) {
            if (sDexExecutorService == null) {
                if (dexOptions.getMaxProcessCount() != null) {
                    DEX_PROCESS_COUNT.set(dexOptions.getMaxProcessCount());
                }
                logger.verbose(
                        "Allocated dexExecutorService of size %1$d.",
                        DEX_PROCESS_COUNT.get());
                sDexExecutorService = Executors.newFixedThreadPool(DEX_PROCESS_COUNT.get());
            } else {
                // check whether our executor service has the same number of max processes as
                // this module requests, and print a warning if necessary.
                if (dexOptions.getMaxProcessCount() != null
                        && dexOptions.getMaxProcessCount() != DEX_PROCESS_COUNT.get()) {
                    logger.warning(
                            "dexOptions is specifying a maximum number of %1$d concurrent dx processes,"
                                    + " but the Gradle daemon was initialized with %2$d.\n"
                                    + "To initialize with a different maximum value,"
                                    + " first stop the Gradle daemon by calling ‘gradlew —-stop’.",
                            dexOptions.getMaxProcessCount(),
                            DEX_PROCESS_COUNT.get());
                }
            }
        }
    }

    public synchronized boolean shouldDexInProcess(@NonNull DexOptions dexOptions) {
        if (mIsDexInProcess != null) {
            return mIsDexInProcess;
        }
        if (!dexOptions.getDexInProcess()) {
            mIsDexInProcess = false;
            return false;
        }

        // Requested memory for dex.
        Long requestedHeapSize;
        if (dexOptions.getJavaMaxHeapSize() != null) {
            requestedHeapSize = PerformanceUtils.parseSizeToBytes(dexOptions.getJavaMaxHeapSize());

            if (requestedHeapSize == null) {
                logger.warning(
                        "Unable to parse dex options size parameter '%1$s', assuming %2$s bytes.",
                        dexOptions.getJavaMaxHeapSize(),
                        DEFAULT_DEX_HEAP_SIZE);
                requestedHeapSize = DEFAULT_DEX_HEAP_SIZE;
            }
        } else {
            requestedHeapSize = DEFAULT_DEX_HEAP_SIZE;
        }
        // Approximate heap size requested.
        long requiredHeapSizeHeuristic = requestedHeapSize + PerformanceUtils.NON_DEX_HEAP_SIZE;
        // Get the heap size defined by the user. This value will be compared with
        // requiredHeapSizeHeuristic, which we suggest the user set in their gradle.properties file.
        long maxMemory = PerformanceUtils.getUserDefinedHeapSize();

        if (requiredHeapSizeHeuristic > maxMemory) {
            String dexOptionsComment = "";
            if (dexOptions.getJavaMaxHeapSize() != null) {
                dexOptionsComment = String.format(
                        " (based on the dexOptions.javaMaxHeapSize = %s)",
                        dexOptions.getJavaMaxHeapSize());
            }

            logger.warning("\nRunning dex as a separate process.\n\n"
                            + "To run dex in process, the Gradle daemon needs a larger heap.\n"
                            + "It currently has %1$d MB.\n"
                            + "For faster builds, increase the maximum heap size for the "
                            + "Gradle daemon to at least %2$s MB%3$s.\n"
                            + "To do this set org.gradle.jvmargs=-Xmx%2$sM in the "
                            + "project gradle.properties.\n"
                            + "For more information see "
                            + "https://docs.gradle.org/current/userguide/build_environment.html\n",
                    maxMemory / (1024 * 1024),
                    // Add -1 and + 1 to round up the division
                    ((requiredHeapSizeHeuristic - 1) / (1024 * 1024)) + 1,
                    dexOptionsComment);
            mIsDexInProcess = false;
            return false;
        }
        mIsDexInProcess = true;
        return true;

    }

    private Future dexInProcess(
            @NonNull final DexProcessBuilder builder,
            @NonNull final DexOptions dexOptions,
            @NonNull final ProcessOutputHandler outputHandler)
            throws IOException, ProcessException {
        final String submission = Joiner.on(',').join(builder.getInputs());
        logger.verbose("Dexing in-process : %1$s", submission);
        try {
            //noinspection FieldAccessNotGuarded
            return sDexExecutorService
                    .submit(
                            () -> {
                                Stopwatch stopwatch = Stopwatch.createStarted();
                                ProcessResult result =
                                        DexWrapper.run(builder, dexOptions, outputHandler);
                                result.assertNormalExitValue();
                                logger.verbose(
                                        "Dexing %1$s took %2$s.", submission, stopwatch.toString());
                                return null;
                            })
                    ;
        } catch (Exception e) {
            throw new ProcessException(e);
        }
    }

    private Future dexOutOfProcess(
            @NonNull final DexProcessBuilder builder,
            @NonNull final DexOptions dexOptions,
            @NonNull final ProcessOutputHandler processOutputHandler)
            throws ProcessException, InterruptedException {
        final String submission = Joiner.on(',').join(builder.getInputs());
        logger.verbose("Dexing out-of-process : %1$s", submission);
        try {
            Callable<Void> task = () -> {
                JavaProcessInfo javaProcessInfo =
                        builder.build(mTargetInfo.getBuildTools(), dexOptions);
                ProcessResult result =
                        mJavaProcessExecutor.execute(javaProcessInfo, processOutputHandler);
                result.rethrowFailure().assertNormalExitValue();
                return null;
            };

            // this is a hack, we always spawn a new process for dependencies.jar so it does
            // get built in parallel with the slices, this is only valid for InstantRun mode.
            if (submission.contains("dependencies.jar")) {
                task.call();
            } else {
                //noinspection FieldAccessNotGuarded
                return sDexExecutorService.submit(task);
            }
        } catch (Exception e) {
            if (builder.getMinSdkVersion() >= 24
                    && !DexProcessBuilder.isMinSdkVersionSupported(mTargetInfo.getBuildTools())) {
                logger.warning(
                        "If you are unable to fix the underlying cause of error, dx "
                                + "might have failed because default or static interface methods "
                                + "(requiring minimum sdk version 24), or signature-polymorphic "
                                + "methods (requiring minimum sdk version 26) are used.\n"
                                + "Please switch to dexing in process or update the build tools to "
                                + "%s.",
                        AndroidBuilder.DEFAULT_BUILD_TOOLS_REVISION.toString());
            }
            throw new ProcessException(e);
        }
        return null;
    }
}
