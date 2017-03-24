package com.taobao.android.differ.dex;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * @author lilong
 * @create 2017-03-22 下午7:39
 */

public class ApkDiff implements Serializable {
    private String baseApkVersion;

    public String getBaseApkVersion() {
        return baseApkVersion;
    }

    public void setBaseApkVersion(String baseApkVersion) {
        this.baseApkVersion = baseApkVersion;
    }

    public String getNewApkVersion() {
        return newApkVersion;
    }

    public void setNewApkVersion(String newApkVersion) {
        this.newApkVersion = newApkVersion;
    }

    public String getNewApkMd5() {
        return newApkMd5;
    }

    public void setNewApkMd5(String newApkMd5) {
        this.newApkMd5 = newApkMd5;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public List<BundleDiffResult> getBundleDiffResults() {
        return bundleDiffResults;
    }

    public void setBundleDiffResults(List<BundleDiffResult> bundleDiffResults) {
        this.bundleDiffResults = bundleDiffResults;
    }

    private String newApkVersion;
    private String newApkMd5;
    private String fileName;
    private List<BundleDiffResult>bundleDiffResults = new ArrayList<>();
}
