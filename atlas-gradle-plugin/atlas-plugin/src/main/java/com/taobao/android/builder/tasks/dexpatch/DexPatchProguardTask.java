package com.taobao.android.builder.tasks.dexpatch;

import com.android.build.gradle.ProguardFiles;
import com.android.build.gradle.internal.TaskManager;
import com.android.build.gradle.internal.api.AppVariantContext;
import com.android.build.gradle.internal.api.AwbTransform;
import com.android.build.gradle.internal.api.VariantContext;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.tasks.BaseTask;
import com.android.build.gradle.internal.variant.BaseVariantOutputData;
import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.JavaLibrary;
import com.android.builder.model.Library;
import com.taobao.android.builder.AtlasBuildContext;
import com.taobao.android.builder.tasks.manager.MtlBaseTaskAction;
import com.taobao.android.builder.tools.MD5Util;
import org.gradle.api.tasks.TaskAction;
import org.jetbrains.annotations.NotNull;
import proguard.ParseException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * @author lilong
 * @create 2017-06-16 On the afternoon of known
 */

public class DexPatchProguardTask extends BaseTask{

    private AppVariantContext variantContext;

    private BaseVariantOutputData baseVariantOutputData;

    @TaskAction
    public void run() throws IOException, ParseException {
        if (!variantContext.getScope().isMinifyEnabled()){
            return;
        }
        ProguardTask proguardTask = new ProguardTask();
        List<File>files = new ArrayList<>();
        File mappingFile = new File(variantContext.apContext.getApExploredFolder(),"mapping.txt");

        if (mappingFile.exists()){
            proguardTask.applyMapping(mappingFile);
        }

        final GradleVariantConfiguration variantConfig = variantContext.getVariantConfiguration();
        Set<File> proguardFiles =
                variantConfig.getProguardFiles(
                        true,
                        Collections.singletonList(
                                ProguardFiles.getDefaultProguardFile(
                                        TaskManager.DEFAULT_PROGUARD_CONFIG_FILE,
                                        getProject())));

        for (File file:proguardFiles){
            proguardTask.applyConfigurationFile(file);
        }

        for (File file:getLibrarys()){
            proguardTask.libraryJar(file);
        }

        List<AndroidLibrary> androidLibraries = AtlasBuildContext.androidDependencyTrees.get(variantContext.getVariantData().getName()).getAllAndroidLibrarys();
        List<JavaLibrary> javaLibraries = AtlasBuildContext.androidDependencyTrees.get(variantContext.getVariantData().getName()).getMainBundle().getJavaLibraries();
        if (androidLibraries.size() > 0) {
            for (AndroidLibrary androidLibrary : androidLibraries) {
                File jarFile = null;
                File outJar = null;
                if (DexPatchContext.modifyArtifact.containsKey(androidLibrary.getResolvedCoordinates().getArtifactId())) {
                    jarFile = DexPatchContext.getInstance().getBundleArtifactOptJar(androidLibrary.getResolvedCoordinates().getArtifactId(), DexPatchContext.modifyArtifact.get(androidLibrary.getResolvedCoordinates().getArtifactId()).bundleName, DexPatchContext.modifyArtifact.get(androidLibrary.getResolvedCoordinates().getArtifactId()).isMaindex);
                    outJar = DexPatchContext.getInstance().getBundleArtifactObfJar(DexPatchContext.modifyArtifact.get(androidLibrary.getResolvedCoordinates().getArtifactId()).artifactName, DexPatchContext.modifyArtifact.get(androidLibrary.getResolvedCoordinates().getArtifactId()).bundleName, DexPatchContext.modifyArtifact.get(androidLibrary.getResolvedCoordinates().getArtifactId()).isMaindex);

                }else {
                    jarFile = getInJar(androidLibrary);
                    outJar = getOutJar(androidLibrary);
                }
                    proguardTask.inJar(jarFile);
                    proguardTask.outJar(outJar);
                    files.add(jarFile);


                if (androidLibrary.getLocalJars().size() > 0){
                    for (File jar:androidLibrary.getLocalJars()){
                        files.add(getInJar(jar));
                        proguardTask.inJar(getInJar(jar));
                        proguardTask.outJar(getOutJar(jar));
                    }
                }
            }

            for (JavaLibrary javaLibrary : javaLibraries) {

                File jarFile = null;
                File outJar = null;
                if (DexPatchContext.modifyArtifact.containsKey(javaLibrary.getResolvedCoordinates().getArtifactId())) {
                    jarFile = DexPatchContext.getInstance().getBundleArtifactOptJar(javaLibrary.getResolvedCoordinates().getArtifactId(), DexPatchContext.modifyArtifact.get(javaLibrary.getResolvedCoordinates().getArtifactId()).bundleName, DexPatchContext.modifyArtifact.get(javaLibrary.getResolvedCoordinates().getArtifactId()).isMaindex);

                    outJar = DexPatchContext.getInstance().getBundleArtifactObfJar(DexPatchContext.modifyArtifact.get(javaLibrary.getResolvedCoordinates().getArtifactId()).artifactName, DexPatchContext.modifyArtifact.get(javaLibrary.getResolvedCoordinates().getArtifactId()).bundleName, DexPatchContext.modifyArtifact.get(javaLibrary.getResolvedCoordinates().getArtifactId()).isMaindex);

                }else {
                    jarFile = getInJar(javaLibrary);
                    outJar = getOutJar(javaLibrary);
                }
                if (!files.contains(jarFile)) {
                    proguardTask.inJar(jarFile);
                    proguardTask.outJar(outJar);
                    files.add(jarFile);
                }
                if (javaLibrary.getDependencies().size() > 0){
                    for (JavaLibrary jar:javaLibrary.getDependencies()){
                            File jarFile1;
                            File outJar1;
                        if (DexPatchContext.modifyArtifact.containsKey(javaLibrary.getResolvedCoordinates().getArtifactId())) {
                            jarFile1 = DexPatchContext.getInstance().getBundleArtifactOptJar(javaLibrary.getResolvedCoordinates().getArtifactId(), DexPatchContext.modifyArtifact.get(javaLibrary.getResolvedCoordinates().getArtifactId()).bundleName, DexPatchContext.modifyArtifact.get(javaLibrary.getResolvedCoordinates().getArtifactId()).isMaindex);

                            outJar1 = DexPatchContext.getInstance().getBundleArtifactObfJar(DexPatchContext.modifyArtifact.get(javaLibrary.getResolvedCoordinates().getArtifactId()).artifactName, DexPatchContext.modifyArtifact.get(javaLibrary.getResolvedCoordinates().getArtifactId()).bundleName, DexPatchContext.modifyArtifact.get(javaLibrary.getResolvedCoordinates().getArtifactId()).isMaindex);

                        }else{
                            jarFile1 = getInJar(jar);
                            outJar1 = getOutJar(jar);
                        }
                        if (!files.contains(getInJar(jarFile1))) {
                            files.add(jarFile1);
                            proguardTask.inJar(jarFile1);
                            proguardTask.outJar(outJar1);
                        }
                    }
                }
            }

            if (null != variantContext.getAppVariantOutputContext(baseVariantOutputData).getAwbTransformMap()) {
                for (AwbTransform awbTransform : variantContext.getAppVariantOutputContext(baseVariantOutputData).getAwbTransformMap().values()) {

                    for (AndroidLibrary androidLibrary : awbTransform.getAwbBundle().getAndroidLibraries()) {
                        File jarFile;
                        File outJar;
                        if (DexPatchContext.modifyArtifact.containsKey(androidLibrary.getResolvedCoordinates().getArtifactId())) {
                            jarFile = DexPatchContext.getInstance().getBundleArtifactOptJar(androidLibrary.getResolvedCoordinates().getArtifactId(), DexPatchContext.modifyArtifact.get(androidLibrary.getResolvedCoordinates().getArtifactId()).bundleName, DexPatchContext.modifyArtifact.get(androidLibrary.getResolvedCoordinates().getArtifactId()).isMaindex);
                            outJar = DexPatchContext.getInstance().getBundleArtifactObfJar(DexPatchContext.modifyArtifact.get(androidLibrary.getResolvedCoordinates().getArtifactId()).artifactName, DexPatchContext.modifyArtifact.get(androidLibrary.getResolvedCoordinates().getArtifactId()).bundleName, DexPatchContext.modifyArtifact.get(androidLibrary.getResolvedCoordinates().getArtifactId()).isMaindex);

                        }else {
                            jarFile = getInJar(androidLibrary);
                             outJar = getOutJar(androidLibrary);
                        }
                        if (!files.contains(jarFile)) {
                            proguardTask.inJar(jarFile);
                            proguardTask.outJar(outJar);
                            files.add(jarFile);
                        }

                    }

                    for (JavaLibrary androidLibrary : awbTransform.getAwbBundle().getJavaLibraries()) {
                        File jarFile;
                        File outJar;
                        if (DexPatchContext.modifyArtifact.containsKey(androidLibrary.getResolvedCoordinates().getArtifactId())) {
                            jarFile = DexPatchContext.getInstance().getBundleArtifactOptJar(androidLibrary.getResolvedCoordinates().getArtifactId(), DexPatchContext.modifyArtifact.get(androidLibrary.getResolvedCoordinates().getArtifactId()).bundleName, DexPatchContext.modifyArtifact.get(androidLibrary.getResolvedCoordinates().getArtifactId()).isMaindex);
                            outJar = DexPatchContext.getInstance().getBundleArtifactObfJar(DexPatchContext.modifyArtifact.get(androidLibrary.getResolvedCoordinates().getArtifactId()).artifactName, DexPatchContext.modifyArtifact.get(androidLibrary.getResolvedCoordinates().getArtifactId()).bundleName, DexPatchContext.modifyArtifact.get(androidLibrary.getResolvedCoordinates().getArtifactId()).isMaindex);

                        }else {
                            jarFile = getInJar(androidLibrary);
                            outJar = getOutJar(androidLibrary);
                        }
                        if (!files.contains(jarFile)) {
                            proguardTask.inJar(jarFile);
                            proguardTask.outJar(outJar);
                            files.add(jarFile);
                        }

                    }

                    System.out.println("proguard files size:"+files.size());

                    }

                }
            }

            proguardTask.runProguard();
        }



    public static class ConfigAction extends MtlBaseTaskAction<DexPatchProguardTask> {

        private VariantContext variantContext;
        private BaseVariantOutputData variantOutputData;
        public ConfigAction(AppVariantContext variantContext, BaseVariantOutputData baseVariantOutputData) {
            super(variantContext, baseVariantOutputData);
            this.variantContext = variantContext;
            this.variantOutputData = baseVariantOutputData;
        }

        @Override
        public String getName() {
            return scope.getTaskName("DexPatchProguard");
        }

        @Override
        public Class<DexPatchProguardTask> getType() {
            return DexPatchProguardTask.class;
        }

        @Override
        public void execute(DexPatchProguardTask dexPatchProguardTask) {

            dexPatchProguardTask.variantContext = (AppVariantContext) variantContext;
            dexPatchProguardTask.baseVariantOutputData = variantOutputData;
            dexPatchProguardTask.setVariantName("DexPatchProguard");

        }
    }

    private File getOutJar(File file) {
        File folder = new File(variantContext.getVariantData().getScope().getGlobalScope().getOutputsDir(),"proguard");
        if (!folder.exists()){
            folder.mkdirs();
        }
        return new File(folder, MD5Util.getFileMD5(file)+"-proguard.jar");

    }

    @NotNull
    private File getInJar(Library androidLibrary) {
       if (androidLibrary instanceof JavaLibrary){
           return getInJar(((JavaLibrary) androidLibrary).getJarFile());
       }else if (androidLibrary instanceof AndroidLibrary){
            return getInJar(((AndroidLibrary) androidLibrary).getJarFile());
       }
        return null;


    }

    @NotNull
    private File getInJar(File file) {
        File folder = new File(variantContext.getVariantData().getScope().getGlobalScope().getOutputsDir(),"opt");
        if (!folder.exists()){
            folder.mkdirs();
        }
        File inJar = new File(folder, MD5Util.getFileMD5(file)+"-opt.jar");
        if (!inJar.exists()){
            return file;
        }
        return inJar;

    }

    private File getOutJar(Library androidLibrary) {
        if (androidLibrary instanceof JavaLibrary){
            return getOutJar(((JavaLibrary) androidLibrary).getJarFile());
        }else if (androidLibrary instanceof AndroidLibrary){
            return getOutJar(((AndroidLibrary) androidLibrary).getJarFile());
        }
        return null;

    }

    private List<File> getLibrarys() {
        List<File> libraryJars = new ArrayList<File>();
        for (File runtimeJar : variantContext.getScope().getGlobalScope().getAndroidBuilder().getBootClasspath(true)) {
            libraryJars.add(runtimeJar);
        }
        return libraryJars;
    }

}
