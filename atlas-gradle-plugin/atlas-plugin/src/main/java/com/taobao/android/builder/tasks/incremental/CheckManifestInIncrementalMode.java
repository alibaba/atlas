package com.taobao.android.builder.tasks.incremental;

import java.io.File;
import java.io.IOException;

import com.android.annotations.NonNull;
import com.android.annotations.VisibleForTesting;
import com.android.build.gradle.internal.api.AppVariantContext;
import com.android.build.gradle.internal.tasks.DefaultAndroidTask;
import com.android.build.gradle.internal.variant.BaseVariantOutputData;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.taobao.android.builder.tasks.manager.MtlBaseTaskAction;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.TaskAction;

/**
 * Created by chenhjohn on 2017/5/22.
 */

public class CheckManifestInIncrementalMode extends DefaultAndroidTask {

    private static final Logger LOG = Logging.getLogger(CheckManifestInIncrementalMode.class);

    private AppVariantContext appVariantContext;

    private File manifestFile;

    private File incrementalSupportDir;

    @NonNull
    @InputFile

    public File getManifestFile() {
        return manifestFile;
    }

    public void setManifestFile(File manifestFile) {
        this.manifestFile = manifestFile;
    }

    @TaskAction
    public void taskAction() throws IOException {
        // If we are NOT  mode, this is an error, this task should not be running.
        if (!appVariantContext.getAtlasExtension().getTBuildConfig().isIncremental()) {
            LOG.warn("CheckManifestInIncrementalMode configured in non incremental build," + " please file a bug.");
            return;
        }
        // always do both, we should make sure that we are not keeping stale data for the previous
        // instance.
        File incrementalManifestFile = getManifestFile();
        LOG.info("CheckManifestInIncrementalMode : Merged manifest %1$s", incrementalManifestFile);
        runManifestChangeVerifier(appVariantContext, incrementalSupportDir, incrementalManifestFile);
    }

    @VisibleForTesting
    static void runManifestChangeVerifier(AppVariantContext appVariantContext, File SupportDir,
                                          @NonNull File manifestFileToPackage) throws IOException {
        File previousManifestFile = new File(SupportDir, "manifest.xml");
        if (previousManifestFile.exists()) {
            String currentManifest = Files.asCharSource(manifestFileToPackage, Charsets.UTF_8).read();
            String previousManifest = Files.asCharSource(previousManifestFile, Charsets.UTF_8).read();
            if (!currentManifest.equals(previousManifest)) {
                // TODO: Deeper comparison, call out just a version change.
                Files.copy(manifestFileToPackage, previousManifestFile);
            }
        } else {
            Files.createParentDirs(previousManifestFile);
            Files.copy(manifestFileToPackage, previousManifestFile);
        }
    }

    public static class ConfigAction extends MtlBaseTaskAction<CheckManifestInIncrementalMode> {

        private final AppVariantContext appVariantContext;

        public ConfigAction(AppVariantContext appVariantContext, BaseVariantOutputData baseVariantOutputData) {
            super(appVariantContext, baseVariantOutputData);
            this.appVariantContext = appVariantContext;
        }

        @Override
        public String getName() {
            return scope.getTaskName("checkManifestChanges", "");
        }

        @Override
        public Class<CheckManifestInIncrementalMode> getType() {
            return CheckManifestInIncrementalMode.class;
        }

        @Override
        public void execute(CheckManifestInIncrementalMode task) {

            super.execute(task);
        }
    }
}
