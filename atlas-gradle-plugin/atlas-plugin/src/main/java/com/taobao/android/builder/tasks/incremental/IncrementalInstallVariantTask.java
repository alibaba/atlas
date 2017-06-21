package com.taobao.android.builder.tasks.incremental;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.TaskManager;
import com.android.build.gradle.internal.api.AppVariantContext;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.scope.ConventionMappingHelper;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.BaseTask;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.BaseVariantOutputData;
import com.android.builder.core.VariantConfiguration;
import com.android.builder.sdk.SdkInfo;
import com.android.builder.testing.ConnectedDevice;
import com.android.builder.testing.ConnectedDeviceProvider;
import com.android.builder.testing.api.DeviceConnector;
import com.android.builder.testing.api.DeviceException;
import com.android.builder.testing.api.DeviceProvider;
import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.MultiLineReceiver;
import com.android.ddmlib.ShellCommandUnresponsiveException;
import com.android.ddmlib.SyncException;
import com.android.ddmlib.TimeoutException;
import com.android.ide.common.process.ProcessException;
import com.android.ide.common.process.ProcessExecutor;
import com.android.utils.ILogger;
import com.google.common.collect.Iterables;
import com.taobao.android.builder.AtlasBuildContext;
import com.taobao.android.builder.dependency.AtlasDependencyTree;
import com.taobao.android.builder.tasks.manager.MtlBaseTaskAction;
import org.gradle.api.GradleException;
import org.gradle.api.Task;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.ParallelizableTask;
import org.gradle.api.tasks.TaskAction;

/**
 * @author chenhjohn
 * @date 2017/5/25
 */

@ParallelizableTask
public class IncrementalInstallVariantTask extends BaseTask {

    public static final String PATCH_NAME = "/patch.zip";

    public static final String PATCH_INSTALL_DIRECTORY_PREFIX = "/sdcard/Android/data/";

    public static final String PATCH_INSTALL_DIRECTORY_SUFFIX = "/files/debug_storage/";

    private File adbExe;

    private ProcessExecutor processExecutor;

    private String projectName;

    private String appPackageName;

    private int timeOutInMs = 0;

    private BaseVariantData<? extends BaseVariantOutputData> variantData;

    private File mainDexFile;

    private Collection<File> awbApkFiles;

    public IncrementalInstallVariantTask() {
        this.getOutputs().upToDateWhen(new Spec<Task>() {
            @Override
            public boolean isSatisfiedBy(Task task) {
                getLogger().debug("Install task is always run.");
                return false;
            }
        });
    }

    @TaskAction
    public void install()
        throws DeviceException, ProcessException, InterruptedException, TimeoutException, AdbCommandRejectedException,
               SyncException, IOException, ShellCommandUnresponsiveException {
        final ILogger iLogger = getILogger();
        DeviceProvider deviceProvider = new ConnectedDeviceProvider(getAdbExe(), getTimeOutInMs(), iLogger);
        deviceProvider.init();
        VariantConfiguration variantConfig = variantData.getVariantConfiguration();
        String variantName = variantConfig.getFullName();
        int successfulInstallCount = 0;
        List<? extends DeviceConnector> devices = deviceProvider.getDevices();
        for (final IDevice device : Iterables.transform(devices, IncrementalInstallVariantTask::getDevice)) {
            Collection<File> awbApkFiles = getAwbApkFiles();
            if (awbApkFiles != null) {
                for (File awbApkFile : awbApkFiles) {
                    getLogger().lifecycle("Installing awb '{}' on '{}' for {}:{}", awbApkFile, device.getName(),
                                          projectName, variantName);

                    installPatch(device, awbApkFile, getAwbPackageName(awbApkFile));
                }
            }

            File mainDexFile = getMainDexFile();
            if (mainDexFile != null) {
                getLogger().lifecycle("Installing mainDex '{}' on '{}' for {}:{}", mainDexFile, device.getName(),
                                      projectName, variantName);

                installPatch(device, mainDexFile, "com.taobao.maindex");
            }
            String appPackageName = getAppPackageName();
            getLogger().lifecycle("Restarting '{}' on '{}' for {}:{}", appPackageName, device.getName(), projectName,
                                  variantName);
            //退到后台
            device.executeShellCommand("input keyevent 3",
                                       //
                                       //$NON-NLS-1$
                                       new MultiLineReceiver() {
                                           @Override
                                           public void processNewLines(String[] lines) {
                                           }

                                           @Override
                                           public boolean isCancelled() {
                                               return false;
                                           }
                                       });

            //杀死进程
            device.executeShellCommand("am " + "force-stop " + appPackageName,
                                       //$NON-NLS-1$
                                       new MultiLineReceiver() {
                                           @Override
                                           public void processNewLines(String[] lines) {
                                           }

                                           @Override
                                           public boolean isCancelled() {
                                               return false;
                                           }
                                       });
            /*device.executeShellCommand("am " + "kill " + appPackageName,
                                       //$NON-NLS-1$
                                       new MultiLineReceiver() {
                                           @Override
                                           public void processNewLines(String[] lines) {
                                           }

                                           @Override
                                           public boolean isCancelled() {
                                               return false;
                                           }
                                       });*/
            /*device.executeShellCommand(
                "am " + "broadcast " + "-a " + "com.taobao.atlas.intent.PATCH_APP " + "-e " + "pkg " + appPackageName,
                //$NON-NLS-1$
                new MultiLineReceiver() {
                    @Override
                    public void processNewLines(String[] lines) {
                    }

                    @Override
                    public boolean isCancelled() {
                        return false;
                    }
                });*/
            //启动
            device.executeShellCommand("monkey " + "-p " + appPackageName + " -c android.intent.category.LAUNCHER 1",
                                       //$NON-NLS-1$
                                       new MultiLineReceiver() {
                                           @Override
                                           public void processNewLines(String[] lines) {
                                           }

                                           @Override
                                           public boolean isCancelled() {
                                               return false;
                                           }
                                       });
            successfulInstallCount++;
        }

        if (successfulInstallCount == 0) {
            throw new GradleException("Failed to install on any devices.");
        } else {
            getLogger().quiet("Installed on {} {}.", successfulInstallCount,
                              successfulInstallCount == 1 ? "device" : "devices");
        }
    }

    private static Field sDevice;

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

    private static String getAwbPackageName(@NonNull File inputFile) {
        // get the filename
        String name = inputFile.getName();
        // remove the extension
        int pos = name.lastIndexOf('.');
        if (pos != -1) {
            name = name.substring(0, pos);
        }

        return name.substring(3).replace("_", ".");
    }

    private void installPatch(IDevice device, File patch, String name)
        throws TimeoutException, AdbCommandRejectedException, SyncException, IOException {
        String PATCH_INSTALL_DIRECTORY = PATCH_INSTALL_DIRECTORY_PREFIX + getAppPackageName()
            + PATCH_INSTALL_DIRECTORY_SUFFIX + name + PATCH_NAME;
        device.pushFile(patch.getAbsolutePath(), PATCH_INSTALL_DIRECTORY);
    }

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

    @InputFile
    @Optional
    public File getMainDexFile() {
        return mainDexFile;
    }

    public void setMainDexFile(File maindexFile) {
        this.mainDexFile = maindexFile;
    }

    @InputFiles
    @Optional
    public Collection<File> getAwbApkFiles() {
        return awbApkFiles;
    }

    public void setAwbApkFiles(Collection<File> awbApkFiles) {
        this.awbApkFiles = awbApkFiles;
    }

    public static class ConfigAction extends MtlBaseTaskAction<IncrementalInstallVariantTask> {
        private final AppVariantContext appVariantContext;

        private final VariantScope scope;

        public ConfigAction(AppVariantContext appVariantContext, BaseVariantOutputData baseVariantOutputData) {
            super(appVariantContext, baseVariantOutputData);
            this.appVariantContext = appVariantContext;
            this.scope = baseVariantOutputData.getScope().getVariantScope();
        }

        @NonNull
        @Override
        public String getName() {
            return scope.getTaskName("installIncremental");
        }

        @NonNull
        @Override
        public Class<IncrementalInstallVariantTask> getType() {
            return IncrementalInstallVariantTask.class;
        }

        @Override
        public void execute(@NonNull IncrementalInstallVariantTask incrementalInstallVariantTask) {
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
            ConventionMappingHelper.map(incrementalInstallVariantTask, "mainDexFile", new Callable<File>() {

                @Override
                public File call() {
                    AtlasDependencyTree atlasDependencyTree = AtlasBuildContext.androidDependencyTrees.get(
                        incrementalInstallVariantTask.getVariantName());
                    List<String> allDependencies = atlasDependencyTree.getMainBundle().getAllDependencies();
                    if (allDependencies.size() == 0) {
                        return null;
                    }
                    return getAppVariantOutputContext().getApkOutputFile(true);
                }
            });
            ConventionMappingHelper.map(incrementalInstallVariantTask, "awbApkFiles",
                                        appVariantContext::getAwbApkFiles);
        }
    }
}
