package com.taobao.android.tools;

import com.taobao.android.differ.dex.PatchException;
import com.taobao.android.object.DexDiffInfo;
import com.taobao.android.tpatch.utils.SmaliUtils;
import org.antlr.runtime.RecognitionException;
import org.jf.dexlib2.iface.ClassDef;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author lilong
 * @create 2017-11-07 下午2:42
 */

public class HotDexPatchDexTool extends DexPatchDexTool {

    private static final String HOT_DEXNAME = "hot-diff.dex";

    private Set<ClassDef>hotClassDefs;

    public HotDexPatchDexTool(List<File> baseDexFiles, List<File> newDexFiles, int apiLevel, Map<String, ClassDef> map, boolean mainBundle) {
        super(baseDexFiles, newDexFiles, apiLevel, map, mainBundle);
    }

    public HotDexPatchDexTool(File baseDex, File newDex, int apiLevel, boolean mainBundle) {
        super(baseDex, newDex, apiLevel, mainBundle);
    }

    @Override
    public Set<ClassDef> createModifyClasses() throws IOException, PatchException {
        dexDiffer.setTpatch(false);
        Set<ClassDef>mClasses =  super.createModifyClasses();
        if (hotClassList == null || hotClassList.size() == 0){
            return mClasses;
        }
        hotClassDefs = new HashSet<>();
        Iterator<ClassDef>iterator = mClasses.iterator();
        while (iterator.hasNext()) {
            ClassDef classDef = iterator.next();
            if (hotClassList.contains(classDef.getType()) || hotClassList.contains(SmaliUtils.getDalvikClassName(classDef.getType()))) {
                    hotClassDefs.add(classDef);
                    iterator.remove();
                }
        }
        return mClasses;

    }

    @Override
    public DexDiffInfo createPatchDex(File outDexFile) throws IOException, RecognitionException, PatchException {
        DexDiffInfo dexDiffInfo = super.createPatchDex(outDexFile);
        if (hotClassDefs!= null && hotClassDefs.size() > 0){
            File hotDexFile = new File(outDexFile.getParentFile(),HOT_DEXNAME);
            writeDex(hotDexFile,hotClassDefs);
        }
        return dexDiffInfo;

    }
}
