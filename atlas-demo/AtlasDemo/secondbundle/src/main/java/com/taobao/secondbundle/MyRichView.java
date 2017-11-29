package com.taobao.secondbundle;

import android.content.Context;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.widget.TextView;

/**
 * Created by guanjie on 2017/11/14.
 */

public class MyRichView extends TextView{
    public MyRichView(Context context) {
        super(context);
    }

    public MyRichView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public MyRichView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public MyRichView(Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }
}
