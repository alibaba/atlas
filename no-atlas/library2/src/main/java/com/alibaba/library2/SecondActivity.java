package com.alibaba.library2;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.widget.TextView;
import com.android.alibaba.ip.api.ModifyMethod;

/**
 * 创建日期：2019/3/26 on 上午11:42
 * 描述:
 * 作者:zhayu.ll
 */
public class SecondActivity extends AppCompatActivity {
    private TextView view;


    @ModifyMethod
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout);
        view = findViewById(R.id.textview);

        view.setText("this is SecondActivity patch");

    }
}
