package com.taobao.android.builder;

import com.android.build.gradle.AppPlugin;
import com.taobao.android.builder.manager.AtlasConfigurationHelper;
import com.taobao.android.builder.manager.PluginManager;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.internal.reflect.Instantiator;

import javax.inject.Inject;

/**
 * @author lilong
 * @create 2017-08-23 9:10 a.m.
 */

public class AtlasDexPatchPlugin extends AtlasBasePlugin {
    @Inject
    public AtlasDexPatchPlugin(Instantiator instantiator) {
        super(instantiator);
    }

    @Override
    public void apply(Project project) {
        PluginManager.addPluginIfNot(project, AppPlugin.class);
        PluginManager.addPluginIfNot(project, AtlasPlugin.class);
        super.apply(project);
        project.afterEvaluate(new Action<Project>()
            {
            @Override
            public void execute(Project project) {

                atlasConfigurationHelper.configDexPatchTasksAfterEvaluate();
            }
        });
    }

    @Override
    protected AtlasConfigurationHelper getConfigurationHelper(Project project) {
        return new AtlasConfigurationHelper(project,
                instantiator,
                creator);
    }
}
