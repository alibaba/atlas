package com.android.build.gradle.internal.transforms

import com.android.build.api.transform.QualifiedContent
import com.android.build.api.transform.QualifiedContent.Scope.EXTERNAL_LIBRARIES
import com.android.build.api.transform.Transform
import com.android.build.api.transform.TransformInvocation
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.internal.api.AppVariantContext
import com.android.build.gradle.internal.api.BaseVariantImpl
import com.android.build.gradle.internal.pipeline.TransformManager
import com.android.build.gradle.internal.res.namespaced.JarRequest
import com.android.build.gradle.internal.res.namespaced.JarWorkerRunnable
import com.android.build.gradle.internal.scope.ApkData
import com.android.build.gradle.internal.tasks.Workers
import com.google.common.collect.Sets
import com.taobao.android.builder.AtlasBuildContext
import com.taobao.android.builder.dependency.model.AwbBundle
import com.taobao.android.builder.extension.AtlasExtension
import com.taobao.android.builder.tasks.manager.transform.MtlInjectTransform
import java.io.File
import java.util.function.Consumer
import java.util.function.Predicate
import java.util.regex.Pattern

/**
 * @ClassName DelegateMergeClassesTransform
 * @Description TODO
 * @Author zhayu.ll
 * @Date 2019-09-16 13:16
 * @Version 1.0
 */
class DelegateMergeClassesTransform(
        private val context: AppVariantContext<*, *, *>,
        val data: ApkData
) : MtlInjectTransform(context, data) {

    override fun getName() = "delegateMergeClasses"

    public lateinit var outputJarFile: File


    override fun getSecondaryFileOutputs() = listOf(outputJarFile)

    override fun isIncremental() = false

    override fun getInputTypes(): Set<QualifiedContent.ContentType> {
        return TransformManager.CONTENT_CLASS
    }

    override fun getScopes(): MutableSet<QualifiedContent.Scope> {
        return Sets.immutableEnumSet(EXTERNAL_LIBRARIES)
    }

    override fun getReferencedScopes(): MutableSet<in QualifiedContent.Scope> {
        return TransformManager.SCOPE_FULL_PROJECT
    }

    override fun doTransform(invocation: TransformInvocation) {
        // Filter out everything but the .class and .kotlin_module files.
        val classFilter: (className: String) -> Boolean =
                { it -> CLASS_PATTERN.matcher(it).matches() || KOTLIN_MODULE_PATTERN.matcher(it).matches() }

        val workers = Workers.getWorker(invocation.context.workerExecutor)


        var fromJars = ArrayList(AtlasBuildContext.atlasMainDexHelperMap.get(appVariantContext.variantName)!!.allMainDexJars)

        var fromDirectories = AtlasBuildContext.atlasMainDexHelperMap.get(appVariantContext.variantName)!!.inputDirs

        context.getAppVariantOutputContext(apkData).awbTransformMap.values.forEach(Consumer {
            var files: List<File> = it.inputFiles
            val libraries: List<File> = it.inputLibraries
            val dirs: Collection<File> = ArrayList(it.inputDirs)

            if (it.awbBundle.dynamicFeature) {

                val featureOutputJarFile = context.getAppVariantOutputContext(apkData).getFeatureMergeClassesFile(it.awbBundle)
                it.awbBundle.mergeJarFile = featureOutputJarFile
                featureOutputJarFile.parentFile.mkdirs()
                files = files + libraries
                workers.use {
                    it.submit(
                            JarWorkerRunnable::class.java,
                            JarRequest(
                                    toFile = featureOutputJarFile,
                                    fromJars = files,
                                    fromDirectories = dirs as List<File>,
                                    filter = classFilter
                            )
                    )

                }
            }else{
                fromJars.addAll(files+libraries)
                fromDirectories.addAll(dirs)

            }

        })







        workers.use {
            it.submit(
                    JarWorkerRunnable::class.java,
                    JarRequest(
                            toFile = outputJarFile,
                            fromJars = fromJars,
                            fromDirectories = fromDirectories,
                            filter = classFilter
                    )
            )
        }


    }

    companion object {
        private val CLASS_PATTERN = Pattern.compile(".*\\.class$")
        private val KOTLIN_MODULE_PATTERN = Pattern.compile("^META-INF/.*\\.kotlin_module$")
    }
}
