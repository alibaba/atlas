package com.taobao.atlas.remote.base;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        findViewById(R.id.btn_start_local_bundle).setOnClickListener(this);
        findViewById(R.id.btn_start_remote_bundle).setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.btn_start_local_bundle) {
            startActivity(new Intent().setClassName(
                    getBaseContext(), "com.taobao.atlas.remote.localbundle.LocalBundleActivity"
            ));
        } else if (id == R.id.btn_start_remote_bundle) {
            startActivity(new Intent().setClassName(
                    getBaseContext(), "com.taobao.atlas.remote.remotebundle.RemoteBundleActivity"
            ));
        }
    }
}