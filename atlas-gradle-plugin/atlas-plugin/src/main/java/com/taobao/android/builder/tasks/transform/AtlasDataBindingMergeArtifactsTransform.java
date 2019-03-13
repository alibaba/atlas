package com.taobao.android.builder.tasks.transform;

import android.databinding.tool.DataBindingBuilder;
import com.android.annotations.NonNull;
import com.android.build.api.transform.*;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.internal.api.AppVariantContext;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.tasks.databinding.DataBindingMergeArtifactsTransform;
import com.android.builder.model.AndroidLibrary;
import com.android.utils.ILogger;
import com.google.common.base.Charsets;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.google.common.io.ByteStreams;
import com.google.common.io.Closer;
import com.taobao.android.builder.AtlasBuildContext;
import com.taobao.android.builder.dependency.AtlasDependencyTree;
import com.taobao.android.builder.dependency.model.AwbBundle;
import org.apache.commons.io.FileUtils;
import org.gradle.api.logging.Logger;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * @author lilong
 * @create 2017-12-08 上午2:28
 */

public class AtlasDataBindingMergeArtifactsTransform extends DataBindingMergeArtifactsTransform {

    @NonNull
    private final ILogger logger;
    private final File outFolder;
    private final AppVariantContext variantContext;

    public AtlasDataBindingMergeArtifactsTransform(AppVariantContext variantContext, Logger logger, File outFolder) {

        super(logger, outFolder);
        this.logger = new LoggerWrapper(logger);
        this.outFolder = outFolder;
        this.variantContext = variantContext;
    }



    @NonNull
    @Override
    public String getName() {
        return "dataBindingMergeArtifacts";
    }

    @NonNull
    @Override
    public Collection<File> getSecondaryDirectoryOutputs() {
        return Collections.singleton(outFolder);
    }

    @Override
    public void transform(@NonNull TransformInvocation transformInvocation)
            throws TransformException, InterruptedException, IOException {


        Collection<TransformInput> inputs = transformInvocation.getReferencedInputs();
        //noinspection ResultOfMethodCallIgnored
        outFolder.mkdirs();
        if (transformInvocation.isIncremental()) {
            incrementalUpdate(inputs);
        } else {
            fullCopy(inputs);
        }
    }

    private void incrementalUpdate(@NonNull Collection<TransformInput> inputs) {


        inputs.forEach(input -> input.getDirectoryInputs().stream().filter((Predicate<DirectoryInput>) input1 -> {
             File file = input1.getFile().getParentFile();
            if (AtlasBuildContext.atlasMainDexHelperMap.get(variantContext.getVariantName()).getMainManifestFiles().containsKey(file.getAbsolutePath()) || variantContext.getAtlasExtension().getTBuildConfig().getAllBundlesToMdex()){
                return false;
            }
            return true;
        }).forEach(directoryInput -> {
            directoryInput.getChangedFiles().forEach((file, status) -> {
                if (isResource(file.getName())) {
                    switch (status) {
                        case NOTCHANGED:
                            // Ignore
                            break;
                        case ADDED:
                        case CHANGED:
                            try {
                                FileUtils.copyFile(file, new File(outFolder, file.getName()));
                            } catch (IOException e) {
                                logger.error(e, "Cannot copy data binding artifacts from "
                                        + "dependency.");
                            }
                            break;
                        case REMOVED:
                            FileUtils.deleteQuietly(new File(outFolder, file.getName()));
                            break;
                    }
                }
            });
        }));
        inputs.forEach(input -> input.getJarInputs().forEach(jarInput -> {
            switch (jarInput.getStatus()) {
                case NOTCHANGED:
                    // Ignore
                    break;
                case ADDED:
                case CHANGED:
                    try {
                        extractBinFilesFromJar(jarInput.getFile());
                    } catch (IOException e) {
                        logger.error(e, "Cannot extract data binding from input jar ");
                    }
                    break;
                case REMOVED:
                    File jarOutFolder = getOutFolderForJarFile(jarInput.getFile());
                    FileUtils.deleteQuietly(jarOutFolder);
                    break;
            }
        }));
    }

    private void fullCopy(Collection<TransformInput> inputs) throws IOException {
        FileUtils.deleteQuietly(outFolder);
        FileUtils.forceMkdir(outFolder);

        for (TransformInput input : inputs) {
            for (DirectoryInput dirInput : input.getDirectoryInputs()) {
                File dataBindingDir = dirInput.getFile();
                if (!dataBindingDir.exists()) {
                    continue;
                }
                if (!AtlasBuildContext.atlasMainDexHelperMap.get(variantContext.getVariantName()).getMainManifestFiles().containsKey(dataBindingDir.getParentFile().getAbsolutePath())&&!variantContext.getAtlasExtension().getTBuildConfig().getAllBundlesToMdex()){
                    continue;
                }

                File artifactFolder = new File(dataBindingDir,
                        DataBindingBuilder.INCREMENTAL_BIN_AAR_DIR);
                if (!artifactFolder.exists()) {
                    continue;
                }
                //noinspection ConstantConditions
                for (String artifactName : artifactFolder.list()) {
                    if (isResource(artifactName)) {
                        FileUtils.copyFile(new File(artifactFolder, artifactName),
                                new File(outFolder, artifactName));
                    }
                }
            }
            for(JarInput jarInput : input.getJarInputs()) {
                File jarFile = jarInput.getFile();
                extractBinFilesFromJar(jarFile);
            }
        }
    }

    private static boolean isResource(String fileName) {
        for (String ext : DataBindingBuilder.RESOURCE_FILE_EXTENSIONS) {
            if (fileName.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    @NonNull
    @Override
    public Set<QualifiedContent.ContentType> getInputTypes() {
        return TransformManager.DATA_BINDING_ARTIFACT;
    }

    @NonNull
    @Override
    public Set<QualifiedContent.Scope> getReferencedScopes() {
        return Sets.immutableEnumSet(QualifiedContent.Scope.SUB_PROJECTS,
                QualifiedContent.Scope.EXTERNAL_LIBRARIES);
    }

    @NonNull
    @Override
    public Set<QualifiedContent.Scope> getScopes() {
        return TransformManager.EMPTY_SCOPES;
    }

    @NonNull
    @Override
    public Set<QualifiedContent.ContentType> getOutputTypes() {
        //noinspection unchecked
        return ImmutableSet.of();
    }

    @Override
    public boolean isIncremental() {
        return true;
    }

    private void extractBinFilesFromJar(File jarFile) throws IOException {
        File jarOutFolder = getOutFolderForJarFile(jarFile);
        FileUtils.deleteQuietly(jarOutFolder);
        FileUtils.forceMkdir(jarOutFolder);

        try (Closer localCloser = Closer.create()) {
            FileInputStream fis = localCloser.register(new FileInputStream(jarFile));
            ZipInputStream zis = localCloser.register(new ZipInputStream(fis));
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }

                String name = entry.getName();

                if (!isResource(name)) {
                    continue;
                }
                // get rid of the path. We don't need it since the file name includes the domain
                name = new File(name).getName();
                File out = new File(jarOutFolder, name);
                //noinspection ResultOfMethodCallIgnored
                FileOutputStream fos = localCloser.register(new FileOutputStream(out));
                ByteStreams.copy(zis, fos);
                zis.closeEntry();
            }
        }
    }

    @NonNull
    private File getOutFolderForJarFile(File jarFile) {
        return new File(outFolder, getJarFilePrefix(jarFile));
    }

    /**
     * Files exported from jars are exported into a certain folder so that we can rebuild them
     * when the related jar file changes.
     */
    @NonNull
    private static String getJarFilePrefix(@NonNull File inputFile) {
        // get the filename
        String name = inputFile.getName();
        // remove the extension
        int pos = name.lastIndexOf('.');
        if (pos != -1) {
            name = name.substring(0, pos);
        }

        // add a hash of the original file path.
        String input = inputFile.getAbsolutePath();
        HashFunction hashFunction = Hashing.sha1();
        HashCode hashCode = hashFunction.hashString(input, Charsets.UTF_16LE);

        return name + "-" + hashCode.toString();
    }


    private void processAwbsDataBindings() throws IOException{
        AtlasDependencyTree atlasDependencyTree =  AtlasBuildContext.androidDependencyTrees.get(variantContext.getVariantName());
        for (AwbBundle awbBundle:atlasDependencyTree.getAwbBundles()){
            File awbDataBindDepsOutDir = variantContext.getAwbDataBindingMergeArtifacts(awbBundle);
            for (AndroidLibrary androidLibrary:awbBundle.getAndroidLibraries()){
                File artifactFolder = new File(androidLibrary.getFolder(),DataBindingBuilder.DATA_BINDING_ROOT_FOLDER_IN_AAR+"/"+DataBindingBuilder.INCREMENTAL_BIN_AAR_DIR);
                if (artifactFolder.exists() && artifactFolder.listFiles()!= null){
                    for (String artifactName : artifactFolder.list()) {
                        if (isResource(artifactName)) {
                            FileUtils.copyFile(new File(artifactFolder, artifactName),
                                    new File(awbDataBindDepsOutDir, artifactName));
                        }
                    }
                }
            }
            File awbDataBindDir = new File(awbBundle.getAndroidLibrary().getFolder(),DataBindingBuilder.DATA_BINDING_ROOT_FOLDER_IN_AAR+"/"+DataBindingBuilder.INCREMENTAL_BIN_AAR_DIR);
            if (awbDataBindDir.exists() && awbDataBindDir.listFiles()!= null){
                for (String artifactName : awbDataBindDir.list()) {
                    if (isResource(artifactName)) {
                        FileUtils.copyFile(new File(awbDataBindDir, artifactName),
                                new File(awbDataBindDepsOutDir, artifactName));
                    }
                }
            }
        }
    }
}
