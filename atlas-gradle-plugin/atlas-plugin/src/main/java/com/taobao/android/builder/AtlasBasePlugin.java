package com.taobao.android.builder;

import com.android.tools.lint.gradle.api.ToolingRegistryProvider;
import com.taobao.android.builder.extension.AtlasExtension;
import com.taobao.android.builder.manager.AtlasConfigurationHelper;
import com.taobao.android.builder.manager.Version;
import com.taobao.android.builder.tools.PluginTypeUtils;
import com.taobao.android.builder.tools.log.LogOutputListener;
import com.taobao.android.builder.tools.process.ApkProcessor;
import org.gradle.BuildResult;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.ProjectEvaluationListener;
import org.gradle.api.ProjectState;
import org.gradle.api.tasks.StopExecutionException;
import org.gradle.internal.reflect.DirectInstantiator;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;

import javax.inject.Inject;
import java.util.regex.Pattern;

/**
 * @author lilong
 * @create 2017-08-23 When in the morning
 */

public abstract class AtlasBasePlugin implements Plugin<Project>, ToolingRegistryProvider, FakeProjectEvaluationListener {


    protected ToolingModelBuilderRegistry toolingModelBuilderRegistry;

    protected AtlasConfigurationHelper atlasConfigurationHelper;

    protected AtlasExtension atlasExtension;

    protected Project project;

    @Inject
    public AtlasBasePlugin(ToolingModelBuilderRegistry toolingModelBuilderRegistry) {

        this.toolingModelBuilderRegistry = toolingModelBuilderRegistry;

    }
    @Override
    public void apply(Project project) {
        this.project = project;
        LogOutputListener.addListener(project);

        addBuildAction();

        atlasConfigurationHelper = getConfigurationHelper(project);

        AtlasBuildContext.atlasConfigurationHelper = atlasConfigurationHelper;

        atlasExtension =  atlasConfigurationHelper.createExtendsion();

        atlasConfigurationHelper.autoSetBuildTypes(atlasExtension);

        atlasConfigurationHelper.createLibCompenents();

        project.afterEvaluate(new Action<Project>() {
            @Override
            public void execute(Project project) {
                afterEvaluate(project);
            }
        });

    }

    protected abstract AtlasConfigurationHelper getConfigurationHelper(Project project);

    /**
     * Determine if the plug-in's dependency configuration is correct
     */
    private void addBuildAction() {


        project.getGradle().buildFinished(buildResult -> AtlasBuildContext.reset());

    }

    @Override
    public void afterEvaluate(Project project) {
        try {
            atlasConfigurationHelper.createBuilderAfterEvaluate();
        } catch (Exception e) {
            throw new GradleException("update builder failed", e);
        }

        atlasConfigurationHelper.registAtlasStreams();


        atlasConfigurationHelper.configDependencies(atlasExtension.getTBuildConfig().getFeatureConfigFile());


        //3. update extension
        atlasConfigurationHelper.updateExtensionAfterEvaluate();

        //4. Set up the android builder


        //5. Configuration tasks
        atlasConfigurationHelper.configTasksAfterEvaluate();
    }
}
