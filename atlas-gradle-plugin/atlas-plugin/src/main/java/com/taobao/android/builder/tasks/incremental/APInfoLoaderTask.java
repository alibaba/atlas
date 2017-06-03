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
import com.android.builder.model.ProductFlavor;
import com.google.common.base.Strings;
import com.taobao.android.builder.tasks.manager.MtlBaseTaskAction;
import org.gradle.api.tasks.TaskAction;

import static com.taobao.android.builder.AtlasBuildContext.appVariantContext;

/**
 * @author chenhjohn
 * @date 2017/6/3
 */

public class APInfoLoaderTask extends DefaultAndroidTask {
    private ApContext apContext;

    private ApkVariantOutputData apkVariantOutputData;

    @TaskAction
    public void taskAction() {
        DefaultManifestParser manifestParser = new DefaultManifestParser(apContext.getBaseManifest());
        String versionNameOverride = apkVariantOutputData.getVersionNameOverride();
        if (Strings.isNullOrEmpty(versionNameOverride)) {
            apkVariantOutputData.setVersionNameOverride(manifestParser.getVersionName());

            GradleVariantConfiguration variantConfiguration = appVariantContext.getScope().getVariantConfiguration();
            ProductFlavor mergedFlavor = variantConfiguration.getMergedFlavor();
            String versionName = mergedFlavor.getVersionName();
            if (versionName == null) {
                ((DefaultProductFlavor)mergedFlavor).setVersionName(manifestParser.getVersionName());
            }
        }

        int versionCodeOverride = apkVariantOutputData.getVersionCodeOverride();
        if (versionCodeOverride == -1) {
            apkVariantOutputData.setVersionCodeOverride(manifestParser.getVersionCode());
        }

        ManifestProcessorTask manifestProcessorTask = apkVariantOutputData.manifestProcessorTask;
        ConventionMappingHelper.map(manifestProcessorTask, "mainManifest", new Callable<File>() {
            @Override
            public File call() throws Exception {
                return apContext.getBaseModifyManifest();
            }
        });
    }

    public static class ConfigAction extends MtlBaseTaskAction<APInfoLoaderTask> {

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
        public Class<APInfoLoaderTask> getType() {
            return APInfoLoaderTask.class;
        }

        @Override
        public void execute(APInfoLoaderTask task) {

            super.execute(task);
            task.apContext = variantContext.apContext;
            task.apkVariantOutputData = (ApkVariantOutputData)baseVariantOutputData;
        }
    }
}
