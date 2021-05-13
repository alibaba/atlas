package com.android.build.gradle.tasks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.api.BaseVariantOutput;
import com.android.build.gradle.internal.api.AppVariantContext;
import com.android.build.gradle.internal.api.AppVariantOutputContext;
import com.android.build.gradle.internal.api.VariantContext;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.dsl.AbiSplitOptions;
import com.android.build.gradle.internal.dsl.DslAdaptersKt;
import com.android.build.gradle.internal.incremental.FileType;
import com.android.build.gradle.internal.packaging.IncrementalPackagerBuilder;
import com.android.build.gradle.internal.pipeline.StreamFilter;
import com.android.build.gradle.internal.scope.*;
import com.android.build.gradle.internal.tasks.TaskInputHelper;
import com.android.build.gradle.internal.tasks.Workers;
import com.android.build.gradle.internal.variant.MultiOutputPolicy;
import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.options.ProjectOptions;
import com.android.build.gradle.options.StringOption;
import com.android.build.gradle.tasks.PackageAndroidArtifact;
import com.android.builder.profile.ProcessProfileWriter;
import com.google.wireless.android.sdk.stats.GradleBuildProjectMetrics;
import com.taobao.android.builder.dependency.model.AwbBundle;
import com.taobao.android.builder.tasks.manager.MtlBaseTaskAction;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.workers.WorkerExecutor;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;

import static com.android.build.gradle.internal.scope.InternalArtifactType.MERGED_ASSETS;

/**
 * \* Created with IntelliJ IDEA.
 * \* User: lilong
 * \* Date: 2021/3/17
 * \* Time: 11:12 上午
 * \* Description:
 * \
 */
public class FeaturePackageApplication extends PackageAndroidArtifact {


    InternalArtifactType expectedOutputType;


    private AppVariantOutputContext appVariantOutputContext;

    private AwbBundle awbBundle;


    @Inject
    public FeaturePackageApplication(WorkerExecutor workerExecutor) {
        super(Workers.INSTANCE.getWorker(workerExecutor));
    }

    @Override
    @Internal
    protected InternalArtifactType getInternalArtifactType() {
        return expectedOutputType;
    }

    @Override
    public void doFullTaskAction() {
         if (!appVariantOutputContext.getAwbJniFolder(awbBundle).exists()){
             jniFolders = appVariantOutputContext.getVariantContext().getProject().files();
         }else{
             jniFolders = appVariantOutputContext.getFeatureJniFolders(awbBundle);
         }
        super.doFullTaskAction();

        appVariantOutputContext.getScope().
                getArtifacts().createBuildableArtifact(MERGED_ASSETS, BuildArtifactsHolder.OperationType.APPEND,appVariantOutputContext.getFeatureApkOutputDir(awbBundle),getName());

        Collection<String>options = appVariantOutputContext.getScope().getTaskContainer().getPackageAndroidTask().get().aaptOptionsNoCompress;
         if (options == null){
             appVariantOutputContext.getScope().getTaskContainer().getPackageAndroidTask().get().aaptOptionsNoCompress = new ArrayList();
         }
             appVariantOutputContext.getScope().getTaskContainer().getPackageAndroidTask().get().aaptOptionsNoCompress.add(appVariantOutputContext.getFeaturePackageOutputFile(awbBundle).getName());
//
//
//              appVariantOutputContext.getVariantContext().getProject().files(appVariantOutputContext.getVariantContext().getPluginApkOutputDir()));

    }

    @Override
    @Internal
    protected boolean isIncremental() {
        return false;
    }

    public void recordMetrics(File apkOutputFile, File resourcesApFile) {
        long metricsStartTime = System.nanoTime();
        GradleBuildProjectMetrics.Builder metrics = GradleBuildProjectMetrics.newBuilder();

        Long apkSize = getSize(apkOutputFile);
        if (apkSize != null) {
            metrics.setApkSize(apkSize);
        }

        Long resourcesApSize = getSize(resourcesApFile);
        if (resourcesApSize != null) {
            metrics.setResourcesApSize(resourcesApSize);
        }

        metrics.setMetricsTimeNs(System.nanoTime() - metricsStartTime);

        ProcessProfileWriter.getProject(getProject().getPath()).setMetrics(metrics);
    }

    @Nullable
    @Internal
    private static Long getSize(@Nullable File file) {
        if (file == null) {
            return null;
        }
        try {
            return java.nio.file.Files.size(file.toPath());
        } catch (IOException e) {
            return null;
        }
    }

    // ----- CreationAction -----

    /**
     * Configures the task to perform the "standard" packaging, including all files that should end
     * up in the APK.
     */
    public static class StandardCreationAction extends MtlBaseTaskAction<FeaturePackageApplication> {

        private final InternalArtifactType expectedOutputType = InternalArtifactType.FULL_APK;

        private final AwbBundle awbBundle;

        private AppVariantContext variantContext;

        private File outputDirectory;


        public StandardCreationAction(VariantContext variantContext, BaseVariantOutput baseVariantOutput) {
            this(((AppVariantContext) variantContext).getPluginBundle(),variantContext, baseVariantOutput);
        }

        public StandardCreationAction(AwbBundle awbBundle, VariantContext variantContext, BaseVariantOutput baseVariantOutput) {
            super(variantContext, baseVariantOutput);
            this.variantContext = (AppVariantContext) variantContext;
            this.awbBundle = awbBundle;


        }

        @Override
        public void configure(FeaturePackageApplication packageAndroidArtifact) {
            super.configure(packageAndroidArtifact);
            packageAndroidArtifact.awbBundle = awbBundle;
            packageAndroidArtifact.appVariantOutputContext = getAppVariantOutputContext();
            VariantScope variantScope = getVariantScope();
            GlobalScope globalScope = variantScope.getGlobalScope();
            GradleVariantConfiguration variantConfiguration =
                    variantScope.getVariantConfiguration();
            InternalArtifactType resourceFilesInputType =
                    variantScope.useResourceShrinker()
                            ? InternalArtifactType.SHRUNK_PROCESSED_RES
                            : InternalArtifactType.PROCESSED_RES;

            packageAndroidArtifact.instantRunFileType = FileType.MAIN;
            packageAndroidArtifact.taskInputType = resourceFilesInputType;
            packageAndroidArtifact.minSdkVersion =
                    TaskInputHelper.memoize(variantScope::getMinSdkVersion);
            packageAndroidArtifact.instantRunContext =
                    TaskInputHelper.memoize(variantScope::getInstantRunBuildContext);

            packageAndroidArtifact.resourceFiles = getAppVariantOutputContext().getFeatureResourceFiles(awbBundle);
            packageAndroidArtifact.outputDirectory = getAppVariantOutputContext().getFeatureApkOutputDir(awbBundle);
            this.outputDirectory = getAppVariantOutputContext().getFeatureApkOutputDir(awbBundle);
            packageAndroidArtifact.setIncrementalFolder(
                    new File(
                            variantScope.getIncrementalDir(packageAndroidArtifact.getName()),
                            "tmp"));
            packageAndroidArtifact.outputScope = variantScope.getOutputScope();

            packageAndroidArtifact.fileCache = globalScope.getBuildCache();
            packageAndroidArtifact.aaptOptionsNoCompress =
                    DslAdaptersKt.convert(globalScope.getExtension().getAaptOptions())
                            .getNoCompress();

            packageAndroidArtifact.manifests = getAppVariantOutputContext().getFeatureManifest();

            packageAndroidArtifact.dexFolders = getAppVariantOutputContext().getFeatureDexFolders(awbBundle);
//            packageAndroidArtifact.featureDexFolder = getFeatureDexFolder();


            packageAndroidArtifact.javaResourceFiles = getAppVariantOutputContext().getFeatureJavaResourceFiles(awbBundle);

            packageAndroidArtifact.assets = getAppVariantOutputContext().getFeatureAssets(awbBundle);

            packageAndroidArtifact.setAbiFilters(variantConfiguration.getSupportedAbis());
            packageAndroidArtifact.setJniDebugBuild(
                    variantConfiguration.getBuildType().isJniDebuggable());
            packageAndroidArtifact.setDebugBuild(
                    variantConfiguration.getBuildType().isDebuggable());

            packageAndroidArtifact.apkList =
                    getVariantScope()
                            .getArtifacts()
                            .getFinalArtifactFiles(InternalArtifactType.APK_LIST);

            ProjectOptions projectOptions = variantScope.getGlobalScope().getProjectOptions();
            packageAndroidArtifact.projectBaseName = globalScope.getProjectBaseName();
            packageAndroidArtifact.manifestType = variantScope.getManifestArtifactType();
            packageAndroidArtifact.buildTargetAbi =
                    globalScope.getExtension().getSplits().getAbi().isEnable()
                            ? projectOptions.get(StringOption.IDE_BUILD_TARGET_ABI)
                            : null;
            packageAndroidArtifact.buildTargetDensity =
                    globalScope.getExtension().getSplits().getDensity().isEnable()
                            ? projectOptions.get(StringOption.IDE_BUILD_TARGET_DENSITY)
                            : null;

            packageAndroidArtifact.apkFormat =
                    projectOptions.get(BooleanOption.DEPLOYMENT_USES_DIRECTORY)
                            ? IncrementalPackagerBuilder.ApkFormat.DIRECTORY
                            : projectOptions.get(BooleanOption.DEPLOYMENT_PROVIDES_LIST_OF_CHANGES)
                            ? IncrementalPackagerBuilder.ApkFormat.FILE_WITH_LIST_OF_CHANGES
                            : IncrementalPackagerBuilder.ApkFormat.FILE;

            packageAndroidArtifact.setSigningConfig(variantScope.getSigningConfigFileCollection());


            finalConfigure(packageAndroidArtifact);
        }

        @NonNull
        @Override
        public String getName() {
            return getVariantScope().getTaskName("package"+ awbBundle.getName(),"Feature");
        }

        @NonNull
        @Override
        public Class<FeaturePackageApplication> getType() {
            return FeaturePackageApplication.class;
        }

        @Override
        public void preConfigure(@NonNull String taskName) {
            super.preConfigure(taskName);
//            getVariantScope()
//                    .getArtifacts()
//                    .appendArtifact(
//                            expectedOutputType, ImmutableList.of(outputDirectory), taskName);
        }

        @Override
        public void handleProvider(
                @NonNull TaskProvider<? extends FeaturePackageApplication> taskProvider) {
            super.handleProvider(taskProvider);
        }

        protected void finalConfigure(FeaturePackageApplication task) {
            task.expectedOutputType = expectedOutputType;
            task.outputFileProvider = apkData -> getAppVariantOutputContext().getFeaturePackageOutputFile(awbBundle);

//            Set<String> filters =
//                    AbiSplitOptions.getAbiFilters(
//                            variantContext.getScope().getGlobalScope().getExtension().getSplits().getAbiFilters());

//            task.jniFolders =  getAppVariantOutputContext().getPluginJniFolders(variantContext.getPluginBundle());
        }
    }
}
