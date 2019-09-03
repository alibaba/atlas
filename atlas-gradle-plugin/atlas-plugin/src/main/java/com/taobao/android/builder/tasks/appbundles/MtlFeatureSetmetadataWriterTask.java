package com.taobao.android.builder.tasks.appbundles;

import com.android.build.gradle.api.BaseVariantOutput;
import com.android.build.gradle.internal.api.VariantContext;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.tasks.AndroidVariantTask;
import com.android.build.gradle.internal.tasks.TaskInputHelper;
import com.android.build.gradle.internal.tasks.Workers;
import com.android.build.gradle.internal.tasks.featuresplit.FeatureSetMetadata;
import com.android.build.gradle.internal.tasks.featuresplit.FeatureSplitDeclaration;
import com.android.build.gradle.options.IntegerOption;
import com.android.ide.common.workers.ExecutorServiceAdapter;
import com.android.ide.common.workers.WorkerExecutorFacade;
import com.taobao.android.builder.AtlasBuildContext;
import com.taobao.android.builder.dependency.model.AwbBundle;
import com.taobao.android.builder.tasks.manager.MtlBaseTaskAction;
import org.apache.commons.io.FileUtils;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;
import org.gradle.workers.WorkerExecutor;
import org.jetbrains.annotations.NotNull;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * @ClassName MtlFeatureSetmetadataWriterTask
 * @Description TODO
 * @Author zhayu.ll
 * @Date 2019-08-20 10:27
 * @Version 1.0
 */
public class MtlFeatureSetmetadataWriterTask extends AndroidVariantTask {


    private File outFile;

    private Integer maxNumberOfFeaturesBeforeOreo;

    @Input
    private Provider<Integer>minSdkVersion = null;

    private WorkerExecutorFacade workers = null;

    @Inject
    public MtlFeatureSetmetadataWriterTask(WorkerExecutor workers) {
        this.workers = Workers.INSTANCE.getWorker(workers);
    }

    @TaskAction
    public void fullTaskAction() {
        FeatureParams params = new FeatureParams();
        params.minSdkVersion = minSdkVersion.get();
        params.outputFile = outFile;
        params.maxNumberOfFeaturesBeforeOreo = 100;
        AtlasBuildContext.androidDependencyTrees.get(getVariantName()).getAwbBundles().forEach(new Consumer<AwbBundle>() {
            @Override
            public void accept(AwbBundle awbBundle) {
                if (awbBundle.dynamicFeature) {
                    params.featureBundles.add(awbBundle);
                }
            }
        });
        workers.submit(FeatureSetRunnable.class, params);
        workers.await();
    }


    private static class FeatureParams implements Serializable {

        Set<AwbBundle> featureBundles = new HashSet<>();
        int minSdkVersion;
        int maxNumberOfFeaturesBeforeOreo;
        File outputFile;

    }


    private static class FeatureSetRunnable implements Runnable {

        private FeatureParams params;
        @Inject
        private FeatureSetRunnable(FeatureParams params) {
            this.params = params;

        }

        @Override
        public void run() {
            List<FeatureSplitDeclaration> features = new ArrayList<>();
            FeatureSetMetadata featureSetMetadata = new FeatureSetMetadata(params.maxNumberOfFeaturesBeforeOreo);

            for (AwbBundle awbBundle : params.featureBundles) {
                features.add(new FeatureSplitDeclaration(awbBundle.getFeatureName(), awbBundle.getPackageName()));

            }

            for (AwbBundle awbBundle : params.featureBundles) {
                featureSetMetadata.addFeatureSplit(
                        params.minSdkVersion, awbBundle.getName(), awbBundle.getFeatureName());
            }


            // save the list.
            try {
                FileUtils.forceMkdir(params.outputFile.getParentFile());
                featureSetMetadata.save(params.outputFile);
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
    }

    public static class ConfigAction extends MtlBaseTaskAction<MtlFeatureSetmetadataWriterTask> {


        private File outputFile;

        public ConfigAction(VariantContext variantContext, BaseVariantOutput baseVariantOutput) {
            super(variantContext, baseVariantOutput);
        }

        @Override
        public String getName() {
            return variantContext.getBaseVariantData().getTaskName("generatemtl", "FeatureMetadata");
        }

        @Override
        public Class getType() {
            return MtlFeatureSetmetadataWriterTask.class;
        }

        @Override
        public void configure(MtlFeatureSetmetadataWriterTask task) {
            super.configure(task);
            task.setVariantName(variantContext.getVariantName());
            task.outFile = outputFile;
            task.minSdkVersion = TaskInputHelper.memoizeToProvider(task.getProject(), () -> variantContext.getVariantConfiguration().getMinSdkVersion().getApiLevel());
            Integer maxNumberOfFeaturesBeforeOreo = variantContext.getScope().getGlobalScope().getProjectOptions()
                    .get(IntegerOption.PRE_O_MAX_NUMBER_OF_FEATURES);
            if (maxNumberOfFeaturesBeforeOreo != null) {
                task.maxNumberOfFeaturesBeforeOreo =
                        Integer.min(100, maxNumberOfFeaturesBeforeOreo);
            }

        }

        @Override
        public void preConfigure(@NotNull String s) {
            super.preConfigure(s);
            outputFile = variantContext.getScope().getArtifacts().getFinalArtifactFiles(
                    InternalArtifactType.FEATURE_SET_METADATA).get().getSingleFile();
        }
    }


}
