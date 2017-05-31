package com.taobao.android.builder.tasks.incremental;

import com.android.build.gradle.internal.api.AppVariantContext;
import com.android.build.gradle.internal.tasks.DefaultAndroidTask;
import com.android.build.gradle.internal.variant.BaseVariantOutputData;
import com.taobao.android.builder.extension.PatchConfig;
import com.taobao.android.builder.extension.TBuildType;
import com.taobao.android.builder.tasks.manager.MtlBaseTaskAction;
import org.gradle.api.tasks.TaskAction;

/**
 * Created by chenhjohn on 2017/5/31.
 */

public class PreIncrementalInstallVariantTask extends DefaultAndroidTask {

    private AppVariantContext appVariantContext;

    @TaskAction
    public void taskAction() {
        TBuildType tBuildType = appVariantContext.getBuildType();
        PatchConfig patchConfig = tBuildType.getPatchConfig();
        if (patchConfig == null) {
            patchConfig = new PatchConfig(tBuildType.getName());
        }
        patchConfig.setCreateTPatch(true);
    }

    public static class ConfigAction extends MtlBaseTaskAction<PreIncrementalInstallVariantTask> {

        private AppVariantContext appVariantContext;

        public ConfigAction(AppVariantContext appVariantContext, BaseVariantOutputData baseVariantOutputData) {
            super(appVariantContext, baseVariantOutputData);
            this.appVariantContext = appVariantContext;
        }

        @Override
        public String getName() {
            return scope.getTaskName("PreIncrementalInstallVariantTask", "");
        }

        @Override
        public Class<PreIncrementalInstallVariantTask> getType() {
            return PreIncrementalInstallVariantTask.class;
        }

        @Override
        public void execute(PreIncrementalInstallVariantTask task) {

            super.execute(task);
            task.appVariantContext = appVariantContext;
        }
    }
}
