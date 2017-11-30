package com.taobao.secondbundle;

import android.app.ProgressDialog;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.AttrRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StyleRes;
import android.taobao.atlas.remote.IRemote;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;

/**
 * Created by guanjie on 2017/11/14.
 */

public class MyRichFrameLayout extends FrameLayout implements IRemote{
    public MyRichFrameLayout(@NonNull Context context) {
        super(context);
        init();
    }

    public MyRichFrameLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();

    }

    public MyRichFrameLayout(@NonNull Context context, @Nullable AttributeSet attrs, @AttrRes int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();

    }

    public MyRichFrameLayout(@NonNull Context context, @Nullable AttributeSet attrs, @AttrRes int defStyleAttr, @StyleRes int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();

    }

    public void init(){
        LayoutInflater inflater = LayoutInflater.from(getContext());
        inflater.inflate(R.layout.test_layout,this);
        this.findViewById(R.id.cccc).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
//                new AlertDialog.Builder(getContext()).setMessage("remoteview上弹dialog").show();
                ProgressDialog dialog = new ProgressDialog(getContext());
                dialog.show();
            }
        });
    }

    @Override
    public Bundle call(String commandName, Bundle args, IResponse callback) {
        return null;
    }

    @Override
    public <T> T getRemoteInterface(Class<T> interfaceClass, Bundle args) {
        return null;
    }
}
