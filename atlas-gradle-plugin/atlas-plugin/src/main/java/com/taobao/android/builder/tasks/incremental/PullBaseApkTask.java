package com.taobao.android.builder.tasks.incremental;

import java.io.File;
import java.util.List;

import com.android.build.gradle.internal.api.AppVariantContext;
import com.android.build.gradle.internal.tasks.BaseTask;
import com.android.build.gradle.internal.variant.BaseVariantOutputData;
import com.android.builder.testing.ConnectedDeviceProvider;
import com.android.builder.testing.api.DeviceConnector;
import com.android.builder.testing.api.DeviceException;
import com.android.builder.testing.api.DeviceProvider;
import com.android.ide.common.process.ProcessException;
import com.android.ide.common.process.ProcessExecutor;
import com.android.utils.ILogger;
import com.google.common.base.Preconditions;
import com.taobao.android.builder.tasks.manager.MtlBaseTaskAction;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.TaskAction;

/**
 * Created by chenhjohn on 2017/6/14.
 */

public class PullBaseApkTask extends BaseTask {

    private File adbExe;

    private ProcessExecutor processExecutor;

    private final int timeOutInMs = 0;

    @TaskAction
    public void taskAction() throws DeviceException, ProcessException, InterruptedException {
        final ILogger iLogger = getILogger();
        DeviceProvider deviceProvider = new ConnectedDeviceProvider(getAdbExe(), getTimeOutInMs(), iLogger);
        deviceProvider.init();

        List<? extends DeviceConnector> devices = deviceProvider.getDevices();
        Preconditions.checkState(devices.size() == 1, "There must be exactly one device");
    }

    @InputFile
    public File getAdbExe() {
        return adbExe;
    }

    public void setAdbExe(File adbExe) {
        this.adbExe = adbExe;
    }

    @Input
    public int getTimeOutInMs() {
        return timeOutInMs;
    }

    public void setTimeOutInMs(int timeOutInMs) {
        this.timeOutInMs = timeOutInMs;
    }

    public static class ConfigAction extends MtlBaseTaskAction<PullBaseApkTask> {

        private final AppVariantContext appVariantContext;

        public ConfigAction(AppVariantContext appVariantContext, BaseVariantOutputData baseVariantOutputData) {
            super(appVariantContext, baseVariantOutputData);
            this.appVariantContext = appVariantContext;
        }

        @Override
        public String getName() {
            return scope.getTaskName("", "");
        }

        @Override
        public Class<PullBaseApkTask> getType() {
            return PullBaseApkTask.class;
        }

        @Override
        public void execute(PullBaseApkTask task) {

            super.execute(task);
        }
    }
}
