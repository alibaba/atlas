package com.taobao.atlas.update.util;

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Created by wuzhong on 2016/11/23.
 */

public class ZipUtils {

    private static int BUFFEREDSIZE = 1024;

    public static void unzip(String zipFilename, String outputDirectory)
            throws IOException {
        File outFile = new File(outputDirectory);
        if (!outFile.exists()) {
            outFile.mkdirs();
        }
        if (!outFile.exists()) {
            throw new IOException("file not exist");
        }
        ZipFile zipFile = new ZipFile(zipFilename);
        Enumeration en = zipFile.entries();
        ZipEntry zipEntry = null;
        while (en.hasMoreElements()) {
            zipEntry = (ZipEntry) en.nextElement();
            if (zipEntry.isDirectory()) {
                String dirName = zipEntry.getName();
                dirName = dirName.substring(0, dirName.length() - 1);
                File f = new File(outFile.getPath() + File.separator + dirName);
                f.mkdirs();
            } else {
                String strFilePath = outFile.getPath() + File.separator
                        + zipEntry.getName();
                File f = new File(strFilePath);

                // 判断文件不存在的话，就创建该文件所在文件夹的目录
                if (!f.exists()) {
                    String[] arrFolderName = zipEntry.getName().split("/");
                    String strRealFolder = "";
                    for (int i = 0; i < (arrFolderName.length - 1); i++) {
                        strRealFolder += arrFolderName[i] + File.separator;
                    }
                    strRealFolder = outFile.getPath() + File.separator
                            + strRealFolder;
                    File tempDir = new File(strRealFolder);
                    tempDir.mkdirs();
                }
                f.createNewFile();
                InputStream in = zipFile.getInputStream(zipEntry);
                FileOutputStream out = new FileOutputStream(f);
                try {
                    int c;
                    byte[] by = new byte[BUFFEREDSIZE];
                    while ((c = in.read(by)) != -1) {
                        out.write(by, 0, c);
                    }
                    out.flush();
                } catch (IOException e) {
                    throw e;
                } finally {
                    closeQuitely(out);
                    closeQuitely(in);
                }
            }
        }
        zipFile.close();
    }

    private static void closeQuitely(Closeable stream) {
        try {
            if (stream != null)
                stream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
