package com.taobao.android.builder.tasks.instantapp;

import com.android.annotations.NonNull;
import com.android.build.gradle.api.BaseVariantOutput;
import com.android.build.gradle.internal.api.VariantContext;
import com.android.build.gradle.internal.publishing.AndroidArtifacts;
import com.android.build.gradle.internal.scope.*;
import com.android.build.gradle.internal.tasks.ApplicationId;
import com.android.build.gradle.internal.tasks.DefaultAndroidTask;
import com.android.build.gradle.tasks.BundleInstantApp;
import com.android.utils.FileUtils;
import com.taobao.android.builder.tasks.manager.MtlBaseTaskAction;
import org.apache.commons.compress.utils.IOUtils;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static com.android.SdkConstants.DOT_ZIP;

public class AtlasBundleInstantApp extends DefaultAndroidTask {

    @TaskAction
    public void taskAction() throws IOException {
        FileUtils.mkdirs(bundleDirectory);

        File bundleFile = new File(bundleDirectory, bundleName);
        FileUtils.deleteIfExists(bundleFile);

        // FIXME: Use ZFile to compress in parallel.
        try (ZipOutputStream zipOutputStream =
                     new ZipOutputStream(new FileOutputStream(bundleFile))) {
            for (File apkDirectory : apkDirectories) {
                Collection<BuildOutput> buildOutputs = BuildOutputs.load(apkDirectory);
                for (BuildOutput buildOutput : buildOutputs) {
                    if (buildOutput.getType() == TaskOutputHolder.TaskOutputType.APK) {
                        File apkFile = buildOutput.getOutputFile();
                        try (FileInputStream fileInputStream = new FileInputStream(apkFile)) {
                            byte[] inputBuffer = IOUtils.toByteArray(fileInputStream);
                            zipOutputStream.putNextEntry(new ZipEntry(apkFile.getName()));
                            zipOutputStream.write(inputBuffer, 0, inputBuffer.length);
                            zipOutputStream.closeEntry();
                        }
                    }
                }
            }
        }

//        // Write the json output.
//        InstantAppOutputScope instantAppOutputScope =
//                new InstantAppOutputScope(
//                        ApplicationId.load(applicationId.getSingleFile()).getApplicationId(),
//                        bundleFile,
//                        apkDirectories.getFiles().stream().collect(Collectors.toList()));
//        instantAppOutputScope.save(bundleDirectory);
    }


    private File bundleDirectory;
    private String bundleName;

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
            bundleInstantApp.bundleDirectory = bundleDirectory;
            bundleInstantApp.bundleName =
                    scope.getGlobalScope().getProjectBaseName()
                            + "-"
                            + scope.getVariantConfiguration().getBaseName()
                            + DOT_ZIP;
        }

        private final VariantScope scope;
        private final File bundleDirectory;
    }
}
