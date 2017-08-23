package com.taobao.android.builder.tasks.dexpatch;

import com.android.build.gradle.internal.api.AppVariantContext;
import com.android.build.gradle.internal.tasks.BaseTask;
import com.android.build.gradle.internal.variant.BaseVariantOutputData;
import com.android.builder.model.BuildType;
import com.taobao.android.builder.extension.TBuildType;
import com.taobao.android.builder.tasks.manager.MtlBaseTaskAction;
import org.gradle.api.tasks.TaskAction;
import com.taobao.android.builder.extension.DexConfig;
import java.io.File;
import java.io.IOException;

/**
 * @author lilong
 * @create 2017-05-03 下午1:02
 */

public class DexPatchDexTask extends BaseTask {

    private AppVariantContext variantContext;

    private TBuildType buildType;

    @TaskAction
    public void run(){
    DexBuilder dexBuilder = DexBuilder.getInstance();
    DexConfig dexConfig = buildType.getDexConfig();
    dexBuilder.init(dexConfig);
    DexPatchContext dexPatchContext = DexPatchContext.getInstance();
    for (DiffDependenciesTask.DiffResult diffResult: DexPatchContext.diffResults){
        if (dexPatchContext.getBundleArtifactObfJar(diffResult.artifactName,diffResult.bundleName,diffResult.isMaindex).exists()) {
            dexBuilder.setInput(dexPatchContext.getBundleArtifactObfJar(diffResult.artifactName, diffResult.bundleName, diffResult.isMaindex), diffResult.bundleName);
        }else {
            dexBuilder.setInput(dexPatchContext.getBundleArtifactJar(diffResult.artifactName, diffResult.bundleName, diffResult.isMaindex), diffResult.bundleName);
        }
    }
    try {
        dexBuilder.excute();
    } catch (IOException e) {
        e.printStackTrace();
    }

}



    public static class ConfigAction extends MtlBaseTaskAction <DexPatchDexTask>{
    private AppVariantContext variantContext;
    private TBuildType buildType;
        public ConfigAction(AppVariantContext variantContext, BaseVariantOutputData baseVariantOutputData) {
            super(variantContext, baseVariantOutputData);
            this.variantContext = variantContext;
             buildType =variantContext.getBuildType();
        }

        @Override
        public String getName() {
            return scope.getTaskName("DexPatchDex");
        }

        @Override
        public Class<DexPatchDexTask> getType() {
            return DexPatchDexTask.class;
        }

        @Override
        public void execute(DexPatchDexTask dexPatchDexTask) {
            dexPatchDexTask.setVariantName("DexPatchDex");
            dexPatchDexTask.variantContext = variantContext;
            dexPatchDexTask.buildType = buildType;

        }
    }
}
