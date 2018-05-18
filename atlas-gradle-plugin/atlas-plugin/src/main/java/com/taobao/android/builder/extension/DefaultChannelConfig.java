package com.taobao.android.builder.extension;

import java.io.File;
import java.util.Map;

/**
 * Created by jason on 18/3/20.
 */

public class DefaultChannelConfig {

    private String name;

    private boolean enabled = false;

    private String ttids = "";

    private File ttidFile;

    private String apkName;

    private String channelPropertyName = "ttid";

    private String propertiesFile = "config.properties";

    private String channelFolder = "dev-adaptation";

    public DefaultChannelConfig(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getTtids() {
        return ttids;
    }

    public void setTtids(String ttids) {
        this.ttids = ttids;
    }

    public File getTtidFile() {
        return ttidFile;
    }

    public void setTtidFile(File ttidFile) {
        this.ttidFile = ttidFile;
    }

    public String getApkName() {
        return apkName;
    }

    public void setApkName(String apkName) {
        this.apkName = apkName;
    }

    public String getChannelPropertyName() {
        return channelPropertyName;
    }

    public void setChannelPropertyName(String channelPropertyName) {
        this.channelPropertyName = channelPropertyName;
    }

    public String getPropertiesFile() {
        return propertiesFile;
    }

    public void setPropertiesFile(String propertiesFile) {
        this.propertiesFile = propertiesFile;
    }

    public String getChannelFolder() {
        return channelFolder;
    }

    public void setChannelFolder(String channelFolder) {
        this.channelFolder = channelFolder;
    }
}
