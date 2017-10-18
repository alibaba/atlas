package com.taobao.android.builder.tasks.dexpatch;

import com.alibaba.fastjson.JSON;
import com.android.build.gradle.internal.api.AppVariantContext;
import com.android.build.gradle.internal.api.AppVariantOutputContext;
import com.android.build.gradle.internal.tasks.BaseTask;
import com.android.build.gradle.internal.variant.BaseVariantOutputData;
import com.taobao.android.builder.tasks.manager.MtlBaseTaskAction;
import com.taobao.android.differ.dex.PatchException;
import com.taobao.android.object.ArtifactBundleInfo;
import com.taobao.android.object.BuildPatchInfos;
import com.taobao.android.object.PatchBundleInfo;
import com.taobao.android.object.PatchInfo;
import com.taobao.android.tpatch.utils.PathUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.gradle.api.tasks.TaskAction;

import java.io.*;
import java.sql.Date;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;

/**
 * @author lilong
 * @create 2017-05-03 On the afternoon of 1:03
 */

public class DexPatchPackageTask extends BaseTask {

    private BaseVariantOutputData variantOutputData;
    private AppVariantOutputContext appVariantOutputContext;
    private static final String MAIN_DEX = "libcom_taobao_maindex";
    private static final String MAIN_DEX_SO = "libcom_taobao_maindex.so";
    private static final String MAIN_DEX_NAME = "com.taobao.maindex";

    @TaskAction
    public void run() throws IOException {

        File outPutPatch = new File(variantOutputData.getScope().getGlobalScope().getOutputsDir(), DexPatchContext.getInstance().getBaseVersion() + "@" + DexPatchContext.getInstance().getBaseVersion() + ".tpatch");
        File outPutJson = new File(variantOutputData.getScope().getGlobalScope().getOutputsDir(), "patchs.json");
        Collection<File> files = FileUtils.listFiles(variantOutputData.getScope().getGlobalScope().getOutputsDir(), new String[]{"dex"}, true);
        for (File file : files) {
            if (file.getName().equals("diff.dex")) {
                File outDex = new File(file.getParentFile(), "classes.dex");
                file.renameTo(outDex);
            }
        }

        FileUtils.deleteDirectory(DexPatchContext.getInstance().getBundleDiffFolder(true));
        FileUtils.deleteDirectory(DexPatchContext.getInstance().getBundleDiffFolder(false));

        try {
            if (new File(DexPatchContext.getInstance().getDiffFolder(), MAIN_DEX).exists()) {
                File mainDex = new File(DexPatchContext.getInstance().getDiffFolder(), MAIN_DEX_SO);
                zipBunldeSo(new File(DexPatchContext.getInstance().getDiffFolder(), MAIN_DEX), mainDex);
                FileUtils.deleteDirectory(new File(DexPatchContext.getInstance().getDiffFolder(), MAIN_DEX));
            }
            zipBunldeSo(DexPatchContext.getInstance().getDiffFolder(), outPutPatch);
        } catch (Exception e) {
            e.printStackTrace();
        }

        BuildPatchInfos buildPatchInfos = new BuildPatchInfos();
        List<PatchInfo> patchInfos = new ArrayList<>();
        PatchInfo patchInfo = new PatchInfo();
        List<PatchBundleInfo> list = new ArrayList<>();
        boolean add = false;
        for (ArtifactBundleInfo artifactBundleInfo : appVariantOutputContext.artifactBundleInfos) {
            for (String key : DexPatchContext.dexBuilder.getOutputs().keySet()) {
                if (artifactBundleInfo.getPkgName().equals(key)) {
                    PatchBundleInfo dexPatchBundelInfo = new PatchBundleInfo();
                    dexPatchBundelInfo.setPkgName(artifactBundleInfo.getPkgName());
                    dexPatchBundelInfo.setName(artifactBundleInfo.getPkgName());
                    dexPatchBundelInfo.setVersion(artifactBundleInfo.getVersion());
                    dexPatchBundelInfo.setUnitTag(artifactBundleInfo.getUnitTag());
                    dexPatchBundelInfo.setSrcUnitTag(artifactBundleInfo.getSrcUnitTag());
                    dexPatchBundelInfo.setDependency(artifactBundleInfo.getDependency());
                    dexPatchBundelInfo.setApplicationName(artifactBundleInfo.getApplicationName());
                    dexPatchBundelInfo.setMainBundle(artifactBundleInfo.getMainBundle());
                    list.add(dexPatchBundelInfo);
                    break;
                }
                if (key.equals(MAIN_DEX_NAME) && !add) {
                    add = true;
                    PatchBundleInfo dexPatchBundelInfo = new PatchBundleInfo();
                    dexPatchBundelInfo.setName(MAIN_DEX_NAME);
                    dexPatchBundelInfo.setMainBundle(true);
                    dexPatchBundelInfo.setPkgName(MAIN_DEX_NAME);
                    dexPatchBundelInfo.setUnitTag(DexPatchContext.mainMd5);
                    dexPatchBundelInfo.setSrcUnitTag(DexPatchContext.srcMainMd5);
                    dexPatchBundelInfo.setVersion(DexPatchContext.getInstance().getBaseVersion());
                    list.add(dexPatchBundelInfo);
                }
            }

        }
        patchInfo.setBundles(list);
        patchInfo.setFileName(outPutPatch.getName());
        patchInfo.setPatchVersion(DexPatchContext.getInstance().getBaseVersion());
        patchInfo.setTargetVersion(DexPatchContext.getInstance().getBaseVersion());
        patchInfos.add(patchInfo);
        buildPatchInfos.setBaseVersion(DexPatchContext.getInstance().getBaseVersion());
        buildPatchInfos.setPatches(patchInfos);
        buildPatchInfos.setDiffBundleDex(true);


        try {
            FileUtils.writeStringToFile(outPutJson, JSON.toJSONString(buildPatchInfos));
        } catch (IOException e) {
            e.printStackTrace();
        }

    }


    public static class ConfigAction extends MtlBaseTaskAction<DexPatchPackageTask> {

        private AppVariantContext appVariantContext;
        private BaseVariantOutputData baseVariantOutputData;

        public ConfigAction(AppVariantContext variantContext, BaseVariantOutputData baseVariantOutputData) {
            super(variantContext, baseVariantOutputData);
            this.appVariantContext = variantContext;
            this.baseVariantOutputData = baseVariantOutputData;
        }

        @Override
        public String getName() {
            return scope.getTaskName("DexPatchPackage");
        }

        @Override
        public Class<DexPatchPackageTask> getType() {
            return DexPatchPackageTask.class;
        }

        @Override
        public void execute(DexPatchPackageTask dexPatchPackageTask) {
            dexPatchPackageTask.setVariantName("DexPatchPackage");
            dexPatchPackageTask.variantOutputData = baseVariantOutputData;
            dexPatchPackageTask.appVariantOutputContext = this.getAppVariantOutputContext();


        }
    }

    private void zipBunldeSo(File bundleFolder, File soOutputFile) throws PatchException {
        try {
            Manifest e = this.createManifest();
            FileOutputStream fileOutputStream = new FileOutputStream(soOutputFile);
            JarOutputStream jos = new JarOutputStream(new BufferedOutputStream(fileOutputStream), e);
            File[] files = bundleFolder.listFiles();
            File[] var7 = files;
            int var8 = files.length;

            for (int var9 = 0; var9 < var8; ++var9) {
                File file = var7[var9];
                if (file.isDirectory()) {
                    this.addDirectory(jos, file, file.getName());
                } else {
                    this.addFile(jos, file);
                }
            }

            IOUtils.closeQuietly(jos);
            if (null != fileOutputStream) {
                IOUtils.closeQuietly(fileOutputStream);
            }

        } catch (IOException var11) {
            throw new PatchException(var11.getMessage(), var11);
        }
    }

    private Manifest createManifest() {
        Manifest manifest = new Manifest();
        Attributes main = manifest.getMainAttributes();
        main.putValue("Manifest-Version", "1.0");
        main.putValue("Created-By", "1.0 (DexPatch)");
        main.putValue("Created-Time", (new Date(System.currentTimeMillis())).toGMTString());
        return manifest;
    }

    private void addFile(JarOutputStream jos, File file) throws PatchException {
        byte[] buf = new byte[8064];
        String path = file.getName();
        FileInputStream in = null;

        try {
            in = new FileInputStream(file);
            ZipEntry e = new ZipEntry(path);
            jos.putNextEntry(e);

            int len;
            while ((len = in.read(buf)) > 0) {
                jos.write(buf, 0, len);
            }

            jos.closeEntry();
            in.close();
        } catch (IOException var8) {
            throw new PatchException(var8.getMessage(), var8);
        }
    }

    protected void addDirectory(JarOutputStream jos, File directory, String prefix) throws PatchException {
        if (directory != null && directory.exists()) {
            Collection files = FileUtils.listFiles(directory, TrueFileFilter.INSTANCE, TrueFileFilter.INSTANCE);
            byte[] buf = new byte[8064];
            Iterator var6 = files.iterator();

            while (true) {
                File file;
                do {
                    if (!var6.hasNext()) {
                        return;
                    }

                    file = (File) var6.next();
                } while (file.isDirectory());

                String path = prefix + File.separator + PathUtils.toRelative(directory, file.getAbsolutePath());
                FileInputStream in = null;

                try {
                    in = new FileInputStream(file);
                    ZipEntry e = new ZipEntry(path);
                    jos.putNextEntry(e);

                    int len;
                    while ((len = in.read(buf)) > 0) {
                        jos.write(buf, 0, len);
                    }

                    jos.closeEntry();
                    in.close();
                } catch (IOException var12) {
                    throw new PatchException(var12.getMessage(), var12);
                }
            }
        }
    }

}
