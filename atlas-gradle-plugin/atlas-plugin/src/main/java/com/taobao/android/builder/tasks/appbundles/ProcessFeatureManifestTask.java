package com.taobao.android.builder.tasks.appbundles;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.artifact.BuildableArtifact;
import com.android.build.gradle.api.BaseVariantOutput;
import com.android.build.gradle.internal.api.AppVariantContext;
import com.android.build.gradle.internal.api.VariantContext;
import com.android.build.gradle.internal.api.artifact.BuildableArtifactUtil;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.core.VariantConfiguration;
import com.android.build.gradle.internal.dependency.ArtifactCollectionWithExtraArtifact;
import com.android.build.gradle.internal.dsl.CoreBuildType;
import com.android.build.gradle.internal.dsl.CoreProductFlavor;
import com.android.build.gradle.internal.matcher.FileExtensionWithPrefixPathMatcher;
import com.android.build.gradle.internal.scope.*;
import com.android.build.gradle.internal.tasks.ModuleMetadata;
import com.android.build.gradle.internal.tasks.TaskInputHelper;
import com.android.build.gradle.internal.tasks.featuresplit.FeatureSetMetadata;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.tasks.AnnotationProcessingTaskCreationAction;
import com.android.build.gradle.tasks.ManifestProcessorTask;
import com.android.build.gradle.tasks.ProcessApplicationManifest;
import com.android.builder.core.VariantType;
import com.android.builder.dexing.DexingType;
import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.ApiVersion;
import com.android.manifmerger.ManifestMerger2;
import com.android.manifmerger.ManifestProvider;
import com.android.manifmerger.MergingReport;
import com.android.manifmerger.XmlDocument;
import com.android.utils.FileUtils;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.taobao.android.builder.dependency.model.AwbBundle;
import com.taobao.android.builder.tasks.manager.FeatureBaseTaskAction;
import com.taobao.android.builder.tasks.manager.MtlBaseTaskAction;
import com.taobao.android.builder.tools.ReflectUtils;
import com.taobao.android.builder.tools.xml.XmlHelper;
import org.apache.commons.compress.compressors.FileNameUtil;
import org.apache.commons.lang.StringUtils;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.Namespace;
import org.dom4j.QName;
import org.dom4j.tree.DefaultElement;
import org.gradle.api.GradleException;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.file.FileCollection;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.*;
import org.gradle.api.tasks.Optional;
import org.gradle.internal.component.local.model.OpaqueComponentArtifactIdentifier;
import org.gradle.util.TextUtil;
import org.jetbrains.annotations.NotNull;

import javax.inject.Inject;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.Document;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.ALL;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.MODULE;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.*;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.FEATURE_APPLICATION_ID_DECLARATION;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.*;

/**
 * @ClassName ProcessFeatureManifestTask
 * @Description TODO
 * @Author zhayu.ll
 * @Date 2019-08-20 16:59
 * @Version 1.0
 */
public class ProcessFeatureManifestTask extends ManifestProcessorTask {

    private Supplier<String> minSdkVersion;
    private Supplier<String> targetSdkVersion;
    private Supplier<Integer> maxSdkVersion;
    private VariantContext variantContext;
    private VariantConfiguration<CoreBuildType, CoreProductFlavor, CoreProductFlavor>
            variantConfiguration;
    private ArtifactCollection manifests;
    private BuildableArtifact autoNamespacedManifests;
    private ArtifactCollection featureManifests;
    private FileCollection microApkManifest;
    private BuildableArtifact compatibleScreensManifest;
    private FileCollection packageManifest;
    private File mainManifest;
    private AwbBundle awbBundle;
    private BuildableArtifact apkList;
    private Supplier<EnumSet<ManifestMerger2.Invoker.Feature>> optionalFeatures;
    private OutputScope outputScope;

    // supplier to read the file above to get the feature name for the current project.
    @Nullable
    private Supplier<String> featureNameSupplier = null;

    @Inject
    public ProcessFeatureManifestTask(ObjectFactory objectFactory) {
        super(objectFactory);
    }

    @Override
    public void doFullTaskAction() throws IOException, DocumentException {
        // read the output of the compatible screen manifest.


        ModuleMetadata moduleMetadata = null;
        if (packageManifest != null && !packageManifest.isEmpty()) {
            moduleMetadata = ModuleMetadata.load(packageManifest.getSingleFile());
            boolean isDebuggable = optionalFeatures.get().contains(ManifestMerger2.Invoker.Feature.DEBUGGABLE);
            if (moduleMetadata.getDebuggable() != isDebuggable) {
                String moduleType =
                        variantConfiguration.getType().isHybrid()
                                ? "Instant App Feature"
                                : "Dynamic Feature";
                String errorMessage =
                        String.format(
                                "%1$s '%2$s' (build type '%3$s') %4$s debuggable,\n"
                                        + "and the corresponding build type in the base "
                                        + "application %5$s debuggable.\n"
                                        + "Recommendation: \n"
                                        + "   in  %6$s\n"
                                        + "   set android.buildTypes.%3$s.debuggable = %7$s",
                                moduleType,
                                getProject().getPath(),
                                variantConfiguration.getBuildType().getName(),
                                isDebuggable ? "is" : "is not",
                                moduleMetadata.getDebuggable() ? "is" : "is not",
                                getProject().getBuildFile(),
                                moduleMetadata.getDebuggable() ? "true" : "false");
                throw new InvalidUserDataException(errorMessage);
            }
        }


        for (ApkData apkData : outputScope.getApkDatas()) {

            File manifestOutputFile =
                    new File(variantContext.getScope().getGlobalScope().getIntermediatesDir(),
                            FileUtils.join(
                                    "feature_merged_manifests", apkData.getDirName(), awbBundle.getName(), SdkConstants.ANDROID_MANIFEST_XML));

            File instantRunManifestOutputFile =
                    getInstantRunManifestOutputDirectory().isPresent()
                            ? FileUtils.join(
                            getInstantRunManifestOutputDirectory().get().getAsFile(),
                            apkData.getDirName(), awbBundle.getName(),
                            SdkConstants.ANDROID_MANIFEST_XML)
                            : null;

            File metadataFeatureManifestOutputFile =
                    FileUtils.join(
                            getMetadataFeatureManifestOutputDirectory(),
                            apkData.getDirName(),
                            SdkConstants.ANDROID_MANIFEST_XML);

            File bundleManifestOutputFile =
                    FileUtils.join(
                            getBundleManifestOutputDirectory(),
                            apkData.getDirName(),
                            SdkConstants.ANDROID_MANIFEST_XML);

            File instantAppManifestOutputFile =
                    getInstantAppManifestOutputDirectory().isPresent()
                            ? FileUtils.join(
                            getInstantAppManifestOutputDirectory().get().getAsFile(),
                            apkData.getDirName(), awbBundle.getName(),
                            SdkConstants.ANDROID_MANIFEST_XML)
                            : null;

            MergingReport mergingReport =
                    getBuilder()
                            .mergeManifestsForApplication(
                                    getMainManifest(),
                                    getManifestOverlays(),
                                    computeFullProviderList(null),
                                    getNavigationFiles(),
                                    getFeatureName(),
                                    moduleMetadata == null
                                            ? getPackageOverride()
                                            : moduleMetadata.getApplicationId(),
                                    moduleMetadata == null
                                            ? apkData.getVersionCode()
                                            : Integer.parseInt(moduleMetadata.getVersionCode()),
                                    moduleMetadata == null
                                            ? apkData.getVersionName()
                                            : moduleMetadata.getVersionName(),
                                    getMinSdkVersion(),
                                    getTargetSdkVersion(),
                                    getMaxSdkVersion(),
                                    manifestOutputFile.getAbsolutePath(),
                                    // no aapt friendly merged manifest file necessary for applications.
                                    null /* aaptFriendlyManifestOutputFile */,
                                    instantRunManifestOutputFile != null
                                            ? instantRunManifestOutputFile.getAbsolutePath()
                                            : null,
                                    metadataFeatureManifestOutputFile.getAbsolutePath(),
                                    bundleManifestOutputFile.getAbsolutePath(),
                                    instantAppManifestOutputFile != null
                                            ? instantAppManifestOutputFile.getAbsolutePath()
                                            : null,
                                    ManifestMerger2.MergeType.APPLICATION,
                                    variantConfiguration.getManifestPlaceholders(),
                                    getOptionalFeatures(),
                                    getReportFile());

            if (bundleManifestOutputFile.exists()) {
                org.dom4j.Document document = XmlHelper.readXml(bundleManifestOutputFile);
                Namespace namespace = new Namespace("dist", "http://schemas.android.com/apk/distribution");
                Element appElement = document.getRootElement().element("application");
                appElement.remove(appElement.attribute("label"));
                appElement.remove(appElement.attribute("icon"));
                Element element = document.getRootElement().addNamespace("dist", "http://schemas.android.com/apk/distribution");
                DefaultElement defaultElement = new DefaultElement("module", namespace);
                defaultElement.addAttribute(new QName("onDemand", namespace), String.valueOf(true));
                defaultElement.addAttribute(new QName("title", namespace), "@string/"+variantContext.getAtlasExtension().tBuildConfig.getTitle());
                defaultElement.addElement(new QName("fusing", namespace)).addAttribute(new QName("include", namespace), String.valueOf(true));
                element.add(defaultElement);
                XmlHelper.saveDocument(document, new File(bundleManifestOutputFile.getParentFile(), "AndroidManifest-modify.xml"));
                bundleManifestOutputFile.delete();
                new File(bundleManifestOutputFile.getParentFile(), "AndroidManifest-modify.xml").renameTo(bundleManifestOutputFile);
            }

        }

    }

    @Nullable
    @Override
    @Internal
    public File getAaptFriendlyManifestOutputFile() {
        return null;
    }

    @Optional
    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public File getMainManifest() {
        return mainManifest;
    }

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public List<File> getManifestOverlays() {
        return variantConfiguration.getManifestOverlays();
    }

    @Input
    @Optional
    public String getPackageOverride() {
        return variantConfiguration.getIdOverride();
    }

    @Input
    public List<Integer> getVersionCodes() {
        return outputScope
                .getApkDatas()
                .stream()
                .map(ApkData::getVersionCode)
                .collect(Collectors.toList());
    }

    @Input
    @Optional
    public List<String> getVersionNames() {
        return outputScope
                .getApkDatas()
                .stream()
                .map(ApkData::getVersionName)
                .collect(Collectors.toList());
    }

    /**
     * Returns a serialized version of our map of key value pairs for placeholder substitution.
     * <p>
     * This serialized form is only used by gradle to compare past and present tasks to determine
     * whether a task need to be re-run or not.
     */
    @Input
    @Optional
    public String getManifestPlaceholders() {
        return serializeMap(variantConfiguration.getManifestPlaceholders());
    }

    private List<ManifestProvider> computeProviders(
            @NonNull Set<ResolvedArtifactResult> artifacts,
            @NonNull InternalArtifactType artifactType) {
        List<ManifestProvider> providers = Lists.newArrayListWithCapacity(artifacts.size());
        for (ResolvedArtifactResult artifact : artifacts) {
            File directory = artifact.getFile();
            BuildElements splitOutputs = ExistingBuildElements.from(artifactType, directory);
            if (splitOutputs.isEmpty()) {
                throw new GradleException("Could not load manifest from " + directory);
            }
            providers.add(
                    new CreationAction.ManifestProviderImpl(
                            splitOutputs.iterator().next().getOutputFile(),
                            getArtifactName(artifact)));
        }

        return providers;
    }

    /**
     * Compute the final list of providers based on the manifest file collection and the other
     * providers.
     *
     * @return the list of providers.
     */
    private List<ManifestProvider> computeFullProviderList(
            @Nullable BuildOutput compatibleScreenManifestForSplit) {
//        final Set<ResolvedArtifactResult> artifacts = manifests.getArtifacts();
        Collection<File>manifests = awbBundle.getAndroidLibraries().stream().map(new Function<AndroidLibrary, File>() {
            @Override
            public File apply(AndroidLibrary androidLibrary) {
                return androidLibrary.getManifest();
            }
        }).collect(Collectors.toList());
        List<ManifestProvider> providers = Lists.newArrayListWithCapacity(manifests.size() + 2);

        for (File manifest : manifests) {
            providers.add(
                    new CreationAction.ManifestProviderImpl(
                            (File) ((AppVariantContext)variantContext).manifestMap.get(manifest.getAbsolutePath()), manifest.getName().replace(".xml","")));
            break;
        }


        if (microApkManifest != null) {
            // this is now always present if embedding is enabled, but it doesn't mean
            // anything got embedded so the file may not run (the file path exists and is
            // returned by the FC but the file doesn't exist.
            File microManifest = microApkManifest.getSingleFile();
            if (microManifest.isFile()) {
                providers.add(
                        new ProcessApplicationManifest.CreationAction.ManifestProviderImpl(
                                microManifest, "Wear App sub-manifest"));
            }
        }

        if (compatibleScreenManifestForSplit != null) {
            providers.add(
                    new ProcessApplicationManifest.CreationAction.ManifestProviderImpl(
                            compatibleScreenManifestForSplit.getOutputFile(),
                            "Compatible-Screens sub-manifest"));

        }

        if (autoNamespacedManifests != null) {
            // We do not have resolved artifact results here, we need to find the artifact name
            // based on the file name.
            File directory = BuildableArtifactUtil.singleFile(autoNamespacedManifests);
            Preconditions.checkState(
                    directory.isDirectory(),
                    "Auto namespaced manifests should be a directory.",
                    directory);
            for (File autoNamespacedManifest : Preconditions.checkNotNull(directory.listFiles())) {
                providers.add(
                        new ProcessApplicationManifest.CreationAction.ManifestProviderImpl(
                                autoNamespacedManifest,
                                getNameFromAutoNamespacedManifest(autoNamespacedManifest)));
            }
        }

        if (featureManifests != null) {
            providers.addAll(
                    computeProviders(
                            featureManifests.getArtifacts(),
                            InternalArtifactType.METADATA_FEATURE_MANIFEST));
        }

        return providers;
    }

    @NonNull
    @Internal
    private static String getNameFromAutoNamespacedManifest(@NonNull File manifest) {
        final String manifestSuffix = "_AndroidManifest.xml";
        String fileName = manifest.getName();
        // Get the ID based on the file name generated by the [AutoNamespaceDependenciesTask]. It is
        // the sanitized name, but should be enough.
        if (!fileName.endsWith(manifestSuffix)) {
            throw new RuntimeException(
                    "Invalid auto-namespaced manifest file: " + manifest.getAbsolutePath());
        }
        return fileName.substring(0, fileName.length() - manifestSuffix.length());
    }

    // TODO put somewhere else?
    @NonNull
    @Internal
    public static String getArtifactName(@NonNull ResolvedArtifactResult artifact) {
        ComponentIdentifier id = artifact.getId().getComponentIdentifier();
        if (id instanceof ProjectComponentIdentifier) {
            return ((ProjectComponentIdentifier) id).getProjectPath();

        } else if (id instanceof ModuleComponentIdentifier) {
            ModuleComponentIdentifier mID = (ModuleComponentIdentifier) id;
            return mID.getGroup() + ":" + mID.getModule() + ":" + mID.getVersion();

        } else if (id instanceof OpaqueComponentArtifactIdentifier) {
            // this is the case for local jars.
            // FIXME: use a non internal class.
            return id.getDisplayName();
        } else if (id instanceof ArtifactCollectionWithExtraArtifact.ExtraComponentIdentifier) {
            return id.getDisplayName();
        } else {
            throw new RuntimeException("Unsupported type of ComponentIdentifier");
        }
    }

    @Input
    @Optional
    public String getMinSdkVersion() {
        return minSdkVersion.get();
    }

    @Input
    @Optional
    public String getTargetSdkVersion() {
        return targetSdkVersion.get();
    }

    @Input
    @Optional
    public Integer getMaxSdkVersion() {
        return maxSdkVersion.get();
    }

    /**
     * Not an input, see {@link #getOptionalFeaturesString()}.
     */
    @Internal
    public EnumSet<ManifestMerger2.Invoker.Feature> getOptionalFeatures() {
        return optionalFeatures.get();
    }

    /**
     * Synthetic input for {@link #getOptionalFeatures()}
     */
    @Input
    public List<String> getOptionalFeaturesString() {
        return getOptionalFeatures().stream().map(Enum::toString).collect(Collectors.toList());
    }

    @Internal
    public VariantConfiguration getVariantConfiguration() {
        return variantConfiguration;
    }

    public void setVariantConfiguration(
            VariantConfiguration<CoreBuildType, CoreProductFlavor, CoreProductFlavor> variantConfiguration) {
        this.variantConfiguration = variantConfiguration;
    }

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public FileCollection getManifests() {
        return manifests.getArtifactFiles();
    }

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public List<File> getNavigationFiles() {
        return variantConfiguration.getNavigationFiles();
    }

    @InputFiles
    @Optional
    @PathSensitive(PathSensitivity.RELATIVE)
    public FileCollection getFeatureManifests() {
        if (featureManifests == null) {
            return null;
        }
        return featureManifests.getArtifactFiles();
    }

    @InputFiles
    @Optional
    @PathSensitive(PathSensitivity.RELATIVE)
    public FileCollection getMicroApkManifest() {
        return microApkManifest;
    }

    @InputFiles
    @Optional
    @PathSensitive(PathSensitivity.RELATIVE)
    public BuildableArtifact getCompatibleScreensManifest() {
        return compatibleScreensManifest;
    }

    @InputFiles
    @Optional
    @PathSensitive(PathSensitivity.RELATIVE)
    public FileCollection getPackageManifest() {
        return packageManifest;
    }

    @Input
    @Optional
    public String getFeatureName() {
        return featureNameSupplier != null ? featureNameSupplier.get() : null;
    }

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public BuildableArtifact getApkList() {
        return apkList;
    }

    public static class CreationAction
            extends FeatureBaseTaskAction<ProcessFeatureManifestTask> {

        protected final VariantScope variantScope;
        protected final boolean isAdvancedProfilingOn = false;
        private File reportFile;
        private File featureManifestOutputDirectory;
        private File bundleManifestOutputDirectory;


        public CreationAction(AwbBundle awbBundle, VariantContext variantContext,
                              BaseVariantOutput baseVariantOutput) {
            super(awbBundle, variantContext, baseVariantOutput);
            this.variantScope = variantContext.getScope();
        }

        @Override
        public void preConfigure(@NonNull String taskName) {
            super.preConfigure(taskName);
            reportFile =
                    FileUtils.join(
                            variantScope.getGlobalScope().getOutputsDir(),
                            "feature-logs", awbBundle.getName(),
                            "manifest-merger-"
                                    + variantScope.getVariantConfiguration().getBaseName()
                                    + "-report.txt");


            featureManifestOutputDirectory = getAppVariantOutputContext().getFeatureManifestOutputDir(scope.getVariantConfiguration(), awbBundle);


            bundleManifestOutputDirectory = getAppVariantOutputContext().getBundleManifestOutputDir(scope.getVariantConfiguration(), awbBundle);
            ;

        }

        @Override
        public void configure(@NonNull ProcessFeatureManifestTask task) {
            super.configure(task);
            task.awbBundle = awbBundle;
            task.mainManifest = awbBundle.getManifest();
            final BaseVariantData variantData = variantScope.getVariantData();
            final GradleVariantConfiguration config = variantData.getVariantConfiguration();
            GlobalScope globalScope = variantScope.getGlobalScope();

            task.outputScope = variantData.getOutputScope();

            task.setVariantConfiguration(config);
            task.variantContext = variantContext;

            Project project = globalScope.getProject();

            task.manifests = variantScope.getArtifactCollection(RUNTIME_CLASSPATH, ALL, MANIFEST);

            // optional manifest files too.
            if (variantScope.getTaskContainer().getMicroApkTask() != null
                    && config.getBuildType().isEmbedMicroApp()) {
                task.microApkManifest = project.files(variantScope.getMicroApkManifestFile());
            }
            BuildArtifactsHolder artifacts = variantScope.getArtifacts();
            task.compatibleScreensManifest =
                    artifacts.getFinalArtifactFiles(
                            InternalArtifactType.COMPATIBLE_SCREEN_MANIFEST);

            task.minSdkVersion =
                    TaskInputHelper.memoize(
                            () -> {
                                ApiVersion minSdk = config.getMergedFlavor().getMinSdkVersion();
                                return minSdk == null ? null : minSdk.getApiString();
                            });

            task.targetSdkVersion =
                    TaskInputHelper.memoize(
                            () -> {
                                ApiVersion targetSdk =
                                        config.getMergedFlavor().getTargetSdkVersion();
                                return targetSdk == null ? null : targetSdk.getApiString();
                            });

            task.maxSdkVersion =
                    TaskInputHelper.memoize(config.getMergedFlavor()::getMaxSdkVersion);

            task.setMetadataFeatureManifestOutputDirectory(featureManifestOutputDirectory);
            task.setBundleManifestOutputDirectory(bundleManifestOutputDirectory);
            task.setReportFile(reportFile);
            task.optionalFeatures =
                    TaskInputHelper.memoize(
                            () -> getOptionalFeatures(variantScope, isAdvancedProfilingOn));

            task.featureNameSupplier = () -> awbBundle.getFeatureName();

            task.packageManifest = variantContext.getProject().files(new File(variantContext.getScope().getGlobalScope().getIntermediatesDir(),
                    "application-meta/application-metadata.json"));

//            }
        }

        @NotNull
        @Override
        public String getName() {
            return scope.getTaskName("processFeature" + awbBundle.getFeatureName(), "manifest");
        }

        @NotNull
        @Override
        public Class<ProcessFeatureManifestTask> getType() {
            return ProcessFeatureManifestTask.class;
        }

        /**
         * Implementation of AndroidBundle that only contains a manifest.
         * <p>
         * This is used to pass to the merger manifest snippet that needs to be added during
         * merge.
         */
        public static class ManifestProviderImpl implements ManifestProvider {

            @NonNull
            private final File manifest;

            @NonNull
            private final String name;

            public ManifestProviderImpl(@NonNull File manifest, @NonNull String name) {
                this.manifest = manifest;
                this.name = name;
            }

            @NonNull
            @Override
            public File getManifest() {
                return manifest;
            }

            @NonNull
            @Override
            public String getName() {
                return name;
            }
        }
    }

    private static EnumSet<ManifestMerger2.Invoker.Feature> getOptionalFeatures(
            VariantScope variantScope, boolean isAdvancedProfilingOn) {
        List<ManifestMerger2.Invoker.Feature> features = new ArrayList<>();


        features.add(ManifestMerger2.Invoker.Feature.ADD_FEATURE_SPLIT_ATTRIBUTE);
        features.add(ManifestMerger2.Invoker.Feature.CREATE_FEATURE_MANIFEST);
        features.add(ManifestMerger2.Invoker.Feature.STRIP_MIN_SDK_FROM_FEATURE_MANIFEST);

        features.add(ManifestMerger2.Invoker.Feature.ADD_INSTANT_APP_MANIFEST);

        features.add(ManifestMerger2.Invoker.Feature.CREATE_BUNDLETOOL_MANIFEST);


        // create it for dynamic-features and base modules that are not hybrid base features.
        // hybrid features already contain the split name.
        features.add(ManifestMerger2.Invoker.Feature.ADD_SPLIT_NAME_TO_BUNDLETOOL_MANIFEST);

        if (variantScope.getVariantConfiguration().getBuildType().isDebuggable()) {
            features.add(ManifestMerger2.Invoker.Feature.DEBUGGABLE);
            if (isAdvancedProfilingOn) {
                features.add(ManifestMerger2.Invoker.Feature.ADVANCED_PROFILING);
            }
        }

        if (variantScope.getVariantConfiguration().getDexingType() == DexingType.LEGACY_MULTIDEX) {
            if (variantScope
                    .getGlobalScope()
                    .getProjectOptions()
                    .get(BooleanOption.USE_ANDROID_X)) {
                features.add(ManifestMerger2.Invoker.Feature.ADD_ANDROIDX_MULTIDEX_APPLICATION_IF_NO_NAME);
            } else {

                //we do not need multidex
//                features.add(ManifestMerger2.Invoker.Feature.ADD_SUPPORT_MULTIDEX_APPLICATION_IF_NO_NAME);
            }
        }

        if (variantScope
                .getGlobalScope()
                .getProjectOptions()
                .get(BooleanOption.ENFORCE_UNIQUE_PACKAGE_NAMES)) {
            features.add(ManifestMerger2.Invoker.Feature.ENFORCE_UNIQUE_PACKAGE_NAME);
        }

        return features.isEmpty() ? EnumSet.noneOf(ManifestMerger2.Invoker.Feature.class) : EnumSet.copyOf(features);
    }
}
