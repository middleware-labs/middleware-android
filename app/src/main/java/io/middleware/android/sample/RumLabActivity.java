package io.middleware.android.sample;

import static io.opentelemetry.api.common.AttributeKey.stringKey;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import java.io.IOException;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.middleware.android.sdk.Middleware;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.TraceId;
import io.opentelemetry.context.Scope;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class RumLabActivity extends AppCompatActivity {

    private static final String TAG = "RumLab";

    private Middleware middleware;
    private Call.Factory okHttpClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rum_lab);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(getString(R.string.rum_lab_title));
        }

        middleware = Middleware.getInstance();
        okHttpClient = ((CoffeeCartApplication) getApplication()).getRumOkHttpClient();

        Button btnHttp = findViewById(R.id.btn_http_call);
        Button btnCrash = findViewById(R.id.crash_button);
        Button btnWebView = findViewById(R.id.web_view_button);
        Button btnWorker = findViewById(R.id.worker_button);
        Button btnCustomEvent = findViewById(R.id.custom_event_button);
        Button btnAnr = findViewById(R.id.anr_button);
        Button btnObfuscatedCrash = findViewById(R.id.btn_trigger_crash);
        Button btnNewSession = findViewById(R.id.force_new_session);
        Button btnCustomException = findViewById(R.id.btn_custom_exception);

        btnHttp.setOnClickListener(v -> makeHttpCall());
        btnCrash.setOnClickListener(v -> triggerCrash());
        btnWebView.setOnClickListener(v -> openWebView());
        btnWorker.setOnClickListener(v -> runWorker());
        btnCustomEvent.setOnClickListener(v -> sendCustomEvent());
        btnAnr.setOnClickListener(v -> simulateAnr());
        btnObfuscatedCrash.setOnClickListener(v -> triggerObfuscatedCrash());
        btnNewSession.setOnClickListener(v -> forceNewSession());
        btnCustomException.setOnClickListener(v -> addCustomException());

        middleware.i(TAG, "RUM Lab opened");
    }

    private void makeHttpCall() {
        Span workflow = middleware.startWorkflow("RumLab HTTP Call");
        middleware.addEvent("rum_lab_http_call", Attributes.empty());
        String url = "https://dummyjson.com/products";

        try (Scope scope = workflow.makeCurrent()) {
            Request request = new Request.Builder().url(url).get().build();
            middleware.d(TAG, "HTTP call starting: " + url);
            okHttpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    middleware.e(TAG, "HTTP failure: " + e.getMessage());
                    middleware.addException(e);
                    workflow.setStatus(StatusCode.ERROR, "HTTP call failed");
                    workflow.end();
                    runOnUiThread(() -> Toast.makeText(RumLabActivity.this,
                            "HTTP call failed", Toast.LENGTH_SHORT).show());
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) {
                    try (ResponseBody body = response.body()) {
                        middleware.d(TAG, "HTTP response: " + response.code());
                        workflow.end();
                        runOnUiThread(() -> Toast.makeText(RumLabActivity.this,
                                "HTTP " + response.code(), Toast.LENGTH_SHORT).show());
                    }
                }
            });
        }
    }

    private void triggerCrash() {
        middleware.d(TAG, "Triggering multi-thread crash");
        middleware.addEvent("rum_lab_crash", Attributes.empty());
        Span workflow = middleware.startWorkflow("Crash Workflow");
        CountDownLatch latch = new CountDownLatch(1);
        int numThreads = 4;
        for (int i = 0; i < numThreads; i++) {
            final int threadIdx = i;
            Thread t = new Thread(() -> {
                try {
                    if (latch.await(10, TimeUnit.SECONDS)) {
                        workflow.end();
                        throw new IllegalStateException(
                                "Deliberate crash from thread crash-thread-" + threadIdx);
                    }
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            });
            t.setName("crash-thread-" + i);
            t.start();
        }
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        latch.countDown();
    }

    private void openWebView() {
        middleware.d(TAG, "Opening WebView from RUM Lab");
        startActivity(new Intent(this, WebViewActivity.class));
    }

    private void runWorker() {
        middleware.d(TAG, "Starting background worker");
        middleware.addEvent("rum_lab_worker", Attributes.empty());
        SampleWorkerManager.startWorker(getApplicationContext());
        Toast.makeText(this, "Worker enqueued", Toast.LENGTH_SHORT).show();
    }

    private void sendCustomEvent() {
        middleware.d(TAG, "Sending custom RUM event");
        middleware.addEvent("rum_lab_custom_event",
                Attributes.of(stringKey("message"), "Hello from RUM Lab!",
                        stringKey("source"), "rum_lab"));
        Toast.makeText(this, "Custom RUM event sent", Toast.LENGTH_SHORT).show();
    }

    private void simulateAnr() {
        middleware.i(TAG, "Simulating ANR – blocking main thread");
        middleware.addEvent("rum_lab_anr", Attributes.empty());
        Span anrSpan = middleware.startWorkflow("ANR Simulation");
        for (int i = 1; i <= 10; i++) {
            try {
                Thread.sleep(1000);
                anrSpan.addEvent("Main thread sleep iteration: " + i);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        anrSpan.end();
    }

    private void triggerObfuscatedCrash() {
        middleware.d(TAG, "Triggering obfuscated crash via CrashHelper");
        middleware.addEvent("rum_lab_obfuscated_crash", Attributes.empty());
        new CrashHelper().executeRiskOperation();
    }

    private void forceNewSession() {
        Random random = new Random();
        String newSessionId = TraceId.fromLongs(random.nextLong(), random.nextLong());
        middleware.setNativeSession(newSessionId, String.valueOf(System.currentTimeMillis()));
        middleware.i(TAG, "New session forced: " + newSessionId);
        Toast.makeText(this, "New session: " + newSessionId.substring(0, 8) + "…",
                Toast.LENGTH_LONG).show();
    }

    private void addCustomException() {
        Exception e = new UnsupportedOperationException(
                "Custom exception from RUM Lab – " + System.currentTimeMillis());
        middleware.e(TAG, "Adding custom exception: " + e.getMessage());
        middleware.addException(e, Attributes.of(
                stringKey("source"), "rum_lab",
                stringKey("severity"), "low"
        ));
        Toast.makeText(this, "Custom exception recorded", Toast.LENGTH_SHORT).show();
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
