package com.taobao.android.builder.tasks.app.merge;

import com.android.build.gradle.internal.publishing.AndroidArtifacts;
import com.taobao.android.builder.dependency.model.AwbBundle;
import org.apache.commons.io.FileUtils;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.io.SAXReader;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.file.FileCollection;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;

/**
 * AppendMainArtifactsCollection
 *
 * @author zhayu.ll
 * @date 18/6/12
 */
public class AppendMainArtifactsCollection implements ArtifactCollection {

    private ArtifactCollection artifactResults;

    private FileCollection filesResults;



    private Set<ResolvedArtifactResult> allResults = new HashSet<>();

    public static Map<String,String> bundle2Map = new HashMap<>();

    private FileCollection allFiles = null;


    private AwbBundle awbBundle;
    private Project project;

    private AndroidArtifacts.ArtifactType artifactType;

    public AppendMainArtifactsCollection(Project project, ArtifactCollection artifactResults, AwbBundle awbBundle, AndroidArtifacts.ArtifactType artifactType) {
        this.artifactResults = artifactResults;
        this.awbBundle = awbBundle;
        this.project = project;
        this.artifactType = artifactType;
    }

    public AppendMainArtifactsCollection(Project project, FileCollection filesResults, AwbBundle awbBundle, AndroidArtifacts.ArtifactType artifactType) {
        this.filesResults = filesResults;
        this.awbBundle = awbBundle;
        this.project = project;
        this.artifactType = artifactType;
    }

    @Override
    public FileCollection getArtifactFiles() {
        if (allFiles != null) {
            return allFiles;
        }
        FileCollection files = null;
        if (artifactResults!= null) {
            files = artifactResults.getArtifactFiles();
        }else {
            files = filesResults;
        }
        Set<File> fileSet = new HashSet<>();

        switch (artifactType) {
            case ANDROID_RES:
                for (ResolvedArtifactResult resolvedArtifactResult : awbBundle.getResolvedResArtifactResults()) {
                    fileSet.add(correctFile(resolvedArtifactResult.getFile()));
                }
                allFiles = files.plus(project.files(fileSet));
                return allFiles;
            case ASSETS:
                for (ResolvedArtifactResult resolvedArtifactResult : awbBundle.getResolvedAssetsArtifactResults()) {
                    fileSet.add(resolvedArtifactResult.getFile());
                }
                allFiles = files.plus(project.files(fileSet));
                return allFiles;
            case SYMBOL_LIST_WITH_PACKAGE_NAME:
                for (ResolvedArtifactResult resolvedArtifactResult : awbBundle.getResolvedSymbolListWithPackageNameArtifactResults()) {
                    fileSet.add(resolvedArtifactResult.getFile());
                }
                allFiles = files.plus(project.files(fileSet));
                return allFiles;

        }

        return null;
    }

    @Override
    public Set<ResolvedArtifactResult> getArtifacts() {
        if (allResults.size() == 0) {
            allResults.addAll(artifactResults.getArtifacts());

            switch (artifactType) {
                case ASSETS:
                    allResults.addAll(awbBundle.getResolvedAssetsArtifactResults());
                    break;

                case ANDROID_RES:
                    allResults.addAll(awbBundle.getResolvedResArtifactResults());
                    break;

                case SYMBOL_LIST_WITH_PACKAGE_NAME:
                    allResults.addAll(awbBundle.getResolvedSymbolListWithPackageNameArtifactResults());

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

    private File correctFile(File file) {
            Collection<File> files = FileUtils.listFiles(file, new String[]{"xml"}, true);
            files.parallelStream().forEach(file1 -> {
                List<String> lines = null;
                try {
                    lines = FileUtils.readLines(file1);
                    lines.forEach(new Consumer<String>() {
                        @Override
                        public void accept(String s) {
                            if (s.contains("http://schemas.android.com/apk/res/" + awbBundle.getPackageName())){
                                bundle2Map.put(file1.getName(),awbBundle.getPackageName());
                            }
//                            String s1 = s.replace("http://schemas.android.com/apk/res/" + awbBundle.getPackageName(), "http://schemas.android.com/apk/res-auto");
//                            newLines.add(s1);
                        }
                    });
//                    FileUtils.writeLines(file1, newLines);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        return file;

    }

}
