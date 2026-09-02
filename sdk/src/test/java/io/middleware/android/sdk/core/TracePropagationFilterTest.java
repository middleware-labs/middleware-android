package io.middleware.android.sdk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.regex.Pattern;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.instrumentation.okhttp.v3_0.OkHttpTelemetry;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

/**
 * Covers the filter through the real {@code OkHttpTelemetry} call factory rather than in
 * isolation, because the behaviour that matters is an ordering one: the filter must run after
 * telemetry's own network interceptor, which is what writes the headers. A unit test of
 * {@code intercept} alone would still pass if that ordering ever inverted.
 */
class TracePropagationFilterTest {

    private MockWebServer server;
    private InMemorySpanExporter spans;
    private OpenTelemetry openTelemetry;

    @BeforeEach
    void setup() throws IOException {
        server = new MockWebServer();
        server.start();
        spans = InMemorySpanExporter.create();
        openTelemetry =
                OpenTelemetrySdk.builder()
                        .setTracerProvider(
                                SdkTracerProvider.builder()
                                        .addSpanProcessor(SimpleSpanProcessor.create(spans))
                                        .build())
                        .setPropagators(
                                ContextPropagators.create(
                                        TextMapPropagator.composite(
                                                W3CTraceContextPropagator.getInstance())))
                        .build();
    }

    @AfterEach
    void teardown() throws IOException {
        server.shutdown();
    }

    private RecordedRequest call(OkHttpClient base) throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200));
        Call.Factory factory = OkHttpTelemetry.builder(openTelemetry).build().newCallFactory(base);
        Request request = new Request.Builder().url(server.url("/orders")).build();
        try (Response response = factory.newCall(request).execute()) {
            assertEquals(200, response.code());
        }
        return server.takeRequest();
    }

    @Test
    void injectsTraceparentWhenNoFilterIsInstalled() throws Exception {
        RecordedRequest recorded = call(new OkHttpClient.Builder().build());

        assertNotNull(
                recorded.getHeader("traceparent"),
                "baseline: telemetry injects traceparent on an unfiltered client");
    }

    @Test
    void stripsTraceHeadersForNonMatchingUrl() throws Exception {
        OkHttpClient base =
                new OkHttpClient.Builder()
                        .addNetworkInterceptor(
                                new TracePropagationFilter(
                                        Collections.singletonList(
                                                Pattern.compile("api\\.example\\.com"))))
                        .build();

        RecordedRequest recorded = call(base);

        assertNull(recorded.getHeader("traceparent"));
        assertNull(recorded.getHeader("tracestate"));
        assertNull(recorded.getHeader("baggage"));
        assertNull(recorded.getHeader("b3"));
        assertEquals(1, spans.getFinishedSpanItems().size(), "the span is still recorded");
    }

    @Test
    void keepsTraceHeadersForMatchingUrl() throws Exception {
        OkHttpClient base =
                new OkHttpClient.Builder()
                        .addNetworkInterceptor(
                                new TracePropagationFilter(
                                        Arrays.asList(
                                                Pattern.compile("api\\.example\\.com"),
                                                Pattern.compile(
                                                        Pattern.quote(server.getHostName())))))
                        .build();

        RecordedRequest recorded = call(base);

        assertNotNull(recorded.getHeader("traceparent"));
    }

    @Test
    void emptyTargetsStripEverything() throws Exception {
        OkHttpClient base =
                new OkHttpClient.Builder()
                        .addNetworkInterceptor(
                                new TracePropagationFilter(Collections.emptyList()))
                        .build();

        RecordedRequest recorded = call(base);

        assertNull(recorded.getHeader("traceparent"));
    }

    @Test
    void targetsMatchAnywhereInTheUrl() throws Exception {
        // A bare host, not anchored and not escaped, is what a host will actually pass.
        OkHttpClient base =
                new OkHttpClient.Builder()
                        .addNetworkInterceptor(
                                new TracePropagationFilter(
                                        Collections.singletonList(Pattern.compile("/orders"))))
                        .build();

        RecordedRequest recorded = call(base);

        assertNotNull(recorded.getHeader("traceparent"));
    }
}
