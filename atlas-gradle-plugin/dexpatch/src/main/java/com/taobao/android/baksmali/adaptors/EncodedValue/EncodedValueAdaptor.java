/*
 * [The "BSD licence"]
 * Copyright (c) 2010 Ben Gruver (JesusFreke)
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. The name of the author may not be used to endorse or promote products
 *    derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 * THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.taobao.android.baksmali.adaptors.EncodedValue;

import com.taobao.android.baksmali.adaptors.ReferenceFormatter;
import com.taobao.android.baksmali.Renderers.BooleanRenderer;
import com.taobao.android.baksmali.Renderers.ByteRenderer;
import com.taobao.android.baksmali.Renderers.CharRenderer;
import com.taobao.android.baksmali.Renderers.DoubleRenderer;
import com.taobao.android.baksmali.Renderers.FloatRenderer;
import com.taobao.android.baksmali.Renderers.IntegerRenderer;
import com.taobao.android.baksmali.Renderers.LongRenderer;
import com.taobao.android.baksmali.Renderers.ShortRenderer;
import com.taobao.android.baksmali.util.ReferenceUtil;

import org.jf.dexlib2.ValueType;
import org.jf.dexlib2.iface.value.AnnotationEncodedValue;
import org.jf.dexlib2.iface.value.ArrayEncodedValue;
import org.jf.dexlib2.iface.value.BooleanEncodedValue;
import org.jf.dexlib2.iface.value.ByteEncodedValue;
import org.jf.dexlib2.iface.value.CharEncodedValue;
import org.jf.dexlib2.iface.value.DoubleEncodedValue;
import org.jf.dexlib2.iface.value.EncodedValue;
import org.jf.dexlib2.iface.value.EnumEncodedValue;
import org.jf.dexlib2.iface.value.FieldEncodedValue;
import org.jf.dexlib2.iface.value.FloatEncodedValue;
import org.jf.dexlib2.iface.value.IntEncodedValue;
import org.jf.dexlib2.iface.value.LongEncodedValue;
import org.jf.dexlib2.iface.value.MethodEncodedValue;
import org.jf.dexlib2.iface.value.ShortEncodedValue;
import org.jf.dexlib2.iface.value.StringEncodedValue;
import org.jf.dexlib2.iface.value.TypeEncodedValue;
import org.jf.util.IndentingWriter;

import java.io.IOException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public abstract class EncodedValueAdaptor {
    public static void writeTo(@Nonnull IndentingWriter writer, @Nonnull EncodedValue encodedValue,
                               @Nullable String containingClass)
            throws IOException {
        switch (encodedValue.getValueType()) {
            case ValueType.ANNOTATION:
                AnnotationEncodedValueAdaptor.writeTo(writer, (AnnotationEncodedValue) encodedValue, containingClass);
                return;
            case ValueType.ARRAY:
                ArrayEncodedValueAdaptor.writeTo(writer, (ArrayEncodedValue)encodedValue, containingClass);
                return;
            case ValueType.BOOLEAN:
                BooleanRenderer.writeTo(writer, ((BooleanEncodedValue) encodedValue).getValue());
                return;
            case ValueType.BYTE:
                ByteRenderer.writeTo(writer, ((ByteEncodedValue) encodedValue).getValue());
                return;
            case ValueType.CHAR:
                CharRenderer.writeTo(writer, ((CharEncodedValue) encodedValue).getValue());
                return;
            case ValueType.DOUBLE:
                DoubleRenderer.writeTo(writer, ((DoubleEncodedValue) encodedValue).getValue());
                return;
            case ValueType.ENUM:
                EnumEncodedValue enumEncodedValue = (EnumEncodedValue)encodedValue;
                boolean useImplicitReference = false;
                if (enumEncodedValue.getValue().getDefiningClass().equals(containingClass)) {
                    useImplicitReference = true;
                }
                writer.write(".enum ");
                ReferenceUtil.writeFieldDescriptor(writer, enumEncodedValue.getValue(), useImplicitReference);
                return;
            case ValueType.FIELD:
                FieldEncodedValue fieldEncodedValue = (FieldEncodedValue)encodedValue;
                useImplicitReference = false;
                if (fieldEncodedValue.getValue().getDefiningClass().equals(containingClass)) {
                    useImplicitReference = true;
                }
                ReferenceUtil.writeFieldDescriptor(writer, fieldEncodedValue.getValue(), useImplicitReference);
                return;
            case ValueType.FLOAT:
                FloatRenderer.writeTo(writer, ((FloatEncodedValue) encodedValue).getValue());
                return;
            case ValueType.INT:
                IntegerRenderer.writeTo(writer, ((IntEncodedValue) encodedValue).getValue());
                return;
            case ValueType.LONG:
                LongRenderer.writeTo(writer, ((LongEncodedValue) encodedValue).getValue());
                return;
            case ValueType.METHOD:
                MethodEncodedValue methodEncodedValue = (MethodEncodedValue)encodedValue;
                useImplicitReference = false;
                if (methodEncodedValue.getValue().getDefiningClass().equals(containingClass)) {
                    useImplicitReference = true;
                }
                ReferenceUtil.writeMethodDescriptor(writer, methodEncodedValue.getValue(), useImplicitReference);
                return;
            case ValueType.NULL:
                writer.write("null");
                return;
            case ValueType.SHORT:
                ShortRenderer.writeTo(writer, ((ShortEncodedValue) encodedValue).getValue());
                return;
            case ValueType.STRING:
                ReferenceFormatter.writeStringReference(writer, ((StringEncodedValue) encodedValue).getValue());
                return;
            case ValueType.TYPE:
                writer.write(((TypeEncodedValue)encodedValue).getValue());
        }
    }
}
