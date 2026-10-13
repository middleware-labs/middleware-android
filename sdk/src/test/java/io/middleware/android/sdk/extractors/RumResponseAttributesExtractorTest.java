package io.middleware.android.sdk.extractors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import io.middleware.android.sdk.utils.ServerTimingHeaderParser;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.instrumentation.okhttp.v3_0.OkHttpTelemetry;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okio.Buffer;
import okio.BufferedSink;
import okio.GzipSink;
import okio.Okio;

/**
 * Runs the extractor inside the real {@code OkHttpTelemetry} call factory: the extractor sees the
 * raw network-interceptor response, so non-consumption and gzip handling only mean anything
 * end-to-end.
 */
class RumResponseAttributesExtractorTest {

    private static final AttributeKey<String> BODY = AttributeKey.stringKey("http.response.body");
    private static final String TRACE_ID = "0af7651916cd43dd8448eb211c80319c";
    private static final String SPAN_ID = "b7ad6b7169203331";

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
                                ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                        .build();
    }

    @AfterEach
    void teardown() throws IOException {
        server.shutdown();
    }

    /** Body the app received, so tests can assert capture did not consume it. */
    private String appBody;

    private Attributes call(
            MockResponse mockResponse,
            boolean headers,
            boolean bodies,
            Set<String> ignore,
            Request.Builder request)
            throws Exception {
        server.enqueue(mockResponse);
        Call.Factory factory =
                OkHttpTelemetry.builder(openTelemetry)
                        .addAttributesExtractor(
                                new RumResponseAttributesExtractor(
                                        new ServerTimingHeaderParser(), headers, bodies, ignore))
                        .build()
                        .newCallFactory(new OkHttpClient());
        try (Response response =
                     factory.newCall(request.url(server.url("/api")).build()).execute()) {
            appBody = response.body() == null ? null : response.body().string();
        }
        assertEquals(1, spans.getFinishedSpanItems().size());
        SpanData span = spans.getFinishedSpanItems().get(0);
        return span.getAttributes();
    }

    private Attributes call(MockResponse mockResponse, boolean headers, boolean bodies)
            throws Exception {
        return call(mockResponse, headers, bodies, Collections.emptySet(), new Request.Builder());
    }

    private static MockResponse json(String body) {
        return new MockResponse()
                .setHeader("Content-Type", "application/json; charset=utf-8")
                .setHeader("X-Custom-Header", "custom")
                .setBody(body);
    }

    private static long countPrefix(Attributes attributes, String prefix) {
        return attributes.asMap().keySet().stream()
                .filter(k -> k.getKey().startsWith(prefix))
                .count();
    }

    @Test
    void defaultsCaptureNothing() throws Exception {
        Attributes attributes =
                call(json("{\"a\":1}"), false, false, Collections.emptySet(),
                        new Request.Builder().header("Authorization", "Bearer secret"));

        assertEquals(0, countPrefix(attributes, "http.request.header."));
        assertEquals(0, countPrefix(attributes, "http.response.header."));
        assertNull(attributes.get(BODY));
        assertEquals("{\"a\":1}", appBody);
    }

    @Test
    void legacyConstructorCapturesNothing() throws Exception {
        server.enqueue(json("{\"a\":1}"));
        Call.Factory factory =
                OkHttpTelemetry.builder(openTelemetry)
                        .addAttributesExtractor(
                                new RumResponseAttributesExtractor(new ServerTimingHeaderParser()))
                        .build()
                        .newCallFactory(new OkHttpClient());
        try (Response response =
                     factory.newCall(new Request.Builder().url(server.url("/")).build()).execute()) {
            response.body().string();
        }
        Attributes attributes = spans.getFinishedSpanItems().get(0).getAttributes();
        assertEquals(0, countPrefix(attributes, "http.response.header."));
        assertNull(attributes.get(BODY));
    }

    @Test
    void serverTimingCorrelationIsRecordedWithBothFlagsOff() throws Exception {
        Attributes attributes =
                call(json("{}").setHeader(
                                "Server-Timing",
                                "traceparent;desc=\"00-" + TRACE_ID + "-" + SPAN_ID + "-01\""),
                        false, false);

        assertEquals(TRACE_ID, attributes.get(AttributeKey.stringKey("link.traceId")));
        assertEquals(SPAN_ID, attributes.get(AttributeKey.stringKey("link.spanId")));
        assertEquals("http", attributes.get(AttributeKey.stringKey("component")));
        assertEquals(0, countPrefix(attributes, "http.response.header."));
    }

    @Test
    void headersOnly() throws Exception {
        Attributes attributes =
                call(json("{\"a\":1}"), true, false, Collections.emptySet(),
                        new Request.Builder().header("X-Request-Id", "abc"));

        assertEquals("application/json; charset=utf-8",
                attributes.get(AttributeKey.stringKey("http.response.header.content_type")));
        assertEquals("custom",
                attributes.get(AttributeKey.stringKey("http.response.header.x_custom_header")));
        assertEquals("abc",
                attributes.get(AttributeKey.stringKey("http.request.header.x_request_id")));
        // Propagation headers are on the request the network interceptor saw.
        assertTrue(attributes.get(AttributeKey.stringKey("http.request.header.traceparent")) != null);
        assertNull(attributes.get(BODY));
    }

    @Test
    void headerKeysAreLowercasedWithUnderscores() throws Exception {
        Attributes attributes =
                call(json("{}").setHeader("X-Rate-Limit-Remaining", "5"), true, false);

        assertEquals("5", attributes.get(
                AttributeKey.stringKey("http.response.header.x_rate_limit_remaining")));
        assertNull(attributes.get(
                AttributeKey.stringKey("http.response.header.X-Rate-Limit-Remaining")));
        attributes.asMap().keySet().stream()
                .map(AttributeKey::getKey)
                .filter(k -> k.startsWith("http.response.header.")
                        || k.startsWith("http.request.header."))
                .forEach(k -> {
                    String name = k.substring(k.lastIndexOf("header.") + "header.".length());
                    assertEquals(name.toLowerCase(), name, k);
                    assertFalse(name.contains("-"), k);
                });
    }

    @Test
    void repeatedHeadersAreJoinedWithComma() throws Exception {
        MockResponse response = json("{}")
                .addHeader("Set-Cookie", "a=1")
                .addHeader("Set-Cookie", "b=2");

        Attributes attributes = call(response, true, false);

        assertEquals("a=1,b=2",
                attributes.get(AttributeKey.stringKey("http.response.header.set_cookie")));
    }

    @Test
    void ignoreHeadersIsCaseInsensitive() throws Exception {
        Set<String> ignore = new HashSet<>(Arrays.asList("AUTHORIZATION", "x-custom-header"));

        Attributes attributes =
                call(json("{}"), true, false, ignore,
                        new Request.Builder()
                                .header("Authorization", "Bearer secret")
                                .header("X-Kept", "kept"));

        assertNull(attributes.get(AttributeKey.stringKey("http.request.header.authorization")));
        assertNull(attributes.get(AttributeKey.stringKey("http.response.header.x_custom_header")));
        assertEquals("kept", attributes.get(AttributeKey.stringKey("http.request.header.x_kept")));
    }

    @Test
    void accessTokenIsMasked() throws Exception {
        Attributes attributes =
                call(json("{}").setHeader("X-Access-Token", "resp-secret"), true, false,
                        Collections.emptySet(),
                        new Request.Builder().header("X-Access-Token", "req-secret"));

        assertEquals("********",
                attributes.get(AttributeKey.stringKey("http.request.header.x_access_token")));
        assertEquals("********",
                attributes.get(AttributeKey.stringKey("http.response.header.x_access_token")));
    }

    @Test
    void bodiesOnly() throws Exception {
        Attributes attributes = call(json("{\"user\":\"ada\"}"), false, true);

        assertEquals("{\"user\":\"ada\"}", attributes.get(BODY));
        assertEquals(0, countPrefix(attributes, "http.response.header."));
        assertEquals(0, countPrefix(attributes, "http.request.header."));
        assertEquals("{\"user\":\"ada\"}", appBody, "capture must not consume the body");
    }

    @Test
    void capturesPlusJsonAndTextPlain() throws Exception {
        Attributes problem = call(new MockResponse()
                .setHeader("Content-Type", "application/problem+json")
                .setBody("{\"title\":\"bad\"}"), false, true);
        assertEquals("{\"title\":\"bad\"}", problem.get(BODY));

        spans.reset();
        Attributes text = call(new MockResponse()
                .setHeader("Content-Type", " Text/Plain ; charset=ISO-8859-1")
                .setBody("hello"), false, true);
        assertEquals("hello", text.get(BODY));
    }

    @Test
    void skipsOtherContentTypes() throws Exception {
        Attributes html = call(new MockResponse()
                .setHeader("Content-Type", "text/html")
                .setBody("<html></html>"), false, true);
        assertNull(html.get(BODY));
        assertEquals("<html></html>", appBody);

        spans.reset();
        Attributes none = call(new MockResponse().setBody("{\"a\":1}"), false, true);
        assertNull(none.get(BODY), "no Content-Type means no capture");
    }

    @Test
    void skipsEmptyBodies() throws Exception {
        Attributes empty = call(json(""), false, true);
        assertNull(empty.get(BODY));

        spans.reset();
        Attributes blank = call(json("   \n"), false, true);
        assertNull(blank.get(BODY));
    }

    @Test
    void bodyIsCappedAt64KiBWithoutTruncatingWhatTheAppReads() throws Exception {
        StringBuilder sb = new StringBuilder("\"");
        while (sb.length() < 200_000) {
            sb.append("abcdefghij");
        }
        sb.append('"');
        String big = sb.toString();

        Attributes attributes = call(json(big), false, true);

        String captured = attributes.get(BODY);
        assertEquals(RumResponseAttributesExtractor.MAX_BODY_BYTES, captured.length());
        assertEquals(big.substring(0, captured.length()), captured);
        assertEquals(big, appBody);
    }

    @Test
    void gzipBodiesAreInflated() throws Exception {
        String payload = "{\"compressed\":true}";
        Buffer gz = new Buffer();
        try (BufferedSink sink = Okio.buffer(new GzipSink(gz))) {
            sink.writeUtf8(payload);
        }
        MockResponse response = new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setHeader("Content-Encoding", "gzip")
                .setBody(gz);

        // OkHttp adds Accept-Encoding: gzip itself, so it transparently inflates for the app
        // while the network interceptor sees the compressed bytes.
        Attributes attributes = call(response, false, true);

        assertEquals(payload, attributes.get(BODY));
        assertEquals(payload, appBody);
    }

    @Test
    void contentTypeMatching() {
        assertTrue(RumResponseAttributesExtractor.isCapturedBodyType("application/json"));
        assertTrue(RumResponseAttributesExtractor.isCapturedBodyType("APPLICATION/JSON;charset=utf-8"));
        assertTrue(RumResponseAttributesExtractor.isCapturedBodyType("application/vnd.api+json"));
        assertTrue(RumResponseAttributesExtractor.isCapturedBodyType("text/plain"));
        assertFalse(RumResponseAttributesExtractor.isCapturedBodyType("text/html"));
        assertFalse(RumResponseAttributesExtractor.isCapturedBodyType("application/xml"));
        assertFalse(RumResponseAttributesExtractor.isCapturedBodyType("application/octet-stream"));
        assertFalse(RumResponseAttributesExtractor.isCapturedBodyType(""));
        assertFalse(RumResponseAttributesExtractor.isCapturedBodyType(null));
    }

    @Test
    void normalizeHeaderName() {
        assertEquals("content_type", RumResponseAttributesExtractor.normalizeHeaderName("Content-Type"));
        assertEquals("x_access_token", RumResponseAttributesExtractor.normalizeHeaderName("X-ACCESS-TOKEN"));
        assertEquals("traceparent", RumResponseAttributesExtractor.normalizeHeaderName("traceparent"));
    }
}
