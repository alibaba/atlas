package com.taobao.firstbundle;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.taobao.atlas.framework.Framework;
import android.taobao.atlas.remote.IRemote;
import android.taobao.atlas.remote.IRemoteContext;
import android.taobao.atlas.remote.IRemoteTransactor;
import android.taobao.atlas.remote.RemoteFactory;
import android.taobao.atlas.remote.fragment.RemoteFragment;
import android.taobao.atlas.remote.transactor.RemoteTransactor;
import android.taobao.atlas.remote.view.RemoteView;
import android.taobao.atlas.runtime.RuntimeVariables;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.taobao.middleware.ICaculator;

public class UseremoteActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_useremote);


        findViewById(R.id.btn_view).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                RemoteFactory.requestRemote(RemoteView.class, UseremoteActivity.this, new Intent("atlas.view.intent.action.SECOND_RICH"),
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
        });

        findViewById(R.id.btn_frag).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                RemoteFactory.requestRemote(RemoteFragment.class, UseremoteActivity.this, new Intent("atlas.fragment.intent.action.SECOND_FRAGMENT"),
                        new RemoteFactory.OnRemoteStateListener<RemoteFragment>() {
                            @Override
                            public void onRemotePrepared(RemoteFragment iRemote) {
                                getSupportFragmentManager().beginTransaction().add(R.id.fl_content2,iRemote).commit();
                            }

                            @Override
                            public void onFailed(String s) {
                                Log.e("UserRemoteActivity",s);
                            }
                        });
            }
        });

        findViewById(R.id.btn_tran).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                RemoteFactory.requestRemote(RemoteTransactor.class, UseremoteActivity.this, new Intent("atlas.transaction.intent.action.SECOND_TRANSACTION"),
                        new RemoteFactory.OnRemoteStateListener<RemoteTransactor>() {
                            @Override
                            public void onRemotePrepared(RemoteTransactor iRemote) {
                                // if you want remote call you
                                iRemote.registerHostTransactor(new IRemote() {
                                    @Override
                                    public Bundle call(String s, Bundle bundle, IResponse iResponse) {
                                        Toast.makeText(RuntimeVariables.androidApplication,"Command is "+s,Toast.LENGTH_SHORT).show();
                                        return null;
                                    }

                                    @Override
                                    public <T> T getRemoteInterface(Class<T> aClass, Bundle bundle) {
                                        return null;
                                    }
                                });

                                ICaculator caculator = iRemote.getRemoteInterface(ICaculator.class,null);
                                Toast.makeText(RuntimeVariables.androidApplication,"1+1 = "+caculator.sum(1,1),Toast.LENGTH_SHORT).show();

                                //you can also use this
//                                Bundle bundle = new Bundle();
//                                bundle.putInt("num1",1);
//                                bundle.putInt("num2",1);
//                                Bundle result  = iRemote.call("sum",bundle,null);
//                                Toast.makeText(RuntimeVariables.androidApplication,"1+1 = "+result.getInt("result"),Toast.LENGTH_SHORT).show();
                            }

                            @Override
                            public void onFailed(String s) {
                                Log.e("UserRemoteActivity",s);
                            }
                        });
            }
        });

    }
}
