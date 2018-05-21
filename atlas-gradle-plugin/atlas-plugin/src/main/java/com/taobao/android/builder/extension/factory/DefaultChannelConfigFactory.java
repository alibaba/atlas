package com.taobao.android.builder.extension.factory;

import com.taobao.android.builder.extension.DefaultChannelConfig;

import org.gradle.api.NamedDomainObjectFactory;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;
import org.gradle.internal.reflect.Instantiator;
import com.android.annotations.NonNull;

/**
 * Created by jason on 18/3/20.
 */

public class DefaultChannelConfigFactory implements NamedDomainObjectFactory<DefaultChannelConfig> {

    @NonNull
    private final Instantiator instantiator;
    @NonNull
    private final Project project;
    @NonNull
    private final Logger logger;

    public DefaultChannelConfigFactory(Instantiator instantiator, Project project, Logger logger) {
        this.instantiator = instantiator;
        this.project = project;
        this.logger = logger;
    }

    @Override
    public DefaultChannelConfig create(String name) {
        return instantiator.newInstance(DefaultChannelConfig.class, name);
    }
}
