package com.taobao.android.builder.insant;

import com.android.annotations.NonNull;
import com.android.build.api.transform.*;
import com.android.build.gradle.api.BaseVariantOutput;
import com.android.build.gradle.internal.ApkDataUtils;
import com.android.build.gradle.internal.api.AppVariantContext;
import com.android.build.gradle.internal.api.AppVariantOutputContext;
import com.android.build.gradle.internal.pipeline.InjectTransform;
import com.android.build.gradle.internal.pipeline.IntermediateFolderUtils;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.publishing.AndroidArtifacts;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.transforms.ProGuardTransform;
import com.android.build.gradle.internal.transforms.ProguardConfigurable;
import com.android.build.gradle.tasks.SimpleWorkQueue;
import com.android.builder.model.AndroidLibrary;
import com.android.builder.tasks.Job;
import com.android.builder.tasks.JobContext;
import com.android.ide.common.build.ApkData;
import com.google.common.base.Joiner;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.SettableFuture;
import com.taobao.android.builder.AtlasBuildContext;
import com.taobao.android.builder.dependency.AtlasDependencyTree;
import com.taobao.android.builder.dependency.model.AwbBundle;
import com.taobao.android.builder.extension.TBuildConfig;
import com.taobao.android.builder.tasks.manager.transform.MtlInjectTransform;
import com.taobao.android.builder.tools.ReflectUtils;
import com.taobao.android.builder.tools.TransformInputUtils;
import com.taobao.android.builder.tools.log.FileLogger;
import com.taobao.android.builder.tools.proguard.AtlasProguardHelper;
import com.taobao.android.builder.tools.proguard.AwbProguardConfiguration;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.jf.util.ImmutableUtils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.logging.Logger;

import static com.android.builder.model.AndroidProject.FD_OUTPUTS;

/**
 * FakeProguardTransform
 *
 * @author zhayu.ll
 * @date 18/10/18
 */
public class DelegateProguardTransform extends MtlInjectTransform {

    private final TBuildConfig buildConfig;

    private static org.gradle.api.logging.Logger sLogger = null;

    private boolean firstTime;

    private ProGuardTransform proGuardTransform;

    List<File> defaultProguardFiles = new ArrayList<>();

    public DelegateProguardTransform(AppVariantContext appVariantContext, ApkData apkData) {
        super(appVariantContext, apkData);
        proGuardTransform = new ProGuardTransform(appVariantContext.getScope());
        this.buildConfig = appVariantContext.getAtlasExtension().getTBuildConfig();
        sLogger = appVariantContext.getProject().getLogger();

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
        return TransformManager.CONTENT_JARS;
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
        firstTime = true;
        defaultProguardFiles.addAll(appVariantContext.getVariantData().getVariantConfiguration().getBuildType().getProguardFiles());

        if (buildConfig.getConsumerProguardEnabled()){
            defaultProguardFiles.addAll(appVariantContext.getScope().getArtifactFileCollection(AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH, AndroidArtifacts.ArtifactScope.ALL, AndroidArtifacts.ArtifactType.PROGUARD_RULES).getFiles());
        }

        List<AwbBundle> awbBundles = AtlasBuildContext.androidDependencyTrees.get(
                appVariantContext.getScope().getVariantConfiguration().getFullName()).getAwbBundles();
        if (awbBundles != null && awbBundles.size() > 0) {
            File bundleRKeepFile = new File(appVariantContext.getBaseVariantData().getScope().getGlobalScope().getIntermediatesDir(), "awb-progrard/bundleRKeep.cfg");
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
        if (buildConfig.isBundleProguardConfigEnabled() &&! buildConfig.getConsumerProguardEnabled()) {
            applyBundleProguardConfigration(appVariantContext);
        }

        proGuardTransform.setConfigurationFiles(appVariantContext.getScope().getGlobalScope().getProject().files(defaultProguardFiles));

        //apply mapping
        applyMapping(appVariantContext);

        //set output
        File proguardOutFile = new File(appVariantContext.getProject().getBuildDir(), "outputs/proguard.cfg");
        proGuardTransform.printconfiguration(proguardOutFile);
        SettableFuture<TransformOutputProvider> resultFuture = SettableFuture.create();
        final Job<Void> job = new Job<>(getName(),
                new com.android.builder.tasks.Task<Void>() {
                    @Override
                    public void run(@NonNull Job<Void> job,
                                    @NonNull JobContext<Void> context) throws IOException {

                        try {
                            Method m = ProGuardTransform.class.getDeclaredMethod("doMinification", Collection.class, Collection.class, TransformOutputProvider.class);
                            m.setAccessible(true);
                            m.invoke(proGuardTransform, getAllInput(), transformInvocation.getReferencedInputs(), transformInvocation.getOutputProvider());
                        }catch (Exception e){
                            e.printStackTrace();
                        }
                    }

                    @Override
                    public void finished() {
                        resultFuture.set(transformInvocation.getOutputProvider());
                    }

                    @Override
                    public void error(Throwable e) {
                        resultFuture.setException(e);
                    }
                }, resultFuture);
        try {
            SimpleWorkQueue.push(job);

            // wait for the task completion.
            try {
                job.awaitRethrowExceptions();
            } catch (ExecutionException e) {
                throw new RuntimeException("Job failed, see logs for details", e.getCause());
            }

            IntermediateFolderUtils folderUtils = (IntermediateFolderUtils) ReflectUtils.getField(transformInvocation.getOutputProvider(),"folderUtils");
            AtlasBuildContext.atlasMainDexHelperMap.get(appVariantContext.getVariantName()).addAllMainDexJars(FileUtils.listFiles(folderUtils.getRootFolder(),new String[]{"jar"},true));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    private Collection<TransformInput> getAllInput() {
        Collection <JarInput> jarInputs = new HashSet<>();
        Collection<DirectoryInput>directoryInputs = new HashSet<>();
        Collection<File>jars = AtlasBuildContext.atlasMainDexHelperMap.get(appVariantContext.getVariantName()).getAllMainDexJars();
        jars.forEach(new Consumer<File>() {
            @Override
            public void accept(File file) {
                jarInputs.add(TransformInputUtils.makeJarInput(file,appVariantContext));
            }
        });

        Collection<File>dirs = AtlasBuildContext.atlasMainDexHelperMap.get(appVariantContext.getVariantName()).getInputDirs();
        dirs.forEach(new Consumer<File>() {
            @Override
            public void accept(File file) {
                directoryInputs.add(TransformInputUtils.makeDirectoryInput(file,appVariantContext));
            }
        });

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

        GlobalScope globalScope = variantScope.getGlobalScope();
        File proguardOut = new File(Joiner.on(File.separatorChar)
                .join(String.valueOf(globalScope.getBuildDir()), FD_OUTPUTS, "mapping",
                        variantScope.getVariantConfiguration().getDirName()));

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
}
