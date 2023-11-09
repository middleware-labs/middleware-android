package io.middleware.android.sdk;

import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static io.middleware.android.sdk.utils.Constants.COMPONENT_ERROR;
import static io.middleware.android.sdk.utils.Constants.COMPONENT_KEY;
import static io.opentelemetry.api.common.AttributeKey.longKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;

import android.app.Application;
import android.content.Context;
import android.webkit.WebView;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import io.middleware.android.sdk.builders.MiddlewareBuilder;
import io.middleware.android.sdk.core.RumSetup;
import io.middleware.android.sdk.core.models.NativeRumSessionId;
import io.opentelemetry.android.GlobalAttributesSpanAppender;
import io.opentelemetry.android.OpenTelemetryRum;
import io.opentelemetry.android.instrumentation.network.CurrentNetworkProvider;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;

@ExtendWith(MockitoExtension.class)
public class MiddlewareTest {

    private Tracer tracer;

    @RegisterExtension
    final OpenTelemetryExtension openTelemetryExtension = OpenTelemetryExtension.create();
    @Mock
    private OpenTelemetryRum openTelemetryRum;
    @Mock
    private RumSetup rumSetup;
    @Mock
    private GlobalAttributesSpanAppender globalAttributes;
    private MiddlewareBuilder middlewareBuilder;


    @BeforeEach
    public void setup() {
        tracer = openTelemetryExtension.getOpenTelemetry().getTracer("testTracer");
        middlewareBuilder =
                new MiddlewareBuilder()
                        .setDeploymentEnvironment("TEST")
                        .setServiceName("test-middleware-sdk")
                        .setProjectName("test-middleware-sdk")
                        .setTarget("http://backend")
                        .setRumAccessToken("abracadabra")
                        .disableAnrDetection();
        Middleware.resetSingletonForTest();
    }

    @Test
    void initialization_onlyOnce() {
        Application application = mock(Application.class, RETURNS_DEEP_STUBS);

        CurrentNetworkProvider currentNetworkProvider =
                mock(CurrentNetworkProvider.class, RETURNS_DEEP_STUBS);
        Context context = mock(Context.class);

        when(application.getApplicationContext()).thenReturn(context);
        Middleware singleton =
                Middleware.initialize(middlewareBuilder, application, app -> currentNetworkProvider);
        Middleware sameInstance = middlewareBuilder.build(application);

        assertSame(singleton, sameInstance);
    }

    @Test
    void getInstance_NoOp() {
        Middleware instance = Middleware.getInstance();
        assertTrue(instance instanceof NoOpMiddleware);
    }

    @Test
    void getInstance() {
        Application application = mock(Application.class, RETURNS_DEEP_STUBS);
        CurrentNetworkProvider currentNetworkProvider =
                mock(CurrentNetworkProvider.class, RETURNS_DEEP_STUBS);
        Context context = mock(Context.class);

        when(application.getApplicationContext()).thenReturn(context);

        Middleware singleton =
                Middleware.initialize(middlewareBuilder, application, app -> currentNetworkProvider);
        assertSame(singleton, Middleware.getInstance());
    }

    @Test
    void addEvent() {
        when(openTelemetryRum.getOpenTelemetry()).thenReturn(openTelemetryExtension.getOpenTelemetry());

        Middleware middleware = new Middleware(openTelemetryRum, null, globalAttributes);

        Attributes attributes = Attributes.of(stringKey("one"), "1", longKey("two"), 2L);
        middleware.addEvent("foo", attributes);

        List<SpanData> spans = openTelemetryExtension.getSpans();
        assertEquals(1, spans.size());
        assertEquals("foo", spans.get(0).getName());
        assertEquals(attributes.asMap(), spans.get(0).getAttributes().asMap());
    }


    @Test
    void addException() {
        InMemorySpanExporter testExporter = InMemorySpanExporter.create();
        OpenTelemetrySdk testSdk = OpenTelemetrySdk.builder()
                .setTracerProvider(
                        SdkTracerProvider.builder()
                                .addSpanProcessor(SimpleSpanProcessor.create(testExporter))
                                .build())
                .build();

        when(openTelemetryRum.getOpenTelemetry()).thenReturn(testSdk);

        Middleware middleware = new Middleware(openTelemetryRum, rumSetup, globalAttributes);

        NullPointerException exception = new NullPointerException("Oops");
        Attributes attributes = Attributes.of(stringKey("one"), "1", longKey("two"), 2L);
        middleware.addException(exception, attributes);

        List<SpanData> spans = testExporter.getFinishedSpanItems();
        assertEquals(1, spans.size());

        assertThat(spans.get(0))
                .hasName("NullPointerException")
                .hasAttributes(
                        attributes.toBuilder()
                                .put(COMPONENT_KEY, COMPONENT_ERROR)
                                .build())
                .hasException(exception);
    }

    @Test
    void integrateWithBrowserRum() {
        Application application = mock(Application.class, RETURNS_DEEP_STUBS);
        CurrentNetworkProvider currentNetworkProvider =
                mock(CurrentNetworkProvider.class, RETURNS_DEEP_STUBS);
        Context context = mock(Context.class);
        WebView webView = mock(WebView.class);

        when(application.getApplicationContext()).thenReturn(context);

        Middleware middleware =
                Middleware.initialize(middlewareBuilder, application, app -> currentNetworkProvider);
        middleware.integrateWithBrowserRum(webView);

        verify(webView)
                .addJavascriptInterface(isA(NativeRumSessionId.class), eq("MiddlewareNative"));
    }
}