package com.android.build.gradle.internal.transforms;

import com.android.build.gradle.internal.dependency.ExtractAarTransform;
import com.taobao.android.builder.AtlasBuildContext;

import java.io.File;
import java.util.List;

/**
 * @author lilong
 * @create 2017-11-30 下午10:36
 */

public class ExtractAwbTransform extends ExtractAarTransform {

    @Override
    public List<File> transform(File input) {
        File outputDirectory = getOutputDirectory();
        AtlasBuildContext.bundleExploadDir.put(outputDirectory.getParentFile().getName(),outputDirectory);
        return super.transform(input);
    }
}
