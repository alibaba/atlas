package com.android.build.gradle.internal.scope;

import com.android.annotations.NonNull;
import groovy.lang.GroovyObject;
import org.gradle.api.Task;
import org.gradle.api.internal.ConventionMapping;
import org.gradle.api.internal.ConventionTask;

import java.util.concurrent.Callable;

/**
 * @author lilong
 * @create 2017-11-30 下午2:37
 */

public final class ConventionMappingHelper {
    private ConventionMappingHelper() {}

    public static void map(@NonNull Task task, @NonNull String key, @NonNull Callable<?> value) {
        if (task instanceof ConventionTask) {
            ((ConventionTask) task).getConventionMapping().map(key, value);
        } else if (task instanceof GroovyObject) {
            ConventionMapping conventionMapping =
                    (ConventionMapping) ((GroovyObject) task).getProperty("conventionMapping");
            conventionMapping.map(key, value);
        } else {
            throw new IllegalArgumentException(
                    "Don't know how to apply convention mapping to task of type " + task.getClass().getName());
        }
    }
}
