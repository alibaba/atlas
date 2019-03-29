package com.taobao.android.builder.tasks.transform.dex;

import android.databinding.tool.util.L;
import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.gradle.internal.BuildCacheUtils;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.internal.pipeline.OriginalStream;
import com.android.build.gradle.internal.transforms.DexArchiveBuilderTransform;
import com.android.build.gradle.internal.transforms.PreDexTransform;
import com.android.builder.core.DexOptions;
import com.android.builder.dexing.DexerTool;
import com.android.builder.utils.FileCache;
import com.android.dx.Version;
import com.android.tools.r8.AtlasD8;
import com.android.utils.FileUtils;
import com.google.common.base.*;
import com.google.common.collect.Multimap;
import com.google.common.hash.Hashing;
import com.google.common.io.ByteStreams;
import com.taobao.android.builder.tools.MD5Util;
import org.gradle.api.Project;

import java.nio.file.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

import static com.android.builder.dexing.ClassFileInput.CLASS_MATCHER;

/**
 * @author lilong
 * @create 2017-12-08 上午3:47
 */

public class AtlasDexArchiveBuilderCacheHander {
    private static final LoggerWrapper logger =
            LoggerWrapper.getLogger(AtlasDexArchiveBuilderCacheHander.class);

    // Increase this if we might have generated broken cache entries to invalidate them.
    private static final int CACHE_KEY_VERSION = 4;

    @Nullable
    private final FileCache userLevelCache;
    @NonNull
    private final DexOptions dexOptions;
    private final int minSdkVersion;
    private final boolean isDebuggable;
    @NonNull
    private final DexerTool dexer;

    private static Project project;

    public AtlasDexArchiveBuilderCacheHander(Project p,
            @Nullable FileCache userLevelCache,
            @NonNull DexOptions dexOptions,
            int minSdkVersion,
            boolean isDebuggable,
            @NonNull DexerTool dexer) {
        project = p;
        this.userLevelCache = userLevelCache;
        this.dexOptions = dexOptions;
        this.minSdkVersion = minSdkVersion;
        this.isDebuggable = isDebuggable;
        this.dexer = dexer;
    }

    @Nullable
    public File getCachedVersionIfPresent(JarInput input) throws Exception {
        FileCache cache =
                getBuildCache(
                        input.getFile(), isExternalLib(input), userLevelCache);

        if (cache == null) {
            return null;
        }

        FileCache.Inputs buildCacheInputs =
                getBuildCacheInputs(
                        input.getFile(), dexOptions, dexer, minSdkVersion, isDebuggable);
        return cache.cacheEntryExists(buildCacheInputs)
                ? cache.getFileInCache(buildCacheInputs)
                : null;
    }

    @Nullable
    public File getCachedVersionIfPresent(File input) throws Exception {
        assert input.isFile();
        FileCache cache =
                getBuildCache(
                        input, true, userLevelCache);

        if (cache == null) {
            return null;
        }

        FileCache.Inputs buildCacheInputs =
                getBuildCacheInputs(
                        input, dexOptions, dexer, minSdkVersion, isDebuggable);
        return cache.cacheEntryExists(buildCacheInputs)
                ? cache.getFileInCache(buildCacheInputs)
                : null;
    }

    /**
     * Returns if the qualified content is an external jar.
     */
    private static boolean isExternalLib(@NonNull QualifiedContent content) {
        return content.getFile().isFile()
                && content.getScopes()
                .equals(Collections.singleton(QualifiedContent.Scope.EXTERNAL_LIBRARIES))
                && content.getContentTypes()
                .equals(Collections.singleton(QualifiedContent.DefaultContentType.CLASSES))
                && !content.getName().startsWith(OriginalStream.LOCAL_JAR_GROUPID);
    }

    /**
     * Input parameters to be provided by the client when using {@link FileCache}.
     * <p>
     * <p>The clients of {@link FileCache} need to exhaustively specify all the inputs that affect
     * the creation of an output file/directory. This enum class lists the input parameters that are
     * used in {@link DexArchiveBuilderCacheHandler}.
     */
    private enum FileCacheInputParams {

        /**
         * The input file.
         */
        FILE,

        /**
         * Dx version used to create the dex archive.
         */
        DX_VERSION,

        /**
         * Whether jumbo mode is enabled.
         */
        JUMBO_MODE,

        /**
         * Whether optimize is enabled.
         */
        OPTIMIZE,

        /**
         * Tool used to produce the dex archive.
         */
        DEXER_TOOL,

        /**
         * Version of the cache key.
         */
        CACHE_KEY_VERSION,

        /**
         * Min sdk version used to generate dex.
         */
        MIN_SDK_VERSION,

        /**
         * If generate dex is debuggable.
         */
        IS_DEBUGGABLE,
    }

    /**
     * Returns a {@link FileCache.Inputs} object computed from the given parameters for the
     * predex-library task to use the build cache.
     */
    @NonNull
    public static FileCache.Inputs getBuildCacheInputs(
            @NonNull File inputFile,
            @NonNull DexOptions dexOptions,
            @NonNull DexerTool dexerTool,
            int minSdkVersion,
            boolean isDebuggable)
            throws Exception {
        // To use the cache, we need to specify all the inputs that affect the outcome of a pre-dex
        // (see DxDexKey for an exhaustive list of these inputs)
        FileCache.Inputs.Builder buildCacheInputs =
                new FileCache.Inputs.Builder(FileCache.Command.PREDEX_LIBRARY_TO_DEX_ARCHIVE);
        java.util.function.Predicate<String> CLASS_MATCHER = s -> s.endsWith(SdkConstants.DOT_CLASS);

        if (inputFile.isDirectory()){
            String hash = Files.walk(inputFile.toPath())
                    .filter(p -> CLASS_MATCHER.test(p.toString())).sorted().map(new Function<Path, String>() {
                        @javax.annotation.Nullable
                        @Override
                        public String apply(@javax.annotation.Nullable Path input) {
                            try {
                                return com.google.common.io.Files.asByteSource(input.toFile()).hash(Hashing.sha256()).toString();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            return "";
                        }
                    }).collect(StringBuilder::new, StringBuilder::append, StringBuilder::append).toString();



            buildCacheInputs
                    .putString("hash",MD5Util.getMD5(hash))
                    .putString(FileCacheInputParams.DX_VERSION.name(), Version.VERSION)
                    .putBoolean(FileCacheInputParams.JUMBO_MODE.name(), isJumboModeEnabledForDx())
                    .putBoolean(
                            FileCacheInputParams.OPTIMIZE.name(),
                            !dexOptions.getAdditionalParameters().contains("--no-optimize"))
                    .putString(FileCacheInputParams.DEXER_TOOL.name(), dexerTool.name())
                    .putLong(FileCacheInputParams.CACHE_KEY_VERSION.name(), CACHE_KEY_VERSION)
                    .putLong(FileCacheInputParams.MIN_SDK_VERSION.name(), minSdkVersion)
                    .putBoolean(FileCacheInputParams.IS_DEBUGGABLE.name(), isDebuggable);



        }else {
            buildCacheInputs
                    .putFile(
                            FileCacheInputParams.FILE.name(),
                            inputFile,
                            FileCache.FileProperties.HASH)
                    .putString(FileCacheInputParams.DX_VERSION.name(), Version.VERSION)
                    .putBoolean(FileCacheInputParams.JUMBO_MODE.name(), isJumboModeEnabledForDx())
                    .putBoolean(
                            FileCacheInputParams.OPTIMIZE.name(),
                            !dexOptions.getAdditionalParameters().contains("--no-optimize"))
                    .putString(FileCacheInputParams.DEXER_TOOL.name(), dexerTool.name())
                    .putLong(FileCacheInputParams.CACHE_KEY_VERSION.name(), CACHE_KEY_VERSION)
                    .putLong(FileCacheInputParams.MIN_SDK_VERSION.name(), minSdkVersion)
                    .putBoolean(FileCacheInputParams.IS_DEBUGGABLE.name(), isDebuggable);
        }
       if (project.hasProperty("light") && project.hasProperty("deepShrink")){
            buildCacheInputs.putBoolean("deepShrink",AtlasD8.deepShrink);
        }
        return buildCacheInputs.build();
    }

    /**
     * Jumbo mode is always enabled for dex archives - see http://b.android.com/321744
     */
    static boolean isJumboModeEnabledForDx() {
        return true;
    }


    static FileCache getBuildCache(
            @NonNull File inputFile, boolean isExternalLib, @Nullable FileCache buildCache) {
        // We use the build cache only when it is enabled and the input file is a (non-snapshot)
        // external-library jar file
        if (buildCache == null || !isExternalLib) {
            return null;
        }
        // After the check above, here the build cache should be enabled and the input file is an
        // external-library jar file. We now check whether it is a snapshot version or not (to
        // address http://b.android.com/228623).
        // Note that the current check is based on the file path; if later on there is a more
        // reliable way to verify whether an input file is a snapshot, we should replace this check
        // with that.
        if (inputFile.getPath().contains("-SNAPSHOT")) {
            return null;
        } else {
            return buildCache;
        }
    }

    void populateCache(Multimap<QualifiedContent, File> cacheableItems)
            throws Exception {

        for (QualifiedContent input : cacheableItems.keys()) {
            FileCache cache =
                    getBuildCache(
                            input.getFile(), isExternalLib(input), userLevelCache);
            if (cache != null) {
                FileCache.Inputs buildCacheInputs =
                        AtlasDexArchiveBuilderCacheHander.getBuildCacheInputs(
                                input.getFile(), dexOptions, dexer, minSdkVersion, isDebuggable);
                FileCache.QueryResult result =
                        cache.createFileInCacheIfAbsent(
                                buildCacheInputs,
                                in -> {
                                    Collection<File> dexArchives = cacheableItems.get(input);
                                    logger.verbose(
                                            "Merging %1$s into %2$s",
                                            Joiner.on(',').join(dexArchives), in.getAbsolutePath());
                                    mergeJars(in, cacheableItems.get(input));
                                });
                if (result.getQueryEvent().equals(FileCache.QueryEvent.CORRUPTED)) {
                    Verify.verifyNotNull(result.getCauseOfCorruption());
                    logger.info(
                            "The build cache at '%1$s' contained an invalid cache entry.\n"
                                    + "Cause: %2$s\n"
                                    + "We have recreated the cache entry.\n"
                                    + "%3$s",
                            cache.getCacheDirectory().getAbsolutePath(),
                            Throwables.getStackTraceAsString(result.getCauseOfCorruption()),
                            BuildCacheUtils.BUILD_CACHE_TROUBLESHOOTING_MESSAGE);
                }
            }
        }
    }

    public void populateCache(File input,File out)
            throws Exception {

            FileCache cache =
                    getBuildCache(
                            input, true, userLevelCache);
            if (cache != null) {
                FileCache.Inputs buildCacheInputs =
                        AtlasDexArchiveBuilderCacheHander.getBuildCacheInputs(
                                input, dexOptions, dexer, minSdkVersion, isDebuggable);
                FileCache.QueryResult result =
                        cache.createFileInCacheIfAbsent(
                                buildCacheInputs,
                                in -> {
                                    Files.copy(out.toPath(),in.toPath());
                                });
                if (result.getQueryEvent().equals(FileCache.QueryEvent.CORRUPTED)) {
                    Verify.verifyNotNull(result.getCauseOfCorruption());
                    logger.info(
                            "The build cache at '%1$s' contained an invalid cache entry.\n"
                                    + "Cause: %2$s\n"
                                    + "We have recreated the cache entry.\n"
                                    + "%3$s",
                            cache.getCacheDirectory().getAbsolutePath(),
                            Throwables.getStackTraceAsString(result.getCauseOfCorruption()),
                            BuildCacheUtils.BUILD_CACHE_TROUBLESHOOTING_MESSAGE);
                }else if (result.getQueryEvent().equals(FileCache.QueryEvent.MISSED)){
                    logger.warning("miss D8 cache:"+input.getAbsolutePath());
                }else if (result.getQueryEvent().equals(FileCache.QueryEvent.HIT)){
                    logger.warning("hit D8 cache:"+input.getAbsolutePath());

                }
            }
    }


    private static void mergeJars(File out, Iterable<File> dexArchives) throws IOException {

        try (JarOutputStream jarOutputStream =
                     new JarOutputStream(new BufferedOutputStream(new FileOutputStream(out)))) {

            Set<String> usedNames = new HashSet<>();
            for (File dexArchive : dexArchives) {
                if (dexArchive.exists()) {
                    try (JarFile jarFile = new JarFile(dexArchive)) {
                        Enumeration<JarEntry> entries = jarFile.entries();
                        while (entries.hasMoreElements()) {
                            JarEntry jarEntry = entries.nextElement();
                            // Get unique name as jars might have multiple classes.dex, as for D8
                            // we do not want to output single dex per class for performance reasons
                            String entryName = jarEntry.getName();
                            while (!usedNames.add(entryName)) {
                                entryName = "_" + entryName;
                            }
                            jarOutputStream.putNextEntry(new JarEntry(entryName));
                            try (InputStream inputStream =
                                         new BufferedInputStream(jarFile.getInputStream(jarEntry))) {
                                ByteStreams.copy(inputStream, jarOutputStream);
                            }
                            jarOutputStream.closeEntry();
                        }
                    }
                }
            }
        }
    }

}
