package com.taobao.zip;

import com.android.tools.r8.CompilationFailedException;
import com.taobao.android.builder.tools.zip.BetterZip;
import com.taobao.android.builder.tools.zip.SevenZip;

import org.junit.Test;

import java.io.File;
import java.io.IOException;

/**
 * com.taobao.zip
 *
 * @author lilong
 * @time 9:00 AM
 * @date 2020/3/19
 */
public class ZipTest {

    @Test
    public void test() throws IOException, CompilationFailedException {
        File zipFile = new File("/Users/lilong/Downloads/unzip7/classes.zip");
        File unzip = new File("/Users/lilong/Downloads/unzip71");

        SevenZip.decompress(zipFile.getAbsolutePath(),unzip.getAbsolutePath());
//        BetterZip.addFileAndDir(zipFile, "lib", folder);
    }
}
