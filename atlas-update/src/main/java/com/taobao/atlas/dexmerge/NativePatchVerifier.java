package com.taobao.atlas.dexmerge;

import android.os.Build;
import android.taobao.atlas.util.FileUtils;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * NativePatchVerifier
 *
 * @author zhayu.ll
 * @date 18/5/10
 */
public class NativePatchVerifier implements PatchVerifier {

    private List<PatchInfo> patchInfos = new ArrayList<>();
    static String extractTag = "lib/armeabi/";

    static {

        if (Build.CPU_ABI.contains("x86")) {
                extractTag = "lib/x86/";
        }
    }

    public NativePatchVerifier(File patchInfo) throws IOException {
        this(new FileInputStream(patchInfo));
    }

    public NativePatchVerifier(InputStream patchInfo) throws IOException {
        read(patchInfo);
    }

    @Override
    public boolean verify(File mergeFile) {
        for (PatchInfo patchInfo:patchInfos){
            if (patchInfo.soAbiName.equals(extractTag.concat(mergeFile.getName()))){
                return patchInfo.md5.equals(FileUtils.getMd5ByFile(mergeFile));
            }
        }
        return true;
    }


    public void read(InputStream inputStream) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.contains(":")) {
                String[] s = line.split(":");
                if (s.length != 3) {
                    throw new IOException("SO-PATCH-INF generate error!");
                }
                PatchInfo patchInfo = new PatchInfo();
                patchInfo.bundleName = s[0];
                patchInfo.soAbiName = s[1];
                patchInfo.md5 = s[2];
                patchInfos.add(patchInfo);
            }
        }

    }
}
