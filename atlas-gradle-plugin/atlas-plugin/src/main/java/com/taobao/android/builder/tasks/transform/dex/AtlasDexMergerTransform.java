package com.taobao.android.builder.tasks.transform.dex;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.transform.*;
import com.android.build.gradle.internal.BuildCacheUtils;
import com.android.build.gradle.internal.InternalScope;
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
import com.android.tools.r8.AtlasD8;
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

public class AtlasDexMergerTransform extends Transform {


    private AtlasDexMerger atlasMainDexMerger;

    private AtlasDexMerger awbDexMerger;

    private File mainDexListFile;
    private DexingType dexingType;
    private DexMergerTool dexMergerTool;
    private AppVariantOutputContext variantOutputContext;

    public AtlasDexMergerTransform(AppVariantOutputContext appVariantOutputContext,
                                   @NonNull DexingType dexingType,
                                   @Nullable FileCollection mainDexListFile,
                                   @NonNull ErrorReporter errorReporter,
                                   @NonNull DexMergerTool dexMerger,
                                   int minSdkVersion,
                                   boolean isDebuggable
    ) {
        this.variantOutputContext = appVariantOutputContext;
        atlasMainDexMerger = new AtlasMainDexMerger(dexingType, mainDexListFile, errorReporter, dexMerger, minSdkVersion, isDebuggable, appVariantOutputContext);
        awbDexMerger = new AwbDexsMerger(DexingType.MONO_DEX, null, errorReporter, dexMerger, minSdkVersion, isDebuggable, appVariantOutputContext);
        this.mainDexListFile = mainDexListFile == null ? null : mainDexListFile.getSingleFile();
        this.dexingType = dexingType;
        this.dexMergerTool = dexMerger;


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
        return false;
    }

    @Override
    public void transform(TransformInvocation transformInvocation) throws TransformException, IOException, InterruptedException {
        if (variantOutputContext.getVariantContext().getProject().hasProperty("light") && variantOutputContext.getVariantContext().getProject().hasProperty("deepShrink")) {
            AtlasD8.deepShrink = true;
        }
        super.transform(transformInvocation);
        TransformOutputProvider transformOutputProvider = transformInvocation.getOutputProvider();
        transformOutputProvider.deleteAll();
        atlasMainDexMerger.merge(transformInvocation);
        awbDexMerger.merge(transformInvocation);
        if (variantOutputContext.getVariantContext().getAtlasExtension().getTBuildConfig().getMergeBundlesDex()) {
            atlasMainDexMerger.getAllDexsArchives().addAll(awbDexMerger.getAllDexsArchives());
            atlasMainDexMerger.mergeAll(transformInvocation);
        }

    }

}
