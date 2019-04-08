package com.taobao.android.builder.tasks.transform.dex;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.transform.Format;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.TransformInvocation;
import com.android.build.api.transform.TransformOutputProvider;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.internal.api.AppVariantOutputContext;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.builder.core.ErrorReporter;
import com.android.builder.dexing.DexMergerTool;
import com.android.builder.dexing.DexingType;
import com.android.builder.utils.FileCache;
import com.android.ide.common.blame.Message;
import com.android.ide.common.blame.ParsingProcessOutputHandler;
import com.android.ide.common.blame.parser.DexParser;
import com.android.ide.common.blame.parser.ToolOutputParser;
import com.android.ide.common.process.ProcessOutput;
import com.android.ide.common.process.ProcessOutputHandler;
import com.android.tools.r8.AtlasD8;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import org.apache.commons.io.FileUtils;
import org.gradle.api.file.FileCollection;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;

/**
 * AtlasDexMerger
 *
 * @author zhayu.ll
 * @date 18/2/9
 */
public abstract class AtlasDexMerger {
    @NonNull
    protected final DexingType dexingType;


    protected LoggerWrapper logger;

    public CacheHandler cacheHandler;

    @Nullable
    protected final FileCollection mainDexListFile;

    @NonNull protected final DexMergerTool dexMerger;

    protected static final String CACHE_VERSION="1.0.1";

    protected final String CLASSES_DEX="classes.dex";

    protected final int minSdkVersion;
    protected final boolean isDebuggable;
    @NonNull protected final ErrorReporter errorReporter;

    public List<File> getAllDexsArchives() {
        return allDexsArchives;
    }

    protected List<File>allDexsArchives = new ArrayList<>();

    @NonNull private final ForkJoinPool forkJoinPool = ForkJoinPool.commonPool();

    protected AppVariantOutputContext variantOutputContext;

    protected ProcessOutputHandler outputHandler;


    public AtlasDexMerger(DexingType dexingType, FileCollection mainDexListFile, ErrorReporter errorReporter, DexMergerTool dexMerger, int minSdkVersion, boolean isDebuggable, AppVariantOutputContext appVariantOutputContext) {
        this.dexingType = dexingType;
        this.mainDexListFile = mainDexListFile;
        this.dexMerger = dexMerger;
        this.minSdkVersion = minSdkVersion;
        this.isDebuggable = isDebuggable;
        this.logger= LoggerWrapper.getLogger(getClass());
        Preconditions.checkState(
                (dexingType == DexingType.LEGACY_MULTIDEX) == (mainDexListFile != null),
                "Main dex list must only be set when in legacy multidex");
        this.errorReporter = errorReporter;
        this.variantOutputContext = appVariantOutputContext;
         outputHandler =
                new ParsingProcessOutputHandler(
                        new ToolOutputParser(new DexParser(), Message.Kind.ERROR, logger),
                        new ToolOutputParser(new DexParser(), logger),
                        errorReporter);

    }


    protected FileCache.Inputs getBuildCacheInputs(List<File> mainDexFiles, DexingType dexingType, DexMergerTool dexMerger, File file, int minSdkVersion, boolean isDebuggable, String bundleName,String id) {
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
        inputsBuilder.putString("bundleName",bundleName);
        if (AtlasD8.deepShrink) {
            inputsBuilder.putBoolean("deepShrink", AtlasD8.deepShrink);
        }

        FileCache.Inputs inputs = inputsBuilder.build();
        return inputs;
    }


    protected void sort(List<File>dexFiles){
        Collections.sort(dexFiles, (o1, o2) -> {
            if (o1.length() > o2.length()) {
                return 1;
            }else if (o1.length() < o2.length()){
                return -1;
            }else {
                return 0;
            }
        });
    }


    @NonNull
    private ForkJoinTask<Void> submitForMerging(
            @NonNull ProcessOutput output,
            @NonNull File dexOutputDir,
            @NonNull Iterable<Path> dexArchives,
            @Nullable Path mainDexList) {
        DexMergeTransformCallable callable =
                new DexMergeTransformCallable(
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
    protected List<ForkJoinTask<Void>> handleNativeMultiDex(
            @NonNull List<File> inputs,
            @NonNull ProcessOutput output,
            @NonNull File outPutDir,
            File mainDexList)
            throws IOException {
//        ImmutableList.Builder<ForkJoinTask<Void>> subTasks = ImmutableList.builder();
//
//        throw new IOException("instantRun in AtlasPlugin is deprecared!");

        ImmutableList.Builder<Path> dexArchiveBuilder = ImmutableList.builder();
        for ( File file:inputs){
            dexArchiveBuilder.add(file.toPath());
        }
        ImmutableList<Path> dexesToMerge = dexArchiveBuilder.build();
        if (dexesToMerge.isEmpty()) {
            return ImmutableList.of();
        }

        return ImmutableList.of(submitForMerging(output, outPutDir, dexesToMerge, mainDexList == null ? null:mainDexList.toPath()));

    }

    @NonNull
    public List<ForkJoinTask<Void>> handleLegacyAndMonoDex(
            @NonNull Collection<File> inputs,
            @NonNull ProcessOutput output,File awbDexOutFolder,File mainDexList)
            throws IOException {
        ImmutableList.Builder<Path> dexArchiveBuilder = ImmutableList.builder();
        for ( File file:inputs){
            dexArchiveBuilder.add(file.toPath());
        }
        ImmutableList<Path> dexesToMerge = dexArchiveBuilder.build();
        if (dexesToMerge.isEmpty()) {
            return ImmutableList.of();
        }

        return ImmutableList.of(submitForMerging(output, awbDexOutFolder, dexesToMerge, mainDexList== null? null:mainDexList.toPath()));
    }

    public abstract void merge(TransformInvocation transformInvocation);


    @NonNull
    protected File getDexOutputLocation(
            @NonNull TransformOutputProvider outputProvider,
            @NonNull String name,
            @NonNull Set<? super QualifiedContent.Scope> scopes) {
        return outputProvider.getContentLocation(name, TransformManager.CONTENT_DEX, scopes, Format.DIRECTORY);
    }

    public void mergeAll(TransformInvocation transformInvocation) throws IOException {
        ProcessOutput output = outputHandler.createOutput();
        for (File file:allDexsArchives){
            logger.warning("mergeAll File:"+file.getAbsolutePath());
        }
        TransformOutputProvider outputProvider = transformInvocation.getOutputProvider();
        File outputDir =
                getDexOutputLocation(outputProvider, "main", TransformManager.SCOPE_FULL_PROJECT);

        File finalDexDir = new File(outputDir.getParentFile(),"temp");
        finalDexDir.mkdirs();
        List<ForkJoinTask<Void>> mergeTasks = new ArrayList();
        if (dexingType == DexingType.NATIVE_MULTIDEX) {
            mergeTasks.addAll(
                    handleNativeMultiDex(
                            allDexsArchives,
                            output,
                            finalDexDir,
                             null));
        } else {
            mergeTasks.addAll(
                    handleLegacyAndMonoDex(
                            allDexsArchives, output, finalDexDir, mainDexListFile == null ? null : mainDexListFile.getSingleFile()));
        }

        mergeTasks.forEach(voidForkJoinTask -> voidForkJoinTask.join());

        FileUtils.deleteDirectory(outputDir);
        FileUtils.moveDirectory(finalDexDir,outputDir);

    }


    public static interface CacheHandler{

        void handleMissActionResult(File outputDir,File in) throws IOException;

        void handleQueryResult(FileCache.QueryResult queryResult,File outDir,String bundleName) throws IOException;

    }



}
