package com.taobao.android.builder.tools.zip;

import com.android.ide.common.process.CmdExecutor;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * \* Created with IntelliJ IDEA.
 * \* User: lilong
 * \* Date: 2020/12/31
 * \* Time: 3:32 下午
 * \* Description:
 * \
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


            return true;
        }
}
