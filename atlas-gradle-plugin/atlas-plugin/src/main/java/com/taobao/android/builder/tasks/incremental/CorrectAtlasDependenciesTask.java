package com.taobao.android.builder.tasks.incremental;

import com.alibaba.fastjson.JSON;
import com.android.annotations.NonNull;
import com.android.build.gradle.internal.api.VariantContext;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.scope.ConventionMappingHelper;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.BaseTask;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.BaseVariantOutputData;
import com.android.builder.model.Library;
import com.android.builder.model.MavenCoordinates;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.taobao.android.builder.AtlasBuildContext;
import com.taobao.android.builder.dependency.AtlasDependencyTree;
import com.taobao.android.builder.dependency.model.AwbBundle;
import com.taobao.android.builder.dependency.output.DependencyJson;
import com.taobao.android.builder.tasks.manager.MtlBaseTaskAction;

import org.apache.commons.io.FileUtils;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionComparator;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Created by chenhjohn on 2017/5/1.
 */

public class CorrectAtlasDependenciesTask extends BaseTask {

    // ----- PUBLIC TASK API -----
    private final DependencyHandler dependencies;

    private final Comparator<String> versionComparator = new DefaultVersionComparator().asStringComparator();

    private AtlasDependencyTree atlasDependencyTree;

    private File baseDependenciesFile;

    public CorrectAtlasDependenciesTask() {
        dependencies = getProject().getDependencies();
    }
    // ----- PRIVATE TASK API -----

    // @InputFile
    public File getBaseDependenciesFile() {
        return baseDependenciesFile;
    }

    public void setBaseDependenciesFile(File baseDependenciesFile) {
        this.baseDependenciesFile = baseDependenciesFile;
    }

    @TaskAction
    void generate() throws IOException {
        DependencyJson apDependencyJson = JSON.parseObject(FileUtils.readFileToString(
                getBaseDependenciesFile()), DependencyJson.class);

        final List<String> mainDexList = apDependencyJson.getMainDex();
        for (AwbBundle awbBundle : atlasDependencyTree.getAwbBundles()) {
            List<Library> libraries = new ArrayList<>();
            libraries.addAll(awbBundle.getAndroidLibraries());
            libraries.addAll(awbBundle.getJavaLibraries());

            List<String> baseLibraries = getBaseAwbBundleLibraries(apDependencyJson, awbBundle);

            Set<Library> librarysToRemove = new HashSet<Library>();
            for (Library library : libraries) {
                // 移除主dex中的依赖
                for (String mainDex : mainDexList) {
                    if (isSameGroupAndModule(library, mainDex)) {
                        librarysToRemove.add(library);
                        // TODO 比较版本号
                        /*if (versionComparator.compare(resolvedCoordinates.getVersion(),
                                                      dependency.getVersion()) <= 0) {
                            break;
                        }*/
                    }
                }

                // // 移除相同依赖
                // for (String baseLibrary : baseLibraries) {
                //     if (isSameResolvedDependency(library, baseLibrary)) {
                //         librarysToRemove.add(library);
                //         break;
                //     }
                // }
            }

            System.out.println("Removed " +
                               librarysToRemove.size() +
                               " useless librarys from " +
                               awbBundle.getName() +
                               ":\n  " +
                               Joiner.on(", ").join(librarysToRemove));
            awbBundle.getAndroidLibraries().removeAll(librarysToRemove);
            awbBundle.getJavaLibraries().removeAll(librarysToRemove);
        }
    }

    private List<String> getBaseAwbBundleLibraries(DependencyJson apDependencyJson, AwbBundle awbBundle) {
        Map<String, ArrayList<String>> baseAwbs = apDependencyJson.getAwbs();
        List<Map.Entry<String, ArrayList<String>>> awbs = baseAwbs.entrySet()
                .stream()
                .filter(entry -> isSameGroupAndModule(awbBundle.getAndroidLibrary(),
                                                      entry.getKey()))
                .collect(Collectors.toList());
        Map.Entry<String, ArrayList<String>> baseAwb = Iterables.getOnlyElement(awbs, null);
        return baseAwb == null ? ImmutableList.<String>of() : baseAwb.getValue();
    }

    private boolean isSameResolvedDependency(Library library, String baseLibrary) {
        Dependency dependency = dependencies.create(baseLibrary);
        MavenCoordinates resolvedCoordinates = library.getResolvedCoordinates();
        return dependency.getGroup().equals(resolvedCoordinates.getGroupId()) &&
               dependency.getName().equals(resolvedCoordinates.getArtifactId()) &&
               versionComparator.compare(resolvedCoordinates.getVersion(),
                                         dependency.getVersion()) <= 0;
    }

    private boolean isSameGroupAndModule(Library library, String dependencyString) {
        Dependency dependency = dependencies.create(dependencyString);
        MavenCoordinates resolvedCoordinates = library.getResolvedCoordinates();
        return dependency.getGroup().equals(resolvedCoordinates.getGroupId()) &&
               dependency.getName().equals(resolvedCoordinates.getArtifactId());
    }

    // ----- Config Action -----
    public static final class ConfigAction extends MtlBaseTaskAction<CorrectAtlasDependenciesTask> {
        @NonNull
        private final VariantScope scope;

        public ConfigAction(VariantContext variantContext, BaseVariantOutputData baseVariantOutputData) {
            super(variantContext, baseVariantOutputData);
            this.scope = baseVariantOutputData.getScope().getVariantScope();
        }

        @Override
        @NonNull
        public String getName() {
            return scope.getTaskName("correct", "AtlasDependencies");
        }

        @Override
        @NonNull
        public Class<CorrectAtlasDependenciesTask> getType() {
            return CorrectAtlasDependenciesTask.class;
        }

        @Override
        public void execute(@NonNull CorrectAtlasDependenciesTask correctDependencies) {
            BaseVariantData<? extends BaseVariantOutputData> variantData = scope.getVariantData();

            final GradleVariantConfiguration variantConfiguration = variantData.getVariantConfiguration();
            correctDependencies.setAndroidBuilder(scope.getGlobalScope().getAndroidBuilder());
            correctDependencies.setVariantName(scope.getVariantConfiguration().getFullName());
            correctDependencies.atlasDependencyTree = AtlasBuildContext.androidDependencyTrees.get(
                    correctDependencies.getVariantName());

            ConventionMappingHelper.map(correctDependencies, "baseDependenciesFile", () -> {
                return variantContext.apContext.getBaseDependenciesFile();
            });
        }
    }
}
