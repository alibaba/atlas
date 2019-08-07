package com.taobao.android.builder.tasks.manager.transform;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.build.api.artifact.BuildableArtifact;
import com.android.build.api.transform.*;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.internal.api.artifact.BuildableArtifactUtil;
import com.android.build.gradle.internal.crash.PluginCrashReporter;
import com.android.build.gradle.internal.pipeline.ExtendedContentType;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.transforms.DexMergerTransform;
import com.android.build.gradle.internal.transforms.DexMergerTransformCallable;
import com.android.build.gradle.internal.transforms.TransformInputUtil;
import com.android.builder.dexing.DexMergerTool;
import com.android.builder.dexing.DexingType;
import com.android.ide.common.blame.Message;
import com.android.ide.common.blame.MessageReceiver;
import com.android.ide.common.blame.ParsingProcessOutputHandler;
import com.android.ide.common.blame.parser.DexParser;
import com.android.ide.common.blame.parser.ToolOutputParser;
import com.android.ide.common.process.ProcessException;
import com.android.ide.common.process.ProcessOutput;
import com.android.ide.common.process.ProcessOutputHandler;
import com.android.utils.FileUtils;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.*;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipFile;

/**
 * @ClassName MtlDexMergeTransform
 * @Description TODO
 * @Author zhayu.ll
 * @Date 2019-07-26 10:52
 * @Version 1.0
 */
public class MtlDexMergeTransform extends Transform {


    private static final LoggerWrapper logger = LoggerWrapper.getLogger(MtlDexMergeTransform.class);
    @VisibleForTesting
    public static final int ANDROID_L_MAX_DEX_FILES = 100;
    // We assume the maximum number of dexes that will be produced from the external dependencies is
    // EXTERNAL_DEPS_DEX_FILES, so the remaining ANDROID_L_MAX_DEX_FILES - EXTERNAL_DEPS_DEX_FILES
    // can be used for the remaining inputs. This is a generous assumption that 50 completely full
    // dex files will be needed for the external dependencies.
    @VisibleForTesting public static final int EXTERNAL_DEPS_DEX_FILES = 50;

    @NonNull
    private final DexingType dexingType;
    @Nullable
    private final BuildableArtifact mainDexListFile;
    @NonNull private final BuildableArtifact duplicateClassesCheck;
    @NonNull private final DexMergerTool dexMerger;
    private final int minSdkVersion;
    private final boolean isDebuggable;
    @NonNull private final MessageReceiver messageReceiver;
    @NonNull private final ForkJoinPool forkJoinPool = new ForkJoinPool();
    private final boolean includeFeaturesInScopes;
    private final boolean isInInstantRunMode;



    public MtlDexMergeTransform(
            @NonNull DexingType dexingType,
            @Nullable BuildableArtifact mainDexListFile,
            @NonNull BuildableArtifact duplicateClassesCheck,
            @NonNull MessageReceiver messageReceiver,
            @NonNull DexMergerTool dexMerger,
            int minSdkVersion,
            boolean isDebuggable,
            boolean includeFeaturesInScopes,
            boolean isInInstantRunMode) {
        this.dexingType = dexingType;
        this.mainDexListFile = mainDexListFile;
        this.duplicateClassesCheck = duplicateClassesCheck;
        this.dexMerger = dexMerger;
        this.minSdkVersion = minSdkVersion;
        this.isDebuggable = isDebuggable;
        Preconditions.checkState(
                (dexingType == DexingType.LEGACY_MULTIDEX) == (mainDexListFile != null),
                "Main dex list must only be set when in legacy multidex");
        this.messageReceiver = messageReceiver;
        this.includeFeaturesInScopes = includeFeaturesInScopes;
        this.isInInstantRunMode = isInInstantRunMode;
    }

    @NonNull
    @Override
    public String getName() {
        return "dexMerger";
    }

    @NonNull
    @Override
    public Set<QualifiedContent.ContentType> getInputTypes() {
        return ImmutableSet.of(ExtendedContentType.DEX_ARCHIVE);
    }

    @NonNull
    @Override
    public Set<QualifiedContent.ContentType> getOutputTypes() {
        return TransformManager.CONTENT_DEX;
    }

    @NonNull
    @Override
    public Set<? super QualifiedContent.Scope> getScopes() {
        if (includeFeaturesInScopes) {
            return TransformManager.SCOPE_FULL_WITH_IR_AND_FEATURES;
        }
        return TransformManager.SCOPE_FULL_WITH_IR_FOR_DEXING;
    }

    @NonNull
    @Override
    public Collection<SecondaryFile> getSecondaryFiles() {
        SecondaryFile dupCheck = SecondaryFile.nonIncremental(duplicateClassesCheck);
        if (mainDexListFile != null) {
            return ImmutableList.of(SecondaryFile.nonIncremental(mainDexListFile), dupCheck);
        } else {
            return ImmutableList.of(dupCheck);
        }
    }

    @NonNull
    @Override
    public Map<String, Object> getParameterInputs() {
        Map<String, Object> params = Maps.newHashMapWithExpectedSize(4);
        params.put("dexing-type", dexingType.name());
        params.put("dex-merger-tool", dexMerger.name());
        params.put("is-debuggable", isDebuggable);
        params.put("min-sdk-version", minSdkVersion);
        params.put("is-in-instant-run", isInInstantRunMode);

        return params;
    }

    @Override
    public boolean isIncremental() {
        return true;
    }

    @Override
    public boolean isCacheable() {
        return true;
    }

    @Override
    public void transform(@NonNull TransformInvocation transformInvocation)
            throws TransformException, IOException, InterruptedException {
        TransformOutputProvider outputProvider = transformInvocation.getOutputProvider();
        Preconditions.checkNotNull(
                outputProvider, "Missing output object for transform " + getName());

        ProcessOutputHandler outputHandler =
                new ParsingProcessOutputHandler(
                        new ToolOutputParser(new DexParser(), Message.Kind.ERROR, logger),
                        new ToolOutputParser(new DexParser(), logger),
                        messageReceiver);

        if (!transformInvocation.isIncremental()) {
            outputProvider.deleteAll();
        }

        ProcessOutput output = null;
        List<ForkJoinTask<Void>> mergeTasks;
        try (Closeable ignored = output = outputHandler.createOutput()) {
            if (dexingType == DexingType.NATIVE_MULTIDEX && isDebuggable) {

                logger.warning("use NATIVE_MULTIDEX");
                mergeTasks =
                        handleNativeMultiDexDebug(
                                transformInvocation.getInputs(),
                                output,
                                outputProvider,
                                transformInvocation.isIncremental());
            } else {
                logger.warning("use mono-dex");
                mergeTasks = mergeDex(transformInvocation.getInputs(), output, outputProvider);
            }

            // now wait for all merge tasks completion
            mergeTasks.forEach(ForkJoinTask::join);
        } catch (Exception e) {
            PluginCrashReporter.maybeReportException(e);
            // Print the error always, even without --stacktrace
            logger.error(null, Throwables.getStackTraceAsString(e));
            throw new TransformException(e);
        } finally {
            if (output != null) {
                try {
                    outputHandler.handleOutput(output);
                } catch (ProcessException e) {
                    // ignore this one
                }
            }
            forkJoinPool.shutdown();
            forkJoinPool.awaitTermination(100, TimeUnit.SECONDS);
        }
    }

    /**
     * For legacy and mono-dex we always merge all dex archives, non-incrementally. For release
     * native multidex we do the same, to get the smallest possible dex files.
     */
    @NonNull
    private List<ForkJoinTask<Void>> mergeDex(
            @NonNull Collection<TransformInput> inputs,
            @NonNull ProcessOutput output,
            @NonNull TransformOutputProvider outputProvider)
            throws IOException {
        Iterator<Path> dirInputs =
                TransformInputUtil.getDirectories(inputs).stream().map(File::toPath).iterator();
        Iterator<Path> jarInputs =
                inputs.stream()
                        .flatMap(transformInput -> transformInput.getJarInputs().stream())
                        .filter(jarInput -> jarInput.getStatus() != Status.REMOVED && isValidJar(jarInput))
                        .map(jarInput -> jarInput.getFile().toPath())
                        .iterator();
        Iterator<Path> dexArchives = Iterators.concat(dirInputs, jarInputs);

        if (!dexArchives.hasNext()) {
            return ImmutableList.of();
        }

        File outputDir = getDexOutputLocation(outputProvider, "main", getScopes());
        // this deletes and creates the dir for the output
        FileUtils.cleanOutputDir(outputDir);

        Path mainDexClasses;
        if (mainDexListFile == null) {
            mainDexClasses = null;
        } else {
            mainDexClasses = BuildableArtifactUtil.singleFile(mainDexListFile).toPath();
        }

        return ImmutableList.of(submitForMerging(output, outputDir, dexArchives, mainDexClasses));
    }

    private static boolean isValidJar(JarInput jarInput) {
       File f =  jarInput.getFile();
       if (!f.exists()){
           return false;
       }
        ZipFile zipFile = null;
        try {
             zipFile = new ZipFile(f);
            if (zipFile.getEntry("classes.dex")!= null){
                return true;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            if (zipFile!= null){
                try {
                    zipFile.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        return false;


    }

    /**
     * All external library inputs will be merged together (this may result in multiple DEX files),
     * while other inputs will be merged individually (merging a single input might also result in
     * multiple DEX files).
     */
    @NonNull
    private List<ForkJoinTask<Void>> handleNativeMultiDexDebug(
            @NonNull Collection<TransformInput> inputs,
            @NonNull ProcessOutput output,
            @NonNull TransformOutputProvider outputProvider,
            boolean isIncremental)
            throws IOException {

        ImmutableList.Builder<ForkJoinTask<Void>> subTasks = ImmutableList.builder();

        List<DirectoryInput> directoryInputs = new ArrayList<>();
        List<JarInput> externalLibs = new ArrayList<>();
        List<JarInput> nonExternalJars = new ArrayList<>();
        collectInputsForNativeMultiDex(inputs, directoryInputs, externalLibs, nonExternalJars);

        boolean mergeAllInputs = shouldMergeInputsForNative(directoryInputs, nonExternalJars);
        subTasks.addAll(
                processDirectories(
                        output, outputProvider, isIncremental, directoryInputs, mergeAllInputs));

        if (!nonExternalJars.isEmpty()) {

            subTasks.addAll(
                       processNonExternalJarsSeparately(
                               output, outputProvider, isIncremental, nonExternalJars));
//            if (mergeAllInputs) {
//            subTasks.addAll(
//                    processNonExternalJarsTogether(
//                            output, outputProvider, isIncremental, nonExternalJars));
        }
//            } else {
//
//            }


        subTasks.addAll(processExternalJars(output, outputProvider, isIncremental, externalLibs));
        return subTasks.build();
    }

    /**
     * If all directory and non-external jar inputs should be merge individually, or we should merge
     * them together (all directory ones together, and all non-external jar ones together).
     *
     * <p>In order to improve the incremental build times, we will try to merge a single directory
     * input or non-external jar input in a single dex merger invocation i.e. a single input will
     * produce at least one dex file.
     *
     * <p>However, on Android L (API levels 21 and 22) there is a 100 dex files limit that we might
     * hit. Therefore, we might need to merge all directory inputs in a single dex merger
     * invocation. The same applies to non-external jar inputs.
     */
    private boolean shouldMergeInputsForNative(
            @NonNull Collection<DirectoryInput> directories,
            @NonNull Collection<JarInput> nonExternalJars) {

        return true;
//        if (minSdkVersion > 22) {
//            return false;
//        }
//
//        long dirInputsCount = directories.stream().filter(d -> d.getFile().exists()).count();
//        long nonExternalJarCount =
//                nonExternalJars.stream().filter(d -> d.getStatus() != Status.REMOVED).count();
//        return dirInputsCount + nonExternalJarCount
//                > ANDROID_L_MAX_DEX_FILES - EXTERNAL_DEPS_DEX_FILES;
    }

    /**
     * Reads all inputs and adds the input to the corresponding collection. NB: this method mutates
     * the collections in its parameters.
     */
    private static void collectInputsForNativeMultiDex(
            @NonNull Collection<TransformInput> inputs,
            @NonNull Collection<DirectoryInput> directoryInputs,
            @NonNull Collection<JarInput> externalLibs,
            @NonNull Collection<JarInput> nonExternalJars) {
        for (TransformInput input : inputs) {
            directoryInputs.addAll(input.getDirectoryInputs());

            for (JarInput jarInput : input.getJarInputs()) {
                if (!isValidJar(jarInput)){
                    continue;
                }

                if (jarInput.getScopes().equals(Collections.singleton(QualifiedContent.Scope.EXTERNAL_LIBRARIES))) {
                    externalLibs.add(jarInput);
                } else {
                    nonExternalJars.add(jarInput);
                }
            }
        }
    }

    private List<ForkJoinTask<Void>> processNonExternalJarsSeparately(
            @NonNull ProcessOutput output,
            @NonNull TransformOutputProvider outputProvider,
            boolean isIncremental,
            @NonNull Collection<JarInput> inputs)
            throws IOException {
        ImmutableList.Builder<ForkJoinTask<Void>> subTasks = ImmutableList.builder();

        for (JarInput jarInput : inputs) {
            File dexOutput = getDexOutputLocation(outputProvider, jarInput);

            if (!isIncremental || jarInput.getStatus() != Status.NOTCHANGED) {
                FileUtils.cleanOutputDir(dexOutput);
            }

            if (!isIncremental
                    || jarInput.getStatus() == Status.ADDED
                    || jarInput.getStatus() == Status.CHANGED) {
                subTasks.add(
                        submitForMerging(
                                output,
                                dexOutput,
                                Iterators.singletonIterator(jarInput.getFile().toPath()),
                                null));
            }
        }
        return subTasks.build();
    }

    @NonNull
    private List<ForkJoinTask<Void>> processNonExternalJarsTogether(
            @NonNull ProcessOutput output,
            @NonNull TransformOutputProvider outputProvider,
            boolean isIncremental,
            @NonNull Collection<JarInput> inputs)
            throws IOException {

        if (inputs.isEmpty()) {
            return ImmutableList.of();
        }

        Set<Status> statuses = EnumSet.noneOf(Status.class);
        Iterable<? super QualifiedContent.Scope> allScopes = new HashSet<>();
        for (JarInput jarInput : inputs) {
            statuses.add(jarInput.getStatus());
            allScopes = Iterables.concat(allScopes, jarInput.getScopes());
        }
        if (isIncremental && statuses.equals(Collections.singleton(Status.NOTCHANGED))) {
            return ImmutableList.of();
        }

        File mergedOutput =
                getDexOutputLocation(outputProvider, "nonExternalJars", Sets.newHashSet(allScopes));
        FileUtils.cleanOutputDir(mergedOutput);

        Iterator<Path> toMerge =
                inputs.stream()
                        .filter(i -> i.getStatus() != Status.REMOVED)
                        .map(i -> i.getFile().toPath())
                        .iterator();

        if (toMerge.hasNext()) {
            return ImmutableList.of(submitForMerging(output, mergedOutput, toMerge, null));
        } else {
            return ImmutableList.of();
        }
    }

    private List<ForkJoinTask<Void>> processDirectories(
            @NonNull ProcessOutput output,
            @NonNull TransformOutputProvider outputProvider,
            boolean isIncremental,
            @NonNull Collection<DirectoryInput> inputs,
            boolean mergeAllInputs)
            throws IOException {
        ImmutableList.Builder<ForkJoinTask<Void>> subTasks = ImmutableList.builder();
        List<DirectoryInput> deleted = new ArrayList<>();
        List<DirectoryInput> changed = new ArrayList<>();
        List<DirectoryInput> notChanged = new ArrayList<>();

        for (DirectoryInput directoryInput : inputs) {
            Path rootFolder = directoryInput.getFile().toPath();
            if (!Files.isDirectory(rootFolder)) {
                deleted.add(directoryInput);
            } else {
                boolean runAgain = !isIncremental;

                if (!runAgain) {
                    // check the incremental case
                    Collection<Status> statuses = directoryInput.getChangedFiles().values();
                    runAgain =
                            statuses.contains(Status.ADDED)
                                    || statuses.contains(Status.REMOVED)
                                    || statuses.contains(Status.CHANGED);
                }

                if (runAgain) {
                    changed.add(directoryInput);
                } else {
                    notChanged.add(directoryInput);
                }
            }
        }

        if (isIncremental && deleted.isEmpty() && changed.isEmpty()) {
            return subTasks.build();
        }

        if (mergeAllInputs) {
            File dexOutput =
                    getDexOutputLocation(
                            outputProvider,
                            isInInstantRunMode ? "slice_0" : "directories",
                            ImmutableSet.of(QualifiedContent.Scope.PROJECT));
            FileUtils.cleanOutputDir(dexOutput);

            Iterator<Path> toMerge =
                    Iterators.transform(
                            Iterators.concat(changed.iterator(), notChanged.iterator()),
                            i -> Objects.requireNonNull(i).getFile().toPath());
            if (toMerge.hasNext()) {
                subTasks.add(submitForMerging(output, dexOutput, toMerge, null));
            }
        } else {
            for (DirectoryInput directoryInput : deleted) {
                File dexOutput = getDexOutputLocation(outputProvider, directoryInput);
                FileUtils.cleanOutputDir(dexOutput);
            }
            for (DirectoryInput directoryInput : changed) {
                File dexOutput = getDexOutputLocation(outputProvider, directoryInput);


                FileUtils.cleanOutputDir(dexOutput);
                subTasks.add(
                        submitForMerging(
                                output,
                                dexOutput,
                                Iterators.singletonIterator(directoryInput.getFile().toPath()),
                                null));
            }
        }
        return subTasks.build();
    }

    @NonNull
    private List<ForkJoinTask<Void>> processExternalJars(
            @NonNull ProcessOutput output,
            @NonNull TransformOutputProvider outputProvider,
            boolean isIncremental,
            List<JarInput> externalLibs)
            throws IOException {
        ImmutableList.Builder<ForkJoinTask<Void>> subTasks = ImmutableList.builder();
        File externalLibsOutput =
                getDexOutputLocation(
                        outputProvider, "externalLibs", ImmutableSet.of(QualifiedContent.Scope.EXTERNAL_LIBRARIES));

        if (!isIncremental
                || externalLibs.stream().anyMatch(i -> i.getStatus() != Status.NOTCHANGED)) {
            // if non-incremental, or inputs have changed, merge again
            FileUtils.cleanOutputDir(externalLibsOutput);
            Iterator<Path> externalLibsToMerge =
                    externalLibs
                            .stream()
                            .filter(i -> i.getStatus() != Status.REMOVED)
                            .map(input -> input.getFile().toPath())
                            .iterator();
            if (externalLibsToMerge.hasNext()) {
                subTasks.add(
                        submitForMerging(output, externalLibsOutput, externalLibsToMerge, null));
            }
        }

        return subTasks.build();
    }

    /**
     * Add a merging task to the queue of tasks.
     *
     * @param output the process output that dx will output to.
     * @param dexOutputDir the directory to output dexes to
     * @param dexArchives the dex archive inputs
     * @param mainDexList the list of classes to keep in the main dex. Must be set <em>if and
     *     only</em> legacy multidex mode is used.
     * @return the {@link ForkJoinTask} instance for the submission.
     */
    @NonNull
    private ForkJoinTask<Void> submitForMerging(
            @NonNull ProcessOutput output,
            @NonNull File dexOutputDir,
            @NonNull Iterator<Path> dexArchives,
            @Nullable Path mainDexList) {
        DexMergerTransformCallable callable =
                new DexMergerTransformCallable(
                        messageReceiver,
                        dexingType,
                        output,
                        dexOutputDir,
                        dexArchives,
                        mainDexList,
                        forkJoinPool,
                        dexMerger,
                        minSdkVersion,
                        isDebuggable);
        return forkJoinPool.submit(callable);
    }

    @NonNull
    private File getDexOutputLocation(
            @NonNull TransformOutputProvider outputProvider, @NonNull QualifiedContent content) {
        String name;
        if (content.getName().startsWith("slice_")) {
            name = content.getName();
        } else {
            name = content.getFile().toString();
        }
        return outputProvider.getContentLocation(
                name, getOutputTypes(), content.getScopes(), Format.DIRECTORY);
    }

    @NonNull
    private File getDexOutputLocation(
            @NonNull TransformOutputProvider outputProvider,
            @NonNull String name,
            @NonNull Set<? super QualifiedContent.Scope> scopes) {
        return outputProvider.getContentLocation(name, getOutputTypes(), scopes, Format.DIRECTORY);
    }
}
