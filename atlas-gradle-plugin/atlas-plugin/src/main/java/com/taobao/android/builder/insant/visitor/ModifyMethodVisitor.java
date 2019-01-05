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

    private TaobaoInstantRunTransform.PatchPolicy[] patchPolicy = new TaobaoInstantRunTransform.PatchPolicy[]{TaobaoInstantRunTransform.PatchPolicy.NONE};
    public ModifyMethodVisitor(int api, MethodVisitor mv, TaobaoInstantRunTransform.PatchPolicy[] patchPolicy) {
        super(api,mv);
        this.patchPolicy = patchPolicy;
    }

    @Override
    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
        if (desc.equals(TBIncrementalVisitor.MODIFY_METHOD.getDescriptor()) && visible) {
            patchPolicy[0] = TaobaoInstantRunTransform.PatchPolicy.MODIFY;
        }else if (desc.equals(TBIncrementalVisitor.ADD_METHOD.getDescriptor()) && visible){
            throw new RuntimeException("add method is not support!");
        }
        return super.visitAnnotation(desc, visible);
    }
}
