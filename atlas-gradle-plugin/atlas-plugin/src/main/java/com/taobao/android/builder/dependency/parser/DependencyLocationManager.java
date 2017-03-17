package com.taobao.android.builder.dependency.parser;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Optional;

import com.android.build.gradle.AndroidGradleOptions;
import com.android.build.gradle.internal.tasks.PrepareLibraryTask;
import com.android.builder.model.MavenCoordinates;
import com.android.builder.utils.FileCache;
import com.android.utils.FileUtils;
import com.google.common.base.Preconditions;
import org.gradle.api.Project;

import static com.android.builder.model.AndroidProject.FD_INTERMEDIATES;

/**
 * Created by wuzhong on 2017/3/16.
 *
 * @author wuzhong
 * @date 2017/03/16
 */
public class DependencyLocationManager {

    public static File getExploreDir(Project project, MavenCoordinates mavenCoordinates, File bundle, String type,
                                     String path) {

        Optional<FileCache> buildCache =
            AndroidGradleOptions.getBuildCache(project);
        File explodedDir;
        if (PrepareLibraryTask.shouldUseBuildCache(
            buildCache.isPresent(), mavenCoordinates) ) { //&& !"awb".equals(type)
            try {
                explodedDir = buildCache.get().getFileInCache(
                    PrepareLibraryTask.getBuildCacheInputs(bundle));

                return explodedDir;

            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        } else {
            Preconditions.checkState(
                !AndroidGradleOptions
                    .isImprovedDependencyResolutionEnabled(project),
                "Improved dependency resolution must be used with "
                    + "build cache.");

            return FileUtils.join(
                project.getBuildDir(),
                FD_INTERMEDIATES,
                "exploded-" + type,
                path);
        }

        //throw new GradleException("set explored dir exception");

    }

}
