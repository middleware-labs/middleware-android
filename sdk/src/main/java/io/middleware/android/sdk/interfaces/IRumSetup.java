package io.middleware.android.sdk.interfaces;

import android.os.Looper;

import java.time.Duration;

import io.opentelemetry.android.GlobalAttributesSpanAppender;
import io.opentelemetry.android.OpenTelemetryRum;
import io.opentelemetry.android.instrumentation.activity.VisibleScreenTracker;
import io.opentelemetry.android.instrumentation.network.CurrentNetworkProvider;
import io.opentelemetry.android.instrumentation.startup.AppStartupTimer;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.resources.Resource;

public interface IRumSetup {
    void setMetrics(String baseEndpoint, Resource middlewareResource);

    void setTraces(String target, Resource middlewareResource);

    void setLogs(String target, Resource middlewareResource);

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

    void mergeResource(Resource middlewareResource);

    Attributes modifyEventAttributes(String eventName, Attributes attributes);

    OpenTelemetryRum build();
}
