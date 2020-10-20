package com.taobao.android.builder.tools.zip;

import com.android.ide.common.process.CmdExecutor;

import java.io.File;
import java.io.IOException;

/**
 * com.taobao.android.builder.tools.zip
 *
 * @author lilong
 * @time 10:11 AM
 * @date 2020/10/14
 */
public class Better7Zip {

    public static boolean  zipDirectory(File folder, File dest) {

        if (!folder.isDirectory()) {
            return false;
        }

        //zip -r taobao-android-debug.apk zzzz
        boolean success = CmdExecutor.execute(folder.getAbsolutePath(), "7z", "a", "-t7z","-r",dest.getAbsolutePath(), "./");

        if (success) {
            return true;
        }

        dest.delete();

        try {
            SevenZip.compress(dest.getAbsolutePath(),folder);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }

    public static boolean unzipDirectory(File zipFile, File dest) throws IOException {

        if (!zipFile.exists()) {
            return false;
        }

        //zip -r taobao-android-debug.apk zzzz
        boolean success = CmdExecutor.execute(dest.getAbsolutePath(), "7z", "x", zipFile.getAbsolutePath(), "-r","-o./");

        if (success) {
            return true;
        }

        dest.delete();

        try {
            SevenZip.decompress(zipFile.getAbsolutePath(), dest.getAbsolutePath());
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }
}
