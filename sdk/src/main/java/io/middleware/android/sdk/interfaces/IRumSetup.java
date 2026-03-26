package io.middleware.android.sdk.interfaces;

import android.os.Looper;

import java.time.Duration;

import io.middleware.android.sdk.exporters.MiddlewareLogsExporter;
import io.middleware.android.sdk.exporters.MiddlewareMetricsExporter;
import io.middleware.android.sdk.exporters.MiddlewareSpanExporter;
import io.opentelemetry.android.GlobalAttributesSpanAppender;
import io.opentelemetry.android.OpenTelemetryRum;
import io.opentelemetry.android.instrumentation.activity.VisibleScreenTracker;
import io.opentelemetry.android.instrumentation.network.CurrentNetworkProvider;
import io.opentelemetry.android.instrumentation.startup.AppStartupTimer;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.resources.Resource;

public interface IRumSetup {
    void setMetrics();

    void setTraces();

    void setLogs();

    void setLoggingSpanExporter();

    void setGlobalAttributes(GlobalAttributesSpanAppender globalAttributesSpanAppender);

    void setScreenAttributes(VisibleScreenTracker visibleScreenTracker);

    void setNetworkAttributes(CurrentNetworkProvider currentNetworkProvider);

    void setPropagators();

    void setAnrDetector(Looper mainLooper);

    void setNetworkMonitor(CurrentNetworkProvider currentNetworkProvider);

    void setSlowRenderingDetector(Duration slowRenderingDetectionPollInterval);

    void setCrashReporter();

    void setLifecycleInstrumentations(VisibleScreenTracker visibleScreenTracker, AppStartupTimer appStartupTimer);

    void setResource(Resource resource);
    Resource getResource();
    MiddlewareSpanExporter getSpanExporter();

    MiddlewareMetricsExporter getMetricsExporter();

    MiddlewareLogsExporter getLogsExporter();

    Attributes modifyEventAttributes(String eventName, Attributes attributes);

    OpenTelemetryRum build();
}
