package com.taobao.android.builder.insant;

import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInvocation;
import com.android.build.gradle.internal.aapt.AaptGeneration;
import com.android.build.gradle.internal.dsl.CoreSigningConfig;
import com.android.build.gradle.internal.incremental.InstantRunBuildContext;
import com.android.build.gradle.internal.scope.PackagingScope;
import com.android.build.gradle.internal.transforms.InstantRunDependenciesApkBuilder;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.internal.aapt.AaptOptions;
import com.android.builder.utils.FileCache;
import com.google.common.collect.Sets;
import com.taobao.android.builder.tasks.manager.transform.TransformManager;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;

import java.io.File;
import java.io.IOException;
import java.util.Set;

/**
 * TaobaoInstantRunDependenciesApkBuilder
 *
 * @author zhayu.ll
 * @date 18/10/12
 */
public class TaobaoInstantRunDependenciesApkBuilder extends InstantRunDependenciesApkBuilder {

    public TaobaoInstantRunDependenciesApkBuilder(Logger logger, Project project, InstantRunBuildContext buildContext, AndroidBuilder androidBuilder, FileCache fileCache, PackagingScope packagingScope, CoreSigningConfig signingConf, AaptGeneration aaptGeneration, AaptOptions aaptOptions, File outputDirectory, File supportDirectory, File aaptIntermediateDirectory) {
        super(logger, project, buildContext, androidBuilder, fileCache, packagingScope, signingConf, aaptGeneration, aaptOptions, outputDirectory, supportDirectory, aaptIntermediateDirectory);
    }

    @Override
    public Set<QualifiedContent.Scope> getScopes() {
        return com.android.build.gradle.internal.pipeline.TransformManager.SCOPE_FULL_PROJECT;
    }

    @Override
    public void transform(TransformInvocation transformInvocation) throws TransformException, InterruptedException, IOException {
        super.transform(transformInvocation);
    }
}
