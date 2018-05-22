package com.android.build.gradle.internal.transforms;

import com.google.common.collect.ImmutableList;
import org.gradle.api.artifacts.transform.ArtifactTransform;

import java.io.File;
import java.util.List;
/**
 * @author lilong
 * @create 2017-12-20 下午1:08
 */

public class LoadSolibTransform extends ArtifactTransform {

    @Override
    public List<File> transform(File input) {
        return ImmutableList.of(input);
    }
}
