package com.taobao.android.builder.tasks.transform.cache;

import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.Transform;
import com.google.common.collect.ImmutableList;
import org.gradle.api.Project;

import java.util.List;
/**
 * @author lilong
 * @create 2017-12-21 上午11:20
 */
public class NoCache extends TransformCache {

    public NoCache(Project project,Transform transform) {
        super(project, transform);
    }

    @Override
    public void cache(QualifiedContent qualifiedContent, List result) {

    }

    @Override
    public List getCache(QualifiedContent qualifiedContent) {
        return ImmutableList.of();
    }

    @Override
    public void saveContent() {

    }

    @Override
    public void cache(List list, List result) {

    }
}
