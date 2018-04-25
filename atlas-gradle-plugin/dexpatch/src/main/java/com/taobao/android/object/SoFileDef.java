package com.taobao.android.object;

import java.io.File;

/**
 * SoFileDef
 *
 * @author zhayu.ll
 * @date 18/4/25
 */
public class SoFileDef {
    public File baseSoFile;
    public File newSoFile;
    public File patchFile;

    public SoFileDef(File baseSoFile, File newSoFile, File patchFile) {
        this.baseSoFile = baseSoFile;
        this.newSoFile = newSoFile;
        this.patchFile = patchFile;
    }
}
