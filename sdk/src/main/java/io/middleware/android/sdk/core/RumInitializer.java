package io.middleware.android.sdk.core;

import static java.util.Objects.requireNonNull;
import static io.middleware.android.sdk.utils.Constants.APP_NAME_KEY;
import static io.middleware.android.sdk.utils.Constants.RUM_TRACER_NAME;
import static io.opentelemetry.semconv.ResourceAttributes.BROWSER_MOBILE;
import static io.opentelemetry.semconv.ResourceAttributes.DEPLOYMENT_ENVIRONMENT;
import static io.opentelemetry.semconv.ResourceAttributes.SERVICE_NAME;

import android.annotation.SuppressLint;
import android.app.Application;
import android.os.Build;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.gson.Gson;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Calendar;
import java.util.Objects;
import java.util.TimeZone;
import java.util.function.Function;

import io.middleware.android.sdk.Middleware;
import io.middleware.android.sdk.builders.MiddlewareBuilder;
import io.middleware.android.sdk.core.models.InitializationEvents;
import io.middleware.android.sdk.core.models.RumData;
import io.middleware.android.sdk.core.replay.ReplayRecording;
import io.middleware.android.sdk.interfaces.IRum;
import io.middleware.android.sdk.messages.Rum;
import io.middleware.android.sdk.messages.RumMessage;
import io.middleware.android.sdk.utils.RumUtil;
import io.opentelemetry.android.GlobalAttributesSpanAppender;
import io.opentelemetry.android.OpenTelemetryRum;
import io.opentelemetry.android.instrumentation.activity.VisibleScreenTracker;
import io.opentelemetry.android.instrumentation.network.CurrentNetworkProvider;
import io.opentelemetry.android.instrumentation.startup.AppStartupTimer;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.resources.ResourceBuilder;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
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
        initializerEvent.recordInitializationSpans(
                builder.getConfigFlags(),
                openTelemetryRum.getOpenTelemetry().getTracer(RUM_TRACER_NAME));
        return new Middleware(openTelemetryRum, rumSetup, globalAttributesSpanAppender);
    }

    public void sendRumEvent(ReplayRecording replayRecording, Attributes attributes) {
        RumMessage rumMessage = new RumMessage.Builder()
                .events(replayRecording.getPayload())
                .sessionId(attributes.get(AttributeKey.stringKey("session.id")))
                .version(RumUtil.getVersion(Objects.requireNonNull(application)))
                .build();

        final RumData rumData = new RumData();
        rumData.setAccessToken(builder.rumAccessToken);
        rumData.setEndpoint(builder.target + "/v1/metrics/rum");
        rumData.setPayload(new Gson().toJson(rumMessage));
        startRumService(rumData);
    }

    private void startRumService(RumData rumData) {
        Log.d("Middleware", "Starting RUM Service to send rum data");
        postRum(rumData);
    }

    private void postRum(RumData rumData) {
        OkHttpClient okHttpClient = new OkHttpClient.Builder().build();
        MediaType MEDIA_TYPE_JSON = MediaType.parse("application/json; charset=utf-8");
        RequestBody requestBody = RequestBody.Companion.create(rumData.getPayload(), MEDIA_TYPE_JSON);
        Request request = new Request.Builder()
                .url(rumData.getEndpoint())
                .header("Origin", builder.projectName)
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