package com.android.tools.r8.dex;

import com.android.tools.r8.AtlasD8;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.graph.*;
import com.android.tools.r8.naming.MinifiedNameMapPrinter;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.utils.*;
import com.taobao.android.builder.tools.ReflectUtils;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2ReferenceMap;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;

import java.io.*;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * AtlasApplicationWriter
 *
 * @author zhayu.ll
 * @date 18/4/3
 */
public class AtlasApplicationWriter extends ApplicationWriter{



    public AtlasApplicationWriter(DexApplication application, AppInfo appInfo, InternalOptions options, Marker marker, byte[] deadCode, NamingLens namingLens, byte[] proguardSeedsData) {
        super(application, appInfo, options, marker, deadCode, namingLens, proguardSeedsData);
    }

    @Override
    public AndroidApp write(PackageDistribution packageDistribution, ExecutorService executorService) throws IOException, ExecutionException {
        this.application.timing.begin("AtlasApplicationWriter.write");

        AndroidApp var10;
        try {
            this.application.dexItemFactory.sort(this.namingLens);

            assert this.markerString == null || this.application.dexItemFactory.extractMarker() != null;

            AtlasApplicationWriter.SortAnnotations sortAnnotations = new AtlasApplicationWriter.SortAnnotations();
            this.application.classes().forEach((clazz) -> {
                clazz.addDependencies(sortAnnotations);
            });
            AtlasVirtualFile.Distributor distributor = null;
            if (this.options.outputMode == OutputMode.FilePerClass) {
                assert packageDistribution == null : "Cannot combine package distribution definition with file-per-class option.";

                distributor = new AtlasVirtualFile.FilePerClassDistributor(this);
            } else if (!this.options.canUseMultidex() && this.options.mainDexKeepRules.isEmpty() && this.application.mainDexList.isEmpty()) {
                if (packageDistribution != null) {
                    throw new CompilationError("Cannot apply package distribution. Multidex is not supported with API level " + this.options.minApiLevel + ". For API level < " + 21 + ", main dex classes list or rules must be specified.");
                }

                distributor = new AtlasVirtualFile.MonoDexDistributor(this);
            } else if (packageDistribution != null) {
                assert !this.options.minimalMainDex : "Cannot combine package distribution definition with minimal-main-dex option.";

                distributor = new AtlasVirtualFile.PackageMapDistributor(this, packageDistribution, executorService);
            } else {
                distributor = new AtlasVirtualFile.FillFilesDistributor(this, this.options.minimalMainDex);
            }

            Map<Integer, AtlasVirtualFile> newFiles = ((AtlasVirtualFile.Distributor)distributor).run();
            LinkedHashMap<AtlasVirtualFile, Future<byte[]>> dexDataFutures = new LinkedHashMap();

            for(int i = 0; i < newFiles.size(); ++i) {
                AtlasVirtualFile newFile = (AtlasVirtualFile)newFiles.get(i);

                assert newFile.getId() == i;

                assert !newFile.isEmpty();

                if (!newFile.isEmpty()) {
                    dexDataFutures.put(newFile, executorService.submit(() -> {
                        return writeDexFile(newFile);
                    }));
                }
            }

            AndroidApp.Builder builder = AndroidApp.builder();

            try {
                Iterator var17 = dexDataFutures.entrySet().iterator();

                while(var17.hasNext()) {
                    Map.Entry<AtlasVirtualFile, Future<byte[]>> entry = (Map.Entry)var17.next();
                    builder.addDexProgramData((byte[])((Future)entry.getValue()).get(), ((AtlasVirtualFile)entry.getKey()).getClassDescriptors());
                }
            } catch (InterruptedException var14) {
                throw new RuntimeException("Interrupted while waiting for future.", var14);
            }

            if (this.deadCode != null) {
                builder.setDeadCode(this.deadCode);
            }

            byte[] proguardMapResult = this.writeProguardMapFile();
            if (proguardMapResult != null) {
                builder.setProguardMapData(proguardMapResult);
            }

            if (this.proguardSeedsData != null) {
                builder.setProguardSeedsData(this.proguardSeedsData);
            }

            byte[] mainDexList = this.writeMainDexList();
            if (mainDexList != null) {
                builder.setMainDexListOutputData(mainDexList);
            }

            var10 = builder.build();
        } finally {
            this.application.timing.end();
        }

        return var10;
    }

    private byte[] writeDexFile(AtlasVirtualFile vfile) {
        FileWriter fileWriter = new FileWriter(vfile.computeMapping(this.application), this.application, this.appInfo, this.options, this.namingLens);
        fileWriter.rewriteCodeWithJumboStrings(vfile.classes());
        fileWriter.collect();
        processFileWriter(fileWriter);
        return fileWriter.generate();
    }

    private void processFileWriter(FileWriter fileWriter) {
        if (AtlasD8.deepShrink) {
            System.out.println("start to deepShrink of dx");
            Object mixedSectionOffsets = ReflectUtils.getField(fileWriter, "mixedSectionOffsets");
            ObjectToOffsetMapping mapping = (ObjectToOffsetMapping) ReflectUtils.getField(fileWriter, "mapping");

            Object2IntMap<DexDebugInfo> debugInfoObject2IntMap = (Object2IntMap<DexDebugInfo>) ReflectUtils.getField(mixedSectionOffsets, "debugInfos");
            Reference2IntMap<DexCode> codesObject2IntMap = (Reference2IntMap<DexCode>) ReflectUtils.getField(mixedSectionOffsets, "codes");

            debugInfoObject2IntMap.clear();
            for (DexProgramClass dexProgramClass:mapping.getClasses()){
                ReflectUtils.updateField(dexProgramClass,"sourceFile",null);
            }
            for (DexCode dexCode:codesObject2IntMap.keySet()){
                dexCode.setDebugInfo(null);
            }
            System.out.println("end to deepShrink of dx");
        }


    }

    private byte[] writeProguardMapFile() throws IOException {
        if (!this.namingLens.isIdentityLens()) {
            MinifiedNameMapPrinter printer = new MinifiedNameMapPrinter(this.application, this.namingLens);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            PrintStream stream = new PrintStream(bytes);
            printer.write(stream);
            stream.flush();
            return bytes.toByteArray();
        } else if (this.application.getProguardMap() != null) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            Writer writer = new PrintWriter(bytes);
            this.application.getProguardMap().write(writer, !this.options.skipDebugLineNumberOpt);
            writer.flush();
            return bytes.toByteArray();
        } else {
            return null;
        }
    }

    private String mapMainDexListName(DexType type) {
        return DescriptorUtils.descriptorToJavaType(this.namingLens.lookupDescriptor(type).toString()).replace('.', '/') + ".class";
    }

    private byte[] writeMainDexList() throws IOException {
        if (this.application.mainDexList.isEmpty()) {
            return null;
        } else {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            PrintWriter writer = new PrintWriter(bytes);
            this.application.mainDexList.forEach((type) -> {
                writer.println(this.mapMainDexListName(type));
            });
            writer.flush();
            return bytes.toByteArray();
        }
    }

    private static class SortAnnotations extends MixedSectionCollection {
        private SortAnnotations() {
        }

        public boolean add(DexAnnotationSet dexAnnotationSet) {
            dexAnnotationSet.sort();
            return true;
        }

        public boolean add(DexAnnotation annotation) {
            annotation.annotation.sort();
            return true;
        }

        public boolean add(DexEncodedArray dexEncodedArray) {
            DexValue[] var2 = dexEncodedArray.values;
            int var3 = var2.length;

            for(int var4 = 0; var4 < var3; ++var4) {
                DexValue value = var2[var4];
                value.sort();
            }

            return true;
        }

        public boolean add(DexProgramClass dexClassData) {
            return true;
        }

        public boolean add(DexCode dexCode) {
            return true;
        }

        public boolean add(DexDebugInfo dexDebugInfo) {
            return true;
        }

        public boolean add(DexTypeList dexTypeList) {
            return true;
        }

        public boolean add(DexAnnotationSetRefList annotationSetRefList) {
            return true;
        }

        public boolean setAnnotationsDirectoryForClass(DexProgramClass clazz, DexAnnotationDirectory annotationDirectory) {
            return true;
        }
    }
}
