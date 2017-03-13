package com.taobao.android.builder.tasks.library;

import com.taobao.android.builder.tasks.maven.PublishToMavenRepositoryHook;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository;
import org.gradle.api.publish.plugins.PublishingPlugin;
import org.gradle.api.tasks.TaskContainer;

/**
 * Created by wuzhong on 2017/2/9.
 */
public class PublishHooker {

    PublishToMavenRepository publishTask = null;

    PublishToMavenRepositoryHook hookTask = null;

    private Project project;

    public PublishHooker(Project project) {
        this.project = project;
    }

    public void hookPublish() {

        project.getTasks().whenTaskAdded(new Action<Task>() {
            @Override
            public void execute(Task task) {
                if ("publishMavenPublicationToMavenRepository".equals(task.getName())) {
                    publishTask = (PublishToMavenRepository) task;
                    task.setEnabled(false);

                    if (null != hookTask) {
                        hookTask.setRepository(publishTask.getRepository());
                        hookTask.setPublication(publishTask.getPublication());
                    }
                }
            }
        });

        final TaskContainer tasks = project.getTasks();
        Task publishLifecycleTask = tasks.getByName(PublishingPlugin.PUBLISH_LIFECYCLE_TASK_NAME);
        String taskName = "publishMavenPublicationToMavenRepositoryHook";
        hookTask = tasks.create(taskName,
                                PublishToMavenRepositoryHook.class,
                                new Action<PublishToMavenRepositoryHook>() {
                                    public void execute(PublishToMavenRepositoryHook publishTask) {
                                        publishTask.setGroup(PublishingPlugin.PUBLISH_TASK_GROUP);
                                    }
                                });

        publishLifecycleTask.dependsOn(taskName);
    }
}
