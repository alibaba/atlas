package com.taobao.android.builder.tasks.app.databinding;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import android.databinding.tool.DataBindingBuilder;
import com.android.build.gradle.internal.api.AppVariantContext;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.BaseTask;
import com.android.build.gradle.internal.tasks.databinding.DataBindingProcessLayoutsTask;
import com.android.build.gradle.internal.variant.BaseVariantOutputData;
import com.taobao.android.builder.AtlasBuildContext;
import com.taobao.android.builder.dependency.AtlasDependencyTree;
import com.taobao.android.builder.dependency.model.AwbBundle;
import com.taobao.android.builder.tasks.app.bundle.TaskCreater;
import com.taobao.android.builder.tasks.manager.MtlBaseTaskAction;
import com.taobao.android.builder.tools.concurrent.ExecutorServicesHelper;
import org.gradle.api.tasks.TaskAction;

/**
 * Created by wuzhong on 2017/3/27.
 */
public class AwbDataBindingProcessLayoutTask extends BaseTask {

    AppVariantContext appVariantContext;

    @TaskAction
    public void run() throws ExecutionException, InterruptedException {

        AtlasDependencyTree atlasDependencyTree = AtlasBuildContext.androidDependencyTrees.get(
            getVariantName());

        if (null == atlasDependencyTree) {
            return;
        }

        DataBindingBuilder dataBindingBuilder = new DataBindingBuilder();
        dataBindingBuilder.setPrintMachineReadableOutput(false);
        dataBindingBuilder.setDebugLogEnabled(appVariantContext.getProject().getLogger().isDebugEnabled());

        List<Runnable> tasks = new ArrayList<Runnable>();

        for (final AwbBundle awbBundle : atlasDependencyTree.getAwbBundles()) {

            tasks.add(new Runnable() {
                @Override
                public void run() {

                    AwbDataBindingProcessLayoutsConfigAction processLayoutsConfigAction =
                        new AwbDataBindingProcessLayoutsConfigAction(appVariantContext, awbBundle, dataBindingBuilder);
                    DataBindingProcessLayoutsTask dataBindingProcessLayoutsTask = TaskCreater.create(
                        appVariantContext.getProject(), processLayoutsConfigAction.getName(),
                        processLayoutsConfigAction.getType());

                    processLayoutsConfigAction.execute(dataBindingProcessLayoutsTask);

                    dataBindingProcessLayoutsTask.execute();
                }
            });

        }

        ExecutorServicesHelper executorServicesHelper = new ExecutorServicesHelper("dataBindingProcessLayoutTask",
                                                                                   getLogger(),
                                                                                   0);

        executorServicesHelper.execute(tasks);

    }

    public static class ConfigAction extends MtlBaseTaskAction<AwbDataBindingProcessLayoutTask> {

        private AppVariantContext appVariantContext;

        public ConfigAction(AppVariantContext appVariantContext, BaseVariantOutputData baseVariantOutputData) {
            super(appVariantContext, baseVariantOutputData);
            this.appVariantContext = appVariantContext;
        }

        @Override
        public String getName() {
            return scope.getTaskName("dataBindingProcessLayout", "Awbs");
        }

        @Override
        public Class<AwbDataBindingProcessLayoutTask> getType() {
            return AwbDataBindingProcessLayoutTask.class;
        }

        @Override
        public void execute(AwbDataBindingProcessLayoutTask parallelTask) {

            super.execute(parallelTask);

            parallelTask.appVariantContext = appVariantContext;

        }
    }

    public static class AwbDataBindingProcessLayoutsConfigAction
        implements TaskConfigAction<DataBindingProcessLayoutsTask> {
        private final AppVariantContext appVariantContext;
        private final AwbBundle awbBundle;
        private final DataBindingBuilder dataBindingBuilder;

        public AwbDataBindingProcessLayoutsConfigAction(AppVariantContext appVariantContext, AwbBundle awbBundle,
                                                        DataBindingBuilder dataBindingBuilder) {
            this.appVariantContext = appVariantContext;
            this.awbBundle = awbBundle;
            this.dataBindingBuilder = dataBindingBuilder;
        }

        @Override
        public String getName() {
            return appVariantContext.getScope().getTaskName("dataBindingProcessLayouts[" + awbBundle.getName() + "]");
        }

        @Override
        public Class<DataBindingProcessLayoutsTask> getType() {
            return DataBindingProcessLayoutsTask.class;
        }

        @Override
        public void execute(DataBindingProcessLayoutsTask task) {
            VariantScope variantScope = appVariantContext.getScope();
            task.setXmlProcessor(
                AwbXmlProcessor.getLayoutXmlProcessor(appVariantContext, awbBundle, dataBindingBuilder));
            task.setSdkDir(variantScope.getGlobalScope().getSdkHandler().getSdkFolder());
            task.setMinSdk(variantScope.getVariantConfiguration().getMinSdkVersion().getApiLevel());
            task.setLayoutInputFolder(appVariantContext.getAwbMergeResourcesOutputDir(awbBundle));
            task.setLayoutOutputFolder(appVariantContext.getAwbLayoutFolderOutputForDataBinding(awbBundle));
            task.setXmlInfoOutFolder(appVariantContext.getAwbLayoutInfoOutputForDataBinding(awbBundle));

        }

    }

}
