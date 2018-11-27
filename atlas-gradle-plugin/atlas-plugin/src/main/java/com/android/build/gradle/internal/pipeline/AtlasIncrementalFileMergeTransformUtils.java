package com.android.build.gradle.internal.pipeline;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.apkzlib.utils.CachedSupplier;
import com.android.apkzlib.utils.IOExceptionRunnable;
import com.android.build.api.transform.*;
import com.android.build.gradle.internal.api.AwbTransform;
import com.android.builder.files.FileCacheByPath;
import com.android.builder.files.IncrementalRelativeFileSets;
import com.android.builder.files.RelativeFile;
import com.android.builder.files.RelativeFiles;
import com.android.builder.merge.IncrementalFileMergerInput;
import com.android.builder.merge.LazyIncrementalFileMergerInput;
import com.android.builder.merge.LazyIncrementalFileMergerInputs;
import com.android.ide.common.res2.FileStatus;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.taobao.android.builder.AtlasBuildContext;
import com.taobao.android.builder.dependency.model.AwbBundle;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.*;

/**
 * @author lilong
 * @create 2017-12-20 下午1:08
 */
public class AtlasIncrementalFileMergeTransformUtils {

    private AtlasIncrementalFileMergeTransformUtils() {}

    /**
     * Creates an {@link IncrementalFileMergerInput} from a {@link JarInput}. All files in the jar
     * will be reported in the incremental input. This method assumes the input contains
     * incremental information.
     *
     * @param jarInput the jar input
     * @param zipCache the zip cache; the cache will not be modified
     * @param cacheUpdate will receive actions to update the cache for the next iteration
     * @param contentMap if not {@code null}, receives a mapping from all generated inputs to
     * {@link QualifiedContent} they came from
     * @return the input
     */
    @NonNull
    public static IncrementalFileMergerInput toIncrementalInput(
            @NonNull JarInput jarInput,
            @NonNull FileCacheByPath zipCache,
            @NonNull List<Runnable> cacheUpdate,
            @Nullable Map<IncrementalFileMergerInput, QualifiedContent> contentMap) {
        File jarFile = jarInput.getFile();
        if (jarFile.isFile()) {
            cacheUpdate.add(IOExceptionRunnable.asRunnable(() -> zipCache.add(jarFile)));
        } else {
            cacheUpdate.add(IOExceptionRunnable.asRunnable(() -> zipCache.remove(jarFile)));
        }

        IncrementalFileMergerInput input =
                new LazyIncrementalFileMergerInput(
                        jarFile.getAbsolutePath(),
                        new CachedSupplier<>(() -> computeUpdates(jarInput, zipCache)),
                        new CachedSupplier<>(() -> computeFiles(jarInput)));
        if (contentMap != null) {
            contentMap.put(input, jarInput);
        }

        return input;
    }

    /**
     * Creates an {@link IncrementalFileMergerInput} from a {@link DirectoryInput}. All files in the
     * directory will be reported in the incremental input. This method assumes the input contains
     * incremental information.
     *
     * @param directoryInput the directory input
     * @param contentMap if not {@code null}, receives a mapping from all generated inputs to
     * {@link QualifiedContent} they came from
     * @return the input
     */
    @NonNull
    public static IncrementalFileMergerInput toIncrementalInput(
            @NonNull DirectoryInput directoryInput,
            @Nullable Map<IncrementalFileMergerInput, QualifiedContent> contentMap) {
        IncrementalFileMergerInput input =
                new LazyIncrementalFileMergerInput(
                        directoryInput.getFile().getAbsolutePath(),
                        new CachedSupplier<>(() -> computeUpdates(directoryInput)),
                        new CachedSupplier<>(() -> computeFiles(directoryInput)));

        if (contentMap != null) {
            contentMap.put(input, directoryInput);
        }

        return input;
    }

    /**
     * Creates an {@link IncrementalFileMergerInput} from a {@link JarInput}. All files in
     * the zip will be reported in the incremental input. This method assumes the input does not
     * contain incremental information. All files will be reported as new.
     *
     * @param jarInput the jar input
     * @param zipCache the zip cache; the cache will not be modified
     * @param cacheUpdate will receive actions to update the cache for the next iteration
     * @param contentMap if not {@code null}, receives a mapping from all generated inputs to
     * {@link QualifiedContent} they came from
     * @return the input or {@code null} if the jar input does not exist
     */
    @Nullable
    public static IncrementalFileMergerInput toNonIncrementalInput(
            @NonNull JarInput jarInput,
            @NonNull FileCacheByPath zipCache,
            @NonNull List<Runnable> cacheUpdate,
            @Nullable Map<IncrementalFileMergerInput, QualifiedContent> contentMap) {
        File jarFile = jarInput.getFile();
        if (!jarFile.isFile()) {
            return null;
        }

        cacheUpdate.add(IOExceptionRunnable.asRunnable(() -> zipCache.add(jarFile)));

        IncrementalFileMergerInput input =
                LazyIncrementalFileMergerInputs.fromNew(
                        jarFile.getAbsolutePath(),
                        ImmutableSet.of(jarFile));
        if (contentMap != null) {
            contentMap.put(input, jarInput);
        }

        return input;
    }

    /**
     * Creates an {@link IncrementalFileMergerInput} from a {@link DirectoryInput}. All files in
     * the directory will be reported in the incremental input. This method assumes the input does
     * not contain incremental information. All files will be reported as new.
     *
     * @param directoryInput the directory input
     * @param contentMap if not {@code null}, receives a mapping from all generated inputs to
     * {@link QualifiedContent} they came from
     * @return the input or {@code null} if the jar input does not exist
     */
    @Nullable
    public static IncrementalFileMergerInput toNonIncrementalInput(
            @NonNull DirectoryInput directoryInput,
            @Nullable Map<IncrementalFileMergerInput, QualifiedContent> contentMap) {
        File dirFile = directoryInput.getFile();
        if (!dirFile.isDirectory()) {
            return null;
        }

        IncrementalFileMergerInput input =
                LazyIncrementalFileMergerInputs.fromNew(
                        dirFile.getAbsolutePath(),
                        ImmutableSet.of(dirFile));

        if (contentMap != null) {
            contentMap.put(input, directoryInput);
        }

        return input;
    }

    /**
     * Computes all updates in a {@link JarInput}.
     *
     * @param jarInput the jar input
     * @param zipCache the cache of zip files; the cache will not be modified
     * @return a mapping from all files that have changed to the type of change
     */
    @NonNull
    private static ImmutableMap<RelativeFile, FileStatus> computeUpdates(
            @NonNull JarInput jarInput,
            @NonNull FileCacheByPath zipCache) {
        try {
            switch (jarInput.getStatus()) {
                case ADDED:
                    return IncrementalRelativeFileSets.fromZip(
                            jarInput.getFile(),
                            FileStatus.NEW);
                case REMOVED:
                    File cached = zipCache.get(jarInput.getFile());
                    if (cached == null) {
                        throw new RuntimeException("File '" + jarInput.getFile() + "' was "
                                + "deleted, but previous version not found in cache");
                    }

                    return IncrementalRelativeFileSets.fromZip(cached, FileStatus.REMOVED);
                case CHANGED:
                    return IncrementalRelativeFileSets.fromZip(
                            jarInput.getFile(),
                            zipCache,
                            new HashSet<>());
                case NOTCHANGED:
                    return ImmutableMap.of();
                default:
                    throw new AssertionError();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Computes a set with all files in a {@link JarInput}.
     *
     * @param jarInput the jar input
     * @return all files in the input
     */
    @NonNull
    private static ImmutableSet<RelativeFile> computeFiles(@NonNull JarInput jarInput) {
        File jar = jarInput.getFile();
        assert jar.isFile();

        try {
            return RelativeFiles.fromZip(jar);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }


    /**
     * Computes all updates in a {@link DirectoryInput}.
     *
     * @param directoryInput the directory input
     * @return a mapping from all files that have changed to the type of change
     */
    @NonNull
    private static ImmutableMap<RelativeFile, FileStatus> computeUpdates(
            @NonNull DirectoryInput directoryInput) {
        ImmutableMap.Builder<RelativeFile, FileStatus> builder = ImmutableMap.builder();

        Map<File, Status> changedFiles = directoryInput.getChangedFiles();
        for (Map.Entry<File, Status> changedFile : changedFiles.entrySet()) {
            RelativeFile rf = new RelativeFile(directoryInput.getFile(), changedFile.getKey());
            FileStatus status = mapStatus(changedFile.getValue());
            if (status != null && !(new File(rf.getBase(), rf.getRelativePath()).isDirectory())) {
                builder.put(rf, status);
            }
        }

        return builder.build();
    }

    /**
     * Computes a set with all files in a {@link DirectoryInput}.
     *
     * @param directoryInput the directory input
     * @return all files in the input
     */
    @NonNull
    private static ImmutableSet<RelativeFile> computeFiles(@NonNull DirectoryInput directoryInput) {
        File dir = directoryInput.getFile();
        assert dir.isDirectory();
        return RelativeFiles.fromDirectory(dir);
    }

    /**
     * Creates a list of {@link IncrementalFileMergerInput} from a {@link TransformInput}. All files
     * in the input will be reported in the incremental input, including those inside zips. This
     * method assumes the input contains incremental information.
     *
     * @param transformInput the transform input
     * @param zipCache the zip cache; the cache will not be modified
     * @param cacheUpdates receives updates to the cache
     * @param contentMap if not {@code null}, receives a mapping from all generated inputs to
     * {@link QualifiedContent} they came from
     * @param awbBundle
     * @return the inputs
     */
    @NonNull
    public static ImmutableList<IncrementalFileMergerInput> toIncrementalInput(
            @NonNull TransformInput transformInput,
            @NonNull FileCacheByPath zipCache,
            @NonNull List<Runnable> cacheUpdates,
            @Nullable Map<IncrementalFileMergerInput, QualifiedContent> contentMap, AwbTransform awbTransform,String name) {
        ImmutableList.Builder<IncrementalFileMergerInput> builder = ImmutableList.builder();

        for (JarInput jarInput : transformInput.getJarInputs()) {

            if (awbTransform == null) {
                if (!AtlasBuildContext.atlasMainDexHelperMap.get(name).getMainResFiles().containsKey(jarInput.getFile().getAbsolutePath())) {
                    continue;
                }
            } else {
                if (!awbTransform.getLibraryResourcesInutDir().contains(jarInput.getFile())){
                    continue;
                }

            }
            builder.add(toIncrementalInput(jarInput, zipCache, cacheUpdates, contentMap));
        }

        for (DirectoryInput dirInput : transformInput.getDirectoryInputs()) {
            if (awbTransform == null) {
                if (!AtlasBuildContext.atlasMainDexHelperMap.get(name).getMainSoFiles().containsKey(dirInput.getFile().getAbsolutePath())) {
                    continue;
                }

            }else {
                if (!awbTransform.getLibraryJniLibsInputDir().contains(dirInput.getFile())){
                    continue;
                }
            }

            builder.add(toIncrementalInput(dirInput, contentMap));
        }

        for (DirectoryInput dirInput : buildLocalDirInput()) {
            if (awbTransform == null) {
                if (!AtlasBuildContext.atlasMainDexHelperMap.get(name).getMainSoFiles().containsKey(dirInput.getFile().getAbsolutePath())) {
                    continue;
                }

            }else {
                if (!awbTransform.getLibraryJniLibsInputDir().contains(dirInput.getFile())){
                    continue;
                }
            }

            IncrementalFileMergerInput mergeInput =
                    toIncrementalInput(dirInput, contentMap);
            if (mergeInput != null) {
                builder.add(mergeInput);
            }

        }

        return builder.build();
    }

    /**
     * Creates a list of {@link IncrementalFileMergerInput} from a {@link TransformInput}. All files
     * in the input will be reported in the incremental input, including those inside zips. This
     * method assumes the input does not contain incremental information. All files will be reported
     * as new.
     *
     * @param transformInput the transform input
     * @param zipCache the zip cache; the cache will not be modified
     * @param cacheUpdates receives updates to the cache
     * @param contentMap if not {@code null}, receives a mapping from all generated inputs to
     * {@link QualifiedContent} they came from
     * @param awbBundle
     * @return the inputs
     */
    @NonNull
    public static ImmutableList<IncrementalFileMergerInput> toNonIncrementalInput(
            @NonNull TransformInput transformInput,
            @NonNull FileCacheByPath zipCache,
            @NonNull List<Runnable> cacheUpdates,
            @Nullable Map<IncrementalFileMergerInput, QualifiedContent> contentMap, AwbTransform awbTransform,String name) {
        ImmutableList.Builder<IncrementalFileMergerInput> builder = ImmutableList.builder();

        for (JarInput jarInput : transformInput.getJarInputs()) {
            if (awbTransform == null) {
                if (!AtlasBuildContext.atlasMainDexHelperMap.get(name).getMainResFiles().containsKey(jarInput.getFile().getAbsolutePath())) {
                    continue;
                }
            } else {
                if (!awbTransform.getLibraryResourcesInutDir().contains(jarInput.getFile())){
                    continue;
                }

            }

            IncrementalFileMergerInput mergeInput =
                    toNonIncrementalInput(jarInput, zipCache, cacheUpdates, contentMap);
            if (mergeInput != null) {
                builder.add(mergeInput);
            }
        }

        for (DirectoryInput dirInput : transformInput.getDirectoryInputs()) {
            if (awbTransform == null) {
                if (!AtlasBuildContext.atlasMainDexHelperMap.get(name).getMainSoFiles().containsKey(dirInput.getFile().getAbsolutePath())) {
                    continue;
                }

            }else {
                if (!awbTransform.getLibraryJniLibsInputDir().contains(dirInput.getFile())){
                    continue;
                }
            }

            IncrementalFileMergerInput mergeInput =
                    toNonIncrementalInput(dirInput, contentMap);
            if (mergeInput != null) {
                builder.add(mergeInput);
            }

        }



        return builder.build();
    }

    /**
     * Creates a list of {@link IncrementalFileMergerInput} from a {@link TransformInvocation}. All
     * files in the input will be reported in the incremental input, including those inside zips.
     *
     * @param transformInvocation the transform invocation
     * @param zipCache the zip cache; the cache will not be modified
     * @param cacheUpdates receives updates to the cache
     * @param full is this a full build? If not, then it is an incremental build; in full builds
     * the output is not cleaned, it is the responsibility of the caller to ensure the output
     * is properly set up; {@code full} cannot be {@code false} if the transform invocation is not
     * stating that the invocation is an incremental one
     * @param contentMap if not {@code null}, receives a mapping from all generated inputs to
     * {@link QualifiedContent} they came from
     */
    @NonNull
    public static ImmutableList<IncrementalFileMergerInput> toInput(
            @NonNull TransformInvocation transformInvocation,
            @NonNull FileCacheByPath zipCache,
            @NonNull List<Runnable> cacheUpdates,
            boolean full,
            @Nullable Map<IncrementalFileMergerInput, QualifiedContent> contentMap, AwbTransform awbBundle,String name) {
        if (!full) {
            Preconditions.checkArgument(transformInvocation.isIncremental());
        }

        if (full) {
            cacheUpdates.add(IOExceptionRunnable.asRunnable(zipCache::clear));
        }

        ImmutableList.Builder<IncrementalFileMergerInput> builder = ImmutableList.builder();
        for (TransformInput input : transformInvocation.getInputs()) {
            if (full) {
                builder.addAll(toNonIncrementalInput(input, zipCache, cacheUpdates, contentMap,awbBundle,name));
            } else {
                builder.addAll(toIncrementalInput(input, zipCache, cacheUpdates, contentMap,awbBundle,name));
            }
        }

        for (DirectoryInput dirInput : buildLocalDirInput()) {
            if (awbBundle == null) {
                if (!AtlasBuildContext.atlasMainDexHelperMap.get(name).getMainSoFiles().containsKey(dirInput.getFile().getAbsolutePath())) {
                    continue;
                }

            }else {
                if (!awbBundle.getLibraryJniLibsInputDir().contains(dirInput.getFile())){
                    continue;
                }
            }

            IncrementalFileMergerInput mergeInput =
                    toNonIncrementalInput(dirInput, contentMap);
            if (mergeInput != null) {
                builder.add(mergeInput);
            }

        }


        return builder.build();
    }

    /**
     * Maps a {@link Status} to a {@link FileStatus}.
     *
     * @param status the status
     * @return the {@link FileStatus} or {@code null} if {@code status} is {@link Status#NOTCHANGED}
     */
    @Nullable
    private static FileStatus mapStatus(@NonNull Status status) {
        switch (status) {
            case ADDED:
                return FileStatus.NEW;
            case CHANGED:
                return FileStatus.CHANGED;
            case NOTCHANGED:
                return null;
            case REMOVED:
                return FileStatus.REMOVED;
            default:
                throw new AssertionError();
        }
    }

    private static Collection<? extends DirectoryInput> buildLocalDirInput() {
        Set<DirectoryInput>directoryInputs = new HashSet<>();
        for (File file: AtlasBuildContext.localLibs){
            DirectoryInput directoryInput = new DirectoryInput() {
                @Override
                public Map<File, Status> getChangedFiles() {
                     return ImmutableMap.of(file, Status.NOTCHANGED);
                }

                @Override
                public String getName() {
                    return "localLibs";
                }

                @Override
                public File getFile() {
                    return file;
                }

                @Override
                public Set<ContentType> getContentTypes() {
                    return TransformManager.CONTENT_NATIVE_LIBS;
                }

                @Override
                public Set<? super Scope> getScopes() {
                    return TransformManager.SCOPE_FULL_PROJECT;
                }
            };

            directoryInputs.add(directoryInput);
        }
        return directoryInputs;


    }
}
