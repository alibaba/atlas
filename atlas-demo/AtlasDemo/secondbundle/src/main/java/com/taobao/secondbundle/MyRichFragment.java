package com.taobao.secondbundle;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.taobao.atlas.remote.IRemote;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

/**
 * Created by guanjie on 2017/12/8.
 */

public class MyRichFragment extends Fragment implements IRemote{

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_plus_one,container,false);


        v.findViewById(R.id.plus_one_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent();
                intent.setClassName(view.getContext(),"com.taobao.secondbundlelibrary.SecondbundleShareActivity");

                view.getContext().startActivity(intent);
            }
        });


        return v;
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
