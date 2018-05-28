package com.taobao.android.builder;

import com.android.build.gradle.AndroidGradleOptions;
import com.android.build.gradle.internal.AtlasDependencyManager;
import com.android.build.gradle.internal.DependencyManager;
import com.taobao.android.builder.extension.AtlasExtension;
import com.taobao.android.builder.manager.AtlasConfigurationHelper;
import com.taobao.android.builder.manager.Version;
import com.taobao.android.builder.tools.PathUtil;
import com.taobao.android.builder.tools.PluginTypeUtils;
import com.taobao.android.builder.tools.log.LogOutputListener;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.StopExecutionException;
import org.gradle.internal.reflect.Instantiator;

import javax.inject.Inject;
import java.util.regex.Pattern;

/**
 * @author lilong
 * @create 2017-08-23 When in the morning
 */

public abstract class AtlasBasePlugin implements Plugin<Project> {

    public static final String BUNDLE_COMPILE = "bundleCompile";
    public static final String PROVIDED_COMPILE = "providedCompile";

    protected Project project;
    public static final Pattern PLUGIN_ACCEPTABLE_VERSIONS = Pattern.compile("2\\.[3-9].*");
    public static final String PLUGIN_MIN_VERSIONS = "2.3.0";

    public static final Pattern JDK_VERSIONS = Pattern.compile("1\\.[8-9].*");
    public static final String JDK_MIN_VERSIONS = "1.8";

    protected Instantiator instantiator;

    protected AtlasExtension atlasExtension;

    public static String creator = "AtlasPlugin" + Version.ANDROID_GRADLE_PLUGIN_VERSION;

    protected AtlasConfigurationHelper atlasConfigurationHelper;

    @Inject
    public AtlasBasePlugin(Instantiator instantiator) {

        this.instantiator = instantiator;

    }
    @Override
    public void apply(Project project) {
        this.project = project;
        LogOutputListener.addListener(project);

        checkPluginSetup();

        atlasConfigurationHelper = getConfigurationHelper(project);
        atlasExtension = atlasConfigurationHelper.createExtendsion();

    }

    protected abstract AtlasConfigurationHelper getConfigurationHelper(Project project);

    /**
     * Determine if the plug-in's dependency configuration is correct
     */
    private void checkPluginSetup() {

        if (!PluginTypeUtils.usedGooglePlugin(project)) {
            throw new StopExecutionException("Atlas plugin need android plugin to run!");
        }

        String androidVersion = com.android.builder.Version.ANDROID_GRADLE_PLUGIN_VERSION;
        //Determine the Android pluginThe version of
        if (!PLUGIN_ACCEPTABLE_VERSIONS.matcher(androidVersion).matches()) {
            String errorMessage = String.format("Android Gradle plugin version %s is required. Current version is %s. ",
                    PLUGIN_MIN_VERSIONS, androidVersion);
            throw new StopExecutionException(errorMessage);
        }

        //check jdk version
        String jdkVersion = System.getProperty("java.version");
        if (!JDK_VERSIONS.matcher(jdkVersion).matches()) {
            String errorMessage = String.format("JDK version %s is required. Current version is %s. ",
                    JDK_MIN_VERSIONS, jdkVersion);
            throw new StopExecutionException(errorMessage);
        }

        if (AndroidGradleOptions.isBuildCacheEnabled(project)) {
            //project.setProperty(AndroidGradleOptions.PROPERTY_ENABLE_BUILD_CACHE, false);
            String errorMessage = "android.enableBuildCache is disabled by atlas, we will open it later, "
                    + "\r\n please `add android.enableBuildCache false` to gradle.properties";
            //throw new StopExecutionException(errorMessage);
        }

        if(!PathUtil.getJarFile(DependencyManager.class).getAbsolutePath().equals(PathUtil.getJarFile(AtlasDependencyManager.class).getAbsolutePath())){
            throw new StopExecutionException("please remove the google plugin `classpath 'com.android.tools.build:gradle:xxx'` in buildscript dependencies \n"
                    + "it will be auto include by atlasplugin");
        }

    }

}
