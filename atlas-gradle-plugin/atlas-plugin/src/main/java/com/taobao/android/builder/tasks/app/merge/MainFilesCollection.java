package com.taobao.android.builder.tasks.app.merge;

import com.taobao.android.builder.AtlasBuildContext;
import groovy.lang.Closure;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.internal.file.AbstractFileCollection;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.StopExecutionException;
import org.gradle.api.tasks.TaskDependency;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * MainFilesCollection
 *
 * @author zhayu.ll
 * @date 18/3/1
 */
public class MainFilesCollection extends AbstractFileCollection{

    private Set<File>mainJars = new HashSet<>();
    private String name;

    public MainFilesCollection(String name) {
        this.name = name;
    }


    @Override
    public String getDisplayName() {
        return "maindex-jars";
    }

    @Override
    public Set<File> getFiles() {
        if (mainJars.size() > 0){
            return mainJars;
        }else {
            mainJars.addAll(AtlasBuildContext.atlasMainDexHelperMap.get(name).getAllMainDexJars());
        }
        return mainJars;
    }
}
