package com.android.build.gradle.internal.incremental;

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

    TBMethodRedirection(LabelNode label, String name, List<Type> types, Type type) {
        super(label, name, types, type);
        this.name = name;
    }

    @Override
    protected void doRedirect(GeneratorAdapter mv, int change) {
        // Push the three arguments
        mv.loadLocal(change);
        mv.push(name);
        ByteCodeUtils.newVariableArray(mv, ByteCodeUtils.toLocalVariables(types));

        // now invoke the generic dispatch method.
        mv.invokeInterface(TBIncrementalSupportVisitor.ALI_CHANGE_TYPE, Method.getMethod("Object ipc$dispatch(String, Object[])"));
    }
}
