package com.taobao.demo;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.Button;


public class RemoteDemoActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.guild_remote_bundle);

        final Activity activity = this;

        Button button = (Button) findViewById(R.id.load_remote_bundle);
        button.setOnClickListener(view -> {
            Intent intent = new Intent();
            intent.setClassName(activity, "com.taobao.remotebunle.RemoteBundleActivity");
            startActivity(intent);
        });


    }

}
