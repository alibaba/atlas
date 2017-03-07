package com.taobao.atlas.dexmerge;
import com.taobao.atlas.dexmerge.IDexMergeCallback;
import com.taobao.atlas.dexmerge.MergeObject;
/**
 * Created by xieguo.xg on 1/20/16.
 */
interface IDexMergeBinder {

    void dexMerge(in String patchFilePath,in List<MergeObject> toMergeList, boolean diffBundleDex);

    void registerListener(IDexMergeCallback listener);
}