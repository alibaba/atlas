package com.android.build.gradle.internal.api;

import com.android.build.gradle.AppExtension;
import com.android.build.gradle.FeatureExtension;
import com.android.build.gradle.tasks.MergeManifests;
import com.taobao.android.builder.extension.AtlasExtension;
import org.gradle.api.Project;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;

import java.io.File;

/**
 * FeatureVariantContext
 *
 * @author zhayu.ll
 * @date 18/1/4
 * @time 下午2:50
 * @description  
 */
public class FeatureVariantContext extends VariantContext {

    public FeatureVariantContext(FeatureVariantImpl featureVariant, Project project, AtlasExtension atlasExtension, FeatureExtension featureExtension) {
        super(featureVariant,project,atlasExtension,featureExtension);
    }

    public File getModifiedManifest(ResolvedArtifactResult artifact) {
        return new File(getModifyManifestDir(),
                MergeManifests.getArtifactName(artifact) +
                        ".xml");

    }

    public File getModifyManifestDir() {
        return new File(scope.getGlobalScope().getIntermediatesDir(),
                "/manifest-modify/" + getVariantConfiguration().getDirName() + "/");
    }
}
