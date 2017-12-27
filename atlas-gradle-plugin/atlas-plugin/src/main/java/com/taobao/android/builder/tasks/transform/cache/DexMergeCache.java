package com.taobao.android.builder.tasks.transform.cache;

import com.alibaba.fastjson.JSON;
import com.android.build.api.transform.Transform;
import com.google.common.collect.ImmutableList;
import com.taobao.android.builder.tools.MD5Util;
import com.taobao.android.builder.tools.log.FileLogger;
import org.apache.commons.io.FileUtils;
import org.gradle.api.Project;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

/**
 * @author lilong
 * @create 2017-12-21 上午11:02
 */
public class DexMergeCache extends DexCache{

    private FileLogger fileLogger = FileLogger.getInstance("dexmerge");
    @Override
    protected void readLocalCache() {
        super.readLocalCache();
    }

    public DexMergeCache(Project project,Transform transform, String type, String version, File location) {
        super(project,transform,type, version, location);
        readLocalCache();
    }

    @Override
    public void saveContent() {
        if (cacheFile.exists()) {
            FileUtils.deleteQuietly(cacheFile);
        } else {
            cacheFile.getParentFile().mkdirs();
        }
            Iterator iterator = cacheMap.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry entry = (Map.Entry) iterator.next();
                if (!keys.contains(entry.getKey())) {
                    List<String>values = cacheMap.get(entry.getKey());
                    clearAllFiles(values);
                    iterator.remove();
                }
            }
        try {
            FileUtils.writeStringToFile(cacheFile, JSON.toJSONString(cacheMap));
        } catch (IOException e) {
            e.printStackTrace();
        }        }

    public synchronized void mergeCache(List<Path>filePaths,List<File> outFiles){
        try {
            String key = calculateKey(filePaths);
            keys.add(key);
            List<String>value = calculateCacheValue(outFiles);
            cacheMap.put(key,value);
            StringBuilder stringBuilder = new StringBuilder();
            for (Path path:filePaths){
                stringBuilder.append(path.toAbsolutePath()+",");
            }
            fileLogger.log("miss cache:"+stringBuilder.toString());

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public List<String>calculateCacheValue(List<File> outFiles){
        List<String> filePath = new ArrayList<>();
        for (File file:outFiles){
            filePath.add(file.getAbsolutePath());
        }

        return filePath;

    }

    private String calculateKey(List<Path> filePaths) throws Exception {
        Collections.sort(filePaths);
        String key = MD5Util.getMD5(getTransformParameters());
        for (Path path:filePaths){
            if (path.toFile().isDirectory()){
                Collection<File>files = FileUtils.listFiles(path.toFile(),new String[]{"dex"},true);
                List<File>dexFiles = new ArrayList<>(files);
                Collections.sort(dexFiles);
                for (File dexFile:dexFiles){
                    key = key+MD5Util.getFileMD5(dexFile);
                }
            }
            key = key+MD5Util.getFileMD5(path.toFile());
        }
        return MD5Util.getMD5(key+MD5Util.getMD5(type+version));

    }

    public synchronized List<Path> getCache(List<Path>filePaths){
        List<Path> results = new ArrayList<>();
        String key = null;
        try {
             key = calculateKey(filePaths);
            List<String> paths = cacheMap.get(key);
            if (paths == null){
                return ImmutableList.of();
            }
            for (String str:paths){
                if (new File(str).exists()) {
                    results.add(new File(str).toPath());
                }else {
                    clearAllFiles(paths);
                    cacheMap.remove(key);
                    return ImmutableList.of();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        StringBuilder stringBuilder = new StringBuilder();
        for (Path path:filePaths){
            stringBuilder.append(path.toAbsolutePath()+",");
        }

        fileLogger.log("hit cache:"+stringBuilder.toString());
        keys.add(key);
        return results;


    }


}
