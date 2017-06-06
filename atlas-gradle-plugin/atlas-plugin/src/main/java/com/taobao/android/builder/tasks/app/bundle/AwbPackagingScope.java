package com.taobao.android.builder.tasks.app.bundle;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.api.ApkOutputFile;
import com.android.build.gradle.internal.api.AppVariantContext;
import com.android.build.gradle.internal.api.AppVariantOutputContext;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.dsl.AaptOptions;
import com.android.build.gradle.internal.dsl.CoreSigningConfig;
import com.android.build.gradle.internal.dsl.PackagingOptions;
import com.android.build.gradle.internal.incremental.InstantRunBuildContext;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.PackagingScope;
import com.android.build.gradle.internal.scope.VariantOutputScope;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.variant.ApkVariantOutputData;
import com.android.build.gradle.internal.variant.SplitHandlingPolicy;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.core.VariantType;
import com.android.builder.model.ApiVersion;
import com.android.utils.StringHelper;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.taobao.android.builder.AtlasBuildContext;
import com.taobao.android.builder.dependency.model.AwbBundle;
import org.gradle.api.Project;

/**
 * Created by chenhjohn on 2017/5/11.
 *
 * @author chenhjohn
 * @date 2017/05/11
 */

public class AwbPackagingScope implements PackagingScope {
    protected final VariantOutputScope variantOutputScope;

    protected final VariantScope variantScope;

    protected final GlobalScope globalScope;

    private final AppVariantContext appVariantContext;

    private final AppVariantOutputContext appVariantOutputContext;

    private final GradleVariantConfiguration config;

    private final ApkVariantOutputData variantOutputData;

    private final AwbBundle awbBundle;

    public AwbPackagingScope(VariantOutputScope variantOutputScope, AppVariantContext appVariantContext,
                             AwbBundle awbBundle) {
        this.variantOutputScope = variantOutputScope;
        this.variantScope = variantOutputScope.getVariantScope();
        this.globalScope = variantScope.getGlobalScope();
        this.appVariantContext = appVariantContext;
        config = variantOutputScope.getVariantScope().getVariantConfiguration();
        variantOutputData = (ApkVariantOutputData)variantOutputScope.getVariantOutputData();
        appVariantOutputContext = appVariantContext.getAppVariantOutputContext(variantOutputData);
        this.awbBundle = awbBundle;
    }

    @NonNull
    @Override
    public AndroidBuilder getAndroidBuilder() {
        return globalScope.getAndroidBuilder();
    }

    @NonNull
    @Override
    public File getFinalResourcesFile() {
        ProcessAwbAndroidResources processAwbAndroidResources = appVariantOutputContext.getAwbAndroidResourcesMap().get(
            awbBundle.getName());
        if (processAwbAndroidResources == null) { return null; }
        File resourceFile = processAwbAndroidResources.getPackageOutputFile();
        return resourceFile;
    }

    @NonNull
    @Override
    public String getFullVariantName() {
        return variantScope.getFullVariantName();
    }

    @NonNull
    @Override
    public ApiVersion getMinSdkVersion() {
        return variantScope.getMinSdkVersion();
    }

    @NonNull
    @Override
    public InstantRunBuildContext getInstantRunBuildContext() {
        return variantScope.getInstantRunBuildContext();
    }

    @NonNull
    @Override
    public File getInstantRunSupportDir() {
        return variantScope.getInstantRunSupportDir();
    }

    @NonNull
    @Override
    public File getIncrementalDir(@NonNull String name) {
        return variantScope.getIncrementalDir(name);
    }

    @NonNull
    @Override
    public Set<File> getDexFolders() {
        File dexOutputFile = appVariantContext.getAwbDexOutput(awbBundle.getName());
        Set<File> dexFolders = new HashSet<File>();
        dexFolders.add(dexOutputFile);
        return dexFolders;
    }

    @NonNull
    @Override
    public Set<File> getJavaResources() {
        Set<File> javaResourcesLocations = Sets.newHashSet();
        if (appVariantContext.getAtlasExtension().getTBuildConfig().isIncremental()
            && awbBundle.getBaseAwbDependencies() != null && awbBundle.getBaseAwbDependencies().size() > 1) {
            File baseAwb = appVariantOutputContext.getVariantContext().apContext.getBaseExplodedAwb(
                awbBundle.getAwbSoName());
            javaResourcesLocations.add(baseAwb);
        }
        if (appVariantContext.getAtlasExtension().getTBuildConfig().getMergeAwbJavaRes()) {
            javaResourcesLocations.addAll(awbBundle.getLibraryJars());
        }
        return javaResourcesLocations;
    }

    @NonNull
    @Override
    public File getAssetsDir() {
        return appVariantOutputContext.getVariantContext().getMergeAssets(awbBundle);
    }

    @NonNull
    @Override
    public Set<File> getJniFolders() {
        Set<File> jniFolders = Sets.newHashSet();
        if (appVariantOutputContext.getAwbJniFolder(awbBundle) != null && appVariantOutputContext.getAwbJniFolder(
            awbBundle).exists()) {
            jniFolders.add(appVariantOutputContext.getAwbJniFolder(awbBundle));
        }
        return jniFolders;
    }

    @NonNull
    @Override
    public SplitHandlingPolicy getSplitHandlingPolicy() {
        return SplitHandlingPolicy.PRE_21_POLICY;
    }

    @NonNull
    @Override
    public Set<String> getAbiFilters() {
        if (variantOutputData.getMainOutputFile().getFilter(com.android.build.OutputFile.ABI) != null) {
            return ImmutableSet.of(variantOutputData.getMainOutputFile().getFilter(com.android.build.OutputFile.ABI));
        }
        Set<String> supportedAbis = config.getSupportedAbis();
        if (supportedAbis != null) {
            return supportedAbis;
        }
        return ImmutableSet.of();
    }

    @NonNull
    @Override
    public ApkOutputFile getMainOutputFile() {
        return variantOutputScope.getMainOutputFile();
    }

    @Nullable
    @Override
    public Set<String> getSupportedAbis() {
        return variantScope.getVariantConfiguration().getSupportedAbis();
    }

    @Override
    public boolean isDebuggable() {
        return variantScope.getVariantConfiguration().getBuildType().isDebuggable();
    }

    @Override
    public boolean isJniDebuggable() {
        return variantScope.getVariantConfiguration().getBuildType().isJniDebuggable();
    }

    @Nullable
    @Override
    public CoreSigningConfig getSigningConfig() {
        return variantScope.getVariantConfiguration().getSigningConfig();
    }

    @NonNull
    @Override
    public PackagingOptions getPackagingOptions() {
        return globalScope.getExtension().getPackagingOptions();
    }

    @NonNull
    @Override
    public String getTaskName(@NonNull String name) {
        return getTaskName(name, "");
    }

    @NonNull
    @Override
    public String getTaskName(@NonNull String prefix, @NonNull String suffix) {
        return variantScope.getTaskName(prefix, StringHelper.capitalize(awbBundle.getName()) + suffix);
    }

    @NonNull
    @Override
    public Project getProject() {
        return globalScope.getProject();
    }

    @NonNull
    @Override
    public File getOutputPackage() {
        return getOutputFile(awbBundle);
    }

    private File getOutputFile(AwbBundle awbBundle) {
        if (null != awbBundle.outputBundleFile) {
            return awbBundle.outputBundleFile;
        }

        if (AtlasBuildContext.sBuilderAdapter.packageRemoteAwbInJni && awbBundle.isRemote) {
            File file = appVariantOutputContext.getAwbPackageOutAppOutputFile(awbBundle);
            appVariantOutputContext.appBuildInfo.getOtherFilesMap().put("remotebundles/" + file.getName(), file);
            return file;
        }

        return appVariantOutputContext.getAwbPackageOutputFile(awbBundle);
    }

    @NonNull
    @Override
    public File getIntermediateApk() {
        return variantOutputScope.getIntermediateApk();
    }

    @NonNull
    @Override
    public File getInstantRunSplitApkOutputFolder() {
        return variantScope.getInstantRunSplitApkOutputFolder();
    }

    @Nullable
    @Override
    public File getAtomMetadataBaseFolder() {
        return variantOutputScope.getAtomMetadataBaseFolder();
    }

    @NonNull
    @Override
    public String getApplicationId() {
        return variantScope.getVariantConfiguration().getApplicationId();
    }

    @Override
    public int getVersionCode() {
        return 0;
    }

    @Nullable
    @Override
    public String getVersionName() {
        return null;
    }

    @NonNull
    @Override
    public AaptOptions getAaptOptions() {
        return globalScope.getExtension().getAaptOptions();
    }

    @NonNull
    @Override
    public VariantType getVariantType() {
        return VariantType.LIBRARY;
    }

    @NonNull
    @Override
    public File getManifestFile() {
        // TODO: Replace with an empty manifest.
        return awbBundle.getMergedManifest();
    }
}
