package com.taobao.atlas.dexmerge;

/**
 * Created by xieguo.xg on 1/20/16.
 */
interface IDexMergeCallback {

    void onMergeFinish(String filePath,boolean result, String reason);

    void onMergeAllFinish(boolean result,String reason);
}
