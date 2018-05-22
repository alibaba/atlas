package com.android.tools.r8;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.dexing.*;
import com.android.tools.r8.errors.CompilationError;
import com.google.common.collect.Iterables;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;

/**
 * AtlasD8Merger
 *
 * @author zhayu.ll
 * @date 18/4/3
 */
public class AtlasD8Merger implements DexArchiveMerger {
    @NonNull
    private final OutputStream errorStream;
    private final int minSdkVersion;
    @NonNull private final CompilationMode compilationMode;

    public AtlasD8Merger(
            @NonNull OutputStream errorStream,
            int minSdkVersion,
            @NonNull CompilationMode compilationMode) {
        this.errorStream = errorStream;
        this.minSdkVersion = minSdkVersion;
        this.compilationMode = compilationMode;
    }

    @Override
    public void mergeDexArchives(
            @NonNull Iterable<Path> inputs,
            @NonNull Path outputDir,
            @Nullable Path mainDexClasses,
            @NonNull DexingType dexingType)
            throws DexArchiveMergerException {
        if (Iterables.isEmpty(inputs)) {
            return;
        }

        D8Command.Builder builder = D8Command.builder();

        for (Path input : inputs) {
            try (DexArchive archive = DexArchives.fromInput(input)) {
                for (DexArchiveEntry dexArchiveEntry : archive.getFiles()) {
                    builder.addDexProgramData(dexArchiveEntry.getDexFileContent());
                }
            } catch (IOException e) {
                throw new DexArchiveMergerException(e);
            }
        }
        try {
            if (mainDexClasses != null) {
                builder.addMainDexListFiles(mainDexClasses);
            }
            builder.setMinApiLevel(minSdkVersion).setMode(compilationMode).setOutputPath(outputDir);
            AtlasD8.run(builder.build());
        } catch (CompilationException | IOException | CompilationError e) {
            DexArchiveMergerException exception = new DexArchiveMergerException(e);
            try {
                errorStream.write(e.getMessage().getBytes());
            } catch (IOException ex) {
                System.err.println(e.getMessage());
                exception.addSuppressed(ex);
            }
            throw exception;
        }
    }
}
