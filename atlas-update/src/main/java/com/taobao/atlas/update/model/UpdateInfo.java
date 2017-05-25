package com.taobao.atlas.update.model;

import android.taobao.atlas.runtime.RuntimeVariables;

import java.io.File;
import java.io.Serializable;
import java.util.List;

/**
 * Created by wuzhong on 2016/11/23.
 */

public class UpdateInfo implements Serializable{

    /**
     * 当前的客户端版本
     */
    public String baseVersion;
    /**
     * 更新后的客户端版本
     */
    public String updateVersion;

    public boolean dexPatch;

    public boolean lowDisk = false;

    /**
     * 更新的模块列表信息
     */
    public List<Item> updateBundles;

    public File workDir = new File(RuntimeVariables.androidApplication.getCacheDir(), "atlas_update");

    /**
     * 更新的模块信息
     */
    public static class Item implements Serializable {
        /**
         * 是不是主dex
         */
        public boolean isMainDex;
        /**
         * bundle 的名称
         */
        public String name;
        /**
         * bundle 版本信息
         */
//        public String version;
//        /**
//         * bundle 的代码仓库对应的版本
//         */
//        public String srcVersion;

        public String unitTag;
        public String srcUnitTag;
        /**
         * 依赖的 bundle 列表
         */
        public List<String> dependency;

        public long dexpatchVersion = -1;

        public boolean reset = false;
    }

}
