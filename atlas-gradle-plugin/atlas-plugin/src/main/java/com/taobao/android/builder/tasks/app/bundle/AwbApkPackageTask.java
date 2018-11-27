package com.taobao.android.builder.tasks.app.bundle;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.apkzlib.utils.IOExceptionWrapper;
import com.android.apkzlib.zip.compress.Zip64NotSupportedException;
import com.android.build.gradle.internal.api.AppVariantContext;
import com.android.build.gradle.internal.api.AppVariantOutputContext;
import com.android.build.gradle.internal.api.VariantContext;
import com.android.build.gradle.internal.incremental.DexPackagingPolicy;
import com.android.build.gradle.internal.incremental.FileType;
import com.android.build.gradle.internal.incremental.InstantRunBuildContext;
import com.android.build.gradle.internal.packaging.IncrementalPackagerBuilder;
import com.android.build.gradle.internal.scope.BuildOutput;
import com.android.build.gradle.internal.scope.BuildOutputs;
import com.android.build.gradle.internal.scope.DefaultGradlePackagingScope;
import com.android.build.gradle.internal.scope.OutputScope;
import com.android.build.gradle.internal.scope.TaskOutputHolder;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.KnownFilesSaveData;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.files.FileCacheByPath;
import com.android.builder.files.IncrementalRelativeFileSets;
import com.android.builder.files.RelativeFile;
import com.android.builder.internal.packaging.IncrementalPackager;
import com.android.builder.model.SigningConfig;
import com.android.builder.packaging.PackagingUtils;
import com.android.ide.common.build.ApkData;
import com.android.ide.common.res2.FileStatus;
import com.android.utils.FileUtils;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.io.ByteStreams;
import com.taobao.android.builder.AtlasBuildContext;
import com.taobao.android.builder.dependency.model.AwbBundle;
import com.taobao.android.builder.tools.zip.BetterZip;
import org.gradle.api.file.FileCollection;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * @author lilong
 * @create 2017-12-06 上午11:17
 */

public class AwbApkPackageTask {

    private final String taskName;
    private FileCollection resourceFiles;
    private OutputScope outputScope;
    private AwbBundle awbBundle;
    private File outputDirectory;
    private AppVariantOutputContext appVariantOutputContext;
    private FileCollection dexFolders;
    private FileCollection javaResouresFiles;
    private FileCollection assets;
    private FileCollection jniFolders;
    private FileCollection awbManifestFolder;
    protected InstantRunBuildContext instantRunContext;


    private static final String ZIP_DIFF_CACHE_DIR = "zip-cache";

    private static final String ZIP_64_COPY_DIR = "zip64-copy";
    private DexPackagingPolicy dexPackagingPolicy = DexPackagingPolicy.STANDARD;
    private SigningConfig signingConfig;
    private AndroidBuilder androidBuilder;
    private int miniSdkVersion;
    private boolean debug;
    private Set<String> supportAbis;
    private boolean jniDebug;
    Collection<String> aaptOptionsNoCompress;
    private DefaultGradlePackagingScope packagingScope;

    public AwbApkPackageTask(FileCollection resourceFiles, VariantContext variantContext, AwbBundle awbBundle, AppVariantOutputContext appVariantOutputContext, FileCollection dexFolders,
                             FileCollection javaResouresFiles, FileCollection assets, FileCollection jniFolders, FileCollection awbManifestFolder, AndroidBuilder androidBuilder,
                             int miniSdkVersion, String taskName) {
        this.packagingScope = new DefaultGradlePackagingScope(variantContext.getScope());
        this.resourceFiles = resourceFiles;
        this.outputScope = variantContext.getScope().getOutputScope();
        this.awbBundle = awbBundle;
        this.appVariantOutputContext = appVariantOutputContext;
        this.outputDirectory = ((AppVariantContext) variantContext).getAwbApkOutputDir();
        this.dexFolders = dexFolders;
        this.javaResouresFiles = javaResouresFiles;
        this.assets = assets;
        this.jniFolders = jniFolders;
        this.awbManifestFolder = awbManifestFolder;
        this.instantRunContext = packagingScope.getInstantRunBuildContext();
        this.signingConfig = packagingScope.getSigningConfig();
        this.androidBuilder = androidBuilder;
        this.miniSdkVersion = miniSdkVersion;
        this.debug = packagingScope.isDebuggable();
        supportAbis = packagingScope.getSupportedAbis();
        this.jniDebug = packagingScope.isJniDebuggable();
        aaptOptionsNoCompress = packagingScope.getAaptOptions().getNoCompress();
        this.taskName = taskName;


    }

    public File getAwbPackageOutputFile(AppVariantContext variantContext, String awbOutputName) {
        Set<String> libSoNames = variantContext.getAtlasExtension().getTBuildConfig().getKeepInLibSoNames();

        File file = null;
        if (libSoNames.isEmpty() || libSoNames.contains(awbOutputName)) {
            //直接移动到主apk的lib下
            file = new File(packagingScope.getJniFolders().getSingleFile(), "lib/armeabi" + File.separator + awbOutputName);
        } else {
            file = new File(variantContext.getVariantData().mergeAssetsTask.getOutputDir(), awbOutputName);
        }

        Set<String> assetsSoNames = variantContext.getAtlasExtension().getTBuildConfig().getKeepInAssetsSoNames();
        if (!assetsSoNames.isEmpty() && assetsSoNames.contains(awbOutputName)) {
            file = new File(variantContext.getVariantData().mergeAssetsTask.getOutputDir(), awbOutputName);
        }
        return file;
    }

    public File doFullTaskAction() throws IOException {
        if (supportAbis == null){
            supportAbis = ImmutableSet.of();
        }
        ApkData apkData = appVariantOutputContext.getScope().getOutputScope().getApkDatas().get(0);
        if (dexFolders.getSingleFile().exists() && awbBundle.getMergedManifest().exists()) {
            File[] dexFile = dexFolders.getSingleFile().listFiles((dir, name) -> name.equals("classes.dex"));
            if (dexFile != null) {
                File file = AtlasBuildContext.atlasApkProcessor.securitySignApk(dexFile[0], awbBundle.getMergedManifest(),appVariantOutputContext.getVariantContext().getBuildType(),true);
                if (file!= null && file.exists()) {
                    BetterZip.addFile(getAndroidResources(apkData, resourceFiles.getSingleFile()).iterator().next(), "res/drawable/".concat(file.getName()), file);
                }
            }
        }
        File file = splitFullAction(apkData, resourceFiles.getSingleFile());
        outputScope.save(VariantScope.TaskOutputType.APK, outputDirectory);
        return file;
    }


    public File splitFullAction(@NonNull ApkData apkData, @Nullable File processedResources)
            throws IOException {

        File incrementalDirForSplit = new File(getIncrementalFolder(awbBundle), apkData.getFullName());

        /*
         * Clear the intermediate build directory. We don't know if anything is in there and
         * since this is a full build, we don't want to get any interference from previous state.
         */
        if (incrementalDirForSplit.exists()) {
            FileUtils.deleteDirectoryContents(incrementalDirForSplit);
        } else {
            FileUtils.mkdirs(incrementalDirForSplit);
        }

        File cacheByPathDir = new File(incrementalDirForSplit, ZIP_DIFF_CACHE_DIR);
        FileUtils.mkdirs(cacheByPathDir);
        FileCacheByPath cacheByPath = new FileCacheByPath(cacheByPathDir);

        /*
         * Clear the cache to make sure we have do not do an incremental build.
         */
        cacheByPath.clear();

        Set<File> androidResources = getAndroidResources(apkData, processedResources);

        appVariantOutputContext.getVariantContext().getProject().getLogger().warn(awbBundle.getName()+" androidResources File:"+androidResources.iterator().next().getAbsolutePath());

        FileUtils.mkdirs(outputDirectory);

        File outputFile = getOutputFile(awbBundle);

        /*
         * Additionally, make sure we have no previous package, if it exists.
         */
        FileUtils.deleteIfExists(outputFile);

        ImmutableMap<RelativeFile, FileStatus> updatedDex =
                IncrementalRelativeFileSets.fromZipsAndDirectories(dexFolders);
        ImmutableMap<RelativeFile, FileStatus> updatedJavaResources = getJavaResourcesChanges();
        ImmutableMap<RelativeFile, FileStatus> updatedAssets =
                IncrementalRelativeFileSets.fromZipsAndDirectories(assets.getFiles());
        ImmutableMap<RelativeFile, FileStatus> updatedAndroidResources =
                IncrementalRelativeFileSets.fromZipsAndDirectories(androidResources);
        ImmutableMap<RelativeFile, FileStatus> updatedJniResources =
                IncrementalRelativeFileSets.fromZipsAndDirectories(jniFolders);

        Collection<BuildOutput> manifestOutputs = BuildOutputs.load(TaskOutputHolder.TaskOutputType.MERGED_MANIFESTS, awbManifestFolder.getSingleFile().getParentFile());

        doTask(
                apkData,
                incrementalDirForSplit,
                outputFile,
                cacheByPath,
                manifestOutputs,
                updatedDex,
                updatedJavaResources,
                updatedAssets,
                updatedAndroidResources,
                updatedJniResources);

        /*
         * Update the known files.
         */
        KnownFilesSaveData saveData = KnownFilesSaveData.make(incrementalDirForSplit);
        saveData.setInputSet(updatedDex.keySet(), KnownFilesSaveData.InputSet.DEX);
        saveData.setInputSet(updatedJavaResources.keySet(), KnownFilesSaveData.InputSet.JAVA_RESOURCE);
        saveData.setInputSet(updatedAssets.keySet(), KnownFilesSaveData.InputSet.ASSET);
        saveData.setInputSet(updatedAndroidResources.keySet(), KnownFilesSaveData.InputSet.ANDROID_RESOURCE);
        saveData.setInputSet(updatedJniResources.keySet(), KnownFilesSaveData.InputSet.NATIVE_RESOURCE);
        saveData.saveCurrentData();
        File file;
        String outputFileName = outputFile.getName();
        file = getAwbPackageOutputFile(appVariantOutputContext.getVariantContext(), outputFileName);
        FileUtils.copyFileToDirectory(outputFile, file.getParentFile());
        return new File(file.getParentFile(), outputFileName);
    }

    private void doTask(
            @NonNull ApkData apkData,
            @NonNull File incrementalDirForSplit,
            @NonNull File outputFile,
            @NonNull FileCacheByPath cacheByPath,
            @NonNull Collection<BuildOutput> manifestOutputs,
            @NonNull ImmutableMap<RelativeFile, FileStatus> changedDex,
            @NonNull ImmutableMap<RelativeFile, FileStatus> changedJavaResources,
            @NonNull ImmutableMap<RelativeFile, FileStatus> changedAssets,
            @NonNull ImmutableMap<RelativeFile, FileStatus> changedAndroidResources,
            @NonNull ImmutableMap<RelativeFile, FileStatus> changedNLibs)
            throws IOException {

        ImmutableMap.Builder<RelativeFile, FileStatus> javaResourcesForApk =
                ImmutableMap.builder();
        javaResourcesForApk.putAll(changedJavaResources);

        if (dexPackagingPolicy == DexPackagingPolicy.INSTANT_RUN_MULTI_APK) {
            changedDex = ImmutableMap.copyOf(
                    Maps.filterKeys(
                            changedDex,
                            Predicates.compose(
                                    Predicates.in(dexFolders.getFiles()),
                                    RelativeFile::getBase
                            )));
        }
        final ImmutableMap<RelativeFile, FileStatus> dexFilesToPackage = changedDex;

        String abiFilter = apkData.getFilter(com.android.build.OutputFile.FilterType.ABI);

        // find the manifest file for this split.
        BuildOutput manifestForSplit =
                OutputScope.getOutput(manifestOutputs, TaskOutputHolder.TaskOutputType.MERGED_MANIFESTS, apkData);

        FileUtils.mkdirs(outputFile.getParentFile());

        try (IncrementalPackager packager =
                     new IncrementalPackagerBuilder()
                             .withOutputFile(outputFile)
                             .withSigning(null)
                             .withCreatedBy(androidBuilder.getCreatedBy())
                             .withMinSdk(miniSdkVersion)
                             // TODO: allow extra metadata to be saved in the split scope to avoid
                             // reparsing
                             // these manifest files.
                             .withNativeLibraryPackagingMode(
                                     PackagingUtils.getNativeLibrariesLibrariesPackagingMode(manifestForSplit == null ? awbManifestFolder.getSingleFile() :
                                             manifestForSplit.getOutputFile()))
                             .withNoCompressPredicate(
                                     PackagingUtils.getNoCompressPredicate(
                                             aaptOptionsNoCompress, manifestForSplit == null ? awbManifestFolder.getSingleFile():manifestForSplit.getOutputFile()))
                             .withIntermediateDir(incrementalDirForSplit)
                             .withProject(appVariantOutputContext.getScope().getGlobalScope().getProject())
                             .withDebuggableBuild(debug)
                             .withAcceptedAbis(
                                     abiFilter == null ? supportAbis : ImmutableSet.of(abiFilter))
                             .withJniDebuggableBuild(jniDebug)
                             .build()) {
            packager.updateDex(dexFilesToPackage);
            packager.updateJavaResources(changedJavaResources);
            packager.updateAssets(changedAssets);
            packager.updateAndroidResources(changedAndroidResources);
            packager.updateNativeLibraries(changedNLibs);
            // Only report APK as built if it has actually changed.
            if (packager.hasPendingChangesWithWait()) {
                // FIX-ME : below would not work in multi apk situations. There is code somewhere
                // to ensure we only build ONE multi APK for the target device, make sure it is still
                // active.
                instantRunContext.addChangedFile(FileType.MAIN, outputFile);
            }
        }

        /*
         * Save all used zips in the cache.
         */
        Stream.concat(
                dexFilesToPackage.keySet().stream(),
                Stream.concat(
                        changedJavaResources.keySet().stream(),
                        Stream.concat(
                                changedAndroidResources.keySet().stream(),
                                changedNLibs.keySet().stream())))
                .map(RelativeFile::getBase)
                .filter(File::isFile)
                .distinct()
                .forEach(
                        (File f) -> {
                            try {
                                cacheByPath.add(f);
                            } catch (IOException e) {
                                throw new IOExceptionWrapper(e);
                            }
                        });
    }

    @NonNull
    Set<File> getAndroidResources(@NonNull ApkData apkData, @Nullable File processedResources)
            throws IOException {

        //todo :for instantrun mode ....
//        if (instantRunContext.isInInstantRunMode()
//                && instantRunContext.getPatchingPolicy()
//                == InstantRunPatchingPolicy.MULTI_APK_SEPARATE_RESOURCES) {
//            Collection<BuildOutput> manifestFiles =
//                    BuildOutputs.load(
//                            TaskOutputHolder.TaskOutputType.INSTANT_RUN_MERGED_MANIFESTS,
//                            manifests);
//            BuildOutput manifestOutput =
//                    OutputScope.getOutput(
//                            manifestFiles,
//                            TaskOutputHolder.TaskOutputType.INSTANT_RUN_MERGED_MANIFESTS,
//                            apkData);
//
//            if (manifestOutput == null) {
//                throw new RuntimeException("Cannot find merged manifest file");
//            }
//            File manifestFile = manifestOutput.getOutputFile();
//            return ImmutableSet.of(generateEmptyAndroidResourcesForInstantRun(manifestFile));
//        } else {
        return processedResources != null
                ? ImmutableSet.of(processedResources)
                : ImmutableSet.of();
//        }
    }


    private File getOutputFile(AwbBundle awbBundle) {
        if (null != awbBundle.outputBundleFile) {
            return awbBundle.outputBundleFile;
        }

        if (AtlasBuildContext.sBuilderAdapter.packageRemoteAwbInJni && awbBundle.isRemote) {
            File file = appVariantOutputContext.getAwbPackageOutAppOutputFile(awbBundle);
            appVariantOutputContext.appBuildInfo.getOtherFilesMap()
                    .put("remotebundles/" + file.getName(), file);
            return file;
        }

        return appVariantOutputContext.getAwbPackageOutputFile(awbBundle);
    }


    ImmutableMap<RelativeFile, FileStatus> getJavaResourcesChanges() throws IOException {

        ImmutableMap.Builder<RelativeFile, FileStatus> updatedJavaResourcesBuilder =
                ImmutableMap.builder();
        for (File javaResourceFile : javaResouresFiles) {
            try {
                updatedJavaResourcesBuilder.putAll(
                        javaResourceFile.isFile()
                                ? IncrementalRelativeFileSets.fromZip(javaResourceFile)
                                : IncrementalRelativeFileSets.fromDirectory(javaResourceFile));
            } catch (Zip64NotSupportedException e) {
                updatedJavaResourcesBuilder.putAll(
                        IncrementalRelativeFileSets.fromZip(
                                copyJavaResourcesOnly(getIncrementalFolder(awbBundle), javaResourceFile)));
            }
        }
        return updatedJavaResourcesBuilder.build();
    }

    private File getIncrementalFolder(AwbBundle awbBundle) {

        return new File(packagingScope.getIncrementalDir(taskName), awbBundle.getName());


    }

    static File copyJavaResourcesOnly(File destinationFolder, File zip64File) throws IOException {
        File cacheDir = new File(destinationFolder, ZIP_64_COPY_DIR);
        File copiedZip = new File(cacheDir, zip64File.getName());
        FileUtils.mkdirs(copiedZip.getParentFile());

        try (ZipFile inFile = new ZipFile(zip64File);
             ZipOutputStream outFile =
                     new ZipOutputStream(
                             new BufferedOutputStream(new FileOutputStream(copiedZip)))) {

            Enumeration<? extends ZipEntry> entries = inFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry zipEntry = entries.nextElement();
                if (!zipEntry.getName().endsWith(SdkConstants.DOT_CLASS)) {
                    outFile.putNextEntry(new ZipEntry(zipEntry.getName()));
                    try {
                        ByteStreams.copy(
                                new BufferedInputStream(inFile.getInputStream(zipEntry)), outFile);
                    } finally {
                        outFile.closeEntry();
                    }
                }
            }
        }
        return copiedZip;
    }


    public void doIncrementalTaskAction(Map<File, FileStatus> changedInputs) throws IOException {
        checkNotNull(changedInputs, "changedInputs == null");
        outputScope.parallelForEachOutput(
                BuildOutputs.load(TaskOutputHolder.TaskOutputType.PROCESSED_RES, resourceFiles),
                TaskOutputHolder.TaskOutputType.PROCESSED_RES,
                TaskOutputHolder.TaskOutputType.PROCESSED_RES,
                (split, output) -> splitIncrementalAction(split, output, changedInputs));
        outputScope.save(TaskOutputHolder.TaskOutputType.PROCESSED_RES, outputDirectory);
    }

    private File splitIncrementalAction(
            ApkData apkData, @Nullable File processedResources, Map<File, FileStatus> changedInputs)
            throws IOException {

        Set<File> androidResources = getAndroidResources(apkData, processedResources);

        File incrementalDirForSplit = new File(getIncrementalFolder(awbBundle), apkData.getFullName());

        File cacheByPathDir = new File(incrementalDirForSplit, ZIP_DIFF_CACHE_DIR);
        if (!cacheByPathDir.exists()) {
            FileUtils.mkdirs(cacheByPathDir);
        }
        FileCacheByPath cacheByPath = new FileCacheByPath(cacheByPathDir);

        KnownFilesSaveData saveData = KnownFilesSaveData.make(incrementalDirForSplit);

        final Set<File> assetsFiles = assets.getFiles();

        Set<Runnable> cacheUpdates = new HashSet<>();
        ImmutableMap<RelativeFile, FileStatus> changedDexFiles =
                KnownFilesSaveData.getChangedInputs(
                        changedInputs,
                        saveData,
                        KnownFilesSaveData.InputSet.DEX,
                        dexFolders.getFiles(),
                        cacheByPath,
                        cacheUpdates);

        ImmutableMap<RelativeFile, FileStatus> changedJavaResources;
        try {
            changedJavaResources =
                    KnownFilesSaveData.getChangedInputs(
                            changedInputs,
                            saveData,
                            KnownFilesSaveData.InputSet.JAVA_RESOURCE,
                            javaResouresFiles.getFiles(),
                            cacheByPath,
                            cacheUpdates);
        } catch (Zip64NotSupportedException e) {
            // copy all changedInputs into a smaller jar and rerun.
            ImmutableMap.Builder<File, FileStatus> copiedInputs = ImmutableMap.builder();
            for (Map.Entry<File, FileStatus> fileFileStatusEntry : changedInputs.entrySet()) {
                copiedInputs.put(
                        copyJavaResourcesOnly(getIncrementalFolder(awbBundle), fileFileStatusEntry.getKey()),
                        fileFileStatusEntry.getValue());
            }
            changedJavaResources =
                    KnownFilesSaveData.getChangedInputs(
                            copiedInputs.build(),
                            saveData,
                            KnownFilesSaveData.InputSet.JAVA_RESOURCE,
                            javaResouresFiles.getFiles(),
                            cacheByPath,
                            cacheUpdates);
        }

        ImmutableMap<RelativeFile, FileStatus> changedAssets =
                KnownFilesSaveData.getChangedInputs(
                        changedInputs,
                        saveData,
                        KnownFilesSaveData.InputSet.ASSET,
                        assetsFiles,
                        cacheByPath,
                        cacheUpdates);

        ImmutableMap<RelativeFile, FileStatus> changedAndroidResources =
                KnownFilesSaveData.getChangedInputs(
                        changedInputs,
                        saveData,
                        KnownFilesSaveData.InputSet.ANDROID_RESOURCE,
                        androidResources,
                        cacheByPath,
                        cacheUpdates);

        ImmutableMap<RelativeFile, FileStatus> changedNLibs =
                KnownFilesSaveData.getChangedInputs(
                        changedInputs,
                        saveData,
                        KnownFilesSaveData.InputSet.NATIVE_RESOURCE,
                        jniFolders.getFiles(),
                        cacheByPath,
                        cacheUpdates);

        File outputFile = getOutputFile(awbBundle);

        Collection<BuildOutput> manifestOutputs = BuildOutputs.load(TaskOutputHolder.TaskOutputType.MERGED_MANIFESTS, awbManifestFolder.getSingleFile().getParentFile());

        doTask(
                apkData,
                incrementalDirForSplit,
                outputFile,
                cacheByPath,
                manifestOutputs,
                changedDexFiles,
                changedJavaResources,
                changedAssets,
                changedAndroidResources,
                changedNLibs);

        /*
         * Update the cache
         */
        cacheUpdates.forEach(Runnable::run);

        /*
         * Update the save data keep files.
         */
        ImmutableMap<RelativeFile, FileStatus> allDex =
                IncrementalRelativeFileSets.fromZipsAndDirectories(dexFolders);
        ImmutableMap<RelativeFile, FileStatus> allJavaResources =
                IncrementalRelativeFileSets.fromZipsAndDirectories(javaResouresFiles);
        ImmutableMap<RelativeFile, FileStatus> allAssets =
                IncrementalRelativeFileSets.fromZipsAndDirectories(assetsFiles);
        ImmutableMap<RelativeFile, FileStatus> allAndroidResources =
                IncrementalRelativeFileSets.fromZipsAndDirectories(androidResources);
        ImmutableMap<RelativeFile, FileStatus> allJniResources =
                IncrementalRelativeFileSets.fromZipsAndDirectories(jniFolders);

        saveData.setInputSet(allDex.keySet(), KnownFilesSaveData.InputSet.DEX);
        saveData.setInputSet(allJavaResources.keySet(), KnownFilesSaveData.InputSet.JAVA_RESOURCE);
        saveData.setInputSet(allAssets.keySet(), KnownFilesSaveData.InputSet.ASSET);
        saveData.setInputSet(allAndroidResources.keySet(), KnownFilesSaveData.InputSet.ANDROID_RESOURCE);
        saveData.setInputSet(allJniResources.keySet(), KnownFilesSaveData.InputSet.NATIVE_RESOURCE);
        saveData.saveCurrentData();
        return outputFile;
    }


}
