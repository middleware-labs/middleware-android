package io.middleware.android.sdk.core;

import static io.middleware.android.sdk.utils.Constants.RUM_TRACER_NAME;
import static io.middleware.android.sdk.utils.Constants.SESSION_START_TIME;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Looper;

import io.middleware.android.sdk.Middleware;
import io.middleware.android.sdk.builders.MiddlewareBuilder;
import io.middleware.android.sdk.core.models.InitializationEvents;
import io.middleware.android.sdk.interfaces.IRum;
import io.opentelemetry.android.GlobalAttributesSpanAppender;
import io.opentelemetry.android.OpenTelemetryRum;
import io.opentelemetry.android.instrumentation.activity.startup.AppStartupTimer;
import io.opentelemetry.api.common.Attributes;

public class RumInitializer implements IRum {
    private final MiddlewareBuilder builder;
    private final Application application;
    private final AppStartupTimer appStartupTimer;
    private final InitializationEvents initializerEvent;

    public RumInitializer(MiddlewareBuilder builder, Context context, AppStartupTimer appStartupTimer) {
        this.builder = builder;
        if (context instanceof Activity) {
            this.application = ((Activity) context).getApplication();
        } else if (context instanceof Application) {
            this.application = (Application) context;
        } else {
            this.application = (Application) context.getApplicationContext();
        }
        this.appStartupTimer = appStartupTimer;
        this.initializerEvent = new InitializationEvents(appStartupTimer);
    }

    @Override
    public Middleware initialize(Looper mainLooper) {
        initializerEvent.begin();
        long startTimeMs = appStartupTimer.clockNow() / 1_000_000L;
        Attributes globalAttributes = builder.globalAttributes.toBuilder().put(SESSION_START_TIME, String.valueOf(startTimeMs)).build();
        builder.setGlobalAttributes(globalAttributes);
        GlobalAttributesSpanAppender globalAttributesSpanAppender = GlobalAttributesSpanAppender.create(globalAttributes);
        final RumSetup rumSetup = new RumSetup(application, builder);
        initializerEvent.emit("resourceInitialized");
        rumSetup.setGlobalAttributes(globalAttributesSpanAppender);
        initializerEvent.emit("globalAttributesInitialized");
        rumSetup.setTraces();
        initializerEvent.emit("tracesInitialized");
        rumSetup.setLogs();
        initializerEvent.emit("logsInitialized");
        if (builder.isDebugEnabled()) {
            rumSetup.setLoggingSpanExporter();
            initializerEvent.emit("loggingSpanExporterInitialized");
        }
        rumSetup.setPropagators();
        initializerEvent.emit("propagatorsInitialized");

        if (builder.isSlowRenderingDetectionEnabled()) {
            rumSetup.setSlowRenderingDetector(builder.slowRenderingDetectionPollInterval);
            initializerEvent.emit("slowRenderingInitialized");
        }

        if (builder.isNetworkMonitorEnabled()) {
            rumSetup.setNetworkMonitor();
            initializerEvent.emit("networkChangeInitialized");
        }

        if (builder.isAnrDetectionEnabled()) {
            rumSetup.setAnrDetector(mainLooper);
            initializerEvent.emit("anrDetectionInitialized");
        }

        if (builder.isCrashReportingEnabled()) {
            rumSetup.setCrashReporter();
            initializerEvent.emit("crashReportingInitialized");
        }

        if (builder.isUIInstrumentationEnabled()) {
            rumSetup.setUIInstrumentation();
            initializerEvent.emit("uiInstrumentationInitialized");
        }
        final OpenTelemetryRum openTelemetryRum = rumSetup.build();
        rumSetup.bindSessionProvider(openTelemetryRum);
        initializerEvent.recordInitializationSpans(
                builder.getConfigFlags(),
                openTelemetryRum.getOpenTelemetry().getTracer(RUM_TRACER_NAME));
        return new Middleware(openTelemetryRum, rumSetup, globalAttributesSpanAppender);
    }

}
