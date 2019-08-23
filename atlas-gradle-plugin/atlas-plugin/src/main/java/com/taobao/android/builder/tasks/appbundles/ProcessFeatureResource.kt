package com.taobao.android.builder.tasks.appbundles

import com.android.SdkConstants
import com.android.build.VariantOutput
import com.android.build.api.artifact.BuildableArtifact
import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.TaskManager
import com.android.build.gradle.internal.api.artifact.singleFile
import com.android.build.gradle.internal.dsl.AaptOptions
import com.android.build.gradle.internal.dsl.convert
import com.android.build.gradle.internal.incremental.InstantRunBuildContext
import com.android.build.gradle.internal.incremental.InstantRunPatchingPolicy
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.res.getAapt2FromMaven
import com.android.build.gradle.internal.res.namespaced.Aapt2ServiceKey
import com.android.build.gradle.internal.res.namespaced.getAaptDaemon
import com.android.build.gradle.internal.res.namespaced.registerAaptService
import com.android.build.gradle.internal.res.rewriteLinkException
import com.android.build.gradle.internal.scope.*
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
import com.android.ide.common.blame.MergingLog
import com.android.ide.common.process.ProcessException
import com.android.ide.common.symbols.SymbolIo
import com.android.ide.common.workers.WorkerExecutorException
import com.android.ide.common.workers.WorkerExecutorFacade
import com.android.sdklib.IAndroidTarget
import com.android.utils.FileUtils
import com.google.common.base.Preconditions
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import org.gradle.api.file.FileCollection
import org.gradle.api.logging.Logging
import org.gradle.api.tasks.*
import org.gradle.tooling.BuildException
import org.gradle.workers.WorkerExecutor
import java.io.File
import java.io.IOException
import java.io.Serializable
import java.nio.file.Files
import java.nio.file.Path
import java.util.ArrayList
import java.util.function.Supplier
import java.util.regex.Pattern
import javax.inject.Inject

/**
 * @ClassName ProcessFeatureResource
 * @Description TODO
 * @Author zhayu.ll
 * @Date 2019-08-23 11:51
 * @Version 1.0
 */
open class ProcessFeatureResource @Inject constructor(workerExecutor: WorkerExecutor) :
        ProcessAndroidResources() {

    companion object {
        private const val IR_APK_FILE_NAME = "resources"
        private val LOG = Logging.getLogger(ProcessFeatureResource::class.java)

        private fun getOutputBaseNameFile(apkData: ApkData, resPackageOutputFolder: File): File {
            return File(
                    resPackageOutputFolder,
                    SdkConstants.FN_RES_BASE + SdkConstants.RES_QUALIFIER_SEP + apkData.fullName + SdkConstants.DOT_RES
            )
        }
    }

    private var sourceOutputDir: File? = null

    private var textSymbolOutputDir: Supplier<File?> = Supplier { null }

    private var symbolsWithPackageNameOutputFile: File? = null

    private var proguardOutputFile: File? = null

    private var mainDexListProguardOutputFile: File? = null

    private var dependenciesFileCollection: FileCollection? = null
    private var sharedLibraryDependencies: FileCollection? = null

    private var resOffsetSupplier: (Supplier<Int>)? = null

    private lateinit var multiOutputPolicy: MultiOutputPolicy

    private lateinit var type: VariantType

    private var aapt2FromMaven: FileCollection? = null

    private var debuggable: Boolean = false

    private lateinit var aaptOptions: AaptOptions

    private lateinit var mergeBlameLogFolder: File

    private lateinit var buildContext: InstantRunBuildContext

    private var featureResourcePackages: FileCollection? = null

    private lateinit var originalApplicationId: Supplier<String?>

    private var buildTargetDensity: String? = null

    private var useConditionalKeepRules: Boolean = false

    private lateinit var resPackageOutputFolder: File

    private lateinit var projectBaseName: String

    private lateinit var taskInputType: InternalArtifactType

    private var isNamespaced = false

    private lateinit var splitList: SplitList

    private lateinit var applicationId: Supplier<String?>

    private lateinit var supportDirectory: File

    private lateinit var apkList: BuildableArtifact

    private var convertedLibraryDependencies: BuildableArtifact? = null

    private var inputResourcesDir: BuildableArtifact? = null

    private lateinit var variantScope: VariantScope

    private var isLibrary: Boolean = false

    private val workers: WorkerExecutorFacade = Workers.getWorker(workerExecutor)

    @Input
    fun getTaskInputType(): InternalArtifactType? {
        return taskInputType
    }

    @Input
    fun getUseConditionalKeepRules(): Boolean {
        return useConditionalKeepRules
    }

    @Input
    fun getPatchingPolicy(): InstantRunPatchingPolicy {
        return buildContext.patchingPolicy
    }

    @Input
    fun getProjectBaseName(): String? {
        return projectBaseName
    }

    @Input
    fun getApplicationId(): String? {
        return applicationId.get()
    }

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    fun getApkList(): BuildableArtifact? {
        return apkList
    }

    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    @Optional
    fun getConvertedLibraryDependencies(): BuildableArtifact? {
        return convertedLibraryDependencies
    }

    // FIX-ME : make me incremental !
    override fun doFullTaskAction() {
        FileUtils.deleteDirectoryContents(resPackageOutputFolder)

        val manifestBuildElements = ExistingBuildElements.from(taskInputType, manifestFiles)

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
            val unprocessedManifest = manifestBuildElements.toMutableList()

            val mainOutput = chooseOutput(manifestBuildElements)

            unprocessedManifest.remove(mainOutput)
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

    }
}

private fun chooseOutput(manifestBuildElements: BuildElements): BuildOutput {
    when (multiOutputPolicy) {
        MultiOutputPolicy.SPLITS -> {
            val main = manifestBuildElements
                    .stream()
                    .filter { output -> output.apkData.type == VariantOutput.OutputType.MAIN }
                    .findFirst()
            if (!main.isPresent) {
                throw RuntimeException("No main apk found")
            }
            return main.get()
        }
        MultiOutputPolicy.MULTI_APK -> {
            val nonDensity = manifestBuildElements
                    .stream()
                    .filter { output ->
                        output.apkData
                                .getFilter(
                                        VariantOutput.FilterType
                                                .DENSITY
                                ) == null
                    }
                    .findFirst()
            if (!nonDensity.isPresent) {
                throw RuntimeException("No non-density apk found")
            }
            return nonDensity.get()
        }

    }
}

abstract class BaseCreationAction(
        scope: VariantScope,
        private val generateLegacyMultidexMainDexProguardRules: Boolean,
        private val baseName: String?,
        private val isLibrary: Boolean
) : VariantTaskCreationAction<ProcessFeatureResource>(scope) {
    private lateinit var resPackageOutputFolder: File
    private lateinit var proguardOutputFile: File
    private lateinit var aaptMainDexListProguardOutputFile: File

    override val name: String
        get() = variantScope.getTaskName("process", "Resources")

    override val type: Class<ProcessFeatureResource>
        get() = ProcessFeatureResource::class.java

    protected open fun preconditionsCheck(variantData: BaseVariantData) {}

    override fun preConfigure(taskName: String) {
        super.preConfigure(taskName)
        val variantScope = variantScope

        resPackageOutputFolder = variantScope
                .artifacts
                .appendArtifact(InternalArtifactType.PROCESSED_RES, taskName, "out")

        if (ProcessAndroidResources.generatesProguardOutputFile(variantScope)) {
            proguardOutputFile = variantScope.processAndroidResourcesProguardOutputFile
            variantScope
                    .artifacts
                    .appendArtifact(
                            InternalArtifactType.AAPT_PROGUARD_FILE,
                            ImmutableList.of(proguardOutputFile),
                            taskName
                    )
        }

        if (generateLegacyMultidexMainDexProguardRules) {
            aaptMainDexListProguardOutputFile = variantScope
                    .artifacts
                    .appendArtifact(
                            InternalArtifactType
                                    .LEGACY_MULTIDEX_AAPT_DERIVED_PROGUARD_RULES,
                            taskName,
                            "manifest_keep.txt"
                    )
        }
    }

    override fun handleProvider(
            taskProvider: TaskProvider<out LinkApplicationAndroidResourcesTask>
    ) {
        super.handleProvider(taskProvider)
        variantScope.taskContainer.processAndroidResTask = taskProvider
    }

    override fun configure(task: LinkApplicationAndroidResourcesTask) {
        super.configure(task)
        val variantScope = variantScope
        val variantData = variantScope.variantData
        val projectOptions = variantScope.globalScope.projectOptions
        val config = variantData.variantConfiguration

        preconditionsCheck(variantData)

        task.resPackageOutputFolder = resPackageOutputFolder
        task.aapt2FromMaven = getAapt2FromMaven(variantScope.globalScope)

        task.applicationId = TaskInputHelper.memoize { config.applicationId }

        task.incrementalFolder = variantScope.getIncrementalDir(name)
        if (variantData.type.canHaveSplits) {
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
        } else {
            task.splitList = SplitList(
                    ImmutableSet.of(),
                    ImmutableSet.of(),
                    ImmutableSet.of(),
                    ImmutableSet.of()
            )
        }

        task.multiOutputPolicy = variantData.multiOutputPolicy
        task.apkList = variantScope
                .artifacts
                .getFinalArtifactFiles(InternalArtifactType.APK_LIST)

        if (ProcessAndroidResources.generatesProguardOutputFile(variantScope)) {
            task.setProguardOutputFile(proguardOutputFile)
        }

        if (generateLegacyMultidexMainDexProguardRules) {
            task.setAaptMainDexListProguardOutputFile(aaptMainDexListProguardOutputFile)
        }

        task.variantScope = variantScope
        task.outputScope = variantData.outputScope
        task.originalApplicationId = TaskInputHelper.memoize { config.originalApplicationId }

        val aaptFriendlyManifestsFilePresent = variantScope
                .artifacts
                .hasFinalProduct(InternalArtifactType.AAPT_FRIENDLY_MERGED_MANIFESTS)
        task.taskInputType = if (aaptFriendlyManifestsFilePresent)
            InternalArtifactType.AAPT_FRIENDLY_MERGED_MANIFESTS
        else
            variantScope.manifestArtifactType
        task.setManifestFiles(
                variantScope.artifacts.getFinalProduct(task.taskInputType)
        )

        task.setType(config.type)
        task.setDebuggable(config.buildType.isDebuggable)
        task.setAaptOptions(variantScope.globalScope.extension.aaptOptions)

        task.buildTargetDensity = projectOptions.get(StringOption.IDE_BUILD_TARGET_DENSITY)

        task.useConditionalKeepRules = projectOptions.get(BooleanOption.CONDITIONAL_KEEP_RULES)

        task.setMergeBlameLogFolder(variantScope.resourceBlameLogDir)

        task.buildContext = variantScope.instantRunBuildContext

        val variantType = variantScope.type

        // Tests should not have feature dependencies, however because they include the
        // tested production component in their dependency graph, we see the tested feature
        // package in their graph. Therefore we have to manually not set this up for tests.
        task.featureResourcePackages = if (variantType.isForTesting)
            null
        else
            variantScope.getArtifactFileCollection(
                    AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH, AndroidArtifacts.ArtifactScope.MODULE, AndroidArtifacts.ArtifactType.FEATURE_RESOURCE_PKG
            )

        if (variantType.isFeatureSplit) {
            task.resOffsetSupplier = FeatureSetMetadata.getInstance()
                    .getResOffsetSupplierForTask(variantScope, task)
        }

        task.projectBaseName = baseName!!
        task.isLibrary = isLibrary
        task.supportDirectory = File(variantScope.instantRunSplitApkOutputFolder, "resources")
    }
}

class CreationAction(
        scope: VariantScope,
        private val symbolLocation: Supplier<File>,
        private val symbolsWithPackageNameOutputFile: File,
        generateLegacyMultidexMainDexProguardRules: Boolean,
        private val sourceArtifactType: TaskManager.MergeType,
        baseName: String,
        isLibrary: Boolean
) : BaseCreationAction(scope, generateLegacyMultidexMainDexProguardRules, baseName, isLibrary) {
    private var sourceOutputDir: File? = null

    override fun preconditionsCheck(variantData: BaseVariantData) {
        if (variantData.type.isAar) {
            throw IllegalArgumentException("Use GenerateLibraryRFileTask")
        } else {
            Preconditions.checkState(
                    sourceArtifactType === TaskManager.MergeType.MERGE,
                    "source output type should be MERGE",
                    sourceArtifactType
            )
        }
    }

    override fun preConfigure(taskName: String) {
        super.preConfigure(taskName)
        sourceOutputDir = variantScope
                .artifacts
                .appendArtifact(
                        InternalArtifactType.NOT_NAMESPACED_R_CLASS_SOURCES,
                        taskName,
                        SdkConstants.FD_RES_CLASS
                )
    }

    override fun configure(task: ProcessFeatureResource) {
        super.configure(task)

        task.setSourceOutputDir(sourceOutputDir)

        task.dependenciesFileCollection = variantScope
                .getArtifactFileCollection(
                        AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                        AndroidArtifacts.ArtifactScope.ALL,
                        AndroidArtifacts.ArtifactType.SYMBOL_LIST_WITH_PACKAGE_NAME
                )

        task.inputResourcesDir = variantScope
                .artifacts
                .getFinalArtifactFiles(sourceArtifactType.outputType)

        @Suppress("UNCHECKED_CAST")
        task.textSymbolOutputDir = symbolLocation as Supplier<File?>
        task.symbolsWithPackageNameOutputFile = symbolsWithPackageNameOutputFile
    }
}


private class AaptSplitInvoker @Inject
internal constructor(private val params: AaptSplitInvokerParams) : Runnable {

    companion object {
        @Synchronized
        @Throws(IOException::class)
        fun appendOutput(
                output: BuildOutput, resPackageOutputFolder: File
        ) {
            val buildOutputs = ArrayList(
                    ExistingBuildElements.from(resPackageOutputFolder).elements
            )
            buildOutputs.add(output)
            BuildElements(buildOutputs).save(resPackageOutputFolder)
        }
    }

    override fun run() {
        try {
            invokeAaptForSplit(params)
        } catch (e: IOException) {
            throw RuntimeException(e)
        }

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
                    if (params.isNamespaced && params.variantDataType === VariantTypeImpl.FEATURE) {
                        "dummy"
                    } else {
                        params.originalApplicationId
                    }

            // we have to clean the source folder output in case the package name changed.
            srcOut = params.sourceOutputDir
            if (srcOut != null) {
                FileUtils.cleanOutputDir(srcOut)
            }

            symbolOutputDir = params.textSymbolOutputDir
            proguardOutputFile = params.proguardOutputFile
            mainDexListProguardOutputFile = params.mainDexListProguardOutputFile
        }

        val densityFilterData = params.apkData.getFilter(VariantOutput.FilterType.DENSITY)
        // if resConfigs is set, we should not use our preferredDensity.
        val preferredDensity =
                densityFilterData?.identifier
                        ?: if (params.resourceConfigs.isEmpty()) params.buildTargetDensity else null

        try {

            // If we are in instant run mode and we use a split APK for these resources.
            if (params.isInInstantRunMode && params.patchingPolicy == InstantRunPatchingPolicy.MULTI_APK_SEPARATE_RESOURCES) {
                params.supportDirectory.mkdirs()
                // create a split identification manifest.
                manifestFile = InstantRunSliceSplitApkBuilder.generateSplitApkManifest(
                        params.supportDirectory,
                        IR_APK_FILE_NAME,
                        { params.applicationId },
                        params.apkData.versionName,
                        params.apkData.versionCode,
                        params.manifestOutput.properties[SdkConstants.ATTR_MIN_SDK_VERSION]
                )
            }

            // If the new resources flag is enabled and if we are dealing with a library process
            // resources through the new parsers
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
                        .setVariantType(params.variantType)
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

                if (params.isNamespaced) {
                    val packagedDependencies = ImmutableList.builder<File>()
                    packagedDependencies.addAll(params.dependencies)
                    if (params.convertedLibraryDependenciesPath != null) {
                        Files.list(params.convertedLibraryDependenciesPath).map { it.toFile() }
                                .forEach { packagedDependencies.add(it) }
                    }
                    configBuilder.setStaticLibraryDependencies(packagedDependencies.build())
                } else {
                    if (params.generateCode) {
                        configBuilder.setLibrarySymbolTableFiles(params.dependencies)
                    }
                    configBuilder.setResourceDir(checkNotNull(params.inputResourcesDir))
                }

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
                    }
                } catch (e: Aapt2Exception) {
                    throw rewriteLinkException(
                            e, MergingLog(params.mergeBlameFolder)
                    )
                }

                if (LOG.isInfoEnabled) {
                    LOG.info("Aapt output file {}", resOutBaseNameFile.absolutePath)
                }
            }
            if (params.generateCode
                    && (params.isLibrary || !params.dependencies.isEmpty())
                    && params.symbolsWithPackageNameOutputFile != null
            ) {
                SymbolIo.writeSymbolListWithPackageName(
                        File(
                                params.textSymbolOutputDir!!,
                                SdkConstants.R_CLASS + SdkConstants.DOT_TXT
                        )
                                .toPath(),
                        manifestFile.toPath(),
                        params.symbolsWithPackageNameOutputFile.toPath()
                )
            }
            appendOutput(
                    BuildOutput(
                            InternalArtifactType.PROCESSED_RES,
                            params.apkData,
                            resOutBaseNameFile,
                            params.manifestOutput.properties
                    ),
                    params.resPackageOutputFolder
            )
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
        task: LinkApplicationAndroidResourcesTask
) : Serializable {
    val resourceConfigs: Set<String> = splitList.resourceConfigs
    val multiOutputPolicySplitList: Set<String> = splitList.getSplits(task.multiOutputPolicy)
    val variantScopeMainSplit: ApkData = task.variantScope.outputScope.mainSplit
    val resPackageOutputFolder: File = task.resPackageOutputFolder
    val isNamespaced: Boolean = task.isNamespaced
    val variantDataType: VariantType = task.variantScope.variantData.type
    val originalApplicationId: String? = task.originalApplicationId.get()
    val sourceOutputDir: File? = task.getSourceOutputDir()
    val textSymbolOutputDir: File? = task.textSymbolOutputDir.get()
    val proguardOutputFile: File? = task.getProguardOutputFile()
    val mainDexListProguardOutputFile: File? = task.getMainDexListProguardOutputFile()
    val buildTargetDensity: String? = task.buildTargetDensity
    val isInInstantRunMode: Boolean = task.buildContext.isInInstantRunMode
    val patchingPolicy: InstantRunPatchingPolicy = task.buildContext.patchingPolicy
    val supportDirectory: File = task.supportDirectory
    val applicationId: String? = task.applicationId.get()
    val aaptOptions: com.android.builder.internal.aapt.AaptOptions = task.aaptOptions.convert()
    val variantType: VariantType = task.getType()
    val debuggable: Boolean = task.getDebuggable()
    val packageId: Int? = task.getResOffset()
    val incrementalFolder: File = task.incrementalFolder
    val androidJarPath: String = task.builder.target.getPath(IAndroidTarget.ANDROID_JAR)
    val convertedLibraryDependenciesPath: Path? = if (task.convertedLibraryDependencies == null)
        null
    else
        task.convertedLibraryDependencies!!.singleFile().toPath()
    val inputResourcesDir: File? = if (task.getInputResourcesDir() == null)
        null
    else
        task.inputResourcesDir!!.singleFile()
    val mergeBlameFolder: File = task.getMergeBlameLogFolder()
    val isLibrary: Boolean = task.isLibrary
    val symbolsWithPackageNameOutputFile: File? = task.symbolsWithPackageNameOutputFile
    val useConditionalKeepRules: Boolean = task.useConditionalKeepRules
}

@Optional
@Input
fun getResOffset(): Int? {
    return if (resOffsetSupplier != null) resOffsetSupplier!!.get() else null
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

@InputFiles
@Optional
@PathSensitive(PathSensitivity.RELATIVE)
fun getInputResourcesDir(): BuildableArtifact? {
    return inputResourcesDir
}

@OutputDirectory
@Optional
override fun getSourceOutputDir(): File? {
    return sourceOutputDir
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

@org.gradle.api.tasks.OutputFile
@Optional
fun getSymbolsWithPackageNameOutputFile(): File? {
    return symbolsWithPackageNameOutputFile
}

@org.gradle.api.tasks.OutputFile
@Optional
fun getProguardOutputFile(): File? {
    return proguardOutputFile
}

fun setProguardOutputFile(proguardOutputFile: File) {
    this.proguardOutputFile = proguardOutputFile
}

@org.gradle.api.tasks.OutputFile
@Optional
fun getMainDexListProguardOutputFile(): File? {
    return mainDexListProguardOutputFile
}

fun setAaptMainDexListProguardOutputFile(mainDexListProguardOutputFile: File) {
    this.mainDexListProguardOutputFile = mainDexListProguardOutputFile
}

@Input
fun getBuildToolsVersion(): String {
    return buildTools.revision.toString()
}

@InputFiles
@Optional
@PathSensitive(PathSensitivity.NONE)
fun getDependenciesFileCollection(): FileCollection? {
    return dependenciesFileCollection
}

@InputFiles
@Optional
@PathSensitive(PathSensitivity.NONE)
fun getSharedLibraryDependencies(): FileCollection? {
    return sharedLibraryDependencies
}

@Input
fun getTypeAsString(): String {
    return type.name
}

@Internal
fun getType(): VariantType {
    return type
}

fun setType(type: VariantType) {
    this.type = type
}

fun setSourceOutputDir(sourceOutputDir: File?) {
    this.sourceOutputDir = sourceOutputDir
}

@InputFiles
@Optional
@PathSensitive(PathSensitivity.RELATIVE)
fun getAapt2FromMaven(): FileCollection? {
    return aapt2FromMaven
}

@Input
fun getDebuggable(): Boolean {
    return debuggable
}

fun setDebuggable(debuggable: Boolean) {
    this.debuggable = debuggable
}

@Nested
fun getAaptOptions(): AaptOptions {
    return aaptOptions
}

fun setAaptOptions(aaptOptions: AaptOptions) {
    this.aaptOptions = aaptOptions
}

/** Only used for rewriting error messages. Should not affect task result.  */
@Internal
fun getMergeBlameLogFolder(): File {
    return mergeBlameLogFolder
}

fun setMergeBlameLogFolder(mergeBlameLogFolder: File) {
    this.mergeBlameLogFolder = mergeBlameLogFolder
}

@InputFiles
@Optional
@PathSensitive(PathSensitivity.RELATIVE)
fun getFeatureResourcePackages(): FileCollection? {
    return featureResourcePackages
}

@Input
fun getMultiOutputPolicy(): MultiOutputPolicy {
    return multiOutputPolicy
}

@Input
fun getOriginalApplicationId(): String? {
    return originalApplicationId.get()
}

@Nested
@Optional
fun getSplitListInput(): SplitList {
    return splitList
}

@Input
@Optional
fun getBuildTargetDensity(): String? {
    return buildTargetDensity
}

@OutputDirectory
fun getResPackageOutputFolder(): File {
    return resPackageOutputFolder
}

@Input
fun isLibrary(): Boolean {
    return isLibrary
}

@Input
fun isNamespaced(): Boolean {
    return isNamespaced
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
}