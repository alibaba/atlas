package com.taobao.android.tools;

import com.taobao.android.differ.dex.PatchException;
import org.jf.dexlib2.iface.ClassDef;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author lilong
 * @create 2017-11-07 下午2:42
 */

public class DexPatchDexTool extends PatchDexTool {
    private Set<String>excludeClasses = new HashSet<>();

    public DexPatchDexTool(List<File> baseDexFiles, List<File> newDexFiles, int apiLevel, Map<String, ClassDef> map, boolean mainBundle) {
        super(baseDexFiles, newDexFiles, apiLevel, map, mainBundle);
    }

    public DexPatchDexTool(File baseDex, File newDex, int apiLevel, boolean mainBundle) {
        super(baseDex, newDex, apiLevel, mainBundle);
    }

    @Override
    public Set<ClassDef> createModifyClasses() throws IOException, PatchException {
        dexDiffer.setTpatch(false);
        dexDiffer.setExludeClasses(excludeClasses);
        return super.createModifyClasses();
    }

    @Override
    public void setExculdeClasses(Set<String> classes) {
        this.excludeClasses = classes;
    }
}
