package com.taobao.android.builder;
import com.android.build.gradle.*;
import com.taobao.android.builder.manager.PluginManager;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;

import javax.inject.Inject;

/**
 * AtlasFeaturePlugin
 *
 * @author zhayu.ll
 * @date 18/1/3
 * @time 下午7:04
 * @description  
 */
public class AtlasFeaturePlugin implements Plugin<Project> {


    @Override
    public void apply(Project project) {
        PluginManager.addPluginIfNot(project,FeaturePlugin.class);
        PluginManager.addPluginIfNot(project,AtlasPlugin.class);

    }

}
