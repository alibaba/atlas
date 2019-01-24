package com.android.build.gradle.internal.incremental;

import com.android.annotations.NonNull;
import com.android.utils.ILogger;
import com.google.common.base.Objects;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.JSRInlinerAdapter;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.*;
import java.util.function.Consumer;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * 创建日期：2018/11/28 on 下午12:43
 * 描述:
 * 作者:zhayu.ll
 */
public class TBIncrementalSupportVisitor extends TBIncrementalVisitor {

    private boolean disableRedirectionForClass = false;



    public void setPatchInitMethod(boolean patchInitMethod) {
        this.patchInitMethod = patchInitMethod;
    }

    private boolean patchInitMethod = false;


    public boolean isSupportAddCallSuper() {
        return supportAddCallSuper;
    }

    public void setSupportAddCallSuper(boolean supportAddCallSuper) {
        this.supportAddCallSuper = supportAddCallSuper;
    }

    private boolean supportAddCallSuper = false;

    private List<MethodNode>methodNodes = new ArrayList<>();

    private List<String>visitSuperMethods = new ArrayList<>();


    public boolean isSupportEachMethod() {
        return supportEachMethod;
    }

    public void setSupportEachMethod(boolean supportEachMethod) {
        this.supportEachMethod = supportEachMethod;
    }

    private boolean supportEachMethod = false;



    public TBIncrementalSupportVisitor(
            @NonNull ClassNode classNode,
            @NonNull List<ClassNode> parentNodes,
            @NonNull ClassVisitor classVisitor,
            @NonNull ILogger logger) {
        super(classNode, parentNodes, classVisitor, logger);
        classNode.methods.forEach((Consumer<MethodNode>) o -> {
            if (!o.name.equals(ByteCodeUtils.CONSTRUCTOR) &&! o.name.equals(ByteCodeUtils.CLASS_INITIALIZER)) {
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

        if (supportEachMethod){
            methodNodes.forEach(methodNode -> {
                String fullName = methodNode.name + "." + methodNode.desc;
                super.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC
                                | Opcodes.ACC_VOLATILE | Opcodes.ACC_SYNTHETIC | Opcodes.ACC_TRANSIENT,
                        "$ipChange"+"$"+ fullName.hashCode(), getRuntimeTypeName(ALI_CHANGE_TYPE), null, null);
            });

        }else {
//            super.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC
//                            | Opcodes.ACC_VOLATILE | Opcodes.ACC_SYNTHETIC | Opcodes.ACC_TRANSIENT,
//                    "$ipChange", getRuntimeTypeName(ALI_CHANGE_TYPE), null, null);

            super.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC
                            | Opcodes.ACC_TRANSIENT |Opcodes.ACC_SYNTHETIC,
                    "$ipChange", getRuntimeTypeName(ALI_CHANGE_TYPE), null, null);
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
        MethodNode method =
                checkNotNull(
                        getMethodByNameInClass(name, desc, classNode),
                        "Method found by visitor but not in the pre-parsed class node.");
        // does the method use blacklisted APIs.

        if (!supportAddCallSuper) {
            method.instructions.accept(new MethodVisitor(api) {
                @Override
                public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
                    if (opcode == Opcodes.INVOKESPECIAL && !owner.equals(visitedClassName)) {
                        visitSuperMethods.add(name + "." + desc);
                    }
                    super.visitMethodInsn(opcode, owner, name, desc, itf);
                }
            });
        }

        //this is method generaged by visualMachine

        if ((access & Opcodes.ACC_SYNTHETIC) != 0){
            return defaultVisitor;
        }

        boolean hasIncompatibleChange = InstantRunMethodVerifier.verifyMethod(method)
                != InstantRunVerifierStatus.COMPATIBLE;

        if (hasIncompatibleChange || disableRedirectionForClass
                || !isAccessCompatibleWithInstantRun(access)
                || name.equals(ByteCodeUtils.CLASS_INITIALIZER)) {
            return defaultVisitor;
        } else {
            ArrayList<Type> args = new ArrayList<>(Arrays.asList(Type.getArgumentTypes(desc)));
            boolean isStatic = (access & Opcodes.ACC_STATIC) != 0;
            if (!isStatic) {
                args.add(0, Type.getType(Object.class));
            }

            // Install the Jsr/Ret inliner adapter, we have had reports of code still using the
            // Jsr/Ret deprecated byte codes.
            // see https://code.google.com/p/android/issues/detail?id=220019
            JSRInlinerAdapter jsrInlinerAdapter = new JSRInlinerAdapter(
                    defaultVisitor, access, name, desc, signature, exceptions);

            ISMethodVisitor mv = new ISMethodVisitor(jsrInlinerAdapter, access, name, desc);
            if (name.equals(ByteCodeUtils.CONSTRUCTOR)) {

                if (patchInitMethod) {
                    Constructor constructor = ConstructorBuilder.build(visitedClassName, method);
                    LabelNode start = new LabelNode();
                    method.instructions.insert(constructor.loadThis, start);
                    if (constructor.lineForLoad != -1) {
                        // Record the line number from the start of LOAD_0 for uninitialized 'this'.
                        // This allows a breakpoint to be set at the line with this(...) or super(...)
                        // call in the constructor.
                        method.instructions.insert(constructor.loadThis,
                                new LineNumberNode(constructor.lineForLoad, start));
                    }
                    mv.addRedirection(new TBConstructorRedirection(start, constructor, args));

                } else {
                    return defaultVisitor;
                }

            } else {
                mv.addRedirection(new TBMethodRedirection(
                        new LabelNode(mv.getStartLabel()),
                        name + "." + desc,
                        args,
                        Type.getReturnType(desc)));
            }
            method.accept(mv);
            return null;
        }
    }

    /**
     * If a class is package private, make it public so instrumented code living in a different
     * class loader can instantiate them.
     *
     * <p>We also set the {@code ACC_SUPER} flag on every class, since otherwise the
     * {@code invokespecial} bytecodes in access$super get compiled to invoke-direct, which fails
     * at runtime (on KitKat). See the VM spec (4.1) for the whole story of {@code ACC_SUPER}. It
     * is only missing in classes generated by third-party tools, e.g. AspectJ.
     *
     * @param access the original class/method/field access.
     * @return the new access or the same one depending on the original access rights.
     */
    private static int transformClassAccessForInstantRun(int access) {
        AccessRight accessRight = AccessRight.fromNodeAccess(access);
        int fixedVisibility = accessRight == AccessRight.PACKAGE_PRIVATE
                ? access | Opcodes.ACC_PUBLIC
                : access;

        // TODO: only do this on KitKat?
        return fixedVisibility | Opcodes.ACC_SUPER;
    }

    /**
     * If a method/field is not private, make it public. This is to workaround the fact
     * <ul>Our restart.dex files are loaded with a different class loader than the main dex file
     * class loader on restart. so we need methods/fields to be public</ul>
     * <ul>Our reload.dex are loaded from a different class loader as well but methods/fields
     * are accessed through reflection, yet you need class visibility.</ul>
     * <p>
     * remember that in Java, protected methods or fields can be acessed by classes in the same
     * package :
     * {@see https://docs.oracle.com/javase/tutorial/java/javaOO/accesscontrol.html}
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

    private class ISMethodVisitor extends GeneratorAdapter {

        private boolean disableRedirection = false;
        private int change;
        private final List<Type> args;
        private final List<Redirection> redirections;
        private final Map<Label, Redirection> resolvedRedirections;
        private final Label start;
        private String fullName;

        public ISMethodVisitor(MethodVisitor mv, int access, String name, String desc) {
            super(Opcodes.ASM5, mv, access, name, desc);
            this.change = -1;
            this.redirections = new ArrayList<>();
            this.resolvedRedirections = new HashMap<>();
            this.args = new ArrayList<>(Arrays.asList(Type.getArgumentTypes(desc)));
            this.start = new Label();
            boolean isStatic = (access & Opcodes.ACC_STATIC) != 0;
             fullName = name + "." + desc;

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
                if (supportEachMethod){
                    visitFieldInsn(Opcodes.GETSTATIC, visitedClassName, "$ipChange"+"$"+fullName.hashCode(),
                            getRuntimeTypeName(ALI_CHANGE_TYPE));
                }else {
                    visitFieldInsn(Opcodes.GETSTATIC, visitedClassName, "$ipChange",
                            getRuntimeTypeName(ALI_CHANGE_TYPE));
                }
                storeLocal(change);

                redirectAt(start);
            }
            super.visitCode();
        }

        @Override
        public void visitLabel(Label label) {
            super.visitLabel(label);
            redirectAt(label);
        }

        private void redirectAt(Label label) {
            if (disableRedirection) return;
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
        public void visitLocalVariable(String name, String desc, String signature, Label start,
                                       Label end, int index) {
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

    /**
     * Decorated {@link MethodNode} that maintains a reference to the class declaring the method.
     */
    public static class MethodReference {
        final MethodNode method;
        final ClassNode owner;

        private MethodReference(MethodNode method, ClassNode owner) {
            this.method = method;
            this.owner = owner;
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

        final Map<String, MethodReference> uniqueMethods = new HashMap<>();
        if (parentNodes.isEmpty()) {
            // if we cannot determine the parents for this class, let's blindly add all the
            // method of the current class as a gateway to a possible parent version.
            addAllNewMethods(classNode, classNode, uniqueMethods,null);
        } else {
            // otherwise, use the parent list.

            for (ClassNode parentNode : parentNodes) {

                addAllNewMethods(classNode, parentNode, uniqueMethods,supportAddCallSuper? null:visitSuperMethods);
            }
        }

        if (uniqueMethods.size() == 0){
            return;
        }


        int access = Opcodes.ACC_STATIC | Opcodes.ACC_PUBLIC
                | Opcodes.ACC_SYNTHETIC | Opcodes.ACC_VARARGS;
        Method m = new Method("ipc$super", "(L" + visitedClassName
                + ";Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/Object;");
        MethodVisitor visitor = super.visitMethod(access,
                m.getName(),
                m.getDescriptor(),
                null, null);

        final GeneratorAdapter mv = new GeneratorAdapter(access, m, visitor);

        // Gather all methods from itself and its superclasses to generate a giant access$super
        // implementation.
        // This will work fine as long as we don't support adding methods to a class.

        new StringSwitch() {
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
                        parentName,
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
//                mv.visitInsn(Opcodes.RETURN);


                writeMissingMessageWithHash(mv, visitedClassName);
            }
        }.visit(mv, uniqueMethods.keySet());

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

        addAllNewConstructors(uniqueMethods, classNode, true /*keepPrivateConstructors*/);
        for (ClassNode parentNode : parentNodes) {
            addAllNewConstructors(uniqueMethods, parentNode, false /*keepPrivateConstructors*/);
        }

        int access = Opcodes.ACC_PUBLIC | Opcodes.ACC_SYNTHETIC;

        Method m = new Method(ByteCodeUtils.CONSTRUCTOR,
                ALI_DISPATCHING_THIS_SIGNATURE);
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

        new StringSwitch() {

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
//                mv.visitInsn(Opcodes.RETURN);
                writeMissingMessageWithHash(mv, visitedClassName);
            }

            @Override
            void visit(GeneratorAdapter mv, Set<String> strings) {
                super.visit(mv, strings);

            }
        }.visit(mv, uniqueMethods.keySet());

        mv.visitMaxs(1, 3);
        mv.visitEnd();
    }

    @Override
    public void visitEnd() {
        if (!visitedSuperName.equals("java/lang/Object")) {
            createAccessSuper();
        }
        if (patchInitMethod) {
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
        if (isParentClassVisible(methodReference.owner, classNode)) {
            return methodReference.owner.name;
        }
        logger.verbose("Found an inaccessible methodReference %1$s", methodReference.method.name);
        // walk up the hierarchy, starting at the method reference owner.
        Iterator<ClassNode> parentIterator = parentNodes.iterator();
        ClassNode parent = parentIterator.next();
        while (!parent.name.equals(methodReference.owner.name)
                && parentIterator.hasNext()) {
            parent = parentIterator.next();
        }
        while (parentIterator.hasNext()) {
            ClassNode node = parentIterator.next();
            // check that this parent is visible, there might be several layers of package
            // private classes.
            if (isParentClassVisible(node, classNode)) {
                //noinspection unchecked: ASM API
                for (MethodNode methodNode : (List<MethodNode>) node.methods) {
                    // do not reference bridge methods, they might not be translated into dex, or
                    // might disappear in the next javac compiler for that use case.
                    if (methodNode.name.equals(methodReference.method.name)
                            && methodNode.desc.equals(methodReference.method.desc)
                            && (methodNode.access
                            & (Opcodes.ACC_BRIDGE | Opcodes.ACC_ABSTRACT)) == 0) {
                        logger.verbose(
                                "Using class %1$s for dispatching %2$s:%3$s",
                                node.name,
                                methodReference.method.name,
                                methodReference.method.desc);
                        return node.name;
                    }
                }
            }
        }
        logger.verbose("Using immediate parent for dispatching %1$s", methodReference.method.desc);
        return classNode.superName;
    }

    private static boolean isParentClassVisible(@NonNull ClassNode parent,
                                                @NonNull ClassNode child) {

        return ((parent.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED)) != 0 ||
                com.google.common.base.Objects.equal(ByteCodeUtils.getPackageName(parent.name),
                        ByteCodeUtils.getPackageName(child.name)));
    }

    /**
     * Add all unseen methods from the passed ClassNode's methods.
     *
     * @param instrumentedClass class that is being visited
     * @param superClass        the class to save all new methods from
     * @param methods           the methods already encountered in the ClassNode hierarchy
     * @param visitSuperMethods
     * @see ClassNode#methods
     */
    public static void addAllNewMethods(
            ClassNode instrumentedClass,
            ClassNode superClass,
            Map<String, MethodReference> methods, List<String> visitSuperMethods) {

        //noinspection unchecked
        if (superClass.name.equals("java/lang/Object")){
            return;
        }
        for (MethodNode method : (List<MethodNode>) superClass.methods) {
            if (method.name.equals(ByteCodeUtils.CONSTRUCTOR) || method.name.equals(ByteCodeUtils.CLASS_INITIALIZER)) {
                continue;
            }
            String name = method.name + "." + method.desc;
            if (isAccessCompatibleWithInstantRun(method.access)
                    && !methods.containsKey(name)
                    && (method.access & Opcodes.ACC_STATIC) == 0
                    && isCallableFromSubclass(method, superClass, instrumentedClass)
                    ) {
                if (visitSuperMethods == null){
                    methods.put(name, new MethodReference(method, superClass));
                }else if (visitSuperMethods.contains(name)) {
                    methods.put(name, new MethodReference(method, superClass));
                }
            }
        }
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


    private static final class TBVisitorBuilder implements IncrementalVisitor.VisitorBuilder {

        private TBVisitorBuilder() {
        }

        @NonNull
        @Override
        public IncrementalVisitor build(
                @NonNull ClassNode classNode,
                @NonNull List<ClassNode> parentNodes,
                @NonNull ClassVisitor classVisitor,
                @NonNull ILogger logger) {
            return new TBIncrementalSupportVisitor(classNode, parentNodes, classVisitor, logger);
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
    public static final IncrementalVisitor.VisitorBuilder VISITOR_BUILDER = new TBIncrementalSupportVisitor.TBVisitorBuilder();
}
