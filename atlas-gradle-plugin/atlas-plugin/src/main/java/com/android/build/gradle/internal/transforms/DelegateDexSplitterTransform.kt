package com.android.build.gradle.internal.transforms

import com.android.build.api.artifact.BuildableArtifact
import com.android.build.api.transform.*
import com.android.build.gradle.internal.api.AppVariantContext
import com.android.build.gradle.internal.api.artifact.singleFile
import com.android.build.gradle.internal.pipeline.TransformManager
import com.android.build.gradle.internal.scope.ApkData
import com.android.utils.FileUtils
import com.taobao.android.builder.tasks.manager.transform.MtlInjectTransform
import org.gradle.api.file.FileCollection
import java.io.File
import java.io.FileFilter
import java.io.FilenameFilter
import java.nio.file.Files
import java.util.function.Consumer
import java.util.function.Supplier

/**
 * @ClassName DelegateDexSplitterTransform
 * @Description TODO
 * @Author zhayu.ll
 * @Date 2019-09-16 11:44
 * @Version 1.0
 */

class DelegateDexSplitterTransform(
        private val appVariantContext: AppVariantContext<*,*,*>,
        private val apkData: ApkData

) :
        MtlInjectTransform(appVariantContext,apkData) {

    override fun getName(): String = "delegateDexSplitter"


    public lateinit var outputDir: File

    public lateinit var featureJars: List<Supplier<File>>

    public lateinit var baseJars: FileCollection

    public lateinit var mappingFileSrc: BuildableArtifact

    override fun getInputTypes(): MutableSet<QualifiedContent.ContentType> = TransformManager.CONTENT_DEX

    override fun getOutputTypes(): MutableSet<QualifiedContent.ContentType> = TransformManager.CONTENT_DEX

    override fun getScopes(): MutableSet<in QualifiedContent.Scope> =
            TransformManager.SCOPE_FULL_WITH_IR_AND_FEATURES

    override fun isIncremental(): Boolean = false

    override fun getSecondaryFiles(): MutableCollection<SecondaryFile> {
        val secondaryFiles: MutableCollection<SecondaryFile> = mutableListOf()
//        secondaryFiles.add(SecondaryFile.nonIncremental(featureJars))
//        secondaryFiles.add(SecondaryFile.nonIncremental(baseJars))
//        mappingFileSrc?.let { secondaryFiles.add(SecondaryFile.nonIncremental(it)) }
        return secondaryFiles
    }

    override fun getSecondaryDirectoryOutputs(): MutableCollection<File> {
        return mutableListOf(outputDir)
    }

    override fun transform(transformInvocation: TransformInvocation) {
        val mappingFile =
                if (mappingFileSrc?.singleFile()?.exists() == true
                        && !mappingFileSrc!!.singleFile().isDirectory) {
                    mappingFileSrc!!.singleFile()
                } else {
                    null
                }

            val outputProvider = requireNotNull(
                    transformInvocation.outputProvider,
                    { "No output provider set" }
            )
            outputProvider.deleteAll()
            FileUtils.deleteRecursivelyIfExists(outputDir)

        val builder = com.android.builder.dexing.DexSplitterTool.Builder(outputDir?.toPath(), mappingFile?.toPath())

            for (dirInput in TransformInputUtil.getDirectories(transformInvocation.inputs)) {
                dirInput.listFiles()?.toList()?.map { it.toPath() }?.forEach { builder.addInputArchive(it) }
            }

            featureJars.map{ it.get()}.forEach { file ->
                builder.addFeatureJar(file.toPath(), file.nameWithoutExtension)
                Files.createDirectories(File(outputDir, file.nameWithoutExtension).toPath())
            }

            baseJars!!.files.forEach { builder.addBaseJar(it.toPath()) }
            builder.build().run()

            val transformOutputDir =
                    outputProvider.getContentLocation(
                            "splitDexFiles", outputTypes, scopes, Format.DIRECTORY
                    )
            Files.createDirectories(transformOutputDir.toPath())

            outputDir!!.listFiles().find { it.name == "base" }?.let {
                FileUtils.copyDirectory(it, transformOutputDir)
                FileUtils.deleteRecursivelyIfExists(it)
            }

        appVariantOutputContext.awbTransformMap.values.filter { it.awbBundle.dynamicFeature }.forEach(Consumer {

            var featureName:String = it.awbBundle.featureName
            var dir:File = appVariantOutputContext.getFeatureFinalDexFolder(it.awbBundle)
            dir.mkdirs()


            outputDir!!.listFiles().find { it.name ==  featureName}?.listFiles(FileFilter { it.name.endsWith("dex") })!![0]?.let {
                FileUtils.copyFileToDirectory(it, dir)
                FileUtils.deleteRecursivelyIfExists(it)
            }
        })
    }

}