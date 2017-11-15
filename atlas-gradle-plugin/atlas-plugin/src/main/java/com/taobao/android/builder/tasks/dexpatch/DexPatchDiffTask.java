package com.taobao.android.builder.tasks.dexpatch;

import com.android.build.gradle.internal.api.AppVariantContext;
import com.android.build.gradle.internal.api.VariantContext;
import com.android.build.gradle.internal.tasks.BaseTask;
import com.android.build.gradle.internal.variant.BaseVariantOutputData;
import com.taobao.android.builder.tasks.manager.MtlBaseTaskAction;
import com.taobao.android.builder.tools.zip.BetterZip;
import com.taobao.android.builder.tools.zip.ZipUtils;
import com.taobao.android.differ.dex.PatchException;
import com.taobao.android.object.DexDiffInfo;
import com.taobao.android.task.ExecutorServicesHelper;
import com.taobao.android.tools.DexPatchDexTool;
import org.antlr.runtime.RecognitionException;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

/**
 * @author lilong
 * @create 2017-05-05 At 10:07
 */

public class DexPatchDiffTask extends BaseTask {
    private static final String MAIN_DEX = "com.taobao.maindex";

    private VariantContext variantContext;

    @TaskAction
    public void run() {

        ExecutorServicesHelper executorServicesHelper = new ExecutorServicesHelper();

        for (Map.Entry entry : DexPatchContext.dexBuilder.getOutputs().entrySet()) {
            executorServicesHelper.submitTask("dexDiff", new Callable<Boolean>() {
                @Override
                public Boolean call() throws Exception {
                    File newDexFile = null;

                    newDexFile = new File(DexPatchContext.getInstance().getDiffFolder(), "lib" + ((String) entry.getKey().toString().replace(".", "_") + "/classes.dex"));
                    if (!newDexFile.exists()) {
                        newDexFile = DexPatchContext.getInstance().getBundleDexFile((String) entry.getKey(), entry.getKey().equals(MAIN_DEX));
                    }

                    List<File> baseDexFile = getBaseDexFile((String) entry.getKey());

                    DexPatchDexTool tPatchDexTool = new DexPatchDexTool(baseDexFile, com.google.common.collect.Lists.newArrayList(newDexFile), 19, null, ((String) entry.getKey()).equals(MAIN_DEX));
                    try {
                        File outDex = new File(DexPatchContext.getInstance().getDiffFolder(), ((String) "lib" + ((String) entry.getKey().toString().replace(".", "_") + "/diff.dex")));
                        if (!outDex.getParentFile().exists()) {
                            outDex.getParentFile().mkdirs();
                        }
                        DexDiffInfo dexDiffInfo = tPatchDexTool.createPatchDex(outDex);
                        for (String className:dexDiffInfo.getClassDiffInfoMap().keySet()){
                            System.out.println("modify class:"+className);
                        }

                    } catch (IOException e) {
                        e.printStackTrace();
                    } catch (PatchException e) {
                        e.printStackTrace();
                    } catch (RecognitionException e) {
                        e.printStackTrace();
                    }
                    return true;
                }
            });

        }

        try {
            executorServicesHelper.waitTaskCompleted("dexDiff");
            executorServicesHelper.stop();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }


    }

    private List getBaseDexFile(String bundleName) throws IOException {
        List list = new ArrayList();
        File baseApk = new File(variantContext.apContext.getApExploredFolder(), "android.apk");
        BetterZip.unzipDirectory(baseApk, new File(variantContext.apContext.getApExploredFolder(), "unzip"));

        if (bundleName.equals(MAIN_DEX)) {
            File[] files = new File(variantContext.apContext.getApExploredFolder(), "unzip").listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    return name.endsWith(".dex");
                }
            });
            for (int i = 0; i < files.length; i++) {
                list.add(files[i]);
            }
        } else {
            String baseFilePath = null;
            if (variantContext.apContext.getBaseSo("lib" + bundleName.replace(".", "_") + ".so") == null) {
                baseFilePath = new File(variantContext.apContext.getApExploredFolder(), "remotebundles/" + "lib" + bundleName.replace(".", "_") + ".so").getAbsolutePath();
            } else {
                baseFilePath = variantContext.apContext.getBaseSo("lib" + bundleName.replace(".", "_") + ".so").getAbsolutePath();
            }
            if (new File(baseFilePath).exists()) {
                ZipUtils.extractZipFileToFolder(new File(baseFilePath), "classes.dex", new File(variantContext.apContext.getApExploredFolder(), bundleName));
                list.add(new File(variantContext.apContext.getApExploredFolder().getAbsolutePath() + File.separator + bundleName, "classes.dex"));
            }
        }
        return list;
    }


    public static class ConfigAction extends MtlBaseTaskAction<DexPatchDiffTask> {

        private VariantContext variantContext;

        public ConfigAction(AppVariantContext variantContext, BaseVariantOutputData baseVariantOutputData) {
            super(variantContext, baseVariantOutputData);
            this.variantContext = variantContext;
        }

        @Override
        public String getName() {
            return scope.getTaskName("DexPatchDiff");
        }

        @Override
        public Class<DexPatchDiffTask> getType() {
            return DexPatchDiffTask.class;
        }

        @Override
        public void execute(DexPatchDiffTask dexPatchDiffTask) {
            dexPatchDiffTask.setVariantName("DexPatchDiff");
            dexPatchDiffTask.variantContext = variantContext;

        }
    }
}
