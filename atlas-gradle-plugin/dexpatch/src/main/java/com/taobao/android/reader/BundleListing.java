package com.taobao.android.reader;

import java.io.Serializable;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * @author lilong
 * @create 2017-08-15 下午1:40
 */

public class BundleListing implements Serializable {

    private LinkedHashMap<String, BundleInfo> bundles = new LinkedHashMap<String, BundleInfo>();

    public LinkedHashMap<String, BundleInfo> getBundles() {
        return bundles;
    }

    public void setBundles(LinkedHashMap<String, BundleInfo> bundles) {
        this.bundles = bundles;
    }


    public static class BundleInfo {
        private String name;
        private String pkgName;
        private String applicationName;
        private String version;
        private String desc;
        private String url;
        private String md5;
        private boolean isInternal = true;
        private List<String> dependency;
        private List<String> totalDependency;
        private HashMap<String, Boolean> activities;
        private HashMap<String, Boolean> services;
        private HashMap<String, Boolean> receivers;
        private HashMap<String, Boolean> contentProviders;
        private String unique_tag;

        public String getCurrent_unique_tag() {
            return current_unique_tag;
        }

        public void setCurrent_unique_tag(String current_unique_tag) {
            this.current_unique_tag = current_unique_tag;
        }

        private String current_unique_tag;
        private long size;

        public long getSize() {
            return size;
        }

        public void setSize(long size) {
            this.size = size;
        }

        public boolean isInternal() {
            return isInternal;
        }

        public void setIsInternal(boolean isInternal) {
            this.isInternal = isInternal;
        }

        public String getApplicationName() {
            return applicationName;
        }

        public void setApplicationName(String applicationName) {
            this.applicationName = applicationName;
        }

        public HashMap<String, Boolean> getReceivers() {
            return receivers;
        }

        public void setReceivers(HashMap<String, Boolean> receivers) {
            this.receivers = receivers;
        }

        public HashMap<String, Boolean> getContentProviders() {
            return contentProviders;
        }

        public void setContentProviders(HashMap<String, Boolean> contentProviders) {
            this.contentProviders = contentProviders;
        }

        public String getUnique_tag() {
            return unique_tag;
        }

        public void setUnique_tag(String unique_tag) {
            this.unique_tag = unique_tag;
        }

        public String getMd5() {
            return md5;
        }

        public void setMd5(String md5) {
            this.md5 = md5;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getDesc() {
            return desc;
        }

        public void setDesc(String desc) {
            this.desc = desc;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

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

        public HashMap<String, Boolean> getActivities() {
            return activities;
        }

        public void setActivities(HashMap<String, Boolean> activities) {
            this.activities = activities;
        }

        public HashMap<String, Boolean> getServices() {
            return services;
        }

        public void setServices(HashMap<String, Boolean> services) {
            this.services = services;
        }

    }
}