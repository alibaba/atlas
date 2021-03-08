package com.taobao.android.builder.tasks.app;

import com.alibaba.fastjson.JSON;
import com.android.build.gradle.api.BaseVariantOutput;
import com.android.build.gradle.internal.api.AppVariantContext;
import com.android.build.gradle.internal.api.AppVariantOutputContext;
import com.android.build.gradle.internal.api.VariantContext;
import com.android.build.gradle.internal.publishing.AndroidArtifacts;
import com.android.build.gradle.internal.tasks.AndroidBuilderTask;
import com.android.build.gradle.tasks.ProcessApplicationManifest;
import com.android.ide.common.resources.*;
import com.android.resources.ResourceType;
import com.google.common.base.Preconditions;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.sun.org.apache.xerces.internal.dom.NamedNodeMapImpl;
import com.taobao.android.builder.AtlasBuildContext;
import com.taobao.android.builder.dependency.AtlasDependencyTree;
import com.taobao.android.builder.extension.TBuildConfig;
import com.taobao.android.builder.tasks.manager.MtlBaseTaskAction;
import com.taobao.android.builder.tools.FileNameUtils;
import com.taobao.android.builder.tools.MD5Util;

import javafx.util.Pair;
import jxl.Workbook;
import jxl.write.Label;
import jxl.write.WritableSheet;
import jxl.write.WritableWorkbook;
import jxl.write.WriteException;

import org.apache.commons.compress.compressors.FileNameUtil;
import org.apache.commons.io.FileUtils;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.tasks.TaskAction;

import javax.xml.parsers.DocumentBuilderFactory;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * ScanDupResTask
 *
 * @author zhayu.ll
 * @date 18/8/10
 */
public class ScanDupResTask extends AndroidBuilderTask {

    private AppVariantOutputContext appVariantOutputContext;

    private AppVariantContext appVariantContext;

    Map<String, String> depsMap = new HashMap<>();


    @TaskAction
    void generate() throws IOException {


        AtlasDependencyTree atlasDependencyTree = AtlasBuildContext.androidDependencyTrees.get(
                getVariantName());
        if (null == atlasDependencyTree) {
            return;
        }
        TBuildConfig tBuildConfig = appVariantContext.getAtlasExtension().getTBuildConfig();

        List<String> duplicateResList = tBuildConfig.getDuplicateResList();

        File dupResFileXml = new File(appVariantContext.getProject().getBuildDir(), "outputs/warning-dup-res.xls");

        WritableWorkbook writableWorkbook = null;

        try {
            dupResFileXml.getParentFile().mkdirs();
            dupResFileXml.createNewFile();
            writableWorkbook = Workbook.createWorkbook(dupResFileXml);

        } catch (IOException e) {
            e.printStackTrace();
        }

        WritableSheet sheet = writableWorkbook.createSheet("重复res", 0);
        WritableSheet sheet1 = writableWorkbook.createSheet("重复asset", 0);

        ArtifactCollection res = appVariantContext.getScope().getArtifactCollection(AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH, AndroidArtifacts.ArtifactScope.ALL, AndroidArtifacts.ArtifactType.ANDROID_RES);
        ArtifactCollection assets = appVariantContext.getScope().getArtifactCollection(AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH, AndroidArtifacts.ArtifactScope.ALL, AndroidArtifacts.ArtifactType.ASSETS);

        findDuplicateResource(sheet1,assets,duplicateResList);




        Map<String, File> map = new HashMap<>();
        Map<String,List<String>> resFiles = new HashMap<>();
        Map<String, Pair<String, File>> valuesMap = new HashMap<>();
        ResourceMerger resourceMerger = new ResourceMerger(14);
        if (res != null) {

            Set<ResolvedArtifactResult> libArtifacts = res.getArtifacts();
            List<ResourceSet> resourceSetList = Lists.newArrayListWithExpectedSize(libArtifacts.size());
            // the order of the artifact is descending order, so we need to reverse it.
            for (ResolvedArtifactResult artifact : libArtifacts) {
                ResourceSet resourceSet =
                        new ResourceSet(
                                ProcessApplicationManifest.getArtifactName(artifact),
                                null,
                                null,
                                true);
                resourceSet.setFromDependency(true);
                resourceSet.addSource(artifact.getFile());
                Collection<File>files = FileUtils.listFiles(artifact.getFile(),new String[]{"png","xml","webp","jpg"},true);
                List<String> fileNames = new ArrayList<String>();
                files.forEach(new Consumer<File>() {
                    @Override
                    public void accept(File file) {
                        fileNames.add("res"+file.getAbsolutePath().substring(artifact.getFile().getAbsolutePath().length()));
                    }
                });
                resFiles.put(ProcessApplicationManifest.getArtifactName(artifact),fileNames);
                depsMap.put(artifact.getFile().getPath(), ProcessApplicationManifest.getArtifactName(artifact));
                // add to 0 always, since we need to reverse the order.
                resourceSetList.add(0, resourceSet);
            }
            resourceSetList.forEach(resourceSet -> {
                try {
                    resourceSet.loadFromFiles(getILogger());
                } catch (MergingException e) {
                    e.printStackTrace();
                }
                resourceMerger.addDataSet(resourceSet);
            });

            ListMultimap<String, ResourceMergerItem> mValuesResMap = ArrayListMultimap.create();


            AtomicInteger atomicInteger = new AtomicInteger(0);

            try {
                resourceMerger.mergeData(new MergeConsumer<ResourceMergerItem>() {
                    @Override
                    public void start(DocumentBuilderFactory documentBuilderFactory) throws ConsumerException {
                    }

                    @Override
                    public void end() throws ConsumerException {

                    }

                    @Override
                    public void addItem(ResourceMergerItem item) throws ConsumerException {
                        DataFile.FileType type = item.getSourceType();
                        if (type == DataFile.FileType.XML_VALUES) {
                            mValuesResMap.put(item.getQualifiers(), item);
                        } else {
                            File file = null;
                            String folderName = null;
                            Preconditions.checkState(item.getSource() != null);
                            file = item.getFile();
                            String name = item.getName();
                            folderName = getFolderName(item);
                            String tag = folderName + "/" + file.getName();

                            if (type == DataFile.FileType.GENERATED_FILES) {
                                if (!map.containsKey(tag)) {
                                    map.put(tag, file);
                                } else if (!map.get(tag).equals(file)) {
                                    if (!isSameBundle(map.get(tag), file, atlasDependencyTree)
                                            && allInMainBundle(getId(map.get(tag)), getId(file), atlasDependencyTree)
                                            && !isSameFile(map.get(tag), file))
                                        checkResourceNeedUnique(duplicateResList, tag, map.get(tag), file);
                                    Label label = new Label(0, atomicInteger.get(), tag);
                                    Label label1 = new Label(1, atomicInteger.get(), getId(map.get(tag)));
                                    Label label2 = new Label(2, atomicInteger.get(), getId(file));
                                    atomicInteger.getAndIncrement();
                                    try {
                                        sheet.addCell(label);
                                        sheet.addCell(label1);
                                        sheet.addCell(label2);

                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }

                                }
//                                    try {
//                                        MergedResourceWriter.FileGenerationParameters workItem = new MergedResourceWriter.FileGenerationParameters(item, this.mPreprocessor);
//                                        if (workItem.resourceItem.getSource() != null) {
//                                            this.getExecutor().submit(workItem);
//                                        }
//                                    } catch (Exception var6) {
//                                        throw new ConsumerException(var6, ((ResourceFile)item.getSource()).getFile());
//                                    }
                            } else if (type == DataFile.FileType.SINGLE_FILE) {
                                if (!map.containsKey(tag)) {
                                    map.put(tag, file);
                                } else if (!map.get(tag).equals(file)) {
                                    if (!isSameBundle(map.get(tag), file, atlasDependencyTree)
                                            && allInMainBundle(getId(map.get(tag)), getId(file), atlasDependencyTree)
                                            && !isSameFile(map.get(tag), file))
                                        checkResourceNeedUnique(duplicateResList, tag, map.get(tag), file);

                                    Label label = new Label(0, atomicInteger.get(), tag);
                                    Label label1 = new Label(1, atomicInteger.get(), getId(map.get(tag)));
                                    Label label2 = new Label(2, atomicInteger.get(), getId(file));
                                    atomicInteger.getAndIncrement();
                                    try {
                                        sheet.addCell(label);
                                        sheet.addCell(label1);
                                        sheet.addCell(label2);

                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                }
                            }

//                                this.mCompileResourceRequests.add(new CompileResourceRequest(file, this.getRootFolder(), folderName));
//                            }
                        }
                    }

                    @Override
                    public void removeItem(ResourceMergerItem removedItem, ResourceMergerItem replacedBy) throws ConsumerException {
                        DataFile.FileType removedType = removedItem.getSourceType();
                        DataFile.FileType replacedType = replacedBy != null ? replacedBy.getSourceType() : null;
                        switch (removedType) {
                            case SINGLE_FILE:
                            case GENERATED_FILES:
                                if (replacedType == DataFile.FileType.SINGLE_FILE || replacedType == DataFile.FileType.GENERATED_FILES) {
//                                    System.out.println(removedItem.getQualifiers()+":"+removedItem.getQualifiers());
//                                    File removedFile = getResourceOutputFile(removedItem);
//                                    File replacedFile = getResourceOutputFile(replacedBy);
//                                    if (removedFile.equals(replacedFile)) {
//                                        break;
//                                    }
                                }

//                                this.removeOutFile(removedItem);
                                break;
                            case XML_VALUES:
                                mValuesResMap.put(removedItem.getQualifiers(), replacedBy);
//                                System.out.println(removedItem.getQualifiers());
//                                this.mQualifierWithDeletedValues.add(removedItem.getQualifiers());
                                break;
                            default:
                                throw new IllegalStateException();
                        }
                    }

                    @Override
                    public boolean ignoreItemInMerge(ResourceMergerItem item) {
                        DataFile.FileType type = item.getSourceType();
                        if (type == DataFile.FileType.XML_VALUES) {
                            mValuesResMap.put(item.getQualifiers(), item);
                        } else {
                            File file = null;
                            String folderName = null;
                            Preconditions.checkState(item.getSource() != null);
                            file = item.getFile();
                            folderName = getFolderName(item);
//                            if (ResourceItem.isTouched()) {
                            String tag = folderName + "/" + file.getName();

                            if (type == DataFile.FileType.GENERATED_FILES) {
                                if (!map.containsKey(tag)) {
                                    map.put(tag, file);
                                } else if (!map.get(tag).equals(file)) {
                                    if (!isSameBundle(map.get(tag), file, atlasDependencyTree) && allInMainBundle(getId(map.get(tag)), getId(file), atlasDependencyTree)
                                            && !isSameFile(map.get(tag), file))
                                        checkResourceNeedUnique(duplicateResList, tag, map.get(tag), file);
                                    Label label = new Label(0, atomicInteger.get(), tag);
                                    Label label1 = new Label(1, atomicInteger.get(), getId(map.get(tag)));
                                    Label label2 = new Label(2, atomicInteger.get(), getId(file));
                                    atomicInteger.getAndIncrement();
                                    try {
                                        sheet.addCell(label);
                                        sheet.addCell(label1);
                                        sheet.addCell(label2);

                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                }
//                                    try {
//                                        MergedResourceWriter.FileGenerationParameters workItem = new MergedResourceWriter.FileGenerationParameters(item, this.mPreprocessor);
//                                        if (workItem.resourceItem.getSource() != null) {
//                                            this.getExecutor().submit(workItem);
//                                        }
//                                    } catch (Exception var6) {
//                                        throw new ConsumerException(var6, ((ResourceFile)item.getSource()).getFile());
//                                    }
                            } else if (type == DataFile.FileType.SINGLE_FILE) {
                                if (!map.containsKey(tag)) {
                                    map.put(tag, file);
                                } else if (!isSameBundle(map.get(tag), file, atlasDependencyTree)
                                        && allInMainBundle(getId(map.get(tag)), getId(file), atlasDependencyTree)
                                        && !isSameFile(map.get(tag), file)) {
                                    checkResourceNeedUnique(duplicateResList, tag, map.get(tag), file);
                                    Label label = new Label(0, atomicInteger.get(), tag);
                                    Label label1 = new Label(1, atomicInteger.get(), getId(map.get(tag)));
                                    Label label2 = new Label(2, atomicInteger.get(), getId(file));
                                    atomicInteger.getAndIncrement();
                                    try {
                                        sheet.addCell(label);
                                        sheet.addCell(label1);
                                        sheet.addCell(label2);

                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                }
                            }

//                                this.mCompileResourceRequests.add(new CompileResourceRequest(file, this.getRootFolder(), folderName));
//                            }
                        }
                        return false;
                    }


                }, false);
            } catch (MergingException e) {
                e.printStackTrace();
            }

            mValuesResMap.asMap().values().forEach(resourceItems -> {
                for (ResourceMergerItem resourceItem : resourceItems) {
                    String tag = null;
                    if (resourceItem.getSource() == null) {
                        tag = resourceItem.getQualifiers() + ":" + resourceItem.getType().getName() + ":" + resourceItem.getName();

                    } else {
                        tag = resourceItem.getQualifiers() + ":" + resourceItem.getKey();
                    }
                    if (!valuesMap.containsKey(tag)) {
                        String value = getOtherString(resourceItem);
                        if (resourceItem.getSource() == null || resourceItem.getFile() == null) {
                            valuesMap.put(tag, new Pair<String, File>(value, new File("aa")));
                        } else {
                            valuesMap.put(tag, new Pair<String, File>(value, resourceItem.getFile()));

                        }
                    } else {
                        if (resourceItem.getFile() != null && valuesMap.get(tag) != null) {
                            if (!valuesMap.get(tag).equals(resourceItem.getFile()) && !isSameValue(resourceItem, valuesMap.get(tag).getKey()))
                                if (!tag.equals(":string/app_name")) {
                                    checkResourceNeedUnique(duplicateResList, tag, map.get(tag), resourceItem.getFile());
                                    Label label = new Label(0, atomicInteger.get(), tag);
                                    Label label1 = new Label(1, atomicInteger.get(), valuesMap.get(tag).getKey());
                                    Label label2 = new Label(2, atomicInteger.get(), getOtherString(resourceItem));
                                    Label label3 = new Label(3, atomicInteger.get(), getId(valuesMap.get(tag).getValue()));
                                    Label label4 = new Label(4, atomicInteger.get(), getId(resourceItem.getFile()));

                                    atomicInteger.getAndIncrement();
                                    try {
                                        sheet.addCell(label);
                                        sheet.addCell(label1);
                                        sheet.addCell(label2);
                                        sheet.addCell(label3);
                                        sheet.addCell(label4);


                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                }
                        }
                    }
                }

            });


            try {
                writableWorkbook.write();
                writableWorkbook.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        FileUtils.write(new File(appVariantContext.getProject().getBuildDir(),"res.json"), JSON.toJSONString(resFiles));

    }

    private void findDuplicateResource(WritableSheet sheet, ArtifactCollection assets, List<String> duplicateResList) {
        Map<Pair<String, File>, String> map1 = new HashMap<>();
        AtomicInteger atomicInteger1 = new AtomicInteger();
        for (File file : assets.getArtifactFiles().getFiles()) {
            Collection<File> files = FileUtils.listFiles(file, null, true);
            for (File file1 : files) {
                boolean e = false;
                for (Pair stringFilePair : map1.keySet()) {
                    if (stringFilePair.getKey().equals(file1.getAbsolutePath().substring(file1.getAbsolutePath().indexOf("assets"))) && !isSameFile((File) stringFilePair.getValue(), file1) && !map1.get(stringFilePair).equals(file.getAbsolutePath())) {
                        checkAssetNeedUnique(duplicateResList, file1, file1, (File) stringFilePair.getValue());
                        Label label = new Label(0, atomicInteger1.get(), file1.getName());
                        Label label1 = new Label(1, atomicInteger1.get(), map1.get(stringFilePair));
                        Label label2 = new Label(2, atomicInteger1.get(), file.getAbsolutePath());
                        try {
                            sheet.addCell(label);
                            sheet.addCell(label1);
                            sheet.addCell(label2);
                        } catch (WriteException ex) {
                            ex.printStackTrace();
                        }


                        atomicInteger1.getAndIncrement();
                        e = true;
                        break;
                    }
                }

                if (!e) {

                    map1.put(new Pair<>(file1.getAbsolutePath().substring(file1.getAbsolutePath().indexOf("assets")), file1), file.getAbsolutePath());
                }


            }
        }
    }

    public static class ConfigActon extends MtlBaseTaskAction<ScanDupResTask> {

        private AppVariantContext variantContext;

        public ConfigActon(VariantContext variantContext, BaseVariantOutput baseVariantOutput) {
            super(variantContext, baseVariantOutput);
            this.variantContext = (AppVariantContext) variantContext;
        }

        @Override
        public String getName() {
            return scope.getTaskName("scan", "DupRes");
        }

        @Override
        public Class<ScanDupResTask> getType() {
            return ScanDupResTask.class;
        }

        @Override
        public void configure(ScanDupResTask scanDupResTask) {
            super.configure(scanDupResTask);
            if (!variantContext.getAtlasExtension().getTBuildConfig().getScanDupRes()) {
                scanDupResTask.setEnabled(false);
                return;
            }
            scanDupResTask.appVariantContext = variantContext;
            scanDupResTask.appVariantOutputContext = getAppVariantOutputContext();
        }
    }

    private boolean isSameBundle(File file, File file1, AtlasDependencyTree atlasDependencyTree) {

        return false;
//        String id1 = getId(file);
//        String id2 = getId(file1);
//
//        return isSameBundle(id1,id2,atlasDependencyTree);


    }

    private String getId(File file) {
        AtomicReference<String> dep = new AtomicReference<>();
        depsMap.keySet().forEach(s -> {
            if (file.getAbsolutePath().startsWith(s)) {
                dep.set(depsMap.get(s));
            }
        });
        return dep.get();
    }


    private static String getFolderName(ResourceMergerItem resourceItem) {
        ResourceType itemType = resourceItem.getType();
        String folderName = itemType.getName();
        String qualifiers = resourceItem.getQualifiers();
        if (!qualifiers.isEmpty()) {
            folderName = folderName + "-" + qualifiers;
        }

        return folderName;
    }


    private boolean allInMainBundle(String id1, String id2, AtlasDependencyTree atlasDependencyTree) {

        return true;

    }


    private boolean isSameFile(File file1, File file2) {

        return MD5Util.getFileMD5(file1).equals(MD5Util.getFileMD5(file2));
    }

    private boolean isSameValue(ResourceItem resourceItem, String value) {

        return getOtherString((ResourceMergerItem) resourceItem).equals(value);
    }

    private String getOtherString(ResourceMergerItem resourceItem) {

        if (resourceItem.getValue().getFirstChild() != null && hasValues(resourceItem)) {
            return resourceItem.getValue().getFirstChild().toString();
        }
        Field field = null;
        try {
            field = NamedNodeMapImpl.class.getDeclaredField("nodes");
            ((Field) field).setAccessible(true);
            return field.get(resourceItem.getValue().getAttributes()).toString();
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        return "";

    }

    private boolean hasValues(ResourceItem resourceItem) {
        return resourceItem.getType().getName().equals("bool")
                || resourceItem.getType().getName().equals("color")
                || resourceItem.getType().getName().equals("id")
                || resourceItem.getType().getName().equals("integer")
                || resourceItem.getType().getName().equals("string")
                || resourceItem.getType().getName().equals("public");
    }

    private void checkAssetNeedUnique(List<String> uniqueResList,
                                      File assetFile,
                                      File bundle1,
                                      File bundle2) {
        if (uniqueResList == null || assetFile == null || bundle1 == null || bundle2 == null || uniqueResList.size() == 0) {
            return;
        }
        String assetAbsolutePath = assetFile.getAbsolutePath().substring(assetFile.getAbsolutePath().indexOf("assets"));
        if (!uniqueResList.contains(assetAbsolutePath)) {
            throw new RuntimeException("Assets does not allow duplicates: " + assetAbsolutePath
                    + "\n" + "bundles which have duplicate resource: "
                    + bundle1.getAbsolutePath() + " and " + bundle2.getAbsolutePath());
        }
    }

    private void checkResourceNeedUnique(List<String> uniqueResList, String resFilePath, File bundle1, File bundle2) {
        if (uniqueResList == null || resFilePath == null || uniqueResList.size() == 0) {
            return;
        }
        if (!uniqueResList.contains(resFilePath)) {
            throw new RuntimeException("Resource does not allow duplicates: " + resFilePath
                    + "\n" + "bundles which have duplicate resource: "
                    + bundle1.getAbsolutePath() + " and " + bundle2.getAbsolutePath());
        }
    }

}
