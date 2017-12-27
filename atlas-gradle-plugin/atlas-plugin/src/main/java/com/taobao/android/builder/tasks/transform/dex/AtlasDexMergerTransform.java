package com.taobao.android.builder.tasks.transform.dex;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.transform.*;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.internal.api.AppVariantOutputContext;
import com.android.build.gradle.internal.pipeline.ExtendedContentType;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.transforms.DexMergerTransform;
import com.android.build.gradle.internal.transforms.DexMergerTransformCallable;
import com.android.builder.core.ErrorReporter;
import com.android.builder.dexing.DexMergerTool;
import com.android.builder.dexing.DexingType;
import com.android.ide.common.blame.Message;
import com.android.ide.common.blame.ParsingProcessOutputHandler;
import com.android.ide.common.blame.parser.DexParser;
import com.android.ide.common.blame.parser.ToolOutputParser;
import com.android.ide.common.process.ProcessOutput;
import com.android.ide.common.process.ProcessOutputHandler;
import com.android.utils.FileUtils;
import com.android.utils.ILogger;
import com.google.common.collect.*;
import com.taobao.android.builder.AtlasBuildContext;
import com.taobao.android.builder.dependency.model.AwbBundle;
import com.taobao.android.builder.tasks.transform.cache.CacheFactory;
import com.taobao.android.builder.tasks.transform.cache.DexCache;
import com.taobao.android.builder.tasks.transform.cache.DexMergeCache;
import javafx.util.Pair;
import org.gradle.api.file.FileCollection;
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
    private DexMergeCache dexCache;
    private static final String id = "atlasDexmerge";
    private static final String CACHE_VERSION="1.0";

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
        atlasMainDexMerger = new AtlasMainDexMerger(dexingType, mainDexListFile, errorReporter, dexMerger, minSdkVersion,isDebuggable);
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
        return true;
    }

    @Override
    public boolean isCacheable() {
        return true;
    }

    @Override
    public void transform(TransformInvocation transformInvocation) throws TransformException, IOException, InterruptedException {

        super.transform(transformInvocation);
        TransformOutputProvider transformOutputProvider = transformInvocation.getOutputProvider();
        atlasMainDexMerger.merge(transformOutputProvider);
        List<ForkJoinTask<Void>> mergeTasks = new ArrayList<>();
        dexCache = (DexMergeCache) CacheFactory.get(appVariantOutputContext.getVariantContext().getProject(),id,CACHE_VERSION,this,transformInvocation, DexMergeCache.class);
        ProcessOutputHandler outputHandler =
                new ParsingProcessOutputHandler(
                        new ToolOutputParser(new DexParser(), Message.Kind.ERROR, logger),
                        new ToolOutputParser(new DexParser(), logger),
                        errorReporter);

        List<Pair<List<Path>,File>>awbDexs = new ArrayList<>();
        if (AtlasBuildContext.status.equals(AtlasBuildContext.STATUS.EXTERNALLIBSMERGE) || AtlasBuildContext.status.equals(AtlasBuildContext.STATUS.DEXARCHIVE)){
            Map map = AtlasBuildContext.atlasMainDexHelper.getAwbDexFiles();
            Iterator iterator = map.entrySet().iterator();
            while (iterator.hasNext()){
                Map.Entry<AwbBundle,Multimap> entry =(Map.Entry<AwbBundle,Multimap> )iterator.next();
                Multimap<QualifiedContent,File> multimap = entry.getValue();
                AwbBundle awbBundle = entry.getKey();
                File outPutFolder = appVariantOutputContext.getAwbDexOutput(awbBundle.getName());
                List<File>inputs = new ArrayList<>();
                for (QualifiedContent qualifiedContent : multimap.asMap().keySet()){
                    inputs.addAll(appVariantOutputContext.getScope().getGlobalScope().getProject().files(multimap.get(qualifiedContent)).getFiles());
                }
                List<Path>dexesToMerges = new ArrayList<>();
                mergeTasks.addAll(handleLegacyAndMonoDex(inputs,outputHandler.createOutput(),outPutFolder,dexesToMerges));
                javafx.util.Pair pair = new Pair<List,File>(dexesToMerges,outPutFolder);
                awbDexs.add(pair);

            }
            mergeTasks.forEach(ForkJoinTask::join);
            for (Pair pair:awbDexs){
                dexCache.mergeCache((List<Path>) pair.getKey(),Lists.newArrayList(((File)pair.getValue()).listFiles()));
            }
            dexCache.saveContent();

        }

    }


    @NonNull
    public List<ForkJoinTask<Void>> handleLegacyAndMonoDex(
            @NonNull Collection<File> inputs,
            @NonNull ProcessOutput output,
            @NonNull File awbDexOutFolder,List<Path>dexesToMerges)
            throws IOException {
        ImmutableList.Builder<Path> dexArchiveBuilder = ImmutableList.builder();
        for ( File file:inputs){
                if (valid(file)) {
                    dexArchiveBuilder.add(file.toPath());
                }
        }
        ImmutableList<Path> dexesToMerge = dexArchiveBuilder.build();
        if (dexesToMerge.isEmpty()) {
            return ImmutableList.of();
        }

        dexesToMerges.addAll(dexesToMerge);

        if (dexCache.getCache(dexesToMerges).size() > 0){
            return ImmutableList.of();
        }else {
            FileUtils.cleanOutputDir(awbDexOutFolder);
        }
        return ImmutableList.of(submitForMerging(output, awbDexOutFolder, dexesToMerges, null));
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
}
