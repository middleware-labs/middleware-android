package io.middleware.android.sample;

import static io.opentelemetry.api.common.AttributeKey.stringKey;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.MutableLiveData;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.middleware.android.sdk.Middleware;
import io.middleware.android.sdk.core.replay.MiddlewareRecorder;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.events.EventEmitter;
import io.opentelemetry.api.events.EventEmitterProvider;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.logs.internal.SdkEventEmitterProvider;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

@RequiresApi(api = Build.VERSION_CODES.N)
public class MainActivity extends AppCompatActivity {

    private Call.Factory okHttpClient;
    private final MutableLiveData<String> httpResponse = new MutableLiveData<>();
    private Middleware middleware;
    int count = 0;
    private MiddlewareRecorder recorder = Middleware.getInstance().getRecorder();

    @Override
    protected void onResume() {
        super.onResume();
        recorder.startRecording(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        recorder.stopRecording();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        middleware = Middleware.getInstance();
        okHttpClient = buildOkHttpClient(middleware);
        count = 0;
        final Button httpButton = findViewById(R.id.http_button);
        httpButton.setOnClickListener(v -> {
            middleware.addEvent("click", Attributes.empty());
            Span workflow = middleware.startWorkflow("MAKE HTTP CUSTOM CALL");
            makeCall("http://pmrum.o11ystore.com/?user=me&pass=secret123secret", workflow);
            makeRawCall("https://demo.mw.dev/api/products?currencyCode=USD");
            middleware.setGlobalAttribute(AttributeKey.longKey("customerId"), 123456L);
            count++;
            middleware.d("BUTTONS", "User tapped the HTTP Call Button " + count + " times");
            Toast.makeText(getApplicationContext(), "HTTP CALL Successful", Toast.LENGTH_SHORT).show();
        });

        final Button crashButton = findViewById(R.id.crash_button);
        crashButton.setOnClickListener(v -> {
            middleware.d("BUTTON", "CLICKED : CRASH BUTTON");
            middleware.addEvent("click", Attributes.empty());
            Span workflow = middleware.startWorkflow("Crash Workflow");
            crashFlowTrigger(workflow);
            middleware.setGlobalAttribute(stringKey("crashId"), String.valueOf(Math.random()));

        });

        final Button webView = findViewById(R.id.web_view_button);
        webView.setOnClickListener(v -> {
            middleware.d("BUTTON", "CLICKED: WEBVIEW BUTTON");
            middleware.addEvent("click", Attributes.empty());
            Intent i = new Intent(this, WebViewActivity.class);
            startActivity(i);
            Toast.makeText(getApplicationContext(), "Moved to WebView Activity", Toast.LENGTH_SHORT).show();
        });

        final Button workerButton = findViewById(R.id.worker_button);
        workerButton.setOnClickListener(v -> {
            middleware.d("BUTTON", "CLICKED: WORKER BUTTON");
            middleware.addEvent("click", Attributes.empty());
            SampleWorkerManager.startWorker(this.getApplicationContext());
            Toast.makeText(getApplicationContext(), "Worker Execution Completed", Toast.LENGTH_SHORT).show();
        });

        final Button customRumEvent = findViewById(R.id.custom_event_button);
        customRumEvent.setOnClickListener(v -> {
            middleware.d("BUTTON", "CLICKED: CUSTOM RUM EVENT");
            middleware.addEvent("rum_event", Attributes.of(stringKey("message"), "My First RUM EVENT"));
            Toast.makeText(getApplicationContext(), "Your Rum Event Successfully sent", Toast.LENGTH_SHORT).show();
        });

        final Button anrButton = findViewById(R.id.anr_button);
        anrButton.setOnClickListener(v -> {
            final Span appFreezing = middleware.startWorkflow("App Freezing");
            for (int i = 1; i <= 25; i++) {
                try {
                    Thread.sleep(1000);
                    appFreezing.addEvent("Sleeping Count: " + i);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    appFreezing.end();
                }
            }
        });
    }

    private void crashFlowTrigger(Span workflow) {
        CountDownLatch latch = new CountDownLatch(1);
        int numThreads = 4;
        for (int i = 0; i < numThreads; ++i) {
            Thread t =
                    new Thread(
                            () -> {
                                try {
                                    if (latch.await(10, TimeUnit.SECONDS)) {
                                        workflow.end();
                                        throw new IllegalStateException(
                                                "Failure from thread "
                                                        + Thread.currentThread().getName());
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

    @SuppressLint("AllowAllHostnameVerifier")
    private Call.Factory buildOkHttpClient(Middleware middlewareRum) {
        // grab the default executor service that okhttp uses, and wrap it with one that will
        // propagate the otel context.
        OkHttpClient.Builder builder = new OkHttpClient.Builder();

        // NOTE: This is really bad and dangerous. Don't ever do this in the real world.
        // it's only necessary because the demo endpoint uses a self-signed SSL cert.
        return middlewareRum.createRumOkHttpCallFactory(
                builder.build());

    }

    private void makeCall(String url, Span workflow) {
        // make sure the span is in the current context so it can be propagated into the async call.
        try (Scope scope = workflow.makeCurrent()) {
            Call call = okHttpClient.newCall(new Request.Builder().url(url).get().build());
            middleware.d("HTTP", "HTTP CALL STARTED " + url);
            middleware.d("HTTP", "HEADERS : " + call.request().headers().toString());
            call.enqueue(
                    new Callback() {
                        @Override
                        public void onFailure(@NonNull Call call, @NonNull IOException e) {
                            httpResponse.postValue("error");
                            workflow.setStatus(StatusCode.ERROR, "failure to communicate");
                            workflow.end();
                        }

                        @Override
                        public void onResponse(@NonNull Call call, @NonNull Response response) {
                            try (ResponseBody body = response.body()) {
                                int responseCode = response.code();
                                httpResponse.postValue("" + responseCode);
                                workflow.end();
                            }
                        }
                    });
        }
    }

    private void makeRawCall(String url) {
        Call call = okHttpClient.newCall(new Request.Builder().url(url).get().build());
        middleware.d("HTTP", "HTTP CALL STARTED " + url);
        middleware.d("HTTP", "HEADERS : " + call.request().headers().toString());
        call.enqueue(
                new Callback() {
                    @Override
                    public void onFailure(@NonNull Call call, @NonNull IOException e) {
                        httpResponse.postValue("error");
                    }

                    @Override
                    public void onResponse(@NonNull Call call, @NonNull Response response) {
                        try (ResponseBody body = response.body()) {
                            int responseCode = response.code();
                            middleware.d("Response", body.toString());
                            httpResponse.postValue("" + responseCode);
                        }
                    }
                });

    }

    public static void emitEvent(Middleware middleware, String eventDomain, String eventName) {
        EventEmitterProvider eventEmitterProvider =
                SdkEventEmitterProvider.create(
                        ((OpenTelemetrySdk) middleware.getOpenTelemetry()).getSdkLoggerProvider());
        EventEmitter eventEmitter =
                eventEmitterProvider
                        .eventEmitterBuilder("test")
                        .setEventDomain(eventDomain)
                        .build();
        eventEmitter.emit(eventName, Attributes.empty());
    }
}
