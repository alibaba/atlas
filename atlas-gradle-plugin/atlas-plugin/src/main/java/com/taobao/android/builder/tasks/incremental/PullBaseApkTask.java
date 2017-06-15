package com.taobao.android.builder.tasks.incremental;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.android.build.gradle.internal.api.AppVariantContext;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.scope.ConventionMappingHelper;
import com.android.build.gradle.internal.tasks.BaseTask;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.BaseVariantOutputData;
import com.android.builder.sdk.SdkInfo;
import com.android.builder.testing.ConnectedDeviceProvider;
import com.android.builder.testing.api.DeviceConnector;
import com.android.builder.testing.api.DeviceException;
import com.android.builder.testing.api.DeviceProvider;
import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.MultiLineReceiver;
import com.android.ddmlib.ShellCommandUnresponsiveException;
import com.android.ddmlib.TimeoutException;
import com.android.ide.common.process.ProcessException;
import com.android.ide.common.process.ProcessExecutor;
import com.android.utils.ILogger;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.taobao.android.builder.tasks.manager.MtlBaseTaskAction;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.TaskAction;

/**
 * Created by chenhjohn on 2017/6/14.
 */

public class PullBaseApkTask extends BaseTask {

    /**
     * Pattern to parse the output of the 'pm path com.mypackage.myapp' command.<br>
     * The output format looks like:<br>
     * /data/app/com.mypackage.myapp.apk
     */
    private static final Pattern sPmPattern = Pattern.compile("^package:(.+?)$"); //$NON-NLS-1$

    private AppVariantContext appVariantContext;

    private File adbExe;

    private ProcessExecutor processExecutor;

    private String appPackageName;

    private int timeOutInMs = 0;

    @TaskAction
    public void taskAction()
        throws DeviceException, ProcessException, InterruptedException, TimeoutException, AdbCommandRejectedException,
               ShellCommandUnresponsiveException, IOException {
        final ILogger iLogger = getILogger();
        DeviceProvider deviceProvider = new ConnectedDeviceProvider(getAdbExe(), getTimeOutInMs(), iLogger);
        deviceProvider.init();

        List<? extends DeviceConnector> devices = deviceProvider.getDevices();
        Preconditions.checkState(devices.size() == 1, "There must be exactly one device");
        DeviceConnector device = devices.get(0);
        final List<String> output = new ArrayList<String>();
        String command = "pm path " + getAppPackageName();
        MultiLineReceiver outputReceiver = new MultiLineReceiver() {
            @Override
            public void processNewLines(String[] lines) {
                for (String line : lines) {
                    if (!line.isEmpty()) {
                        // get the filepath and package from the line
                        Matcher m = sPmPattern.matcher(line);
                        if (m.matches()) {
                            String apkPath = m.group(1);
                            output.add(apkPath);
                        }
                    }
                }
            }

            @Override
            public boolean isCancelled() {
                return false;
            }
        };
        device.executeShellCommand(command, outputReceiver, getTimeOutInMs(), TimeUnit.SECONDS);
        device.pullFile(Iterables.getOnlyElement(output), appVariantContext.apContext.getBaseApk().getPath());
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

    public static class ConfigAction extends MtlBaseTaskAction<PullBaseApkTask> {

        private final AppVariantContext appVariantContext;

        public ConfigAction(AppVariantContext appVariantContext, BaseVariantOutputData baseVariantOutputData) {
            super(appVariantContext, baseVariantOutputData);
            this.appVariantContext = appVariantContext;
        }

        @Override
        public String getName() {
            return scope.getTaskName("pull", "BaseApk");
        }

        @Override
        public Class<PullBaseApkTask> getType() {
            return PullBaseApkTask.class;
        }

        @Override
        public void execute(PullBaseApkTask task) {

            super.execute(task);
            File baseApFile = ApDependencies.getBaseApFile(scope.getGlobalScope().getProject(),
                                                           appVariantContext.getBuildType());
            if (baseApFile != null) {
                task.setEnabled(false);
            }
            BaseVariantData<? extends BaseVariantOutputData> variantData = scope.getVariantScope().getVariantData();
            final GradleVariantConfiguration variantConfiguration = variantData.getVariantConfiguration();
            task.appVariantContext = appVariantContext;
            task.setTimeOutInMs(scope.getGlobalScope().getExtension().getAdbOptions().getTimeOutInMs());
            task.setProcessExecutor(scope.getGlobalScope().getAndroidBuilder().getProcessExecutor());

            ConventionMappingHelper.map(task, "adbExe", new Callable<File>() {
                @Override
                public File call() throws Exception {
                    final SdkInfo info = scope.getGlobalScope().getSdkHandler().getSdkInfo();
                    return (info == null ? null : info.getAdb());
                }
            });

            ConventionMappingHelper.map(task, "appPackageName", variantConfiguration::getApplicationId);
        }
    }
}
