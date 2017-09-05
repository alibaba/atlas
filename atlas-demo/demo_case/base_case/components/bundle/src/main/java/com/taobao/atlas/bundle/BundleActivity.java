package com.taobao.atlas.bundle;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.view.View;

/**
 * Created by zhongcang on 2017/9/5.
 * .
 */

public class BundleActivity extends AppCompatActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bundle);
        findViewById(R.id.btn_start_main).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent().setClassName(getPackageName(), "com.taobao.atalas.base.DemoActivity"));
                finish();
            }
        });
    }
}
