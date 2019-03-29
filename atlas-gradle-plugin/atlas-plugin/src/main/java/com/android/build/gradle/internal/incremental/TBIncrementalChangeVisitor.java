package com.android.build.gradle.internal.incremental;

import com.android.annotations.NonNull;
import com.android.annotations.VisibleForTesting;
import com.android.utils.ILogger;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.JSRInlinerAdapter;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 创建日期：2019/1/5 on 上午10:54
 * 描述:
 * 作者:zhayu.ll
 */
public class TBIncrementalChangeVisitor extends TBIncrementalVisitor {

    public static final VisitorBuilder VISITOR_BUILDER = new IncrementalVisitor.VisitorBuilder() {
        @NonNull
        @Override
        public IncrementalVisitor build(@NonNull ClassNode classNode,
                                        @NonNull List<ClassNode> parentNodes,
                                        @NonNull ClassVisitor classVisitor,
                                        @NonNull ILogger logger) {
            return new TBIncrementalChangeVisitor(classNode, parentNodes, classVisitor, logger);
        }

        @NonNull
        @Override
        public String getMangledRelativeClassFilePath(@NonNull String path) {
            // Remove .class (length 6) and replace with $override.class
            return path.substring(0, path.length() - 6) + OVERRIDE_SUFFIX + ".class";
        }

        @NonNull
        @Override
        public OutputType getOutputType() {
            return OutputType.OVERRIDE;
        }
    };

    // todo : find a better way to specify logging and append to a log file.
    private static final boolean DEBUG = false;

    @VisibleForTesting
    public static final String OVERRIDE_SUFFIX = "$ipReplace";

    private static final String METHOD_MANGLE_PREFIX = "static$";

    private TBIncrementalChangeVisitor.MachineState state = TBIncrementalChangeVisitor.MachineState.NORMAL;
    private boolean instantRunDisabled = false;

    // Description prefix used to add fake "this" as the first argument to each instance method
    // when converted to a static method.
    private String instanceToStaticDescPrefix;

    // List of constructors we encountered and deconstructed.
    List<MethodNode> addedMethods = new ArrayList<>();

    private enum MachineState {
        NORMAL, AFTER_NEW
    }

    public TBIncrementalChangeVisitor(
            @NonNull ClassNode classNode,
            @NonNull List<ClassNode> parentNodes,
            @NonNull ClassVisitor classVisitor,
            @NonNull ILogger logger) {
        super(classNode, parentNodes, classVisitor, logger);
    }

    /**
     * Turns this class into an override class that can be loaded by our custom class loader:
     *<ul>
     *   <li>Make the class name be OriginalName$override</li>
     *   <li>Ensure the class derives from java.lang.Object, no other inheritance</li>
     *   <li>Ensure the class has a public parameterless constructor that is a noop.</li>
     *</ul>
     */
    @Override
    public void visit(int version, int access, String name, String signature, String superName,
                      String[] interfaces) {
        super.visit(version, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER,
                name + OVERRIDE_SUFFIX, signature, "java/lang/Object",
                new String[]{TBIncrementalSupportVisitor.ALI_CHANGE_TYPE.getInternalName()});

        if (DEBUG) {
            System.out.println(">>>>>>>> Processing " + name + "<<<<<<<<<<<<<");
        }

        visitedClassName = name;
        visitedSuperName = superName;
        instanceToStaticDescPrefix = "(L" + visitedClassName + ";";

        // Create empty constructor
        MethodVisitor mv = super
                .visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V",
                false);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        super.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_SYNTHETIC | Opcodes.ACC_STATIC,
                "$ipObsolete", "Z", null, null);
    }

    @Override
    public void visitOuterClass(String owner, String name, String desc) {
        // Ignore, the class hierarchy is not relevant in the override classes.
    }

    @Override
    public void visitInnerClass(String name, String outerName, String innerName, int access) {
        // Ignore, the class hierarchy is not relevant in the override classes.
    }

    @Override
    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
        if (TBIncrementalSupportVisitor.ALI_DISABLE_ANNOTATION_TYPE.getDescriptor().equals(desc)) {
            instantRunDisabled = true;
        }
        return super.visitAnnotation(desc, visible);
    }

    /**
     * Generates new delegates for all 'patchable' methods in the visited class. Delegates
     * are static methods that do the same thing the visited code does, but from outside the class.
     * For instance methods, the instance is passed as the first argument. Note that:
     * <ul>
     *   <li>We ignore the class constructor as we don't support it right now</li>
     *   <li>We skip abstract methods.</li>
     *   <li>For constructors split the method body into super arguments and the rest of
     *   the method body, see {@link ConstructorBuilder}</li>
     * </ul>
     */
    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature,
                                     String[] exceptions) {

        if (instantRunDisabled || !isAccessCompatibleWithInstantRun(access)) {
            // Nothing to generate.
            return null;
        }
        if (name.equals(ByteCodeUtils.CLASS_INITIALIZER)) {
            // we skip the class init as it can reset static fields which we don't support right now
            return null;
        }

        boolean isStatic = (access & Opcodes.ACC_STATIC) != 0;
        String newDesc = computeOverrideMethodDesc(desc, isStatic);

        if (DEBUG) {
            System.out.println(">>> Visiting method " + visitedClassName + ":" + name + ":" + desc);
            if (exceptions != null) {
                for (String exception : exceptions) {
                    System.out.println("> Exception thrown : " + exception);
                }
            }
        }
        if (DEBUG) {
            System.out.println("New Desc is " + newDesc + ":" + isStatic);
        }

        // Do not carry on any access flags from the original method. For example synchronized
        // on the original method would translate into a static synchronized method here.
        access = Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC;
        MethodNode method = getMethodByNameInClass(name, desc, classNode);
        if (name.equals(ByteCodeUtils.CONSTRUCTOR)) {
            Constructor constructor = ConstructorBuilder.build(visitedClassName, method);

            MethodVisitor mv = createMethodAdapter(access, constructor.args.name,
                    constructor.args.desc, constructor.args.desc, constructor.args.signature,
                    exceptions, isStatic, true /* isConstructor */);
            constructor.args.accept(mv);

            mv = createMethodAdapter(access, constructor.body.name,
                    constructor.body.desc, newDesc, constructor.body.signature, exceptions,
                    isStatic, true /* isConstructor */);
            constructor.body.accept(mv);

            // Remember our created methods so we can generated the access$dispatch for them.
            addedMethods.add(constructor.args);
            addedMethods.add(constructor.body);
            return null;
        } else {
            String newName = isStatic ? computeOverrideMethodName(name, desc) : name;
            return createMethodAdapter(access, newName, newDesc, newDesc, signature, exceptions,
                    isStatic, false /* isConstructor */);
        }
    }

    /**
     * Creates a method adapter that will instrument to original code in such a way that it can
     * patch live code.
     * @param access the method access flags.
     * @param name the method name
     * @param originalDesc the original description.
     * @param newDesc the modified method description that suits the InstantRun patching algorithms
     * @param signature the method signature.
     * @param exceptions the exceptions thrown by the method
     * @param isStatic true if the method is static, false otherwise.
     * @param isConstructor true if a constructor. false otherwise.
     * @return the method adapter visitor.
     */
    private MethodVisitor createMethodAdapter(
            int access,
            String name,
            String originalDesc,
            String newDesc,
            String signature,
            String[] exceptions,
            boolean isStatic,
            boolean isConstructor) {

        MethodVisitor methodVisitor =
                super.visitMethod(access, name, originalDesc, signature,  exceptions);
        methodVisitor = new TBIncrementalChangeVisitor.ISVisitor(methodVisitor, access, name, newDesc, isStatic, isConstructor);
        // Install the Jsr/Ret inliner adapter, we have had reports of code still using the
        // Jsr/Ret deprecated byte codes.
        // see https://code.google.com/p/android/issues/detail?id=220019
        return new JSRInlinerAdapter(methodVisitor, access, name, newDesc, signature, exceptions);
    }

    @Override
    public FieldVisitor visitField(int access, String name, String desc, String signature,
                                   Object value) {
        // do not add any of the original class fields in the $override class, they would never
        // be used and confuse the debugger.
        return null;
    }

    public class ISVisitor extends GeneratorAdapter {

        private final boolean isStatic;
        private final boolean isConstructor;

        /**
         * Instrument a method.
         * @param mv the parent method visitor.
         * @param access the method access flags.
         * @param name method name.
         * @param desc method signature.
         * @param isStatic true if the instrumented method was originally a static method.
         * @param isConstructor true if  the instrumented code was originally a constructor body.
         */
        public ISVisitor(
                MethodVisitor mv,
                int access,
                String name,
                String desc,
                boolean isStatic,
                boolean isConstructor) {
            super(Opcodes.ASM5, mv, access, name, desc);
            this.isStatic = isStatic;
            this.isConstructor = isConstructor;
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String desc) {
            if (DEBUG) {
                System.out.println(
                        "Visit field access : " + owner + ":" + name + ":" + desc + ":" + isStatic);
            }
            AccessRight accessRight;
            if (!owner.equals(visitedClassName)) {
                if (DEBUG) {
                    System.out.println(owner + ":" + name + " field access");
                }
                // we are accessing another object field, and at this point the visitor is not smart
                // enough to know if has seen this class before or not so we must assume the field
                // is *not* accessible from the $override class which lives in a different
                // hierarchy and package.
                // However, since we made all package-private and protected fields public, and it
                // cannot be private since the visitedClassName is not the "owner", we can safely
                // assume it's public.
                accessRight = AccessRight.PUBLIC;
            } else {
                // check the field access bits.
                FieldNode fieldNode = getFieldByName(name);
                if (fieldNode == null) {
                    // If this is an inherited field, we might not have had access to the parent
                    // bytecode. In such a case, treat it as private.
                    accessRight = AccessRight.PACKAGE_PRIVATE;
                } else {
                    accessRight = AccessRight.fromNodeAccess(fieldNode.access);
                }
            }

            boolean handled = false;
            switch(opcode) {
                case Opcodes.PUTSTATIC:
                case Opcodes.GETSTATIC:
                    handled = visitStaticFieldAccess(opcode, owner, name, desc, accessRight);
                    break;
                case Opcodes.PUTFIELD:
                case Opcodes.GETFIELD:
                    handled = visitFieldAccess(opcode, owner, name, desc, accessRight);
                    break;
                default:
                    System.out.println("Unhandled field opcode " + opcode);
            }
            if (!handled) {
                super.visitFieldInsn(opcode, owner, name, desc);
            }
        }

        /**
         * Visits an instance field access. The field could be of the visited class or it could be
         * an accessible field from the class being visited (unless it's private).
         * <p>
         * For private instance fields, the access instruction is rewritten to calls to reflection
         * to access the fields value:
         * <p>
         * Pseudo code for Get:
         * <code>
         *   value = $instance.fieldName;
         * </code>
         * becomes:
         * <code>
         *   value = (unbox)$package/AndroidInstantRuntime.getPrivateField($instance, $fieldName);
         * </code>
         * <p>
         * Pseudo code for Set:
         * <code>
         *   $instance.fieldName = value;
         * </code>
         * becomes:
         * <code>
         *   $package/AndroidInstantRuntime.setPrivateField($instance, value, $fieldName);
         * </code>
         *
         *
         * @param opcode the field access opcode, can only be {@link Opcodes#PUTFIELD} or
         *               {@link Opcodes#GETFIELD}
         * @param owner the field declaring class
         * @param name the field name
         * @param desc the field type
         * @param accessRight the {@link AccessRight} for the field.
         * @return true if the field access was handled or false otherwise.
         */
        private boolean visitFieldAccess(
                int opcode, String owner, String name, String desc, AccessRight accessRight) {

            // if the accessed field is anything but public, we must go through reflection.
            boolean useReflection = accessRight != AccessRight.PUBLIC;

            // if the accessed field is accessed from within a constructor, it might be a public
            // final field that cannot be set by anything but the original constructor unless
            // we use reflection.
            if (!useReflection) {
                useReflection = isConstructor && (owner.equals(visitedClassName));
            }

            if (useReflection) {
                // we should make this more efficient, have a per field access type method
                // for getting and setting field values.
                switch (opcode) {
                    case Opcodes.GETFIELD:
                        if (DEBUG) {
                            System.out.println("Get field");
                        }
                        // push declaring class
                        visitLdcInsn(Type.getType("L" + owner + ";"));

                        // the instance of the owner class we are getting the field value from
                        // is on top of the stack. It could be "this"
                        push(name);

                        // Stack :  <receiver>
                        //          <field_declaring_class>
                        //          <field_name>
                        invokeStatic(ALI_RUNTIME_TYPE,
                                Method.getMethod("Object getPrivateField(Object, Class, String)"));
                        // Stack : <field_value>
                        ByteCodeUtils.unbox(this, Type.getType(desc));
                        break;
                    case Opcodes.PUTFIELD:
                        if (DEBUG) {
                            System.out.println("Set field");
                        }
                        // the instance of the owner class we are getting the field value from
                        // is second on the stack. It could be "this"
                        // top of the stack is the new value we are trying to set, box it.
                        box(Type.getType(desc));

                        // push declaring class
                        visitLdcInsn(Type.getType("L" + owner + ";"));
                        // push the field name.
                        push(name);
                        // Stack :  <receiver>
                        //          <boxed_field_value>
                        //          <field_declaring_class>
                        //          <field_name>
                        invokeStatic(ALI_RUNTIME_TYPE,
                                Method.getMethod(
                                        "void setPrivateField(Object, Object, Class, String)"));
                        break;
                    default:
                        throw new RuntimeException(
                                "VisitFieldAccess called with wrong opcode " + opcode);
                }
                return true;
            }
            // if this is a public field, no need to change anything we can access it from the
            // $override class.
            return false;
        }

        /**
         * Static field access visit.
         * So far we do not support class initializer "clinit" that would reset the static field
         * value in the class newer versions. Think about the case, where a static initializer
         * resets a static field value, we don't know if the current field value was set through
         * the initial class initializer or some code path, should we change the field value to the
         * new one ?
         *
         * For private static fields, the access instruction is rewritten to calls to reflection
         * to access the fields value:
         * <p>
         * Pseudo code for Get:
         * <code>
         *   value = $type.fieldName;
         * </code>
         * becomes:
         * <code>
         *   value = (unbox)$package/AndroidInstantRuntime.getStaticPrivateField(
         *       $type.class, $fieldName);
         * </code>
         * <p>
         * Pseudo code for Set:
         * <code>
         *   $type.fieldName = value;
         * </code>
         * becomes:
         * <code>
         *   $package/AndroidInstantRuntime.setStaticPrivateField(value, $type.class $fieldName);
         * </code>
         *
         * @param opcode the field access opcode, can only be {@link Opcodes#PUTSTATIC} or
         *               {@link Opcodes#GETSTATIC}
         * @param name the field name
         * @param desc the field type
         * @param accessRight the {@link AccessRight} for the field.
         * @return true if the field access was handled or false
         */
        private boolean visitStaticFieldAccess(
                int opcode, String owner, String name, String desc, AccessRight accessRight) {

            if (accessRight != AccessRight.PUBLIC) {
                switch (opcode) {
                    case Opcodes.GETSTATIC:
                        if (DEBUG) {
                            System.out.println("Get static field " + name);
                        }
                        // nothing of interest is on the stack.
                        visitLdcInsn(Type.getType("L" + owner + ";"));
                        push(name);
                        // Stack : <target_class>
                        //         <field_name>
                        invokeStatic(ALI_RUNTIME_TYPE,
                                Method.getMethod("Object getStaticPrivateField(Class, String)"));
                        // Stack : <field_value>
                        ByteCodeUtils.unbox(this, Type.getType(desc));
                        return true;
                    case Opcodes.PUTSTATIC:
                        if (DEBUG) {
                            System.out.println("Set static field " + name);
                        }
                        // the new field value is on top of the stack.
                        // box it into an Object.
                        box(Type.getType(desc));
                        visitLdcInsn(Type.getType("L" + owner + ";"));
                        push(name);
                        // Stack :  <boxed_field_value>
                        //          <target_class>
                        //          <field_name>
                        invokeStatic(ALI_RUNTIME_TYPE,
                                Method.getMethod(
                                        "void setStaticPrivateField(Object, Class, String)"));
                        return true;
                    default:
                        throw new RuntimeException(
                                "VisitStaticFieldAccess called with wrong opcode " + opcode);
                }
            }
            return false;
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc,
                                    boolean itf) {

            if (DEBUG) {
                System.out.println("Generic Method dispatch : " + opcode +
                        ":" + owner + ":" + name + ":" + desc + ":" + itf + ":" + isStatic);
            }
            boolean opcodeHandled = false;
            if (opcode == Opcodes.INVOKESPECIAL) {
                opcodeHandled = handleSpecialOpcode(owner, name, desc, itf);
            } else if (opcode == Opcodes.INVOKEVIRTUAL) {
                opcodeHandled = handleVirtualOpcode(owner, name, desc, itf);
            } else if (opcode == Opcodes.INVOKESTATIC) {
                opcodeHandled = handleStaticOpcode(owner, name, desc, itf);
            }
            if (DEBUG) {
                System.out.println("Opcode handled ? " + opcodeHandled);
            }
            if (!opcodeHandled) {
                mv.visitMethodInsn(opcode, owner, name, desc, itf);
            }
            if (DEBUG) {
                System.out.println("Done with generic method dispatch");
            }
        }

        /**
         * Rewrites INVOKESPECIAL method calls:
         * <ul>
         *  <li>calls to constructors are handled specially (see below)</li>
         *  <li>calls to super methods are rewritten to call the 'access$super' trampoline we
         *      injected into the original code</li>
         *  <li>calls to methods in this class are rewritten to call the mathcin $override class
         *  static method</li>
         * </ul>
         */
        private boolean handleSpecialOpcode(String owner, String name, String desc,
                                            boolean itf) {
            if (name.equals("<init>")) {
                return handleConstructor(owner, name, desc);
            }
            if (owner.equals(visitedClassName)) {
                if (DEBUG) {
                    System.out.println(
                            "Private Method : " + name + ":" + desc + ":" + itf + ":" + isStatic);
                }
                // private method dispatch, just invoke the $override class static method.
                String newDesc = computeOverrideMethodDesc(desc, false /*isStatic*/);
                super.visitMethodInsn(Opcodes.INVOKESTATIC, owner + "$ipReplace", name, newDesc, itf);
                return true;
            } else {
                if (DEBUG) {
                    System.out.println(
                            "Super Method : " + name + ":" + desc + ":" + itf + ":" + isStatic);
                }
                int arr = boxParametersToNewLocalArray(Type.getArgumentTypes(desc));
                push(name + "." + desc);
                loadLocal(arr);
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, visitedClassName, "ipc$super",
                        instanceToStaticDescPrefix
                                + "Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/Object;",
                        false);
                handleReturnType(desc);

                return true;
            }
        }

        /**
         * Rewrites INVOKEVIRTUAL method calls.
         * <p>
         * Virtual calls to protected methods are rewritten according to the following pseudo code:
         * before:
         * <code>
         *   $value = $instance.protectedVirtual(arg1, arg2);
         * </code>
         * after:
         * <code>
         *   $value = (unbox)$package/AndroidInstantRuntime.invokeProtectedMethod($instance,
         *          new object[] {arg1, arg2}, new Class[] { String.class, Integer.class },
         *          "protectedVirtual");
         * </code>
         */
        private boolean handleVirtualOpcode(String owner, String name, String desc, boolean itf) {

            if (DEBUG) {
                System.out.println(
                        "Virtual Method : " + name + ":" + desc + ":" + itf + ":" + isStatic);

            }
            AccessRight accessRight = getMethodAccessRight(owner, name, desc);
            if (accessRight == AccessRight.PUBLIC) {
                return false;
            }

            // for anything else, private, protected and package private, we must go through
            // reflection.
            // Stack : <receiver>
            //      <param_1>
            //      <param_2>
            //      ...
            //      <param_n>
            pushMethodRedirectArgumentsOnStack(name, desc);

            // Stack : <receiver>
            //      <array of parameter_values>
            //      <array of parameter_types>
            //      <method_name>
            invokeStatic(ALI_RUNTIME_TYPE, Method.getMethod(
                    "Object invokeProtectedMethod(Object, Object[], Class[], String)"));
            // Stack : <return value or null if no return value>
            handleReturnType(desc);
            return true;
        }

        /**
         * Rewrites INVOKESTATIC method calls.
         * <p>
         * Static calls to non-public methods are rewritten according to the following pseudo code:
         * before:
         * <code>
         *   $value = $type.protectedStatic(arg1, arg2);
         * </code>
         * after:
         * <code>
         *   $value = (unbox)$package/AndroidInstantRuntime.invokeProtectedStaticMethod(
         *          new object[] {arg1, arg2}, new Class[] { String.class, Integer.class },
         *          "protectedStatic", $type.class);
         * </code>
         */
        private boolean handleStaticOpcode(String owner, String name, String desc, boolean itf) {

            if (DEBUG) {
                System.out.println(
                        "Static Method : " + name + ":" + desc + ":" + itf + ":" + isStatic);

            }
            AccessRight accessRight = getMethodAccessRight(owner, name, desc);
            if (accessRight == AccessRight.PUBLIC) {
                return false;
            }

            // for anything else, private, protected and package private, we must go through
            // reflection.

            // stack: <param_1>
            //      <param_2>
            //      ...
            //      <param_n>
            pushMethodRedirectArgumentsOnStack(name, desc);

            // push the class implementing the original static method
            visitLdcInsn(Type.getType("L" + owner + ";"));

            // stack: <boxed method parameter>
            //      <target parameter types>
            //      <target method name>
            //      <target class name>
            invokeStatic(ALI_RUNTIME_TYPE, Method.getMethod(
                    "Object invokeProtectedStaticMethod(Object[], Class[], String, Class)"));
            // stack : method return value or null if the method was VOID.
            handleReturnType(desc);
            return true;
        }

        @Override
        public void visitTypeInsn(int opcode, String s) {
            if (opcode == Opcodes.NEW) {
                // state can only normal or dup_after new
                if (state == TBIncrementalChangeVisitor.MachineState.AFTER_NEW) {
                    throw new RuntimeException("Panic, two NEW opcode without a DUP");
                }

                if (isInSamePackage(s)) {
                    // this is a new allocation in the same package, this could be protected or
                    // package private class, we must go through reflection, otherwise not.
                    // set our state so we swallow the next DUP we encounter.
                    state = TBIncrementalChangeVisitor.MachineState.AFTER_NEW;

                    // swallow the NEW, we will also swallow the DUP associated with the new
                    return;
                }
            }
            super.visitTypeInsn(opcode, s);
        }

        @Override
        public void visitInsn(int opcode) {
            // check the last object allocation we encountered, if this is in the same package
            // we need to go through reflection and should therefore remove the DUP, otherwise
            // we leave it.
            if (opcode == Opcodes.DUP && state == TBIncrementalChangeVisitor.MachineState.AFTER_NEW) {

                state = TBIncrementalChangeVisitor.MachineState.NORMAL;
                return;
            }
            super.visitInsn(opcode);
        }

        /**
         * For calls to constructors in the same package, calls are rewritten to use reflection
         * to create the instance (see above, the NEW and DUP instructions are also removed) using
         * the following pseudo code.
         * <p>
         * before:
         * <code>
         *   $value = new $type(arg1, arg2);
         * </code>
         * after:
         * <code>
         *   $value = ($type)$package/AndroidInstantRuntime.newForClass(new Object[] {arg1, arg2 },
         *       new Class[]{ String.class, Integer.class }, $type.class);
         * </code>
         *
         */
        private boolean handleConstructor(String owner, String name, String desc) {

            if (isInSamePackage(owner)) {

                Type expectedType = Type.getType("L" + owner + ";");
                pushMethodRedirectArgumentsOnStack(name, desc);

                // pop the name, we don't need it.
                pop();
                visitLdcInsn(expectedType);

                invokeStatic(ALI_RUNTIME_TYPE, Method.getMethod(
                        "Object newForClass(Object[], Class[], Class)"));

                checkCast(expectedType);
                ByteCodeUtils.unbox(this, expectedType);
                return true;
            } else {
                return false;
            }
        }

        @Override
        public void visitLocalVariable(String name, String desc, String signature, Label start,
                                       Label end, int index) {
            // Even if we call the first argument of the static redirection "this", JDI has a
            // specific API to retrieve "thisObject" from the current stack frame, which totally
            // ignores and bypasses this variable declaration. We will not show the renamed
            // variable to the user and will redirect in Studio to be the real this object.
            // We use a name unlikely to be used, but different than "this".
            if ("this".equals(name)) {
                name = "$this";
            }
            super.visitLocalVariable(name, desc, signature, start, end, index);
        }

        @Override
        public void visitEnd() {
            if (DEBUG) {
                System.out.println("Method visit end");
            }
        }

        /**
         * Returns the actual method access right or a best guess if we don't have access to the
         * method definition.
         * @param owner the method owner class
         * @param name the method name
         * @param desc the method signature
         * @return the {@link AccessRight} for that method.
         */
        private AccessRight getMethodAccessRight(String owner, String name, String desc) {
            AccessRight accessRight;
            if (owner.equals(visitedClassName)) {
                MethodNode methodByName = getMethodByName(name, desc);
                if (methodByName == null) {
                    // we did not find the method invoked on ourselves, which mean that it really
                    // is a parent class method invocation and we just don't have access to it.
                    // the most restrictive access right in that case is protected.
                    return AccessRight.PROTECTED;
                }
                accessRight = AccessRight.fromNodeAccess(methodByName.access);
            } else {
                // we are accessing another class method, and since we make all protected and
                // package-private methods public, we can safely assume it is public.
                accessRight = AccessRight.PUBLIC;
            }
            return accessRight;
        }

        /**
         * Push arguments necessary to invoke one of the method redirect function :
         * <ul>{@link GenericInstantRuntime#invokeProtectedMethod(Object, Object[], Class[], String)}</ul>
         * <ul>{@link GenericInstantRuntime#invokeProtectedStaticMethod(Object[], Class[], String, Class)}</ul>
         *
         * This function will only push on the stack the three common arguments :
         *      Object[] the boxed parameter values
         *      Class[] the parameter types
         *      String the original method name
         *
         * Stack before :
         *          <param1>
         *          <param2>
         *          ...
         *          <paramN>
         * Stack After :
         *          <array of parameters>
         *          <array of parameter types>
         *          <method name>
         * @param name the original method name.
         * @param desc the original method signature.
         */
        private void pushMethodRedirectArgumentsOnStack(String name, String desc) {
            Type[] parameterTypes = Type.getArgumentTypes(desc);

            // stack : <parameters values>
            int parameters = boxParametersToNewLocalArray(parameterTypes);
            // push the parameter values as a Object[] on the stack.
            loadLocal(parameters);

            // push the parameter types as a Class[] on the stack
            pushParameterTypesOnStack(parameterTypes);

            push(name);
        }

        /**
         * Creates an array of {@link Class} objects with the same size of the array of the passed
         * parameter types. For each parameter type, stores its {@link Class} object into the
         * result array. For intrinsic types which are not present in the class constant pool, just
         * push the actual {@link Type} object on the stack and let ASM do the rest. For non
         * intrinsic type use a {@link MethodVisitor#visitLdcInsn(Object)} to ensure the
         * referenced class's presence in this class constant pool.
         *
         * Stack Before : nothing of interest
         * Stack After : <array of {@link Class}>
         *
         * @param parameterTypes a method list of parameters.
         */
        private void pushParameterTypesOnStack(Type[] parameterTypes) {
            push(parameterTypes.length);
            newArray(Type.getType(Class.class));

            for (int i = 0; i < parameterTypes.length; i++) {
                dup();
                push(i);
                switch(parameterTypes[i].getSort()) {
                    case Type.OBJECT:
                    case Type.ARRAY:
                        visitLdcInsn(parameterTypes[i]);
                        break;
                    case Type.BOOLEAN:
                    case Type.CHAR:
                    case Type.BYTE:
                    case Type.SHORT:
                    case Type.INT:
                    case Type.LONG:
                    case Type.FLOAT:
                    case Type.DOUBLE:
                        push(parameterTypes[i]);
                        break;
                    default:
                        throw new RuntimeException(
                                "Unexpected parameter type " + parameterTypes[i]);

                }
                arrayStore(Type.getType(Class.class));
            }
        }

        /**
         * Handle method return logic.
         * @param desc the method signature
         */
        private void handleReturnType(String desc) {
            Type ret = Type.getReturnType(desc);
            if (ret.getSort() == Type.VOID) {
                pop();
            } else {
                ByteCodeUtils.unbox(this, ret);
            }
        }

        private int boxParametersToNewLocalArray(Type[] parameterTypes) {
            int parameters = newLocal(Type.getType("[Ljava/lang.Object;"));
            push(parameterTypes.length);
            newArray(Type.getType(Object.class));
            storeLocal(parameters);

            for (int i = parameterTypes.length - 1; i >= 0; i--) {
                loadLocal(parameters);
                swap(parameterTypes[i], Type.getType(Object.class));
                push(i);
                swap(parameterTypes[i], Type.INT_TYPE);
                box(parameterTypes[i]);
                arrayStore(Type.getType(Object.class));
            }
            return parameters;
        }
    }

    @Override
    public void visitEnd() {
        addDispatchMethod();
    }

    /**
     * To each class, add the dispatch method called by the original code that acts as a trampoline to
     * invoke the changed methods.
     * <p>
     * Pseudo code:
     * <code>
     *   Object access$dispatch(String name, object[] args) {
     *      if (name.equals(
     *          "firstMethod.(L$type;Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/Object;")) {
     *        return firstMethod(($type)arg[0], (String)arg[1], arg[2]);
     *      }
     *      if (name.equals("secondMethod.(L$type;Ljava/lang/String;I;)V")) {
     *        secondMethod(($type)arg[0], (String)arg[1], (int)arg[2]);
     *        return;
     *      }
     *      ...
     *      StringBuilder $local1 = new StringBuilder();
     *      $local1.append("Method not found ");
     *      $local1.append(name);
     *      $local1.append(" in " + visitedClassName +
     *          "$dispatch implementation, restart the application");
     *      throw new $package/InstantReloadException($local1.toString());
     *   }
     * </code>
     */
    private void addDispatchMethod() {
        int access = Opcodes.ACC_PUBLIC | Opcodes.ACC_VARARGS;
        Method m = new Method("ipc$dispatch", "(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/Object;");
        MethodVisitor visitor = super.visitMethod(access,
                m.getName(),
                m.getDescriptor(),
                null, null);

        final GeneratorAdapter mv = new GeneratorAdapter(access, m, visitor);

        if (TRACING_ENABLED) {
            mv.push("Redirecting ");
            mv.loadArg(0);
            trace(mv, 2);
        }

        List<MethodNode> allMethods = new ArrayList<>();

        // if we are disabled, do not generate any dispatch, the method will throw an exception
        // if invoked which should never happen.
        if (!instantRunDisabled) {
            //noinspection unchecked
            allMethods.addAll(classNode.methods);
            allMethods.addAll(addedMethods);
        }

        final Map<String, MethodNode> methods = new HashMap<>();
        for (MethodNode methodNode : allMethods) {
            if (methodNode.name.equals("<clinit>") || methodNode.name.equals("<init>")) {
                continue;
            }
            if (!isAccessCompatibleWithInstantRun(methodNode.access)) {
                continue;
            }
            methods.put(methodNode.name + "." + methodNode.desc, methodNode);
        }

        new StringSwitch() {
            @Override
            void visitString() {
                mv.visitVarInsn(Opcodes.ALOAD, 1);
            }

            @Override
            void visitCase(String methodName) {
                MethodNode methodNode = methods.get(methodName);
                String name = methodNode.name;
                boolean isStatic = (methodNode.access & Opcodes.ACC_STATIC) != 0;
                String newDesc =
                        computeOverrideMethodDesc(methodNode.desc, isStatic);

                if (TRACING_ENABLED) {
                    trace(mv, "M: " + name + " P:" + newDesc);
                }
                Type[] args = Type.getArgumentTypes(newDesc);
                int argc = 0;
                for (Type t : args) {
                    mv.visitVarInsn(Opcodes.ALOAD, 2);
                    mv.push(argc);
                    mv.visitInsn(Opcodes.AALOAD);
                    ByteCodeUtils.unbox(mv, t);
                    argc++;
                }
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, visitedClassName + OVERRIDE_SUFFIX,
                        isStatic ? computeOverrideMethodName(name, methodNode.desc) : name,
                        newDesc, false);
                Type ret = Type.getReturnType(methodNode.desc);
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
        }.visit(mv, methods.keySet());

        mv.visitMaxs(0, 0);
        mv.visitEnd();

        super.visitEnd();
    }


    /**
     * Returns true if the passed class name is in the same package as the visited class.
     *
     * @param type The type name of the other object, either a "com/var/Object" or a "[Type" one.
     * @return true if className and visited class are in the same java package.
     */
    private boolean isInSamePackage(@NonNull String type) {
        if (type.charAt(0) == '[') {
            return false;
        }
        return getPackage(visitedClassName).equals(getPackage(type));
    }

    /**
     * @return the package of the given / separated class name.
     */
    private String getPackage(@NonNull String className) {
        int i = className.lastIndexOf('/');
        return i == -1 ? className : className.substring(0, i);
    }

    /**
     * Returns true if the passed class name is an ancestor of the visited class.
     *
     * @param className a / separated class name
     * @return true if it is an ancestor, false otherwise.
     */
    private boolean isAnAncestor(@NonNull String className) {
        for (ClassNode parentNode : parentNodes) {
            if (parentNode.name.equals(className)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Instance methods, when converted to static methods need to have the subject object as
     * the first parameter. If the method is static, it is unchanged.
     */
    @NonNull
    private String computeOverrideMethodDesc(@NonNull String desc, boolean isStatic) {
        if (isStatic) {
            return desc;
        } else {
            return instanceToStaticDescPrefix + desc.substring(1);
        }
    }

    /**
     * Prevent method name collisions.
     *
     * A static method that takes an instance of this class as the first argument might clash with
     * a rewritten instance method, and this rewrites all methods like that. This is an
     * over-approximation of the necessary renames, but it has the advantage of neither adding
     * additional state nor requiring lookups.
     */
    @NonNull
    private String computeOverrideMethodName(@NonNull String name, @NonNull String desc) {
        if (desc.startsWith(instanceToStaticDescPrefix)
                && !name.equals("init$args")
                && !name.equals("init$body")) {
            return METHOD_MANGLE_PREFIX + name;
        }
        return name;
    }
}
