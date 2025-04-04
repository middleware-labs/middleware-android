package io.middleware.android.sdk;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

import android.location.Location;
import android.webkit.WebView;

import org.junit.jupiter.api.Test;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import okhttp3.OkHttpClient;

class NoOpMiddlewareTest {
    @Test
    void shouldNotThrowException() {
        NoOpMiddleware instance = NoOpMiddleware.INSTANCE;
        instance.addEvent("foo", Attributes.empty());
        instance.addException(new RuntimeException(), Attributes.empty());

        assertNotNull(instance.getOpenTelemetry());
        assertNotNull(instance.getRumSessionId());
        assertNotNull(instance.getTracer());
        assertNotNull(instance.startWorkflow("foo"));
        OkHttpClient okHttpClient = mock(OkHttpClient.class);
        assertSame(okHttpClient, instance.createRumOkHttpCallFactory(okHttpClient));

        instance.updateGlobalAttributes(attributesBuilder -> {
        });
        instance.setGlobalAttribute(AttributeKey.stringKey("foo"), "bar");
        instance.flushSpans();

        instance.integrateWithBrowserRum(mock(WebView.class));

        Location location = mock(Location.class);
        instance.updateLocation(location);
    }
}