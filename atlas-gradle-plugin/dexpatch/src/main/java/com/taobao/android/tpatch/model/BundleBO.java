package com.taobao.android.tpatch.model;

import java.io.File;

/**
 * @author lilong
 * @create 2017-03-27 下午1:23
 */

public class BundleBO {
    private String bundleName;

    public String getBundleName() {
        return bundleName;
    }

    public File getBundleFile() {
        return bundleFile;
    }

    public String getBundleVersion() {
        return bundleVersion;
    }

    private File bundleFile;
    private String bundleVersion;

    public BundleBO(String bundleName, File bundleFile, String bundleVersion) {
        this.bundleName = bundleName;
        this.bundleFile = bundleFile;
        this.bundleVersion = bundleVersion;
    }
}
