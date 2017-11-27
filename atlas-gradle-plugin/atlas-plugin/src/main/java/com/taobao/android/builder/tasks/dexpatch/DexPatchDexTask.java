package com.taobao.android.builder.tasks.dexpatch;

import com.android.build.gradle.internal.api.AppVariantContext;
import com.android.build.gradle.internal.tasks.BaseTask;
import com.android.build.gradle.internal.variant.BaseVariantOutputData;
import com.taobao.android.builder.extension.DexConfig;
import com.taobao.android.builder.extension.TBuildType;
import com.taobao.android.builder.tasks.manager.MtlBaseTaskAction;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;

/**
 * @author lilong
 * @create 2017-05-03 On the afternoon of 1:02
 */

public class DexPatchDexTask extends BaseTask {

    private TBuildType buildType;

    private AppVariantContext variantContext;

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
//        DexPatchContext.dexBuilder.clear();
    }

        File mappingFile = null;
        File baseApkFile = null;
        if (variantContext.apContext.getApExploredFolder().exists()){
            if (new File(variantContext.apContext.getApExploredFolder(),"mapping.data").exists()){
                mappingFile =new File(variantContext.apContext.getApExploredFolder(),"mapping.data");
            }else if (new File(variantContext.apContext.getApExploredFolder(),"full-mapping.txt").exists()){
                mappingFile = new File(variantContext.apContext.getApExploredFolder(),"mapping.txt");
            }
            baseApkFile = new File(variantContext.apContext.getApExploredFolder(),"android.apk");
        }

        if (mappingFile!= null) {
            try {
                DexPatchContext.dexBuilder.obfDex(mappingFile, variantContext.bundleListCfg, baseApkFile,buildType,false);
            }catch (Exception e) {
                e.printStackTrace();
            }
        }

    }



    public static class ConfigAction extends MtlBaseTaskAction <DexPatchDexTask>{
    private TBuildType buildType;
    private AppVariantContext variantContext;
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
            dexPatchDexTask.variantContext= this.variantContext;

        }
    }
}
