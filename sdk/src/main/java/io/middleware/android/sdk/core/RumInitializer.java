package io.middleware.android.sdk.core;

import static java.util.Objects.requireNonNull;
import static io.middleware.android.sdk.utils.Constants.APP_NAME_KEY;
import static io.middleware.android.sdk.utils.Constants.BASE_ORIGIN;
import static io.middleware.android.sdk.utils.Constants.LOG_TAG;
import static io.middleware.android.sdk.utils.Constants.RUM_TRACER_NAME;
import static io.opentelemetry.semconv.ResourceAttributes.BROWSER_MOBILE;
import static io.opentelemetry.semconv.ResourceAttributes.DEPLOYMENT_ENVIRONMENT;
import static io.opentelemetry.semconv.ResourceAttributes.SERVICE_NAME;

import android.app.Application;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.gson.Gson;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import io.middleware.android.sdk.Middleware;
import io.middleware.android.sdk.builders.MiddlewareBuilder;
import io.middleware.android.sdk.core.models.InitializationEvents;
import io.middleware.android.sdk.core.models.RumData;
import io.middleware.android.sdk.core.replay.RREvent;
import io.middleware.android.sdk.core.replay.ReplayRecording;
import io.middleware.android.sdk.interfaces.IRum;
import io.opentelemetry.android.GlobalAttributesSpanAppender;
import io.opentelemetry.android.OpenTelemetryRum;
import io.opentelemetry.android.instrumentation.activity.VisibleScreenTracker;
import io.opentelemetry.android.instrumentation.network.CurrentNetworkProvider;
import io.opentelemetry.android.instrumentation.startup.AppStartupTimer;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.common.v1.InstrumentationScope;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.metrics.v1.Gauge;
import io.opentelemetry.proto.metrics.v1.Metric;
import io.opentelemetry.proto.metrics.v1.MetricsData;
import io.opentelemetry.proto.metrics.v1.NumberDataPoint;
import io.opentelemetry.proto.metrics.v1.ResourceMetrics;
import io.opentelemetry.proto.metrics.v1.ScopeMetrics;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.resources.ResourceBuilder;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class RumInitializer implements IRum {
    private final MiddlewareBuilder builder;
    private final Application application;
    private final AppStartupTimer appStartupTimer;
    private final InitializationEvents initializerEvent;

    public RumInitializer(MiddlewareBuilder builder, Application application, AppStartupTimer appStartupTimer) {
        this.builder = builder;
        this.application = application;
        this.appStartupTimer = appStartupTimer;
        this.initializerEvent = new InitializationEvents(appStartupTimer);
    }

    @Override
    public Middleware initialize(Function<Application, CurrentNetworkProvider> currentNetworkProviderFactory, Looper mainLooper) {
        initializerEvent.begin();
        VisibleScreenTracker visibleScreenTracker = new VisibleScreenTracker();
        GlobalAttributesSpanAppender globalAttributesSpanAppender = GlobalAttributesSpanAppender.create(builder.globalAttributes);
        final CurrentNetworkProvider currentNetworkProvider = currentNetworkProviderFactory.apply(application);
        final RumSetup rumSetup = new RumSetup(application);
        Resource middlewareResource = createMiddlewareResource();
        rumSetup.mergeResource(middlewareResource);
        initializerEvent.emit("resourceInitialized");
        rumSetup.setGlobalAttributes(globalAttributesSpanAppender);
        initializerEvent.emit("globalAttributesInitialized");
        rumSetup.setNetworkAttributes(currentNetworkProvider);
        initializerEvent.emit("networkAttributesInitialized");
        rumSetup.setScreenAttributes(visibleScreenTracker);
        initializerEvent.emit("screenAttributesInitialized");
        rumSetup.setMetrics(builder.target, middlewareResource);
        initializerEvent.emit("metricsInitialized");
        rumSetup.setTraces(builder.target, middlewareResource);
        initializerEvent.emit("tracesInitialized");
        rumSetup.setLogs(builder.target, middlewareResource);
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
            rumSetup.setNetworkMonitor(currentNetworkProvider);
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

        if (builder.isActivityLifecycleEnabled()) {
            rumSetup.setLifecycleInstrumentations(visibleScreenTracker, appStartupTimer);
            initializerEvent.emit("activityLifecycleInitialized");
        }

        final OpenTelemetryRum openTelemetryRum = rumSetup.build();
        final Meter build = openTelemetryRum.getOpenTelemetry()
                .getMeterProvider()
                .meterBuilder("mw-counter")
                .build();
        final LongCounter userStatus = build
                .counterBuilder("user.status")
                .setDescription("User Status")
                .setUnit("")
                .build();
        userStatus.add(1, createMiddlewareResource()
                .getAttributes().toBuilder()
                .put("session.id", openTelemetryRum.getRumSessionId()).build()
        );
        initializerEvent.recordInitializationSpans(
                builder.getConfigFlags(),
                openTelemetryRum.getOpenTelemetry().getTracer(RUM_TRACER_NAME));
        return new Middleware(openTelemetryRum, rumSetup, globalAttributesSpanAppender);
    }

    public void sendRumEvent(ReplayRecording replayRecording, Attributes attributes) {
        attributes = attributes.toBuilder().put("mw.account_key", builder.rumAccessToken).build();
        ResourceMetrics resourceMetrics = new ResourceMetrics.Builder().resource(
                        new io.opentelemetry.proto.resource.v1.Resource.Builder()
                                .attributes(attributesToKeyValueIterable(attributes)).build()
                )
                .scope_metrics(Collections.singletonList(new ScopeMetrics.Builder()
                        .scope(new InstrumentationScope.Builder().build())
                        .metrics(transformRREvent(requireNonNull(replayRecording.getPayload())))
                        .build()))
                .build();
        MetricsData metricsData = new MetricsData.Builder()
                .resource_metrics(Collections.singletonList(resourceMetrics)).build();
        final RumData rumData = new RumData();
        rumData.setAccessToken(builder.rumAccessToken);
        rumData.setEndpoint(builder.target + "/v1/metrics");

        Log.d(LOG_TAG, new Gson().toJson(metricsData));
        rumData.setPayload(new Gson().toJson(metricsData));

        startRumService(rumData);
    }

    private List<Metric> transformRREvent(List<RREvent> events) {
        final List<Metric> metrics = new ArrayList<>();
        events.forEach(rrEvent -> {
            Metric metric = new Metric.Builder()
                    .name("rum_event")
                    .gauge(new Gauge.Builder()
                            .data_points(Collections.singletonList(new NumberDataPoint.Builder()
                                    .attributes(Arrays.asList(
                                            new KeyValue("type", new AnyValue.Builder()
                                                    .string_value(String.valueOf(rrEvent.getType()))
                                                    .build()),
                                            new KeyValue("timestamp", new AnyValue.Builder()
                                                    .string_value(String.valueOf(rrEvent.getTimestamp()))
                                                    .build()),
                                            new KeyValue("data", new AnyValue.Builder()
                                                    .string_value(new Gson().toJson(rrEvent.getData()))
                                                    .build())
                                    ))
                                    .time_unix_nano(rrEvent.getTimestamp() * 1000000)
                                    .as_double(0.0)
                                    .build())
                            )
                            .build())
                    .build();
            metrics.add(metric);
        });
        return metrics;
    }

    private void startRumService(RumData rumData) {
        Log.d("Middleware", "Starting RUM Service to send rum data");
        postRum(rumData);
    }

    private static List<KeyValue> attributesToKeyValueIterable(Attributes attributes) {
        final List<KeyValue> keyValueList = new ArrayList<>();
        attributes.forEach((key, value) -> {
            keyValueList.add(new KeyValue.Builder().key(String.valueOf(key)).value(new AnyValue.Builder()
                    .string_value(String.valueOf(value)).build()).build());
        });
        return keyValueList;
    }

    private void postRum(RumData rumData) {
        OkHttpClient okHttpClient = new OkHttpClient.Builder().build();
        RequestBody requestBody = RequestBody.Companion.create(rumData.getPayload().getBytes());
        Request request = new Request.Builder()
                .url(rumData.getEndpoint())
                .header("Origin", BASE_ORIGIN)
                .header("MW_API_KEY", rumData.getAccessToken())
                .header("Content-Type", "application/json")
                .post(requestBody)
                .build();
        try {
            okHttpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    e.printStackTrace();
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    if (response.isSuccessful()) {
                        Log.d("RumService", "Video Recorded Successfully" + response.code());
                    }
                    assert response.body() != null;
                    response.body().close();
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
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
        resourceBuilder.put(BROWSER_MOBILE.getKey(), true);
        return resourceBuilder.build();
    }
}