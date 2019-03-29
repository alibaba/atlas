package com.android.build.gradle.internal.incremental;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.utils.FileUtils;
import com.android.utils.ILogger;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import com.taobao.android.builder.insant.matcher.Imatcher;
import com.taobao.android.builder.insant.matcher.ImplementsMatcher;
import com.taobao.android.builder.insant.matcher.MatcherCreator;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.SerialVersionUIDAdder;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * 创建日期：2018/11/6 on 下午7:41
 * 描述:
 * 作者:zhayu.ll
 */
public class TBIncrementalVisitor extends IncrementalVisitor {

    public TBIncrementalVisitor(ClassNode classNode, List<ClassNode> parentNodes, ClassVisitor classVisitor, ILogger logger) {
        super(classNode, parentNodes, classVisitor, logger);
    }

    public interface InjectErrorListener {

        void onError(ErrorType errorType);

    }

    public static final String ALI_RUNTIME_PACKAGE = "com/android/alibaba/ip/runtime";
    public static final String ALI_ABSTRACT_PATCHES_LOADER_IMPL =
            ALI_RUNTIME_PACKAGE + "/AbstractPatchesLoaderImpl";
    public static final String ALI_APP_PATCHES_LOADER_IMPL = ALI_RUNTIME_PACKAGE + "/AppPatchesLoaderImpl";


    protected static final Type ALI_INSTANT_RELOAD_EXCEPTION =
            Type.getObjectType(ALI_RUNTIME_PACKAGE + "/InstantReloadException");

    public static final String ALI_DISPATCHING_THIS_SIGNATURE =
            "([Ljava/lang/Object;"
                    + ALI_INSTANT_RELOAD_EXCEPTION.getDescriptor() + ")V";

    protected static final Type ALI_RUNTIME_TYPE =
            Type.getObjectType(ALI_RUNTIME_PACKAGE + "/AndroidInstantRuntime");
    public static final Type ALI_DISABLE_ANNOTATION_TYPE =
            Type.getObjectType("com/android/alibaba/ip/api/DisableInstantRun");
    public static final Type TARGET_API_TYPE =
            Type.getObjectType("android/annotation/TargetApi");

    protected static final boolean TRACING_ENABLED = Boolean.getBoolean("FDR_TRACING");

    public static final Type ALI_CHANGE_TYPE = Type.getObjectType(ALI_RUNTIME_PACKAGE + "/IpChange");


    public static final Type ADD_CLASS =
            Type.getObjectType("com/android/alibaba/ip/api/AddClass");

    public static final Type MODIFY_CLASS =
            Type.getObjectType("com/android/alibaba/ip/api/ModifyClass");

    public static final Type MODIFY_FIELD =
            Type.getObjectType("com/android/alibaba/ip/api/ModifyField");

    public static final Type MODIFY_METHOD =
            Type.getObjectType("com/android/alibaba/ip/api/ModifyMethod");

    public static final Type ADD_FIELD =
            Type.getObjectType("com/android/alibaba/ip/api/AddField");


    public static final Type ADD_METHOD =
            Type.getObjectType("com/android/alibaba/ip/api/AddMethod");


    public enum ErrorType {
        R_CLASS, INTERFACE, NEW_API, PACKAGE_DISABLED, NO_METHOD, SUB_EXCEEDED, IMPLEMENTS
    }

    @Nullable
    public static File instrumentClass(
            int targetApiLevel,
            @NonNull File inputRootDirectory,
            @NonNull File inputFile,
            @NonNull File outputDirectory,
            @NonNull VisitorBuilder visitorBuilder,
            @NonNull ILogger logger,
            InjectErrorListener injectErrorListener,
            boolean addSerialVersionUID, boolean patchInitMethod, boolean patchEachMethod,boolean supportAddCallSuper,int count) throws IOException {

        byte[] classBytes;
        String path = FileUtils.relativePath(inputFile, inputRootDirectory);

        // if the class is not eligible for IR, return the non instrumented version or null if
        // the override class is requested.
        if (!isClassEligibleForInstantRun(inputFile)) {
            if (injectErrorListener != null) {
                injectErrorListener.onError(ErrorType.R_CLASS);
            }
            if (visitorBuilder.getOutputType() == OutputType.INSTRUMENT) {
                File outputFile = new File(outputDirectory, path);
                Files.createParentDirs(outputFile);
                Files.copy(inputFile, outputFile);
                return outputFile;
            } else {
                return null;
            }
        }
        classBytes = Files.toByteArray(inputFile);
        ClassReader classReader = new ClassReader(classBytes);
        // override the getCommonSuperClass to use the thread context class loader instead of
        // the system classloader. This is useful as ASM needs to load classes from the project
        // which the system classloader does not have visibility upon.
        // TODO: investigate if there is not a simpler way than overriding.
        ClassWriter classWriter = new ClassWriter(classReader, ClassWriter.COMPUTE_FRAMES) {
            @Override
            protected String getCommonSuperClass(final String type1, final String type2) {
                Class<?> c, d;
                ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
                try {
                    c = Class.forName(type1.replace('/', '.'), false, classLoader);
                    d = Class.forName(type2.replace('/', '.'), false, classLoader);
                } catch (ClassNotFoundException e) {
                    // This may happen if we're processing class files which reference APIs not
                    // available on the target device. In this case return a dummy value, since this
                    // is ignored during dx compilation.
                    System.err.println("can not find superClass:"+type1 +" or "+type2);
                    return "instant/run/NoCommonSuperClass";
                } catch (Throwable e) {
                    throw new RuntimeException(type1+":"+type2,e);
                }
                if (c.isAssignableFrom(d)) {
                    return type1;
                }
                if (d.isAssignableFrom(c)) {
                    return type2;
                }
                if (c.isInterface() || d.isInterface()) {
                    return "java/lang/Object";
                } else {
                    do {
                        c = c.getSuperclass();
                    } while (!c.isAssignableFrom(d));
                    return c.getName().replace('.', '/');
                }
            }
        };

        ClassNode classNode = AsmUtils.readClass(classReader);

        boolean hasOtherMethod = false;
        if (classNode != null && classNode.methods != null) {
            for (Object methodNode : classNode.methods) {
                if (methodNode instanceof MethodNode) {
                    if (!((MethodNode) methodNode).name.equals(ByteCodeUtils.CLASS_INITIALIZER) && !((MethodNode) methodNode).name.equals(ByteCodeUtils.CONSTRUCTOR)) {
                        hasOtherMethod = true;
                    }
                }
            }
        }


        // when dealing with interface, we just copy the inputFile over without any changes unless
        // this is a package private interface.
        AccessRight accessRight = AccessRight.fromNodeAccess(classNode.access);
        File outputFile = new File(outputDirectory, path);

        if (!hasOtherMethod) {
            if (visitorBuilder.getOutputType() == OutputType.INSTRUMENT) {
                Files.createParentDirs(outputFile);
                Files.write(classBytes, outputFile);
                return outputFile;
            } else {
                return null;
            }

        }

        if ((classNode.access & Opcodes.ACC_INTERFACE) != 0) {
            if (visitorBuilder.getOutputType() == OutputType.INSTRUMENT) {
                // don't change the name of interfaces.
                if (injectErrorListener != null) {
                    injectErrorListener.onError(ErrorType.INTERFACE);
                }
                Files.createParentDirs(outputFile);
                if (accessRight == AccessRight.PACKAGE_PRIVATE) {
                    classNode.access = classNode.access | Opcodes.ACC_PUBLIC;
                    classNode.accept(classWriter);
                    Files.write(classWriter.toByteArray(), outputFile);
                } else {
                    // just copy the input file over, no change.
                    Files.write(classBytes, outputFile);
                }
                return outputFile;
            } else {
                return null;
            }
        }

        Class<?> c, d;
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        try {
            c = Class.forName(classNode.name.replace('/', '.'), false, classLoader);
            for (Imatcher imatcher: MatcherCreator.getMatchers()){
                d = ((ImplementsMatcher)imatcher).getClazz(classLoader);
                if (d.isAssignableFrom(c)){
                    if (visitorBuilder.getOutputType() == OutputType.INSTRUMENT) {
                        // don't change the name of interfaces.
                        if (injectErrorListener != null) {
                            injectErrorListener.onError(ErrorType.IMPLEMENTS);
                        }
                        Files.createParentDirs(outputFile);

                        // just copy the input file over, no change.
                        Files.write(classBytes, outputFile);
                        return outputFile;
                    } else {
                        return null;
                    }
                }

            }

        }catch (Throwable e){
            e.printStackTrace();
        }



        AsmUtils.DirectoryBasedClassReader directoryClassReader =
                new AsmUtils.DirectoryBasedClassReader(getBinaryFolder(inputFile, classNode));

        // if we are targeting a more recent version than the current device, disable instant run
        // for that class.
        List<ClassNode> parentsNodes =
                isClassTargetingNewerPlatform(
                        targetApiLevel, TARGET_API_TYPE, directoryClassReader, classNode, logger)
                        ? ImmutableList.of()
                        : AsmUtils.parseParents(
                        logger, directoryClassReader, classNode, targetApiLevel);
        // if we could not determine the parent hierarchy, disable instant run.
        if (parentsNodes.isEmpty() || isPackageInstantRunDisabled(inputFile)) {
            if (visitorBuilder.getOutputType() == OutputType.INSTRUMENT) {
                if (injectErrorListener != null) {
                    if (parentsNodes.isEmpty()) {
                        injectErrorListener.onError(ErrorType.NEW_API);
                    } else {
                        injectErrorListener.onError(ErrorType.PACKAGE_DISABLED);
                    }
                }
                Files.createParentDirs(outputFile);
                Files.write(classBytes, outputFile);
                return outputFile;
            } else {
                return null;
            }
        }

//        Map<String, TBIncrementalSupportVisitor.MethodReference> methods = new HashMap<>();
//        for (ClassNode pN : parentsNodes) {
//            TBIncrementalSupportVisitor.addAllNewMethods(classNode,pN,methods, null);
//        }
//        if (methods.size() > count) {
//            if (visitorBuilder.getOutputType() == OutputType.INSTRUMENT) {
//                if (injectErrorListener != null) {
//                    injectErrorListener.onError(ErrorType.SUB_EXCEEDED);
//                }
//                Files.createParentDirs(outputFile);
//                Files.write(classBytes, outputFile);
//                return outputFile;
//            } else {
//                return null;
//            }
//        }

        outputFile = new File(outputDirectory, visitorBuilder.getMangledRelativeClassFilePath(path));
        Files.createParentDirs(outputFile);
        IncrementalVisitor visitor =
                visitorBuilder.build(classNode, parentsNodes, classWriter, logger);
        if (visitor instanceof TBIncrementalSupportVisitor) {
            ((TBIncrementalSupportVisitor) visitor).setPatchInitMethod(patchInitMethod);
            ((TBIncrementalSupportVisitor) visitor).setSupportEachMethod(patchEachMethod);
            ((TBIncrementalSupportVisitor) visitor).setSupportAddCallSuper(supportAddCallSuper);

        }

        if (visitorBuilder.getOutputType() == OutputType.INSTRUMENT) {
            /*
             * Classes that do not have a serial version unique identifier, will be updated to
             * contain one. This is accomplished by using the {@link SerialVersionUIDAdder} class
             * visitor that is added when this visitor is created (see the constructor). This way,
             * the serialVersionUID is the same for instrumented and non-instrumented classes. All
             * classes will have a serialVersionUID, so if some of the classes that is extended
             * starts implementing {@link java.io.Serializable}, serialization and deserialization
             * will continue to work correctly.
             */
            if (addSerialVersionUID) {
                classNode.accept(new SerialVersionUIDAdder(visitor));
            } else {
                classNode.accept(visitor);
            }
        } else {
            classNode.accept(visitor);
        }

        Files.write(classWriter.toByteArray(), outputFile);
        return outputFile;
    }


    @VisibleForTesting
    public static boolean isClassEligibleForInstantRun(@NonNull File inputFile) {

        if (inputFile.getPath().endsWith(SdkConstants.DOT_CLASS)) {
            String fileName = inputFile.getName();
            return !fileName.equals("R" + SdkConstants.DOT_CLASS) && !fileName.startsWith("R$");
        } else {
            return false;
        }
    }

    protected enum AccessRight {

        PRIVATE, PACKAGE_PRIVATE, PROTECTED, PUBLIC;

        @NonNull
        public static TBIncrementalVisitor.AccessRight fromNodeAccess(int nodeAccess) {
            if ((nodeAccess & Opcodes.ACC_PRIVATE) != 0) return PRIVATE;
            if ((nodeAccess & Opcodes.ACC_PROTECTED) != 0) return PROTECTED;
            if ((nodeAccess & Opcodes.ACC_PUBLIC) != 0) return PUBLIC;
            return PACKAGE_PRIVATE;
        }
    }

    @NonNull
    private static File getBinaryFolder(@NonNull File inputFile, @NonNull ClassNode classNode) {
        return new File(inputFile.getAbsolutePath().substring(0,
                inputFile.getAbsolutePath().length() - (classNode.name.length() + ".class".length())));
    }

    @VisibleForTesting
    static boolean isClassTargetingNewerPlatform(
            int targetApiLevel,
            @NonNull Type targetApiAnnotationType,
            @NonNull AsmUtils.ClassReaderProvider locator,
            @NonNull ClassNode classNode,
            @NonNull ILogger logger) throws IOException {

        List<AnnotationNode> invisibleAnnotations =
                AsmUtils.getInvisibleAnnotationsOnClassOrOuterClasses(locator, classNode, logger);
        for (AnnotationNode classAnnotation : invisibleAnnotations) {
            if (classAnnotation.desc.equals(targetApiAnnotationType.getDescriptor())) {
                int valueIndex = 0;
                List values = classAnnotation.values;
                while (valueIndex < values.size()) {
                    String name = (String) values.get(valueIndex);
                    if (name.equals("value")) {
                        Object value = values.get(valueIndex + 1);
                        return Integer.class.cast(value) > targetApiLevel;
                    }
                    valueIndex = valueIndex + 2;
                }
            }
        }
        return false;
    }

    private static boolean isPackageInstantRunDisabled(@NonNull File inputFile) throws IOException {

        ClassNode packageInfoClass = AsmUtils.parsePackageInfo(inputFile);
        if (packageInfoClass != null) {
            //noinspection unchecked
            List<AnnotationNode> annotations = packageInfoClass.invisibleAnnotations;
            if (annotations == null) {
                return false;
            }
            for (AnnotationNode annotation : annotations) {
                if (annotation.desc.equals(DISABLE_ANNOTATION_TYPE.getDescriptor())) {
                    return true;
                }
            }
        }
        return false;
    }

}
