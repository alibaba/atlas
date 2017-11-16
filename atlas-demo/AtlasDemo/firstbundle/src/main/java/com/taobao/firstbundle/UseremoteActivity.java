package com.taobao.firstbundle;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.taobao.atlas.framework.Framework;
import android.taobao.atlas.remote.IRemoteContext;
import android.taobao.atlas.remote.RemoteFactory;
import android.taobao.atlas.remote.view.RemoteView;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.FrameLayout;

public class UseremoteActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_useremote);

        RemoteFactory.requestRemote(RemoteView.class, this, new Intent("atlas.view.intent.action.SECOND_RICH"),
                new RemoteFactory.OnRemoteStateListener<RemoteView>() {
            @Override
            public void onRemotePrepared(RemoteView iRemoteContext) {
                FrameLayout layout = (FrameLayout) findViewById(R.id.fl_content);
                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
                layout.addView(iRemoteContext,params);
            }

            @Override
            public void onFailed(String s) {
                Log.e("UserRemoteActivity",s);
            }
        });

    }
}
