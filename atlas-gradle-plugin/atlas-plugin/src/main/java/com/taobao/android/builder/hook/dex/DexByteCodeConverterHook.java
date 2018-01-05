package com.taobao.android.builder.hook.dex;

import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.internal.api.AppVariantContext;
import com.android.build.gradle.internal.api.AppVariantOutputContext;
import com.android.build.gradle.internal.api.AwbTransform;
import com.android.build.gradle.internal.api.VariantContext;
import com.android.builder.core.*;
import com.android.builder.sdk.TargetInfo;
import com.android.ide.common.blame.Message;
import com.android.ide.common.blame.ParsingProcessOutputHandler;
import com.android.ide.common.blame.parser.DexParser;
import com.android.ide.common.blame.parser.ToolOutputParser;
import com.android.ide.common.internal.WaitableExecutor;
import com.android.ide.common.process.*;
import com.android.utils.ILogger;
import com.taobao.android.builder.AtlasBuildContext;
import com.taobao.android.builder.dependency.AtlasDependencyTree;
import com.taobao.android.builder.dependency.model.AwbBundle;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicLong;


/**
 * @author lilong
 * @create 2017-05-12 Morning in the afternoon
 */

public class DexByteCodeConverterHook extends DexByteCodeConverter {

    private VariantContext variantContext;

    private AppVariantOutputContext variantOutputContext;

    private ILogger logger;

    Collection<File>inputFile= new ArrayList<>();

    private WaitableExecutor waitableExecutor = WaitableExecutor.useGlobalSharedThreadPool();

    public DexByteCodeConverterHook(VariantContext variantContext, AppVariantOutputContext variantOutputContext, ILogger logger, TargetInfo targetInfo, JavaProcessExecutor javaProcessExecutor, boolean verboseExec, ErrorReporter errorReporter) {
        super(logger, targetInfo, javaProcessExecutor, verboseExec, errorReporter);
        this.variantContext = variantContext;
        this.variantOutputContext = variantOutputContext;
        this.logger = logger;
    }


    @Override
    public void runDexer(DexProcessBuilder builder, DexOptions dexOptions, ProcessOutputHandler processOutputHandler) throws ProcessException, IOException, InterruptedException {
        builder.addInputs(inputFile);
        super.runDexer(builder,dexOptions,processOutputHandler);


    }
    @Override
    public void convertByteCode(Collection<File> inputs, File outDexFolder, boolean multidex, File mainDexList, DexOptions dexOptions, ProcessOutputHandler processOutputHandler, int minSdkVersion) throws IOException, InterruptedException, ProcessException {

        for (File file: inputs){
                logger.info("input dexFile:",file.getAbsolutePath());
                 if (file.isDirectory()){
                File[]jars = file.listFiles(new FilenameFilter() {
                    @Override
                    public boolean accept(File dir, String name) {
                        return name.endsWith(".jar");
                    }
                });
                if (jars!= null) {
                    inputFile.addAll(Arrays.asList(jars));
                }
            }
        }
        waitableExecutor.execute((Callable<Void>) () -> {
                    DexByteCodeConverterHook.super.convertByteCode(inputs, outDexFolder, multidex, mainDexList, dexOptions, processOutputHandler, minSdkVersion);
                    return null;
                }
        );
        AtlasDependencyTree atlasDependencyTree = AtlasBuildContext.androidDependencyTrees.get(
                variantContext.getVariantName());

        if (null == atlasDependencyTree) {
            return;
        }
        ProcessOutputHandler outputHandler =
                new ParsingProcessOutputHandler(
                        new ToolOutputParser(new DexParser(), Message.Kind.ERROR, LoggerWrapper.getLogger(DexByteCodeConverterHook.class)),
                        new ToolOutputParser(new DexParser(), LoggerWrapper.getLogger(DexByteCodeConverterHook.class)),
                        AtlasBuildContext.androidBuilderMap.get(variantContext.getProject()).getErrorReporter());


        for (final AwbBundle awbBundle : atlasDependencyTree.getAwbBundles()) {
            waitableExecutor.execute((Callable<Void>) () -> {
                try {

                    long start = System.currentTimeMillis();
                    //create dex
                    File dexOutputFile = ((AppVariantContext) variantContext).getAwbDexOutput(awbBundle.getName());
                    if (dexOutputFile.exists()) {
                        FileUtils.cleanDirectory(dexOutputFile);
                    }else {
                        FileUtils.forceMkdir(dexOutputFile);
                    }

                    // if some of our .jar input files exist, just reset the inputDir to null
                    AwbTransform awbTransform = variantOutputContext.getAwbTransformMap()
                            .get(awbBundle.getName());
                    List<File> inputFiles = new ArrayList<File>();
                    inputFiles.addAll(awbTransform.getInputFiles());
                    inputFiles.addAll(awbTransform.getInputLibraries());
                    if (null != awbTransform.getInputDir()) {
                        inputFiles.add(awbTransform.getInputDir());
                    }
                    AtlasBuildContext.androidBuilderMap.get(variantContext.getProject())
                   .convertByteCode(inputFiles,
                    dexOutputFile,
                   false,
                    null,
                    dexOptions,
                    outputHandler, true);

                } catch (Exception e) {

                }
                return null;


            }
            );
        }

        waitableExecutor.waitForTasksWithQuickFail(true);
    }
}
