package com.taobao.android.builder.insant;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.HashMap;
import java.util.Map;

/**
 * 创建日期：2019/6/28 on 下午4:12
 * 描述:
 * 作者:zhayu.ll
 */
public class ModifyFinalFieldVisitor extends ClassVisitor {

    private Map<String,Object>resFields = new HashMap<>();

    public ModifyFinalFieldVisitor(int asm5, ClassWriter classWriter, Map<String, Object> resFields) {
        super(asm5,classWriter);
        this.resFields = resFields;
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
        if (mv != null){
            return new VisitMethod(api,mv);
        }
        return mv;
    }


    private class VisitMethod extends MethodVisitor{

        public VisitMethod(int api, MethodVisitor mv) {
            super(api, mv);
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String desc) {
            System.err.println("visit "+opcode+":"+owner + ":" +name+":"+desc);
            String className = owner.replace("/",".");
            if (opcode == Opcodes.GETSTATIC && resFields.containsKey(className+":"+name)) {
                System.err.println("change "+owner+":"+name + " value to "+resFields.get(className+":"+name));
                visitLdcInsn(resFields.get(className+":"+name));
            }else {
                super.visitFieldInsn(opcode, owner, name, desc);
            }
        }


    }


}
