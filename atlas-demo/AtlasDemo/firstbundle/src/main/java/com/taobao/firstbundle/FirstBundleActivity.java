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
//        Toast.makeText(this,"this is aaaaaaaaaa  newnewnenenwn bundle",Toast.LENGTH_SHORT).show();


        int model = R.drawable.ssss;
        Resources resources = getResources();
        Uri uri = null;
        try {
            uri = Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE + "://"
                    + resources.getResourcePackageName(model) + '/'
                    + resources.getResourceTypeName(model) + '/'
                    + resources.getResourceEntryName(model));
        } catch (Resources.NotFoundException e) {
            e.printStackTrace();
        }
        ContentResolver contentResolver = getContentResolver();
        try {
            InputStream is = contentResolver.openInputStream(uri);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

//    @Override
//    public void onListFragmentInteraction(DummyContent.DummyItem item) {
//
//    }

    @Override
    public void onFragmentInteraction(Uri uri) {

    }
}
