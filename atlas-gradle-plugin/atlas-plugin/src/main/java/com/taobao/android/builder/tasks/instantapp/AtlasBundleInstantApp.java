package com.taobao.android.builder.tasks.instantapp;

import com.android.annotations.NonNull;
import com.android.build.gradle.api.BaseVariantOutput;
import com.android.build.gradle.internal.api.VariantContext;
import com.android.build.gradle.internal.dsl.CoreSigningConfig;
import com.android.build.gradle.internal.scope.*;
import com.android.build.gradle.internal.tasks.DefaultAndroidTask;
import com.android.build.gradle.internal.variant.MultiOutputPolicy;
import com.android.builder.signing.DefaultSigningConfig;
import com.android.builder.signing.SigningException;
import com.android.utils.FileUtils;
import com.taobao.android.builder.tasks.manager.MtlBaseTaskAction;
import com.taobao.android.builder.tools.sign.AndroidSigner;
import org.apache.commons.compress.utils.IOUtils;
import org.gradle.api.tasks.TaskAction;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Enumeration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static com.android.SdkConstants.DOT_ANDROID_PACKAGE;
import static com.android.SdkConstants.DOT_ZIP;

public class AtlasBundleInstantApp extends DefaultAndroidTask {

    private File apkFile;

    private File bundleDirectory;
    private String bundleName;
    private VariantScope scope;
    private static Pattern excludePattern = Pattern.compile("^(META-INF/)\\w*");
    private static Pattern apkPattern = Pattern.compile("libcom_\\w*(.so)$");
    private static Pattern storePattern = Pattern.compile("^(raw/)|(^assets/)");



    @TaskAction
    public void taskAction() throws IOException {
        FileUtils.mkdirs(bundleDirectory);
        File bundleFile = new File(bundleDirectory, bundleName);
        FileUtils.deleteIfExists(bundleFile);
        File baseFeatureApk = new File(bundleDirectory, "baseFeature.apk");
        if (apkFile.exists()) {
            try {
                make(baseFeatureApk, apkFile, bundleFile, scope.getVariantConfiguration().getSigningConfig());
            } catch (SigningException e) {
                e.printStackTrace();
            }
        }
    }


    private void make(File baseFeatureApk, File apkFile, File bundleFile, CoreSigningConfig signingConfig) throws IOException, SigningException {
        ZipFile zipFile = new ZipFile(apkFile);
        Enumeration entries = zipFile.entries();
        ZipOutputStream zipOutputStream = new ZipOutputStream(new FileOutputStream(bundleFile));
        ZipOutputStream baseFeatureStream = new ZipOutputStream(new FileOutputStream(baseFeatureApk));
        while (entries.hasMoreElements()) {
            ZipEntry zipEntry = (ZipEntry) entries.nextElement();
            if (excludePattern.matcher(zipEntry.getName()).find()) {
                continue;
            } else if (apkPattern.matcher(zipEntry.getName()).find()) {
                byte[] inputBuffer = IOUtils.toByteArray(zipFile.getInputStream(zipEntry));
                zipOutputStream.putNextEntry(new ZipEntry(zipEntry.getName().substring(zipEntry.getName().lastIndexOf("/") + 1).replace(".so", DOT_ANDROID_PACKAGE)));
                zipOutputStream.write(inputBuffer, 0, inputBuffer.length);
                zipOutputStream.closeEntry();
            } else {
                byte[] inputBuffer = IOUtils.toByteArray(zipFile.getInputStream(zipEntry));
                if (storePattern.matcher(zipEntry.getName()).find()) {
                    baseFeatureStream.putNextEntry(new ZipEntry(zipEntry));
                } else {
                    baseFeatureStream.putNextEntry(new ZipEntry(zipEntry.getName()));

                }
                baseFeatureStream.write(inputBuffer, 0, inputBuffer.length);
                baseFeatureStream.closeEntry();
            }
        }
        baseFeatureStream.close();
        AndroidSigner androidSigner = new AndroidSigner();
        File signedApk = new File(baseFeatureApk.getParentFile(), "baseFeature-signed.apk");
        androidSigner.signFile(baseFeatureApk, signedApk, (DefaultSigningConfig) signingConfig);
        byte[] inputBuffer = IOUtils.toByteArray(new ZipInputStream(new FileInputStream(signedApk)));
        zipOutputStream.putNextEntry(new ZipEntry(baseFeatureApk.getName()));
        zipOutputStream.write(inputBuffer, 0, inputBuffer.length);
        zipOutputStream.closeEntry();
        zipOutputStream.close();
        FileUtils.deleteIfExists(signedApk);
        FileUtils.deleteIfExists(baseFeatureApk);
    }


    public static class ConfigAction extends MtlBaseTaskAction<AtlasBundleInstantApp> {

        public ConfigAction(VariantContext variantContext, BaseVariantOutput baseVariantOutput) {
            super(variantContext, baseVariantOutput);
        }

        @NonNull
        @Override
        public String getName() {
            return scope.getTaskName("package", "AtlasInstantAppBundle");
        }

        @NonNull
        @Override
        public Class<AtlasBundleInstantApp> getType() {
            return AtlasBundleInstantApp.class;
        }

        @Override
        public void execute(@NonNull AtlasBundleInstantApp bundleInstantApp) {
            bundleInstantApp.setVariantName(scope.getFullVariantName());
            final boolean splitsArePossible =
                    scope.getOutputScope().getMultiOutputPolicy() == MultiOutputPolicy.SPLITS;
            File finalApkLocation = scope.getApkLocation();
            File outputDirectory =
                    splitsArePossible
                            ? scope.getFullApkPackagesOutputDirectory()
                            : finalApkLocation;
            bundleInstantApp.bundleName = scope.getOutputScope().getApkDatas().get(0).getOutputFileName().replace(DOT_ANDROID_PACKAGE, DOT_ZIP);

            bundleInstantApp.bundleDirectory = outputDirectory;

            bundleInstantApp.scope = variantContext.getScope();

            bundleInstantApp.apkFile = new File(outputDirectory, scope.getOutputScope().getApkDatas().get(0).getOutputFileName());

        }
    }

}
