package com.taobao.android.tools;

import com.google.common.collect.Lists;
import com.taobao.android.differ.dex.DexDiffer;
import com.taobao.android.differ.dex.PatchException;
import com.taobao.android.filter.DexDiffFilter;
import com.taobao.android.object.ClassDiffInfo;
import com.taobao.android.object.DexDiffInfo;
import com.taobao.android.object.DiffType;
import org.antlr.runtime.RecognitionException;
import org.apache.commons.io.FileUtils;
import org.jf.dexlib2.DexFileFactory;
import org.jf.dexlib2.Opcodes;
import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.iface.DexFile;
import org.jf.dexlib2.writer.io.FileDataStore;
import org.jf.dexlib2.writer.pool.BasePool;
import org.jf.dexlib2.writer.pool.DexPool;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;

/**
 * tpatch diff tool
 *
 /**
 * Created by lilong on 16/6/17.
 */

public abstract class PatchDexTool {

    private List<File> baseDexFiles;
    private List<File> newDexFiles;
    private int apiLevel;
    public DexDiffer dexDiffer;
    private DexDiffFilter dexDiffFilter;
    public Set<String>hotClassList;
    DexDiffInfo dexDiffInfo = null;
    private boolean mainBundle;
    private static final int MAX_COUNT = 64000;
    private Map<String, ClassDef>lastBundleClassMap = new HashMap<String, ClassDef>();

    public void setTPatch(boolean tpatch) {
        this.tpatch = tpatch;
    }

    private boolean tpatch;

    public boolean isNewPatch() {
        return newPatch;
    }

    public void setNewPatch(boolean newPatch) {
        this.newPatch = newPatch;
    }

    private boolean newPatch = true;

    public PatchDexTool(List<File> baseDexFiles, List<File> newDexFiles, int apiLevel, Map<String,ClassDef> map,boolean mainBundle) {
        this.baseDexFiles = baseDexFiles;
        this.newDexFiles = newDexFiles;
        this.apiLevel = apiLevel;
        this.mainBundle = mainBundle;
        assert (null != baseDexFiles && baseDexFiles.size() > 0);
        assert (null != newDexFiles && newDexFiles.size() > 0);
        this.dexDiffer = new DexDiffer(baseDexFiles, newDexFiles, apiLevel);
        if (map == null) {
            dexDiffer.setLastBundleClassMap(lastBundleClassMap);
        }else {
            dexDiffer.setLastBundleClassMap(map);
        }


    }

    public PatchDexTool(File baseDex, File newDex, int apiLevel,boolean mainBundle) {
        baseDexFiles = Lists.newArrayList();
        baseDexFiles.add(baseDex);
        newDexFiles = Lists.newArrayList();
        newDexFiles.add(newDex);
        this.mainBundle = mainBundle;
        this.dexDiffer = new DexDiffer(baseDex, newDex, apiLevel);
        dexDiffer.setLastBundleClassMap(lastBundleClassMap);

    }

    public void setDexDiffFilter(DexDiffFilter dexDiffFilter) {
        this.dexDiffFilter = dexDiffFilter;
        this.dexDiffer.setDexDiffFilter(dexDiffFilter);
    }

    /**
     * 生成淘宝的动态部署的patch的Dex文件
     *
     * @param outDexFile
     */


    public Set<ClassDef> createModifyClasses() throws IOException, PatchException {
        dexDiffer.setNewPatch(newPatch);
        dexDiffInfo = dexDiffer.doDiff();
        final Set<ClassDef> modifyClasses = new HashSet<ClassDef>();
        for (ClassDiffInfo classDiffInfo : dexDiffInfo.getClassDiffInfoMap().values()) {
            if (DiffType.MODIFY.equals(classDiffInfo.getType()) || DiffType.ADD.equals(classDiffInfo.getType()) || DiffType.OVERRIDE.equals(classDiffInfo.getType())) {
                modifyClasses.add(classDiffInfo.getClassDef());
            }
        }
        return modifyClasses;

    }

    public DexDiffInfo createPatchDex(File outDexFolder) throws IOException, RecognitionException, PatchException {
         Set<ClassDef>modifyClasses = createModifyClasses();
            if (modifyClasses.size() > 0) {
                writeDex(outDexFolder,modifyClasses);
                writePatchInfo(outDexFolder);

            }
        return dexDiffInfo;
    }

    public void writeDex(File outDexFolder,Set<ClassDef>classDefs) throws IOException {
        int i = 0 ;
        File outDexFile = getDexFile(outDexFolder,i);
        if (!outDexFile.getParentFile().exists()){
            outDexFile.getParentFile().mkdirs();
        }
        if (mainBundle){
            List<ClassDef> sortClassDefs = sort(classDefs);
            DexPool dexPool = new DexPool(Opcodes.getDefault());
            Iterator<ClassDef>iterator = sortClassDefs.iterator();
            while (iterator.hasNext()) {
                ClassDef classDef = iterator.next();
                    dexPool.internClass(classDef);
                    if (((BasePool)dexPool.methodSection).getItemCount() > MAX_COUNT||((BasePool)dexPool.fieldSection).getItemCount() > MAX_COUNT){
                        dexPool.writeTo(new FileDataStore(outDexFile));
                        outDexFile = getDexFile(outDexFolder,++i);
                        dexPool = new DexPool(Opcodes.getDefault());
                    }
            }
            dexPool.writeTo(new FileDataStore(outDexFile));


        }else {
            DexFileFactory.writeDexFile(outDexFile.getAbsolutePath(), new DexFile() {
                @Nonnull
                @Override
                public Set<? extends ClassDef> getClasses() {
                    return new AbstractSet<ClassDef>() {
                        @Nonnull
                        @Override
                        public Iterator<ClassDef> iterator() {
                            return classDefs.iterator();
                        }

                        @Override
                        public int size() {
                            return classDefs.size();
                        }
                    };
                }

                @Nonnull
                @Override
                public Opcodes getOpcodes() {
                    return Opcodes.getDefault();
                }
            });
        }

    }

    private void writePatchInfo(File outDexFolder) {
        File patchInfo = new File(outDexFolder,TPatchTool.DEX_PATCH_META);
        Collections.sort(dexDiffer.getDiffClasses());
        try {
            FileUtils.writeLines(patchInfo,dexDiffer.getDiffClasses());
        } catch (IOException e) {
            e.printStackTrace();
        }


    }

    private List<ClassDef> sort(Set<ClassDef> classDefs){
        List<ClassDef>lastDexClasses = new ArrayList<>();
        classDefs.forEach(classDef -> {
            if (classDef.getType().equals("Landroid/taobao/atlas/framework/FrameworkProperties;")||classDef.getType().equals("Landroid/taobao/atlas/bundleInfo/AtlasBundleInfoGenerator;")){
                lastDexClasses.add(classDef);
            }
        });
        classDefs.removeAll(lastDexClasses);

        classDefs.forEach(classDef -> lastDexClasses.add(0,classDef));

        return lastDexClasses;
    }

    private File getDexFile(File dexFolder,int i) {

        return new File(dexFolder,TPatchTool.CLASSES+(i==0?"":i)+TPatchTool.DEX_SUFFIX);
    }

    public void setPatchClassList(Set<String> hotClassList){
        this.hotClassList = hotClassList;
    }

    public abstract void setExculdeClasses(Set<String>classes);

    public abstract void setPatchClasses(Set<String>classes);



}
