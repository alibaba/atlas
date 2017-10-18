package com.taobao.android.builder.tasks.dexpatch.builder;

import com.android.dx.command.dexer.Main;
import com.google.common.collect.Lists;
import com.taobao.android.builder.extension.DexConfig;
import com.taobao.android.builder.extension.TBuildType;
import com.taobao.android.builder.tasks.dexpatch.DexPatchContext;
import com.taobao.android.dex.Dex;
import com.taobao.android.dx.merge.CollisionPolicy;
import com.taobao.android.dx.merge.DexMerger;
import com.taobao.android.task.ExecutorServicesHelper;
import com.taobao.android.tpatch.utils.DexBuilderUtils;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

/**
 * @author lilong
 * @create 2017-08-24 11:49 a.m.
 */

public class DefaultDexBuilder implements DexBuilder{

    private static DexBuilder dexBuilder = null;

    private Map<String, List<File>> inputs = new HashMap<>();


    private Map<String, List<File>> outputs = new HashMap<>();

    private boolean useMyDex;

    private DefaultDexBuilder() {

    }

    public static DexBuilder getInstance() {
        if (dexBuilder == null) {
            synchronized (DexBuilder.class) {
                if (dexBuilder == null) {
                    dexBuilder = new DefaultDexBuilder();
                }
            }
        }
        return dexBuilder;
    }

    public void setInput(File file, String bundleName) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            List files = walkForDir(file);
            if (inputs.containsKey(bundleName)) {
                inputs.get(bundleName).addAll(files);
            } else {
                inputs.put(bundleName, files);
            }
        } else {
            if (inputs.containsKey(bundleName)) {
                inputs.get(bundleName).add(file);
            } else {
                inputs.put(bundleName, Lists.newArrayList(file));
            }
        }

    }

    public List<File> getOutput(String bundleName) {

        if (outputs.containsKey(bundleName)) {
            return outputs.get(bundleName);
        } else {
            return null;
        }
    }

    public Map<String, List<File>> getOutputs() {
        return outputs;
    }

    @Override
    public void obfDex(File mappingFile, File bundleListCfg, File baseApkFile, TBuildType buildType, boolean b) {

        return;
    }


    private List walkForDir(File file) {
        List<File> files = new ArrayList<>();
        files.addAll(FileUtils.listFiles(file, new String[]{"jar,class"}, true));
        return files;
    }

    public void excute() throws IOException {
        com.taobao.android.task.ExecutorServicesHelper executorServicesHelper = new ExecutorServicesHelper();
        for (Map.Entry entry : inputs.entrySet()) {
            executorServicesHelper.submitTask("buildDex", new Callable<Boolean>() {
                @Override
                public Boolean call() throws Exception {
                    List<File> files = (List<File>) entry.getValue();
                    String bundleName = (String) entry.getKey();
                    for (File file : files) {
                        if (!file.exists()) {
                            continue;
                        }
                        if (useMyDex) {
                            DexBuilderUtils.buildDexInJvm(file, new File(file.getParentFile(), "classes.dex"), Lists.newArrayList("--force-jumbo"));
                        } else {
                            Main.main(new String[]{"--force-jumbo", "--output=" + new File(file.getParentFile(), "classes.dex").getAbsolutePath(), file.getAbsolutePath()});
                        }
                        ArrayList arrayList = new ArrayList();
                        if (outputs.get(bundleName) != null) {
                            arrayList = (ArrayList) outputs.get(bundleName);
                        }
                        arrayList.add(new File(file.getParentFile(), "classes.dex"));
                        outputs.put(bundleName, arrayList);
                    }
                    DexMerger dexMerger = new DexMerger(toDex(outputs.get(bundleName)), CollisionPolicy.KEEP_FIRST);
                    Dex dex = dexMerger.merge();
                    File outFile = null;
                    outFile = DexPatchContext.getInstance().getBundleDexFile((String) entry.getKey(), entry.getKey().equals("com.taobao.maindex"));
                    dex.writeTo(outFile);
                    outputs.put(bundleName, Lists.newArrayList(outFile));
                    return true;
                }
            });
        }

        try {
            executorServicesHelper.waitTaskCompleted("buildDex");
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
        executorServicesHelper.stop();
    }


    private Dex[] toDex(List<File> files) throws IOException {
        Dex[] dexes = new Dex[files.size()];

        for (int i = 0; i < files.size(); i++) {
            dexes[i] = new Dex(files.get(i));
        }
        return dexes;
    }




    public void clear() {
        inputs.clear();
        outputs.clear();
    }

    public void init(DexConfig dexConfig) {
        if (dexConfig == null){
            return;
        }
        if (dexConfig.isUseMyDex()) {
            useMyDex = true;
        } else {
            useMyDex = false;
        }
    }
}
