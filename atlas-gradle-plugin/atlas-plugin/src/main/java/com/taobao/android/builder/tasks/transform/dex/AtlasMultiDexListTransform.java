package com.taobao.android.builder.tasks.transform.dex;

import com.android.annotations.NonNull;
import com.android.build.api.transform.*;
import com.android.build.gradle.internal.dsl.DexOptions;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.pipeline.TransformTask;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.transforms.BaseProguardAction;
import com.android.build.gradle.internal.transforms.MultiDexTransform;
import com.android.build.gradle.internal.transforms.TransformInputUtil;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.taobao.android.builder.AtlasBuildContext;
import com.taobao.android.builder.tasks.app.BuildAtlasEnvTask;
import com.taobao.android.builder.tasks.app.BuildAtlasEnvTask;
import com.taobao.android.builder.tools.multidex.FastMultiDexer;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.LoggingManager;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author lilong
 * @create 2017-12-27 下午2:30
 */


public class AtlasMultiDexListTransform extends BaseProguardAction {

    private final File mainDexListFile;
    private final File manifestKeepListProguardFile;
    private final File userMainDexKeepProguard;
    private final File userMainDexKeepFile;
    private VariantScope variantScope;

    public AtlasMultiDexListTransform(VariantScope variantScope, DexOptions dexOptions) {
        super(variantScope);
        this.variantScope = variantScope;
        mainDexListFile = variantScope.getMainDexListFile();
        this.manifestKeepListProguardFile = variantScope.getManifestKeepListProguardFile();
        this.userMainDexKeepProguard = variantScope.getVariantConfiguration().getMultiDexKeepProguard();
        this.userMainDexKeepFile = variantScope.getVariantConfiguration().getMultiDexKeepFile();

    }

    @Override
    public String getName() {
        return "atlasmultidexlist";
    }

    @Override
    public Set<QualifiedContent.ContentType> getInputTypes() {
        return ImmutableSet.of(QualifiedContent.DefaultContentType.CLASSES);
    }

    @Override
    public Set<QualifiedContent.ContentType> getOutputTypes() {
        return ImmutableSet.of(QualifiedContent.DefaultContentType.CLASSES);
    }

    @Override
    public Set<QualifiedContent.Scope> getScopes() {
        return TransformManager.EMPTY_SCOPES;
    }

    @Override
    public Set<QualifiedContent.Scope> getReferencedScopes() {
        return TransformManager.SCOPE_FULL_PROJECT;
    }

    @Override
    public Collection<SecondaryFile> getSecondaryFiles() {
        return Stream.of(manifestKeepListProguardFile, userMainDexKeepFile, userMainDexKeepProguard)
                .filter(Objects::nonNull)
                .map(SecondaryFile::nonIncremental)
                .collect(Collectors.toList());
    }

    @Override
    public Collection<File> getSecondaryFileOutputs() {
        return ImmutableList.of(mainDexListFile);
    }

    @Override
    public boolean isIncremental() {
        return false;
    }

    public void transform(@NonNull TransformInvocation transformInvocation)
            throws TransformException, InterruptedException, IOException {
        if (mainDexListFile.exists() && !variantScope.getVariantData().getName().toLowerCase().endsWith("release")){
            return;
        }
        LoggingManager loggingManager = transformInvocation.getContext().getLogging();
        loggingManager.captureStandardOutput(LogLevel.INFO);
        loggingManager.captureStandardError(LogLevel.WARN);
        Collection<File> inputs =AtlasBuildContext.atlasMainDexHelperMap.get(variantScope.getFullVariantName()).getAllMainDexJars();
        inputs.addAll(AtlasBuildContext.atlasMainDexHelperMap.get(variantScope.getFullVariantName()).getInputDirs());
        if (AtlasBuildContext.androidBuilderMap.get(variantScope.getGlobalScope().getProject()) == null) {
            super.transform(transformInvocation);
        } else if (AtlasBuildContext.androidBuilderMap.get(variantScope.getGlobalScope().getProject()).multiDexer == null) {
            super.transform(transformInvocation);
        }
        FastMultiDexer fastMultiDexer = (FastMultiDexer) AtlasBuildContext.androidBuilderMap.get(variantScope.getGlobalScope().getProject()).multiDexer;

        Collection<File>files = fastMultiDexer.repackageJarList(inputs, mainDexListFile,variantScope.getVariantData().getName().toLowerCase().endsWith("release"));

        if (files!= null && files.size() > 0){
            AtlasBuildContext.atlasMainDexHelperMap.get(variantScope.getFullVariantName()).addAllMainDexJars(files);

        }
    }

}
