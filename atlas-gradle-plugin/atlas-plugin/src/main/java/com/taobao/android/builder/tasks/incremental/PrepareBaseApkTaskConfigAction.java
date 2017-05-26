package com.taobao.android.builder.tasks.incremental;

import java.io.File;
import java.util.Set;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.api.VariantContext;
import com.android.build.gradle.internal.pipeline.StreamFilter;
import com.android.build.gradle.internal.variant.BaseVariantOutputData;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.taobao.android.builder.tasks.manager.MtlBaseTaskAction;
import groovy.lang.Closure;
import org.gradle.api.file.CopySpec;
import org.gradle.api.tasks.Sync;
import org.gradle.api.tasks.util.PatternSet;

/**
 * Created by chenhjohn on 2017/5/26.
 */

public class PrepareBaseApkTaskConfigAction extends MtlBaseTaskAction<Sync> {
    public PrepareBaseApkTaskConfigAction(VariantContext variantContext, BaseVariantOutputData baseVariantOutputData) {
        super(variantContext, baseVariantOutputData);
    }

    @Override
    @NonNull
    public String getName() {
        return scope.getTaskName("prepare", "BaseApk");
    }

    @NonNull
    @Override
    public Class<Sync> getType() {
        return Sync.class;
    }

    @Override
    public void execute(@NonNull Sync prepareBaseApkTask) {
        prepareBaseApkTask.from(variantContext.getProject().zipTree(variantContext.apContext.getBaseApk()),
                                new Closure(PrepareBaseApkTaskConfigAction.class) {
                                    public Object doCall(CopySpec cs) {
                                        int dexFilesCount = 0;
                                        Set<File> dexFolders = scope.getVariantScope().getTransformManager()
                                            .getPipelineOutput(StreamFilter.DEX).keySet();
                                        // Preconditions.checkState(dexFolders
                                        // .size() == 1,
                                        //                          "There must
                                        // be exactly one output");
                                        for (File dexFolder : dexFolders) {
                                            dexFilesCount += scope.getGlobalScope().getProject().fileTree(ImmutableMap
                                                                                                              .of("dir",
                                                                                                                  dexFolder,
                                                                                                                  "includes",
                                                                                                                  ImmutableList
                                                                                                                      .of("classes*.dex")))
                                                .getFiles().size();
                                        }
                                        Set<File> baseDexFileSet = variantContext.getProject().zipTree(
                                            variantContext.apContext.getBaseApk()).matching(
                                            new PatternSet().include("classes*.dex")).getFiles();
                                        File[] baseDexFiles = baseDexFileSet.toArray(new File[baseDexFileSet.size()]);
                                        int j = baseDexFileSet.size() + dexFilesCount;
                                        for (int i = baseDexFiles.length - 1; i >= 0; i--) {
                                            cs.rename(baseDexFiles[i].getName(), "classes" + j + ".dex");
                                            j--;
                                        }
                                        return cs;
                                    }
                                });
        prepareBaseApkTask.into(variantContext.apContext.getBaseApkDirectory());
    }
}
