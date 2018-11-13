package com.taobao.android.builder.tasks.transform;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.transform.*;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.internal.api.AppVariantOutputContext;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.transforms.DesugarTransform;
import com.android.build.gradle.internal.transforms.DesugarWorkerItem;
import com.android.build.gradle.internal.transforms.TransformInputUtil;
import com.android.builder.Version;
import com.android.builder.core.DesugarProcessBuilder;
import com.android.builder.utils.FileCache;
import com.android.ide.common.internal.WaitableExecutor;
import com.android.ide.common.process.JavaProcessExecutor;
import com.android.ide.common.process.LoggedProcessOutputHandler;
import com.android.ide.common.process.ProcessException;
import com.android.utils.PathUtils;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.*;
import com.google.common.hash.Hashing;
import com.google.common.hash.HashingInputStream;
import com.taobao.android.builder.AtlasBuildContext;
import org.gradle.workers.WorkerExecutor;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * AtlasDesugarTransform
 *
 * @author zhayu.ll
 * @date 18/2/7
 */
public class AtlasDesugarTransform extends Transform {
    public Transform oldTransform;

    private enum FileCacheInputParams {

        /**
         * The input file.
         */
        FILE,

        /**
         * Version of the plugin containing Desugar used to generate the output.
         */
        PLUGIN_VERSION,

        /**
         * Minimum sdk version passed to Desugar, affects output.
         */
        MIN_SDK_VERSION,
    }

    private static class InputEntry {
        @Nullable
        private final FileCache cache;
        @Nullable
        private final FileCache.Inputs inputs;
        @NonNull
        private final Path inputPath;
        @NonNull
        private final Path outputPath;

        public InputEntry(
                @Nullable FileCache cache,
                @Nullable FileCache.Inputs inputs,
                @NonNull Path inputPath,
                @NonNull Path outputPath) {
            this.cache = cache;
            this.inputs = inputs;
            this.inputPath = inputPath;
            this.outputPath = outputPath;
        }

        @Nullable
        public FileCache getCache() {
            return cache;
        }

        @Nullable
        public FileCache.Inputs getInputs() {
            return inputs;
        }

        @NonNull
        public Path getInputPath() {
            return inputPath;
        }

        @NonNull
        public Path getOutputPath() {
            return outputPath;
        }
    }

    private static final LoggerWrapper logger = LoggerWrapper.getLogger(DesugarTransform.class);

    @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized")
    // we initialize this field only once, so having unsynchronized reads is fine
    private static final AtomicReference<Path> desugarJar = new AtomicReference<Path>(null);

    private static final String DESUGAR_JAR = "desugar_deploy.jar";

    @NonNull
    private final Supplier<List<File>> androidJarClasspath;
    private final AppVariantOutputContext appVariantOutputContext;
    @NonNull
    private final List<Path> compilationBootclasspath;
    @Nullable
    private final FileCache userCache;
    private final int minSdk;
    @NonNull
    private final JavaProcessExecutor executor;
    @NonNull
    private final Path tmpDir;
    @NonNull
    private final WaitableExecutor waitableExecutor;
    private boolean verbose;
    private final boolean enableGradleWorkers;

    @NonNull
    private Set<AtlasDesugarTransform.InputEntry> cacheMisses = Sets.newConcurrentHashSet();

    public AtlasDesugarTransform(AppVariantOutputContext appVariantOutputContext,
                                 @NonNull Supplier<List<File>> androidJarClasspath,
                                 @NonNull List<Path> compilationBootclasspath,
                                 @Nullable FileCache userCache,
                                 int minSdk,
                                 @NonNull JavaProcessExecutor executor,
                                 boolean verbose,
                                 boolean enableGradleWorkers,
                                 @NonNull Path tmpDir) {
        this.appVariantOutputContext = appVariantOutputContext;
        this.androidJarClasspath = androidJarClasspath;
        this.compilationBootclasspath = compilationBootclasspath;
        this.userCache = userCache;
        this.minSdk = minSdk;
        this.executor = executor;
        this.waitableExecutor = WaitableExecutor.useGlobalSharedThreadPool();
        this.verbose = verbose;
        this.enableGradleWorkers = enableGradleWorkers;
        this.tmpDir = tmpDir;
    }

    @NonNull
    @Override
    public String getName() {
        return "desugar";
    }

    @NonNull
    @Override
    public Set<QualifiedContent.ContentType> getInputTypes() {
        return TransformManager.CONTENT_CLASS;
    }

    @NonNull
    @Override
    public Set<? super QualifiedContent.Scope> getScopes() {
        return TransformManager.SCOPE_FULL_PROJECT;
    }

    @NonNull
    @Override
    public Set<? super QualifiedContent.Scope> getReferencedScopes() {
        return ImmutableSet.of(QualifiedContent.Scope.PROVIDED_ONLY, QualifiedContent.Scope.TESTED_CODE);
    }

    @NonNull
    @Override
    public Map<String, Object> getParameterInputs() {
        return ImmutableMap.of("Min sdk", minSdk);
    }

    @NonNull
    @Override
    public Collection<SecondaryFile> getSecondaryFiles() {
        ImmutableList.Builder<SecondaryFile> files = ImmutableList.builder();
        androidJarClasspath.get().forEach(file -> files.add(SecondaryFile.nonIncremental(file)));

        compilationBootclasspath.forEach(
                file -> files.add(SecondaryFile.nonIncremental(file.toFile())));

        return files.build();
    }

    @Override
    public boolean isIncremental() {
        return false;
    }

    @Override
    public void transform(@NonNull TransformInvocation transformInvocation)
            throws TransformException, InterruptedException, IOException {
        try {
            Map<JarInput, File> transformFiles = new HashMap<>();
            initDesugarJar(userCache);
            processInputs(transformInvocation, transformFiles);
            waitableExecutor.waitForTasksWithQuickFail(true);

            if (enableGradleWorkers) {
                processNonCachedOnesWithGradleExecutor(
                        transformInvocation.getContext().getWorkerExecutor(),
                        getClasspath(transformInvocation));
            } else {
                processNonCachedOnes(getClasspath(transformInvocation));
                waitableExecutor.waitForTasksWithQuickFail(true);
            }
            AtlasBuildContext.atlasMainDexHelperMap.get(appVariantOutputContext.getVariantContext().getVariantName()).updateMainDexFiles(transformFiles);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TransformException(e);
        } catch (Exception e) {
            throw new TransformException(e);
        }
    }

    private void processInputs(@NonNull TransformInvocation transformInvocation, Map<JarInput, File> transformFiles) throws Exception {
        TransformOutputProvider outputProvider = transformInvocation.getOutputProvider();
        Preconditions.checkNotNull(outputProvider);
        outputProvider.deleteAll();
        AtlasBuildContext.atlasMainDexHelperMap.get(appVariantOutputContext.getVariantContext().getVariantName()).getInputDirs().clear();
        for (TransformInput input : transformInvocation.getInputs()) {
            for (DirectoryInput dirInput : input.getDirectoryInputs()) {
                Path rootFolder = dirInput.getFile().toPath();
                Path output = getOutputPath(transformInvocation.getOutputProvider(), dirInput);
                if (Files.notExists(rootFolder)) {
                    PathUtils.deleteIfExists(output);
                } else {
                    AtlasBuildContext.atlasMainDexHelperMap.get(appVariantOutputContext.getVariantContext().getVariantName()).getInputDirs().add(output.toFile());
                    Set<Status> statuses = Sets.newHashSet(dirInput.getChangedFiles().values());
                    boolean reRun =
                            !transformInvocation.isIncremental()
                                    || !Objects.equals(
                                    statuses, Collections.singleton(Status.NOTCHANGED));

                    if (reRun) {
                        PathUtils.deleteIfExists(output);
                        processSingle(rootFolder, output, dirInput.getScopes());
                    }
                }
            }


            for (JarInput jarInput : input.getJarInputs()) {
                if (jarInput.getScopes().contains(QualifiedContent.Scope.SUB_PROJECTS)) {
                    if (jarInput.getName().contains("::")) {
                        continue;
                    }
                }
                Path output = getOutputPath(outputProvider, jarInput);
                if (inMainDex(jarInput)) {
                    Files.deleteIfExists(output);
                    logger.info("process maindex desugar:" + jarInput.getFile().getAbsolutePath());
                    processSingle(jarInput.getFile().toPath(), output, jarInput.getScopes());
                    transformFiles.put(jarInput, output.toFile());
                } else {
                    File file = appVariantOutputContext.updateAwbDexFile(jarInput, output.toFile());
                    if (file != null) {
                        if (!jarInput.getFile().equals(file)) {
                            logger.info("process awb desugar:" + file.getAbsolutePath());
                            Files.deleteIfExists(output);
                            processSingle(file.toPath(), output, jarInput.getScopes());
                        } else {
                            logger.info("process awb desugar:" + jarInput.getFile().getAbsolutePath());
                            Files.deleteIfExists(output);
                            processSingle(jarInput.getFile().toPath(), output, jarInput.getScopes());
                        }
                    } else {
                        throw new TransformException(jarInput.getFile().getAbsolutePath() + "is not in maindex and awb libraries in AtlasdesugarTransform!");
                    }
                }
            }
        }
    }

    private void processNonCachedOnes(List<Path> classpath) throws IOException {
        int parallelExecutions = waitableExecutor.getParallelism();

        int index = 0;
        Multimap<Integer, AtlasDesugarTransform.InputEntry> procBuckets = ArrayListMultimap.create();
        for (AtlasDesugarTransform.InputEntry pathPathEntry : cacheMisses) {
            int bucketId = index % parallelExecutions;
            procBuckets.put(bucketId, pathPathEntry);
            index++;
        }

        List<Path> desugarBootclasspath = getBootclasspath();
        for (Integer bucketId : procBuckets.keySet()) {
            Callable<Void> callable =
                    () -> {
                        Map<Path, Path> inToOut = Maps.newHashMap();
                        for (AtlasDesugarTransform.InputEntry e : procBuckets.get(bucketId)) {
                            inToOut.put(e.getInputPath(), e.getOutputPath());
                        }

                        DesugarProcessBuilder processBuilder =
                                new DesugarProcessBuilder(
                                        desugarJar.get(),
                                        verbose,
                                        inToOut,
                                        classpath,
                                        desugarBootclasspath,
                                        minSdk,
                                        tmpDir);
                        boolean isWindows =
                                SdkConstants.currentPlatform() == SdkConstants.PLATFORM_WINDOWS;
                        executor.execute(
                                processBuilder.build(isWindows),
                                new LoggedProcessOutputHandler(logger))
                                .rethrowFailure()
                                .assertNormalExitValue();

                        // now copy to the cache because now we have the file
                        for (AtlasDesugarTransform.InputEntry e : procBuckets.get(bucketId)) {
                            if (e.getCache() != null && e.getInputs() != null) {
                                e.getCache()
                                        .createFileInCacheIfAbsent(
                                                e.getInputs(),
                                                in -> Files.copy(e.getOutputPath(), in.toPath()));
                            }
                        }

                        return null;
                    };
            waitableExecutor.execute(callable);
        }
    }

    private void processNonCachedOnesWithGradleExecutor(
            WorkerExecutor workerExecutor, List<Path> classpath)
            throws IOException, ProcessException, ExecutionException {
        List<Path> desugarBootclasspath = getBootclasspath();
        for (AtlasDesugarTransform.InputEntry pathPathEntry : cacheMisses) {
            DesugarWorkerItem workerItem =
                    new DesugarWorkerItem(
                            desugarJar.get(),
                            PathUtils.createTmpDirToRemoveOnShutdown("gradle_lambdas"),
                            true,
                            pathPathEntry.getInputPath(),
                            pathPathEntry.getOutputPath(),
                            classpath,
                            desugarBootclasspath,
                            minSdk);

            workerExecutor.submit(DesugarWorkerItem.DesugarAction.class, workerItem::configure);
        }

        workerExecutor.await();

        for (AtlasDesugarTransform.InputEntry e : cacheMisses) {
            if (e.getCache() != null && e.getInputs() != null) {
                e.getCache()
                        .createFileInCacheIfAbsent(
                                e.getInputs(), in -> Files.copy(e.getOutputPath(), in.toPath()));
            }
        }
    }

    @NonNull
    private List<Path> getClasspath(@NonNull TransformInvocation transformInvocation)
            throws IOException {
        ImmutableList.Builder<Path> classpathEntries = ImmutableList.builder();

        classpathEntries.addAll(
                TransformInputUtil.getAllFiles(transformInvocation.getInputs())
                        .stream()
                        .map(File::toPath)
                        .iterator());

        classpathEntries.addAll(
                TransformInputUtil.getAllFiles(transformInvocation.getReferencedInputs())
                        .stream()
                        .map(File::toPath)
                        .iterator());

        return classpathEntries.build();
    }

    @NonNull
    private List<Path> getBootclasspath() throws IOException {
        List<Path> desugarBootclasspath =
                androidJarClasspath.get().stream().map(File::toPath).collect(Collectors.toList());
        desugarBootclasspath.addAll(compilationBootclasspath);

        return desugarBootclasspath;
    }

    private void processSingle(
            @NonNull Path input, @NonNull Path output, @NonNull Set<? super QualifiedContent.Scope> scopes)
            throws Exception {
        waitableExecutor.execute(
                () -> {
                    if (output.toString().endsWith(SdkConstants.DOT_JAR)) {
                        Files.createDirectories(output.getParent());
                    } else {
                        Files.createDirectories(output);
                    }

                    FileCache cacheToUse;
                    if (Files.isRegularFile(input)
                            && Objects.equals(
                            scopes, Collections.singleton(QualifiedContent.Scope.EXTERNAL_LIBRARIES))) {
                        cacheToUse = userCache;
                    } else {
                        cacheToUse = null;
                    }

                    processUsingCache(input, output, cacheToUse);
                    return null;
                });
    }

    private void processUsingCache(
            @NonNull Path input,
            @NonNull Path output,
            @Nullable FileCache cache)
            throws Exception {
        if (cache != null) {
            try {
                FileCache.Inputs cacheKey = getBuildCacheInputs(input, minSdk);
                if (cache.cacheEntryExists(cacheKey)) {
                    logger.info("process desugar hit cache:" + input.toFile().getAbsolutePath());
                    FileCache.QueryResult result =
                            cache.createFile(
                                    output.toFile(),
                                    cacheKey,
                                    () -> {
                                        throw new AssertionError("Entry should exist.");
                                    });

                    if (result.getQueryEvent().equals(FileCache.QueryEvent.CORRUPTED)) {
                        Objects.requireNonNull(result.getCauseOfCorruption());
                        logger.verbose(
                                "The build cache at '%1$s' contained an invalid cache entry.\n"
                                        + "Cause: %2$s\n"
                                        + "We have recreated the cache entry.\n",
                                cache.getCacheDirectory().getAbsolutePath(),
                                Throwables.getStackTraceAsString(result.getCauseOfCorruption()));
                    }

                    if (Files.notExists(output)) {
                        throw new RuntimeException(
                                String.format(
                                        "Entry for %s is invalid. Please clean your build cache "
                                                + "under %s.",
                                        output.toString(),
                                        cache.getCacheDirectory().getAbsolutePath()));
                    }
                } else {
                    cacheMissAction(cache, cacheKey, input, output);
                }
            } catch (Exception exception) {
                logger.error(
                        null,
                        String.format(
                                "Unable to Desugar '%1$s' to '%2$s' using the build cache at"
                                        + " '%3$s'.\n",
                                input.toString(),
                                output.toString(),
                                cache.getCacheDirectory().getAbsolutePath()));
                throw new RuntimeException(exception);
            }
        } else {
            cacheMissAction(null, null, input, output);
        }
    }

    private void cacheMissAction(
            @Nullable FileCache cache,
            @Nullable FileCache.Inputs inputs,
            @NonNull Path input,
            @NonNull Path output)
            throws IOException, ProcessException {
        logger.info("process desugar miss cache:" + input.toFile().getAbsolutePath());

        // add it to the list of cache misses, that will be processed
        cacheMisses.add(new AtlasDesugarTransform.InputEntry(cache, inputs, input, output));
    }

    @NonNull
    private static Path getOutputPath(
            @NonNull TransformOutputProvider outputProvider, @NonNull QualifiedContent content) {
        return outputProvider
                .getContentLocation(
                        content.getName(),
                        content.getContentTypes(),
                        content.getScopes(),
                        content.getFile().isDirectory() ? Format.DIRECTORY : Format.JAR)
                .toPath();
    }

    @NonNull
    private static FileCache.Inputs getBuildCacheInputs(@NonNull Path input, int minSdkVersion)
            throws IOException {
        FileCache.Inputs.Builder buildCacheInputs =
                new FileCache.Inputs.Builder(FileCache.Command.DESUGAR_LIBRARY);

        buildCacheInputs
                .putFile(
                        AtlasDesugarTransform.FileCacheInputParams.FILE.name(),
                        input.toFile(),
                        FileCache.FileProperties.PATH_HASH)
                .putString(
                        AtlasDesugarTransform.FileCacheInputParams.PLUGIN_VERSION.name(),
                        Version.ANDROID_GRADLE_PLUGIN_VERSION)
                .putLong(AtlasDesugarTransform.FileCacheInputParams.MIN_SDK_VERSION.name(), minSdkVersion);

        return buildCacheInputs.build();
    }

    /**
     * Set this location of extracted desugar jar that is used for processing.
     */
    private static void initDesugarJar(@Nullable FileCache cache) throws IOException {
        if (isDesugarJarInitialized()) {
            return;
        }

        URL url = DesugarProcessBuilder.class.getClassLoader().getResource(DESUGAR_JAR);
        Preconditions.checkNotNull(url);

        Path extractedDesugar = null;
        if (cache != null) {
            try {
                String fileHash;
                try (HashingInputStream stream =
                             new HashingInputStream(Hashing.sha256(), url.openStream())) {
                    fileHash = stream.hash().toString();
                }
                FileCache.Inputs inputs =
                        new FileCache.Inputs.Builder(FileCache.Command.EXTRACT_DESUGAR_JAR)
                                .putString("pluginVersion", Version.ANDROID_GRADLE_PLUGIN_VERSION)
                                .putString("jarUrl", url.toString())
                                .putString("fileHash", fileHash)
                                .build();

                File cachedFile =
                        cache.createFileInCacheIfAbsent(
                                inputs, file -> copyDesugarJar(url, file.toPath()))
                                .getCachedFile();
                Preconditions.checkNotNull(cachedFile);
                extractedDesugar = cachedFile.toPath();
            } catch (IOException | ExecutionException e) {
                logger.error(e, "Unable to cache Desugar jar. Extracting to temp dir.");
            }
        }

        synchronized (desugarJar) {
            if (isDesugarJarInitialized()) {
                return;
            }

            if (extractedDesugar == null) {
                extractedDesugar = PathUtils.createTmpToRemoveOnShutdown(DESUGAR_JAR);
                copyDesugarJar(url, extractedDesugar);
            }
            desugarJar.set(extractedDesugar);
        }
    }

    private static void copyDesugarJar(@NonNull URL inputUrl, @NonNull Path targetPath)
            throws IOException {
        try (InputStream inputStream = inputUrl.openConnection().getInputStream()) {
            Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static boolean isDesugarJarInitialized() {
        return desugarJar.get() != null && Files.isRegularFile(desugarJar.get());
    }

    private boolean inMainDex(JarInput jarInput) throws IOException {

        return AtlasBuildContext.atlasMainDexHelperMap.get(appVariantOutputContext.getVariantContext().getVariantName()).inMainDex(jarInput);
    }
}
