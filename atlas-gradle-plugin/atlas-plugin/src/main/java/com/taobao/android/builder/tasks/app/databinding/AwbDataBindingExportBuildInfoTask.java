package com.taobao.android.builder.tasks.app.databinding;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

import android.databinding.tool.DataBindingBuilder;
import com.android.build.gradle.internal.api.AppVariantContext;
import com.android.build.gradle.internal.scope.ConventionMappingHelper;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.tasks.BaseTask;
import com.android.build.gradle.internal.tasks.databinding.DataBindingExportBuildInfoTask;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.BaseVariantOutputData;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.taobao.android.builder.AtlasBuildContext;
import com.taobao.android.builder.dependency.AtlasDependencyTree;
import com.taobao.android.builder.dependency.model.AwbBundle;
import com.taobao.android.builder.tasks.app.bundle.TaskCreater;
import com.taobao.android.builder.tasks.manager.MtlBaseTaskAction;
import com.taobao.android.builder.tools.concurrent.ExecutorServicesHelper;
import org.gradle.api.file.ConfigurableFileTree;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.TaskAction;

/**
 * Created by wuzhong on 2017/3/27.
 */
public class AwbDataBindingExportBuildInfoTask extends BaseTask {

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

                    AwbDataBindingExportBuildInfoConfigAction exportBuildInfoConfigAction
                        = new AwbDataBindingExportBuildInfoConfigAction(appVariantContext, awbBundle,
                                                                        dataBindingBuilder
                                                                            .getPrintMachineReadableOutput(),
                                                                        dataBindingBuilder);
                    DataBindingExportBuildInfoTask exportBuildInfoTask = TaskCreater.create(
                        appVariantContext.getProject(),
                        exportBuildInfoConfigAction
                            .getName(),
                        exportBuildInfoConfigAction
                            .getType());
                    exportBuildInfoConfigAction.execute(exportBuildInfoTask);

                    exportBuildInfoTask.execute();
                }
            });

        }

        ExecutorServicesHelper executorServicesHelper = new ExecutorServicesHelper("dataBindingExportBuildInfo",
                                                                                   getLogger(),
                                                                                   0);

        executorServicesHelper.execute(tasks);

    }

    public static class ConfigAction extends MtlBaseTaskAction<AwbDataBindingExportBuildInfoTask> {

        private AppVariantContext appVariantContext;

        public ConfigAction(AppVariantContext appVariantContext, BaseVariantOutputData baseVariantOutputData) {
            super(appVariantContext, baseVariantOutputData);
            this.appVariantContext = appVariantContext;
        }

        @Override
        public String getName() {
            return scope.getTaskName("dataBindingExportBuildInfo", "Awbs");
        }

        @Override
        public Class<AwbDataBindingExportBuildInfoTask> getType() {
            return AwbDataBindingExportBuildInfoTask.class;
        }

        @Override
        public void execute(AwbDataBindingExportBuildInfoTask parallelTask) {

            super.execute(parallelTask);

            parallelTask.appVariantContext = appVariantContext;

        }
    }

    public static class AwbDataBindingExportBuildInfoConfigAction implements TaskConfigAction<DataBindingExportBuildInfoTask> {
        private final AppVariantContext appVariantContext;
        private final boolean printMachineReadableErrors;
        private final AwbBundle awbBundle;
        private final DataBindingBuilder dataBindingBuilder;

        public AwbDataBindingExportBuildInfoConfigAction(AppVariantContext appVariantContext, AwbBundle awbBundle,
                                                         boolean printMachineReadableErrors,
                                                         DataBindingBuilder dataBindingBuilder) {
            this.appVariantContext = appVariantContext;
            this.printMachineReadableErrors = printMachineReadableErrors;
            this.awbBundle = awbBundle;
            this.dataBindingBuilder = dataBindingBuilder;
        }

        @Override
        public String getName() {
            return appVariantContext.getScope().getTaskName(
                "dataBindingExportBuildInfoAwb[" + awbBundle.getName() + "]");
        }

        @Override
        public Class<DataBindingExportBuildInfoTask> getType() {
            return DataBindingExportBuildInfoTask.class;
        }

        @Override
        public void execute(DataBindingExportBuildInfoTask task) {
            final BaseVariantData<? extends BaseVariantOutputData> variantData = appVariantContext.getScope()
                .getVariantData();
            task.setXmlProcessor(
                AwbXmlProcessor.getLayoutXmlProcessor(appVariantContext, awbBundle, dataBindingBuilder));
            task.setSdkDir(appVariantContext.getScope().getGlobalScope().getSdkHandler().getSdkFolder());
            task.setXmlOutFolder(appVariantContext.getAwbLayoutInfoOutputForDataBinding(awbBundle));

            ConventionMappingHelper.map(task, "compilerClasspath", new Callable<FileCollection>() {
                @Override
                public FileCollection call() {
                    return appVariantContext.getScope().getJavaClasspath();
                }
            });
            ConventionMappingHelper
                .map(task, "compilerSources", new Callable<Iterable<ConfigurableFileTree>>() {
                    @Override
                    public Iterable<ConfigurableFileTree> call() throws Exception {
                        return Iterables.filter(appVariantContext.getAwSourceOutputDir(awbBundle),
                                                new Predicate<ConfigurableFileTree>() {
                                                    @Override
                                                    public boolean apply(ConfigurableFileTree input) {
                                                        File
                                                            dataBindingOut = appVariantContext
                                                            .getAwbClassOutputForDataBinding(awbBundle);
                                                        return !dataBindingOut.equals(input.getDir());
                                                    }
                                                });
                    }
                });

            task.setExportClassListTo(variantData.getType().isExportDataBindingClassList() ?
                                          new File(appVariantContext.getAwbLayoutFolderOutputForDataBinding(awbBundle),
                                                   "_generated.txt") : null);
            //task.setPrintMachineReadableErrors(printMachineReadableErrors);
            task.setDataBindingClassOutput(appVariantContext.getAwbClassOutputForDataBinding(awbBundle));
        }

    }

}
