package com.android.build.gradle.internal.incremental;

import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.tree.LabelNode;

import java.util.List;

/**
 * 创建日期：2019/1/5 on 上午11:12
 * 描述:
 * 作者:zhayu.ll
 */
public class TBMethodRedirection extends MethodRedirection {
    private String name;
    private String code;


    TBMethodRedirection(LabelNode label, String name, List<Type> types, Type type) {
        super(label, name, types, type);
        this.name = name;
        this.code = Integer.toHexString((name).hashCode());
    }

    @Override
    void redirect(GeneratorAdapter mv, int change) {


        Label l0 = new Label();
//        mv.loadLocal(change);
//        mv.visitJumpInsn(Opcodes.IFNULL, l0);
        mv.loadLocal(change);
        mv.visitTypeInsn(Opcodes.INSTANCEOF, TBIncrementalVisitor.ALI_CHANGE_TYPE.getInternalName());
        mv.visitJumpInsn(Opcodes.IFEQ, l0);


        doRedirect(mv, change);

        if (type == Type.VOID_TYPE) {
            mv.pop();
        } else {
            ByteCodeUtils.unbox(mv, type);
        }
        mv.returnValue();

        mv.visitLabel(l0);

    }

    @Override
    protected void doRedirect(GeneratorAdapter mv, int change) {
        // Push the three arguments
        mv.loadLocal(change);
        mv.push(code);
        ByteCodeUtils.newVariableArray(mv, ByteCodeUtils.toLocalVariables(types));

        // now invoke the generic dispatch method.
        mv.invokeInterface(TBIncrementalSupportVisitor.ALI_CHANGE_TYPE, Method.getMethod("Object ipc$dispatch(String, Object[])"));
    }
}
