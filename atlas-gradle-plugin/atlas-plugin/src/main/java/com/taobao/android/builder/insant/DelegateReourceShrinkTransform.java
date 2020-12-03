package com.taobao.android.builder.insant;


import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.artifact.BuildableArtifact;
import com.android.build.api.transform.*;
import com.android.build.gradle.internal.api.AppVariantContext;
import com.android.build.gradle.internal.api.artifact.BuildableArtifactUtil;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.dsl.AaptOptions;
import com.android.build.gradle.internal.pipeline.ExtendedContentType;
import com.android.build.gradle.internal.pipeline.IntermediateFolderUtils;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.scope.*;
import com.android.build.gradle.internal.tasks.Workers;
import com.android.build.gradle.internal.transforms.ShrinkResourcesTransform;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.MultiOutputPolicy;
import com.android.build.gradle.tasks.PackageAndroidArtifact;
import com.android.build.gradle.tasks.ResourceUsageAnalyzer;
import com.android.builder.core.VariantType;
import com.android.ide.common.workers.WorkerExecutorFacade;
import com.android.utils.FileUtils;
import com.google.common.base.Joiner;
import com.google.common.collect.*;
import com.taobao.android.builder.AtlasBuildContext;
import com.taobao.android.builder.tasks.manager.transform.MtlInjectTransform;
import com.taobao.android.builder.tools.ReflectUtils;
import org.gradle.api.file.Directory;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.provider.Provider;
import org.xml.sax.SAXException;

import javax.inject.Inject;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * FakeProguardTransform
 *
 * @author zhayu.ll
 * @date 18/10/18
 */
public class DelegateReourceShrinkTransform extends MtlInjectTransform {

    private static boolean ourWarned = true; // Logging disabled until shrinking is on by default.

    @NonNull
    private final BaseVariantData variantData;

    @NonNull private final Logger logger;

    @NonNull private final BuildableArtifact sourceDir;
    @NonNull private final BuildableArtifact resourceDir;
    @Nullable
    private BuildableArtifact mappingFileSrc;
    @NonNull private final Provider<Directory> mergedManifests;
    @NonNull private final BuildableArtifact uncompressedResources;

    @NonNull private final AaptOptions aaptOptions;
    @NonNull private final VariantType variantType;
    private final boolean isDebuggableBuildType;
    @NonNull private final MultiOutputPolicy multiOutputPolicy;

    @NonNull private final File compressedResources;

    public DelegateReourceShrinkTransform(AppVariantContext appVariantContext, ApkData apkData) {
        super(appVariantContext, apkData);
        VariantScope variantScope = appVariantContext.getVariantData().getScope();
        GlobalScope globalScope = variantScope.getGlobalScope();
        GradleVariantConfiguration variantConfig = appVariantContext.getVariantData().getVariantConfiguration();
        this.variantData = appVariantContext.getVariantData();
        this.logger = appVariantContext.getProject().getLogger();
        BuildArtifactsHolder artifacts = variantScope.getArtifacts();
        this.sourceDir =
                artifacts.getFinalArtifactFiles(
                        InternalArtifactType.NOT_NAMESPACED_R_CLASS_SOURCES);
        this.resourceDir = variantScope.getArtifacts().getFinalArtifactFiles(
                InternalArtifactType.MERGED_NOT_COMPILED_RES);
        this.mergedManifests = artifacts.getFinalProduct(InternalArtifactType.MERGED_MANIFESTS);
        this.uncompressedResources = scope.getArtifacts()
                .getFinalArtifactFiles(InternalArtifactType.PROCESSED_RES);

        this.aaptOptions = globalScope.getExtension().getAaptOptions();
        this.variantType = variantData.getType();
        this.isDebuggableBuildType = variantConfig.getBuildType().isDebuggable();
        this.multiOutputPolicy = variantData.getMultiOutputPolicy();

        this.compressedResources =  FileUtils.join(
                globalScope.getIntermediatesDir(),
                "res_stripped",
                scope.getVariantConfiguration().getDirName());;
    }

    @Override
    public String getName() {
        return "delegateReourceShrinkTransform";
    }

    @Override
    public boolean isCacheable() {
        return false;
    }

    @Override
    public Set<QualifiedContent.ContentType> getInputTypes() {
        return ImmutableSet.of(ExtendedContentType.DEX, QualifiedContent.DefaultContentType.CLASSES);
    }

    @NonNull
    @Override
    public Set<QualifiedContent.Scope> getScopes() {
        return TransformManager.SCOPE_FULL_PROJECT;
    }

    @NonNull
    @Override
    public Set<QualifiedContent.Scope> getReferencedScopes() {
        return TransformManager.SCOPE_FULL_PROJECT;
    }

    @NonNull
    @Override
    public Collection<SecondaryFile> getSecondaryFiles() {
        Collection<SecondaryFile> secondaryFiles = Lists.newLinkedList();

        // FIXME use Task output to get FileCollection for sourceDir/resourceDir
        secondaryFiles.add(SecondaryFile.nonIncremental(sourceDir));
        secondaryFiles.add(SecondaryFile.nonIncremental(resourceDir));

        if (mappingFileSrc != null) {
            secondaryFiles.add(SecondaryFile.nonIncremental(mappingFileSrc));
        }

        secondaryFiles.add(SecondaryFile.nonIncremental(mergedManifests.get().getAsFile()));
        secondaryFiles.add(SecondaryFile.nonIncremental(uncompressedResources));

        return secondaryFiles;
    }

    @NonNull
    @Override
    public Map<String, Object> getParameterInputs() {
        Map<String, Object> params = Maps.newHashMapWithExpectedSize(7);
        params.put(
                "aaptOptions",
                Joiner.on(";")
                        .join(
                                aaptOptions.getIgnoreAssetsPattern() != null
                                        ? aaptOptions.getIgnoreAssetsPattern()
                                        : "",
                                aaptOptions.getNoCompress() != null
                                        ? Joiner.on(":").join(aaptOptions.getNoCompress())
                                        : "",
                                aaptOptions.getFailOnMissingConfigEntry(),
                                aaptOptions.getAdditionalParameters() != null
                                        ? Joiner.on(":").join(aaptOptions.getAdditionalParameters())
                                        : "",
                                aaptOptions.getCruncherProcesses()));
        params.put("variantType", variantType.getName());
        params.put("isDebuggableBuildType", isDebuggableBuildType);
        params.put("splitHandlingPolicy", multiOutputPolicy);

        return params;
    }

    @NonNull
    @Override
    public Collection<File> getSecondaryDirectoryOutputs() {
        return ImmutableList.of(compressedResources);
    }

    @Override
    public boolean isIncremental() {
        return false;
    }

    @Override
    public void transform(TransformInvocation invocation) throws TransformException, InterruptedException, IOException {
        Collection<TransformInput> inputs = invocation.getInputs();
        List<File> classes = new ArrayList<>();

        File folder = (File) ReflectUtils.getField(invocation.getInputs().iterator().next(), "optionalRootLocation");
        classes.addAll(org.apache.commons.io.FileUtils.listFiles(folder,new String[]{"dex"},true));

        BuildElements mergedManifestsOutputs =
                ExistingBuildElements.from(InternalArtifactType.MERGED_MANIFESTS, mergedManifests);

        mappingFileSrc =
                appVariantContext.getScope().getArtifacts().hasArtifact(InternalArtifactType.APK_MAPPING)
                        ? appVariantContext.getScope()
                        .getArtifacts()
                        .getFinalArtifactFiles(InternalArtifactType.APK_MAPPING)
                        : null;
        try (WorkerExecutorFacade workers =
                     Workers.INSTANCE.getWorker(invocation.getContext().getWorkerExecutor())) {
            ExistingBuildElements.from(InternalArtifactType.PROCESSED_RES, uncompressedResources)
                    .transform(
                            workers,
                            DelegateReourceShrinkTransform.SplitterRunnable.class,
                            (ApkData apkInfo, File buildInput) ->
                                    new DelegateReourceShrinkTransform.SplitterParams(
                                            apkInfo,
                                            buildInput,
                                            mergedManifestsOutputs,
                                            classes,
                                            this))
                    .into(InternalArtifactType.SHRUNK_PROCESSED_RES, compressedResources);
        }
        appVariantContext.getScope().getArtifacts()
                .appendArtifact(
                        InternalArtifactType.SHRUNK_PROCESSED_RES,
                        ImmutableList.of(compressedResources),
                        getName());

        PackageAndroidArtifact packageAndroidArtifact = appVariantContext.getVariantData().getTaskContainer().getPackageAndroidTask().get();
        ReflectUtils.updateField(packageAndroidArtifact,"resourceFiles",appVariantContext.getScope().getArtifacts().getFinalArtifactFiles(InternalArtifactType.SHRUNK_PROCESSED_RES));
        ReflectUtils.updateField(packageAndroidArtifact,"taskInputType",InternalArtifactType.SHRUNK_PROCESSED_RES);

    }

    private static class SplitterRunnable extends BuildElementsTransformRunnable {

        @Inject
        public SplitterRunnable(@NonNull DelegateReourceShrinkTransform.SplitterParams params) {
            super(params);
        }

        @Override
        public void run() {
            DelegateReourceShrinkTransform.SplitterParams params = (DelegateReourceShrinkTransform.SplitterParams) getParams();
            File reportFile = null;
            if (params.mappingFile != null) {
                File logDir = params.mappingFile.getParentFile();
                if (logDir != null) {
                    reportFile = new File(logDir, "resources.txt");
                }
            }

            FileUtils.mkdirs(params.compressedResourceFile.getParentFile());

            if (params.mergedManifest == null) {
                try {
                    FileUtils.copyFile(
                            params.uncompressedResourceFile, params.compressedResourceFile);
                } catch (IOException e) {
                    Logging.getLogger(ShrinkResourcesTransform.class)
                            .error("Failed to copy uncompressed resource file :", e);
                    throw new RuntimeException("Failed to copy uncompressed resource file", e);
                }

                return;
            }

            // Analyze resources and usages and strip out unused
            ResourceUsageAnalyzer analyzer =
                    new ResourceUsageAnalyzer(
                            params.sourceDir,
                            params.classes,
                            params.mergedManifest.getOutputFile(),
                            params.mappingFile,
                            params.resourceDir,
                            reportFile,
                            ResourceUsageAnalyzer.ApkFormat.BINARY);
            try {
                analyzer.setVerbose(params.isInfoLoggingEnabled);
                analyzer.setDebug(params.isDebugLoggingEnabled);
                try {
                    analyzer.analyze();
                } catch (IOException | ParserConfigurationException | SAXException e) {
                    throw new RuntimeException(e);
                }

                // Just rewrite the .ap_ file to strip out the res/ files for unused resources
                try {
                    analyzer.rewriteResourceZip(
                            params.uncompressedResourceFile, params.compressedResourceFile);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

                // Dump some stats
                int unused = analyzer.getUnusedResourceCount();
                if (unused > 0) {
                    StringBuilder sb = new StringBuilder(200);
                    sb.append("Removed unused resources");

                    // This is a bit misleading until we can strip out all resource types:
                    //int total = analyzer.getTotalResourceCount()
                    //sb.append("(" + unused + "/" + total + ")")

                    long before = params.uncompressedResourceFile.length();
                    long after = params.compressedResourceFile.length();
                    long percent = (int) ((before - after) * 100 / before);
                    sb.append(": Binary resource data reduced from ")
                            .append(toKbString(before))
                            .append("KB to ")
                            .append(toKbString(after))
                            .append("KB: Removed ")
                            .append(percent)
                            .append("%");
                    if (!ourWarned) {
                        ourWarned = true;
                        sb.append("\n")
                                .append(
                                        "Note: If necessary, you can disable resource shrinking by adding\n")
                                .append("android {\n")
                                .append("    buildTypes {\n")
                                .append("        ")
                                .append(params.buildTypeName)
                                .append(" {\n")
                                .append("            shrinkResources false\n")
                                .append("        }\n")
                                .append("    }\n")
                                .append("}");
                    }

                    System.out.println(sb.toString());
                }
            } finally {
                analyzer.dispose();
            }
        }
    }

    private static class SplitterParams extends BuildElementsTransformParams {
        @NonNull private final File uncompressedResourceFile;
        @NonNull private final File compressedResourceFile;
        @Nullable private final BuildOutput mergedManifest;
        @NonNull private final List<File> classes;
        @Nullable private final File mappingFile;
        private final String buildTypeName;
        private final File sourceDir;
        private final File resourceDir;
        private final boolean isInfoLoggingEnabled;
        private final boolean isDebugLoggingEnabled;

        SplitterParams(
                @NonNull ApkData apkInfo,
                @NonNull File uncompressedResourceFile,
                @NonNull BuildElements mergedManifests,
                @NonNull List<File> classes,
                DelegateReourceShrinkTransform transform) {
            this.uncompressedResourceFile = uncompressedResourceFile;
            this.mergedManifest = mergedManifests.element(apkInfo);
            this.classes = classes;
            compressedResourceFile =
                    new File(
                            transform.compressedResources,
                            "resources-" + apkInfo.getBaseName() + "-stripped.ap_");
            mappingFile =
                    transform.mappingFileSrc != null
                            ? BuildableArtifactUtil.singleFile(transform.mappingFileSrc)
                            : null;
            buildTypeName =
                    transform.variantData.getVariantConfiguration().getBuildType().getName();
            sourceDir = Iterables.getOnlyElement(transform.sourceDir.getFiles());
            resourceDir = BuildableArtifactUtil.singleFile(transform.resourceDir);
            isInfoLoggingEnabled = transform.logger.isEnabled(LogLevel.INFO);
            isDebugLoggingEnabled = transform.logger.isEnabled(LogLevel.DEBUG);
        }

        @NonNull
        @Override
        public File getOutput() {
            return compressedResourceFile;
        }
    }

    private static String toKbString(long size) {
        return Integer.toString((int)size/1024);
    }
}

