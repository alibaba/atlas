package com.taobao.android.builder.tasks.appbundles

import com.android.build.gradle.api.BaseVariantOutput
import com.android.build.gradle.internal.api.AppVariantContext
import com.android.build.gradle.internal.api.AppVariantOutputContext
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.BuildArtifactsHolder
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.AndroidVariantTask
import com.android.build.gradle.internal.tasks.PerModuleReportDependenciesTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.tasks.featuresplit.FeatureSetMetadata
import com.android.tools.build.libraries.metadata.*
import com.taobao.android.builder.AtlasBuildContext
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
 * @ClassName MtlPerModuleReportDependenciesTask
 * @Description TODO
 * @Author zhayu.ll
 * @Date 2019-09-06 10:45
 * @Version 1.0
 */

open class MtlPerModuleReportDependenciesTask :
        AndroidVariantTask() {

    private lateinit var runtimeClasspath: Configuration

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

    @TaskAction
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

            var find = false

            val moduleComponentSelector = dependency.requested as ModuleComponentSelector
            if (allDeps != null) {
                for (dep in allDeps) {
                    if (dep.startsWith(moduleComponentSelector.group + ":" + moduleComponentSelector.module)) {
                        find = true
                        break
                    }
                }
            }

            if (!find) {
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

        appDependencies.writeDelimitedTo(FileOutputStream(dependenciesList))
    }


    class CreationAction(
            variantContext: AppVariantContext<*, *, *>,
            baseVariantOutput: BaseVariantOutput
            ) : MtlBaseTaskAction<MtlPerModuleReportDependenciesTask>(variantContext, baseVariantOutput) {
        override val name: String = variantScope.getTaskName("collect", "Dependencies")
        override val type: Class<MtlPerModuleReportDependenciesTask> = MtlPerModuleReportDependenciesTask::class.java

        private lateinit var dependenciesList: Provider<RegularFile>
        override fun preConfigure(taskName: String) {
            super.preConfigure(taskName)
            dependenciesList = variantScope
                    .artifacts
                    .createArtifactFile(
                            InternalArtifactType.METADATA_LIBRARY_DEPENDENCIES_REPORT,
                            BuildArtifactsHolder.OperationType.INITIAL,
                            taskName,
                            "dependencies.pb"
                    )
        }

        override fun configure(task: MtlPerModuleReportDependenciesTask) {
            super.configure(task)
            task.outputContext = appVariantOutputContext
            task.dependenciesList = dependenciesList.get().asFile
            task.runtimeClasspath = variantScope.variantDependencies.runtimeClasspath
            task.runtimeClasspathArtifacts = variantScope.getArtifactCollection(
                    AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                    AndroidArtifacts.ArtifactScope.EXTERNAL,
                    // Normally we would query for PROCESSED_JAR, but JAR is probably sufficient here
                    // since this task is interested in only the meta data of the jar files.
                    AndroidArtifacts.ArtifactType.JAR
            ).artifactFiles


            task.moduleNameSupplier = if (variantScope.type.isBaseModule)
                Supplier { "base" }
            else {
                val featureName: Supplier<String> = FeatureSetMetadata.getInstance()
                        .getFeatureNameSupplierForTask(variantScope, task)
                Supplier { "${featureName.get()}" }
            }
        }
    }
}