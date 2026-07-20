package io.middleware.android.sample;

import android.os.Bundle;
import android.view.MenuItem;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import io.middleware.android.sdk.Middleware;

public class WebViewActivity extends AppCompatActivity {

    private static final String TAG = "WebViewActivity";
    // Hybrid RUM demo: the sandbox PWA runs the Middleware browser SDK;
    // integrateWithBrowserRum bridges the native session id into it, so native
    // and web telemetry (including both session replay streams) land in one
    // session.
    private static final String HELP_URL = "https://sandbox-frontend.mw.dev";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_web_view);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(getString(R.string.webview_title));
        }

        WebView webView = findViewById(R.id.webView);
        webView.getSettings().setJavaScriptEnabled(true);
        // The sandbox PWA needs localStorage; without DOM storage most modern
        // web apps render a blank page inside a WebView.
        webView.getSettings().setDomStorageEnabled(true);
        webView.setWebViewClient(new WebViewClient());

        Middleware.getInstance().integrateWithBrowserRum(webView);
        Middleware.getInstance().i(TAG, "WebView loading: " + HELP_URL);

        webView.loadUrl(HELP_URL);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
