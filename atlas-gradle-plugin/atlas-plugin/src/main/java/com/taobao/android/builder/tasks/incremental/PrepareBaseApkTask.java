package com.taobao.android.builder.tasks.incremental;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.api.VariantContext;
import com.android.build.gradle.internal.pipeline.StreamFilter;
import com.android.build.gradle.internal.scope.ConventionMappingHelper;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.IncrementalTask;
import com.android.build.gradle.internal.variant.BaseVariantOutputData;
import com.android.builder.core.VariantConfiguration;
import com.android.ide.common.res2.FileStatus;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.taobao.android.builder.tasks.manager.MtlBaseTaskAction;
import com.taobao.android.builder.tools.zip.BetterZip;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputDirectory;

/**
 * Created by chenhjohn on 2017/4/24.
 */

public class PrepareBaseApkTask extends IncrementalTask {

    // ----- PUBLIC TASK API -----
    private File baseApk;

    private int dexFilesCount;

    private File outputDir;

    // ----- PRIVATE TASK API -----

    @Override
    protected boolean isIncremental() {
        // TODO fix once dep file parsing is resolved.
        return false;
    }

    @Override
    protected void doFullTaskAction() throws IOException {
        File baseApk = getBaseApk();
        File outputDir = getOutputDir();
        FileUtils.deleteDirectoryContents(outputDir);
        BetterZip.unzipDirectory(baseApk, outputDir);
        FileUtils.deleteDirectoryContents(FileUtils.join(outputDir, "META-INF/"));
        int dexFilesCount = getDexFilesCount();
        Set<File> baseDexFileSet = getProject().fileTree(
            ImmutableMap.of("dir", outputDir, "includes", ImmutableList.of("classes*.dex"))).getFiles();
        File[] baseDexFiles = baseDexFileSet.toArray(new File[baseDexFileSet.size()]);
        int j = baseDexFileSet.size() + dexFilesCount;
        for (int i = baseDexFiles.length - 1; i >= 0; i--) {
            FileUtils.renameTo(baseDexFiles[i], new File(outputDir, "classes" + j + ".dex"));
            j--;
        }
    }

    @InputFile
    public File getBaseApk() {
        return baseApk;
    }

    public void setBaseApk(File baseApk) {
        this.baseApk = baseApk;
    }

    @Input
    public int getDexFilesCount() {
        return dexFilesCount;
    }

    public void setDexFilesCount(int dexFilesCount) {
        this.dexFilesCount = dexFilesCount;
    }

    @OutputDirectory
    public File getOutputDir() {
        return outputDir;
    }

    public void setOutputDir(File outputDir) {
        this.outputDir = outputDir;
    }

    @Override
    protected void doIncrementalTaskAction(Map<File, FileStatus> changedInputs) throws IOException {

        for (final Map.Entry<File, FileStatus> entry : changedInputs.entrySet()) {
            FileStatus status = entry.getValue();
            switch (status) {
                case NEW:
                    break;
                case CHANGED:
                    break;
                case REMOVED:
                    break;
            }
        }
    }

    public static class ConfigAction extends MtlBaseTaskAction<PrepareBaseApkTask> {
        @NonNull
        VariantScope scope;

        public ConfigAction(VariantContext variantContext, BaseVariantOutputData baseVariantOutputData) {
            super(variantContext, baseVariantOutputData);
            this.scope = baseVariantOutputData.getScope().getVariantScope();
        }

        @Override
        @NonNull
        public String getName() {
            return scope.getTaskName("prepare", "BaseApk");
        }

        @Override
        @NonNull
        public Class<PrepareBaseApkTask> getType() {
            return PrepareBaseApkTask.class;
        }

        @Override
        public void execute(@NonNull PrepareBaseApkTask prepareBaseApkTask) {
            final VariantConfiguration<?, ?, ?> variantConfiguration = scope.getVariantConfiguration();

            prepareBaseApkTask.setAndroidBuilder(scope.getGlobalScope().getAndroidBuilder());
            prepareBaseApkTask.setVariantName(scope.getVariantConfiguration().getFullName());
            prepareBaseApkTask.setIncrementalFolder(scope.getIncrementalDir(getName()));
            ConventionMappingHelper.map(prepareBaseApkTask, "baseApk", new Callable<File>() {
                @Override
                public File call() {
                    return variantContext.apContext.getBaseApk();
                }
            });
            ConventionMappingHelper.map(prepareBaseApkTask, "dexFilesCount", new Callable<Integer>() {
                @Override
                public Integer call() {
                    int dexFilesCount = 0;
                    Set<File> dexFolders = scope.getTransformManager().getPipelineOutput(StreamFilter.DEX).keySet();
                    // Preconditions.checkState(dexFolders.size() == 1,
                    //                          "There must be exactly one output");
                    for (File dexFolder : dexFolders) {
                        dexFilesCount += scope.getGlobalScope().getProject().fileTree(
                            ImmutableMap.of("dir", dexFolder, "includes", ImmutableList.of("classes*.dex"))).getFiles()
                            .size();
                    }
                    return dexFilesCount;
                }
            });
            ConventionMappingHelper.map(prepareBaseApkTask, "outputDir", new Callable<File>()

            {
                @Override
                public File call() {
                    return new File(variantContext.apContext.getBaseApk() + "_");
                }
            });
        }
    }
}
