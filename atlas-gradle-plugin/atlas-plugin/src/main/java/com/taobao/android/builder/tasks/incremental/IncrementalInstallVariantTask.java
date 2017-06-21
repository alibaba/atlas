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
import com.android.ddmlib.SyncException;
import com.android.ddmlib.TimeoutException;
import com.android.utils.FileUtils;
import org.gradle.api.tasks.ParallelizableTask;

/**
 * @author chenhjohn
 * @date 2017/5/25
 */

@ParallelizableTask
public class IncrementalInstallVariantTask extends BaseIncrementalInstallVariantTask {

    public static final String PATCH_NAME = "/patch.zip";

    public static final String PATCH_INSTALL_DIRECTORY_PREFIX = "/sdcard/Android/data/";

    public static final String PATCH_INSTALL_DIRECTORY_SUFFIX = "/files/debug_storage/";

    @Override
    protected void install(String projectName, String variantName, String appPackageName, IDevice device,
                           Collection<File> apkFiles) throws Exception {
        //安装awb
        //安装mainDex
        String patchInstallDirectory = getPatchInstallDirectory();
        if (apkFiles != null) {
            for (File apkFile : apkFiles) {
                getLogger().lifecycle("Installing awb '{}' on '{}' for {}:{}", apkFile, device.getName(), projectName,
                                      variantName);

                installPatch(device, apkFile, getAwbPackageName(apkFile), patchInstallDirectory);
            }
        }

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
    }

    private void installPatch(IDevice device, File patch, String name, String patchInstallDirectory)
        throws TimeoutException, AdbCommandRejectedException, SyncException, IOException {
        patchInstallDirectory = FileUtils.join(patchInstallDirectory, name, PATCH_NAME);
        device.pushFile(patch.getAbsolutePath(), patchInstallDirectory);
    }

    private String getPatchInstallDirectory() {
        return FileUtils.join(PATCH_INSTALL_DIRECTORY_PREFIX, getAppPackageName(), PATCH_INSTALL_DIRECTORY_SUFFIX);
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
