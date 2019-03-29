package com.taobao.android.builder.tasks.app;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.transform.TransformException;
import com.android.build.gradle.api.BaseVariantOutput;
import com.android.build.gradle.internal.TaskContainerAdaptor;
import com.android.build.gradle.internal.api.AppVariantContext;
import com.android.build.gradle.internal.api.AppVariantOutputContext;
import com.android.build.gradle.internal.api.AwbTransform;
import com.android.build.gradle.internal.ide.AtlasAndroidLibraryImpl;
import com.android.build.gradle.internal.ide.AtlasDependencyGraph;
import com.android.build.gradle.internal.publishing.AndroidArtifacts;
import com.android.build.gradle.internal.publishing.AtlasAndroidArtifacts;
import com.android.build.gradle.internal.tasks.BaseTask;
import com.android.build.gradle.tasks.MergeManifests;
import com.android.build.gradle.tasks.MergeResources;
import com.android.build.gradle.tasks.MergeSourceSetFolders;
import com.android.build.gradle.tasks.ProcessAndroidResources;
import com.android.builder.dependency.level2.AndroidDependency;
import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.JavaLibrary;
import com.android.ide.common.res2.*;
import com.android.resources.ResourceType;
import com.google.common.base.Preconditions;
import com.google.common.collect.*;

import com.taobao.android.builder.AtlasBuildContext;
import com.taobao.android.builder.dependency.AtlasDependencyTree;
import com.taobao.android.builder.dependency.model.AwbBundle;
import com.taobao.android.builder.dependency.model.SoLibrary;
import com.taobao.android.builder.tasks.app.merge.AppendMainArtifactsCollection;
import com.taobao.android.builder.tasks.app.merge.MainArtifactsCollection;
import com.taobao.android.builder.tasks.app.merge.MainFilesCollection;
import com.taobao.android.builder.tasks.manager.MtlBaseTaskAction;
import com.taobao.android.builder.tools.ReflectUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.FalseFileFilter;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.AbstractFileCollection;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier;
import org.gradle.internal.component.local.model.DefaultProjectComponentIdentifier;
import org.gradle.internal.component.local.model.OpaqueComponentArtifactIdentifier;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.ClassReader;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.*;
import java.util.function.Consumer;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.ALL;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.*;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.ANNOTATION_PROCESSOR;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH;

/**
 * @author lilong
 * @create 2017-12-02 上午7:34
 */

public class BuildAtlasEnvTask extends BaseTask {


    private AppVariantOutputContext appVariantOutputContext;

    private ArtifactCollection compileManifests;

    public Map<String, File> allManifests = new HashMap<>();

    public Map<String, File> allSolibs = new HashMap<>();


    public Map<String, ResolvedArtifactResult> allAndroidRes = new HashMap<>();

    public Map<String, ResolvedArtifactResult> allAndroidAssets = new HashMap<>();

    public Map<String, ResolvedArtifactResult> allAndroidRnames = new HashMap<>();



    public Map<String, File> allJavaRes = new HashMap<>();

    public Set<FileIdentity> allJars = new HashSet<>();

    public Set<FileIdentity> appLocalJars = new HashSet<>();


    private AppVariantContext appVariantContext;

    private ArtifactCollection compileJars;

    private ArtifactCollection nativeLibs;

    private ArtifactCollection javaResources;
    private ArtifactCollection nativeLibs2;
    private ArtifactCollection res;
    private ArtifactCollection assets;

    private ArtifactCollection symbolListWithPackageNames;



    @TaskAction
    void generate() throws TransformException {

        Set<ResolvedArtifactResult> compileArtifacts = compileManifests.getArtifacts();
        Set<ResolvedArtifactResult> jarArtifacts = compileJars.getArtifacts();
        Set<ResolvedArtifactResult> nativeLibsArtifacts = nativeLibs.getArtifacts();
        Set<ResolvedArtifactResult> javaResourcesArtifacts = javaResources.getArtifacts();
        Set<ResolvedArtifactResult> androidRes = res.getArtifacts();
        Set<ResolvedArtifactResult> androidAssets = assets.getArtifacts();
        Set<ResolvedArtifactResult> androidRnames = symbolListWithPackageNames.getArtifacts();

        AtlasDependencyTree androidDependencyTree = AtlasBuildContext.androidDependencyTrees.get(getVariantName());
        List<AwbBundle> bundles = new ArrayList<>();
        bundles.add(androidDependencyTree.getMainBundle());
        bundles.addAll(androidDependencyTree.getAwbBundles());





        //this is no used ,if used in future add to transform!

        Set<ResolvedArtifactResult> nativeLibsArtifacts2 = nativeLibs2.getArtifacts();

        nativeLibsArtifacts.addAll(nativeLibsArtifacts2);

        AtlasBuildContext.localLibs = nativeLibs2.getArtifactFiles().getFiles();


        for (ResolvedArtifactResult resolvedArtifactResult : jarArtifacts) {
            ComponentIdentifier componentIdentifier = resolvedArtifactResult.getId().getComponentIdentifier();
            if (componentIdentifier instanceof DefaultModuleComponentIdentifier) {
                allJars.add(new FileIdentity(((DefaultModuleComponentIdentifier) componentIdentifier).getGroup() + ":" + ((DefaultModuleComponentIdentifier) componentIdentifier).getModule(), resolvedArtifactResult.getFile(), resolvedArtifactResult.getId().getDisplayName().startsWith("classes.jar") ? false : true, false));
            } else if (componentIdentifier instanceof DefaultProjectComponentIdentifier) {
                String projectPath = ((DefaultProjectComponentIdentifier) resolvedArtifactResult.getId().getComponentIdentifier()).getProjectPath();
                allJars.add(new FileIdentity(projectPath.substring(projectPath.lastIndexOf(":") + 1), resolvedArtifactResult.getFile(), resolvedArtifactResult.getId().getDisplayName().startsWith("classes.jar") ? false : true, true));
            } else if (componentIdentifier instanceof OpaqueComponentArtifactIdentifier) {
                if (resolvedArtifactResult.getFile().getAbsolutePath().contains("renderscript"))
                appLocalJars.add(new FileIdentity(componentIdentifier.getDisplayName(), resolvedArtifactResult.getFile(), true, false));
            }
        }
        for (ResolvedArtifactResult resolvedArtifactResult : compileArtifacts) {
            if (resolvedArtifactResult.getId().getComponentIdentifier() instanceof DefaultModuleComponentIdentifier) {
                allManifests.put(((DefaultModuleComponentIdentifier) resolvedArtifactResult.getId().getComponentIdentifier()).getGroup() + ":" + ((DefaultModuleComponentIdentifier) resolvedArtifactResult.getId().getComponentIdentifier()).getModule(), resolvedArtifactResult.getFile());
            } else if (resolvedArtifactResult.getId().getComponentIdentifier() instanceof DefaultProjectComponentIdentifier) {
                String projectPath = ((DefaultProjectComponentIdentifier) resolvedArtifactResult.getId().getComponentIdentifier()).getProjectPath();
                allManifests.put(projectPath.substring(projectPath.lastIndexOf(":") + 1), resolvedArtifactResult.getFile());
            }
        }

        for (ResolvedArtifactResult resolvedArtifactResult : nativeLibsArtifacts) {
            if (resolvedArtifactResult.getId().getComponentIdentifier() instanceof DefaultModuleComponentIdentifier) {
                allSolibs.put(((DefaultModuleComponentIdentifier) resolvedArtifactResult.getId().getComponentIdentifier()).getGroup() + ":" + ((DefaultModuleComponentIdentifier) resolvedArtifactResult.getId().getComponentIdentifier()).getModule(), resolvedArtifactResult.getFile());
            } else if (resolvedArtifactResult.getId().getComponentIdentifier() instanceof DefaultProjectComponentIdentifier) {
                String projectPath = ((DefaultProjectComponentIdentifier) resolvedArtifactResult.getId().getComponentIdentifier()).getProjectPath();
                allSolibs.put(projectPath.substring(projectPath.lastIndexOf(":") + 1), resolvedArtifactResult.getFile());
            }
        }

        for (ResolvedArtifactResult resolvedArtifactResult : javaResourcesArtifacts) {
            if (resolvedArtifactResult.getId().getComponentIdentifier() instanceof DefaultModuleComponentIdentifier) {
                allJavaRes.put(((DefaultModuleComponentIdentifier) resolvedArtifactResult.getId().getComponentIdentifier()).getGroup() + ":" + ((DefaultModuleComponentIdentifier) resolvedArtifactResult.getId().getComponentIdentifier()).getModule(), resolvedArtifactResult.getFile());
            } else if (resolvedArtifactResult.getId().getComponentIdentifier() instanceof DefaultProjectComponentIdentifier) {
                String projectPath = ((DefaultProjectComponentIdentifier) resolvedArtifactResult.getId().getComponentIdentifier()).getProjectPath();
                allJavaRes.put(projectPath.substring(projectPath.lastIndexOf(":") + 1), resolvedArtifactResult.getFile());
            }
        }

        for (ResolvedArtifactResult resolvedArtifactResult : androidRes) {
            if (resolvedArtifactResult.getId().getComponentIdentifier() instanceof DefaultModuleComponentIdentifier) {
                allAndroidRes.put(((DefaultModuleComponentIdentifier) resolvedArtifactResult.getId().getComponentIdentifier()).getGroup() + ":" + ((DefaultModuleComponentIdentifier) resolvedArtifactResult.getId().getComponentIdentifier()).getModule(), resolvedArtifactResult);
            } else if (resolvedArtifactResult.getId().getComponentIdentifier() instanceof DefaultProjectComponentIdentifier) {
                String projectPath = ((DefaultProjectComponentIdentifier) resolvedArtifactResult.getId().getComponentIdentifier()).getProjectPath();
                allAndroidRes.put(projectPath.substring(projectPath.lastIndexOf(":") + 1), resolvedArtifactResult);
            }
        }

        for (ResolvedArtifactResult resolvedArtifactResult : androidAssets) {
            if (resolvedArtifactResult.getId().getComponentIdentifier() instanceof DefaultModuleComponentIdentifier) {
                allAndroidAssets.put(((DefaultModuleComponentIdentifier) resolvedArtifactResult.getId().getComponentIdentifier()).getGroup() + ":" + ((DefaultModuleComponentIdentifier) resolvedArtifactResult.getId().getComponentIdentifier()).getModule(), resolvedArtifactResult);
            } else if (resolvedArtifactResult.getId().getComponentIdentifier() instanceof DefaultProjectComponentIdentifier) {
                String projectPath = ((DefaultProjectComponentIdentifier) resolvedArtifactResult.getId().getComponentIdentifier()).getProjectPath();
                allAndroidAssets.put(projectPath.substring(projectPath.lastIndexOf(":") + 1), resolvedArtifactResult);
            }
        }

        for (ResolvedArtifactResult resolvedArtifactResult : androidRnames) {
            if (resolvedArtifactResult.getId().getComponentIdentifier() instanceof DefaultModuleComponentIdentifier) {
                allAndroidRnames.put(((DefaultModuleComponentIdentifier) resolvedArtifactResult.getId().getComponentIdentifier()).getGroup() + ":" + ((DefaultModuleComponentIdentifier) resolvedArtifactResult.getId().getComponentIdentifier()).getModule(), resolvedArtifactResult);
            } else if (resolvedArtifactResult.getId().getComponentIdentifier() instanceof DefaultProjectComponentIdentifier) {
                String projectPath = ((DefaultProjectComponentIdentifier) resolvedArtifactResult.getId().getComponentIdentifier()).getProjectPath();
                allAndroidRnames.put(projectPath.substring(projectPath.lastIndexOf(":") + 1), resolvedArtifactResult);
            }
        }


         //app localJar is not support , this may course duplicate localjars
        appLocalJars.stream().forEach(fileIdentity -> AtlasBuildContext.atlasMainDexHelperMap.get(getVariantName()).addMainDex(fileIdentity));

        AtlasDependencyTree atlasDependencyTree = AtlasBuildContext.androidDependencyTrees.get(getVariantName());
        List<AndroidLibrary> androidLibraries = atlasDependencyTree.getAllAndroidLibrarys();


        androidLibraries.stream().forEach(androidLibrary -> {
            if (androidLibrary instanceof AtlasAndroidLibraryImpl) {
                AndroidDependency fakeAndroidLibrary = ((AtlasAndroidLibraryImpl) androidLibrary).getAndroidLibrary();
                File id = null;

                if ((id = allManifests.get(androidLibrary.getResolvedCoordinates().getGroupId() + ":" + androidLibrary.getResolvedCoordinates().getArtifactId())) == null) {
                    id = allManifests.get(androidLibrary.getResolvedCoordinates().toString().split(":")[1]);
                }
                if (id == null) {
                    getLogger().warn("id == null---------------------" + androidLibrary.getResolvedCoordinates().getGroupId() + ":" + androidLibrary.getResolvedCoordinates().getArtifactId());
                    throw new GradleException("excute failed! ");

                }
                ReflectUtils.updateField(fakeAndroidLibrary, "extractedFolder", id.getParentFile());
                ReflectUtils.updateField(fakeAndroidLibrary, "jarsRootFolder", id.getParentFile());
                ((AtlasAndroidLibraryImpl) androidLibrary).setAndroidLibrary(AndroidDependency.createExplodedAarLibrary(null, androidLibrary.getResolvedCoordinates(), androidLibrary.getName(), ((AtlasAndroidLibraryImpl) androidLibrary).getPath(), id.getParentFile()));
                appVariantContext.manifestMap.put(androidLibrary.getManifest().getAbsolutePath(),
                        appVariantContext.getModifyManifest(androidLibrary));
            }
        });

        List<AndroidLibrary> mainDexAndroidLibraries = atlasDependencyTree.getMainBundle().getAndroidLibraries();
        List<JavaLibrary> mainDexJarLibraries = atlasDependencyTree.getMainBundle().getJavaLibraries();
        List<SoLibrary> mainSoLibraries = atlasDependencyTree.getMainBundle().getSoLibraries();

        for (AndroidLibrary androidLibrary : mainDexAndroidLibraries) {
            String name = androidLibrary.getResolvedCoordinates().getGroupId() + ":" + androidLibrary.getResolvedCoordinates().getArtifactId();
            String moudleName = androidLibrary.getResolvedCoordinates().toString().split(":")[1];
            fillMainManifest(name, moudleName);
            fillMainJar(name, moudleName);
            fillAllJavaRes(name, moudleName);
            fillMainSolibs(name, moudleName);
        }

        for (JavaLibrary jarLibrary : mainDexJarLibraries) {
            String moudleName = jarLibrary.getName().split(":")[1];
            String name = jarLibrary.getResolvedCoordinates().getGroupId() + ":" + jarLibrary.getResolvedCoordinates().getArtifactId();
            fillMainJar(name, moudleName);
            fillAllJavaRes(name, moudleName);
        }

        for (SoLibrary soLibrary : mainSoLibraries) {
            String name = soLibrary.getResolvedCoordinates().getGroupId() + ":" + soLibrary.getResolvedCoordinates().getArtifactId();
            String moudleName = soLibrary.getResolvedCoordinates().toString().split(":")[1];
            fillMainSolibs(name, moudleName);
        }


        for (AwbBundle awbBundle : atlasDependencyTree.getAwbBundles()) {
            List<AndroidLibrary> awbAndroidLibraries = awbBundle.getAndroidLibraries();
            List<JavaLibrary> awbJarLibraries = awbBundle.getJavaLibraries();
            List<SoLibrary> awbSoLibraries = awbBundle.getSoLibraries();

            for (AndroidLibrary androidLibrary : awbAndroidLibraries) {
                String name = androidLibrary.getResolvedCoordinates().getGroupId() + ":" + androidLibrary.getResolvedCoordinates().getArtifactId();
                String moudleName = androidLibrary.getResolvedCoordinates().toString().split(":")[1];
                fillAwbManifest(name, moudleName, awbBundle);
                fillAwbJar(name, moudleName, awbBundle);
                fillAwbAllJavaRes(name, moudleName, awbBundle);
                fillAwbSolibs(name, moudleName, awbBundle);
                fillAwbAndroidRes(name, moudleName, awbBundle);
                fillAwbAndroidAssets(name, moudleName, awbBundle);
                fillAwbAndroidRs(name, moudleName, awbBundle);

            }
            for (JavaLibrary jarLibrary : awbJarLibraries) {
                String moudleName = jarLibrary.getName().split(":")[1];
                String name = jarLibrary.getResolvedCoordinates().getGroupId() + ":" + jarLibrary.getResolvedCoordinates().getArtifactId();
                fillAwbJar(name, moudleName, awbBundle);
            }
            for (SoLibrary soLibrary : awbSoLibraries) {
                String name = soLibrary.getResolvedCoordinates().getGroupId() + ":" + soLibrary.getResolvedCoordinates().getArtifactId();
                String moudleName = soLibrary.getResolvedCoordinates().toString().split(":")[1];
                fillAwbSolibs(name, moudleName, awbBundle);
            }
            String name = awbBundle.getResolvedCoordinates().getGroupId() + ":" + awbBundle.getResolvedCoordinates().getArtifactId();
            String moudleName = awbBundle.getResolvedCoordinates().toString().split(":")[1];
            fillAwbManifest(name, moudleName, awbBundle);
            fillAwbJar(name, moudleName, awbBundle);
            fillAwbAllJavaRes(name, moudleName, awbBundle);
            fillAwbSolibs(name, moudleName, awbBundle);
            fillAwbAndroidRes(name, moudleName, awbBundle);
            fillAwbAndroidAssets(name, moudleName, awbBundle);
            fillAwbAndroidRs(name, moudleName, awbBundle);

        }


        MergeResources mergeResources = appVariantContext.getScope().getMergeResourcesTask().get(new TaskContainerAdaptor(getProject().getTasks()));

        try {
            //mergeresources
            Field field = MergeResources.class.getDeclaredField("libraries");
            field.setAccessible(true);
            field.set(mergeResources, new MainArtifactsCollection((ArtifactCollection) field.get(mergeResources), getProject(),mergeResources.getVariantName()));
            appVariantOutputContext.getAwbTransformMap().values().stream().forEach(awbTransform -> {
                if (isMBundle(appVariantContext,awbTransform.getAwbBundle())) {
                    try {
                        awbTransform.getAwbBundle().isMBundle = true;
                        awbTransform.getAwbBundle().bundleInfo.setIsMBundle(true);
                        field.set(mergeResources, new AppendMainArtifactsCollection(appVariantContext.getProject(), (ArtifactCollection) field.get(mergeResources), awbTransform.getAwbBundle(), ANDROID_RES));
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                    }
                }
            });
            mergeResources.doLast(task -> FileUtils.listFiles(((MergeResources) task).getOutputDir(),new String[]{"xml"},true).parallelStream().forEach(file -> {
                if (!AppendMainArtifactsCollection.bundle2Map.containsKey(file.getName())){
                    return;
                }
                List<String> lines = null;
                List<String> newLines = new ArrayList<>();
                try {
                    lines = FileUtils.readLines(file);
                    lines.forEach(s -> {
                        String s1 = s;
                        if (s.contains("http://schemas.android.com/apk/res/" + AppendMainArtifactsCollection.bundle2Map.get(file.getName()))) {
                            s1 = s.replace("http://schemas.android.com/apk/res/" + AppendMainArtifactsCollection.bundle2Map.get(file.getName()), "http://schemas.android.com/apk/res-auto");
                        }
                        newLines.add(s1);
                    });
                    FileUtils.writeLines(file, newLines);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }));


            //mergeSourcesSets
            MergeSourceSetFolders mergeSourceSetFolders = appVariantContext.getScope().getMergeAssetsTask().get(new TaskContainerAdaptor(getProject().getTasks()));
            Field assetsField = MergeSourceSetFolders.class.getDeclaredField("libraries");
            assetsField.setAccessible(true);
            assetsField.set(mergeSourceSetFolders, new MainArtifactsCollection((ArtifactCollection) assetsField.get(mergeSourceSetFolders), getProject(),mergeSourceSetFolders.getVariantName()));
            appVariantOutputContext.getAwbTransformMap().values().stream().forEach(awbTransform -> {
                if (isMBundle(appVariantContext,awbTransform.getAwbBundle())) {
                    try {
                        awbTransform.getAwbBundle().isMBundle = true;
                        awbTransform.getAwbBundle().bundleInfo.setIsMBundle(true);
                        assetsField.set(mergeSourceSetFolders, new AppendMainArtifactsCollection(appVariantContext.getProject(), (ArtifactCollection) assetsField.get(mergeSourceSetFolders), awbTransform.getAwbBundle(), ASSETS));
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                    }
                }
            });
            AtlasBuildContext.atlasMainDexHelperMap.get(getVariantName()).getMainSoFiles().put(appVariantContext.getScope().getMergeNativeLibsOutputDir().getAbsolutePath(), true);

        } catch (Exception e) {

        }

        //process resources
        ProcessAndroidResources processAndroidResources = appVariantContext.getScope().getProcessResourcesTask().get(new TaskContainerAdaptor(appVariantContext.getProject().getTasks()));
        FileCollection fileCollection = processAndroidResources.getSymbolListsWithPackageNames();
        Set<String> filesNames = new HashSet<>();
        for (String fileName : AtlasBuildContext.atlasMainDexHelperMap.get(getVariantName()).getMainManifestFiles().keySet()) {
            filesNames.add(fileName.substring(fileName.lastIndexOf(File.separatorChar) + 1));
        }
        FileCollection updateFileCollection = fileCollection.filter(element -> filesNames.contains(element.getParentFile().getParentFile().getName()));
        ReflectUtils.updateField(processAndroidResources, "symbolListsWithPackageNames", updateFileCollection);
        appVariantOutputContext.getAwbTransformMap().values().stream().forEach(awbTransform -> {
            if (isMBundle(appVariantContext,awbTransform.getAwbBundle())) {
                awbTransform.getAwbBundle().isMBundle = true;
                awbTransform.getAwbBundle().bundleInfo.setIsMBundle(true);
                FileCollection fc = new AppendMainArtifactsCollection(appVariantContext.getProject(),processAndroidResources.getSymbolListsWithPackageNames() , awbTransform.getAwbBundle(), SYMBOL_LIST_WITH_PACKAGE_NAME).getArtifactFiles();
                ReflectUtils.updateField(processAndroidResources, "symbolListsWithPackageNames", fc);
            }
        });

        appVariantContext.processResAwbsTask.mainDexSymbolFileCollection = updateFileCollection;


//        FileCollection fs = appVariantContext.getScope().getArtifactFileCollection(AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH,AndroidArtifacts.ArtifactScope.ALL,AndroidArtifacts.ArtifactType.CLASSES);
//        fs.getFiles().forEach(new Consumer<File>() {
//            @Override
//            public void accept(File file) {
//                if (file.exists()){
//                    try {
//                        JarFile jarFile = new JarFile(file);
//                        Enumeration<JarEntry>enumeration = jarFile.entries();
//                        while (enumeration.hasMoreElements()){
//                            JarEntry jarEntry = enumeration.nextElement();
//                            if (jarEntry.getName().endsWith(".class")){
//                                ClassReader classReader = new ClassReader(jarFile.getInputStream(jarEntry));
//                                String[]ss = classReader.getInterfaces();
//                                if (ss!= null){
//                                    for (String s:ss){
//                                        if (s.contains("IExternalComponentGetter")||s.contains("IExternalComponentGetter.class")){
//                                            System.out.println("IExternalComponentGetter:"+jarEntry.getName());
//                                        }else if (s.contains("IExternalModuleGetter")||s.contains("IExternalModuleGetter.class")){
//                                            System.out.println("IExternalModuleGetter:"+jarEntry.getName());
//                                        }
//                                    }
//                                }
//
//                            }
//                        }
//                    } catch (IOException e) {
//                        e.printStackTrace();
//                    }
//                }
//            }
//        });

        allManifests.clear();
        allJavaRes.clear();
        allSolibs.clear();
        allJars.clear();
        allAndroidAssets.clear();
        allAndroidRes.clear();







//
//        try {
//            duplicateClazzNote();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }

    }


    private void fillAwbAndroidRs(String name, String moudleName, AwbBundle awbBundle) {
        ResolvedArtifactResult id = null;
        if ((id = allAndroidRnames.get(name)) == null) {
            id = allAndroidRnames.get(moudleName);
        }
        if (id != null) {
            awbBundle.getResolvedSymbolListWithPackageNameArtifactResults().add(id);
        }

    }

    private void fillAwbAndroidAssets(String name, String moudleName, AwbBundle awbBundle) {
        ResolvedArtifactResult id = null;
        if ((id = allAndroidAssets.get(name)) == null) {
            id = allAndroidAssets.get(moudleName);
        }
        if (id != null) {
            awbBundle.getResolvedAssetsArtifactResults().add(id);
        }
    }

    private void fillAwbJar(String name, String moudleName, AwbBundle awbBundle) {
        AwbTransform awbTransform = appVariantOutputContext.getAwbTransformMap().get(awbBundle.getName());


    }

    private void fillAwbManifest(String name, String moudleName, AwbBundle awbBundle) {

    }

    private void fillAwbAndroidRes(String name, String moudleName, AwbBundle awbBundle) {
        ResolvedArtifactResult id = null;
        if ((id = allAndroidRes.get(name)) == null) {
            id = allAndroidRes.get(moudleName);
        }
        if (id != null) {
            awbBundle.getResolvedResArtifactResults().add(id);
        }

    }

    private void fillAwbSolibs(String name, String moudleName, AwbBundle awbBundle) {
        AwbTransform awbTransform = appVariantOutputContext.getAwbTransformMap().get(awbBundle.getName());
        File id = null;
        if ((id = allSolibs.get(name)) == null) {
            id = allSolibs.get(moudleName);
        }
        if (id != null) {
            awbTransform.getLibraryJniLibsInputDir().add(id);
        }
    }

    private void fillAwbAllJavaRes(String name, String moudleName, AwbBundle awbBundle) {
        AwbTransform awbTransform = appVariantOutputContext.getAwbTransformMap().get(awbBundle.getName());
        File id = null;
        if ((id = allJavaRes.get(name)) == null) {
            id = allJavaRes.get(moudleName);
        }
        if (id != null) {
            awbTransform.getLibraryResourcesInutDir().add(id);
        }
    }


    private void duplicateClazzNote() throws IOException {
        File outPutFile = new File(appVariantContext.getScope().getGlobalScope().getOutputsDir(), "duplicate-class.txt");
        Set<String> warnList = new HashSet<>();
        Set<File> jarFiles = compileJars.getArtifactFiles().getFiles();
        Map<String, String> jarClassNames = Maps.newHashMap();
        for (File jarFile : jarFiles) {
            JarFile jar = new JarFile(jarFile);
            Enumeration<JarEntry> entryEnumeration = jar.entries();
            while (entryEnumeration.hasMoreElements()) {
                JarEntry jarEntry = entryEnumeration.nextElement();
                String className = jarEntry.getName();
                if (className.endsWith(".class")) {
                    if (jarClassNames.containsKey(className)) {
                        warnList.add(String.format("duplicate class %s in %s and %s", className, jarClassNames.get(className), jarFile.getAbsolutePath()));
                        getLogger().warn(String.format("duplicate class %s in %s and %s", className, jarClassNames.get(className), jarFile.getAbsolutePath()));
                    } else {
                        jarClassNames.put(className, jarFile.getAbsolutePath());
                    }
                }
            }
        }

        if (!outPutFile.getParentFile().exists()) {
            outPutFile.getParentFile().mkdirs();
            FileUtils.writeLines(outPutFile, warnList, true);
        }

    }



    private void fillMainManifest(String name, String moudleName) {
        File id = null;
        if ((id = allManifests.get(name)) == null) {
            id = allManifests.get(moudleName);
        }
        if (id != null) {
            AtlasBuildContext.atlasMainDexHelperMap.get(getVariantName()).getMainManifestFiles().put(id.getParentFile().getAbsolutePath(), true);
        }
    }

    private void fillMainSolibs(String name, String moudleName) {
        File id = null;
        if ((id = allSolibs.get(name)) == null) {
            id = allSolibs.get(moudleName);
        }
        if (id != null) {
            AtlasBuildContext.atlasMainDexHelperMap.get(getVariantName()).getMainSoFiles().put(id.getAbsolutePath(), true);
        }
    }

    private void fillAllJavaRes(String name, String moudleName) {
        File id = null;
        if ((id = allJavaRes.get(name)) == null) {
            id = allJavaRes.get(moudleName);
        }
        if (id != null) {
            AtlasBuildContext.atlasMainDexHelperMap.get(getVariantName()).getMainResFiles().put(id.getAbsolutePath(), true);
        }
    }


    private void fillMainJar(String name, String moudleName) {
        Iterator<FileIdentity> identities = allJars.iterator();
        while (identities.hasNext()) {
            FileIdentity fileIdentity = identities.next();
            if (fileIdentity.name.equals(name) || fileIdentity.name.equals(moudleName)) {
                if (fileIdentity.localJar && fileIdentity.name.equals(fileIdentity.file.getName())){
                    identities.remove();
                    continue;
                }
                AtlasBuildContext.atlasMainDexHelperMap.get(getVariantName()).getMainDexFiles().add(fileIdentity);
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
            updateDependenciesTask.nativeLibs = appVariantContext.getScope().getArtifactCollection(COMPILE_CLASSPATH, ALL, JNI);

            updateDependenciesTask.nativeLibs2 = AtlasDependencyGraph.computeArtifactCollection(variantContext.getScope(), AtlasAndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH, ALL, AtlasAndroidArtifacts.AtlasArtifactType.LIBS);

            updateDependenciesTask.javaResources = appVariantContext.getScope().getArtifactCollection(COMPILE_CLASSPATH, ALL, JAVA_RES);

            updateDependenciesTask.res = appVariantContext.getScope().getArtifactCollection(COMPILE_CLASSPATH, ALL, ANDROID_RES);

            updateDependenciesTask.assets = appVariantContext.getScope().getArtifactCollection(COMPILE_CLASSPATH, ALL, ASSETS);

            updateDependenciesTask.symbolListWithPackageNames = appVariantContext.getScope().getArtifactCollection(
                    RUNTIME_CLASSPATH,
                    ALL,
                    AndroidArtifacts.ArtifactType.SYMBOL_LIST_WITH_PACKAGE_NAME);



            List<ProjectDependency> projectDependencies = new ArrayList<>();
            appVariantContext.getScope().getVariantDependencies().getCompileClasspath().getAllDependencies().forEach(dependency -> {
                if (dependency instanceof ProjectDependency) {
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

    public static class FileIdentity {
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

        public FileIdentity(String name, File file, boolean localJar, boolean subProject) {
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


    private boolean isMBundle(AppVariantContext appVariantContext, AwbBundle awbBundle){


        if (awbBundle.getPackageName().equals("com.taobao.android.customdetail")){
            return false;
        }

        if (appVariantContext.getAtlasExtension().getTBuildConfig().getOutOfApkBundles().contains(awbBundle.getResolvedCoordinates().getArtifactId())){
            return false;
        }

        return appVariantContext.getAtlasExtension().getTBuildConfig().getAllBundlesToMdex() || appVariantContext.getAtlasExtension().getTBuildConfig().getBundleToMdex().contains(awbBundle.getPackageName());

    }


    private static class EmptyArtifactCollection implements ArtifactCollection{
        FileCollection updateFileCollection;

        public EmptyArtifactCollection(FileCollection updateFileCollection) {
            this.updateFileCollection = updateFileCollection;
        }

        @Override
        public FileCollection getArtifactFiles() {
            return updateFileCollection;
        }

        @Override
        public Set<ResolvedArtifactResult> getArtifacts() {
            return ImmutableSet.of();
        }

        @Override
        public Collection<Throwable> getFailures() {
            return null;
        }

        @NotNull
        @Override
        public Iterator<ResolvedArtifactResult> iterator() {
            return getArtifacts().iterator();
        }
    }

}
