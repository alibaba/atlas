package com.taobao.android.builder.insant;

import com.android.annotations.NonNull;
import com.android.annotations.VisibleForTesting;
import com.android.build.api.transform.*;
import com.android.build.gradle.internal.api.AppVariantContext;
import com.android.build.gradle.internal.api.AppVariantOutputContext;
import com.android.build.gradle.internal.api.AwbTransform;
import com.android.build.gradle.internal.transforms.ExtractJarsTransform;
import com.android.builder.packaging.PackagingUtils;
import com.android.ide.common.internal.WaitableExecutor;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.io.ByteStreams;
import com.taobao.android.builder.AtlasBuildContext;
import com.taobao.android.builder.tasks.app.BuildAtlasEnvTask;
import com.taobao.android.builder.tools.MD5Util;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

import java.io.*;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static com.android.utils.FileUtils.mkdirs;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * TaobaoExtractJarsTransform
 *
 * @author zhayu.ll
 * @date 18/10/12
 */
public class TaobaoExtractJarsTransform extends Transform {

    @VisibleForTesting
    static Logger LOGGER = Logging.getLogger(TaobaoExtractJarsTransform.class);

    @NonNull
    private final Set<QualifiedContent.ContentType> contentTypes;
    @NonNull
    private final Set<QualifiedContent.Scope> scopes;

    private AppVariantContext variantContext;

    private AppVariantOutputContext variantOutputContext;

    public TaobaoExtractJarsTransform(
            AppVariantContext variantContext,
            AppVariantOutputContext variantOutputContext,
            @NonNull Set<QualifiedContent.ContentType> contentTypes,
            @NonNull Set<QualifiedContent.Scope> scopes) {
        this.variantContext = variantContext;
        this.variantOutputContext = variantOutputContext;
        this.contentTypes = contentTypes;
        this.scopes = scopes;
    }

    @NonNull
    @Override
    public String getName() {
        return "taobaoextractJars";
    }

    @NonNull
    @Override
    public Set<QualifiedContent.ContentType> getInputTypes() {
        return contentTypes;
    }

    @NonNull
    @Override
    public Set<QualifiedContent.Scope> getScopes() {
        return scopes;
    }

    @Override
    public boolean isIncremental() {
        return false;
    }

    @Override
    public void transform(TransformInvocation transformInvocation) throws IOException, TransformException, InterruptedException {
        TransformOutputProvider outputProvider = transformInvocation.getOutputProvider();
        boolean isIncremental = transformInvocation.isIncremental();
        checkNotNull(outputProvider, "Missing output object for transform " + getName());

        // as_input transform and no referenced scopes, all the inputs will in InputOutputStreams.
        final boolean extractCode = contentTypes.contains(QualifiedContent.DefaultContentType.CLASSES);

        if (!isIncremental) {
            outputProvider.deleteAll();
        }
        try {
            WaitableExecutor executor = WaitableExecutor.useGlobalSharedThreadPool();

            AtlasBuildContext.atlasMainDexHelperMap.get(variantContext.getVariantName()).getInputDirs().clear();

                for (File jarFile : AtlasBuildContext.atlasMainDexHelperMap.get(variantContext.getVariantName()).getAllMainDexJars()) {
//                    final File jarFile = jarInput.getFile();

                    JarInput jarInput = makeJarInput(jarFile);

                    LOGGER.warn("input JarFile:"+jarFile.getAbsolutePath());

                    // create an output folder for this jar, keeping its type and scopes.
                    final File outJarFolder =
                            outputProvider.getContentLocation(
                                    jarInput.getName(),
                                    jarInput.getContentTypes(),
                                    jarInput.getScopes(),
                                    Format.DIRECTORY);
                    FileUtils.mkdirs(outJarFolder);
                    LOGGER.warn("outJar Folder:"+outJarFolder.getAbsolutePath());

                    AtlasBuildContext.atlasMainDexHelperMap.get(variantContext.getVariantName()).getInputDirs().add(outJarFolder);
                    if (!isIncremental) {
                        executor.execute(() -> {
                            extractJar(outJarFolder, jarFile, extractCode);
                            return null;
                        });
                    } else {
                        switch (jarInput.getStatus()) {
                            case CHANGED:
                                executor.execute(() -> {
                                    FileUtils.cleanOutputDir(outJarFolder);
                                    extractJar(outJarFolder, jarFile, extractCode);
                                    return null;
                                });
                                break;
                            case ADDED:
                                executor.execute(() -> {
                                    extractJar(outJarFolder, jarFile, extractCode);
                                    return null;
                                });
                                break;
                            case REMOVED:
                                executor.execute(
                                        () -> {
                                            FileUtils.cleanOutputDir(outJarFolder);
                                            return null;
                                        });
                                break;
                            case NOTCHANGED:
                                break;
                        }
                    }
            }

            AtlasBuildContext.atlasMainDexHelperMap.get(variantContext.getVariantName()).addMainDexJars(Sets.newHashSet());

            for (AwbTransform awbTransform:variantOutputContext.getAwbTransformMap().values()){
                File outJarFolder = variantOutputContext.getAwbExtractJarsFolder(awbTransform.getAwbBundle());
                awbTransform.getInputFiles().forEach(file -> executor.execute(() -> {
                    LOGGER.warn("ExtractAwbJar["+awbTransform.getAwbBundle().getPackageName()+"]---------------------"+file.getAbsolutePath());
                    extractJar(outJarFolder, file, extractCode);
                    return null;
                }));
                awbTransform.getInputLibraries().forEach(file -> executor.execute(() -> {
                    LOGGER.warn("ExtractAwbJar["+awbTransform.getAwbBundle().getPackageName()+"]---------------------"+file.getAbsolutePath());
                    extractJar(outJarFolder, file, extractCode);
                    return null;
                }));

                awbTransform.addDir(outJarFolder);
                awbTransform.getInputFiles().clear();
            }



            executor.waitForTasksWithQuickFail(true);

        } catch (InterruptedException e) {
            throw e;
        } catch (Exception e) {
            throw new TransformException(e);
        }
    }

        private JarInput makeJarInput(File file) {
            BuildAtlasEnvTask.FileIdentity finalFileIdentity = AtlasBuildContext.atlasMainDexHelperMap.get(variantContext.getVariantName()).get(file);
            return new JarInput() {
                @Override
                public Status getStatus() {
                    return Status.ADDED;
                }

                @Override
                public String getName() {

                    return MD5Util.getFileMD5(file);
                }

                @Override
                public File getFile() {
                    return file;
                }

                @Override
                public Set<ContentType> getContentTypes() {
                    return ImmutableSet.of(QualifiedContent.DefaultContentType.CLASSES);
                }

                @Override
                public Set<? super Scope> getScopes() {
                    if (finalFileIdentity == null){
                        return  ImmutableSet.of(Scope.EXTERNAL_LIBRARIES);
                    }
                    if (finalFileIdentity.subProject) {
                        return ImmutableSet.of(Scope.SUB_PROJECTS);
                    } else {
                        return ImmutableSet.of(Scope.EXTERNAL_LIBRARIES);
                    }
                }
            };
    }

    private static void extractJar(
            @NonNull File outJarFolder,
            @NonNull File jarFile,
            boolean extractCode) throws IOException {
        if (!jarFile.exists()){
            return;
        }
        mkdirs(outJarFolder);
        HashSet<String> lowerCaseNames = new HashSet<>();
        boolean foundCaseInsensitiveIssue = false;

        try (InputStream fis = new BufferedInputStream(new FileInputStream(jarFile));
             ZipInputStream zis = new ZipInputStream(fis)) {
            // loop on the entries of the intermediary package and put them in the final package.
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                try {
                    String name = entry.getName();

                    // do not take directories
                    if (entry.isDirectory()) {
                        continue;
                    }

                    foundCaseInsensitiveIssue = foundCaseInsensitiveIssue ||
                            !lowerCaseNames.add(name.toLowerCase(Locale.US));

                    TaobaoExtractJarsTransform.Action action = getAction(name, extractCode);
                    if (action == TaobaoExtractJarsTransform.Action.COPY) {
                        File outputFile = new File(outJarFolder,
                                name.replace('/', File.separatorChar));
                        mkdirs(outputFile.getParentFile());

                        try (OutputStream outputStream =
                                     new BufferedOutputStream(new FileOutputStream(outputFile))) {
                            ByteStreams.copy(zis, outputStream);
                            outputStream.flush();
                        }
                    }
                } finally {
                    zis.closeEntry();
                }
            }

        }

        if (foundCaseInsensitiveIssue) {
            LOGGER.error(
                    "Jar '{}' contains multiple entries which will map to "
                            + "the same file on case insensitive file systems.\n"
                            + "This can be caused by obfuscation with useMixedCaseClassNames.\n"
                            + "This build will be incorrect on case insensitive "
                            + "file systems.", jarFile.getAbsolutePath());
        }
    }

    /**
     * Define all possible actions for a Jar file entry.
     */
    enum Action {
        /**
         * Copy the file to the output destination.
         */
        COPY,
        /**
         * Ignore the file.
         */
        IGNORE
    }

    /**
     * Provides an {@link ExtractJarsTransform.Action} for the archive entry.
     * @param archivePath the archive entry path in the archive.
     * @param extractCode whether to extract class files
     * @return the action to implement.
     */
    @NonNull
    public static TaobaoExtractJarsTransform.Action getAction(@NonNull String archivePath, boolean extractCode) {
        // Manifest files are never merged.
        if (JarFile.MANIFEST_NAME.equals(archivePath)) {
            return TaobaoExtractJarsTransform.Action.IGNORE;
        }

        // split the path into segments.
        String[] segments = archivePath.split("/");

        // empty path? skip to next entry.
        if (segments.length == 0) {
            return TaobaoExtractJarsTransform.Action.IGNORE;
        }

        return PackagingUtils.checkFileForApkPackaging(archivePath, extractCode)
                ? TaobaoExtractJarsTransform.Action.COPY
                : TaobaoExtractJarsTransform.Action.IGNORE;
    }

}
