package io.middleware.android.sample;


import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.appcompat.app.AppCompatActivity;

import io.middleware.android.sdk.Middleware;

public class WebViewActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_web_view);
        WebView mywebview = findViewById(R.id.webView);
        mywebview.getSettings().setJavaScriptEnabled(true);
        mywebview.setWebViewClient(new WebViewClient());
        Middleware.getInstance().integrateWithBrowserRum(mywebview);
        mywebview.loadUrl("https://www.middleware.io/");
    }
}
