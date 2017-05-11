package com.taobao.atlas.update.util;

import java.io.File;

/**
 * Created by wuzhong on 2016/11/23.
 */

public class PatchCleaner {

    public static void clearUpdatePath(String bundleUpdatePath) {
        File updatePath = new File(bundleUpdatePath);
        if (updatePath.exists()) {
            File[] updatePathArray = updatePath.listFiles();
            for (File file : updatePathArray) {
                if (file.isDirectory()) {
                    clearUpdatePath(file.getAbsolutePath());
                } else {
                    file.delete();
                }
            }
            updatePath.delete();
        }
    }

}
