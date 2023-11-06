package io.middleware.android.sdk.core;

import static java.util.Objects.requireNonNull;
import static io.middleware.android.sdk.core.Constants.APP_NAME_KEY;
import static io.middleware.android.sdk.core.Constants.COMPONENT_APPSTART;
import static io.middleware.android.sdk.core.Constants.COMPONENT_ERROR;
import static io.middleware.android.sdk.core.Constants.COMPONENT_KEY;
import static io.middleware.android.sdk.core.Constants.COMPONENT_UI;
import static io.opentelemetry.android.RumConstants.APP_START_SPAN_NAME;
import static io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor.constant;
import static io.opentelemetry.semconv.ResourceAttributes.BROWSER_MOBILE;
import static io.opentelemetry.semconv.ResourceAttributes.DEPLOYMENT_ENVIRONMENT;
import static io.opentelemetry.semconv.ResourceAttributes.SERVICE_NAME;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;

import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Calendar;
import java.util.Objects;
import java.util.TimeZone;
import java.util.function.Function;

import io.middleware.android.sdk.Middleware;
import io.middleware.android.sdk.builders.MiddlewareBuilder;
import io.middleware.android.sdk.core.models.RumData;
import io.middleware.android.sdk.core.models.ScreenAttributesAppender;
import io.middleware.android.sdk.extractors.CrashComponentExtractor;
import io.middleware.android.sdk.messages.Rum;
import io.middleware.android.sdk.messages.RumMessage;
import io.middleware.android.sdk.utils.RumUtil;
import io.opentelemetry.android.GlobalAttributesSpanAppender;
import io.opentelemetry.android.OpenTelemetryRum;
import io.opentelemetry.android.OpenTelemetryRumBuilder;
import io.opentelemetry.android.RuntimeDetailsExtractor;
import io.opentelemetry.android.instrumentation.activity.VisibleScreenTracker;
import io.opentelemetry.android.instrumentation.anr.AnrDetector;
import io.opentelemetry.android.instrumentation.crash.CrashReporter;
import io.opentelemetry.android.instrumentation.lifecycle.AndroidLifecycleInstrumentation;
import io.opentelemetry.android.instrumentation.network.CurrentNetworkProvider;
import io.opentelemetry.android.instrumentation.network.NetworkChangeMonitor;
import io.opentelemetry.android.instrumentation.slowrendering.SlowRenderingDetector;
import io.opentelemetry.android.instrumentation.startup.AppStartupTimer;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.exporter.logging.LoggingSpanExporter;
import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter;
import io.opentelemetry.exporter.otlp.http.metrics.OtlpHttpMetricExporter;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.resources.ResourceBuilder;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;

public class RumInitializer {
    private final MiddlewareBuilder builder;
    private final Application application;
    private final InitializationEvents initializationEvents;
    private final AppStartupTimer appStartupTimer;

    public RumInitializer(MiddlewareBuilder builder, Application application, AppStartupTimer appStartupTimer) {
        this.builder = builder;
        this.application = application;
        this.initializationEvents = new InitializationEvents(appStartupTimer);
        this.appStartupTimer = appStartupTimer;
    }

    public Middleware initialize(Function<Application, CurrentNetworkProvider> currentNetworkProviderFactory) {
        VisibleScreenTracker visibleScreenTracker = new VisibleScreenTracker();
//        builder.setSessionId(RumUtil.generateSessionId());
        GlobalAttributesSpanAppender globalAttributesSpanAppender = GlobalAttributesSpanAppender.create(builder.globalAttributes);
        final CurrentNetworkProvider currentNetworkProvider = currentNetworkProviderFactory.apply(application);
        OpenTelemetryRumBuilder otelRumBuilder = OpenTelemetryRum.builder(application);
        otelRumBuilder.mergeResource(createMiddlewareResource());
        // Add span processor that appends global attributes.
        otelRumBuilder.addTracerProviderCustomizer(
                (tracerProviderBuilder, app) ->
                        tracerProviderBuilder.addSpanProcessor(globalAttributesSpanAppender));

        otelRumBuilder.addTracerProviderCustomizer((sdkTracerProviderBuilder, application1) -> {
            sdkTracerProviderBuilder.addResource(createMiddlewareResource());
            Log.d("Middleware", "URL => " + builder.target);
            sdkTracerProviderBuilder.addSpanProcessor(
                    BatchSpanProcessor
                            .builder(OtlpHttpSpanExporter
                                    .builder()
                                    .setEndpoint(builder.target + "/v1/traces")
                                    .setTimeout(Duration.ofMillis(10000))
                                    .addHeader("Content-Type", "application/json")
                                    .addHeader("Access-Control-Allow-Headers", "*")
                                    .build())
                            .build());

            return sdkTracerProviderBuilder;
        });

        otelRumBuilder.addMeterProviderCustomizer((sdkMeterProviderBuilder, application1) -> {
            sdkMeterProviderBuilder.addResource(createMiddlewareResource());
            sdkMeterProviderBuilder.registerMetricReader(
                    PeriodicMetricReader.create(
                            OtlpHttpMetricExporter
                                    .builder()
                                    .setEndpoint(builder.target + "/v1/metrics")
                                    .setTimeout(Duration.ofMillis(10000))
                                    .addHeader("Content-Type", "application/json")
                                    .addHeader("Access-Control-Allow-Headers", "*")
                                    .build()
                    ));
            return sdkMeterProviderBuilder;
        });
        otelRumBuilder.addLoggerProviderCustomizer((sdkLoggerProviderBuilder, application1) -> {
            final String logsEndpoint = builder.target + "/v1/logs";
            Log.d("Middleware", "URL => " + logsEndpoint);
            sdkLoggerProviderBuilder.setResource(createMiddlewareResource());
            sdkLoggerProviderBuilder.addLogRecordProcessor(SimpleLogRecordProcessor
                    .create(OtlpHttpLogRecordExporter
                            .builder()
                            .addHeader("Content-Type", "application/json")
                            .addHeader("Access-Control-Allow-Headers", "*")
                            .setEndpoint(logsEndpoint)
                            .build()));
            return sdkLoggerProviderBuilder;
        });

        otelRumBuilder.addTracerProviderCustomizer((sdkTracerProviderBuilder, application1) -> {
            ScreenAttributesAppender screenAttributesAppender =
                    new ScreenAttributesAppender(visibleScreenTracker);
            sdkTracerProviderBuilder.addSpanProcessor(screenAttributesAppender);
            return sdkTracerProviderBuilder;
        });

        otelRumBuilder.addTracerProviderCustomizer((sdkTracerProviderBuilder, application1) -> {
            sdkTracerProviderBuilder.addSpanProcessor(SimpleSpanProcessor.create(LoggingSpanExporter.create()));
            return sdkTracerProviderBuilder;
        });

        final LogToSpanBridge logToSpanBridge = new LogToSpanBridge();
        otelRumBuilder.addLoggerProviderCustomizer((sdkLoggerProviderBuilder, application1) -> {
            sdkLoggerProviderBuilder.addLogRecordProcessor(logToSpanBridge);
            return sdkLoggerProviderBuilder;
        });
        otelRumBuilder.addInstrumentation(
                instrumentedApplication ->
                        logToSpanBridge.setTracerProvider(
                                instrumentedApplication.getOpenTelemetrySdk().getTracerProvider()));

        if (builder.isCrashReportingEnabled()) {
            installCrashReporter(otelRumBuilder);
        }

        if (builder.isSlowRenderingDetectionEnabled()) {
            installSlowRenderingDetector(otelRumBuilder);
        }

        if (builder.isNetworkMonitorEnabled()) {
            installNetworkMonitor(otelRumBuilder, currentNetworkProvider);
        }

        if (builder.isAnrDetectionEnabled()) {
            installAnrDetector(otelRumBuilder, Looper.getMainLooper());
        }
        installLifecycleInstrumentations(otelRumBuilder, visibleScreenTracker);
        OpenTelemetryRum openTelemetryRum = otelRumBuilder.build();
        return new Middleware(openTelemetryRum, globalAttributesSpanAppender);
    }

    private void installAnrDetector(OpenTelemetryRumBuilder otelRumBuilder, Looper mainLooper) {
        otelRumBuilder.addInstrumentation(
                instrumentedApplication -> {
                    AnrDetector.builder()
                            .addAttributesExtractor(constant(COMPONENT_KEY, COMPONENT_ERROR))
                            .setMainLooper(mainLooper)
                            .build()
                            .installOn(instrumentedApplication);
                });
    }

    private void installSlowRenderingDetector(OpenTelemetryRumBuilder otelRumBuilder) {
        otelRumBuilder.addInstrumentation(
                instrumentedApplication -> {
                    SlowRenderingDetector.builder()
                            .setSlowRenderingDetectionPollInterval(
                                    builder.slowRenderingDetectionPollInterval)
                            .build()
                            .installOn(instrumentedApplication);
                });
    }

    private void installNetworkMonitor(
            OpenTelemetryRumBuilder otelRumBuilder, CurrentNetworkProvider currentNetworkProvider) {
        otelRumBuilder.addInstrumentation(
                instrumentedApplication -> {
                    NetworkChangeMonitor.create(currentNetworkProvider)
                            .installOn(instrumentedApplication);
                });
    }

    private void installCrashReporter(OpenTelemetryRumBuilder otelRumBuilder) {
        otelRumBuilder.addInstrumentation(
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

    private void installLifecycleInstrumentations(
            OpenTelemetryRumBuilder otelRumBuilder, VisibleScreenTracker visibleScreenTracker) {

        otelRumBuilder.addInstrumentation(
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
                    initializationEvents.emit("activityLifecycleCallbacksInitialized");
                });
    }

    private Resource createMiddlewareResource() {
        // applicationName can't be null at this stage
        String applicationName = requireNonNull(builder.projectName);
        ResourceBuilder resourceBuilder = Resource.getDefault().toBuilder().put(APP_NAME_KEY, applicationName);
        if (builder.deploymentEnvironment != null) {
            resourceBuilder.put(DEPLOYMENT_ENVIRONMENT.getKey(), builder.deploymentEnvironment);
        }
        resourceBuilder.put(SERVICE_NAME.getKey(), builder.serviceName);
        resourceBuilder.put("project.name", builder.projectName);
        resourceBuilder.put("mw_agent", true);
        resourceBuilder.put("mw.account_key", builder.rumAccessToken);
        resourceBuilder.put("browser.trace", true);
        resourceBuilder.put("session.id", builder.sessionId);
        resourceBuilder.put(BROWSER_MOBILE.getKey(), true);
        return resourceBuilder.build();
    }

    @SuppressLint("ObsoleteSdkInt")
    public void sendRumEvent(String name, Attributes attributes) {
        String timestamp;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            LocalDateTime utcDateTime = LocalDateTime.now(ZoneId.of("UTC"));
            timestamp = utcDateTime.toString();
        } else {
            @SuppressLint("SimpleDateFormat") SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
            df.setTimeZone(TimeZone.getTimeZone("UTC"));
            Calendar c = Calendar.getInstance();
            timestamp = df.format(c.getTime());
        }
        RumMessage rumMessage = new RumMessage.Builder(name)
                .setAttributes(attributes)
                .timestamp(timestamp)
                .sessionId(attributes.get(AttributeKey.stringKey("session.id")))
                .version(RumUtil.getVersion(Objects.requireNonNull(application)))
                .os("Android")
                .osVersion(Build.VERSION.RELEASE)
                .platform(String.format("%s %s", Build.MANUFACTURER, Build.MODEL))
                .build();

        Rum rum = new Rum();
        rum.setEventData(new RumMessage[]{rumMessage});
        final RumData rumData = new RumData();
        rumData.setAccessToken(builder.rumAccessToken);
        rumData.setEndpoint(builder.target + "/v1/metrics/rum");
        rumData.setPayload(new Gson().toJson(rum));
        startRumService(rumData);
    }

    private void startRumService(RumData rumData) {
        Log.d("Middleware", "Starting RUM Service to send rum data");
        RumServiceManager.startWorker(application.getApplicationContext(), rumData);
    }

}