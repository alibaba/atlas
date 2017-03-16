package com.taobao.android.builder.manager;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.PluginContainer;

/**
 * Created by wuzhong on 2017/3/15.
 *
 * @author wuzhong
 * @date 2017/03/15
 */
public class PluginManager {


    public static void addPluginIfNot(Project project, Class<? extends Plugin> pluginClazz){

        PluginContainer pluginManager = project.getPlugins();

        if (pluginManager.hasPlugin(pluginClazz)){
            return;
        }

        pluginManager.apply(pluginClazz);


    }


}
