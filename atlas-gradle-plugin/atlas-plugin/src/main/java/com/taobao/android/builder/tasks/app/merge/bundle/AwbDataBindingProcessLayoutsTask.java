package com.taobao.android.builder.tasks.app.merge.bundle;

import android.databinding.tool.DataBindingBuilder;
import android.databinding.tool.LayoutXmlProcessor;
import android.databinding.tool.processing.Scope;
import com.android.annotations.NonNull;
import com.android.build.gradle.internal.api.AppVariantContext;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantScope;
import com.taobao.android.builder.dependency.model.AwbBundle;
import com.taobao.android.builder.tasks.app.databinding.AwbXmlProcessor;
import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.incremental.IncrementalTaskInputs;
import org.gradle.api.tasks.incremental.InputFileDetails;
import org.xml.sax.SAXException;
import javax.xml.bind.JAXBException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathExpressionException;
import java.io.File;
import java.io.IOException;

/**
 * @author lilong
 * @create 2017-12-07 下午9:11
 */

public class AwbDataBindingProcessLayoutsTask extends DefaultTask {

    private LayoutXmlProcessor xmlProcessor;

    private File sdkDir;

    private int minSdk;

    private File layoutInputFolder;

    private File layoutOutputFolder;

    private File xmlInfoOutFolder;

    @InputDirectory
    public File getLayoutInputFolder() {
        return layoutInputFolder;
    }

    public void setLayoutInputFolder(File layoutInputFolder) {
        this.layoutInputFolder = layoutInputFolder;
    }

    @OutputDirectory
    public File getLayoutOutputFolder() {
        return layoutOutputFolder;
    }

    public void setLayoutOutputFolder(File layoutOutputFolder) {
        this.layoutOutputFolder = layoutOutputFolder;
    }

    @OutputDirectory
    public File getXmlInfoOutFolder() {
        return xmlInfoOutFolder;
    }

    @TaskAction
    public void processResources(IncrementalTaskInputs incrementalTaskInputs)
            throws ParserConfigurationException, SAXException, XPathExpressionException,
            IOException, JAXBException {
        final LayoutXmlProcessor.ResourceInput resourceInput =
                new LayoutXmlProcessor.ResourceInput(incrementalTaskInputs.isIncremental(),
                        getLayoutInputFolder(), getLayoutOutputFolder());
        if (incrementalTaskInputs.isIncremental()) {
            incrementalTaskInputs.outOfDate(new Action<InputFileDetails>() {
                @Override
                public void execute(InputFileDetails inputFileDetails) {
                    if (inputFileDetails.isAdded()) {
                        resourceInput.added(inputFileDetails.getFile());
                    } else if (inputFileDetails.isModified()) {
                        resourceInput.changed(inputFileDetails.getFile());
                    }
                }
            });
            incrementalTaskInputs.removed(new Action<InputFileDetails>() {
                @Override
                public void execute(InputFileDetails inputFileDetails) {
                    resourceInput.removed(inputFileDetails.getFile());
                }
            });
        }
        xmlProcessor.processResources(resourceInput);
        Scope.assertNoError();
        xmlProcessor.writeLayoutInfoFiles(getXmlInfoOutFolder());
        Scope.assertNoError();
    }

    public void setXmlProcessor(LayoutXmlProcessor xmlProcessor) {
        this.xmlProcessor = xmlProcessor;
    }

    public File getSdkDir() {
        return sdkDir;
    }

    public void setSdkDir(File sdkDir) {
        this.sdkDir = sdkDir;
    }

    public int getMinSdk() {
        return minSdk;
    }

    public void setMinSdk(int minSdk) {
        this.minSdk = minSdk;
    }

    public void setXmlInfoOutFolder(File xmlInfoOutFolder) {
        this.xmlInfoOutFolder = xmlInfoOutFolder;
    }

    public static class AwbDataBindingProcessLayoutsConfigAction
            implements TaskConfigAction<AwbDataBindingProcessLayoutsTask> {
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
        public Class<AwbDataBindingProcessLayoutsTask> getType() {
            return AwbDataBindingProcessLayoutsTask.class;
        }

        @Override
        public void execute(AwbDataBindingProcessLayoutsTask task) {
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
