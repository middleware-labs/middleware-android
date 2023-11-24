package io.middleware.android.sdk.core;

import static io.middleware.android.sdk.utils.Constants.COMPONENT_APPSTART;
import static io.middleware.android.sdk.utils.Constants.COMPONENT_ERROR;
import static io.middleware.android.sdk.utils.Constants.COMPONENT_KEY;
import static io.middleware.android.sdk.utils.Constants.COMPONENT_UI;
import static io.middleware.android.sdk.utils.Constants.EVENT_TYPE;
import static io.opentelemetry.android.RumConstants.APP_START_SPAN_NAME;
import static io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor.constant;

import android.app.Application;
import android.os.Looper;

import java.time.Duration;
import java.util.function.Function;

import io.middleware.android.sdk.core.models.ScreenAttributesAppender;
import io.middleware.android.sdk.exporters.MiddlewareLogsExporter;
import io.middleware.android.sdk.exporters.MiddlewareMetricsExporter;
import io.middleware.android.sdk.exporters.MiddlewareSpanExporter;
import io.middleware.android.sdk.extractors.CrashComponentExtractor;
import io.middleware.android.sdk.extractors.MiddlewareScreenNameExtractor;
import io.middleware.android.sdk.interfaces.IRumSetup;
import io.opentelemetry.android.GlobalAttributesSpanAppender;
import io.opentelemetry.android.OpenTelemetryRum;
import io.opentelemetry.android.OpenTelemetryRumBuilder;
import io.opentelemetry.android.RuntimeDetailsExtractor;
import io.opentelemetry.android.instrumentation.activity.VisibleScreenTracker;
import io.opentelemetry.android.instrumentation.anr.AnrDetector;
import io.opentelemetry.android.instrumentation.crash.CrashReporter;
import io.opentelemetry.android.instrumentation.lifecycle.AndroidLifecycleInstrumentation;
import io.opentelemetry.android.instrumentation.network.CurrentNetworkProvider;
import io.opentelemetry.android.instrumentation.network.NetworkAttributesSpanAppender;
import io.opentelemetry.android.instrumentation.network.NetworkChangeMonitor;
import io.opentelemetry.android.instrumentation.slowrendering.SlowRenderingDetector;
import io.opentelemetry.android.instrumentation.startup.AppStartupTimer;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.exporter.logging.LoggingSpanExporter;
import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter;
import io.opentelemetry.exporter.otlp.http.metrics.OtlpHttpMetricExporter;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;

public class RumSetup implements IRumSetup {
    private final OpenTelemetryRumBuilder openTelemetryRumBuilder;

    public RumSetup(Application application) {
        openTelemetryRumBuilder = OpenTelemetryRum.builder(application);
    }

    @Override
    public void setMetrics(String baseEndpoint, Resource middlewareResource) {
        openTelemetryRumBuilder.addMeterProviderCustomizer((sdkMeterProviderBuilder, application1) -> {
            sdkMeterProviderBuilder.addResource(middlewareResource);
            sdkMeterProviderBuilder.registerMetricReader(

                    PeriodicMetricReader.create(
                            new MiddlewareMetricsExporter(
                                    OtlpHttpMetricExporter
                                            .builder()
                                            .setEndpoint(baseEndpoint + "/v1/metrics")
                                            .setTimeout(Duration.ofMillis(10000))
                                            .addHeader("Content-Type", "application/json")
                                            .addHeader("Access-Control-Allow-Headers", "*")
                                            .build()
                            )
                    ));
            return sdkMeterProviderBuilder;
        });
    }

    @Override
    public void setTraces(String target, Resource middlewareResource) {
        openTelemetryRumBuilder.addTracerProviderCustomizer((sdkTracerProviderBuilder, application1) -> {
            sdkTracerProviderBuilder.addResource(middlewareResource);
            sdkTracerProviderBuilder.addSpanProcessor(
                    BatchSpanProcessor
                            .builder(
                                    new MiddlewareSpanExporter(
                                            OtlpHttpSpanExporter
                                                    .builder()
                                                    .setEndpoint(target + "/v1/traces")
                                                    .setTimeout(Duration.ofMillis(10000))
                                                    .addHeader("Content-Type", "application/json")
                                                    .addHeader("Access-Control-Allow-Headers", "*")
                                                    .build()
                                    )
                            )
                            .build());

            return sdkTracerProviderBuilder;
        });
    }

    @Override
    public void setLogs(String target, Resource middlewareResource) {
        openTelemetryRumBuilder.addLoggerProviderCustomizer((sdkLoggerProviderBuilder, application1) -> {
            sdkLoggerProviderBuilder.setResource(middlewareResource);
            sdkLoggerProviderBuilder.addLogRecordProcessor(SimpleLogRecordProcessor
                    .create(new MiddlewareLogsExporter(
                                    OtlpHttpLogRecordExporter
                                            .builder()
                                            .setEndpoint(target + "/v1/logs")
                                            .addHeader("Content-Type", "application/json")
                                            .addHeader("Access-Control-Allow-Headers", "*")
                                            .build()
                            )
                    )
            );
            return sdkLoggerProviderBuilder;
        });
    }

    @Override
    public void setLoggingSpanExporter() {
        openTelemetryRumBuilder.addTracerProviderCustomizer((sdkTracerProviderBuilder, application1) -> {
            sdkTracerProviderBuilder.addSpanProcessor(SimpleSpanProcessor.create(LoggingSpanExporter.create()));
            return sdkTracerProviderBuilder;
        });
    }

    @Override
    public void setGlobalAttributes(GlobalAttributesSpanAppender globalAttributesSpanAppender) {
        openTelemetryRumBuilder.addTracerProviderCustomizer(
                (tracerProviderBuilder, app) ->
                        tracerProviderBuilder.addSpanProcessor(globalAttributesSpanAppender));

    }

    @Override
    public void setScreenAttributes(VisibleScreenTracker visibleScreenTracker) {
        openTelemetryRumBuilder.addTracerProviderCustomizer((sdkTracerProviderBuilder, application1) -> {
            ScreenAttributesAppender screenAttributesAppender =
                    new ScreenAttributesAppender(visibleScreenTracker);
            sdkTracerProviderBuilder.addSpanProcessor(screenAttributesAppender);
            return sdkTracerProviderBuilder;
        });
    }


    @Override
    public void setNetworkAttributes(CurrentNetworkProvider currentNetworkProvider) {
        openTelemetryRumBuilder.addTracerProviderCustomizer(
                (tracerProviderBuilder, app) -> {
                    SpanProcessor networkAttributesSpanAppender =
                            NetworkAttributesSpanAppender.create(currentNetworkProvider);
                    return tracerProviderBuilder.addSpanProcessor(networkAttributesSpanAppender);
                });
    }

    @Override
    public void setPropagators() {
        openTelemetryRumBuilder.addPropagatorCustomizer(textMapPropagator -> TextMapPropagator.composite(textMapPropagator,
                io.opentelemetry.extension.trace.propagation.B3Propagator.injectingSingleHeader()
        ));
    }

    @Override
    public void setAnrDetector(Looper mainLooper) {
        openTelemetryRumBuilder.addInstrumentation(
                instrumentedApplication -> {
                    AnrDetector.builder()
                            .addAttributesExtractor(constant(COMPONENT_KEY, COMPONENT_ERROR))
                            .addAttributesExtractor(constant(EVENT_TYPE, COMPONENT_ERROR))
                            .setMainLooper(mainLooper)
                            .build()
                            .installOn(instrumentedApplication);
                });
    }

    @Override
    public void setNetworkMonitor(CurrentNetworkProvider currentNetworkProvider) {
        openTelemetryRumBuilder.addInstrumentation(
                instrumentedApplication -> {
                    NetworkChangeMonitor.create(currentNetworkProvider)
                            .installOn(instrumentedApplication);
                });
    }

    @Override
    public void setSlowRenderingDetector(Duration slowRenderingDetectionPollInterval) {
        openTelemetryRumBuilder.addInstrumentation(
                instrumentedApplication -> {
                    SlowRenderingDetector.builder()
                            .setSlowRenderingDetectionPollInterval(
                                    slowRenderingDetectionPollInterval)
                            .build()
                            .installOn(instrumentedApplication);
                });
    }

    @Override
    public void setCrashReporter() {
        openTelemetryRumBuilder.addInstrumentation(
                instrumentedApplication -> {
                    CrashReporter.builder()
                            .addAttributesExtractor(
                                    RuntimeDetailsExtractor.create(
                                            instrumentedApplication
                                                    .getApplication()
                                                    .getApplicationContext()))
                            .addAttributesExtractor(new CrashComponentExtractor())
                            .build()
                            .installOn(instrumentedApplication);
                });
    }

    @Override
    public void setLifecycleInstrumentations(VisibleScreenTracker visibleScreenTracker, AppStartupTimer appStartupTimer) {
        openTelemetryRumBuilder.addInstrumentation(
                instrumentedApp -> {
                    Function<Tracer, Tracer> tracerCustomizer =
                            tracer ->
                                    (Tracer)
                                            spanName -> {
                                                String component =
                                                        spanName.equals(APP_START_SPAN_NAME)
                                                                ? COMPONENT_APPSTART
                                                                : COMPONENT_UI;
                                                return tracer.spanBuilder(spanName)
                                                        .setAttribute(EVENT_TYPE, "app_activity")
                                                        .setAttribute(COMPONENT_KEY, component);
                                            };
                    AndroidLifecycleInstrumentation instrumentation =
                            AndroidLifecycleInstrumentation.builder()
                                    .setVisibleScreenTracker(visibleScreenTracker)
                                    .setStartupTimer(appStartupTimer)
                                    .setTracerCustomizer(tracerCustomizer)
                                    .setScreenNameExtractor(MiddlewareScreenNameExtractor.INSTANCE)
                                    .build();
                    instrumentation.installOn(instrumentedApp);
                });
    }

    @Override
    public void mergeResource(Resource middlewareResource) {
        openTelemetryRumBuilder.mergeResource(middlewareResource);
    }

    @Override
    public Attributes modifyEventAttributes(String eventName, Attributes attributes) {
        Attributes newAttributes = attributes;
        if (eventName.toLowerCase().contains("click")) {
            newAttributes = newAttributes.toBuilder()
                    .put("event.type", "click")
                    .build();
        }
        return newAttributes;
    }

    @Override
    public OpenTelemetryRum build() {
        return openTelemetryRumBuilder.build();
    }
}
