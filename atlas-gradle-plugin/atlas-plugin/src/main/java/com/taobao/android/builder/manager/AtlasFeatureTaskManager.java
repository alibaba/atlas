package com.taobao.android.builder.manager;

import com.android.build.api.transform.QualifiedContent;
import com.android.build.gradle.BaseExtension;
import com.android.build.gradle.FeatureExtension;
import com.android.build.gradle.api.BaseVariantOutput;
import com.android.build.gradle.api.FeatureVariant;
import com.android.build.gradle.api.LibraryVariant;
import com.android.build.gradle.api.LibraryVariantOutput;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.internal.TaskContainerAdaptor;
import com.android.build.gradle.internal.api.FeatureVariantContext;
import com.android.build.gradle.internal.api.FeatureVariantImpl;
import com.android.build.gradle.internal.api.LibVariantContext;
import com.android.build.gradle.internal.api.LibraryVariantImpl;
import com.android.build.gradle.internal.dependency.FilteredArtifactCollection;
import com.android.build.gradle.internal.pipeline.StreamBasedTask;
import com.android.build.gradle.internal.pipeline.TransformStream;
import com.android.build.gradle.internal.pipeline.TransformTask;
import com.android.build.gradle.internal.publishing.AndroidArtifacts;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.transforms.FixStackFramesTransform;
import com.android.build.gradle.tasks.GenerateBuildConfig;
import com.android.build.gradle.tasks.MergeManifests;
import com.android.build.gradle.tasks.ProcessAndroidResources;
import com.android.builder.core.AtlasBuilder;
import com.android.utils.ILogger;
import com.google.common.collect.ImmutableList;
import com.taobao.android.builder.extension.AtlasExtension;
import com.taobao.android.builder.extension.TBuildType;
import com.taobao.android.builder.tasks.app.GenerateAtlasSourceTask;
import com.taobao.android.builder.tasks.app.manifest.StandardizeLibManifestTask;
import com.taobao.android.builder.tasks.feature.FeatureLibManifestTask;
import com.taobao.android.builder.tasks.feature.PrePareFeatureTask;
import com.taobao.android.builder.tasks.library.AwbGenerator;
import com.taobao.android.builder.tasks.library.JarExtractTask;
import com.taobao.android.builder.tasks.manager.MtlTaskContext;
import com.taobao.android.builder.tasks.manager.MtlTaskInjector;
import com.taobao.android.builder.tasks.manager.transform.TransformManager;
import com.taobao.android.builder.tools.ReflectUtils;
import com.taobao.android.builder.tools.ideaplugin.AwoPropHandler;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.reflect.FieldUtils;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.bundling.Zip;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

/**
 * AtlasFeatureTaskManager
 *
 * @author zhayu.ll
 * @date 18/1/3
 * @time 下午8:15
 * @description  
 */
public class AtlasFeatureTaskManager extends AtlasBaseTaskManager {

    private FeatureExtension featureExtension;

    private ILogger logger = LoggerWrapper.getLogger(AtlasFeatureTaskManager.class);

    public AtlasFeatureTaskManager(AtlasBuilder androidBuilder, BaseExtension androidExtension, Project project, AtlasExtension atlasExtension) {
        super(androidBuilder, androidExtension, project, atlasExtension);
        this.featureExtension = (FeatureExtension) androidExtension;
    }

    @Override
    public void runTask() {

        if (featureExtension.getBaseFeature()) {
            return;
        }


        featureExtension.getFeatureVariants().forEach(featureVariant -> {

            FeatureVariantContext featureVariantContext = new FeatureVariantContext((FeatureVariantImpl) featureVariant,
                    project, atlasExtension, featureExtension);
            ArtifactCollection allArtifacts = featureVariantContext.getScope().getArtifactCollection(AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH, AndroidArtifacts.ArtifactScope.EXTERNAL, AndroidArtifacts.ArtifactType.CLASSES);
            ArtifactCollection artifacts = featureVariantContext.getScope().getArtifactCollection(AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH, AndroidArtifacts.ArtifactScope.ALL, AndroidArtifacts.ArtifactType.FEATURE_TRANSITIVE_DEPS);
            ArtifactCollection filterArtifacts =
                    new FilteredArtifactCollection(
                            featureVariantContext.getProject(),
                            allArtifacts,
                            artifacts.getArtifactFiles());

            List<TransformTask> transformTasks = TransformManager.findTransformTaskByTransformType(featureVariantContext, FixStackFramesTransform.class);
            if (transformTasks != null) {
                for (TransformTask transformTask : transformTasks) {
                    try {
                        Field field = StreamBasedTask.class.getDeclaredField("consumedInputStreams");
                        Field field1 = StreamBasedTask.class.getDeclaredField("referencedInputStreams");
                        field1.setAccessible(true);
                        field.setAccessible(true);
                        Collection<TransformStream> consumedInputStreams = (Collection<TransformStream>) field.get(transformTask);
                        Collection<TransformStream> referencedInputStreams = (Collection<TransformStream>)field1.get(transformTask);
                        for (TransformStream stream : consumedInputStreams) {
                            if (stream.getContentTypes().contains(QualifiedContent.DefaultContentType.CLASSES) && stream.getScopes().contains(QualifiedContent.Scope.EXTERNAL_LIBRARIES)) {
                                ReflectUtils.updateField(stream, "fileCollection", filterArtifacts.getArtifactFiles());
                                ReflectUtils.updateField(stream, "artifactCollection", filterArtifacts);

                                break;
                            }

                        }

                        for (TransformStream transformStream:referencedInputStreams){
                            if (transformStream.getContentTypes().contains(QualifiedContent.DefaultContentType.CLASSES)&&transformStream.getScopes().contains(QualifiedContent.Scope.PROVIDED_ONLY)){
                                ReflectUtils.updateField(transformStream, "fileCollection", project.files());
//                                ReflectUtils.updateField(transformStream, "artifactCollection", filterArtifacts);
                            }
                        }
                    } catch (Exception e) {

                    }

                }
            }
            featureVariantContext.getScope().getProcessResourcesTask().get(new TaskContainerAdaptor(featureVariantContext.getProject().getTasks())).setEnableAapt2(true);

        });

        featureExtension.getLibraryVariants().forEach(libraryVariant -> {

            LibVariantContext libVariantContext = new LibVariantContext((LibraryVariantImpl) libraryVariant,
                    project,
                    atlasExtension,
                    featureExtension);

            TBuildType tBuildType = libVariantContext.getBuildType();
            if (null != tBuildType) {
                try {
                    new AwoPropHandler().process(tBuildType,
                            atlasExtension.getBundleConfig());
                } catch (Exception e) {
                    throw new GradleException("process awo exception", e);
                }
            }

            AwbGenerator awbGenerator = new AwbGenerator(atlasExtension);

            Collection<BaseVariantOutput> list = libVariantContext.getBaseVariant().getOutputs();

            if (null != list) {

                for (BaseVariantOutput libVariantOutputData : list) {

                    Zip zipTask = ((LibraryVariantOutput) (libVariantOutputData)).getPackageLibrary();

                    if (atlasExtension.getBundleConfig().isJarEnabled()) {
                        new JarExtractTask().generateJarArtifict(zipTask);
                    }

                    //Build the awb and extension
//                    if (atlasExtension.getBundleConfig().isAwbBundle()) {
                    awbGenerator.generateAwbArtifict(zipTask, libVariantContext);
//                    }

                    if (null != tBuildType && (StringUtils.isNotEmpty(tBuildType.getBaseApDependency())
                            || null != tBuildType.getBaseApFile()) &&

                            libraryVariant.getName().equals("debug")) {

                        atlasExtension.getTBuildConfig().setUseCustomAapt(true);

                        libVariantContext.setBundleTask(zipTask);

                        try {

                            libVariantContext.setAwbBundle(awbGenerator.createAwbBundle(libVariantContext));
                        } catch (IOException e) {
                            throw new GradleException("set awb bundle error");
                        }

                    }

                }


            }

        });


    }

}
