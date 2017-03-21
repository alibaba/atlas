package com.middleware.dialog;

import android.content.res.Resources;
import android.util.TypedValue;

/**
 * Created by guanjie on 15/9/19.
 */
public class Utils {
    /**
     * Convert Dp to Pixel
     */
    public static int dpToPx(float dp, Resources resources){
        float px = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.getDisplayMetrics());
        return (int) px;
    }
}
