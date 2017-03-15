///*
// * Copyright (C) 2016 The Android Open Source Project
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// *      http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//
//package com.android.build.gradle.internal;
//
//import com.alibaba.fastjson.JSON;
//import com.android.SdkConstants;
//import com.android.annotations.NonNull;
//import com.android.annotations.Nullable;
//import com.android.build.gradle.AndroidGradleOptions;
//import com.android.build.gradle.internal.dependency.DependencyGraph;
//import com.android.build.gradle.internal.dependency.MutableDependencyDataMap;
//import com.android.build.gradle.internal.dependency.VariantDependencies;
//import com.android.build.gradle.internal.scope.AndroidTask;
//import com.android.build.gradle.internal.tasks.PrepareDependenciesTask;
//import com.android.build.gradle.internal.tasks.PrepareLibraryTask;
//import com.android.build.gradle.internal.variant.BaseVariantData;
//import com.android.build.gradle.internal.variant.BaseVariantOutputData;
//import com.android.builder.dependency.MavenCoordinatesImpl;
//import com.android.builder.dependency.level2.AndroidDependency;
//import com.android.builder.dependency.level2.AtomDependency;
//import com.android.builder.dependency.level2.Dependency;
//import com.android.builder.dependency.level2.DependencyNode;
//import com.android.builder.dependency.level2.DependencyNode.NodeType;
//import com.android.builder.dependency.level2.JavaDependency;
//import com.android.builder.model.AndroidLibrary;
//import com.android.builder.model.AndroidProject;
//import com.android.builder.model.SyncIssue;
//import com.android.builder.sdk.SdkLibData;
//import com.android.builder.utils.FileCache;
//import com.android.sdklib.repository.meta.DetailsTypes;
//import com.android.utils.FileUtils;
//import com.android.utils.ILogger;
//import com.google.common.base.Joiner;
//import com.google.common.base.Preconditions;
//import com.google.common.collect.ImmutableList;
//import com.google.common.collect.Lists;
//import com.google.common.collect.Maps;
//import com.google.common.collect.Sets;
//import com.taobao.android.builder.AtlasBuildContext;
//import com.taobao.android.builder.dependency.AndroidDependencyTree;
//import com.taobao.android.builder.dependency.parser.CircleDependencyCheck;
//import com.taobao.android.builder.dependency.parser.ResolvedDependencyContainer;
//import com.taobao.android.builder.dependency.parser.ResolvedDependencyInfo;
//import com.taobao.android.builder.tools.PluginTypeUtils;
//
//import org.apache.commons.lang.StringUtils;
//import org.gradle.api.CircularReferenceException;
//import org.gradle.api.Project;
//import org.gradle.api.artifacts.Configuration;
//import org.gradle.api.artifacts.ModuleVersionIdentifier;
//import org.gradle.api.artifacts.ModuleVersionSelector;
//import org.gradle.api.artifacts.ProjectDependency;
//import org.gradle.api.artifacts.ResolvedArtifact;
//import org.gradle.api.artifacts.SelfResolvingDependency;
//import org.gradle.api.artifacts.UnresolvedDependency;
//import org.gradle.api.artifacts.component.ComponentIdentifier;
//import org.gradle.api.artifacts.component.ComponentSelector;
//import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
//import org.gradle.api.artifacts.result.DependencyResult;
//import org.gradle.api.artifacts.result.ResolvedComponentResult;
//import org.gradle.api.artifacts.result.ResolvedDependencyResult;
//import org.gradle.api.artifacts.result.UnresolvedDependencyResult;
//import org.gradle.api.logging.Logging;
//import org.gradle.api.specs.Specs;
//import org.gradle.util.GUtil;
//
//import java.io.File;
//import java.io.IOException;
//import java.io.UncheckedIOException;
//import java.util.ArrayList;
//import java.util.Collections;
//import java.util.HashSet;
//import java.util.List;
//import java.util.Locale;
//import java.util.Map;
//import java.util.Optional;
//import java.util.Set;
//
//import static com.android.SdkConstants.DOT_JAR;
//import static com.android.SdkConstants.EXT_ANDROID_PACKAGE;
//import static com.android.SdkConstants.EXT_JAR;
//import static com.android.build.gradle.internal.TaskManager.DIR_ATOMBUNDLES;
//import static com.android.build.gradle.internal.TaskManager.DIR_BUNDLES;
//import static com.android.builder.core.BuilderConstants.EXT_ATOMBUNDLE_ARCHIVE;
//import static com.android.builder.core.BuilderConstants.EXT_LIB_ARCHIVE;
//import static com.android.builder.core.ErrorReporter.EvaluationMode.STANDARD;
//import static com.android.builder.model.AndroidProject.FD_INTERMEDIATES;
//
///**
// * A manager to resolve configuration dependencies.
// */
//public class TDependencyManager extends DependencyManager {
//
//    private static final boolean DEBUG_DEPENDENCY = false;
//    public static final String EXPLODED_AAR = "exploded-aar";
//    private final Project project;
//    private final ExtraModelInfo extraModelInfo;
//    private final ILogger logger;
//    private final SdkHandler sdkHandler;
//    private SdkLibData sdkLibData =  SdkLibData.dontDownload();
//    private boolean repositoriesUpdated = false;
//
//    private final Map<String, PrepareLibraryTask> prepareLibTaskMap = Maps.newHashMap();
//
//    public TDependencyManager(
//            @NonNull Project project,
//            @NonNull ExtraModelInfo extraModelInfo,
//            @NonNull SdkHandler sdkHandler) {
//        super(project, extraModelInfo, sdkHandler);
//        this.project = project;
//        this.extraModelInfo = extraModelInfo;
//        this.sdkHandler = sdkHandler;
//        logger = new LoggerWrapper(Logging.getLogger(TDependencyManager.class));
//    }
//
//    @Override
//    public void addDependenciesToPrepareTask(
//            @NonNull TaskFactory tasks,
//            @NonNull BaseVariantData<? extends BaseVariantOutputData> variantData,
//            @NonNull AndroidTask<PrepareDependenciesTask> prepareDependenciesTask) {
//        super.addDependenciesToPrepareTask(tasks,variantData,prepareDependenciesTask);
//    }
//
//    @Override
//    public Set<AndroidDependency> resolveDependencies(
//            @NonNull VariantDependencies variantDeps,
//            @Nullable String testedProjectPath) {
//        // set of Android Libraries to explode. This only concerns remote libraries, as modules
//        // are now used through their staging folders rather than their bundled AARs.
//        // Therefore there is no dependency on these exploded tasks since remote AARs are
//        // downloaded during the dependency resolution process.
//        // because they are not immutable (them or the children could be skipped()), we use
//        // an identity set.
//        Set<AndroidDependency> libsToExplode = Sets.newIdentityHashSet();
//
//        resolveDependencies(
//                variantDeps,
//                testedProjectPath,
//                libsToExplode);
//        return libsToExplode;
//    }
//
//    public void processLibraries(@NonNull Set<AndroidDependency> libsToExplode) {
//        for (AndroidDependency lib: libsToExplode) {
//            maybeCreatePrepareLibraryTask(lib, project);
//        }
//    }
//
//    /**
//     * Handles the library and returns a task to "prepare" the library (ie unarchive it). The task
//     * will be reused for all projects using the same library.
//     *
//     * @param library the library.
//     * @param project the project
//     * @return the prepare task.
//     */
//    private PrepareLibraryTask maybeCreatePrepareLibraryTask(
//            @NonNull AndroidDependency library,
//            @NonNull Project project) {
//        if (library.isSubModule()) {
//            throw new RuntimeException("Creating PrepareLib task for submodule: " + library.getCoordinates());
//        }
//
//        // create proper key for the map. library here contains all the dependencies which
//        // are not relevant for the task (since the task only extract the aar which does not
//        // include the dependencies.
//        // However there is a possible case of a rewritten dependencies (with resolution strategy)
//        // where the aar here could have different dependencies, in which case we would still
//        // need the same task.
//        // So we extract a AbstractBundleDependency (no dependencies) from the AndroidDependency to
//        // make the map key that doesn't take into account the dependencies.
//        String key = library.getCoordinates().toString();
//
//        PrepareLibraryTask prepareLibraryTask = prepareLibTaskMap.get(key);
//
//        if (prepareLibraryTask == null) {
//            String bundleName = GUtil.toCamelCase(library.getName().replaceAll("\\:", " "));
//
//            prepareLibraryTask = project.getTasks().create(
//                    "prepare" + bundleName + "Library", PrepareLibraryTask.class);
//
//            prepareLibraryTask.setDescription("Prepare " + library.getName());
//            prepareLibraryTask.setVariantName("");
//            prepareLibraryTask.init(
//                    library.getArtifactFile(),
//                    library.getExtractedFolder(),
//                    AndroidGradleOptions.getBuildCache(project),
//                    library.getCoordinates());
//
//            prepareLibTaskMap.put(key, prepareLibraryTask);
//        }
//
//        return prepareLibraryTask;
//    }
//
//    private void resolveDependencies(
//            @NonNull final VariantDependencies variantDeps,
//            @Nullable String testedProjectPath,
//            @NonNull Set<AndroidDependency> libsToExplodeOut) {
//        boolean needPackageScope = true;
//        if (AndroidGradleOptions.buildModelOnly(project)) {
//            // if we're only syncing (building the model), then we only need the package
//            // scope if we will actually pass it to the IDE.
//            Integer modelLevelInt = AndroidGradleOptions.buildModelOnlyVersion(project);
//            int modelLevel = AndroidProject.MODEL_LEVEL_0_ORIGINAL;
//            if (modelLevelInt != null) {
//                modelLevel = modelLevelInt;
//            }
//            if (modelLevel > AndroidProject.MODEL_LEVEL_2_DONT_USE) {
//                needPackageScope = AndroidGradleOptions.buildModelWithFullDependencies(project);
//            }
//        }
//
//        Configuration compileClasspath = variantDeps.getCompileConfiguration();
//        Configuration packageClasspath = variantDeps.getPackageConfiguration();
//
//        if (DEBUG_DEPENDENCY) {
//            System.out.println(">>>>>>>>>>");
//            System.out.println(
//                    project.getName() + ":" +
//                            compileClasspath.getName() + "/" +
//                            packageClasspath.getName());
//        }
//
//        Map<ModuleVersionIdentifier, List<ResolvedArtifact>> artifacts = Maps.newHashMap();
//        collectArtifacts(compileClasspath, artifacts);
//        collectArtifacts(packageClasspath, artifacts);
//        // 不使用官方的扁平化的依赖处理，改用自己处理树状的依赖关系;对于application的依赖，我们只取compile的依赖
//        ResolvedDependencyContainer compileResolvedDependencyContainer = new ResolvedDependencyContainer(project);
//        Set<ModuleVersionIdentifier> directDependencies = new HashSet<ModuleVersionIdentifier>();
//        Set<? extends DependencyResult> projectDependencies = compileClasspath.getIncoming()
//                .getResolutionResult()
//                .getRoot()
//                .getAndroidLibraries();
//
//        for (DependencyResult dependencyResult : projectDependencies) {
//            if (dependencyResult instanceof ResolvedDependencyResult) {
//
//                ModuleVersionIdentifier moduleVersion = ((ResolvedDependencyResult) dependencyResult)
//                        .getSelected()
//                        .getModuleVersion();
//                CircleDependencyCheck circleDependencyCheck = new CircleDependencyCheck(
//                        moduleVersion);
//
//                if (!directDependencies.contains(moduleVersion)) {
//                    directDependencies.add(moduleVersion);
//                    resolveDependency(compileResolvedDependencyContainer,
//                                      null,
//                                      ((ResolvedDependencyResult) dependencyResult).getSelected(),
//                                      artifacts,
//                                      variantDeps,
//                                      0,
//                                      circleDependencyCheck,
//                                      circleDependencyCheck.getRootDependencyNode());
//                }
//            }
//        }
//
//        AndroidDependencyTree androidDependencyTree = compileResolvedDependencyContainer.reslovedDependencies()
//                .toAndroidDependency();
//
//        if (PluginTypeUtils.isAppProject(project)){
//            AtlasBuildContext.androidDependencyTrees.put(variantDeps.getName(), androidDependencyTree);
//        }else {
//            AtlasBuildContext.libDependencyTrees.put(variantDeps.getName(), androidDependencyTree);
//        }
//
//
//        //output tree file only once
//        if (project.getLogger().isInfoEnabled()) {
//            project.getLogger()
//                    .info("[dependencyTree" +
//                                  variantDeps.getName() +
//                                  "]" +
//                                  JSON.toJSONString(androidDependencyTree.getDependencyJson(),
//                                                    true));
//        }
//
//        Set<String> currentUnresolvedDependencies = Sets.newHashSet();
//
//        // records the artifact we find during package, to detect provided only dependencies.
//        Set<String> artifactSet = Sets.newHashSet();
//
//        // start with package dependencies, record the artifacts
//        DependencyGraph packagedGraph;
//        if (needPackageScope) {
//            packagedGraph = resolveConfiguration(
//                    packageClasspath,
//                    variantDeps,
//                    libsToExplodeOut,
//                    currentUnresolvedDependencies,
//                    testedProjectPath,
//                    artifactSet,
//                    ScopeType.PACKAGE);
//        } else {
//            packagedGraph = DependencyGraph.getEmpty();
//        }
//
//        // then the compile dependencies, comparing against the record package dependencies
//        // to set the provided flag.
//        // if we have not compute the package scope, we disable the computation of
//        // provided bits. This disables the checks on impossible provided libs (provided aar in
//        // apk project).
//        ScopeType scopeType = needPackageScope ? ScopeType.COMPILE : ScopeType.COMPILE_ONLY;
//        DependencyGraph compileDependencies = resolveConfiguration(
//                compileClasspath,
//                variantDeps,
//                libsToExplodeOut,
//                currentUnresolvedDependencies,
//                testedProjectPath,
//                artifactSet,
//                scopeType);
//
//        if (extraModelInfo.getMode() != STANDARD &&
//                compileClasspath.getResolvedConfiguration().hasError()) {
//            for (String dependency : currentUnresolvedDependencies) {
//                extraModelInfo.handleSyncError(
//                        dependency,
//                        SyncIssue.TYPE_UNRESOLVED_DEPENDENCY,
//                        String.format(
//                                "Unable to resolve dependency '%s'",
//                                dependency));
//            }
//        }
//
//        variantDeps.setDependencies(compileDependencies, packagedGraph, needPackageScope);
//
//        if (DEBUG_DEPENDENCY) {
//            System.out.println("*** COMPILE DEPS ***");
//            /*
//            for (AndroidLibrary lib : compileDependencies.getAndroidDependencies()) {
//                System.out.println("LIB: " + lib);
//            }
//            for (AndroidAtom atom : compileDependencies.getAtomDependencies()) {
//                System.out.println("ATOM: " + atom);
//            }
//            for (JavaLibrary jar : compileDependencies.getJavaLibraries()) {
//                System.out.println("JAR: " + jar);
//            }
//            for (JavaLibrary jar : compileDependencies.getLocalDependencies()) {
//                System.out.println("LOCAL-JAR: " + jar);
//            }
//            System.out.println("*** PACKAGE DEPS ***");
//            for (AndroidLibrary lib : packagedGraph.getAndroidDependencies()) {
//                System.out.println("LIB: " + lib);
//            }
//            for (JavaLibrary jar : packagedGraph.getJavaLibraries()) {
//                System.out.println("JAR: " + jar);
//            }
//            for (JavaLibrary jar : packagedGraph.getLocalDependencies()) {
//                System.out.println("LOCAL-JAR: " + jar);
//            }
//            System.out.println("***");
//            */
//            System.out.println(project.getName() + ":" + compileClasspath.getName() + "/" +packageClasspath.getName());
//            System.out.println("<<<<<<<<<<");
//        }
//    }
//
//    enum ScopeType {
//        PACKAGE,
//        COMPILE,
//        COMPILE_ONLY;
//    }
//
//
//    /**
//     * 解析依赖
//     *
//     * @param resolvedDependencyContainer
//     * @param parent
//     * @param resolvedComponentResult
//     * @param artifacts
//     * @param configDependencies
//     * @param indent
//     */
//    private void resolveDependency(ResolvedDependencyContainer resolvedDependencyContainer,
//                                   ResolvedDependencyInfo parent,
//                                   ResolvedComponentResult resolvedComponentResult,
//                                   Map<ModuleVersionIdentifier, List<ResolvedArtifact>> artifacts,
//                                   VariantDependencies configDependencies,
//                                   int indent,
//                                   CircleDependencyCheck circleDependencyCheck,
//                                   CircleDependencyCheck.DependencyNode node) {
//        ModuleVersionIdentifier moduleVersion = resolvedComponentResult.getModuleVersion();
//
//        if (configDependencies.getChecker().checkForExclusion(moduleVersion)) {
//            return;
//        }
//
//        if (moduleVersion.getName().equals("support-annotations") &&
//                moduleVersion.getGroup().equals("com.android.support")) {
//            configDependencies.setAnnotationsPresent(true);
//        }
//
//        // now loop on all the artifact for this modules.
//        List<ResolvedArtifact> moduleArtifacts = artifacts.get(moduleVersion);
//
//        if (null == moduleArtifacts) {
//            return;
//        }
//
//        ComponentIdentifier id = resolvedComponentResult.getId();
//        String gradlePath = (id instanceof ProjectComponentIdentifier) ? ((ProjectComponentIdentifier) id)
//                .getProjectPath() : null;
//
//        // 如果同时找到多个依赖，暂时没法判断是那个真正有用
//        for (ResolvedArtifact resolvedArtifact : moduleArtifacts) {
//
//            ResolvedDependencyInfo resolvedDependencyInfo = new ResolvedDependencyInfo(moduleVersion
//                                                                                               .getVersion(),
//                                                                                       moduleVersion
//                                                                                               .getGroup(),
//                                                                                       moduleVersion
//                                                                                               .getName(),
//                                                                                       resolvedArtifact
//                                                                                               .getType(),
//                                                                                       resolvedArtifact
//                                                                                               .getClassifier());
//            resolvedDependencyInfo.setIndent(indent);
//            resolvedDependencyInfo.setGradlePath(gradlePath);
//            resolvedDependencyInfo.setResolvedArtifact(resolvedArtifact);
//
//            //String parentVersionString = parent.getType();
//            String moduleVersonString = moduleVersion.toString() +
//                    "." +
//                    resolvedArtifact.getType() +
//                    "." +
//                    resolvedArtifact.getClassifier() +
//                    "." +
//                    indent;
//            if (null != parent) {
//                if ("awb".equals(parent.getType())) {
//                    moduleVersonString = parent.toString() + "->" + moduleVersonString;
//                }
//            }
//
//
//            String path = computeArtifactPath(moduleVersion, resolvedArtifact);
//            String name = computeArtifactName(moduleVersion, resolvedArtifact);
//
//            File explodedDir = project.file(project.getBuildDir() +
//                                                    "/" +
//                                                    FD_INTERMEDIATES +
//                                                    "/exploded-" +
//                                                    resolvedArtifact.getType().toLowerCase() +
//                                                    "/" +
//                                                    path);
//            resolvedDependencyInfo.setExplodedDir(explodedDir);
//            resolvedDependencyInfo.setDependencyName(name);
//
//            if (null == parent) {
//                parent = resolvedDependencyInfo;
//            } else {
//
//                if (null == resolvedDependencyInfo.getParent()) {
//                    resolvedDependencyInfo.setParent(parent);
//                }
//
//                parent.getChildren().add(resolvedDependencyInfo);
//            }
//            Set<? extends DependencyResult> dependencies = resolvedComponentResult.getAndroidLibraries();
//            for (DependencyResult dep : dependencies) {
//                if (dep instanceof ResolvedDependencyResult) {
//                    ResolvedComponentResult childResolvedComponentResult = ((ResolvedDependencyResult) dep)
//                            .getSelected();
//                    CircleDependencyCheck.DependencyNode childNode = circleDependencyCheck.addDependency(
//                            childResolvedComponentResult.getModuleVersion(),
//                            node,
//                            indent + 1);
//                    CircleDependencyCheck.CircleResult circleResult = circleDependencyCheck.checkCircle(
//                            logger);
//                    if (circleResult.hasCircle) {
//                        logger.warning("[CircleDependency]" +
//                                               StringUtils.join(circleResult.detail, ";"));
//                    } else {
//                        resolveDependency(resolvedDependencyContainer,
//                                          parent,
//                                          ((ResolvedDependencyResult) dep).getSelected(),
//                                          artifacts,
//                                          configDependencies,
//                                          indent + 1,
//                                          circleDependencyCheck,
//                                          childNode);
//                    }
//                }
//            }
//            resolvedDependencyContainer.addDependency(resolvedDependencyInfo);
//        }
//    }
//
//
//    @NonNull
//    private DependencyGraph resolveConfiguration(
//            @NonNull Configuration configuration,
//            @NonNull final VariantDependencies variantDeps,
//            @NonNull Set<AndroidDependency> libsToExplodeOut,
//            @NonNull Set<String> currentUnresolvedDependencies,
//            @Nullable String testedProjectPath,
//            @NonNull Set<String> artifactSet,
//            @NonNull ScopeType scopeType) {
//
//        // collect the artifacts first.
//        Map<ModuleVersionIdentifier, List<ResolvedArtifact>> artifacts = Maps.newHashMap();
//        configuration = collectArtifacts(configuration, artifacts);
//
//        // keep a map of modules already processed so that we don't go through sections of the
//        // graph that have been seen elsewhere.
//        // TODO this is not correct if the requested coordinate is different but keep as is for now
//        Map<ModuleVersionIdentifier, List<DependencyNode>> foundNodes = Maps.newHashMap();
//
//        // get the graph for the Android and Jar dependencies. This does not include
//        // local jars.
//        Map<Object, Dependency> dependencyMap = Maps.newHashMap();
//        List<DependencyNode> dependencies = Lists.newArrayList();
//
//        Set<? extends DependencyResult> dependencyResultSet = configuration.getIncoming()
//                .getResolutionResult().getRoot().getAndroidLibraries();
//
//        // create a container for all the dependency related mutable data, only when creating
//        // the package dependencies for a test project.
//        MutableDependencyDataMap mutableDependencyContainer = MutableDependencyDataMap.newInstance();
//                //scopeType == ScopeType.PACKAGE
//                //    ? MutableDependencyDataMap.newInstance()
//                //    : MutableDependencyDataMap.EMPTY;
//
//        for (DependencyResult dependencyResult : dependencyResultSet) {
//            if (dependencyResult instanceof ResolvedDependencyResult) {
//                addDependency(
//                        mutableDependencyContainer,
//                        ((ResolvedDependencyResult) dependencyResult).getSelected(),
//                        variantDeps,
//                        dependencyMap,
//                        dependencies,
//                        foundNodes,
//                        artifacts,
//                        libsToExplodeOut,
//                        currentUnresolvedDependencies,
//                        testedProjectPath,
//                        Collections.emptyList(),
//                        artifactSet,
//                        scopeType,
//                        false, /*forceProvided*/
//                        0);
//            } else if (dependencyResult instanceof UnresolvedDependencyResult) {
//                ComponentSelector attempted = ((UnresolvedDependencyResult) dependencyResult).getAttempted();
//                if (attempted != null) {
//                    currentUnresolvedDependencies.add(attempted.toString());
//                }
//            }
//        }
//
//        // also need to process local jar files, as they are not processed by the
//        // resolvedConfiguration result. This only includes the local jar files for this project.
//        for (org.gradle.api.artifacts.Dependency dependency : configuration.getAllDependencies()) {
//            if (dependency instanceof SelfResolvingDependency &&
//                    !(dependency instanceof ProjectDependency)) {
//                Set<File> files = ((SelfResolvingDependency) dependency).resolve();
//                for (File localJarFile : files) {
//                    if (DEBUG_DEPENDENCY) {
//                        System.out.println("LOCAL " + configuration.getName() + ": " + localJarFile.getName());
//                    }
//                    // only accept local jar, no other types.
//                    if (!localJarFile.getName().toLowerCase(Locale.getDefault()).endsWith(DOT_JAR)) {
//                        variantDeps.getChecker().handleIssue(
//                                localJarFile.getAbsolutePath(),
//                                SyncIssue.TYPE_NON_JAR_LOCAL_DEP,
//                                SyncIssue.SEVERITY_ERROR,
//                                String.format(
//                                        "Project %s: Only Jar-type local dependencies are supported. Cannot handle: %s",
//                                        project.getName(), localJarFile.getAbsolutePath()));
//                    } else {
//                        JavaDependency localJar = new JavaDependency(localJarFile);
//                        switch (scopeType) {
//                            case PACKAGE:
//                                artifactSet.add(localJar.getCoordinates().getVersionlessId());
//                                break;
//                            case COMPILE:
//                                if (!artifactSet
//                                        .contains(localJar.getCoordinates().getVersionlessId())) {
//                                    mutableDependencyContainer.setProvided(localJar);
//                                }
//                                break;
//                            case COMPILE_ONLY:
//                                // if we only have the compile scope, ignore computation of the
//                                // provided bits.
//                                break;
//                            default:
//                                throw new RuntimeException("unsupported ProvidedComputationAction");
//                        }
//
//                        // add the Dependency to the map
//                        dependencyMap.put(localJar.getAddress(), localJar);
//                        // and add the node to the graph
//                        DependencyNode node = new DependencyNode(
//                                localJar.getAddress(),
//                                NodeType.JAVA,
//                                ImmutableList.of(), // no dependencies
//                                null /*requested coord*/);
//                        dependencies.add(node);
//                    }
//                }
//            }
//        }
//
//        return new DependencyGraph(
//                dependencyMap,
//                dependencies,
//                mutableDependencyContainer);
//    }
//
//    /**
//     * Collects the resolved artifacts and returns a configuration which contains them. If the
//     * configuration has unresolved dependencies we check that we have the latest version of the
//     * Google repository and the Android Support repository and we install them if not. After this,
//     * the resolution is retried with a fresh copy of the configuration, that will contain the newly
//     * updated repositories. If this passes, we return the correct configuration and we fill the
//     * artifacts map.
//     * @param configuration the configuration from which we get the artifacts
//     * @param artifacts the map of artifacts that are being collected
//     * @return a valid configuration that has only resolved dependencies.
//     */
//    private Configuration collectArtifacts(
//            Configuration configuration,
//            Map<ModuleVersionIdentifier,
//            List<ResolvedArtifact>> artifacts) {
//
//        Set<ResolvedArtifact> allArtifacts;
//        // Make a copy because Gradle keeps a per configuration state of resolution that we
//        // need to reset.
//        Configuration configurationCopy = configuration.copyRecursive();
//
//        Set<UnresolvedDependency> unresolvedDependencies =
//                configuration
//                        .getResolvedConfiguration()
//                        .getLenientConfiguration()
//                        .getUnresolvedModuleDependencies();
//
//        if (unresolvedDependencies.isEmpty()) {
//            allArtifacts = configuration.getResolvedConfiguration().getResolvedArtifacts();
//        } else {
//            if (!repositoriesUpdated && sdkLibData.useSdkDownload()) {
//                List<String> repositoryPaths = new ArrayList<>();
//
//                for (UnresolvedDependency dependency : unresolvedDependencies) {
//                    if (isGoogleOwnedDependency(dependency.getSelector())) {
//                        repositoryPaths.add(getRepositoryPath(dependency.getSelector()));
//                    }
//                }
//                sdkLibData.setNeedsCacheReset(sdkHandler.checkResetCache());
//                List<File> updatedRepositories = sdkHandler.getSdkLoader()
//                        .updateRepositories(repositoryPaths, sdkLibData, logger);
//
//                // Adding the updated local maven repositories to the project in order to
//                // bypass the fact that the old repositories contain the unresolved
//                // resolution result.
//                for (File updatedRepository : updatedRepositories) {
//                    project.getRepositories().maven(newRepo -> {
//                        newRepo.setName("Updated " + updatedRepository.getPath());
//                        newRepo.setUrl(updatedRepository.toURI());
//
//                        // Make sure the new repo uses a different cache of resolution results,
//                        // by adding a fake additional URL.
//                        // See Gradle's DependencyResolverIdentifier#forExternalResourceResolver
//                        newRepo.artifactUrls(project.getRootProject().file("sdk-manager"));
//                    });
//                }
//                repositoriesUpdated = true;
//            }
//            if (extraModelInfo.getMode() != STANDARD) {
//                allArtifacts = configurationCopy.getResolvedConfiguration()
//                        .getLenientConfiguration()
//                        .getArtifacts(Specs.satisfyAll());
//            } else {
//                allArtifacts = configurationCopy.getResolvedConfiguration()
//                        .getResolvedArtifacts();
//
//            }
//            // Modify the configuration to the one that passed.
//            configuration = configurationCopy;
//        }
//
//        for (ResolvedArtifact artifact : allArtifacts) {
//            ModuleVersionIdentifier id = artifact.getModuleVersion().getId();
//            List<ResolvedArtifact> moduleArtifacts = artifacts.get(id);
//
//            if (moduleArtifacts == null) {
//                moduleArtifacts = Lists.newArrayList();
//                artifacts.put(id, moduleArtifacts);
//            }
//
//            if (!moduleArtifacts.contains(artifact)) {
//                moduleArtifacts.add(artifact);
//            }
//        }
//        return configuration;
//    }
//
//    /**
//     * Returns the path of an artifact SDK repository.
//     * @param selector the selector of an artifact.
//     * @return a {@code String} containing the path.
//     */
//    private static String getRepositoryPath(ModuleVersionSelector selector) {
//        return DetailsTypes.MavenType.getRepositoryPath(
//                selector.getGroup(), selector.getName(), selector.getVersion());
//    }
//
//    private boolean isGoogleOwnedDependency(ModuleVersionSelector selector) {
//        return selector.getGroup().startsWith(SdkConstants.ANDROID_SUPPORT_ARTIFACT_PREFIX)
//                || selector.getGroup().startsWith(SdkConstants.GOOGLE_SUPPORT_ARTIFACT_PREFIX)
//                || selector.getGroup().startsWith(SdkConstants.FIREBASE_ARTIFACT_PREFIX);
//    }
//
//    private static void printIndent(int indent, @NonNull String message) {
//        for (int i = 0 ; i < indent ; i++) {
//            System.out.print("\t");
//        }
//
//        System.out.println(message);
//    }
//
//    private void addDependency(
//            @NonNull MutableDependencyDataMap mutableDependencyDataMap,
//            @NonNull ResolvedComponentResult resolvedComponentResult,
//            @NonNull VariantDependencies configDependencies,
//            @NonNull Map<Object, Dependency> outDependencyMap,
//            @NonNull List<DependencyNode> outDependencies,
//            @NonNull Map<ModuleVersionIdentifier, List<DependencyNode>> alreadyFoundNodes,
//            @NonNull Map<ModuleVersionIdentifier, List<ResolvedArtifact>> artifacts,
//            @NonNull Set<AndroidDependency> libsToExplodeOut,
//            @NonNull Set<String> currentUnresolvedDependencies,
//            @Nullable String testedProjectPath,
//            @NonNull List<String> projectChain,
//            @NonNull Set<String> artifactSet,
//            @NonNull ScopeType scopeType,
//            boolean forceProvided,
//            int indent) {
//
//        ModuleVersionIdentifier moduleVersion = resolvedComponentResult.getModuleVersion();
//        if (configDependencies.getChecker().checkForExclusion(moduleVersion)) {
//            return;
//        }
//
//        if (moduleVersion.getName().equals("support-annotations") &&
//                moduleVersion.getGroup().equals("com.android.support")) {
//            configDependencies.setAnnotationsPresent(true);
//        }
//
//        List<DependencyNode> nodesForThisModule = alreadyFoundNodes.get(moduleVersion);
//
//        if (nodesForThisModule != null) {
//            if (DEBUG_DEPENDENCY) {
//                printIndent(indent, "FOUND DEP: " + moduleVersion.getName());
//            }
//            outDependencies.addAll(nodesForThisModule);
//
//        } else {
//            if (DEBUG_DEPENDENCY) {
//                printIndent(indent, "NOT FOUND: " + moduleVersion.getName());
//            }
//            // new dependency artifact! Might be a jar, an atom or a library
//
//            // get the associated gradlepath
//            ComponentIdentifier id = resolvedComponentResult.getId();
//            String gradlePath = (id instanceof ProjectComponentIdentifier) ?
//                    ((ProjectComponentIdentifier) id).getProjectPath() : null;
//
//            // check if this is a tested app project (via a separate test module).
//            // In which case, all the dependencies must become provided.
//            boolean childForceProvided = forceProvided;
//            if (scopeType == ScopeType.COMPILE &&
//                    testedProjectPath != null && testedProjectPath.equals(gradlePath)) {
//                childForceProvided = true;
//            }
//
//            // get the nested components first.
//            Set<? extends DependencyResult> dependencies = resolvedComponentResult.getAndroidLibraries();
//            List<DependencyNode> transitiveDependencies = Lists.newArrayListWithExpectedSize(dependencies.size());
//
//            for (DependencyResult dependencyResult : dependencies) {
//                if (dependencyResult instanceof ResolvedDependencyResult) {
//                    ResolvedComponentResult selected =
//                            ((ResolvedDependencyResult) dependencyResult).getSelected();
//
//                    List<String> newProjectChain = projectChain;
//
//                    ComponentIdentifier identifier = selected.getId();
//                    if (identifier instanceof ProjectComponentIdentifier) {
//                        String projectPath =
//                                ((ProjectComponentIdentifier) identifier).getProjectPath();
//
//                        int index = projectChain.indexOf(projectPath);
//                        if (index != -1) {
//                            projectChain.add(projectPath);
//                            String path = Joiner
//                                    .on(" -> ")
//                                    .join(projectChain.subList(index, projectChain.size()));
//
//                            throw new CircularReferenceException(
//                                    "Circular reference between projects: " + path);
//                        }
//
//                        newProjectChain = Lists.newArrayList();
//                        newProjectChain.addAll(projectChain);
//                        newProjectChain.add(projectPath);
//                    }
//
//                    addDependency(
//                            mutableDependencyDataMap,
//                            selected,
//                            configDependencies,
//                            outDependencyMap,
//                            transitiveDependencies,
//                            alreadyFoundNodes,
//                            artifacts,
//                            libsToExplodeOut,
//                            currentUnresolvedDependencies,
//                            testedProjectPath,
//                            newProjectChain,
//                            artifactSet,
//                            scopeType,
//                            childForceProvided,
//                            indent + 1);
//                } else if (dependencyResult instanceof UnresolvedDependencyResult) {
//                    ComponentSelector attempted = ((UnresolvedDependencyResult) dependencyResult).getAttempted();
//                    if (attempted != null) {
//                        currentUnresolvedDependencies.add(attempted.toString());
//                    }
//                }
//            }
//
//            if (DEBUG_DEPENDENCY) {
//                printIndent(indent, "BACK2: " + moduleVersion.getName());
//                printIndent(indent, "NESTED DEPS: " + transitiveDependencies.size());
//            }
//
//            // now loop on all the artifact for this modules.
//            List<ResolvedArtifact> moduleArtifacts = artifacts.get(moduleVersion);
//
//            if (moduleArtifacts != null) {
//                for (ResolvedArtifact artifact : moduleArtifacts) {
//                    MavenCoordinatesImpl mavenCoordinates = createMavenCoordinates(artifact);
//
//                    // check if we already have a Dependency object for this coordinate.
//                    Dependency alreadyCreatedDependency = outDependencyMap.get(mavenCoordinates);
//                    NodeType nodeType = null;
//                    if (alreadyCreatedDependency != null) {
//                        if (alreadyCreatedDependency instanceof AndroidDependency) {
//                            nodeType = NodeType.ANDROID;
//                        } else if (alreadyCreatedDependency instanceof JavaDependency) {
//                            nodeType = NodeType.JAVA;
//                        } else if (alreadyCreatedDependency instanceof AtomDependency) {
//                            nodeType = NodeType.ATOM;
//                        } else {
//                            throw new RuntimeException("Unknown type of Dependency");
//                        }
//
//                    } else {
//                        boolean provided = forceProvided;
//                        String coordKey = mavenCoordinates.getVersionlessId();
//                        if (scopeType == ScopeType.PACKAGE) {
//                            artifactSet.add(coordKey);
//                        } else if (scopeType == ScopeType.COMPILE) {
//                            provided |= !artifactSet.contains(coordKey);
//                        }
//
//                        // if we don't have one, need to create it.
//                        if (EXT_LIB_ARCHIVE.equals(artifact.getExtension())) {
//                            if (DEBUG_DEPENDENCY) {
//                                printIndent(indent, "TYPE: AAR");
//                            }
//
//                            String path = computeArtifactPath(moduleVersion, artifact);
//                            String name = computeArtifactName(moduleVersion, artifact);
//
//                            if (DEBUG_DEPENDENCY) {
//                                printIndent(indent, "NAME: " + name);
//                                printIndent(indent, "PATH: " + path);
//                            }
//
//                            final String variantName = artifact.getClassifier();
//
//                            AndroidDependency androidDependency;
//                            Project subProject = null;
//
//                            boolean isSubProject = false;
//                            if (gradlePath != null) {
//                                // this is a sub-module. Get the matching object file
//                                // to query its build output;
//                                subProject = project.findProject(gradlePath);
//
//                                // this could be a simple project wrapping an aar file, so we check the
//                                // presence of the android plugin to make sure it's an android module.
//                                isSubProject =
//                                        subProject.getPlugins().hasPlugin("com.android.library")
//                                                ||
//                                                subProject.getPlugins()
//                                                        .hasPlugin("com.android.model.library");
//                            }
//
//                            if (isSubProject) {
//                                // if there is a variant name then we use it for the leaf
//                                // (this means the subproject is publishing all its variants and each
//                                // artifact has a classifier that is the variant Name).
//                                // Otherwise the subproject only outputs a single artifact
//                                // and the location was set to default.
//                                String pathLeaf = variantName != null ? variantName : "default";
//
//                                File stagingDir = FileUtils.join(
//                                        subProject.getBuildDir(),
//                                        FD_INTERMEDIATES, DIR_BUNDLES,
//                                        pathLeaf);
//
//                                androidDependency = AndroidDependency.createStagedAarLibrary(
//                                        artifact.getFile(),
//                                        mavenCoordinates,
//                                        name,
//                                        gradlePath,
//                                        stagingDir,
//                                        variantName);
//
//                            } else {
//                                // If the build cache is used, we create and cache the exploded aar
//                                // inside the build cache directory; otherwise, we explode the aar
//                                // to a location inside the project's build directory.
//                                Optional<FileCache> buildCache =
//                                        AndroidGradleOptions.getBuildCache(project);
//                                File explodedDir;
//                                if (PrepareLibraryTask.shouldUseBuildCache(
//                                        buildCache.isPresent(), mavenCoordinates)) {
//                                    try {
//                                        explodedDir = buildCache.get().getFileInCache(
//                                                PrepareLibraryTask.getBuildCacheInputs(
//                                                        artifact.getFile()));
//                                    } catch (IOException e) {
//                                        throw new UncheckedIOException(e);
//                                    }
//                                } else {
//                                    Preconditions.checkState(
//                                            !AndroidGradleOptions
//                                                    .isImprovedDependencyResolutionEnabled(project),
//                                            "Improved dependency resolution must be used with "
//                                                    + "build cache.");
//
//                                    explodedDir = FileUtils.join(
//                                            project.getBuildDir(),
//                                            FD_INTERMEDIATES,
//                                            EXPLODED_AAR,
//                                            path);
//                                }
//
//                                androidDependency = AndroidDependency.createExplodedAarLibrary(
//                                        artifact.getFile(),
//                                        mavenCoordinates,
//                                        name,
//                                        null /*gradlePath*/,
//                                        explodedDir);
//                            }
//
//                            alreadyCreatedDependency = androidDependency;
//                            nodeType = NodeType.ANDROID;
//                            outDependencyMap.put(
//                                    androidDependency.getAddress(), androidDependency);
//
//                            // only record the libraries to explode if they are remote and not
//                            // sub-modules.
//                            if (!isSubProject) {
//                                libsToExplodeOut.add(androidDependency);
//                            }
//
//                            // check this aar does not have a dependency on an atom, as this would
//                            // not work.
//                            if (containsDirectDependency(transitiveDependencies, NodeType.ATOM)) {
//                                configDependencies.getChecker()
//                                        .handleIssue(
//                                                createMavenCoordinates(artifact).toString(),
//                                                SyncIssue.TYPE_AAR_DEPEND_ON_ATOM,
//                                                SyncIssue.SEVERITY_ERROR,
//                                                String.format(
//                                                        "Module '%s' depends on one or more Android Atoms but is a library",
//                                                        moduleVersion));
//                            }
//                        } else if (EXT_ATOMBUNDLE_ARCHIVE.equals(artifact.getExtension())) {
//                            if (provided) {
//                                configDependencies.getChecker()
//                                        .handleIssue(
//                                                createMavenCoordinates(artifact).toString(),
//                                                SyncIssue.TYPE_ATOM_DEPENDENCY_PROVIDED,
//                                                SyncIssue.SEVERITY_ERROR,
//                                                String.format(
//                                                        "Module '%s' is an Atom, which cannot be a provided dependency",
//                                                        moduleVersion));
//                            }
//                            if (DEBUG_DEPENDENCY) {
//                                printIndent(indent, "TYPE: ATOM");
//                            }
//
//                            // if this is a package scope, then skip the dependencies.
//                            if (scopeType == ScopeType.PACKAGE) {
//                                recursiveSkip(
//                                        mutableDependencyDataMap,
//                                        transitiveDependencies,
//                                        outDependencyMap,
//                                        true /*skipAndroidDep*/,
//                                        true /*skipJavaDep*/);
//                            }
//
//                            String path = computeArtifactPath(moduleVersion, artifact);
//                            String name = computeArtifactName(moduleVersion, artifact);
//
//                            if (DEBUG_DEPENDENCY) {
//                                printIndent(indent, "NAME: " + name);
//                                printIndent(indent, "PATH: " + path);
//                            }
//
//                            final String variantName = artifact.getClassifier();
//
//                            // if there is a variant name then we use it for the leaf
//                            // (this means the subproject is publishing all its variants and each
//                            // artifact has a classifier that is the variant Name).
//                            // Otherwise the subproject only outputs a single artifact
//                            // and the location was set to default.
//                            String pathLeaf = variantName != null ? variantName : "default";
//
//                            Project subProject = project.findProject(gradlePath);
//                            File stagingDir = FileUtils.join(
//                                    subProject.getBuildDir(),
//                                    FD_INTERMEDIATES, DIR_ATOMBUNDLES,
//                                    pathLeaf);
//
//                            AtomDependency atomDependency = new AtomDependency(
//                                    artifact.getFile(),
//                                    mavenCoordinates,
//                                    name,
//                                    gradlePath,
//                                    stagingDir,
//                                    moduleVersion.getName() /* atomName */,
//                                    variantName);
//
//                            alreadyCreatedDependency = atomDependency;
//                            nodeType = NodeType.ATOM;
//
//                            outDependencyMap.put(atomDependency.getAddress(), atomDependency);
//
//                        } else if (EXT_JAR.equals(artifact.getExtension())) {
//                            if (DEBUG_DEPENDENCY) {
//                                printIndent(indent, "TYPE: JAR");
//                            }
//                            boolean isRootOfSeparateTestedApp = testedProjectPath != null
//                                    && testedProjectPath.equals(gradlePath);
//                            // check this jar does not have a dependency on an library, as this would not work.
//                            if (containsDirectDependency(transitiveDependencies, NodeType.ANDROID)) {
//                                // there is one case where it's ok to have a jar depend on aars:
//                                // when a test project tests a separate app project, the code of the
//                                // app is published as a jar, but it brings in the dependencies
//                                // of the app (which can be aars).
//                                // we know we're in that case if testedProjectPath is non null, so we
//                                // can detect this an accept it.
//                                if (!isRootOfSeparateTestedApp) {
//                                    configDependencies.getChecker()
//                                            .handleIssue(
//                                                    createMavenCoordinates(artifact).toString(),
//                                                    SyncIssue.TYPE_JAR_DEPEND_ON_AAR,
//                                                    SyncIssue.SEVERITY_ERROR,
//                                                    String.format(
//                                                            "Module '%s' depends on one or more Android Libraries but is a jar",
//                                                            moduleVersion));
//                                }
//                            }
//
//                            // check this jar does not have a dependency on an atom, as this would not work.
//                            if (containsDirectDependency(transitiveDependencies, NodeType.ATOM)) {
//                                configDependencies.getChecker()
//                                        .handleIssue(
//                                                createMavenCoordinates(artifact).toString(),
//                                                SyncIssue.TYPE_JAR_DEPEND_ON_ATOM,
//                                                SyncIssue.SEVERITY_ERROR,
//                                                String.format(
//                                                        "Module '%s' depends on one or more Android Atoms but is a jar",
//                                                        moduleVersion));
//                            }
//
//                            JavaDependency javaDependency = new JavaDependency(
//                                    artifact.getFile(),
//                                    mavenCoordinates,
//                                    computeArtifactName(moduleVersion, artifact),
//                                    gradlePath);
//
//                            alreadyCreatedDependency = javaDependency;
//                            nodeType = NodeType.JAVA;
//                            outDependencyMap.put(javaDependency.getAddress(), javaDependency);
//
//                            if (isRootOfSeparateTestedApp) {
//                                // the jar and dependencies of the separate tested app must be excluded
//                                // from the test app, so we mark the package scope as skipped and the
//                                // compile scope as provided.
//                                if (scopeType == ScopeType.PACKAGE) {
//                                    mutableDependencyDataMap.skip(javaDependency);
//                                    recursiveSkip(
//                                            mutableDependencyDataMap,
//                                            transitiveDependencies,
//                                            outDependencyMap,
//                                            true /*skipAndroidDep*/,
//                                            true /*skipJavaDep*/);
//                                } else {
//                                    // the current one is done below, so we only need to do the
//                                    // recursive ones.
//                                    recursiveProvided(
//                                            mutableDependencyDataMap,
//                                            transitiveDependencies,
//                                            outDependencyMap,
//                                            true /*skipAndroidDep*/,
//                                            true /*skipJavaDep*/);
//
//                                    provided = true;
//                                }
//                            }
//
//                            if (DEBUG_DEPENDENCY) {
//                                printIndent(indent, "JAR-INFO: " + javaDependency.toString());
//                            }
//
//                        } else if (EXT_ANDROID_PACKAGE.equals(artifact.getExtension())) {
//                            String name = computeArtifactName(moduleVersion, artifact);
//
//                            configDependencies.getChecker().handleIssue(
//                                    name,
//                                    SyncIssue.TYPE_DEPENDENCY_IS_APK,
//                                    SyncIssue.SEVERITY_ERROR,
//                                    String.format(
//                                            "Dependency %s on project %s resolves to an APK archive "
//                                                    +
//                                                    "which is not supported as a compilation dependency. File: %s",
//                                            name, project.getName(), artifact.getFile()));
//                        } else if ("apklib".equals(artifact.getExtension())) {
//                            String name = computeArtifactName(moduleVersion, artifact);
//
//                            configDependencies.getChecker().handleIssue(
//                                    name,
//                                    SyncIssue.TYPE_DEPENDENCY_IS_APKLIB,
//                                    SyncIssue.SEVERITY_ERROR,
//                                    String.format(
//                                            "Packaging for dependency %s is 'apklib' and is not supported. "
//                                                    +
//                                                    "Only 'aar' libraries are supported.", name));
//                        } else if ("awb".equals(artifact.getExtension())) {
//
//                            break;
//                        } else if ("solib".equals(artifact.getExtension())) {
//
//                            break;
//                        } else {
//                            String name = computeArtifactName(moduleVersion, artifact);
//
//                            logger.warning(String.format(
//                                    "Unrecognized dependency: '%s' (type: '%s', extension: '%s')",
//                                    name, artifact.getType(), artifact.getExtension()));
//                        }
//
//                        if (provided && alreadyCreatedDependency != null) {
//                            mutableDependencyDataMap.setProvided(alreadyCreatedDependency);
//                        }
//                    }
//
//                    if (alreadyCreatedDependency != null) {
//                        // all we need is to create a DependencyNode
//                        DependencyNode node = new DependencyNode(
//                                alreadyCreatedDependency.getAddress(),
//                                nodeType,
//                                transitiveDependencies,
//                                null /*requested Coordinates*/);
//                        outDependencies.add(node);
//
//                        if (nodesForThisModule == null) {
//                            nodesForThisModule = Lists.newArrayList(node);
//                            alreadyFoundNodes.put(moduleVersion, nodesForThisModule);
//                        } else {
//                            nodesForThisModule.add(node);
//                        }
//                    }
//                }
//            }
//
//            if (DEBUG_DEPENDENCY) {
//                printIndent(indent, "DONE: " + moduleVersion.getName());
//            }
//        }
//    }
//
//    @NonNull
//    private static MavenCoordinatesImpl createMavenCoordinates(
//            @NonNull ResolvedArtifact resolvedArtifact) {
//        return new MavenCoordinatesImpl(
//                resolvedArtifact.getModuleVersion().getId().getGroup(),
//                resolvedArtifact.getModuleVersion().getId().getName(),
//                resolvedArtifact.getModuleVersion().getId().getVersion(),
//                resolvedArtifact.getExtension(),
//                resolvedArtifact.getClassifier());
//    }
//
//    private static boolean containsDirectDependency(
//            @NonNull List<DependencyNode> dependencies,
//            @NonNull NodeType nodeType) {
//        return dependencies.stream().anyMatch(
//                dependencyNode -> dependencyNode.getNodeType() == nodeType);
//    }
//
//    private static void recursiveSkip(
//            @NonNull MutableDependencyDataMap mutableDependencyDataMap,
//            @NonNull List<DependencyNode> nodes,
//            @NonNull Map<Object, Dependency> dependencyMap,
//            boolean skipAndroidDependency,
//            boolean skipJavaDependency) {
//        for (DependencyNode node : nodes) {
//            Dependency dep = dependencyMap.get(node.getAddress());
//
//            if (skipAndroidDependency) {
//                if (dep instanceof AndroidDependency) {
//                    mutableDependencyDataMap.skip(dep);
//                }
//            }
//
//            if (skipJavaDependency) {
//                if (dep instanceof JavaDependency) {
//                    mutableDependencyDataMap.skip(dep);
//                }
//            }
//
//            recursiveSkip(
//                    mutableDependencyDataMap,
//                    node.getAndroidLibraries(),
//                    dependencyMap,
//                    skipAndroidDependency,
//                    skipJavaDependency);
//        }
//    }
//
//    private static void recursiveProvided(
//            @NonNull MutableDependencyDataMap mutableDependencyDataMap,
//            @NonNull List<DependencyNode> nodes,
//            @NonNull Map<Object, Dependency> dependencyMap,
//            boolean skipAndroidDependency,
//            boolean skipJavaDependency) {
//        for (DependencyNode node : nodes) {
//            Dependency dep = dependencyMap.get(node.getAddress());
//
//            if (skipAndroidDependency) {
//                if (dep instanceof AndroidDependency) {
//                    mutableDependencyDataMap.setProvided(dep);
//                }
//            }
//
//            if (skipJavaDependency) {
//                if (dep instanceof JavaDependency) {
//                    mutableDependencyDataMap.setProvided(dep);
//                }
//            }
//
//            recursiveProvided(
//                    mutableDependencyDataMap,
//                    node.getAndroidLibraries(),
//                    dependencyMap,
//                    skipAndroidDependency,
//                    skipJavaDependency);
//        }
//    }
//
//    @NonNull
//    private String computeArtifactPath(
//            @NonNull ModuleVersionIdentifier moduleVersion,
//            @NonNull ResolvedArtifact artifact) {
//        StringBuilder pathBuilder = new StringBuilder(
//                moduleVersion.getGroup().length()
//                        + moduleVersion.getName().length()
//                        + moduleVersion.getVersion().length()
//                        + 10); // in case of classifier which is rare.
//
//        pathBuilder.append(normalize(logger, moduleVersion, moduleVersion.getGroup()))
//                .append(File.separatorChar)
//                .append(normalize(logger, moduleVersion, moduleVersion.getName()))
//                .append(File.separatorChar)
//                .append(normalize(logger, moduleVersion,
//                        moduleVersion.getVersion()));
//
//        if (artifact.getClassifier() != null && !artifact.getClassifier().isEmpty()) {
//            pathBuilder.append(File.separatorChar).append(normalize(logger, moduleVersion,
//                    artifact.getClassifier()));
//        }
//
//        return pathBuilder.toString();
//    }
//
//    @NonNull
//    private static String computeArtifactName(
//            @NonNull ModuleVersionIdentifier moduleVersion,
//            @NonNull ResolvedArtifact artifact) {
//        StringBuilder nameBuilder = new StringBuilder(
//                moduleVersion.getGroup().length()
//                        + moduleVersion.getName().length()
//                        + moduleVersion.getVersion().length()
//                        + 10); // in case of classifier which is rare.
//
//        nameBuilder.append(moduleVersion.getGroup())
//                .append(':')
//                .append(moduleVersion.getName())
//                .append(':')
//                .append(moduleVersion.getVersion());
//
//        if (artifact.getClassifier() != null && !artifact.getClassifier().isEmpty()) {
//            nameBuilder.append(':').append(artifact.getClassifier());
//        }
//
//        return nameBuilder.toString();
//    }
//
//    /**
//     * Normalize a path to remove all illegal characters for all supported operating systems.
//     * {@see http://en.wikipedia.org/wiki/Filename#Comparison%5Fof%5Ffile%5Fname%5Flimitations}
//     *
//     * @param id the module coordinates that generated this path
//     * @param path the proposed path name
//     * @return the normalized path name
//     */
//    static String normalize(ILogger logger, ModuleVersionIdentifier id, String path) {
//        if (path == null || path.isEmpty()) {
//            logger.verbose(String.format(
//                    "When unzipping library '%s:%s:%s, either group, name or version is empty",
//                    id.getGroup(), id.getName(), id.getVersion()));
//            return path;
//        }
//        // list of illegal characters
//        String normalizedPath = path.replaceAll("[%<>:\"/?*\\\\]", "@");
//        if (normalizedPath == null || normalizedPath.isEmpty()) {
//            // if the path normalization failed, return the original path.
//            logger.verbose(String.format(
//                    "When unzipping library '%s:%s:%s, the normalized '%s' is empty",
//                    id.getGroup(), id.getName(), id.getVersion(), path));
//            return path;
//        }
//        try {
//            int pathPointer = normalizedPath.length() - 1;
//            // do not end your path with either a dot or a space.
//            String suffix = "";
//            while (pathPointer >= 0 && (normalizedPath.charAt(pathPointer) == '.'
//                    || normalizedPath.charAt(pathPointer) == ' ')) {
//                pathPointer--;
//                suffix += "@";
//            }
//            if (pathPointer < 0) {
//                throw new RuntimeException(String.format(
//                        "When unzipping library '%s:%s:%s, " +
//                        "the path '%s' cannot be transformed into a valid directory name",
//                        id.getGroup(), id.getName(), id.getVersion(), path));
//            }
//            return normalizedPath.substring(0, pathPointer + 1) + suffix;
//        } catch (Exception e) {
//            logger.error(e, String.format(
//                    "When unzipping library '%s:%s:%s', " +
//                    "Path normalization failed for input %s",
//                    id.getGroup(), id.getName(), id.getVersion(), path));
//            return path;
//        }
//    }
//
//    public void setSdkLibData(@NonNull SdkLibData sdkLibData) {
//        this.sdkLibData = sdkLibData;
//    }
//}
