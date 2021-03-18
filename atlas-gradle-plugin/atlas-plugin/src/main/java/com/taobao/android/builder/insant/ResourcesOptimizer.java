package com.taobao.android.builder.insant;
import com.android.SdkConstants;
import com.android.build.gradle.internal.api.artifact.BuildableArtifactUtil;
import com.android.build.gradle.internal.res.Aapt2MavenUtils;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.ide.common.process.*;
import com.android.utils.FileUtils;
import com.android.utils.LineCollector;
import com.android.utils.StdLogger;
import com.taobao.android.builder.extension.TBuildType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ResourcesOptimizer {

    private File inputFile;

    private File outFile;

    private TBuildType tBuildType;

    public ResourcesOptimizer(File inputApkFile, File outApkFile, TBuildType buildType) {
        this.inputFile = inputApkFile;
        this.outFile = outApkFile;
        this.tBuildType = buildType;

    }

    public void optimize(VariantScope variantScope){
        File resMapFile = new File(BuildableArtifactUtil.singleFile(variantScope
                .getArtifacts()
                .getFinalArtifactFiles(InternalArtifactType.APK_MAPPING)).getParentFile(),"res-mapping.txt");
        File aapt2File = new File(Aapt2MavenUtils.getAapt2FromMaven(variantScope.getGlobalScope()).getSingleFile(), SdkConstants.FN_AAPT2);
        aapt2File.setExecutable(true);
        try {
            List<String>results = new ArrayList<>();
            if (tBuildType.isObfucateStringPool()){
                results = invokeAapt(aapt2File,"optimize", inputFile.getPath(),"--enable-resource-path-shortening",
                    "--enable-resource-obfuscation",
//                        "--enable-sparse-encoding",
                        "--resource-path-shortening-map",resMapFile.getPath(),
                        "-o", outFile.getPath());
            }else if (tBuildType.isShorteningResName()){
                results = invokeAapt(aapt2File,"optimize", inputFile.getPath(),"--enable-resource-path-shortening",
//                        "--enable-sparse-encoding",
                        "--resource-path-shortening-map",resMapFile.getPath(),
                        "-o", outFile.getPath());
            }else{
                FileUtils.renameTo(inputFile,outFile);
            }

            System.err.println(results.toString());
        } catch (ProcessException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private List<String> invokeAapt(File aapt2Executable, String...args) throws ProcessException {
        ProcessOutputHandler processOutputHeader = new ProcessOutputHandler() {
            private ProcessOutput processOutput;
            @Override
            public ProcessOutput createOutput() {
                if (processOutput  == null) {
                    processOutput =  new BaseProcessOutputHandler.BaseProcessOutput();
                }
                return processOutput;
            }

            @Override
            public void handleOutput(ProcessOutput processOutput) throws ProcessException {

            }
        };
        ProcessInfoBuilder processInfoBuilder =new ProcessInfoBuilder()
                .setExecutable(aapt2Executable)
                .addArgs(args);
        DefaultProcessExecutor processExecutor = new DefaultProcessExecutor(new StdLogger(StdLogger.Level.ERROR));
        processExecutor
                .execute(processInfoBuilder.createProcess(), processOutputHeader)
                .rethrowFailure();
        BaseProcessOutputHandler.BaseProcessOutput  processOutput = (BaseProcessOutputHandler.BaseProcessOutput) processOutputHeader.createOutput();
        LineCollector lineCollector = new LineCollector();
        processOutput.processStandardOutputLines(lineCollector);
        return lineCollector.getResult();
    }
}
