package com.taobao.android.builder.tasks.transform;

import com.android.build.api.transform.TransformInvocation;
import com.android.build.gradle.api.BaseVariantOutput;
import com.android.build.gradle.internal.ApkDataUtils;
import com.android.build.gradle.internal.api.AppVariantContext;
import com.android.build.gradle.internal.api.AppVariantOutputContext;
import com.android.build.gradle.internal.api.AwbTransform;
import com.android.build.gradle.internal.transforms.TransformInputUtil;

import com.google.common.collect.ImmutableSet;
import com.taobao.android.builder.AtlasBuildContext;
import com.taobao.android.builder.dependency.AtlasDependencyTree;

import java.io.File;
import java.util.Collection;

public final class AtlasTransformUtils {
    private AtlasTransformUtils() {
    }

    public static Collection<File> getTransformInputs(AppVariantContext appVariantContext,
            TransformInvocation invocation) {
        ImmutableSet.Builder<File> builder = ImmutableSet.builder();
        Collection<File> transformInputs = TransformInputUtil.getAllFiles(invocation.getInputs());
        builder.addAll(transformInputs);
        AtlasDependencyTree atlasDependencyTree = AtlasBuildContext.androidDependencyTrees.get(
                appVariantContext.getVariantConfiguration().getFullName());

        if (atlasDependencyTree.getAwbBundles().size() > 0) {

            BaseVariantOutput vod =
                    (BaseVariantOutput) appVariantContext.getVariantOutputData().iterator().next();
            AppVariantOutputContext appVariantOutputContext =
                    appVariantContext.getAppVariantOutputContext(ApkDataUtils.get(vod));
            Collection<AwbTransform> awbTransforms =
                    appVariantOutputContext.getAwbTransformMap().values();
            awbTransforms.forEach(awbTransform -> {
                builder.addAll(awbTransform.getInputLibraries());
            });
        }
        return builder.build();
    }
}
