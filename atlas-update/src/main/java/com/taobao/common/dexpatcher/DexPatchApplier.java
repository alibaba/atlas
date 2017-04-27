/*
 * Tencent is pleased to support the open source community by making Tinker available.
 *
 * Copyright (C) 2016 THL A29 Limited, a Tencent company. All rights reserved.
 *
 * Licensed under the BSD 3-Clause License (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 *
 * https://opensource.org/licenses/BSD-3-Clause
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.taobao.common.dexpatcher;

import com.taobao.common.dexpatcher.algorithms.patch.*;
import com.taobao.common.dexpatcher.struct.DexPatchFile;
import com.taobao.common.dexpatcher.struct.SmallPatchedDexItemFile;
import com.taobao.dex.*;
import com.taobao.dx.util.Hex;
import com.taobao.dx.util.IndexMap;

import java.io.*;
import java.util.Arrays;

/**
 * Created by tangyinsheng on 2016/6/30.
 */
public class DexPatchApplier {
    private final Dex oldDex;
    private final Dex patchedDex;

    /** May be null if we need to generate small patch. **/
    private final DexPatchFile patchFile;

    private final SmallPatchedDexItemFile extraInfoFile;
    private final IndexMap oldToFullPatchedIndexMap;
    private final IndexMap patchedToSmallPatchedIndexMap;

    private final String oldDexSignStr;

    private DexSectionPatchAlgorithm<StringData> stringDataSectionPatchAlg;
    private DexSectionPatchAlgorithm<Integer> typeIdSectionPatchAlg;
    private DexSectionPatchAlgorithm<ProtoId> protoIdSectionPatchAlg;
    private DexSectionPatchAlgorithm<FieldId> fieldIdSectionPatchAlg;
    private DexSectionPatchAlgorithm<MethodId> methodIdSectionPatchAlg;
    private DexSectionPatchAlgorithm<ClassDef> classDefSectionPatchAlg;
    private DexSectionPatchAlgorithm<TypeList> typeListSectionPatchAlg;
    private DexSectionPatchAlgorithm<AnnotationSetRefList> annotationSetRefListSectionPatchAlg;
    private DexSectionPatchAlgorithm<AnnotationSet> annotationSetSectionPatchAlg;
    private DexSectionPatchAlgorithm<ClassData> classDataSectionPatchAlg;
    private DexSectionPatchAlgorithm<Code> codeSectionPatchAlg;
    private DexSectionPatchAlgorithm<DebugInfoItem> debugInfoSectionPatchAlg;
    private DexSectionPatchAlgorithm<Annotation> annotationSectionPatchAlg;
    private DexSectionPatchAlgorithm<EncodedValue> encodedArraySectionPatchAlg;
    private DexSectionPatchAlgorithm<AnnotationsDirectory> annotationsDirectorySectionPatchAlg;

    public DexPatchApplier(File oldDexIn, File patchFileIn) throws IOException {
        this(
                new Dex(oldDexIn),
                (patchFileIn != null ? new DexPatchFile(patchFileIn) : null),
                null
        );
    }

    public DexPatchApplier(InputStream oldDexIn, InputStream patchFileIn) throws IOException {
        this(
                new Dex(oldDexIn),
                (patchFileIn != null ? new DexPatchFile(patchFileIn) : null),
                null
        );
    }

    public DexPatchApplier(InputStream oldDexIn, int initDexSize, InputStream patchFileIn) throws IOException {
        this(
                new Dex(oldDexIn, initDexSize),
                (patchFileIn != null ? new DexPatchFile(patchFileIn) : null),
                null
        );
    }

    public DexPatchApplier(
            File oldDexIn,
            File patchFileIn,
            SmallPatchedDexItemFile extraInfoFile
    ) throws IOException {
        this(
                new Dex(oldDexIn),
                (patchFileIn != null ? new DexPatchFile(patchFileIn) : null),
                extraInfoFile
        );
    }

    public DexPatchApplier(
            InputStream oldDexIn,
            InputStream patchFileIn,
            SmallPatchedDexItemFile extraInfoFile
    ) throws IOException {
        this(
                new Dex(oldDexIn),
                (patchFileIn != null ? new DexPatchFile(patchFileIn) : null),
                extraInfoFile
        );
    }

    public DexPatchApplier(
            InputStream oldDexIn,
            int initDexSize,
            InputStream patchFileIn,
            SmallPatchedDexItemFile extraInfoFile
    ) throws IOException {
        this(
                new Dex(oldDexIn, initDexSize),
                (patchFileIn != null ? new DexPatchFile(patchFileIn) : null),
                extraInfoFile
        );
    }

    public DexPatchApplier(
            Dex oldDexIn,
            DexPatchFile patchFileIn,
            SmallPatchedDexItemFile extraAddedDexElementsFile
    ) {
        this.oldDex = oldDexIn;
        this.oldDexSignStr = Hex.toHexString(oldDexIn.computeSignature(false));
        this.patchFile = patchFileIn;
        if (extraAddedDexElementsFile == null) {
            this.patchedDex = new Dex(patchFileIn.getPatchedDexSize());
        } else {
            this.patchedDex = new Dex(
                    extraAddedDexElementsFile.getPatchedDexSizeByOldDexSign(this.oldDexSignStr)
            );
        }
        this.oldToFullPatchedIndexMap = new IndexMap();
        this.patchedToSmallPatchedIndexMap = (extraAddedDexElementsFile != null ? new IndexMap() : null);
        this.extraInfoFile = extraAddedDexElementsFile;

        if ((patchFileIn == null) && (extraAddedDexElementsFile == null
                || !extraAddedDexElementsFile.isAffectedOldDex(this.oldDexSignStr))) {
            throw new UnsupportedOperationException(
                    "patchFileIn is null, and extraAddedDexElementFile"
                            + "(smallPatchInfo) is null or does not mention "
                            + "oldDexIn."
            );
        }
    }

    public void executeAndSaveTo(OutputStream out) throws IOException {
        // Before executing, we should check if this patch can be applied to
        // old dex we passed in.
        byte[] oldDexSign = this.oldDex.computeSignature(false);
        if (oldDexSign == null) {
            throw new IOException("failed to compute old dex's signature.");
        }

//        if (this.patchFile != null) {
//            byte[] oldDexSignInPatchFile = this.patchFile.getOldDexSignature();
//            if (CompareUtils.uArrCompare(oldDexSign, oldDexSignInPatchFile) != 0) {
//                throw new IOException(
//                        String.format(
//                                "old dex signature mismatch! expected: %s, actual: %s",
//                                Arrays.toString(oldDexSign),
//                                Arrays.toString(oldDexSignInPatchFile)
//                        )
//                );
//            }
//        }

        String oldDexSignStr = Hex.toHexString(oldDexSign);

        // Firstly, set sections' offset after patched, sort according to their offset so that
        // the dex lib of aosp can calculate section size.
        TableOfContents patchedToc = this.patchedDex.getTableOfContents();

        patchedToc.header.off = 0;
        patchedToc.header.size = 1;
        patchedToc.mapList.size = 1;

        if (extraInfoFile == null || !extraInfoFile.isAffectedOldDex(this.oldDexSignStr)) {
            patchedToc.stringIds.off
                    = this.patchFile.getPatchedStringIdSectionOffset();
            patchedToc.typeIds.off
                    = this.patchFile.getPatchedTypeIdSectionOffset();
            patchedToc.typeLists.off
                    = this.patchFile.getPatchedTypeListSectionOffset();
            patchedToc.protoIds.off
                    = this.patchFile.getPatchedProtoIdSectionOffset();
            patchedToc.fieldIds.off
                    = this.patchFile.getPatchedFieldIdSectionOffset();
            patchedToc.methodIds.off
                    = this.patchFile.getPatchedMethodIdSectionOffset();
            patchedToc.classDefs.off
                    = this.patchFile.getPatchedClassDefSectionOffset();
            patchedToc.mapList.off
                    = this.patchFile.getPatchedMapListSectionOffset();
            patchedToc.stringDatas.off
                    = this.patchFile.getPatchedStringDataSectionOffset();
            patchedToc.annotations.off
                    = this.patchFile.getPatchedAnnotationSectionOffset();
            patchedToc.annotationSets.off
                    = this.patchFile.getPatchedAnnotationSetSectionOffset();
            patchedToc.annotationSetRefLists.off
                    = this.patchFile.getPatchedAnnotationSetRefListSectionOffset();
            patchedToc.annotationsDirectories.off
                    = this.patchFile.getPatchedAnnotationsDirectorySectionOffset();
            patchedToc.encodedArrays.off
                    = this.patchFile.getPatchedEncodedArraySectionOffset();
            patchedToc.debugInfos.off
                    = this.patchFile.getPatchedDebugInfoSectionOffset();
            patchedToc.codes.off
                    = this.patchFile.getPatchedCodeSectionOffset();
            patchedToc.classDatas.off
                    = this.patchFile.getPatchedClassDataSectionOffset();
            patchedToc.fileSize
                    = this.patchFile.getPatchedDexSize();
        } else {
            patchedToc.stringIds.off
                    = this.extraInfoFile.getPatchedStringIdOffsetByOldDexSign(oldDexSignStr);
            patchedToc.typeIds.off
                    = this.extraInfoFile.getPatchedTypeIdOffsetByOldDexSign(oldDexSignStr);
            patchedToc.typeLists.off
                    = this.extraInfoFile.getPatchedTypeListOffsetByOldDexSign(oldDexSignStr);
            patchedToc.protoIds.off
                    = this.extraInfoFile.getPatchedProtoIdOffsetByOldDexSign(oldDexSignStr);
            patchedToc.fieldIds.off
                    = this.extraInfoFile.getPatchedFieldIdOffsetByOldDexSign(oldDexSignStr);
            patchedToc.methodIds.off
                    = this.extraInfoFile.getPatchedMethodIdOffsetByOldDexSign(oldDexSignStr);
            patchedToc.classDefs.off
                    = this.extraInfoFile.getPatchedClassDefOffsetByOldDexSign(oldDexSignStr);
            patchedToc.mapList.off
                    = this.extraInfoFile.getPatchedMapListOffsetByOldDexSign(oldDexSignStr);
            patchedToc.stringDatas.off
                    = this.extraInfoFile.getPatchedStringDataOffsetByOldDexSign(oldDexSignStr);
            patchedToc.annotations.off
                    = this.extraInfoFile.getPatchedAnnotationOffsetByOldDexSign(oldDexSignStr);
            patchedToc.annotationSets.off
                    = this.extraInfoFile.getPatchedAnnotationSetOffsetByOldDexSign(oldDexSignStr);
            patchedToc.annotationSetRefLists.off
                    = this.extraInfoFile.getPatchedAnnotationSetRefListOffsetByOldDexSign(oldDexSignStr);
            patchedToc.annotationsDirectories.off
                    = this.extraInfoFile.getPatchedAnnotationsDirectoryOffsetByOldDexSign(oldDexSignStr);
            patchedToc.encodedArrays.off
                    = this.extraInfoFile.getPatchedEncodedArrayOffsetByOldDexSign(oldDexSignStr);
            patchedToc.debugInfos.off
                    = this.extraInfoFile.getPatchedDebugInfoOffsetByOldDexSign(oldDexSignStr);
            patchedToc.codes.off
                    = this.extraInfoFile.getPatchedCodeOffsetByOldDexSign(oldDexSignStr);
            patchedToc.classDatas.off
                    = this.extraInfoFile.getPatchedClassDataOffsetByOldDexSign(oldDexSignStr);
            patchedToc.fileSize
                    = this.extraInfoFile.getPatchedDexSizeByOldDexSign(oldDexSignStr);
        }

        Arrays.sort(patchedToc.sections);

        patchedToc.computeSizesFromOffsets();

        // Secondly, run patch algorithms according to sections' dependencies.
        this.stringDataSectionPatchAlg = new StringDataSectionPatchAlgorithm(
                patchFile, oldDex, patchedDex, oldToFullPatchedIndexMap,
                patchedToSmallPatchedIndexMap, extraInfoFile
        );
        this.typeIdSectionPatchAlg = new TypeIdSectionPatchAlgorithm(
                patchFile, oldDex, patchedDex, oldToFullPatchedIndexMap,
                patchedToSmallPatchedIndexMap, extraInfoFile
        );
        this.protoIdSectionPatchAlg = new ProtoIdSectionPatchAlgorithm(
                patchFile, oldDex, patchedDex, oldToFullPatchedIndexMap,
                patchedToSmallPatchedIndexMap, extraInfoFile
        );
        this.fieldIdSectionPatchAlg = new FieldIdSectionPatchAlgorithm(
                patchFile, oldDex, patchedDex, oldToFullPatchedIndexMap,
                patchedToSmallPatchedIndexMap, extraInfoFile
        );
        this.methodIdSectionPatchAlg = new MethodIdSectionPatchAlgorithm(
                patchFile, oldDex, patchedDex, oldToFullPatchedIndexMap,
                patchedToSmallPatchedIndexMap, extraInfoFile
        );
        this.classDefSectionPatchAlg = new ClassDefSectionPatchAlgorithm(
                patchFile, oldDex, patchedDex, oldToFullPatchedIndexMap,
                patchedToSmallPatchedIndexMap, extraInfoFile
        );
        this.typeListSectionPatchAlg = new TypeListSectionPatchAlgorithm(
                patchFile, oldDex, patchedDex, oldToFullPatchedIndexMap,
                patchedToSmallPatchedIndexMap, extraInfoFile
        );
        this.annotationSetRefListSectionPatchAlg = new AnnotationSetRefListSectionPatchAlgorithm(
                patchFile, oldDex, patchedDex, oldToFullPatchedIndexMap,
                patchedToSmallPatchedIndexMap, extraInfoFile
        );
        this.annotationSetSectionPatchAlg = new AnnotationSetSectionPatchAlgorithm(
                patchFile, oldDex, patchedDex, oldToFullPatchedIndexMap,
                patchedToSmallPatchedIndexMap, extraInfoFile
        );
        this.classDataSectionPatchAlg = new ClassDataSectionPatchAlgorithm(
                patchFile, oldDex, patchedDex, oldToFullPatchedIndexMap,
                patchedToSmallPatchedIndexMap, extraInfoFile
        );
        this.codeSectionPatchAlg = new CodeSectionPatchAlgorithm(
                patchFile, oldDex, patchedDex, oldToFullPatchedIndexMap,
                patchedToSmallPatchedIndexMap, extraInfoFile
        );
        this.debugInfoSectionPatchAlg = new DebugInfoItemSectionPatchAlgorithm(
                patchFile, oldDex, patchedDex, oldToFullPatchedIndexMap,
                patchedToSmallPatchedIndexMap, extraInfoFile
        );
        this.annotationSectionPatchAlg = new AnnotationSectionPatchAlgorithm(
                patchFile, oldDex, patchedDex, oldToFullPatchedIndexMap,
                patchedToSmallPatchedIndexMap, extraInfoFile
        );
        this.encodedArraySectionPatchAlg = new StaticValueSectionPatchAlgorithm(
                patchFile, oldDex, patchedDex, oldToFullPatchedIndexMap,
                patchedToSmallPatchedIndexMap, extraInfoFile
        );
        this.annotationsDirectorySectionPatchAlg = new AnnotationsDirectorySectionPatchAlgorithm(
                patchFile, oldDex, patchedDex, oldToFullPatchedIndexMap,
                patchedToSmallPatchedIndexMap, extraInfoFile
        );

        this.stringDataSectionPatchAlg.execute();
        this.typeIdSectionPatchAlg.execute();
        this.typeListSectionPatchAlg.execute();
        this.protoIdSectionPatchAlg.execute();
        this.fieldIdSectionPatchAlg.execute();
        this.methodIdSectionPatchAlg.execute();
        Runtime.getRuntime().gc();
        this.annotationSectionPatchAlg.execute();
        this.annotationSetSectionPatchAlg.execute();
        this.annotationSetRefListSectionPatchAlg.execute();
        this.annotationsDirectorySectionPatchAlg.execute();
        Runtime.getRuntime().gc();
        this.debugInfoSectionPatchAlg.execute();
        this.codeSectionPatchAlg.execute();
        Runtime.getRuntime().gc();
        this.classDataSectionPatchAlg.execute();
        this.encodedArraySectionPatchAlg.execute();
        this.classDefSectionPatchAlg.execute();
        Runtime.getRuntime().gc();

        // Thirdly, write header, mapList. Calculate and write patched dex's sign and checksum.
        Dex.Section headerOut = this.patchedDex.openSection(patchedToc.header.off);
        patchedToc.writeHeader(headerOut);

        Dex.Section mapListOut = this.patchedDex.openSection(patchedToc.mapList.off);
        patchedToc.writeMap(mapListOut);

        this.patchedDex.writeHashes();

        // Finally, write patched dex to file.
        this.patchedDex.writeTo(out);
    }

    public void executeAndSaveTo(File file) throws IOException {
        OutputStream os = null;
        try {
            os = new BufferedOutputStream(new FileOutputStream(file));
            executeAndSaveTo(os);
        } finally {
            if (os != null) {
                try {
                    os.close();
                } catch (Exception e) {
                    // ignored.
                }
            }
        }
    }
}
