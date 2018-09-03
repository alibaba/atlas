package com.taobao.android.builder.tools;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import java.io.*;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

/**
 * JarUtils
 *
 * @author zhayu.ll
 * @date 18/9/1
 */
public class JarUtils {


    public static void splitMainJar(List<File> result, File outJar, int index,int maxClassesSize) throws IOException {
        boolean hasClass = false;
        File splitOutJar = new File(outJar.getParentFile(), FileNameUtils.getUniqueJarName(outJar) + "-" + (index + 1) + ".jar");
        File splitOutJarMain = new File(outJar.getParentFile(), FileNameUtils.getUniqueJarName(outJar) + "-" + (index) + ".jar");
        JarOutputStream jos = new JarOutputStream(
                new BufferedOutputStream(new FileOutputStream(splitOutJar)));
        JarOutputStream josMain = new JarOutputStream(
                new BufferedOutputStream(new FileOutputStream(splitOutJarMain)));
        JarFile jarFile = new JarFile(outJar);
        try {

            Enumeration<JarEntry> entryEnumeration = jarFile.entries();
            int i = 0;
            while (entryEnumeration.hasMoreElements()) {
                JarEntry jarEntry = entryEnumeration.nextElement();
                if (jarEntry.getName().endsWith(".class")) {
                    i++;
                }
                if (i > maxClassesSize) {
                    hasClass = true;
                    copyStream(jarFile.getInputStream(jarEntry), jos, jarEntry, jarEntry.getName());
                } else {
                    copyStream(jarFile.getInputStream(jarEntry), josMain, jarEntry, jarEntry.getName());
                }
            }
            IOUtils.closeQuietly(jos);
            IOUtils.closeQuietly(josMain);
            jarFile.close();
            if (!hasClass) {
                FileUtils.deleteQuietly(splitOutJar);
                return;
            } else {
                FileUtils.deleteQuietly(outJar);
                result.remove(outJar);
                result.add(splitOutJar);
                result.add(splitOutJarMain);
            }
            splitMainJar(result, splitOutJar, index + 1,maxClassesSize);
        }catch (Exception e){

        }finally {

        }

    }

    private static void copyStream(InputStream inputStream, JarOutputStream jos, JarEntry ze, String pathName) {
        try {

            ZipEntry newEntry = new ZipEntry(pathName);
            // Make sure there is date and time set.
            if (ze.getTime() != -1) {
                newEntry.setTime(ze.getTime());
                newEntry.setCrc(ze.getCrc()); // If found set it into output file.
            }
            jos.putNextEntry(newEntry);
            IOUtils.copy(inputStream, jos);
            IOUtils.closeQuietly(inputStream);
        } catch (Exception e) {
            e.printStackTrace();
            //throw new GradleException("copy stream exception", e);
            //e.printStackTrace();
//            logger.error("copy stream exception >>> " + pathName + " >>>" + e.getMessage());
        }
    }
}
