package com.taobao.android.builder.hook.dex;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.builder.dexing.*;
import com.android.dex.Dex;
import com.android.dx.command.dexer.DxContext;
import com.android.utils.ILogger;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;

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

    private ILogger logger = LoggerWrapper.getLogger(AtlasDexArchiveMerger.class);

    private DexMergingStrategy mergingStrategy = new AtlasDexMergingStrategy();


    public AtlasDexArchiveMerger(ForkJoinPool forkJoinPool) {
        this.forkJoinPool = forkJoinPool;
    }

    @Override
    public void mergeDexArchives(Iterable<Path> inputs, Path outputDir, Path mainDexClasses, DexingType dexingType) throws DexArchiveMergerException {

        List<Path> inputPaths = Ordering.natural().sortedCopy(inputs);
//        Set<String> mainClasses = null;
//        try {
//            mainClasses = Sets.newHashSet(readAllLines(mainDexClasses));
//        } catch (IOException e) {
//            e.printStackTrace();
//        }


        Iterator<DexMergeEntry> entries =
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

        int classesDexSuffix = 0;


        List<ForkJoinTask<Void>> subTasks = new ArrayList<>();
        List<String> toMergeInMain = Lists.newArrayList();
        mergingStrategy.startNewDex();

        while (entries.hasNext()) {
            DexMergeEntry entry = entries.next();
            Dex dex = null;
            try {
                dex = new Dex(entry.dexFileContent);
            } catch (IOException e) {
                e.printStackTrace();
            }

            if (dexingType == DexingType.LEGACY_MULTIDEX) {
                if (entry.name.startsWith("fastmaindex")) {
                    logger.info("add fastmultidex.jar to first dex");
                    toMergeInMain.add(entry.name);
                    mergingStrategy.tryToAddForMerging(dex);
                    break;
                }
            }
        }
        try {
            entries = getAllEntriesFromArchives(inputPaths).iterator();
        } catch (IOException e) {
            e.printStackTrace();
        }
        while (entries.hasNext()) {
            DexMergeEntry entry = entries.next();
            Dex dex = null;
            try {
                dex = new Dex(entry.dexFileContent);
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (toMergeInMain.contains(entry.name)){
                continue;
            }
            if (!mergingStrategy.tryToAddForMerging(dex)) {
                Path dexOutput = new File(outputDir.toFile(), getDexFileName(classesDexSuffix++)).toPath();
                if (mergingStrategy.getAllDexToMerge().size() > 0) {
                    subTasks.add(submitForMerging(mergingStrategy.getAllDexToMerge(), dexOutput));
                }
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
            Path dexOutput = new File(outputDir.toFile(), getDexFileName(classesDexSuffix)).toPath();
            if (mergingStrategy.getAllDexToMerge().size() > 0) {
                subTasks.add(submitForMerging(mergingStrategy.getAllDexToMerge(), dexOutput));
            }
        }

        // now wait for all subtasks completion.
        subTasks.forEach(ForkJoinTask::join);

    }


    @NonNull
    static List<DexMergeEntry> getAllEntriesFromArchives(@NonNull Collection<Path> inputs)
            throws IOException {
        List<DexMergeEntry> entries = Lists.newArrayList();
        for (Path p : inputs) {
            entries.add(new DexMergeEntry(Files.readAllBytes(p),p.toFile().getParentFile().getName()));
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


    static class DexMergeEntry{

         public byte[] dexFileContent;

         public String name;

        public DexMergeEntry(byte[] dexFileContent, String name) {
            this.dexFileContent = dexFileContent;
            this.name = name;
        }
    }
}
