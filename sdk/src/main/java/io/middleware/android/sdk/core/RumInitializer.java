package io.middleware.android.sdk.core;

import static java.util.Objects.requireNonNull;
import static io.middleware.android.sdk.utils.Constants.APP_NAME_KEY;
import static io.opentelemetry.semconv.ResourceAttributes.BROWSER_MOBILE;
import static io.opentelemetry.semconv.ResourceAttributes.DEPLOYMENT_ENVIRONMENT;
import static io.opentelemetry.semconv.ResourceAttributes.SERVICE_NAME;

import android.annotation.SuppressLint;
import android.app.Application;
import android.os.Build;
import android.os.Looper;
import android.util.Log;

import com.google.gson.Gson;

import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Calendar;
import java.util.Objects;
import java.util.TimeZone;
import java.util.function.Function;

import io.middleware.android.sdk.Middleware;
import io.middleware.android.sdk.builders.MiddlewareBuilder;
import io.middleware.android.sdk.core.models.RumData;
import io.middleware.android.sdk.core.services.RumServiceManager;
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

public class RumInitializer implements IRum {
    private final MiddlewareBuilder builder;
    private final Application application;
    private final AppStartupTimer appStartupTimer;

    public RumInitializer(MiddlewareBuilder builder, Application application, AppStartupTimer appStartupTimer) {
        this.builder = builder;
        this.application = application;
        this.appStartupTimer = appStartupTimer;
    }

    @Override
    public Middleware initialize(Function<Application, CurrentNetworkProvider> currentNetworkProviderFactory, Looper mainLooper) {
        VisibleScreenTracker visibleScreenTracker = new VisibleScreenTracker();
        GlobalAttributesSpanAppender globalAttributesSpanAppender = GlobalAttributesSpanAppender.create(builder.globalAttributes);
        final CurrentNetworkProvider currentNetworkProvider = currentNetworkProviderFactory.apply(application);
        final RumSetup rumSetup = new RumSetup(application);
        Resource middlewareResource = createMiddlewareResource();
        rumSetup.mergeResource(middlewareResource);
        rumSetup.setGlobalAttributes(globalAttributesSpanAppender);
        rumSetup.setNetworkAttributes(currentNetworkProvider);
        rumSetup.setScreenAttributes(visibleScreenTracker);
        rumSetup.setMetrics(builder.target, middlewareResource);
        rumSetup.setTraces(builder.target, middlewareResource);
        rumSetup.setLogs(builder.target, middlewareResource);
        if (builder.isDebugEnabled()) {
            rumSetup.setLoggingSpanExporter();
        }
        rumSetup.setPropagators();

        if (builder.isSlowRenderingDetectionEnabled()) {
            rumSetup.setSlowRenderingDetector(builder.slowRenderingDetectionPollInterval);
        }

        if (builder.isNetworkMonitorEnabled()) {
            rumSetup.setNetworkMonitor(currentNetworkProvider);
        }

        if (builder.isAnrDetectionEnabled()) {
            rumSetup.setAnrDetector(mainLooper);
        }

        if (builder.isCrashReportingEnabled()) {
            rumSetup.setCrashReporter();
        }
        rumSetup.setLifecycleInstrumentations(visibleScreenTracker, appStartupTimer);
        final OpenTelemetryRum openTelemetryRum = rumSetup.build();
        return new Middleware(openTelemetryRum, rumSetup, globalAttributesSpanAppender);
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

        final Rum rum = new Rum();
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