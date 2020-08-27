package com.android.build.gradle.internal.incremental;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.utils.ILogger;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;

import org.objectweb.asm.*;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.JSRInlinerAdapter;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * 创建日期：2018/11/28 on 下午12:43
 * 描述:
 * 作者:zhayu.ll
 */
public class TBIncrementalSupportVisitor extends TBIncrementalVisitor {

    private boolean disableRedirectionForClass = false;
    private boolean isInterface = false;
    private boolean classInitializerAdded = false;
    private boolean patchInitMethod = true;
    private boolean supportAddCallSuper = true;
    private boolean patchInterface = false;
    private boolean patchEachMethod = false;
    private InjectMethodCallBack injectMethodCallBack;


    private List<MethodNode> methodNodes = new ArrayList<>();

    private Map<String, String> visitSuperMethods = new HashMap<>();


    public void setPatchInitMethod(boolean patchInitMethod) {
        this.patchInitMethod = patchInitMethod;
    }

    public void setSupportAddCallSuper(boolean supportAddCallSuper) {
        this.supportAddCallSuper = supportAddCallSuper;
    }

    public void setPatchEachMethod(boolean patchEachMethod) {
        this.patchEachMethod = patchEachMethod;
    }

    public void setInjectMethodCallback(InjectMethodCallBack injectMethodCallback) {
        this.injectMethodCallBack = injectMethodCallback;
    }

    private static final class VisitorBuilder implements TBIncrementalVisitor.VisitorBuilder {

        private VisitorBuilder() {
        }

        @NonNull
        @Override
        public IncrementalVisitor build(
                @NonNull AsmClassNode classNode,
                @NonNull ClassVisitor classVisitor,
                @NonNull ILogger logger) {
            return new TBIncrementalSupportVisitor(classNode, classVisitor, logger);
        }

        @Override
        @NonNull
        public String getMangledRelativeClassFilePath(@NonNull String originalClassFilePath) {
            return originalClassFilePath;
        }

        @NonNull
        @Override
        public OutputType getOutputType() {
            return OutputType.INSTRUMENT;
        }
    }

    @NonNull
    public static final TBIncrementalVisitor.VisitorBuilder VISITOR_BUILDER = new VisitorBuilder();

    public TBIncrementalSupportVisitor(
            @NonNull AsmClassNode classNode,
            @NonNull ClassVisitor classVisitor,
            @NonNull ILogger logger) {
        super(classNode, classVisitor, logger);
        classNode.getClassNode().methods.forEach((Consumer<MethodNode>) o -> {
            if (!o.name.equals(ByteCodeUtils.CONSTRUCTOR) && !o.name.equals(ByteCodeUtils.CLASS_INITIALIZER)) {
                methodNodes.add(o);
            }
        });

    }

    /**
     * Ensures that the class contains a $change field used for referencing the IncrementalChange
     * dispatcher.
     *
     * <p>Also updates package_private visibility to public so we can call into this class from
     * outside the package.
     */
    @Override
    public void visit(int version, int access, String name, String signature, String superName,
                      String[] interfaces) {
        visitedClassName = name;
        visitedSuperName = superName;
        isInterface = (access & Opcodes.ACC_INTERFACE) != 0;
        int fieldAccess =
                isInterface
                        ? Opcodes.ACC_PUBLIC
                        | Opcodes.ACC_STATIC
                        | Opcodes.ACC_SYNTHETIC
                        | Opcodes.ACC_FINAL
                        : Opcodes.ACC_PUBLIC
                        | Opcodes.ACC_STATIC
                        | Opcodes.ACC_VOLATILE
                        | Opcodes.ACC_SYNTHETIC
                        | Opcodes.ACC_TRANSIENT;

        // when dealing with interfaces, the $change field is an AtomicReference to the CHANGE_TYPE
        // since fields in interface must be final. For classes, it's the CHANGE_TYPE directly.
        if (isInterface) {

                super.visitField(
                        fieldAccess,
                        "$ipChange",
                        getRuntimeTypeName(Type.getType(AtomicReference.class)),
                        null,
                        null);

        } else {
            if (patchEachMethod && methodNodes.size() > 0) {
                methodNodes.forEach(methodNode -> TBIncrementalSupportVisitor.super.visitField(fieldAccess, "$ipChange$" + Integer.toHexString((methodNode.name + "." + methodNode.desc).hashCode()), getRuntimeTypeName(ALI_CHANGE_TYPE), null, null));
            } else {
                super.visitField(fieldAccess, "$ipChange", getRuntimeTypeName(ALI_CHANGE_TYPE), null, null);
            }
        }
        access = transformClassAccessForInstantRun(access);
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public void visitInnerClass(String name, String outerName, String innerName, int access) {
        int newAccess =
                access & (~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED)) | Opcodes.ACC_PUBLIC;
        super.visitInnerClass(name, outerName, innerName, newAccess);
    }

    @Override
    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
        if (desc.equals(ALI_DISABLE_ANNOTATION_TYPE.getDescriptor())) {
            disableRedirectionForClass = true;
        }
        return super.visitAnnotation(desc, visible);
    }


    @Override
    public FieldVisitor visitField(int access, String name, String desc, String signature,
                                   Object value) {

        access = transformAccessForInstantRun(access);
        return super.visitField(access, name, desc, signature, value);
    }

    /**
     * Insert Constructor specific logic({@link ConstructorRedirection} and
     * {@link ConstructorBuilder}) for constructor redirecting or
     * normal method redirecting ({@link MethodRedirection}) for other methods.
     */
    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature,
                                     String[] exceptions) {

        access = transformAccessForInstantRun(access);

        MethodVisitor defaultVisitor = super.visitMethod(access, name, desc, signature, exceptions);


        // Install the Jsr/Ret inliner adapter, we have had reports of code still using the
        // Jsr/Ret deprecated byte codes.
        // see https://code.google.com/p/android/issues/detail?id=220019
        defaultVisitor =
                new JSRInlinerAdapter(defaultVisitor, access, name, desc, signature, exceptions);
        MethodNode method =
                checkNotNull(
                        getMethodByNameInClass(name, desc, classAndInterfaceNode),
                        "Method found by visitor but not in the pre-parsed class node.");

        if (!supportAddCallSuper) {
            method.instructions.accept(new MethodVisitor(api) {
                @Override
                public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
                    if (opcode == Opcodes.INVOKESPECIAL && !owner.equals(visitedClassName)) {
                        String newDesc = name + "." + desc;
                        if (!visitSuperMethods.containsKey(newDesc)) {
                            visitSuperMethods.put(newDesc, owner);
                        }
                    }
                    super.visitMethodInsn(opcode, owner, name, desc, itf);
                }
            });
        }


        // does the method use blacklisted APIs.
        boolean hasIncompatibleChange = InstantRunMethodVerifier.verifyMethod(method)
                != InstantRunVerifierStatus.COMPATIBLE;

        if (hasIncompatibleChange
                || disableRedirectionForClass
                || !isAccessCompatibleWithInstantRun(access)
                || (isInterface && !patchInterface)) {
            return defaultVisitor;
        }
        if (name.equals(ByteCodeUtils.CLASS_INITIALIZER)) {
            classInitializerAdded = true;
            return isInterface
                    ? new ISInterfaceStaticInitializerMethodVisitor(
                    defaultVisitor, access, name, desc)
                    : defaultVisitor;
        }

        ArrayList<Type> args = new ArrayList<>(Arrays.asList(Type.getArgumentTypes(desc)));
        boolean isStatic = (access & Opcodes.ACC_STATIC) != 0;
        if (!isStatic) {
            args.add(0, Type.getType(Object.class));
        }

        ISAbstractMethodVisitor mv =
                isInterface
                        ? new ISDefaultMethodVisitor(defaultVisitor, access, name, desc)
                        : new ISMethodVisitor(defaultVisitor, access, name, desc);

        if (name.equals(ByteCodeUtils.CONSTRUCTOR)) {
            if ((access & Opcodes.ACC_SYNTHETIC) != 0
                    || ByteCodeUtils.isAnnotatedWith(method, "Lkotlin/jvm/JvmOverloads;")
                    || !patchInitMethod) {
                return defaultVisitor;
            }
            Constructor constructor = ConstructorBuilder.build(visitedClassName, method);
            LabelNode start = new LabelNode();
            method.instructions.insert(constructor.loadThis, start);
            if (constructor.lineForLoad != -1) {
                // Record the line number from the start of LOAD_0 for uninitialized 'this'.
                // This allows a breakpoint to be set at the line with this(...) or super(...)
                // call in the constructor.
                method.instructions.insert(
                        constructor.loadThis, new LineNumberNode(constructor.lineForLoad, start));
            }
            mv.addRedirection(new TBConstructorRedirection(start, constructor, args));
        } else {
            mv.addRedirection(
                    new TBMethodRedirection(
                            new LabelNode(mv.getStartLabel()),
                            name + "." + desc,
                            args,
                            Type.getReturnType(desc)));
            if (injectMethodCallBack != null){
                injectMethodCallBack.method(javaMethod(method),name+"."+desc);
            }
        }
        method.accept(mv);
        return null;
    }

    private String javaMethod(MethodNode method) {
        Method m = new Method(method.name,method.desc);
        Type[]types = m.getArgumentTypes();
        Type type = m.getReturnType();
        Type methodType= Type.getMethodType(type,types);
        return method.name+methodType.getDescriptor();
    }

    /**
     * If a class is package private, make it public so instrumented code living in a different
     * class loader can instantiate them.
     *
     * @param access the original class/method/field access.
     * @return the new access or the same one depending on the original access rights.
     */
    private static int transformClassAccessForInstantRun(int access) {
        AccessRight accessRight = AccessRight.fromNodeAccess(access);

        return accessRight == AccessRight.PACKAGE_PRIVATE ? access | Opcodes.ACC_PUBLIC : access;
    }

    /**
     * If a method/field is not private, make it public. This is to workaround the fact
     *
     * <ul>
     *   reload.dex are loaded from a different class loader but private methods/fields are accessed
     *   through reflection, therefore you need visibility.
     * </ul>
     * <p>
     * remember that in Java, protected methods or fields can be accessed by classes in the same
     * package : {@see https://docs.oracle.com/javase/tutorial/java/javaOO/accesscontrol.html}
     *
     * @param access the original class/method/field access.
     * @return the new access or the same one depending on the original access rights.
     */
    private static int transformAccessForInstantRun(int access) {
        AccessRight accessRight = AccessRight.fromNodeAccess(access);
        if (accessRight != AccessRight.PRIVATE) {
            access &= ~Opcodes.ACC_PROTECTED;
            access &= ~Opcodes.ACC_PRIVATE;
            return access | Opcodes.ACC_PUBLIC;
        }
        return access;
    }

    private abstract static class ISAbstractMethodVisitor extends GeneratorAdapter {

        protected boolean disableRedirection = false;
        protected int change;
        protected int hash;

        protected final List<Type> args;
        protected final List<Redirection> redirections;
        protected final Map<Label, Redirection> resolvedRedirections;
        protected final Label start;

        public ISAbstractMethodVisitor(MethodVisitor mv, int access, String name, String desc) {
            super(Opcodes.ASM5, mv, access, name, desc);
            this.hash = (name + "." + desc).hashCode();
            this.change = -1;
            this.redirections = new ArrayList<>();
            this.resolvedRedirections = new HashMap<>();
            this.args = new ArrayList<>(Arrays.asList(Type.getArgumentTypes(desc)));
            this.start = new Label();
            boolean isStatic = (access & Opcodes.ACC_STATIC) != 0;
            // if this is not a static, we add a fictional first parameter what will contain the
            // "this" reference which can be loaded with ILOAD_0 bytecode.
            if (!isStatic) {
                args.add(0, Type.getType(Object.class));
            }
        }

        @Override
        public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
            if (desc.equals(ALI_DISABLE_ANNOTATION_TYPE.getDescriptor())) {
                disableRedirection = true;
            }
            return super.visitAnnotation(desc, visible);
        }

        /**
         * inserts a new local '$change' in each method that contains a reference to the type's
         * IncrementalChange dispatcher, this is done to avoid threading issues.
         * <p>
         * Pseudo code:
         * <code>
         * $package/IncrementalChange $local1 = $className$.$change;
         * </code>
         */
        @Override
        public void visitCode() {
            if (!disableRedirection) {
                // Labels cannot be used directly as they are volatile between different visits,
                // so we must use LabelNode and resolve before visiting for better performance.
                for (Redirection redirection : redirections) {
                    resolvedRedirections.put(redirection.getPosition().getLabel(), redirection);
                }

                super.visitLabel(start);
                change = newLocal(ALI_CHANGE_TYPE);
                visitChangeField();
                storeLocal(change);


                redirectAt(start);
            }
            super.visitCode();
        }

        protected abstract void visitChangeField();

        @Override
        public void visitLabel(Label label) {
            super.visitLabel(label);
            redirectAt(label);
        }

        protected void redirectAt(Label label) {
            if (disableRedirection) {
                return;
            }
            Redirection redirection = resolvedRedirections.get(label);
            if (redirection != null) {
                // A special line number to mark this area of code.
                super.visitLineNumber(0, label);
                redirection.redirect(this, change);
            }
        }

        public void addRedirection(@NonNull Redirection redirection) {
            redirections.add(redirection);
        }

        @Override
        public void visitLocalVariable(
                String name, String desc, String signature, Label start, Label end, int index) {
            // In dex format, the argument names are separated from the local variable names. It
            // seems to be needed to declare the local argument variables from the beginning of
            // the methods for dex to pick that up. By inserting code before the first label we
            // break that. In Java this is fine, and the debugger shows the right thing. However
            // if we don't readjust the local variables, we just don't see the arguments.
            if (!disableRedirection && index < args.size()) {
                start = this.start;
            }
            super.visitLocalVariable(name, desc, signature, start, end, index);
        }

        public Label getStartLabel() {
            return start;
        }
    }

    private class ISMethodVisitor extends ISAbstractMethodVisitor {

        public ISMethodVisitor(MethodVisitor mv, int access, String name, String desc) {
            super(mv, access, name, desc);

        }

        @Override
        protected void visitChangeField() {
            if (patchEachMethod) {
                visitFieldInsn(
                        Opcodes.GETSTATIC,
                        visitedClassName,
                        "$ipChange$" + Integer.toHexString(hash),
                        getRuntimeTypeName(ALI_CHANGE_TYPE));
            } else {
                visitFieldInsn(
                        Opcodes.GETSTATIC,
                        visitedClassName,
                        "$ipChange",
                        getRuntimeTypeName(ALI_CHANGE_TYPE));
            }
        }
    }

    private class ISDefaultMethodVisitor extends ISAbstractMethodVisitor {

        public ISDefaultMethodVisitor(MethodVisitor mv, int access, String name, String desc) {
            super(mv, access, name, desc);
        }

        @Override
        protected void visitChangeField() {
                visitFieldInsn(
                        Opcodes.GETSTATIC,
                        visitedClassName,
                        "$ipChange",
                        getRuntimeTypeName(Type.getType(AtomicReference.class)));

            mv.visitMethodInsn(
                    Opcodes.INVOKEVIRTUAL,
                    "java/util/concurrent/atomic/AtomicReference",
                    "get",
                    "()Ljava/lang/Object;",
                    false);
        }
    }

    private class ISInterfaceStaticInitializerMethodVisitor extends GeneratorAdapter {
        public ISInterfaceStaticInitializerMethodVisitor(
                MethodVisitor mv, int access, String name, String desc) {
            super(Opcodes.ASM5, mv, access, name, desc);
        }

        @Override
        public void visitCode() {
            addInterfaceClassInitializerCode(this);
            super.visitCode();
        }
    }

    /**
     * Decorated {@link MethodNode} that maintains a reference to the class declaring the method.
     */
    private static class MethodReference {
        final MethodNode method;
        final ClassNode owner;

        private MethodReference(MethodNode method, ClassNode owner) {
            this.method = method;
            this.owner = owner;
        }

        private String getDefautlDispatchName() {
            return getDefaultDispatchName(method);
        }

        private static String getDefaultDispatchName(MethodNode method) {
            return method.name + "." + method.desc;
        }
    }

    /***
     * Inserts a trampoline to this class so that the updated methods can make calls to super
     * class methods.
     * <p>
     * Pseudo code for this trampoline:
     * <code>
     *   Object access$super($classType instance, String name, object[] args) {
     *      switch(name) {
     *          case "firstMethod.(Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/Object;":
     *            return super~instance.firstMethod((String)arg[0], arg[1]);
     *          case "secondMethod.(Ljava/lang/String;I)V":
     *            return super~instance.firstMethod((String)arg[0], arg[1]);
     *
     *          default:
     *            StringBuilder $local1 = new StringBuilder();
     *            $local1.append("Method not found ");
     *            $local1.append(name);
     *            $local1.append(" in " $classType $super implementation");
     *            throw new $package/InstantReloadException($local1.toString());
     *      }
     * </code>
     */
    private void createAccessSuper() {
        int access = Opcodes.ACC_STATIC | Opcodes.ACC_PUBLIC
                | Opcodes.ACC_SYNTHETIC | Opcodes.ACC_VARARGS;
        Method m = new Method("ipc$super", "(L" + visitedClassName
                + ";Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/Object;");
        MethodVisitor visitor = super.visitMethod(access,
                m.getName(),
                m.getDescriptor(),
                null, null);

        final GeneratorAdapter mv = new GeneratorAdapter(access, m, visitor);

        // Gather all methods from itself and its superclasses to generate a giant ipc$super
        // implementation.
        // This will work fine as long as we don't support adding methods to a class.
        final Map<String, MethodReference> uniqueMethods = new HashMap<>();
        if (classAndInterfaceNode.hasParent()) {
            addAllNewMethods(
                    classAndInterfaceNode.getClassNode(),
                    classAndInterfaceNode.getParent(),
                    uniqueMethods);
        } else {
            // if we cannot determine the parents for this class, let's blindly add all the
            // method of the current class as a gateway to a possible parent version.
            addAllNewMethods(
                    classAndInterfaceNode.getClassNode(),
                    classAndInterfaceNode.getClassNode(),
                    uniqueMethods);
        }

        // and gather all default methods of all directly/inherited implemented interfaces.

        for (AsmInterfaceNode implementedInterface : classAndInterfaceNode.getInterfaces()) {
            addDefaultMethods(
                    classAndInterfaceNode.getClassNode(), implementedInterface, uniqueMethods);

            implementedInterface.onAll(
                    interfaceNode -> {
                        addAllNewMethods(
                                classAndInterfaceNode.getClassNode(), interfaceNode, uniqueMethods);
                        return null;
                    });
        }


        Map<String, MethodReference> shouldVisitMethods = new HashMap<>();

        if (!supportAddCallSuper) {
            Map<String, MethodReference> finalShouldVisitMethods = shouldVisitMethods;
            uniqueMethods.keySet().forEach(s -> {
                if (visitSuperMethods.containsKey(s)) {
                    finalShouldVisitMethods.put(s, uniqueMethods.get(s));
                }
            });
        }else {
            shouldVisitMethods = uniqueMethods;
        }


        new TBStringSwitch() {
            @Override
            void visitString() {
                mv.visitVarInsn(Opcodes.ALOAD, 1);
            }

            @Override
            void visitCase(String methodName) {
                MethodReference methodRef = uniqueMethods.get(methodName);

                mv.visitVarInsn(Opcodes.ALOAD, 0);

                Type[] args = Type.getArgumentTypes(methodRef.method.desc);
                int argc = 0;
                for (Type t : args) {
                    mv.visitVarInsn(Opcodes.ALOAD, 2);
                    mv.push(argc);
                    mv.visitInsn(Opcodes.AALOAD);
                    ByteCodeUtils.unbox(mv, t);
                    argc++;
                }

                if (TRACING_ENABLED) {
                    trace(mv, "super selected ", methodRef.owner.name,
                            methodRef.method.name, methodRef.method.desc);
                }
                String parentName = findParentClassForMethod(methodRef);
                logger.verbose(
                        "Generating ipc$super for %1$s recev %2$s",
                        methodRef.method.name,
                        parentName);

                // Call super on the other object, yup this works cos we are on the right place to
                // call from.
                mv.visitMethodInsn(Opcodes.INVOKESPECIAL,
                        visitSuperMethods.get(methodName),
                        methodRef.method.name,
                        methodRef.method.desc, false);

                Type ret = Type.getReturnType(methodRef.method.desc);
                if (ret.getSort() == Type.VOID) {
                    mv.visitInsn(Opcodes.ACONST_NULL);
                } else {
                    mv.box(ret);
                }
                mv.visitInsn(Opcodes.ARETURN);
            }

            @Override
            void visitDefault() {
                writeMissingMessageWithHash(mv, visitedClassName);
            }
        }.visit(mv, shouldVisitMethods.keySet());

        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    /***
     * Inserts a trampoline to this class so that the updated methods can make calls to
     * constructors.
     *
     * <p>
     * Pseudo code for this trampoline:
     * <code>
     *   ClassName(Object[] args, Marker unused) {
     *      String name = (String) args[0];
     *      if (name.equals(
     *          "java/lang/ClassName.(Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/Object;")) {
     *        this((String)arg[1], arg[2]);
     *        return
     *      }
     *      if (name.equals("SuperClassName.(Ljava/lang/String;I)V")) {
     *        super((String)arg[1], (int)arg[2]);
     *        return;
     *      }
     *      ...
     *      StringBuilder $local1 = new StringBuilder();
     *      $local1.append("Method not found ");
     *      $local1.append(name);
     *      $local1.append(" in " $classType $super implementation");
     *      throw new $package/InstantReloadException($local1.toString());
     *   }
     * </code>
     */
    private void createDispatchingThis() {
        // Gather all methods from itself and its superclasses to generate a giant constructor
        // implementation.
        // This will work fine as long as we don't support adding constructors to classes.
        final Map<String, MethodNode> uniqueMethods = new HashMap<>();

        addAllNewConstructors(
                uniqueMethods,
                classAndInterfaceNode.getClassNode(),
                true /*keepPrivateConstructors*/);
        classAndInterfaceNode.onParents(
                parentClassNode -> {
                    addAllNewConstructors(
                            uniqueMethods, parentClassNode, false /*keepPrivateConstructors*/);
                    return null;
                });

        int access = Opcodes.ACC_PUBLIC | Opcodes.ACC_SYNTHETIC;

        Method m = new Method(ByteCodeUtils.CONSTRUCTOR,
                TBConstructorRedirection.DISPATCHING_THIS_SIGNATURE);
        MethodVisitor visitor = super.visitMethod(0, m.getName(), m.getDescriptor(), null, null);
        final GeneratorAdapter mv = new GeneratorAdapter(access, m, visitor);

        mv.visitCode();
        // Mark this code as redirection code
        Label label = new Label();
        mv.visitLineNumber(0, label);

        // Get and store the constructor canonical name.
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.push(1);
        mv.visitInsn(Opcodes.AALOAD);
        mv.unbox(Type.getType("Ljava/lang/String;"));
        final int constructorCanonicalName = mv.newLocal(Type.getType("Ljava/lang/String;"));
        mv.storeLocal(constructorCanonicalName);

        new TBStringSwitch() {

            @Override
            void visitString() {
                mv.loadLocal(constructorCanonicalName);
            }

            @Override
            void visitCase(String canonicalName) {
                MethodNode methodNode = uniqueMethods.get(canonicalName);
                String owner = canonicalName.split("\\.")[0];

                // Parse method arguments and
                mv.visitVarInsn(Opcodes.ALOAD, 0);
                Type[] args = Type.getArgumentTypes(methodNode.desc);
                int argc = 1;
                for (Type t : args) {
                    mv.visitVarInsn(Opcodes.ALOAD, 1);
                    mv.push(argc + 1);
                    mv.visitInsn(Opcodes.AALOAD);
                    ByteCodeUtils.unbox(mv, t);
                    argc++;
                }

                mv.visitMethodInsn(Opcodes.INVOKESPECIAL, owner, ByteCodeUtils.CONSTRUCTOR,
                        methodNode.desc, false);

                mv.visitInsn(Opcodes.RETURN);
            }

            @Override
            void visitDefault() {
                writeMissingMessageWithHash(mv, visitedClassName);
            }
        }.visit(mv, uniqueMethods.keySet());

        mv.visitMaxs(1, 3);
        mv.visitEnd();
    }

    /**
     * add a static initializer to java8 interfaces (this visitor will only be called when default
     * methods are present).
     */
    private void addInterfaceClassInitializer() {
        if (classInitializerAdded) {
            return;
        }
        MethodVisitor mv = super.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        mv.visitCode();
        addInterfaceClassInitializerCode(mv);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(3, 0);
        mv.visitEnd();
    }

    private void addInterfaceClassInitializerCode(MethodVisitor mv) {
        mv.visitTypeInsn(Opcodes.NEW, "java/util/concurrent/atomic/AtomicReference");
        mv.visitInsn(Opcodes.DUP);
        mv.visitInsn(Opcodes.ACONST_NULL);
        mv.visitMethodInsn(
                Opcodes.INVOKESPECIAL,
                "java/util/concurrent/atomic/AtomicReference",
                "<init>",
                "(Ljava/lang/Object;)V",
                false);
        mv.visitFieldInsn(
                Opcodes.PUTSTATIC,
                visitedClassName,
                "$ipChange",
                "Ljava/util/concurrent/atomic/AtomicReference;");
    }

    @Override
    public void visitEnd() {

        if (!visitedSuperName.equals("java/lang/Object")) {
            createAccessSuper();
        }

        if (isInterface && patchInterface) {
            addInterfaceClassInitializer();
        } else if (patchInitMethod) {
            createDispatchingThis();
        }
        super.visitEnd();
    }

    /**
     * Find a suitable parent for a method reference. The method owner is not always a valid
     * parent to dispatch to. For instance, take the following example :
     * <code>
     * package a;
     * public class A {
     * public void publicMethod();
     * }
     * <p>
     * package a;
     * class B extends A {
     * public void publicMethod();
     * }
     * <p>
     * package b;
     * public class C extends B {
     * ...
     * }
     * </code>
     * when instrumenting C, the first method reference for "publicMethod" is on class B which we
     * cannot invoke directly since it's present on a private package B which is not located in the
     * same package as C. However C can still call the "publicMethod" since it's defined on A which
     * is a public class.
     * <p>
     * We cannot just blindly take the top most definition of "publicMethod" hoping this is the
     * accessible one since you can very well do :
     * <code>
     * package a;
     * class A {
     * public void publicMethod();
     * }
     * <p>
     * package a;
     * public class B extends A {
     * public void publicMethod();
     * }
     * <p>
     * package b;
     * public class C extends B {
     * ...
     * }
     * </code>
     * <p>
     * In that case, the top most parent class is the one defined the unaccessible method reference.
     * <p>
     * Therefore, the solution is to walk up the hierarchy until we find the same method defined on
     * an accessible class, if we cannot find such a method, the suitable parent is the parent class
     * of the visited class which is legal (but might consume a DEX id).
     *
     * @param methodReference the method reference to find a suitable parent for.
     * @return the parent class name
     */
    @NonNull
    String findParentClassForMethod(@NonNull MethodReference methodReference) {
        logger.verbose(
                "MethodRef %1$s access(%2$s) -> owner %3$s access(%4$s)",
                methodReference.method.name, methodReference.method.access,
                methodReference.owner.name, methodReference.owner.access);
        // if the method owner class is accessible from the visited class, just use that.
        if (isParentClassVisible(methodReference.owner, classAndInterfaceNode.getClassNode())) {
            return methodReference.owner.name;
        }
        logger.verbose("Found an inaccessible methodReference %1$s", methodReference.method.name);

        // walk up the hierarchy, starting at the method reference owner.
        if (classAndInterfaceNode.hasParent()) {
            AsmClassNode asmClassNode = classAndInterfaceNode.getParent();
            while (!asmClassNode.getClassNode().name.equals(methodReference.owner.name)
                    && asmClassNode.hasParent()) {
                asmClassNode = asmClassNode.getParent();
            }
            if (asmClassNode.hasParent()) {
                String selectedParent =
                        asmClassNode.onParents(
                                parentClassNode -> {

                                    // check that this parent is visible, there might be several layers of package
                                    // private classes.
                                    if (isParentClassVisible(
                                            parentClassNode,
                                            classAndInterfaceNode.getClassNode())) {
                                        //noinspection unchecked: ASM API
                                        for (MethodNode methodNode :
                                                (List<MethodNode>) parentClassNode.methods) {
                                            // do not reference bridge methods, they might not be translated into dex, or
                                            // might disappear in the next javac compiler for that use case.
                                            if (methodNode.name.equals(methodReference.method.name)
                                                    && methodNode.desc.equals(
                                                    methodReference.method.desc)
                                                    && (methodNode.access
                                                    & (Opcodes.ACC_BRIDGE
                                                    | Opcodes.ACC_ABSTRACT))
                                                    == 0) {
                                                logger.verbose(
                                                        "Using class %1$s for dispatching %2$s:%3$s",
                                                        parentClassNode.name,
                                                        methodReference.method.name,
                                                        methodReference.method.desc);
                                                return parentClassNode.name;
                                            }
                                        }
                                    }
                                    return null;
                                });
                if (selectedParent != null) {
                    return selectedParent;
                }
            }
        }
        logger.verbose("Using immediate parent for dispatching %1$s", methodReference.method.desc);
        return classAndInterfaceNode.getClassNode().superName;
    }

    private static boolean isParentClassVisible(@NonNull ClassNode parent,
                                                @NonNull ClassNode child) {

        return ((parent.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED)) != 0 ||
                Objects.equal(ByteCodeUtils.getPackageName(parent.name),
                        ByteCodeUtils.getPackageName(child.name)));
    }

    /**
     * Add all unseen method from the passed ClassNode's methods and implemented interfaces.
     *
     * @param instrumentedClass class that is being visited
     * @param superClass        the class to save all directly implemented methods as well as default
     *                          methods from implemented interfaces from
     * @param methods           the methods already encountered in the ClassNode hierarchy
     * @see ClassNode#methods
     */
    private void addAllNewMethods(
            ClassNode instrumentedClass,
            AsmClassNode superClass,
            Map<String, MethodReference> methods) {

        superClass.onAll(
                classNode -> {
                    addAllNewMethods(instrumentedClass, classNode, methods);
                    return null;
                });
    }

    /**
     * Add all unseen methods from the passed ClassNode's methods.
     *
     * @param instrumentedClass class that is being visited
     * @param superClass        the class to save all new methods from
     * @param methods           the methods already encountered in the ClassNode hierarchy
     * @see ClassNode#methods
     */
    private List<MethodReference> addAllNewMethods(
            ClassNode instrumentedClass,
            ClassNode superClass,
            Map<String, MethodReference> methods) {

        ImmutableList.Builder<MethodReference> methodRefs = ImmutableList.builder();

        if (superClass == null || superClass.name.equals("java/lang/Object") || (instrumentedClass.access & Opcodes.ACC_INTERFACE) != 0) {
            return methodRefs.build();
        }

        //noinspection unchecked
        for (MethodNode method : (List<MethodNode>) superClass.methods) {
            MethodReference methodRef =
                    addNewMethod(instrumentedClass, superClass, method, methods);
            if (methodRef != null) {
                methodRefs.add(methodRef);
            }
        }
        return methodRefs.build();
    }

    /**
     * Adds all default method for the passed implemented interface of a class as well as all
     * default methods from any interface extended by the passed implemented interface.
     *
     * @param instrumentedClass    the class we are bytecode instrumenting.
     * @param implementedInterface the interface that might contain default methods.
     * @param methods              map of methods we already encountered on the instrumentedClass implementation.
     * @return nothing
     */
    private Void addDefaultMethods(
            ClassNode instrumentedClass,
            AsmInterfaceNode implementedInterface,
            Map<String, MethodReference> methods) {

        Map<String, MethodReference> visitedInterfaceMethods = new HashMap<>();
        implementedInterface.onAll(
                interfaceNode -> {
                    addAllNewMethods(instrumentedClass, interfaceNode, methods)
                            .forEach(
                                    methodRef -> {
                                        visitedInterfaceMethods.put(
                                                methodRef.getDefautlDispatchName(), methodRef);
                                    });
                    return null;
                });

        // now make sure we have a gateway for inherited default methods that are not present
        // on the derived interface as calls can be made using the derived interface as the owner.
        for (MethodNode methodNode :
                (List<MethodNode>) implementedInterface.getClassNode().methods) {
            visitedInterfaceMethods.remove(MethodReference.getDefaultDispatchName(methodNode));
        }
        // all the remaining methods should be added under this interface to make sure we calling
        // all possible owners for this method.
        for (MethodReference methodReference : visitedInterfaceMethods.values()) {
            addNewMethod(
                    implementedInterface.getClassNode().name
                            + "."
                            + methodReference.getDefautlDispatchName(),
                    instrumentedClass,
                    implementedInterface.getClassNode(),
                    methodReference.method,
                    methods);
        }
        return null;
    }

    /**
     * Add a new method in the list of unseen methods in the instrumentedClass hierarchy.
     *
     * @param instrumentedClass class that is being visited
     * @param superClass        the class or interface defining the passed method.
     * @param method            the method to study
     * @param methods           the methods arlready encountered in the ClassNode hierarchy
     * @return the newly added {@link MethodReference} of null if the method was not added for any
     * reason.
     */
    @Nullable
    private static MethodReference addNewMethod(
            ClassNode instrumentedClass,
            ClassNode superClass,
            MethodNode method,
            Map<String, MethodReference> methods) {

        if (method.name.equals(ByteCodeUtils.CONSTRUCTOR)
                || method.name.equals(ByteCodeUtils.CLASS_INITIALIZER)) {
            return null;
        }

        String name = MethodReference.getDefaultDispatchName(method);

        // if we are dealing with an interface default method we should always provide a
        // way to invoke it through the access$super. It is possible that this default method
        // is overriden by a super class but since several interfaces can implement the same
        // method name, you can easily end up with a partial override (overriding some of the
        // implemented interfaces methods but not all of them).
        if ((superClass.access & Opcodes.ACC_INTERFACE) != 0
                && isParentClassVisible(superClass, instrumentedClass)) {
            // all default methods name will use the defining interface name for the hashcode
            // to avoid name collision when dealing with 2 methods with the same names coming
            // from 2 distinct interfaces.
            name = superClass.name + "." + name;
            return null;
        }

        return addNewMethod(name, instrumentedClass, superClass, method, methods);
    }

    /**
     * Add a new method in the list of unseen methods in the instrumentedClass hierarchy.
     *
     * @param name              the dispatching name that will be used for matching callers to the passed method.
     * @param instrumentedClass class that is being visited
     * @param superClass        the class or interface defining the passed method.
     * @param method            the method to study
     * @param methods           the methods arlready encountered in the ClassNode hierarchy
     * @return the newly added {@link MethodReference} of null if the method was not added for any
     * reason.
     */
    @Nullable
    private static MethodReference addNewMethod(
            String name,
            ClassNode instrumentedClass,
            ClassNode superClass,
            MethodNode method,
            Map<String, MethodReference> methods) {
        if (isAccessCompatibleWithInstantRun(method.access)
                && !methods.containsKey(name)
                && (method.access & Opcodes.ACC_STATIC) == 0
                && isCallableFromSubclass(method, superClass, instrumentedClass)) {

//            if (visitSuperMethods.containsKey(MethodReference.getDefaultDispatchName(method))) {
            MethodReference methodReference = new MethodReference(method, superClass);
            methods.put(MethodReference.getDefaultDispatchName(method), methodReference);
            return methodReference;

//            }

        }
        return null;
    }

    @SuppressWarnings("SimplifiableIfStatement")
    private static boolean isCallableFromSubclass(
            @NonNull MethodNode method,
            @NonNull ClassNode superClass,
            @NonNull ClassNode subclass) {
        if ((method.access & Opcodes.ACC_PRIVATE) != 0) {
            return false;
        } else if ((method.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED)) != 0) {
            return true;
        } else {
            // "package private" access modifier.
            return Objects.equal(
                    ByteCodeUtils.getPackageName(superClass.name),
                    ByteCodeUtils.getPackageName(subclass.name));
        }
    }

    /**
     * Add all constructors from the passed ClassNode's methods. {@see ClassNode#methods}
     *
     * @param methods                 the constructors already encountered in the ClassNode hierarchy
     * @param classNode               the class to save all new methods from.
     * @param keepPrivateConstructors whether to keep the private constructors.
     */
    private void addAllNewConstructors(Map<String, MethodNode> methods, ClassNode classNode,
                                       boolean keepPrivateConstructors) {
        //noinspection unchecked
        for (MethodNode method : (List<MethodNode>) classNode.methods) {
            if (!method.name.equals(ByteCodeUtils.CONSTRUCTOR)) {
                continue;
            }

            if (!isAccessCompatibleWithInstantRun(method.access)) {
                continue;
            }

            if (!keepPrivateConstructors && (method.access & Opcodes.ACC_PRIVATE) != 0) {
                continue;
            }
            if (!classNode.name.equals(visitedClassName)
                    && !classNode.name.equals(visitedSuperName)) {
                continue;
            }
            String key = classNode.name + "." + method.desc;
            if (methods.containsKey(key)) {
                continue;
            }
            methods.put(key, method);
        }
    }
}