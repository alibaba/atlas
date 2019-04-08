package com.taobao.android.builder.tasks.app;

import com.android.build.gradle.api.BaseVariantOutput;
import com.android.build.gradle.internal.api.AppVariantContext;
import com.android.build.gradle.internal.api.AppVariantOutputContext;
import com.android.build.gradle.internal.api.VariantContext;
import com.android.build.gradle.internal.publishing.AndroidArtifacts;
import com.android.build.gradle.internal.tasks.BaseTask;
import com.android.build.gradle.tasks.MergeManifests;
import com.android.build.gradle.tasks.ResourceException;
import com.android.builder.core.BuilderConstants;
import com.android.builder.model.AndroidLibrary;
import com.android.ide.common.res2.*;
import com.android.resources.ResourceType;
import com.google.common.base.Preconditions;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.sun.org.apache.xerces.internal.dom.NamedNodeMapImpl;
import com.taobao.android.builder.AtlasBuildContext;
import com.taobao.android.builder.dependency.AtlasDependencyTree;
import com.taobao.android.builder.dependency.model.AwbBundle;
import com.taobao.android.builder.tasks.manager.MtlBaseTaskAction;
import com.taobao.android.builder.tools.MD5Util;
import javafx.util.Pair;
import org.apache.commons.io.FileUtils;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.tasks.TaskAction;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.*;
import java.util.function.Consumer;

/**
 * ScanDupResTask
 *
 * @author zhayu.ll
 * @date 18/8/10
 */
public class ScanDupResTask extends BaseTask {

    private AppVariantOutputContext appVariantOutputContext;

    private AppVariantContext appVariantContext;

    @TaskAction
    void generate() {


        AtlasDependencyTree atlasDependencyTree = AtlasBuildContext.androidDependencyTrees.get(
                getVariantName());
        if (null == atlasDependencyTree) {
            return;
        }
        File dupResFile = new File(appVariantContext.getProject().getBuildDir(),"outputs/warning-dup-res.properties");
        File dupAssetsFile = new File(appVariantContext.getProject().getBuildDir(),"outputs/warning-dup-assets.properties");

        ArtifactCollection res = appVariantContext.getScope().getArtifactCollection(AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH,AndroidArtifacts.ArtifactScope.ALL,AndroidArtifacts.ArtifactType.ANDROID_RES);
        ArtifactCollection assets = appVariantContext.getScope().getArtifactCollection(AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH,AndroidArtifacts.ArtifactScope.ALL,AndroidArtifacts.ArtifactType.ASSETS);

        Set<String> errors = new HashSet<>();

        Map<Pair<String,File>,String>map1 = new HashMap<>();
        for (File file :assets.getArtifactFiles().getFiles()){
            Collection<File> files = FileUtils.listFiles(file,null,true);
           for (File file1:files){
               boolean e = false;
               for (Pair stringFilePair:map1.keySet()){
                   if (stringFilePair.getKey().equals(file1.getAbsolutePath().substring(file1.getAbsolutePath().indexOf("assets"))) && !isSameFile((File) stringFilePair.getValue(),file1)&&!map1.get(stringFilePair).equals(file.getAbsolutePath())){
                       errors.add("dup assets:"+file1.getName() +" in "+map1.get(stringFilePair) + " and "+file.getAbsolutePath());
                       e = true;
                       break;
                   }
               }

               if (!e){

                       map1.put(new Pair<>(file1.getAbsolutePath().substring(file1.getAbsolutePath().indexOf("assets")),file1),file.getAbsolutePath());
               }


           }
        }
        try {
            FileUtils.writeLines(dupAssetsFile,errors);
        } catch (IOException e) {
            e.printStackTrace();
        }

        Map<String,File> map = new HashMap<>();
        Map<String,Pair<String,File>>valuesMap = new HashMap<>();
        List<String> exceptions = new ArrayList<>();

        ResourceMerger resourceMerger = new ResourceMerger(14);
        if (res != null) {
            Set<ResolvedArtifactResult> libArtifacts = res.getArtifacts();
            List<ResourceSet> resourceSetList = Lists.newArrayListWithExpectedSize(libArtifacts.size());
            // the order of the artifact is descending order, so we need to reverse it.
            for (ResolvedArtifactResult artifact : libArtifacts) {
                ResourceSet resourceSet =
                        new ResourceSet(
                                MergeManifests.getArtifactName(artifact),
                                null,
                                null,
                                true);
                resourceSet.setFromDependency(true);
                resourceSet.addSource(artifact.getFile());

                // add to 0 always, since we need to reverse the order.
                resourceSetList.add(0,resourceSet);
            }
            resourceSetList.forEach(resourceSet -> {
                try {
                    resourceSet.loadFromFiles(getILogger());
                } catch (MergingException e) {
                    e.printStackTrace();
                }
                resourceMerger.addDataSet(resourceSet);
            });

            ListMultimap<String, ResourceItem> mValuesResMap = ArrayListMultimap.create();

            try {
                resourceMerger.mergeData(new MergeConsumer<ResourceItem>() {
                    @Override
                    public void start(DocumentBuilderFactory documentBuilderFactory) throws ConsumerException {

                    }

                    @Override
                    public void end() throws ConsumerException {

                    }

                    @Override
                    public void addItem(ResourceItem item) throws ConsumerException {
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
                            String tag= folderName+"/"+file.getName();

                            if (type == DataFile.FileType.GENERATED_FILES) {
                                if (!map.containsKey(tag)){
                                    map.put(tag,file);
                                }else if (!map.get(tag).equals(file)){
                                    if (!isSameBundle(map.get(tag),file,atlasDependencyTree)
                                            && allInMainBundle(getId(map.get(tag)),getId(file),atlasDependencyTree)
                                            && !isSameFile(map.get(tag),file))
                                        exceptions.add("dup File:"+tag+"|"+getId(map.get(tag))+"|"+getId(file));
                                }
//                                    try {
//                                        MergedResourceWriter.FileGenerationParameters workItem = new MergedResourceWriter.FileGenerationParameters(item, this.mPreprocessor);
//                                        if (workItem.resourceItem.getSource() != null) {
//                                            this.getExecutor().submit(workItem);
//                                        }
//                                    } catch (Exception var6) {
//                                        throw new ConsumerException(var6, ((ResourceFile)item.getSource()).getFile());
//                                    }
                            }else if (type == DataFile.FileType.SINGLE_FILE){
                                if (!map.containsKey(tag)){
                                    map.put(tag,file);
                                }else if (!map.get(tag).equals(file)){
                                    if (!isSameBundle(map.get(tag),file,atlasDependencyTree)
                                            && allInMainBundle(getId(map.get(tag)),getId(file),atlasDependencyTree)
                                            && !isSameFile(map.get(tag),file))
                                        exceptions.add("dup File:"+tag+"|"+getId(map.get(tag))+"|"+getId(file));
                                }
                            }

//                                this.mCompileResourceRequests.add(new CompileResourceRequest(file, this.getRootFolder(), folderName));
//                            }
                        }
                    }

                    @Override
                    public void removeItem(ResourceItem removedItem, ResourceItem replacedBy) throws ConsumerException {
                        DataFile.FileType removedType = removedItem.getSourceType();
                        DataFile.FileType replacedType = replacedBy != null ? replacedBy.getSourceType() : null;
                        switch(removedType) {
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
                    public boolean ignoreItemInMerge(ResourceItem item) {
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
                            String tag= folderName+"/"+file.getName();

                            if (type == DataFile.FileType.GENERATED_FILES) {
                                if (!map.containsKey(tag)){
                                    map.put(tag,file);
                                }else if (!map.get(tag).equals(file)){
                                    if (!isSameBundle(map.get(tag),file,atlasDependencyTree)&&allInMainBundle(getId(map.get(tag)),getId(file),atlasDependencyTree)
                                            && !isSameFile(map.get(tag),file))
                                        exceptions.add("dup File:"+tag+"|"+getId(map.get(tag))+"|"+getId(file));
                                }
//                                    try {
//                                        MergedResourceWriter.FileGenerationParameters workItem = new MergedResourceWriter.FileGenerationParameters(item, this.mPreprocessor);
//                                        if (workItem.resourceItem.getSource() != null) {
//                                            this.getExecutor().submit(workItem);
//                                        }
//                                    } catch (Exception var6) {
//                                        throw new ConsumerException(var6, ((ResourceFile)item.getSource()).getFile());
//                                    }
                            }else if (type == DataFile.FileType.SINGLE_FILE){
                                if (!map.containsKey(tag)){
                                    map.put(tag,file);
                                    if (!isSameBundle(map.get(tag),file,atlasDependencyTree)
                                            && allInMainBundle(getId(map.get(tag)),getId(file),atlasDependencyTree)
                                            && !isSameFile(map.get(tag),file))
                                        exceptions.add("dup File:"+tag+"|"+getId(map.get(tag))+"|"+getId(file));
                                }
                            }

//                                this.mCompileResourceRequests.add(new CompileResourceRequest(file, this.getRootFolder(), folderName));
//                            }
                        }
                        return false;
                    }
                },false);
            } catch (MergingException e) {
                e.printStackTrace();
            }

            mValuesResMap.asMap().values().forEach(resourceItems -> {
                for (ResourceItem resourceItem:resourceItems){
                    String tag = null;
                    if (resourceItem.getSource() == null){
                        tag = resourceItem.getQualifiers() + ":" +resourceItem.getType().getName()+":"+resourceItem.getName();

                    }else {
                        tag = resourceItem.getQualifiers() + ":" +  resourceItem.getKey();
                    }
                    if (!valuesMap.containsKey(tag)){
                        String value = getOtherString(resourceItem);
                        if (resourceItem.getSource() == null ||resourceItem.getFile() == null) {
                            valuesMap.put(tag, new Pair<String,File>(value,new File("aa")));
                        }else {
                            valuesMap.put(tag, new Pair<String, File>(value,resourceItem.getFile()));

                        }
                    }else {
                        if (resourceItem.getFile()!= null && valuesMap.get(tag)!= null) {
                            if (!valuesMap.get(tag).equals(resourceItem.getFile()) && !isSameValue(resourceItem,valuesMap.get(tag).getKey()))
                                if (!tag.equals(":string/app_name")) {
                                    exceptions.add("dup value " + tag + "|" + valuesMap.get(tag).getKey() + "|" + getOtherString(resourceItem) + "|" + getId(valuesMap.get(tag).getValue()) + "|" + getId(resourceItem.getFile()));
                                }
                                }
                    }
                }

            });

            Collections.sort(exceptions);
            try {
                FileUtils.writeLines(dupResFile,exceptions);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }


    }

    public static class ConfigActon extends MtlBaseTaskAction<ScanDupResTask>{

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
        public void execute(ScanDupResTask scanDupResTask) {
            super.execute(scanDupResTask);
            if (!variantContext.getAtlasExtension().getTBuildConfig().getScanDupRes()){
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

    private String getId(File file){
        String id1= file.getAbsolutePath().substring(file.getAbsolutePath().indexOf("files-1.1")+10);
        id1 = id1.substring(0,id1.indexOf("/"));
        return id1;
    }



    private static String getFolderName(ResourceItem resourceItem) {
        ResourceType itemType = resourceItem.getType();
        String folderName = itemType.getName();
        String qualifiers = resourceItem.getQualifiers();
        if (!qualifiers.isEmpty()) {
            folderName = folderName + "-" + qualifiers;
        }

        return folderName;
    }


    private boolean allInMainBundle(String id1,String id2,AtlasDependencyTree atlasDependencyTree){

        return true;

    }


    private boolean isSameFile(File file1,File file2){
        return MD5Util.getFileMD5(file1).equals(MD5Util.getFileMD5(file2));
    }

    private boolean isSameValue(ResourceItem resourceItem,String value){

       return getOtherString(resourceItem).equals(value);
    }

    private String getOtherString(ResourceItem resourceItem){

        if (resourceItem.getValue().getFirstChild() != null && hasValues(resourceItem)){
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
                ||resourceItem.getType().getName().equals("color")
                ||resourceItem.getType().getName().equals("id")
                ||resourceItem.getType().getName().equals("integer")
                ||resourceItem.getType().getName().equals("string")
                ||resourceItem.getType().getName().equals("public");
    }




}
