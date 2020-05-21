package com.taobao.android.builder.insant;

import com.alibaba.fastjson.JSON;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.transform.*;
import com.android.build.gradle.api.BaseVariantOutput;
import com.android.build.gradle.internal.ApkDataUtils;
import com.android.build.gradle.internal.PostprocessingFeatures;
import com.android.build.gradle.internal.api.AppVariantContext;
import com.android.build.gradle.internal.api.AppVariantOutputContext;
import com.android.build.gradle.internal.api.AwbTransform;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.pipeline.InjectTransform;
import com.android.build.gradle.internal.pipeline.IntermediateFolderUtils;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.publishing.AndroidArtifacts;
import com.android.build.gradle.internal.scope.ApkData;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.WorkLimiter;
import com.android.build.gradle.internal.transforms.ProGuardTransform;

import com.android.build.gradle.options.BooleanOption;
import com.android.builder.model.AndroidLibrary;

import com.android.builder.utils.ExceptionConsumer;
import com.android.builder.utils.FileCache;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import com.google.common.util.concurrent.SettableFuture;
import com.google.gson.Gson;
import com.taobao.android.builder.AtlasBuildContext;
import com.taobao.android.builder.dependency.AtlasDependencyTree;
import com.taobao.android.builder.dependency.model.AwbBundle;
import com.taobao.android.builder.extension.TBuildConfig;
import com.taobao.android.builder.tasks.manager.transform.MtlInjectTransform;
import com.taobao.android.builder.tools.ReflectUtils;
import com.taobao.android.builder.tools.TransformInputUtils;
import com.taobao.android.builder.tools.cache.Cache;
import com.taobao.android.builder.tools.log.FileLogger;
import com.taobao.android.builder.tools.proguard.AtlasProguardHelper;
import com.taobao.android.builder.tools.proguard.AwbProguardConfiguration;
import com.taobao.android.provider.MainDexListProvider;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import proguard.ClassPathEntry;
import proguard.Configuration;

import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.ALL;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.MODULE;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.CONSUMER_PROGUARD_RULES;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.METADATA_VALUES;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH;
import static com.android.builder.model.AndroidProject.FD_OUTPUTS;

/**
 * FakeProguardTransform
 *
 * @author zhayu.ll
 * @date 18/10/18
 */
public class DelegateProguardTransform extends MtlInjectTransform {

    private final TBuildConfig buildConfig;

    private Configuration configuration;

    private static final String VERSION = "1.0";

    private static org.gradle.api.logging.Logger sLogger = null;

    private static final String CHANGE_ENTRY_KEY = "instantpatch_change_classpathEntries";

    private static final String CHANGE_CFG_KEY = "proguard_cfg_changed";

    private static final String WHERE_NOTE_KEY = "class_not_found_note";


    private ProGuardTransform proGuardTransform;

    List<File> defaultProguardFiles = new ArrayList<>();

    public DelegateProguardTransform(AppVariantContext appVariantContext, ApkData apkData) {
        super(appVariantContext, apkData);
        proGuardTransform = new ProGuardTransform(appVariantContext.getScope());
        this.buildConfig = appVariantContext.getAtlasExtension().getTBuildConfig();
        sLogger = appVariantContext.getProject().getLogger();
        configuration = (Configuration) ReflectUtils.getField(proGuardTransform,"configuration");

    }

    @Override
    public boolean updateNextTransformInput() {
        return false;
    }

    @Override
    public String getName() {
        return "delegateProguardTransform";
    }

    @Override
    public Set<QualifiedContent.ContentType> getInputTypes() {
        return TransformManager.CONTENT_CLASS;
    }

    @Override
    public Set<? super QualifiedContent.Scope> getScopes() {
        return TransformManager.SCOPE_FULL_PROJECT;
    }

    @Override
    public boolean isIncremental() {
        return false;
    }

    @Override
    public void transform(TransformInvocation transformInvocation) throws TransformException, InterruptedException, IOException {
        super.transform(transformInvocation);


        System.getProperties().setProperty(WHERE_NOTE_KEY, new File(appVariantContext.getScope().getGlobalScope().getOutputsDir(),"warning-classnotfound-note.properties").getPath());

        PostprocessingFeatures postprocessingFeatures = scope.getPostprocessingFeatures();
        if (postprocessingFeatures != null) {
            proGuardTransform.setActions(postprocessingFeatures);
        }

        Callable<Collection<File>> proguardConfigFiles = scope::getProguardFiles;

        defaultProguardFiles.addAll(appVariantContext.getVariantData().getVariantConfiguration().getBuildType().getProguardFiles());

        Collections.sort(defaultProguardFiles);
        FileCache.Inputs.Builder builder = new FileCache.Inputs.Builder(FileCache.Command.EXTRACT_DESUGAR_JAR);

        builder.putString(getName(),VERSION);

        defaultProguardFiles.forEach(file -> builder.putFile(file.getName(), file, FileCache.FileProperties.HASH));

        FileCache fileCache = getUserIntermediatesCache();
        FileCache.Inputs inputs = builder.build();
        FileCache.QueryResult queryResult= null;
        try {
             queryResult = fileCache.createFileInCacheIfAbsent(inputs, cacheFile -> cacheFile.createNewFile());
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
        if (queryResult != null && queryResult.getQueryEvent().equals(FileCache.QueryEvent.HIT)){
            System.getProperties().setProperty(CHANGE_CFG_KEY, "false");
        }else {
            System.getProperties().setProperty(CHANGE_CFG_KEY, "true");

        }

        ConfigurableFileCollection configurationFiles = null;
        final InternalArtifactType aaptProguardFileType =
                scope.consumesFeatureJars()
                        ? InternalArtifactType.MERGED_AAPT_PROGUARD_FILE
                        : InternalArtifactType.AAPT_PROGUARD_FILE;

//               if (buildConfig.getConsumerProguardEnabled()) {
        configurationFiles =
                appVariantContext.getProject().files(
                        proguardConfigFiles,
                        scope.getArtifacts().getFinalArtifactFiles(aaptProguardFileType),
                        scope.getArtifactFileCollection(
                                RUNTIME_CLASSPATH, ALL, CONSUMER_PROGUARD_RULES));
//               }

        if (scope.getType().isHybrid() && scope.getType().isBaseModule()) {
            Callable<Collection<File>> consumerProguardFiles = scope::getConsumerProguardFiles;
            configurationFiles.from(consumerProguardFiles);
        }

        maybeAddFeatureProguardRules(scope, configurationFiles);

        if (buildConfig.getConsumerProguardEnabled()) {
            defaultProguardFiles.addAll(configurationFiles.getFiles());
        }
//        proGuardTransform.setConfigurationFiles(configurationFiles);

        if (scope.getVariantData().getType().isAar()) {
            proGuardTransform.keep("class **.R");
            proGuardTransform.keep("class **.R$*");
        }

        List<AwbBundle> awbBundles = AtlasBuildContext.androidDependencyTrees.get(
                appVariantContext.getScope().getVariantConfiguration().getFullName()).getAwbBundles();
        if (awbBundles != null && awbBundles.size() > 0) {
            File bundleRKeepFile = new File(appVariantContext.getBaseVariantData().getScope().getGlobalScope().getIntermediatesDir(), "awb-proguard/bundleRKeep.cfg");
            if (!bundleRKeepFile.getParentFile().exists()) {
                bundleRKeepFile.getParentFile().mkdirs();
            }

            StringBuilder keepRStr = new StringBuilder();
            for (AwbBundle bundleItem : awbBundles) {
                keepRStr.append(String.format("-keep class %s.R{*;}\n", bundleItem.bundleInfo.getPkgName()));
                keepRStr.append(String.format("-keep class %s.R$*{*;}\n", bundleItem.bundleInfo.getPkgName()));
            }
            try {
                BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(bundleRKeepFile));
                bufferedWriter.write(keepRStr.toString());
                bufferedWriter.flush();
                IOUtils.closeQuietly(bufferedWriter);
                FileLogger.getInstance("proguard").log("R keep infos: " + keepRStr);
            } catch (IOException e) {
                throw new RuntimeException("generate bundleRkeepFile failed", e);
            }
            appVariantContext.getBaseVariantData().getVariantConfiguration().getBuildType().getProguardFiles().add(bundleRKeepFile);
            defaultProguardFiles.add(bundleRKeepFile);
        }

        //apply bundle Inout
        applyBundleInOutConfigration(appVariantContext);

        //apply bundle's configuration, Switch control
        if (buildConfig.isBundleProguardConfigEnabled() && !buildConfig.getConsumerProguardEnabled()) {
            applyBundleProguardConfigration(appVariantContext);
        }

        proGuardTransform.setConfigurationFiles(appVariantContext.getScope().getGlobalScope().getProject().files(defaultProguardFiles));

        //apply mapping
        applyMapping(appVariantContext);



        Collection<TransformInput>transformInputs= getAllInput();

        if (appVariantContext.getBuildType().getPatchConfig().isCreateIPatch()){
            collectPatchInfo(transformInputs);

        }


        //set output
        File proguardOutFile = new File(appVariantContext.getProject().getBuildDir(), "outputs/proguard.cfg");
        proGuardTransform.printconfiguration(proguardOutFile);
        SettableFuture<TransformOutputProvider> resultFuture = SettableFuture.create();

        new WorkLimiter(4).limit(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                try {
                    Method m = ProGuardTransform.class.getDeclaredMethod("doMinification", Collection.class, Collection.class, TransformOutputProvider.class);
                    m.setAccessible(true);
                    m.invoke(proGuardTransform, transformInputs, transformInvocation.getReferencedInputs(), transformInvocation.getOutputProvider());
                    if (!appVariantContext.getScope().getOutputProguardMappingFile().isFile()) {
                        Files.asCharSink(appVariantContext.getScope().getOutputProguardMappingFile(), Charsets.UTF_8).write("");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return null;
            }
        });

        IntermediateFolderUtils folderUtils = (IntermediateFolderUtils) ReflectUtils.getField(transformInvocation.getOutputProvider(), "folderUtils");
        AtlasBuildContext.atlasMainDexHelperMap.get(appVariantContext.getVariantName()).addAllMainDexJars(FileUtils.listFiles(folderUtils.getRootFolder(), new String[]{"jar"}, true));


    }

    private Collection<TransformInput> getAllInput() {
        Collection<JarInput> jarInputs = new HashSet<>();
        Collection<DirectoryInput> directoryInputs = new HashSet<>();
        Collection<File> jars = AtlasBuildContext.atlasMainDexHelperMap.get(appVariantContext.getVariantName()).getAllMainDexJars();
        jars.forEach(new Consumer<File>() {
            @Override
            public void accept(File file) {
                jarInputs.add(TransformInputUtils.makeJarInput(file, appVariantContext));
            }
        });

        Collection<File> dirs = AtlasBuildContext.atlasMainDexHelperMap.get(appVariantContext.getVariantName()).getInputDirs();
        dirs.forEach(new Consumer<File>() {
            @Override
            public void accept(File file) {
                directoryInputs.add(TransformInputUtils.makeDirectoryInput(file));
            }
        });

        if (appVariantContext.getAtlasExtension().isAppBundlesEnabled()) {

            getAppVariantOutputContext().getAwbTransformMap().values().forEach(new Consumer<AwbTransform>() {
                @Override
                public void accept(AwbTransform awbTransform) {
                    if (!awbTransform.getAwbBundle().dynamicFeature) {
                        awbTransform.getInputDirs().forEach(new Consumer<File>() {
                            @Override
                            public void accept(File file) {
                                directoryInputs.add(TransformInputUtils.makeDirectoryInput(file));
                            }
                        });
                        awbTransform.getInputLibraries().forEach(new Consumer<File>() {
                            @Override
                            public void accept(File file) {
                                jarInputs.add(TransformInputUtils.makeJarInput(file, appVariantContext));

                            }
                        });
                        awbTransform.getInputFiles().forEach(new Consumer<File>() {
                            @Override
                            public void accept(File file) {
                                jarInputs.add(TransformInputUtils.makeJarInput(file, appVariantContext));

                            }
                        });
                    }
                }
            });

        }

        return Sets.newHashSet(new TransformInput() {
            @Override
            public Collection<JarInput> getJarInputs() {
                return jarInputs;
            }

            @Override
            public Collection<DirectoryInput> getDirectoryInputs() {
                return directoryInputs;
            }
        });


    }

    public File applyBundleInOutConfigration(final AppVariantContext appVariantContext) {

        VariantScope variantScope = appVariantContext.getScope();
        GlobalScope globalScope = appVariantContext.getScope().getGlobalScope();

        File proguardOut = new File(appVariantContext.getBaseVariantData().getScope().getGlobalScope().getIntermediatesDir(), "awb-proguard");

        File awbInOutConfig = new File(proguardOut, "awb_inout_config.cfg");

        //Add awb configuration
        AtlasDependencyTree dependencyTree = AtlasBuildContext.androidDependencyTrees.get(
                variantScope.getVariantConfiguration().getFullName());

        if (dependencyTree.getAwbBundles().size() > 0) {
            BaseVariantOutput vod = (BaseVariantOutput) appVariantContext.getVariantOutputData().iterator().next();
            AppVariantOutputContext appVariantOutputContext = appVariantContext.getAppVariantOutputContext(ApkDataUtils.get(vod));
            File awbObfuscatedDir = new File(globalScope.getIntermediatesDir(),
                    "/classes-proguard/" + variantScope.getVariantConfiguration()
                            .getDirName());
            AwbProguardConfiguration awbProguardConfiguration = new AwbProguardConfiguration(
                    appVariantOutputContext.getAwbTransformMap().values(), awbObfuscatedDir, appVariantOutputContext);

            try {
                awbProguardConfiguration.printConfigFile(awbInOutConfig);
            } catch (IOException e) {
                throw new GradleException("", e);
            }

            defaultProguardFiles.add(awbInOutConfig);

        }

        return awbInOutConfig;

    }

    public File applyMapping(final AppVariantContext appVariantContext) {

        File mappingFile = null;
        if (null != appVariantContext.apContext.getApExploredFolder() && appVariantContext.apContext
                .getApExploredFolder().exists()) {
            mappingFile = new File(appVariantContext.apContext.getApExploredFolder(), "mapping.txt");
        } else {
            mappingFile = new File(
                    appVariantContext.getScope().getGlobalScope().getProject().getProjectDir(),
                    "mapping.txt");
        }

        if (null != mappingFile && mappingFile.exists()) {
            proGuardTransform.applyTestedMapping(mappingFile);
            return mappingFile;
        }
        return null;

    }

    public void applyBundleProguardConfigration(final AppVariantContext appVariantContext) {

        Set<String> blackList = appVariantContext.getAtlasExtension().getTBuildConfig()
                .getBundleProguardConfigBlackList();

        List<File> proguardFiles = new ArrayList<>();
        VariantScope variantScope = appVariantContext.getScope();
        for (AwbBundle awbBundle : AtlasBuildContext.androidDependencyTrees.get(
                variantScope.getVariantConfiguration().getFullName()).getAwbBundles()) {
            for (AndroidLibrary androidDependency : awbBundle.getAllLibraryAars()) {
                File proguardRules = androidDependency.getProguardRules();

                String groupName = androidDependency.getResolvedCoordinates().getGroupId() + ":" + androidDependency
                        .getResolvedCoordinates().getArtifactId();
                if (blackList.contains(groupName)) {
                    sLogger.info("[proguard] skip proguard from " + androidDependency.getResolvedCoordinates());
                    continue;
                }

                if (proguardRules.isFile()) {
                    proguardFiles.add(proguardRules);
                    sLogger.warn("[proguard] load proguard from " + androidDependency.getResolvedCoordinates());
                } else {
                    sLogger.info("[proguard] missing proguard from " + androidDependency.getResolvedCoordinates());
                }
            }
        }
        defaultProguardFiles.addAll(proguardFiles);

    }

    void collectPatchInfo(Collection<TransformInput> transformInputs){
        ModifyClassFinder modifyClassFinder = new ModifyClassFinder(appVariantContext);
        List<ModifyClassFinder.CodeChange> codeChanges = new ArrayList<>();
        List<File>changeJarFiles = new ArrayList<>();
        configuration.optimize = false;
        configuration.shrink = false;
        transformInputs.parallelStream().forEach(transformInput -> transformInput.getJarInputs().parallelStream().forEach(jarInput -> {
            Collection<ModifyClassFinder.CodeChange> changes = new ArrayList<>();
            try {
                if (modifyClassFinder.parseJarPolicies(jarInput.getFile(),changes)){
                    codeChanges.addAll(changes);
                    changeJarFiles.add(jarInput.getFile());
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

        }));
        codeChanges.forEach(codeChange -> System.err.println("codeChange:"+codeChange.toString()));
        if (changeJarFiles.size() > 0){
            System.setProperty(CHANGE_ENTRY_KEY, new Gson().toJson(changeJarFiles.stream().map(file -> new ClassPathEntry(file,false)).collect(Collectors.toList())));
        }
    }


    private void maybeAddFeatureProguardRules(
            @NonNull VariantScope variantScope,
            @NonNull ConfigurableFileCollection configurationFiles) {

        if (variantScope.consumesFeatureJars()) {
            configurationFiles.from(
                    variantScope.getArtifactFileCollection(
                            METADATA_VALUES, MODULE, CONSUMER_PROGUARD_RULES));
        }
    }

    @Nullable
    private FileCache getUserIntermediatesCache() {
        if (appVariantContext.getScope().getGlobalScope()
                .getProjectOptions()
                .get(BooleanOption.ENABLE_INTERMEDIATE_ARTIFACTS_CACHE)) {
            return appVariantContext.getScope().getGlobalScope().getBuildCache();
        } else {
            return null;
        }
    }
}
