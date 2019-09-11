package com.taobao.android.builder.tasks.appbundles

import com.android.build.gradle.api.BaseVariantOutput
import com.android.build.gradle.internal.api.VariantContext
import com.android.build.gradle.internal.tasks.AndroidVariantTask
import com.taobao.android.builder.dependency.model.AwbBundle
import com.taobao.android.builder.tasks.manager.MtlBaseTaskAction
import com.android.SdkConstants
import com.android.SdkConstants.FD_ASSETS
import com.android.SdkConstants.FD_DEX
import com.android.build.api.artifact.BuildableArtifact
import com.android.build.gradle.internal.pipeline.StreamFilter
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.publishing.AndroidArtifacts.MODULE_PATH
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.tasks.featuresplit.FeatureSetMetadata
import com.android.builder.files.NativeLibraryAbiPredicate
import com.android.builder.packaging.JarMerger
import com.android.utils.FileUtils
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Predicate
import java.util.function.Supplier

/**
 * Task that zips a module's bundle elements into a zip file. This gets published
 * so that the base app can package into the bundle.
 *
 */
/**
 * @ClassName PreModuleBundlesTask
 * @Description TODO
 * @Author zhayu.ll
 * @Date 2019-09-05 16:14
 * @Version 1.0
 */
open class PerModuleBundlesTask : AndroidVariantTask() {

    @get:OutputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    lateinit var outputDir: File
        private set

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    lateinit var dexFiles: FileCollection
        private set

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    lateinit var featureDexFiles: FileCollection
        private set

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    lateinit var resFiles: FileCollection
        private set

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    lateinit var javaResFiles: FileCollection
        private set

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    lateinit var assetsFiles: FileCollection
        private set

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    lateinit var nativeLibsFiles: FileCollection
        private set

    @get:Input
    @get:Optional
    var abiFilters: Set<String>? = null
        private set

    private lateinit var fileNameSupplier: Supplier<String>

    @get:Input
    val fileName: String
        get() = fileNameSupplier.get()

    fun zip() {
        FileUtils.cleanOutputDir(outputDir)
        val jarMerger = JarMerger(File(outputDir, fileName).toPath())

        val filters = abiFilters
        val abiFilter: Predicate<String>? = if (filters != null) NativeLibraryAbiPredicate(filters, false) else null

        jarMerger.use { it ->

            it.addDirectory(
                    assetsFiles.single().toPath(),
                    null,
                    null,
                    Relocator(FD_ASSETS)
            )

            it.addJar(resFiles.single().toPath(), null, ResRelocator())

            // dex files
            val dexFilesSet = if (hasFeatureDexFiles()) featureDexFiles.files else dexFiles.files
            if (dexFilesSet.size == 1) {
                // Don't rename if there is only one input folder
                // as this might be the legacy multidex case.
                addHybridFolder(it, dexFilesSet.sortedBy { it.name }, Relocator(FD_DEX), null)
            } else {
                addHybridFolder(it, dexFilesSet.sortedBy { it.name }, DexRelocator(FD_DEX), null)
            }

            val javaResFilesSet = if (hasFeatureDexFiles()) setOf<File>() else javaResFiles.files
            addHybridFolder(it, javaResFilesSet,
                    Relocator("root"),
                    JarMerger.EXCLUDE_CLASSES)

            addHybridFolder(it, nativeLibsFiles.files, fileFilter = abiFilter)
        }
    }

    private fun addHybridFolder(
            jarMerger: JarMerger,
            files: Iterable<File>,
            relocator: JarMerger.Relocator? = null,
            fileFilter: Predicate<String>? = null) {
        // in this case the artifact is a folder containing things to add
        // to the zip. These can be file to put directly, jars to copy the content
        // of, or folders
        for (file in files) {

            if (!file.exists()){
                continue
            }
            if (file.isFile) {
                if (file.name.endsWith(SdkConstants.DOT_JAR)) {
                    jarMerger.addJar(file.toPath(), fileFilter, relocator)
                } else if (fileFilter == null || fileFilter.test(file.name)) {
                    if (relocator != null) {
                        jarMerger.addFile(relocator.relocate(file.name), file.toPath())
                    } else {
                        jarMerger.addFile(file.name, file.toPath())
                    }
                }
            } else {

                jarMerger.addDirectory(
                        file.toPath(),
                        fileFilter,
                        null,
                        relocator)
            }
        }
    }

    private fun hasFeatureDexFiles() = featureDexFiles.files.isNotEmpty()

    class CreationAction(
            var awbBundle: AwbBundle,
            variantContext: VariantContext<*, *, *>,
            var variantOutput: BaseVariantOutput
    ) : MtlBaseTaskAction<PerModuleBundlesTask>(variantContext, variantOutput) {

        override val name: String
            get() = variantScope.getTaskName("build" + awbBundle.name, "PreBundle")
        override val type: Class<PerModuleBundlesTask>
            get() = PerModuleBundlesTask::class.java

        private lateinit var outputDir: File

        override fun preConfigure(taskName: String) {
            super.preConfigure(taskName)

            outputDir = appVariantOutputContext.getFeatureModuleBundleOutputDir(scope.variantConfiguration, awbBundle)

        }

        override fun configure(task: PerModuleBundlesTask) {
            super.configure(task)

            val artifacts = variantScope.artifacts

            val featureName: Supplier<String> = Supplier { awbBundle.featureName }



            task.outputDir = outputDir
            task.fileNameSupplier = Supplier { "${featureName.get()}.zip" }
            task.assetsFiles = variantContext.project.files(appVariantOutputContext.getFeatureMergeAssetsFolder(scope.variantConfiguration, awbBundle))

            task.resFiles = variantContext.project.files(appVariantOutputContext.geteBundledResFile(scope.variantConfiguration, awbBundle))
            task.featureDexFiles = variantContext.project.files(appVariantOutputContext.getFeatureFinalDexFolder(awbBundle))
            task.javaResFiles = variantContext.project.files(appVariantOutputContext.getAwbJavaResFolder(awbBundle))
            task.nativeLibsFiles = variantContext.project.files(appVariantOutputContext.getAwbJniFolder(awbBundle))
            task.abiFilters = variantScope.variantConfiguration.supportedAbis
        }

    }

    private class Relocator(private val prefix: String) : JarMerger.Relocator {
        override fun relocate(entryPath: String) = "$prefix/$entryPath"
    }

    /**
     * Relocate the dex files into a single folder which might force renaming
     * the dex files into a consistent scheme : classes.dex, classes2.dex, classes4.dex, ...
     *
     * <p>Note that classes1.dex is not a valid dex file name</p>
     *
     * When dealing with native multidex, we can have several folders each containing 1 to many
     * dex files (with the same naming scheme). In that case, merge and rename accordingly, note
     * that only one classes.dex can exist in the merged location so the others will be renamed.
     *
     * When dealing with a single feature (base) in legacy multidex, we must not rename the
     * main dex file (classes.dex) as it contains the bootstrap code for the application.
     */
    private class DexRelocator(private val prefix: String) : JarMerger.Relocator {
        // first valid classes.dex with an index starts at 2.
        val index = AtomicInteger(2)
        val classesDexNameUsed = AtomicBoolean(false)
        override fun relocate(entryPath: String): String {
            if (entryPath.startsWith("classes")) {
                return if (entryPath == "classes.dex" && !classesDexNameUsed.get()) {
                    classesDexNameUsed.set(true)
                    "$prefix/classes.dex"
                } else {
                    val entryIndex = index.getAndIncrement()
                    "$prefix/classes$entryIndex.dex"
                }
            }
            return "$prefix/$entryPath"
        }
    }


    private class ResRelocator : JarMerger.Relocator {
        override fun relocate(entryPath: String) = when (entryPath) {
            SdkConstants.FN_ANDROID_MANIFEST_XML -> "manifest/" + SdkConstants.FN_ANDROID_MANIFEST_XML
            else -> entryPath
        }
    }
}


