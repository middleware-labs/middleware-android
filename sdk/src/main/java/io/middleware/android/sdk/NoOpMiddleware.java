package io.middleware.android.sdk;


import android.webkit.WebView;
import io.opentelemetry.android.OpenTelemetryRum;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.Tracer;
import java.util.function.Consumer;
import okhttp3.Call;
import okhttp3.OkHttpClient;

class NoOpMiddleware extends Middleware {
    static final NoOpMiddleware INSTANCE = new NoOpMiddleware();

    // passing null values here is fine, they'll never get used anyway
    @SuppressWarnings("NullAway")
    private NoOpMiddleware() {
        super(OpenTelemetryRum.noop(), null);
    }

    @Override
    public Call.Factory createRumOkHttpCallFactory(OkHttpClient client) {
        return client;
    }

    @Override
    public OpenTelemetry getOpenTelemetry() {
        return OpenTelemetry.noop();
    }

    @Override
    Tracer getTracer() {
        return getOpenTelemetry().getTracer("unused");
    }

    @Override
    public void updateGlobalAttributes(Consumer<AttributesBuilder> attributesUpdater) {
        // no-op
    }

    @Override
    public String getRumSessionId() {
        return "";
    }

    @Override
    public void addRumEvent(String name, Attributes attributes) {
        // no-op
    }

    @Override
    public void integrateWithBrowserRum(WebView webView) {
        // no-op
    }

    @Override
    void flushSpans() {
        // no-op
    }
}