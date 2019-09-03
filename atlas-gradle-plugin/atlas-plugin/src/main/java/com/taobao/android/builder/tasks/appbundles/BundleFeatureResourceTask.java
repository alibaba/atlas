package com.taobao.android.builder.tasks.appbundles;

import com.android.build.gradle.api.BaseVariantOutput;
import com.android.build.gradle.internal.api.VariantContext;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.dsl.AaptOptions;
import com.android.build.gradle.internal.dsl.DslAdaptersKt;
import com.android.build.gradle.internal.ide.AaptOptionsImpl;
import com.android.build.gradle.internal.res.Aapt2MavenUtils;
import com.android.build.gradle.internal.res.Aapt2ProcessResourcesRunnable;
import com.android.build.gradle.internal.res.namespaced.Aapt2DaemonManagerService;
import com.android.build.gradle.internal.res.namespaced.Aapt2ServiceKey;
import com.android.build.gradle.internal.scope.ApkData;
import com.android.build.gradle.internal.scope.ExistingBuildElements;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.tasks.AndroidBuilderTask;
import com.android.build.gradle.internal.tasks.TaskInputHelper;
import com.android.build.gradle.internal.tasks.Workers;
import com.android.build.gradle.internal.tasks.featuresplit.FeatureSetMetadata;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.options.ProjectOptions;
import com.android.build.gradle.options.StringOption;
import com.android.builder.core.VariantType;
import com.android.builder.core.VariantTypeImpl;
import com.android.builder.internal.aapt.AaptPackageConfig;
import com.android.ide.common.workers.ExecutorServiceAdapter;
import com.android.ide.common.workers.WorkerExecutorFacade;
import com.android.sdklib.IAndroidTarget;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.taobao.android.builder.dependency.model.AwbBundle;
import com.taobao.android.builder.tasks.manager.MtlBaseTaskAction;
import org.apache.commons.io.FileUtils;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskAction;
import org.gradle.workers.WorkerExecutor;
import org.jetbrains.annotations.NotNull;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Supplier;

/**
 * @ClassName BundleFeatureResource
 * @Description TODO
 * @Author zhayu.ll
 * @Date 2019-08-21 14:31
 * @Version 1.0
 */
public class BundleFeatureResourceTask extends AndroidBuilderTask {

    private File featureBundledResFile;
    private File    incrementalFolder;
    private Provider<String> versionName;
    private Provider<Integer> versionCode;
    private ApkData apkData;
    private File manifestFile;
    private File inputResourcesDir;
    private Supplier<Integer>resIdSupplier;
    private boolean debuggable;
    private AaptOptions aaptOptions;
    private String buildTargetDensity;
    private File mergeBlameLogFolder;
    private int minSdkVersion;

    private Collection<String> resConfig;

    private FileCollection aapt2FromMaven;

    private BaseVariantOutput baseVariantOutput;

    private VariantContext variantContext;

    private WorkerExecutorFacade workers = null;

    @Inject
    public BundleFeatureResourceTask(WorkerExecutor workers) {
        this.workers = Workers.INSTANCE.getWorker(workers);
    }


    public void taskAction(){
        try {
            FileUtils.forceMkdir(featureBundledResFile.getParentFile());
        } catch (IOException e) {
            e.printStackTrace();
        }


        ImmutableList.Builder featurePackagesBuilder = new ImmutableList.Builder<File>();

        featurePackagesBuilder.add(ExistingBuildElements.from(InternalArtifactType.PROCESSED_RES,variantContext.getScope().getArtifacts().getFinalArtifactFiles(InternalArtifactType.PROCESSED_RES)).iterator().next().getOutputFile());

        AaptPackageConfig aaptPackageConfig = new AaptPackageConfig.Builder()
                .setAndroidJarPath(getBuilder().getTarget().getPath(IAndroidTarget.ANDROID_JAR))
                .setManifestFile(manifestFile)
                .setOptions(DslAdaptersKt.convert(aaptOptions))
                .setResourceOutputApk(featureBundledResFile)
                .setVariantType(VariantTypeImpl.OPTIONAL_APK)
                .setDebuggable(debuggable)
                .setPackageId(resIdSupplier.get())
                .setAllowReservedPackageId(true)
                .setDependentFeatures(featurePackagesBuilder.build())
                .setResourceDir(inputResourcesDir)
                .setResourceConfigs(ImmutableSet.copyOf(resConfig)).build();


        Aapt2ServiceKey aapt2ServiceKey = Aapt2DaemonManagerService.registerAaptService(aapt2FromMaven,null,getBuilder().getLogger());

        workers.submit(Aapt2ProcessResourcesRunnable.class,new Aapt2ProcessResourcesRunnable.Params(aapt2ServiceKey,aaptPackageConfig));
    }

    public static class CreationAction extends MtlBaseTaskAction<BundleFeatureResourceTask>{


        private File featureBundledResFile;

        private AwbBundle awbBundle;

        public CreationAction(AwbBundle awbBundle,VariantContext variantContext, BaseVariantOutput baseVariantOutput) {
            super(variantContext, baseVariantOutput);
            this.awbBundle = awbBundle;
        }

        @Override
        public void configure(BundleFeatureResourceTask task) {
            super.configure(task);
            BaseVariantData variantData = scope.getVariantData();
            ProjectOptions projectOptions = scope.getGlobalScope().getProjectOptions();
            GradleVariantConfiguration variantConfiguration = scope.getVariantConfiguration();
            task.featureBundledResFile = featureBundledResFile;
            task.incrementalFolder = scope.getIncrementalDir(getName());
            task.baseVariantOutput = baseVariantOutput;
            task.variantContext = variantContext;
            task.versionName = TaskInputHelper.memoizeToProvider(variantContext.getProject(), new Supplier<String>() {


                @Override
                public String get() {
                    return variantConfiguration.getVersionName();
                }
            });

            task.versionCode = TaskInputHelper.memoizeToProvider(variantContext.getProject(), new Supplier<Integer>() {
                @Override
                public Integer get() {
                    return variantConfiguration.getVersionCode();
                }
            });

            task.apkData = variantData.getOutputScope().getMainSplit();

            task.manifestFile = new File(getAppVariantOutputContext().getBundleManifestOutputDir(variantConfiguration,awbBundle),"AndroidManifest.xml");

            task.inputResourcesDir = getAppVariantOutputContext().getFeatureMergedResourceDir(variantConfiguration,awbBundle);

            task.resIdSupplier = new Supplier<Integer>() {
                @Override
                public Integer get() {
                    try {
                        return FeatureSetMetadata.load(
                                variantContext.getScope().getArtifacts().getFinalArtifactFiles(
                                        InternalArtifactType.FEATURE_SET_METADATA).get().getSingleFile()).getResOffsetFor(awbBundle.getName());

                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    return 0;
                }
            };

            task.debuggable = variantConfiguration.getBuildType().isDebuggable();
            task.aaptOptions = scope.getGlobalScope().getExtension().getAaptOptions();

            task.buildTargetDensity =
                    projectOptions.get(StringOption.IDE_BUILD_TARGET_DENSITY);

            task.mergeBlameLogFolder = getAppVariantOutputContext().getFeatureResourceBlameLogDir(variantConfiguration,awbBundle);
            task.aapt2FromMaven = Aapt2MavenUtils.getAapt2FromMaven(scope.getGlobalScope());
            task.minSdkVersion = scope.getMinSdkVersion().getApiLevel();

            task.resConfig =
                    scope.getVariantConfiguration().getMergedFlavor().getResourceConfigurations();



        }

        @Override
        public void preConfigure(@NotNull String s) {
            super.preConfigure(s);
            featureBundledResFile = getAppVariantOutputContext().geteBundledResFile(scope.getVariantConfiguration(),awbBundle);
        }

        @NotNull
        @Override
        public String getName() {
            return variantContext.getScope().getTaskName("bundleFeature"+awbBundle.getFeatureName(), "Resources");
        }

        @NotNull
        @Override
        public Class<BundleFeatureResourceTask> getType() {
            return BundleFeatureResourceTask.class;
        }
    }
}
