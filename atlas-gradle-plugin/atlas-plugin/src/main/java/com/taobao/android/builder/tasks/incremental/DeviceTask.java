package com.taobao.android.builder.tasks.incremental;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.TaskManager;
import com.android.build.gradle.internal.api.AppVariantContext;
import com.android.build.gradle.internal.api.VariantContext;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.scope.ConventionMappingHelper;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.IncrementalTask;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.BaseVariantOutputData;
import com.android.builder.sdk.SdkInfo;
import com.android.builder.testing.ConnectedDevice;
import com.android.builder.testing.ConnectedDeviceProvider;
import com.android.builder.testing.api.DeviceConnector;
import com.android.builder.testing.api.DeviceProvider;
import com.android.ddmlib.IDevice;
import com.android.ide.common.process.ProcessExecutor;
import com.android.ide.common.res2.FileStatus;
import com.android.utils.ILogger;
import com.google.common.collect.Iterables;
import com.taobao.android.builder.tasks.manager.MtlBaseTaskAction;

import org.gradle.api.GradleException;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Created by chenhjohn on 2017/8/11.
 */

abstract class DeviceTask extends IncrementalTask {
    protected static final long LS_TIMEOUT_SEC = 2;
    private static Field sDevice;
    protected String projectName;
    protected BaseVariantData<? extends BaseVariantOutputData> variantData;
    private File adbExe;
    private ProcessExecutor processExecutor;
    private String appPackageName;
    private String versionName;
    private int timeOutInMs = 0;

    private static IDevice getDevice(DeviceConnector device) {
        if (sDevice == null) {
            try {
                sDevice = ConnectedDevice.class.getDeclaredField("iDevice");
                sDevice.setAccessible(true);
            } catch (NoSuchFieldException e) {
                throw new RuntimeException(e);
            }
        }
        try {
            return (IDevice) sDevice.get(device);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void doFullTaskAction() throws IOException {
        try {
            final ILogger iLogger = getILogger();
            DeviceProvider deviceProvider = new ConnectedDeviceProvider(getAdbExe(), getTimeOutInMs(), iLogger);
            deviceProvider.init();
            int successfulInstallCount = 0;
            List<? extends DeviceConnector> devices = deviceProvider.getDevices();
            for (final IDevice device : Iterables.transform(devices, DeviceTask::getDevice)) {
                doFullTaskAction(device);

                successfulInstallCount++;
            }

            if (successfulInstallCount == 0) {
                throw new GradleException("Failed to install on any devices.");
            } else {
                getLogger().quiet("Installed on {} {}.",
                        successfulInstallCount,
                        successfulInstallCount == 1 ? "device" : "devices");
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected abstract void doFullTaskAction(IDevice device);

    @Override
    protected void doIncrementalTaskAction(Map<File, FileStatus> changedInputs) throws IOException {
        super.doIncrementalTaskAction(changedInputs);
        try {
            final ILogger iLogger = getILogger();
            DeviceProvider deviceProvider = new ConnectedDeviceProvider(getAdbExe(), getTimeOutInMs(), iLogger);
            deviceProvider.init();
            int successfulInstallCount = 0;
            List<? extends DeviceConnector> devices = deviceProvider.getDevices();
            for (final IDevice device : Iterables.transform(devices, DeviceTask::getDevice)) {
                doIncrementalTaskAction(device, changedInputs);

                successfulInstallCount++;
            }

            if (successfulInstallCount == 0) {
                throw new GradleException("Failed to install on any devices.");
            } else {
                getLogger().quiet("Installed on {} {}.",
                        successfulInstallCount,
                        successfulInstallCount == 1 ? "device" : "devices");
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected abstract void doIncrementalTaskAction(IDevice device, Map<File, FileStatus> changedInputs) throws IOException;

    @InputFile
    public File getAdbExe() {
        return adbExe;
    }

    public void setAdbExe(File adbExe) {
        this.adbExe = adbExe;
    }

    public ProcessExecutor getProcessExecutor() {
        return processExecutor;
    }

    public void setProcessExecutor(ProcessExecutor processExecutor) {
        this.processExecutor = processExecutor;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    @Input
    public String getAppPackageName() {
        return appPackageName;
    }

    public void setAppPackageName(String appPackageName) {
        this.appPackageName = appPackageName;
    }

    @Input
    public int getTimeOutInMs() {
        return timeOutInMs;
    }

    public void setTimeOutInMs(int timeOutInMs) {
        this.timeOutInMs = timeOutInMs;
    }

    public BaseVariantData<? extends BaseVariantOutputData> getVariantData() {
        return variantData;
    }

    public void setVariantData(BaseVariantData<? extends BaseVariantOutputData> variantData) {
        this.variantData = variantData;
    }

    @Input
    public String getVersionName() {
        return versionName;
    }

    public void setVersionName(String versionName) {
        this.versionName = versionName;
    }

    /**
     * Created by chenhjohn on 2017/8/11.
     */

    abstract static class ConfigAction<T extends DeviceTask> extends MtlBaseTaskAction<T> {
        protected final AppVariantContext appVariantContext;
        protected final VariantScope scope;

        public ConfigAction(VariantContext variantContext, BaseVariantOutputData baseVariantOutputData, AppVariantContext appVariantContext) {
            super(variantContext, baseVariantOutputData);
            this.scope = baseVariantOutputData.getScope().getVariantScope();
            this.appVariantContext = appVariantContext;
        }

        @Override
        public void execute(@NonNull T deviceTask) {
            BaseVariantData<? extends BaseVariantOutputData> variantData = scope.getVariantData();

            final GradleVariantConfiguration variantConfiguration = variantData.getVariantConfiguration();

            deviceTask.setDescription("Installs the "
                    + scope.getVariantData().getDescription()
                    + ".");
            deviceTask.setVariantName(scope.getVariantConfiguration().getFullName());
            deviceTask.setAndroidBuilder(scope.getGlobalScope().getAndroidBuilder());
            deviceTask.setGroup(TaskManager.INSTALL_GROUP);
            deviceTask.setProjectName(scope.getGlobalScope().getProject().getName());
            deviceTask.setVariantData(scope.getVariantData());
            deviceTask.setTimeOutInMs(scope.getGlobalScope()
                    .getExtension()
                    .getAdbOptions()
                    .getTimeOutInMs());
            deviceTask.setProcessExecutor(scope.getGlobalScope()
                    .getAndroidBuilder()
                    .getProcessExecutor());
            ConventionMappingHelper.map(deviceTask, "adbExe", new Callable<File>() {
                @Override
                public File call() throws Exception {
                    final SdkInfo info = scope.getGlobalScope().getSdkHandler().getSdkInfo();
                    return (info == null ? null : info.getAdb());
                }
            });

            ConventionMappingHelper.map(deviceTask,
                    "appPackageName",
                    variantConfiguration::getApplicationId);
            ConventionMappingHelper.map(deviceTask,
                    "versionName",
                    variantConfiguration::getVersionName);

        }

    }
}
