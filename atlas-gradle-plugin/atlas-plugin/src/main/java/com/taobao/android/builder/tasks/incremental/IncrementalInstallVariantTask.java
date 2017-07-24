package com.taobao.android.builder.tasks.incremental;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.api.AppVariantContext;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.variant.BaseVariantOutputData;
import com.android.builder.testing.api.DeviceException;
import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.CollectingOutputReceiver;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.ShellCommandUnresponsiveException;
import com.android.ddmlib.SyncException;
import com.android.ddmlib.TimeoutException;
import com.google.common.base.Joiner;
import org.gradle.api.tasks.ParallelizableTask;

/**
 * @author chenhjohn
 * @date 2017/5/25
 */

@ParallelizableTask
public class IncrementalInstallVariantTask extends BaseIncrementalInstallVariantTask {

    private static final String PATCH_NAME = "patch.zip";

    private static final String PATCH_INSTALL_DIRECTORY_SUFFIX = "files/debug_storage/";

    @Override
    protected void install(String projectName, String variantName, String appPackageName, IDevice device,
                           Collection<File> apkFiles) throws Exception {
        //安装awb
        //安装mainDex
        String patchInstallDirectory = getPatchInstallDirectory();
        if (apkFiles != null) {
            for (File apkFile : apkFiles) {

                installPatch(projectName,
                             variantName,
                             appPackageName,
                             device,
                             apkFile,
                             getAwbPackageName(apkFile),
                             patchInstallDirectory);
            }
        }

        getLogger().lifecycle("Restarting '{}' on '{}' for {}:{}",
                              appPackageName,
                              device.getName(),
                              projectName,
                              variantName);
        restartApp(device, appPackageName);
    }

    void restartApp(IDevice device, String appPackageName)
        throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException, IOException,
               DeviceException {
        // //退到后台
        // runCommand(device, "input keyevent 3");

        //杀死进程
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
        startApp(device, appPackageName);
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
        getLogger().lifecycle("Installing awb '{}' on '{}' to '{}' for {}:{}",
                              patch,
                              device.getName(),
                              remotePatchFile,
                              projectName,
                              variantName);
        device.pushFile(patch.getAbsolutePath(), remotePatchFile);
    }

    private String getPatchInstallDirectory() {
        return Joiner.on('/').join(PATCH_INSTALL_DIRECTORY_PREFIX, getAppPackageName(), PATCH_INSTALL_DIRECTORY_SUFFIX);
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
