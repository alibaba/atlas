package com.taobao.android.tpatch.model;

import java.io.File;

/**
 * @author lilong
 * @create 2017-03-27 下午1:22
 */

public class ApkBO {


    public ApkBO(File apkFile, String versionName, String apkName) {
        this.apkFile = apkFile;
        this.versionName = versionName;
        this.apkName = apkName;
    }

    private File apkFile;

    public File getApkFile() {
        return apkFile;
    }

    public String getVersionName() {
        return versionName;
    }

    public String getApkName() {
        return apkName;
    }
    private String versionName;
    private String apkName;
}
