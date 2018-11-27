package com.taobao.android.builder.tasks.app.merge;

import com.taobao.android.builder.AtlasBuildContext;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.file.FileCollection;

import java.io.File;
import java.util.*;
import java.util.function.Consumer;

/**
 * @author lilong
 * @create 2017-12-06 下午11:02
 */

public class MainArtifactsCollection implements ArtifactCollection {

    private Set<ResolvedArtifactResult>fullArtifacts = new HashSet<>();
    private Project project;
    private String variantName;

    Set<ResolvedArtifactResult>mainDexs;

    public MainArtifactsCollection(ArtifactCollection fullArtifacts, Project project,String variantName) {
        this.fullArtifacts = fullArtifacts.getArtifacts();
        this.project = project;
        this.variantName = variantName;
    }

    @Override
    public FileCollection getArtifactFiles() {
        Set<File> mainDexFiles = new HashSet<>();
        if (mainDexs != null){
            for (ResolvedArtifactResult resolvedArtifactResult:mainDexs){
                mainDexFiles.add(resolvedArtifactResult.getFile());
            }
        }else {
            getArtifacts();
            getArtifactFiles();
        }

        return project.files(mainDexFiles);
    }

    @Override
    public Set<ResolvedArtifactResult> getArtifacts() {
            mainDexs = new HashSet<>();
        for (ResolvedArtifactResult resolvedArtifactResult:fullArtifacts){
            String name = resolvedArtifactResult.getFile().getAbsolutePath();
            if (AtlasBuildContext.atlasMainDexHelperMap.get(variantName).getMainManifestFiles().containsKey(name.substring(0,name.lastIndexOf(File.separatorChar)))){
                mainDexs.add(resolvedArtifactResult);
            }
        }
        return mainDexs;

    }

    @Override
    public Collection<Throwable> getFailures() {
        return null;
    }

    @Override
    public Iterator<ResolvedArtifactResult> iterator() {
        return getArtifacts().iterator();
    }

    @Override
    public void forEach(Consumer<? super ResolvedArtifactResult> action) {
        getArtifacts().forEach(action);
    }

    @Override
    public Spliterator<ResolvedArtifactResult> spliterator() {
        return getArtifacts().spliterator();
    }
}
