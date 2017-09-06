package com.taobao.atlas.complex.firstbundle;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.TextView;

import com.taobao.atlas.complex.publicbundle.LibUtils;

public class FirstBundleActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_first_bundle);
        intiView();
    }

    private void intiView() {
        TextView content = (TextView) findViewById(R.id.text);
        content.setText(LibUtils.getShowText());
    }
}
