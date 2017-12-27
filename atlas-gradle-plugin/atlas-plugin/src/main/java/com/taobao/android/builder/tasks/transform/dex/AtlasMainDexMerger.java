package com.taobao.android.builder.tasks.transform.dex;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.build.api.transform.*;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.transforms.DexMergerTransform;
import com.android.build.gradle.internal.transforms.DexMergerTransformCallable;
import com.android.build.gradle.internal.transforms.TransformInputUtil;
import com.android.builder.core.ErrorReporter;
import com.android.builder.dexing.DexMergerTool;
import com.android.builder.dexing.DexingType;
import com.android.ide.common.blame.Message;
import com.android.ide.common.blame.ParsingProcessOutputHandler;
import com.android.ide.common.blame.parser.DexParser;
import com.android.ide.common.blame.parser.ToolOutputParser;
import com.android.ide.common.process.ProcessException;
import com.android.ide.common.process.ProcessOutput;
import com.android.ide.common.process.ProcessOutputHandler;
import com.android.utils.FileUtils;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.taobao.android.builder.AtlasBuildContext;
import org.gradle.api.file.FileCollection;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.stream.Collectors;

/**
 * @author lilong
 * @create 2017-12-27 上午9:43
 */


public class AtlasMainDexMerger {

    private static final LoggerWrapper logger = LoggerWrapper.getLogger(DexMergerTransform.class);
    @VisibleForTesting
    public static final int ANDROID_L_MAX_DEX_FILES = 100;
    @VisibleForTesting public static final int EXTERNAL_DEPS_DEX_FILES = 50;

    @NonNull private final DexingType dexingType;
    @Nullable private final FileCollection mainDexListFile;
    @NonNull private final DexMergerTool dexMerger;
    private final int minSdkVersion;
    private final boolean isDebuggable;
    @NonNull private final ErrorReporter errorReporter;
    @NonNull private final ForkJoinPool forkJoinPool = ForkJoinPool.commonPool();

    public AtlasMainDexMerger(DexingType dexingType, FileCollection mainDexListFile, ErrorReporter errorReporter, DexMergerTool dexMerger, int minSdkVersion, boolean isDebuggable) {
        this.dexingType = dexingType;
        this.mainDexListFile = mainDexListFile;
        this.dexMerger = dexMerger;
        this.minSdkVersion = minSdkVersion;
        this.isDebuggable = isDebuggable;
        Preconditions.checkState(
                (dexingType == DexingType.LEGACY_MULTIDEX) == (mainDexListFile != null),
                "Main dex list must only be set when in legacy multidex");
        this.errorReporter = errorReporter;

    }

    public void merge(TransformOutputProvider outputProvider) throws TransformException {

        if (dexMerger == DexMergerTool.D8) {
            logger.info("D8 is used to merge dex.");
        }
        try {
            outputProvider.deleteAll();
        } catch (IOException e) {
            e.printStackTrace();
        }

        ProcessOutputHandler outputHandler =
                new ParsingProcessOutputHandler(
                        new ToolOutputParser(new DexParser(), Message.Kind.ERROR, logger),
                        new ToolOutputParser(new DexParser(), logger),
                        errorReporter);

        ProcessOutput output = null;
        List<ForkJoinTask<Void>> mergeTasks;
        try (Closeable ignored = output = outputHandler.createOutput()) {
            if (dexingType == DexingType.NATIVE_MULTIDEX) {
                mergeTasks =
                        handleNativeMultiDex(
                                AtlasBuildContext.atlasMainDexHelper.getMainDexAchives(),
                                output,
                                outputProvider,
                                false);
            } else {
                mergeTasks =
                        handleLegacyAndMonoDex(
                                AtlasBuildContext.atlasMainDexHelper.getMainDexAchives(), output, outputProvider);
            }

            // now wait for all merge tasks completion
            mergeTasks.forEach(ForkJoinTask::join);

        } catch (IOException e) {
            throw new TransformException(e);
        } catch (Exception e) {
            throw new TransformException(Throwables.getRootCause(e));
        } finally {
            if (output != null) {
                try {
                    outputHandler.handleOutput(output);
                } catch (ProcessException e) {
                    // ignore this one
                }
            }
        }
    }

    /** For legacy and mono-dex we always merge all dex archives, non-incrementally. */
    @NonNull
    private List<ForkJoinTask<Void>> handleLegacyAndMonoDex(
            @NonNull Map<QualifiedContent, List<File>> allDexFiles,
            @NonNull ProcessOutput output,
            @NonNull TransformOutputProvider outputProvider)
            throws IOException {
        ImmutableList.Builder<Path> dexArchiveBuilder = ImmutableList.builder();
        for (List<File>inputs:allDexFiles.values()) {
            inputs.stream()
                    .map(file -> file.toPath())
                    .forEach(dexArchiveBuilder::add);
        }

        ImmutableList<Path> dexesToMerge = dexArchiveBuilder.build();
        if (dexesToMerge.isEmpty()) {
            return ImmutableList.of();
        }

        File outputDir =
                getDexOutputLocation(outputProvider, "main", TransformManager.SCOPE_FULL_PROJECT);
        // this deletes and creates the dir for the output
        FileUtils.cleanOutputDir(outputDir);

        Path mainDexClasses;
        if (mainDexListFile == null) {
            mainDexClasses = null;
        } else {
            mainDexClasses = mainDexListFile.getSingleFile().toPath();
        }

        return ImmutableList.of(submitForMerging(output, outputDir, dexesToMerge, mainDexClasses));
    }

    /**
     * All external library inputs will be merged together (this may result in multiple DEX files),
     * while other inputs will be merged individually (merging a single input might also result in
     * multiple DEX files).
     */
    @NonNull
    private List<ForkJoinTask<Void>> handleNativeMultiDex(
            @NonNull Map<QualifiedContent, List<File>> inputs,
            @NonNull ProcessOutput output,
            @NonNull TransformOutputProvider outputProvider,
            boolean isIncremental)
            throws IOException {
        return null;

//        ImmutableList.Builder<ForkJoinTask<Void>> subTasks = ImmutableList.builder();
//
//        List<DirectoryInput> directoryInputs = new ArrayList<>();
//        List<JarInput> externalLibs = new ArrayList<>();
//        List<JarInput> nonExternalJars = new ArrayList<>();
//        collectInputsForNativeMultiDex(inputs, directoryInputs, externalLibs, nonExternalJars);
//
//        boolean mergeAllInputs = shouldMergeInputsForNative(directoryInputs, nonExternalJars);
//        subTasks.addAll(
//                processDirectories(
//                        output, outputProvider, isIncremental, directoryInputs, mergeAllInputs));
//
//        if (!nonExternalJars.isEmpty()) {
//            if (mergeAllInputs) {
//                subTasks.addAll(
//                        processNonExternalJarsTogether(
//                                output, outputProvider, isIncremental, nonExternalJars));
//            } else {
//                subTasks.addAll(
//                        processNonExternalJarsSeparately(
//                                output, outputProvider, isIncremental, nonExternalJars));
//            }
//        }
//
//        subTasks.addAll(processExternalJars(output, outputProvider, isIncremental, externalLibs));
//        return subTasks.build();
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
        if (minSdkVersion > 22) {
            return false;
        }

        long dirInputsCount = directories.stream().filter(d -> d.getFile().exists()).count();
        long nonExternalJarCount =
                nonExternalJars.stream().filter(d -> d.getStatus() != Status.REMOVED).count();
        return dirInputsCount + nonExternalJarCount
                > ANDROID_L_MAX_DEX_FILES - EXTERNAL_DEPS_DEX_FILES;
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
            File dexOutput =
                    getDexOutputLocation(outputProvider, jarInput.getName(), jarInput.getScopes());

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
                                ImmutableList.of(jarInput.getFile().toPath()),
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

        Map<Status, List<JarInput>> byStatus =
                inputs.stream().collect(Collectors.groupingBy(JarInput::getStatus));

        if (isIncremental && byStatus.keySet().equals(Collections.singleton(Status.NOTCHANGED))) {
            return ImmutableList.of();
        }

        for (Status s : Status.values()) {
            byStatus.putIfAbsent(s, ImmutableList.of());
        }
        Set<? super QualifiedContent.Scope> allScopes =
                inputs.stream()
                        .map(JarInput::getScopes)
                        .flatMap(Set::stream)
                        .collect(Collectors.toSet());
        File mergedOutput = getDexOutputLocation(outputProvider, "nonExternalJars", allScopes);
        FileUtils.cleanOutputDir(mergedOutput);

        List<Path> toMerge =
                new ArrayList<>(
                        byStatus.get(Status.CHANGED).size()
                                + byStatus.get(Status.NOTCHANGED).size()
                                + byStatus.get(Status.ADDED).size());
        for (JarInput input :
                Iterables.concat(
                        byStatus.get(Status.CHANGED),
                        byStatus.get(Status.NOTCHANGED),
                        byStatus.get(Status.ADDED))) {
            toMerge.add(input.getFile().toPath());
        }

        if (!toMerge.isEmpty()) {
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
                            outputProvider, "directories", ImmutableSet.of(QualifiedContent.Scope.PROJECT));
            FileUtils.cleanOutputDir(dexOutput);

            List<Path> toMerge = new ArrayList<>(changed.size() + notChanged.size());
            for (DirectoryInput input : Iterables.concat(changed, notChanged)) {
                toMerge.add(input.getFile().toPath());
            }
            if (!toMerge.isEmpty()) {
                subTasks.add(submitForMerging(output, dexOutput, toMerge, null));
            }
        } else {
            for (DirectoryInput directoryInput : deleted) {
                File dexOutput =
                        getDexOutputLocation(
                                outputProvider,
                                directoryInput.getName(),
                                directoryInput.getScopes());
                FileUtils.cleanOutputDir(dexOutput);
            }
            for (DirectoryInput directoryInput : changed) {
                File dexOutput =
                        getDexOutputLocation(
                                outputProvider,
                                directoryInput.getName(),
                                directoryInput.getScopes());
                FileUtils.cleanOutputDir(dexOutput);
                subTasks.add(
                        submitForMerging(
                                output,
                                dexOutput,
                                ImmutableList.of(directoryInput.getFile().toPath()),
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
            Iterable<Path> externalLibsToMerge =
                    externalLibs
                            .stream()
                            .filter(i -> i.getStatus() != Status.REMOVED)
                            .map(input -> input.getFile().toPath())
                            .collect(Collectors.toList());
            if (!Iterables.isEmpty(externalLibsToMerge)) {
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
            @NonNull Iterable<Path> dexArchives,
            @Nullable Path mainDexList) {
        DexMergerTransformCallable callable =
                new DexMergerTransformCallable(
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
            @NonNull TransformOutputProvider outputProvider,
            @NonNull String name,
            @NonNull Set<? super QualifiedContent.Scope> scopes) {
        return outputProvider.getContentLocation(name, TransformManager.CONTENT_DEX, scopes, Format.DIRECTORY);
    }
}
