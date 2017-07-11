package com.taobao.android.builder.tasks.incremental;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.api.AppVariantContext;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.variant.BaseVariantOutputData;
import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.MultiLineReceiver;
import com.android.ddmlib.ShellCommandUnresponsiveException;
import com.android.ddmlib.SyncException;
import com.android.ddmlib.TimeoutException;
import com.android.utils.FileUtils;

/**
 * Created by chenhjohn on 2017/6/21.
 */

public class AwosInstallTask extends IncrementalInstallVariantTask {
    public static final String PATCH_INSTALL_DIRECTORY_SUFFIX = "/files/atlas-debug/";

    @Override
    protected void install(String projectName, String variantName, String appPackageName, IDevice device,
                           Collection<File> apkFiles) throws Exception {
        String patchInstallDirectory = getPatchInstallDirectory();
        //安装mainDex
        if (apkFiles != null) {
            for (File apkFile : apkFiles) {
                getLogger().lifecycle("Installing awb '{}' on '{}' for {}:{}",
                                      apkFile,
                                      device.getName(),
                                      projectName,
                                      variantName);
                installPatch(device, apkFile, apkFile.getName(), patchInstallDirectory, getAppPackageName());
            }
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
        }
    }

    private void installPatch(IDevice device, File patch, String name, String patchInstallDirectory,
                              String appPackageName)
        throws TimeoutException, AdbCommandRejectedException, SyncException, IOException,
               ShellCommandUnresponsiveException {
        patchInstallDirectory = FileUtils.join(patchInstallDirectory, name);
        device.pushFile(patch.getAbsolutePath(), patchInstallDirectory);
        device.executeShellCommand(
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
            });
        //循环监听
        int sleepTime = 1000;
        while (hasBinary(device, patchInstallDirectory)) {
            try {
                Thread.sleep(sleepTime);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private String getPatchInstallDirectory() {
        return FileUtils.join(PATCH_INSTALL_DIRECTORY_PREFIX, getAppPackageName(), PATCH_INSTALL_DIRECTORY_SUFFIX);
    }

    public static class ConfigAction extends BaseIncrementalInstallVariantTask.ConfigAction<AwosInstallTask> {
        private final VariantScope scope;

        public ConfigAction(AppVariantContext appVariantContext, BaseVariantOutputData baseVariantOutputData) {
            super(appVariantContext, baseVariantOutputData);
            this.scope = baseVariantOutputData.getScope().getVariantScope();
        }

        @NonNull
        @Override
        public String getName() {
            return scope.getTaskName("installAwos");
        }

        @NonNull
        @Override
        public Class<AwosInstallTask> getType() {
            return AwosInstallTask.class;
        }
    }
}
