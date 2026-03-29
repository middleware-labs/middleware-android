package io.middleware.android.sdk.core;

import static java.util.Objects.requireNonNull;
import static io.middleware.android.sdk.utils.Constants.APP_NAME_KEY;
import static io.middleware.android.sdk.utils.Constants.APP_VERSION;
import static io.middleware.android.sdk.utils.Constants.BASE_ORIGIN;
import static io.middleware.android.sdk.utils.Constants.COMPONENT_ERROR;
import static io.middleware.android.sdk.utils.Constants.COMPONENT_KEY;
import static io.middleware.android.sdk.utils.Constants.EVENT_TYPE;
import static io.middleware.android.sdk.utils.Constants.SESSION_START_TIME;
import static io.opentelemetry.android.common.RumConstants.RUM_SDK_VERSION;
import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor.constant;
import static io.opentelemetry.semconv.ServiceAttributes.SERVICE_NAME;
import static io.opentelemetry.semconv.incubating.DeviceIncubatingAttributes.DEVICE_MANUFACTURER;
import static io.opentelemetry.semconv.incubating.DeviceIncubatingAttributes.DEVICE_MODEL_IDENTIFIER;
import static io.opentelemetry.semconv.incubating.DeviceIncubatingAttributes.DEVICE_MODEL_NAME;
import static io.opentelemetry.semconv.incubating.OsIncubatingAttributes.OS_DESCRIPTION;
import static io.opentelemetry.semconv.incubating.OsIncubatingAttributes.OS_NAME;
import static io.opentelemetry.semconv.incubating.OsIncubatingAttributes.OS_TYPE;
import static io.opentelemetry.semconv.incubating.OsIncubatingAttributes.OS_VERSION;

import android.app.Application;
import android.os.Build;
import android.os.Looper;

import com.google.gson.Gson;

import java.time.Duration;

import io.middleware.android.sdk.builders.MiddlewareBuilder;
import io.middleware.android.sdk.core.instrumentations.crash.CrashAttributesExtractor;
import io.middleware.android.sdk.core.instrumentations.crash.CrashInstrumentation;
import io.middleware.android.sdk.exporters.MiddlewareLogsExporter;
import io.middleware.android.sdk.exporters.MiddlewareMetricsExporter;
import io.middleware.android.sdk.exporters.MiddlewareSpanExporter;
import io.middleware.android.sdk.interfaces.IRumSetup;
import io.opentelemetry.android.BuildConfig;
import io.opentelemetry.android.GlobalAttributesSpanAppender;
import io.opentelemetry.android.OpenTelemetryRum;
import io.opentelemetry.android.OpenTelemetryRumBuilder;
import io.opentelemetry.android.config.OtelRumConfig;
import io.opentelemetry.android.instrumentation.anr.AnrInstrumentation;
import io.opentelemetry.android.instrumentation.network.NetworkChangeInstrumentation;
import io.opentelemetry.android.instrumentation.slowrendering.SlowRenderingInstrumentation;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.exporter.logging.LoggingSpanExporter;
import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.resources.ResourceBuilder;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;

public class RumSetup implements IRumSetup {
    private final OpenTelemetryRumBuilder openTelemetryRumBuilder;
    private Resource resource;
    private MiddlewareSpanExporter middlewareSpanExporter;
    private MiddlewareLogsExporter middlewareLogsExporter;
    private MiddlewareMetricsExporter middlewareMetricsExporter;
    private String resourceAttributes;
    private final MiddlewareBuilder builder;

    public RumSetup(Application application, MiddlewareBuilder builder) {
        this.builder = builder;
        this.setResource(createMiddlewareResource());
        final OtelRumConfig otelRumConfig = new OtelRumConfig();
        otelRumConfig.shouldIncludeNetworkAttributes();
        otelRumConfig.shouldDiscoverInstrumentations();
        otelRumConfig.shouldIncludeScreenAttributes();
        otelRumConfig.shouldGenerateSdkInitializationEvents();
        openTelemetryRumBuilder = OpenTelemetryRum.builder(application, otelRumConfig);
        openTelemetryRumBuilder.mergeResource(resource);
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
    public void setResource(Resource resource) {
        this.resource = resource;
        this.resourceAttributes = new Gson().toJson(this.resource.getAttributes().asMap());
    }

    @Override
    public Resource getResource() {
        return resource;
    }

    @Override
    public String getResourceAttributes() {
        return resourceAttributes;
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
        // applicationName can't be null at this stage
        String applicationName = requireNonNull(builder.projectName);
        ResourceBuilder resourceBuilder = Resource.getDefault().toBuilder().put(APP_NAME_KEY, applicationName);
        if (builder.deploymentEnvironment != null) {
            resourceBuilder.put("env", builder.deploymentEnvironment);
        }
        final String appVersion = this.builder.globalAttributes.get(APP_VERSION);
        if (appVersion != null) {
            resourceBuilder.put(APP_VERSION, appVersion);
        }
        final String sessionStartTime = this.builder.globalAttributes.get(SESSION_START_TIME);
        if (sessionStartTime != null) {
            resourceBuilder.put(SESSION_START_TIME, sessionStartTime);
        }
        resourceBuilder.put(SERVICE_NAME, builder.serviceName);
        resourceBuilder.put("project.name", builder.projectName);
        resourceBuilder.put("mw.rum", true);
        resourceBuilder.removeIf(attributeKey -> attributeKey.equals(stringKey("os.name")));
        resourceBuilder.put("os", "Android");
        resourceBuilder.put("recording", builder.isRecordingEnabled() ? "1" : "0");
        resourceBuilder.put("browser.trace", "true");
        resourceBuilder.put(RUM_SDK_VERSION, BuildConfig.OTEL_ANDROID_VERSION);
        resourceBuilder.put(DEVICE_MODEL_NAME, Build.MODEL);
        resourceBuilder.put(DEVICE_MODEL_IDENTIFIER, Build.MODEL);
        resourceBuilder.put(DEVICE_MANUFACTURER, Build.MANUFACTURER);
        resourceBuilder.put(OS_NAME, "Android");
        resourceBuilder.put(OS_TYPE, "linux");
        resourceBuilder.put(OS_VERSION, Build.VERSION.RELEASE);
        resourceBuilder.put(OS_DESCRIPTION, getOSDescription());
        return resourceBuilder.build();
    }

    private static String getOSDescription() {
        StringBuilder osDescriptionBuilder = new StringBuilder();
        return osDescriptionBuilder
                .append("Android Version ")
                .append(Build.VERSION.RELEASE)
                .append(" (Build ")
                .append(Build.ID)
                .append(" API level ")
                .append(Build.VERSION.SDK_INT)
                .append(")")
                .toString();
    }
}
