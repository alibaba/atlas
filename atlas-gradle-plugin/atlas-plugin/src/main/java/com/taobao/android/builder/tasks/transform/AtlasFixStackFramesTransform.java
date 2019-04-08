package com.taobao.android.builder.tasks.transform;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.transform.*;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.internal.api.AppVariantOutputContext;
import com.android.build.gradle.internal.transforms.FixStackFramesTransform;
import com.android.builder.utils.ExceptionRunnable;
import com.android.builder.utils.FileCache;
import com.android.ide.common.internal.WaitableExecutor;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.io.ByteStreams;
import com.taobao.android.builder.AtlasBuildContext;
import com.taobao.android.builder.dependency.model.AwbBundle;
import com.taobao.android.builder.tasks.app.BuildAtlasEnvTask;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.*;
import java.util.function.Supplier;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * AtlasFixStackFramesTransform
 *
 * @author zhayu.ll
 * @date 18/2/7
 */
public class AtlasFixStackFramesTransform extends Transform {

    public Transform oldTransform;
    private WaitableExecutor waitableExecutor = WaitableExecutor.useGlobalSharedThreadPool();
    private AppVariantOutputContext appVariantOutputContext;

    @NonNull
    private final Supplier<List<File>> androidJarClasspath;
    @NonNull
    private final List<Path> compilationBootclasspath;
    @Nullable
    private final FileCache userCache;
    @Nullable
    private URLClassLoader classLoader = null;

    public AtlasFixStackFramesTransform(AppVariantOutputContext appVariantOutputContext, Supplier<List<File>> androidJarClasspath, List<Path> compilationBootclasspath, FileCache userCache) {
        this.androidJarClasspath = androidJarClasspath;
        this.compilationBootclasspath = compilationBootclasspath;
        this.userCache = userCache;
        this.appVariantOutputContext = appVariantOutputContext;
    }

    @NonNull
    private URLClassLoader getClassLoader(@NonNull TransformInvocation invocation)
            throws MalformedURLException {
        if (classLoader == null) {
            ImmutableList.Builder<URL> urls = new ImmutableList.Builder<>();
            for (File file : androidJarClasspath.get()) {
                urls.add(file.toURI().toURL());
            }
            for (Path bootClasspath : this.compilationBootclasspath) {
                if (Files.exists(bootClasspath)) {
                    urls.add(bootClasspath.toUri().toURL());
                }
            }
            for (TransformInput inputs :
                    Iterables.concat(invocation.getInputs(), invocation.getReferencedInputs())) {
                for (DirectoryInput directoryInput : inputs.getDirectoryInputs()) {
                    if (directoryInput.getFile().isDirectory()) {
                        urls.add(directoryInput.getFile().toURI().toURL());
                    }
                }
                for (JarInput jarInput : inputs.getJarInputs()) {
                    if (jarInput.getFile().isFile()) {
                        urls.add(jarInput.getFile().toURI().toURL());
                    }
                }
            }

            ImmutableList<URL> allUrls = urls.build();
            URL[] classLoaderUrls = allUrls.toArray(new URL[allUrls.size()]);
            classLoader = new URLClassLoader(classLoaderUrls);
        }
        return classLoader;
    }

    private static class FixFramesVisitor extends ClassWriter {

        @NonNull
        private final URLClassLoader classLoader;

        public FixFramesVisitor(int flags, @NonNull URLClassLoader classLoader) {
            super(flags);
            this.classLoader = classLoader;
        }

        @Override
        protected String getCommonSuperClass(String type1, String type2) {
            Class<?> c;
            Class<?> d;
            ClassLoader classLoader = this.classLoader;
            try {
                c = Class.forName(type1.replace('/', '.'), false, classLoader);
                d = Class.forName(type2.replace('/', '.'), false, classLoader);
            } catch (Exception e) {
                throw new RuntimeException(
                        String.format(
                                "Unable to find common supper type for %s and %s.", type1, type2),
                        e);
            }
            if (c.isAssignableFrom(d)) {
                return type1;
            }
            if (d.isAssignableFrom(c)) {
                return type2;
            }
            if (c.isInterface() || d.isInterface()) {
                return "java/lang/Object";
            } else {
                do {
                    c = c.getSuperclass();
                } while (!c.isAssignableFrom(d));
                return c.getName().replace('.', '/');
            }
        }
    }

    private static final LoggerWrapper logger =
            LoggerWrapper.getLogger(FixStackFramesTransform.class);
    private static final FileTime ZERO = FileTime.fromMillis(0);


    @Override
    public String getName() {
        return oldTransform.getName();
    }

    @Override
    public Set<QualifiedContent.ContentType> getInputTypes() {
        return oldTransform.getInputTypes();
    }

    @Override
    public Set<? super QualifiedContent.Scope> getScopes() {
        return oldTransform.getScopes();
    }

    @Override
    public Set<? super QualifiedContent.Scope> getReferencedScopes() {
        return oldTransform.getReferencedScopes();
    }

    @Override
    public Collection<SecondaryFile> getSecondaryFiles() {
        return oldTransform.getSecondaryFiles();
    }

    @Override
    public boolean isIncremental() {
        return false;
    }

    @Override
    public void transform(TransformInvocation transformInvocation) throws TransformException, InterruptedException, IOException {
        TransformOutputProvider outputProvider =
                Preconditions.checkNotNull(transformInvocation.getOutputProvider());

//        boolean incremental = transformInvocation.isIncremental();
//        if (!incremental) {
            outputProvider.deleteAll();
//        }
        Map<JarInput,File> mainDexTransformFiles = new HashMap<>();
        try {
            for (TransformInput input : transformInvocation.getInputs()) {
                for (JarInput jarInput : input.getJarInputs()) {
                    boolean flag = inMainDex(jarInput);
                    File output =
                            outputProvider.getContentLocation(
                                    jarInput.getName(),
                                    jarInput.getContentTypes(),
                                    jarInput.getScopes(),
                                    Format.JAR);
                    if (flag) {
                        mainDexTransformFiles.put(jarInput,output);
                        Files.deleteIfExists(output.toPath());
                        logger.info("process maindex fixStackFrames:"+jarInput.getFile().getAbsolutePath());
                        processJar(jarInput.getFile(), output, transformInvocation);
                    } else {
                        File file = appVariantOutputContext.updateAwbDexFile(jarInput, output);
                        if (file!= null){
                            if (!jarInput.getFile().equals(file)){
                                logger.info("process awb fixStackFrames:"+file.getAbsolutePath());
                                Files.deleteIfExists(output.toPath());
                                processJar(file, output, transformInvocation);
                            }else {
                                logger.info("process awb fixStackFrames:"+jarInput.getFile().getAbsolutePath());
                                Files.deleteIfExists(output.toPath());
                                processJar(jarInput.getFile(), output, transformInvocation);
                            }
                        }else {
                            logger.warning(jarInput.getFile().getAbsolutePath() +"is not in maindex and awb libraries in AtlasFixStackFramesTransform!");
                        }
                    }

                }
            }

            waitableExecutor.waitForTasksWithQuickFail(true);
            AtlasBuildContext.atlasMainDexHelperMap.get(appVariantOutputContext.getVariantContext().getVariantName()).updateMainDexFiles(mainDexTransformFiles);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            throw new TransformException(e);
        } finally {
            if (classLoader != null) {
                classLoader.close();
            }
        }
    }

    private void processJar(
            @NonNull File input, @NonNull File output, @NonNull TransformInvocation invocation) {
        waitableExecutor.execute(
                () -> {
                    ExceptionRunnable fileCreator = createFile(input, output, invocation);
                    if (userCache != null) {
                        FileCache.Inputs key =
                                new FileCache.Inputs.Builder(FileCache.Command.FIX_STACK_FRAMES)
                                        .putFile(
                                                "file",
                                                input,
                                                FileCache.FileProperties.PATH_HASH)
                                        .build();
                        FileCache.QueryResult queryResult = userCache.createFile(output, key, fileCreator);
                        if (queryResult.getQueryEvent().equals(FileCache.QueryEvent.HIT)){
                            logger.info("process fixStackFrames hit:"+input.getAbsolutePath());
                        }else{
                            logger.info("process fixStackFrames miss:"+input.getAbsolutePath());

                        }


                    } else {
                        fileCreator.run();
                    }
                    return null;
                });
    }

    @NonNull
    private ExceptionRunnable createFile(
            @NonNull File input, @NonNull File output, @NonNull TransformInvocation invocation) {
        return () -> {
            try (ZipFile inputZip = new ZipFile(input);
                 ZipOutputStream outputZip =
                         new ZipOutputStream(
                                 new BufferedOutputStream(
                                         Files.newOutputStream(output.toPath())))) {
                Enumeration<? extends ZipEntry> inEntries = inputZip.entries();
                while (inEntries.hasMoreElements()) {
                    ZipEntry entry = inEntries.nextElement();
                    if (!entry.getName().endsWith(SdkConstants.DOT_CLASS)) {
                        continue;
                    }
                    InputStream originalFile =
                            new BufferedInputStream(inputZip.getInputStream(entry));
                    ZipEntry outEntry = new ZipEntry(entry.getName());

                    byte[] newEntryContent =
                            getFixedClass(originalFile, getClassLoader(invocation));
                    CRC32 crc32 = new CRC32();
                    crc32.update(newEntryContent);
                    outEntry.setCrc(crc32.getValue());
                    outEntry.setMethod(ZipEntry.STORED);
                    outEntry.setSize(newEntryContent.length);
                    outEntry.setCompressedSize(newEntryContent.length);
                    outEntry.setLastAccessTime(ZERO);
                    outEntry.setLastModifiedTime(ZERO);
                    outEntry.setCreationTime(ZERO);

                    outputZip.putNextEntry(outEntry);
                    outputZip.write(newEntryContent);
                    outputZip.closeEntry();
                }
            }
        };
    }

    @NonNull
    private static byte[] getFixedClass(
            @NonNull InputStream originalFile, @NonNull URLClassLoader classLoader)
            throws IOException {
        byte[] bytes = ByteStreams.toByteArray(originalFile);
        try {
            ClassReader classReader = new ClassReader(bytes);
            ClassWriter classWriter = new AtlasFixStackFramesTransform.FixFramesVisitor(ClassWriter.COMPUTE_FRAMES, classLoader);
            classReader.accept(classWriter, ClassReader.SKIP_FRAMES);
            return classWriter.toByteArray();
        } catch (Throwable t) {
            // we could not fix it, just copy the original and log the exception
            logger.verbose(t.getMessage());
            return bytes;
        }
    }

    private boolean inMainDex(JarInput jarInput) throws IOException {

        return AtlasBuildContext.atlasMainDexHelperMap.get(appVariantOutputContext.getVariantContext().getVariantName()).inMainDex(jarInput);
    }

}
