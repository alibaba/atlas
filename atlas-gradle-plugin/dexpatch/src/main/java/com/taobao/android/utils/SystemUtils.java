package com.taobao.android.utils;

import org.apache.commons.lang3.StringUtils;

import java.io.File;

/**
 * SystemUtils
 *
 * @author zhayu.ll
 * @date 18/4/23
 */
public class SystemUtils {


    public static File getDiffFile() {


        String osName = "mac";
        String fileName = "bsdiff";

        if (StringUtils.containsIgnoreCase(System.getProperty("os.name"), "Mac")) {
            osName = "mac";
        } else if (StringUtils.containsIgnoreCase(System.getProperty("os.name"), "Linux")) {
            osName = "linux";
        } else if (StringUtils.containsIgnoreCase(System.getProperty("os.name"), "windows")) {
            osName = "win";
            fileName = "bsdiff.exe";
        }
        String diffPath = osName + "/" + fileName;

        return new File(SystemUtils.class.getClassLoader().getResource(diffPath).getFile());
    }


    public static File getPatchFile() {


        String osName = "mac";
        String fileName = "bspatch";

        if (StringUtils.containsIgnoreCase(System.getProperty("os.name"), "Mac")) {
            osName = "mac";
        } else if (StringUtils.containsIgnoreCase(System.getProperty("os.name"), "Linux")) {
            osName = "linux";
        } else if (StringUtils.containsIgnoreCase(System.getProperty("os.name"), "windows")) {
            osName = "win";
            fileName = "bspatch.exe";
        }
        String diffPath = osName + "/" + fileName;

        return new File(SystemUtils.class.getClassLoader().getResource(diffPath).getFile());
    }
}
