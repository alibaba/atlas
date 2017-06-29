package com.taobao.android.builder.tasks.incremental;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.TaskManager;
import com.android.build.gradle.internal.api.AppVariantContext;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.scope.ConventionMappingHelper;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.IncrementalTask;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.BaseVariantOutputData;
import com.android.builder.core.VariantConfiguration;
import com.android.builder.sdk.SdkInfo;
import com.android.builder.testing.ConnectedDevice;
import com.android.builder.testing.ConnectedDeviceProvider;
import com.android.builder.testing.api.DeviceConnector;
import com.android.builder.testing.api.DeviceProvider;
import com.android.ddmlib.IDevice;
import com.android.ide.common.process.ProcessExecutor;
import com.android.ide.common.res2.FileStatus;
import com.android.utils.ILogger;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.taobao.android.builder.AtlasBuildContext;
import com.taobao.android.builder.dependency.AtlasDependencyTree;
import com.taobao.android.builder.tasks.manager.MtlBaseTaskAction;
import org.gradle.api.GradleException;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;

/**
 * Created by chenhjohn on 2017/6/21.
 */

abstract class BaseIncrementalInstallVariantTask extends IncrementalTask {
    private static Field sDevice;

    private File adbExe;

    private ProcessExecutor processExecutor;

    private String projectName;

    private String appPackageName;

    private int timeOutInMs = 0;

    private BaseVariantData<? extends BaseVariantOutputData> variantData;

    private Collection<File> apkFiles;

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
            return (IDevice)sDevice.get(device);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected boolean isIncremental() {
        return true;
    }

    @Override
    protected void doFullTaskAction() throws IOException {
        install(getApkFiles());
    }

    private void install(Collection<File> apkFiles) {
        try {
            final ILogger iLogger = getILogger();
            DeviceProvider deviceProvider = new ConnectedDeviceProvider(getAdbExe(), getTimeOutInMs(), iLogger);
            deviceProvider.init();
            VariantConfiguration variantConfig = variantData.getVariantConfiguration();
            String variantName = variantConfig.getFullName();
            int successfulInstallCount = 0;
            List<? extends DeviceConnector> devices = deviceProvider.getDevices();
            for (final IDevice device : Iterables.transform(devices, BaseIncrementalInstallVariantTask::getDevice)) {
                try {
                    install(projectName, variantName, getAppPackageName(), device, apkFiles);
                } catch (Exception e) {
                    e.printStackTrace();
                }

                successfulInstallCount++;
            }

            if (successfulInstallCount == 0) {
                throw new GradleException("Failed to install on any devices.");
            } else {
                getLogger().quiet("Installed on {} {}.", successfulInstallCount,
                                  successfulInstallCount == 1 ? "device" : "devices");
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void doIncrementalTaskAction(Map<File, FileStatus> changedInputs) throws IOException {
        ImmutableSet.Builder<File> builder = ImmutableSet.builder();
        for (final Map.Entry<File, FileStatus> entry : changedInputs.entrySet()) {
            FileStatus status = entry.getValue();
            switch (status) {
                case NEW:
                case CHANGED:
                    builder.add(entry.getKey());
                    break;
                case REMOVED:
                    break;
            }
        }
        install(builder.build());
    }

    protected abstract void install(String projectName, String variantName, String appPackageName, IDevice device,
                                    Collection<File> apkFiles) throws Exception;

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

    @InputFiles
    @Optional
    public Collection<File> getApkFiles() {
        return apkFiles;
    }

    public void setApkFiles(Collection<File> apkFiles) {
        this.apkFiles = apkFiles;
    }

    public abstract static class ConfigAction<T extends BaseIncrementalInstallVariantTask>
        extends MtlBaseTaskAction<T> {
        private final AppVariantContext appVariantContext;

        private final VariantScope scope;

        public ConfigAction(AppVariantContext appVariantContext, BaseVariantOutputData baseVariantOutputData) {
            super(appVariantContext, baseVariantOutputData);
            this.appVariantContext = appVariantContext;
            this.scope = baseVariantOutputData.getScope().getVariantScope();
        }

        @Override
        public void execute(@NonNull T incrementalInstallVariantTask) {
            BaseVariantData<? extends BaseVariantOutputData> variantData = scope.getVariantData();

            final GradleVariantConfiguration variantConfiguration = variantData.getVariantConfiguration();

            incrementalInstallVariantTask.setDescription(
                "Installs the " + scope.getVariantData().getDescription() + ".");
            incrementalInstallVariantTask.setVariantName(scope.getVariantConfiguration().getFullName());
            incrementalInstallVariantTask.setAndroidBuilder(scope.getGlobalScope().getAndroidBuilder());
            incrementalInstallVariantTask.setGroup(TaskManager.INSTALL_GROUP);
            incrementalInstallVariantTask.setProjectName(scope.getGlobalScope().getProject().getName());
            incrementalInstallVariantTask.setVariantData(scope.getVariantData());
            incrementalInstallVariantTask.setTimeOutInMs(
                scope.getGlobalScope().getExtension().getAdbOptions().getTimeOutInMs());
            incrementalInstallVariantTask.setProcessExecutor(
                scope.getGlobalScope().getAndroidBuilder().getProcessExecutor());
            ConventionMappingHelper.map(incrementalInstallVariantTask, "adbExe", new Callable<File>() {
                @Override
                public File call() throws Exception {
                    final SdkInfo info = scope.getGlobalScope().getSdkHandler().getSdkInfo();
                    return (info == null ? null : info.getAdb());
                }
            });

            ConventionMappingHelper.map(incrementalInstallVariantTask, "appPackageName",
                                        variantConfiguration::getApplicationId);
            //TODO 先根据依赖判断
            ConventionMappingHelper.map(incrementalInstallVariantTask, "apkFiles", new Callable<ImmutableList<File>>() {

                @Override
                public ImmutableList<File> call() {
                    ImmutableList.Builder<File> builder = ImmutableList.builder();
                    //Awb
                    Collection awbApkFiles = appVariantContext.getAwbApkFiles();
                    if (awbApkFiles != null) {
                        builder.addAll(awbApkFiles);
                    }
                    //Main
                    AtlasDependencyTree atlasDependencyTree = AtlasBuildContext.androidDependencyTrees.get(
                        incrementalInstallVariantTask.getVariantName());
                    List<String> allDependencies = atlasDependencyTree.getMainBundle().getAllDependencies();
                    if (allDependencies.size() > 0) {
                        builder.add(getAppVariantOutputContext().getPatchApkOutputFile());
                    }
                    return builder.build();
                }
            });
        }
    }
}
