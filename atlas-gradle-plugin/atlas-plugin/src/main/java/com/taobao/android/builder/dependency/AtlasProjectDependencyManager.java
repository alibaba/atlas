package com.taobao.android.builder.dependency;

import java.util.function.Consumer;

import com.taobao.android.builder.AtlasPlugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.internal.artifacts.dependencies.DefaultProjectDependency;

/**
 * Created by wuzhong on 2017/3/16.
 *
 * @author wuzhong
 * @date 2017/03/16
 */
public class AtlasProjectDependencyManager {

    public static void addProjectDependency(Project project, String variantName) {

        Task task = project.getTasks().findByName("prepare" + variantName + "Dependencies");

        if (null == task){
            return;
        }

        DependencySet dependencies = project.getConfigurations().getByName(
            AtlasPlugin.BUNDLE_COMPILE).getDependencies();

        if (null == dependencies){
            return;
        }

        dependencies.forEach(new Consumer<Dependency>() {
            @Override
            public void accept(Dependency dependency) {
                if (dependency instanceof  DefaultProjectDependency){

                    Project subProject = ((DefaultProjectDependency)dependency).getDependencyProject();

                    Task assembleTask = subProject.getTasks().findByName("assembleRelease");

                    task.dependsOn(assembleTask);

                }
            }
        });

    }

}
