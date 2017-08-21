/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.build.gradle.tasks.factory;

import com.android.annotations.NonNull;
import com.android.annotations.VisibleForTesting;
import com.android.build.gradle.AndroidGradleOptions;
import com.android.build.gradle.internal.CompileOptions;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.builder.model.SyncIssue;
import com.android.sdklib.AndroidTargetHash;
import com.android.sdklib.AndroidVersion;
import com.android.utils.ILogger;
import com.google.common.base.Joiner;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.gradle.api.JavaVersion;
import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileTree;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.compile.AbstractCompile;

/**
 * Common code for configuring {@link AbstractCompile} instances.
 */
public class AbstractCompilesUtil {

    /**
     * Determines the java language level to use and sets it on the given task and
     * {@link CompileOptions}. The latter is to propagate the information to Studio.
     */
    public static void configureLanguageLevel(
            AbstractCompile compileTask,
            final CompileOptions compileOptions,
            String compileSdkVersion,
            boolean jackEnabled) {
        setDefaultJavaVersion(compileOptions, compileSdkVersion, jackEnabled);
        compileTask.setSourceCompatibility(compileOptions.getSourceCompatibility().toString());
        compileTask.setTargetCompatibility(compileOptions.getTargetCompatibility().toString());
    }

    public static void setDefaultJavaVersion(
            final CompileOptions compileOptions,
            String compileSdkVersion,
            boolean jackEnabled) {
        compileOptions.setDefaultJavaVersion(
                chooseDefaultJavaVersion(
                        compileSdkVersion,
                        System.getProperty("java.specification.version"),
                        jackEnabled));
    }

    @NonNull
    @VisibleForTesting
    static JavaVersion chooseDefaultJavaVersion(
            @NonNull String compileSdkVersion,
            @NonNull String currentJdkVersion,
            boolean jackEnabled) {
        final AndroidVersion hash = AndroidTargetHash.getVersionFromHash(compileSdkVersion);
        Integer compileSdkLevel = (hash == null ? null : hash.getFeatureLevel());

        JavaVersion javaVersionToUse;
        if (compileSdkLevel == null) {
            javaVersionToUse = JavaVersion.VERSION_1_6;
        } else {
            if (0 < compileSdkLevel && compileSdkLevel <= 20) {
                javaVersionToUse = JavaVersion.VERSION_1_6;
            } else if (21 <= compileSdkLevel && compileSdkLevel < 24) {
                javaVersionToUse = JavaVersion.VERSION_1_7;
            } else {
                if (jackEnabled) {
                    javaVersionToUse = JavaVersion.VERSION_1_8;
                } else {
                    javaVersionToUse = JavaVersion.VERSION_1_7;
                }
            }
        }

        JavaVersion jdkVersion = JavaVersion.toVersion(currentJdkVersion);

        if (jdkVersion.compareTo(javaVersionToUse) < 0) {
            Logging.getLogger(AbstractCompilesUtil.class).warn(
                    "Default language level for compileSdkVersion '{}' is " +
                            "{}, but the JDK used is {}, so the JDK language level will be used.",
                    compileSdkVersion,
                    javaVersionToUse,
                    jdkVersion);
            javaVersionToUse = jdkVersion;
        }
        return javaVersionToUse;
    }

    public static boolean isIncremental(Project project, VariantScope variantScope,
            CompileOptions compileOptions, Collection<File> processorPath, ILogger log) {
        boolean incremental = true;
        if (compileOptions.getIncremental() != null) {
            incremental = compileOptions.getIncremental();
            log.verbose("Incremental flag set to %1$b in DSL", incremental);
        } else {
            if (variantScope.getGlobalScope().getExtension().getDataBinding().isEnabled()
                    || !processorPath.isEmpty()
                    || project.getPlugins().hasPlugin("com.neenbedankt.android-apt")
                    || project.getPlugins().hasPlugin("me.tatarka.retrolambda")) {
                incremental = false;
                log.verbose("Incremental Java compilation disabled in variant %1$s "
                                + "as you are using an incompatible plugin",
                        variantScope.getVariantConfiguration().getFullName());
            } else if (variantScope.getTestedVariantData() != null) {
                // Incremental javac is currently (Gradle 2.14-2.14.1) broken for invocations
                // that have directories on their classpath.
                incremental = false;
            } else if (com.android.ide.common.repository.GradleVersion.parse("4.0").compareTo(project.getGradle().getGradleVersion()) > 0) {
                // For now, default to true, unless the use uses several source folders,
                // in that case, we cannot guarantee that the incremental java works fine.

                // some source folders may be configured but do not exist, in that case, don't
                // use as valid source folders to determine whether or not we should turn on
                // incremental compilation.
                List<File> sourceFolders = new ArrayList<File>();
                for (ConfigurableFileTree sourceFolder
                        : variantScope.getVariantData().getUserJavaSources()) {

                    if (sourceFolder.getDir().exists()) {
                        sourceFolders.add(sourceFolder.getDir());
                    }
                }
                incremental = sourceFolders.size() == 1;
                if (sourceFolders.size() > 1) {
                    log.verbose("Incremental Java compilation disabled in variant %1$s "
                                    + "as you are using %2$d source folders : %3$s",
                            variantScope.getVariantConfiguration().getFullName(),
                            sourceFolders.size(), Joiner.on(',').join(sourceFolders));
                }
            }
        }

        if (AndroidGradleOptions.isJavaCompileIncrementalPropertySet(project)) {
            variantScope.getGlobalScope().getAndroidBuilder().getErrorReporter().handleSyncError(
                    null,
                    SyncIssue.TYPE_GENERIC,
                    String.format(
                            "The %s property has been replaced by a DSL property. Please add the "
                                    + "following to your build.gradle instead:\n"
                                    + "android {\n"
                                    + "  compileOptions.incremental = false\n"
                                    + "}",
                            AndroidGradleOptions.PROPERTY_INCREMENTAL_JAVA_COMPILE));
        }
        return incremental;
    }
}
