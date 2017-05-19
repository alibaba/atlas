package com.taobao.android.builder.tasks.app.bundle;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.incremental.InstantRunPatchingPolicy;
import com.android.build.gradle.internal.scope.ConventionMappingHelper;
import com.android.build.gradle.internal.scope.PackagingScope;
import com.android.build.gradle.tasks.PackageAndroidArtifact;
import org.gradle.api.tasks.ParallelizableTask;

/**
 * Created by chenhjohn on 2017/5/11.
 *
 * @author chenhjohn
 * @date 2017/05/11
 */

@ParallelizableTask
public class PackageAwb extends PackageAndroidArtifact {
    // ----- ConfigAction -----

    /**
     * Configures the task to perform the "standard" packaging, including all
     * files that should end up in the APK.
     */
    public static class StandardConfigAction extends ConfigAction<PackageAwb> {

        public StandardConfigAction(@NonNull PackagingScope scope, @Nullable InstantRunPatchingPolicy patchingPolicy) {
            super(scope, patchingPolicy);
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
            ConventionMappingHelper.map(PackageAwb, "outputFile", packagingScope::getOutputPackage);
            super.execute(PackageAwb);
        }
    }

}
