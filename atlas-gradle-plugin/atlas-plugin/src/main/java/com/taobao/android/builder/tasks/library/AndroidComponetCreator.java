package com.taobao.android.builder.tasks.library;

import com.taobao.android.builder.extension.AtlasExtension;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.internal.artifacts.publish.ArchivePublishArtifact;
import org.gradle.api.tasks.bundling.Zip;

import static com.taobao.android.builder.AtlasLibTaskManager.COMPILE_CONFIGURATION_NAME;

/**
 * Created by wuzhong on 2017/2/25.
 */
public class AndroidComponetCreator {

    private AtlasExtension atlasExtension;

    private Project project;

    public AndroidComponetCreator(AtlasExtension atlasExtension, Project project) {
        this.atlasExtension = atlasExtension;
        this.project = project;
    }

    public void createAndroidComponent(Zip bundleTask) {
        //增加一个components.android
        if (atlasExtension.getBundleConfig().isAwbBundle()) {
            Configuration compileConfiguration = project.getConfigurations()
                    .getByName(COMPILE_CONFIGURATION_NAME);
            ArchivePublishArtifact bundleArtifact = new ArchivePublishArtifact(bundleTask);
            compileConfiguration.getArtifacts().add(bundleArtifact);
        }
    }
}
