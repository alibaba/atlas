package com.taobao.android.builder.tasks.appbundles;

import com.android.build.gradle.api.BaseVariantOutput;
import com.android.build.gradle.internal.api.VariantContext;
import com.android.build.gradle.internal.tasks.AndroidBuilderTask;
import com.android.build.gradle.internal.tasks.factory.TaskFactoryImpl;
import com.taobao.android.builder.AtlasBuildContext;
import com.taobao.android.builder.tasks.manager.MtlBaseTaskAction;
import org.gradle.api.Task;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.TaskProvider;
import org.jetbrains.annotations.NotNull;

import javax.xml.bind.JAXBException;
import java.io.IOException;

/**
 * @ClassName FeaturesParallelTask
 * @Description TODO
 * @Author zhayu.ll
 * @Date 2019-08-21 09:53
 * @Version 1.0
 */
public class FeaturesParallelTask extends AndroidBuilderTask {

    private ProcessType processType;

    private VariantContext variantContext;

    private BaseVariantOutput variantOutput;

    @TaskAction
    public void taskCreation() {

        switch (processType) {

            case MERGE_MANIFEST:
                AtlasBuildContext.androidDependencyTrees.get(variantName).getAwbBundles().parallelStream().forEach(awbBundle -> {
                    if (awbBundle.dynamicFeature) {
                        TaskProvider<ProcessFeatureManifestTask> provider = new TaskFactoryImpl(getProject().getTasks()).register(new ProcessFeatureManifestTask.CreationAction(awbBundle, variantContext, variantOutput));
                        try {
                            provider.get().doFullTaskAction();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                });

                break;

            case BUNDLE_RES:
                AtlasBuildContext.androidDependencyTrees.get(variantName).getAwbBundles().parallelStream().forEach(awbBundle -> {
                    if (awbBundle.dynamicFeature) {
                        TaskProvider<BundleFeatureResourceTask> provider = new TaskFactoryImpl(getProject().getTasks()).register(new BundleFeatureResourceTask.CreationAction(awbBundle, variantContext, variantOutput));
                        provider.get().taskAction();
                    }
                });

                break;


            case MERGE_RESOURCE:

                AtlasBuildContext.androidDependencyTrees.get(variantName).getAwbBundles().parallelStream().forEach(awbBundle -> {
                    if (awbBundle.dynamicFeature) {
                        TaskProvider<MergeFeatureResource> provider = new TaskFactoryImpl(getProject().getTasks()).register(new MergeFeatureResource.CreationAction(awbBundle, variantContext, variantOutput));
                        try {
                            provider.get().doFullTaskAction();
                        } catch (IOException e) {
                            e.printStackTrace();
                        } catch (JAXBException e) {
                            e.printStackTrace();
                        }
                    }
                });

                break;


            case MERGE_ASSETS:

                AtlasBuildContext.androidDependencyTrees.get(variantName).getAwbBundles().parallelStream().forEach(awbBundle -> {
                    if (awbBundle.dynamicFeature) {
                        TaskProvider<MergeFeatureAssets> provider = new TaskFactoryImpl(getProject().getTasks()).register(new MergeFeatureAssets.CreationAction(awbBundle, variantContext, variantOutput));
                        try {
                            provider.get().doFullTaskAction();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                });


                break;


        }


    }


    public static class CreationManifestsAction extends MtlBaseTaskAction<FeaturesParallelTask> {


        public CreationManifestsAction(VariantContext variantContext, BaseVariantOutput baseVariantOutput) {

            super(variantContext, baseVariantOutput);
        }

        @Override
        public void configure(FeaturesParallelTask task) {
            super.configure(task);
            task.processType = ProcessType.MERGE_MANIFEST;
            task.variantContext = variantContext;
            task.variantOutput = baseVariantOutput;

        }

        @NotNull
        @Override
        public String getName() {
            return scope.getTaskName("processFeatures", "Manifests");
        }

        @NotNull
        @Override
        public Class<FeaturesParallelTask> getType() {
            return FeaturesParallelTask.class;
        }

    }


    public static class CreationAssetsAction extends MtlBaseTaskAction<FeaturesParallelTask> {


        public CreationAssetsAction(VariantContext variantContext, BaseVariantOutput baseVariantOutput) {

            super(variantContext, baseVariantOutput);
        }

        @Override
        public void configure(FeaturesParallelTask task) {
            super.configure(task);
            task.processType = ProcessType.MERGE_ASSETS;
            task.variantContext = variantContext;
            task.variantOutput = baseVariantOutput;

        }

        @NotNull
        @Override
        public String getName() {
            return scope.getTaskName("processFeatures", "Assets");
        }

        @NotNull
        @Override
        public Class<FeaturesParallelTask> getType() {
            return FeaturesParallelTask.class;
        }

    }


    public static class MergeResourceAction extends MtlBaseTaskAction<FeaturesParallelTask> {


        public MergeResourceAction(VariantContext variantContext, BaseVariantOutput baseVariantOutput) {

            super(variantContext, baseVariantOutput);
        }

        @Override
        public void configure(FeaturesParallelTask task) {
            super.configure(task);
            task.processType = ProcessType.MERGE_RESOURCE;
            task.variantContext = variantContext;
            task.variantOutput = baseVariantOutput;

        }

        @NotNull
        @Override
        public String getName() {
            return scope.getTaskName("mergeFeatures", "Resource");
        }

        @NotNull
        @Override
        public Class<FeaturesParallelTask> getType() {
            return FeaturesParallelTask.class;
        }

    }


    public static class CreationBundleResourceAction extends MtlBaseTaskAction<FeaturesParallelTask> {


        public CreationBundleResourceAction(VariantContext variantContext, BaseVariantOutput baseVariantOutput) {

            super(variantContext, baseVariantOutput);
        }

        @Override
        public void configure(FeaturesParallelTask task) {
            super.configure(task);
            task.processType = ProcessType.BUNDLE_RES;
            task.variantContext = variantContext;
            task.variantOutput = baseVariantOutput;

        }

        @NotNull
        @Override
        public String getName() {
            return scope.getTaskName("processBundle", "resources");
        }

        @NotNull
        @Override
        public Class<FeaturesParallelTask> getType() {
            return FeaturesParallelTask.class;
        }

    }


    enum ProcessType {

        MERGE_MANIFEST,
        PROCESS_RESOURCE,
        MERGE_ASSETS,
        JAVAC,
        DEX,
        MERGE_JAVA_RES,
        MEGGE_LIBS,
        BUNDLE_RES,
        MERGE_RESOURCE

    }


    public class CreationProcessResourceAction {
    }
}
