package com.taobao.android.builder.tasks.appbundles;

import com.android.annotations.NonNull;
import com.android.annotations.VisibleForTesting;
import com.android.build.api.artifact.BuildableArtifact;
import com.android.build.gradle.api.BaseVariantOutput;
import com.android.build.gradle.internal.api.VariantContext;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.dsl.AaptOptions;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.AndroidBuilderTask;
import com.android.build.gradle.internal.tasks.TaskInputHelper;
import com.android.build.gradle.internal.tasks.Workers;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.tasks.ProcessApplicationManifest;
import com.android.build.gradle.tasks.ResourceException;
import com.android.builder.core.BuilderConstants;
import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.SourceProvider;
import com.android.ide.common.resources.*;
import com.android.ide.common.workers.WorkerExecutorFacade;
import com.android.utils.FileUtils;
import com.google.common.collect.Lists;
import com.taobao.android.builder.dependency.model.AwbBundle;
import com.taobao.android.builder.tasks.manager.MtlBaseTaskAction;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.*;
import org.gradle.api.tasks.Optional;
import org.gradle.workers.WorkerExecutor;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * @ClassName MergeFeatureAssets
 * @Description TODO
 * @Author zhayu.ll
 * @Date 2019-08-22 15:43
 * @Version 1.0
 */
public class MergeFeatureAssets extends AndroidBuilderTask {


    private Provider<File> outputDir;

    private AwbBundle awbBundle;

    @OutputDirectory
    public Provider<File> getOutputDir() {
        return outputDir;
    }

    // ----- PRIVATE TASK API -----

    // file inputs as raw files, lazy behind a memoized/bypassed supplier
    private Supplier<Collection<File>> sourceFolderInputs;

    // supplier of the assets set, for execution only.
    private Supplier<List<AssetSet>> assetSetSupplier;

    // for the dependencies
    private Supplier<Collection<AndroidLibrary>> libraries = null;

    private BuildableArtifact shadersOutputDir = null;
    private FileCollection copyApk = null;
    private String ignoreAssets = null;

    private final FileValidity<AssetSet> fileValidity = new FileValidity<>();

    private final WorkerExecutorFacade workerExecutor;

    @Inject
    public MergeFeatureAssets(WorkerExecutor workerExecutor) {
        this.workerExecutor = Workers.INSTANCE.getWorker(workerExecutor);
    }

    @Internal
    protected boolean isIncremental() {
        return false;
    }

    protected void doFullTaskAction() throws IOException {
        // this is full run, clean the previous output
        File destinationDir = getOutputDir().get();
        FileUtils.cleanOutputDir(destinationDir);

        List<AssetSet> assetSets = computeAssetSetList();

        // create a new merger and populate it with the sets.
        AssetMerger merger = new AssetMerger();

        try (WorkerExecutorFacade workerExecutor = this.workerExecutor) {
            for (AssetSet assetSet : assetSets) {
                // set needs to be loaded.
                assetSet.loadFromFiles(getILogger());
                merger.addDataSet(assetSet);
            }

            // get the merged set and write it down.
            MergedAssetWriter writer = new MergedAssetWriter(destinationDir, workerExecutor);

            merger.mergeData(writer, false /*doCleanUp*/);

            // No exception? Write the known state.
        } catch (MergingException e) {
            getLogger().error("Could not merge source set folders: ", e);
            throw new ResourceException(e.getMessage(), e);
        }
    }


    @InputFiles
    @Optional
    @PathSensitive(PathSensitivity.RELATIVE)
    public BuildableArtifact getShadersOutputDir() {
        return shadersOutputDir;
    }

    @VisibleForTesting
    void setShadersOutputDir(BuildableArtifact shadersOutputDir) {
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
            assetSetList = assetSets;
        } else {
            int size = assetSets.size() + 3;
            if (libraries != null) {
                size += libraries.get().size();
            }

            assetSetList = Lists.newArrayListWithExpectedSize(size);

            // get the dependency base assets sets.
            // add at the beginning since the libraries are less important than the folder based
            // asset sets.
            if (libraries != null) {
                // the order of the artifact is descending order, so we need to reverse it.
                Collection<AndroidLibrary> featureLibraies = libraries.get();
                for (AndroidLibrary artifact : featureLibraies) {
                    AssetSet assetSet =
                            new AssetSet(artifact.getName());
                    assetSet.addSource(artifact.getAssetsFolder());

                    // add to 0 always, since we need to reverse the order.
                    assetSetList.add(0, assetSet);
                }
            }
            AssetSet assetSet =
                    new AssetSet(awbBundle.getName());
            assetSet.addSource(awbBundle.getAndroidLibrary().getAssetsFolder());

            assetSetList.add(0, assetSet);

        }
        if (ignoreAssets != null) {
            for (AssetSet set : assetSetList) {
                set.setIgnoredPatterns(ignoreAssets);
            }
        }

        return assetSetList;
    }


    protected static class CreationAction
            extends MtlBaseTaskAction<MergeFeatureAssets> {

        protected AwbBundle awbBundle;

        protected CreationAction(AwbBundle awbBundle, VariantContext variantContext, BaseVariantOutput baseVariantOutput) {
            super(variantContext, baseVariantOutput);
            this.awbBundle = awbBundle;
        }

        @NonNull
        @Override
        public Class<MergeFeatureAssets> getType() {
            return MergeFeatureAssets.class;
        }

        @NonNull
        @Override
        public String getName() {
            return getVariantScope().getTaskName("mergeFeature", awbBundle.getName() + "Assets");
        }

        @Override
        public void configure(@NonNull MergeFeatureAssets task) {
            super.configure(task);
            VariantScope scope = getVariantScope();
            final BaseVariantData variantData = scope.getVariantData();
            final GradleVariantConfiguration variantConfig = variantData.getVariantConfiguration();
            task.awbBundle = awbBundle;
            final Function<SourceProvider, Collection<File>> assetDirFunction =
                    SourceProvider::getAssetsDirectories;
            task.assetSetSupplier = () -> variantConfig.getSourceFilesAsAssetSets(assetDirFunction);
            task.sourceFolderInputs = () -> variantConfig.getSourceFiles(assetDirFunction);

            task.shadersOutputDir =
                    scope.getArtifacts().getFinalArtifactFiles(InternalArtifactType.SHADER_ASSETS);

            AaptOptions options = scope.getGlobalScope().getExtension().getAaptOptions();
            if (options != null) {
                task.ignoreAssets = options.getIgnoreAssets();
            }

            task.libraries = () -> awbBundle.getAndroidLibraries();

            task.outputDir = TaskInputHelper.memoizeToProvider(task.getProject(), new Supplier<File>() {
                @Override
                public File get() {
                    return getAppVariantOutputContext().getFeatureMergeAssetsFolder(scope.getVariantConfiguration(), awbBundle);
                }
            });


            task.dependsOn(scope.getTaskContainer().getAssetGenTask());
        }
    }

}
