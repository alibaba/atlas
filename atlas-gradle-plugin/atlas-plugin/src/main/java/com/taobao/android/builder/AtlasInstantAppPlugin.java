package com.taobao.android.builder;

import com.android.build.gradle.AppPlugin;
import com.android.build.gradle.BasePlugin;
import com.android.build.gradle.InstantAppPlugin;
import com.taobao.android.builder.manager.AtlasConfigurationHelper;
import com.taobao.android.builder.manager.PluginManager;
import org.gradle.api.Project;
import org.gradle.internal.reflect.Instantiator;

/**
 * AtlasInstantAppPlugin
 *
 * @author zhayu.ll
 * @date 18/1/4
 * @time 下午5:54
 * @description  
 */
public class AtlasInstantAppPlugin extends AtlasPlugin {


    public AtlasInstantAppPlugin(Instantiator instantiator) {
        super(instantiator);
    }

    @Override
    public void apply(Project project) {
        PluginManager.addPluginIfNot(project, AppPlugin.class);
        PluginManager.addPluginIfNot(project, InstantAppPlugin.class);
        super.apply(project);
    }
}
