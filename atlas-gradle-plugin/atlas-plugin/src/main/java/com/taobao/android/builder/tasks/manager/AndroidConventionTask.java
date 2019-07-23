package com.taobao.android.builder.tasks.manager;

import com.android.build.gradle.internal.tasks.AndroidBuilderTask;
import groovy.lang.Closure;
import org.gradle.api.Task;
import org.gradle.api.internal.ConventionMapping;
import org.gradle.api.internal.IConventionAware;
import org.gradle.api.tasks.Internal;
import org.gradle.internal.extensibility.ConventionAwareHelper;

import java.util.concurrent.Callable;

/**
 * @ClassName AndroidConventionTask
 * @Description TODO
 * @Author zhayu.ll
 * @Date 2019-07-22 10:10
 * @Version 1.0
 */
public class AndroidConventionTask extends AndroidBuilderTask implements IConventionAware {


    private ConventionMapping conventionMapping;

    public Task conventionMapping(String property, Callable<?> mapping) {
        getConventionMapping().map(property, mapping);
        return this;
    }

    public Task conventionMapping(String property, Closure mapping) {
        getConventionMapping().map(property, mapping);
        return this;
    }

    @Override
    @Internal
    public ConventionMapping getConventionMapping() {
        if (conventionMapping == null) {
            conventionMapping = new ConventionAwareHelper(this, getConvention());
        }
        return conventionMapping;
    }
}
