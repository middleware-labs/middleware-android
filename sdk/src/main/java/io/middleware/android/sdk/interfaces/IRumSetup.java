package io.middleware.android.sdk.interfaces;

import android.os.Looper;

import java.time.Duration;

import io.middleware.android.sdk.exporters.MiddlewareLogsExporter;
import io.middleware.android.sdk.exporters.MiddlewareMetricsExporter;
import io.middleware.android.sdk.exporters.MiddlewareSpanExporter;
import io.opentelemetry.android.GlobalAttributesSpanAppender;
import io.opentelemetry.android.OpenTelemetryRum;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.resources.Resource;

public interface IRumSetup {
    void setTraces(String target, Resource middlewareResource);

    void setLogs(String target, Resource middlewareResource);

    void setLoggingSpanExporter();

    void setGlobalAttributes(GlobalAttributesSpanAppender globalAttributesSpanAppender);

    void setPropagators();

    void setAnrDetector(Looper mainLooper);

    void setNetworkMonitor();

    void setSlowRenderingDetector(Duration slowRenderingDetectionPollInterval);

    void setCrashReporter();

    void mergeResource(Resource middlewareResource);

    MiddlewareSpanExporter getSpanExporter();

    MiddlewareMetricsExporter getMetricsExporter();

    MiddlewareLogsExporter getLogsExporter();

    Attributes modifyEventAttributes(String eventName, Attributes attributes);

    OpenTelemetryRum build();
}
