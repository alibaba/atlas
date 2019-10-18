package com.taobao.android.builder.tasks.appbundles

import com.android.build.gradle.api.BaseVariantOutput
import com.android.build.gradle.internal.api.VariantContext
import com.android.build.gradle.internal.core.GradleVariantConfiguration
import com.android.build.gradle.internal.dsl.AaptOptions
import com.android.build.gradle.internal.dsl.convert
import com.android.build.gradle.internal.ide.AaptOptionsImpl
import com.android.build.gradle.internal.res.Aapt2ProcessResourcesRunnable
import com.android.build.gradle.internal.res.getAapt2FromMaven
import com.android.build.gradle.internal.res.namespaced.Aapt2ServiceKey
import com.android.build.gradle.internal.res.namespaced.registerAaptService
import com.android.build.gradle.internal.scope.ApkData
import com.android.build.gradle.internal.scope.ExistingBuildElements
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.AndroidBuilderTask
import com.android.build.gradle.internal.tasks.TaskInputHelper
import com.android.build.gradle.internal.tasks.Workers
import com.android.build.gradle.internal.tasks.featuresplit.FeatureSetMetadata
import com.android.build.gradle.internal.variant.BaseVariantData
import com.android.build.gradle.options.ProjectOptions
import com.android.build.gradle.options.StringOption
import com.android.builder.core.VariantType
import com.android.builder.core.VariantTypeImpl
import com.android.builder.internal.aapt.AaptPackageConfig
import com.android.ide.common.workers.ExecutorServiceAdapter
import com.android.ide.common.workers.WorkerExecutorFacade
import com.android.sdklib.IAndroidTarget
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import com.taobao.android.builder.dependency.model.AwbBundle
import com.taobao.android.builder.tasks.manager.MtlBaseTaskAction
import org.apache.commons.io.FileUtils
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskAction
import org.gradle.workers.WorkerExecutor

import javax.inject.Inject
import java.io.File
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.ForkJoinPool
import java.util.function.Supplier

/**
 * @ClassName BundleFeatureResource
 * @Description TODO
 * @Author zhayu.ll
 * @Date 2019-08-21 14:31
 * @Version 1.0
 */
open class BundleFeatureResourceTask @Inject
constructor(workers: WorkerExecutor) : AndroidBuilderTask() {

    private var featureBundledResFile: File? = null
    private var incrementalFolder: File? = null
    private var versionName: Provider<String>? = null
    private var versionCode: Provider<Int>? = null
    private var apkData: ApkData? = null
    private lateinit var manifestFile: File
    private var inputResourcesDir: File? = null
    private lateinit var resIdSupplier: Supplier<Int>
    private var debuggable: Boolean = false
    private lateinit var aaptOptions: AaptOptions
    private var buildTargetDensity: String? = null
    private var mergeBlameLogFolder: File? = null
    private var minSdkVersion: Int = 0

    private var resConfig: Collection<String>? = null

    private var aapt2FromMaven: FileCollection? = null

    private var baseVariantOutput: BaseVariantOutput? = null

    private var variantContext: VariantContext<*, *, *>? = null


    private val workers = Workers.getWorker(workers)





     fun taskAction() {
        try {
            FileUtils.forceMkdir(featureBundledResFile!!.parentFile)
        } catch (e: IOException) {
            e.printStackTrace()
        }


        val featurePackagesBuilder = ImmutableList.Builder<File>()

        featurePackagesBuilder.add(ExistingBuildElements.from(InternalArtifactType.PROCESSED_RES, variantContext!!.scope.artifacts.getFinalArtifactFiles(InternalArtifactType.PROCESSED_RES)).iterator().next().outputFile)

        val aaptPackageConfig = AaptPackageConfig(
                 androidJarPath = builder.target.getPath(IAndroidTarget.ANDROID_JAR),
                generateProtos = true,
                manifestFile = manifestFile,
                options = aaptOptions.convert(),
               resourceOutputApk = featureBundledResFile,
                variantType= VariantTypeImpl.BASE_APK,
                debuggable = debuggable,
                packageId = resIdSupplier.get(),
                allowReservedPackageId = true,
                dependentFeatures = featurePackagesBuilder.build(),
                resourceDirs = ImmutableList.of(inputResourcesDir),
                resourceConfigs = ImmutableSet.copyOf(resConfig!!))

        val aapt2ServiceKey = registerAaptService(
                aapt2FromMaven = aapt2FromMaven,
                buildToolInfo = null,
                logger = builder.logger
        )
        workers!!.submit(Aapt2ProcessResourcesRunnable::class.java, Aapt2ProcessResourcesRunnable.Params(aapt2ServiceKey, aaptPackageConfig))


         workers.await()
    }

    class CreationAction(private val awbBundle: AwbBundle, variantContext: VariantContext<*, *, *>, baseVariantOutput: BaseVariantOutput) : MtlBaseTaskAction<BundleFeatureResourceTask>(variantContext, baseVariantOutput) {


        private var featureBundledResFile: File? = null

        override val name: String
            get() = variantContext.scope.getTaskName("bundleFeature" + awbBundle.featureName, "Resources")

        override val type: Class<BundleFeatureResourceTask>
            get() = BundleFeatureResourceTask::class.java

        override fun configure(task: BundleFeatureResourceTask) {
            super.configure(task)
            val variantData = scope.variantData
            val projectOptions = scope.globalScope.projectOptions
            val variantConfiguration = scope.variantConfiguration
            task.featureBundledResFile = featureBundledResFile
            task.incrementalFolder = scope.getIncrementalDir(name)
            task.baseVariantOutput = baseVariantOutput
            task.variantContext = variantContext
            task.versionName = TaskInputHelper.memoizeToProvider(variantContext.project) { variantConfiguration.versionName }

            task.versionCode = TaskInputHelper.memoizeToProvider(variantContext.project) { variantConfiguration.versionCode }

            task.apkData = variantData.outputScope.mainSplit

            task.manifestFile = File(appVariantOutputContext!!.getBundleManifestOutputDir(variantConfiguration, awbBundle), "AndroidManifest.xml")

            task.inputResourcesDir = appVariantOutputContext!!.getFeatureMergedResourceDir(variantConfiguration, awbBundle)

            task.resIdSupplier = Supplier {
                try {
                    return@Supplier FeatureSetMetadata.load(
                            variantContext.scope.artifacts.getFinalArtifactFiles(
                                    InternalArtifactType.FEATURE_SET_METADATA).get().singleFile).getResOffsetFor(awbBundle.name)

                } catch (e: IOException) {
                    e.printStackTrace()
                }

                0
            }

            task.debuggable = variantConfiguration.buildType.isDebuggable
            task.aaptOptions = scope.globalScope.extension.aaptOptions

            task.buildTargetDensity = projectOptions.get(StringOption.IDE_BUILD_TARGET_DENSITY)

            task.mergeBlameLogFolder = appVariantOutputContext!!.getFeatureResourceBlameLogDir(variantConfiguration, awbBundle)
            task.aapt2FromMaven = getAapt2FromMaven(scope.globalScope)
            task.minSdkVersion = scope.minSdkVersion.apiLevel

            task.resConfig = scope.variantConfiguration.mergedFlavor.resourceConfigurations


        }

        override fun preConfigure(s: String) {
            super.preConfigure(s)
//            featureBundledResFile = variantScope.artifacts
//                    .appendArtifact(InternalArtifactType.LINKED_RES_FOR_BUNDLE,
//                            name,
//                            "bundled-res.ap_")
            featureBundledResFile = appVariantOutputContext!!.geteBundledResFile(scope.variantConfiguration, awbBundle)
        }
    }
}
