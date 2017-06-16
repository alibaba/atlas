package com.android.build.gradle.internal.pipeline;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.QualifiedContent.ContentType;
import com.google.common.collect.ImmutableSet;

/**
 * Created by chenhjohn on 2017/6/16.
 */

public enum AtlasExtendedContentType implements QualifiedContent.ContentType {
    AWB_APKS(ExtendedContentType.values()[ExtendedContentType.values().length - 1].getValue() << 1),
    AWB_BASE_APK(AWB_APKS.getValue() << 1);

    private final int value;

    AtlasExtendedContentType(int value) {
        this.value = value;
    }

    @Override
    public int getValue() {
        return value;
    }

    static {
        ImmutableSet.Builder<ContentType> builder = ImmutableSet.builder();
        builder.addAll(ExtendedContentType.getAllContentTypes());
        for (AtlasExtendedContentType extendedContentType : AtlasExtendedContentType.values()) {
            builder.add(extendedContentType);
        }
        try {
            Field field = ExtendedContentType.class.getDeclaredField("");
            field.setAccessible(true);
            Field modifiersField = Field.class.getDeclaredField("modifiers");
            modifiersField.setAccessible(true);
            modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);
            field.set(null, builder.build());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
