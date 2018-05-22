package com.android.build.gradle.internal.transforms;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.publishing.AtlasAndroidArtifacts;
import org.gradle.api.artifacts.transform.ArtifactTransform;

import javax.inject.Inject;
import java.io.File;
import java.util.Collections;
import java.util.List;

/**
 * @author lilong
 * @create 2017-12-03 下午1:14
 */

public class ApTransform extends ArtifactTransform {

    @NonNull
    private final AtlasAndroidArtifacts.AtlasArtifactType targetType;

    @Inject
    public ApTransform(@NonNull AtlasAndroidArtifacts.AtlasArtifactType targetType) {
        this.targetType = targetType;
    }

    @NonNull
    public static AtlasAndroidArtifacts.AtlasArtifactType[] getTransformTargets() {
        return new AtlasAndroidArtifacts.AtlasArtifactType[] {
                AtlasAndroidArtifacts.AtlasArtifactType.BASE_APK,
                AtlasAndroidArtifacts.AtlasArtifactType.BASE_MANIFEST,
                AtlasAndroidArtifacts.AtlasArtifactType.BASE_AWO,
                AtlasAndroidArtifacts.AtlasArtifactType.BASE_ATLAS_JSON,
                AtlasAndroidArtifacts.AtlasArtifactType.BASE_REMOTE_BUNDLE,
                AtlasAndroidArtifacts.AtlasArtifactType.BASE_PACKAGE_IDS,
                AtlasAndroidArtifacts.AtlasArtifactType.BASE_ARSC,
                AtlasAndroidArtifacts.AtlasArtifactType.BASE_VERSIONS,
                AtlasAndroidArtifacts.AtlasArtifactType.BASE_DEPENDENCIES,
                AtlasAndroidArtifacts.AtlasArtifactType.BASE_R,
                AtlasAndroidArtifacts.AtlasArtifactType.BASE_APK_FILES,
                AtlasAndroidArtifacts.AtlasArtifactType.BASE_BUILD_TXT,
                AtlasAndroidArtifacts.AtlasArtifactType.BASE_MUPP_JSON,
                AtlasAndroidArtifacts.AtlasArtifactType.MANIFEST,

        };
    }

    @Override
    public List<File> transform(File input) {
        File file;

        file = new File(input,targetType.getType());
        if (file.exists()) {
            return Collections.singletonList(file);
        }

        return Collections.emptyList();
    }
}
