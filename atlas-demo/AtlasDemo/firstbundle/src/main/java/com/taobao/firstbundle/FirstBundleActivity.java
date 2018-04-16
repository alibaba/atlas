package com.taobao.firstbundle;

import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.Toast;

import com.taobao.publicBundle.Tools;

public class FirstBundleActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_firstbundle);

//        bundleCompile
        Log.e("providedComple awb", "invoke tools at public bundle  Tools.getCurrentTime() > " + Tools.getCurrentTime());
  //      Toast.makeText(this, "dsfsfs" + Tools.getCurrentTime(), Toast.LENGTH_LONG).show();
    }

}
