package com.taobao.android.builder.insant;

import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInvocation;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.scope.InstantRunVariantScope;
import com.android.build.gradle.internal.transforms.InstantRunSlicer;
import org.gradle.api.logging.Logger;

import java.io.IOException;
import java.util.Set;

/**
 * TaobaoInstantRunSlicer
 *
 * @author zhayu.ll
 * @date 18/10/12
 */
public class TaobaoInstantRunSlicer extends InstantRunSlicer {

    public TaobaoInstantRunSlicer(Logger logger, InstantRunVariantScope variantScope) {
        super(logger, variantScope);
    }

    @Override
    public Set<QualifiedContent.Scope> getScopes() {
        return TransformManager.SCOPE_FULL_PROJECT;
    }

    @Override
    public void transform(TransformInvocation transformInvocation) throws IOException, TransformException, InterruptedException {
        super.transform(transformInvocation);
    }
}
