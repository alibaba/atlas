

package com.taobao.common.dexpatcher.algorithms.diff;

import com.taobao.dex.Dex;
import com.taobao.dex.ProtoId;
import com.taobao.dex.SizeOf;
import com.taobao.dex.TableOfContents;
import com.taobao.dex.io.DexDataBuffer;
import com.taobao.dx.util.IndexMap;


public class ProtoIdSectionDiffAlgorithm extends DexSectionDiffAlgorithm<ProtoId> {
    public ProtoIdSectionDiffAlgorithm(Dex oldDex, Dex newDex, IndexMap oldToNewIndexMap, IndexMap oldToPatchedIndexMap, IndexMap newToPatchedIndexMap, IndexMap selfIndexMapForSkip) {
        super(oldDex, newDex, oldToNewIndexMap, oldToPatchedIndexMap, newToPatchedIndexMap, selfIndexMapForSkip);
    }

    @Override
    protected TableOfContents.Section getTocSection(Dex dex) {
        return dex.getTableOfContents().protoIds;
    }

    @Override
    protected ProtoId nextItem(DexDataBuffer section) {
        return section.readProtoId();
    }

    @Override
    protected int getItemSize(ProtoId item) {
        return SizeOf.PROTO_ID_ITEM;
    }

    @Override
    protected ProtoId adjustItem(IndexMap indexMap, ProtoId item) {
        return indexMap.adjust(item);
    }

    @Override
    protected void updateIndexOrOffset(IndexMap indexMap, int oldIndex, int oldOffset, int newIndex, int newOffset) {
        if (oldIndex != newIndex) {
            indexMap.mapProtoIds(oldIndex, newIndex);
        }
    }

    @Override
    protected void markDeletedIndexOrOffset(IndexMap indexMap, int deletedIndex, int deletedOffset) {
        indexMap.markProtoIdDeleted(deletedIndex);
    }
}
