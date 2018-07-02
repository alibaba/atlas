package com.taobao.secondbundle;

import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;
import android.taobao.atlas.remote.IRemote;
import android.taobao.atlas.remote.IRemoteTransactor;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

public class SecondBundleFragment extends Fragment implements IRemote {


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.activity_secondbundle, container, false);


        return rootView;
    }


    @Override
    public Bundle call(String s, Bundle bundle, IResponse iResponse) {
        return null;
    }

    @Override
    public <T> T getRemoteInterface(Class<T> aClass, Bundle bundle) {
        return null;
    }
}
