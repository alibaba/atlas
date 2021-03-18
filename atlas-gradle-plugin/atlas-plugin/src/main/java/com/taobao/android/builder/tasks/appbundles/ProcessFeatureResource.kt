package com.taobao.android.builder.tasks.appbundles

import com.android.SdkConstants
import com.android.build.VariantOutput
import com.android.build.api.artifact.BuildableArtifact
import com.android.build.gradle.AppExtension
import com.android.build.gradle.api.BaseVariantOutput
import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.TaskManager
import com.android.build.gradle.internal.api.AppVariantContext
import com.android.build.gradle.internal.api.ApplicationVariantImpl
import com.android.build.gradle.internal.api.VariantContext
import com.android.build.gradle.internal.api.artifact.singleFile
import com.android.build.gradle.internal.dsl.AaptOptions
import com.android.build.gradle.internal.dsl.convert
import com.android.build.gradle.internal.incremental.InstantRunBuildContext
import com.android.build.gradle.internal.incremental.InstantRunPatchingPolicy
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.res.LinkApplicationAndroidResourcesTask
import com.android.build.gradle.internal.res.getAapt2FromMaven
import com.android.build.gradle.internal.res.namespaced.Aapt2ServiceKey
import com.android.build.gradle.internal.res.namespaced.getAaptDaemon
import com.android.build.gradle.internal.res.namespaced.registerAaptService
import com.android.build.gradle.internal.res.rewriteLinkException
import com.android.build.gradle.internal.scope.*
import com.android.builder.internal.aapt.AaptPackageConfig
import com.android.build.gradle.internal.tasks.TaskInputHelper
import com.android.build.gradle.internal.tasks.Workers
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.tasks.featuresplit.FeatureSetMetadata
import com.android.build.gradle.internal.transforms.InstantRunSliceSplitApkBuilder
import com.android.build.gradle.internal.variant.BaseVariantData
import com.android.build.gradle.internal.variant.MultiOutputPolicy
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.StringOption
import com.android.build.gradle.tasks.ProcessAndroidResources
import com.android.builder.model.AndroidLibrary
import com.android.ide.common.blame.MergingLog
import com.android.ide.common.process.ProcessException
import com.android.ide.common.symbols.RGeneration
import com.android.ide.common.symbols.SymbolIo
import com.android.ide.common.symbols.SymbolTable
import com.android.ide.common.workers.WorkerExecutorException
import com.android.ide.common.workers.WorkerExecutorFacade
import com.android.sdklib.IAndroidTarget
import com.android.utils.FileUtils
import com.google.common.base.Preconditions
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import com.google.common.collect.Lists
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import com.taobao.android.builder.dependency.model.AwbBundle
import com.taobao.android.builder.extension.AtlasExtension
import com.taobao.android.builder.extension.TBuildConfig
import com.taobao.android.builder.extension.TBuildType
import com.taobao.android.builder.tasks.manager.MtlBaseTaskAction
import com.taobao.android.builder.tools.ReflectUtils
import com.taobao.android.builder.tools.manifest.ManifestFileUtils
import it.unimi.dsi.fastutil.Hash
import org.gradle.api.file.FileCollection
import org.gradle.api.logging.Logging
import org.gradle.api.tasks.*
import org.gradle.api.tasks.Optional
import org.gradle.tooling.BuildException
import org.gradle.workers.WorkerExecutor
import java.io.File
import java.io.IOException
import java.io.Serializable
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Supplier
import java.util.regex.Pattern
import javax.inject.Inject
import kotlin.Exception
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

/**
 * @ClassName ProcessFeatureResource
 * @Description TODO
 * @Author zhayu.ll
 * @Date 2019-08-23 11:51
 * @Version 1.0
 */
open class ProcessFeatureResource @Inject constructor(workerExecutor: WorkerExecutor) :
        ProcessAndroidResources() {


    private var featureName: String? = null

    public var textSymbolOutputDir: Supplier<File?> = Supplier { null }

    public var symbolsWithPackageNameOutputFile: File? = null

    public var dependenciesFileCollection: FileCollection? = null

    private var sharedLibraryDependencies: FileCollection? = null

    public var resOffsetSupplier: (Supplier<Int>)? = null

    public lateinit var multiOutputPolicy: MultiOutputPolicy

    private var mergedManifest: File? = null


    public var aapt2FromMaven: FileCollection? = null

    public var debuggable: Boolean = false

    public lateinit var aaptOptions: AaptOptions

    private var sourceOutputDir: File? = null

    private var scope: VariantScope? = null


    public lateinit var mergeBlameLogFolder: File

    public lateinit var buildContext: InstantRunBuildContext

    public var featureResourcePackages: FileCollection? = null

    lateinit var originalApplicationId: Supplier<String?>

    public var buildTargetDensity: String? = null


    public lateinit var resPackageOutputFolder: File


    private var isNamespaced = false

    public lateinit var splitList: SplitList

    public lateinit var applicationId: Supplier<String?>

    public lateinit var supportDirectory: File


    private var convertedLibraryDependencies: BuildableArtifact? = null

    var inputResourcesDir: File? = null

    public lateinit var variantScope: VariantScope

    public var isLibrary: Boolean = false

    private val workers: WorkerExecutorFacade = Workers.getWorker(workerExecutor)


    @Input
    fun getPatchingPolicy(): InstantRunPatchingPolicy {
        return buildContext.patchingPolicy
    }


    @Input
    fun getApplicationId(): String? {
        return applicationId.get()
    }


    @Optional
    @Input
    fun getResOffset(): Int? {
        return if (resOffsetSupplier != null) resOffsetSupplier!!.get() else null
    }


    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    @Optional
    fun getConvertedLibraryDependencies(): BuildableArtifact? {
        return convertedLibraryDependencies
    }

    // FIX-ME : make me incremental !
    override fun doFullTaskAction() {


        if (!resPackageOutputFolder.exists()){
            resPackageOutputFolder.mkdirs()
        }



        FileUtils.deleteDirectoryContents(resPackageOutputFolder)


        val featureResourcePackages = if (featureResourcePackages != null)
            featureResourcePackages!!.files
        else
            ImmutableSet.of()

        val dependencies = if (dependenciesFileCollection != null)
            dependenciesFileCollection!!.files
        else
            emptySet()
        val imports = if (sharedLibraryDependencies != null)
            sharedLibraryDependencies!!.files
        else
            emptySet()
        run {
            val aapt2ServiceKey = registerAaptService(
                    aapt2FromMaven, buildTools, iLogger
            )

            // do a first pass at the list so we generate the code synchronously since it's required
            // by the full splits asynchronous processing below.

            val properties = HashMap<String, String>()
            properties.put("packageId", scope?.variantConfiguration?.applicationId!!)
            properties.put("split", "")
            properties.put("minSdkVersion", scope?.variantConfiguration!!.minSdkVersion.toString())
            val mainOutput = BuildOutput(InternalArtifactType.MERGED_MANIFESTS,
                    ApkData.of(VariantOutput.OutputType.MAIN, ImmutableList.of(), -1, null, null, null, scope?.variantConfiguration!!.fullName, scope!!.variantConfiguration.fullName, true),
                    mergedManifest,
                    properties)
            AaptSplitInvoker(
                    AaptSplitInvokerParams(
                            mainOutput,
                            dependencies,
                            imports,
                            splitList,
                            featureResourcePackages,
                            mainOutput.apkData,
                            true,
                            aapt2ServiceKey,
                            this
                    )
            )
                    .run()

        }

        val apkData = ApkData.of(VariantOutput.OutputType.MAIN, ImmutableList.of(), variantScope.variantConfiguration.versionCode, variantScope.variantConfiguration.versionName, null, featureName, scope?.variantConfiguration!!.fullName, scope!!.variantConfiguration.fullName, true)

        val output = BuildOutput(
                InternalArtifactType.PROCESSED_RES,
                apkData,
                getOutputBaseNameFile(apkData,resPackageOutputFolder)
                )
        val buildOutputs = java.util.ArrayList(
                ExistingBuildElements.from(resPackageOutputFolder).elements
        )
        buildOutputs.add(output)
        BuildElements(buildOutputs).save(resPackageOutputFolder)

    }


    fun setSourceOutputDir(sourceOutputDir: File?) {
        this.sourceOutputDir = sourceOutputDir
    }


    override fun getSourceOutputDir(): File? {
        return sourceOutputDir
    }


    abstract class BaseCreationAction(
            val tempVariantContext: VariantContext<*, *, *>,
            val variantOutput: BaseVariantOutput,
            val awbBundle: AwbBundle
    ) : MtlBaseTaskAction<ProcessFeatureResource>(tempVariantContext, variantOutput) {
        private lateinit var resPackageOutputFolder: File

        override val name: String
            get() = variantScope.getTaskName("processFeature"+awbBundle.featureName, "Resources")

        override val type: Class<ProcessFeatureResource>
            get() = ProcessFeatureResource::class.java

        protected open fun preconditionsCheck(variantData: BaseVariantData) {}

        override fun preConfigure(taskName: String) {
            super.preConfigure(taskName)

            resPackageOutputFolder = appVariantOutputContext.getFeatureProcessResourcePackageOutputFile(awbBundle).parentFile


        }


        override fun configure(task: ProcessFeatureResource) {
            super.configure(task)
            val variantScope = variantScope
            task.scope = variantScope
            val variantData = variantScope.variantData
            val projectOptions = variantScope.globalScope.projectOptions
            val config = variantData.variantConfiguration

            preconditionsCheck(variantData)

            task.resPackageOutputFolder = resPackageOutputFolder
            task.aapt2FromMaven = getAapt2FromMaven(variantScope.globalScope)

            task.featureName = awbBundle.name

            task.applicationId = TaskInputHelper.memoize { awbBundle.packageName }

            task.incrementalFolder = appVariantOutputContext.getIncrementalDir(name, awbBundle)
//        if (variantData.type.canHaveSplits) {
            val splits = variantScope.globalScope.extension.splits

            val densitySet = if (splits.density.isEnable)
                ImmutableSet.copyOf(splits.densityFilters)
            else
                ImmutableSet.of()
            val languageSet = if (splits.language.isEnable)
                ImmutableSet.copyOf(splits.languageFilters)
            else
                ImmutableSet.of()
            val abiSet = if (splits.abi.isEnable)
                ImmutableSet.copyOf(splits.abiFilters)
            else
                ImmutableSet.of()
            val resConfigSet = ImmutableSet.copyOf(
                    variantScope
                            .variantConfiguration
                            .mergedFlavor
                            .resourceConfigurations
            )

            task.splitList = SplitList(densitySet, languageSet, abiSet, resConfigSet)


            task.multiOutputPolicy = variantData.multiOutputPolicy

            task.variantScope = variantScope

            ReflectUtils.updateField(task, "outputScope", variantData.outputScope)
            task.originalApplicationId = TaskInputHelper.memoize { awbBundle.packageName }

//        val aaptFriendlyManifestsFilePresent = variantScope
//                .artifacts
//                .hasFinalProduct(InternalArtifactType.AAPT_FRIENDLY_MERGED_MANIFESTS)
//        task.taskInputType = if (aaptFriendlyManifestsFilePresent)
//            InternalArtifactType.AAPT_FRIENDLY_MERGED_MANIFESTS
//        else
//            variantScope.manifestArtifactType
//        task.setManifestFiles(
//                variantScope.artifacts.getFinalProduct(task.taskInputType)
//        )

//        task.setType(config.type)
            task.debuggable = (config.buildType.isDebuggable)
            task.aaptOptions = (variantScope.globalScope.extension.aaptOptions)

            task.buildTargetDensity = projectOptions.get(StringOption.IDE_BUILD_TARGET_DENSITY)


            task.mergeBlameLogFolder = (appVariantOutputContext.getFeatureResourceBlameLogDir(config, awbBundle))

            task.buildContext = variantScope.instantRunBuildContext

//        val variantType = variantScope.type

            // Tests should not have feature dependencies, however because they include the
            // tested production component in their dependency graph, we see the tested feature
            // package in their graph. Therefore we have to manually not set this up for tests.
//        task.featureResourcePackages = if (variantType.isForTesting)
//            null
//        else
            task.featureResourcePackages = variantContext.project.files(scope.artifacts.getFinalArtifactFilesIfPresent(InternalArtifactType.PROCESSED_RES)?.get()!!.files)

//        if (variantType.isFeatureSplit) {
            task.resOffsetSupplier = Supplier {
                try {
                    return@Supplier FeatureSetMetadata.load(
                            variantContext.getScope().getArtifacts().getFinalArtifactFilesIfPresent(
                                    InternalArtifactType.FEATURE_SET_METADATA)!!.get().getSingleFile()).getResOffsetFor(awbBundle.name)
                } catch (e: IOException) {
                    e.printStackTrace()
                }

                0
            }
            task.isLibrary = false;
            task.supportDirectory = File(variantScope.instantRunSplitApkOutputFolder, "resources")
        }
    }

    class CreationAction(
            awbBundle: AwbBundle,
            variantContext: VariantContext<*, *, *>,
            baseVariantOutput: BaseVariantOutput
    ) : BaseCreationAction(variantContext, baseVariantOutput, awbBundle) {
        private var sourceOutputDir: File? = null

        override fun preconditionsCheck(variantData: BaseVariantData) {

        }

        override fun preConfigure(taskName: String) {
            super.preConfigure(taskName)
            sourceOutputDir = appVariantOutputContext.getFeatureRClassSourceOutputDir(variantScope.variantConfiguration, awbBundle)

        }

        override fun configure(task: ProcessFeatureResource) {
            super.configure(task)
            task.mergedManifest = File(appVariantOutputContext.getFeatureManifestOutputDir(variantScope.variantConfiguration, awbBundle),"AndroidManifest.xml")
            task.setSourceOutputDir(sourceOutputDir)

            val symbles = ArrayList<File>()
            awbBundle.androidLibraries.forEach {
                val packageName = ManifestFileUtils.getPackage(it.manifest)
                val out = appVariantOutputContext.getLibrarySymbolWithPackageName(packageName)
                out.parentFile.mkdirs()
                symbles.add(out)
               SymbolIo.writeSymbolListWithPackageName(it.symbolFile.toPath(),packageName,out.toPath())
            }

            if (symbles.size == 0){
                val  out = File(awbBundle.androidLibrary.symbolFile.parentFile,"1.txt")
                SymbolIo.writeSymbolListWithPackageName(File("aaaa").toPath(),awbBundle.packageName+".fake",out.toPath())
                symbles.add(out)
            }

            task.dependenciesFileCollection = variantContext.project.files(symbles)
            task.inputResourcesDir = appVariantOutputContext.getFeatureMergedResourceDir(variantScope.variantConfiguration, awbBundle);

            @Suppress("UNCHECKED_CAST")
            task.textSymbolOutputDir = Supplier {
                return@Supplier appVariantOutputContext.getFeatureSymbols(awbBundle)
            }
            task.symbolsWithPackageNameOutputFile = appVariantOutputContext.getSymbolsWithPackageNameOutputFile(awbBundle)
        }
    }


    private class AaptSplitInvoker @Inject
    internal constructor(private val params: AaptSplitInvokerParams) : Runnable {


        override fun run() {
            try {
                invokeAaptForSplit(params)
            } catch (e: IOException) {
                throw RuntimeException(e)
            }

        }


        private fun getOutputBaseNameFile(apkData: ApkData, resPackageOutputFolder: File): File {
            return File(
                    resPackageOutputFolder,
                    SdkConstants.FN_RES_BASE + SdkConstants.RES_QUALIFIER_SEP + apkData.fullName + SdkConstants.DOT_RES
            )
        }

        @Throws(IOException::class)
        private fun invokeAaptForSplit(params: AaptSplitInvokerParams) {

            val featurePackagesBuilder = ImmutableList.builder<File>()
            for (featurePackage in params.featureResourcePackages) {
                val buildElements = ExistingBuildElements.from(
                        InternalArtifactType.PROCESSED_RES, featurePackage
                )
                if (!buildElements.isEmpty()) {
                    val mainBuildOutput = buildElements.elementByType(VariantOutput.OutputType.MAIN)
                    if (mainBuildOutput != null) {
                        featurePackagesBuilder.add(mainBuildOutput.outputFile)
                    } else {
                        throw IOException(
                                "Cannot find PROCESSED_RES output for " + params.variantScopeMainSplit
                        )
                    }
                }
            }

            val resOutBaseNameFile =
                    getOutputBaseNameFile(params.apkData, params.resPackageOutputFolder)
            var manifestFile = params.manifestOutput.outputFile

            var packageForR: String? = null
            var srcOut: File? = null
            var symbolOutputDir: File? = null
            var proguardOutputFile: File? = null
            var mainDexListProguardOutputFile: File? = null
            if (params.generateCode) {
                // workaround for b/74068247. Until that's fixed, if it's a namespaced feature,
                // an extra empty dummy R.java file will be generated as well
                packageForR =
//                    if (params.isNamespaced && params.variantDataType === VariantTypeImpl.FEATURE) {
//                        "dummy"
//                    } else {
                        params.originalApplicationId
//                    }

                // we have to clean the source folder output in case the package name changed.
                srcOut = params.sourceOutputDir
                if (srcOut != null) {
                    FileUtils.cleanOutputDir(srcOut)
                }

                symbolOutputDir = params.textSymbolOutputDir
                symbolOutputDir?.mkdirs()
                proguardOutputFile = params.proguardOutputFile
                mainDexListProguardOutputFile = params.mainDexListProguardOutputFile
            }

            val densityFilterData = params.apkData.getFilter(VariantOutput.FilterType.DENSITY)
            // if resConfigs is set, we should not use our preferredDensity.
            val preferredDensity =
                    densityFilterData?.identifier
                            ?: if (params.resourceConfigs.isEmpty()) params.buildTargetDensity else null

            try {

                run {
                    val configBuilder = AaptPackageConfig.Builder()
                            .setManifestFile(manifestFile)
                            .setOptions(params.aaptOptions)
                            .setCustomPackageForR(packageForR)
                            .setSymbolOutputDir(symbolOutputDir)
                            .setSourceOutputDir(srcOut)
                            .setResourceOutputApk(resOutBaseNameFile)
                            .setProguardOutputFile(proguardOutputFile)
                            .setMainDexListProguardOutputFile(mainDexListProguardOutputFile)
                            .setVariantType(com.android.builder.core.VariantTypeImpl.OPTIONAL_APK)
                            .setDebuggable(params.debuggable)
                            .setResourceConfigs(params.resourceConfigs)
                            .setSplits(params.multiOutputPolicySplitList)
                            .setPreferredDensity(preferredDensity)
                            .setPackageId(params.packageId)
                            .setAllowReservedPackageId(
                                    params.packageId != null && params.packageId < FeatureSetMetadata.BASE_ID
                            )
                            .setDependentFeatures(featurePackagesBuilder.build())
                            .setImports(params.imports)
                            .setIntermediateDir(params.incrementalFolder)
                            .setAndroidJarPath(params.androidJarPath)
                            .setUseConditionalKeepRules(params.useConditionalKeepRules)


                    if (params.generateCode) {
                        configBuilder.setLibrarySymbolTableFiles(params.dependencies)
                    }
                    configBuilder.setResourceDir(checkNotNull(params.inputResourcesDir))


                    @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
                    Preconditions.checkNotNull<Aapt2ServiceKey>(
                            params.aapt2ServiceKey, "AAPT2 daemon manager service not initialized"
                    )
                    try {
                        getAaptDaemon(params.aapt2ServiceKey!!).use { aaptDaemon ->

                            com.android.builder.core.AndroidBuilder.processResources(
                                    aaptDaemon,
                                    configBuilder.build(),
                                    LoggerWrapper(
                                            Logging.getLogger(
                                                    ProcessFeatureResource::class.java
                                            )
                                    )
                            )
                            params.symbolsWithPackageNameOutputFile?.parentFile?.mkdirs()
                            SymbolIo.writeSymbolListWithPackageName(
                                    File(
                                            params.textSymbolOutputDir!!,
                                            SdkConstants.R_CLASS + SdkConstants.DOT_TXT
                                    )
                                            .toPath(),
                                    manifestFile.toPath(),
                                    params.symbolsWithPackageNameOutputFile?.toPath())

                            var symbolFile = params.task.variantScope.artifacts.getFinalArtifactFiles(InternalArtifactType.SYMBOL_LIST).get().singleFile

                            var symbolTable = SymbolIo.readFromAapt(symbolFile,params.applicationId)

                            var mergedSymbolTable = SymbolIo.readFromAapt(
                                    File(params.textSymbolOutputDir!!,
                                    SdkConstants.R_CLASS + SdkConstants.DOT_TXT
                            ),params.applicationId).merge(symbolTable)

                            params.applicationId?.let { mergedSymbolTable = mergedSymbolTable.rename(it) }

                            FileUtils.cleanOutputDir(srcOut)

                            SymbolIo.exportToJava(mergedSymbolTable,srcOut,true)

                             val depSymbolTables = com.android.ide.common.symbols.loadDependenciesSymbolTables(
                                     params.dependencies)

                            val finalIds = true

                            RGeneration.generateRForLibraries(mergedSymbolTable, depSymbolTables, srcOut, finalIds);


                        }
                    } catch (e: Exception) {
                        throw e

                    }


                }










            } catch (e: ProcessException) {
                throw BuildException(
                        "Failed to process resources, see aapt output above for details.", e
                )
            }

        }

    }


    private class AaptSplitInvokerParams internal constructor(
            val manifestOutput: BuildOutput,
            val dependencies: Set<File>,
            val imports: Set<File>,
            splitList: SplitList,
            val featureResourcePackages: Set<File>,
            val apkData: ApkData,
            val generateCode: Boolean,
            val aapt2ServiceKey: Aapt2ServiceKey?,
            task: ProcessFeatureResource
    ) : Serializable {
        val task:ProcessFeatureResource = task
        val resourceConfigs: Set<String> = splitList.resourceConfigs
        val multiOutputPolicySplitList: Set<String> = splitList.getSplits(task.multiOutputPolicy)
        val variantScopeMainSplit: ApkData = task.variantScope.outputScope.mainSplit
        val resPackageOutputFolder: File = task.resPackageOutputFolder
        val isNamespaced: Boolean = false
        val originalApplicationId: String? = task.originalApplicationId.get()
        val sourceOutputDir: File? = task.getSourceOutputDir()
        val textSymbolOutputDir: File? = task.textSymbolOutputDir.get()
        val proguardOutputFile: File? = null
        val mainDexListProguardOutputFile: File? = null
        val buildTargetDensity: String? = task.buildTargetDensity
        val applicationId: String? = task.applicationId.get()
        val aaptOptions: com.android.builder.internal.aapt.AaptOptions = task.aaptOptions.convert()
        val debuggable: Boolean = task.debuggable
        val packageId: Int? = task.resOffsetSupplier!!.get()
        val incrementalFolder: File = task.incrementalFolder
        val androidJarPath: String = task.builder.target.getPath(IAndroidTarget.ANDROID_JAR)
        val convertedLibraryDependenciesPath: Path? = if (task.convertedLibraryDependencies == null)
            null
        else
            task.convertedLibraryDependencies!!.singleFile().toPath()
        val inputResourcesDir: File? = task.inputResourcesDir;

        val mergeBlameFolder: File = task.mergeBlameLogFolder
        val isLibrary: Boolean = task.isLibrary
        val symbolsWithPackageNameOutputFile: File? = task.symbolsWithPackageNameOutputFile
        val useConditionalKeepRules: Boolean = false
    }


    /**
     * To force the task to execute when the manifest file to use changes.
     *
     *
     * Fix for [b.android.com/209985](http://b.android.com/209985).
     */
    @Input
    fun isInstantRunMode(): Boolean {
        return buildContext.isInInstantRunMode
    }


    @org.gradle.api.tasks.OutputFile
    @Optional
    fun getTextSymbolOutputFile(): File? {
        val outputDir = textSymbolOutputDir.get()
        return if (outputDir != null)
            File(outputDir, SdkConstants.R_CLASS + SdkConstants.DOT_TXT)
        else
            null
    }


    @Input
    fun getBuildToolsVersion(): String {
        return buildTools.revision.toString()
    }


    @InputFiles
    @Optional
    @PathSensitive(PathSensitivity.NONE)
    fun getSharedLibraryDependencies(): FileCollection? {
        return sharedLibraryDependencies
    }


    @Input
    fun getOriginalApplicationId(): String? {
        return originalApplicationId.get()
    }


    @Input
    fun isNamespaced(): Boolean {
        return false
    }

    private fun findPackagedResForSplit(outputFolder: File?, apkData: ApkData): File? {
        val resourcePattern = Pattern.compile(
                SdkConstants.FN_RES_BASE + SdkConstants.RES_QUALIFIER_SEP + apkData.fullName + ".ap__(.*)"
        )

        if (outputFolder == null) {
            return null
        }
        val files = outputFolder.listFiles()
        if (files != null) {
            for (file in files) {
                val match = resourcePattern.matcher(file.name)
                // each time we match, we remove the associated filter from our copies.
                if (match.matches()
                        && !match.group(1).isEmpty()
                        && isValidSplit(apkData, match.group(1))
                ) {
                    return file
                }
            }
        }
        return null
    }

    /**
     * Returns true if the passed split identifier is a valid identifier (valid mean it is a
     * requested split for this task). A density split identifier can be suffixed with characters
     * added by aapt.
     */
    private fun isValidSplit(apkData: ApkData, splitWithOptionalSuffix: String): Boolean {

        var splitFilter = apkData.getFilter(VariantOutput.FilterType.DENSITY)
        if (splitFilter != null) {
            if (splitWithOptionalSuffix.startsWith(splitFilter.identifier)) {
                return true
            }
        }
        val mangledName = unMangleSplitName(splitWithOptionalSuffix)
        splitFilter = apkData.getFilter(VariantOutput.FilterType.LANGUAGE)
        return splitFilter != null && mangledName == splitFilter.identifier
    }

    /**
     * Un-mangle a split name as created by the aapt tool to retrieve a split name as configured in
     * the project's build.gradle.
     *
     *
     * when dealing with several split language in a single split, each language (+ optional
     * region) will be separated by an underscore.
     *
     *
     * note that there is currently an aapt bug, remove the 'r' in the region so for instance,
     * fr-rCA becomes fr-CA, temporarily put it back until it is fixed.
     *
     * @param splitWithOptionalSuffix the mangled split name.
     */
    private fun unMangleSplitName(splitWithOptionalSuffix: String): String {
        val mangledName = splitWithOptionalSuffix.replace("_".toRegex(), ",")
        return if (mangledName.contains("-r")) mangledName else mangledName.replace("-", "-r")
    }


    private fun getOutputBaseNameFile(apkData: ApkData, resPackageOutputFolder: File): File {
        return File(
                resPackageOutputFolder,
                SdkConstants.FN_RES_BASE + SdkConstants.RES_QUALIFIER_SEP + apkData.fullName + SdkConstants.DOT_RES
        )
    }
}