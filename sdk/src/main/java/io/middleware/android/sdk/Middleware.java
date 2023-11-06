package io.middleware.android.sdk;

import static io.middleware.android.sdk.core.Constants.COMPONENT_ERROR;
import static io.middleware.android.sdk.core.Constants.COMPONENT_KEY;
import static io.middleware.android.sdk.core.Constants.LOCATION_LATITUDE_KEY;
import static io.middleware.android.sdk.core.Constants.LOCATION_LONGITUDE_KEY;
import static io.middleware.android.sdk.core.Constants.LOG_TAG;
import static io.middleware.android.sdk.core.Constants.RUM_TRACER_NAME;
import static io.middleware.android.sdk.core.Constants.WORKFLOW_NAME_KEY;

import android.app.Application;
import android.location.Location;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.webkit.WebView;

import androidx.annotation.Nullable;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

import io.middleware.android.sdk.builders.MiddlewareBuilder;
import io.middleware.android.sdk.core.RumInitializer;
import io.middleware.android.sdk.core.models.NativeRumSessionId;
import io.middleware.android.sdk.extractors.RumResponseAttributesExtractor;
import io.middleware.android.sdk.interfaces.IMiddleware;
import io.middleware.android.sdk.utils.ServerTimingHeaderParser;
import io.opentelemetry.android.GlobalAttributesSpanAppender;
import io.opentelemetry.android.OpenTelemetryRum;
import io.opentelemetry.android.instrumentation.network.CurrentNetworkProvider;
import io.opentelemetry.android.instrumentation.startup.AppStartupTimer;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.instrumentation.okhttp.v3_0.OkHttpTelemetry;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import okhttp3.Call;
import okhttp3.OkHttpClient;

/**
 * Entrypoint for the Middleware OpenTelemetry Instrumentation for Android.
 */
public class Middleware implements IMiddleware {
    // initialize this here, statically, to make sure we capture the earliest possible timestamp for
    // startup.
    private static final AppStartupTimer startupTimer = new AppStartupTimer();

    @Nullable
    private static Middleware INSTANCE;

    private final OpenTelemetryRum openTelemetryRum;
    private final GlobalAttributesSpanAppender globalAttributes;

    private static RumInitializer rumInitializer;

    static {
        Handler handler = new Handler(Looper.getMainLooper());
        startupTimer.detectBackgroundStart(handler);
    }

    public Middleware(OpenTelemetryRum openTelemetryRum, GlobalAttributesSpanAppender globalAttributes) {
        this.openTelemetryRum = openTelemetryRum;
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
            Application application,
            Function<Application, CurrentNetworkProvider> currentNetworkProviderFactory) {
        if (INSTANCE != null) {
            Log.w(LOG_TAG, "Singleton Middleware instance has already been initialized.");
            return INSTANCE;
        }
        rumInitializer = new RumInitializer(builder, application, new AppStartupTimer());
        INSTANCE = rumInitializer.initialize(currentNetworkProviderFactory);


        Log.i(
                LOG_TAG,
                "Middleware RUM monitoring initialized with session ID: "
                        + INSTANCE.getRumSessionId());


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
     * Initialize a no-op version of the Middleware API, including the instance of OpenTelemetry that
     * is available. This can be useful for testing, or configuring your app without RUM enabled,
     * but still using the APIs.
     *
     * @return A no-op instance of {@link Middleware}
     */
    public static Middleware noop() {
        return NoOpMiddleware.INSTANCE;
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
        return OkHttpTelemetry.builder(getOpenTelemetry())
                .addAttributesExtractor(
                        new RumResponseAttributesExtractor(new ServerTimingHeaderParser()))
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

    /**
     * Get the Middleware Session ID associated with this instance of the RUM instrumentation library.
     * Note: this value can change throughout the lifetime of an application instance, so it is
     * recommended that you do not cache this value, but always retrieve it from here when needed.
     */
    public String getRumSessionId() {
        return openTelemetryRum.getRumSessionId();
    }

    /**
     * Add a custom event to RUM monitoring. This can be useful to capture business events, or
     * simply add instrumentation to your application.
     *
     * <p>This event will be turned into a Span and sent to the RUM ingest along with other,
     * auto-generated spans.
     *
     * @param name       The name of the event.
     * @param attributes Any {@link Attributes} to associate with the event.
     */
    public void addRumEvent(String name, Attributes attributes) {
        if (isInitialized()) {
            INSTANCE.sendRumEvent(name, attributes);
        } else {
            Log.d(RUM_TRACER_NAME, "Unable to send rum event setup is not done properly.");
        }
    }

    private void sendRumEvent(String name, Attributes attributes) {
        attributes.toBuilder().put("session.id", getRumSessionId());
        rumInitializer.sendRumEvent(name, attributes);
    }

    @Override
    public void addEvent(String name, Attributes attributes) {
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
     * <p>Note: this operation performs an atomic update. The passed function should be free from
     * side effects, since it may be called multiple times in case of thread contention.
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
     * <p>Note: this operation performs an atomic update. The passed function should be free from
     * side effects, since it may be called multiple times in case of thread contention.
     *
     * @param attributesUpdater A function which will update the current set of attributes, by
     *                          operating on a {@link AttributesBuilder} from the current set.
     */
    public void updateGlobalAttributes(Consumer<AttributesBuilder> attributesUpdater) {
        globalAttributes.update(attributesUpdater);
    }

    // for testing only
    static void resetSingletonForTest() {
        INSTANCE = null;
    }

    // (currently) for testing only
    void flushSpans() {
        OpenTelemetry openTelemetry = getOpenTelemetry();
        if (openTelemetry instanceof OpenTelemetrySdk) {
            ((OpenTelemetrySdk) openTelemetry)
                    .getSdkTracerProvider()
                    .forceFlush()
                    .join(1, TimeUnit.SECONDS);
        }
    }

    @Override
    public void integrateWithBrowserRum(WebView webView) {
        webView.addJavascriptInterface(new NativeRumSessionId(this), "MiddlewareNative");
    }

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
}