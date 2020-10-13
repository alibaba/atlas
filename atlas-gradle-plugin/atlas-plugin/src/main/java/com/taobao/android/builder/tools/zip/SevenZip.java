package com.taobao.android.builder.tools.zip;

import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * com.taobao.android.builder.tools.zip
 *
 * @author lilong
 * @time 5:47 PM
 * @date 2020/10/13
 */
public class SevenZip {


    public static void compress(String name, File... files) {
        try (
                SevenZOutputFile out = new SevenZOutputFile(new File(name))) {
            for (File file : files) {
                addToArchiveCompression(out, file, ".");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 解压7z文件
     *
     * @param orgPath 源压缩文件地址
     * @param tarpath 解压后存放的目录地址
     */
    public static void decompress(String orgPath, String tarpath) {
        try {
            SevenZFile sevenZFile = new SevenZFile(new File(orgPath));
            SevenZArchiveEntry entry = sevenZFile.getNextEntry();
            while (entry != null) {
                File file = new File(tarpath + File.separator + entry.getName());
                if (entry.isDirectory()) {
                    if (!file.exists()) {
                        file.mkdirs();
                    }
                    entry = sevenZFile.getNextEntry();
                    continue;
                }
                if (!file.getParentFile().exists()) {
                    file.getParentFile().mkdirs();
                }

                OutputStream out = new FileOutputStream(file);
                BufferedOutputStream bos = new BufferedOutputStream(out);
                int len = -1;
                byte[] buf = new byte[1024];
                while ((len = sevenZFile.read(buf)) != -1) {
                    bos.write(buf, 0, len);
                }
                // 关流顺序，先打开的后关闭
                bos.close();
                out.close();
                entry = sevenZFile.getNextEntry();
            }
            sevenZFile.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void addToArchiveCompression(SevenZOutputFile out, File file, String dir) throws IOException {
        String name = dir + File.separator + file.getName();
        if (dir.equals(".")) {
            name = file.getName();
        }
        SevenZArchiveEntry entry = null;

        if (file.isFile()) {
            FileInputStream fos = new FileInputStream(file);
            BufferedInputStream bis = new BufferedInputStream(fos);
            entry = out.createArchiveEntry(file, name);
            out.putArchiveEntry(entry);
            int len = -1;
            //将源文件写入到7z文件中
            byte[] buf = new byte[1024];
            while ((len = bis.read(buf)) != -1) {
                out.write(buf, 0, len);
            }
            bis.close();
            fos.close();
            out.closeArchiveEntry();
        } else if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    addToArchiveCompression(out, child, name);
                }
            }
        } else {
            System.out.println(file.getName() + " is not supported");
        }
    }
}


