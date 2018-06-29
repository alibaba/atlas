package com.taobao.android.builder.tasks.app.merge;

import com.android.build.gradle.internal.publishing.AndroidArtifacts;
import com.taobao.android.builder.dependency.model.AwbBundle;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.file.FileCollection;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * AppendMainArtifactsCollection
 *
 * @author zhayu.ll
 * @date 18/6/12
 */
public class AppendMainArtifactsCollection implements ArtifactCollection {

    private ArtifactCollection artifactResults;

    private Set<ResolvedArtifactResult> allResults = new HashSet<>();
    private FileCollection allFiles = null;


    private AwbBundle awbBundle;
    private Project project;

    private AndroidArtifacts.ArtifactType artifactType;

    public AppendMainArtifactsCollection(Project project, ArtifactCollection artifactResults, AwbBundle awbBundle,AndroidArtifacts.ArtifactType artifactType) {
        this.artifactResults = artifactResults;
        this.awbBundle = awbBundle;
        this.project = project;
        this.artifactType = artifactType;
    }

    @Override
    public FileCollection getArtifactFiles() {
        if (allFiles != null) {
            return allFiles;
        }
        FileCollection files = artifactResults.getArtifactFiles();
        Set<File> fileSet = new HashSet<>();

        switch (artifactType){
            case ANDROID_RES:
                for (ResolvedArtifactResult resolvedArtifactResult : awbBundle.getResolvedResArtifactResults()) {
                    fileSet.add(resolvedArtifactResult.getFile());
                }
                allFiles = files.plus(project.files(fileSet));
                return allFiles;
            case ASSETS:
                for (ResolvedArtifactResult resolvedArtifactResult : awbBundle.getResolvedAssetsArtifactResults()) {
                    fileSet.add(resolvedArtifactResult.getFile());
                }
                allFiles=files.plus(project.files(fileSet));
                return allFiles;
        }

       return null;
    }

    @Override
    public Set<ResolvedArtifactResult> getArtifacts() {
        if (allResults.size() == 0) {
            allResults.addAll(artifactResults.getArtifacts());

            switch (artifactType){
                case ASSETS:
                    allResults.addAll(awbBundle.getResolvedAssetsArtifactResults());
                    break;

                case ANDROID_RES:
                    allResults.addAll(awbBundle.getResolvedResArtifactResults());
                    break;

            }

        }
        return allResults;
    }

    @Override
    public Collection<Throwable> getFailures() {
        return null;
    }

    @NotNull
    @Override
    public Iterator<ResolvedArtifactResult> iterator() {
        if (allResults.size() == 0) {
            getArtifacts();
        }
        return allResults.iterator();
    }
}
