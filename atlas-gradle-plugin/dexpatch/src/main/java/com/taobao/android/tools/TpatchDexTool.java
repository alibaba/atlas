package com.taobao.android.tools;

import com.taobao.android.differ.dex.PatchException;
import org.antlr.runtime.RecognitionException;
import org.jf.dexlib2.iface.ClassDef;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author lilong
 * @create 2017-11-07 下午2:42
 */

public class TpatchDexTool extends PatchDexTool {

    public TpatchDexTool(List<File> baseDexFiles, List<File> newDexFiles, int apiLevel, Map<String, ClassDef> map, boolean mainBundle) {
        super(baseDexFiles, newDexFiles, apiLevel, map, mainBundle);
    }

    public TpatchDexTool(File baseDex, File newDex, int apiLevel, boolean mainBundle) {
        super(baseDex, newDex, apiLevel, mainBundle);
    }

    @Override
    public Set<ClassDef> createModifyClasses() throws IOException, PatchException {
            dexDiffer.setTpatch(true);
        return super.createModifyClasses();
    }

    @Override
    public void setExculdeClasses(Set<String> classes) {

    }

    @Override
    public void setPatchClasses(Set<String> classes) {

    }

    public static void main(String []args){
        TpatchDexTool tpatchDexTool = new TpatchDexTool(new File("/Users/lilong/Downloads/taobao-android-release/classes.dex"),new File("/Users/lilong/Downloads/taobao-android-release0/classes.dex"),21,false);
        File outDex = new File("/Users/lilong/Downloads/taobao-android-debug1/lib/armeabi/libcom_taobao_taolive/patch.dex");
        try {
            tpatchDexTool.createPatchDex(outDex);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (RecognitionException e) {
            e.printStackTrace();
        } catch (PatchException e) {
            e.printStackTrace();
        }
    }
}
