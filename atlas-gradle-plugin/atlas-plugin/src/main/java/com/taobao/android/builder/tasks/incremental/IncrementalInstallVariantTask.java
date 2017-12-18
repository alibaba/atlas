package com.taobao.android.builder.tasks.incremental;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.api.AppVariantContext;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.variant.BaseVariantOutputData;
import com.android.builder.testing.api.DeviceException;
import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.CollectingOutputReceiver;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.MultiLineReceiver;
import com.android.ddmlib.ShellCommandUnresponsiveException;
import com.android.ddmlib.SyncException;
import com.android.ddmlib.TimeoutException;
import com.android.ide.common.internal.WaitableExecutor;
import com.google.common.base.Joiner;
import org.gradle.api.tasks.ParallelizableTask;

import static com.google.common.collect.Iterables.isEmpty;

/**
 * @author chenhjohn
 * @date 2017/5/25
 */

@ParallelizableTask
public class IncrementalInstallVariantTask extends BaseIncrementalInstallVariantTask {

    private static final String PATCH_NAME = "patch.zip";

    /*private*/ static final String PATCH_INSTALL_DIRECTORY_SUFFIX = "files/debug_storage/";

    @Override
    protected void install(String projectName, String variantName, String appPackageName, IDevice device,
                           Collection<File> apkFiles) throws Exception {
        //安装awb
        //安装mainDex
        String patchInstallDirectory = getPatchInstallDirectory();
        if (apkFiles != null) {
            WaitableExecutor mExecutor = WaitableExecutor.useGlobalSharedThreadPool();

            for (File apkFile : apkFiles) {

                mExecutor.execute(new Callable<Void>() {
                    @Override
                    public Void call() throws Exception {
                        installPatch(projectName, variantName, appPackageName, device, apkFile,
                            getAwbPackageName(apkFile), patchInstallDirectory);
                        return null;
                    }
                });
            }
            mExecutor.waitForTasksWithQuickFail(true /*cancelRemaining*/);
        }

        getLogger().lifecycle("Restarting '{}' on '{}' for {}:{}", appPackageName, device.getName(), projectName,
            variantName);
        restartApp(device, appPackageName);
    }

    void restartApp(IDevice device, String appPackageName)
        throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException, IOException,
        DeviceException {
        Iterable<Integer> processPids = getProcessPids(device, appPackageName);
        if (isEmpty(processPids)) {
            //杀死进程
            //getLogger().lifecycle("实验特性界面恢复重启适配性问题，使用强制杀死进程，请联系歩川（步有个点，歩），告知机型");
            runCommand(device, "am force-stop " + appPackageName);
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
        } else {
            //退到后台
            //getLogger().lifecycle("实验特性，界面恢复重启，如有任何问题请随时与歩川（步有个点，歩）联系");
            runCommand(device, "input keyevent 3");
            boolean success = false;
            for (Integer processId : processPids) {
                /*device.executeShellCommand*/
                success |= runCommand(device, "run-as " + appPackageName + " kill -9 " + processId);
                if (!success) {
                    //getLogger().lifecycle("实验特性界面恢复重启适配性问题，使用强制杀死进程，请联系歩川（步有个点，歩），告知机型");
                    runCommand(device, "am force-stop " + appPackageName);
                    break;
                }
            }
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
        }

        startApp(device, appPackageName);
    }

    private Iterable<Integer> getProcessPids(IDevice device, String appPackageName)
        throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException, IOException {
        final List<Integer> pids = new ArrayList<Integer>(2);
        final MultiLineReceiver receiver = new MultiLineReceiver() {
            @Override
            public void processNewLines(String[] lines) {
                for (String line : lines) {
                    final String[] fields = line.split("\\s+");
                    if (fields.length < 2) {
                        continue;
                    }
                    try {
                        final int processId = Integer.parseInt(fields[1], 10);
                        pids.add(processId);
                    } catch (NumberFormatException ex) {
                        continue;
                    }
                }
            }

            @Override
            public boolean isCancelled() {
                return false;
            }
        };
        device.executeShellCommand("ps | grep " + appPackageName, receiver);
        return pids;
    }

    void startApp(IDevice device, String appPackageName)
        throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException, IOException,
        DeviceException {
        //启动
        CollectingOutputReceiver receiver = new CollectingOutputReceiver();
        String cmd = "monkey " + "-p " + appPackageName + " -c android.intent.category.LAUNCHER 1";
        device.executeShellCommand(cmd, receiver);
        String output = receiver.getOutput();
        if (output.contains("monkey aborted")) {
            throw new DeviceException("Unexpected shell output for " + cmd + ": " + output);
        }
    }

    private void installPatch(String projectName, String variantName, String appPackageName, IDevice device, File patch,
                              String name, String patchInstallDirectory)
        throws TimeoutException, AdbCommandRejectedException, SyncException, IOException {
        String remotePatchFile = Joiner.on('/').join(patchInstallDirectory, name, PATCH_NAME);
        getLogger().lifecycle("Installing awb '{}' on '{}' to '{}' for {}:{}", patch, device.getName(), remotePatchFile,
            projectName, variantName);
        device.pushFile(patch.getAbsolutePath(), remotePatchFile);
    }

    private String getPatchInstallDirectory() {
        return Joiner.on('/').join(PATCH_INSTALL_DIRECTORY_PREFIX, getAppPackageName(), PATCH_INSTALL_DIRECTORY_SUFFIX);
    }

    public static class ConfigAction
        extends BaseIncrementalInstallVariantTask.ConfigAction<IncrementalInstallVariantTask> {
        private final VariantScope scope;

        public ConfigAction(AppVariantContext appVariantContext, BaseVariantOutputData baseVariantOutputData) {
            super(appVariantContext, baseVariantOutputData);
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
    }
}
