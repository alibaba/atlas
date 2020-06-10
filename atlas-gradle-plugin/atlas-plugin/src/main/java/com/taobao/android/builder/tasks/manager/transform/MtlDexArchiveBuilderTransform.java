package com.taobao.android.builder.tasks.manager.transform;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.build.api.transform.*;
import com.android.build.gradle.internal.InternalScope;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.internal.api.AppVariantOutputContext;
import com.android.build.gradle.internal.api.AwbTransform;
import com.android.build.gradle.internal.crash.PluginCrashReporter;
import com.android.build.gradle.internal.pipeline.AtlasIntermediateStreamHelper;
import com.android.build.gradle.internal.pipeline.ExtendedContentType;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.transforms.DexArchiveBuilderTransform;
import com.android.build.gradle.internal.transforms.TransformInputUtil;
import com.android.build.gradle.internal.workeractions.WorkerActionServiceRegistry;
import com.android.builder.core.DefaultDexOptions;
import com.android.builder.core.DexOptions;
import com.android.builder.core.SerializableMessageReceiver;
import com.android.builder.dexing.*;
import com.android.builder.dexing.r8.ClassFileProviderFactory;
import com.android.builder.utils.FileCache;
import com.android.dx.command.dexer.DxContext;
import com.android.ide.common.blame.Message;
import com.android.ide.common.blame.MessageReceiver;
import com.android.ide.common.blame.ParsingProcessOutputHandler;
import com.android.ide.common.blame.parser.DexParser;
import com.android.ide.common.blame.parser.ToolOutputParser;
import com.android.ide.common.internal.WaitableExecutor;
import com.android.ide.common.process.ProcessException;
import com.android.ide.common.process.ProcessOutput;
import com.android.ide.common.process.ProcessOutputHandler;
import com.android.utils.FileUtils;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.*;
import com.taobao.android.builder.AtlasBuildContext;
import com.taobao.android.builder.dependency.model.AwbBundle;
import com.taobao.android.builder.tools.TransformInputUtils;
import com.taobao.android.provider.MainDexListProvider;

import org.gradle.tooling.BuildException;
import org.gradle.workers.IsolationMode;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.android.build.gradle.internal.workeractions.WorkerActionServiceRegistry.INSTANCE;

/**
 * @ClassName MtlDexArchiveBuilderTransform
 * @Description TODO
 * @Author zhayu.ll
 * @Date 2019-07-26 10:06
 * @Version 1.0
 */
public class MtlDexArchiveBuilderTransform extends Transform {


    private static final LoggerWrapper logger =
            LoggerWrapper.getLogger(DexArchiveBuilderTransform.class);

    public static final int DEFAULT_BUFFER_SIZE_IN_KB = 100;

    public static final int NUMBER_OF_SLICES_FOR_PROJECT_CLASSES = 10;

    private static final int DEFAULT_NUM_BUCKETS =
            Math.max(Runtime.getRuntime().availableProcessors() / 2, 1);

    @NonNull
    private final Supplier<List<File>> androidJarClasspath;
    @NonNull
    private final DexOptions dexOptions;
    @NonNull
    private final MessageReceiver messageReceiver;
    @VisibleForTesting
    @NonNull
    final WaitableExecutor executor;
    private final int minSdkVersion;
    @NonNull
    private final DexerTool dexer;
    @NonNull
    private String projectVariant;
    @NonNull
    private final DexArchiveHandler cacheHandler;
    private final boolean useGradleWorkers;
    private final int inBufferSize;
    private final int outBufferSize;
    private final boolean isDebuggable;
    @NonNull
    private final VariantScope.Java8LangSupport java8LangSupportType;
    private final int numberOfBuckets;
    private final boolean includeFeaturesInScopes;
    private boolean isInstantRun;
    private AppVariantOutputContext variantOutputContext;

    private boolean enableDexingArtifactTransform;


    private AtlasIntermediateStreamHelper atlasIntermediateStreamHelper;

    public MtlDexArchiveBuilderTransform(AppVariantOutputContext variantOutputContext,
                                         @NonNull Supplier<List<File>> androidJarClasspath,
                                         @NonNull DexOptions dexOptions,
                                         @NonNull MessageReceiver messageReceiver,
                                         @Nullable FileCache userLevelCache,
                                         int minSdkVersion,
                                         @NonNull DexerTool dexer,
                                         boolean useGradleWorkers,
                                         @Nullable Integer inBufferSize,
                                         @Nullable Integer outBufferSize,
                                         boolean isDebuggable,
                                         @NonNull VariantScope.Java8LangSupport java8LangSupportType,
                                         @NonNull String projectVariant,
                                         @Nullable Integer numberOfBuckets,
                                         boolean includeFeaturesInScopes,
                                         boolean isInstantRun,
                                         boolean enableDexingArtifactTransform,
                                         AtlasIntermediateStreamHelper atlasIntermediateStreamHelper) {

        this.variantOutputContext = variantOutputContext;
        this.androidJarClasspath = androidJarClasspath;
        this.dexOptions = dexOptions;
        this.messageReceiver = messageReceiver;
        this.minSdkVersion = minSdkVersion;
        this.dexer = dexer;
        this.projectVariant = projectVariant;
        this.executor = WaitableExecutor.useGlobalSharedThreadPool();
        this.cacheHandler =
                new DexArchiveHandler(
                        userLevelCache, dexOptions, minSdkVersion, isDebuggable, dexer);
        this.useGradleWorkers = useGradleWorkers;
        this.inBufferSize =
                (inBufferSize == null ? DEFAULT_BUFFER_SIZE_IN_KB : inBufferSize) * 1024;
        this.outBufferSize =
                (outBufferSize == null ? DEFAULT_BUFFER_SIZE_IN_KB : outBufferSize) * 1024;
        this.isDebuggable = isDebuggable;
        this.java8LangSupportType = java8LangSupportType;
        this.numberOfBuckets = 1;
        this.includeFeaturesInScopes = includeFeaturesInScopes;
        this.isInstantRun = isInstantRun;
        this.enableDexingArtifactTransform = enableDexingArtifactTransform;

        this.atlasIntermediateStreamHelper = atlasIntermediateStreamHelper;



    }


    public static final class ClasspathServiceKey
            implements WorkerActionServiceRegistry.ServiceKey<ClassFileProviderFactory> {
        private final long id;

        public ClasspathServiceKey(long id) {
            this.id = id;
        }

        @NonNull
        @Override
        public Class<ClassFileProviderFactory> getType() {
            return ClassFileProviderFactory.class;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            MtlDexArchiveBuilderTransform.ClasspathServiceKey that = (MtlDexArchiveBuilderTransform.ClasspathServiceKey) o;
            return id == that.id;
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }
    }

    /**
     * Wrapper around the {@link com.android.builder.dexing.r8.ClassFileProviderFactory}.
     */
    public static final class ClasspathService
            implements WorkerActionServiceRegistry.RegisteredService<ClassFileProviderFactory> {

        private final ClassFileProviderFactory providerFactory;

        public ClasspathService(ClassFileProviderFactory providerFactory) {
            this.providerFactory = providerFactory;
        }

        @NonNull
        @Override
        public ClassFileProviderFactory getService() {
            return providerFactory;
        }

        @Override
        public void shutdown() {
            // nothing to be done, as providerFactory is a closable
        }
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
        if (enableDexingArtifactTransform) {
            return Sets.immutableEnumSet(QualifiedContent.Scope.PROJECT);
        } else if (includeFeaturesInScopes) {
            return TransformManager.SCOPE_FULL_WITH_IR_AND_FEATURES;
        } else {
            return TransformManager.SCOPE_FULL_WITH_IR_FOR_DEXING;
        }
    }

    @NonNull
    @Override
    public Set<? super QualifiedContent.Scope> getReferencedScopes() {
        Set<? super QualifiedContent.ScopeType> referenced =
                Sets.newHashSet(QualifiedContent.Scope.PROVIDED_ONLY, QualifiedContent.Scope.TESTED_CODE);
        if (enableDexingArtifactTransform) {
            referenced.add(QualifiedContent.Scope.SUB_PROJECTS);
            referenced.add(QualifiedContent.Scope.EXTERNAL_LIBRARIES);
            referenced.add(InternalScope.MAIN_SPLIT);
            if (includeFeaturesInScopes) {
                referenced.add(InternalScope.FEATURES);
            }
        }

        return referenced;
    }

    @NonNull
    @Override
    public Map<String, Object> getParameterInputs() {
        try {
            Map<String, Object> params = Maps.newHashMapWithExpectedSize(6);
            params.put("optimize", !dexOptions.getAdditionalParameters().contains("--no-optimize"));
            params.put("jumbo", dexOptions.getJumboMode());
            params.put("min-sdk-version", minSdkVersion);
            params.put("dex-builder-tool", dexer.name());
            params.put("instant-run", isInstantRun);
            params.put("enable-dexing-artifact-transform", enableDexingArtifactTransform);

            return params;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean isIncremental() {
        return false;
    }

    @Override
    public void transform(@NonNull TransformInvocation transformInvocation)
            throws TransformException, IOException, InterruptedException {

        if (!variantOutputContext.getVariantContext().getBuildType().getPatchConfig().isCreateIPatch()) {
            new Thread(() -> MainDexListProvider.getInstance().generateMainDexList(variantOutputContext.getVariantContext())).start();

        }
        TransformOutputProvider outputProvider = transformInvocation.getOutputProvider();
        if (atlasIntermediateStreamHelper != null) {
            atlasIntermediateStreamHelper.replaceProvider(transformInvocation);

        }

        Preconditions.checkNotNull(outputProvider, "Missing output provider.");
        if (dexOptions.getAdditionalParameters().contains("--no-optimize")) {
            logger.warning(DefaultDexOptions.OPTIMIZE_WARNING);
        }

        logger.verbose("Task is incremental : %b ", transformInvocation.isIncremental());

        if (!transformInvocation.isIncremental()) {
            outputProvider.deleteAll();
        }

        Set<File> additionalPaths;
        MtlDesugarIncrementalTransformHelper desugarIncrementalTransformHelper;
        if (java8LangSupportType != VariantScope.Java8LangSupport.D8) {
            additionalPaths = ImmutableSet.of();
            desugarIncrementalTransformHelper = null;
        } else {
            desugarIncrementalTransformHelper =
                    new MtlDesugarIncrementalTransformHelper(
                            projectVariant, transformInvocation, executor);
            additionalPaths =
                    desugarIncrementalTransformHelper
                            .getAdditionalPaths()
                            .stream()
                            .map(Path::toFile)
                            .collect(Collectors.toSet());
        }

        List<DexArchiveHandler.CacheableItem> cacheableItems = new ArrayList<>();
        boolean isIncremental = transformInvocation.isIncremental();
        List<Path> classpath =
                getClasspath(transformInvocation, java8LangSupportType)
                        .stream()
                        .map(Paths::get)
                        .collect(Collectors.toList());
        List<Path> bootclasspath =
                getBootClasspath(androidJarClasspath, java8LangSupportType)
                        .stream()
                        .map(Paths::get)
                        .collect(Collectors.toList());

        MtlDexArchiveBuilderTransform.ClasspathServiceKey bootclasspathServiceKey = null;
        MtlDexArchiveBuilderTransform.ClasspathServiceKey classpathServiceKey = null;
        try (ClassFileProviderFactory bootClasspathProvider =
                     new ClassFileProviderFactory(bootclasspath);
             ClassFileProviderFactory libraryClasspathProvider =
                     new ClassFileProviderFactory(classpath)) {
            bootclasspathServiceKey = new MtlDexArchiveBuilderTransform.ClasspathServiceKey(bootClasspathProvider.getId());
            classpathServiceKey = new MtlDexArchiveBuilderTransform.ClasspathServiceKey(libraryClasspathProvider.getId());
            INSTANCE.registerService(
                    bootclasspathServiceKey, () -> new MtlDexArchiveBuilderTransform.ClasspathService(bootClasspathProvider));
            INSTANCE.registerService(
                    classpathServiceKey, () -> new MtlDexArchiveBuilderTransform.ClasspathService(libraryClasspathProvider));

            for (TransformInput input : transformInvocation.getInputs()) {
                for (DirectoryInput dirInput : input.getDirectoryInputs()) {
                    if (!AtlasBuildContext.atlasMainDexHelperMap.get(variantOutputContext.getVariantContext().getVariantName()).getInputDirs().contains(dirInput.getFile())) {
                        continue;
                    }
                    logger.warning("Dir input %s", dirInput.getFile().toString());

                    convertToDexArchive(
                            transformInvocation.getContext(),
                            dirInput,
                            outputProvider,
                            isIncremental,
                            bootclasspathServiceKey,
                            classpathServiceKey,
                            additionalPaths);
                }

                for (JarInput jarInput : input.getJarInputs()) {
                    if ( !AtlasBuildContext.atlasMainDexHelperMap.get(variantOutputContext.getVariantContext().getVariantName()).getAllMainDexJars().contains(jarInput.getFile())) {
                        continue;
                    }
                    if (jarInput.getFile().getName().equals("instant-run-bootstrap.jar")){
                        continue;
                    }
                        logger.warning("Jar input %s", jarInput.getFile().toString());
                    MtlDexArchiveBuilderTransform.D8DesugaringCacheInfo cacheInfo =
                            getD8DesugaringCacheInfo(
                                    desugarIncrementalTransformHelper,
                                    bootclasspath,
                                    classpath,
                                    jarInput);

                    List<File> dexArchives =
                            processJarInput(
                                    transformInvocation.getContext(),
                                    jarInput,
                                    outputProvider,
                                    bootclasspathServiceKey,
                                    classpathServiceKey,
                                    cacheInfo);

                    if (!dexArchives.isEmpty()) {
                        cacheableItems.add(
                                new DexArchiveHandler.CacheableItem(
                                        jarInput,
                                        dexArchives,
                                        cacheInfo.orderedD8DesugaringDependencies));
                    }
                }
            }

            if (variantOutputContext.getVariantContext().getAtlasExtension().isAppBundlesEnabled() && variantOutputContext.getVariantContext().getAtlasExtension().getTBuildConfig().getDynamicFeatures().size() > 0){

                ClasspathServiceKey finalBootclasspathServiceKey = bootclasspathServiceKey;
                ClasspathServiceKey finalClasspathServiceKey = classpathServiceKey;
                variantOutputContext.getAwbTransformMap().values().forEach(new Consumer<AwbTransform>() {
                    @Override
                    public void accept(AwbTransform awbTransform) {
                        AtomicInteger atomicInteger = new AtomicInteger(1);
                        if (awbTransform.getAwbBundle().dynamicFeature){
                            awbTransform.getInputDirs().forEach(new Consumer<File>() {
                                @Override
                                public void accept(File file) {
                                    logger.warning("dynamic feature dir input %s", file.getAbsolutePath());

                                    convertFeatureToDexArchive(awbTransform.getAwbBundle(),transformInvocation.getContext(),TransformInputUtils.makeDirectoryInput(file),false, finalBootclasspathServiceKey,
                                            finalClasspathServiceKey,
                                            additionalPaths,atomicInteger);
                                }
                            });

                            awbTransform.getInputFiles().forEach(new Consumer<File>() {
                                @Override
                                public void accept(File file) {
                                    logger.warning("dynamic feature JarInput input %s", file.getAbsolutePath());

                                    JarInput jarInput = TransformInputUtils.makeJarInput(file,variantOutputContext.getVariantContext());
                                            MtlDexArchiveBuilderTransform.D8DesugaringCacheInfo cacheInfo =
                                            getD8DesugaringCacheInfo(
                                                    desugarIncrementalTransformHelper,
                                                    bootclasspath,
                                                    classpath,
                                                    jarInput);
                                    try {
                                        List<File> dexArchives =
                                                processFeatureJarInput(
                                                        awbTransform.getAwbBundle(),
                                                        transformInvocation.getContext(),
                                                        jarInput,
                                                        finalBootclasspathServiceKey,
                                                        finalClasspathServiceKey,
                                                        cacheInfo,
                                                        atomicInteger);
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                }
                            });


                            awbTransform.getInputLibraries().forEach(new Consumer<File>() {
                                @Override
                                public void accept(File file) {
                                    logger.warning("dynamic feature Librarie input %s", file.getAbsolutePath());
                                    JarInput jarInput = TransformInputUtils.makeJarInput(file,variantOutputContext.getVariantContext());
                                    MtlDexArchiveBuilderTransform.D8DesugaringCacheInfo cacheInfo =
                                            getD8DesugaringCacheInfo(
                                                    desugarIncrementalTransformHelper,
                                                    bootclasspath,
                                                    classpath,
                                                    jarInput);
                                    try {
                                        List<File> dexArchives =
                                                processFeatureJarInput(
                                                        awbTransform.getAwbBundle(),
                                                        transformInvocation.getContext(),
                                                        jarInput,
                                                        finalBootclasspathServiceKey,
                                                        finalClasspathServiceKey,
                                                        cacheInfo,
                                                        atomicInteger);
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                }
                            });

                        }else {



                        }


                    }
                });

            }


            // all work items have been submitted, now wait for completion.
            if (useGradleWorkers) {
                transformInvocation.getContext().getWorkerExecutor().await();
            } else {
                executor.waitForTasksWithQuickFail(true);
            }

            // if we are in incremental mode, delete all removed files.


            // and finally populate the caches.
            if (!cacheableItems.isEmpty()) {
                cacheHandler.populateCache(cacheableItems);
            }

            logger.warning("Done with all dex archive conversions");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TransformException(e);
        } catch (Exception e) {
            PluginCrashReporter.maybeReportException(e);
            logger.error(null, Throwables.getStackTraceAsString(e));
            throw new TransformException(e);
        } finally {
            if (classpathServiceKey != null) {
                INSTANCE.removeService(classpathServiceKey);
            }
            if (bootclasspathServiceKey != null) {
                INSTANCE.removeService(bootclasspathServiceKey);
            }
        }
    }

    @NonNull
    private MtlDexArchiveBuilderTransform.D8DesugaringCacheInfo getD8DesugaringCacheInfo(
            @Nullable MtlDesugarIncrementalTransformHelper desugarIncrementalTransformHelper,
            @NonNull List<Path> bootclasspath,
            @NonNull List<Path> classpath,
            @NonNull JarInput jarInput) {

        if (java8LangSupportType != VariantScope.Java8LangSupport.D8) {
            return MtlDexArchiveBuilderTransform.D8DesugaringCacheInfo.NO_INFO;
        }

        Preconditions.checkNotNull(desugarIncrementalTransformHelper);

        Set<Path> unorderedD8DesugaringDependencies =
                desugarIncrementalTransformHelper.getDependenciesPaths(jarInput.getFile().toPath());

        // Don't cache libraries depending on class files in folders:
        // Folders content is expected to change often so probably not worth paying the cache cost
        // if we frequently need to rebuild anyway.
        // Supporting dependency to class files would also require special care to respect order.
        if (unorderedD8DesugaringDependencies
                .stream()
                .anyMatch(path -> !path.toString().endsWith(SdkConstants.DOT_JAR))) {
            return MtlDexArchiveBuilderTransform.D8DesugaringCacheInfo.DONT_CACHE;
        }

        // DesugaringGraph is not calculating the bootclasspath dependencies so just keep the full
        // bootclasspath for now.
        List<Path> bootclasspathPaths =
                bootclasspath
                        .stream()
                        .distinct()
                        .collect(Collectors.toList());

        List<Path> classpathJars =
                classpath
                        .stream()
                        .distinct()
                        .filter(unorderedD8DesugaringDependencies::contains)
                        .collect(Collectors.toList());

        List<Path> allDependencies =
                new ArrayList<>(bootclasspathPaths.size() + classpathJars.size());

        allDependencies.addAll(bootclasspathPaths);
        allDependencies.addAll(classpathJars);
        return new MtlDexArchiveBuilderTransform.D8DesugaringCacheInfo(allDependencies);
    }


    @NonNull
    private List<File> processJarInput(
            @NonNull Context context,
            @NonNull JarInput jarInput,
            @NonNull TransformOutputProvider transformOutputProvider,
            @NonNull MtlDexArchiveBuilderTransform.ClasspathServiceKey bootclasspath,
            @NonNull MtlDexArchiveBuilderTransform.ClasspathServiceKey classpath,
            @NonNull MtlDexArchiveBuilderTransform.D8DesugaringCacheInfo cacheInfo)
            throws Exception {
        Preconditions.checkState(
                jarInput.getFile().exists(),
                "File %s does not exist, yet it is reported as input. Try \n"
                        + "cleaning the build directory.",
                jarInput.getFile().toString());
        return convertJarToDexArchive(
                context,
                jarInput,
                transformOutputProvider,
                bootclasspath,
                classpath,
                cacheInfo);
    }


    @NonNull
    private List<File> processFeatureJarInput(
            AwbBundle awbBundle,
            @NonNull Context context,
            @NonNull JarInput jarFile,
            @NonNull ClasspathServiceKey bootclasspath,
            @NonNull ClasspathServiceKey classpath,
            @NonNull D8DesugaringCacheInfo cacheInfo, AtomicInteger atomicInteger)
            throws Exception {
        Preconditions.checkState(
                jarFile.getFile().exists(),
                "File %s does not exist, yet it is reported as input. Try \n"
                        + "cleaning the build directory.",
                jarFile.toString());
        return convertFeatureJarToDexArchive(
                awbBundle,
                context,
                jarFile,
                bootclasspath,
                classpath,
                cacheInfo,
                atomicInteger);
    }



    private List<File> convertFeatureJarToDexArchive(
            AwbBundle awbBundle,
            @NonNull Context context,
            @NonNull JarInput toConvert,
            @NonNull ClasspathServiceKey bootclasspath,
            @NonNull ClasspathServiceKey classpath,
            @NonNull D8DesugaringCacheInfo cacheInfo, AtomicInteger atomicInteger)
            throws Exception {

        if (cacheInfo != MtlDexArchiveBuilderTransform.D8DesugaringCacheInfo.DONT_CACHE) {
            File cachedVersion =
                    cacheHandler.getCachedVersionIfPresent(
                            toConvert, cacheInfo.orderedD8DesugaringDependencies);
            if (cachedVersion != null) {
                logger.warning("hit cache:" + toConvert.getFile().getAbsolutePath() + " -> " + cachedVersion.getAbsolutePath());
                File outputFile = getFeatureOutputJar(awbBundle,atomicInteger.getAndIncrement());
                Files.copy(
                        cachedVersion.toPath(),
                        outputFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
                // no need to try to cache an already cached version.
                return ImmutableList.of();
            }
            logger.warning("miss cache:" + toConvert.getFile().getAbsolutePath());

        }


        return convertFeatureToDexArchive(
                awbBundle,
                context,
                toConvert,
                false,
                bootclasspath,
                classpath,
                ImmutableSet.of(),atomicInteger);
    }


    private List<File> convertJarToDexArchive(
            @NonNull Context context,
            @NonNull JarInput toConvert,
            @NonNull TransformOutputProvider transformOutputProvider,
            @NonNull MtlDexArchiveBuilderTransform.ClasspathServiceKey bootclasspath,
            @NonNull MtlDexArchiveBuilderTransform.ClasspathServiceKey classpath,
            @NonNull MtlDexArchiveBuilderTransform.D8DesugaringCacheInfo cacheInfo)
            throws Exception {

        if (cacheInfo != MtlDexArchiveBuilderTransform.D8DesugaringCacheInfo.DONT_CACHE) {
            File cachedVersion =
                    cacheHandler.getCachedVersionIfPresent(
                            toConvert, cacheInfo.orderedD8DesugaringDependencies);
            if (cachedVersion != null) {
                logger.warning("hit cache:" + toConvert.getFile().getAbsolutePath() + " -> " + cachedVersion.getAbsolutePath());
                File outputFile = getOutputForJar(transformOutputProvider, toConvert, null);
                Files.copy(
                        cachedVersion.toPath(),
                        outputFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
                // no need to try to cache an already cached version.
                return ImmutableList.of();
            }
            logger.warning("miss cache:" + toConvert.getFile().getAbsolutePath());

        }


        return convertToDexArchive(
                context,
                toConvert,
                transformOutputProvider,
                false,
                bootclasspath,
                classpath,
                ImmutableSet.of());
    }

    public static class DexConversionParameters implements Serializable {
        private final QualifiedContent input;
        private final MtlDexArchiveBuilderTransform.ClasspathServiceKey bootClasspath;
        private final MtlDexArchiveBuilderTransform.ClasspathServiceKey classpath;
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
        private final VariantScope.Java8LangSupport java8LangSupportType;
        @NonNull
        private final Set<File> additionalPaths;
        @Nonnull
        private final MessageReceiver messageReceiver;
        private final boolean isInstantRun;

        public DexConversionParameters(
                @NonNull QualifiedContent input,
                @NonNull MtlDexArchiveBuilderTransform.ClasspathServiceKey bootClasspath,
                @NonNull MtlDexArchiveBuilderTransform.ClasspathServiceKey classpath,
                @NonNull File output,
                int numberOfBuckets,
                int buckedId,
                int minSdkVersion,
                @NonNull List<String> dexAdditionalParameters,
                int inBufferSize,
                int outBufferSize,
                @NonNull DexerTool dexer,
                boolean isDebuggable,
                boolean isIncremental,
                @NonNull VariantScope.Java8LangSupport java8LangSupportType,
                @NonNull Set<File> additionalPaths,
                @Nonnull MessageReceiver messageReceiver,
                boolean isInstantRun) {
            this.input = input;
            this.bootClasspath = bootClasspath;
            this.classpath = classpath;
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
            this.java8LangSupportType = java8LangSupportType;
            this.additionalPaths = additionalPaths;
            this.messageReceiver = messageReceiver;
            this.isInstantRun = isInstantRun;
        }

        public boolean belongsToThisBucket(String path) {
            return getBucketForFile(input, path, numberOfBuckets, isInstantRun) == buckedId;
        }

        public boolean isDirectoryBased() {
            return input instanceof DirectoryInput;
        }
    }

    public static class DexConversionWorkAction implements Runnable {

        private final MtlDexArchiveBuilderTransform.DexConversionParameters dexConversionParameters;

        @Inject
        public DexConversionWorkAction(@NonNull MtlDexArchiveBuilderTransform.DexConversionParameters dexConversionParameters) {
            this.dexConversionParameters = dexConversionParameters;
        }

        @Override
        public void run() {
            try {
                launchProcessing(
                        dexConversionParameters,
                        System.out,
                        System.err,
                        dexConversionParameters.messageReceiver);
            } catch (Exception e) {
                throw new BuildException(e.getMessage(), e);
            }
        }
    }

    private static class D8DesugaringCacheInfo {

        @NonNull
        private static final MtlDexArchiveBuilderTransform.D8DesugaringCacheInfo NO_INFO =
                new MtlDexArchiveBuilderTransform.D8DesugaringCacheInfo(Collections.emptyList());

        @NonNull
        private static final MtlDexArchiveBuilderTransform.D8DesugaringCacheInfo DONT_CACHE =
                new MtlDexArchiveBuilderTransform.D8DesugaringCacheInfo(Collections.emptyList());

        @NonNull
        private final List<Path> orderedD8DesugaringDependencies;

        private D8DesugaringCacheInfo(@NonNull List<Path> orderedD8DesugaringDependencies) {
            this.orderedD8DesugaringDependencies = orderedD8DesugaringDependencies;
        }
    }

    private static DexArchiveBuilder getDexArchiveBuilder(
            int minSdkVersion,
            @NonNull List<String> dexAdditionalParameters,
            int inBufferSize,
            int outBufferSize,
            @NonNull MtlDexArchiveBuilderTransform.ClasspathServiceKey bootClasspath,
            @NonNull MtlDexArchiveBuilderTransform.ClasspathServiceKey classpath,
            @NonNull DexerTool dexer,
            boolean isDebuggable,
            boolean d8DesugaringEnabled,
            @NonNull OutputStream outStream,
            @NonNull OutputStream errStream,
            @NonNull MessageReceiver messageReceiver) {

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
                                DexArchiveHandler.isJumboModeEnabledForDx());

                dexArchiveBuilder = DexArchiveBuilder.createDxDexBuilder(config);
                break;
            case D8:
                dexArchiveBuilder =
                        DexArchiveBuilder.createD8DexBuilder(
                                minSdkVersion,
                                isDebuggable,
                                INSTANCE.getService(bootClasspath).getService(),
                                INSTANCE.getService(classpath).getService(),
                                d8DesugaringEnabled,
                                messageReceiver);
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
            boolean isIncremental,
            @NonNull MtlDexArchiveBuilderTransform.ClasspathServiceKey bootClasspath,
            @NonNull MtlDexArchiveBuilderTransform.ClasspathServiceKey classpath,
            @NonNull Set<File> additionalPaths) {

        logger.verbose("Dexing %s", input.getFile().getAbsolutePath());

        if (!input.getFile().isDirectory() && isNotVilid(input.getFile())) {
            return ImmutableList.of();
        }

        ImmutableList.Builder<File> dexArchives = ImmutableList.builder();
        for (int bucketId = 0; bucketId < numberOfBuckets; bucketId++) {

            File preDexOutputFile;
            if (input instanceof DirectoryInput) {
                preDexOutputFile =
                        getOutputForDir(outputProvider, (DirectoryInput) input, bucketId);
                FileUtils.mkdirs(preDexOutputFile);
            } else {
                preDexOutputFile = getOutputForJar(outputProvider, (JarInput) input, bucketId);
            }

            dexArchives.add(preDexOutputFile);
            MtlDexArchiveBuilderTransform.DexConversionParameters parameters =
                    new MtlDexArchiveBuilderTransform.DexConversionParameters(
                            input,
                            bootClasspath,
                            classpath,
                            preDexOutputFile,
                            numberOfBuckets,
                            bucketId,
                            minSdkVersion,
                            dexOptions.getAdditionalParameters(),
                            inBufferSize,
                            outBufferSize,
                            dexer,
                            isDebuggable,
                            false,
                            java8LangSupportType,
                            additionalPaths,
                            new SerializableMessageReceiver(messageReceiver),
                            isInstantRun);

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
                                            messageReceiver);
                            ProcessOutput output = null;
                            try (Closeable ignored = output = outputHandler.createOutput()) {
                                launchProcessing(
                                        parameters,
                                        output.getStandardOutput(),
                                        output.getErrorOutput(),
                                        messageReceiver);
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
        return dexArchives.build();
    }


    private File getFeatureOutputForDir(AwbBundle awbBundle, Integer bucketId){

        return variantOutputContext.getFeatureDexArchiveFolder(awbBundle,String.valueOf(bucketId));

    }


    private File getFeatureOutputJar(AwbBundle awbBundle, Integer bucketId){

        return variantOutputContext.getFeatureDexArchiveFolder(awbBundle,bucketId+".jar");

    }


    private List<File> convertFeatureToDexArchive(AwbBundle awbBundle,
                                                  @NonNull Context context,
                                                  @NonNull QualifiedContent input,
                                                  boolean isIncremental,
                                                  @NonNull MtlDexArchiveBuilderTransform.ClasspathServiceKey bootClasspath,
                                                  @NonNull MtlDexArchiveBuilderTransform.ClasspathServiceKey classpath,
                                                  @NonNull Set<File> additionalPaths,AtomicInteger atomicInteger) {

        logger.verbose("Dexing %s", input.getFile().getAbsolutePath());

        if (!input.getFile().isDirectory() && isNotVilid(input.getFile())) {
            return ImmutableList.of();
        }

        File file = input.getFile();

        ImmutableList.Builder<File> dexArchives = ImmutableList.builder();
        for (int bucketId = 0; bucketId < numberOfBuckets; bucketId++) {

            File preDexOutputFile = null;
            if (input instanceof DirectoryInput) {
                preDexOutputFile =
                        getFeatureOutputForDir(awbBundle, bucketId);
                FileUtils.mkdirs(preDexOutputFile);
            }else {
                preDexOutputFile = getFeatureOutputJar(awbBundle, atomicInteger.getAndIncrement());
                if (!preDexOutputFile.getParentFile().exists()){
                    preDexOutputFile.getParentFile().mkdirs();
                }

            }

            dexArchives.add(preDexOutputFile);
            MtlDexArchiveBuilderTransform.DexConversionParameters parameters =
                    new MtlDexArchiveBuilderTransform.DexConversionParameters(
                            input,
                            bootClasspath,
                            classpath,
                            preDexOutputFile,
                            numberOfBuckets,
                            bucketId,
                            minSdkVersion,
                            dexOptions.getAdditionalParameters(),
                            inBufferSize,
                            outBufferSize,
                            dexer,
                            isDebuggable,
                            isIncremental,
                            java8LangSupportType,
                            additionalPaths,
                            new SerializableMessageReceiver(messageReceiver),
                            isInstantRun);

            if (useGradleWorkers) {
                context.getWorkerExecutor()
                        .submit(
                                MtlDexArchiveBuilderTransform.DexConversionWorkAction.class,
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
                                            messageReceiver);
                            ProcessOutput output = null;
                            try (Closeable ignored = output = outputHandler.createOutput()) {
                                launchProcessing(
                                        parameters,
                                        output.getStandardOutput(),
                                        output.getErrorOutput(),
                                        messageReceiver);
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
        return dexArchives.build();
    }

    private boolean isNotVilid(File file) {
        if (file == null || !file.exists()) {
            return true;
        }
        JarFile jarFile = null;
        try {
            jarFile = new JarFile(file);

            Enumeration<JarEntry> entryEnumeration = jarFile.entries();
            while (entryEnumeration.hasMoreElements()) {
                JarEntry jarEntry = entryEnumeration.nextElement();
                if (jarEntry.getName().endsWith(".class")) {
                    return false;
                }
            }
        } catch (IOException e) {

        } finally {
            try {
                if (jarFile != null)
                    jarFile.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return true;

    }

    private static void launchProcessing(
            @NonNull MtlDexArchiveBuilderTransform.DexConversionParameters dexConversionParameters,
            @NonNull OutputStream outStream,
            @NonNull OutputStream errStream,
            @NonNull MessageReceiver receiver)
            throws IOException, URISyntaxException {
        DexArchiveBuilder dexArchiveBuilder =
                getDexArchiveBuilder(
                        dexConversionParameters.minSdkVersion,
                        dexConversionParameters.dexAdditionalParameters,
                        dexConversionParameters.inBufferSize,
                        dexConversionParameters.outBufferSize,
                        dexConversionParameters.bootClasspath,
                        dexConversionParameters.classpath,
                        dexConversionParameters.dexer,
                        dexConversionParameters.isDebuggable,
                        VariantScope.Java8LangSupport.D8
                                == dexConversionParameters.java8LangSupportType,
                        outStream,
                        errStream,
                        receiver);

        Path inputPath = dexConversionParameters.input.getFile().toPath();
        Predicate<String> bucketFilter = dexConversionParameters::belongsToThisBucket;

        boolean hasIncrementalInfo =
                dexConversionParameters.isDirectoryBased() && dexConversionParameters.isIncremental;
        Predicate<String> toProcess =
                hasIncrementalInfo
                        ? path -> {
                    File resolved = inputPath.resolve(path).toFile();
                    if (dexConversionParameters.additionalPaths.contains(resolved)) {
                        return true;
                    }
                    Map<File, Status> changedFiles =
                            ((DirectoryInput) dexConversionParameters.input)
                                    .getChangedFiles();

                    Status status = changedFiles.get(resolved);
                    return status == Status.ADDED || status == Status.CHANGED;
                }
                        : path -> true;

        bucketFilter = bucketFilter.and(toProcess);

        logger.verbose("Dexing '" + inputPath + "' to '" + dexConversionParameters.output + "'");

        try (ClassFileInput input = ClassFileInputs.fromPath(inputPath);
             Stream<ClassFileEntry> entries = input.entries(bucketFilter)) {
            dexArchiveBuilder.convert(
                    entries,
                    Paths.get(new URI(dexConversionParameters.output)),
                    dexConversionParameters.isDirectoryBased());
        } catch (DexArchiveBuilderException ex) {
            throw new DexArchiveBuilderException("Failed to process " + inputPath.toString(), ex);
        }
    }

    @NonNull
    private static List<String> getClasspath(
            @NonNull TransformInvocation transformInvocation,
            @NonNull VariantScope.Java8LangSupport java8LangSupportType) {
        if (java8LangSupportType != VariantScope.Java8LangSupport.D8) {
            return Collections.emptyList();
        }

        if (Boolean.TRUE.booleanValue()){
            return Collections.emptyList();
        }
        ImmutableList.Builder<String> classpathEntries = ImmutableList.builder();

        Iterable<TransformInput> dependencies =
                Iterables.concat(
                        transformInvocation.getInputs(), transformInvocation.getReferencedInputs());
        classpathEntries.addAll(
                TransformInputUtil.getDirectories(dependencies)
                        .stream()
                        .map(File::getPath)
                        .distinct()
                        .iterator());

        classpathEntries.addAll(
                Streams.stream(dependencies)
                        .flatMap(transformInput -> transformInput.getJarInputs().stream())
                        .filter(jarInput -> jarInput.getStatus() != Status.REMOVED)
                        .map(jarInput -> jarInput.getFile().getPath())
                        .distinct()
                        .iterator());

        return classpathEntries.build();
    }

    @NonNull
    private static List<String> getBootClasspath(
            @NonNull Supplier<List<File>> androidJarClasspath,
            @NonNull VariantScope.Java8LangSupport java8LangSupportType) {

        if (java8LangSupportType != VariantScope.Java8LangSupport.D8) {
            return Collections.emptyList();
        }
        ImmutableList.Builder<String> classpathEntries = ImmutableList.builder();
        classpathEntries.addAll(androidJarClasspath.get().stream().map(File::getPath).iterator());

        return classpathEntries.build();
    }

    @NonNull
    private static File getOutputForJar(
            @NonNull TransformOutputProvider output,
            @NonNull JarInput qualifiedContent,
            @Nullable Integer bucketId) {
        return output.getContentLocation(
                qualifiedContent.getFile().getName().split("\\.")[0] + (bucketId == null ? "" : ("-" + bucketId)),
                ImmutableSet.of(ExtendedContentType.DEX_ARCHIVE),
                qualifiedContent.getScopes(),
                Format.JAR);
    }

    @NonNull
    private File getOutputForDir(
            @NonNull TransformOutputProvider output,
            @NonNull DirectoryInput directoryInput,
            int bucketId) {
        String name;

            name = directoryInput.getFile().getName();

        return output.getContentLocation(
                name,
                ImmutableSet.of(ExtendedContentType.DEX_ARCHIVE),
                directoryInput.getScopes(),
                Format.DIRECTORY);
    }


    /**
     * Returns the bucket for the specified path. For jar inputs, path in the jar file should be
     * specified (both relative and absolute path work). For directories, absolute path should be
     * specified.
     */
    private static int getBucketForFile(
            @NonNull QualifiedContent content,
            @NonNull String path,
            int numberOfBuckets,
            boolean isInstantRun) {
        if (!isInstantRun || !(content instanceof DirectoryInput)) {
            return Math.abs(path.hashCode()) % numberOfBuckets;
        } else {
            Path filePath = Paths.get(path);
            Preconditions.checkArgument(filePath.isAbsolute(), "Path should be absolute: " + path);
            Path packagePath = filePath.getParent();
            if (packagePath == null) {
                return 0;
            }
            return Math.abs(packagePath.toString().hashCode()) % numberOfBuckets;
        }
    }


}
