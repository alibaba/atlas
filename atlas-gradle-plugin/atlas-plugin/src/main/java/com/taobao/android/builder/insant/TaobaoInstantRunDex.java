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
import com.android.build.gradle.internal.pipeline.AtlasIntermediateFolderUtils;
import com.android.build.gradle.internal.pipeline.ExtendedContentType;
import com.android.build.gradle.internal.scope.InstantRunVariantScope;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.transforms.InstantRunBuildType;
import com.android.build.gradle.internal.transforms.InstantRunDex;
import com.android.build.gradle.internal.transforms.TransformInputUtil;
import com.android.builder.core.DexOptions;
import com.android.builder.dexing.ClassFileInput;
import com.android.builder.dexing.ClassFileInputs;
import com.android.builder.dexing.DexArchiveBuilder;
import com.android.builder.dexing.r8.ClassFileProviderFactory;
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
import org.gradle.api.file.FileCollection;
import org.gradle.api.logging.Logger;

import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Supplier;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Collectors;
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


    private BaseVariantOutput variantOutput;

    private FileCollection bootClasspath;
    private boolean enableDesugaring;

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
            @NonNull DexOptions dexOptions,
            @NonNull Logger logger,
            int minSdkForDx,
            BaseVariantOutput variantOutput) {
        this.variantScope = transformVariantScope;
        this.variantContext = variantContext;
        this.dexOptions = dexOptions;
        this.logger = new LoggerWrapper(logger);
        this.minSdkForDx = minSdkForDx;
        this.variantOutput = variantOutput;
        this.bootClasspath = variantContext.getScope().getBootClasspath();
        this.enableDesugaring = variantContext.getScope().getJava8LangSupportType() == VariantScope.Java8LangSupport.D8;
    }

    @Override
    public void transform(@NonNull TransformInvocation invocation)
            throws IOException, TransformException, InterruptedException {

        if (!variantContext.getBuildType().getPatchConfig().isCreateIPatch()){
            return;
        }
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
        final JarClassesBuilder jarClassesBuilder = getJarClassBuilder(classesJar);

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
//            return;
        }

        if (preloadJarHooker != null && classesJar.exists()){
           classesJar = preloadJarHooker.process(classesJar);
        }


        if (classesJar.exists()) {
            final ImmutableList.Builder<File> inputFiles = ImmutableList.builder();
            inputFiles.add(classesJar);


            try {
                variantScope
                        .getInstantRunBuildContext()
                        .startRecording(InstantRunBuildContext.TaskType.INSTANT_RUN_DEX);
                convertByteCode(classesJar, getClasspath(invocation),outputFolder);
                variantScope
                        .getInstantRunBuildContext()
                        .addChangedFile(FileType.RELOAD_DEX, new File(outputFolder, "classes.dex"));
            } catch (Exception e) {
                throw new TransformException(e);
            } finally {
                variantScope
                        .getInstantRunBuildContext()
                        .stopRecording(InstantRunBuildContext.TaskType.INSTANT_RUN_DEX);
            }

        }



            variantScope.getInstantRunBuildContext().close();


        if (variantContext.getScope().getInstantRunBuildContext().isInInstantRunMode()) {
            List<File>patchFiles = new ArrayList<>();
            InstantRunBuildContext instantRunBuildContext = variantContext.getScope().getInstantRunBuildContext();
            InstantRunBuildContext.Artifact artifact = instantRunBuildContext.getLastBuild().getArtifactForType(FileType.RELOAD_DEX);
            if (artifact!= null) {
                File patchFile = artifact.getLocation();
                if (patchFile!= null && patchFile.exists()) {
                    patchFiles.add(patchFile);
                }
            }
            InstantRunBuildContext.Artifact resArtifact = instantRunBuildContext.getLastBuild().getArtifactForType(FileType.RESOURCES);
            if (resArtifact!= null) {
                File resFile = resArtifact.getLocation();
                if (resFile!= null && resFile.exists()) {
                    patchFiles.add(resFile);

                }
            }
            String baseVersion = ApkDataUtils.get(variantOutput).getVersionName();
            if (patchFiles.size() > 0) {
                File finalFile = variantContext.getAppVariantOutputContext(ApkDataUtils.get(variantOutput)).getIPatchFile(baseVersion);
                zipPatch(finalFile, patchFiles);
            }else {
                logger.warning("patchFiles is not exist or no classes is modified!");
            }
            return;
        }
    }

    public Collection<Path> getBootClasspath() {
        if (!enableDesugaring) {
            return Collections.emptyList();
        }

        return bootClasspath.getFiles().stream().map(File::toPath).collect(Collectors.toList());
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

    protected void convertByteCode(File inputJar, List<Path> classpath, File outputFolder)
            throws IOException {
        try (ClassFileProviderFactory bootClasspathProvider =
                     new ClassFileProviderFactory(getBootClasspath());
             ClassFileProviderFactory libraryClasspathProvider =
                     new ClassFileProviderFactory(classpath);
             ClassFileInput classInput = ClassFileInputs.fromPath(inputJar.toPath())) {
            DexArchiveBuilder d8DexBuilder =
                    DexArchiveBuilder.createD8DexBuilder(
                            minSdkForDx,
                            true,
                            bootClasspathProvider,
                            libraryClasspathProvider,
                            enableDesugaring,
                            variantContext.getScope().getGlobalScope().getAndroidBuilder().getMessageReceiver());
            d8DexBuilder.convert(classInput.entries(p -> true), outputFolder.toPath(), false);
        }
    }

    @VisibleForTesting
    protected JarClassesBuilder getJarClassBuilder(File outputFile) {
        return new JarClassesBuilder(outputFile);
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

    private List<Path> getClasspath(@NonNull TransformInvocation transformInvocation) {
        if (!enableDesugaring) {
            return Collections.emptyList();
        }

        Collection<File> allFiles =
                TransformInputUtil.getAllFiles(transformInvocation.getReferencedInputs());
        return allFiles.stream().map(File::toPath).collect(Collectors.toList());
    }

    @Override
    public boolean isIncremental() {
        return true;
    }

    public static void zipPatch(File file, List<File> patchFiles) throws IOException {
        FileOutputStream fOutputStream = new FileOutputStream(file);
        ZipOutputStream zoutput = new ZipOutputStream(fOutputStream);
        for (File patchFile:patchFiles) {
            BufferedInputStream inputStream = new BufferedInputStream(new FileInputStream(patchFile));
            byte[] BUFFER = new byte[4096];


            ZipEntry zEntry = new ZipEntry(patchFile.getName());
            zoutput.putNextEntry(zEntry);
            int len;
            while ((len = inputStream.read(BUFFER)) > 0) {
                zoutput.write(BUFFER, 0, len);
            }

            inputStream.close();
        }
        zoutput.closeEntry();
        zoutput.close();
    }


    public static interface PreloadJarHooker{

        File process(File jarFile);
    }
}

