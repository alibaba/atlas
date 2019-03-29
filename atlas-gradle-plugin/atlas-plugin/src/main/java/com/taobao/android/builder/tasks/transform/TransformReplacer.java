package com.taobao.android.builder.tasks.transform;

import android.databinding.tool.DataBindingBuilder;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.gradle.api.BaseVariantOutput;
import com.android.build.gradle.internal.ApkDataUtils;
import com.android.build.gradle.internal.ExtraModelInfo;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.internal.TaskManager;
import com.android.build.gradle.internal.aapt.AaptGeneration;
import com.android.build.gradle.internal.api.AppVariantContext;
import com.android.build.gradle.internal.dsl.PackagingOptions;
import com.android.build.gradle.internal.pipeline.TransformTask;
import com.android.build.gradle.internal.process.GradleJavaProcessExecutor;
import com.android.build.gradle.internal.scope.DefaultGradlePackagingScope;
import com.android.build.gradle.internal.scope.PackagingScope;
import com.android.build.gradle.internal.scope.TaskOutputHolder;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.databinding.DataBindingMergeArtifactsTransform;
import com.android.build.gradle.internal.transforms.*;
import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.options.IntegerOption;
import com.android.build.gradle.options.ProjectOptions;
import com.android.build.gradle.tasks.ir.FastDeployRuntimeExtractorTask;
import com.android.builder.core.AtlasBuilder;
import com.android.builder.core.DefaultDexOptions;
import com.android.builder.core.DexOptions;
import com.android.builder.core.ErrorReporter;
import com.android.builder.dexing.DexMergerTool;
import com.android.builder.dexing.DexingType;
import com.android.builder.utils.FileCache;
import com.android.ide.common.internal.WaitableExecutor;
import com.android.ide.common.process.JavaProcessExecutor;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableSet;
import com.taobao.android.builder.AtlasBuildContext;
import com.taobao.android.builder.hook.dex.DexByteCodeConverterHook;
import com.taobao.android.builder.insant.*;
import com.taobao.android.builder.tasks.manager.transform.TransformManager;
import com.taobao.android.builder.tasks.transform.dex.AtlasDexArchiveBuilderTransform;
import com.taobao.android.builder.tasks.transform.dex.AtlasDexMergerTransform;
import com.taobao.android.builder.tasks.transform.dex.AtlasMultiDexListTransform;
import com.taobao.android.builder.tools.ReflectUtils;
import com.taobao.android.builder.tools.multidex.FastMultiDexer;
import groovy.transform.PackageScope;
import org.gradle.api.Action;
import org.gradle.api.Task;
import org.gradle.api.internal.tasks.DefaultTaskOutputs;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.Logging;
import org.gradle.api.specs.AndSpec;
import org.gradle.api.specs.Spec;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.google.common.base.Verify.verifyNotNull;

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
        List<TransformTask> list = TransformManager.findTransformTaskByTransformType(variantContext,
                DexArchiveBuilderTransform.class);

        DefaultDexOptions dexOptions = variantContext.getAppExtension().getDexOptions();

        boolean minified = variantContext.getScope().getCodeShrinker() != null;

        ProjectOptions projectOptions = variantContext.getScope().getGlobalScope().getProjectOptions();

        FileCache userLevelCache = getUserDexCache(minified, dexOptions.getPreDexLibraries());
        for (TransformTask transformTask : list) {
            AtlasDexArchiveBuilderTransform atlasDexArchiveBuilderTransform = new AtlasDexArchiveBuilderTransform(variantContext, vod,
                    dexOptions,
                    variantContext.getScope().getGlobalScope().getAndroidBuilder().getErrorReporter(),
                    userLevelCache,
                    variantContext.getScope().getMinSdkVersion().getFeatureLevel(),
                    variantContext.getScope().getDexer(),
                    projectOptions.get(BooleanOption.ENABLE_GRADLE_WORKERS),
                    projectOptions.get(IntegerOption.DEXING_READ_BUFFER_SIZE),
                    projectOptions.get(IntegerOption.DEXING_WRITE_BUFFER_SIZE),
                    variantContext.getScope().getVariantConfiguration().getBuildType().isDebuggable());
            atlasDexArchiveBuilderTransform.setTransformTask(transformTask);
            ReflectUtils.updateField(transformTask, "transform", atlasDexArchiveBuilderTransform);
            if (variantContext.getScope().getInstantRunBuildContext().isInInstantRunMode() && variantContext.getVariantConfiguration().getMinSdkVersion().getApiLevel() < 21) {
                transformTask.doLast(task -> {
                    task.getLogger().info("generate maindexList......");
                    generateMainDexList(variantContext.getScope());

                });
            }

        }

    }

    private void generateMainDexList(VariantScope variantScope) {
        File mainDexListFile = variantScope.getMainDexListFile();
        Collection<File> inputs = AtlasBuildContext.atlasMainDexHelperMap.get(variantScope.getFullVariantName()).getAllMainDexJars();
        inputs.addAll(AtlasBuildContext.atlasMainDexHelperMap.get(variantScope.getFullVariantName()).getInputDirs());
        FastMultiDexer fastMultiDexer = new FastMultiDexer(variantContext);
        Collection<File> files = null;
        try {
            files = fastMultiDexer.repackageJarList(inputs, mainDexListFile, variantScope.getVariantData().getName().toLowerCase().endsWith("release"));
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (files != null && files.size() > 0) {
            AtlasBuildContext.atlasMainDexHelperMap.get(variantScope.getFullVariantName()).addAllMainDexJars(files);

        }
    }

    public void replaceDataBindingMergeArtifactsTransform() {
        List<TransformTask> list = TransformManager.findTransformTaskByTransformType(variantContext,
                DataBindingMergeArtifactsTransform.class);
        for (TransformTask transformTask : list) {
            File outFolder =
                    new File(
                            variantContext.getScope().getBuildFolderForDataBindingCompiler(),
                            DataBindingBuilder.ARTIFACT_FILES_DIR_FROM_LIBS);
            AtlasDataBindingMergeArtifactsTransform dataBindingMergeArtifactsTransform = new AtlasDataBindingMergeArtifactsTransform(variantContext, Logging.getLogger(AtlasDataBindingMergeArtifactsTransform.class), outFolder);
            ReflectUtils.updateField(transformTask, "transform", dataBindingMergeArtifactsTransform);
        }

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
        List<TransformTask> list = TransformManager.findTransformTaskByTransformType(variantContext,
                ExternalLibsMergerTransform.class);
        for (TransformTask transformTask : list) {
            transformTask.setEnabled(false);


        }
    }

    public void replaceDexMerge(BaseVariantOutput vod) {
        List<TransformTask> list = TransformManager.findTransformTaskByTransformType(variantContext,
                DexMergerTransform.class);
        DexingType dexingType = variantContext.getScope().getDexingType();
        if (variantContext.getScope().getInstantRunBuildContext().isInInstantRunMode() && variantContext.getVariantConfiguration().getMinSdkVersion().getApiLevel() < 21) {
            dexingType = DexingType.LEGACY_MULTIDEX;
        }
        DexMergerTool dexMergerTool = variantContext.getScope().getDexMerger();
        int sdkVerision = variantContext.getScope().getMinSdkVersion().getFeatureLevel();
        boolean debug = variantContext.getScope().getVariantConfiguration().getBuildType().isDebuggable();
        ErrorReporter errorReporter = variantContext.getScope().getGlobalScope().getAndroidBuilder().getErrorReporter();
        for (TransformTask transformTask : list) {
            AtlasDexMergerTransform dexMergerTransform = new AtlasDexMergerTransform(
                    variantContext.getAppVariantOutputContext(ApkDataUtils.get(vod))
                    , dexingType,
                    dexingType == DexingType.LEGACY_MULTIDEX
                            ? variantContext.getProject().files(variantContext.getScope().getMainDexListFile())
                            : null,
                    errorReporter, dexMergerTool, sdkVerision, debug);
            ReflectUtils.updateField(transformTask, "transform", dexMergerTransform);

        }
    }

    public void replaceMultiDexListTransform() {
        List<TransformTask> list = null;
        FastMultiDexer fastMultiDexer = new FastMultiDexer(variantContext);
        if (usingIncrementalDexing(variantContext.getScope())) {
            list = TransformManager.findTransformTaskByTransformType(variantContext,
                    MainDexListTransform.class);
        } else {
            list = TransformManager.findTransformTaskByTransformType(variantContext,
                    MultiDexTransform.class);
        }
        if (list.size() > 0 && fastMultiDexer.isFastMultiDexEnabled()) {
            com.android.build.gradle.internal.dsl.DexOptions dexOptions = variantContext.getScope().getGlobalScope().getExtension().getDexOptions();
            AtlasMultiDexListTransform atlasMultiDexListTransform = new AtlasMultiDexListTransform(variantContext.getScope(), dexOptions);
            for (TransformTask transformTask : list) {
                ReflectUtils.updateField(transformTask, "transform", atlasMultiDexListTransform);
                transformTask.doFirst(task -> AtlasBuildContext.androidBuilderMap.get(task.getProject()).multiDexer = (AtlasBuilder.MultiDexer) fastMultiDexer);
                transformTask.doLast(task -> AtlasBuildContext.androidBuilderMap.get(task.getProject()).multiDexer = null);
            }
        }
    }

    private boolean usingIncrementalDexing(@NonNull VariantScope variantScope) {
        if (!variantScope.getGlobalScope().getProjectOptions().get(BooleanOption.ENABLE_DEX_ARCHIVE)) {
            return false;
        }
        if (variantScope.getVariantConfiguration().getBuildType().isDebuggable()) {
            return true;
        }

        // In release builds only D8 can be used. See b/37140568 for details.
        return variantScope.getGlobalScope().getProjectOptions().get(BooleanOption.ENABLE_D8);
    }

    public void replaceProguardTransform() {

        List<TransformTask> baseTransforms = TransformManager.findTransformTaskByTransformType(
                variantContext, ProGuardTransform.class);
        List<TransformTask> nextTransformTasks = TransformManager.findTransformTaskByTransformType(
                variantContext, DexArchiveBuilderTransform.class);

        for (TransformTask transformTask : baseTransforms) {
            AtlasProguardTransform newTransform = new AtlasProguardTransform(variantContext);
            newTransform.oldTransform = (ProGuardTransform) transformTask.getTransform();
            for (TransformTask nextTransformTask : nextTransformTasks) {
                if (nextTransformTask.getVariantName().equals(transformTask.getVariantName())) {
                    newTransform.nextTransformTask = nextTransformTask;
                }
            }
            ReflectUtils.updateField(transformTask, "transform",
                    newTransform);

//
        }
    }

    public void replaceDexTransform(AppVariantContext appVariantContext, BaseVariantOutput vod) {
        List<TransformTask> baseTransforms = TransformManager.findTransformTaskByTransformType(
                variantContext, DexTransform.class);

        DefaultDexOptions dexOptions = appVariantContext.getAppExtension().getDexOptions();
        DexingType dexingType = appVariantContext.getScope().getDexingType();
        DexByteCodeConverterHook dexByteCodeConverterHook = new DexByteCodeConverterHook(variantContext
                , variantContext.getAppVariantOutputContext(ApkDataUtils.get(vod))
                , LoggerWrapper.getLogger(DexByteCodeConverterHook.class)
                , appVariantContext.getScope().getGlobalScope().getAndroidBuilder().getTargetInfo()
                , new GradleJavaProcessExecutor(appVariantContext.getProject())
                , appVariantContext.getProject().getLogger().isEnabled(LogLevel.INFO)
                , new ExtraModelInfo(appVariantContext.getScope().getGlobalScope().getProjectOptions(), appVariantContext.getProject().getLogger()));

        for (TransformTask transformTask : baseTransforms) {
            DexTransform newTransform = new DexTransform(dexOptions
                    , dexingType
                    , false
                    , appVariantContext.getProject().files(variantContext.getScope().getMainDexListFile())
                    , verifyNotNull(appVariantContext.getScope().getGlobalScope().getAndroidBuilder().getTargetInfo(), "Target Info not set.")
                    , dexByteCodeConverterHook
                    , appVariantContext.getScope().getGlobalScope().getAndroidBuilder().getErrorReporter()
                    , variantContext.getScope().getMinSdkVersion().getFeatureLevel());
            ReflectUtils.updateField(transformTask, "transform",
                    newTransform);
        }

    }

    public void replaceMergeJavaResourcesTransform(AppVariantContext appVariantContext, BaseVariantOutput vod) {
        List<TransformTask> baseTransforms = TransformManager.findTransformTaskByTransformType(
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

    public void replaceFixStackFramesTransform(BaseVariantOutput vod) {
        List<TransformTask> baseTransforms = TransformManager.findTransformTaskByTransformType(
                variantContext, FixStackFramesTransform.class);
        for (TransformTask transformTask : baseTransforms) {
            FixStackFramesTransform transform = (FixStackFramesTransform) transformTask.getTransform();

            AtlasFixStackFramesTransform atlasFixStackFramesTransform = new AtlasFixStackFramesTransform(variantContext.getAppVariantOutputContext(ApkDataUtils.get(vod)), (Supplier<List<File>>) ReflectUtils.getField(transform, "androidJarClasspath"), (List<Path>) ReflectUtils.getField(transform, "compilationBootclasspath"), (FileCache) ReflectUtils.getField(transform, "userCache"));
            atlasFixStackFramesTransform.oldTransform = transform;
            ReflectUtils.updateField(transformTask, "transform",
                    atlasFixStackFramesTransform);
        }
    }

    public void replaceDesugarTransform(BaseVariantOutput vod) {
        List<TransformTask> baseTransforms = TransformManager.findTransformTaskByTransformType(
                variantContext, DesugarTransform.class);
        for (TransformTask transformTask : baseTransforms) {
            DesugarTransform transform = (DesugarTransform) transformTask.getTransform();
            AtlasDesugarTransform atlasDesugarTransform = new AtlasDesugarTransform(
                    variantContext.getAppVariantOutputContext(ApkDataUtils.get(vod)),
                    (Supplier<List<File>>) ReflectUtils.getField(transform, "androidJarClasspath"),
                    (List) ReflectUtils.getField(transform, "compilationBootclasspath"),
                    variantContext.getScope().getGlobalScope().getBuildCache(),
                    (int) ReflectUtils.getField(transform, "minSdk"),
                    (JavaProcessExecutor) ReflectUtils.getField(transform, "executor"),
                    (boolean) ReflectUtils.getField(transform, "verbose"),
                    (boolean) ReflectUtils.getField(transform, "enableGradleWorkers"),
                    (Path) ReflectUtils.getField(transform, "tmpDir"));
            atlasDesugarTransform.oldTransform = transform;
            ReflectUtils.updateField(transformTask, "transform",
                    atlasDesugarTransform);
        }
    }

    public void replaceShrinkResourcesTransform() {
        File shrinkerOutput =
                FileUtils.join(
                        variantContext.getScope().getGlobalScope().getIntermediatesDir(),
                        "res_stripped",
                        variantContext.getScope().getVariantConfiguration().getDirName());
        List<TransformTask> baseTransforms = TransformManager.findTransformTaskByTransformType(
                variantContext, ShrinkResourcesTransform.class);
        for (TransformTask transform : baseTransforms) {
            ShrinkResourcesTransform oldTransform = (ShrinkResourcesTransform) transform.getTransform();
            ResourcesShrinker resourcesShrinker = new ResourcesShrinker(oldTransform, variantContext.getVariantData(),
                    variantContext.getScope().getOutput(TaskOutputHolder.TaskOutputType.PROCESSED_RES),
                    shrinkerOutput,
                    AaptGeneration.fromProjectOptions(variantContext.getScope().getGlobalScope().getProjectOptions()),
                    variantContext.getScope().getOutput(TaskOutputHolder.TaskOutputType.SPLIT_LIST),
                    variantContext.getProject().getLogger(),
                    variantContext);
            ReflectUtils.updateField(transform, "transform",
                    resourcesShrinker);

        }


    }

    public void disableCache() {
        List<TransformTask> list = null;
        if (usingIncrementalDexing(variantContext.getScope())) {
            list = TransformManager.findTransformTaskByTransformType(variantContext,
                    MainDexListTransform.class);
        } else {
            list = TransformManager.findTransformTaskByTransformType(variantContext,
                    MultiDexTransform.class);
        }
        if (list != null) {
            for (TransformTask transformTask : list) {
                List<Object> list1 = (List<Object>) ReflectUtils.getField(DefaultTaskOutputs.class, transformTask.getOutputs(), "cacheIfSpecs");
                list1.clear();
                ReflectUtils.updateField(transformTask.getOutputs(), "upToDateSpec", AndSpec.empty());


            }
        }

        list = TransformManager.findTransformTaskByTransformType(variantContext,
                AtlasProguardTransform.class);
        if (list != null) {
            for (TransformTask transformTask : list) {
                List<Object> list1 = (List<Object>) ReflectUtils.getField(DefaultTaskOutputs.class, transformTask.getOutputs(), "cacheIfSpecs");
                list1.clear();
                ReflectUtils.updateField(transformTask.getOutputs(), "upToDateSpec", AndSpec.empty());

            }
        }

        list = TransformManager.findTransformTaskByTransformType(variantContext,
                DexTransform.class);
        if (list != null) {
            for (TransformTask transformTask : list) {
                List<Object> list1 = (List<Object>) ReflectUtils.getField(DefaultTaskOutputs.class, transformTask.getOutputs(), "cacheIfSpecs");
                list1.clear();
                ReflectUtils.updateField(transformTask.getOutputs(), "upToDateSpec", AndSpec.empty());

            }
        }

        list = TransformManager.findTransformTaskByTransformType(variantContext,
                AtlasMultiDexListTransform.class);
        if (list != null) {
            for (TransformTask transformTask : list) {
                List<Object> list1 = (List<Object>) ReflectUtils.getField(DefaultTaskOutputs.class, transformTask.getOutputs(), "cacheIfSpecs");
                list1.clear();
                ReflectUtils.updateField(transformTask.getOutputs(), "upToDateSpec", AndSpec.empty());


            }
        }


    }

    public void repalaceSomeInstantTransform(BaseVariantOutput vod) {

        variantContext.getProject().getTasks().withType(FastDeployRuntimeExtractorTask.class).forEach(fastDeployRuntimeExtractorTask -> fastDeployRuntimeExtractorTask.setEnabled(false));
        List<TransformTask> baseTransforms = TransformManager.findTransformTaskByTransformType(
                variantContext, InstantRunDependenciesApkBuilder.class);
        if (baseTransforms != null && baseTransforms.size() > 0) {
            for (TransformTask transformTask : baseTransforms) {
                transformTask.setEnabled(false);
            }
        }

        List<TransformTask> transforms = TransformManager.findTransformTaskByTransformType(
                variantContext, InstantRunTransform.class);
        if (transforms != null && transforms.size() > 0) {
            for (TransformTask transformTask : transforms) {
                TaobaoInstantRunTransform taobaoInstantRunTransform = new TaobaoInstantRunTransform(variantContext, variantContext.getAppVariantOutputContext(ApkDataUtils.get(vod)), WaitableExecutor.useGlobalSharedThreadPool(),
                        variantContext.getScope());
                ReflectUtils.updateField(transformTask, "transform", taobaoInstantRunTransform);
            }
        }


        List<TransformTask> verifytransforms = TransformManager.findTransformTaskByTransformType(
                variantContext, InstantRunVerifierTransform.class);
        if (verifytransforms != null && verifytransforms.size() > 0) {
            for (TransformTask transformTask : verifytransforms) {
                transformTask.setEnabled(false);
            }
        }

        List<TransformTask> transforms1 = TransformManager.findTransformTaskByTransformType(
                variantContext, ExtractJarsTransform.class);
        if (transforms1 != null && transforms1.size() > 0) {
            for (TransformTask transformTask : transforms1) {
                TaobaoExtractJarsTransform taobaoExtractJarsTransform = new TaobaoExtractJarsTransform(variantContext, variantContext.getAppVariantOutputContext(ApkDataUtils.get(vod)), ImmutableSet.of(QualifiedContent.DefaultContentType.CLASSES),
                        ImmutableSet.of(QualifiedContent.Scope.SUB_PROJECTS));
                ReflectUtils.updateField(transformTask, "transform", taobaoExtractJarsTransform);
            }
        }

        List<TransformTask> transforms2 = TransformManager.findTransformTaskByTransformType(
                variantContext, InstantRunDex.class);
        if (transforms2 != null && transforms2.size() > 0) {
            for (TransformTask transformTask : transforms2) {
                TaobaoInstantRunDex taobaoInstantRunDex = new TaobaoInstantRunDex(variantContext,
                        variantContext.getScope(),
                        variantContext.getScope().getGlobalScope().getAndroidBuilder().getDexByteCodeConverter(),
                        (DexOptions) ReflectUtils.getField(transformTask.getTransform(), "dexOptions"),
                        variantContext.getProject().getLogger(),
                        (Integer) ReflectUtils.getField(transformTask.getTransform(), "minSdkForDx"),
                vod);
                ReflectUtils.updateField(transformTask, "transform", taobaoInstantRunDex);
            }
        }


        List<TransformTask> transformTaskList = TransformManager.findTransformTaskByTransformType(
                variantContext, InstantRunSliceSplitApkBuilder.class);
        if (transformTaskList != null && transformTaskList.size() > 0) {
            for (TransformTask transformTask : transformTaskList) {
                transformTask.setEnabled(false);
            }
        }

        List<TransformTask> transformTaskList1 = TransformManager.findTransformTaskByTransformType(
                variantContext, InstantRunSlicer.class);
        if (transformTaskList1 != null && transformTaskList1.size() > 0) {
            for (TransformTask transformTask : transformTaskList1) {
                transformTask.setEnabled(false);
//                TaobaoInstantRunSlicer taobaoInstantRunSlicer = new TaobaoInstantRunSlicer(variantContext.getProject().getLogger(),variantContext.getScope());
//                ReflectUtils.updateField(transformTask,"transform",taobaoInstantRunSlicer);
            }
        }


    }
}
