# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

-dontpreverify
-dontusemixedcaseclassnames
#-optimizations code/removal/simple,code/removal/advanced,method/removal/parameter,method/inlining/short,method/inlining/tailrecursion
#-optimizations code/removal/simple,code/removal/advanced,code/removal/variable,code/removal/exception,code/simplification/branch,code/simplification/field,code/simplification/cast,code/simplification/arithmetic,code/simplification/variable
-dontobfuscate
-dontoptimize
-dontshrink
-keepattributes Exceptions,InnerClasses,Signature,SourceFile,LineNumberTable,*Annotation*
-renamesourcefileattribute Taobao
#-keepattributes Exceptions,InnerClasses,Signature,LineNumberTable,*Annotation*
#-repackageclasses 'com.taobao.taobao'
#-allowaccessmodification
#-printmapping map.txt
#-applymapping mapping.txt
-optimizationpasses 1
-target 1.6
-verbose

#-verbose
-dontskipnonpubliclibraryclasses
-dontskipnonpubliclibraryclassmembers

-dontwarn org.mozilla.javascript.**
-dontwarn org.mozilla.classfile.**
-dontwarn java.awt.**
-dontwarn com.google.android.maps.**
-dontwarn android.support.v7.widget.**
-dontwarn android.support.v4.**
-dontwarn com.tencent.mm.sdk.**
-dontwarn ***



