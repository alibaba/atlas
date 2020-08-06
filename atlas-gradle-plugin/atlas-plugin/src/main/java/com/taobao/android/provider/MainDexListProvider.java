package com.taobao.android.provider;

import com.android.build.gradle.internal.api.AppVariantContext;
import com.android.build.gradle.internal.pipeline.TransformManagerDelegate;
import com.android.build.gradle.internal.pipeline.TransformTask;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.transforms.D8MainDexListTransform;
import com.taobao.android.builder.AtlasBuildContext;
import com.taobao.android.builder.tools.multidex.mutli.JarRefactor;

import org.apache.commons.io.FileUtils;
import org.gradle.api.internal.provider.DefaultProvider;
import org.gradle.api.provider.Provider;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * com.taobao.android.provider
 *
 * @author lilong
 * @time 11:52 AM
 * @date 2020/5/20
 */
public class MainDexListProvider {

    private static MainDexListProvider sMainDexListProvider = new MainDexListProvider();

    private File mainDexListFile;


    private MainDexListProvider() {

    }

    public static MainDexListProvider getInstance() {
        return sMainDexListProvider;
    }

    public File getMainDexList(AppVariantContext variantContext) {
        if (mainDexListFile != null){
            return mainDexListFile;
        }
        if (variantContext.getScope()
                .getVariantConfiguration()
                .getMinSdkVersion()
                .getFeatureLevel()
                > 20 || !variantContext.getVariantConfiguration().isMultiDexEnabled()) {
            return null;
        }

        if ((variantContext.getScope()
                .getVariantConfiguration()
                .getMinSdkVersionWithTargetDeviceApi()
                .getFeatureLevel()
                < 21 && variantContext.getScope().getVariantConfiguration().isMultiDexEnabled())) {

            List<TransformTask> transforms = TransformManagerDelegate.findTransformTaskByTransformType(
                    variantContext, D8MainDexListTransform.class);
            if (transforms != null && transforms.size() > 0) {
                TransformTask transformTask = transforms.get(0);
                if (variantContext.getAtlasExtension().isAppBundlesEnabled()) {
                    mainDexListFile =
                            variantContext.getScope()
                                    .getArtifacts()
                                    .appendArtifact(
                                            InternalArtifactType
                                                    .MAIN_DEX_LIST_FOR_BUNDLE,
                                            transformTask.getName(),
                                            "mainDexList.txt");
                } else {
                    mainDexListFile =
                            variantContext.getScope()
                            .getArtifacts()
                            .appendArtifact(
                                    InternalArtifactType.LEGACY_MULTIDEX_MAIN_DEX_LIST,
                                    transformTask.getName(),
                                    "mainDexList.txt");
                }
            }
        }

        if (variantContext.getScope().getInstantRunBuildContext().isInInstantRunMode()){
            mainDexListFile =
                    mainDexListProvider(variantContext).get();
        }
        return mainDexListFile;
    }



    private Provider<File> mainDexListProvider(AppVariantContext variantContext) {
        return new DefaultProvider<>(() -> {
            File finalMainDexListFile = new File(variantContext.getScope().getIntermediateDir(InternalArtifactType.LEGACY_MULTIDEX_MAIN_DEX_LIST), "mainDexList.txt");
            return finalMainDexListFile;
        });

    }

    public void  generateMainDexList(AppVariantContext variantContext){
        if (mainDexListFile == null){
            getMainDexList(variantContext);
        }
        if (!mainDexListFile.getParentFile().exists()) {
            mainDexListFile.getParentFile().mkdirs();
        }
        List<File>files = new ArrayList<>();
        files.addAll(AtlasBuildContext.atlasMainDexHelperMap.get(variantContext.getVariantName()).getAllMainDexJars());
        files.addAll(AtlasBuildContext.atlasMainDexHelperMap.get(variantContext.getVariantName()).getInputDirs());
        try {
        new JarRefactor(variantContext, variantContext.getBuildType().getMultiDexConfig()).repackageJarList(files, mainDexListFile, variantContext.getVariantConfiguration().getBuildType().isMinifyEnabled());
            FileUtils.copyFileToDirectory(mainDexListFile, variantContext.getScope().getGlobalScope().getOutputsDir());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
