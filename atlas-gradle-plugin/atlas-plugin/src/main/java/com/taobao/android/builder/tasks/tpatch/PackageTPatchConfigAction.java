package com.taobao.android.builder.tasks.tpatch;

import com.android.annotations.NonNull;
import com.android.build.gradle.api.BaseVariantOutput;
import com.android.build.gradle.internal.api.AppVariantContext;
import com.android.build.gradle.internal.api.VariantContext;
import com.taobao.android.builder.tasks.manager.MtlBaseTaskAction;
import org.gradle.api.tasks.Sync;

/**
 * Created by chenhjohn on 2017/5/13.
 */

public class PackageTPatchConfigAction extends MtlBaseTaskAction<Sync> {
    public PackageTPatchConfigAction(VariantContext variantContext, BaseVariantOutput baseVariantOutputData) {
        super(variantContext, baseVariantOutputData);
    }

    @NonNull
    @Override
    public String getName() {
        return scope.getTaskName("package", "TPatch");
    }

    @NonNull
    @Override
    public Class<Sync> getType() {
        return Sync.class;
    }

    @Override
    public void execute(@NonNull Sync packageRenderscript) {
        packageRenderscript.from(getAppVariantOutputContext().getApkOutputFile(true));
        packageRenderscript.from(((AppVariantContext)variantContext).getAwbApkFiles());
    }
}
