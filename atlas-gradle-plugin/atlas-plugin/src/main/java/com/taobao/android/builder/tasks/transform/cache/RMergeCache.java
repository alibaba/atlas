package com.taobao.android.builder.tasks.transform.cache;


import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.Transform;
import org.gradle.api.Project;

import java.util.List;

/**
 * @author lilong
 * @create 2017-12-21 上午10:43
 */

public class RMergeCache extends TransformCache {

    public RMergeCache(Project project,Transform transform) {
        super(project, transform);
    }

    @Override
    public void cache(QualifiedContent qualifiedContent, List result) {

    }

    @Override
    public List getCache(QualifiedContent qualifiedContent) {
        return null;
    }

    @Override
    public void saveContent() {

    }

    @Override
    public void cache(List list, List result) {

    }
}
