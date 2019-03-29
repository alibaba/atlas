package com.taobao.android.builder.insant.visitor;

import com.taobao.android.builder.insant.TaobaoInstantRunTransform;
import com.android.build.gradle.internal.incremental.TBIncrementalVisitor;
import org.objectweb.asm.*;

/**
 * 创建日期：2018/11/30 on 上午10:03
 * 描述:
 * 作者:zhayu.ll
 */
public class ModifyClassVisitor extends ClassVisitor {

    private TaobaoInstantRunTransform.PatchPolicy[] patchPolicy;

    public ModifyClassVisitor(int i, TaobaoInstantRunTransform.PatchPolicy[] patchingPolicy) {
        super(i);
        this.patchPolicy = patchingPolicy;
    }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            super.visit(version, access, name, signature, superName, interfaces);
        }

        @Override
        public void visitSource(String source, String debug) {
            super.visitSource(source, debug);
        }

        @Override
        public void visitOuterClass(String owner, String name, String desc) {
            super.visitOuterClass(owner, name, desc);
        }

        @Override
        public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
            if (desc.equals(TBIncrementalVisitor.ADD_CLASS.getDescriptor()) && visible) {
                patchPolicy[0] = TaobaoInstantRunTransform.PatchPolicy.ADD;
            } else if (desc.equals(TBIncrementalVisitor.MODIFY_CLASS.getDescriptor()) && visible) {
                patchPolicy[0] = TaobaoInstantRunTransform.PatchPolicy.MODIFY;
            }
            return super.visitAnnotation(desc, visible);
        }

        @Override
        public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String desc, boolean visible) {
            return super.visitTypeAnnotation(typeRef, typePath, desc, visible);
        }

        @Override
        public void visitAttribute(Attribute attr) {
            super.visitAttribute(attr);
        }

        @Override
        public void visitInnerClass(String name, String outerName, String innerName, int access) {
            super.visitInnerClass(name, outerName, innerName, access);
        }

        @Override
        public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {

            FieldVisitor fv = super.visitField(access, name, desc, signature, value);

            return new ModifyFieldVisitor(api, fv, patchPolicy);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {

            MethodVisitor mv = super.visitMethod(access,name,desc,signature,exceptions);

            return new ModifyMethodVisitor(api,mv,patchPolicy);
        }

        @Override
        public void visitEnd() {
            super.visitEnd();
        }
    }

