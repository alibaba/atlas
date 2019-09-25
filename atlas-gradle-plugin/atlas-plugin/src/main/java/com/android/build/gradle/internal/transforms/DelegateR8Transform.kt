package com.android.build.gradle.internal.transforms

import com.android.build.api.transform.Format
import com.android.build.api.transform.QualifiedContent
import com.android.build.api.transform.SecondaryFile
import com.android.build.api.transform.TransformInvocation
import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.PostprocessingFeatures
import com.android.build.gradle.internal.api.AppVariantContext
import com.android.build.gradle.internal.api.AppVariantOutputContext
import com.android.build.gradle.internal.pipeline.TransformManager
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.options.BooleanOption
import com.android.builder.core.VariantType
import com.android.builder.dexing.DexingType
import com.android.builder.dexing.R8OutputType
import com.android.builder.dexing.runR8
import com.android.ide.common.blame.MessageReceiver
import com.taobao.android.builder.AtlasBuildContext
import com.taobao.android.builder.tools.multidex.mutli.MainDexLister
import org.apache.commons.io.FileUtils
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.provider.DefaultProvider
import org.gradle.api.provider.Provider

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.function.Consumer

/**
 * @ClassName DelegateR8Transform
 * @Description TODO
 * @Author zhayu.ll
 * @Date 2019-09-16 10:10
 * @Version 1.0
 */

class DelegateR8Transform(
        private val variantContext: AppVariantContext<*, *, *>,
        private val appVariantOutputContext: AppVariantOutputContext,
        private val bootClasspath: Lazy<List<File>>,
        private val minSdkVersion: Int,
        private val isDebuggable: Boolean,
        private val java8Support: VariantScope.Java8LangSupport,
        private var disableTreeShaking: Boolean,
        private var disableMinification: Boolean,
        private var mainDexListFiles: FileCollection,
        private val mainDexRulesFiles: FileCollection,
        private val inputProguardMapping: FileCollection,
        private val outputProguardMapping: File,
        proguardConfigurationFiles: ConfigurableFileCollection,
        variantType: VariantType,
        includeFeaturesInScopes: Boolean,
        private val messageReceiver: MessageReceiver,
        private val dexingType: DexingType,
        private val useFullR8: Boolean = false
) :
        ProguardConfigurable(proguardConfigurationFiles, variantType, includeFeaturesInScopes) {

    lateinit var taskName: String
    // This is a huge sledgehammer, but it is necessary until http://b/72683872 is fixed.
    private val proguardConfigurations: MutableList<String> = mutableListOf("-ignorewarnings")

    lateinit var r8Transform: R8Transform

    var mainDexListOutput: File? = null

    constructor(
            variantContext: AppVariantContext<*, *, *>,
            variantOutputContext: AppVariantOutputContext,
            scope: VariantScope,
            mainDexListFiles: FileCollection,
            mainDexRulesFiles: FileCollection,
            inputProguardMapping: FileCollection,
            outputProguardMapping: File
    ) :
            this(
                    variantContext,
                    variantOutputContext,
                    lazy { scope.globalScope.androidBuilder.getBootClasspath(true) },
                    scope.minSdkVersion.featureLevel,
                    scope.variantConfiguration.buildType.isDebuggable,
                    scope.java8LangSupportType,
                    false,
                    false,
                    mainDexListFiles,
                    mainDexRulesFiles,
                    inputProguardMapping,
                    outputProguardMapping,
                    scope.globalScope.project.files(),
                    scope.variantData.type,
                    scope.consumesFeatureJars(),
                    scope.globalScope.messageReceiver,
                    scope.dexingType,
                    scope.globalScope.projectOptions[BooleanOption.FULL_R8]
            )

    override fun getName(): String = "delegateR8"

    override fun getInputTypes(): MutableSet<out QualifiedContent.ContentType> = TransformManager.CONTENT_JARS

    override fun getOutputTypes(): MutableSet<out QualifiedContent.ContentType> {

        return TransformManager.CONTENT_DEX_WITH_RESOURCES

    }

    override fun isIncremental(): Boolean = false

    override fun getSecondaryFiles(): MutableCollection<SecondaryFile> =
            mutableListOf(
                    SecondaryFile.nonIncremental(allConfigurationFiles),
                    SecondaryFile.nonIncremental(mainDexListFiles),
                    SecondaryFile.nonIncremental(mainDexRulesFiles),
                    SecondaryFile.nonIncremental(inputProguardMapping)
            )

    override fun getParameterInputs(): MutableMap<String, Any> =
            mutableMapOf(
                    "minSdkVersion" to minSdkVersion,
                    "isDebuggable" to isDebuggable,
                    "disableTreeShaking" to disableTreeShaking,
                    "java8Support" to (java8Support == VariantScope.Java8LangSupport.R8),
                    "disableMinification" to disableMinification,
                    "proguardConfiguration" to proguardConfigurations,
                    "fullMode" to useFullR8,
                    "dexingType" to dexingType
            )

    override fun getSecondaryFileOutputs(): MutableCollection<File> =
            listOfNotNull(outputProguardMapping, mainDexListOutput).toMutableList()

    override fun keep(keep: String) {
        proguardConfigurations.add("-keep $keep")
    }

    override fun keepattributes() {
        proguardConfigurations.add("-keepattributes *")
    }

    override fun dontwarn(dontwarn: String) {
        proguardConfigurations.add("-dontwarn $dontwarn")
    }

    override fun setActions(actions: PostprocessingFeatures) {
        disableTreeShaking = !actions.isRemoveUnusedCode
        disableMinification = !actions.isObfuscate
        if (!actions.isOptimize) {
            proguardConfigurations.add("-dontoptimize")
        }
    }

    override fun transform(transformInvocation: TransformInvocation) {


        val outputProvider = requireNotNull(
                transformInvocation.outputProvider,
                { "No output provider set" }
        )
        outputProvider.deleteAll()

        val r8OutputType: com.android.builder.dexing.R8OutputType
        val outputFormat: Format

        r8OutputType = R8OutputType.DEX
        outputFormat = Format.DIRECTORY

        val enableDesugaring = java8Support == VariantScope.Java8LangSupport.R8
                && r8OutputType == R8OutputType.DEX
        val toolConfig = com.android.builder.dexing.ToolConfig(
                minSdkVersion = minSdkVersion,
                isDebuggable = isDebuggable,
                disableTreeShaking = disableTreeShaking,
                disableDesugaring = !enableDesugaring,
                disableMinification = disableMinification,
                r8OutputType = r8OutputType
        )

        val proguardMappingInput =
                if (inputProguardMapping.isEmpty) null else inputProguardMapping.singleFile.toPath()

         var allConfigurationFiles = mutableListOf<Path>()

        if (variantContext.atlasExtension.tBuildConfig.isEnabledAllProguardRules) {
            allConfigurationFiles = r8Transform.allConfigurationFiles.files.map { it.toPath() } as MutableList<Path>
        }else{
            allConfigurationFiles = providerProguardRules()
        }

        val proguardConfig = com.android.builder.dexing.ProguardConfig(
                allConfigurationFiles,
                outputProguardMapping.toPath(),
                proguardMappingInput,
                proguardConfigurations
        )


        val inputJavaResources = mutableListOf<Path>()
        val inputClasses = mutableListOf<Path>()
        AtlasBuildContext.atlasMainDexHelperMap.get(variantContext.variantName)!!.allMainDexJars.forEach {

            inputClasses.add(it.toPath())


        }
        AtlasBuildContext.atlasMainDexHelperMap.get(variantContext.variantName)!!.inputDirs.forEach {

            inputClasses.add(it.toPath())

        }

        appVariantOutputContext.awbTransformMap.values.filter { it.awbBundle.dynamicFeature }.forEach(Consumer { inputClasses.add(it.awbBundle.mergeJarFile.toPath()) })


        val javaResources =
                outputProvider.getContentLocation("java_res", setOf(QualifiedContent.DefaultContentType.RESOURCES), scopes, Format.JAR)
        Files.createDirectories(javaResources.toPath().parent)

        val bootClasspathInputs =
                TransformInputUtil.getAllFiles(transformInvocation.referencedInputs) + bootClasspath.value

        inputClasses.forEach(Consumer { variantContext.project.logger.warn("input File:" + it) })



       val  mainDexListConfig = if (dexingType == DexingType.LEGACY_MULTIDEX) {
           mainDexListFiles = variantContext.project.files(mainDexListProvider(inputClasses))
           com.android.builder.dexing.MainDexListConfig(
                    mainDexRulesFiles.files.map { it.toPath() },
                    mainDexListFiles.files.map { it.toPath() },
                   getPlatformRules(),
                    mainDexListOutput?.toPath()
            )
        } else {
         com.android.builder.dexing.MainDexListConfig()
        }

        val output = outputProvider.getContentLocation(
                "main",
                TransformManager.CONTENT_DEX,
                scopes,
                outputFormat
        )

        when (outputFormat) {
            Format.JAR -> Files.createDirectories(output.parentFile.toPath())
            Format.DIRECTORY -> Files.createDirectories(output.toPath())
        }



        runR8(
                inputClasses,
                output.toPath(),
                inputJavaResources,
                javaResources.toPath(),
                bootClasspathInputs.map { it.toPath() },
                toolConfig,
                proguardConfig,
                mainDexListConfig,
                messageReceiver,
                useFullR8
        )


    }

    private fun providerProguardRules(): MutableList<Path> {

         val defaultProguardFiles = mutableListOf<Path>()
        defaultProguardFiles.addAll(variantContext.variantData.variantConfiguration.buildType.proguardFiles.map { it.toPath() })
        val blackList = variantContext.atlasExtension.getTBuildConfig()
                    .bundleProguardConfigBlackList

            val proguardFiles = java.util.ArrayList<File>()
            val variantScope = variantContext.scope
            for (awbBundle in AtlasBuildContext.androidDependencyTrees.get(variantScope.variantConfiguration.fullName)?.getAwbBundles()!!) {
                for (androidDependency in awbBundle.allLibraryAars) {
                    val proguardRules = androidDependency.proguardRules

                    val groupName = androidDependency.resolvedCoordinates.groupId + ":" + androidDependency
                            .resolvedCoordinates.artifactId
                    if (blackList.contains(groupName)) {
                        variantContext.project.logger.info("[proguard] skip proguard from " + androidDependency.resolvedCoordinates)
                        continue
                    }

                    if (proguardRules.isFile) {
                        proguardFiles.add(proguardRules)
                        variantContext.project.logger.warn("[proguard] load proguard from " + androidDependency.resolvedCoordinates)
                    } else {
                        variantContext.project.logger.info("[proguard] missing proguard from " + androidDependency.resolvedCoordinates)
                    }
                }
            }
        defaultProguardFiles.addAll(proguardFiles.map { it.toPath() })

        return defaultProguardFiles

    }


    fun getPlatformRules(): List<String> = listOf(
//            "-keep public class com.taobao.tao.TaobaoApplication { "
//                    + "  <init>();\n"
//                    + "  void attachBaseContext(android.content.Context);\n"
//                    + "}"
//            "-keep public class * extends android.app.backup.BackupAgent { <init>(); }",
//            "-keep public class * implements java.lang.annotation.Annotation { *;}",
//            "-keep public class * extends android.test.InstrumentationTestCase { <init>(); }"
    )

    private fun mainDexListProvider(programFiles: List<Path>): Provider<File> {
        return DefaultProvider {

            val mainDexListFile = variantContext.scope
                    .getArtifacts()
                    .appendArtifact(
                            InternalArtifactType
                                    .MAIN_DEX_LIST_FOR_BUNDLE,
                            taskName,
                            "mainDexList.txt")

            var classes: List<String> = MainDexLister(variantContext,variantContext.buildType.multiDexConfig).getMainDexList(programFiles.map { it.toFile() },mainDexListFile)

            classes.forEach(Consumer { variantContext.project.logger.info(it) })

            mainDexListFile
        }

    }
}