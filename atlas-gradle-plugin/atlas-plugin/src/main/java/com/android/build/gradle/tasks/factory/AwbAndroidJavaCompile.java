package com.android.build.gradle.tasks.factory;

import com.android.build.gradle.internal.incremental.InstantRunBuildContext;
import com.android.sdklib.AndroidTargetHash;
import com.android.sdklib.AndroidVersion;
import com.android.utils.FileUtils;
import com.taobao.android.builder.dependency.model.AwbBundle;
import org.gradle.api.JavaVersion;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.incremental.IncrementalTaskInputs;

/**
 * @author lilong
 * @create 2017-12-08 上午3:43
 */

@CacheableTask
public class AwbAndroidJavaCompile extends AndroidJavaCompile {

    private static boolean analyticsed = false;

    private AwbBundle awbBundle;

    public void setAwbBundle(AwbBundle awbBundle){
        this.awbBundle = awbBundle;
    }

    @Override
    protected void compile(IncrementalTaskInputs inputs) {
        getLogger().info(
                "Compiling with source level {} and target level {}.",
                getSourceCompatibility(),
                getTargetCompatibility());
        if (isPostN()) {
            if (!JavaVersion.current().isJava8Compatible()) {
                throw new RuntimeException("compileSdkVersion '" + compileSdkVersion + "' requires "
                        + "JDK 1.8 or later to compile.");
            }
        }

        if (awbBundle.isDataBindEnabled() && !analyticsed) {
            processAnalytics();
            analyticsed = true;
        }

        // Create directory for output of annotation processor.
        FileUtils.mkdirs(annotationProcessorOutputFolder);

        mInstantRunBuildContext.startRecording(InstantRunBuildContext.TaskType.JAVAC);
        compile();
        mInstantRunBuildContext.stopRecording(InstantRunBuildContext.TaskType.JAVAC);
    }

    private boolean isPostN() {
        final AndroidVersion hash = AndroidTargetHash.getVersionFromHash(compileSdkVersion);
        return hash != null && hash.getApiLevel() >= 24;
    }
}
