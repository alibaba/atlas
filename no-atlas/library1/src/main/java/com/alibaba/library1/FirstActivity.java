package com.alibaba.library1;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import com.alibaba.library1.R;
import com.android.alibaba.ip.common.PatchResult;

import java.io.IOException;

/**
 * 创建日期：2019/3/26 on 上午11:40
 * 描述:
 * 作者:zhayu.ll
 */
public class FirstActivity extends Activity {

    private Button button;
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout1);
        button = findViewById(R.id.button);

        button.setOnClickListener(new View.OnClickListener() {
            @RequiresApi(api = Build.VERSION_CODES.CUPCAKE)
            @Override
            public void onClick(View v) {

                new AsyncTask<Void, Void, PatchResult>() {
                    @Override
                    protected PatchResult doInBackground(Void... voids) {
                        try {
                            return new InstantPatchUpdater(FirstActivity.this).update();
                        } catch (PackageManager.NameNotFoundException e) {
                            e.printStackTrace();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }

                    @Override
                    protected void onPostExecute(PatchResult patchResult) {
                        if (patchResult.resCode == 0){
                            Toast.makeText(FirstActivity.this,"instantpatch 成功",Toast.LENGTH_LONG).show();
                        }else {
                            Toast.makeText(FirstActivity.this,"instantpatch 失败",Toast.LENGTH_LONG).show();

                        }
                    }
                }.execute();

            }
        });

    }
}
