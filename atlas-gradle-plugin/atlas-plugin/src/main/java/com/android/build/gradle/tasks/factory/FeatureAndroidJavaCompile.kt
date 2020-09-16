package com.android.build.gradle.tasks.factory

import com.android.build.gradle.api.BaseVariantOutput
import com.android.build.gradle.internal.api.AppVariantOutputContext
import com.android.build.gradle.internal.api.VariantContext
import com.android.build.gradle.internal.incremental.InstantRunBuildContext
import com.android.build.gradle.internal.scope.BuildArtifactsHolder
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.tasks.*
import com.android.sdklib.AndroidTargetHash
import com.android.sdklib.AndroidVersion
import com.android.utils.FileUtils
import com.taobao.android.builder.dependency.model.AwbBundle
import com.taobao.android.builder.tasks.manager.MtlBaseTaskAction
import com.taobao.android.builder.tools.ReflectUtils
import org.gradle.api.JavaVersion
import org.gradle.api.file.FileTree
import org.gradle.api.internal.TaskOutputsInternal
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.incremental.IncrementalTaskInputs
import java.io.File

/**
 * @author lilong
 * @create 2017-12-08 上午3:43
 */

open class FeatureAndroidJavaCompile : AndroidJavaCompile() {




    private var awbBundle: AwbBundle? = null


    private var appVariantOutputContext : AppVariantOutputContext? = null

    private val isPostN: Boolean
        get() {
            val hash = AndroidTargetHash.getVersionFromHash(compileSdkVersion)
            return hash != null && hash.apiLevel >= 24
        }

    fun setAwbBundle(awbBundle: AwbBundle) {
        this.awbBundle = awbBundle
    }

     fun compile() {
        logger.info(
                "Compiling with source level {} and target level {}.",
                sourceCompatibility,
                targetCompatibility)
        if (isPostN) {
            if (!JavaVersion.current().isJava8Compatible) {
                throw RuntimeException("compileSdkVersion '" + compileSdkVersion + "' requires "
                        + "JDK 1.8 or later to compile.")
            }
        }

        for (source in sourceFileTrees()) {
            this.source(source)
        }



        outputs. setPreviousOutputFiles(project.files())

        destinationDir.mkdirs()


        this.options.isIncremental = false

        super.compile(null)

        appVariantOutputContext!!.awbTransformMap.get(awbBundle!!.name)!!.inputDirs!!.add(destinationDir)


    }

    fun doFullTaskAction() {
        compile()

    }

    companion object {

        private val analyticsed = false
    }

    class CreationAction(val awbBundle: AwbBundle,
                         variantContext: VariantContext<*, *, *>,
                         baseVariantOutput: BaseVariantOutput) :
            MtlBaseTaskAction<FeatureAndroidJavaCompile>(variantContext, baseVariantOutput) {


        private lateinit var destinationDir: File

        override val name: String
            get() = variantScope.getTaskName("compile" + awbBundle.featureName, "JavaWithJavac")

        override val type: Class<FeatureAndroidJavaCompile>
            get() = FeatureAndroidJavaCompile::class.java

        override fun preConfigure(taskName: String) {
            super.preConfigure(taskName)

            // Register annotation processing output.
            // Note that the annotation processing output may be generated before AndroidJavaCompile
            // is executed if annotation processing is done by another task (ProcessAnnotationTask
            // or KaptTask). Since consumers of the annotation processing output does not need to
            // know what task generates it, for simplicity, we still register AndroidJavaCompile as
            // the generating task.


            // Data binding artifact is one of the annotation processing outputs
            if (variantScope.globalScope.extension.dataBinding.isEnabled) {
                variantScope.artifacts.createBuildableArtifact(
                        InternalArtifactType.DATA_BINDING_ARTIFACT,
                        BuildArtifactsHolder.OperationType.APPEND,
                        listOf(variantScope.bundleArtifactFolderForDataBinding),
                        taskName
                )
            }

            // Register compiled Java classes output
            destinationDir = appVariantOutputContext.getFeatureJavacOutputDir(awbBundle)
        }


        override fun configure(task: FeatureAndroidJavaCompile) {
            super.configure(task)
            task.destinationDir = destinationDir
            val globalScope = variantScope.globalScope
            val project = globalScope.project
            val compileOptions = globalScope.extension.compileOptions
            val separateAnnotationProcessingFlag = globalScope
                    .projectOptions
                    .get(BooleanOption.ENABLE_SEPARATE_ANNOTATION_PROCESSING)

            // Configure properties that are shared between AndroidJavaCompile and
            // ProcessAnnotationTask.
            task.configureProperties(variantScope)

            task.appVariantOutputContext = appVariantOutputContext

            // Configure properties that are specific to AndroidJavaCompile

            task.processorListFile =
                    variantScope.artifacts.getFinalArtifactFiles(InternalArtifactType.ANNOTATION_PROCESSOR_LIST)

            ReflectUtils.updateField(task, "compileSdkVersion", globalScope.extension.compileSdkVersion)
            ReflectUtils.updateField(task, "instantRunBuildContext", variantScope.instantRunBuildContext)
            task.configurePropertiesForAnnotationProcessing(variantScope)
            val sources = mutableListOf<FileTree>()
            sources.add(project.fileTree(appVariantOutputContext.getFeatureRClassSourceOutputDir(variantScope.variantConfiguration, awbBundle)))
            task.setAwbBundle(awbBundle)
            ReflectUtils.updateField(task, "sourceFileTrees", {sources.toList()})
        }
    }
}
