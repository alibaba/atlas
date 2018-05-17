package com.taobao.android.tpatch.model;

import com.taobao.android.object.SoFileDef;
import com.taobao.android.tpatch.utils.MD5Util;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * PatchFile
 *
 * @author zhayu.ll
 * @date 18/5/10
 */
public class PatchFile {

    private File patchFile;

    private List<String>patchInfo = new ArrayList<>();

    public PatchFile(File patchFile) {
        assert patchFile!= null;
        this.patchFile = patchFile;

    }

    public void append(SoFileDef soFileDef){
        String newSoMd5 = null;
        try {
            newSoMd5 = MD5Util.getFileMD5String(soFileDef.newSoFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
        patchInfo.add(soFileDef.relativePath+":"+ newSoMd5);
    }

    public void close(){
        try {
            FileUtils.writeLines(patchFile,patchInfo);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
