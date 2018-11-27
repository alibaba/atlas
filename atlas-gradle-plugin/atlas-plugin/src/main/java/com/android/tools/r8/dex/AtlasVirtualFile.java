package com.android.tools.r8.dex;

import com.android.tools.r8.dex.ApplicationWriter;
import com.android.tools.r8.dex.VirtualFile;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.errors.InternalCompilerError;
import com.android.tools.r8.errors.MainDexError;
import com.android.tools.r8.graph.*;
import com.android.tools.r8.naming.ClassNameMapper;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.PackageDistribution;
import com.android.tools.r8.utils.ThreadUtils;
import com.google.common.collect.Iterators;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.collect.UnmodifiableIterator;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Function;

/**
 * AtlasFillFilesDistributor
 *
 * @author zhayu.ll
 * @date 18/4/3
 */
public class AtlasVirtualFile {
    private static final int MAX_ENTRIES = 65000;
    private static final int MAX_PREFILL_ENTRIES = 60536;
    private final int id;
    private final AtlasVirtualFile.VirtualFileIndexedItemCollection indexedItems;
    private final AtlasVirtualFile.IndexedItemTransaction transaction;

    private AtlasVirtualFile(int id, NamingLens namingLens) {
        this.id = id;
        this.indexedItems = new AtlasVirtualFile.VirtualFileIndexedItemCollection(id);
        this.transaction = new AtlasVirtualFile.IndexedItemTransaction(this.indexedItems, namingLens);
    }

    public int getId() {
        return this.id;
    }

    public Set<String> getClassDescriptors() {

        Set<String> classDescriptors = new HashSet<>();
        for (DexProgramClass clazz : indexedItems.classes) {
            boolean added = classDescriptors.add(clazz.type.descriptor.toString());
            assert added;
        }
        return classDescriptors;
    }

    public static String deriveCommonPrefixAndSanityCheck(List<String> fileNames) {
        Iterator<String> nameIterator = fileNames.iterator();
        String first = (String)nameIterator.next();
        if (!first.toLowerCase().endsWith(".dex")) {
            throw new RuntimeException("Illegal suffix for dex file: `" + first + "`.");
        } else {
            String prefix = first.substring(0, first.length() - ".dex".length());
            int var4 = 2;

            String numberPart;
            do {
                if (!nameIterator.hasNext()) {
                    return prefix;
                }

                String next = (String)nameIterator.next();
                if (!next.toLowerCase().endsWith(".dex")) {
                    throw new RuntimeException("Illegal suffix for dex file: `" + first + "`.");
                }

                if (!next.startsWith(prefix)) {
                    throw new RuntimeException("Input filenames lack common prefix.");
                }

                numberPart = next.substring(prefix.length(), next.length() - ".dex".length());
            } while(Integer.parseInt(numberPart) == var4++);

            throw new RuntimeException("DEX files are not numbered consecutively.");
        }
    }

    private static Map<DexProgramClass, String> computeOriginalNameMapping(Collection<DexProgramClass> classes, ClassNameMapper proguardMap) {
        Map<DexProgramClass, String> originalNames = new HashMap();
        classes.forEach((c) -> {
            String var10000 = (String)originalNames.put(c, DescriptorUtils.descriptorToJavaType(c.type.toDescriptorString(), proguardMap));
        });
        return originalNames;
    }

    private static String extractPrefixToken(int prefixLength, String className, boolean addStar) {
        int index = 0;
        int lastIndex = 0;

        int segmentCount;
        for(segmentCount = 0; lastIndex != -1 && segmentCount++ < prefixLength; lastIndex = className.indexOf(46, lastIndex + 1)) {
            index = lastIndex;
        }

        String prefix = className.substring(0, index);
        if (addStar && segmentCount >= prefixLength) {
            prefix = prefix + ".*";
        }

        return prefix;
    }

    public ObjectToOffsetMapping computeMapping(DexApplication application) {
        assert this.transaction.isEmpty();

        return new ObjectToOffsetMapping(this.id, application, (DexProgramClass[])this.indexedItems.classes.toArray(new DexProgramClass[this.indexedItems.classes.size()]), (DexProto[])this.indexedItems.protos.toArray(new DexProto[this.indexedItems.protos.size()]), (DexType[])this.indexedItems.types.toArray(new DexType[this.indexedItems.types.size()]), (DexMethod[])this.indexedItems.methods.toArray(new DexMethod[this.indexedItems.methods.size()]), (DexField[])this.indexedItems.fields.toArray(new DexField[this.indexedItems.fields.size()]), (DexString[])this.indexedItems.strings.toArray(new DexString[this.indexedItems.strings.size()]), (DexCallSite[])this.indexedItems.callSites.toArray(new DexCallSite[this.indexedItems.callSites.size()]), (DexMethodHandle[])this.indexedItems.methodHandles.toArray(new DexMethodHandle[this.indexedItems.methodHandles.size()]));
    }

    private void addClass(DexProgramClass clazz) {
        this.transaction.addClassAndDependencies(clazz);
    }

    private static boolean isFull(int numberOfMethods, int numberOfFields, int maximum) {
        return numberOfMethods > maximum || numberOfFields > maximum;
    }

    private boolean isFull() {
        return isFull(this.transaction.getNumberOfMethods(), this.transaction.getNumberOfFields(), MAX_ENTRIES);
    }

    void throwIfFull(boolean hasMainDexList) {
        if (this.isFull()) {
            throw new MainDexError(hasMainDexList, (long)this.transaction.getNumberOfMethods(), (long)this.transaction.getNumberOfFields(), MAX_ENTRIES);
        }
    }

    private boolean isFilledEnough(AtlasVirtualFile.FillStrategy fillStrategy) {
        return isFull(this.transaction.getNumberOfMethods(), this.transaction.getNumberOfFields(), fillStrategy == AtlasVirtualFile.FillStrategy.FILL_MAX ? MAX_ENTRIES : MAX_PREFILL_ENTRIES);
    }

    public void abortTransaction() {
        this.transaction.abort();
    }

    public void commitTransaction() {
        this.transaction.commit();
    }

    public boolean isEmpty() {
        return this.indexedItems.classes.isEmpty();
    }

    public List<DexProgramClass> classes() {
        return this.indexedItems.classes;
    }

    private static class PackageSplitPopulator implements Callable<Map<String, Integer>> {
        private static final int MINIMUM_PREFIX_LENGTH = 4;
        private static final int MAXIMUM_PREFIX_LENGTH = 7;
        private static final int MIN_FILL_FACTOR = 5;
        private final List<DexProgramClass> classes;
        private final Map<DexProgramClass, String> originalNames;
        private final Set<String> previousPrefixes;
        private final DexItemFactory dexItemFactory;
        private final AtlasVirtualFile.FillStrategy fillStrategy;
        private final AtlasVirtualFile.VirtualFileCycler cycler;

        PackageSplitPopulator(Map<Integer, AtlasVirtualFile> files, Set<DexProgramClass> classes, Map<DexProgramClass, String> originalNames, Set<String> previousPrefixes, DexItemFactory dexItemFactory, AtlasVirtualFile.FillStrategy fillStrategy, NamingLens namingLens) {
            this.classes = new ArrayList(classes);
            this.originalNames = originalNames;
            this.previousPrefixes = previousPrefixes;
            this.dexItemFactory = dexItemFactory;
            this.fillStrategy = fillStrategy;
            this.cycler = new AtlasVirtualFile.VirtualFileCycler(files, namingLens, fillStrategy);
        }

        private String getOriginalName(DexProgramClass clazz) {
            return this.originalNames != null ? (String)this.originalNames.get(clazz) : clazz.toString();
        }

        public Map<String, Integer> call() throws IOException {
            int prefixLength = 4;
            int transactionStartIndex = 0;
            int fileStartIndex = 0;
            String currentPrefix = null;
            Map<String, Integer> newPackageAssignments = new LinkedHashMap();
            AtlasVirtualFile current = this.cycler.next();
            List<DexProgramClass> nonPackageClasses = new ArrayList();

            for(int classIndex = 0; classIndex < this.classes.size(); ++classIndex) {
                DexProgramClass clazz = (DexProgramClass)this.classes.get(classIndex);
                String originalName = this.getOriginalName(clazz);
                if (!AtlasVirtualFile.PackageMapPopulator.coveredByPrefix(originalName, currentPrefix)) {
                    if (currentPrefix != null) {
                        current.commitTransaction();
                        this.cycler.restart();

                        assert !newPackageAssignments.containsKey(currentPrefix);

                        newPackageAssignments.put(currentPrefix, current.id);
                        prefixLength = 3;
                    }

                    String newPrefix;
                    do {
                        ++prefixLength;
                        newPrefix = AtlasVirtualFile.extractPrefixToken(prefixLength, originalName, false);
                    } while(currentPrefix != null && (currentPrefix.startsWith(newPrefix) || this.conflictsWithPreviousPrefix(newPrefix, originalName)));

                    if (!newPrefix.equals("")) {
                        currentPrefix = AtlasVirtualFile.extractPrefixToken(prefixLength, originalName, true);
                    }

                    transactionStartIndex = classIndex;
                }

                if (currentPrefix != null) {
                    assert clazz.superType != null || clazz.type == this.dexItemFactory.objectType;

                    current.addClass(clazz);
                    if (current.isFilledEnough(this.fillStrategy)) {
                        current.abortTransaction();
                        if (classIndex - transactionStartIndex > (classIndex - fileStartIndex) / 5 && prefixLength < 7) {
                            ++prefixLength;
                        } else {
                            fileStartIndex = transactionStartIndex;
                            if (!this.cycler.hasNext()) {
                                if (current.transaction.getNumberOfClasses() == 0) {
                                    for(int j = transactionStartIndex; j <= classIndex; ++j) {
                                        nonPackageClasses.add(this.classes.get(j));
                                    }

                                    transactionStartIndex = classIndex + 1;
                                }

                                this.cycler.addFile();
                            }

                            current = this.cycler.next();
                        }

                        currentPrefix = null;
                        classIndex = transactionStartIndex - 1;

                        assert current != null;
                    }
                } else {
                    assert clazz.superType != null;

                    assert current.transaction.classes.isEmpty();

                    nonPackageClasses.add(clazz);
                }
            }

            current.commitTransaction();

            assert !newPackageAssignments.containsKey(currentPrefix);

            if (currentPrefix != null) {
                newPackageAssignments.put(currentPrefix, current.id);
            }

            if (nonPackageClasses.size() > 0) {
                this.addNonPackageClasses(this.cycler, nonPackageClasses);
            }

            return newPackageAssignments;
        }

        private void addNonPackageClasses(AtlasVirtualFile.VirtualFileCycler cycler, List<DexProgramClass> nonPackageClasses) {
            cycler.restart();
            AtlasVirtualFile current = cycler.next();
            Iterator var4 = nonPackageClasses.iterator();

            while(var4.hasNext()) {
                DexProgramClass clazz = (DexProgramClass)var4.next();
                if (current.isFilledEnough(this.fillStrategy)) {
                    current = this.getVirtualFile(cycler);
                }

                current.addClass(clazz);

                while(current.isFull()) {
                    current.abortTransaction();
                    current = this.getVirtualFile(cycler);
                    boolean wasEmpty = current.isEmpty();
                    current.addClass(clazz);
                    if (wasEmpty && current.isFull()) {
                        throw new InternalCompilerError("Class " + clazz.toString() + " does not fit into a single dex file.");
                    }
                }

                current.commitTransaction();
            }

        }

        private AtlasVirtualFile getVirtualFile(AtlasVirtualFile.VirtualFileCycler cycler) {
            AtlasVirtualFile current = null;

            while(cycler.hasNext() && (current = cycler.next()).isFilledEnough(this.fillStrategy)) {
                ;
            }

            if (current == null || current.isFilledEnough(this.fillStrategy)) {
                current = cycler.addFile();
            }

            return current;
        }

        private boolean conflictsWithPreviousPrefix(String newPrefix, String originalName) {
            if (this.previousPrefixes == null) {
                return false;
            } else {
                Iterator var3 = this.previousPrefixes.iterator();

                String previous;
                do {
                    if (!var3.hasNext()) {
                        return false;
                    }

                    previous = (String)var3.next();
                } while(!previous.startsWith(newPrefix) || originalName.lastIndexOf(46) <= newPrefix.length());

                return true;
            }
        }
    }

    private static class VirtualFileCycler {
        private Map<Integer, AtlasVirtualFile> files;
        private final NamingLens namingLens;
        private final AtlasVirtualFile.FillStrategy fillStrategy;
        private int nextFileId;
        private Iterator<AtlasVirtualFile> allFilesCyclic;
        private Iterator<AtlasVirtualFile> activeFiles;

        VirtualFileCycler(Map<Integer, AtlasVirtualFile> files, NamingLens namingLens, AtlasVirtualFile.FillStrategy fillStrategy) {
            this.files = files;
            this.namingLens = namingLens;
            this.fillStrategy = fillStrategy;
            this.nextFileId = (Integer)Collections.max(files.keySet()) + 1;
            this.reset();
        }

        private void reset() {
            this.allFilesCyclic = Iterators.cycle(this.files.values());
            this.restart();
        }

        boolean hasNext() {
            return this.activeFiles.hasNext();
        }

        AtlasVirtualFile next() {
            AtlasVirtualFile next = (AtlasVirtualFile)this.activeFiles.next();
            return next;
        }

        void restart() {
            this.activeFiles = Iterators.limit(this.allFilesCyclic, this.files.size());
        }

        AtlasVirtualFile addFile() {
            AtlasVirtualFile newFile = new AtlasVirtualFile(this.nextFileId, this.namingLens);
            this.files.put(this.nextFileId, newFile);
            ++this.nextFileId;
            this.reset();
            return newFile;
        }
    }

    private static class PackageMapPopulator implements Callable<List<DexProgramClass>> {
        private final AtlasVirtualFile file;
        private final Collection<DexProgramClass> classes;
        private final PackageDistribution packageDistribution;
        private final Map<DexProgramClass, String> originalNames;

        PackageMapPopulator(AtlasVirtualFile file, Collection<DexProgramClass> classes, PackageDistribution packageDistribution, Map<DexProgramClass, String> originalNames) {
            this.file = file;
            this.classes = classes;
            this.packageDistribution = packageDistribution;
            this.originalNames = originalNames;
        }

        public List<DexProgramClass> call() {
            String currentPrefix = null;
            int currentFileId = -1;
            List<DexProgramClass> inserted = new ArrayList();
            Iterator var4 = this.classes.iterator();

            do {
                if (!var4.hasNext()) {
                    this.file.commitTransaction();
                    return inserted;
                }

                DexProgramClass clazz = (DexProgramClass)var4.next();
                String originalName = (String)this.originalNames.get(clazz);

                assert originalName != null;

                if (!coveredByPrefix(originalName, currentPrefix)) {
                    if (currentPrefix != null) {
                        this.file.commitTransaction();
                    }

                    currentPrefix = this.lookupPrefixFor(originalName);
                    if (currentPrefix == null) {
                        currentFileId = -1;
                    } else {
                        currentFileId = this.packageDistribution.get(currentPrefix);
                    }
                }

                if (currentFileId == this.file.id) {
                    this.file.addClass(clazz);
                    inserted.add(clazz);
                }
            } while(!this.file.isFull());

            throw new CompilationError("Cannot fit package " + currentPrefix + " in requested dex file, consider removing mapping.");
        }

        private String lookupPrefixFor(String originalName) {
            int lastIndexOfDot = originalName.lastIndexOf(46);
            if (lastIndexOfDot < 0) {
                return null;
            } else {
                String prefix = originalName.substring(0, lastIndexOfDot);
                if (this.packageDistribution.containsFile(prefix)) {
                    return prefix;
                } else {
                    prefix = originalName;

                    do {
                        int index;
                        if ((index = prefix.lastIndexOf(46)) == -1) {
                            return null;
                        }

                        prefix = prefix.substring(0, index);
                    } while(!this.packageDistribution.containsFile(prefix + ".*"));

                    return prefix + ".*";
                }
            }
        }

        static boolean coveredByPrefix(String originalName, String currentPrefix) {
            if (currentPrefix == null) {
                return false;
            } else if (currentPrefix.endsWith(".*")) {
                return originalName.startsWith(currentPrefix.substring(0, currentPrefix.length() - 2));
            } else {
                return originalName.startsWith(currentPrefix) && originalName.lastIndexOf(46) == currentPrefix.length();
            }
        }
    }

    private static class IndexedItemTransaction implements IndexedItemCollection {
        private final AtlasVirtualFile.VirtualFileIndexedItemCollection base;
        private final NamingLens namingLens;
        private final Set<DexProgramClass> classes;
        private final Set<DexField> fields;
        private final Set<DexMethod> methods;
        private final Set<DexType> types;
        private final Set<DexProto> protos;
        private final Set<DexString> strings;
        private final Set<DexCallSite> callSites;
        private final Set<DexMethodHandle> methodHandles;

        private IndexedItemTransaction(AtlasVirtualFile.VirtualFileIndexedItemCollection base, NamingLens namingLens) {
            this.classes = new LinkedHashSet();
            this.fields = new LinkedHashSet();
            this.methods = new LinkedHashSet();
            this.types = new LinkedHashSet();
            this.protos = new LinkedHashSet();
            this.strings = new LinkedHashSet();
            this.callSites = new LinkedHashSet();
            this.methodHandles = new LinkedHashSet();
            this.base = base;
            this.namingLens = namingLens;
        }

        private <T extends IndexedDexItem> boolean maybeInsert(T item, Set<T> set) {
            if (!item.hasVirtualFileData(this.base.id) && !set.contains(item)) {
                set.add(item);
                return true;
            } else {
                return false;
            }
        }

        void addClassAndDependencies(DexProgramClass clazz) {
            clazz.collectIndexedItems(this);
        }

        public boolean addClass(DexProgramClass dexProgramClass) {
            if (!this.base.seenClasses.contains(dexProgramClass) && !this.classes.contains(dexProgramClass)) {
                this.classes.add(dexProgramClass);
                return true;
            } else {
                return false;
            }
        }

        public boolean addField(DexField field) {
            return this.maybeInsert(field, this.fields);
        }

        public boolean addMethod(DexMethod method) {
            return this.maybeInsert(method, this.methods);
        }

        public boolean addString(DexString string) {
            return this.maybeInsert(string, this.strings);
        }

        public boolean addProto(DexProto proto) {
            return this.maybeInsert(proto, this.protos);
        }

        public boolean addType(DexType type) {
            return this.maybeInsert(type, this.types);
        }

        public boolean addCallSite(DexCallSite callSite) {
            return this.maybeInsert(callSite, this.callSites);
        }

        public boolean addMethodHandle(DexMethodHandle methodHandle) {
            return this.maybeInsert(methodHandle, this.methodHandles);
        }

        public DexString getRenamedDescriptor(DexType type) {
            return this.namingLens.lookupDescriptor(type);
        }

        public DexString getRenamedName(DexMethod method) {
            assert this.namingLens.checkTargetCanBeTranslated(method);

            return this.namingLens.lookupName(method);
        }

        public DexString getRenamedName(DexField field) {
            return this.namingLens.lookupName(field);
        }

        int getNumberOfMethods() {
            return this.methods.size() + this.base.getNumberOfMethods();
        }

        int getNumberOfFields() {
            return this.fields.size() + this.base.getNumberOfFields();
        }

        private <T extends DexItem> void commitItemsIn(Set<T> set, Function<T, Boolean> hook) {
            set.forEach((item) -> {
                boolean newlyAdded = (Boolean)hook.apply(item);

                assert newlyAdded;

            });
            set.clear();
        }

        void commit() {
            Set var10001 = this.classes;
            AtlasVirtualFile.VirtualFileIndexedItemCollection var10002 = this.base;
            this.base.getClass();
            this.commitItemsIn(var10001, var10002::addClass);
            var10001 = this.fields;
            var10002 = this.base;
            this.base.getClass();
            this.commitItemsIn(var10001, var10002::addField);
            var10001 = this.methods;
            var10002 = this.base;
            this.base.getClass();
            this.commitItemsIn(var10001, var10002::addMethod);
            var10001 = this.protos;
            var10002 = this.base;
            this.base.getClass();
            this.commitItemsIn(var10001, var10002::addProto);
            var10001 = this.types;
            var10002 = this.base;
            this.base.getClass();
            this.commitItemsIn(var10001, var10002::addType);
            var10001 = this.strings;
            var10002 = this.base;
            this.base.getClass();
            this.commitItemsIn(var10001, var10002::addString);
            var10001 = this.callSites;
            var10002 = this.base;
            this.base.getClass();
            this.commitItemsIn(var10001, var10002::addCallSite);
            var10001 = this.methodHandles;
            var10002 = this.base;
            this.base.getClass();
            this.commitItemsIn(var10001, var10002::addMethodHandle);
        }

        void abort() {
            this.classes.clear();
            this.fields.clear();
            this.methods.clear();
            this.protos.clear();
            this.types.clear();
            this.strings.clear();
        }

        public boolean isEmpty() {
            return this.classes.isEmpty() && this.fields.isEmpty() && this.methods.isEmpty() && this.protos.isEmpty() && this.types.isEmpty() && this.strings.isEmpty();
        }

        int getNumberOfStrings() {
            return this.strings.size() + this.base.getNumberOfStrings();
        }

        int getNumberOfClasses() {
            return this.classes.size() + this.base.classes.size();
        }
    }

    private static class VirtualFileIndexedItemCollection implements IndexedItemCollection {
        final int id;
        private final List<DexProgramClass> classes;
        private final List<DexProto> protos;
        private final List<DexType> types;
        private final List<DexMethod> methods;
        private final List<DexField> fields;
        private final List<DexString> strings;
        private final List<DexCallSite> callSites;
        private final List<DexMethodHandle> methodHandles;
        private final Set<DexClass> seenClasses;

        private VirtualFileIndexedItemCollection(int id) {
            this.classes = new ArrayList();
            this.protos = new ArrayList();
            this.types = new ArrayList();
            this.methods = new ArrayList();
            this.fields = new ArrayList();
            this.strings = new ArrayList();
            this.callSites = new ArrayList();
            this.methodHandles = new ArrayList();
            this.seenClasses = Sets.newIdentityHashSet();
            this.id = id;
        }

        private <T extends IndexedDexItem> boolean addItem(T item, List<T> itemList) {
            assert item != null;

            if (item.assignToVirtualFile(this.id)) {
                itemList.add(item);
                return true;
            } else {
                return false;
            }
        }

        public boolean addClass(DexProgramClass clazz) {
            if (this.seenClasses.add(clazz)) {
                this.classes.add(clazz);
                return true;
            } else {
                return false;
            }
        }

        public boolean addField(DexField field) {
            return this.addItem(field, this.fields);
        }

        public boolean addMethod(DexMethod method) {
            return this.addItem(method, this.methods);
        }

        public boolean addString(DexString string) {
            return this.addItem(string, this.strings);
        }

        public boolean addProto(DexProto proto) {
            return this.addItem(proto, this.protos);
        }

        public boolean addCallSite(DexCallSite callSite) {
            return this.addItem(callSite, this.callSites);
        }

        public boolean addMethodHandle(DexMethodHandle methodHandle) {
            return this.addItem(methodHandle, this.methodHandles);
        }

        public boolean addType(DexType type) {
            return this.addItem(type, this.types);
        }

        public int getNumberOfMethods() {
            return this.methods.size();
        }

        public int getNumberOfFields() {
            return this.fields.size();
        }

        public int getNumberOfStrings() {
            return this.strings.size();
        }
    }

    public static class PackageMapDistributor extends AtlasVirtualFile.DistributorBase {
        private final PackageDistribution packageDistribution;
        private final ExecutorService executorService;

        PackageMapDistributor(ApplicationWriter writer, PackageDistribution packageDistribution, ExecutorService executorService) {
            super(writer);
            this.packageDistribution = packageDistribution;
            this.executorService = executorService;
        }

        public Map<Integer, AtlasVirtualFile> run() throws ExecutionException, IOException {
            assert this.nameToFileMap.size() == 1;

            assert this.nameToFileMap.containsKey(0);

            int maxReferencedIndex = this.packageDistribution.maxReferencedIndex();

            for(int index = 1; index <= maxReferencedIndex; ++index) {
                AtlasVirtualFile file = new AtlasVirtualFile(index, this.writer.namingLens);
                this.nameToFileMap.put(index, file);
            }

            this.fillForMainDexList(this.classes);
            this.classes = this.sortClassesByPackage(this.classes, this.originalNames);
            Set<String> usedPrefixes = this.fillForDistribution(this.classes, this.originalNames);
            Map newAssignments;
            if (this.classes.isEmpty()) {
                newAssignments = Collections.emptyMap();
            } else {
                newAssignments = (new AtlasVirtualFile.PackageSplitPopulator(this.nameToFileMap, this.classes, this.originalNames, usedPrefixes, this.application.dexItemFactory, AtlasVirtualFile.FillStrategy.LEAVE_SPACE_FOR_GROWTH, this.writer.namingLens)).call();
                if (!newAssignments.isEmpty() && this.nameToFileMap.size() > 1) {
                    System.err.println(" * The used package map is missing entries. The following default mappings have been used:");
                    this.writeAssignments(newAssignments, new OutputStreamWriter(System.err));
                    System.err.println(" * Consider updating the map.");
                }
            }

            Path newPackageMap = Paths.get("package.map");
            System.out.println(" - " + newPackageMap.toString());
            PackageDistribution.writePackageToFileMap(newPackageMap, newAssignments, this.packageDistribution);
            return this.nameToFileMap;
        }

        private Set<String> fillForDistribution(Set<DexProgramClass> classes, Map<DexProgramClass, String> originalNames) throws ExecutionException {
            Set<String> usedPrefixes = null;
            if (this.packageDistribution != null) {
                ArrayList<Future<List<DexProgramClass>>> futures = new ArrayList(this.nameToFileMap.size());
                usedPrefixes = this.packageDistribution.getFiles();
                Iterator var5 = this.nameToFileMap.values().iterator();

                while(var5.hasNext()) {
                    AtlasVirtualFile file = (AtlasVirtualFile)var5.next();
                    AtlasVirtualFile.PackageMapPopulator populator = new AtlasVirtualFile.PackageMapPopulator(file, classes, this.packageDistribution, originalNames);
                    futures.add(this.executorService.submit(populator));
                }

                ThreadUtils.awaitFutures(futures).forEach(classes::removeAll);
            }

            return usedPrefixes;
        }

        private void writeAssignments(Map<String, Integer> assignments, Writer output) throws IOException {
            Iterator var3 = assignments.entrySet().iterator();

            while(var3.hasNext()) {
                Map.Entry<String, Integer> entry = (Map.Entry)var3.next();
                output.write("    ");
                PackageDistribution.formatEntry(entry, output);
                output.write("\n");
            }

            output.flush();
        }
    }

    public static class MonoDexDistributor extends AtlasVirtualFile.DistributorBase {
        MonoDexDistributor(ApplicationWriter writer) {
            super(writer);
        }

        public Map<Integer, AtlasVirtualFile> run() throws ExecutionException, IOException {
            Iterator var1 = this.classes.iterator();

            while(var1.hasNext()) {
                DexProgramClass programClass = (DexProgramClass)var1.next();
                this.mainDexFile.addClass(programClass);
            }

            this.mainDexFile.commitTransaction();
            this.mainDexFile.throwIfFull(false);
            return this.nameToFileMap;
        }
    }

    public static class FillFilesDistributor extends AtlasVirtualFile.DistributorBase {
        boolean minimalMainDex;
        private final AtlasVirtualFile.FillStrategy fillStrategy;

        FillFilesDistributor(ApplicationWriter writer, boolean minimalMainDex) {
            super(writer);
            this.minimalMainDex = minimalMainDex;
            this.fillStrategy = FillStrategy.FILL_MAX;
        }

        public Map<Integer, AtlasVirtualFile> run() throws ExecutionException, IOException {
            this.fillForMainDexList(this.classes);
            if (this.classes.isEmpty()) {
                return this.nameToFileMap;
            } else {
                Map<Integer, AtlasVirtualFile> filesForDistribution = this.nameToFileMap;
                if (this.minimalMainDex && !this.mainDexFile.isEmpty()) {
                    assert !((AtlasVirtualFile)this.nameToFileMap.get(0)).isEmpty();

                    this.nameToFileMap.put(1, new AtlasVirtualFile(1, this.writer.namingLens));
                    filesForDistribution = Maps.filterKeys(filesForDistribution, (key) -> {
                        return key != 0;
                    });
                }

                this.classes = this.sortClassesByPackage(this.classes, this.originalNames);
                (new AtlasVirtualFile.PackageSplitPopulator(filesForDistribution, this.classes, this.originalNames, (Set)null, this.application.dexItemFactory, this.fillStrategy, this.writer.namingLens)).call();
                return this.nameToFileMap;
            }
        }
    }

    public abstract static class DistributorBase extends AtlasVirtualFile.Distributor {
        protected Set<DexProgramClass> classes;
        protected Map<DexProgramClass, String> originalNames;
        protected final AtlasVirtualFile mainDexFile;

        DistributorBase(ApplicationWriter writer) {
            super(writer);
            this.mainDexFile = new AtlasVirtualFile(0, writer.namingLens);
            this.nameToFileMap.put(0, this.mainDexFile);
            if (writer.markerString != null) {
                this.mainDexFile.transaction.addString(writer.markerString);
                this.mainDexFile.commitTransaction();
            }

            this.classes = Sets.newHashSet(this.application.classes());
            this.originalNames = AtlasVirtualFile.computeOriginalNameMapping(this.classes, this.application.getProguardMap());
        }

        protected void fillForMainDexList(Set<DexProgramClass> classes) {
            if (!this.application.mainDexList.isEmpty()) {
                AtlasVirtualFile mainDexFile = (AtlasVirtualFile)this.nameToFileMap.get(0);

                for(UnmodifiableIterator var3 = this.application.mainDexList.iterator(); var3.hasNext(); mainDexFile.commitTransaction()) {
                    DexType type = (DexType)var3.next();
                    DexClass clazz = this.application.definitionFor(type);
                    if (clazz != null && clazz.isProgramClass()) {
                        DexProgramClass programClass = (DexProgramClass)clazz;
                        mainDexFile.addClass(programClass);
                        classes.remove(programClass);
                    } else {
                        System.out.println("WARNING: Application does not contain `" + type.toSourceString() + "` as referenced in main-dex-list.");
                    }
                }

                mainDexFile.throwIfFull(true);
            }

        }

        TreeSet<DexProgramClass> sortClassesByPackage(Set<DexProgramClass> classes, Map<DexProgramClass, String> originalNames) {
            TreeSet<DexProgramClass> sortedClasses = new TreeSet((a, b) -> {
                String originalA = (String)originalNames.get(a);
                String originalB = (String)originalNames.get(b);
                int indexA = originalA.lastIndexOf(46);
                int indexB = originalB.lastIndexOf(46);
                if (indexA == -1 && indexB == -1) {
                    return originalA.compareTo(originalB);
                } else if (indexA == -1) {
                    return -1;
                } else if (indexB == -1) {
                    return 1;
                } else {
                    String prefixA = originalA.substring(0, indexA);
                    String prefixB = originalB.substring(0, indexB);
                    int result = prefixA.compareTo(prefixB);
                    return result != 0 ? result : originalA.compareTo(originalB);
                }
            });
            sortedClasses.addAll(classes);
            return sortedClasses;
        }
    }

    public static class FilePerClassDistributor extends AtlasVirtualFile.Distributor {
        FilePerClassDistributor(ApplicationWriter writer) {
            super(writer);
        }

        public Map<Integer, AtlasVirtualFile> run() throws ExecutionException, IOException {
            Iterator var1 = this.application.classes().iterator();

            while(var1.hasNext()) {
                DexProgramClass clazz = (DexProgramClass)var1.next();
                AtlasVirtualFile file = new AtlasVirtualFile(this.nameToFileMap.size(), this.writer.namingLens);
                this.nameToFileMap.put(this.nameToFileMap.size(), file);
                file.addClass(clazz);
                file.commitTransaction();
            }

            return this.nameToFileMap;
        }
    }

    public abstract static class Distributor {
        protected final DexApplication application;
        protected final ApplicationWriter writer;
        protected final Map<Integer, AtlasVirtualFile> nameToFileMap = new HashMap();

        Distributor(ApplicationWriter writer) {
            this.application = writer.application;
            this.writer = writer;
        }

        public abstract Map<Integer, AtlasVirtualFile> run() throws ExecutionException, IOException;
    }

    static enum FillStrategy {
        FILL_MAX,
        LEAVE_SPACE_FOR_GROWTH;


    }
}
