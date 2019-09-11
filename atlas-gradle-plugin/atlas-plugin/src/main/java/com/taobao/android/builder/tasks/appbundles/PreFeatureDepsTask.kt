package com.taobao.android.builder.tasks.appbundles

import com.android.build.gradle.BaseExtension
import com.android.build.gradle.api.BaseVariantOutput
import com.android.build.gradle.internal.api.AppVariantContext
import com.android.build.gradle.internal.api.AppVariantOutputContext
import com.android.build.gradle.internal.api.BaseVariantImpl
import com.android.build.gradle.internal.api.VariantContext
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.BuildArtifactsHolder
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.AndroidVariantTask
import com.android.build.gradle.internal.tasks.featuresplit.FeatureSetMetadata
import com.android.tools.build.libraries.metadata.*
import com.taobao.android.builder.AtlasBuildContext
import com.taobao.android.builder.dependency.model.AwbBundle
import com.taobao.android.builder.extension.AtlasExtension
import com.taobao.android.builder.tasks.manager.MtlBaseTaskAction
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.artifacts.result.ComponentSelectionCause
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.internal.artifacts.result.DefaultResolvedDependencyResult
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.io.FileOutputStream
import java.util.*
import java.util.function.Consumer
import java.util.function.Supplier
import kotlin.collections.HashSet

/**
 * @ClassName PreFeatureDepsTask
 * @Description TODO
 * @Author zhayu.ll
 * @Date 2019-09-06 11:15
 * @Version 1.0
 */

open class PreFeatureDepsTask :
        AndroidVariantTask() {

    private lateinit var runtimeClasspath: Configuration

    private lateinit var awbBundle: AwbBundle

    private lateinit var outputContext: AppVariantOutputContext

    @get:Input
    lateinit var runtimeClasspathArtifacts: FileCollection
        private set

    @get:OutputFile
    lateinit var dependenciesList: File
        private set

    private lateinit var moduleNameSupplier: Supplier<String>

    @get:Input
    val moduleName: String
        get() = moduleNameSupplier.get()

    private fun convertDependencyToMavenLibrary(dependency: ModuleComponentSelector?, librariesToIndexMap: Dictionary<Library, Integer>, libraries: LinkedList<Library>): Integer? {
        if (dependency != null) {
            val lib = Library.newBuilder()
                    .setMavenLibrary(MavenLibrary.newBuilder()
                            .setGroupId(dependency.group)
                            .setArtifactId(dependency.module)
                            .setVersion(dependency.version)
                            .build())
                    .build()
            var index = librariesToIndexMap.get(lib)
            if (index == null) {
                index = Integer(libraries.size)
                libraries.add(lib)
                librariesToIndexMap.put(lib, index)
            }
            return index
        }
        return null
    }

    fun writeFile() {

        val librariesToIndexMap: Dictionary<Library, Integer> = Hashtable()
        val libraries = LinkedList<Library>()
        val libraryDependencies = LinkedList<LibraryDependencies>()
        val directDependenciesIndices: MutableSet<Integer> = HashSet()

        val allDeps = AtlasBuildContext.androidDependencyTrees.get(variantName)?.mainBundle?.allDependencies

        AtlasBuildContext.androidDependencyTrees.get(variantName)?.awbBundles?.forEach(Consumer { if (it.dynamicFeature) allDeps?.addAll(it.allDependencies) })

        for (dependency in runtimeClasspath.incoming.resolutionResult.allDependencies) {

            if (dependency.requested !is ModuleComponentSelector) {
                continue
            }
            val moduleComponentSelector = dependency.requested as ModuleComponentSelector

            var find = false


            for (dep in awbBundle.allDependencies){
                if (dep.startsWith(moduleComponentSelector.group + ":" + moduleComponentSelector.module)) {
                    find = true
                    break
                }
            }

           if (!find){
               continue
           }


            val index = convertDependencyToMavenLibrary(
                    dependency.requested as? ModuleComponentSelector,
                    librariesToIndexMap,
                    libraries)
            if (index != null) {

                // add library dependency if we haven't traversed it yet.
                if (libraryDependencies.filter { it.libraryIndex == index.toInt() }.isEmpty()) {
                    val libraryDependency =
                            LibraryDependencies.newBuilder().setLibraryIndex(index.toInt())
                    val dependencyResult = dependency as DefaultResolvedDependencyResult
                    for (libDep in dependencyResult.selected.dependencies) {
                        val depIndex = convertDependencyToMavenLibrary(
                                libDep.requested as? ModuleComponentSelector,
                                librariesToIndexMap,
                                libraries
                        )
                        if (depIndex != null) {
                            libraryDependency.addLibraryDepIndex(depIndex.toInt())
                        }
                    }

                    libraryDependencies.add(libraryDependency.build())
                }

                if (dependency.from.selectionReason.descriptions.filter
                        {
                            it.cause == ComponentSelectionCause.ROOT
                        }.isNotEmpty()) {
                    // this is a direct module dependency.
                    directDependenciesIndices.add(index)
                }
            }
        }


        val moduleDependency = ModuleDependencies.newBuilder().setModuleName(moduleName)
        for (index in directDependenciesIndices) {
            moduleDependency.addDependencyIndex(index.toInt())
        }
        val appDependencies = AppDependencies.newBuilder()
                .addAllLibrary(libraries)
                .addAllLibraryDependencies(libraryDependencies)
                .addModuleDependencies(moduleDependency.build())
                .build()

        dependenciesList.parentFile.mkdirs()
        appDependencies.writeDelimitedTo(FileOutputStream(dependenciesList))
    }


    class CreationAction(
            var awbBundle: AwbBundle,
            variantContext: VariantContext<BaseVariantImpl, BaseExtension, AtlasExtension<*, *>>,
            baseVariantOutput: BaseVariantOutput
            ) : MtlBaseTaskAction<PreFeatureDepsTask>(variantContext, baseVariantOutput) {
        override val name: String = variantScope.getTaskName("collect"+awbBundle.name, "Dependencies")
        override val type: Class<PreFeatureDepsTask> = PreFeatureDepsTask::class.java

        private lateinit var dependenciesList: RegularFile
        override fun preConfigure(taskName: String) {
            super.preConfigure(taskName)
            dependenciesList = appVariantOutputContext.getFeatureDepsFile(awbBundle)

        }

        override fun configure(task: PreFeatureDepsTask) {
            super.configure(task)
            task.awbBundle = awbBundle
            task.outputContext = appVariantOutputContext
            task.dependenciesList = dependenciesList.asFile
            task.runtimeClasspath = variantScope.variantDependencies.runtimeClasspath
            task.runtimeClasspathArtifacts = variantScope.getArtifactCollection(
                    AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                    AndroidArtifacts.ArtifactScope.EXTERNAL,
                    // Normally we would query for PROCESSED_JAR, but JAR is probably sufficient here
                    // since this task is interested in only the meta data of the jar files.
                    AndroidArtifacts.ArtifactType.JAR
            ).artifactFiles


            task.moduleNameSupplier = Supplier { awbBundle.featureName }
        }
    }
}