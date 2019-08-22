package com.taobao.android.builder.tasks.appbundles;

import com.android.build.gradle.api.BaseVariantOutput;
import com.android.build.gradle.internal.api.VariantContext;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.scope.OutputScope;
import com.android.build.gradle.internal.tasks.AndroidVariantTask;
import com.android.build.gradle.internal.tasks.ModuleMetadata;
import com.android.build.gradle.internal.tasks.TaskInputHelper;
import com.google.common.collect.Lists;
import com.taobao.android.builder.tasks.manager.MtlBaseTaskAction;
import org.apache.commons.io.FileUtils;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.Set;
import java.util.function.Supplier;

/**
 * @ClassName MtlModuleMetadataWriterTask
 * @Description TODO
 * @Author zhayu.ll
 * @Date 2019-08-20 15:27
 * @Version 1.0
 */
public class MtlModuleMetadataWriterTask extends AndroidVariantTask {

    @Input
   private Provider<String> applicationId;

    @Input
    private OutputScope outputScope;

    @Input
    private boolean debuggable;

    @OutputFile
    public File getOutputFile() {
        return outputFile;
    }

    public void setOutputFile(File outputFile) {
        this.outputFile = outputFile;
    }

    private File outputFile;

//    @InputFiles
//    public Set<File> getMetadataFromInstalledModule() {
//        return metadataFromInstalledModule.getFiles();
//    }
//
//    public void setMetadataFromInstalledModule(FileCollection metadataFromInstalledModule) {
//        this.metadataFromInstalledModule = metadataFromInstalledModule;
//    }
//
//    private FileCollection metadataFromInstalledModule;

    @TaskAction
    public void fullTaskAction() throws IOException {
        ModuleMetadata moduleMetadata = new ModuleMetadata(
                applicationId.get(),
                String.valueOf(outputScope.getMainSplit().getVersionCode()),
                outputScope.getMainSplit().getVersionName(),
                debuggable);
        moduleMetadata.save(outputFile);
    }





    public static class CreationAction extends MtlBaseTaskAction<MtlModuleMetadataWriterTask>{

        private File outputFile;
        public CreationAction(VariantContext variantContext, BaseVariantOutput baseVariantOutput) {
            super(variantContext, baseVariantOutput);
        }

        @Override
        public void configure(MtlModuleMetadataWriterTask task) {
            super.configure(task);
            task.applicationId = TaskInputHelper.memoizeToProvider(task.getProject(), new Supplier<String>() {
                @Override
                public String get() {
                    return variantContext.getVariantConfiguration().getApplicationId();
                }
            });
            task.outputScope = variantContext.getBaseVariantData().getOutputScope();

            task.debuggable = variantContext.getVariantConfiguration().getBuildType().isDebuggable();

            // publish the ID for the dynamic features (whether it's hybrid or not) to consume.
            task.outputFile = outputFile;

        }


        @Override
        public void preConfigure(@NotNull String s) {
            super.preConfigure(s);

            outputFile = new File(variantContext.getScope().getGlobalScope().getIntermediatesDir(),
                    "application-meta/application-metadata.json"
            );


        }

        @NotNull
        @Override
        public String getName() {
            return variantContext.getScope().getTaskName("mtlwrite", "ModuleMetadata");
        }

        @NotNull
        @Override
        public Class<MtlModuleMetadataWriterTask> getType() {
            return MtlModuleMetadataWriterTask.class;
        }
    }
}
