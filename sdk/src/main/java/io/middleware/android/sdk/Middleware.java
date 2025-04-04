package io.middleware.android.sdk;

import static io.middleware.android.sdk.utils.Constants.BASE_ORIGIN;
import static io.middleware.android.sdk.utils.Constants.COMPONENT_ERROR;
import static io.middleware.android.sdk.utils.Constants.COMPONENT_KEY;
import static io.middleware.android.sdk.utils.Constants.EVENT_TYPE;
import static io.middleware.android.sdk.utils.Constants.LOCATION_LATITUDE_KEY;
import static io.middleware.android.sdk.utils.Constants.LOCATION_LONGITUDE_KEY;
import static io.middleware.android.sdk.utils.Constants.LOG_TAG;
import static io.middleware.android.sdk.utils.Constants.RUM_TRACER_NAME;
import static io.middleware.android.sdk.utils.Constants.WORKFLOW_NAME_KEY;

import android.app.Activity;
import android.app.Application;
import android.location.Location;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.webkit.WebView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

import io.middleware.android.sdk.builders.MiddlewareBuilder;
import io.middleware.android.sdk.core.RumInitializer;
import io.middleware.android.sdk.core.RumSetup;
import io.middleware.android.sdk.core.models.NativeRumSessionId;
import io.middleware.android.sdk.core.replay.v2.ActivityCallbacks;
import io.middleware.android.sdk.core.replay.MiddlewareRecorder;
import io.middleware.android.sdk.core.replay.v2.MiddlewareScreenshotManager;
import io.middleware.android.sdk.core.replay.ReplayRecording;
import io.middleware.android.sdk.extractors.RumResponseAttributesExtractor;
import io.middleware.android.sdk.interfaces.IMiddleware;
import io.middleware.android.sdk.utils.ServerTimingHeaderParser;
import io.opentelemetry.android.GlobalAttributesSpanAppender;
import io.opentelemetry.android.OpenTelemetryRum;
import io.opentelemetry.android.instrumentation.activity.startup.AppStartupTimer;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.okhttp.v3_0.OkHttpTelemetry;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import okhttp3.Call;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;

/**
 * Entrypoint for the Middleware OpenTelemetry Instrumentation for Android.
 */
public class Middleware implements IMiddleware {
    private static final AppStartupTimer startupTimer = new AppStartupTimer();
    @Nullable
    private static Middleware INSTANCE;
    private static Logger LOGGER;
    private static MiddlewareScreenshotManager middlewareScreenshotManager;
    private final OpenTelemetryRum openTelemetryRum;

    private final RumSetup middlewareRum;
    private final GlobalAttributesSpanAppender globalAttributes;
    private static RumInitializer rumInitializer;

    static {
        Handler handler = new Handler(Looper.getMainLooper());
        startupTimer.detectBackgroundStart(handler);
    }

    private String nativeSessionId;

    public Middleware(OpenTelemetryRum openTelemetryRum, RumSetup middlewareRum, GlobalAttributesSpanAppender globalAttributes) {
        this.openTelemetryRum = openTelemetryRum;
        this.middlewareRum = middlewareRum;
        this.globalAttributes = globalAttributes;
    }

    /**
     * Creates a new {@link MiddlewareBuilder}, used to set up a {@link Middleware} instance.
     */
    public static MiddlewareBuilder builder() {
        return new MiddlewareBuilder();
    }

    // for testing purposes
    public static Middleware initialize(
            MiddlewareBuilder builder,
            Application application) {
        if (INSTANCE != null) {
            Log.w(LOG_TAG, "Singleton Middleware instance has already been initialized.");
            return INSTANCE;
        }

        rumInitializer = new RumInitializer(builder, application, startupTimer);
        INSTANCE = rumInitializer.initialize(Looper.getMainLooper());
        LOGGER = INSTANCE.getOpenTelemetry().getLogsBridge()
                .loggerBuilder(builder.serviceName)
                .build();
        if (builder.isRecordingEnabled()) {
            Log.d(LOG_TAG, "Session recording enabled, waiting layout to get attached.");
            middlewareScreenshotManager = new MiddlewareScreenshotManager(
                    System.currentTimeMillis(),
                    builder.target,
                    builder.rumAccessToken
            );
            application.registerActivityLifecycleCallbacks(
                    new ActivityCallbacks(middlewareScreenshotManager))
            ;
        }
        Log.i(LOG_TAG, "Middleware RUM monitoring initialized with session ID: " + INSTANCE.getRumSessionId());
        return INSTANCE;
    }

    /**
     * Returns {@code true} if the Middleware RUM library has been successfully initialized.
     */
    public static boolean isInitialized() {
        return INSTANCE != null;
    }

    /**
     * Get the singleton instance of this class.
     */
    public static Middleware getInstance() {
        if (INSTANCE == null) {
            Log.d(LOG_TAG, "Middleware not initialized. Returning no-op implementation");
            return NoOpMiddleware.INSTANCE;
        }
        return INSTANCE;
    }

    /**
     * Returns the MiddlewareRecording enables recording functionality on activity.
     * NOTE: This api is available above Android Nougat version.
     *
     * @return MiddlewareRecorder
     */
    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    public MiddlewareRecorder getRecorder() {
        return new MiddlewareRecorder(this);
    }


    /**
     * @return {@code true} if the recording started successfully.
     */
    public boolean startRecording() {
        if (middlewareScreenshotManager != null) {
            middlewareScreenshotManager.start();
            return true;
        }
        return false;
    }

    /**
     * @return @{code true} if session recording stopped successfully.
     */
    public boolean stopRecording() {
        if (middlewareScreenshotManager != null) {
            middlewareScreenshotManager.stop();
            return true;
        }
        return false;
    }

    /**
     * Wrap the provided {@link OkHttpClient} with OpenTelemetry and RUM instrumentation. Since
     * {@link Call.Factory} is the primary useful interface implemented by the OkHttpClient, this
     * should be a drop-in replacement for any usages of OkHttpClient.
     *
     * @param client The {@link OkHttpClient} to wrap with OpenTelemetry and RUM instrumentation.
     * @return A {@link okhttp3.Call.Factory} implementation.
     */
    @Override
    public Call.Factory createRumOkHttpCallFactory(OkHttpClient client) {
        return createOkHttpTracing().newCallFactory(client);
    }

    private OkHttpTelemetry createOkHttpTracing() {
        Function<SpanNameExtractor<? super Interceptor.Chain>, SpanNameExtractor<? super Interceptor.Chain>> networkSpanNameExtractor =
                defaultExtractor -> (requestChain -> {
                    Request request = requestChain.request();
                    return "HTTP " + request.method() + " " + request.url().encodedPath();
                });
        return OkHttpTelemetry.builder(getOpenTelemetry())
                .setSpanNameExtractor(networkSpanNameExtractor)
                .addAttributesExtractor(new RumResponseAttributesExtractor(new ServerTimingHeaderParser()))
                .build();
    }

    /**
     * Get a handle to the instance of the OpenTelemetry API that this instance is using for
     * instrumentation.
     */
    @Override
    public OpenTelemetry getOpenTelemetry() {
        return openTelemetryRum.getOpenTelemetry();
    }

    public RumSetup getMiddlewareRum() {
        return middlewareRum;
    }


    /**
     * Get the Middleware Session ID associated with this instance of the RUM instrumentation library.
     * Note: this value can change throughout the lifetime of an application instance, so it is
     * recommended that you do not cache this value, but always retrieve it from here when needed.
     */
    public String getRumSessionId() {
        // return native session id if already set.
        if (nativeSessionId != null) {
            return nativeSessionId;
        }
        return openTelemetryRum.getRumSessionId();
    }


    /**
     * Set Native Session Id.
     * Note: If this is set throughout the session the same session id will be used.
     *
     * @param nativeSessionId
     */
    public void setNativeSessionId(final String nativeSessionId) {
        this.nativeSessionId = nativeSessionId;
    }

    //NOTE: This method is not used as of now will be used in future purposes.
    public void addRumEvent(ReplayRecording replayRecording, Attributes attributes) {
        if (isInitialized()) {
            INSTANCE.sendRumEvent(replayRecording, attributes);
        } else {
            Log.d(RUM_TRACER_NAME, "Unable to send rum event setup is not done properly.");
        }
    }

    private void sendRumEvent(ReplayRecording replayRecording, Attributes attributes) {
        Attributes newAttributes = attributes.toBuilder()
                .put("mw.client_origin", BASE_ORIGIN)
                .put("rum_origin", BASE_ORIGIN)
                .put("origin", BASE_ORIGIN)
                .put("session.id", getRumSessionId())
                .build();
        rumInitializer.sendRumEvent(replayRecording, newAttributes);
    }

    /**
     * Add a custom event to RUM monitoring. This can be useful to capture business events, or
     * simply add instrumentation to your application.
     *
     * @param name       The name of the event.
     * @param attributes Any {@link Attributes} to associate with the event.
     */
    @Override
    public void addEvent(String name, Attributes attributes) {
        if (getMiddlewareRum() != null) {
            attributes = middlewareRum.modifyEventAttributes(name, attributes);
        }
        getTracer().spanBuilder(name).setAllAttributes(attributes).startSpan().end();
    }

    /**
     * Start a Span to time a named workflow.
     *
     * @param workflowName The name of the workflow to start.
     * @return A {@link Span} that has been started.
     */
    @Override
    public Span startWorkflow(String workflowName) {

        return getTracer()
                .spanBuilder(workflowName)
                .setAttribute(WORKFLOW_NAME_KEY, workflowName)
                .startSpan();
    }


    /**
     * Add a custom exception to RUM monitoring. This can be useful for tracking custom error
     * handling in your application.
     *
     * <p>This event will be turned into a Span and sent to the RUM ingest along with other,
     * auto-generated spans.
     *
     * @param throwable A {@link Throwable} associated with this event.
     */
    @Override
    public void addException(Throwable throwable) {
        addException(throwable, Attributes.empty());
    }

    /**
     * Add a custom exception to RUM monitoring. This can be useful for tracking custom error
     * handling in your application.
     *
     * <p>This event will be turned into a Span and sent to the RUM ingest along with other,
     * auto-generated spans.
     *
     * @param throwable  A {@link Throwable} associated with this event.
     * @param attributes Any {@link Attributes} to associate with the event.
     */
    @Override
    public void addException(Throwable throwable, Attributes attributes) {
        getTracer()
                .spanBuilder(throwable.getClass().getSimpleName())
                .setAllAttributes(attributes)
                .setAttribute(COMPONENT_KEY, COMPONENT_ERROR)
                .setAttribute(EVENT_TYPE, COMPONENT_ERROR)
                .startSpan()
                .recordException(throwable)
                .end();
    }

    Tracer getTracer() {
        return getOpenTelemetry().getTracer(RUM_TRACER_NAME);
    }

    /**
     * Set an attribute in the global attributes that will be appended to every span and event.
     *
     * <p>Note: If this key is the same as an existing key in the global attributes, it will replace
     * the existing value.
     *
     * <p>If you attempt to set a value to null or use a null key, this call will be ignored.
     *
     * @param key   The {@link AttributeKey} for the attribute.
     * @param value The value of the attribute, which must match the generic type of the key.
     * @param <T>   The generic type of the value.
     */
    public <T> void setGlobalAttribute(AttributeKey<T> key, T value) {
        updateGlobalAttributes(attributesBuilder -> attributesBuilder.put(key, value));
    }

    /**
     * Update the global set of attributes that will be appended to every span and event.
     *
     * @param attributesUpdater A function which will update the current set of attributes, by
     *                          operating on a {@link AttributesBuilder} from the current set.
     */
    public void updateGlobalAttributes(Consumer<AttributesBuilder> attributesUpdater) {
        globalAttributes.update(attributesUpdater);
    }

    /**
     * Sanitize view for session recording.
     * View will be blurred in session recording.
     *
     * @param view
     */
    public void addSanitizedElement(View view) {
        middlewareScreenshotManager.setViewForBlur(view);
    }

    /**
     * Remove view from sanitization.
     * View will be shown in session recording.
     *
     * @param view
     */
    public void removeSanitizedElement(View view) {
        middlewareScreenshotManager.removeSanitizedElement(view);
    }

    // for testing only
    static void resetSingletonForTest() {
        INSTANCE = null;
    }

    public void flushSpans() {
        OpenTelemetry openTelemetry = getOpenTelemetry();
        if (openTelemetry instanceof OpenTelemetrySdk) {
            ((OpenTelemetrySdk) openTelemetry)
                    .getSdkTracerProvider()
                    .forceFlush()
                    .join(1, TimeUnit.SECONDS);
        }
    }

    /**
     * Inject Javascript interface into WebView enables browser based RUM implementation.
     *
     * @param webView WebView object to inject Javascript interface.
     */
    @Override
    public void integrateWithBrowserRum(WebView webView) {
        webView.addJavascriptInterface(new NativeRumSessionId(this), "MiddlewareNative");
    }

    /**
     * Updates the current location. The latitude and longitude will be appended to every span and
     * event.
     *
     * <p>Note: this operation performs an atomic update. You can safely call it from your {@code
     * LocationListener} or {@code LocationCallback}.
     *
     * @param location the current location. Passing {@code null} removes the location data.
     */
    @Override
    public void updateLocation(@Nullable Location location) {
        if (location == null) {
            updateGlobalAttributes(
                    attributes ->
                            attributes
                                    .remove(LOCATION_LATITUDE_KEY)
                                    .remove(LOCATION_LONGITUDE_KEY));
        } else {
            updateGlobalAttributes(
                    attributes ->
                            attributes
                                    .put(LOCATION_LATITUDE_KEY, location.getLatitude())
                                    .put(LOCATION_LONGITUDE_KEY, location.getLongitude()));
        }
    }

    /**
     * Send an DEBUG log message.
     *
     * @param TAG     Used to identify the source of a log message.  It usually identifies
     *                the class or activity where the log call occurs.
     * @param message The message you would like logged.
     */
    @Override
    public void d(@NonNull String TAG, @NonNull String message) {
        Log.d(TAG, message);
        log(TAG, message, Severity.DEBUG);
    }

    /**
     * Send an ERROR log message.
     *
     * @param TAG     Used to identify the source of a log message.  It usually identifies
     *                the class or activity where the log call occurs.
     * @param message The message you would like logged.
     */
    @Override
    public void e(@NonNull String TAG, @NonNull String message) {
        Log.e(TAG, message);
        log(TAG, message, Severity.ERROR);
    }

    /**
     * Send an INFO log message.
     *
     * @param TAG     Used to identify the source of a log message.  It usually identifies
     *                the class or activity where the log call occurs.
     * @param message The message you would like logged.
     */
    @Override
    public void i(@NonNull String TAG, @NonNull String message) {
        Log.i(TAG, message);
        log(TAG, message, Severity.INFO);
    }

    /**
     * Send an WARN log message.
     *
     * @param TAG     Used to identify the source of a log message.  It usually identifies
     *                the class or activity where the log call occurs.
     * @param message The message you would like logged.
     */
    @Override
    public void w(@NonNull String TAG, @NonNull String message) {
        Log.e(TAG, message);
        log(TAG, message, Severity.WARN);
    }

    /**
     * Send an FATAL log message.
     *
     * @param TAG     Used to identify the source of a log message.  It usually identifies
     *                the class or activity where the log call occurs.
     * @param message The message you would like logged.
     */
    @Override
    public void wtf(@NonNull String TAG, @NonNull String message) {
        Log.wtf(TAG, message);
        log(TAG, message, Severity.ERROR);
    }

    private void log(String TAG, String message, Severity severity) {
        LOGGER.logRecordBuilder()
                .setSeverity(severity)
                .setSeverityText(severity.name())
                .setBody(message)
                .setAttribute(AttributeKey.stringKey("TAG"), TAG)
                .emit();

    }
}