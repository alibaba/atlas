package com.taobao.android.builder.tools;

import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.Status;
import com.android.build.gradle.internal.api.AppVariantContext;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.taobao.android.builder.AtlasBuildContext;
import com.taobao.android.builder.tasks.app.BuildAtlasEnvTask;

import java.io.File;
import java.util.Map;
import java.util.Set;

/**
 * TransformInputUtils
 *
 * @author zhayu.ll
 * @date 18/10/18
 */
public class TransformInputUtils {

    public static DirectoryInput makeDirectoryInput(File file, AppVariantContext variantContext) {
        return new DirectoryInput() {
            @Override
            public Map<File, Status> getChangedFiles() {
                return ImmutableMap.of(file, Status.CHANGED);
            }

            @Override
            public String getName() {
                return "folder";
            }

            @Override
            public File getFile() {
                return file;
            }

            @Override
            public Set<QualifiedContent.ContentType> getContentTypes() {
                return ImmutableSet.of(QualifiedContent.DefaultContentType.CLASSES);
            }

            @Override
            public Set<? super QualifiedContent.Scope> getScopes() {
                return ImmutableSet.of(Scope.EXTERNAL_LIBRARIES);
            }
        };

    }


    public static JarInput makeJarInput(File file,AppVariantContext variantContext) {
        BuildAtlasEnvTask.FileIdentity finalFileIdentity = AtlasBuildContext.atlasMainDexHelperMap.get(variantContext.getVariantName()).get(file);
        return new JarInput() {
            @Override
            public Status getStatus() {
                return Status.ADDED;
            }

            @Override
            public String getName() {

                return MD5Util.getFileMD5(file);
            }

            @Override
            public File getFile() {
                return file;
            }

            @Override
            public Set<QualifiedContent.ContentType> getContentTypes() {
                return ImmutableSet.of(QualifiedContent.DefaultContentType.CLASSES);
            }

            @Override
            public Set<? super QualifiedContent.Scope> getScopes() {
                if (finalFileIdentity == null){
                    return  ImmutableSet.of(Scope.EXTERNAL_LIBRARIES);
                }
                if (finalFileIdentity.subProject) {
                    return ImmutableSet.of(Scope.SUB_PROJECTS);
                } else {
                    return ImmutableSet.of(Scope.EXTERNAL_LIBRARIES);
                }
            }
        };
    }
}
