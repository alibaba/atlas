package com.taobao.android.object;

import com.taobao.android.tpatch.utils.MD5Util;

import java.io.File;
import java.io.IOException;

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
