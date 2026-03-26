package io.middleware.android.sdk.core;

import static java.util.Objects.requireNonNull;
import static io.middleware.android.sdk.utils.Constants.APP_NAME_KEY;
import static io.middleware.android.sdk.utils.Constants.BASE_ORIGIN;
import static io.middleware.android.sdk.utils.Constants.COMPONENT_APPSTART;
import static io.middleware.android.sdk.utils.Constants.COMPONENT_ERROR;
import static io.middleware.android.sdk.utils.Constants.COMPONENT_KEY;
import static io.middleware.android.sdk.utils.Constants.COMPONENT_UI;
import static io.middleware.android.sdk.utils.Constants.EVENT_TYPE;
import static io.opentelemetry.android.RumConstants.APP_START_SPAN_NAME;
import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor.constant;
import static io.opentelemetry.semconv.ResourceAttributes.BROWSER_MOBILE;
import static io.opentelemetry.semconv.ResourceAttributes.DEPLOYMENT_ENVIRONMENT;
import static io.opentelemetry.semconv.ResourceAttributes.SERVICE_NAME;

import android.app.Application;
import android.os.Looper;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Function;

import io.middleware.android.sdk.builders.MiddlewareBuilder;
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
import io.opentelemetry.api.common.AttributeKey;
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
import io.opentelemetry.sdk.resources.ResourceBuilder;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;

public class RumSetup implements IRumSetup {
    private final OpenTelemetryRumBuilder openTelemetryRumBuilder;
    private final MiddlewareBuilder builder;
    private MiddlewareSpanExporter middlewareSpanExporter;
    private MiddlewareLogsExporter middlewareLogsExporter;
    private MiddlewareMetricsExporter middlewareMetricsExporter;
    private Resource resource;

    public RumSetup(Application application, MiddlewareBuilder builder) {
        openTelemetryRumBuilder = OpenTelemetryRum.builder(application);
        this.builder = builder;
        this.resource = createMiddlewareResource();
        openTelemetryRumBuilder.mergeResource(resource);
    }

    @Override
    public void setMetrics() {
        this.middlewareMetricsExporter = new MiddlewareMetricsExporter(
                OtlpHttpMetricExporter
                        .builder()
                        .setEndpoint(builder.target + "/v1/metrics")
                        .setTimeout(Duration.ofMillis(10000))
                        .addHeader("Authorization", builder.rumAccessToken)
                        .addHeader("Origin", BASE_ORIGIN)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Access-Control-Allow-Headers", "*")
                        .build()
        );
        openTelemetryRumBuilder.addMeterProviderCustomizer((sdkMeterProviderBuilder, application1) -> {
            sdkMeterProviderBuilder.addResource(resource);
            sdkMeterProviderBuilder.registerMetricReader(
                    PeriodicMetricReader.create(middlewareMetricsExporter));
            return sdkMeterProviderBuilder;
        });
    }

    @Override
    public void setTraces() {
        this.middlewareSpanExporter = new MiddlewareSpanExporter(
                OtlpHttpSpanExporter
                        .builder()
                        .setEndpoint(builder.target + "/v1/traces")
                        .setTimeout(Duration.ofMillis(10000))
                        .addHeader("Authorization", builder.rumAccessToken)
                        .addHeader("Origin", BASE_ORIGIN)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Access-Control-Allow-Headers", "*")
                        .build()
        );
        openTelemetryRumBuilder.addTracerProviderCustomizer((sdkTracerProviderBuilder, application1) -> {
            sdkTracerProviderBuilder.addResource(resource);
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
    public void setLogs() {
        this.middlewareLogsExporter = new MiddlewareLogsExporter(
                OtlpHttpLogRecordExporter
                        .builder()
                        .setEndpoint(builder.target + "/v1/logs")
                        .addHeader("Authorization", builder.rumAccessToken)
                        .addHeader("Origin", BASE_ORIGIN)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Access-Control-Allow-Headers", "*")
                        .build()
        );
        openTelemetryRumBuilder.addLoggerProviderCustomizer((sdkLoggerProviderBuilder, application1) -> {
            sdkLoggerProviderBuilder.setResource(resource);
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
    public void setResource(Resource resource) {
        this.resource = resource;
    }


    @Override
    public Resource getResource() {
        return resource;
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

    private Resource createMiddlewareResource() {
        if (!builder.resourceAttributes.isEmpty()) {
            return Resource.create(builder.resourceAttributes);
        }
        // applicationName can't be null at this stage
        String applicationName = requireNonNull(builder.projectName);
        ResourceBuilder resourceBuilder = Resource.getDefault().toBuilder().put(APP_NAME_KEY, applicationName);
        if (builder.deploymentEnvironment != null) {
            resourceBuilder.put(DEPLOYMENT_ENVIRONMENT.getKey(), builder.deploymentEnvironment);
        }
        if (builder.globalAttributes != null) {
            builder.globalAttributes.forEach((attributeKey, o) -> {
                resourceBuilder.put(attributeKey.getKey(), String.valueOf(builder.globalAttributes.get(attributeKey)));
            });
        }
        resourceBuilder.put(SERVICE_NAME.getKey(), builder.serviceName);
        resourceBuilder.put("project.name", builder.projectName);
        resourceBuilder.put("mw.rum", true);
        resourceBuilder.removeIf(attributeKey -> attributeKey.equals(stringKey("os.name")));
        resourceBuilder.put("os", "Android");
        resourceBuilder.put("recording", builder.isRecordingEnabled() ? "1" : "0");
        resourceBuilder.put(BROWSER_MOBILE.getKey(), "true");
        return resourceBuilder.build();
    }
}
