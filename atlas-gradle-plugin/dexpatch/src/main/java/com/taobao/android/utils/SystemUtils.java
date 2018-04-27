package com.taobao.android.utils;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.gradle.internal.io.IoUtils;

import java.io.*;

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

        File temp = new File(new File(SystemUtils.class.getProtectionDomain().getCodeSource().getLocation().getFile()).getParentFile(),fileName);
        FileOutputStream fileOutputStream = null;
        InputStream inputStream = null;
        try {

            fileOutputStream = new FileOutputStream(temp);

             inputStream = SystemUtils.class.getClassLoader().getResourceAsStream(diffPath);

            IOUtils.copy(inputStream,fileOutputStream);

        }catch (Exception e){
            e.printStackTrace();
        }finally {
            IOUtils.closeQuietly(inputStream);
            IOUtils.closeQuietly(fileOutputStream);

        }

//        File tempFile = new File(workingDir,)
        return temp;



//        return new File(SystemUtils.class.getClassLoader().getResourceAsStream(diffPath).getFile());
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

        InputStream in = null;
        FileOutputStream fileOutputStream = null;

        File temp = new File(new File(SystemUtils.class.getProtectionDomain().getCodeSource().getLocation().getFile()).getParentFile(),fileName);

        try {

            fileOutputStream = new FileOutputStream(temp);

            in = SystemUtils.class.getClassLoader().getResourceAsStream(diffPath);

            IOUtils.copy(in,fileOutputStream);

        }catch (Exception e){
            e.printStackTrace();
        }finally {
            IOUtils.closeQuietly(in);
            IOUtils.closeQuietly(fileOutputStream);

        }


        return  temp;
    }
}
