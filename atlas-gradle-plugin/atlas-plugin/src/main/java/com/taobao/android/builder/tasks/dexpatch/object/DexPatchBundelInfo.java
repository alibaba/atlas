package com.taobao.android.builder.tasks.dexpatch.object;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * @author lilong
 * @create 2017-05-09 On the afternoon of 1:17
 */

public class DexPatchBundelInfo implements Serializable {

    public String getPkgName() {
        return pkgName;
    }

    public void setPkgName(String pkgName) {
        this.pkgName = pkgName;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public List<String> getDependency() {
        return dependency;
    }

    public void setDependency(List<String> dependency) {
        this.dependency = dependency;
    }

    private String pkgName;
    private String version;

    public boolean isMainBundle() {
        return mainBundle;
    }

    public void setMainBundle(boolean mainBundle) {
        this.mainBundle = mainBundle;
    }

    private boolean mainBundle;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getApplicationName() {
        return applicationName;
    }

    private String name;
    private String applicationName;

    public String getUnitTag() {
        return unitTag;
    }

    public void setUnitTag(String unitTag) {
        this.unitTag = unitTag;
    }

    public String getSrcUnitTag() {
        return srcUnitTag;
    }

    public void setSrcUnitTag(String srcUnitTag) {
        this.srcUnitTag = srcUnitTag;
    }

    private String unitTag;
    private String srcUnitTag;

    public String getSrcVersion() {
        return srcVersion;
    }

    public void setSrcVersion(String srcVersion) {
        this.srcVersion = srcVersion;
    }

    private String srcVersion;
    private List<String> dependency = new ArrayList<>();

    public void setApplicationName(String applicationName) {
        this.applicationName = applicationName;
    }
}
