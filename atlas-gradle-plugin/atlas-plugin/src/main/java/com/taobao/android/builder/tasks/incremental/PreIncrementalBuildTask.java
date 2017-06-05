package com.taobao.android.builder.tasks.incremental;

import java.io.File;
import java.util.concurrent.Callable;

import com.android.build.gradle.internal.api.ApContext;
import com.android.build.gradle.internal.api.AppVariantContext;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.scope.ConventionMappingHelper;
import com.android.build.gradle.internal.tasks.DefaultAndroidTask;
import com.android.build.gradle.internal.variant.ApkVariantOutputData;
import com.android.build.gradle.internal.variant.BaseVariantOutputData;
import com.android.build.gradle.tasks.ManifestProcessorTask;
import com.android.builder.core.DefaultManifestParser;
import com.android.builder.core.DefaultProductFlavor;
import com.google.common.base.Strings;
import com.taobao.android.builder.tasks.manager.MtlBaseTaskAction;
import org.gradle.api.tasks.TaskAction;

import static com.taobao.android.builder.AtlasBuildContext.appVariantContext;

/**
 * @author chenhjohn
 * @date 2017/6/3
 */

public class PreIncrementalBuildTask extends DefaultAndroidTask {
    private ApContext apContext;

    private ApkVariantOutputData apkVariantOutputData;
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

        // atlasFrameworkProperties合并判断
        if (appVariantContext.getAtlasExtension().getTBuildConfig().isIncremental()) {
            File atlasFrameworkPropertiesFile = apContext.getBaseAtlasFrameworkPropertiesFile();
            if (!atlasFrameworkPropertiesFile.exists()) {
                String taskName;
                if (appVariantContext.getAtlasExtension().getTBuildConfig().getClassInject()) {
                    taskName = null;
                } else {
                    taskName = apkVariantOutputData.getScope().getTaskName("generate", "AtlasSources");
                    getLogger().warn("Skipped " + taskName + " : required atlasFrameworkPropertiesFile not found "
                                     + atlasFrameworkPropertiesFile + '\n'
                                     + "Please check and update your baseline project atlasplugin.");
                    getProject().getTasks().getByName(taskName).setEnabled(false);
                }
            }
        }
    }

    public static class ConfigAction extends MtlBaseTaskAction<PreIncrementalBuildTask> {

        private final AppVariantContext appVariantContext;

        public ConfigAction(AppVariantContext appVariantContext, BaseVariantOutputData baseVariantOutputData) {
            super(appVariantContext, baseVariantOutputData);
            this.appVariantContext = appVariantContext;
        }

        @Override
        public String getName() {
            return scope.getTaskName("APInfo", "Loader");
        }

        @Override
        public Class<PreIncrementalBuildTask> getType() {
            return PreIncrementalBuildTask.class;
        }

        @Override
        public void execute(PreIncrementalBuildTask task) {

            super.execute(task);
            task.apContext = variantContext.apContext;
            task.apkVariantOutputData = (ApkVariantOutputData)baseVariantOutputData;
        }
    }
}
