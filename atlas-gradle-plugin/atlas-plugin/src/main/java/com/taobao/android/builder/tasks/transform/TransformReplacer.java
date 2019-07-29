package com.taobao.android.builder.tasks.transform;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.gradle.api.BaseVariantOutput;
import com.android.build.gradle.internal.ApkDataUtils;
import com.android.build.gradle.internal.api.AppVariantContext;
import com.android.build.gradle.internal.dsl.PackagingOptions;
import com.android.build.gradle.internal.pipeline.OriginalStream;
import com.android.build.gradle.internal.pipeline.TransformManagerDelegate;
import com.android.build.gradle.internal.pipeline.TransformTask;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.InstantRunSplitApkResourcesBuilder;
import com.android.build.gradle.internal.transforms.*;
import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.options.IntegerOption;
import com.android.build.gradle.options.ProjectOptions;
import com.android.build.gradle.tasks.ir.FastDeployRuntimeExtractorTask;
import com.android.build.gradle.tasks.ir.GenerateInstantRunAppInfoTask;
import com.android.builder.core.DexOptions;
import com.android.builder.dexing.DexingType;
import com.android.builder.utils.FileCache;
import com.android.ide.common.internal.WaitableExecutor;
import com.google.common.collect.ImmutableSet;
import com.taobao.android.builder.AtlasBuildContext;
import com.taobao.android.builder.insant.*;
import com.taobao.android.builder.tasks.app.BuildAtlasEnvTask;
import com.taobao.android.builder.tasks.manager.transform.MtlDexArchiveBuilderTransform;
import com.taobao.android.builder.tasks.manager.transform.MtlDexArchiveBuilderTransformBuilder;
import com.taobao.android.builder.tasks.manager.transform.MtlDexMergeTransform;
import com.taobao.android.builder.tools.ReflectUtils;
import org.gradle.api.Action;
import org.gradle.api.Task;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * @author lilong
 * @create 2017-12-08 上午9:02
 */

public class TransformReplacer {

    private AppVariantContext variantContext;

    public TransformReplacer(AppVariantContext variantContext) {
        this.variantContext = variantContext;
    }

    public void replaceDexArchiveBuilderTransform(BaseVariantOutput vod) {
        List<TransformTask> list = TransformManagerDelegate.findTransformTaskByTransformType(variantContext,
                DexArchiveBuilderTransform.class);
        if (list != null && list.size() > 0) {
            list.forEach(transformTask -> {
                ProjectOptions projectOptions = new ProjectOptions(variantContext.getProject());
                DexOptions dexOptions = variantContext.getAppExtension().getDexOptions();
                boolean minified = variantContext.getScope().getCodeShrinker() != null;
                FileCache userLevelCache = getUserDexCache(minified, dexOptions.getPreDexLibraries());
                MtlDexArchiveBuilderTransform preDexTransform =
                        new MtlDexArchiveBuilderTransformBuilder()
                                .setAndroidJarClasspath(
                                        () ->
                                                variantContext.getScope()
                                                        .getGlobalScope()
                                                        .getAndroidBuilder()
                                                        .getBootClasspath(false))
                                .setDexOptions(dexOptions)
                                .setMessageReceiver(variantContext.getScope().getGlobalScope().getMessageReceiver())
                                .setUserLevelCache(userLevelCache)
                                .setMinSdkVersion(variantContext.getScope().getMinSdkVersion().getFeatureLevel())
                                .setDexer(variantContext.getScope().getDexer())
                                .setUseGradleWorkers(
                                        projectOptions.get(BooleanOption.ENABLE_GRADLE_WORKERS))
                                .setInBufferSize(projectOptions.get(IntegerOption.DEXING_READ_BUFFER_SIZE))
                                .setOutBufferSize(
                                        projectOptions.get(IntegerOption.DEXING_WRITE_BUFFER_SIZE))
                                .setIsDebuggable(
                                        variantContext.getScope()
                                                .getVariantConfiguration()
                                                .getBuildType()
                                                .isDebuggable())
                                .setJava8LangSupportType(variantContext.getScope().getJava8LangSupportType())
                                .setProjectVariant(getProjectVariantId(variantContext.getScope()))
                                .setNumberOfBuckets(
                                        projectOptions.get(IntegerOption.DEXING_NUMBER_OF_BUCKETS))
                                .setIncludeFeaturesInScope(variantContext.getScope().consumesFeatureJars())
                                .setIsInstantRun(
                                        variantContext.getScope().getInstantRunBuildContext().isInInstantRunMode())
                                .setEnableDexingArtifactTransform(false)
                                .createDexArchiveBuilderTransform();

                ReflectUtils.updateField(transformTask, "transform", preDexTransform);
            });
        }

    }

    public void replaceDexMergeTransform(BaseVariantOutput vod) {
        List<TransformTask> list = TransformManagerDelegate.findTransformTaskByTransformType(variantContext,
                DexMergerTransform.class);
        list.forEach(new Consumer<TransformTask>() {
            @Override
            public void accept(TransformTask transformTask) {
                DexingType dexingType = variantContext.getScope().getDexingType();
                boolean isDebuggable = variantContext.getScope().getVariantConfiguration().getBuildType().isDebuggable();
                MtlDexMergeTransform dexTransform =
                        new MtlDexMergeTransform(
                                dexingType,
                                dexingType == DexingType.LEGACY_MULTIDEX
                                        ? variantContext.getScope()
                                        .getArtifacts()
                                        .getFinalArtifactFiles(
                                                InternalArtifactType.LEGACY_MULTIDEX_MAIN_DEX_LIST)
                                        : null,
                                variantContext.getScope()
                                        .getArtifacts()
                                        .getFinalArtifactFiles(
                                                InternalArtifactType.DUPLICATE_CLASSES_CHECK),
                                variantContext.getScope().getGlobalScope().getMessageReceiver(),
                                variantContext.getScope().getDexMerger(),
                                variantContext.getScope().getMinSdkVersion().getFeatureLevel(),
                                isDebuggable,
                                variantContext.getScope().consumesFeatureJars(),
                                variantContext.getScope().getInstantRunBuildContext().isInInstantRunMode());
                ReflectUtils.updateField(transformTask, "transform", dexTransform);


            }
        });


    }




        private static String getProjectVariantId(@NonNull VariantScope variantScope) {
        return variantScope.getGlobalScope().getProject().getName()
                + ":"
                + variantScope.getFullVariantName();
    }


    @Nullable
    private FileCache getUserDexCache(boolean isMinifiedEnabled, boolean preDexLibraries) {
//        if (!preDexLibraries || isMinifiedEnabled) {
//            return null;
//        }
        return getUserIntermediatesCache();
    }

    @Nullable
    private FileCache getUserIntermediatesCache() {
        if (variantContext.getScope().getGlobalScope()
                .getProjectOptions()
                .get(BooleanOption.ENABLE_INTERMEDIATE_ARTIFACTS_CACHE)) {
            return variantContext.getScope().getGlobalScope().getBuildCache();
        } else {
            return null;
        }
    }


    public void replaceDexExternalLibMerge(BaseVariantOutput vod) {
        List<TransformTask> list = TransformManagerDelegate.findTransformTaskByTransformType(variantContext,
                ExternalLibsMergerTransform.class);
        for (TransformTask transformTask : list) {
            if (variantContext.getScope().getInstantRunBuildContext().isInInstantRunMode())
            transformTask.setEnabled(false);


        }
    }

    public void replaceMergeJavaResourcesTransform(AppVariantContext appVariantContext, BaseVariantOutput vod) {
        List<TransformTask> baseTransforms = TransformManagerDelegate.findTransformTaskByTransformType(
                variantContext, MergeJavaResourcesTransform.class);
        for (TransformTask transformTask : baseTransforms) {
            MergeJavaResourcesTransform transform = (MergeJavaResourcesTransform) transformTask.getTransform();
            PackagingOptions packagingOptions = (PackagingOptions) ReflectUtils.getField(transform, "packagingOptions");
            packagingOptions.exclude("**.aidl");
            packagingOptions.exclude("**.cfg");
            Set<? super QualifiedContent.Scope> mergeScopes = (Set<? super QualifiedContent.Scope>) ReflectUtils.getField(transform, "mergeScopes");
            Set<QualifiedContent.ContentType> mergedType = (Set<QualifiedContent.ContentType>) ReflectUtils.getField(transform, "mergedType");
            String name = (String) ReflectUtils.getField(transform, "name");
            AtlasMergeJavaResourcesTransform atlasMergeJavaResourcesTransform = new AtlasMergeJavaResourcesTransform(appVariantContext.getAppVariantOutputContext(ApkDataUtils.get(vod)), packagingOptions, mergeScopes, mergedType.iterator().next(), name, appVariantContext.getScope());
            ReflectUtils.updateField(transformTask, "transform",
                    atlasMergeJavaResourcesTransform);

        }

    }


    public void repalaceSomeInstantTransform(BaseVariantOutput vod) {

        variantContext.getProject().getTasks().withType(FastDeployRuntimeExtractorTask.class).forEach(fastDeployRuntimeExtractorTask -> fastDeployRuntimeExtractorTask.setEnabled(false));
        List<TransformTask> baseTransforms = TransformManagerDelegate.findTransformTaskByTransformType(
                variantContext, InstantRunDependenciesApkBuilder.class);
        if (baseTransforms != null && baseTransforms.size() > 0) {
            for (TransformTask transformTask : baseTransforms) {
                transformTask.setEnabled(false);
            }
        }

        variantContext.getProject().getTasks().withType(GenerateInstantRunAppInfoTask.class).forEach(generateInstantRunAppInfoTask -> generateInstantRunAppInfoTask.doLast(new Action<Task>() {
            @Override
            public void execute(Task task) {
                AtlasBuildContext.atlasMainDexHelperMap.get(variantContext.getVariantName()).getMainDexFiles().add(new BuildAtlasEnvTask.FileIdentity("instant-run-bootstrap", ((GenerateInstantRunAppInfoTask) task).getOutputFile(), false, false));
            }
        }));

        List<TransformTask> transforms = TransformManagerDelegate.findTransformTaskByTransformType(
                variantContext, InstantRunTransform.class);
        if (transforms != null && transforms.size() > 0) {
            for (TransformTask transformTask : transforms) {
                TaobaoInstantRunTransform taobaoInstantRunTransform = new TaobaoInstantRunTransform(variantContext, variantContext.getAppVariantOutputContext(ApkDataUtils.get(vod)), WaitableExecutor.useGlobalSharedThreadPool(),
                        variantContext.getScope());
                ReflectUtils.updateField(transformTask, "transform", taobaoInstantRunTransform);
            }
        }


        List<TransformTask> verifytransforms = TransformManagerDelegate.findTransformTaskByTransformType(
                variantContext, InstantRunVerifierTransform.class);
        if (verifytransforms != null && verifytransforms.size() > 0) {
            for (TransformTask transformTask : verifytransforms) {
                transformTask.setEnabled(false);
            }
        }

        List<TransformTask> transforms1 = TransformManagerDelegate.findTransformTaskByTransformType(
                variantContext, ExtractJarsTransform.class);
        if (transforms1 != null && transforms1.size() > 0) {
            for (TransformTask transformTask : transforms1) {
                TaobaoExtractJarsTransform taobaoExtractJarsTransform = new TaobaoExtractJarsTransform(variantContext, variantContext.getAppVariantOutputContext(ApkDataUtils.get(vod)), ImmutableSet.of(QualifiedContent.DefaultContentType.CLASSES),
                        ImmutableSet.of(QualifiedContent.Scope.SUB_PROJECTS));
                ReflectUtils.updateField(transformTask, "transform", taobaoExtractJarsTransform);
            }
        }

        List<TransformTask> transforms2 = TransformManagerDelegate.findTransformTaskByTransformType(
                variantContext, InstantRunDex.class);
        if (transforms2 != null && transforms2.size() > 0) {
            for (TransformTask transformTask : transforms2) {
                TaobaoInstantRunDex taobaoInstantRunDex = new TaobaoInstantRunDex(variantContext,
                        variantContext.getScope(),
                        (DexOptions) ReflectUtils.getField(transformTask.getTransform(), "dexOptions"),
                        variantContext.getProject().getLogger(),
                        (Integer) ReflectUtils.getField(transformTask.getTransform(), "minSdkForDx"),
                        vod);
                ReflectUtils.updateField(transformTask, "transform", taobaoInstantRunDex);
            }
        }


        List<TransformTask> transformTaskList = TransformManagerDelegate.findTransformTaskByTransformType(
                variantContext, InstantRunSliceSplitApkBuilder.class);
        if (transformTaskList != null && transformTaskList.size() > 0) {
            for (TransformTask transformTask : transformTaskList) {
                transformTask.setEnabled(false);
            }
        }

        List<TransformTask> transformTaskList1 = TransformManagerDelegate.findTransformTaskByTransformType(
                variantContext, InstantRunSplitApkResourcesBuilder.class);
        if (transformTaskList1 != null && transformTaskList1.size() > 0) {
            for (TransformTask transformTask : transformTaskList1) {
                transformTask.setEnabled(false);
//                TaobaoInstantRunSlicer taobaoInstantRunSlicer = new TaobaoInstantRunSlicer(variantContext.getProject().getLogger(),variantContext.getScope());
//                ReflectUtils.updateField(transformTask,"transform",taobaoInstantRunSlicer);
            }
        }


    }
}
