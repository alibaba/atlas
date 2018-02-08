package com.taobao.android.builder.tasks.transform.dex;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.transform.*;
import com.android.build.gradle.internal.BuildCacheUtils;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.internal.api.AppVariantOutputContext;
import com.android.build.gradle.internal.api.AwbTransform;
import com.android.build.gradle.internal.pipeline.ExtendedContentType;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.transforms.DexMergerTransform;
import com.android.build.gradle.internal.transforms.DexMergerTransformCallable;
import com.android.builder.core.ErrorReporter;
import com.android.builder.dexing.DexMergerTool;
import com.android.builder.dexing.DexingType;
import com.android.builder.utils.ExceptionRunnable;
import com.android.builder.utils.FileCache;
import com.android.ide.common.blame.Message;
import com.android.ide.common.blame.ParsingProcessOutputHandler;
import com.android.ide.common.blame.parser.DexParser;
import com.android.ide.common.blame.parser.ToolOutputParser;
import com.android.ide.common.process.ProcessException;
import com.android.ide.common.process.ProcessOutput;
import com.android.ide.common.process.ProcessOutputHandler;
import com.android.utils.FileUtils;
import com.android.utils.ILogger;
import com.google.common.collect.*;
import com.taobao.android.builder.AtlasBuildContext;
import com.taobao.android.builder.dependency.model.AwbBundle;
import com.taobao.android.builder.tools.MD5Util;
import javafx.util.Pair;
import org.gradle.api.file.FileCollection;

import java.nio.file.Files;
import java.nio.file.Path;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * @author lilong
 * @create 2017-12-08 下午5:26
 */

public class AtlasDexMergerTransform extends Transform{

    private AppVariantOutputContext appVariantOutputContext;
    private DexingType dexingType;
    private ErrorReporter errorReporter;
    private ILogger logger;
    private DexMergerTool dexMergerTool;
    private static final String id = "atlasbundledexmerge";
    private static final String CACHE_VERSION="1.0.1";

    private int minSDK;
    private boolean isDebuggable;

    private AtlasMainDexMerger atlasMainDexMerger;

    private File mainDexListFile;

    @NonNull private final ForkJoinPool forkJoinPool = ForkJoinPool.commonPool();

    public AtlasDexMergerTransform(AppVariantOutputContext appVariantOutputContext,
            @NonNull DexingType dexingType,
            @Nullable FileCollection mainDexListFile,
            @NonNull ErrorReporter errorReporter,
            @NonNull DexMergerTool dexMerger,
            int minSdkVersion,
            boolean isDebuggable
    ) {
        atlasMainDexMerger = new AtlasMainDexMerger(dexingType, mainDexListFile, errorReporter, dexMerger, minSdkVersion,isDebuggable,appVariantOutputContext);
        this.appVariantOutputContext = appVariantOutputContext;
        this.errorReporter = errorReporter;
        this.logger = LoggerWrapper.getLogger(AtlasDexMergerTransform.class);
        this.minSDK = minSdkVersion;
        this.isDebuggable = isDebuggable;
        this.dexMergerTool = dexMerger;
        this.dexingType = dexingType;
        this.mainDexListFile = mainDexListFile == null ? null:mainDexListFile.getSingleFile();



    }

    @Override
    public String getName() {
        return "atlasDexmerge";
    }

    @Override
    public Set<QualifiedContent.ContentType> getInputTypes() {
        return ImmutableSet.of(ExtendedContentType.DEX_ARCHIVE);
    }

    @Override
    public Set<QualifiedContent.ContentType> getOutputTypes() {
        return TransformManager.CONTENT_DEX;
    }

    @Override
    public Set<? super QualifiedContent.Scope> getScopes() {
        return TransformManager.SCOPE_FULL_WITH_IR_FOR_DEXING;
    }

    @Override
    public Collection<SecondaryFile> getSecondaryFiles() {
        if (mainDexListFile != null) {
            return ImmutableList.of(SecondaryFile.nonIncremental(mainDexListFile));
        } else {
            return ImmutableList.of();
        }
    }

    @Override
    public Map<String, Object> getParameterInputs() {
        Map<String, Object> params = new LinkedHashMap<>(2);
        params.put("dexing-type", dexingType.name());
        params.put("dex-merger-tool", dexMergerTool.name());
        return params;
    }

    @Override
    public boolean isIncremental() {
        return false;
    }

    @Override
    public boolean isCacheable() {
        return true;
    }

    @Override
    public void transform(TransformInvocation transformInvocation) throws TransformException, IOException, InterruptedException {

        super.transform(transformInvocation);
        TransformOutputProvider transformOutputProvider = transformInvocation.getOutputProvider();
        transformOutputProvider.deleteAll();
        atlasMainDexMerger.merge(transformInvocation);
        ProcessOutputHandler outputHandler =
                new ParsingProcessOutputHandler(
                        new ToolOutputParser(new DexParser(), Message.Kind.ERROR, logger),
                        new ToolOutputParser(new DexParser(), logger),
                        errorReporter);

        for (AwbTransform awbTransform:appVariantOutputContext.getAwbTransformMap().values()){
            AwbBundle awbBundle = awbTransform.getAwbBundle();
            File file = appVariantOutputContext.getVariantContext().getAwbDexAchiveOutput(awbBundle);
            List<File>awbDexFiles = new ArrayList<>();

            awbDexFiles.addAll(org.apache.commons.io.FileUtils.listFiles(file,new String[]{"jar","dex"},true));
            File []mergeDexs = file.listFiles(pathname -> pathname.getName().endsWith(".jar")||pathname.isDirectory());
            Collections.sort(awbDexFiles, (o1, o2) -> {
                if (o1.length() > o2.length()) {
                    return 1;
                }else if (o1.length() < o2.length()){
                    return -1;
                }else {
                    return 0;
                }
            });

            File outPutFolder = appVariantOutputContext.getAwbDexOutput(awbBundle.getName());
            File outDexFile = new File(outPutFolder,"classes.dex");
            FileUtils.cleanOutputDir(outPutFolder);
            FileCache.Inputs buildCacheInputs = getBuildCacheInputs(awbDexFiles,dexingType,dexMergerTool,null,minSDK,isDebuggable,awbBundle);
            ProcessOutput output = outputHandler.createOutput();
            FileCache fileCache = BuildCacheUtils.createBuildCacheIfEnabled(appVariantOutputContext.getVariantContext().getProject(),appVariantOutputContext.getScope().getGlobalScope().getProjectOptions());
            try {
                FileCache.QueryResult result = fileCache.createFileInCacheIfAbsent(
                        buildCacheInputs,
                        in -> {
                            List<ForkJoinTask<Void>> mergeTasks = new ArrayList<>();
                            mergeTasks.addAll(
                                    handleLegacyAndMonoDex(
                                            Arrays.asList(mergeDexs), output,outPutFolder));
                            mergeTasks.forEach(ForkJoinTask::join);
                            if (output != null) {
                                try {
                                    outputHandler.handleOutput(output);
                                } catch (ProcessException e) {
                                    // ignore this one
                                }
                            }
                            Files.copy(outDexFile.toPath(),in.toPath());
                        }

                        );
                if (result.getQueryEvent().equals(FileCache.QueryEvent.HIT)){
                    logger.info("hit dexmerge cache "+awbBundle.getName() +"->"+result.getCachedFile().getAbsolutePath());
                    if (result.getCachedFile().exists()) {
                        org.apache.commons.io.FileUtils.copyFile(result.getCachedFile(), outDexFile);
                    }
                }else {
                    logger.info("miss dexmerge cache "+awbBundle.getName() +"-> null");

                }
            }catch (Exception e){

            }
        }

    }


    @NonNull
    public List<ForkJoinTask<Void>> handleLegacyAndMonoDex(
            @NonNull Collection<File> inputs,
            @NonNull ProcessOutput output,File awbDexOutFolder)
            throws IOException {
        ImmutableList.Builder<Path> dexArchiveBuilder = ImmutableList.builder();
        for ( File file:inputs){
            dexArchiveBuilder.add(file.toPath());
        }
        ImmutableList<Path> dexesToMerge = dexArchiveBuilder.build();
        if (dexesToMerge.isEmpty()) {
            return ImmutableList.of();
        }

        return ImmutableList.of(submitForMerging(output, awbDexOutFolder, dexesToMerge, null));
    }

    private boolean valid(File file){
        if (file.isDirectory()){
            return true;
        }
        try {
            ZipFile zipFile = new ZipFile(file);
            Enumeration<? extends ZipEntry> entryEnumeration = zipFile.entries();
            while (entryEnumeration.hasMoreElements()) {
                ZipEntry zipEntry = entryEnumeration.nextElement();
                if (zipEntry.getName().endsWith(".dex")) {
                    return true;
                }
            }
        }catch (Exception e){
            logger.warning(file.getAbsolutePath()+ " is no a zipFile!");
            return false;
        }
        return false;
    }

    private ForkJoinTask<Void> submitForMerging(
            @NonNull ProcessOutput output,
            @NonNull File dexOutputDir,
            @NonNull Iterable<Path> dexArchives,
            @Nullable Path mainDexList) {
        DexMergerTransformCallable callable =
                new DexMergerTransformCallable(
                        DexingType.MONO_DEX,
                        output,
                        dexOutputDir,
                        dexArchives,
                        mainDexList,
                        new ForkJoinPool(),
                        dexMergerTool,
                        minSDK,
                        isDebuggable);
        return forkJoinPool.submit(callable);
    }

    private FileCache.Inputs getBuildCacheInputs(List<File> mainDexFiles, DexingType dexingType, DexMergerTool dexMerger, File file, int minSdkVersion, boolean isDebuggable,AwbBundle awbBundle) {
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
        inputsBuilder.putString("bundleName",awbBundle.getName());
        FileCache.Inputs inputs = inputsBuilder.build();
        return inputs;
    }
}
