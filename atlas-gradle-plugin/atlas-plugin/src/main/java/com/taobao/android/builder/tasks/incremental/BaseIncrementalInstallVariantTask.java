package com.taobao.android.builder.tasks.incremental;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.api.AppVariantContext;
import com.android.build.gradle.internal.api.AppVariantOutputContext;
import com.android.build.gradle.internal.scope.ConventionMappingHelper;
import com.android.build.gradle.internal.variant.BaseVariantOutputData;
import com.android.builder.core.VariantConfiguration;
import com.android.ddmlib.CollectingOutputReceiver;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.MultiLineReceiver;
import com.android.ide.common.res2.FileStatus;
import com.google.common.collect.ImmutableList;
import com.taobao.android.builder.AtlasBuildContext;
import com.taobao.android.builder.dependency.AtlasDependencyTree;
import org.gradle.api.GradleException;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;

import static com.android.build.gradle.internal.api.AppVariantOutputContext.MAINDEX_FILE_NAME;

/**
 * Created by chenhjohn on 2017/6/21.
 */

abstract class BaseIncrementalInstallVariantTask extends DeviceTask {
    public static final String PATCH_INSTALL_DIRECTORY_PREFIX = "/sdcard/Android/data/";

    private static final Pattern VERSION_NAME_PATTERN = Pattern.compile("versionName=([^']*)$");

    private Collection<File> apkFiles;
    /*private*/ AtlasDependencyTree atlasDependencyTree;

    static boolean hasBinary(IDevice device, String path) {
        CountDownLatch latch = new CountDownLatch(1);
        CollectingOutputReceiver receiver = new CollectingOutputReceiver(latch);
        try {
            device.executeShellCommand("ls " + path, receiver, LS_TIMEOUT_SEC, TimeUnit.SECONDS);
        } catch (Exception e) {
            return false;
        }
        try {
            latch.await(LS_TIMEOUT_SEC, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            return false;
        }
        String value = receiver.getOutput().trim();
        return !value.endsWith("No such file or directory");
    }

    /*static*/ String getAwbPackageName(@NonNull File inputFile) {
        // get the filename
        String name = inputFile.getName();
        // remove the extension
        int pos = name.lastIndexOf('.');
        if (pos != -1) {
            name = name.substring(0, pos);
        }

        return name.substring(3).replace("_", ".");
        // List<AwbBundle> awbBundles = atlasDependencyTree.getAwbBundles();
        // for (AwbBundle awbBundle : awbBundles) {
        //     if (awbBundle.getAwbSoName().equals(inputFile.getName())) {
        //         return awbBundle.getPackageName();
        //     }
        // }
        // throw new IllegalStateException("Cannot find package for:" + inputFile);
    }

    @Override
    protected boolean isIncremental() {
        return true;
    }

    @Override
    protected void doFullTaskAction(IDevice device) throws Exception {
        install(getApkFiles(), device);
    }

    private void install(Collection<File> apkFiles, IDevice device) {
        try {
            VariantConfiguration variantConfig = variantData.getVariantConfiguration();
            String variantName = variantConfig.getFullName();
            String appPackageName = getAppPackageName();
            VersionNameReceiver versionNameReceiver = new VersionNameReceiver();
            device.executeShellCommand("dumpsys package " + appPackageName,//$NON-NLS-1$
                versionNameReceiver);
            String versionName = getVersionName();
            String versionName1 = versionNameReceiver.getVersionName();
            if (!versionName.equals(versionName1)) {
                getLogger().warn(String.format(
                    "versionName declared at project %1$s value=(%2$s)\n" + "\thas a different value=(%3$s) "
                        + "declared at device %4$s\n", projectName, versionName, versionName1, device));
            }
            install(projectName, variantName, appPackageName, device, apkFiles);
        } catch (Exception e) {
            throw new GradleException("Failed to install on device '" + device + "'", e);
        }
    }

    @Override
    protected void doIncrementalTaskAction(IDevice device, Map<File, FileStatus> changedInputs) throws IOException {
        ImmutableList.Builder<File> builder = ImmutableList.builder();
        File maindexFile = null;
        for (final Map.Entry<File, FileStatus> entry : changedInputs.entrySet()) {
            File file = entry.getKey();
            FileStatus status = entry.getValue();
            switch (status) {
                case NEW:
                case CHANGED:
                    if (MAINDEX_FILE_NAME.equals(file.getName())) {
                        maindexFile = file;
                    } else {
                        builder.add(file);
                    }

                    break;
                case REMOVED:
                    break;
            }
        }
        if (maindexFile != null) {
            builder.add(maindexFile);
        }
        install(builder.build(), device);
    }

    protected abstract void install(String projectName, String variantName, String appPackageName, IDevice device,
                                    Collection<File> apkFiles) throws Exception;

    @InputFiles
    @Optional
    public Collection<File> getApkFiles() {
        return apkFiles;
    }

    public void setApkFiles(Collection<File> apkFiles) {
        this.apkFiles = apkFiles;
    }

    public abstract static class ConfigAction<T extends BaseIncrementalInstallVariantTask>
        extends DeviceTask.ConfigAction<T> {

        public ConfigAction(AppVariantContext appVariantContext, BaseVariantOutputData baseVariantOutputData) {
            super(appVariantContext, baseVariantOutputData, appVariantContext);
        }

        @Override
        public void execute(@NonNull T deviceTask) {
            super.execute(deviceTask);
            AppVariantOutputContext appVariantOutputContext = appVariantContext.getAppVariantOutputContext(
                baseVariantOutputData);
            AtlasDependencyTree atlasDependencyTree = AtlasBuildContext.androidDependencyTrees.get(
                deviceTask.getVariantName());
            //TODO 先根据依赖判断
            ConventionMappingHelper.map(deviceTask, "apkFiles", new Callable<ImmutableList<File>>() {

                @Override
                public ImmutableList<File> call() {
                    ImmutableList.Builder<File> builder = ImmutableList.builder();
                    //推送Awb
                    Collection<File> awbApkFiles = appVariantOutputContext.getAwbApkFiles();
                    if (awbApkFiles != null) {
                        builder.addAll(awbApkFiles);
                    }
                    //TODO 先根据依赖判断
                    //推送Main
                    List<String> allDependencies = atlasDependencyTree.getMainBundle().getAllDependencies();
                    if (allDependencies.size() > 0) {
                        builder.add(getAppVariantOutputContext().getPatchApkOutputFile());
                    }
                    return builder.build();
                }
            });
            deviceTask.atlasDependencyTree = atlasDependencyTree;
        }
    }

    private static class VersionNameReceiver extends MultiLineReceiver {
        private String mVersionName;

        @Override
        public void processNewLines(String[] lines) {
            for (String line : lines) {
                Matcher versionNameMatch = VERSION_NAME_PATTERN.matcher(line);
                if (versionNameMatch.matches()) {
                    mVersionName = versionNameMatch.group(1);
                }
            }
        }

        @Override
        public boolean isCancelled() {
            return mVersionName != null;
        }

        public String getVersionName() {
            return mVersionName;
        }
    }
}
