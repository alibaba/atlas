package com.taobao.android.builder.tasks.dexpatch;

import com.android.build.gradle.internal.api.AppVariantContext;
import com.android.build.gradle.internal.api.AwbTransform;
import com.android.build.gradle.internal.api.VariantContext;
import com.android.build.gradle.internal.tasks.BaseTask;
import com.android.build.gradle.internal.variant.BaseVariantOutputData;
import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.JavaLibrary;
import com.taobao.android.builder.AtlasBuildContext;
import com.taobao.android.builder.tasks.manager.MtlBaseTaskAction;
import org.apache.commons.io.FileUtils;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * @author lilong
 * @create 2017-08-23 On the afternoon of 4:03
 */

public class PrepareBaseFileTask extends BaseTask {

    private AppVariantContext variantContext;
    private BaseVariantOutputData baseVariantOutputData;


    @TaskAction
    public void run() throws Exception {

        List<AndroidLibrary> androidLibraries = AtlasBuildContext.androidDependencyTrees.get(variantContext.getVariantData().getName()).getAllAndroidLibrarys();
        List<JavaLibrary>javaLibraries = AtlasBuildContext.androidDependencyTrees.get(variantContext.getVariantData().getName()).getMainBundle().getJavaLibraries();
        if (androidLibraries.size() > 0) {
            for (AndroidLibrary androidLibrary : androidLibraries) {
                File jarFile = null;

                jarFile = getJarFile(androidLibrary);

                if (DexPatchContext.modifyArtifact.containsKey(androidLibrary.getResolvedCoordinates().getArtifactId())) {
                    FileUtils.copyFileToDirectory(jarFile, DexPatchContext.getInstance().getBundleArtifactFolder(DexPatchContext.modifyArtifact.get(androidLibrary.getResolvedCoordinates().getArtifactId()).bundleName, androidLibrary.getName().split(":")[1], DexPatchContext.modifyArtifact.get(androidLibrary.getResolvedCoordinates().getArtifactId()).isMaindex));
                }
            }
        }

            for (JavaLibrary javaLibrary : javaLibraries) {

                File jarFile = null;
                try {
                    jarFile = getJarFile(javaLibrary);
                    if (!jarFile.exists()) {
                        System.err.println("jarFile not find:" + jarFile.getAbsolutePath());
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                if (DexPatchContext.modifyArtifact.containsKey(javaLibrary.getResolvedCoordinates().getArtifactId())) {
                    FileUtils.copyFileToDirectory(jarFile, DexPatchContext.getInstance().getBundleArtifactFolder(DexPatchContext.modifyArtifact.get(javaLibrary.getResolvedCoordinates().getArtifactId()).bundleName, javaLibrary.getName().split(":")[1], DexPatchContext.modifyArtifact.get(javaLibrary.getResolvedCoordinates().getArtifactId()).isMaindex));
                }

            }

            if (null != variantContext.getAppVariantOutputContext(baseVariantOutputData).getAwbTransformMap()) {
                for (AwbTransform awbTransform : variantContext.getAppVariantOutputContext(baseVariantOutputData).getAwbTransformMap().values()) {
                    for (AndroidLibrary androidLibrary : awbTransform.getAwbBundle().getAndroidLibraries()) {
                        File jarFile = androidLibrary.getJarFile();

                        if (DexPatchContext.modifyArtifact.containsKey(androidLibrary.getResolvedCoordinates().getArtifactId())) {
                            FileUtils.copyFileToDirectory(jarFile, DexPatchContext.getInstance().getBundleArtifactFolder(DexPatchContext.modifyArtifact.get(androidLibrary.getResolvedCoordinates().getArtifactId()).bundleName, androidLibrary.getResolvedCoordinates().getArtifactId(), DexPatchContext.modifyArtifact.get(androidLibrary.getResolvedCoordinates().getArtifactId()).isMaindex));
                        }
                    }

                    for (JavaLibrary androidLibrary : awbTransform.getAwbBundle().getJavaLibraries()) {
                        File jarFile = androidLibrary.getJarFile();

                        if (DexPatchContext.modifyArtifact.containsKey(androidLibrary.getResolvedCoordinates().getArtifactId())) {

                            FileUtils.copyFileToDirectory(jarFile, DexPatchContext.getInstance().getBundleArtifactFolder(DexPatchContext.modifyArtifact.get(androidLibrary.getResolvedCoordinates().getArtifactId()).bundleName, androidLibrary.getResolvedCoordinates().getArtifactId(), DexPatchContext.modifyArtifact.get(androidLibrary.getResolvedCoordinates().getArtifactId()).isMaindex));
                        }


                    }
                }
            }
        }


            public static class ConfigAction extends MtlBaseTaskAction<PrepareBaseFileTask> {

                private VariantContext variantContext;
                private BaseVariantOutputData variantOutputData;

                public ConfigAction(AppVariantContext variantContext, BaseVariantOutputData baseVariantOutputData) {
                    super(variantContext, baseVariantOutputData);
                    this.variantContext = variantContext;
                    this.variantOutputData = baseVariantOutputData;
                }

                @Override
                public String getName() {
                    return scope.getTaskName("DexPatchPrepareJar");
                }

                @Override
                public Class<PrepareBaseFileTask> getType() {
                    return PrepareBaseFileTask.class;
                }

                @Override
                public void execute(PrepareBaseFileTask prepareBaseFileTask) {

                    prepareBaseFileTask.variantContext = (AppVariantContext) variantContext;
                    prepareBaseFileTask.baseVariantOutputData = variantOutputData;
                    prepareBaseFileTask.setVariantName("DexPatchPrepareJar");

                }
            }


            protected File getJarFile(JavaLibrary androidLibrary) throws IOException {
                File newJar = null;
                if (androidLibrary.getResolvedCoordinates().getPackaging().equals("apklib") || androidLibrary.getResolvedCoordinates().getPackaging().equals("apk") || androidLibrary.getResolvedCoordinates().getPackaging().equals("solib")) {
                    return null;
                }
                newJar = androidLibrary.getJarFile();

                return newJar;
            }

            protected File getJarFile (AndroidLibrary androidLibrary) throws IOException {
                File newJar = null;
                if (androidLibrary.getResolvedCoordinates().getPackaging().equals("apklib") || androidLibrary.getResolvedCoordinates().getPackaging().equals("apk") || androidLibrary.getResolvedCoordinates().getPackaging().equals("solib")) {
                    return null;
                }
                newJar = androidLibrary.getJarFile();

                return newJar;
            }


}
