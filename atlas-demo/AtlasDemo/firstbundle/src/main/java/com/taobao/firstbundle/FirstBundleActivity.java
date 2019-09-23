package com.taobao.firstbundle;

import android.net.Uri;
import android.os.Bundle;


import androidx.appcompat.app.AppCompatActivity;
import com.taobao.firstbundle.fragment.BlankFragment;

public class FirstBundleActivity extends AppCompatActivity implements BlankFragment.OnFragmentInteractionListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_firstbundle);
        LibraryTest libraryTest = new LibraryTest();
        libraryTest.test();
    }


    @Override
    public void onFragmentInteraction(Uri uri) {

    }
}
