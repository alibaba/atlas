package com.taobao.android.builder.tasks.incremental;

import java.io.File;
import java.util.Collection;
import java.util.concurrent.Callable;

import com.android.build.api.transform.QualifiedContent;
import com.android.build.gradle.internal.api.ApContext;
import com.android.build.gradle.internal.api.AppVariantContext;
import com.android.build.gradle.internal.api.AppVariantOutputContext;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.pipeline.AtlasExtendedContentType;
import com.android.build.gradle.internal.pipeline.ExtendedContentType;
import com.android.build.gradle.internal.pipeline.OriginalStream;
import com.android.build.gradle.internal.pipeline.OriginalStream.Builder;
import com.android.build.gradle.internal.scope.ConventionMappingHelper;
import com.android.build.gradle.internal.tasks.DefaultAndroidTask;
import com.android.build.gradle.internal.variant.ApkVariantOutputData;
import com.android.build.gradle.internal.variant.BaseVariantOutputData;
import com.android.build.gradle.tasks.ManifestProcessorTask;
import com.android.builder.core.DefaultManifestParser;
import com.android.builder.core.DefaultProductFlavor;
import com.google.common.base.Strings;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.taobao.android.builder.tasks.manager.MtlBaseTaskAction;
import org.gradle.api.tasks.TaskAction;

/**
 * @author chenhjohn
 * @date 2017/6/3
 */

public class PreIncrementalBuildTask extends DefaultAndroidTask {
    private ApContext apContext;

    private ApkVariantOutputData apkVariantOutputData;

    private AppVariantContext appVariantContext;

    @TaskAction
    public void taskAction() {
        // 覆盖包名
        DefaultManifestParser manifestParser = new DefaultManifestParser(apContext.getBaseManifest());
        GradleVariantConfiguration variantConfiguration = appVariantContext.getScope().getVariantConfiguration();
        DefaultProductFlavor mergedFlavor = (DefaultProductFlavor)variantConfiguration.getMergedFlavor();
        String idOverride = variantConfiguration.getIdOverride();
        if (Strings.isNullOrEmpty(idOverride)) {
            mergedFlavor.setApplicationId(manifestParser.getPackage());
        }

        // 覆盖版本号
        String versionNameOverride = apkVariantOutputData.getVersionNameOverride();
        if (Strings.isNullOrEmpty(versionNameOverride)) {
            apkVariantOutputData.setVersionNameOverride(manifestParser.getVersionName());

            String versionName = mergedFlavor.getVersionName();
            if (versionName == null) {
                mergedFlavor.setVersionName(manifestParser.getVersionName());
            }
        }

        int versionCodeOverride = apkVariantOutputData.getVersionCodeOverride();
        if (versionCodeOverride == -1) {
            apkVariantOutputData.setVersionCodeOverride(manifestParser.getVersionCode());
        }

        // 覆盖mainManifest
        ManifestProcessorTask manifestProcessorTask = apkVariantOutputData.manifestProcessorTask;
        ConventionMappingHelper.map(manifestProcessorTask, "mainManifest", new Callable<File>() {
            @Override
            public File call() throws Exception {
                return apContext.getBaseMainManifest();
            }
        });

        // TODO 内容无变化也不执行
        // atlasFrameworkProperties合并判断
        File atlasFrameworkPropertiesFile = apContext.getBaseAtlasFrameworkPropertiesFile();
        if (!atlasFrameworkPropertiesFile.exists()) {
            String taskName;
            if (appVariantContext.getAtlasExtension().getTBuildConfig().getClassInject()) {
                taskName = null;
            } else {
                taskName = apkVariantOutputData.getScope().getTaskName("generate", "AtlasSources");
                getLogger().warn("Skipped "
                                     + taskName
                                     + " : required atlasFrameworkPropertiesFile not found "
                                     + atlasFrameworkPropertiesFile
                                     + '\n'
                                     + "Please check and update your baseline project atlasplugin.");
                getProject().getTasks().getByName(taskName).setEnabled(false);
            }
        }

        Builder builder = OriginalStream.builder().addContentType(AtlasExtendedContentType.AWB_APKS).addScope(
            QualifiedContent.Scope.PROJECT).setFolders(new Supplier<Collection<File>>() {
            @Override
            public Collection<File> get() {
                return ImmutableList.of(appVariantContext.getAwbApkOutputDir());
            }
        });
        // 动态部署增量编译不打包Awb
        if (appVariantContext.getBuildType().getPatchConfig() == null || !appVariantContext.getBuildType()
            .getPatchConfig()
            .isCreateTPatch()) {
            builder.addContentType(ExtendedContentType.NATIVE_LIBS);
        } else {
            // TODO 四大组件变化
            ConventionMappingHelper.map(apkVariantOutputData.processResourcesTask,
                                        "manifestFile",
                                        new Callable<File>() {
                                            @Override
                                            public File call() throws Exception {
                                                return apContext.getBaseMainManifest();
                                            }
                                        });
            AppVariantOutputContext appVariantOutputContext = appVariantContext.getAppVariantOutputContext(
                apkVariantOutputData);
            ConventionMappingHelper.map(apkVariantOutputData.packageAndroidArtifactTask, "signingConfig", () -> null);
            ConventionMappingHelper.map(apkVariantOutputData.packageAndroidArtifactTask,
                                        "outputFile",
                                        appVariantOutputContext::getPatchApkOutputFile);
        }

        appVariantContext.getScope().getTransformManager().addStream(builder.build());
    }

    public static class ConfigAction extends MtlBaseTaskAction<PreIncrementalBuildTask> {

        private final AppVariantContext appVariantContext;

        public ConfigAction(AppVariantContext appVariantContext, BaseVariantOutputData baseVariantOutputData) {
            super(appVariantContext, baseVariantOutputData);
            this.appVariantContext = appVariantContext;
        }

        @Override
        public String getName() {
            return scope.getTaskName("PreIncrementalBuild");
        }

        @Override
        public Class<PreIncrementalBuildTask> getType() {
            return PreIncrementalBuildTask.class;
        }

        @Override
        public void execute(PreIncrementalBuildTask task) {

            super.execute(task);
            task.apContext = variantContext.apContext;
            task.appVariantContext = appVariantContext;
            task.apkVariantOutputData = (ApkVariantOutputData)baseVariantOutputData;
        }
    }
}
