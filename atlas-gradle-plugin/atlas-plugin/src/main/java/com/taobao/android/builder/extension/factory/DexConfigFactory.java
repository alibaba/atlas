package com.taobao.android.builder.extension.factory;

import com.android.annotations.NonNull;
import com.taobao.android.builder.extension.DexConfig;
import org.gradle.api.NamedDomainObjectFactory;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;
import org.gradle.internal.reflect.Instantiator;

/**
 * @author lilong
 * @create 2017-05-26 On the afternoon of mount zalmon
 */

public class DexConfigFactory implements NamedDomainObjectFactory<DexConfig> {

    @NonNull
    private final Instantiator instantiator;
    @NonNull
    private final Project project;

    @NonNull
    private final Logger logger;

    public DexConfigFactory(@NonNull Instantiator instantiator, @NonNull Project project, @NonNull Logger logger) {
        this.instantiator = instantiator;
        this.project = project;
        this.logger = logger;
    }

    @Override
    public DexConfig create(String name) {
        return instantiator.newInstance(DexConfig.class, name);
    }
}
