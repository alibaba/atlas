package com.taobao.zip;

import com.android.tools.r8.CompilationFailedException;
import com.taobao.android.builder.tools.zip.BetterZip;

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
        File zipFile = new File("/Users/lilong/Downloads/core-1.6.4.zip");
        File folder = new File("/Users/lilong/Downloads/lib");
        BetterZip.addFileAndDir(zipFile, "lib", folder);
    }
}
