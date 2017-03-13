package com.taobao.android.builder.tasks.library;

import com.android.build.gradle.tasks.MergeResources;
import com.taobao.android.builder.tasks.manager.TaskQueryHelper;

import org.apache.commons.io.FileUtils;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.Task;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Created by wuzhong on 2017/2/25.
 */
@Deprecated
public class ResMerger {

    private Project project;

    public ResMerger(Project project) {
        this.project = project;
    }

    public void mergeRes() {
        List<MergeResources> mergeResources = TaskQueryHelper.findTask(project,
                                                                       MergeResources.class);
        for (MergeResources task : mergeResources) {
            if (task.getName().equals("packageDebugResources") ||
                    task.getName().equals("packageReleaseResources")) {
                task.doLast(new Action<Task>() {
                    @Override
                    public void execute(Task task) {
                        try {
                            MergeResources task2 = (MergeResources) task;
                            FileUtils.deleteDirectory(task2.getOutputDir());
                            File srcFile = task2.getInputResourceSets()
                                    .get(0)
                                    .getSourceFiles()
                                    .get(0);
                            FileUtils.copyDirectory(srcFile, task2.getOutputDir());
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                });
            }
        }
    }
}
