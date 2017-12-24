package com.taobao.android.builder.tasks.transform.dex;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.build.VariantOutput;
import com.android.build.api.transform.*;
import com.android.build.gradle.api.BaseVariantOutput;
import com.android.build.gradle.internal.ApkDataUtils;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.internal.api.AppVariantContext;
import com.android.build.gradle.internal.api.AwbTransform;
import com.android.build.gradle.internal.pipeline.*;
import com.android.build.gradle.internal.transforms.DexArchiveBuilderTransform;
import com.android.builder.core.DefaultDexOptions;
import com.android.builder.core.DexOptions;
import com.android.builder.core.ErrorReporter;
import com.android.builder.dexing.*;
import com.android.builder.utils.FileCache;
import com.android.dx.command.dexer.DxContext;
import com.android.ide.common.blame.Message;
import com.android.ide.common.blame.ParsingProcessOutputHandler;
import com.android.ide.common.blame.parser.DexParser;
import com.android.ide.common.blame.parser.ToolOutputParser;
import com.android.ide.common.internal.WaitableExecutor;
import com.android.ide.common.process.ProcessException;
import com.android.ide.common.process.ProcessOutput;
import com.android.ide.common.process.ProcessOutputHandler;
import com.android.utils.FileUtils;
import com.google.common.base.Preconditions;
import com.google.common.collect.*;
import com.taobao.android.builder.AtlasBuildContext;
import com.taobao.android.builder.dependency.AtlasDependencyTree;
import com.taobao.android.builder.dependency.model.AwbBundle;
import com.taobao.android.builder.tasks.app.BuildAtlasEnvTask;

import com.taobao.android.builder.tasks.transform.cache.CacheFactory;
import com.taobao.android.builder.tasks.transform.cache.DexCache;
import com.taobao.android.builder.tools.FileNameUtils;
import com.taobao.android.builder.tools.MD5Util;
import com.taobao.android.builder.tools.ReflectUtils;
import com.taobao.android.builder.tools.log.FileLogger;
import org.gradle.tooling.BuildException;
import org.gradle.workers.IsolationMode;

import javax.inject.Inject;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.function.Predicate;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;


/**
 * @author lilong
 * @create 2017-12-08 上午3:43
 */

public class AtlasDexArchiveBuilderTransform extends DexArchiveBuilderTransform {
    private static final LoggerWrapper logger =
            LoggerWrapper.getLogger(DexArchiveBuilderTransform.class);

    public static final int DEFAULT_BUFFER_SIZE_IN_KB = 100;

    public static int NUMBER_OF_BUCKETS = 1;

    private static final String CACHE_ID = "dex-archive";

    private static final String CACHE_VERSION="1.0";


    @NonNull
    private final DexOptions dexOptions;

    private DexCache dexCache;
    @NonNull
    private final ErrorReporter errorReporter;
    @VisibleForTesting
    @NonNull
    final WaitableExecutor executor;
    private final int minSdkVersion;
    @NonNull
    private final DexerTool dexer;
    @NonNull
    private final AtlasDexArchiveBuilderCacheHander cacheHandler;
    private final boolean useGradleWorkers;
    private final int inBufferSize;
    private final int outBufferSize;
    private final boolean isDebuggable;
    private final AppVariantContext variantContext;
    private final BaseVariantOutput variantOutput;
    private TransformTask transformTask;

    public AtlasDexArchiveBuilderTransform(AppVariantContext variantContext, VariantOutput variantOutput,
                                           @NonNull DexOptions dexOptions,
                                           @NonNull ErrorReporter errorReporter,
                                           @Nullable FileCache userLevelCache,
                                           int minSdkVersion,
                                           @NonNull DexerTool dexer,
                                           boolean useGradleWorkers,
                                           @Nullable Integer inBufferSize,
                                           @Nullable Integer outBufferSize,
                                           boolean isDebuggable) {

        super(dexOptions, errorReporter, userLevelCache, minSdkVersion, dexer, useGradleWorkers, inBufferSize, outBufferSize, isDebuggable);
        this.variantContext = variantContext;
        this.variantOutput = (BaseVariantOutput) variantOutput;
        this.dexOptions = dexOptions;
        this.errorReporter = errorReporter;
        this.minSdkVersion = minSdkVersion;
        this.dexer = dexer;
        this.executor = WaitableExecutor.useGlobalSharedThreadPool();
        this.cacheHandler =
                new AtlasDexArchiveBuilderCacheHander(
                        userLevelCache, dexOptions, minSdkVersion, isDebuggable, dexer);
        this.useGradleWorkers = useGradleWorkers;
        this.inBufferSize =
                (inBufferSize == null ? DEFAULT_BUFFER_SIZE_IN_KB : inBufferSize) * 1024;
        this.outBufferSize =
                (outBufferSize == null ? DEFAULT_BUFFER_SIZE_IN_KB : outBufferSize) * 1024;
        this.isDebuggable = isDebuggable;

    }

    @NonNull
    @Override
    public String getName() {
        return "dexBuilder";
    }

    @NonNull
    @Override
    public Set<QualifiedContent.ContentType> getInputTypes() {
        return TransformManager.CONTENT_CLASS;
    }

    @NonNull
    @Override
    public Set<QualifiedContent.ContentType> getOutputTypes() {
        return ImmutableSet.of(ExtendedContentType.DEX_ARCHIVE);
    }

    @NonNull
    @Override
    public Set<? super QualifiedContent.Scope> getScopes() {
        return TransformManager.SCOPE_FULL_WITH_IR_FOR_DEXING;
    }

    @NonNull
    @Override
    public Map<String, Object> getParameterInputs() {
        try {
            Map<String, Object> params = new LinkedHashMap<>(4);
            params.put("optimize", !dexOptions.getAdditionalParameters().contains("--no-optimize"));
            params.put("jumbo", dexOptions.getJumboMode());
            params.put("min-sdk-version", minSdkVersion);
            params.put("dex-builder-tool", dexer.name());

            return params;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean isIncremental() {
        return true;
    }

    @Override
    public void transform(@NonNull TransformInvocation transformInvocation)
            throws IOException {

        AtlasIntermediateStreamHelper atlasIntermediateStreamHelper = new AtlasIntermediateStreamHelper(transformTask);
        atlasIntermediateStreamHelper.replaceProvider(transformInvocation);
        AtlasBuildContext.status = AtlasBuildContext.STATUS.DEXARCHIVE;
        dexCache = (DexCache) CacheFactory.get(variantContext.getProject(),CACHE_ID,CACHE_VERSION,this,transformInvocation,DexCache.class);

        TransformOutputProvider outputProvider = transformInvocation.getOutputProvider();
        Preconditions.checkNotNull(outputProvider, "Missing output provider.");

        if (dexer == DexerTool.D8) {
            logger.info("D8 is used to build dex.");
        }

        if (dexOptions.getAdditionalParameters().contains("--no-optimize")) {
            logger.warning(DefaultDexOptions.OPTIMIZE_WARNING);
        }

        logger.verbose("Task is incremental : %b ", transformInvocation.isIncremental());
//
//        if (!transformInvocation.isIncremental()) {
//            outputProvider.deleteAll();
//            org.apache.commons.io.FileUtils.cleanDirectory(variantContext.getAwbDexAchiveOutputs());
//        }

        List<QualifiedContent> listFiles = new ArrayList<>();
        try {

            for (TransformInput input : transformInvocation.getInputs()) {
                for (DirectoryInput dirInput : input.getDirectoryInputs()) {
                    logger.verbose("Dir input %s", dirInput.getFile().toString());
                    convertToDexArchive(
                            transformInvocation.getContext(),
                            dirInput,
                            outputProvider,
                            transformInvocation.isIncremental());
                }

                for (JarInput jarInput : input.getJarInputs()) {
                    if (!inMainDex(jarInput)) {
                        listFiles.add(jarInput);
                        continue;
                    }
                    if (!validJar(jarInput)){
                        continue;
                    }
                    logger.verbose("Jar input %s", jarInput.getFile().toString());
                    List<File> dexArchives =
                            processJarInput(
                                    transformInvocation.getContext(),
                                    transformInvocation.isIncremental(),
                                    jarInput,
                                    outputProvider);
//                    cacheableItems.putAll(jarInput, dexArchives);
                }
            }


            processAwbDexArchive(transformInvocation, listFiles);
            // all work items have been submitted, now wait for completion.
            if (useGradleWorkers) {
                transformInvocation.getContext().getWorkerExecutor().await();
            } else {
                executor.waitForTasksWithQuickFail(false);
            }

            // if we are in incremental mode, delete all removed files.
            if (transformInvocation.isIncremental()) {
                for (TransformInput transformInput : transformInvocation.getInputs()) {
                    removeDeletedEntries(outputProvider, transformInput);
                }
            }

        }catch (Exception e){
            e.printStackTrace();
        }

        dexCache.saveContent();

    }

    private boolean validJar(JarInput jarInput) {
        if (computerClassCount(jarInput.getFile()) == 0){
            return false;
        }else {
            return true;
        }
    }

    private boolean inMainDex(JarInput jarInput) {
        for (BuildAtlasEnvTask.FileIdentity fileIdentity : AtlasBuildContext.mainDexJar) {
            if (fileIdentity.file.getAbsolutePath().equals(jarInput.getFile().getAbsolutePath())) {
                return true;
            }
        }
        return false;
    }

    private static void removeDeletedEntries(
            @NonNull TransformOutputProvider outputProvider, @NonNull TransformInput transformInput)
            throws IOException {
        for (DirectoryInput directoryInput : transformInput.getDirectoryInputs()) {
            File outputFile = getPreDexFolder(outputProvider, directoryInput);
            try (DexArchive output = DexArchives.fromInput(outputFile.toPath())) {
                for (Map.Entry<File, Status> fileStatusEntry :
                        directoryInput.getChangedFiles().entrySet()) {
                    if (fileStatusEntry.getValue() == Status.REMOVED) {
                        Path relativePath =
                                directoryInput
                                        .getFile()
                                        .toPath()
                                        .relativize(fileStatusEntry.getKey().toPath());
                        output.removeFile(ClassFileEntry.withDexExtension(relativePath.toString()));
                    }
                }
            }
        }
    }

    private List<File> processJarInput(
            @NonNull Context context,
            boolean isIncremental,
            @NonNull JarInput jarInput,
            TransformOutputProvider transformOutputProvider)
            throws Exception {
        if (!isIncremental) {
//            if (dexCache.getCache(jarInput).size() > 0){
//                return ImmutableList.of();
//            }
            Preconditions.checkState(
                    jarInput.getFile().exists(),
                    "File %s does not exist, yet it is reported as input. Try \n"
                            + "cleaning the build directory.",
                    jarInput.getFile().toString());
            List<File> files =  convertJarToDexArchive(context, jarInput, transformOutputProvider);
            return files;
            } else if (jarInput.getStatus() != Status.NOTCHANGED) {
            // delete all preDex jars if they exists.
            for (int bucketId = 0; bucketId < NUMBER_OF_BUCKETS; bucketId++) {
                File shardedOutput = getPreDexJar(transformOutputProvider, jarInput, bucketId);
                FileUtils.deleteIfExists(shardedOutput);
                if (jarInput.getStatus() != Status.REMOVED) {
                    FileUtils.mkdirs(shardedOutput.getParentFile());
                }
            }
            File nonShardedOutput = getPreDexJar(transformOutputProvider, jarInput, null);
            FileUtils.deleteIfExists(nonShardedOutput);
            if (jarInput.getStatus() != Status.REMOVED) {
                FileUtils.mkdirs(nonShardedOutput.getParentFile());
            }

            // and perform dexing if necessary.
            if (jarInput.getStatus() == Status.ADDED || jarInput.getStatus() == Status.CHANGED) {
                return convertJarToDexArchive(context, jarInput, transformOutputProvider);
            }
        }
        return ImmutableList.of();
    }

    private List<File> processAwbJarInput(
            @NonNull Context context,
            boolean isIncremental,
            @NonNull JarInput jarInput,
            File transformOutputProvider)
            throws Exception {
        if (!isIncremental) {
            Preconditions.checkState(
                    jarInput.getFile().exists(),
                    "File %s does not exist, yet it is reported as input. Try \n"
                            + "cleaning the build directory.",
                    jarInput.getFile().toString());
            return convertAwbJarToDexArchive(context, jarInput, transformOutputProvider);
        } else if (jarInput.getStatus() != Status.NOTCHANGED ) {
            // delete all preDex jars if they exists.
            for (int bucketId = 0; bucketId < NUMBER_OF_BUCKETS; bucketId++) {
                File shardedOutput = getAwbPreDexJar(transformOutputProvider, jarInput, bucketId);
                if (dexCache.getCache(jarInput).size() > 0){
                    return dexCache.getCache(jarInput);
                }
                FileUtils.deleteIfExists(shardedOutput);
                if (jarInput.getStatus() != Status.REMOVED) {
                    FileUtils.mkdirs(shardedOutput.getParentFile());
                }
            }
            //not exists
//            File nonShardedOutput = getAwbPreDexJar(transformOutputProvider, jarInput, null);
//            FileUtils.deleteIfExists(nonShardedOutput);
//            if (jarInput.getStatus() != Status.REMOVED) {
//                FileUtils.mkdirs(nonShardedOutput.getParentFile());
//            }

            // and perform dexing if necessary.
            if (jarInput.getStatus() == Status.ADDED || jarInput.getStatus() == Status.CHANGED) {
                return convertAwbJarToDexArchive(context, jarInput, transformOutputProvider);
            }
        }
        return dexCache.getCache(jarInput);
    }

    private List<File> convertJarToDexArchive(
            @NonNull Context context,
            @NonNull JarInput toConvert,
            @NonNull TransformOutputProvider transformOutputProvider)
            throws Exception {

        File cachedVersion = cacheHandler.getCachedVersionIfPresent(toConvert);
        if (cachedVersion == null) {
            return convertToDexArchive(context, toConvert, transformOutputProvider, false);
        } else {
            File outputFile = getPreDexJar(transformOutputProvider, toConvert, null);
            Files.copy(
                    cachedVersion.toPath(),
                    outputFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
            // no need to try to cache an already cached version.
            return ImmutableList.of();
        }
    }

    private List<File> convertAwbJarToDexArchive(
            @NonNull Context context,
            @NonNull JarInput toConvert,
            @NonNull File transformOutputProvider)
            throws Exception {

        File cachedVersion = cacheHandler.getCachedVersionIfPresent(toConvert);
        if (cachedVersion == null) {
            return convertAwbToDexArchive(context, toConvert, transformOutputProvider, false);
        } else {
            File outputFile = getAwbPreDexJar(transformOutputProvider, toConvert, null);
            Files.copy(
                    cachedVersion.toPath(),
                    outputFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
            // no need to try to cache an already cached version.
            return ImmutableList.of();
        }
    }

    public void setTransformTask(TransformTask transformTask) {
        this.transformTask = transformTask;
    }

    public static class DexConversionParameters implements Serializable {
        private final QualifiedContent input;
        private final String output;
        private final int numberOfBuckets;
        private final int buckedId;
        private final int minSdkVersion;
        private final List<String> dexAdditionalParameters;
        private final int inBufferSize;
        private final int outBufferSize;
        private final DexerTool dexer;
        private final boolean isDebuggable;
        private final boolean isIncremental;

        public DexConversionParameters(
                QualifiedContent input,
                File output,
                int numberOfBuckets,
                int buckedId,
                int minSdkVersion,
                List<String> dexAdditionalParameters,
                int inBufferSize,
                int outBufferSize,
                DexerTool dexer,
                boolean isDebuggable,
                boolean isIncremental) {
            this.input = input;
            this.numberOfBuckets = numberOfBuckets;
            this.buckedId = buckedId;
            this.output = output.toURI().toString();
            this.minSdkVersion = minSdkVersion;
            this.dexAdditionalParameters = dexAdditionalParameters;
            this.inBufferSize = inBufferSize;
            this.outBufferSize = outBufferSize;
            this.dexer = dexer;
            this.isDebuggable = isDebuggable;
            this.isIncremental = isIncremental;
        }

        public boolean belongsToThisBucket(String path) {
            return (Math.abs(path.hashCode()) % numberOfBuckets) == buckedId;
        }

        public boolean isDirectoryBased() {
            return input instanceof DirectoryInput;
        }
    }

    public static class DexConversionWorkAction implements Runnable {

        private final AtlasDexArchiveBuilderTransform.DexConversionParameters dexConversionParameters;

        @Inject
        public DexConversionWorkAction(AtlasDexArchiveBuilderTransform.DexConversionParameters dexConversionParameters) {
            this.dexConversionParameters = dexConversionParameters;
        }

        @Override
        public void run() {
            try {
                launchProcessing(dexConversionParameters, System.out, System.err);
            } catch (Exception e) {
                throw new BuildException(e.getMessage(), e);
            }
        }
    }

    private static DexArchiveBuilder getDexArchiveBuilder(
            int minSdkVersion,
            List<String> dexAdditionalParameters,
            int inBufferSize,
            int outBufferSize,
            DexerTool dexer,
            boolean isDebuggable,
            OutputStream outStream,
            OutputStream errStream)
            throws IOException {

        DexArchiveBuilder dexArchiveBuilder;
        switch (dexer) {
            case DX:
                boolean optimizedDex = !dexAdditionalParameters.contains("--no-optimize");
                DxContext dxContext = new DxContext(outStream, errStream);
                DexArchiveBuilderConfig config =
                        new DexArchiveBuilderConfig(
                                dxContext,
                                optimizedDex,
                                inBufferSize,
                                minSdkVersion,
                                DexerTool.DX,
                                outBufferSize,
                                AtlasDexArchiveBuilderCacheHander.isJumboModeEnabledForDx());

                dexArchiveBuilder = DexArchiveBuilder.createDxDexBuilder(config);
                break;
            case D8:
                dexArchiveBuilder =
                        DexArchiveBuilder.createD8DexBuilder(minSdkVersion, isDebuggable);
                break;
            default:
                throw new AssertionError("Unknown dexer type: " + dexer.name());
        }
        return dexArchiveBuilder;
    }

    private List<File> convertToDexArchive(
            @NonNull Context context,
            @NonNull QualifiedContent input,
            @NonNull TransformOutputProvider outputProvider,
            boolean isIncremental)
            throws Exception {

        logger.verbose("Dexing {}", input.getFile().getAbsolutePath());
        ImmutableList.Builder<File> dexArchives = ImmutableList.builder();

        if (dexCache.getCache(input).size() > 0){
                dexArchives.addAll(dexCache.getCache(input));
            return dexArchives.build();

        }
        for (int bucketId = 0; bucketId < NUMBER_OF_BUCKETS; bucketId++) {
            File preDexOutputFile = getPreDexFile(outputProvider, input, bucketId);
            dexArchives.add(preDexOutputFile);
            if (preDexOutputFile.isDirectory()) {
                FileUtils.cleanOutputDir(preDexOutputFile);
            }else {
                FileUtils.deleteIfExists(preDexOutputFile);
            }
            AtlasDexArchiveBuilderTransform.DexConversionParameters parameters =
                    new AtlasDexArchiveBuilderTransform.DexConversionParameters(
                            input,
                            preDexOutputFile,
                            NUMBER_OF_BUCKETS,
                            bucketId,
                            minSdkVersion,
                            dexOptions.getAdditionalParameters(),
                            inBufferSize,
                            outBufferSize,
                            dexer,
                            isDebuggable,
                            isIncremental);

            if (useGradleWorkers) {
                context.getWorkerExecutor()
                        .submit(
                                DexArchiveBuilderTransform.DexConversionWorkAction.class,
                                configuration -> {
                                    configuration.setIsolationMode(IsolationMode.NONE);
                                    configuration.setParams(parameters);
                                });
            } else {
                executor.execute(
                        () -> {
                            ProcessOutputHandler outputHandler =
                                    new ParsingProcessOutputHandler(
                                            new ToolOutputParser(
                                                    new DexParser(), Message.Kind.ERROR, logger),
                                            new ToolOutputParser(new DexParser(), logger),
                                            errorReporter);
                            ProcessOutput output = null;
                            try (Closeable ignored = output = outputHandler.createOutput()) {
                                launchProcessing(
                                        parameters,
                                        output.getStandardOutput(),
                                        output.getErrorOutput());
                            } finally {
                                if (output != null) {
                                    try {
                                        outputHandler.handleOutput(output);
                                    } catch (ProcessException e) {
                                        // ignore this one
                                    }
                                }
                            }
                            return null;
                        });
            }
        }
        List<File> files = dexArchives.build();
        dexCache.cache(input,files);
        return files;
    }


    private static void launchProcessing(
            @NonNull AtlasDexArchiveBuilderTransform.DexConversionParameters dexConversionParameters,
            @NonNull OutputStream outStream,
            @NonNull OutputStream errStream)
            throws IOException, URISyntaxException {
        DexArchiveBuilder dexArchiveBuilder =
                getDexArchiveBuilder(
                        dexConversionParameters.minSdkVersion,
                        dexConversionParameters.dexAdditionalParameters,
                        dexConversionParameters.inBufferSize,
                        dexConversionParameters.outBufferSize,
                        dexConversionParameters.dexer,
                        dexConversionParameters.isDebuggable,
                        outStream,
                        errStream);

                                                                                                                                                                                                                                                                                                                                    Path inputPath = dexConversionParameters.input.getFile().toPath();
        Predicate<String> bucketFilter = dexConversionParameters::belongsToThisBucket;

        boolean hasIncrementalInfo =
                dexConversionParameters.isDirectoryBased() && dexConversionParameters.isIncremental;
        Predicate<String> toProcess =
                hasIncrementalInfo
                        ? path -> {
                    Map<File, Status> changedFiles =
                            ((DirectoryInput) dexConversionParameters.input)
                                    .getChangedFiles();

                    File resolved = inputPath.resolve(path).toFile();
                    Status status = changedFiles.get(resolved);
                    return status == Status.ADDED || status == Status.CHANGED;
                }
                        : path -> true;

        bucketFilter = bucketFilter.and(toProcess);

        try (ClassFileInput input = ClassFileInputs.fromPath(inputPath)) {
            dexArchiveBuilder.convert(
                    input.entries(bucketFilter),
                    Paths.get(new URI(dexConversionParameters.output)),
                    dexConversionParameters.isDirectoryBased());
        } catch (DexArchiveBuilderException ex) {
            throw new DexArchiveBuilderException("Failed to process " + inputPath.toString(), ex);
        }
    }

    @NonNull
    private static File getPreDexFile(
            @NonNull TransformOutputProvider output,
            @NonNull QualifiedContent qualifiedContent,
            int bucketId) {

        return qualifiedContent.getFile().isDirectory()
                ? getPreDexFolder(output, (DirectoryInput) qualifiedContent)
                : getPreDexJar(output, (JarInput) qualifiedContent, bucketId);
    }

    @NonNull
    private static File getPreDexJar(
            @NonNull TransformOutputProvider output,
            @NonNull JarInput qualifiedContent,
            @Nullable Integer bucketId) {

        return output.getContentLocation(
                qualifiedContent.getName() + (bucketId == null ? "" : ("-" + bucketId)),
                ImmutableSet.of(ExtendedContentType.DEX_ARCHIVE),
                qualifiedContent.getScopes(),
                Format.JAR);
    }

    @NonNull
    private static File getPreDexFolder(
            @NonNull TransformOutputProvider output, @NonNull DirectoryInput directoryInput) {

        return FileUtils.mkdirs(
                output.getContentLocation(
                        directoryInput.getName(),
                        ImmutableSet.of(ExtendedContentType.DEX_ARCHIVE),
                        directoryInput.getScopes(),
                        Format.DIRECTORY));
    }


    private List<File> convertAwbToDexArchive(
            @NonNull Context context,
            @NonNull QualifiedContent input,
            @NonNull File outputProvider,
            boolean isIncremental)
            throws Exception {

        int count = 0;
        if (input.getFile().isFile()) {
             count = computerClassCount(input.getFile());
           
        }else if (input.getFile().isDirectory()){
            count = 1;
        }
        logger.verbose("Dexing {}", input.getFile().getAbsolutePath());

        ImmutableList.Builder<File> dexArchives = ImmutableList.builder();

        if (dexCache.getCache(input).size() > 0){
            dexArchives.addAll(dexCache.getCache(input));
            return dexArchives.build();
        }
        for (int bucketId = 0; bucketId < count; bucketId++) {
            File preDexOutputFile = getAwbPreDexFile(outputProvider, input, bucketId);
            dexArchives.add(preDexOutputFile);
            if (preDexOutputFile.isDirectory()) {
                FileUtils.cleanOutputDir(preDexOutputFile);
            }else {
                FileUtils.deleteIfExists(preDexOutputFile);
            }
            AtlasDexArchiveBuilderTransform.DexConversionParameters parameters =
                    new AtlasDexArchiveBuilderTransform.DexConversionParameters(
                            input,
                            preDexOutputFile,
                            NUMBER_OF_BUCKETS,
                            bucketId,
                            minSdkVersion,
                            dexOptions.getAdditionalParameters(),
                            inBufferSize,
                            outBufferSize,
                            dexer,
                            isDebuggable,
                            isIncremental);

            if (useGradleWorkers) {
                context.getWorkerExecutor()
                        .submit(
                                DexArchiveBuilderTransform.DexConversionWorkAction.class,
                                configuration -> {
                                    configuration.setIsolationMode(IsolationMode.NONE);
                                    configuration.setParams(parameters);
                                });
            } else {
                executor.execute(
                        () -> {
                            ProcessOutputHandler outputHandler =
                                    new ParsingProcessOutputHandler(
                                            new ToolOutputParser(
                                                    new DexParser(), Message.Kind.ERROR, logger),
                                            new ToolOutputParser(new DexParser(), logger),
                                            errorReporter);
                            ProcessOutput output = null;
                            try (Closeable ignored = output = outputHandler.createOutput()) {
                                launchProcessing(
                                        parameters,
                                        output.getStandardOutput(),
                                        output.getErrorOutput());
                            } finally {
                                if (output != null) {
                                    try {
                                        outputHandler.handleOutput(output);
                                    } catch (ProcessException e) {
                                        // ignore this one
                                    }
                                }
                            }
                            return null;
                        });
            }
        }

       List<File>files =  dexArchives.build();
       dexCache.cache(input,files);
        return files;
    }


    private void processAwbDexArchive(TransformInvocation transformInvocation, List<QualifiedContent> listFiles) throws Exception {

        File awbApkOutputDir = ((AppVariantContext) variantContext).getAwbApkOutputDir();
        FileUtils.cleanOutputDir(awbApkOutputDir);

        AtlasDependencyTree atlasDependencyTree = AtlasBuildContext.androidDependencyTrees.get(
                variantContext.getScope().getFullVariantName());

        if (null == atlasDependencyTree) {
            return;
        }

        for (final AwbBundle awbBundle : atlasDependencyTree.getAwbBundles()) {
            Multimap<QualifiedContent, File> cacheableItems = HashMultimap.create();

            List<QualifiedContent> qualifiedContents = new LinkedList<>();

            long start = System.currentTimeMillis();


            // if some of our .jar input files exist, just reset the inputDir to null
            AwbTransform awbTransform = variantContext.getAppVariantOutputContext(ApkDataUtils.get(variantOutput)).getAwbTransformMap()
                    .get(awbBundle.getName());
            List<File> inputFiles = new ArrayList<File>();
            inputFiles.addAll(awbTransform.getInputFiles());
            inputFiles.addAll(awbTransform.getInputLibraries());
            if (null != awbTransform.getInputDir()) {
                inputFiles.add(awbTransform.getInputDir());
            }

            for (File file : inputFiles) {
                boolean find = false;
                for (QualifiedContent content : listFiles) {
                    if (content.getFile().getAbsolutePath().equals(file.getAbsolutePath())) {
                        find = true;
                        qualifiedContents.add(content);
                        break;
                    }
                }

                if (!find) {
                    if (file.isDirectory()) {
                        DirectoryInput directoryInput = makeDirectoryInput(file);
                        qualifiedContents.add(directoryInput);
                    } else if (file.isFile()) {
                        JarInput jarInput = makeJarInput(file);
                        qualifiedContents.add(jarInput);
                    }

                }

            }
            for (QualifiedContent qualifiedContent : qualifiedContents) {
                if (qualifiedContent.getFile().isDirectory()) {
                    List<File> folderFiles = convertAwbToDexArchive(
                            transformInvocation.getContext(),
                            qualifiedContent, variantContext.getAwbDexAchiveOutput(awbBundle), transformInvocation.isIncremental()
                    );
                    cacheableItems.putAll(qualifiedContent, folderFiles);

                } else {
                    List<File> jarFiles = processAwbJarInput(transformInvocation.getContext(), transformInvocation.isIncremental(), (JarInput) qualifiedContent, variantContext.getAwbDexAchiveOutput(awbBundle));
                    cacheableItems.putAll(qualifiedContent, jarFiles);
                }
            }

            AtlasBuildContext.awbDexFiles.put(awbBundle, cacheableItems);


        }
    }

    private File getAwbPreDexFile(File outputProvider, QualifiedContent qualifiedContent, Integer bucketId) {
        return qualifiedContent.getFile().isDirectory()
                ? getAwbPreDexFolder(outputProvider, (DirectoryInput) qualifiedContent)
                : getAwbPreDexJar(outputProvider, (JarInput) qualifiedContent, bucketId);
    }


      Map<String,Integer> dexcount = new HashMap<>();


    private File getAwbPreDexJar(
            @NonNull File output,
            @NonNull JarInput qualifiedContent,
            @Nullable Integer bucketId) {
        if (bucketId == null){
            return new File(output, qualifiedContent.getName() + (bucketId == null ? "" : ("-" + bucketId)) + ".jar");
        }
//        synchronized (object) {
            if (bucketId > 5) {
                bucketId = dexcount.get(qualifiedContent.getName());
            }
            dexcount.put(qualifiedContent.getName(), bucketId++);
//        }
        if (!output.exists()){
            output.mkdirs();
        }
        return new File(output, qualifiedContent.getName() + (bucketId == null ? "" : ("-" + bucketId)) + ".jar");

    }

    @NonNull
    private static File getAwbPreDexFolder(
            @NonNull File output, @NonNull DirectoryInput directoryInput) {
        return FileUtils.mkdirs(new File(output, directoryInput.getName()));
    }


    private DirectoryInput makeDirectoryInput(File file) {
        return new DirectoryInput() {
            @Override
            public Map<File, Status> getChangedFiles() {
                return ImmutableMap.of(file, Status.CHANGED);
            }

            @Override
            public String getName() {
                return "folder";
            }

            @Override
            public File getFile() {
                return file;
            }

            @Override
            public Set<ContentType> getContentTypes() {
                return ImmutableSet.of(QualifiedContent.DefaultContentType.CLASSES);
            }

            @Override
            public Set<? super Scope> getScopes() {
                return ImmutableSet.of();
            }
        };

    }


    private JarInput makeJarInput(File file) {
        BuildAtlasEnvTask.FileIdentity fileIdentity = null;
        for (BuildAtlasEnvTask.FileIdentity fy : AtlasBuildContext.awbDexJar) {
            if (fy.file == file) {
                fileIdentity = fy;
                break;
            }
        }
        BuildAtlasEnvTask.FileIdentity finalFileIdentity = fileIdentity;
        return new JarInput() {
            @Override
            public Status getStatus() {
                return Status.ADDED;
            }

            @Override
            public String getName() {

               return MD5Util.getFileMD5(file);
            }

            @Override
            public File getFile() {
                return file;
            }

            @Override
            public Set<ContentType> getContentTypes() {
                return ImmutableSet.of(QualifiedContent.DefaultContentType.CLASSES);
            }

            @Override
            public Set<? super Scope> getScopes() {
                if (finalFileIdentity == null){
                    return  ImmutableSet.of(Scope.EXTERNAL_LIBRARIES);
                }
                if (finalFileIdentity.subProject) {
                    return ImmutableSet.of(Scope.SUB_PROJECTS);
                } else {
                    return ImmutableSet.of(Scope.EXTERNAL_LIBRARIES);
                }
            }
        };
    }

    private int computerClassCount(File file){
        JarFile jarFile = null;
        int count = 0;
        try {
            jarFile = new JarFile(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
        Enumeration enumeration= jarFile.entries();
        while (enumeration.hasMoreElements()){
            JarEntry jarEntry = (JarEntry) enumeration.nextElement();
            if (jarEntry.getName().endsWith(".class")){
                count ++;
            }
            if (count > 1){
                return 1;
            }
        }
       return count;
    }

}
