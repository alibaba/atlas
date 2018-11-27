package com.android.tools.r8;
import com.android.tools.r8.dex.ApplicationReader;
import com.android.tools.r8.dex.AtlasApplicationWriter;
import com.android.tools.r8.dex.Marker;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.errors.MainDexError;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.ir.conversion.IRConverter;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.utils.*;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

/**
 * AtlasD8
 *
 * @author zhayu.ll
 * @date 18/4/3
 */
public class AtlasD8 {
    private static final String VERSION = "v0.1.14";
    private static final int STATUS_ERROR = 1;

    public static boolean deepShrink = false;

    private AtlasD8() {

    }

    public static D8Output run(D8Command command) throws IOException {
        InternalOptions options = command.getInternalOptions();
        options.skipDebugInfoOpt = true;
        options.skipDebugLineNumberOpt = true;
        options.printTimes = true;
        CompilationResult result = runForTesting(command.getInputApp(), options);

        assert result != null;

        D8Output output = new D8Output(result.androidApp, command.getOutputMode());
        if (command.getOutputPath() != null) {
            output.write(command.getOutputPath());
        }

        return output;
    }

    public static D8Output run(D8Command command, ExecutorService executor) throws IOException {
        InternalOptions options = command.getInternalOptions();

        options.skipDebugLineNumberOpt = true;
        options.skipDebugInfoOpt = true;
        options.printTimes = true;
        CompilationResult result = runForTesting(command.getInputApp(), options, executor);

        assert result != null;

        D8Output output = new D8Output(result.androidApp, command.getOutputMode());
        if (command.getOutputPath() != null) {
            output.write(command.getOutputPath());
        }

        return output;
    }

    private static void run(String[] args) throws IOException, CompilationException {
        D8Command.Builder builder = D8Command.parse(args);
        if (builder.getOutputPath() == null) {
            builder.setOutputPath(Paths.get("."));
        }

        D8Command command = builder.build();
        if (command.isPrintHelp()) {
            System.out.println(D8Command.USAGE_MESSAGE);
        } else if (command.isPrintVersion()) {
            System.out.println("D8 v0.1.14");
        } else {
            run(command);
        }
    }

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            System.err.println(D8Command.USAGE_MESSAGE);
            System.exit(1);
        }

        try {
            run(args);
        } catch (NoSuchFileException var3) {
            System.err.println("File not found: " + var3.getFile());
            System.exit(1);
        } catch (FileAlreadyExistsException var4) {
            System.err.println("File already exists: " + var4.getFile());
            System.exit(1);
        } catch (IOException var5) {
            System.err.println("Failed to read or write application files: " + var5.getMessage());
            System.exit(1);
        } catch (RuntimeException var6) {
            System.err.println("Compilation failed with an internal error.");
            Throwable cause = var6.getCause() == null ? var6 : var6.getCause();
            ((Throwable)cause).printStackTrace();
            System.exit(1);
        } catch (CompilationException var7) {
            System.err.println("Compilation failed: " + var7.getMessage());
            System.err.println(D8Command.USAGE_MESSAGE);
            System.exit(1);
        }

    }

    static CompilationResult runForTesting(AndroidApp inputApp, InternalOptions options) throws IOException {
        ExecutorService executor = ThreadUtils.getExecutorService(options);

        CompilationResult var3;
        try {
            var3 = runForTesting(inputApp, options, executor);
        } finally {
            executor.shutdown();
        }

        return var3;
    }

    private static Marker getMarker(InternalOptions options) {
        return options.hasMarker() ? options.getMarker() : (new Marker(Marker.Tool.D8)).put("version", "v0.1.14").put("min-api", options.minApiLevel);
    }

    private static CompilationResult runForTesting(AndroidApp inputApp, InternalOptions options, ExecutorService executor) throws IOException {
        try {
            assert !inputApp.hasPackageDistribution();

            options.skipMinification = true;
            options.inlineAccessors = false;
            options.outline.enabled = false;
            Timing timing = new Timing("DX timer");
            DexApplication app = (new ApplicationReader(inputApp, options, timing)).read(executor);
            AppInfo appInfo = new AppInfo(app);
            app = optimize(app, appInfo, options, timing, executor);
            if (options.hasMethodsFilter()) {
                System.out.println("Finished compilation with method filter: ");
                options.methodsFilter.forEach((m) -> {
                    System.out.println("  - " + m);
                });
                return null;
            } else {
                Marker marker = getMarker(options);
                CompilationResult output = new CompilationResult((new AtlasApplicationWriter(app, appInfo, options, marker, (byte[])null, NamingLens.getIdentityLens(), (byte[])null)).write((PackageDistribution)null, executor), app, appInfo);
                options.printWarnings();
                return output;
            }
        } catch (MainDexError var8) {
            throw new CompilationError(var8.getMessageForD8());
        } catch (ExecutionException var9) {
            if (var9.getCause() instanceof CompilationError) {
                throw (CompilationError)var9.getCause();
            } else {
                throw new RuntimeException(var9.getMessage(), var9.getCause());
            }
        }
    }

    private static DexApplication optimize(DexApplication application, AppInfo appInfo, InternalOptions options, Timing timing, ExecutorService executor) throws IOException, ExecutionException {
        CfgPrinter printer = options.printCfg ? new CfgPrinter() : null;
        IRConverter converter = new IRConverter(timing, application, appInfo, options, printer);
        application = converter.convertToDex(executor);
        if (options.printCfg) {
            if (options.printCfgFile != null && !options.printCfgFile.isEmpty()) {
                OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(options.printCfgFile), StandardCharsets.UTF_8);
                Throwable var8 = null;

                try {
                    writer.write(printer.toString());
                } catch (Throwable var17) {
                    var8 = var17;
                    throw var17;
                } finally {
                    if (writer != null) {
                        if (var8 != null) {
                            try {
                                writer.close();
                            } catch (Throwable var16) {
                                var8.addSuppressed(var16);
                            }
                        } else {
                            writer.close();
                        }
                    }

                }
            } else {
                System.out.print(printer.toString());
            }
        }

        return application;
    }
}
