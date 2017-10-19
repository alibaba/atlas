package com.taobao.android;

import com.google.common.collect.Lists;
import com.taobao.android.apatch.utils.TypeGenUtil;
import com.taobao.android.differ.dex.DexDiffer;
import com.taobao.android.differ.dex.PatchException;
import com.taobao.android.filter.DexDiffFilter;
import com.taobao.android.object.ClassDiffInfo;
import com.taobao.android.object.DexDiffInfo;
import com.taobao.android.object.DiffType;
import com.taobao.android.smali.AfBakSmali;
import com.taobao.android.smali.SmaliMod;
import org.antlr.runtime.RecognitionException;
import org.apache.commons.io.FileUtils;
import org.jf.baksmali.baksmaliOptions;
import org.jf.dexlib2.DexFileFactory;
import org.jf.dexlib2.dexbacked.DexBackedClassDef;
import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.iface.DexFile;
import org.jf.dexlib2.util.SyntheticAccessorResolver;
import org.jf.dexlib2.writer.builder.DexBuilder;
import org.jf.dexlib2.writer.io.FileDataStore;
import org.jf.util.ClassFileNameHandler;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * tpatch diff tool
 */
public class TPatchDexTool {

    private List<File> baseDexFiles;
    private List<File> newDexFiles;
    private File outDexFile;
    private int apiLevel;
    private DexDiffer dexDiffer;
    private DexDiffFilter dexDiffFilter;
    private boolean mainBundle;
    private Map<String, ClassDef>lastBundleClassMap = new HashMap<String, ClassDef>();
    private boolean removeDupStrings;

    public TPatchDexTool(List<File> baseDexFiles, List<File> newDexFiles, int apiLevel, Map<String,ClassDef> map,boolean mainBundle) {
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

    public TPatchDexTool(File baseDex, File newDex, int apiLevel,boolean mainBundle) {
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
    public DexDiffInfo createTPatchDex(File outDexFile) throws IOException, RecognitionException, PatchException {
        DexDiffInfo dexDiffInfo = null;
//        if (mainBundle){
            outDexFile.getParentFile().mkdirs();
             dexDiffInfo = dexDiffer.doDiff();

            // 将有变动的类写入到diff.dex
            final Set<ClassDef> modifyClasses = new HashSet<ClassDef>();

            for (ClassDiffInfo classDiffInfo : dexDiffInfo.getClassDiffInfoMap().values()) {
                if (DiffType.MODIFY.equals(classDiffInfo.getType()) || DiffType.ADD.equals(classDiffInfo.getType()) || DiffType.OVERRIDE.equals(classDiffInfo.getType())) {
                    modifyClasses.add(classDiffInfo.getClassDef());
                }
            }
            if (modifyClasses.size() > 0) {
                DexFileFactory.writeDexFile(outDexFile.getAbsolutePath(), new DexFile() {
                    @Nonnull
                    @Override
                    public Set<? extends ClassDef> getClasses() {
                        return new AbstractSet<ClassDef>() {
                            @Nonnull
                            @Override
                            public Iterator<ClassDef> iterator() {
                                return modifyClasses.iterator();
                            }

                            @Override
                            public int size() {
                                return modifyClasses.size();
                            }
                        };
                    }
                });
            }
//        }else {
//            dexDiffInfo = dexDiffer.doDiff();
////            DexPatchGenerator dexPatchGenerator = new DexPatchGenerator(baseDexFiles.get(0),removeDebugInfo(newDexFiles.get(0)));
////            dexPatchGenerator.executeAndSaveTo(outDexFile);
//        }
        return dexDiffInfo;

    }

}
