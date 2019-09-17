package com.taobao.android.builder.tasks.transform;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.Transform;
import com.android.build.gradle.api.BaseVariantOutput;
import com.android.build.gradle.internal.ApkDataUtils;
import com.android.build.gradle.internal.api.AppVariantContext;
import com.android.build.gradle.internal.api.artifact.BuildableArtifactImpl;
import com.android.build.gradle.internal.api.artifact.BuildableArtifactUtil;
import com.android.build.gradle.internal.dsl.PackagingOptions;
import com.android.build.gradle.internal.pipeline.*;
import com.android.build.gradle.internal.scope.ApkData;
import com.android.build.gradle.internal.scope.BuildArtifactsHolder;
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
import com.android.utils.StringHelper;
import com.google.common.base.CaseFormat;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.taobao.android.builder.AtlasBuildContext;
import com.taobao.android.builder.AtlasMainDexHelper;
import com.taobao.android.builder.insant.*;
import com.taobao.android.builder.tasks.app.BuildAtlasEnvTask;
import com.taobao.android.builder.tasks.manager.transform.MtlDexArchiveBuilderTransform;
import com.taobao.android.builder.tasks.manager.transform.MtlDexArchiveBuilderTransformBuilder;
import com.taobao.android.builder.tasks.manager.transform.MtlDexMergeTransform;
import com.taobao.android.builder.tools.ReflectUtils;
import com.taobao.android.builder.tools.multidex.mutli.JarRefactor;
import org.apache.commons.io.FileUtils;
import org.gradle.api.Action;
import org.gradle.api.Task;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFile;
import org.gradle.api.internal.provider.DefaultPropertyState;
import org.gradle.api.internal.provider.DefaultProvider;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.android.build.gradle.internal.scope.InternalArtifactType.APK_MAPPING;

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
                                .setIntermediateStreamHelper(new AtlasIntermediateStreamHelper(transformTask))
                                .setAppVariantOutputContext(variantContext.getAppVariantOutputContext(ApkDataUtils.get(vod)))
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
                boolean instantRunMode = false;
                DexingType dexingType = null;
                FileCollection multidexFiles = null;
                if (variantContext.getScope().getInstantRunBuildContext().isInInstantRunMode() && !variantContext.getProject().hasProperty("devMode")) {
                    dexingType = DexingType.LEGACY_MULTIDEX;
                    instantRunMode = true;
                    multidexFiles = variantContext.getProject().files(mainDexListProvider());
                } else {
                    dexingType = variantContext.getScope().getDexingType();

                }
                boolean isDebuggable = variantContext.getScope().getVariantConfiguration().getBuildType().isDebuggable();
                MtlDexMergeTransform dexTransform =
                        new MtlDexMergeTransform(
                                variantContext.getAppVariantOutputContext(ApkDataUtils.get(vod)),
                                dexingType,
                                instantRunMode ?
                                        new BuildableArtifactImpl(multidexFiles) :
                                        dexingType == DexingType.LEGACY_MULTIDEX ?
                                                variantContext.getScope()
                                                        .getArtifacts()
                                                        .getFinalArtifactFiles(
                                                                InternalArtifactType.LEGACY_MULTIDEX_MAIN_DEX_LIST) : null
                                ,
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

    public void replaceMultidexTransform(BaseVariantOutput vod) {
        List<TransformTask> transforms = TransformManagerDelegate.findTransformTaskByTransformType(
                variantContext, D8MainDexListTransform.class);



        final File[] mainDexListFile = {null};

        transforms.forEach(transformTask -> {
            transformTask.setEnabled(false);

            if ((variantContext.getScope()
                    .getVariantConfiguration()
                    .getMinSdkVersionWithTargetDeviceApi()
                    .getFeatureLevel()
                    < 21 && variantContext.getScope().getVariantConfiguration().isMultiDexEnabled())) {

                mainDexListFile[0] = variantContext.getScope()
                        .getArtifacts()
                        .appendArtifact(
                                InternalArtifactType.LEGACY_MULTIDEX_MAIN_DEX_LIST,
                                transformTask.getName(),
                                "mainDexList.txt");

            }


        });

        if (mainDexListFile[0] == null && variantContext.getScope().getInstantRunBuildContext().isInInstantRunMode()) {
            mainDexListFile[0] = mainDexListProvider().get();
        }

        if (!variantContext.getScope().getVariantConfiguration().isMultiDexEnabled()){
            return;
        }



        if (variantContext.getProject().hasProperty("devMode")){
            return;
        }

        List<TransformTask> transforms1 = TransformManagerDelegate.findTransformTaskByTransformType(
                variantContext, MtlDexMergeTransform.class);

        File finalMainDexListFile = mainDexListFile[0];


        transforms1.forEach(transformTask1 -> transformTask1.doFirst(task -> {
            try {
                if (!finalMainDexListFile.getParentFile().exists()) {
                    finalMainDexListFile.getParentFile().mkdirs();
                }
                transformTask1.getLogger().warn("begain to generate maindexlist :" + finalMainDexListFile.getAbsolutePath());


                List<File>files = new ArrayList<>();
                files.addAll(AtlasBuildContext.atlasMainDexHelperMap.get(variantContext.getVariantName()).getAllMainDexJars());
                files.addAll(AtlasBuildContext.atlasMainDexHelperMap.get(variantContext.getVariantName()).getInputDirs());

                new JarRefactor(variantContext, variantContext.getBuildType().getMultiDexConfig()).repackageJarList(files, finalMainDexListFile, variantContext.getVariantConfiguration().getBuildType().isMinifyEnabled());
                FileUtils.copyFileToDirectory(finalMainDexListFile, variantContext.getScope().getGlobalScope().getOutputsDir());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }));

}


    private Provider<File> mainDexListProvider() {
        return new DefaultProvider<>(() -> {
            File finalMainDexListFile = new File(variantContext.getScope().getIntermediateDir(InternalArtifactType.LEGACY_MULTIDEX_MAIN_DEX_LIST), "mainDexList.txt");
            return finalMainDexListFile;
        });

    }

    public void replaceR8Transform(BaseVariantOutput vod) {
        List<TransformTask> transforms = TransformManagerDelegate.findTransformTaskByTransformType(
                variantContext, R8Transform.class);
        transforms.forEach(transformTask -> {
            File multiDexKeepFile = variantContext.getScope().getVariantConfiguration().getMultiDexKeepFile();
            FileCollection userMainDexListFiles;
            if (multiDexKeepFile != null) {
                userMainDexListFiles = variantContext.getProject().files(multiDexKeepFile);
            } else {
                userMainDexListFiles = variantContext.getProject().files();
            }
            File multiDexKeepProguard =
                    variantContext.getScope().getVariantConfiguration().getMultiDexKeepProguard();
            FileCollection userMainDexListProguardRules;
            if (multiDexKeepProguard != null) {
                userMainDexListProguardRules = variantContext.getProject().files(multiDexKeepProguard);
            } else {
                userMainDexListProguardRules = variantContext.getProject().files();
            }
            FileCollection inputMapping = null;

            if (variantContext.apContext.getApExploredFolder()!= null) {
                 inputMapping = variantContext.getProject().files(new File(variantContext.apContext.getApExploredFolder(), "mapping.txt"));
            }else {
                inputMapping = variantContext.getProject().files();
            }

            R8Transform r8Transform = (R8Transform) transformTask.getTransform();
            DelegateR8Transform delegateR8Transform = new DelegateR8Transform(variantContext,variantContext.getAppVariantOutputContext(ApkDataUtils.get(vod)),variantContext.getScope(),userMainDexListFiles,userMainDexListProguardRules,inputMapping,variantContext.getScope().getOutputProguardMappingFile());
            delegateR8Transform.setR8Transform(r8Transform);
            ReflectUtils.updateField(transformTask,"transform",delegateR8Transform);
        });
    }
}
