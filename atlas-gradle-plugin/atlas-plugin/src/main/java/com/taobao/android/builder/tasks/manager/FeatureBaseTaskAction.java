package com.taobao.android.builder.tasks.manager;

import com.android.build.gradle.api.BaseVariantOutput;
import com.android.build.gradle.internal.api.VariantContext;
import com.android.build.gradle.internal.tasks.VariantAwareTask;
import com.taobao.android.builder.dependency.model.AwbBundle;
import org.gradle.api.Task;

/**
 * @ClassName FeatureBaseTaskAction
 * @Description TODO
 * @Author zhayu.ll
 * @Date 2019-08-21 09:30
 * @Version 1.0
 */
public abstract class FeatureBaseTaskAction<T extends Task & VariantAwareTask>extends MtlBaseTaskAction<T> {

    protected AwbBundle awbBundle;

    public FeatureBaseTaskAction(AwbBundle awbBundle, VariantContext variantContext, BaseVariantOutput baseVariantOutput) {
        super(variantContext, baseVariantOutput);
        this.awbBundle = awbBundle;
    }
}
