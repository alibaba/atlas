package com.taobao.android.builder.tasks.dexpatch;

import com.android.builder.model.BuildType;
import com.android.dx.command.dexer.Main;
import com.google.common.collect.Lists;
import com.taobao.android.DexObfuscatedTool;
import com.taobao.android.builder.tools.zip.ZipUtils;
import com.taobao.android.dex.Dex;
import com.taobao.android.dx.merge.CollisionPolicy;
import com.taobao.android.dx.merge.DexMerger;
import com.taobao.android.task.ExecutorServicesHelper;
import com.taobao.android.tpatch.utils.DexBuilderUtils;
import org.apache.commons.io.FileUtils;
import org.gradle.api.logging.Logging;

import java.io.*;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import com.taobao.android.builder.extension.DexConfig;

/**
 * @author lilong
 * @create 2017-05-04 上午11:34
 */

public class DexBuilder {

    private static DexBuilder dexBuilder = null;

    private Map<String, List<File>> inputs = new HashMap<>();


    private Map<String, List<File>> outputs = new HashMap<>();

    private boolean useMyDex;

    private DexBuilder() {

    }

    public static DexBuilder getInstance() {
        if (dexBuilder == null) {
            synchronized (DexBuilder.class) {
                if (dexBuilder == null) {
                    dexBuilder = new DexBuilder();
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
//                            clearDir(DexPatchContext.getInstance().getBundleDexFile((String) entry.getKey(), entry.getKey().equals("com.taobao.maindex")).getParentFile());

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

    public void obfDex(File mappingFile) throws Exception {
        doProguard(mappingFile);
    }

    private File makeToPatch(boolean base) throws Exception {
        File patchFile = null;
        if (base) {
            patchFile = DexPatchContext.getInstance().getPatchFile("1.0.0");
        } else {
            patchFile = DexPatchContext.getInstance().getPatchFile("2.0.0");
        }
        if (outputs.size() < 1) {
            return null;
        } else {
            Iterator iterator = outputs.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry entry = (Map.Entry) iterator.next();
                List<File> files = (List<File>) entry.getValue();
                clearDir(files.get(0).getParentFile());
                File bundleSo = new File(files.get(0).getParentFile().getParentFile(), "lib" + entry.getKey().toString().replace(".", "_") + ".so");
                addBundleFileAndDirectoryToZip(bundleSo, files.get(0).getParentFile(), (String) entry.getKey());
                addBundleFileAndDirectoryToZip(patchFile, bundleSo, (String) entry.getKey());
            }
        }
        return patchFile;

    }

    private void clearDir(File parentFile) {
        for (File file : parentFile.listFiles()) {
            if (file.isDirectory()) {
                FileUtils.deleteQuietly(file);
            }
        }
    }

    private void doProguard(File mappingFile) throws IOException {
        DexObfuscatedTool dexObfuscatedTool = new DexObfuscatedTool(mappingFile);
        if (outputs.size() < 1) {
            return;
        } else {
            Iterator iterator = outputs.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry entry = (Map.Entry) iterator.next();
                List<File> files = (List<File>) entry.getValue();
                dexObfuscatedTool.obfuscateDex(DexPatchContext.getInstance().getBundleDexFile((String) entry.getKey(), entry.getKey().equals("com.taobao.maindex")), DexPatchContext.getInstance().getBundleObfDex((String) entry.getKey(), entry.getKey().equals("com.taobao.maindex")));
                entry.setValue(Lists.newArrayList(DexPatchContext.getInstance().getBundleObfDex((String) entry.getKey(), entry.getKey().equals("com.taobao.maindex"))));
            }
        }
    }


    public static void addBundleFileAndDirectoryToZip(File output, File srcDir, String bundleName) throws Exception {
        ZipInputStream zin = null;
        File outputTemp = new File(output.getParentFile(), output.getName() + ".temp");
        if (output.isDirectory()) {
            throw new IOException("This is a directory!");
        } else {
            if (!output.getParentFile().exists()) {
                output.getParentFile().mkdirs();
            }

            if (output.exists()) {
                zin = new ZipInputStream(new FileInputStream(output));
            }

            List fileList = getSubFiles(srcDir, bundleName);
            ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(outputTemp));
            ZipEntry ze = null;
            byte[] buf = new byte[1024];
            boolean readLen = false;

            for (int i = 0; i < fileList.size(); ++i) {
                File f = (File) fileList.get(i);
                ze = new ZipEntry(getAbsFileName(srcDir.getPath(), f));
                ze.setSize(f.length());
                ze.setTime(f.lastModified());
                zos.putNextEntry(ze);
                BufferedInputStream is = new BufferedInputStream(new FileInputStream(f));

                int var10;
                while ((var10 = is.read(buf, 0, 1024)) != -1) {
                    zos.write(buf, 0, var10);
                }

                is.close();
            }
            if (zin != null) {
                ZipEntry zipEntry = zin.getNextEntry();
                do {
                    zos.putNextEntry(zipEntry);
                    int len1;
                    while ((len1 = zin.read(buf)) > 0) {
                        zos.write(buf, 0, len1);
                    }
                    zipEntry = zin.getNextEntry();

                } while (zipEntry != null);
                zin.close();
            }


            zos.close();
            if (output.exists()) {
                FileUtils.deleteQuietly(output);
            }
            FileUtils.moveFile(outputTemp, output);
        }
    }

    private static String getAbsFileName(String baseDir, File realFileName) {
        return realFileName.getName();
    }

    private static List getSubFiles(File baseDir, String bundleName) {
        ArrayList ret = new ArrayList();
        if (!baseDir.isDirectory() && baseDir.isFile()) {
            ret.add(baseDir);
            return ret;
        }
        File[] tmp = baseDir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith(".so") || name.equals(bundleName) || name.equals("classes.dex");
            }
        });

        for (int i = 0; i < tmp.length; ++i) {
            if (tmp[i].isFile()) {
                ret.add(tmp[i]);
            }

            if (tmp[i].isDirectory()) {
                ret.addAll(getSubFiles(tmp[i], bundleName));
            }
        }

        return ret;
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
