package com.taobao.android.builder.tasks.manager.transform;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.api.AppVariantOutputContext;
import com.android.build.gradle.internal.pipeline.AtlasIntermediateStreamHelper;
import com.android.build.gradle.internal.pipeline.IntermediateFolderUtils;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.transforms.DexArchiveBuilderTransform;
import com.android.build.gradle.internal.transforms.DexArchiveBuilderTransformBuilder;
import com.android.builder.core.DexOptions;
import com.android.builder.dexing.DexerTool;
import com.android.builder.utils.FileCache;
import com.android.ide.common.blame.MessageReceiver;
import com.google.common.base.Preconditions;

import java.io.File;
import java.util.List;
import java.util.function.Supplier;

/**
 * @ClassName MtlDexArchiveBuilderTransformBuilder
 * @Description TODO
 * @Author zhayu.ll
 * @Date 2019-07-26 11:46
 * @Version 1.0
 */
public class MtlDexArchiveBuilderTransformBuilder  {
    private Supplier<List<File>> androidJarClasspath;
    private AppVariantOutputContext variantOutputContext;
    private DexOptions dexOptions;
    private MessageReceiver messageReceiver;
    private FileCache userLevelCache;
    private int minSdkVersion;
    private DexerTool dexer;
    private boolean useGradleWorkers;
    private Integer inBufferSize;
    private Integer outBufferSize;
    private boolean isDebuggable;
    private VariantScope.Java8LangSupport java8LangSupportType;
    private String projectVariant;
    private Integer numberOfBuckets;
    private boolean includeFeaturesInScopes;
    private boolean isInstantRun;
    private boolean enableDexingArtifactTransform;
    private AtlasIntermediateStreamHelper atlasIntermediateStreamHelper;

    @NonNull
    public MtlDexArchiveBuilderTransformBuilder setAndroidJarClasspath(
            @NonNull Supplier<List<File>> androidJarClasspath) {
        this.androidJarClasspath = androidJarClasspath;
        return this;
    }


    @NonNull
    public MtlDexArchiveBuilderTransformBuilder setAppVariantOutputContext(
            @NonNull AppVariantOutputContext variantOutputContext) {
        this.variantOutputContext = variantOutputContext;
        return this;
    }


    @NonNull
    public MtlDexArchiveBuilderTransformBuilder setIntermediateStreamHelper(
            @NonNull AtlasIntermediateStreamHelper atlasIntermediateStreamHelper) {
        this.atlasIntermediateStreamHelper = atlasIntermediateStreamHelper;
        return this;
    }

    @NonNull
    public MtlDexArchiveBuilderTransformBuilder setDexOptions(@NonNull DexOptions dexOptions) {
        this.dexOptions = dexOptions;
        return this;
    }

    @NonNull
    public MtlDexArchiveBuilderTransformBuilder setMessageReceiver(
            @NonNull MessageReceiver messageReceiver) {
        this.messageReceiver = messageReceiver;
        return this;
    }

    @NonNull
    public MtlDexArchiveBuilderTransformBuilder setUserLevelCache(@Nullable FileCache userLevelCache) {
        this.userLevelCache = userLevelCache;
        return this;
    }

    @NonNull
    public MtlDexArchiveBuilderTransformBuilder setMinSdkVersion(int minSdkVersion) {
        this.minSdkVersion = minSdkVersion;
        return this;
    }

    @NonNull
    public MtlDexArchiveBuilderTransformBuilder setDexer(@NonNull DexerTool dexer) {
        this.dexer = dexer;
        return this;
    }

    @NonNull
    public MtlDexArchiveBuilderTransformBuilder setUseGradleWorkers(boolean useGradleWorkers) {
        this.useGradleWorkers = useGradleWorkers;
        return this;
    }

    @NonNull
    public MtlDexArchiveBuilderTransformBuilder setInBufferSize(@Nullable Integer inBufferSize) {
        this.inBufferSize = inBufferSize;
        return this;
    }

    @NonNull
    public MtlDexArchiveBuilderTransformBuilder setOutBufferSize(@Nullable Integer outBufferSize) {
        this.outBufferSize = outBufferSize;
        return this;
    }

    @NonNull
    public MtlDexArchiveBuilderTransformBuilder setIsDebuggable(boolean isDebuggable) {
        this.isDebuggable = isDebuggable;
        return this;
    }

    @NonNull
    public MtlDexArchiveBuilderTransformBuilder setJava8LangSupportType(
            @NonNull VariantScope.Java8LangSupport java8LangSupportType) {
        this.java8LangSupportType = java8LangSupportType;
        return this;
    }

    @NonNull
    public MtlDexArchiveBuilderTransformBuilder setProjectVariant(@NonNull String projectVariant) {
        this.projectVariant = projectVariant;
        return this;
    }

    @NonNull
    public MtlDexArchiveBuilderTransformBuilder setNumberOfBuckets(@Nullable Integer numberOfBuckets) {
        this.numberOfBuckets = numberOfBuckets;
        return this;
    }

    @NonNull
    public MtlDexArchiveBuilderTransformBuilder setIncludeFeaturesInScope(
            boolean includeFeaturesInScopes) {
        this.includeFeaturesInScopes = includeFeaturesInScopes;
        return this;
    }

    @NonNull
    public MtlDexArchiveBuilderTransformBuilder setIsInstantRun(boolean isInstantRun) {
        this.isInstantRun = isInstantRun;
        return this;
    }

    @NonNull
    public MtlDexArchiveBuilderTransformBuilder setEnableDexingArtifactTransform(
            boolean enableDexingArtifactTransform) {
        this.enableDexingArtifactTransform = enableDexingArtifactTransform;
        return this;
    }

    public MtlDexArchiveBuilderTransform createDexArchiveBuilderTransform() {
        Preconditions.checkNotNull(androidJarClasspath);
        Preconditions.checkNotNull(dexOptions);
        Preconditions.checkNotNull(messageReceiver);
        Preconditions.checkNotNull(dexer);
        Preconditions.checkNotNull(java8LangSupportType);
        Preconditions.checkNotNull(projectVariant);
        return new MtlDexArchiveBuilderTransform(
                variantOutputContext,
                androidJarClasspath,
                dexOptions,
                messageReceiver,
                userLevelCache,
                minSdkVersion,
                dexer,
                useGradleWorkers,
                inBufferSize,
                outBufferSize,
                isDebuggable,
                java8LangSupportType,
                projectVariant,
                numberOfBuckets,
                includeFeaturesInScopes,
                isInstantRun,
                enableDexingArtifactTransform,
                atlasIntermediateStreamHelper);

    }
}
