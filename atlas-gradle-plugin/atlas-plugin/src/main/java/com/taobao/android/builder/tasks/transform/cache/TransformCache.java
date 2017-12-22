package com.taobao.android.builder.tasks.transform.cache;

import com.alibaba.fastjson.JSON;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.Transform;
import org.gradle.api.Project;

import java.util.List;

/**
 * @author lilong
 * @create 2017-12-21 上午10:43
 */

public abstract class TransformCache<T> {

    protected Transform transform;

    protected Project project;

    public TransformCache(Project project, Transform transform) {
        this.transform = transform;
        this.project = project;
    }

    public abstract void cache(QualifiedContent qualifiedContent, List<T> result);


    public abstract void cache(List<QualifiedContent> qualifiedContents, List<T> result);


    public abstract List<T> getCache(QualifiedContent qualifiedContent);


    public abstract void saveContent();


    public String getTransformParameters() {

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(transform.getName());
        stringBuilder.append(transform.getInputTypes());
        stringBuilder.append(transform.getScopes());
        stringBuilder.append(JSON.toJSONString(transform.getParameterInputs()));
        return stringBuilder.toString();
    }
}
