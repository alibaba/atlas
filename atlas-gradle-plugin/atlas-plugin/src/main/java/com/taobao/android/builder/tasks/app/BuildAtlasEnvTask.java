package com.taobao.android.builder.tasks.app;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.gradle.api.BaseVariantOutput;
import com.android.build.gradle.internal.TaskContainerAdaptor;
import com.android.build.gradle.internal.api.AppVariantContext;
import com.android.build.gradle.internal.api.AppVariantOutputContext;
import com.android.build.gradle.internal.api.AwbTransform;
import com.android.build.gradle.internal.ide.AtlasAndroidLibraryImpl;
import com.android.build.gradle.internal.ide.AtlasDependencyGraph;
import com.android.build.gradle.internal.pipeline.ExtendedContentType;
import com.android.build.gradle.internal.pipeline.OriginalStream;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.publishing.AndroidArtifacts;
import com.android.build.gradle.internal.publishing.AtlasAndroidArtifacts;
import com.android.build.gradle.internal.tasks.BaseTask;
import com.android.build.gradle.tasks.MergeResources;
import com.android.build.gradle.tasks.MergeSourceSetFolders;
import com.android.build.gradle.tasks.ProcessAndroidResources;
import com.android.builder.dependency.level2.AndroidDependency;
import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.JavaLibrary;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Maps;

import com.taobao.android.builder.AtlasBuildContext;
import com.taobao.android.builder.dependency.AtlasDependencyTree;
import com.taobao.android.builder.dependency.model.AwbBundle;
import com.taobao.android.builder.dependency.model.SoLibrary;
import com.taobao.android.builder.tasks.app.merge.MainDexArtifactCollection;
import com.taobao.android.builder.tasks.manager.MtlBaseTaskAction;
import com.taobao.android.builder.tools.ReflectUtils;
import org.apache.commons.io.FileUtils;
import org.gradle.api.Action;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.file.FileCollection;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier;
import org.gradle.internal.component.local.model.DefaultProjectComponentIdentifier;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.*;
import java.util.function.Consumer;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ARTIFACT_TYPE;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.ALL;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.*;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH;

/**
 * @author lilong
 * @create 2017-12-02 上午7:34
 */

public class BuildAtlasEnvTask extends BaseTask {


    private  AppVariantOutputContext appVariantOutputContext;

    private ArtifactCollection compileManifests;

    public static Map<String, File> allManifests = new HashMap<>();

    public static Map<String, File> allSolibs = new HashMap<>();

    public static Map<String, File> allJavaRes = new HashMap<>();

    public static Set<FileIdentity>allJars = new HashSet<>();


    private AppVariantContext appVariantContext;

    private ArtifactCollection compileJars;

    private ArtifactCollection nativeLibs;

    private ArtifactCollection javaResources;
    private ArtifactCollection nativeLibs2;


    @TaskAction
    void generate() {

        Set<ResolvedArtifactResult> compileArtifacts = compileManifests.getArtifacts();
        Set<ResolvedArtifactResult> jarArtifacts = compileJars.getArtifacts();
        Set<ResolvedArtifactResult> nativeLibsArtifacts = nativeLibs.getArtifacts();
        Set<ResolvedArtifactResult> javaResourcesArtifacts = javaResources.getArtifacts();

        //this is no used ,if used in future add to transform!

        Set<ResolvedArtifactResult> nativeLibsArtifacts2 = nativeLibs2.getArtifacts();

        nativeLibsArtifacts.addAll(nativeLibsArtifacts2);

        AtlasBuildContext.localLibs = nativeLibs2.getArtifactFiles().getFiles();


        for (ResolvedArtifactResult resolvedArtifactResult: jarArtifacts){
            ComponentIdentifier componentIdentifier = resolvedArtifactResult.getId().getComponentIdentifier();
            if (componentIdentifier instanceof DefaultModuleComponentIdentifier){
                allJars.add(new FileIdentity(((DefaultModuleComponentIdentifier) componentIdentifier).getGroup()+":"+((DefaultModuleComponentIdentifier) componentIdentifier).getModule(),resolvedArtifactResult.getFile(),resolvedArtifactResult.getId().getDisplayName().startsWith("classes.jar")? false:true,false));
            }else if (componentIdentifier instanceof DefaultProjectComponentIdentifier){
                allJars.add(new FileIdentity(componentIdentifier.getDisplayName().split(":")[1],resolvedArtifactResult.getFile(),resolvedArtifactResult.getId().getDisplayName().startsWith("classes.jar")? false:true,true));
            }
        }
        for (ResolvedArtifactResult resolvedArtifactResult : compileArtifacts) {
            if (resolvedArtifactResult.getId().getComponentIdentifier() instanceof DefaultModuleComponentIdentifier) {
                allManifests.put(((DefaultModuleComponentIdentifier) resolvedArtifactResult.getId().getComponentIdentifier()).getGroup()+":"+((DefaultModuleComponentIdentifier) resolvedArtifactResult.getId().getComponentIdentifier()).getModule(), resolvedArtifactResult.getFile());
            } else if (resolvedArtifactResult.getId().getComponentIdentifier() instanceof DefaultProjectComponentIdentifier) {
                allManifests.put(((DefaultProjectComponentIdentifier) resolvedArtifactResult.getId().getComponentIdentifier()).getProjectPath().substring(1), resolvedArtifactResult.getFile());
            }
        }

        for (ResolvedArtifactResult resolvedArtifactResult : nativeLibsArtifacts) {
            if (resolvedArtifactResult.getId().getComponentIdentifier() instanceof DefaultModuleComponentIdentifier) {
                allSolibs.put(((DefaultModuleComponentIdentifier) resolvedArtifactResult.getId().getComponentIdentifier()).getGroup()+":"+((DefaultModuleComponentIdentifier) resolvedArtifactResult.getId().getComponentIdentifier()).getModule(), resolvedArtifactResult.getFile());
            } else if (resolvedArtifactResult.getId().getComponentIdentifier() instanceof DefaultProjectComponentIdentifier) {
                allSolibs.put(((DefaultProjectComponentIdentifier) resolvedArtifactResult.getId().getComponentIdentifier()).getProjectPath().substring(1), resolvedArtifactResult.getFile());
            }
        }

        for (ResolvedArtifactResult resolvedArtifactResult : javaResourcesArtifacts) {
            if (resolvedArtifactResult.getId().getComponentIdentifier() instanceof DefaultModuleComponentIdentifier) {
                allJavaRes.put(((DefaultModuleComponentIdentifier) resolvedArtifactResult.getId().getComponentIdentifier()).getGroup()+":"+((DefaultModuleComponentIdentifier) resolvedArtifactResult.getId().getComponentIdentifier()).getModule(), resolvedArtifactResult.getFile());
            } else if (resolvedArtifactResult.getId().getComponentIdentifier() instanceof DefaultProjectComponentIdentifier) {
                allJavaRes.put(((DefaultProjectComponentIdentifier) resolvedArtifactResult.getId().getComponentIdentifier()).getProjectPath().substring(1), resolvedArtifactResult.getFile());
            }
        }

        AtlasDependencyTree atlasDependencyTree = AtlasBuildContext.androidDependencyTrees.get(getVariantName());
        List<AndroidLibrary> androidLibraries = atlasDependencyTree.getAllAndroidLibrarys();


        androidLibraries.stream().forEach(androidLibrary -> {
            if (androidLibrary instanceof AtlasAndroidLibraryImpl) {
                AndroidDependency fakeAndroidLibrary = ((AtlasAndroidLibraryImpl) androidLibrary).getAndroidLibrary();
                File id = null;

                if ((id = allManifests.get(androidLibrary.getResolvedCoordinates().getGroupId()+":"+androidLibrary.getResolvedCoordinates().getArtifactId())) == null) {
                    id = allManifests.get(androidLibrary.getResolvedCoordinates().toString().split(":")[1]);
                }
                ReflectUtils.updateField(fakeAndroidLibrary, "extractedFolder", id.getParentFile());
                ReflectUtils.updateField(fakeAndroidLibrary, "jarsRootFolder", id.getParentFile());
                ((AtlasAndroidLibraryImpl) androidLibrary).setAndroidLibrary(AndroidDependency.createExplodedAarLibrary(null, androidLibrary.getResolvedCoordinates(), androidLibrary.getName(), ((AtlasAndroidLibraryImpl) androidLibrary).getPath(), id.getParentFile()));
                appVariantContext.manifestMap.put(androidLibrary.getManifest().getAbsolutePath(),
                        appVariantContext.getModifyManifest(androidLibrary));
            }
        });

        List<AndroidLibrary> mainDexAndroidLibraries = atlasDependencyTree.getMainBundle().getAndroidLibraries();
        List<JavaLibrary>mainDexJarLibraries = atlasDependencyTree.getMainBundle().getJavaLibraries();
        List<SoLibrary>mainSoLibraries = atlasDependencyTree.getMainBundle().getSoLibraries();

        for (AndroidLibrary androidLibrary : mainDexAndroidLibraries) {
            String name = androidLibrary.getResolvedCoordinates().getGroupId()+":"+androidLibrary.getResolvedCoordinates().getArtifactId();
            String moudleName = androidLibrary.getResolvedCoordinates().toString().split(":")[1];
            fillMainManifest(name,moudleName);
            fillMainJar(name,moudleName);
            fillAllJavaRes(name,moudleName);
            fillMainSolibs(name,moudleName);
        }

        for (JavaLibrary jarLibrary:mainDexJarLibraries){
            String moudleName = jarLibrary.getName().split(":")[1];
            String name = jarLibrary.getResolvedCoordinates().getGroupId()+":"+jarLibrary.getResolvedCoordinates().getArtifactId();
            fillMainJar(name,moudleName);
        }

        for (SoLibrary soLibrary:mainSoLibraries){
            String name = soLibrary.getResolvedCoordinates().getGroupId() + ":" + soLibrary.getResolvedCoordinates().getArtifactId();
            String moudleName = soLibrary.getResolvedCoordinates().toString().split(":")[1];
            fillMainSolibs(name,moudleName);
        }


        for (AwbBundle awbBundle:atlasDependencyTree.getAwbBundles()){
            List<AndroidLibrary> awbAndroidLibraries = awbBundle.getAndroidLibraries();
            List<JavaLibrary>awbJarLibraries = awbBundle.getJavaLibraries();
            List<SoLibrary>awbSoLibraries = awbBundle.getSoLibraries();

            for (AndroidLibrary androidLibrary:awbAndroidLibraries) {
                String name = androidLibrary.getResolvedCoordinates().getGroupId() + ":" + androidLibrary.getResolvedCoordinates().getArtifactId();
                String moudleName = androidLibrary.getResolvedCoordinates().toString().split(":")[1];
                fillAwbManifest(name, moudleName,awbBundle);
                fillAwbJar(name, moudleName,awbBundle);
                fillAwbAllJavaRes(name, moudleName,awbBundle);
                fillAwbSolibs(name, moudleName,awbBundle);
            }
            for (JavaLibrary jarLibrary:awbJarLibraries){
                String moudleName = jarLibrary.getName().split(":")[1];
                String name = jarLibrary.getResolvedCoordinates().getGroupId()+":"+jarLibrary.getResolvedCoordinates().getArtifactId();
                fillAwbJar(name,moudleName,awbBundle);
            }
            for (SoLibrary soLibrary:awbSoLibraries){
                String name = soLibrary.getResolvedCoordinates().getGroupId() + ":" + soLibrary.getResolvedCoordinates().getArtifactId();
                String moudleName = soLibrary.getResolvedCoordinates().toString().split(":")[1];
                fillAwbSolibs(name,moudleName,awbBundle);
            }
            String name = awbBundle.getResolvedCoordinates().getGroupId() + ":" + awbBundle.getResolvedCoordinates().getArtifactId();
            String moudleName = awbBundle.getResolvedCoordinates().toString().split(":")[1];
            fillAwbManifest(name, moudleName,awbBundle);
            fillAwbJar(name, moudleName,awbBundle);
            fillAwbAllJavaRes(name, moudleName,awbBundle);
            fillAwbSolibs(name, moudleName,awbBundle);
        }




        AtlasBuildContext.atlasMainDexHelper.addAwbDexJars(allJars);


        MergeResources mergeResources = appVariantContext.getScope().getMergeResourcesTask().get(new TaskContainerAdaptor(getProject().getTasks()));

        try {
            //mergeresources
            Field field = MergeResources.class.getDeclaredField("libraries");
            field.setAccessible(true);
            field.set(mergeResources, new MainDexArtifactCollection((ArtifactCollection) field.get(mergeResources), getProject()));

            //mergeSourcesSets
            MergeSourceSetFolders mergeSourceSetFolders = appVariantContext.getScope().getMergeAssetsTask().get(new TaskContainerAdaptor(getProject().getTasks()));
            Field field1 = MergeSourceSetFolders.class.getDeclaredField("libraries");
            field1.setAccessible(true);
            field1.set(mergeSourceSetFolders, new MainDexArtifactCollection((ArtifactCollection) field1.get(mergeSourceSetFolders), getProject()));
        } catch (Exception e) {

        }


        //process resources
        ProcessAndroidResources processAndroidResources = appVariantContext.getScope().getProcessResourcesTask().get(new TaskContainerAdaptor(appVariantContext.getProject().getTasks()));
        FileCollection fileCollection = processAndroidResources.getSymbolListsWithPackageNames();
        Set<String>filesNames = new HashSet<>();
        for (String fileName:AtlasBuildContext.atlasMainDexHelper.getMainManifestFiles().keySet()){
            filesNames.add(fileName.substring(fileName.lastIndexOf("/")+1));
        }
        FileCollection updateFileCollection = fileCollection.filter(element -> filesNames.contains(element.getParentFile().getParentFile().getName()));
        ReflectUtils.updateField(processAndroidResources,"symbolListsWithPackageNames",updateFileCollection);
        appVariantContext.processResAwbsTask.mainDexSymbolFileCollection = updateFileCollection;




//
//        try {
//            duplicateClazzNote();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }

    }

    private void fillAwbJar(String name, String moudleName, AwbBundle awbBundle) {
        AwbTransform awbTransform = appVariantOutputContext.getAwbTransformMap().get(awbBundle.getName());


    }

    private void fillAwbManifest(String name, String moudleName, AwbBundle awbBundle) {

    }

    private void fillAwbSolibs(String name,String moudleName,AwbBundle awbBundle){
        AwbTransform awbTransform = appVariantOutputContext.getAwbTransformMap().get(awbBundle.getName());
        File id = null;
        if ((id = allSolibs.get(name)) == null) {
            id = allSolibs.get(moudleName);
        }
        if (id!= null) {
            awbTransform.getLibraryJniLibsInputDir().add(id);
        }
        }

    private void fillAwbAllJavaRes(String name,String moudleName,AwbBundle awbBundle){
        AwbTransform awbTransform = appVariantOutputContext.getAwbTransformMap().get(awbBundle.getName());
        File id = null;
        if ((id = allJavaRes.get(name)) == null) {
            id = allJavaRes.get(moudleName);
        }
        if (id!= null) {
            awbTransform.getLibraryResourcesInutDir().add(id);
        }
    }



    private void duplicateClazzNote() throws IOException {
        File outPutFile = new File(appVariantContext.getScope().getGlobalScope().getOutputsDir(),"duplicate-class.txt");
        Set<String>warnList = new HashSet<>();
        Set<File>jarFiles = compileJars.getArtifactFiles().getFiles();
        Map<String, String> jarClassNames = Maps.newHashMap();
        for (File jarFile:jarFiles){
            JarFile jar = new JarFile(jarFile);
            Enumeration<JarEntry>entryEnumeration = jar.entries();
            while (entryEnumeration.hasMoreElements()){
                JarEntry jarEntry = entryEnumeration.nextElement();
                String className = jarEntry.getName();
                if (className.endsWith(".class")){
                   if (jarClassNames.containsKey(className)){
                       warnList.add(String.format("duplicate class %s in %s and %s",className,jarClassNames.get(className),jarFile.getAbsolutePath()));
                        getLogger().warn(String.format("duplicate class %s in %s and %s",className,jarClassNames.get(className),jarFile.getAbsolutePath()));
                   }else {
                       jarClassNames.put(className,jarFile.getAbsolutePath());
                   }
                }
            }
        }

        if (!outPutFile.getParentFile().exists()){
            outPutFile.getParentFile().mkdirs();
            FileUtils.writeLines(outPutFile,warnList,true);
        }

    }

    private void fillMainManifest(String name, String moudleName) {
        File id = null;
        if ((id = allManifests.get(name)) == null) {
            id = allManifests.get(moudleName);
        }
        if (id!= null) {
            AtlasBuildContext.atlasMainDexHelper.getMainManifestFiles().put(id.getParentFile().getAbsolutePath(), true);
        }
    }

    private void fillMainSolibs(String name, String moudleName) {
        File id = null;
        if ((id = allSolibs.get(name)) == null) {
            id = allSolibs.get(moudleName);
        }
        if (id!= null) {
            AtlasBuildContext.atlasMainDexHelper.getMainSoFiles().put(id.getAbsolutePath(), true);
        }
    }

    private void fillAllJavaRes(String name, String moudleName) {
        File id = null;
        if ((id = allJavaRes.get(name)) == null) {
            id = allJavaRes.get(moudleName);
        }
        if (id!= null) {
            AtlasBuildContext.atlasMainDexHelper.getMainResFiles().put(id.getAbsolutePath(), true);
        }
    }




    private void fillMainJar(String name, String moudleName) {
        Iterator<FileIdentity>identities = allJars.iterator();
        while (identities.hasNext()){
            FileIdentity fileIdentity = identities.next();
            if (fileIdentity.name.equals(name)||fileIdentity.name.equals(moudleName)){
                AtlasBuildContext.atlasMainDexHelper.getMainDexFiles().add(fileIdentity);
                identities.remove();
            }
        }
    }


    public static class ConfigAction extends MtlBaseTaskAction<BuildAtlasEnvTask> {

        private AppVariantContext appVariantContext;

        public ConfigAction(AppVariantContext appVariantContext,
                            BaseVariantOutput baseVariantOutputData) {
            super(appVariantContext, baseVariantOutputData);
            this.appVariantContext = appVariantContext;
        }

        @Override
        public String getName() {
            return scope.getTaskName("build", "atlasEnv");
        }

        @Override
        public Class<BuildAtlasEnvTask> getType() {
            return BuildAtlasEnvTask.class;
        }

        @Override
        public void execute(BuildAtlasEnvTask updateDependenciesTask) {
            super.execute(updateDependenciesTask);
            updateDependenciesTask.appVariantContext = this.appVariantContext;
            updateDependenciesTask.compileManifests =
                    appVariantContext.getScope().getArtifactCollection(COMPILE_CLASSPATH, ALL, MANIFEST);
            updateDependenciesTask.compileJars =
                    appVariantContext.getScope().getArtifactCollection(COMPILE_CLASSPATH, ALL, CLASSES);

            updateDependenciesTask.appVariantOutputContext = getAppVariantOutputContext();
            updateDependenciesTask.nativeLibs = appVariantContext.getScope().getArtifactCollection(COMPILE_CLASSPATH,ALL,JNI);

            updateDependenciesTask.nativeLibs2 = AtlasDependencyGraph.computeArtifactCollection(variantContext.getScope(), AtlasAndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH,ALL,AtlasAndroidArtifacts.AtlasArtifactType.LIBS);

            updateDependenciesTask.javaResources = appVariantContext.getScope().getArtifactCollection(COMPILE_CLASSPATH,ALL,JAVA_RES);

            List<ProjectDependency>projectDependencies = new ArrayList<>();
            appVariantContext.getScope().getVariantDependencies().getCompileClasspath().getAllDependencies().forEach(dependency -> {
                if (dependency instanceof ProjectDependency){
                    projectDependencies.add((ProjectDependency) dependency);
//                        ((ProjectDependency) dependency).getDependencyProject().getConfigurations().getByName("compile").
//                                getIncoming()
//                                .artifactView(
//                                        config -> {
//                                            config.attributes(attributes);
//                                            if (filter != null) {
//                                                config.componentFilter(filter);
//                                            }
//                                            // TODO somehow read the unresolved dependencies?
//                                            config.lenient(lenientMode);
//                                        })
//                                .getArtifacts();
                }
            });

        }
    }

    public static class FileIdentity{
        public String name;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof FileIdentity)) return false;

            FileIdentity that = (FileIdentity) o;

            if (localJar != that.localJar) return false;
            if (subProject != that.subProject) return false;
            if (name != null ? !name.equals(that.name) : that.name != null) return false;
            return file != null ? file.equals(that.file) : that.file == null;
        }

        @Override
        public int hashCode() {
            int result = name != null ? name.hashCode() : 0;
            result = 31 * result + (file != null ? file.hashCode() : 0);
            result = 31 * result + (localJar ? 1 : 0);
            result = 31 * result + (subProject ? 1 : 0);
            return result;
        }

        public File file;
        public boolean localJar;
        public boolean subProject;

        public FileIdentity(String name, File file,boolean localJar,boolean subProject) {
            this.name = name;
            this.file = file;
            this.localJar = localJar;
            this.subProject = subProject;
        }
    }

    @Nullable
    private static Spec<ComponentIdentifier> getComponentFilter(
            @NonNull AndroidArtifacts.ArtifactScope scope) {
        switch (scope) {
            case ALL:
                return null;
            case EXTERNAL:
                // since we want both Module dependencies and file based dependencies in this case
                // the best thing to do is search for non ProjectComponentIdentifier.
                return id -> !(id instanceof ProjectComponentIdentifier);
            case MODULE:
                return id -> id instanceof ProjectComponentIdentifier;
            default:
                throw new RuntimeException("unknown ArtifactScope value");
        }
    }
}
