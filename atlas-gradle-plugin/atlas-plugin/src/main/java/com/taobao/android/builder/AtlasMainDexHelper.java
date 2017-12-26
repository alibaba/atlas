package com.taobao.android.builder;


import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.QualifiedContent;
import com.google.common.collect.Multimap;
import com.taobao.android.builder.dependency.model.AwbBundle;
import com.taobao.android.builder.tasks.app.BuildAtlasEnvTask;
import java.io.File;
import java.util.*;

/**
 * @author lilong
 * @create 2017-12-27 下午2:30
 */

public class AtlasMainDexHelper {

    private   LinkedHashSet<BuildAtlasEnvTask.FileIdentity> mainDexJar = new LinkedHashSet<>();

    private   LinkedHashSet<BuildAtlasEnvTask.FileIdentity>awbDexJar = new LinkedHashSet<>();

    private   Map<AwbBundle, Multimap<QualifiedContent, File>> awbDexFile = new HashMap<AwbBundle, Multimap<QualifiedContent, File>>();

    private   Map<String,Boolean>mainManifestMap = new HashMap<>();

    private   Map<String, Boolean> mainNativeSoMap = new LinkedHashMap<>();

    private   Map<String, Boolean> mainRes = new LinkedHashMap<>();

    public void addMainDexJars(Set<BuildAtlasEnvTask.FileIdentity>maindexs){
        mainDexJar.addAll(maindexs);
    }

    public void addAwbDexJars(Set<BuildAtlasEnvTask.FileIdentity>awbDexs){
        awbDexJar.addAll(awbDexs);
    }

    public void addAwbDexFiles(Map awbDexFiles){
        awbDexFile.putAll(awbDexFiles);
    }

    public void addMainDexManifests(Map mainDexMap){
        mainManifestMap.putAll(mainDexMap);
    }

    public void addMainDexNativeSos(Map mainNativeSo){
        mainNativeSoMap.putAll(mainNativeSo);
    }

    public void addMainRes(Map mainResMap){
        mainRes.putAll(mainResMap);
    }

    public Map<AwbBundle, Multimap<QualifiedContent, File>>getAwbDexFiles(){
        return awbDexFile;
    }

    public Map<String,Boolean> getMainManifestFiles(){
        return mainManifestMap;
    }

    public LinkedHashSet<BuildAtlasEnvTask.FileIdentity> getMainDexFiles(){
        return mainDexJar;
    }

    public Map<String, Boolean> getMainSoFiles(){
        return mainNativeSoMap;
    }


    public void updateMainDexFile(File oldFile,File newFile){
        for (BuildAtlasEnvTask.FileIdentity id:mainDexJar){
            if (id.file.equals(oldFile)){
                id.file = newFile;
                break;
            }
        }

    }

    public void updateMainDexFile(JarInput oldFile,File newFile){

        for (BuildAtlasEnvTask.FileIdentity id:mainDexJar){
            if (id.file.equals(oldFile.getFile())){
                id.file = newFile;
                break;
            }
        }

    }

    public boolean inMainDex(JarInput jarInput){
        File file = jarInput.getFile();
        return inMainDex(file);
    }

    public boolean inMainDex(File jarFile){
        for (BuildAtlasEnvTask.FileIdentity fileIdentity:mainDexJar){
            if (fileIdentity.file.equals(jarFile)){
                return true;
            }
        }
        return false;
    }


    public BuildAtlasEnvTask.FileIdentity get(JarInput jarInput){
        File file = jarInput.getFile();
        return get(file);
    }

    public BuildAtlasEnvTask.FileIdentity get(File jarFile){
        for (BuildAtlasEnvTask.FileIdentity fileIdentity:mainDexJar){
            if (fileIdentity.file.equals(jarFile)){
                return fileIdentity;
            }
        }
        return null;
    }


    public void addMainDex(BuildAtlasEnvTask.FileIdentity fileIdentity){
         mainDexJar.add(fileIdentity);
    }


    public Collection<File> getAllMainDexJars() {
        Set<File>allMainDexJars = new HashSet<>();
        for (BuildAtlasEnvTask.FileIdentity fileIdentity:mainDexJar){
            allMainDexJars.add(fileIdentity.file);
        }
        return allMainDexJars;
    }

    public Map<String,Boolean> getMainResFiles() {
        return mainRes;
    }
}
