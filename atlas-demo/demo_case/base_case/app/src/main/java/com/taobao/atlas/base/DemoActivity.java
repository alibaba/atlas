package com.taobao.atlas.base;

import android.content.Intent;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;

/**
 * Created by zhongcang on 2017/9/5.
 * .
 */

public class DemoActivity extends AppCompatActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_demo);
        findViewById(R.id.btn_start_bundle).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(Env.TAG, "onClick: ");
                startActivity(
                        new Intent().setClassName(getBaseContext(), "com.taobao.atlas.bundle.BundleActivity")
                );
            }
        });
    }
}