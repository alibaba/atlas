package com.taobao.atlas.dexmerge.dx.merge;

/**
 * Created by lilong on 16/10/13.
 */
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

import com.taobao.atlas.dex.*;

import java.io.IOException;
import java.util.*;

/**
 * Combine two dex files into one.
 */
public final class DexTransform {

    private final Dex dex;
    private final IndexMap               indexMap;
    private final WriterSizes            writerSizes;
    private final Dex                    dexOut;

    private final Dex.Section            headerOut;

    /** All IDs and definitions sections */
    private final Dex.Section            idsDefsOut;

    private final Dex.Section            mapListOut;

    private final Dex.Section            typeListOut;

    private final Dex.Section            classDataOut;

    private final Dex.Section            codeOut;

    private final Dex.Section            stringDataOut;

    private final Dex.Section            debugInfoOut;

    private final Dex.Section            encodedArrayOut;

    /** annotations directory on a type */
    private final Dex.Section            annotationsDirectoryOut;

    /** sets of annotations on a member, parameter or type */
    private final Dex.Section            annotationSetOut;

    /** parameter lists */
    private final Dex.Section            annotationSetRefListOut;

    /** individual annotations, each containing zero or more fields */
    private final Dex.Section            annotationOut;

    private final TableOfContents contentsOut;

    private final InstructionTransformer instructionTransformer;
    private TreeMap<String, DexItem> stringValueOffMaps      = new TreeMap<String, DexItem>(); // 字符串的maps,key为value,value为index
    private Map<String, DexItem> methodCodeOffMaps       = new HashMap<String, DexItem>(); // 方法名和字节码的off的对应关系
    private List<Integer> debugInfoRemoveMethodss = new ArrayList<Integer>();
    private Map<Integer,Integer>     removeClassDefs = new HashMap<Integer, Integer>();
    private final boolean onlyShrink;

    public DexTransform(Dex dex) throws IOException {
        this(dex, new WriterSizes(dex, true),false);
    }

    private DexTransform(Dex dex, WriterSizes writerSizes,boolean onlyShrink) throws IOException{
        this.dex = dex;
        this.writerSizes = writerSizes;
        this.onlyShrink = onlyShrink;

        dexOut = new Dex(writerSizes.size());

        indexMap = new IndexMap(dexOut, dex.getTableOfContents());

        instructionTransformer = new InstructionTransformer();

        headerOut = dexOut.appendSection(writerSizes.header, "header");
        idsDefsOut = dexOut.appendSection(writerSizes.idsDefs, "ids defs");

        contentsOut = dexOut.getTableOfContents();
        contentsOut.dataOff = dexOut.getNextSectionStart();

        contentsOut.mapList.off = dexOut.getNextSectionStart();
        contentsOut.mapList.size = 1;
        mapListOut = dexOut.appendSection(writerSizes.mapList, "map list");

        contentsOut.typeLists.off = dexOut.getNextSectionStart();
        typeListOut = dexOut.appendSection(writerSizes.typeList, "type list");

        contentsOut.annotationSetRefLists.off = dexOut.getNextSectionStart();
        annotationSetRefListOut = dexOut.appendSection(writerSizes.annotationsSetRefList, "annotation set ref list");

        contentsOut.annotationSets.off = dexOut.getNextSectionStart();
        annotationSetOut = dexOut.appendSection(writerSizes.annotationsSet, "annotation sets");

        contentsOut.classDatas.off = dexOut.getNextSectionStart();
        classDataOut = dexOut.appendSection(writerSizes.classData, "class data");

        contentsOut.codes.off = dexOut.getNextSectionStart();
        codeOut = dexOut.appendSection(writerSizes.code, "code");

        contentsOut.stringDatas.off = dexOut.getNextSectionStart();
        stringDataOut = dexOut.appendSection(writerSizes.stringData, "string data");

        contentsOut.debugInfos.off = dexOut.getNextSectionStart();
        debugInfoOut = dexOut.appendSection(writerSizes.debugInfo, "debug info");

        contentsOut.annotations.off = dexOut.getNextSectionStart();
        annotationOut = dexOut.appendSection(writerSizes.annotation, "annotation");

        contentsOut.encodedArrays.off = dexOut.getNextSectionStart();
        encodedArrayOut = dexOut.appendSection(writerSizes.encodedArray, "encoded array");

        contentsOut.annotationsDirectories.off = dexOut.getNextSectionStart();
        annotationsDirectoryOut = dexOut.appendSection(writerSizes.annotationsDirectory, "annotations directory");

        contentsOut.dataSize = dexOut.getNextSectionStart() - contentsOut.dataOff;
        if(!onlyShrink) {
            initIndexMaps();
        }
    }

    /**
     * 初始化所有的Maps
     */
    private void initIndexMaps() {
        intStringIndexMaps(dex);
        initCodeMaps(dex);
    }

    private void intStringIndexMaps(Dex dex) {
        TableOfContents.Section stringIds = dex.getTableOfContents().stringIds;
        Dex.Section stringDataSection = dex.open(stringIds.off);
        for (int i = 0; i < stringIds.size; i++) {
            int position = stringDataSection.getPosition();
            int off = stringDataSection.readInt();
            String value = null;
            if (off < 0) {// 如果为负数,则表示取下一个dex的值
                value =  String.valueOf(off);
                stringValueOffMaps.put(value, new DexItem(off, i, off));
            } else {
                stringDataSection.getData().position(position);
                value = stringDataSection.readString();
                stringValueOffMaps.put(value, new DexItem(off, i, off));
            }
        }
    }

    private void initCodeMaps(Dex dex) {
        Iterable<ClassDef> classDefs = dex.classDefs();
        for (ClassDef classDef : classDefs) {
            if(classDef.getClassDataOffset() > 0) {
                ClassData classData = dex.readClassData(classDef);
                ClassData.Method[] directMethods = classData.getDirectMethods();
                ClassData.Method[] virtualMethods = classData.getVirtualMethods();
                initMethodsMaps(dex, directMethods);
                initMethodsMaps(dex, virtualMethods);
            }

        }
    }

    private void initMethodsMaps(Dex dex, ClassData.Method[] methods) {
        for (ClassData.Method method : methods) {
            MethodId methodId = dex.methodIds().get(method.getMethodIndex());
            String className = dex.typeNames().get(methodId.getDeclaringClassIndex());
            String methodName = dex.strings().get(methodId.getNameIndex())
                    + dex.readTypeList(dex.protoIds().get(methodId.getProtoIndex()).getParametersOffset());
            methodCodeOffMaps.put(className + "." + methodName,
                    new DexItem(method.getCodeOffset(), method.getMethodIndex(), method.getCodeOffset()));
        }
    }

    private Set<String> getMethodMaps(Dex dex){
        Set<String> methods = new HashSet<String>();
        Iterable<ClassDef> classDefs = dex.classDefs();
        for (ClassDef classDef : classDefs) {
            if(classDef.getClassDataOffset() > 0) {
                ClassData classData = dex.readClassData(classDef);
                ClassData.Method[] directMethods = classData.getDirectMethods();
                ClassData.Method[] virtualMethods = classData.getVirtualMethods();
                initMethodsMaps(dex, directMethods, methods);
                initMethodsMaps(dex, virtualMethods, methods);
            }

        }
        return methods;
    }

    private void initMethodsMaps(Dex dex, ClassData.Method[] methods, Set<String> methodSets) {
        for (ClassData.Method method : methods) {
            MethodId methodId = dex.methodIds().get(method.getMethodIndex());
            String className = dex.typeNames().get(methodId.getDeclaringClassIndex());
            String methodName = dex.strings().get(methodId.getNameIndex())
                    + dex.readTypeList(dex.protoIds().get(methodId.getProtoIndex()).getParametersOffset());
            methodSets.add(className + "." + methodName);
        }
    }

    /**
     * 根据基线的dex,去除重复的string值
     *
     * @param baseDex
     * @return
     */
    public DexTransform uninoStringValues(Dex baseDex) {
        TableOfContents.Section stringIds = baseDex.getTableOfContents().stringIds;
        Dex.Section stringSection = baseDex.open(stringIds.off);
        for (int i = 0; i < stringIds.size; i++) {
            String value = stringSection.readString();
            if (stringValueOffMaps.containsKey(value)) {
                DexItem dexItem = stringValueOffMaps.get(value);
                dexItem.newOffset = (i + 1) * -1;
                stringValueOffMaps.put(value, dexItem);
            }
        }
        return this;
    }

    public DexTransform markClassDef(Dex baseDex,List<String>classNames) {
        for (ClassDef classDef:dex.classDefs()) {
            int typeId = classDef.getTypeIndex();
            String value = dex.typeNames().get(typeId);
            if (classNames.contains(value)) {
                for (ClassDef baseClassDef : baseDex.classDefs()) {
                    if (baseDex.typeNames().get(baseClassDef.getTypeIndex()).equals(value)) {
                        removeClassDefs.put(typeId, baseClassDef.getClassDataOffset() * -1);
                    }
                }
            }
        }
        return this;
    }

    /**
     * 移除没有变动的方法的code和debug info信息
     *
     * @param modifyMethods
     * @return
     */
    public DexTransform removeMethodCodes(HashMap<String, ArrayList<String>> modifyMethods) {
        assert null != modifyMethods;
        List<String> fullModifyMethods = new ArrayList<String>();
        for (Map.Entry<String, ArrayList<String>> entry : modifyMethods.entrySet()) {
            String className = entry.getKey();
            for (String modifyMethodName : entry.getValue()) {
                String fullMehtodName = className + "." + modifyMethodName;
                fullModifyMethods.add(fullMehtodName);
            }
        }
        for (Map.Entry<String, DexItem> entry : methodCodeOffMaps.entrySet()) {
            String name = entry.getKey();
            if (!fullModifyMethods.contains(name)) {
                DexItem dexItem = entry.getValue();
                dexItem.newOffset = 1;
                methodCodeOffMaps.put(name, dexItem);
            }
        }

        return this;
    }

    public DexTransform removeMethodCodes(HashMap<String, ArrayList<String>> modifyMethods,Dex baseDex) {
        assert null != modifyMethods;
        List<String> fullModifyMethods = new ArrayList<String>();
        for (Map.Entry<String, ArrayList<String>> entry : modifyMethods.entrySet()) {
            String className = entry.getKey();
            for (String modifyMethodName : entry.getValue()) {
                String fullMehtodName = className + "." + modifyMethodName;
                fullModifyMethods.add(fullMehtodName);
            }
        }
        Set<String>  baseMethods =  getMethodMaps(baseDex);
        for (Map.Entry<String, DexItem> entry : methodCodeOffMaps.entrySet()) {
            String name = entry.getKey();
            if (!fullModifyMethods.contains(name) &&  baseMethods.contains(name)) {
                DexItem dexItem = entry.getValue();
                dexItem.newOffset = 1;
                methodCodeOffMaps.put(name, dexItem);
            }
        }

        return this;
    }

    /**
     * 要单独移除debugInfo的类
     *
     * @param removeClasses
     * @return
     */
    public DexTransform removeDebugInfos(List<String> removeClasses) {
        assert null != removeClasses;
        for (Map.Entry<String, DexItem> entry : methodCodeOffMaps.entrySet()) {
            String name = entry.getKey();
            String className = name.substring(0, name.indexOf("."));
            if (removeClasses.contains(className)) {
                debugInfoRemoveMethodss.add(entry.getValue().index);
            }
        }
        return this;
    }

    protected Dex transfromDex() throws IOException {
        transformStringIds();
        transformTypeIds();
        transformTypeLists();
        transformProtoIds();
        transformFieldIds();
        transformMethodIds();
        transformAnnotations();

        transformAnnotationSets(dex, indexMap);
        transformAnnotationSetRefLists(dex, indexMap);
        transformAnnotationDirectories(dex, indexMap);
        transformStaticValues(dex, indexMap);

        // 处理ClassDef的值
        transformClassDefs();

        // write the header
        contentsOut.header.off = 0;
        contentsOut.header.size = 1;
        contentsOut.fileSize = dexOut.getLength();
        contentsOut.computeSizesFromOffsets();
        contentsOut.writeHeader(headerOut, dex.getTableOfContents().apiLevel);
        contentsOut.writeMap(mapListOut);

        // generate and write the hashes
        dexOut.writeHashes();

        return dexOut;
    }

    /**
     * 处理StringIds的操作
     */
    private void transformStringIds() {
        if(onlyShrink){
            contentsOut.stringIds.off = idsDefsOut.getPosition();
            TableOfContents.Section stringIds = dex.getTableOfContents().stringIds;
            Dex.Section stringDataSection = dex.open(stringIds.off);
            for (int i = 0; i < stringIds.size; i++) {
                int position = stringDataSection.getPosition();
                int off = stringDataSection.readInt();
                if (off < 0) {// 如果为负数,则表示取下一个dex的值
                    idsDefsOut.writeInt(off);
                } else {
                    stringDataSection.getData().position(position);
                    contentsOut.stringDatas.size++;
                    idsDefsOut.writeInt(stringDataOut.getPosition());
                    stringDataOut.writeStringData(stringDataSection.readString());
                }
                indexMap.stringIds[i] = (short) i;
            }
            contentsOut.stringIds.size = stringIds.size;

        }else {
            contentsOut.stringIds.off = idsDefsOut.getPosition();
            int index = 0;
            for (Map.Entry<String, DexItem> entry : stringValueOffMaps.entrySet()) {
                String value = entry.getKey();
                DexItem item = entry.getValue();
                if (item.newOffset > 0) {
                    contentsOut.stringDatas.size++;
                    idsDefsOut.writeInt(stringDataOut.getPosition());
                    stringDataOut.writeStringData(value);
                } else {
                    idsDefsOut.writeInt(item.newOffset);
                }
                indexMap.stringIds[index] = (short) index;
                index++;

            }
            contentsOut.stringIds.size = stringValueOffMaps.size();
        }
    }

    public Dex process() throws IOException {
        Dex result = transfromDex();
        DexTransform dexShrink = new DexTransform(result, new WriterSizes(this),true);
        result = dexShrink.transfromDex();
        return result;
    }

    /**
     * Reads an IDs section of two dex files and writes an IDs section of a
     * merged dex file. Populates maps from old to new indices in the merge.
     */
    abstract class IdTransform<T extends Comparable<T>> {

        private final Dex.Section out;

        protected IdTransform(Dex.Section out){
            this.out = out;
        }

        public void execute() {
            TableOfContents.Section dexSection = getSection(dex.getTableOfContents());
            int size = dexSection.size;
            if (dexSection.exists()) {
                Dex.Section inSection = dex.open(dexSection.off);
                getSection(contentsOut).off = out.getPosition();
                for (int i = 0; i < size; i++) {
                    updateIndex(inSection.getPosition(), indexMap, i, i);
                    T value = read(inSection, indexMap, i);
                    write(value);
                }
            } else {
                getSection(contentsOut).off = 0;
            }
            getSection(contentsOut).size = dexSection.size;
        }

        abstract TableOfContents.Section getSection(TableOfContents tableOfContents);

        abstract T read(Dex.Section in, IndexMap indexMap, int index);

        abstract void updateIndex(int offset, IndexMap indexMap, int oldIndex, int newIndex);

        abstract void write(T value);

    }

    private void transformTypeIds() {
        new IdTransform<Integer>(idsDefsOut) {

            @Override
            TableOfContents.Section getSection(TableOfContents tableOfContents) {
                return tableOfContents.typeIds;
            }

            @Override
            Integer read(Dex.Section in, IndexMap indexMap, int index) {
                int stringIndex = in.readInt();
                return indexMap.adjustString(stringIndex);
            }

            @Override
            void updateIndex(int offset, IndexMap indexMap, int oldIndex, int newIndex) {
                indexMap.typeIds[oldIndex] = (short) newIndex;
            }

            @Override
            void write(Integer value) {
                idsDefsOut.writeInt(value);
            }
        }.execute();
    }

    private void transformTypeLists() {
        new IdTransform<TypeList>(typeListOut) {

            @Override
            TableOfContents.Section getSection(TableOfContents tableOfContents) {
                return tableOfContents.typeLists;
            }

            @Override
            TypeList read(Dex.Section in, IndexMap indexMap, int index) {
                return indexMap.adjustTypeList(in.readTypeList());
            }

            @Override
            void updateIndex(int offset, IndexMap indexMap, int oldIndex, int newIndex) {
                indexMap.putTypeListOffset(offset, typeListOut.getPosition());
            }

            @Override
            void write(TypeList value) {
                typeListOut.writeTypeList(value);
            }
        }.execute();
    }

    private void transformProtoIds() {
        new IdTransform<ProtoId>(idsDefsOut) {

            @Override
            TableOfContents.Section getSection(TableOfContents tableOfContents) {
                return tableOfContents.protoIds;
            }

            @Override
            ProtoId read(Dex.Section in, IndexMap indexMap, int index) {
                return indexMap.adjust(in.readProtoId());
            }

            @Override
            void updateIndex(int offset, IndexMap indexMap, int oldIndex, int newIndex) {
                indexMap.protoIds[oldIndex] = (short) newIndex;
            }

            @Override
            void write(ProtoId value) {
                value.writeTo(idsDefsOut);
            }
        }.execute();
    }

    private void transformFieldIds() {
        new IdTransform<FieldId>(idsDefsOut) {

            @Override
            TableOfContents.Section getSection(TableOfContents tableOfContents) {
                return tableOfContents.fieldIds;
            }

            @Override
            FieldId read(Dex.Section in, IndexMap indexMap, int index) {
                return indexMap.adjust(in.readFieldId());
            }

            @Override
            void updateIndex(int offset, IndexMap indexMap, int oldIndex, int newIndex) {
                indexMap.fieldIds[oldIndex] = (short) newIndex;
            }

            @Override
            void write(FieldId value) {
                value.writeTo(idsDefsOut);
            }
        }.execute();
    }

    private void transformMethodIds() {
        new IdTransform<MethodId>(idsDefsOut) {

            @Override
            TableOfContents.Section getSection(TableOfContents tableOfContents) {
                return tableOfContents.methodIds;
            }

            @Override
            MethodId read(Dex.Section in, IndexMap indexMap, int index) {
                return indexMap.adjust(in.readMethodId());
            }

            @Override
            void updateIndex(int offset, IndexMap indexMap, int oldIndex, int newIndex) {
                indexMap.methodIds[oldIndex] = (short) newIndex;
            }

            @Override
            void write(MethodId methodId) {
                methodId.writeTo(idsDefsOut);
            }
        }.execute();
    }

    private void transformAnnotations() {
        new IdTransform<Annotation>(annotationOut) {

            @Override
            TableOfContents.Section getSection(TableOfContents tableOfContents) {
                return tableOfContents.annotations;
            }

            @Override
            Annotation read(Dex.Section in, IndexMap indexMap, int index) {
                return indexMap.adjust(in.readAnnotation());
            }

            @Override
            void updateIndex(int offset, IndexMap indexMap, int oldIndex, int newIndex) {
                indexMap.putAnnotationOffset(offset, annotationOut.getPosition());
            }

            @Override
            void write(Annotation value) {
                value.writeTo(annotationOut);
            }
        }.execute();
    }

    private void transformClassDefs() {
        TableOfContents.Section dexSection = dex.getTableOfContents().classDefs;
        if (dexSection.exists()) {
            contentsOut.classDefs.off = idsDefsOut.getPosition();
            contentsOut.classDefs.size = dexSection.size;
            // 书写classData
            for (ClassDef oldClassDef : dex.classDefs()) {
                ClassDef classDef = indexMap.adjust(oldClassDef);
                transformClassDef(dex, classDef, indexMap);
            }
        }
    }

    private void transformAnnotationSets(Dex in, IndexMap indexMap) {
        TableOfContents.Section section = in.getTableOfContents().annotationSets;
        if (section.exists()) {
            Dex.Section setIn = in.open(section.off);
            for (int i = 0; i < section.size; i++) {
                transformAnnotationSet(indexMap, setIn);
            }
        }
    }

    private void transformAnnotationSetRefLists(Dex in, IndexMap indexMap) {
        TableOfContents.Section section = in.getTableOfContents().annotationSetRefLists;
        if (section.exists()) {
            Dex.Section setIn = in.open(section.off);
            for (int i = 0; i < section.size; i++) {
                transformAnnotationSetRefList(indexMap, setIn);
            }
        }
    }

    private void transformAnnotationDirectories(Dex in, IndexMap indexMap) {
        TableOfContents.Section section = in.getTableOfContents().annotationsDirectories;
        if (section.exists()) {
            Dex.Section directoryIn = in.open(section.off);
            for (int i = 0; i < section.size; i++) {
                transformAnnotationDirectory(directoryIn, indexMap);
            }
        }
    }

    private void transformStaticValues(Dex in, IndexMap indexMap) {
        TableOfContents.Section section = in.getTableOfContents().encodedArrays;
        if (section.exists()) {
            Dex.Section staticValuesIn = in.open(section.off);
            for (int i = 0; i < section.size; i++) {
                transformStaticValues(staticValuesIn, indexMap);
            }
        }
    }

    /**
     * Reads a class_def_item beginning at {@code in} and writes the index and
     * data.
     */
    private void transformClassDef(Dex in, ClassDef classDef, IndexMap indexMap) {
        idsDefsOut.assertFourByteAligned();
        idsDefsOut.writeInt(classDef.getTypeIndex());
        idsDefsOut.writeInt(classDef.getAccessFlags());
        idsDefsOut.writeInt(classDef.getSupertypeIndex());
        idsDefsOut.writeInt(classDef.getInterfacesOffset());

        int sourceFileIndex = indexMap.adjustString(classDef.getSourceFileIndex());
        idsDefsOut.writeInt(sourceFileIndex);

        int annotationsOff = classDef.getAnnotationsOffset();
        idsDefsOut.writeInt(indexMap.adjustAnnotationDirectory(annotationsOff));

        int classDataOff = classDef.getClassDataOffset();
        if (removeClassDefs.containsKey(classDef.getTypeIndex())&&!onlyShrink) {
            idsDefsOut.writeInt(removeClassDefs.get(classDef.getTypeIndex()));
        }else if (onlyShrink && classDataOff < 0){
            idsDefsOut.writeInt(classDataOff);

        }else if (classDataOff == 0) {
            idsDefsOut.writeInt(0);
        } else {
            idsDefsOut.writeInt(classDataOut.getPosition());
            ClassData classData = in.readClassData(classDef);
            transformClassData(in, classData, indexMap);
        }

        int staticValuesOff = classDef.getStaticValuesOffset();
        idsDefsOut.writeInt(indexMap.adjustStaticValues(staticValuesOff));
    }

    /**
     * Transform all annotations on a class.
     */
    private void transformAnnotationDirectory(Dex.Section directoryIn, IndexMap indexMap) {
        contentsOut.annotationsDirectories.size++;
        annotationsDirectoryOut.assertFourByteAligned();
        indexMap.putAnnotationDirectoryOffset(directoryIn.getPosition(), annotationsDirectoryOut.getPosition());

        int classAnnotationsOffset = indexMap.adjustAnnotationSet(directoryIn.readInt());
        annotationsDirectoryOut.writeInt(classAnnotationsOffset);

        int fieldsSize = directoryIn.readInt();
        annotationsDirectoryOut.writeInt(fieldsSize);

        int methodsSize = directoryIn.readInt();
        annotationsDirectoryOut.writeInt(methodsSize);

        int parameterListSize = directoryIn.readInt();
        annotationsDirectoryOut.writeInt(parameterListSize);

        for (int i = 0; i < fieldsSize; i++) {
            // field index
            annotationsDirectoryOut.writeInt(indexMap.adjustField(directoryIn.readInt()));

            // annotations offset
            annotationsDirectoryOut.writeInt(indexMap.adjustAnnotationSet(directoryIn.readInt()));
        }

        for (int i = 0; i < methodsSize; i++) {
            // method index
            annotationsDirectoryOut.writeInt(indexMap.adjustMethod(directoryIn.readInt()));

            // annotation set offset
            annotationsDirectoryOut.writeInt(indexMap.adjustAnnotationSet(directoryIn.readInt()));
        }

        for (int i = 0; i < parameterListSize; i++) {
            // method index
            annotationsDirectoryOut.writeInt(indexMap.adjustMethod(directoryIn.readInt()));

            // annotations offset
            annotationsDirectoryOut.writeInt(indexMap.adjustAnnotationSetRefList(directoryIn.readInt()));
        }
    }

    /**
     * Transform all annotations on a single type, member or parameter.
     */
    private void transformAnnotationSet(IndexMap indexMap, Dex.Section setIn) {
        contentsOut.annotationSets.size++;
        annotationSetOut.assertFourByteAligned();
        indexMap.putAnnotationSetOffset(setIn.getPosition(), annotationSetOut.getPosition());

        int size = setIn.readInt();
        annotationSetOut.writeInt(size);

        for (int j = 0; j < size; j++) {
            annotationSetOut.writeInt(indexMap.adjustAnnotation(setIn.readInt()));
        }
    }

    /**
     * Transform all annotation set ref lists.
     */
    private void transformAnnotationSetRefList(IndexMap indexMap, Dex.Section refListIn) {
        contentsOut.annotationSetRefLists.size++;
        annotationSetRefListOut.assertFourByteAligned();
        indexMap.putAnnotationSetRefListOffset(refListIn.getPosition(), annotationSetRefListOut.getPosition());

        int parameterCount = refListIn.readInt();
        annotationSetRefListOut.writeInt(parameterCount);
        for (int p = 0; p < parameterCount; p++) {
            annotationSetRefListOut.writeInt(indexMap.adjustAnnotationSet(refListIn.readInt()));
        }
    }

    private void transformClassData(Dex in, ClassData classData, IndexMap indexMap) {
        contentsOut.classDatas.size++;

        ClassData.Field[] staticFields = classData.getStaticFields();
        ClassData.Field[] instanceFields = classData.getInstanceFields();
        ClassData.Method[] directMethods = classData.getDirectMethods();
        ClassData.Method[] virtualMethods = classData.getVirtualMethods();

        classDataOut.writeUleb128(staticFields.length);
        classDataOut.writeUleb128(instanceFields.length);
        classDataOut.writeUleb128(directMethods.length);
        classDataOut.writeUleb128(virtualMethods.length);

        transformFields(indexMap, staticFields);
        transformFields(indexMap, instanceFields);
        transformMethods(in, indexMap, directMethods);
        transformMethods(in, indexMap, virtualMethods);
    }

    private void transformFields(IndexMap indexMap, ClassData.Field[] fields) {
        int lastOutFieldIndex = 0;
        for (ClassData.Field field : fields) {
            int outFieldIndex = indexMap.adjustField(field.getFieldIndex());
            classDataOut.writeUleb128(outFieldIndex - lastOutFieldIndex);
            lastOutFieldIndex = outFieldIndex;
            classDataOut.writeUleb128(field.getAccessFlags());
        }
    }

    private void transformMethods(Dex in, IndexMap indexMap, ClassData.Method[] methods) {
        int lastOutMethodIndex = 0;
        for (ClassData.Method method : methods) {
            int outMethodIndex = indexMap.adjustMethod(method.getMethodIndex());
            classDataOut.writeUleb128(outMethodIndex - lastOutMethodIndex);
            lastOutMethodIndex = outMethodIndex;

            classDataOut.writeUleb128(method.getAccessFlags());

            if(onlyShrink) {
                if (method.getCodeOffset() <= 1) {
                    classDataOut.writeUleb128(method.getCodeOffset());
                } else {
                    codeOut.alignToFourBytesWithZeroFill();
                    classDataOut.writeUleb128(codeOut.getPosition());
                    transformCode(in, in.readCode(method), indexMap,method.getMethodIndex());
                }
            }else{
                MethodId methodId = dex.methodIds().get(method.getMethodIndex());
                String className = dex.typeNames().get(methodId.getDeclaringClassIndex());
                String methodName = dex.strings().get(methodId.getNameIndex())
                        + dex.readTypeList(dex.protoIds().get(methodId.getProtoIndex()).getParametersOffset());
                String fullMethodName = className + "." + methodName;
                DexItem dexItem = methodCodeOffMaps.get(fullMethodName);

                if (dexItem.newOffset <= 1) {
                    classDataOut.writeUleb128(dexItem.newOffset);
                } else {
                    codeOut.alignToFourBytesWithZeroFill();
                    classDataOut.writeUleb128(codeOut.getPosition());
                    transformCode(in, in.readCode(method), indexMap, method.getMethodIndex());
                }
            }
        }
    }

    private void transformCode(Dex in, Code code, IndexMap indexMap, int methodIndex) {
        contentsOut.codes.size++;
        codeOut.assertFourByteAligned();

        codeOut.writeUnsignedShort(code.getRegistersSize());
        codeOut.writeUnsignedShort(code.getInsSize());
        codeOut.writeUnsignedShort(code.getOutsSize());

        Code.Try[] tries = code.getTries();
        Code.CatchHandler[] catchHandlers = code.getCatchHandlers();
        codeOut.writeUnsignedShort(tries.length);

        int debugInfoOffset = code.getDebugInfoOffset();

        // 判断是否移除debugInfo
        if (debugInfoRemoveMethodss.contains(methodIndex) || debugInfoOffset <= 0) {
            codeOut.writeInt(0);
        } else {
            codeOut.writeInt(debugInfoOut.getPosition());
            transformDebugInfoItem(in.open(debugInfoOffset), indexMap);
        }

        short[] instructions = code.getInstructions();
        short[] newInstructions = instructionTransformer.transform(indexMap, instructions);
        codeOut.writeInt(newInstructions.length);
        codeOut.write(newInstructions);

        if (tries.length > 0) {
            if (newInstructions.length % 2 == 1) {
                codeOut.writeShort((short) 0); // padding
            }

            /*
             * We can't write the tries until we've written the catch handlers.
             * Unfortunately they're in the opposite order in the dex file so we
             * need to transform them out-of-order.
             */
            Dex.Section triesSection = dexOut.open(codeOut.getPosition());
            codeOut.skip(tries.length * SizeOf.TRY_ITEM);
            int[] offsets = transformCatchHandlers(indexMap, catchHandlers);
            transformTries(triesSection, tries, offsets);
        }
    }

    /**
     * Writes the catch handlers to {@code codeOut} and returns their indices.
     */
    private int[] transformCatchHandlers(IndexMap indexMap, Code.CatchHandler[] catchHandlers) {
        int baseOffset = codeOut.getPosition();
        codeOut.writeUleb128(catchHandlers.length);
        int[] offsets = new int[catchHandlers.length];
        for (int i = 0; i < catchHandlers.length; i++) {
            offsets[i] = codeOut.getPosition() - baseOffset;
            transformEncodedCatchHandler(catchHandlers[i], indexMap);
        }
        return offsets;
    }

    private void transformTries(Dex.Section out, Code.Try[] tries, int[] catchHandlerOffsets) {
        for (Code.Try tryItem : tries) {
            out.writeInt(tryItem.getStartAddress());
            out.writeUnsignedShort(tryItem.getInstructionCount());
            out.writeUnsignedShort(catchHandlerOffsets[tryItem.getCatchHandlerIndex()]);
        }
    }

    private static final byte DBG_END_SEQUENCE         = 0x00;
    private static final byte DBG_ADVANCE_PC           = 0x01;
    private static final byte DBG_ADVANCE_LINE         = 0x02;
    private static final byte DBG_START_LOCAL          = 0x03;
    private static final byte DBG_START_LOCAL_EXTENDED = 0x04;
    private static final byte DBG_END_LOCAL            = 0x05;
    private static final byte DBG_RESTART_LOCAL        = 0x06;
    private static final byte DBG_SET_PROLOGUE_END     = 0x07;
    private static final byte DBG_SET_EPILOGUE_BEGIN   = 0x08;
    private static final byte DBG_SET_FILE             = 0x09;

    private void transformDebugInfoItem(Dex.Section in, IndexMap indexMap) {
        contentsOut.debugInfos.size++;
        int lineStart = in.readUleb128();
        debugInfoOut.writeUleb128(lineStart);

        int parametersSize = in.readUleb128();
        debugInfoOut.writeUleb128(parametersSize);

        for (int p = 0; p < parametersSize; p++) {
            int parameterName = in.readUleb128p1();
            debugInfoOut.writeUleb128p1(indexMap.adjustString(parameterName));
        }

        int addrDiff; // uleb128 address delta.
        int lineDiff; // sleb128 line delta.
        int registerNum; // uleb128 register number.
        int nameIndex; // uleb128p1 string index. Needs indexMap adjustment.
        int typeIndex; // uleb128p1 type index. Needs indexMap adjustment.
        int sigIndex; // uleb128p1 string index. Needs indexMap adjustment.

        while (true) {
            int opcode = in.readByte();
            debugInfoOut.writeByte(opcode);

            switch (opcode) {
                case DBG_END_SEQUENCE:
                    return;

                case DBG_ADVANCE_PC:
                    addrDiff = in.readUleb128();
                    debugInfoOut.writeUleb128(addrDiff);
                    break;

                case DBG_ADVANCE_LINE:
                    lineDiff = in.readSleb128();
                    debugInfoOut.writeSleb128(lineDiff);
                    break;

                case DBG_START_LOCAL:
                case DBG_START_LOCAL_EXTENDED:
                    registerNum = in.readUleb128();
                    debugInfoOut.writeUleb128(registerNum);
                    nameIndex = in.readUleb128p1();
                    debugInfoOut.writeUleb128p1(indexMap.adjustString(nameIndex));
                    typeIndex = in.readUleb128p1();
                    debugInfoOut.writeUleb128p1(indexMap.adjustType(typeIndex));
                    if (opcode == DBG_START_LOCAL_EXTENDED) {
                        sigIndex = in.readUleb128p1();
                        debugInfoOut.writeUleb128p1(indexMap.adjustString(sigIndex));
                    }
                    break;

                case DBG_END_LOCAL:
                case DBG_RESTART_LOCAL:
                    registerNum = in.readUleb128();
                    debugInfoOut.writeUleb128(registerNum);
                    break;

                case DBG_SET_FILE:
                    nameIndex = in.readUleb128p1();
                    debugInfoOut.writeUleb128p1(indexMap.adjustString(nameIndex));
                    break;

                case DBG_SET_PROLOGUE_END:
                case DBG_SET_EPILOGUE_BEGIN:
                default:
                    break;
            }
        }
    }

    private void transformEncodedCatchHandler(Code.CatchHandler catchHandler, IndexMap indexMap) {
        int catchAllAddress = catchHandler.getCatchAllAddress();
        int[] typeIndexes = catchHandler.getTypeIndexes();
        int[] addresses = catchHandler.getAddresses();

        if (catchAllAddress != -1) {
            codeOut.writeSleb128(-typeIndexes.length);
        } else {
            codeOut.writeSleb128(typeIndexes.length);
        }

        for (int i = 0; i < typeIndexes.length; i++) {
            codeOut.writeUleb128(indexMap.adjustType(typeIndexes[i]));
            codeOut.writeUleb128(addresses[i]);
        }

        if (catchAllAddress != -1) {
            codeOut.writeUleb128(catchAllAddress);
        }
    }

    private void transformStaticValues(Dex.Section in, IndexMap indexMap) {
        contentsOut.encodedArrays.size++;
        indexMap.putStaticValuesOffset(in.getPosition(), encodedArrayOut.getPosition());
        indexMap.adjustEncodedArray(in.readEncodedArray()).writeTo(encodedArrayOut);
    }

    /**
     * Byte counts for the sections written when creating a dex. Target sizes
     * are defined in one of two ways:
     * <ul>
     * <li>By pessimistically guessing how large the union of dex files will be.
     *     We're pessimistic because we can't predict the amount of duplication
     *     between dex files, nor can we predict the length of ULEB-encoded
     *     offsets or indices.
     * <li>By exactly measuring an existing dex.
     * </ul>
     */
    private static class WriterSizes {

        private int header = SizeOf.HEADER_ITEM;
        private int idsDefs;
        private int mapList;
        private int typeList;
        private int classData;
        private int code;
        private int stringData;
        private int debugInfo;
        private int encodedArray;
        private int annotationsDirectory;
        private int annotationsSet;
        private int annotationsSetRefList;
        private int annotation;

        /**
         * Compute sizes for merging several dexes.
         */
        public WriterSizes(Dex dex, boolean exact){
            plus(dex.getTableOfContents(), exact);
            fourByteAlign();
        }

        public WriterSizes(DexTransform dexTransform){
            header = dexTransform.headerOut.used();
            idsDefs = dexTransform.idsDefsOut.used();
            mapList = dexTransform.mapListOut.used();
            typeList = dexTransform.typeListOut.used();
            classData = dexTransform.classDataOut.used();
            code = dexTransform.codeOut.used();
            stringData = dexTransform.stringDataOut.used();
            debugInfo = dexTransform.debugInfoOut.used();
            encodedArray = dexTransform.encodedArrayOut.used();
            annotationsDirectory = dexTransform.annotationsDirectoryOut.used();
            annotationsSet = dexTransform.annotationSetOut.used();
            annotationsSetRefList = dexTransform.annotationSetRefListOut.used();
            annotation = dexTransform.annotationOut.used();
            fourByteAlign();
        }

        private void plus(TableOfContents contents, boolean exact) {
            idsDefs += contents.stringIds.size * SizeOf.STRING_ID_ITEM + contents.typeIds.size * SizeOf.TYPE_ID_ITEM
                    + contents.protoIds.size * SizeOf.PROTO_ID_ITEM + contents.fieldIds.size * SizeOf.MEMBER_ID_ITEM
                    + contents.methodIds.size * SizeOf.MEMBER_ID_ITEM
                    + contents.classDefs.size * SizeOf.CLASS_DEF_ITEM;
            mapList = SizeOf.UINT + (contents.sections.length * SizeOf.MAP_ITEM);
            typeList += fourByteAlign(contents.typeLists.byteCount); // We count each dex's
            // typelists section as realigned on 4 bytes, because each typelist of each dex's
            // typelists section is aligned on 4 bytes. If we didn't, there is a case where each
            // size of both dex's typelists section is a multiple of 2 but not a multiple of 4,
            // and the sum of both sizes is a multiple of 4 but would not be sufficient to write
            // each typelist aligned on 4 bytes.
            stringData += contents.stringDatas.byteCount;
            annotationsDirectory += contents.annotationsDirectories.byteCount;
            annotationsSet += contents.annotationSets.byteCount;
            annotationsSetRefList += contents.annotationSetRefLists.byteCount;

            if (exact) {
                code += contents.codes.byteCount;
                classData += contents.classDatas.byteCount;
                encodedArray += contents.encodedArrays.byteCount;
                annotation += contents.annotations.byteCount;
                debugInfo += contents.debugInfos.byteCount;
            } else {
                // at most 1/4 of the bytes in a code section are uleb/sleb
                code += (int) Math.ceil(contents.codes.byteCount * 1.25);
                // at most 1/3 of the bytes in a class data section are uleb/sleb
                classData += (int) Math.ceil(contents.classDatas.byteCount * 1.34);
                // all of the bytes in an encoding arrays section may be uleb/sleb
                encodedArray += contents.encodedArrays.byteCount * 2;
                // all of the bytes in an annotations section may be uleb/sleb
                annotation += (int) Math.ceil(contents.annotations.byteCount * 2);
                // all of the bytes in a debug info section may be uleb/sleb
                debugInfo += contents.debugInfos.byteCount * 2;
            }
        }

        private void fourByteAlign() {
            header = fourByteAlign(header);
            idsDefs = fourByteAlign(idsDefs);
            mapList = fourByteAlign(mapList);
            typeList = fourByteAlign(typeList);
            classData = fourByteAlign(classData);
            code = fourByteAlign(code);
            stringData = fourByteAlign(stringData);
            debugInfo = fourByteAlign(debugInfo);
            encodedArray = fourByteAlign(encodedArray);
            annotationsDirectory = fourByteAlign(annotationsDirectory);
            annotationsSet = fourByteAlign(annotationsSet);
            annotationsSetRefList = fourByteAlign(annotationsSetRefList);
            annotation = fourByteAlign(annotation);
        }

        private static int fourByteAlign(int position) {
            return (position + 3) & ~3;
        }

        public int size() {
            return header + idsDefs + mapList + typeList + classData + code + stringData + debugInfo + encodedArray
                    + annotationsDirectory + annotationsSet + annotationsSetRefList + annotation;
        }
    }

    class DexItem {

        int offset;
        int index;
        int newOffset;

        public DexItem(int offset, int index, int newOffset){
            this.offset = offset;
            this.index = index;
            this.newOffset = newOffset;
        }
    }
}

