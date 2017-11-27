package com.taobao.android.builder.hook.dex;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.builder.core.DexOptions;
import com.android.builder.core.DexProcessBuilder;
import com.android.ide.common.process.ProcessException;
import com.android.ide.common.process.ProcessOutput;
import com.android.ide.common.process.ProcessOutputHandler;
import com.android.ide.common.process.ProcessResult;
import com.google.common.collect.Iterables;
import com.taobao.android.dx.command.DxConsole;
import com.taobao.android.dx.command.dexer.Main;

import java.io.File;
import java.io.IOException;

/**
 * @author lilong
 * @create 2017-05-12 At 8:01
 */

public class DexWrapperHook {

    public static ProcessResult run(
            @NonNull DexProcessBuilder processBuilder,
            @NonNull DexOptions dexOptions,
            @NonNull ProcessOutputHandler outputHandler) throws IOException, ProcessException {
        ProcessOutput output = outputHandler.createOutput();
        int res;
        try {
//            DxConsole.out = outputHandler.createOutput().getStandardOutput();
//            DxConsole.err = outputHandler.createOutput().getErrorOutput();
            DxConsole dxConsole = new DxConsole();
            Main.Arguments args = buildArguments(processBuilder, dexOptions, dxConsole);
            res = new Main().run(args);
        } finally {
            output.close();
        }

        outputHandler.handleOutput(output);
        return new DexProcessResult(res);
    }

    @NonNull
    private static Main.Arguments buildArguments(
            @NonNull DexProcessBuilder processBuilder,
            @NonNull DexOptions dexOptions,
            @NonNull DxConsole dxConsole)
            throws ProcessException {
        Main.Arguments args = new Main.Arguments();

        // Inputs:
        args.fileNames = Iterables.toArray(processBuilder.getFilesToAdd(), String.class);

        // Outputs:
        if (processBuilder.getOutputFile().isDirectory() && !processBuilder.isMultiDex()) {
            args.outName = new File(processBuilder.getOutputFile(), "classes.dex").getPath();
            args.jarOutput = false;
        } else {
            String outputFileAbsolutePath = processBuilder.getOutputFile().getAbsolutePath();
            args.outName = outputFileAbsolutePath;
            args.jarOutput = outputFileAbsolutePath.endsWith(SdkConstants.DOT_JAR);
        }

        // Multi-dex:
        args.multiDex = processBuilder.isMultiDex();
        if (processBuilder.getMainDexList() != null) {
            args.mainDexListFile = processBuilder.getMainDexList().getPath();
        }

        // Other:
        args.verbose = processBuilder.isVerbose();
        // due to b.android.com/82031
//        args.optimize = true;
        args.numThreads = 1;
        args.forceJumbo = dexOptions.getJumboMode();
        if (dexOptions.getAdditionalParameters().contains("--debug")) {
            args.debug = true;
        } else if (dexOptions.getAdditionalParameters().contains("--verbose")) {
            args.verbose = true;
        } else if (dexOptions.getAdditionalParameters().contains("--verbose-dump")) {
            args.verboseDump = true;
        } else if (dexOptions.getAdditionalParameters().contains("--no-files")) {
            args.emptyOk = true;
        } else if (dexOptions.getAdditionalParameters().contains("--no-optimize")) {
            args.optimize = false;
        } else if (dexOptions.getAdditionalParameters().contains("--no-strict")) {
            args.strictNameCheck = false;
        } else if (dexOptions.getAdditionalParameters().contains("--core-library")) {
            args.coreLibrary = true;
        } else if (dexOptions.getAdditionalParameters().contains("--statistics")) {
            args.statistics = true;
        }
//        args.parseFlags(Iterables.toArray(dexOptions.getAdditionalParameters(), String.class));
//        args.makeOptionsObjects();

        return args;
    }

    private static class DexProcessResult implements ProcessResult {

        private int mExitValue;

        DexProcessResult(int exitValue) {
            mExitValue = exitValue;
        }

        @NonNull
        @Override
        public ProcessResult assertNormalExitValue()
                throws ProcessException {
            if (mExitValue != 0) {
                throw new ProcessException(
                        String.format("Return code %d for dex process", mExitValue));
            }

            return this;
        }

        @Override
        public int getExitValue() {
            return mExitValue;
        }

        @NonNull
        @Override
        public ProcessResult rethrowFailure()
                throws ProcessException {
            return assertNormalExitValue();
        }
    }

}
