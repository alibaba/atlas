package com.taobao.android.builder.hook.dex;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.builder.dexing.*;
import com.android.dex.*;
import com.android.dx.command.dexer.DxContext;
import com.android.utils.ILogger;
import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;
import com.google.common.collect.Sets;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
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

        for (Path path:inputPaths){
            logger.warning("input dexmerge path:"+path.toString());
        }
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
            if (toMergeInMain.contains(entry.name)) {
                continue;
            }
            try {
                dex = new Dex(entry.dexFileContent);
            } catch (IOException e) {
                e.printStackTrace();
            }

            if (!mergingStrategy.tryToAddForMerging(dex)) {
                Path dexOutput = new File(outputDir.toFile(), getDexFileName(classesDexSuffix++)).toPath();
                logger.warning("dexOutput 0 :"+dexOutput.toString());

                if (mergingStrategy.getAllDexToMerge().size() > 0) {
                    subTasks.add(submitForMerging(mergingStrategy.getAllDexToMerge(), dexOutput));
                }else {
                    classesDexSuffix --;
                }
                mergingStrategy.startNewDex();

                // adding now should succeed
                if (!mergingStrategy.tryToAddForMerging(dex)) {
                    if (mergingStrategy instanceof AtlasDexMergingStrategy){
                        ((AtlasDexMergingStrategy) mergingStrategy).forceAdd(dex);
                         dexOutput = new File(outputDir.toFile(), getDexFileName(classesDexSuffix++)).toPath();
                        logger.warning("dexOutput 1:"+dexOutput.toString());
                        subTasks.add(submitForMerging(mergingStrategy.getAllDexToMerge(), dexOutput));
                        mergingStrategy.startNewDex();

                    }else {
                        throw new DexArchiveMergerException(
                                "A single DEX file from a dex archive has more than 64K references.");
                    }
                }
            }
        }


        // if there are some remaining unprocessed dex files, merge them
        if (!mergingStrategy.getAllDexToMerge().isEmpty()) {
            Path dexOutput = new File(outputDir.toFile(), getDexFileName(classesDexSuffix)).toPath();
            logger.warning("dexOutput 2:"+dexOutput.toString());

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
            entries.add(new DexMergeEntry(Files.readAllBytes(p), p.toFile().getParentFile().getName()));
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
        static final int MAX_NUMBER_OF_IDS_IN_DEX = 64000;

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

        public void forceAdd(Dex dex){
            currentDexesToMerge.add(dex);
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


    public static class AtlasDexRefMergingStrategy implements DexMergingStrategy{

        static final int MAX_NUMBER_OF_IDS_IN_DEX = 64000;

        @NonNull private final Set<AtlasDexRefMergingStrategy.FieldEvaluated> fieldRefs = Sets.newHashSet();

        @NonNull private final Set<AtlasDexRefMergingStrategy.MethodEvaluated> methodRefs = Sets.newHashSet();
        @NonNull private final List<Dex> currentDexes = Lists.newArrayList();

        @Override
        public boolean tryToAddForMerging(@NonNull Dex dexFile) {
            if (tryAddFields(dexFile) && tryAddMethods(dexFile)) {
                currentDexes.add(dexFile);
                return true;
            } else {
                return false;
            }
        }

        @Override
        public void startNewDex() {
            fieldRefs.clear();
            methodRefs.clear();
            currentDexes.clear();
        }

        @NonNull
        @Override
        public ImmutableList<Dex> getAllDexToMerge() {
            return ImmutableList.copyOf(currentDexes);
        }

        private boolean tryAddFields(@NonNull Dex dexFile) {
            List<FieldId> fieldIds = dexFile.fieldIds();
            Set<AtlasDexRefMergingStrategy.FieldEvaluated> fieldsEvaluated = new HashSet<>(fieldIds.size());

            fieldIds.forEach(f -> fieldsEvaluated.add(AtlasDexRefMergingStrategy.FieldEvaluated.create(f, dexFile)));

            // find how many references are shared, and deduct from the total count
            int shared = Sets.intersection(fieldsEvaluated, fieldRefs).size();

            if (fieldRefs.size() + fieldsEvaluated.size() - shared >MAX_NUMBER_OF_IDS_IN_DEX ) {
                return false;
            } else {
                fieldRefs.addAll(fieldsEvaluated);
                return true;
            }
        }

        private boolean tryAddMethods(@NonNull Dex dexFile) {
            List<MethodId> methodIds = dexFile.methodIds();
            Set<AtlasDexRefMergingStrategy.MethodEvaluated> methodsEvaluated = new HashSet<>(methodIds.size());
            methodIds.forEach(f -> methodsEvaluated.add(AtlasDexRefMergingStrategy.MethodEvaluated.create(f, dexFile)));

            // find how many references are shared, and deduct from the total count
            int shared = Sets.intersection(methodsEvaluated, methodRefs).size();
            if (methodRefs.size() + methodsEvaluated.size() - shared > MAX_NUMBER_OF_IDS_IN_DEX) {
                return false;
            } else {
                methodRefs.addAll(methodsEvaluated);
                return true;
            }
        }

        @AutoValue
        public abstract static class FieldEvaluated {

            @NonNull
            public static AtlasDexRefMergingStrategy.FieldEvaluated create(@NonNull FieldId fieldId, @NonNull Dex dex) {

                    return new AutoValue_AtlasDexArchiveMerger_AtlasDexRefMergingStrategy_FieldEvaluated(
                            dex.typeNames().get(fieldId.getDeclaringClassIndex()),
                            dex.typeNames().get(fieldId.getTypeIndex()),
                            dex.strings().get(fieldId.getNameIndex()));

            }

            @NonNull
            public abstract String declaringClass();

            @NonNull
            public abstract String type();

            @NonNull
            public abstract String name();
        }

        @AutoValue
        public abstract static class MethodEvaluated {

            @NonNull
            public static AtlasDexRefMergingStrategy.MethodEvaluated create(@NonNull MethodId methodId, @NonNull Dex dex) {
                String declaringClass = dex.typeNames().get(methodId.getDeclaringClassIndex());
                String name = dex.strings().get(methodId.getNameIndex());

                ProtoId protoId = dex.protoIds().get(methodId.getProtoIndex());
                String protoShorty = dex.strings().get(protoId.getShortyIndex());
                String protoReturnType = dex.typeNames().get(protoId.getReturnTypeIndex());
                String protoParameterTypes = dex.readTypeList(protoId.getParametersOffset()).toString();
                return new AutoValue_AtlasDexArchiveMerger_AtlasDexRefMergingStrategy_MethodEvaluated(declaringClass,name,protoShorty,protoReturnType,protoParameterTypes);
            }

            @NonNull
            public abstract String declaringClass();

            @NonNull
            public abstract String name();

            @NonNull
            public abstract String protoShorty();

            @NonNull
            public abstract String protoReturnType();

            @NonNull
            public abstract String protoParameterTypes();
        }
    }


    static class DexMergeEntry {

        public byte[] dexFileContent;

        public String name;

        public DexMergeEntry(byte[] dexFileContent, String name) {
            this.dexFileContent = dexFileContent;
            this.name = name;
        }
    }
}
