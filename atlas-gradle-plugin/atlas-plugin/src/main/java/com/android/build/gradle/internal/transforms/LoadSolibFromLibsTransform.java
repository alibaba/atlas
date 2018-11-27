package com.android.build.gradle.internal.transforms;

import com.google.common.collect.ImmutableList;
import org.apache.commons.io.FileUtils;
import org.gradle.api.artifacts.transform.ArtifactTransform;

import java.io.File;
import java.util.Collection;
import java.util.List;

/**
 * @author lilong
 * @create 2017-12-03 下午2:20
 */
public class LoadSolibFromLibsTransform extends ArtifactTransform {

    @Override
    public List<File> transform(File input) {

        File libs =  new File(input,"jars/libs");

        if (!libs.exists()){
            return ImmutableList.of();
        }
        Collection<File>files = FileUtils.listFiles(libs,new String[]{"so"},true);

        if (files != null && files.size() > 0){
            return ImmutableList.of(libs);

        }

        return ImmutableList.of();

    }
}
