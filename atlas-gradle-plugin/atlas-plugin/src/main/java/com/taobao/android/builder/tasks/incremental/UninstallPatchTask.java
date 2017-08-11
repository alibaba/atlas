package com.taobao.android.builder.tasks.incremental;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.api.AppVariantContext;
import com.android.build.gradle.internal.variant.BaseVariantOutputData;
import com.android.ddmlib.IDevice;
import com.android.ide.common.res2.FileStatus;
import com.google.common.base.Joiner;
import org.gradle.api.Task;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.ParallelizableTask;

/**
 * Created by chenhjohn on 2017/6/21.
 */

@ParallelizableTask
public class UninstallPatchTask extends DeviceTask {
    public static final String PATCH_INSTALL_DIRECTORY_PREFIX = "/sdcard/Android/data/";

    private static final String PATCH_INSTALL_DIRECTORY_SUFFIX = "files/debug_storage/";

    public UninstallPatchTask() {
        this.getOutputs().upToDateWhen(new Spec<Task>() {
            @Override
            public boolean isSatisfiedBy(Task task) {
                getLogger().debug("Uninstall patch task is always run.");
                return false;
            }
        });
    }

    @Override
    protected void doFullTaskAction(IDevice device) throws Exception {
        runCommand(device, "rm -rf " + Joiner.on('/')
            .join(PATCH_INSTALL_DIRECTORY_PREFIX, getAppPackageName(), PATCH_INSTALL_DIRECTORY_SUFFIX));

    }

    @Override
    protected void doIncrementalTaskAction(IDevice device, Map<File, FileStatus> changedInputs) throws IOException {

    }

    public static class ConfigAction extends DeviceTask.ConfigAction<UninstallPatchTask> {

        public ConfigAction(AppVariantContext appVariantContext, BaseVariantOutputData baseVariantOutputData) {
            super(appVariantContext, baseVariantOutputData, appVariantContext);
        }

        @NonNull
        @Override
        public String getName() {
            return scope.getTaskName("uninstallPatch");
        }

        @NonNull
        @Override
        public Class<UninstallPatchTask> getType() {
            return UninstallPatchTask.class;
        }

    }

}
