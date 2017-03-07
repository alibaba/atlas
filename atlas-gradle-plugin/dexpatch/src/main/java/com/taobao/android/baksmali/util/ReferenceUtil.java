package com.taobao.android.baksmali.util;

import com.taobao.android.apatch.utils.TypeGenUtil;
import com.taobao.android.object.DexDiffInfo;

import org.jf.dexlib2.dexbacked.DexBackedClassDef;
import org.jf.dexlib2.dexbacked.DexBackedField;
import org.jf.dexlib2.iface.reference.FieldReference;
import org.jf.dexlib2.iface.reference.MethodReference;
import org.jf.dexlib2.iface.reference.Reference;
import org.jf.dexlib2.iface.reference.StringReference;
import org.jf.dexlib2.iface.reference.TypeReference;
import org.jf.util.StringUtils;

import java.io.IOException;
import java.io.Writer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

;
;

/**
 * Created by shenghua.nish on 2016-03-18 下午9:32.
 */
public class ReferenceUtil {
    public static String getMethodDescriptor(MethodReference methodReference) {
        return getMethodDescriptor(methodReference, false);
    }

    public static String getMethodDescriptor(MethodReference methodReference, boolean useImplicitReference) {
        StringBuilder sb = new StringBuilder();
        if (!useImplicitReference) {
            String clazz = methodReference.getDefiningClass();//TypeGenUtil.newType(methodReference.getDefiningClass());
            sb.append(clazz);
            sb.append("->");
        }
        sb.append(methodReference.getName());
        sb.append('(');
        for (CharSequence paramType : methodReference.getParameterTypes()) {
            sb.append(paramType);
        }
        sb.append(')');
        sb.append(methodReference.getReturnType());
        return sb.toString();
    }

    public static void writeMethodDescriptor(Writer writer, MethodReference methodReference) throws IOException {
        writeMethodDescriptor(writer, methodReference, false);
    }

    public static void writeMethodDescriptor(Writer writer, MethodReference methodReference,
                                             boolean useImplicitReference) throws IOException {
        if (!useImplicitReference) {
            String clazz = TypeGenUtil.newType(methodReference.getDefiningClass());
            writer.write(clazz);
            writer.write("->");
        }
        writer.write(methodReference.getName());
        writer.write('(');
        for (CharSequence paramType : methodReference.getParameterTypes()) {
            writer.write(paramType.toString());
        }
        writer.write(')');
        writer.write(methodReference.getReturnType());
    }

    public static String getFieldDescriptor(FieldReference fieldReference) {
        return getFieldDescriptor(fieldReference, false);
    }

    public static String getFieldDescriptor(FieldReference fieldReference, boolean useImplicitReference) {
        StringBuilder sb = new StringBuilder();
        if (!useImplicitReference) {
            String clazz = fieldReference.getDefiningClass();
            if (DexDiffInfo.getModifiedClasses(clazz) != null) {
                if (isStaticFiled(DexDiffInfo.getModifiedClasses(clazz), fieldReference)) {//静态变量要访问以前的
                } else {
                    //		clazz = TypeGenUtil.newType(clazz);
                }
            }
            sb.append(clazz);
            sb.append("->");
        }
        sb.append(fieldReference.getName());
        sb.append(':');

        String clazz = fieldReference.getType();//TypeGenUtil.newType(fieldReference.getType());
        sb.append(clazz);
        return sb.toString();
    }

    public static String getShortFieldDescriptor(FieldReference fieldReference) {
        StringBuilder sb = new StringBuilder();
        sb.append(fieldReference.getName());
        sb.append(':');
        String clazz = TypeGenUtil.newType(fieldReference.getType());
        sb.append(clazz);
        return sb.toString();
    }

    public static void writeFieldDescriptor(Writer writer, FieldReference fieldReference) throws IOException {
        writeFieldDescriptor(writer, fieldReference, false);
    }

    public static void writeFieldDescriptor(Writer writer, FieldReference fieldReference,
                                            boolean implicitReference) throws IOException {
        if (!implicitReference) {
            String clazz = fieldReference.getDefiningClass();
            if (DexDiffInfo.getModifiedClasses(clazz) != null) {
                if (isStaticFiled(DexDiffInfo.getModifiedClasses(clazz), fieldReference)) {//静态变量要访问以前的
                } else {
                    clazz = TypeGenUtil.newType(clazz);
                }
            }
            writer.write(clazz);
            writer.write("->");
        }
        writer.write(fieldReference.getName());
        writer.write(':');
        writer.write(fieldReference.getType());
    }

    @Nullable
    public static String getReferenceString(@Nonnull Reference reference) {
        return getReferenceString(reference, null);
    }

    @Nullable
    public static String getReferenceString(@Nonnull Reference reference, @Nullable String containingClass) {
        if (reference instanceof StringReference) {
            return String.format("\"%s\"", StringUtils.escapeString(((StringReference) reference).getString()));
        }
        if (reference instanceof TypeReference) {
            String clazz = ((TypeReference) reference).getType(); //TypeGenUtil.newType(((TypeReference)reference).getType());
            return clazz;
        }
        if (reference instanceof FieldReference) {
            FieldReference fieldReference = (FieldReference) reference;
            boolean useImplicitReference = fieldReference.getDefiningClass().equals(containingClass);
            return getFieldDescriptor((FieldReference) reference, useImplicitReference);
        }
        if (reference instanceof MethodReference) {
            MethodReference methodReference = (MethodReference) reference;
            boolean useImplicitReference = methodReference.getDefiningClass().equals(containingClass);
            return getMethodDescriptor((MethodReference) reference, useImplicitReference);
        }
        return null;
    }

    private static boolean isStaticFiled(DexBackedClassDef classDef, FieldReference reference) {
        for (DexBackedField field : classDef.getStaticFields()) {
            if (field.equals(reference)) {
                return true;
            }
        }
        return false;
    }

    private ReferenceUtil() {
    }
}
