package com.taobao.android.builder.tasks.feature;

import com.android.build.gradle.api.BaseVariantOutput;
import com.android.build.gradle.internal.api.FeatureVariantContext;
import com.android.build.gradle.internal.api.VariantContext;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.dsl.CoreBuildType;
import com.android.build.gradle.internal.dsl.CoreProductFlavor;
import com.android.build.gradle.internal.scope.BuildOutput;
import com.android.build.gradle.internal.scope.BuildOutputs;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.DefaultAndroidTask;
import com.android.build.gradle.tasks.MergeManifests;
import com.android.builder.core.VariantConfiguration;
import com.android.manifmerger.ManifestProvider;
import com.google.common.collect.Lists;
import com.taobao.android.builder.AtlasBuildContext;
import com.taobao.android.builder.tasks.manager.MtlBaseTaskAction;
import com.taobao.android.builder.tools.manifest.ManifestFileUtils;
import com.taobao.android.builder.tools.manifest.ManifestInfo;
import org.dom4j.DocumentException;
import org.gradle.api.GradleException;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.ALL;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.MODULE;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.MANIFEST;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.METADATA_APP_ID_DECLARATION;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.METADATA_FEATURE_MANIFEST;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.METADATA_VALUES;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH;
import static com.taobao.android.builder.tasks.app.manifest.StandardizeLibManifestTask.getManifestFileObject;

/**
 * FeatureLibManifestTask
 *
 * @author zhayu.ll
 * @date 18/1/4
 * @time 下午4:28
 * @description  
 */
public class FeatureLibManifestTask extends DefaultAndroidTask{


    private ArtifactCollection manifests;
    private FileCollection packageManifest;
    private ArtifactCollection featureManifests;
    private FeatureVariantContext featureVariantContext;
    private File mainManifestFile;
    private VariantConfiguration variantConfiguration;

    @Internal
    public VariantConfiguration getVariantConfiguration() {
        return variantConfiguration;
    }

    public void setVariantConfiguration(
            VariantConfiguration<CoreBuildType, CoreProductFlavor, CoreProductFlavor> variantConfiguration) {
        this.variantConfiguration = variantConfiguration;
    }


    @TaskAction
    public void taskAction() throws DocumentException, IOException {

        mainManifestFile = variantConfiguration.getMainManifest();
        ManifestInfo mainManifestFileObject = getManifestFileObject(mainManifestFile);
        final Set<ResolvedArtifactResult> artifacts = manifests.getArtifacts();
        List<ManifestProvider> providers = Lists.newArrayListWithCapacity(artifacts.size() + 2);
        for (ResolvedArtifactResult artifact : artifacts) {
            File manifestFile = artifact.getFile();
            File modifyManifest = featureVariantContext.getModifiedManifest(artifact);
            ManifestFileUtils.updatePreProcessManifestFile(modifyManifest, manifestFile, mainManifestFileObject,
                    true, featureVariantContext.getAtlasExtension()
                            .getTBuildConfig().isIncremental());
            providers.add(new MergeManifests.ConfigAction.ManifestProviderImpl(
                    modifyManifest,
                    MergeManifests.getArtifactName(artifact)));
        }


        if (featureManifests != null) {
            final Set<ResolvedArtifactResult> featureArtifacts = featureManifests.getArtifacts();
            for (ResolvedArtifactResult artifact : featureArtifacts) {
                File directory = artifact.getFile();
                File modifyManifest = featureVariantContext.getModifiedManifest(artifact);
                Collection<BuildOutput> splitOutputs =
                        BuildOutputs.load(VariantScope.TaskOutputType.MERGED_MANIFESTS, directory);
                if (splitOutputs.isEmpty()) {
                    throw new GradleException("Could not load manifest from " + directory);
                }
                ManifestFileUtils.updatePreProcessManifestFile(modifyManifest, splitOutputs.iterator().next().getOutputFile(), mainManifestFileObject,
                        true, featureVariantContext.getAtlasExtension()
                                .getTBuildConfig().isIncremental());

                providers.add(
                        new MergeManifests.ConfigAction.ManifestProviderImpl(
                                modifyManifest,
                                MergeManifests.getArtifactName(artifact)));
            }
        }
        AtlasBuildContext.androidBuilderMap.get(getProject()).manifestProviders = providers;

    }








    public static class ConfigAction extends MtlBaseTaskAction<FeatureLibManifestTask>{

        private FeatureVariantContext featureVariantContext;

        public ConfigAction(VariantContext variantContext, BaseVariantOutput baseVariantOutput) {
            super(variantContext, baseVariantOutput);
            this.featureVariantContext = (FeatureVariantContext) variantContext;
        }

        @Override
        public void execute(FeatureLibManifestTask task) {

            task.setVariantName(variantContext.getVariantName());

            task.featureVariantContext = featureVariantContext;
            task.manifests =
                    scope.getArtifactCollection(RUNTIME_CLASSPATH, ALL, MANIFEST);
            task.packageManifest =
                    scope.getArtifactFileCollection(
                            METADATA_VALUES, MODULE, METADATA_APP_ID_DECLARATION);

            final GradleVariantConfiguration config = scope.getVariantData().getVariantConfiguration();

            task.setVariantConfiguration(config);
            task.featureManifests =
                    scope.getArtifactCollection(
                            METADATA_VALUES, MODULE, METADATA_FEATURE_MANIFEST);
            super.execute(task);
        }

        @Override
        public String getName() {
            return variantContext.getScope().getTaskName("feature","libManifest");
        }

        @Override
        public Class<FeatureLibManifestTask> getType() {
            return FeatureLibManifestTask.class;
        }
    }


}
