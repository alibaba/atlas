package com.taobao.android.builder.tasks.app.bundle;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.api.AppVariantOutputContext;
import com.android.build.gradle.internal.api.AwbTransform;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.dsl.DexOptions;
import com.android.build.gradle.internal.scope.ConventionMappingHelper;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.BaseTask;
import com.android.build.gradle.internal.variant.ApkVariantData;
import com.android.ide.common.blame.Message;
import com.android.ide.common.blame.ParsingProcessOutputHandler;
import com.android.ide.common.blame.parser.DexParser;
import com.android.ide.common.blame.parser.ToolOutputParser;
import com.android.ide.common.process.ProcessException;
import com.android.ide.common.process.ProcessOutputHandler;
import com.android.utils.FileUtils;
import com.android.utils.StringHelper;
import com.taobao.android.builder.dependency.model.AwbBundle;
import com.taobao.android.dex.DexException;
import com.taobao.android.dx.merge.CollisionPolicy;
import com.taobao.android.dx.merge.DexMerger;
import org.gradle.api.Action;
import org.gradle.api.tasks.*;
import org.gradle.api.tasks.incremental.IncrementalTaskInputs;
import org.gradle.api.tasks.incremental.InputFileDetails;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static com.android.builder.model.AndroidProject.FD_INTERMEDIATES;

@ParallelizableTask
public class Dex extends BaseTask {
    @Input
    public String getBuildToolsVersion() {
        return getBuildTools().getRevision().toString();
    }

    /**
     * Actual entry point for the action.
     * Calls out to the doTaskAction as needed.
     */
    @TaskAction
    public void taskAction(IncrementalTaskInputs inputs) throws IOException, InterruptedException, ProcessException {
        Collection<File> _inputFiles = getInputFiles();
        File _inputDir = getInputDir();
        if (_inputFiles == null && _inputDir == null) {
            throw new RuntimeException("Dex task \'" + getName() + ": inputDir and inputFiles cannot both be null");
        }

        if (!dexOptions.getIncremental() || !enableIncremental) {
            doTaskAction(_inputFiles, _inputDir, false);
            return;

        }

        if (!inputs.isIncremental()) {
            getProject().getLogger().info("Unable to do incremental execution: full task run.");
            doTaskAction(_inputFiles, _inputDir, false);
            return;

        }

        final AtomicBoolean forceFullRun = new AtomicBoolean();

        //noinspection GroovyAssignabilityCheck
        inputs.outOfDate(new Action<InputFileDetails>() {
            @Override
            public void execute(InputFileDetails change) {
                // force full dx run if existing jar file is modified
                // New jar files are fine.
                if (((InputFileDetails)change).isModified() && ((InputFileDetails)change).getFile().getPath().endsWith(
                    SdkConstants.DOT_JAR)) {
                    getProject().getLogger().info(
                        "Force full dx run: Found updated " + String.valueOf(((InputFileDetails)change).getFile()));
                    forceFullRun.set(true);
                }

            }

        });

        //noinspection GroovyAssignabilityCheck
        inputs.removed(change -> {
            // force full dx run if existing jar file is removed
            if (((InputFileDetails)change).getFile().getPath().endsWith(SdkConstants.DOT_JAR)) {
                getProject().getLogger().info(
                    "Force full dx run: Found removed " + String.valueOf(((InputFileDetails)change).getFile()));
                forceFullRun.set(true);
            }

        });

        doTaskAction(_inputFiles, _inputDir, !forceFullRun.get());
    }

    private void doTaskAction(@Nullable Collection<File> inputFiles, @Nullable File inputDir, boolean incremental)
        throws IOException, ProcessException, InterruptedException {
        File outFolder = getOutputFolder();
        if (!incremental) {
            FileUtils.deleteDirectoryContents(outFolder);
        }

        File tmpFolder = getTmpFolder();
        tmpFolder.mkdirs();

        // if some of our .jar input files exist, just reset the inputDir to null
        for (File inputFile : inputFiles) {
            if (inputFile.exists()) {
                inputDir = null;
            }

        }

        if (inputDir != null) {
            inputFiles = getProject().files(inputDir).getFiles();
        }
        final ProcessOutputHandler outputHandler = new ParsingProcessOutputHandler(
            new ToolOutputParser(new DexParser(), Message.Kind.ERROR, getILogger()),
            new ToolOutputParser(new DexParser(), getILogger()), getBuilder().getErrorReporter());

        List<File> inputDexFiles = new ArrayList<File>();
        inputDexFiles.addAll(inputFiles);
        inputDexFiles.addAll(getLibraries());
        getBuilder().convertByteCode(inputDexFiles, outFolder, getMultiDexEnabled(), getMainDexListFile(),
                                     getDexOptions(), outputHandler);
        File dexBaseFile = getDexBaseFile();
        if (dexBaseFile != null) {
            ZipFile files = new ZipFile(dexBaseFile);
            ZipEntry entry = files.getEntry("classes.dex");
            if (entry == null) {
                throw new DexException("Expected classes.dex in " + dexBaseFile);
            }
            File file = new File(outFolder, "classes.dex");
            com.taobao.android.dex.Dex merged = new DexMerger(new com.taobao.android.dex.Dex[]{new com.taobao.android.dex.Dex(file),
                new com.taobao.android.dex.Dex(files.getInputStream(entry))}, CollisionPolicy.KEEP_FIRST).merge();
            merged.writeTo(file);
        }
    }

    @OutputDirectory
    public File getOutputFolder() {
        return outputFolder;
    }

    public void setOutputFolder(File outputFolder) {
        this.outputFolder = outputFolder;
    }

    @Input
    @Optional
    public List<String> getAdditionalParameters() {
        return additionalParameters;
    }

    public void setAdditionalParameters(List<String> additionalParameters) {
        this.additionalParameters = additionalParameters;
    }

    public boolean getEnableIncremental() {
        return enableIncremental;
    }

    public boolean isEnableIncremental() {
        return enableIncremental;
    }

    public void setEnableIncremental(boolean enableIncremental) {
        this.enableIncremental = enableIncremental;
    }

    @InputFiles
    @Optional
    public Collection<File> getInputFiles() {
        return inputFiles;
    }

    public void setInputFiles(Collection<File> inputFiles) {
        this.inputFiles = inputFiles;
    }

    @InputDirectory
    @Optional
    public File getInputDir() {
        return inputDir;
    }

    public void setInputDir(File inputDir) {
        this.inputDir = inputDir;
    }

    @InputFiles
    public Collection<File> getLibraries() {
        return libraries;
    }

    public void setLibraries(Collection<File> libraries) {
        this.libraries = libraries;
    }

    @Nested
    public DexOptions getDexOptions() {
        return dexOptions;
    }

    public void setDexOptions(DexOptions dexOptions) {
        this.dexOptions = dexOptions;
    }

    @Input
    public boolean getMultiDexEnabled() {
        return multiDexEnabled;
    }

    public boolean isMultiDexEnabled() {
        return multiDexEnabled;
    }

    public void setMultiDexEnabled(boolean multiDexEnabled) {
        this.multiDexEnabled = multiDexEnabled;
    }

    @Input
    public boolean getOptimize() {
        return optimize;
    }

    public boolean isOptimize() {
        return optimize;
    }

    public void setOptimize(boolean optimize) {
        this.optimize = optimize;
    }

    @InputFile
    @Optional
    public File getMainDexListFile() {
        return mainDexListFile;
    }

    public void setMainDexListFile(File mainDexListFile) {
        this.mainDexListFile = mainDexListFile;
    }

    public File getDexBaseFile() {
        return dexBaseFile;
    }

    public void setDexBaseFile(File dexBaseFile) {
        this.dexBaseFile = dexBaseFile;
    }

    public File getTmpFolder() {
        return tmpFolder;
    }

    public void setTmpFolder(File tmpFolder) {
        this.tmpFolder = tmpFolder;
    }

    @OutputDirectory
    private File outputFolder;

    @Input
    @Optional
    private List<String> additionalParameters;

    private boolean enableIncremental = true;

    @InputFiles
    @Optional
    private Collection<File> inputFiles;

    @InputDirectory
    @Optional
    private File inputDir;

    @InputFiles
    private Collection<File> libraries;

    @Nested
    private DexOptions dexOptions;

    @Input
    private boolean multiDexEnabled = false;

    @Input
    private boolean optimize = true;

    @InputFile
    @Optional
    private File dexBaseFile;

    @InputFile
    @Optional
    private File mainDexListFile;

    private File tmpFolder;

    public static class ConfigAction implements TaskConfigAction<Dex> {
        public ConfigAction(VariantScope scope, AppVariantOutputContext appVariantOutputContext, AwbBundle awbBundle) {
            this.scope = scope;
            this.appVariantOutputContext = appVariantOutputContext;
            this.awbBundle = awbBundle;
        }

        @Override
        public String getName() {
            return getTaskName("dex", "");
        }

        public String getTaskName(@NonNull String prefix, @NonNull String suffix) {
            return scope.getTaskName(prefix, StringHelper.capitalize(awbBundle.getName()) + suffix);
        }

        @Override
        public Class<Dex> getType() {
            return ((Class<Dex>)(Dex.class));
        }

        @Override
        public void execute(Dex dexTask) {
            ApkVariantData variantData = (ApkVariantData)scope.getVariantData();
            final GradleVariantConfiguration config = variantData.getVariantConfiguration();

            // boolean isTestForApp = config.getType().isForTesting() && (DefaultGroovyMethods.asType(variantData,
            //                                                                                        TestVariantData
            //                                                                                            .class))
            //     .getTestedVariantData().getVariantConfiguration().getType().equals(DEFAULT);
            //
            // boolean isMultiDexEnabled = config.isMultiDexEnabled() && !isTestForApp;
            // boolean isLegacyMultiDexMode = config.isLegacyMultiDexMode();

            // variantData.dexTask = dexTask;
            dexTask.setVariantName(scope.getFullVariantName());
            dexTask.setAndroidBuilder(scope.getGlobalScope().getAndroidBuilder());
            ConventionMappingHelper.map(dexTask, "outputFolder", new Callable<File>() {
                @Override
                public File call() throws Exception {
                    return appVariantOutputContext.getVariantContext().getAwbDexOutput(awbBundle.getName());
                }

            });
            dexTask.setTmpFolder(new File(
                String.valueOf(scope.getGlobalScope().getBuildDir()) + "/" + FD_INTERMEDIATES + "/tmp/dex/" + config
                    .getDirName()));
            dexTask.setDexOptions(scope.getGlobalScope().getExtension().getDexOptions());
            // dexTask.setMultiDexEnabled(isMultiDexEnabled);
            // dx doesn't work with receving --no-optimize in debug so we disable it for now.
            dexTask.setOptimize(true);//!variantData.variantConfiguration.buildType.debuggable

            // inputs
            // if (awbBundle.invokeMethod("getInputDirCallable", new Object[0]) != null) {
            //     ConventionMappingHelper.map(dexTask, "inputDir",
            //                                 awbBundle.invokeMethod("getInputDirCallable", new Object[0]));
            // }

            ConventionMappingHelper.map(dexTask, "inputDir", new Callable<File>() {
                @Override
                public File call() {
                    AwbTransform awbTransform = appVariantOutputContext.getAwbTransformMap().get(awbBundle.getName());
                    return awbTransform.getInputDir();
                }
            });
            ConventionMappingHelper.map(dexTask, "inputFiles", new Callable<List<File>>() {
                @Override
                public List<File> call() {
                    AwbTransform awbTransform = appVariantOutputContext.getAwbTransformMap().get(awbBundle.getName());
                    return awbTransform.getInputFiles();
                }
            });
//            ConventionMappingHelper.map(dexTask, "dexBaseFile", new Callable<File>() {
//                @Override
//                public File call() {
//                    if (!awbBundle.getManifest().exists()) {
//                        return null;
//                    }
//                    return appVariantOutputContext.getVariantContext().apContext.getBaseAwb(awbBundle.getAwbSoName());
//                }
//            });
            ConventionMappingHelper.map(dexTask, "libraries", new Callable<List<File>>() {
                @Override
                public List<File> call() {
                    AwbTransform awbTransform = appVariantOutputContext.getAwbTransformMap().get(awbBundle.getName());
                    return awbTransform.getInputLibraries();
                }
            });

            // if (isMultiDexEnabled && isLegacyMultiDexMode) {
            //     // configure the dex task to receive the generated class list.
            //     ConventionMappingHelper.map(dexTask, "mainDexListFile", new Callable<File>() {
            //         @Override
            //         public File call() throws Exception {
            //             return scope.getMainDexListFile();
            //         }
            //
            //     });
            // }

        }

        private final VariantScope scope;

        private final AppVariantOutputContext appVariantOutputContext;

        private final AwbBundle awbBundle;
    }
}
