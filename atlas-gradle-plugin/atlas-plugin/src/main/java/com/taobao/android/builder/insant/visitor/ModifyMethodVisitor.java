package com.taobao.android.builder.insant.visitor;

import com.taobao.android.builder.insant.TaobaoInstantRunTransform;
import com.android.build.gradle.internal.incremental.TBIncrementalVisitor;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.MethodVisitor;

/**
 * 创建日期：2018/11/30 on 上午10:03
 * 描述:
 * 作者:zhayu.ll
 */
public class ModifyMethodVisitor extends MethodVisitor {

    private TaobaoInstantRunTransform.CodeChange codeChange;

    private String methodName;

    private String methodDesc;

    public ModifyMethodVisitor(String name, String desc, int api, MethodVisitor mv, TaobaoInstantRunTransform.CodeChange codeChange) {
        super(api,mv);
        this.codeChange = codeChange;
        this.methodName = name;
        this.methodDesc = desc;
    }

    @Override
    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
        if (desc.equals(TBIncrementalVisitor.MODIFY_METHOD.getDescriptor()) && visible && codeChange.getPy()!= TaobaoInstantRunTransform.PatchPolicy.ADD) {
            codeChange.setPy(TaobaoInstantRunTransform.PatchPolicy.MODIFY);
            TaobaoInstantRunTransform.CodeChange codeChange = new TaobaoInstantRunTransform.CodeChange();
            codeChange.setPy(TaobaoInstantRunTransform.PatchPolicy.MODIFY);
            codeChange.setCode(methodName+"."+methodDesc);
            this.codeChange.getCodeChanges().add(codeChange);
        }else if (desc.equals(TBIncrementalVisitor.ADD_METHOD.getDescriptor()) && visible){
            codeChange.setPy(TaobaoInstantRunTransform.PatchPolicy.MODIFY);
            TaobaoInstantRunTransform.CodeChange codeChange = new TaobaoInstantRunTransform.CodeChange();
            codeChange.setPy(TaobaoInstantRunTransform.PatchPolicy.ADD);
            codeChange.setCode(methodName+"|"+methodDesc);
            this.codeChange.getCodeChanges().add(codeChange);
        }
        return super.visitAnnotation(desc, visible);
    }
}
