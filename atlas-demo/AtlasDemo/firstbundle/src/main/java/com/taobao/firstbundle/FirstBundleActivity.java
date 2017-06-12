package com.taobao.firstbundle;

import android.content.ContentResolver;
import android.content.res.Resources;
import android.net.Uri;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.Toast;

import com.taobao.firstbundle.fragment.BlankFragment;

import java.io.FileNotFoundException;
import java.io.InputStream;

public class FirstBundleActivity extends AppCompatActivity implements BlankFragment.OnFragmentInteractionListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_firstbundle);
//        Toast.makeText(this,"dsfsfs",Toast.LENGTH_LONG).show();
    }

//    @Override
//    public void onListFragmentInteraction(DummyContent.DummyItem item) {
//
//    }

    @Override
    public void onFragmentInteraction(Uri uri) {

    }
}
