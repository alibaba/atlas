package com.taobao.android.builder.extension.factory;

import com.android.annotations.NonNull;
import com.taobao.android.builder.extension.EnhanceConfig;
import org.gradle.api.NamedDomainObjectFactory;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;
import org.gradle.internal.reflect.Instantiator;

/**
 * EnhanceConfigFactory
 *
 * @author zhayu.ll
 * @date 18/3/14
 */
public class EnhanceConfigFactory implements NamedDomainObjectFactory<EnhanceConfig> {

    @NonNull
    private final Instantiator instantiator;
    @NonNull
    private final Project project;

    @NonNull
    private final Logger logger;

    public EnhanceConfigFactory(@NonNull Instantiator instantiator, @NonNull Project project, @NonNull Logger logger) {
        this.instantiator = instantiator;
        this.project = project;
        this.logger = logger;
    }

    @Override
    public EnhanceConfig create(String name) {
        return instantiator.newInstance(EnhanceConfig.class, name);
    }

}
