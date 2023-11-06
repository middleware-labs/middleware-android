package io.middleware.android.sdk.interfaces;

import android.location.Location;
import android.webkit.WebView;

import androidx.annotation.Nullable;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import okhttp3.Call;
import okhttp3.OkHttpClient;

public interface IMiddleware {
    Call.Factory createRumOkHttpCallFactory(OkHttpClient client);

    void addEvent(String name, Attributes attributes);

    Span startWorkflow(String workflowName);

    void addException(Throwable throwable);

    void addException(Throwable throwable, Attributes attributes);

    <T> void setGlobalAttribute(AttributeKey<T> key, T value);

    OpenTelemetry getOpenTelemetry();

    void integrateWithBrowserRum(WebView webView);

    void updateLocation(@Nullable Location location);
}
