package com.taobao.android.builder.hook.dex;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.BuildCacheUtils;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.internal.api.AppVariantContext;
import com.android.build.gradle.internal.api.AppVariantOutputContext;
import com.android.build.gradle.internal.api.AwbTransform;
import com.android.build.gradle.internal.api.VariantContext;
import com.android.build.gradle.options.BooleanOption;
import com.android.builder.core.*;
import com.android.builder.dexing.*;
import com.android.builder.internal.compiler.DexWrapper;
import com.android.builder.sdk.TargetInfo;
import com.android.builder.utils.ExceptionRunnable;
import com.android.builder.utils.FileCache;
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
import com.taobao.android.builder.tasks.transform.dex.AtlasDexMerger;
import com.taobao.android.builder.tools.FileNameUtils;
import com.taobao.android.builder.tools.JarUtils;
import com.taobao.android.builder.tools.MD5Util;
import com.taobao.android.builder.tools.cache.FileCacheCenter;
import com.taobao.android.builder.tools.cache.FileCacheException;
import com.taobao.android.builder.tools.multidex.FastMultiDexer;
import it.unimi.dsi.fastutil.Hash;
import org.apache.commons.compress.compressors.FileNameUtil;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.gradle.caching.configuration.BuildCache;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;


/**
 * @author lilong
 * @create 2017-05-12 Morning in the afternoon
 */

public class DexByteCodeConverterHook extends DexByteCodeConverter {

    private VariantContext variantContext;

    private Boolean mIsDexInProcess = null;

    private final TargetInfo mTargetInfo;

    public static final int MAX_CLASSES = 3000;

    private AppVariantOutputContext variantOutputContext;

    private final JavaProcessExecutor mJavaProcessExecutor;

    private List<Future> futureList = new ArrayList<>();

    private static final Object LOCK_FOR_DEX = new Object();

    private static final AtomicInteger DEX_PROCESS_COUNT = new AtomicInteger(4);


    private static ExecutorService sDexExecutorService = null;

    AtlasDexArchiveMerger atlasDexArchiveMerger;
    private ILogger logger;

    private String type = "main-dex-dx-1.0";

    Collection<File> inputFile = new ArrayList<>();

    Pattern pattern = Pattern.compile("\\d+.dex");

    private AtomicInteger atomicInteger = new AtomicInteger();

    private Set<AwbBundle> mBundleSets = new HashSet<>();

    List<Path> dexPaths = new ArrayList<>();


    private WaitableExecutor waitableExecutor = WaitableExecutor.useGlobalSharedThreadPool();

    ForkJoinPool mainforkJoinPool = null;
    FileCache fileCache = null;
    FileCache.Inputs.Builder globalCacheBuilder = null;

    private List<Throwable> failures = new ArrayList<>();


    public DexByteCodeConverterHook(VariantContext variantContext, AppVariantOutputContext variantOutputContext, ILogger logger, TargetInfo targetInfo, JavaProcessExecutor javaProcessExecutor, boolean verboseExec, ErrorReporter errorReporter) {
        super(logger, targetInfo, javaProcessExecutor, verboseExec, errorReporter);
        this.variantContext = variantContext;
        this.variantOutputContext = variantOutputContext;
        this.logger = logger;
        this.mTargetInfo = targetInfo;
        this.mJavaProcessExecutor = javaProcessExecutor;
        if (variantContext.getScope().getGlobalScope()
                .getProjectOptions()
                .get(BooleanOption.ENABLE_INTERMEDIATE_ARTIFACTS_CACHE)) {
            fileCache = variantContext.getScope().getGlobalScope().getBuildCache();
        }
    }


    //    @Override
//    public void runDexer(DexProcessBuilder builder, DexOptions dexOptions, ProcessOutputHandler processOutputHandler) throws ProcessException, IOException, InterruptedException {
//        builder.addInputs(inputFile);
//        super.runDexer(builder,dexOptions,processOutputHandler);
//
//
//    }
    @Override
    public void convertByteCode(Collection<File> inputs, File outDexFolder, boolean multidex, final File mainDexList, DexOptions dexOptions, ProcessOutputHandler processOutputHandler, int minSdkVersion) throws IOException, InterruptedException, ProcessException {
        logger.warning("outDexFolder:"+outDexFolder.getAbsolutePath());
        FileUtils.forceMkdir(outDexFolder);
//        outDexFolder.mkdirs();
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
                                if (null != awbTransform.getInputDirs()) {
                                    inputFiles.addAll(awbTransform.getInputDirs());
                                }


                                if (variantContext.getScope().getDexer() == DexerTool.DX) {
                                    AtlasBuildContext.androidBuilderMap.get(variantContext.getProject())
                                            .convertByteCode(inputFiles,
                                                    dexOutputFile,
                                                    false,
                                                    null,
                                                    dexOptions,
                                                    outputHandler, true);
                                } else if (variantContext.getScope().getDexer() == DexerTool.D8) {


                                    new AtlasD8Creator(inputFiles, ((AppVariantContext) variantContext).getAwbDexAchiveOutput(awbBundle), multidex, mainDexList, dexOptions, minSdkVersion, fileCache, processOutputHandler, variantContext, variantOutputContext).create(awbBundle);
                                }

                                if (awbBundle.isMBundle) {
                                    mBundleSets.add(awbBundle);
                                }

                            } catch (Exception e) {
                                throw new ProcessException(awbBundle.getName(), e);

                            }
                            return null;


                        }
                );
            }
        }

        File tempDexFolder = null;

        inputFile.addAll(AtlasBuildContext.atlasMainDexHelperMap.get(variantContext.getVariantName()).getAllMainDexJars());
        inputFile.addAll(AtlasBuildContext.atlasMainDexHelperMap.get(variantContext.getVariantName()).getInputDirs());

        logger.warning("maindex inputFile size :" + inputFile.size());

        if (variantContext.getScope().getDexer() == DexerTool.D8) {

            AtlasD8Creator atlasD8Creator = new AtlasD8Creator(inputs, ((AppVariantContext) variantContext).getMainDexAchive(), multidex, mainDexList, dexOptions, minSdkVersion, fileCache, processOutputHandler, variantContext, variantOutputContext);
            atlasD8Creator.setMainDexOut(outDexFolder);
            atlasD8Creator.create(new AwbBundle());
            return;

        }

        initDexExecutorService(dexOptions);

        if (!multidex) {

            if (fileCache != null && globalCacheBuilder == null) {
                globalCacheBuilder = new FileCache.Inputs.Builder(FileCache.Command.PREDEX_LIBRARY)
                        .putBoolean("multidex", false)
                        .putLong("minisdk", minSdkVersion)
                        .putString("dexoptions", dexOptions.getAdditionalParameters().toString())
                        .putBoolean("jumbomode", dexOptions.getJumboMode())
                        .putString("type", type);

                inputFile = new ArrayList<>(inputFile);
                Collections.sort((List<File>) inputFile);
                FileCache.Inputs inputsKey = globalCacheBuilder.putString("md5", MD5Util.getFileMd5(inputFile)).build();

                try {
                    fileCache.createFile(outDexFolder, inputsKey, () -> {
                        logger.warning("dex inputFile missCache: " + inputFile.toString());
                        outDexFolder.mkdirs();
                        DexByteCodeConverterHook.super.convertByteCode(inputFile, outDexFolder, multidex, mainDexList, dexOptions, processOutputHandler, minSdkVersion);
                    });
                } catch (ExecutionException e) {
                    e.printStackTrace();
                    failures.add(e);
                }

            } else {

                DexByteCodeConverterHook.super.convertByteCode(inputFile, outDexFolder, multidex, mainDexList, dexOptions, processOutputHandler, minSdkVersion);
            }
//            try {
//                for (Future future : futureList) {
//                    future.get();
//                }
//            } catch (Exception e) {
//                throw new ProcessException(e);
//            }

        } else {

            if (mainDexList != null && !mainDexList.exists()) {
                generateMainDexList(mainDexList);
            }

            tempDexFolder = variantOutputContext.getMainDexOutDir();
            if (tempDexFolder.exists()) {
                FileUtils.cleanDirectory(tempDexFolder);
            }

            File finalTempDexFolder = tempDexFolder;
            if (fileCache != null && globalCacheBuilder == null) {
                if (mainDexList!= null) {
                    globalCacheBuilder = new FileCache.Inputs.Builder(FileCache.Command.PREDEX_LIBRARY)
                            .putBoolean("multidex", true)
                            .putFile("multidexlist", mainDexList, FileCache.FileProperties.HASH)
                            .putLong("minisdk", minSdkVersion)
                            .putString("dexoptions", dexOptions.getAdditionalParameters().toString())
                            .putBoolean("jumbomode", dexOptions.getJumboMode())
                            .putString("type", type);
                }else {
                    globalCacheBuilder = new FileCache.Inputs.Builder(FileCache.Command.PREDEX_LIBRARY)
                            .putBoolean("multidex", true)
                            .putLong("minisdk", minSdkVersion)
                            .putString("dexoptions", dexOptions.getAdditionalParameters().toString())
                            .putBoolean("jumbomode", dexOptions.getJumboMode())
                            .putString("type", type);
                }

            }

            if (inputFile.size() ==1 ){
                splitFile();
            }
            inputFile.parallelStream().forEach(file -> {
                File outPutFolder = new File(finalTempDexFolder, FilenameUtils.getBaseName(file.getName()));
                if (globalCacheBuilder != null && file.isFile()) {
                    FileCache.Inputs.Builder builder = copyOf(globalCacheBuilder);
                    FileCache.Inputs cacheInputs = null;
                    if (file.isFile()) {
                        cacheInputs = builder.putFile("hash", file, FileCache.FileProperties.HASH).build();
                    } else {
                        Collection<File> files = FileUtils.listFiles(file, new String[]{"class"}, true);
                        Collections.sort((List<File>) files);
                        cacheInputs = builder.putString("hash", MD5Util.getFileMd5(files)).build();
                    }
                    try {
                        fileCache.createFile(outPutFolder, cacheInputs, () -> {
                            logger.warning("maindex inputFile missCache:" + file.getAbsolutePath());
                            outPutFolder.mkdirs();
                            DexByteCodeConverterHook.super.convertByteCode(Arrays.asList(file), outPutFolder, true, mainDexList, dexOptions, processOutputHandler, minSdkVersion);
                        });
                    } catch (Exception e) {
                        failures.add(e);
                        e.printStackTrace();
                    }

                } else {
                    logger.warning("maindex inputFile:" + file.getAbsolutePath());
                    outPutFolder.mkdirs();
                    try {
                        DexByteCodeConverterHook.super.convertByteCode(Arrays.asList(file), outPutFolder, true, mainDexList, dexOptions, processOutputHandler, minSdkVersion);
                    } catch (Exception e) {
                        e.printStackTrace();
                        failures.add(e);
                    }
                }


            });

            if (failures.size() > 0) {
                throw new ProcessException(failures.get(0));
            }


//            for (Future future : futureList) {
//                try {
//                    future.get();
//                } catch (ExecutionException e) {
//                    throw new ProcessException(e);
//                }
//            }
//
//            inputFile.stream().parallel().forEach(new Consumer<File>() {
//                @Override
//                public void accept(File file) {
//                    fileCache.createFile()fileCacheMap.get(file)
//                }
//            });


            Collection<File> dexFiles = FileUtils.listFiles(tempDexFolder, new String[]{"dex"}, true);
            if (dexFiles != null) {
                logger.warning("maindex outDexFiles size:" + dexFiles.size());
                dexPaths = dexFiles.stream().map(file -> file.toPath()).collect(Collectors.toList());
            }
            mainforkJoinPool = new ForkJoinPool();
            atlasDexArchiveMerger = new AtlasDexArchiveMerger(mainforkJoinPool);
            if (!variantContext.getAtlasExtension().getTBuildConfig().getMergeBundlesDex()) {
                try {
                    atlasDexArchiveMerger.mergeDexArchives(dexPaths, outDexFolder.toPath(), mainDexList ==null? null:mainDexList.toPath(), DexingType.LEGACY_MULTIDEX);
                } catch (DexArchiveMergerException e) {
                    throw new ProcessException(e);
                }
            }

        }

        waitableExecutor.waitForTasksWithQuickFail(true);

        atomicInteger.set(FileUtils.listFiles(outDexFolder, new String[]{"dex"}, true).size());

        logger.warning("maindex final dexs size:" + atomicInteger.get());


        for (AwbBundle bundle : mBundleSets) {
            File awbDex = new File(((AppVariantContext) variantContext).getAwbDexOutput(bundle.getName()), "classes.dex");
            if (awbDex.exists() && !variantContext.getAtlasExtension().getTBuildConfig().getMergeBundlesDex()) {
                FileUtils.moveFile(awbDex, new File(outDexFolder, "classes" + atomicInteger.incrementAndGet() + ".dex"));
            } else if (awbDex.exists() && variantContext.getAtlasExtension().getTBuildConfig().getMergeBundlesDex()) {
                dexPaths.add(awbDex.toPath());
            } else {
                logger.warning(awbDex.getAbsoluteFile() + " is not exist!");
            }
        }

        if (variantContext.getAtlasExtension().getTBuildConfig().getMergeBundlesDex()) {
            try {
                atlasDexArchiveMerger.mergeDexArchives(dexPaths, outDexFolder.toPath(), null, DexingType.LEGACY_MULTIDEX);
            } catch (DexArchiveMergerException e) {
                e.printStackTrace();
            } finally {

            }
        }

        if (tempDexFolder != null && tempDexFolder.exists()) {
            FileUtils.deleteDirectory(tempDexFolder);
        }


    }

    private void splitFile() {
        inputFile = new ArrayList<>(inputFile);
        File file = inputFile.iterator().next();
        inputFile.clear();
        try {
            JarUtils.splitMainJar((List<File>) inputFile,file,1,MAX_CLASSES);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }



    private void generateEmptyMainDexList(File mainDexList) {
        try {
            mainDexList.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private FileCache.Inputs.Builder copyOf(FileCache.Inputs.Builder globalCacheBuilder) {
        FileCache.Inputs.Builder builder = new FileCache.Inputs.Builder(FileCache.Command.PREDEX_LIBRARY).putString("globalCacheBuilder", globalCacheBuilder.build().toString());
        return builder;
    }

    private void generateMainDexList(File mainDexListFile) {
        Collection<File> inputs = AtlasBuildContext.atlasMainDexHelperMap.get(variantContext.getVariantName()).getAllMainDexJars();
        inputs.addAll(AtlasBuildContext.atlasMainDexHelperMap.get(variantContext.getVariantName()).getInputDirs());
        FastMultiDexer fastMultiDexer = (FastMultiDexer) AtlasBuildContext.androidBuilderMap.get(variantContext.getScope().getGlobalScope().getProject()).multiDexer;
        if (fastMultiDexer == null) {
            fastMultiDexer = new FastMultiDexer((AppVariantContext) variantContext);
        }
        Collection<File> files = null;
        try {
            files = fastMultiDexer.repackageJarList(inputs, mainDexListFile, variantContext.getScope().getVariantData().getName().toLowerCase().endsWith("release"));
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (files != null && files.size() > 0) {
            AtlasBuildContext.atlasMainDexHelperMap.get(variantContext.getVariantName()).addAllMainDexJars(files);

        }
    }

    @Override
    public void runDexer(DexProcessBuilder builder, DexOptions dexOptions, ProcessOutputHandler processOutputHandler) throws ProcessException, IOException, InterruptedException {
        if (builder.getInputs() == null || builder.getInputs().size() == 0) {
            return;
        }


        if (dexOptions.getAdditionalParameters().contains("--no-optimize")) {
            logger.warning(DefaultDexOptions.OPTIMIZE_WARNING);
        }

        if (shouldDexInProcess(dexOptions)) {
            dexInProcess(builder, dexOptions, processOutputHandler);
        } else {
            dexOutOfProcess(builder, dexOptions, processOutputHandler);
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

    private void dexInProcess(
            @NonNull final DexProcessBuilder builder,
            @NonNull final DexOptions dexOptions,
            @NonNull final ProcessOutputHandler outputHandler)
            throws IOException, ProcessException {
        final String submission = Joiner.on(',').join(builder.getInputs());
        logger.verbose("Dexing in-process : %1$s", submission);
        try {
            //noinspection FieldAccessNotGuarded
            sDexExecutorService
                    .submit(
                            () -> {
                                Stopwatch stopwatch = Stopwatch.createStarted();
                                ProcessResult result =
                                        DexWrapper.run(builder, dexOptions, outputHandler);
                                result.assertNormalExitValue();
                                logger.verbose(
                                        "Dexing %1$s took %2$s.", submission, stopwatch.toString());
                                return null;
                            }).get()
            ;
        } catch (Exception e) {
            throw new ProcessException(new ArrayList<>(builder.getInputs()).toString(),e);
        }
    }

    private void dexOutOfProcess(
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
                sDexExecutorService.submit(task).get();
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
    }


}
