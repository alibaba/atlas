package com.taobao.android.builder.tasks.app.merge.bundle;

import android.databinding.tool.LayoutXmlProcessor;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.api.BaseVariantOutput;
import com.android.build.gradle.internal.CombinedInput;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.internal.aapt.AaptGeneration;
import com.android.build.gradle.internal.aapt.AaptGradleFactory;
import com.android.build.gradle.internal.api.AppVariantContext;
import com.android.build.gradle.internal.api.VariantContext;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.IncrementalTask;
import com.android.build.gradle.internal.tasks.TaskInputHelper;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.tasks.MergeResources;
import com.android.build.gradle.tasks.ResourceException;
import com.android.build.gradle.tasks.WorkerExecutorAdapter;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.internal.aapt.Aapt;
import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.VectorDrawablesOptions;
import com.android.builder.png.VectorDrawableRenderer;
import com.android.builder.utils.FileCache;
import com.android.ide.common.blame.MergingLog;
import com.android.ide.common.blame.MergingLogRewriter;
import com.android.ide.common.blame.ParsingProcessOutputHandler;
import com.android.ide.common.blame.parser.ToolOutputParser;
import com.android.ide.common.blame.parser.aapt.Aapt2OutputParser;
import com.android.ide.common.blame.parser.aapt.AaptOutputParser;
import com.android.ide.common.process.LoggedProcessOutputHandler;
import com.android.ide.common.res2.*;
import com.android.ide.common.vectordrawable.ResourcesNotSupportedException;
import com.android.ide.common.workers.WorkerExecutorFacade;
import com.android.resources.Density;
import com.android.utils.FileUtils;
import com.android.utils.ILogger;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.taobao.android.builder.dependency.model.AwbBundle;
import com.taobao.android.builder.tasks.manager.MtlBaseTaskAction;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.*;
import org.gradle.api.tasks.Optional;
import org.gradle.workers.WorkerExecutor;
import org.jaxen.JaxenException;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * @author lilong
 * @create 2017-12-01 下午4:30
 */

@CacheableTask
public class MergeAwbResource extends IncrementalTask {

    private AwbBundle awbBundle;


    public void setAwbBundle(AwbBundle awbBundle) {
        this.awbBundle = awbBundle;
    }

    private File outputDir;


    private SingleFileProcessor layoutXmlProcessor;
    private File generatedPngsOutputDir;

    // ----- PRIVATE TASK API -----

    /**
     * Optional file to write any publicly imported resource types and names to
     */
    private File publicFile;

    private boolean processResources;

    private boolean crunchPng;

    private boolean validateEnabled;

    private File blameLogFolder;

    @Nullable
    private FileCache fileCache;

    // file inputs as raw files, lazy behind a memoized/bypassed supplier
    private Supplier<Collection<File>> sourceFolderInputs;
    private Supplier<List<ResourceSet>> resSetSupplier;

    private List<ResourceSet> processedInputs;

    private List<AndroidLibrary> libraries;

    private FileCollection renderscriptResOutputDir;
    private FileCollection generatedResOutputDir;
    private FileCollection microApkResDirectory;
    private FileCollection extraGeneratedResFolders;

    private final FileValidity<ResourceSet> fileValidity = new FileValidity<>();

    private boolean disableVectorDrawables;

    private Collection<String> generatedDensities;

    private int minSdk;

    private VariantScope variantScope;

    private AaptGeneration aaptGeneration;

//    @Nullable private SingleFileProcessor dataBindingLayoutProcessor;

    /** Where data binding exports its outputs after parsing layout files. */
    @Nullable private File dataBindingLayoutInfoOutFolder;

    @Nullable private File mergedNotCompiledResourcesOutputDirectory;

    private boolean pseudoLocalesEnabled;

    @NonNull
    private static Aapt makeAapt(
            @NonNull AaptGeneration aaptGeneration,
            @NonNull AndroidBuilder builder,
            @Nullable FileCache fileCache,
            boolean crunchPng,
            @NonNull VariantScope scope,
            @NonNull File intermediateDir,
            @Nullable MergingLog blameLog)
            throws IOException {
        return AaptGradleFactory.make(
                aaptGeneration,
                builder,
                blameLog != null
                        ? new ParsingProcessOutputHandler(
                        new ToolOutputParser(
                                aaptGeneration == AaptGeneration.AAPT_V1
                                        ? new AaptOutputParser()
                                        : new Aapt2OutputParser(),
                                builder.getLogger()),
                        new MergingLogRewriter(blameLog::find, builder.getErrorReporter()))
                        : new LoggedProcessOutputHandler(
                        new AaptGradleFactory.FilteringLogger(builder.getLogger())),
                fileCache,
                crunchPng,
                intermediateDir,
                scope.getGlobalScope().getExtension().getAaptOptions().getCruncherProcesses());
    }

    @Input
    public String getBuildToolsVersion() {
        return getBuildTools().getRevision().toString();
    }

    @Override
    protected boolean isIncremental() {
        return false;
    }

    @OutputDirectory
    @Optional
    public File getDataBindingLayoutInfoOutFolder() {
        return dataBindingLayoutInfoOutFolder;
    }

    private final WorkerExecutorFacade<MergedResourceWriter.FileGenerationParameters>
            workerExecutorFacade;

    @Inject
    public MergeAwbResource(WorkerExecutor workerExecutor) {
        this.workerExecutorFacade =
                new WorkerExecutorAdapter<>(workerExecutor, MergeAwbResource.FileGenerationWorkAction.class);
    }

    @Override
    protected void doFullTaskAction() throws IOException, ExecutionException {
        if (awbBundle.isMBundle){
            return;
        }
        ResourcePreprocessor preprocessor = getPreprocessor();

        // this is full run, clean the previous output
        File destinationDir = getOutputDir();
        FileUtils.cleanOutputDir(destinationDir);

        List<ResourceSet> resourceSets = getConfiguredResourceSets(preprocessor);

        // create a new merger and populate it with the sets.
        ResourceMerger merger = new ResourceMerger(minSdk);
        MergingLog mergingLog =
                getBlameLogFolder() != null ? new MergingLog(getBlameLogFolder()) : null;

        try (QueueableResourceCompiler resourceCompiler =
                     processResources
                             ? makeAapt(
                             aaptGeneration,
                             getBuilder(),
                             fileCache,
                             crunchPng,
                             variantScope,
                             getAaptTempDir(),
                             mergingLog)
                             : QueueableResourceCompiler.NONE) {

            for (ResourceSet resourceSet : resourceSets) {
                resourceSet.loadFromFiles(getILogger());
                merger.addDataSet(resourceSet);
            }

            MergedResourceWriter writer =
                    new MergedResourceWriter(
                            workerExecutorFacade,
                            destinationDir,
                            getPublicFile(),
                            mergingLog,
                            preprocessor,
                            resourceCompiler,
                            getIncrementalFolder(),
                            null,
//                            layoutXmlProcessor,//dataBindingLayoutProcessor,
                            mergedNotCompiledResourcesOutputDirectory,
                            pseudoLocalesEnabled,
                            getCrunchPng());

            merger.mergeData(writer, false /*doCleanUp*/);

            if (layoutXmlProcessor != null) {
                try {
                    layoutXmlProcessor.end();

                }catch (Exception e){

                }
            }

            // No exception? Write the known state.
            merger.writeBlobTo(getIncrementalFolder(), writer, false);
        } catch (MergingException e) {
            System.out.println(e.getMessage());
            merger.cleanBlob(getIncrementalFolder());
            throw new ResourceException(e.getMessage(), e);
        } finally {
            cleanup();
        }
    }

    @Override
    protected void doIncrementalTaskAction(Map<File, FileStatus> changedInputs)
            throws IOException, ExecutionException {
        ResourcePreprocessor preprocessor = getPreprocessor();

        // create a merger and load the known state.
        ResourceMerger merger = new ResourceMerger(minSdk);
        try {
            if (!merger.loadFromBlob(getIncrementalFolder(), true /*incrementalState*/)) {
                doFullTaskAction();
                return;
            }

            for (ResourceSet resourceSet : merger.getDataSets()) {
                resourceSet.setPreprocessor(preprocessor);
            }

            List<ResourceSet> resourceSets = getConfiguredResourceSets(preprocessor);

            // compare the known state to the current sets to detect incompatibility.
            // This is in case there's a change that's too hard to do incrementally. In this case
            // we'll simply revert to full build.
            if (!merger.checkValidUpdate(resourceSets)) {
                getLogger().info("Changed Resource sets: full task run!");
                doFullTaskAction();
                return;
            }

            // The incremental process is the following:
            // Loop on all the changed files, find which ResourceSet it belongs to, then ask
            // the resource set to update itself with the new file.
            for (Map.Entry<File, FileStatus> entry : changedInputs.entrySet()) {
                File changedFile = entry.getKey();

                merger.findDataSetContaining(changedFile, fileValidity);
                if (fileValidity.getStatus() == FileValidity.FileStatus.UNKNOWN_FILE) {
                    doFullTaskAction();
                    return;
                } else if (fileValidity.getStatus() == FileValidity.FileStatus.VALID_FILE) {
                    if (!fileValidity.getDataSet().updateWith(
                            fileValidity.getSourceFile(), changedFile, entry.getValue(),
                            getILogger())) {
                        getLogger().info(
                                String.format("Failed to process %s event! Full task run",
                                        entry.getValue()));
                        doFullTaskAction();
                        return;
                    }
                }
            }

            MergingLog mergingLog =
                    getBlameLogFolder() != null ? new MergingLog(getBlameLogFolder()) : null;

            try (QueueableResourceCompiler resourceCompiler =
                         processResources
                                 ? makeAapt(
                                 aaptGeneration,
                                 getBuilder(),
                                 fileCache,
                                 crunchPng,
                                 variantScope,
                                 getAaptTempDir(),
                                 mergingLog)
                                 : QueueableResourceCompiler.NONE) {

                MergedResourceWriter writer =
                        new MergedResourceWriter(
                                workerExecutorFacade,
                                getOutputDir(),
                                getPublicFile(),
                                mergingLog,
                                preprocessor,
                                resourceCompiler,
                                getIncrementalFolder(),
                                null,//dataBindingLayoutProcessor,
                                mergedNotCompiledResourcesOutputDirectory,
                                pseudoLocalesEnabled,
                                getCrunchPng());

                merger.mergeData(writer, false /*doCleanUp*/);

//                if (dataBindingLayoutProcessor != null) {
//                    dataBindingLayoutProcessor.end();
//                }

                // No exception? Write the known state.
                merger.writeBlobTo(getIncrementalFolder(), writer, false);
            }
        } catch (MergingException e) {
            merger.cleanBlob(getIncrementalFolder());
            throw new ResourceException(e.getMessage(), e);
        } finally {
            cleanup();
        }
    }

    public static class FileGenerationWorkAction implements Runnable {

        private final MergedResourceWriter.FileGenerationWorkAction workAction;

        @Inject
        public FileGenerationWorkAction(MergedResourceWriter.FileGenerationParameters workItem) {
            this.workAction = new MergedResourceWriter.FileGenerationWorkAction(workItem);
        }

        @Override
        public void run() {
            workAction.run();
        }
    }

    private static class MergeResourcesVectorDrawableRenderer extends VectorDrawableRenderer {

        public MergeResourcesVectorDrawableRenderer(
                int minSdk,
                File outputDir,
                Collection<Density> densities,
                Supplier<ILogger> loggerSupplier) {
            super(minSdk, outputDir, densities, loggerSupplier);
        }

        @Override
        public void generateFile(File toBeGenerated, File original) throws IOException {
            try {
                super.generateFile(toBeGenerated, original);
            } catch (ResourcesNotSupportedException e) {
                // Add gradle-specific error message.
                throw new GradleException(
                        String.format(
                                "Can't process attribute %1$s=\"%2$s\": "
                                        + "references to other resources are not supported by "
                                        + "build-time PNG generation. "
                                        + "See http://developer.android.com/tools/help/vector-asset-studio.html "
                                        + "for details.",
                                e.getName(), e.getValue()));
            }
        }
    }

    @NonNull
    private ResourcePreprocessor getPreprocessor() {
        // Only one pre-processor for now. The code will need slight changes when we add more.

        if (isDisableVectorDrawables()) {
            // If the user doesn't want any PNGs, leave the XML file alone as well.
            return NoOpResourcePreprocessor.INSTANCE;
        }

        Collection<Density> densities =
                getGeneratedDensities().stream().map(Density::getEnum).collect(Collectors.toList());

        return new MergeAwbResource.MergeResourcesVectorDrawableRenderer(
                getMinSdk(),
                getGeneratedPngsOutputDir(),
                densities,
                LoggerWrapper.supplierFor(MergeAwbResource.class));
    }

    @NonNull
    private List<ResourceSet> getConfiguredResourceSets(ResourcePreprocessor preprocessor) {
        // it is possible that this get called twice in case the incremental run fails and reverts
        // back to full task run. Because the cached ResourceList is modified we don't want
        // to recompute this twice (plus, why recompute it twice anyway?)
        if (processedInputs == null) {
            processedInputs = computeResourceSetList();
            List<ResourceSet> generatedSets = Lists.newArrayListWithCapacity(processedInputs.size());

            for (ResourceSet resourceSet : processedInputs) {
                resourceSet.setPreprocessor(preprocessor);
                ResourceSet generatedSet = new GeneratedResourceSet(resourceSet);
                resourceSet.setGeneratedSet(generatedSet);
                generatedSets.add(generatedSet);
            }

            // We want to keep the order of the inputs. Given inputs:
            // (A, B, C, D)
            // We want to get:
            // (A-generated, A, B-generated, B, C-generated, C, D-generated, D).
            // Therefore, when later in {@link DataMerger} we look for sources going through the
            // list backwards, B-generated will take priority over A (but not B).
            // A real life use-case would be if an app module generated resource overrode a library
            // module generated resource (existing not in generated but bundled dir at this stage):
            // (lib, app debug, app main)
            // We will get:
            // (lib generated, lib, app debug generated, app debug, app main generated, app main)
            for (int i = 0; i < generatedSets.size(); ++i) {
                processedInputs.add(2 * i, generatedSets.get(i));
            }
        }

        return processedInputs;
    }

    /**
     * Release resource sets not needed any more, otherwise they will waste heap space for the
     * duration of the build.
     *
     * <p>This might be called twice when an incremental build falls back to a full one.
     */
    private void cleanup() {
        fileValidity.clear();
        processedInputs = null;
    }

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public FileCollection getRenderscriptResOutputDir() {
        return renderscriptResOutputDir;
    }

    @VisibleForTesting
    void setRenderscriptResOutputDir(@NonNull FileCollection renderscriptResOutputDir) {
        this.renderscriptResOutputDir = renderscriptResOutputDir;
    }

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public FileCollection getGeneratedResOutputDir() {
        return generatedResOutputDir;
    }

    @VisibleForTesting
    void setGeneratedResOutputDir(@NonNull FileCollection generatedResOutputDir) {
        this.generatedResOutputDir = generatedResOutputDir;
    }

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    @Optional
    public FileCollection getMicroApkResDirectory() {
        return microApkResDirectory;
    }

    @VisibleForTesting
    void setMicroApkResDirectory(@NonNull FileCollection microApkResDirectory) {
        this.microApkResDirectory = microApkResDirectory;
    }

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    @Optional
    public FileCollection getExtraGeneratedResFolders() {
        return extraGeneratedResFolders;
    }

    @VisibleForTesting
    void setExtraGeneratedResFolders(@NonNull FileCollection extraGeneratedResFolders) {
        this.extraGeneratedResFolders = extraGeneratedResFolders;
    }

    @Optional
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public FileCollection getLibraries() {
        List<File>resFiles = new ArrayList<>();
        for (AndroidLibrary androidLibrary:libraries){
            resFiles.add(androidLibrary.getResFolder());
        }
        resFiles.add(awbBundle.getAndroidLibrary().getResFolder());
        return getProject().files(resFiles);
 }

    @VisibleForTesting
    void setLibraries(List<AndroidLibrary> libraries) {
        this.libraries = libraries;
    }

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public Collection<File> getSourceFolderInputs() {
        return sourceFolderInputs.get();
    }

    @OutputDirectory
    public File getOutputDir() {
        return outputDir;
    }

    public void setOutputDir(File outputDir) {
        this.outputDir = outputDir;
    }

    @Input
    public boolean getCrunchPng() {
        return crunchPng;
    }

    @Input
    public boolean getProcessResources() {
        return processResources;
    }

    @Optional
    @OutputFile
    public File getPublicFile() {
        return publicFile;
    }

    public void setPublicFile(File publicFile) {
        this.publicFile = publicFile;
    }

    // Synthetic input: the validation flag is set on the resource sets in ConfigAction.execute.
    @Input
    public boolean isValidateEnabled() {
        return validateEnabled;
    }

    public void setValidateEnabled(boolean validateEnabled) {
        this.validateEnabled = validateEnabled;
    }

    @OutputDirectory
    @Optional
    public File getBlameLogFolder() {
        return blameLogFolder;
    }

    public void setBlameLogFolder(File blameLogFolder) {
        this.blameLogFolder = blameLogFolder;
    }

    @OutputDirectory
    public File getGeneratedPngsOutputDir() {
        return generatedPngsOutputDir;
    }

    public void setGeneratedPngsOutputDir(File generatedPngsOutputDir) {
        this.generatedPngsOutputDir = generatedPngsOutputDir;
    }

    @Input
    public Collection<String> getGeneratedDensities() {
        return generatedDensities;
    }

    @Input
    public int getMinSdk() {
        return minSdk;
    }

    public void setMinSdk(int minSdk) {
        this.minSdk = minSdk;
    }

    public void setGeneratedDensities(Collection<String> generatedDensities) {
        this.generatedDensities = generatedDensities;
    }

    @Input
    public boolean isDisableVectorDrawables() {
        return disableVectorDrawables;
    }

    public void setDisableVectorDrawables(boolean disableVectorDrawables) {
        this.disableVectorDrawables = disableVectorDrawables;
    }

    @Input
    public String getAaptGeneration() {
        return aaptGeneration.name();
    }

    @Nullable
    @OutputDirectory
    @Optional
    public File getMergedNotCompiledResourcesOutputDirectory() {
        return mergedNotCompiledResourcesOutputDirectory;
    }

    @Input
    public boolean isPseudoLocalesEnabled() {
        return pseudoLocalesEnabled;
    }

    @VisibleForTesting
    void setResSetSupplier(@NonNull Supplier<List<ResourceSet>> resSetSupplier) {
        this.resSetSupplier = resSetSupplier;
    }

    /**
     * Compute the list of resource set to be used during execution based all the inputs.
     */
    @VisibleForTesting
    @NonNull
    List<ResourceSet> computeResourceSetList() {
        List<ResourceSet> sourceFolderSets = resSetSupplier.get();
        int size = sourceFolderSets.size() + 4;
        if (libraries != null) {
            size += libraries.size();
        }

        List<ResourceSet> resourceSetList = Lists.newArrayListWithExpectedSize(size);

        // add at the beginning since the libraries are less important than the folder based
        // resource sets.
        // get the dependencies first
        if (libraries != null) {
            // the order of the artifact is descending order, so we need to reverse it.
            for (AndroidLibrary artifact : libraries) {
                ResourceSet resourceSet =
                        new ResourceSet(
                                artifact.getName(),
                                null,
                                null,
                                validateEnabled);
                resourceSet.setFromDependency(true);
                resourceSet.addSource(artifact.getResFolder());

                // add to 0 always, since we need to reverse the order.
                resourceSetList.add(0,resourceSet);
            }
        }


        ResourceSet resourceSet =
                new ResourceSet(
                        awbBundle.getName(),
                        null,
                        null,
                        validateEnabled);
        resourceSet.setFromDependency(true);
        resourceSet.addSource(awbBundle.getAndroidLibrary().getResFolder());
        // add the folder based next
        resourceSetList.addAll(sourceFolderSets);
        resourceSetList.add(resourceSet);

        // We add the generated folders to the main set
        List<File> generatedResFolders = Lists.newArrayList();

        generatedResFolders.addAll(renderscriptResOutputDir.getFiles());
        generatedResFolders.addAll(generatedResOutputDir.getFiles());

        FileCollection extraFolders = getExtraGeneratedResFolders();
        if (extraFolders != null) {
            generatedResFolders.addAll(extraFolders.getFiles());
        }
        if (microApkResDirectory != null) {
            generatedResFolders.addAll(microApkResDirectory.getFiles());
        }

//        // add the generated files to the main set.
//        final ResourceSet mainResourceSet = sourceFolderSets.get(0);
//        assert mainResourceSet.getConfigName().equals(BuilderConstants.MAIN);
//        mainResourceSet.addSources(generatedResFolders);

        return resourceSetList;
    }

    /**
     * Obtains the temporary directory for {@code aapt} to use.
     *
     * @return the temporary directory
     */
    @NonNull
    private File getAaptTempDir() {
        return FileUtils.mkdirs(new File(getIncrementalFolder(), "aapt-temp"));
    }

    public static class MergeAwbResourceConfigAction extends MtlBaseTaskAction<MergeAwbResource> {

        @NonNull
        private final VariantScope scope;

        private AwbBundle awbBundle;

        private VariantContext variantContext;
        @Nullable
        private final File outputLocation;
        @Nullable private final File mergedNotCompiledOutputDirectory;
        private final boolean includeDependencies;
        private final boolean processResources;


        public MergeAwbResourceConfigAction(VariantContext variantContext,
                                            BaseVariantOutput baseVariantOutput,
                                            AwbBundle awbBundle) {
            super(variantContext, baseVariantOutput);
            this.variantContext = variantContext;
            this.awbBundle = awbBundle;
            this.scope = variantContext.getScope();
            this.outputLocation = variantContext.getMergeResources(awbBundle);
            this.mergedNotCompiledOutputDirectory = variantContext.getMergeNotCompiledFolder(awbBundle);
            this.includeDependencies = true;
            this.processResources = true;
        }
//        public MergeAwbResourceConfigAction(
//                @NonNull VariantScope scope,
//                @NonNull String taskNamePrefix,
//                @Nullable File outputLocation,
//                @Nullable File mergedNotCompiledOutputDirectory,
//                boolean includeDependencies,
//                boolean processResources) {
//            this.scope = scope;
//            this.taskNamePrefix = taskNamePrefix;
//            this.outputLocation = outputLocation;
//            this.mergedNotCompiledOutputDirectory = mergedNotCompiledOutputDirectory;
//            this.includeDependencies = includeDependencies;
//            this.processResources = processResources;
//        }

        @NonNull
        @Override
        public String getName() {
            return scope.getTaskName("merge", "Resource[" + awbBundle.getName() + "]");
        }

        @NonNull
        @Override
        public Class<MergeAwbResource> getType() {
            return MergeAwbResource.class;
        }

        @Override
        public void execute(@NonNull MergeAwbResource mergeResourcesTask) {
            final BaseVariantData variantData = scope.getVariantData();
            final Project project = scope.getGlobalScope().getProject();
            mergeResourcesTask.awbBundle = awbBundle;
            mergeResourcesTask.setMinSdk(
                    variantData.getVariantConfiguration().getMinSdkVersion().getApiLevel());
            mergeResourcesTask.aaptGeneration =
                    AaptGeneration.fromProjectOptions(scope.getGlobalScope().getProjectOptions());
            mergeResourcesTask.setAndroidBuilder(scope.getGlobalScope().getAndroidBuilder());
            mergeResourcesTask.fileCache = scope.getGlobalScope().getBuildCache();
            mergeResourcesTask.setVariantName(scope.getVariantConfiguration().getFullName());
            mergeResourcesTask.setIncrementalFolder(scope.getIncrementalDir(getName()));
            mergeResourcesTask.variantScope = scope;
            // Libraries use this task twice, once for compilation (with dependencies),
            // where blame is useful, and once for packaging where it is not.
            if (includeDependencies) {
                mergeResourcesTask.setBlameLogFolder(variantContext.getAwbResourceBlameLogDir(awbBundle));
            }
            mergeResourcesTask.processResources = processResources;
            mergeResourcesTask.crunchPng = scope.isCrunchPngs();

            VectorDrawablesOptions vectorDrawablesOptions = variantData
                    .getVariantConfiguration()
                    .getMergedFlavor()
                    .getVectorDrawables();

            Set<String> generatedDensities = vectorDrawablesOptions.getGeneratedDensities();

            // Collections.<String>emptySet() is used intentionally instead of Collections.emptySet()
            // to keep compatibility with javac 1.8.0_45 used by ab/
            mergeResourcesTask.setGeneratedDensities(
                    MoreObjects.firstNonNull(generatedDensities, Collections.<String>emptySet()));

            mergeResourcesTask.setDisableVectorDrawables(
                    (vectorDrawablesOptions.getUseSupportLibrary() != null
                            && vectorDrawablesOptions.getUseSupportLibrary())
                            || mergeResourcesTask.getGeneratedDensities().isEmpty());

            final boolean validateEnabled =
                    !scope.getGlobalScope()
                            .getProjectOptions()
                            .get(BooleanOption.DISABLE_RESOURCE_VALIDATION);

            mergeResourcesTask.setValidateEnabled(validateEnabled);

            if (includeDependencies) {
                mergeResourcesTask.libraries = awbBundle.getAndroidLibraries();
            }

            mergeResourcesTask.resSetSupplier =
                    () -> ImmutableList.of();
            mergeResourcesTask.sourceFolderInputs =
                    TaskInputHelper.bypassFileSupplier(
                            () ->ImmutableList.of());
            mergeResourcesTask.extraGeneratedResFolders = variantData.getExtraGeneratedResFolders();
            mergeResourcesTask.renderscriptResOutputDir = project.files(variantContext.getGenerateRs(awbBundle));
            mergeResourcesTask.generatedResOutputDir = project.files(variantContext.getGenerateResValue(awbBundle));
            if (scope.getMicroApkTask() != null &&
                    variantData.getVariantConfiguration().getBuildType().isEmbedMicroApp()) {
                mergeResourcesTask.microApkResDirectory = project.files(scope.getMicroApkResDirectory());
            }

            mergeResourcesTask.setOutputDir(outputLocation);
            mergeResourcesTask.setGeneratedPngsOutputDir(variantContext.getPngsOutputDir(awbBundle));
//            variantData.mergeResourcesTask = mergeResourcesTask;


            if (scope.getGlobalScope().getExtension().getDataBinding().isEnabled() && ((AppVariantContext)variantContext).isDataBindEnabled(awbBundle)) {
                // Keep as an output.
                mergeResourcesTask.dataBindingLayoutInfoOutFolder =
                      ((AppVariantContext) variantContext).getAwbLayoutInfoOutputForDataBinding(awbBundle);

                mergeResourcesTask.layoutXmlProcessor =
                        new SingleFileProcessor() {
                            final LayoutXmlProcessor processor =
                                    variantData.getLayoutXmlProcessor();

                            @Override
                            public boolean processSingleFile(File file, File out) throws Exception {
                                return processor.processSingleFile(file, out);
                            }

                            @Override
                            public void processRemovedFile(File file) {
                                processor.processRemovedFile(file);
                            }

                            @Override
                            public void end() throws javax.xml.bind.JAXBException{
                                processor.writeLayoutInfoFiles(
                                        mergeResourcesTask.dataBindingLayoutInfoOutFolder);
                            }
                        };
            }

            mergeResourcesTask.mergedNotCompiledResourcesOutputDirectory =
                    mergedNotCompiledOutputDirectory;

            mergeResourcesTask.pseudoLocalesEnabled =
                    scope.getVariantData()
                            .getVariantConfiguration()
                            .getBuildType()
                            .isPseudoLocalesEnabled();




        }


    }

    // Workaround for https://issuetracker.google.com/67418335
    @Override
    @Input
    @NonNull
    public String getCombinedInput() {
        return new CombinedInput(super.getCombinedInput())
                .add("dataBindingLayoutInfoOutFolder", getDataBindingLayoutInfoOutFolder())
                .add("publicFile", getPublicFile())
                .add("blameLogFolder", getBlameLogFolder())
                .add(
                        "mergedNotCompiledResourcesOutputDirectory",
                        getMergedNotCompiledResourcesOutputDirectory())
                .toString();
    }
}
