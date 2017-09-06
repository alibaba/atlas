package com.taobao.atlas.complex.publicbundle;


import android.content.Context;

/**
 * Created by 种藏 on 2017/9/6.
 * .
 */

public class LibUtils {
    private static String sContent = "";

    static void init(Context context) {
        sContent = context.getResources().getString(R.string.usage_bundle_dependencies);
    }

    public static String getShowText() {
        return sContent;
    }
}