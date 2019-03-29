package com.taobao.android.builder.hook.dex;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.builder.dexing.*;
import com.android.dex.Dex;
import com.android.dx.command.dexer.DxContext;
import com.google.common.base.Preconditions;
import com.google.common.collect.*;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

/**
 * 创建日期：2018/12/21 on 下午1:11
 * 描述:
 * 作者:zhayu.ll
 */
public class DexArchiveMergerHook implements DexArchiveMerger {

    @NonNull private final DxContext dxContext;

    @NonNull private final DexMergingStrategy mergingStrategy;

    @NonNull private final ForkJoinPool forkJoinPool;

    static final PathMatcher jarMatcher =
            FileSystems.getDefault().getPathMatcher("glob:**" + SdkConstants.DOT_JAR);


    public DexArchiveMergerHook(
            @NonNull DxContext dxContext,
            @NonNull DexMergingStrategy mergingStrategy,
            @NonNull ForkJoinPool forkJoinPool) {
        this.dxContext = dxContext;
        this.mergingStrategy = mergingStrategy;
        this.forkJoinPool = forkJoinPool;
    }

    @Override
    public void mergeDexArchives(Iterable<Path> inputs, Path outputDir, Path mainDexClasses, DexingType dexingType) throws DexArchiveMergerException {

        if (Iterables.isEmpty(inputs)) {
            return;
        }
        // sort paths so we produce deterministic output
        List<Path> inputPaths = Ordering.natural().sortedCopy(inputs);

        try {
            switch (dexingType) {
                case MONO_DEX:
                    Preconditions.checkState(
                            mainDexClasses == null, "Main dex list cannot be set for monodex.");
                    mergeMonoDex(inputPaths, outputDir);
                    break;
                case LEGACY_MULTIDEX:
                    Preconditions.checkNotNull(
                            mainDexClasses, "Main dex list must be set for legacy multidex.");
                    mergeMultidex(
                            inputPaths,
                            outputDir,
                            Sets.newHashSet(Files.readAllLines(mainDexClasses)),
                            dexingType);
                    break;
                case NATIVE_MULTIDEX:
                    Preconditions.checkState(
                            mainDexClasses == null,
                            "Main dex list cannot be set for native multidex.");
                    mergeMultidex(inputPaths, outputDir, Collections.emptySet(), dexingType);
                    break;
                default:
                    throw new IllegalStateException("Unknown dexing mode" + dexingType);
            }
        } catch (IOException e) {
            throw new DexArchiveMergerException(e);
        }
    }


    private void mergeMonoDex(@NonNull Collection<Path> inputs, @NonNull Path output)
            throws IOException {
        Map<Path, List<Dex>> dexesFromArchives = Maps.newConcurrentMap();
        // counts how many inputs are yet to be processed
        AtomicInteger inputsToProcess = new AtomicInteger(inputs.size());
        ArrayList<ForkJoinTask<Void>> subTasks = new ArrayList<>();
        for (Path archivePath : inputs) {
            subTasks.add(
                    forkJoinPool.submit(
                            () -> {
                                try (DexArchive dexArchive = DexArchives.fromInput(archivePath)) {
                                    List<DexArchiveEntry> entries = dexArchive.getFiles();
                                    List<Dex> dexes = new ArrayList<>(entries.size());
                                    for (DexArchiveEntry e : entries) {
                                        dexes.add(new Dex(e.getDexFileContent()));
                                    }

                                    dexesFromArchives.put(dexArchive.getRootPath(), dexes);
                                }

                                if (inputsToProcess.decrementAndGet() == 0) {
                                    mergeMonoDexEntries(output, dexesFromArchives).join();
                                }
                                return null;
                            }));
        }
        // now wait for all subtasks execution.
        subTasks.forEach(ForkJoinTask::join);
    }

    private ForkJoinTask<Void> mergeMonoDexEntries(
            @NonNull Path output, @NonNull Map<Path, List<Dex>> dexesFromArchives) {
        List<Path> sortedPaths = Ordering.natural().sortedCopy(dexesFromArchives.keySet());
        int numberOfDexFiles = dexesFromArchives.values().stream().mapToInt(List::size).sum();
        List<Dex> sortedDexes = new ArrayList<>(numberOfDexFiles);
        for (Path p : sortedPaths) {
            sortedDexes.addAll(dexesFromArchives.get(p));
        }
        // trigger merging with sorted set
        return submitForMerging(sortedDexes, output.resolve(getDexFileName(0)));
    }

    /**
     * Merges all DEX files from the dex archives into DEX file(s). It does so by using {@link
     * DexMergingStrategy} which specifies when a DEX file should be started.
     *
     * <p>For {@link DexingType#LEGACY_MULTIDEX} mode, only classes specified in the main dex
     * classes list will be packaged in the classes.dex, thus creating a minimal main DEX. Remaining
     * DEX classes will be placed in other DEX files.
     *
     * @throws IOException if dex archive cannot be read, or merged DEX file(s) cannot be written
     */
    private void mergeMultidex(
            @NonNull Collection<Path> inputs,
            @NonNull Path output,
            @NonNull Set<String> mainDexClasses,
            @NonNull DexingType dexingType)
            throws IOException, DexArchiveMergerException {
        Iterator<DexArchiveEntry> entries =
                getAllEntriesFromArchives(inputs).iterator();
        if (!entries.hasNext()) {
            // nothing to do
            return;
        }

        int classesDexSuffix;
        if (dexingType == DexingType.LEGACY_MULTIDEX) {
            // if we are in native multidex, we should leave classes.dex for the main dex
            classesDexSuffix = 1;
        } else {
            classesDexSuffix = 0;
        }

        List<ForkJoinTask<Void>> subTasks = new ArrayList<>();
        List<Dex> toMergeInMain = Lists.newArrayList();
        mergingStrategy.startNewDex();

        boolean generate = false;

        while (entries.hasNext()) {
            DexArchiveEntry entry = entries.next();
            Dex dex = new Dex(entry.getDexFileContent());
            if (dexingType == DexingType.LEGACY_MULTIDEX &&!generate) {
                // check if this should go to the main dex
                String relativeUnixPath =
                        DexArchiveEntry.withClassExtension(entry.getRelativePathInArchive());
                if (mainDexClasses.contains(relativeUnixPath)) {
                    if (!mergingStrategy.tryToAddForMerging(dex)){
                        if (dexingType == DexingType.LEGACY_MULTIDEX) {
                            // write the main dex file
                            subTasks.add(submitForMerging(toMergeInMain, output.resolve(getDexFileName(0))));
                            generate = true;
                            mergingStrategy.startNewDex();
                        }

                    }else {
                        toMergeInMain.add(dex);
                        continue;
                    }
                }
            }

            if (!mergingStrategy.tryToAddForMerging(dex)) {
                Path dexOutput = output.resolve(getDexFileName(classesDexSuffix++));
                subTasks.add(submitForMerging(mergingStrategy.getAllDexToMerge(), dexOutput));
                mergingStrategy.startNewDex();

                // adding now should succeed
                if (!mergingStrategy.tryToAddForMerging(dex)) {
                    throw new DexArchiveMergerException(
                            "A single DEX file from a dex archive has more than 64K references.");
                }
            }
        }



        // if there are some remaining unprocessed dex files, merge them
        if (!mergingStrategy.getAllDexToMerge().isEmpty()) {
            Path dexOutput = output.resolve(getDexFileName(classesDexSuffix));
            subTasks.add(submitForMerging(mergingStrategy.getAllDexToMerge(), dexOutput));
        }

        // now wait for all subtasks completion.
        subTasks.forEach(ForkJoinTask::join);
    }

    private ForkJoinTask<Void> submitForMerging(
            @NonNull List<Dex> dexes, @NonNull Path dexOutputPath) {
        return forkJoinPool.submit(new DexArchiveMergerCallable(dexes, dexOutputPath, dxContext));
    }

    @NonNull
    private String getDexFileName(int classesDexIndex) {
        if (classesDexIndex == 0) {
            return SdkConstants.FN_APK_CLASSES_DEX;
        } else {
            return String.format(SdkConstants.FN_APK_CLASSES_N_DEX, (classesDexIndex + 1));
        }
    }

    public static final Predicate<Path> DEX_ENTRY_FILTER =
            f -> f.toString().endsWith(SdkConstants.DOT_DEX);


    /**
     * Creates a {@link com.android.builder.dexing.DexArchive} from the specified path. It supports
     * .jar files and directories as inputs.
     *
     * <p>In case of a .jar file, note there are two mutually exclusive modes, write-only and
     * read-only. In case of a write-only mode, only allowed operation is adding entries. If
     * read-only mode is used, entires can only be read.
     */
    @NonNull
    public static DexArchive fromInput(@NonNull Path path) throws IOException {
        if (jarMatcher.matches(path)) {
            return new NonIncrementalJarDexArchiveHook(path);
        } else {
            return new DirDexArchiveHook(path);
        }
    }

    @NonNull
    static List<DexArchiveEntry> getEntriesFromSingleArchive(@NonNull Path archivePath)
            throws IOException {
        try (DexArchive archive = fromInput(archivePath)) {
            return archive.getFiles();
        }
    }

    @NonNull
    static List<DexArchiveEntry> getAllEntriesFromArchives(@NonNull Collection<Path> inputs)
            throws IOException {
        List<DexArchiveEntry> entries = Lists.newArrayList();
        for (Path p : inputs) {
            entries.addAll(getEntriesFromSingleArchive(p));
        }
        return entries;
    }
}
