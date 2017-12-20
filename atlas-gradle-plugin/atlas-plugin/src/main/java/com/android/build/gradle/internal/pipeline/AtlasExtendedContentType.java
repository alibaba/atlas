package com.android.build.gradle.internal.pipeline;

import com.android.build.api.transform.QualifiedContent.ContentType;
import com.android.build.api.transform.QualifiedContent.DefaultContentType;
import com.google.common.collect.ImmutableSet;

import java.util.Set;

/**
 * Content types private to theAtlas Android Plugin.
 */
public enum AtlasExtendedContentType implements ContentType {

    // /**
    //  * The content is dex files.
    //  */
    // DEX(0x1000),
    //
    // /**
    //  * Content is a native library.
    //  */
    // NATIVE_LIBS(0x2000),
    //
    // /**
    //  * Instant Run '$override' classes, which contain code of new method bodies.
    //  *
    //  * <p>This stream also contains the AbstractPatchesLoaderImpl class for applying HotSwap
    //  * changes.
    //  */
    // CLASSES_ENHANCED(0x4000),
    //
    // /**
    //  * The content is Jack library.
    //  *
    //  * This is zip file containing classes in jayce format.
    //  * If the library has been pre-dexed it will also contain the corresponding dex.
    //  */
    // JACK(0x8000),

    AWB_CLASSES(0x03),

    AWB_RESOURCES(0x04),

    AWB_APKS(0x1000000);

    /**
     * The content is an artifact exported by the data binding compiler.
     */

    private final int value;

    AtlasExtendedContentType(int value) {
        this.value = value;
    }

    @Override
    public int getValue() {
        return value;
    }

    /**
     * Returns all {@link DefaultContentType} and {@link AtlasExtendedContentType} content types.
     *
     * @return a set of all known {@link ContentType}
     */
    public static Set<ContentType> getAllContentTypes() {
        return allContentTypes;
    }

    private static final Set<ContentType> allContentTypes;

    static {
        ImmutableSet.Builder<ContentType> builder = ImmutableSet.builder();
        for (DefaultContentType contentType : DefaultContentType.values()) {
            builder.add(contentType);
        }
        for (AtlasExtendedContentType extendedContentType : AtlasExtendedContentType.values()) {
            builder.add(extendedContentType);
        }
        allContentTypes = builder.build();
    }
}