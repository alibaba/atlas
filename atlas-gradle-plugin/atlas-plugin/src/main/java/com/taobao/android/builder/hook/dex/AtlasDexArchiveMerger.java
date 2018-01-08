package com.taobao.android.builder.hook.dex;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.builder.dexing.*;
import com.android.dex.Dex;
import com.android.dex.DexFormat;
import com.android.dx.command.dexer.DxContext;
import com.android.ide.common.internal.WaitableExecutor;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;
import com.google.common.collect.Sets;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;

import static java.nio.file.Files.readAllLines;

/**
 * AtlasDexArchiveMerger
 *
 * @author zhayu.ll
 * @date 18/1/5
 * @time 下午3:18
 * @description  
 */
public class AtlasDexArchiveMerger implements DexArchiveMerger {

    private ForkJoinPool forkJoinPool = null;

    private DexMergingStrategy mergingStrategy = new AtlasDexMergingStrategy();


    public AtlasDexArchiveMerger(ForkJoinPool forkJoinPool) {
        this.forkJoinPool = forkJoinPool;
    }

    @Override
    public void mergeDexArchives(Iterable<Path> inputs, Path outputDir, Path mainDexClasses, DexingType dexingType) throws DexArchiveMergerException {

        List<Path> inputPaths = Ordering.natural().sortedCopy(inputs);
        Set<String> mainClasses = null;
        try {
            mainClasses = Sets.newHashSet(readAllLines(mainDexClasses));
        } catch (IOException e) {
            e.printStackTrace();
        }


        Iterator<DexArchiveEntry> entries =
                null;
        try {
            entries = getAllEntriesFromArchives(inputPaths).iterator();
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (!entries.hasNext()) {
            // nothing to do
            return;
        }

        int classesDexSuffix;
        if (dexingType == DexingType.LEGACY_MULTIDEX) {
            classesDexSuffix = 1;
        } else {
            classesDexSuffix = 0;
        }

        List<ForkJoinTask<Void>> subTasks = new ArrayList<>();
        List<Dex> toMergeInMain = Lists.newArrayList();
        mergingStrategy.startNewDex();

        while (entries.hasNext()) {
            DexArchiveEntry entry = entries.next();
            Dex dex = null;
            try {
                dex = new Dex(entry.getDexFileContent());
            } catch (IOException e) {
                e.printStackTrace();
            }

            if (dexingType == DexingType.LEGACY_MULTIDEX) {
                // check if this should go to the main dex
                String relativeUnixPath =
                        DexArchiveEntry.withClassExtension(entry.getRelativePathInArchive());
                if (mainClasses.contains(relativeUnixPath)) {
                    toMergeInMain.add(dex);
                    continue;
                }
            }

            if (!mergingStrategy.tryToAddForMerging(dex)) {
                Path dexOutput = new File(outputDir.toFile(), getDexFileName(classesDexSuffix++)).toPath();
                subTasks.add(submitForMerging(mergingStrategy.getAllDexToMerge(), dexOutput));
                mergingStrategy.startNewDex();

                // adding now should succeed
                if (!mergingStrategy.tryToAddForMerging(dex)) {
                    throw new DexArchiveMergerException(
                            "A single DEX file from a dex archive has more than 64K references.");
                }
            }
        }

        if (dexingType == DexingType.LEGACY_MULTIDEX) {
            // write the main dex file
            subTasks.add(submitForMerging(toMergeInMain, new File(outputDir.toFile(), getDexFileName(0)).toPath()));
        }

        // if there are some remaining unprocessed dex files, merge them
        if (!mergingStrategy.getAllDexToMerge().isEmpty()) {
            Path dexOutput = new File(outputDir.toFile(), getDexFileName(classesDexSuffix)).toPath();
            subTasks.add(submitForMerging(mergingStrategy.getAllDexToMerge(), dexOutput));
        }

        // now wait for all subtasks completion.
        subTasks.forEach(ForkJoinTask::join);

    }

    @NonNull
    static List<DexArchiveEntry> getEntriesFromSingleArchive(@NonNull Path archivePath)
            throws IOException {
        try (DexArchive archive = DexArchives.fromInput(archivePath)) {
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


    @NonNull
    private String getDexFileName(int classesDexIndex) {
        if (classesDexIndex == 0) {
            return SdkConstants.FN_APK_CLASSES_DEX;
        } else {
            return String.format(SdkConstants.FN_APK_CLASSES_N_DEX, (classesDexIndex + 1));
        }
    }

    private ForkJoinTask<Void> submitForMerging(
            @NonNull List<Dex> dexes, @NonNull Path dexOutputPath) {
        return forkJoinPool.submit(new DexArchiveMergerCallable(dexes, dexOutputPath, new DxContext()));
    }

    public static class AtlasDexMergingStrategy implements DexMergingStrategy {

        @VisibleForTesting
        static final int MAX_NUMBER_OF_IDS_IN_DEX = 64500;

        @NonNull
        private final List<Dex> currentDexesToMerge = Lists.newArrayList();
        private int currentMethodIdsUsed = 0;
        private int currentFieldIdsUsed = 0;

        @Override
        public boolean tryToAddForMerging(@NonNull Dex dexFile) {
            int dexMethodIds = dexFile.getTableOfContents().methodIds.size;
            int dexFieldIds = dexFile.getTableOfContents().fieldIds.size;

            if (dexMethodIds + currentMethodIdsUsed > MAX_NUMBER_OF_IDS_IN_DEX) {
                return false;
            }

            if (dexFieldIds + currentFieldIdsUsed > MAX_NUMBER_OF_IDS_IN_DEX) {
                return false;
            }

            currentMethodIdsUsed += dexMethodIds;
            currentFieldIdsUsed += dexFieldIds;

            currentDexesToMerge.add(dexFile);
            return true;
        }

        @Override
        public void startNewDex() {
            currentMethodIdsUsed = 0;
            currentFieldIdsUsed = 0;
            currentDexesToMerge.clear();
        }

        @NonNull
        @Override
        public ImmutableList<Dex> getAllDexToMerge() {
            return ImmutableList.copyOf(currentDexesToMerge);
        }
    }
}
