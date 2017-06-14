package com.taobao.firstbundle;

import android.net.Uri;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class WebViewDemoActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_web_view);


        ((WebView)findViewById(R.id.webView1)).setWebViewClient(new WebViewClient());

        ((WebView)findViewById(R.id.webView1)).loadUrl("http://www.baidu.com");

    }
}
