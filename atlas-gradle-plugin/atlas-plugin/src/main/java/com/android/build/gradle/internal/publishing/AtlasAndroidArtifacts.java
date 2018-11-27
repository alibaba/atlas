package com.android.build.gradle.internal.publishing;

import com.android.annotations.NonNull;
import org.gradle.api.artifacts.type.ArtifactTypeDefinition;
import org.gradle.api.attributes.Attribute;

import static com.android.build.gradle.internal.publishing.AndroidArtifacts.PublishedConfigType.*;

/**
 * @author lilong
 * @create 2017-11-30 上午11:48
 */

public class AtlasAndroidArtifacts extends AndroidArtifacts{


    public static final String TYPE_AWB = "awb";

    public static final String TYPE_AP ="ap";

    public static final String TYPE_SOLIB ="solib";

    public static final String TYPE_AWO = "awo";

    public static final String TYPE_REMOVE_BUNDLE = "remotebundles";

    public static final String TYPE_PACKAGEID = "packageIdFile.properties";

    public static final String TYPE_ARSC = "resources.arsc";

    public static final String TYPE_VERSIONS = "version.properties";

    public static final String TYPE_DEPENDENCIES = "dependencies.txt";

    public static final String TYPE_R_TXT = "R.txt";

    public static final String TYPE_APK_FILES = "apk-files.txt";

    public static final String TYPE_BUILD_TXT = "build.txt";

    public static final String TYPE_MUPPBUNDLEINFO = "muppBundleInfo.json";



    public static final String TYPE_EXPLODED_AWB = "android-exploded-awb";

    public static final String TYPE_EXPLODED_AP = "android-exploded-ap";

    public static final String TYPE_EXPLODED_SOLIB = "android-exploded-solib";

    public static final String TYPE_LIBS = "libs";

    public static final String TYPE_ATLAS_JSON = "atlasFrameworkProperties.json";


    public static final Attribute<String> ARTIFACT_TYPE = Attribute.of("artifactType", String.class);

    // types for main artifacts
    public static final String TYPE_AAR = "aar";
    private static final String TYPE_APK = "apk";
    private static final String TYPE_JAR = ArtifactTypeDefinition.JAR_TYPE;

    // types for AAR content
    private static final String TYPE_CLASSES = "android-classes";
    private static final String TYPE_JAVA_RES = "android-java-res";
    private static final String TYPE_MANIFEST = "android-manifest";
    private static final String TYPE_MANIFEST_METADATA = "android-manifest-metadata";
    private static final String TYPE_ANDROID_RES = "android-res";
    private static final String TYPE_ASSETS = "android-assets";
    private static final String TYPE_JNI = "android-jni";
    private static final String TYPE_AIDL = "android-aidl";
    private static final String TYPE_RENDERSCRIPT = "android-renderscript";
    private static final String TYPE_LINT_JAR = "android-lint";
    private static final String TYPE_EXT_ANNOTATIONS = "android-ext-annot";
    private static final String TYPE_PUBLIC_RES = "android-public-res";
    private static final String TYPE_SYMBOL = "android-symbol";
    private static final String TYPE_SYMBOL_WITH_PACKAGE_NAME = "android-symbol-with-package-name";
    private static final String TYPE_PROGUARD_RULES = "android-proguad";
    private static final String TYPE_DATA_BINDING_ARTIFACT = "android-databinding";
    private static final String TYPE_EXPLODED_AAR = "android-exploded-aar";

    // types for additional artifacts to go with APK
    private static final String TYPE_MAPPING = "android-mapping";
    private static final String TYPE_METADATA = "android-metadata";

    // types for feature-split content.
    private static final String TYPE_FEATURE_IDS_DECLARATION = "android-feature-split-ids";
    private static final String TYPE_FEATURE_APPLICATION_ID = "android-feature-application-id";
    private static final String TYPE_FEATURE_RESOURCE_PKG = "android-feature-res-ap_";
    private static final String TYPE_FEATURE_TRANSITIVE_DEPS = "android-feature-transitive-deps";

    // types for metadata content.
    private static final String TYPE_METADATA_FEATURE_DECLARATION = "android-metadata-feature-decl";
    private static final String TYPE_METADATA_FEATURE_MANIFEST =
            "android-metadata-feature-manifest";
    private static final String TYPE_METADATA_APP_ID_DECLARATION = "android-metadata-app-id-decl";

    public enum ConsumedConfigType {
        COMPILE_CLASSPATH("compileClasspath", API_ELEMENTS, true),
        RUNTIME_CLASSPATH("runtimeClasspath", RUNTIME_ELEMENTS, true),
        ANNOTATION_PROCESSOR("annotationProcessorClasspath", RUNTIME_ELEMENTS, false),
        METADATA_VALUES("metadata", METADATA_ELEMENTS, false),
        BUNDLECOMPILE_CLASSPATH("bundleCompile",RUNTIME_ELEMENTS,true);

        @NonNull private final String name;
        @NonNull private final AndroidArtifacts.PublishedConfigType publishedTo;
        private final boolean needsTestedComponents;

        ConsumedConfigType(
                @NonNull String name,
                @NonNull AndroidArtifacts.PublishedConfigType publishedTo,
                boolean needsTestedComponents) {
            this.name = name;
            this.publishedTo = publishedTo;
            this.needsTestedComponents = needsTestedComponents;
        }

        @NonNull
        public String getName() {
            return name;
        }

        @NonNull
        public AndroidArtifacts.PublishedConfigType getPublishedTo() {
            return publishedTo;
        }

        public boolean needsTestedComponents() {
            return needsTestedComponents;
        }
    }

    public enum PublishedConfigType {
        API_ELEMENTS,
        RUNTIME_ELEMENTS,
        METADATA_ELEMENTS
    }

    public enum ArtifactScope {
        ALL, EXTERNAL, MODULE
    }


    public enum AtlasArtifactType {

        AWB(TYPE_AWB),
        EXPLODED_AWB(TYPE_EXPLODED_AWB),
        EXPLODED_AP(TYPE_EXPLODED_AP),
        AP(TYPE_AP),
        CLASSES(TYPE_CLASSES),
        // Jar file for annotation processor as both classes and resources are needed, and for building model
        JAR(TYPE_JAR),

        // manifest is published to both to compare and detect provided-only library dependencies.
        MANIFEST(TYPE_MANIFEST),
        MANIFEST_METADATA(TYPE_MANIFEST_METADATA),

        // API only elements.
        AIDL(TYPE_AIDL),
        RENDERSCRIPT(TYPE_RENDERSCRIPT),
        DATA_BINDING_ARTIFACT(TYPE_DATA_BINDING_ARTIFACT),

        // runtime only elements
        JAVA_RES(TYPE_JAVA_RES),
        ANDROID_RES(TYPE_ANDROID_RES),
        ASSETS(TYPE_ASSETS),
        SYMBOL_LIST(TYPE_SYMBOL),
        /**
         * The symbol list with the package name as the first line. As the r.txt format in the AAR
         * cannot be changed, this is created by prepending the package name from the
         * AndroidManifest.xml to the existing r.txt file.
         */
        SYMBOL_LIST_WITH_PACKAGE_NAME(TYPE_SYMBOL_WITH_PACKAGE_NAME),
        JNI(TYPE_JNI),
        ANNOTATIONS(TYPE_EXT_ANNOTATIONS),
        PUBLIC_RES(TYPE_PUBLIC_RES),
        PROGUARD_RULES(TYPE_PROGUARD_RULES),

        LINT(TYPE_LINT_JAR),

        APK_MAPPING(TYPE_MAPPING),
        APK_METADATA(TYPE_METADATA),
        APK(TYPE_APK),

        // Feature split related artifacts.
        FEATURE_IDS_DECLARATION(TYPE_FEATURE_IDS_DECLARATION),
        FEATURE_APPLICATION_ID_DECLARATION(TYPE_FEATURE_APPLICATION_ID),
        FEATURE_RESOURCE_PKG(TYPE_FEATURE_RESOURCE_PKG),
        FEATURE_TRANSITIVE_DEPS(TYPE_FEATURE_TRANSITIVE_DEPS),

        // Metadata artifacts
        METADATA_FEATURE_DECLARATION(TYPE_METADATA_FEATURE_DECLARATION),
        METADATA_FEATURE_MANIFEST(TYPE_METADATA_FEATURE_MANIFEST),
        METADATA_APP_ID_DECLARATION(TYPE_METADATA_APP_ID_DECLARATION),

        // types for querying only. Not publishable.
        AAR(TYPE_AAR),
        EXPLODED_AAR(TYPE_EXPLODED_AAR),
        BASE_AWO(TYPE_AWO),
        BASE_ATLAS_JSON(TYPE_ATLAS_JSON),
        BASE_REMOTE_BUNDLE(TYPE_REMOVE_BUNDLE),
        BASE_PACKAGE_IDS(TYPE_PACKAGEID),
        BASE_ARSC(TYPE_ARSC),
        BASE_VERSIONS(TYPE_VERSIONS),
        BASE_DEPENDENCIES(TYPE_DEPENDENCIES),
        BASE_R(TYPE_R_TXT),
        BASE_APK_FILES(TYPE_APK_FILES),
        BASE_BUILD_TXT(TYPE_BUILD_TXT),
        BASE_MUPP_JSON(TYPE_MUPPBUNDLEINFO),
        BASE_APK(TYPE_APK),
        BASE_MANIFEST(TYPE_MANIFEST),
        LIBS(TYPE_LIBS);


        @NonNull
        private final String type;

        AtlasArtifactType(@NonNull String type) {
            this.type = type;
        }

        @NonNull
        public String getType() {
            return type;
        }
    }
}
