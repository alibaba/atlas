package com.taobao.android.builder.tasks.library;

import java.io.File;
import java.io.IOException;

import com.taobao.android.builder.extension.AtlasExtension;
import com.taobao.android.builder.tools.zip.ZipUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.gradle.api.Action;
import org.gradle.api.Task;
import org.gradle.api.tasks.bundling.Zip;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by wuzhong on 2017/2/25.
 */
public class AwbGenerator {

    private static Logger sLogger = LoggerFactory.getLogger(AwbGenerator.class);

    private AtlasExtension atlasExtension;

    public AwbGenerator(AtlasExtension atlasExtension) {
        this.atlasExtension = atlasExtension;
    }

    /**
     * 创建基本的AWB任务
     */
    public void generate(final Zip bundleTask) {

        if (atlasExtension.getBundleConfig().isAwbBundle()) {
            bundleTask.setArchiveName(FilenameUtils.getBaseName(bundleTask.getArchiveName()) +
                                          ".awb");
            bundleTask.setDestinationDir(new File(bundleTask.getDestinationDir().getParentFile(),
                                                  "awb"));
        }

        bundleTask.doLast(new Action<Task>() {
            @Override
            public void execute(Task task) {

                File outputFile = new File(bundleTask.getDestinationDir(),
                                           bundleTask.getArchiveName());

                if (!outputFile.exists()) {
                    return;
                }

                try {
                    extractJar(outputFile, bundleTask);
                    //重新生成aar
                    if (atlasExtension.getBundleConfig().isAwbBundle()) {

                        FileUtils.copyFile(outputFile,
                                           new File(new File(bundleTask.getDestinationDir()
                                                                 .getParentFile(), "aar"),
                                                    FilenameUtils.getBaseName(bundleTask.getArchiveName()) +
                                                        ".aar"));

                    }
                } catch (IOException e) {
                    sLogger.error("awb exception",e);
                }
            }
        });
    }

    private void extractJar(File outputFile, Zip bundleTask) {
        File f = ZipUtils.extractZipFileToFolder(outputFile,
                                                 "classes.jar",
                                                 outputFile.getParentFile());

        if (null != f && f.exists()) {
            File jar = new File(new File(bundleTask.getDestinationDir().getParentFile(), "jar"),
                                FilenameUtils.getBaseName(bundleTask.getArchiveName()) +
                                    ".jar");
            jar.getParentFile().mkdirs();
            f.renameTo(jar);
        }
    }
}
