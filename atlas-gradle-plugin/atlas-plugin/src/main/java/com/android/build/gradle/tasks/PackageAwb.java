package com.android.build.gradle.tasks;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.incremental.InstantRunPatchingPolicy;
import com.android.build.gradle.internal.scope.*;
import com.android.builder.utils.FileCache;
import org.gradle.api.file.FileCollection;

import java.io.File;

/**
 * Created by chenhjohn on 2017/5/11.
 *
 * @author chenhjohn
 * @date 2017/05/11
 */

public class PackageAwb extends PackageAndroidArtifact {


    // ----- ConfigAction -----

    /**
     * Configures the task to perform the "standard" packaging, including all
     * files that should end up in the APK.
     */
    public static class StandardConfigAction extends ConfigAction<PackageAwb> {


        public StandardConfigAction(PackagingScope packagingScope, File outputDirectory, InstantRunPatchingPolicy patchingPolicy, TaskOutputHolder.TaskOutputType inputResourceFilesType, FileCollection resourceFiles, FileCollection manifests, TaskOutputHolder.TaskOutputType manifestType, FileCache fileCache, OutputScope outputScope) {

            super(packagingScope, outputDirectory, patchingPolicy, inputResourceFilesType, resourceFiles, manifests, manifestType, fileCache, outputScope);
        }

        @NonNull
        @Override
        public String getName() {
            return packagingScope.getTaskName("package");
        }

        @NonNull
        @Override
        public Class<PackageAwb> getType() {
            return PackageAwb.class;
        }

        @Override
        public void execute(@NonNull final PackageAwb PackageAwb) {
            ConventionMappingHelper.map(PackageAwb, "outputFile", packagingScope::getOutputScope);
            super.execute(PackageAwb);
        }
    }


    protected VariantScope.TaskOutputType getTaskOutputType(){

        return TaskOutputHolder.TaskOutputType.APK;
    }

    @Override
    void recordMetrics(File outputFile, File resourcesApFile) {
            //
        return;
    }


}
