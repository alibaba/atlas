package com.taobao.android.builder.tasks.app.merge.bundle;

import com.android.annotations.NonNull;
import com.android.annotations.VisibleForTesting;
import com.android.build.gradle.api.BaseVariantOutput;
import com.android.build.gradle.internal.api.AppVariantContext;
import com.android.build.gradle.internal.api.VariantContext;
import com.android.build.gradle.internal.dsl.AaptOptions;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.IncrementalTask;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.tasks.MergeSourceSetFolders;
import com.android.build.gradle.tasks.ResourceException;
import com.android.build.gradle.tasks.WorkerExecutorAdapter;
import com.android.builder.core.VariantConfiguration;
import com.android.builder.core.VariantType;
import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.SourceProvider;
import com.android.ide.common.res2.*;
import com.android.ide.common.workers.WorkerExecutorFacade;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.taobao.android.builder.dependency.model.AwbBundle;
import com.taobao.android.builder.tasks.manager.MtlBaseTaskAction;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.*;
import org.gradle.workers.WorkerExecutor;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * @author lilong
 * @create 2017-12-04 下午5:16
 */

public class MergeAwbAssets extends IncrementalTask{

    // ----- PUBLIC TASK API -----

    private File outputDir;
    private AwbBundle awbBundle;

    private AppVariantContext variantContext;

    @OutputDirectory
    public File getOutputDir() {
        return outputDir;
    }

    public void setOutputDir(File outputDir) {
        this.outputDir = outputDir;
    }

    // ----- PRIVATE TASK API -----

    // file inputs as raw files, lazy behind a memoized/bypassed supplier
    private Supplier<Collection<File>> sourceFolderInputs;

    // supplier of the assets set, for execution only.
    private Supplier<List<AssetSet>> assetSetSupplier;

    // for the dependencies
    private List<AndroidLibrary> libraries = null;

    private FileCollection shadersOutputDir = null;
    private FileCollection copyApk = null;
    private String ignoreAssets = null;

    private final FileValidity<AssetSet> fileValidity = new FileValidity<>();

    private final WorkerExecutorFacade<MergedAssetWriter.AssetWorkParameters> workerExecutor;

    @Inject
    public MergeAwbAssets(WorkerExecutor workerExecutor) {
        this.workerExecutor = new WorkerExecutorAdapter<>(workerExecutor, MergeSourceSetFolders.AssetWorkAction.class);
    }

    @Override
    @Internal
    protected boolean isIncremental() {
        return true;
    }

    @Override
    protected void doFullTaskAction() throws IOException {
        if (awbBundle.isMBundle){
            return;
        }
        // this is full run, clean the previous output
        File destinationDir = getOutputDir();
        FileUtils.cleanOutputDir(destinationDir);

        List<AssetSet> assetSets = computeAssetSetList();

        // create a new merger and populate it with the sets.
        AssetMerger merger = new AssetMerger();

        try {
            for (AssetSet assetSet : assetSets) {
                // set needs to be loaded.
                assetSet.loadFromFiles(getILogger());
                merger.addDataSet(assetSet);
            }

            // get the merged set and write it down.
            MergedAssetWriter writer = new MergedAssetWriter(destinationDir, workerExecutor);

            merger.mergeData(writer, false /*doCleanUp*/);

            // No exception? Write the known state.
            merger.writeBlobTo(getIncrementalFolder(), writer, false);
        } catch (MergingException e) {
            getLogger().error("Could not merge source set folders: ", e);
            merger.cleanBlob(getIncrementalFolder());
            throw new ResourceException(e.getMessage(), e);
        }
//        if (awbBundle.mBundle){
//            org.apache.commons.io.FileUtils.moveDirectoryToDirectory(destinationDir,variantContext.getVariantData().mergeAssetsTask.getOutputDir(),true);
//        }
    }

    @Override
    protected void doIncrementalTaskAction(Map<File, FileStatus> changedInputs) throws IOException {
        // create a merger and load the known state.
        AssetMerger merger = new AssetMerger();
        try {
            if (!merger.loadFromBlob(getIncrementalFolder(), true /*incrementalState*/)) {
                doFullTaskAction();
                return;
            }

            // compare the known state to the current sets to detect incompatibility.
            // This is in case there's a change that's too hard to do incrementally. In this case
            // we'll simply revert to full build.
            List<AssetSet> assetSets = computeAssetSetList();

            if (!merger.checkValidUpdate(assetSets)) {
                getLogger().info("Changed Asset sets: full task run!");
                doFullTaskAction();
                return;

            }

            // The incremental process is the following:
            // Loop on all the changed files, find which ResourceSet it belongs to, then ask
            // the resource set to update itself with the new file.
            for (Map.Entry<File, FileStatus> entry : changedInputs.entrySet()) {
                File changedFile = entry.getKey();

                // Ignore directories.
                if (changedFile.isDirectory()) {
                    continue;
                }

                merger.findDataSetContaining(changedFile, fileValidity);
                if (fileValidity.getStatus() == FileValidity.FileStatus.UNKNOWN_FILE) {
                    doFullTaskAction();
                    return;

                } else if (fileValidity.getStatus() == FileValidity.FileStatus.VALID_FILE) {
                    if (!fileValidity.getDataSet().updateWith(
                            fileValidity.getSourceFile(),
                            changedFile,
                            entry.getValue(),
                            getILogger())) {
                        getLogger().info(
                                "Failed to process {} event! Full task run", entry.getValue());
                        doFullTaskAction();
                        return;
                    }
                }
            }

            MergedAssetWriter writer = new MergedAssetWriter(getOutputDir(), workerExecutor);

            merger.mergeData(writer, false /*doCleanUp*/);

            // No exception? Write the known state.
            merger.writeBlobTo(getIncrementalFolder(), writer, false);
        } catch (MergingException e) {
            getLogger().error("Could not merge source set folders: ", e);
            merger.cleanBlob(getIncrementalFolder());
            throw new ResourceException(e.getMessage(), e);
        } finally {
            // some clean up after the task to help multi variant/module builds.
            fileValidity.clear();
        }
    }

    public static class AssetWorkAction implements Runnable {

        private final MergedAssetWriter.AssetWorkAction workAction;

        @Inject
        public AssetWorkAction(MergedAssetWriter.AssetWorkParameters workItem) {
            workAction = new MergedAssetWriter.AssetWorkAction(workItem);
        }

        @Override
        public void run() {
            workAction.run();
        }
    }

    @Optional
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public FileCollection getLibraries() {
        List<File>files = new ArrayList<>();
        if (libraries != null) {
            for (AndroidLibrary androidLibrary:libraries){
                files.add(androidLibrary.getAssetsFolder());
            }
        }
        files.add(awbBundle.getAndroidLibrary().getAssetsFolder());
        return getProject().files(files);
    }

    @VisibleForTesting
    public void setLibraries(@NonNull List<AndroidLibrary> libraries) {
        this.libraries = libraries;
    }

    @InputFiles
    @Optional
    @PathSensitive(PathSensitivity.RELATIVE)
    public FileCollection getShadersOutputDir() {
        return shadersOutputDir;
    }

    @VisibleForTesting
    void setShadersOutputDir(FileCollection shadersOutputDir) {
        this.shadersOutputDir = shadersOutputDir;
    }

    @InputFiles
    @Optional
    @PathSensitive(PathSensitivity.RELATIVE)
    public FileCollection getCopyApk() {
        return copyApk;
    }

    @VisibleForTesting
    void setCopyApk(FileCollection copyApk) {
        this.copyApk = copyApk;
    }

    @Input
    @Optional
    public String getIgnoreAssets() {
        return ignoreAssets;
    }

    @VisibleForTesting
    void setAssetSetSupplier(Supplier<List<AssetSet>> assetSetSupplier) {
        this.assetSetSupplier = assetSetSupplier;
    }

    // input list for the source folder based asset folders.
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public Collection<File> getSourceFolderInputs() {
        return sourceFolderInputs.get();
    }

    /**
     * Compute the list of Asset set to be used during execution based all the inputs.
     */
    @VisibleForTesting
    List<AssetSet> computeAssetSetList() {
        List<AssetSet> assetSetList;
        List<AssetSet> assetSets = assetSetSupplier.get();
        if (copyApk == null
                && shadersOutputDir == null
                && ignoreAssets == null
                && libraries == null) {
            assetSetList = new ArrayList<>(assetSets);
        } else {
            int size = assetSets.size() + 3;
            if (libraries != null) {
                size += libraries.size();
            }

            assetSetList = Lists.newArrayListWithExpectedSize(size);

            // get the dependency base assets sets.
            // add at the beginning since the libraries are less important than the folder based
            // asset sets.
            if (libraries != null) {
                // the order of the artifact is descending order, so we need to reverse it.
                List<? extends AndroidLibrary> bundleDeps = awbBundle.getAndroidLibraries();
                // the list of dependency must be reversed to use the right overlay order.
                for (int n = bundleDeps.size() - 1; n >= 0; n--) {
                    AndroidLibrary dependency = bundleDeps.get(n);

                    File assetFolder = dependency.getAssetsFolder();
                    if (assetFolder.isDirectory()) {
                        AssetSet assetSet = new AssetSet(dependency.getName());
                        assetSet.addSource(assetFolder);
                        assetSetList.add(assetSet);
                    }
                }

            }
                File awbAssetFolder = awbBundle.getAndroidLibrary().getAssetsFolder();
                if (awbAssetFolder.isDirectory()) {
                    AssetSet awbAssetSet = new AssetSet(awbBundle.getAndroidLibrary().getFolder().getName());
                    awbAssetSet.addSource(awbAssetFolder);
                    assetSetList.add(awbAssetSet);
                }



            // add the generated folders to the first set of the folder-based sets.
            List<File> generatedAssetFolders = Lists.newArrayList();

            if (shadersOutputDir != null) {
                generatedAssetFolders.addAll(shadersOutputDir.getFiles());
            }

            if (copyApk != null) {
                generatedAssetFolders.addAll(copyApk.getFiles());
            }

//            // for awb no need to add main assets
//            final AssetSet mainAssetSet = assetSets.get(0);
//            assert mainAssetSet.getConfigName().equals(BuilderConstants.MAIN);
//            mainAssetSet.addSources(generatedAssetFolders);

            assetSetList.addAll(assetSets);
        }

        if (ignoreAssets != null) {
            for (AssetSet set : assetSetList) {
                set.setIgnoredPatterns(ignoreAssets);
            }
        }

        return assetSetList;
    }


    public static class MergeAwbAssetConfigAction extends MtlBaseTaskAction<MergeAwbAssets> {
        @NonNull
        protected final VariantScope scope;

        @NonNull protected final File outputDir;

        private AwbBundle awbBundle;

        public MergeAwbAssetConfigAction(VariantContext variantContext, BaseVariantOutput baseVariantOutput, AwbBundle awbBundle) {
            super(variantContext, baseVariantOutput);
            this.variantContext = variantContext;
            this.scope = variantContext.getScope();
            this.awbBundle = awbBundle;
            this.outputDir = variantContext.getMergeAssets(awbBundle);
        }

        @Override
        public String getName() {
            return variantContext.getScope().getTaskName("merge", "Assets[" + awbBundle.getName() + "]");
        }

        @NonNull
        @Override
        public Class<MergeAwbAssets> getType() {
            return MergeAwbAssets.class;
        }

        @Override
        public void execute(@NonNull MergeAwbAssets mergeAssetsTask) {
            BaseVariantData variantData = scope.getVariantData();
            VariantConfiguration variantConfig = variantData.getVariantConfiguration();
            mergeAssetsTask.setAndroidBuilder(scope.getGlobalScope().getAndroidBuilder());
            mergeAssetsTask.setVariantName(variantConfig.getFullName());
            mergeAssetsTask.setIncrementalFolder(scope.getIncrementalDir(getName()));
            final Project project = scope.getGlobalScope().getProject();

            final Function<SourceProvider, Collection<File>> assetDirFunction =
                    SourceProvider::getAssetsDirectories;
//            mergeAssetsTask.assetSetSupplier =
//                    () -> variantConfig.getSourceFilesAsAssetSets(assetDirFunction);

            mergeAssetsTask.assetSetSupplier = ()-> ImmutableList.of();
            mergeAssetsTask.sourceFolderInputs = () -> ImmutableList.of();
//            mergeAssetsTask.sourceFolderInputs =
//                    TaskInputHelper.bypassFileSupplier(
//                            () -> variantConfig.getSourceFiles(assetDirFunction));

            mergeAssetsTask.shadersOutputDir = project.files(variantContext.getAwbShadersOutputDir(awbBundle));
            if (variantData.copyApkTask != null) {
                mergeAssetsTask.copyApk = project.files(variantData.copyApkTask.getDestinationDir());
            }

            AaptOptions options = scope.getGlobalScope().getExtension().getAaptOptions();
            if (options != null) {
                mergeAssetsTask.ignoreAssets = options.getIgnoreAssets();
            }

            if (!variantConfig.getType().equals(VariantType.LIBRARY)) {
                mergeAssetsTask.libraries = awbBundle.getAndroidLibraries();
            }

            mergeAssetsTask.awbBundle = awbBundle;
            mergeAssetsTask.variantContext = (AppVariantContext) this.variantContext;
            mergeAssetsTask.setOutputDir(outputDir);
        }
    }

    public static class MergeAwbJniLibFoldersConfigAction extends MtlBaseTaskAction<MergeAwbAssets> {

        private AwbBundle awbBundle;
        public MergeAwbJniLibFoldersConfigAction(VariantContext variantContext, BaseVariantOutput baseVariantOutput, AwbBundle awbBundle) {
            super(variantContext,baseVariantOutput);
            this.awbBundle = awbBundle;
        }

        @NonNull
        @Override
        public String getName() {
            return scope.getTaskName("merge", "JniLibFolders["+awbBundle.getName()+"]");
        }

        @Override
        public Class<MergeAwbAssets> getType() {
            return MergeAwbAssets.class;
        }

        @Override
        public void execute(@NonNull MergeAwbAssets mergeAssetsTask) {
            BaseVariantData variantData = scope.getVariantData();
            VariantConfiguration variantConfig = variantData.getVariantConfiguration();

            mergeAssetsTask.setAndroidBuilder(scope.getGlobalScope().getAndroidBuilder());
            mergeAssetsTask.setVariantName(variantConfig.getFullName());
            mergeAssetsTask.setIncrementalFolder(scope.getIncrementalDir(getName()));
//            final Project project = scope.getGlobalScope().getProject();
//            final Function<SourceProvider, Collection<File>> assetDirFunction =
//                    SourceProvider::getJniLibsDirectories;
//            mergeAssetsTask.assetSetSupplier =
//                    () -> variantConfig.getSourceFilesAsAssetSets(assetDirFunction);
//            mergeAssetsTask.sourceFolderInputs =
//                    TaskInputHelper.bypassFileSupplier(
//                            () -> variantConfig.getSourceFiles(assetDirFunction));

            mergeAssetsTask.assetSetSupplier = () -> ImmutableList.of();
            mergeAssetsTask.sourceFolderInputs = ()-> ImmutableList.of();
            mergeAssetsTask.awbBundle = awbBundle;
            if (!variantConfig.getType().equals(VariantType.LIBRARY)) {
                mergeAssetsTask.libraries = awbBundle.getAndroidLibraries();
            }
            mergeAssetsTask.setOutputDir(variantContext.getAwbMergeNativeLibsOutputDir(awbBundle));
        }
    }

    public static class MergeAwbShaderSourceFoldersConfigAction extends MtlBaseTaskAction<MergeAwbAssets> {

        private AwbBundle awbBundle;

        public MergeAwbShaderSourceFoldersConfigAction(VariantContext variantContext, BaseVariantOutput baseVariantOutput, AwbBundle awbBundle) {
            super(variantContext,baseVariantOutput);
            this.awbBundle = awbBundle;
        }

        @NonNull
        @Override
        public String getName() {
            return scope.getTaskName("merge", "Shaders["+awbBundle.getName()+"]");
        }

        @Override
        public Class getType() {
            return MergeAwbAssets.class;
        }

        @Override
        public void execute(@NonNull MergeAwbAssets mergeAssetsTask) {
            BaseVariantData variantData = scope.getVariantData();
            VariantConfiguration variantConfig = variantData.getVariantConfiguration();

            mergeAssetsTask.setAndroidBuilder(scope.getGlobalScope().getAndroidBuilder());
            mergeAssetsTask.setVariantName(variantConfig.getFullName());
            mergeAssetsTask.setIncrementalFolder(scope.getIncrementalDir(getName()));
            final Project project = scope.getGlobalScope().getProject();

            final Function<SourceProvider, Collection<File>> assetDirFunction =
                    SourceProvider::getShadersDirectories;
//            mergeAssetsTask.assetSetSupplier =
//                    () -> variantConfig.getSourceFilesAsAssetSets(assetDirFunction);
//            mergeAssetsTask.sourceFolderInputs =
//                    TaskInputHelper.bypassFileSupplier(
//                            () -> variantConfig.getSourceFiles(assetDirFunction));
            mergeAssetsTask.assetSetSupplier = () -> ImmutableList.of();
            mergeAssetsTask.sourceFolderInputs = () -> ImmutableList.of();

            mergeAssetsTask.awbBundle = awbBundle;
            if (!variantConfig.getType().equals(VariantType.LIBRARY)) {
                mergeAssetsTask.libraries = awbBundle.getAndroidLibraries();
            }
            mergeAssetsTask.setOutputDir(variantContext.getMergeShadersOutputDir(awbBundle));
        }
    }
}
