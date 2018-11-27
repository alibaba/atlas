package com.taobao.android.utils;

import com.taobao.android.dex.util.FileUtils;

import java.io.File;
import java.io.IOException;

/**
 * SoDiffUtils
 *
 * @author zhayu.ll
 * @date 18/4/25
 */
public class SoDiffUtils {

    public  static void diffSo(File workingDir, File baseSo, File newSo, File patchFile) throws IOException {
        File bsDiffFile = SystemUtils.getDiffFile();
        bsDiffFile.setExecutable(true);
        if (!patchFile.getParentFile().exists()){
            org.apache.commons.io.FileUtils.forceMkdir(patchFile.getParentFile());
        }
        CommandUtils.exec(workingDir,bsDiffFile.getAbsolutePath()+ " "+baseSo.getAbsolutePath() + " "+newSo.getAbsolutePath()+" "+patchFile.getAbsolutePath());
        if (!patchFile.exists()){
            throw new IOException("SO DIFF failed! "+ patchFile.getAbsolutePath() + " generate failed!");
        }

    }

    public  static void patchSo(File workingDir,File baseSo,File newSo,File patchFile) throws IOException {
        File bsPatchFile = SystemUtils.getPatchFile();
        bsPatchFile.setExecutable(true);
        if (!newSo.getParentFile().exists()){
            org.apache.commons.io.FileUtils.forceMkdir(newSo.getParentFile());
        }
        CommandUtils.exec(workingDir,bsPatchFile.getAbsolutePath()+ " "+baseSo.getAbsolutePath() + " "+newSo.getAbsolutePath()+" "+patchFile.getAbsolutePath());
        if (!newSo.exists()){
            throw new IOException("SO DIFF failed! " + newSo.getAbsolutePath() + "generate failed!");
        }

    }
}
