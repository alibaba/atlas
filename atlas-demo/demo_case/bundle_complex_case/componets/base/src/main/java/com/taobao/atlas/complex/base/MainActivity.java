package com.taobao.atlas.complex.base;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.BottomNavigationView;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.view.ViewGroup;

import com.taobao.atlas.complex.base.middleware.ActivityGroupDelegate;


/**
 * Created by zhongcang on 2017/9/5.
 * .
 */

public class MainActivity extends AppCompatActivity {

    private ActivityGroupDelegate mActivityDelegate;
    private ViewGroup mActivityGroupContainer;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initView();
        mActivityDelegate = new ActivityGroupDelegate(this, savedInstanceState);
        switchToBaseActivity();
    }

    private void initView() {
        ((BottomNavigationView) findViewById(R.id.navigation)).setOnNavigationItemSelectedListener(mOnNavigationItemSelectedListener);
        mActivityGroupContainer = (ViewGroup) findViewById(R.id.content);
        setSupportActionBar((Toolbar) findViewById(R.id.toolbar));
    }

    private BottomNavigationView.OnNavigationItemSelectedListener mOnNavigationItemSelectedListener = new BottomNavigationView.OnNavigationItemSelectedListener() {
        @Override
        public boolean onNavigationItemSelected(@NonNull MenuItem item) {
            int itemId = item.getItemId();
            if (itemId == R.id.navigation_base) {
                switchToBaseActivity();
            } else if (itemId == R.id.navigation_first) {
                switchToActivity("activity_first", "com.taobao.atlas.complex.firstbundle.FirstBundleActivity");
            } else if (itemId == R.id.navigation_second) {
                switchToActivity("activity_second", "com.taobao.atlas.complex.secondbundle.SecondBundleActivity");
            } else {
                return false;
            }
            return true;
        }
    };

    private void switchToBaseActivity() {
        switchToActivity("activity_base", "com.taobao.atlas.complex.base.BaseActivity");
    }

    private void switchToActivity(String key, String activityName) {
        Intent intent = new Intent();
        intent.setClassName(getBaseContext(), activityName);
        mActivityDelegate.startChildActivity(mActivityGroupContainer, key, intent);
    }
}