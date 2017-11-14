package com.taobao.atlas.update;

import java.util.ArrayList;
import java.util.List;

import com.taobao.atlas.update.model.UpdateInfo;
import com.taobao.atlas.update.model.UpdateInfo.Item;

/**
 * Created by 种藏 on 2017/11/14.
 * .
 */

public class UpdateBundleDivider {

    public static List<Item> dividePatchInfo(List<Item> allItem, int patchType) {
        List<UpdateInfo.Item> list = new ArrayList<>();
        if (null == allItem) {
            return list;
        }
        for (UpdateInfo.Item item : allItem) {
            if (item.patchType == patchType) {
                Item newItem = Item.makeCopy(item);
                newItem.patchType = patchType;
                list.add(newItem);
                continue;
            }
            if (item.patchType == Item.PATCH_DEX_C_AND_H && (patchType == Item.PATCH_DEX_COLD || patchType == Item.PATCH_DEX_HOT) ) {
                Item newItem = Item.makeCopy(item);
                newItem.patchType = patchType;
                list.add(newItem);
            }
        }
        return list;
    }
}