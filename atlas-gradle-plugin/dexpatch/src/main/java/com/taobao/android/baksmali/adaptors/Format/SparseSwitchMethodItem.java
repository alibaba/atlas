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

package com.taobao.android.baksmali.adaptors.Format;

import com.taobao.android.baksmali.adaptors.CommentingIndentingWriter;
import com.taobao.android.baksmali.adaptors.LabelMethodItem;
import com.taobao.android.baksmali.adaptors.MethodDefinition;
import com.taobao.android.baksmali.Renderers.IntegerRenderer;

import org.jf.dexlib2.iface.instruction.SwitchElement;
import org.jf.dexlib2.iface.instruction.formats.SparseSwitchPayload;
import org.jf.util.IndentingWriter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class SparseSwitchMethodItem extends InstructionMethodItem<SparseSwitchPayload> {
    private final List<SparseSwitchMethodItem.SparseSwitchTarget> targets;

    // Whether this sparse switch instruction should be commented out because it is never referenced
    private boolean commentedOut;

    public SparseSwitchMethodItem(MethodDefinition methodDef, int codeAddress, SparseSwitchPayload instruction) {
        super(methodDef, codeAddress, instruction);

        int baseCodeAddress = methodDef.getSparseSwitchBaseAddress(codeAddress);

        targets = new ArrayList<SparseSwitchMethodItem.SparseSwitchTarget>();
        if (baseCodeAddress >= 0) {
            for (SwitchElement switchElement: instruction.getSwitchElements()) {
                LabelMethodItem label = methodDef.getLabelCache().internLabel(
                        new LabelMethodItem( methodDef.classDef.options, baseCodeAddress + switchElement.getOffset(),
                                "sswitch_"));
                targets.add(new SparseSwitchMethodItem.SparseSwitchLabelTarget(switchElement.getKey(), label));
            }
        } else {
            commentedOut = true;
            //if we couldn't determine a base address, just use relative offsets rather than labels
            for (SwitchElement switchElement: instruction.getSwitchElements()) {
                targets.add(new SparseSwitchMethodItem.SparseSwitchOffsetTarget(switchElement.getKey(), switchElement.getOffset()));
            }
        }
    }

    @Override
    public boolean writeTo(IndentingWriter writer) throws IOException {
        if (commentedOut) {
            writer = new org.jf.baksmali.Adaptors.CommentingIndentingWriter(writer);
        }

        writer.write(".sparse-switch\n");
        writer.indent(4);
        for (SparseSwitchMethodItem.SparseSwitchTarget target: targets) {
            org.jf.baksmali.Renderers.IntegerRenderer.writeTo(writer, target.getKey());
            writer.write(" -> ");
            target.writeTargetTo(writer);
            writeCommentIfResourceId(writer, target.getKey());
            writer.write('\n');
        }
        writer.deindent(4);
        writer.write(".end sparse-switch");
        return true;
    }

    private static abstract class SparseSwitchTarget {
        private final int key;
        public SparseSwitchTarget(int key) {
            this.key = key;
        }
        public int getKey() { return key; }
        public abstract void writeTargetTo(IndentingWriter writer) throws IOException;
    }

    private static class SparseSwitchLabelTarget extends SparseSwitchMethodItem.SparseSwitchTarget {
        private final LabelMethodItem target;
        public SparseSwitchLabelTarget(int key, LabelMethodItem target) {
            super(key);
            this.target = target;
        }

        public void writeTargetTo(IndentingWriter writer) throws IOException {
            target.writeTo(writer);
        }
    }

    private static class SparseSwitchOffsetTarget extends SparseSwitchMethodItem.SparseSwitchTarget {
        private final int target;
        public SparseSwitchOffsetTarget(int key, int target) {
            super(key);
            this.target = target;
        }

        public void writeTargetTo(IndentingWriter writer) throws IOException {
            if (target >= 0) {
                writer.write('+');
            }
            writer.printSignedIntAsDec(target);
        }
    }
}
