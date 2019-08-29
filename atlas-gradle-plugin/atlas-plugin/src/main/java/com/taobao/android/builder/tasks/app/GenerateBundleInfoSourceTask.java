package com.taobao.android.builder.tasks.app;

import com.alibaba.fastjson.JSON;
import com.android.build.gradle.api.BaseVariantOutput;
import com.android.build.gradle.internal.api.AppVariantContext;
import com.android.build.gradle.internal.tasks.AndroidBuilderTask;
import com.taobao.android.builder.AtlasBuildContext;
import com.taobao.android.builder.tasks.app.prepare.BundleInfoSourceCreator;
import com.taobao.android.builder.tasks.manager.MtlBaseTaskAction;
import com.taobao.android.builder.tools.bundleinfo.model.BasicBundleInfo;
import com.taobao.android.builder.tools.classinject.InjectParam;
import org.apache.commons.io.FileUtils;
import org.gradle.api.GradleException;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * @ClassName Generate
 * @Description TODO
 * @Author zhayu.ll
 * @Date 2019-08-29 16:06
 * @Version 1.0
 */
public class GenerateBundleInfoSourceTask extends AndroidBuilderTask {

    private AppVariantContext appVariantContext;

    private File outputDir;

    @OutputDirectory
    public File getOutputDir() {
        return outputDir;
    }

    private InjectParam injectParam;

    @Input
    public InjectParam getInput() {
        if (null != injectParam) {
            return injectParam;
        }
        try {
            injectParam = AtlasBuildContext.sBuilderAdapter.apkInjectInfoCreator.creteInjectParam(appVariantContext);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return injectParam;
    }

    @TaskAction
    void generate() {

        InjectParam injectParam = getInput();
        List<BasicBundleInfo> info = JSON.parseArray(injectParam.bundleInfo,BasicBundleInfo.class);
        File outputSourceGeneratorFile = new File(outputDir,"com/android/tools/bundleInfo/BundleInfoGenerator.java");
        StringBuffer infoGeneratorSourceStr = new BundleInfoSourceCreator().createBundleInfoSourceStr(info);
        outputSourceGeneratorFile.getParentFile().mkdirs();
        getLogger().info(infoGeneratorSourceStr.toString());
        try {
            FileUtils.writeStringToFile(outputSourceGeneratorFile,infoGeneratorSourceStr.toString());
        } catch (IOException e) {
            throw new GradleException(e.getMessage(), e);
        }


    }


    public static class ConfigAction extends MtlBaseTaskAction<GenerateBundleInfoSourceTask> {

        private AppVariantContext appVariantContext;

        public ConfigAction(AppVariantContext appVariantContext,
                            BaseVariantOutput baseVariantOutputData) {
            super(appVariantContext, baseVariantOutputData);
            this.appVariantContext = appVariantContext;
        }

        @Override
        public String getName() {
            return scope.getTaskName("generate", "bundleInfoSources");
        }

        @Override
        public Class<GenerateBundleInfoSourceTask> getType() {
            return GenerateBundleInfoSourceTask.class;
        }

        @Override
        public void configure(GenerateBundleInfoSourceTask atlasSourceTask) {

            super.configure(atlasSourceTask);

            File srcDir = appVariantContext.getAtlaSourceDir();
            appVariantContext.getVariantData().getTaskContainer().getJavacTask().get().source(srcDir);

            atlasSourceTask.outputDir = srcDir;
            atlasSourceTask.appVariantContext = appVariantContext;

        }
    }
}
