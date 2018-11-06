package com.taobao.android.builder.insant.incremental;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.build.gradle.internal.incremental.AsmUtils;
import com.android.build.gradle.internal.incremental.IncrementalVisitor;
import com.android.utils.FileUtils;
import com.android.utils.ILogger;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.SerialVersionUIDAdder;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Observable;
import java.util.Observer;

/**
 * 创建日期：2018/11/6 on 下午7:41
 * 描述:
 * 作者:zhayu.ll
 */
public class TBIncrementalVisitor extends IncrementalVisitor {

    public TBIncrementalVisitor(ClassNode classNode, List<ClassNode> parentNodes, ClassVisitor classVisitor, ILogger logger) {
        super(classNode, parentNodes, classVisitor, logger);
    }

    public interface InjectErrorListener{

        void onError(ErrorType errorType);

    }

    public enum ErrorType{
        R_CLASS,INTERFACE , NEW_API, PACKAGE_DISABLED
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
            boolean addSerialVersionUID) throws IOException {

        byte[] classBytes;
        String path = FileUtils.relativePath(inputFile, inputRootDirectory);

        // if the class is not eligible for IR, return the non instrumented version or null if
        // the override class is requested.
        if (!isClassEligibleForInstantRun(inputFile)) {
            if (injectErrorListener!= null){
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
                    return "instant/run/NoCommonSuperClass";
                } catch (Exception e) {
                    throw new RuntimeException(e);
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

        ClassNode classNode =  AsmUtils.readClass(classReader);

        // when dealing with interface, we just copy the inputFile over without any changes unless
        // this is a package private interface.
        AccessRight accessRight = AccessRight.fromNodeAccess(classNode.access);
        File outputFile = new File(outputDirectory, path);
        if ((classNode.access & Opcodes.ACC_INTERFACE) != 0) {
            if (visitorBuilder.getOutputType() == OutputType.INSTRUMENT) {
                // don't change the name of interfaces.
                if (injectErrorListener != null){
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
                if (injectErrorListener!= null){
                    if (parentsNodes.isEmpty()){
                        injectErrorListener.onError(ErrorType.NEW_API);
                    }else {
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

        outputFile = new File(outputDirectory, visitorBuilder.getMangledRelativeClassFilePath(path));
        Files.createParentDirs(outputFile);
        IncrementalVisitor visitor =
                visitorBuilder.build(classNode, parentsNodes, classWriter, logger);

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
            }else {
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
        static TBIncrementalVisitor.AccessRight fromNodeAccess(int nodeAccess) {
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

        ClassNode packageInfoClass =  AsmUtils.parsePackageInfo(inputFile);
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
