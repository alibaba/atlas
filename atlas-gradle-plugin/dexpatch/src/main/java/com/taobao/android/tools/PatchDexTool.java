package com.taobao.android.tools;

import com.google.common.collect.Lists;
import com.taobao.android.differ.dex.DexDiffer;
import com.taobao.android.differ.dex.PatchException;
import com.taobao.android.filter.DexDiffFilter;
import com.taobao.android.object.ClassDiffInfo;
import com.taobao.android.object.DexDiffInfo;
import com.taobao.android.object.DiffType;
import org.antlr.runtime.RecognitionException;
import org.jf.dexlib2.DexFileFactory;
import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.iface.DexFile;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * tpatch diff tool
 */
public abstract class PatchDexTool {

    private List<File> baseDexFiles;
    private List<File> newDexFiles;
    private File outDexFile;
    private int apiLevel;
    public DexDiffer dexDiffer;
    private DexDiffFilter dexDiffFilter;
    public Set<String>hotClassList;
    DexDiffInfo dexDiffInfo = null;
    private boolean mainBundle;
    private Map<String, ClassDef>lastBundleClassMap = new HashMap<String, ClassDef>();
    private boolean removeDupStrings;

    public void setTPatch(boolean tpatch) {
        this.tpatch = tpatch;
    }

    private boolean tpatch;

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
            dexDiffInfo = dexDiffer.doDiff();
        final Set<ClassDef> modifyClasses = new HashSet<ClassDef>();
        for (ClassDiffInfo classDiffInfo : dexDiffInfo.getClassDiffInfoMap().values()) {
            if (DiffType.MODIFY.equals(classDiffInfo.getType()) || DiffType.ADD.equals(classDiffInfo.getType()) || DiffType.OVERRIDE.equals(classDiffInfo.getType())) {
                modifyClasses.add(classDiffInfo.getClassDef());
            }
        }
        return modifyClasses;

    }

    public DexDiffInfo createPatchDex(File outDexFile) throws IOException, RecognitionException, PatchException {
         Set<ClassDef>modifyClasses = createModifyClasses();
            if (modifyClasses.size() > 0) {
                writeDex(outDexFile,modifyClasses);
            }
        return dexDiffInfo;
    }

    public void writeDex(File outDexFile,Set<ClassDef>classDefs) throws IOException {

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
        });
    }

    public void setPatchClassList(Set<String> hotClassList){
        this.hotClassList = hotClassList;
    }

    public abstract void setExculdeClasses(Set<String>classes);

}
