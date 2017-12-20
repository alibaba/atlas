package com.taobao.android.builder.tasks.app.merge;


import com.android.build.gradle.api.BaseVariantOutput;
import com.android.build.gradle.internal.api.AppVariantContext;
import com.taobao.android.builder.AtlasBuildContext;
import com.taobao.android.builder.dependency.AtlasDependencyTree;
import com.taobao.android.builder.dependency.model.AwbBundle;
import com.taobao.android.builder.tasks.app.merge.bundle.MergeAwbAssets;
import com.taobao.android.builder.tasks.manager.MtlBaseTaskAction;
import com.taobao.android.builder.tasks.manager.MtlParallelTask;
import com.taobao.android.builder.tasks.manager.TaskCreater;
import org.gradle.api.DefaultTask;

import java.util.ArrayList;
import java.util.List;

public class MergeAwbsJniFolder extends MtlBaseTaskAction<MtlParallelTask> {
    private AppVariantContext appVariantContext;

    public MergeAwbsJniFolder(AppVariantContext appVariantContext, BaseVariantOutput baseVariantOutputData) {
        super(appVariantContext, baseVariantOutputData);
        this.appVariantContext = appVariantContext;
    }

    @Override
    public String getName() {
        return scope.getTaskName("mergeJniFolders", "Awbs");
    }

    @Override
    public Class<MtlParallelTask> getType() {
        return MtlParallelTask.class;
    }

    @Override
    public void execute(MtlParallelTask parallelTask) {

        super.execute(parallelTask);

        AtlasDependencyTree atlasDependencyTree = AtlasBuildContext.androidDependencyTrees.get(parallelTask.getVariantName());

        if (null == atlasDependencyTree) {
            return;
        }

        List<DefaultTask> tasks = new ArrayList<DefaultTask>();

        for (final AwbBundle awbBundle : atlasDependencyTree.getAwbBundles()) {

            MergeAwbAssets.MergeAwbJniLibFoldersConfigAction mergeAwbJniLibFoldersConfigAction = new MergeAwbAssets.MergeAwbJniLibFoldersConfigAction(appVariantContext, baseVariantOutput, awbBundle);

            MergeAwbAssets mergeTask = TaskCreater.create(appVariantContext.getProject(), mergeAwbJniLibFoldersConfigAction.getName(), mergeAwbJniLibFoldersConfigAction.getType());

            mergeAwbJniLibFoldersConfigAction.execute(mergeTask);

            tasks.add(mergeTask);

        }


        parallelTask.parallelTask = tasks;
        parallelTask.concurrent = false;
        parallelTask.uniqueTaskName = getName();

    }
}
