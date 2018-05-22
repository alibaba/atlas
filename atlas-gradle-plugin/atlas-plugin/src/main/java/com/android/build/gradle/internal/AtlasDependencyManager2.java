package com.android.build.gradle.internal;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.ide.AtlasDependencyGraph;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.builder.dependency.level2.AndroidDependency;
import com.android.builder.model.SyncIssue;
import com.android.builder.model.level2.DependencyGraphs;
import org.gradle.api.Project;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.function.Consumer;

/**
 * @author lilong
 * @create 2017-12-01 上午10:09
 */

public class AtlasDependencyManager2 {

    private static final Logger sLogger = LoggerFactory.getLogger(AtlasDependencyManager2.class);

    private final Project project;

    private final ExtraModelInfo extraModelInfo;

//    private ApDependencies apDependencies;

    public AtlasDependencyManager2(@NonNull Project project, @NonNull ExtraModelInfo extraModelInfo) {
        this.project = project;
        this.extraModelInfo = extraModelInfo;
    }


    public Set<AndroidDependency> resolveDependencies(@NonNull VariantScope variantScope) {
//        this.apDependencies = resolveApDependencies(variantDeps);

        AtlasDependencyGraph artifactDependencyGraph = new AtlasDependencyGraph();

        DependencyGraphs dependencyGraphs = artifactDependencyGraph.createLevel4DependencyGraph(variantScope, false, true, new Consumer<SyncIssue>() {
            @Override
            public void accept(SyncIssue syncIssue) {
                sLogger.error(syncIssue.getMessage());
            }
        });


        return null;

    }

}
