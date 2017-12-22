package com.taobao.android.builder.tasks.transform.cache;
import com.android.build.api.transform.Transform;
import com.android.build.api.transform.TransformInvocation;
import com.android.build.api.transform.TransformOutputProvider;
import com.android.build.gradle.internal.pipeline.IntermediateFolderUtils;
import com.taobao.android.builder.tools.ReflectUtils;
import org.gradle.api.Project;
import proguard.classfile.Clazz;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
/**
 * @author lilong
 * @create 2017-12-21 上午10:43
 */
public class CacheFactory {

    private static Map<String,TransformCache> caches = new HashMap<>();


    public static TransformCache get(Project project,String id, String versionName, Transform transform, TransformInvocation transformInvocation, Class<? extends TransformCache> clazz){
        TransformCache instance = null;
        if (caches.containsKey(id)){
            return caches.get(id);
        }
        TransformOutputProvider tr = transformInvocation.getOutputProvider();

        IntermediateFolderUtils intermediateFolderUtils = (IntermediateFolderUtils) ReflectUtils.getField(tr,"folderUtils");

        File location = intermediateFolderUtils.getRootFolder();
        try {
             instance = clazz.getDeclaredConstructor(Project.class,Transform.class,String.class,String.class,File.class).newInstance(project,transform,id,versionName,location);
            caches.put(id,instance);
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();

        }
        return instance;
    }
}
