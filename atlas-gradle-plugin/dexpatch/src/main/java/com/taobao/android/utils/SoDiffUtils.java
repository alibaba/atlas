package com.taobao.android.utils;

import java.io.File;

/**
 * SoDiffUtils
 *
 * @author zhayu.ll
 * @date 18/4/25
 */
public class SoDiffUtils {

    public static void diffSo(File workingDir, File baseSo, File newSo, File patchFile){
        File bsDiffFile = SystemUtils.getDiffFile();
        CommandUtils.exec(workingDir,bsDiffFile.getAbsolutePath()+ " "+baseSo.getAbsolutePath() + " "+newSo.getAbsolutePath()+" "+patchFile.getAbsolutePath());

    }

    public static void patchSo(File workingDir,File baseSo,File newSo,File patchFile){
        File bsPatchFile = SystemUtils.getPatchFile();
        CommandUtils.exec(workingDir,bsPatchFile.getAbsolutePath()+ " "+baseSo.getAbsolutePath() + " "+newSo.getAbsolutePath()+" "+patchFile.getAbsolutePath());

    }
}
