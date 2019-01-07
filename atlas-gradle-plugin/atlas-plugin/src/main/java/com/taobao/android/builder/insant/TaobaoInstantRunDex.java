package com.taobao.android.builder.insant;

import com.alibaba.fastjson.JSON;
import com.android.annotations.NonNull;
import com.android.annotations.VisibleForTesting;
import com.android.build.api.transform.*;
import com.android.build.gradle.api.BaseVariantOutput;
import com.android.build.gradle.internal.ApkDataUtils;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.internal.api.AppVariantContext;
import com.android.build.gradle.internal.incremental.FileType;
import com.android.build.gradle.internal.incremental.InstantRunBuildContext;
import com.android.build.gradle.internal.pipeline.ExtendedContentType;
import com.android.build.gradle.internal.scope.InstantRunVariantScope;
import com.android.build.gradle.internal.transforms.InstantRunBuildType;
import com.android.build.gradle.internal.transforms.InstantRunDex;
import com.android.builder.core.DexByteCodeConverter;
import com.android.builder.core.DexOptions;
import com.android.builder.model.OptionalCompilationStep;
import com.android.ide.common.process.LoggedProcessOutputHandler;
import com.android.ide.common.process.ProcessException;
import com.android.utils.FileUtils;
import com.android.utils.ILogger;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import com.taobao.android.builder.tools.MD5Util;
import org.dom4j.DocumentException;
import org.gradle.api.logging.Logger;

import java.io.*;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * TaobaoInstantRunDex
 *
 * @author zhayu.ll
 * @date 18/10/18
 */
public class TaobaoInstantRunDex extends Transform {


    @NonNull
    private final DexOptions dexOptions;

    @NonNull
    private final ILogger logger;

    private DexByteCodeConverter dexByteCodeConverter;

    private BaseVariantOutput variantOutput;

    public PreloadJarHooker getPreloadJarHooker() {
        return preloadJarHooker;
    }

    public void setPreloadJarHooker(PreloadJarHooker preloadJarHooker) {
        this.preloadJarHooker = preloadJarHooker;
    }

    private PreloadJarHooker preloadJarHooker;
    @NonNull
    private final InstantRunVariantScope variantScope;
    private final int minSdkForDx;
    private AppVariantContext variantContext;

    public TaobaoInstantRunDex(
            AppVariantContext variantContext,
            @NonNull InstantRunVariantScope transformVariantScope,
            DexByteCodeConverter dexByteCodeConverter,
            @NonNull DexOptions dexOptions,
            @NonNull Logger logger,
            int minSdkForDx,
            BaseVariantOutput variantOutput) {
        this.variantScope = transformVariantScope;
        this.variantContext = variantContext;
        this.dexByteCodeConverter = dexByteCodeConverter;
        this.dexOptions = dexOptions;
        this.logger = new LoggerWrapper(logger);
        this.minSdkForDx = minSdkForDx;
        this.variantOutput = variantOutput;
    }

    @Override
    public void transform(@NonNull TransformInvocation invocation)
            throws IOException, TransformException, InterruptedException {

        File outputFolder = variantScope.getReloadDexOutputFolder();
//        boolean changesAreCompatible =
//                variantScope.getInstantRunBuildContext().hasPassedVerification();
//        boolean restartDexRequested =
//                variantScope.getGlobalScope().isActive(OptionalCompilationStep.RESTART_ONLY);

//        if (!changesAreCompatible || restartDexRequested) {
            FileUtils.cleanOutputDir(outputFolder);
//            return;
//        }

        // create a tmp jar file.
        File classesJar = new File(outputFolder, "classes.jar");
        if (classesJar.exists()) {
            FileUtils.delete(classesJar);
        }
        Files.createParentDirs(classesJar);
        final TaobaoInstantRunDex.JarClassesBuilder jarClassesBuilder = getJarClassBuilder(classesJar);

        try {
            for (TransformInput input : invocation.getReferencedInputs()) {
                for (DirectoryInput directoryInput : input.getDirectoryInputs()) {
                    if (!directoryInput.getContentTypes()
                            .contains(ExtendedContentType.CLASSES_ENHANCED)) {
                        continue;
                    }
                    final File folder = directoryInput.getFile();
                    if (invocation.isIncremental()) {
                        for (Map.Entry<File, Status> entry :
                                directoryInput.getChangedFiles().entrySet()) {
                            if (entry.getValue() != Status.REMOVED) {
                                File file = entry.getKey();
                                if (file.isFile()) {
                                    jarClassesBuilder.add(folder, file);
                                }
                            }
                        }
                    } else {
                        Iterable<File> files = FileUtils.getAllFiles(folder);
                        for (File inputFile : files) {
                            jarClassesBuilder.add(folder, inputFile);
                        }
                    }
                }
            }
        } finally {
            jarClassesBuilder.close();
        }

        // if no files were added, clean up and return.
        if (jarClassesBuilder.isEmpty()) {
            FileUtils.cleanOutputDir(outputFolder);
            return;
        }

        if (preloadJarHooker != null){
           classesJar = preloadJarHooker.process(classesJar);
        }

        final ImmutableList.Builder<File> inputFiles = ImmutableList.builder();
        inputFiles.add(classesJar);

        try {
            variantScope
                    .getInstantRunBuildContext()
                    .startRecording(InstantRunBuildContext.TaskType.INSTANT_RUN_DEX);
            convertByteCode(inputFiles.build(), outputFolder);
            variantScope
                    .getInstantRunBuildContext()
                    .addChangedFile(FileType.RELOAD_DEX, new File(outputFolder, "classes.dex"));
        } catch (ProcessException e) {
            throw new TransformException(e);
        } finally {
            variantScope
                    .getInstantRunBuildContext()
                    .stopRecording(InstantRunBuildContext.TaskType.INSTANT_RUN_DEX);
        }

        variantScope.getInstantRunBuildContext().close();

        if (variantContext.getScope().getInstantRunBuildContext().isInInstantRunMode()) {
            InstantRunBuildContext instantRunBuildContext = variantContext.getScope().getInstantRunBuildContext();
            InstantRunBuildContext.Artifact artifact = instantRunBuildContext.getLastBuild().getArtifactForType(FileType.RELOAD_DEX);
            File patchFile = artifact.getLocation();
            String baseVersion = ApkDataUtils.get(variantOutput).getVersionName();
            if (artifact!= null && patchFile.exists()) {
                File finalFile = variantContext.getAppVariantOutputContext(ApkDataUtils.get(variantOutput)).getIPatchFile(baseVersion);
                zipPatch(finalFile, patchFile);
            }else {
                logger.warning("patchFile is not exist or no classes is modified!");
            }
            return;
        }
    }



    @VisibleForTesting
    static class JarClassesBuilder implements Closeable {
        final File outputFile;
        private JarOutputStream jarOutputStream;
        boolean empty = true;

        private JarClassesBuilder(@NonNull File outputFile) {
            this.outputFile = outputFile;
        }

        void add(File inputDir, File file) throws IOException {
            if (jarOutputStream == null) {
                jarOutputStream = new JarOutputStream(
                        new BufferedOutputStream(new FileOutputStream(outputFile)));
            }
            empty = false;
            copyFileInJar(inputDir, file, jarOutputStream);
        }

        @Override
        public void close() throws IOException {
            if (jarOutputStream != null) {
                jarOutputStream.close();
            }
        }

        boolean isEmpty() {
            return empty;
        }
    }

    @VisibleForTesting
    protected void convertByteCode(List<File> inputFiles, File outputFolder)
            throws InterruptedException, ProcessException, IOException {
        dexByteCodeConverter
                .convertByteCode(
                        inputFiles,
                        outputFolder,
                        false /* multiDexEnabled */,
                        null /*getMainDexListFile */,
                        dexOptions,
                        new LoggedProcessOutputHandler(logger),
                        minSdkForDx);
    }

    @VisibleForTesting
    protected TaobaoInstantRunDex.JarClassesBuilder getJarClassBuilder(File outputFile) {
        return new TaobaoInstantRunDex.JarClassesBuilder(outputFile);
    }

    private static void copyFileInJar(File inputDir, File inputFile, JarOutputStream jarOutputStream)
            throws IOException {

        String entryName = inputFile.getPath().substring(inputDir.getPath().length() + 1);
        JarEntry jarEntry = new JarEntry(entryName);
        jarOutputStream.putNextEntry(jarEntry);
        Files.copy(inputFile, jarOutputStream);
        jarOutputStream.closeEntry();
    }

    @NonNull
    @Override
    public String getName() {
        return "instantReloadDex";
    }

    @NonNull
    @Override
    public Set<QualifiedContent.ContentType> getInputTypes() {
        return ImmutableSet.of(ExtendedContentType.CLASSES_ENHANCED);
    }

    @NonNull
    @Override
    public Set<QualifiedContent.Scope> getScopes() {
        return ImmutableSet.of();
    }

    @NonNull
    @Override
    public Set<QualifiedContent.Scope> getReferencedScopes() {
        return Sets.immutableEnumSet(QualifiedContent.Scope.PROJECT);
    }

    @NonNull
    @Override
    public Map<String, Object> getParameterInputs() {
        ImmutableMap.Builder<String, Object> params = ImmutableMap.builder();
        params.put(
                "changesAreCompatible",
                variantScope.getInstantRunBuildContext().hasPassedVerification());
        params.put(
                "restartDexRequested",
                variantScope.getGlobalScope().isActive(OptionalCompilationStep.RESTART_ONLY));
        params.put("minSdkForDx", minSdkForDx);
        return params.build();
    }

    @NonNull
    @Override
    public Collection<File> getSecondaryDirectoryOutputs() {
        return ImmutableList.of(variantScope.getReloadDexOutputFolder());
    }

    @Override
    public boolean isIncremental() {
        return true;
    }

    public static void zipPatch(File file, File dexFile) throws IOException {
        BufferedInputStream inputStream = new BufferedInputStream(new FileInputStream(dexFile));
        byte[] BUFFER = new byte[4096];
        FileOutputStream fOutputStream = new FileOutputStream(file);
        ZipOutputStream zoutput = new ZipOutputStream(fOutputStream);
        ZipEntry zEntry = new ZipEntry(dexFile.getName());
        zoutput.putNextEntry(zEntry);
        int len;
        while ((len = inputStream.read(BUFFER)) > 0) {
            zoutput.write(BUFFER, 0, len);
        }
        zoutput.closeEntry();
        zoutput.close();
        inputStream.close();
    }


    public static interface PreloadJarHooker{

        File process(File jarFile);
    }
}

