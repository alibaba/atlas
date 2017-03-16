package com.taobao.android.builder;

import com.android.build.gradle.LibraryPlugin;
import com.taobao.android.builder.manager.PluginManager;
import org.gradle.api.Plugin;
import org.gradle.api.Project;

/**
 * Created by wuzhong on 2017/3/15.
 *
 * @author wuzhong
 * @date 2017/03/15
 *
 */
public class AtlasLibPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {

        PluginManager.addPluginIfNot(project, LibraryPlugin.class);

        PluginManager.addPluginIfNot(project, AtlasPlugin.class);

    }

}
