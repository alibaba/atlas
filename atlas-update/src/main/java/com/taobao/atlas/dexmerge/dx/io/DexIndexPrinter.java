/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.taobao.atlas.dexmerge.dx.io;

import android.util.Log;
import com.taobao.atlas.dex.ClassDef;
import com.taobao.atlas.dex.Dex;
import com.taobao.atlas.dex.FieldId;
import com.taobao.atlas.dex.MethodId;
import com.taobao.atlas.dex.ProtoId;
import com.taobao.atlas.dex.TableOfContents;
import java.io.File;
import java.io.IOException;

/**
 * Executable that prints all indices of a dex file.
 */
public final class DexIndexPrinter {
    private static final String TAG = "DexIndexPrinter";
    private final Dex dex;
    private final TableOfContents tableOfContents;

    public DexIndexPrinter(File file) throws IOException {
        this.dex = new Dex(file);
        this.tableOfContents = dex.getTableOfContents();
    }

    private void printMap() {
        for (TableOfContents.Section section : tableOfContents.sections) {
            if (section.off != -1) {
                Log.d("DexIndexPrinter","section " + Integer.toHexString(section.type)
                        + " off=" + Integer.toHexString(section.off)
                        + " size=" + Integer.toHexString(section.size)
                        + " byteCount=" + Integer.toHexString(section.byteCount));
            }
        }
    }

    private void printStrings() throws IOException {
        int index = 0;
        for (String string : dex.strings()) {
            Log.d("DexIndexPrinter","string " + index + ": " + string);
            index++;
        }
    }

    private void printTypeIds() throws IOException {
        int index = 0;
        for (Integer type : dex.typeIds()) {
            Log.d("DexIndexPrinter","type " + index + ": " + dex.strings().get(type));
            index++;
        }
    }

    private void printProtoIds() throws IOException {
        int index = 0;
        for (ProtoId protoId : dex.protoIds()) {
            Log.d("DexIndexPrinter","proto " + index + ": " + protoId);
            index++;
        }
    }

    private void printFieldIds() throws IOException {
        int index = 0;
        for (FieldId fieldId : dex.fieldIds()) {
            Log.d("DexIndexPrinter","field " + index + ": " + fieldId);
            index++;
        }
    }

    private void printMethodIds() throws IOException {
        int index = 0;
        for (MethodId methodId : dex.methodIds()) {
            Log.d("DexIndexPrinter","methodId " + index + ": " + methodId);
            index++;
        }
    }

    private void printTypeLists() throws IOException {
        if (tableOfContents.typeLists.off == -1) {
            Log.d("DexIndexPrinter","No type lists");
            return;
        }
        Dex.Section in = dex.open(tableOfContents.typeLists.off);
        for (int i = 0; i < tableOfContents.typeLists.size; i++) {
            int size = in.readInt();
            Log.d("DexIndexPrinter","Type list i=" + i + ", size=" + size + ", elements=");
            for (int t = 0; t < size; t++) {
                Log.d("DexIndexPrinter"," " + dex.typeNames().get((int) in.readShort()));
            }
            if (size % 2 == 1) {
                in.readShort(); // retain alignment
            }
//            System.out.println();
        }
    }

    private void printClassDefs() {
        int index = 0;
        for (ClassDef classDef : dex.classDefs()) {
            Log.d("DexIndexPrinter","class def " + index + ": " + classDef);
            index++;
        }
    }

    public static void main(String[] args) throws IOException {
        DexIndexPrinter indexPrinter = new DexIndexPrinter(new File(args[0]));
        indexPrinter.printMap();
        indexPrinter.printStrings();
        indexPrinter.printTypeIds();
        indexPrinter.printProtoIds();
        indexPrinter.printFieldIds();
        indexPrinter.printMethodIds();
        indexPrinter.printTypeLists();
        indexPrinter.printClassDefs();
    }
}
