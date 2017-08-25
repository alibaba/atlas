package com.taobao.android.builder.tasks.dexpatch;

import com.android.build.gradle.internal.api.AppVariantContext;
import com.android.build.gradle.internal.tasks.BaseTask;
import com.android.build.gradle.internal.variant.BaseVariantOutputData;
import com.taobao.android.builder.extension.DexConfig;
import com.taobao.android.builder.extension.TBuildType;
import com.taobao.android.builder.tasks.manager.MtlBaseTaskAction;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;

/**
 * @author lilong
 * @create 2017-05-03 下午1:02
 */

public class DexPatchDexTask extends BaseTask {

    private TBuildType buildType;

    @TaskAction
    public void run(){
    DexConfig dexConfig = buildType.getDexConfig();
    DexPatchContext.dexBuilder.init(dexConfig);
    DexPatchContext dexPatchContext = DexPatchContext.getInstance();
    for (DiffDependenciesTask.DiffResult diffResult: DexPatchContext.diffResults){
        if (dexPatchContext.getBundleArtifactObfJar(diffResult.artifactName,diffResult.bundleName,diffResult.isMaindex).exists()) {
            DexPatchContext.dexBuilder.setInput(dexPatchContext.getBundleArtifactObfJar(diffResult.artifactName, diffResult.bundleName, diffResult.isMaindex), diffResult.bundleName);
        }else {
            DexPatchContext.dexBuilder.setInput(dexPatchContext.getBundleArtifactJar(diffResult.artifactName, diffResult.bundleName, diffResult.isMaindex), diffResult.bundleName);
        }
    }
    try {
        DexPatchContext.dexBuilder.excute();
    } catch (IOException e) {
        e.printStackTrace();
    }finally {
        DexPatchContext.dexBuilder.clear();
    }

}



    public static class ConfigAction extends MtlBaseTaskAction <DexPatchDexTask>{
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
            dexPatchDexTask.buildType = buildType;

        }
    }
}
