package com.android.build.gradle.internal.transforms;

import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteStreams;
import org.gradle.api.artifacts.transform.ArtifactTransform;

import java.io.*;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static com.android.utils.FileUtils.mkdirs;

/**
 * @author lilong
 * @create 2017-11-30 下午10:07
 */

public class ExtractApTransform extends ArtifactTransform {

    @Override
    public List<File> transform(File input) {
        File outputDir = getOutputDirectory();
        mkdirs(outputDir);

        try (InputStream fis = new BufferedInputStream(new FileInputStream(input));
             ZipInputStream zis = new ZipInputStream(fis)) {
            // loop on the entries of the intermediary package and put them in the final package.
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                try {
                    String name = entry.getName();

                    // do not take directories
                    if (entry.isDirectory()) {
                        continue;
                    }
                    String path = name;

                    File outputFile = new File(outputDir, path.replace('/', File.separatorChar));
                    mkdirs(outputFile.getParentFile());

                    try (OutputStream outputStream =
                                 new BufferedOutputStream(new FileOutputStream(outputFile))) {
                        ByteStreams.copy(zis, outputStream);
                        outputStream.flush();
                    }
                } finally {
                    zis.closeEntry();
                }
            }

        } catch (Throwable t) {
            throw new RuntimeException(t);
        }

        return ImmutableList.of(outputDir);
        }
}
