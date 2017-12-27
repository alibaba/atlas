package com.taobao.android.builder.tasks.transform.dex;

import com.android.build.api.transform.*;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.internal.api.AppVariantOutputContext;
import com.android.build.gradle.internal.pipeline.ExtendedContentType;
import com.android.build.gradle.internal.transforms.DexMergerTransformCallable;
import com.android.builder.core.ErrorReporter;
import com.android.builder.dexing.DexMergerTool;
import com.android.builder.dexing.DexingType;
import com.android.ide.common.blame.Message;
import com.android.ide.common.blame.ParsingProcessOutputHandler;
import com.android.ide.common.blame.parser.DexParser;
import com.android.ide.common.blame.parser.ToolOutputParser;
import com.android.ide.common.process.ProcessOutputHandler;
import com.android.utils.ILogger;
import com.google.common.collect.*;
import com.taobao.android.builder.AtlasBuildContext;
import com.taobao.android.builder.dependency.model.AwbBundle;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.nio.file.Path;

/**
 * @author lilong
 * @create 2017-12-08 下午2:30
 */

public class AtlasExternalLibsMergerTransform extends Transform {

    private DexingType dexingType;
    private DexMergerTool dexMergerTool;
    private int minisdk;
    private boolean isDebuggable;
    private DexMergerTransformCallable.Factory factory;
    private AppVariantOutputContext variantContext;
    private ForkJoinPool forkJoinPool = ForkJoinPool.commonPool();
    private ErrorReporter errorReporter;
    private ILogger logger = LoggerWrapper.getLogger(AtlasExternalLibsMergerTransform.class);

    public AtlasExternalLibsMergerTransform(AppVariantOutputContext appVariantOutputContext, DexingType dexingType, com.android.builder.dexing.DexMergerTool dexMergerTool, int minSdkVersion, boolean isDebuggable, com.android.builder.core.ErrorReporter errorReporter, com.android.build.gradle.internal.transforms.DexMergerTransformCallable.Factory callableFactory) {

        this.minisdk = minSdkVersion;
        this.factory = callableFactory;
        this.dexingType = dexingType;
        this.variantContext = appVariantOutputContext;
        this.isDebuggable = isDebuggable;
        this.dexMergerTool= dexMergerTool;
        this.errorReporter = errorReporter;
    }

    @Override
    public String getName() {
        return "atlasExternalLibsDexMerger";
    }

    @Override
    public Set<QualifiedContent.ContentType> getInputTypes() {
        return ImmutableSet.of(ExtendedContentType.DEX_ARCHIVE);
    }

    @Override
    public Set<? super QualifiedContent.Scope> getScopes() {
        return ImmutableSet.of(QualifiedContent.Scope.EXTERNAL_LIBRARIES);
    }

    @Override
    public boolean isIncremental() {
        return true;
    }

    public void transform(TransformInvocation transformInvocation) {
        Map<AwbBundle, Multimap<QualifiedContent, File>> cacheItems = new HashMap<>();
        Collection<JarInput> jarInputs = new ArrayList<>();
        ProcessOutputHandler outputHandler =
                new ParsingProcessOutputHandler(
                        new ToolOutputParser(new DexParser(), Message.Kind.ERROR, logger),
                        new ToolOutputParser(new DexParser(), logger),
                        errorReporter);
        for (TransformInput transformInput : transformInvocation.getInputs()) {
            jarInputs.addAll(transformInput.getJarInputs());
        }
        for (Map.Entry entry : AtlasBuildContext.atlasMainDexHelper.getAwbDexFiles().entrySet()) {
            Multimap multimap = HashMultimap.create();
            List<File> externalDexs = new ArrayList<>();
            Multimap<QualifiedContent, File> dexMultimap = (Multimap<QualifiedContent, File>) entry.getValue();
            for (QualifiedContent qualifiedContent : dexMultimap.keySet()) {
                if (!qualifiedContent.getFile().isDirectory() && qualifiedContent.getScopes().contains(QualifiedContent.Scope.EXTERNAL_LIBRARIES) && AtlasBuildContext.status.equals(AtlasBuildContext.STATUS.DEXARCHIVE)) {
                    externalDexs.addAll(dexMultimap.get(qualifiedContent));
                } else {
                    multimap.put(qualifiedContent, dexMultimap.get(qualifiedContent));
                }
                File outPutDir = variantContext.getAwbExternalLibsMergeFolder((AwbBundle) entry.getKey());
                if (outPutDir.exists()){
                    try {
                        FileUtils.cleanDirectory(outPutDir);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }else {
                    outPutDir.mkdirs();
                }
                if (externalDexs.size() > 0) {
                    DexMergerTransformCallable dexMergerTransformCallable = new DexMergerTransformCallable(dexingType, outputHandler.createOutput(), outPutDir, toPaths(externalDexs), null, new ForkJoinPool(), dexMergerTool, minisdk, isDebuggable);
                    try {
                        dexMergerTransformCallable.call();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                if (outPutDir.listFiles() != null && outPutDir.listFiles().length > 0) {
                    List<File> dexs = Arrays.asList(outPutDir.listFiles());
                    multimap.put(qualifiedContent, dexs);
                } else {
                    multimap.put(qualifiedContent, dexMultimap.get(qualifiedContent));
                }
            }
            cacheItems.put((AwbBundle) entry.getKey(),multimap);
        }

        AtlasBuildContext.status = AtlasBuildContext.STATUS.EXTERNALLIBSMERGE;
        AtlasBuildContext.atlasMainDexHelper.getAwbDexFiles().clear();
        AtlasBuildContext.atlasMainDexHelper.addAwbDexFiles(cacheItems);
        boolean changed = false;
        for (JarInput jarInput : jarInputs) {
            if (!jarInput.getStatus().equals(Status.NOTCHANGED)) {
                changed = true;
                break;
            }
        }
        if (transformInvocation.isIncremental() && !changed) {
            return;
        }
        List<JarInput> jarInputsFilter = jarInputs.stream().filter(jarInput -> jarInput.getStatus() != Status.REMOVED).collect(Collectors.toList());

        File outputDir = transformInvocation.getOutputProvider().getContentLocation("main",
                getOutputTypes(),
                getScopes(),
                Format.DIRECTORY);
        if (outputDir.exists()) {
            try {
                FileUtils.cleanDirectory(outputDir);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }else {
            outputDir.mkdirs();
        }

        // if all jars were removed, nothing to do.
        if (jarInputsFilter.isEmpty()) {
            return;
        }

        List<Path> paths = new ArrayList<>();
        for (JarInput jarInput : jarInputsFilter) {
            paths.add(jarInput.getFile().toPath());
        }


        Callable callable = factory.create(dexingType, outputHandler.createOutput(), outputDir, paths, null, forkJoinPool, dexMergerTool, minisdk, isDebuggable);
        try {
            callable.call();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public List<Path> toPaths(List<File> list) {
        List<Path> pathList = new ArrayList<Path>();
        for (File q : list) {
            pathList.add(q.toPath());
        }
        return pathList;
    }

}
