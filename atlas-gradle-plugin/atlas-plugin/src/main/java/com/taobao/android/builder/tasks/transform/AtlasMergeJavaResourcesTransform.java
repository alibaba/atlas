package com.taobao.android.builder.tasks.transform;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.transform.*;
import com.android.build.gradle.internal.api.AppVariantOutputContext;
import com.android.build.gradle.internal.api.AwbTransform;
import com.android.build.gradle.internal.dsl.PackagingOptions;
import com.android.build.gradle.internal.packaging.PackagingFileAction;
import com.android.build.gradle.internal.packaging.ParsedPackagingOptions;
import com.android.build.gradle.internal.pipeline.AtlasIncrementalFileMergeTransformUtils;
import com.android.build.gradle.internal.pipeline.ExtendedContentType;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.transforms.MergeJavaResourcesTransform;
import com.android.builder.files.FileCacheByPath;
import com.android.builder.merge.*;
import com.android.ide.common.internal.WaitableExecutor;
import com.android.utils.FileUtils;
import com.android.utils.ImmutableCollectors;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.taobao.android.builder.AtlasBuildContext;
import com.taobao.android.builder.dependency.model.AwbBundle;
import com.taobao.android.builder.tools.MD5Util;
import com.taobao.android.builder.tools.zip.BetterZip;
import com.taobao.android.builder.tools.zip.ZipUtils;
import org.gradle.internal.impldep.org.apache.tools.zip.ZipUtil;

import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipOutputStream;

import static com.google.common.base.Preconditions.checkNotNull;


/**
 * @author lilong
 * @create 2017-12-27 下午2:30
 */

public class AtlasMergeJavaResourcesTransform extends MergeJavaResourcesTransform {

    private static final Pattern JAR_ABI_PATTERN = Pattern.compile("lib/([^/]+)/[^/]+");
    private static final Pattern ABI_FILENAME_PATTERN = Pattern.compile(".*\\.so");

    @NonNull
    private final PackagingOptions packagingOptions;

    @NonNull
    private final String name;

    @NonNull
    private final Set<? super QualifiedContent.Scope> mergeScopes;
    @NonNull
    private final Set<QualifiedContent.ContentType> mergedType;


    private Set<String> paths = new HashSet<>();


    @NonNull
    private final File intermediateDir;

    private final Predicate<String> acceptedPathsPredicate;
    @NonNull
    private File cacheDir;


    private AppVariantOutputContext appVariantOutputContext;


    private File outputLocation;

    private WaitableExecutor waitableExecutor;

    public AtlasMergeJavaResourcesTransform(AppVariantOutputContext appVariantOutputContext, PackagingOptions packagingOptions, Set<? super QualifiedContent.Scope> mergeScopes, QualifiedContent.ContentType mergedType, String name, VariantScope variantScope) {
        super(packagingOptions, mergeScopes, mergedType, name, variantScope);
        this.packagingOptions = packagingOptions;
        this.name = name;
        this.mergeScopes = ImmutableSet.copyOf(mergeScopes);
        this.mergedType = ImmutableSet.of(mergedType);
        this.intermediateDir = variantScope.getIncrementalDir(
                variantScope.getFullVariantName() + "-" + name);
        waitableExecutor = WaitableExecutor.useGlobalSharedThreadPool();
        this.appVariantOutputContext = appVariantOutputContext;
        if (mergedType == QualifiedContent.DefaultContentType.RESOURCES) {
            acceptedPathsPredicate =
                    path -> !path.endsWith(SdkConstants.DOT_CLASS)
                            && !path.endsWith(SdkConstants.DOT_NATIVE_LIBS);
        } else if (mergedType == ExtendedContentType.NATIVE_LIBS) {
            acceptedPathsPredicate =
                    path -> {
                        Matcher m = JAR_ABI_PATTERN.matcher(path);

                        // if the ABI is accepted, check the 3rd segment
                        if (m.matches()) {
                            paths.add(path);
                            // remove the beginning of the path (lib/<abi>/)
                            String filename = path.substring(5 + m.group(1).length());
                            // and check the filename
                            return ABI_FILENAME_PATTERN.matcher(filename).matches() ||
                                    SdkConstants.FN_GDBSERVER.equals(filename) ||
                                    SdkConstants.FN_GDB_SETUP.equals(filename);
                        }

                        return false;

                    };
        } else {
            throw new UnsupportedOperationException(
                    "mergedType param must be RESOURCES or NATIVE_LIBS");
        }
    }

    private void processAtlasNativeSo(String path) {
        appVariantOutputContext.getVariantContext().getProject().getLogger().info("processAtlasNativeSo soFile path:" + path);
        Set<String> removedNativeSos = appVariantOutputContext.getVariantContext().getAtlasExtension().getTBuildConfig().getOutOfApkNativeSos();
        if (removedNativeSos.size() > 0) {
            if (removedNativeSos.contains(path)) {
                File soFile = outputLocation.toPath().resolve(path).toFile();
                appVariantOutputContext.getVariantContext().getProject().getLogger().info("remove soFile path:" + soFile.getAbsolutePath());
                String url = AtlasBuildContext.atlasApkProcessor.uploadNativeSo(appVariantOutputContext.getVariantContext().getProject(), soFile, appVariantOutputContext.getVariantContext().getBuildType());
                NativeInfo nativeInfo = new NativeInfo();
                nativeInfo.bundleName = "mainBundle";
                nativeInfo.md5 = MD5Util.getFileMD5(soFile);
                nativeInfo.url = url;
                nativeInfo.path = path;
                appVariantOutputContext.getSoMap().put(path, nativeInfo);
                try {
                    org.apache.commons.io.FileUtils.moveFileToDirectory(soFile, appVariantOutputContext.getRemoteNativeSoFolder(nativeInfo.bundleName), true);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public void transform(TransformInvocation invocation) throws IOException {

        waitableExecutor.execute(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                cacheDir = new File(intermediateDir, "zip-cache");
                FileUtils.mkdirs(cacheDir);
                FileCacheByPath zipCache = new FileCacheByPath(cacheDir);
                TransformOutputProvider outputProvider = invocation.getOutputProvider();
                checkNotNull(outputProvider, "Missing output object for transform " + getName());

                ParsedPackagingOptions packagingOptions = new ParsedPackagingOptions(AtlasMergeJavaResourcesTransform.this.packagingOptions);

                boolean full = false;
                IncrementalFileMergerState state = loadMergeState();
                if (state == null || !invocation.isIncremental()) {
                    /*
                     * This is a full build.
                     */
                    state = new IncrementalFileMergerState();
                    outputProvider.deleteAll();
                    full = true;
                }

                List<Runnable> cacheUpdates = new ArrayList<>();

                Map<IncrementalFileMergerInput, QualifiedContent> contentMap = new HashMap<>();
                List<IncrementalFileMergerInput> inputs =
                        new ArrayList<>(
                                AtlasIncrementalFileMergeTransformUtils.toInput(
                                        invocation,
                                        zipCache,
                                        cacheUpdates,
                                        full,
                                        contentMap, null,appVariantOutputContext.getVariantContext().getVariantName()));




                /*
                 * In an ideal world, we could just send the inputs to the file merger. However, in the
                 * real world we live in, things are more complicated :)
                 *
                 * We need to:
                 *
                 * 1. We need to bring inputs that refer to the project scope before the other inputs.
                 * 2. Prefix libraries that come from directories with "lib/".
                 * 3. Filter all inputs to remove anything not accepted by acceptedPathsPredicate neither
                 * by packagingOptions.
                 */

                // Sort inputs to move project scopes to the start.
                inputs.sort((i0, i1) -> {
                    int v0 = contentMap.get(i0).getScopes().contains(QualifiedContent.Scope.PROJECT) ? 0 : 1;
                    int v1 = contentMap.get(i1).getScopes().contains(QualifiedContent.Scope.PROJECT) ? 0 : 1;
                    return v0 - v1;
                });

                // Prefix libraries with "lib/" if we're doing libraries.
                assert mergedType.size() == 1;
                QualifiedContent.ContentType mergedType = AtlasMergeJavaResourcesTransform.this.mergedType.iterator().next();
                if (mergedType == ExtendedContentType.NATIVE_LIBS) {
                    inputs =
                            inputs.stream()
                                    .map(
                                            i -> {
                                                QualifiedContent qc = contentMap.get(i);
                                                if (qc.getFile().isDirectory()) {
                                                    i =
                                                            new RenameIncrementalFileMergerInput(
                                                                    i,
                                                                    s -> "lib/" + s,
                                                                    s -> s.substring("lib/".length()));
                                                    contentMap.put(i, qc);
                                                }

                                                return i;
                                            })
                                    .collect(Collectors.toList());
                }

                // Filter inputs.
                Predicate<String> inputFilter =
                        acceptedPathsPredicate.and(
                                path -> packagingOptions.getAction(path) != PackagingFileAction.EXCLUDE);
                inputs = inputs.stream()
                        .map(i -> {
                            IncrementalFileMergerInput i2 =
                                    new FilterIncrementalFileMergerInput(i, inputFilter);
                            contentMap.put(i2, contentMap.get(i));
                            return i2;
                        })
                        .collect(Collectors.toList());

                /*
                 * Create the algorithm used by the merge transform. This algorithm decides on which
                 * algorithm to delegate to depending on the packaging option of the path. By default it
                 * requires just one file (no merging).
                 */
                StreamMergeAlgorithm mergeTransformAlgorithm = StreamMergeAlgorithms.select(path -> {
                    PackagingFileAction packagingAction = packagingOptions.getAction(path);
                    switch (packagingAction) {
                        case EXCLUDE:
                            // Should have been excluded from the input.
                            throw new AssertionError();
                        case PICK_FIRST:
                            return StreamMergeAlgorithms.pickFirst();
                        case MERGE:
                            return StreamMergeAlgorithms.concat();
                        case NONE:
                            return StreamMergeAlgorithms.acceptOnlyOne();
                        default:
                            throw new AssertionError();
                    }
                });

                /*
                 * Create an output that uses the algorithm. This is not the final output because,
                 * unfortunately, we still have the complexity of the project scope overriding other scopes
                 * to solve.
                 *
                 * When resources inside a jar file are extracted to a directory, the results may not be
                 * expected on Windows if the file names end with "." (bug 65337573), or if there is an
                 * uppercase/lowercase conflict. To work around this issue, we copy these resources to a
                 * jar file.
                 */
                IncrementalFileMergerOutput baseOutput;
                if (mergedType == QualifiedContent.DefaultContentType.RESOURCES) {
                    outputLocation =
                            outputProvider.getContentLocation(
                                    "resources", getOutputTypes(), getScopes(), Format.JAR);
                    baseOutput =
                            IncrementalFileMergerOutputs.fromAlgorithmAndWriter(
                                    mergeTransformAlgorithm, MergeOutputWriters.toZip(outputLocation));
                    AtlasBuildContext.atlasMainDexHelperMap.get(appVariantOutputContext.getVariantContext().getVariantName()).addMainJavaRes(outputLocation);
                } else {
                    outputLocation =
                            outputProvider.getContentLocation(
                                    "resources", getOutputTypes(), getScopes(), Format.DIRECTORY);
                    baseOutput =
                            IncrementalFileMergerOutputs.fromAlgorithmAndWriter(
                                    mergeTransformAlgorithm,
                                    MergeOutputWriters.toDirectory(outputLocation));
                }

                /*
                 * We need a custom output to handle the case in which the same path appears in multiple
                 * inputs and the action is NONE, but only one input is actually PROJECT. In this specific
                 * case we will ignore all other inputs.
                 */

                Set<IncrementalFileMergerInput> projectInputs =
                        contentMap.keySet().stream()
                                .filter(i -> contentMap.get(i).getScopes().contains(QualifiedContent.Scope.PROJECT))
                                .collect(Collectors.toSet());

                IncrementalFileMergerOutput output = new DelegateIncrementalFileMergerOutput(baseOutput) {
                    @Override
                    public void create(
                            @NonNull String path,
                            @NonNull List<IncrementalFileMergerInput> inputs) {
                        super.create(path, filter(path, inputs));
                    }

                    @Override
                    public void update(
                            @NonNull String path,
                            @NonNull List<String> prevInputNames,
                            @NonNull List<IncrementalFileMergerInput> inputs) {
                        super.update(path, prevInputNames, filter(path, inputs));
                    }

                    @Override
                    public void remove(@NonNull String path) {
                        super.remove(path);
                    }

                    @NonNull
                    private ImmutableList<IncrementalFileMergerInput> filter(
                            @NonNull String path,
                            @NonNull List<IncrementalFileMergerInput> inputs) {
                        PackagingFileAction packagingAction = packagingOptions.getAction(path);
                        if (packagingAction == PackagingFileAction.NONE
                                && inputs.stream().anyMatch(projectInputs::contains)) {
                            inputs = inputs.stream()
                                    .filter(projectInputs::contains)
                                    .collect(ImmutableCollectors.toImmutableList());
                        }

                        return ImmutableList.copyOf(inputs);
                    }
                };

                state = IncrementalFileMerger.merge(ImmutableList.copyOf(inputs), output, state);
                saveMergeState(state);

                cacheUpdates.forEach(Runnable::run);
                return null;
            }
        });

        for (AwbTransform awbTransform : appVariantOutputContext.getAwbTransformMap().values()) {

            File awbCacheDir = new File(intermediateDir, "awb-zip-cache" + File.separator + awbTransform.getAwbBundle().getName());
            waitableExecutor.execute(new Callable<Void>() {
                @Override
                public Void call() throws Exception {
                    FileUtils.mkdirs(awbCacheDir);
                    FileCacheByPath zipCache = new FileCacheByPath(awbCacheDir);
                    ParsedPackagingOptions packagingOptions = new ParsedPackagingOptions(AtlasMergeJavaResourcesTransform.this.packagingOptions);
                    boolean full = false;
                    IncrementalFileMergerState state = loadAwbMergeState(awbTransform.getAwbBundle());
                    if (state == null || !invocation.isIncremental()) {
                        /*
                         * This is a full build.
                         */
                        state = new IncrementalFileMergerState();
                        if (appVariantOutputContext.getAwbJniFolder(awbTransform.getAwbBundle()).exists() && mergedType.contains(ExtendedContentType.NATIVE_LIBS)) {
                            FileUtils.deleteDirectoryContents(appVariantOutputContext.getAwbJniFolder(awbTransform.getAwbBundle()));
                        }
                        if (appVariantOutputContext.getAwbJavaResFolder(awbTransform.getAwbBundle()).exists() && mergedType.contains(QualifiedContent.DefaultContentType.RESOURCES)) {
                            FileUtils.deleteDirectoryContents(appVariantOutputContext.getAwbJavaResFolder(awbTransform.getAwbBundle()));
                        }

                        full = true;
                    }

                    List<Runnable> cacheUpdates = new ArrayList<>();

                    Map<IncrementalFileMergerInput, QualifiedContent> contentMap = new HashMap<>();
                    List<IncrementalFileMergerInput> inputs =
                            new ArrayList<>(
                                    AtlasIncrementalFileMergeTransformUtils.toInput(
                                            invocation,
                                            zipCache,
                                            cacheUpdates,
                                            full,
                                            contentMap, awbTransform,appVariantOutputContext.getVariantContext().getVariantName()));




                    /*
                     * In an ideal world, we could just send the inputs to the file merger. However, in the
                     * real world we live in, things are more complicated :)
                     *
                     * We need to:
                     *
                     * 1. We need to bring inputs that refer to the project scope before the other inputs.
                     * 2. Prefix libraries that come from directories with "lib/".
                     * 3. Filter all inputs to remove anything not accepted by acceptedPathsPredicate neither
                     * by packagingOptions.
                     */

                    // Sort inputs to move project scopes to the start.
                    inputs.sort((i0, i1) -> {
                        int v0 = contentMap.get(i0).getScopes().contains(QualifiedContent.Scope.PROJECT) ? 0 : 1;
                        int v1 = contentMap.get(i1).getScopes().contains(QualifiedContent.Scope.PROJECT) ? 0 : 1;
                        return v0 - v1;
                    });

                    // Prefix libraries with "lib/" if we're doing libraries.
                    assert mergedType.size() == 1;
                    QualifiedContent.ContentType mergedType = AtlasMergeJavaResourcesTransform.this.mergedType.iterator().next();
                    if (mergedType == ExtendedContentType.NATIVE_LIBS) {
                        inputs =
                                inputs.stream()
                                        .map(
                                                i -> {
                                                    QualifiedContent qc = contentMap.get(i);
                                                    if (qc.getFile().isDirectory()) {
                                                        i =
                                                                new RenameIncrementalFileMergerInput(
                                                                        i,
                                                                        s -> "lib/" + s,
                                                                        s -> s.substring("lib/".length()));
                                                        contentMap.put(i, qc);
                                                    }

                                                    return i;
                                                })
                                        .collect(Collectors.toList());
                    }

                    // Filter inputs.
                    Predicate<String> inputFilter =
                            acceptedPathsPredicate.and(
                                    path -> packagingOptions.getAction(path) != PackagingFileAction.EXCLUDE);
                    inputs = inputs.stream()
                            .map(i -> {
                                IncrementalFileMergerInput i2 =
                                        new FilterIncrementalFileMergerInput(i, inputFilter);
                                contentMap.put(i2, contentMap.get(i));
                                return i2;
                            })
                            .collect(Collectors.toList());

                    /*
                     * Create the algorithm used by the merge transform. This algorithm decides on which
                     * algorithm to delegate to depending on the packaging option of the path. By default it
                     * requires just one file (no merging).
                     */
                    StreamMergeAlgorithm mergeTransformAlgorithm = StreamMergeAlgorithms.select(path -> {
                        PackagingFileAction packagingAction = packagingOptions.getAction(path);
                        switch (packagingAction) {
                            case EXCLUDE:
                                // Should have been excluded from the input.
                                throw new AssertionError();
                            case PICK_FIRST:
                                return StreamMergeAlgorithms.pickFirst();
                            case MERGE:
                                return StreamMergeAlgorithms.concat();
                            case NONE:
                                return StreamMergeAlgorithms.acceptOnlyOne();
                            default:
                                throw new AssertionError();
                        }
                    });

                    /*
                     * Create an output that uses the algorithm. This is not the final output because,
                     * unfortunately, we still have the complexity of the project scope overriding other scopes
                     * to solve.
                     *
                     * When resources inside a jar file are extracted to a directory, the results may not be
                     * expected on Windows if the file names end with "." (bug 65337573), or if there is an
                     * uppercase/lowercase conflict. To work around this issue, we copy these resources to a
                     * jar file.
                     */
                    IncrementalFileMergerOutput baseOutput;
                    if (mergedType == QualifiedContent.DefaultContentType.RESOURCES) {
                        File outputLocation = new File(appVariantOutputContext.getAwbJavaResFolder(awbTransform.getAwbBundle()), "res.jar");
                        if (!appVariantOutputContext.getAwbJavaResFolder(awbTransform.getAwbBundle()).exists()) {
                            appVariantOutputContext.getAwbJavaResFolder(awbTransform.getAwbBundle()).mkdirs();
                        }
                        createEmptyZipFile(outputLocation);
                        baseOutput =
                                IncrementalFileMergerOutputs.fromAlgorithmAndWriter(
                                        mergeTransformAlgorithm, MergeOutputWriters.toZip(outputLocation));
                    } else {
                        File outputLocation = appVariantOutputContext.getAwbJniFolder(awbTransform.getAwbBundle());
                        baseOutput =
                                IncrementalFileMergerOutputs.fromAlgorithmAndWriter(
                                        mergeTransformAlgorithm,
                                        MergeOutputWriters.toDirectory(outputLocation));
                    }

                    /*
                     * We need a custom output to handle the case in which the same path appears in multiple
                     * inputs and the action is NONE, but only one input is actually PROJECT. In this specific
                     * case we will ignore all other inputs.
                     */

                    Set<IncrementalFileMergerInput> projectInputs =
                            contentMap.keySet().stream()
                                    .filter(i -> contentMap.get(i).getScopes().contains(QualifiedContent.Scope.PROJECT))
                                    .collect(Collectors.toSet());

                    IncrementalFileMergerOutput output = new DelegateIncrementalFileMergerOutput(baseOutput) {
                        @Override
                        public void create(
                                @NonNull String path,
                                @NonNull List<IncrementalFileMergerInput> inputs) {
                            super.create(path, filter(path, inputs));
                        }

                        @Override
                        public void update(
                                @NonNull String path,
                                @NonNull List<String> prevInputNames,
                                @NonNull List<IncrementalFileMergerInput> inputs) {
                            super.update(path, prevInputNames, filter(path, inputs));
                        }

                        @Override
                        public void remove(@NonNull String path) {
                            super.remove(path);
                        }

                        @NonNull
                        private ImmutableList<IncrementalFileMergerInput> filter(
                                @NonNull String path,
                                @NonNull List<IncrementalFileMergerInput> inputs) {
                            PackagingFileAction packagingAction = packagingOptions.getAction(path);
                            if (packagingAction == PackagingFileAction.NONE
                                    && inputs.stream().anyMatch(projectInputs::contains)) {
                                inputs = inputs.stream()
                                        .filter(projectInputs::contains)
                                        .collect(ImmutableCollectors.toImmutableList());
                            }

                            return ImmutableList.copyOf(inputs);
                        }
                    };

                    state = IncrementalFileMerger.merge(ImmutableList.copyOf(inputs), output, state);
                    saveAwbMergeState(state, awbTransform.getAwbBundle());

                    cacheUpdates.forEach(Runnable::run);

                    return null;
                }
            });


        }


        try {
            waitableExecutor.waitForTasksWithQuickFail(false);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        appVariantOutputContext.getAwbTransformMap().values().stream().forEach(awbTransform -> {
            if (awbTransform.getAwbBundle().isMBundle) {
                if (mergedType.contains(ExtendedContentType.NATIVE_LIBS)) {
                    File bundleOutputLocation = appVariantOutputContext.getAwbJniFolder(awbTransform.getAwbBundle());
                    if (bundleOutputLocation.exists()) {
                        try {

                            org.apache.commons.io.FileUtils.copyDirectory(bundleOutputLocation, outputLocation);
                            org.apache.commons.io.FileUtils.deleteDirectory(bundleOutputLocation);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }

                } else {
                    File bundleOutputLocation = new File(appVariantOutputContext.getAwbJavaResFolder(awbTransform.getAwbBundle()), "res.jar");
                    File tempDir = new File(outputLocation.getParentFile(), "unzip");
                    try {
                        if (bundleOutputLocation.exists() && ZipUtils.isZipFile(bundleOutputLocation)) {
                            BetterZip.unzipDirectory(bundleOutputLocation, tempDir);
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        });

        if (!mergedType.contains(ExtendedContentType.NATIVE_LIBS)) {
            File tempDir = new File(outputLocation.getParentFile(), "unzip");
            if (outputLocation != null && outputLocation.exists() && ZipUtils.isZipFile(outputLocation)) {
                BetterZip.unzipDirectory(outputLocation, tempDir);
            }
            if (tempDir.exists() && tempDir.listFiles() != null) {
                FileUtils.deleteIfExists(outputLocation);
                BetterZip.zipDirectory(tempDir, outputLocation);
            }

        }


        paths.parallelStream().forEach(s ->  processAtlasNativeSo(s));


    }



    private void createEmptyZipFile(File outputLocation) throws IOException {
        ZipOutputStream zipOutputStream = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(outputLocation)));
        zipOutputStream.close();

    }

    private IncrementalFileMergerState loadAwbMergeState(AwbBundle awbBundle) throws IOException {
        File incrementalFile = incrementalAwbStateFile(awbBundle);
        if (!incrementalFile.isFile()) {
            return null;
        }

        try (ObjectInputStream i = new ObjectInputStream(new FileInputStream(incrementalFile))) {
            return (IncrementalFileMergerState) i.readObject();
        } catch (ClassNotFoundException e) {
            throw new IOException(e);
        }

    }

    @Nullable
    private IncrementalFileMergerState loadMergeState() throws IOException {
        File incrementalFile = incrementalStateFile();
        if (!incrementalFile.isFile()) {
            return null;
        }

        try (ObjectInputStream i = new ObjectInputStream(new FileInputStream(incrementalFile))) {
            return (IncrementalFileMergerState) i.readObject();
        } catch (ClassNotFoundException e) {
            throw new IOException(e);
        }
    }

    /**
     * Save the incremental merge state.
     *
     * @param state the state
     * @throws IOException failed to save the state
     */
    private void saveMergeState(@NonNull IncrementalFileMergerState state) throws IOException {
        File incrementalFile = incrementalStateFile();

        FileUtils.mkdirs(incrementalFile.getParentFile());
        try (ObjectOutputStream o = new ObjectOutputStream(new FileOutputStream(incrementalFile))) {
            o.writeObject(state);
        }
    }


    private void saveAwbMergeState(@NonNull IncrementalFileMergerState state, AwbBundle awbBundle) throws IOException {
        File incrementalFile = incrementalAwbStateFile(awbBundle);

        FileUtils.mkdirs(incrementalFile.getParentFile());
        try (ObjectOutputStream o = new ObjectOutputStream(new FileOutputStream(incrementalFile))) {
            o.writeObject(state);
        }
    }

    @NonNull
    private File incrementalStateFile() {
        return new File(intermediateDir, "merge-state");
    }

    @NonNull
    private File incrementalAwbStateFile(AwbBundle awbBundle) {
        return new File(intermediateDir, "awb-merge-state" + File.pathSeparator + awbBundle.getName());
    }


    public class NativeInfo {
        public String path;
        public String url;
        public String md5;
        public String bundleName;

    }


}
