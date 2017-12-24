package com.taobao.android.builder.tasks.transform.cache;

import com.alibaba.fastjson.JSON;
import com.android.build.api.transform.*;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.taobao.android.builder.tools.MD5Util;
import com.taobao.android.builder.tools.log.FileLogger;
import org.apache.commons.io.FileUtils;
import org.gradle.api.Project;
import org.gradle.internal.impldep.com.amazonaws.util.Md5Utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

/**
 * @author lilong
 * @create 2017-12-21 上午10:43
 */

public class DexCache extends TransformCache<File> {

    protected File cacheFile;

    private static final String AWB_CACHE = "awb-cache.json";

    protected String type;

    protected String version;

    private Set<String> keys = new HashSet<>();

    private FileLogger fileLogger = FileLogger.getInstance("dexcache");

    private boolean increment;


    protected Map<String, List<String>> cacheMap = new HashMap<>();

    public DexCache(Project project, Transform transform, String type, String version, File location) {
        super(project, transform);
        this.type = type;

        increment = transform.isIncremental();

        this.version = version;

        cacheFile = new File(location, AWB_CACHE);

        readLocalCache();

    }

    @Override
    public synchronized void cache(QualifiedContent qualifiedContent, List<File> result) {

        try {
            String key = calculateKey(qualifiedContent.getFile());
            cacheMap.put(key, toString(result));
            keys.add(key);
            StringBuilder stringBuilder = new StringBuilder();
            for (File file1 : result) {
                stringBuilder.append(file1.getAbsolutePath() + ",");
            }
            String log = "miss cache:" + qualifiedContent.getFile().getAbsolutePath() + " newCache:" + stringBuilder.toString();
            project.getLogger().info(log);
            fileLogger.log(log);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private String calculateKey(File file) throws Exception {
        String key = null;
        if (file.isFile()) {
            key = MD5Util.getMD5(MD5Util.getMD5(getTransformParameters()) + MD5Util.getFileMD5(file) + MD5Util.getMD5(type + version));
        } else if (file.isDirectory()) {
            Collection<File> files = FileUtils.listFiles(file, new String[]{"class"}, true);
            ArrayList<File> classFiles = new ArrayList<>();
            classFiles.addAll(files);
            Collections.sort(classFiles);
            key = MD5Util.getMD5(getTransformParameters());
            for (File classFile : classFiles) {
                key = key + MD5Util.getFileMD5(classFile);
            }

            key = MD5Util.getMD5(key + MD5Util.getMD5(type + version));
        }
        return key;
    }

    @Override
    public void cache(List<QualifiedContent> qualifiedContents, List<File> result) {

    }

    private List<String> toString(List<File> result) {
        List<String> strs = new ArrayList<>();
        for (File file : result) {
            strs.add(file.getAbsolutePath());
        }
        return strs;
    }

    @Override
    public synchronized List<File> getCache(QualifiedContent qualifiedContent) {
        File file = qualifiedContent.getFile();
        List<File> files = new ArrayList<>();
        try {
            String key = calculateKey(file);
            List<String> strings = cacheMap.get(key);
            if (strings == null) {
                return ImmutableList.of();
            }
            keys.add(key);
            for (String s : strings) {
                files.add(new File(s));
            }
            if (files.size() == 0) {
                cacheMap.remove(key);
                return ImmutableList.of();
            }

            for (File cacheFile : files) {
                if (!cacheFile.exists()) {
                    clearAllFiles(files);
                    cacheMap.remove(key);

                    return ImmutableList.of();
                }
            }

            if (qualifiedContent instanceof JarInput) {
                Status status = ((JarInput) qualifiedContent).getStatus();
                if (status.equals(Status.REMOVED) || !qualifiedContent.getFile().exists()) {
                    clearAllFiles(files);
                    cacheMap.remove(key);
                    return ImmutableList.of();
                }
            } else if (qualifiedContent instanceof DirectoryInput) {
                Map<File, Status> map = ((DirectoryInput) qualifiedContent).getChangedFiles();
                if (map.values().contains(Status.CHANGED) || map.values().contains(Status.REMOVED) || map.values().contains(Status.ADDED) || !qualifiedContent.getFile().exists()) {
                    clearAllFiles(files);
                    cacheMap.remove(key);
                    return ImmutableList.of();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        StringBuilder stringBuilder = new StringBuilder();
        for (File file1 : files) {
            stringBuilder.append(file1.getAbsolutePath() + ",");
        }

        String log = "hit cache:" + qualifiedContent.getFile().getAbsolutePath() + " cache:" + stringBuilder.toString();
        project.getLogger().info(log);
        fileLogger.log(log);
        return files;
    }

    protected void clearAllFiles(List<?> files) {

        for (Object file : files) {
            if (file.getClass() == String.class) {
                if (new File(String.valueOf(file)).exists()) {
                    FileUtils.deleteQuietly(new File(String.valueOf(file)));
                }
            } else if (file.getClass() == File.class) {
                if (((File) file).exists()) {
                    FileUtils.deleteQuietly((File) file);
                }
            } else if (file instanceof Path) {
                if (((Path) file).toFile().exists()) {
                    FileUtils.deleteQuietly(((Path) file).toFile());

                }
            }
        }
    }

    protected void readLocalCache() {
        if (cacheFile.exists()) {
            try {
                cacheMap = JSON.parseObject(FileUtils.readFileToString(cacheFile), Map.class);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }


    public void saveContent() {
        if (cacheFile.exists()) {
            FileUtils.deleteQuietly(cacheFile);
        } else {
            cacheFile.getParentFile().mkdirs();
        }

        if (!increment) {
            Iterator iterator = cacheMap.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry entry = (Map.Entry) iterator.next();
                if (!keys.contains(entry.getKey())) {
                    iterator.remove();
                }
            }
        }
        try {
            FileUtils.writeStringToFile(cacheFile, JSON.toJSONString(cacheMap));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
