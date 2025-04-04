package io.middleware.android.sdk.core;

import static io.middleware.android.sdk.utils.Constants.BASE_ORIGIN;
import static io.middleware.android.sdk.utils.Constants.COMPONENT_ERROR;
import static io.middleware.android.sdk.utils.Constants.COMPONENT_KEY;
import static io.middleware.android.sdk.utils.Constants.EVENT_TYPE;
import static io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor.constant;

import android.app.Application;
import android.os.Looper;

import java.time.Duration;
import java.util.Objects;

import io.middleware.android.sdk.core.instrumentations.crash.CrashAttributesExtractor;
import io.middleware.android.sdk.core.instrumentations.crash.CrashInstrumentation;
import io.middleware.android.sdk.exporters.MiddlewareLogsExporter;
import io.middleware.android.sdk.exporters.MiddlewareMetricsExporter;
import io.middleware.android.sdk.exporters.MiddlewareSpanExporter;
import io.middleware.android.sdk.interfaces.IRumSetup;
import io.opentelemetry.android.GlobalAttributesSpanAppender;
import io.opentelemetry.android.OpenTelemetryRum;
import io.opentelemetry.android.OpenTelemetryRumBuilder;
import io.opentelemetry.android.config.OtelRumConfig;
import io.opentelemetry.android.instrumentation.anr.AnrInstrumentation;
import io.opentelemetry.android.instrumentation.network.NetworkChangeInstrumentation;
import io.opentelemetry.android.instrumentation.slowrendering.SlowRenderingInstrumentation;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.exporter.logging.LoggingSpanExporter;
import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;

public class RumSetup implements IRumSetup {
    private final OpenTelemetryRumBuilder openTelemetryRumBuilder;
    private MiddlewareSpanExporter middlewareSpanExporter;
    private MiddlewareLogsExporter middlewareLogsExporter;
    private MiddlewareMetricsExporter middlewareMetricsExporter;

    public RumSetup(Application application) {
        final OtelRumConfig otelRumConfig = new OtelRumConfig();
        otelRumConfig.shouldIncludeNetworkAttributes();
        otelRumConfig.shouldDiscoverInstrumentations();
        otelRumConfig.shouldIncludeScreenAttributes();
        otelRumConfig.shouldGenerateSdkInitializationEvents();
        openTelemetryRumBuilder = OpenTelemetryRum.builder(application, otelRumConfig);
        openTelemetryRumBuilder.addInstrumentation(new SessionInstrumentation());
    }

    @Override
    public void setTraces(String target, Resource middlewareResource) {
        this.middlewareSpanExporter = new MiddlewareSpanExporter(
                OtlpHttpSpanExporter
                        .builder()
                        .setEndpoint(target + "/v1/traces")
                        .setTimeout(Duration.ofMillis(10000))
                        .addHeader("Authorization", Objects.requireNonNull(middlewareResource
                                .getAttribute(AttributeKey.stringKey("mw.account_key"))))
                        .addHeader("Origin", BASE_ORIGIN)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Access-Control-Allow-Headers", "*")
                        .build()
        );
        openTelemetryRumBuilder.addTracerProviderCustomizer((sdkTracerProviderBuilder, application1) -> {
            sdkTracerProviderBuilder.addResource(middlewareResource);
            sdkTracerProviderBuilder.addSpanProcessor(
                    BatchSpanProcessor
                            .builder(middlewareSpanExporter)
                            .build());

            return sdkTracerProviderBuilder;
        });
    }

    @Override
    public MiddlewareSpanExporter getSpanExporter() {
        return middlewareSpanExporter;
    }

    @Override
    public MiddlewareMetricsExporter getMetricsExporter() {
        return middlewareMetricsExporter;
    }

    @Override
    public MiddlewareLogsExporter getLogsExporter() {
        return middlewareLogsExporter;
    }

    @Override
    public void setLogs(String target, Resource middlewareResource) {
        this.middlewareLogsExporter = new MiddlewareLogsExporter(
                OtlpHttpLogRecordExporter
                        .builder()
                        .setEndpoint(target + "/v1/logs")
                        .addHeader("Authorization", Objects.requireNonNull(middlewareResource
                                .getAttribute(AttributeKey.stringKey("mw.account_key"))))
                        .addHeader("Origin", BASE_ORIGIN)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Access-Control-Allow-Headers", "*")
                        .build()
        );
        openTelemetryRumBuilder.addLoggerProviderCustomizer((sdkLoggerProviderBuilder, application1) -> {
            sdkLoggerProviderBuilder.setResource(middlewareResource);
            sdkLoggerProviderBuilder.addLogRecordProcessor(SimpleLogRecordProcessor
                    .create(middlewareLogsExporter)
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
    public void setPropagators() {
        openTelemetryRumBuilder.addPropagatorCustomizer(textMapPropagator -> TextMapPropagator.composite(textMapPropagator,
                io.opentelemetry.extension.trace.propagation.B3Propagator.injectingSingleHeader()
        ));
    }

    @Override
    public void setAnrDetector(Looper mainLooper) {
        AnrInstrumentation anrInstrumentation = new AnrInstrumentation();
        anrInstrumentation.addAttributesExtractor(constant(COMPONENT_KEY, COMPONENT_ERROR));
        anrInstrumentation.addAttributesExtractor(constant(EVENT_TYPE, COMPONENT_ERROR));
        anrInstrumentation.setMainLooper(mainLooper);
        openTelemetryRumBuilder.addInstrumentation(anrInstrumentation);
    }

    @Override
    public void setNetworkMonitor() {
        openTelemetryRumBuilder.addInstrumentation(new NetworkChangeInstrumentation());
    }

    @Override
    public void setSlowRenderingDetector(Duration slowRenderingDetectionPollInterval) {
        SlowRenderingInstrumentation slowRenderingInstrumentation = new SlowRenderingInstrumentation();
        slowRenderingInstrumentation.setSlowRenderingDetectionPollInterval(slowRenderingDetectionPollInterval);
        openTelemetryRumBuilder.addInstrumentation(slowRenderingInstrumentation);
    }

    @Override
    public void setCrashReporter() {
        CrashInstrumentation crashReporterInstrumentation = new CrashInstrumentation();
        crashReporterInstrumentation.addAttributesExtractor(new CrashAttributesExtractor());
        openTelemetryRumBuilder.addInstrumentation(crashReporterInstrumentation);
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
