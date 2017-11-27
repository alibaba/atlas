package com.taobao.android.builder.manager;

import com.android.build.gradle.AppExtension;
import com.android.build.gradle.BaseExtension;
import com.android.build.gradle.api.ApplicationVariant;
import com.android.build.gradle.internal.api.AppVariantContext;
import com.android.builder.core.AtlasBuilder;
import com.taobao.android.builder.AtlasBuildContext;
import com.taobao.android.builder.extension.AtlasExtension;
import com.taobao.android.builder.tasks.app.bundle.JavacAwbsTask;
import com.taobao.android.builder.tasks.dexpatch.*;
import com.taobao.android.builder.tasks.manager.MtlTaskContext;
import com.taobao.android.builder.tasks.manager.MtlTaskInjector;
import org.gradle.api.Project;
import org.gradle.api.tasks.compile.JavaCompile;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * @author lilong
 * @create 2017-08-23 In the morning he
 */

public class DexPatchTaskManager extends AtlasBaseTaskManager {
    private AppExtension appExtension;

    public DexPatchTaskManager(AtlasBuilder androidBuilder, BaseExtension androidExtension, Project project, AtlasExtension atlasExtension) {
        super(androidBuilder, androidExtension, project, atlasExtension);
        this.appExtension = (AppExtension) androidExtension;
    }

    @Override
    public void runTask() {

        appExtension.getApplicationVariants().forEach(new Consumer<ApplicationVariant>() {

            @Override
            public void accept(ApplicationVariant applicationVariant) {

                AppVariantContext appVariantContext = AtlasBuildContext.sBuilderAdapter.appVariantContextFactory
                        .getAppVariantContext(project, applicationVariant);

        List<MtlTaskContext> mtlTaskContextList = new ArrayList<>();


        mtlTaskContextList.add(new MtlTaskContext(JavacAwbsTask.class));
                mtlTaskContextList.add(new MtlTaskContext(JavaCompile.class));
                mtlTaskContextList.add(new MtlTaskContext(DiffDependenciesTask.ConfigAction.class, null));
                mtlTaskContextList.add(new MtlTaskContext(PrepareBaseFileTask.ConfigAction.class,null));
        mtlTaskContextList.add(new MtlTaskContext(DexPatchProguardTask.ConfigAction.class,null));

        mtlTaskContextList.add(new MtlTaskContext(DexPatchDexTask.ConfigAction.class, null));

        mtlTaskContextList.add(new MtlTaskContext(DexPatchDiffTask.ConfigAction.class,null));

        mtlTaskContextList.add(new MtlTaskContext(DexPatchPackageTask.ConfigAction.class, null));

        new MtlTaskInjector(appVariantContext).injectTasks(mtlTaskContextList, null);

        }
    });
    }
}
