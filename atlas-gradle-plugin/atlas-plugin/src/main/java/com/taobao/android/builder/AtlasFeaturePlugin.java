package com.taobao.android.builder;
import com.android.build.gradle.*;
import org.gradle.api.Project;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;

/**
 * AtlasFeaturePlugin
 *
 * @author zhayu.ll
 * @date 18/1/3
 * @time 下午7:04
 * @description  
 */
public class AtlasFeaturePlugin extends FeaturePlugin {

    public AtlasFeaturePlugin(Instantiator instantiator, ToolingModelBuilderRegistry registry) {
        super(instantiator, registry);
    }

    @Override
    public void apply(Project project) {
        super.apply(project);
    }

}
