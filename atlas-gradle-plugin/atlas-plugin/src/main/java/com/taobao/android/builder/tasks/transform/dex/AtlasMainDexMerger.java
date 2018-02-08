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
import com.android.build.gradle.internal.transforms.DexMergerTransform;
import com.android.build.gradle.internal.transforms.DexMergerTransformCallable;
import com.android.build.gradle.internal.transforms.TransformInputUtil;
import com.android.builder.core.ErrorReporter;
import com.android.builder.dexing.DexMergerTool;
import com.android.builder.dexing.DexingType;
import com.android.builder.utils.FileCache;
import com.android.ide.common.blame.Message;
import com.android.ide.common.blame.ParsingProcessOutputHandler;
import com.android.ide.common.blame.parser.DexParser;
import com.android.ide.common.blame.parser.ToolOutputParser;
import com.android.ide.common.process.ProcessException;
import com.android.ide.common.process.ProcessOutput;
import com.android.ide.common.process.ProcessOutputHandler;
import com.android.utils.FileUtils;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.taobao.android.builder.AtlasBuildContext;
import com.taobao.android.builder.tools.MD5Util;
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


public class AtlasMainDexMerger {

    private static final LoggerWrapper logger = LoggerWrapper.getLogger(DexMergerTransform.class);
    @VisibleForTesting
    public static final int ANDROID_L_MAX_DEX_FILES = 100;
    @VisibleForTesting public static final int EXTERNAL_DEPS_DEX_FILES = 50;

    @NonNull private final DexingType dexingType;
    @Nullable private final FileCollection mainDexListFile;
    @NonNull private final DexMergerTool dexMerger;
    private static final String id = "atlasmaindexmerge";

    private static final String CACHE_VERSION="1.0.1";


    private final int minSdkVersion;
    private final boolean isDebuggable;
    @NonNull private final ErrorReporter errorReporter;
    @NonNull private final ForkJoinPool forkJoinPool = ForkJoinPool.commonPool();

    private AppVariantOutputContext variantOutputContext;

    public AtlasMainDexMerger(DexingType dexingType, FileCollection mainDexListFile, ErrorReporter errorReporter, DexMergerTool dexMerger, int minSdkVersion, boolean isDebuggable, AppVariantOutputContext appVariantOutputContext) {
        this.dexingType = dexingType;
        this.mainDexListFile = mainDexListFile;
        this.dexMerger = dexMerger;
        this.minSdkVersion = minSdkVersion;
        this.isDebuggable = isDebuggable;
        Preconditions.checkState(
                (dexingType == DexingType.LEGACY_MULTIDEX) == (mainDexListFile != null),
                "Main dex list must only be set when in legacy multidex");
        this.errorReporter = errorReporter;
        this.variantOutputContext = appVariantOutputContext;

    }

    public void merge(TransformInvocation transformInvocation) throws TransformException {

        if (dexMerger == DexMergerTool.D8) {
            logger.info("D8 is used to merge dex.");
        }

        TransformOutputProvider outputProvider = transformInvocation.getOutputProvider();
        Collection<TransformInput>transformInputs = transformInvocation.getInputs();


        List<File>mainDexFiles = new ArrayList<>();


        final File[][] mergeDexs = {new File[0]};


        File outputDir =
                getDexOutputLocation(outputProvider, "main", TransformManager.SCOPE_FULL_PROJECT);
        // this deletes and creates the dir for the output
        try {
            FileUtils.cleanOutputDir(outputDir);
        } catch (IOException e) {
            e.printStackTrace();
        }

        transformInputs.forEach((TransformInput transformInput) -> {
            File file = (File) ReflectUtils.getField(transformInput, "optionalRootLocation");
            if (file!= null && file.exists()) {
                mainDexFiles.addAll(org.apache.commons.io.FileUtils.listFiles(file, new String[]{"jar", "dex"}, true));
                mergeDexs[0] = file.listFiles(pathname -> pathname.getName().endsWith(".jar")||pathname.isDirectory());
            }
        });

        File outDexFile = new File(outputDir,"classes.dex");
        Collections.sort(mainDexFiles, (o1, o2) -> {
            if (o1.length() > o2.length()) {
                return 1;
            }else if (o1.length() < o2.length()){
                return -1;
            }else {
                return 0;
            }
        });
        FileCache.Inputs buildCacheInputs =
                getBuildCacheInputs(
                        mainDexFiles, dexingType, dexMerger, mainDexListFile == null? null:mainDexListFile.getSingleFile(),minSdkVersion, isDebuggable);

        ProcessOutputHandler outputHandler =
                new ParsingProcessOutputHandler(
                        new ToolOutputParser(new DexParser(), Message.Kind.ERROR, logger),
                        new ToolOutputParser(new DexParser(), logger),
                        errorReporter);
        ProcessOutput output = outputHandler.createOutput();
        FileCache fileCache = BuildCacheUtils.createBuildCacheIfEnabled(variantOutputContext.getVariantContext().getProject(),variantOutputContext.getScope().getGlobalScope().getProjectOptions());
        try {
            FileCache.QueryResult result = fileCache.createFileInCacheIfAbsent(
                    buildCacheInputs,
                    in -> {
                        List<ForkJoinTask<Void>> mergeTasks = new ArrayList<>();
                        if (dexingType == DexingType.NATIVE_MULTIDEX) {
                            mergeTasks =
                                    handleNativeMultiDex(
                                            Arrays.asList(mergeDexs[0]),
                                            output,
                                            outputDir,
                                            false);
                        } else {
                            mergeTasks =
                                    handleLegacyAndMonoDex(
                                            Arrays.asList(mergeDexs[0]), output,outputDir);
                        }

                        mergeTasks.forEach(ForkJoinTask::join);

                        File []dexs = outputDir.listFiles((dir, name) -> name.endsWith(".dex"));
                        if (dexs!= null && dexs.length > 1){
                            for (File file:dexs){
                                org.apache.commons.io.FileUtils.copyFileToDirectory(file,in.getParentFile());
                            }

                        }else if (dexs.length ==1){
                            Files.copy(outDexFile.toPath(), in.toPath());
                        }

                        if (output != null) {
                            try {
                                outputHandler.handleOutput(output);
                            } catch (ProcessException e) {
                                // ignore this one
                            }
                        }
                    });

            if (result.getQueryEvent().equals(FileCache.QueryEvent.HIT)){
                if (result.getCachedFile().exists()) {
                    logger.info("hit maindex dexmerge cache ->"+result.getCachedFile().getAbsolutePath());
                    org.apache.commons.io.FileUtils.copyFile(result.getCachedFile(), outDexFile);
                }else if (!result.getCachedFile().exists()){
                    File[]dexs = result.getCachedFile().getParentFile().listFiles((dir, name) -> name.endsWith(".dex"));
                    if (dexs!= null && dexs.length > 0){
                        for (File dex:dexs) {
                            logger.info("hit maindex dexmerge cache ->"+dex.getAbsolutePath());
                            org.apache.commons.io.FileUtils.copyFileToDirectory(dex, outputDir);
                        }

                    }
                }
            }else {
                logger.info("miss maindex dexmerge cache -> null");

            }
        }catch (Exception e){

        }


    }

    private FileCache.Inputs getBuildCacheInputs(List<File> mainDexFiles, DexingType dexingType, DexMergerTool dexMerger, File file, int minSdkVersion, boolean isDebuggable) {
        FileCache.Inputs.Builder inputsBuilder = new FileCache.Inputs.Builder(FileCache.Command.PREDEX_LIBRARY_TO_DEX_ARCHIVE);
        for (int i = 0; i < mainDexFiles.size();i++) {
            inputsBuilder.putFile(String.valueOf(i), mainDexFiles.get(i), FileCache.FileProperties.HASH);
        }
        inputsBuilder.putString("dexingType",dexingType.name()).putString("dexMerger",dexMerger.name());
        if (file!= null && file.exists()) {
            inputsBuilder.putFile("maindexlist", file, FileCache.FileProperties.HASH);
        }
        inputsBuilder.putLong("minSdkVersion",minSdkVersion).putBoolean("isDebuggable",isDebuggable);
        inputsBuilder.putString("type",id).putString("version",CACHE_VERSION);
        inputsBuilder.putString("bundleName","maindex");
        FileCache.Inputs inputs = inputsBuilder.build();
        return inputs;
    }

    /** For legacy and mono-dex we always merge all dex archives, non-incrementally. */
    @NonNull
    private List<ForkJoinTask<Void>> handleLegacyAndMonoDex(
            @NonNull List<File> allDexFiles,
            @NonNull ProcessOutput output,
            @NonNull File outputDir)
            throws IOException {
        ImmutableList.Builder<Path> dexArchiveBuilder = ImmutableList.builder();
            allDexFiles.stream()
                    .map(file -> file.toPath())
                    .forEach(dexArchiveBuilder::add);


        ImmutableList<Path> dexesToMerge = dexArchiveBuilder.build();
        if (dexesToMerge.isEmpty()) {
            return ImmutableList.of();
        }

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
            @NonNull List<File> inputs,
            @NonNull ProcessOutput output,
            @NonNull File outPutDir,
            boolean isIncremental)
            throws IOException {
        ImmutableList.Builder<ForkJoinTask<Void>> subTasks = ImmutableList.builder();

        throw new IOException("instantRun in AtlasPlugin is deprecared!");

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
