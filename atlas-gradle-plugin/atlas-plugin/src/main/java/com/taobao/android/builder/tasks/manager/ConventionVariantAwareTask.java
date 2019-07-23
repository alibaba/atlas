package com.taobao.android.builder.tasks.manager;

import com.android.build.gradle.internal.tasks.VariantAwareTask;
import org.gradle.api.internal.ConventionTask;
import org.jetbrains.annotations.NotNull;

/**
 * @ClassName ConventionVariantAwareTask
 * @Description TODO
 * @Author zhayu.ll
 * @Date 2019-07-22 10:07
 * @Version 1.0
 */
public class ConventionVariantAwareTask extends ConventionTask implements VariantAwareTask {

    protected  String variantName;
    @NotNull
    @Override
    public String getVariantName() {
        return variantName;
    }

    @Override
    public void setVariantName(@NotNull String s) {
        this.variantName = s;

    }
}
