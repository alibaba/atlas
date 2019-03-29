package com.taobao.android.builder.tasks.transform;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.transform.*;
import com.android.build.gradle.api.BaseVariantOutput;
import com.android.build.gradle.internal.ApkDataUtils;
import com.android.build.gradle.internal.aapt.AaptGeneration;
import com.android.build.gradle.internal.api.AppVariantContext;
import com.android.build.gradle.internal.api.AppVariantOutputContext;
import com.android.build.gradle.internal.api.AwbTransform;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.dsl.AaptOptions;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.scope.*;
import com.android.build.gradle.internal.transforms.ShrinkResourcesTransform;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.MultiOutputPolicy;
import com.android.build.gradle.tasks.ResourceUsageAnalyzer;
import com.android.builder.core.VariantType;
import com.android.ide.common.build.ApkData;
import com.android.ide.common.internal.WaitableExecutor;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableList;
import com.taobao.android.builder.AtlasBuildContext;
import com.taobao.android.builder.dependency.model.AwbBundle;
import org.gradle.api.file.FileCollection;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.Logger;
import org.gradle.tooling.BuildException;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinTask;

/**
 * ResourcesShrinker
 *
 * @author zhayu.ll
 * @date 18/5/22
 */
public class ResourcesShrinker extends Transform {

    public ShrinkResourcesTransform resourcesTransform;
    private static boolean ourWarned = true; // Logging disabled until shrinking is on by default.

    @NonNull
    private final BaseVariantData variantData;

    @NonNull
    private final Logger logger;

    @NonNull
    private final File sourceDir;
    @NonNull
    private final FileCollection resourceDir;
    @Nullable
    private final FileCollection mappingFileSrc;
    @NonNull
    private final FileCollection mergedManifests;
    @NonNull
    private final FileCollection uncompressedResources;
    @NonNull
    private final FileCollection splitListInput;

    @NonNull
    private final AaptGeneration aaptGeneration;
    @NonNull
    private final AaptOptions aaptOptions;
    @NonNull
    private final VariantType variantType;
    private final boolean isDebuggableBuildType;
    @NonNull
    private final MultiOutputPolicy multiOutputPolicy;

    @NonNull
    private final File compressedResources;

    private AppVariantContext variantContext;


    @Override
    public boolean isCacheable() {
        return true;
    }

    public ResourcesShrinker(ShrinkResourcesTransform resourcesTransform, @NonNull BaseVariantData variantData,
                             @NonNull FileCollection uncompressedResources,
                             @NonNull File compressedResources,
                             @NonNull AaptGeneration aaptGeneration,
                             @NonNull FileCollection splitListInput,
                             @NonNull Logger logger,
                             AppVariantContext variantContext) {

        this.variantContext = variantContext;
        this.resourcesTransform = resourcesTransform;
        VariantScope variantScope = variantData.getScope();
        GlobalScope globalScope = variantScope.getGlobalScope();
        GradleVariantConfiguration variantConfig = variantData.getVariantConfiguration();

        this.variantData = variantData;
        this.logger = logger;

        this.sourceDir = variantScope.getRClassSourceOutputDir();
        this.resourceDir = variantScope.getOutput(TaskOutputHolder.TaskOutputType.MERGED_NOT_COMPILED_RES);
        this.mappingFileSrc =
                variantScope.hasOutput(TaskOutputHolder.TaskOutputType.APK_MAPPING)
                        ? variantScope.getOutput(TaskOutputHolder.TaskOutputType.APK_MAPPING)
                        : null;
        this.mergedManifests = variantScope.getOutput(TaskOutputHolder.TaskOutputType.MERGED_MANIFESTS);
        this.uncompressedResources = uncompressedResources;
        this.splitListInput = splitListInput;

        this.aaptGeneration = aaptGeneration;
        this.aaptOptions = globalScope.getExtension().getAaptOptions();
        this.variantType = variantData.getType();
        this.isDebuggableBuildType = variantConfig.getBuildType().isDebuggable();
        this.multiOutputPolicy = variantData.getOutputScope().getMultiOutputPolicy();
        this.compressedResources = compressedResources;
    }

    @Override
    public String getName() {
        return resourcesTransform.getName();
    }

    @Override
    public Set<QualifiedContent.ContentType> getInputTypes() {
        return resourcesTransform.getInputTypes();
    }

    @Override
    public Set<? super QualifiedContent.Scope> getScopes() {
        return resourcesTransform.getScopes();
    }

    @Override
    public boolean isIncremental() {
        return false;
    }

    @Override
    public void transform(TransformInvocation transformInvocation) throws TransformException, InterruptedException, IOException {
        super.transform(transformInvocation);
        SplitList splitList = SplitList.load(splitListInput);
        Collection<BuildOutput> uncompressedBuildOutputs = BuildOutputs.load(uncompressedResources);
        OutputScope outputScope = variantData.getScope().getOutputScope();
        outputScope.parallelForEachOutput(
                uncompressedBuildOutputs,
                TaskOutputHolder.TaskOutputType.PROCESSED_RES,
                TaskOutputHolder.TaskOutputType.SHRUNK_PROCESSED_RES,
                this::splitAction,
                transformInvocation,
                splitList);
        outputScope.save(TaskOutputHolder.TaskOutputType.SHRUNK_PROCESSED_RES, compressedResources);


    }

    @Nullable
    public File splitAction(
            @NonNull ApkData apkData,
            @Nullable File uncompressedResourceFile,
            TransformInvocation invocation,
            SplitList splitList) {

        if (uncompressedResourceFile == null) {
            return null;
        }

        List<File> classes = new ArrayList<>();
        classes.addAll(AtlasBuildContext.atlasMainDexHelperMap.get(variantContext.getVariantName()).getInputDirs());
        classes.addAll(AtlasBuildContext.atlasMainDexHelperMap.get(variantContext.getVariantName()).getAllMainDexJars());
        AppVariantOutputContext appVariantOutputContext = variantContext.getAppVariantOutputContext(apkData);
        for (AwbTransform awbTransform : appVariantOutputContext.getAwbTransformMap().values()) {
            classes.addAll(awbTransform.getInputLibraries());
            if (awbTransform.getInputDirs()!= null && awbTransform.getInputDirs().size() > 0) {
                classes.addAll(awbTransform.getInputDirs());
            }
        }


        WaitableExecutor executor = WaitableExecutor.useGlobalSharedThreadPool();

        Collection<BuildOutput> mergedManifests = BuildOutputs.load(ResourcesShrinker.this.mergedManifests);
        BuildOutput mergedManifest =
                OutputScope.getOutput(mergedManifests, TaskOutputHolder.TaskOutputType.MERGED_MANIFESTS, apkData);
        File mappingFile = mappingFileSrc != null ? mappingFileSrc.getSingleFile() : null;

        ForkJoinTask<File> task = executor.execute(() -> {
            File reportFile = null;
            if (mappingFile != null) {
                File logDir = mappingFile.getParentFile();
                if (logDir != null) {
                    reportFile = new File(logDir, "resources.txt");
                }
            }
            File compressedResourceFile =
                    new File(
                            compressedResources,
                            "resources-" + apkData.getBaseName() + "-stripped.ap_");
            FileUtils.mkdirs(compressedResourceFile.getParentFile());


            if (mergedManifest == null) {
                try {
                    FileUtils.copyFile(uncompressedResourceFile, compressedResourceFile);
                } catch (IOException e) {
                    logger.error("Failed to copy uncompressed resource file :", e);
                    throw new RuntimeException("Failed to copy uncompressed resource file", e);
                }
                return compressedResourceFile;
            }

            // Analyze resources and usages and strip out unused
            ResourceUsageAnalyzer analyzer =
                    new ResourceUsageAnalyzer(
                            sourceDir,
                            classes,
                            mergedManifest.getOutputFile(),
                            mappingFile,
                            resourceDir.getSingleFile(),
                            reportFile);
            try {
                analyzer.setVerbose(logger.isEnabled(LogLevel.INFO));
                analyzer.setDebug(logger.isEnabled(LogLevel.DEBUG));
                analyzer.analyze();

                // Just rewrite the .ap_ file to strip out the res/ files for unused resources
                analyzer.rewriteResourceZip(uncompressedResourceFile, compressedResourceFile);

                // Dump some stats
                int unused = analyzer.getUnusedResourceCount();
                if (unused > 0) {
                    StringBuilder sb = new StringBuilder(200);
                    sb.append("Removed unused resources");

                    // This is a bit misleading until we can strip out all resource types:
                    //int total = analyzer.getTotalResourceCount()
                    //sb.append("(" + unused + "/" + total + ")")

                    long before = uncompressedResourceFile.length();
                    long after = compressedResourceFile.length();
                    long percent = (int) ((before - after) * 100 / before);
                    sb.append(": Binary resource data reduced from ").
                            append(toKbString(before)).
                            append("KB to ").
                            append(toKbString(after)).
                            append("KB: Removed ").append(percent).append("%");
                    if (!ourWarned) {
                        ourWarned = true;
                        String name = variantData.getVariantConfiguration().getBuildType().getName();
                        sb.append("\n")
                                .append(
                                        "Note: If necessary, you can disable resource shrinking by adding\n")
                                .append("android {\n")
                                .append("    buildTypes {\n")
                                .append("        ")
                                .append(name)
                                .append(" {\n")
                                .append("            shrinkResources false\n")
                                .append("        }\n")
                                .append("    }\n")
                                .append("}");
                    }

                    System.out.println(sb.toString());
                }
            } catch (Exception e) {
                logger.quiet("Failed to shrink resources: ignoring", e);
            } finally {
                analyzer.dispose();
            }
            return compressedResourceFile;

        });

        for (AwbTransform awbTransform : appVariantOutputContext.getAwbTransformMap().values()) {
            AwbBundle awbBundle = awbTransform.getAwbBundle();
            File compressedBundleResourceFile = appVariantOutputContext.getAwbCompressResourcePackageOutputFile(awbBundle);
            File unCompressedBundleResourceFile = appVariantOutputContext.getAwbProcessResourcePackageOutputFile(awbBundle);
            File awbResDir = appVariantOutputContext.getAwbMergedResourceDir(variantContext.getVariantConfiguration(),awbBundle);
            File reportFile = new File(uncompressedResourceFile.getParentFile(), "resources.txt");
            File bundleSourceDir = appVariantOutputContext.getAwbRClassSourceOutputDir(variantContext.getVariantConfiguration(),awbBundle);
            executor.execute(() -> {
                ResourceUsageAnalyzer analyzer =
                        new ResourceUsageAnalyzer(
                                bundleSourceDir,
                                classes,
                                mergedManifest.getOutputFile(),
                                mappingFile,
                                awbResDir,
                                reportFile);
                try {
                    analyzer.setVerbose(logger.isEnabled(LogLevel.INFO));
                    analyzer.setDebug(logger.isEnabled(LogLevel.DEBUG));
                    analyzer.analyze();

                    // Just rewrite the .ap_ file to strip out the res/ files for unused resources
                    analyzer.rewriteResourceZip(unCompressedBundleResourceFile, compressedBundleResourceFile);

                    // Dump some stats
                    int unused = analyzer.getUnusedResourceCount();
                    if (unused > 0) {
                        StringBuilder sb = new StringBuilder(200);
                        sb.append("Removed awb bundle" + awbBundle.getName() + " unused resources");

                        // This is a bit misleading until we can strip out all resource types:
                        //int total = analyzer.getTotalResourceCount()
                        //sb.append("(" + unused + "/" + total + ")")

                        long before = unCompressedBundleResourceFile.length();
                        long after = compressedBundleResourceFile.length();
                        long percent = (int) ((before - after) * 100 / before);
                        sb.append(": Binary resource data reduced from ").
                                append(toKbString(before)).
                                append("KB to ").
                                append(toKbString(after)).
                                append("KB: Removed ").append(percent).append("%");
                        if (!ourWarned) {
                            ourWarned = true;
                            String name = variantData.getVariantConfiguration().getBuildType().getName();
                            sb.append("\n")
                                    .append(
                                            "Note: If necessary, you can disable resource shrinking by adding\n")
                                    .append("android {\n")
                                    .append("    buildTypes {\n")
                                    .append("        ")
                                    .append(name)
                                    .append(" {\n")
                                    .append("            shrinkResources false\n")
                                    .append("        }\n")
                                    .append("    }\n")
                                    .append("}");
                        }

                        System.out.println(sb.toString());
                    }
                } catch (Exception e) {
                    logger.quiet("Failed to shrink resources: ignoring", e);
                } finally {
                    analyzer.dispose();
                }
                return compressedBundleResourceFile;
            });
        }

        try {
            List<WaitableExecutor.TaskResult<File>> taskResults = executor.waitForAllTasks();
            taskResults.forEach(
                    taskResult -> {
                        if (taskResult.getException() != null) {
                            throw new BuildException(
                                    taskResult.getException().getMessage(),
                                    taskResult.getException());
                        }
                    });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }

        return task.join();

    }

    private static String toKbString(long size) {
        return Integer.toString((int) size / 1024);
    }

    public Set<QualifiedContent.Scope> getReferencedScopes() {
        return TransformManager.SCOPE_FULL_PROJECT;
    }

    public Map<String, Object> getParameterInputs() {
        return resourcesTransform.getParameterInputs();
    }

    public Collection<SecondaryFile> getSecondaryFiles() {
        return resourcesTransform.getSecondaryFiles();
    }

    @NonNull
    @Override
    public Collection<File> getSecondaryDirectoryOutputs() {
        return ImmutableList.of(compressedResources);
    }
}
