package com.taobao.android.builder;

import org.gradle.api.Project;
import org.gradle.api.ProjectState;

/**
 * com.taobao.android.builder
 *
 * @author lilong
 * @time 7:11 PM
 * @date 2020/7/6
 */
public interface FakeProjectEvaluationListener {

    public void afterEvaluate(Project project);
}
