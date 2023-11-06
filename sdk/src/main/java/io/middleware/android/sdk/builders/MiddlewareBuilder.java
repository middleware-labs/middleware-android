package io.middleware.android.sdk.builders;

import static io.middleware.android.sdk.core.Constants.LOG_TAG;

import android.app.Application;
import android.util.Log;

import androidx.annotation.Nullable;

import java.time.Duration;

import io.middleware.android.sdk.Middleware;
import io.middleware.android.sdk.core.models.ConfigFlags;
import io.opentelemetry.android.instrumentation.network.CurrentNetworkProvider;
import io.opentelemetry.api.common.Attributes;

/**
 * A builder of {@link Middleware}.
 */
public final class MiddlewareBuilder {

    private static final Duration DEFAULT_SLOW_RENDERING_DETECTION_POLL_INTERVAL =
            Duration.ofSeconds(1);

    @Nullable
    public
    String projectName;
    @Nullable
    public
    String serviceName;
    @Nullable
    public
    String target;

    @Nullable
    public
    String rumAccessToken;

    @Nullable
    Application application;

    @Nullable
    public
    String sessionId;

    private final ConfigFlags configFlags = new ConfigFlags();

    public Duration slowRenderingDetectionPollInterval = DEFAULT_SLOW_RENDERING_DETECTION_POLL_INTERVAL;
    public Attributes globalAttributes = Attributes.empty();
    @Nullable
    public
    String deploymentEnvironment;
    boolean sessionBasedSamplerEnabled = false;
    double sessionBasedSamplerRatio = 1.0;
    boolean isSubprocess = false;

    /**
     * Sets the application name that will be used to identify your application in the Middleware RUM
     * UI.
     *
     * @return {@code this}
     */
    public MiddlewareBuilder setProjectName(String projectName) {
        this.projectName = projectName;
        return this;
    }

    public MiddlewareBuilder setServiceName(String serviceName) {
        this.serviceName = serviceName;
        return this;
    }

    protected MiddlewareBuilder setSessionId(String sessionId) {
        this.sessionId = sessionId;
        return this;
    }

    /**
     * Sets the "target" endpoint URL to be used by the RUM library.
     *
     * @return {@code this}
     */
    public MiddlewareBuilder setTarget(String target) {
        this.target = target;
        return this;
    }

    /**
     * Sets the RUM auth token to be used by the RUM library.
     *
     * @return {@code this}
     */
    public MiddlewareBuilder setRumAccessToken(String rumAuthToken) {
        this.rumAccessToken = rumAuthToken;
        return this;
    }

    /**
     * Enables debugging information to be emitted from the RUM library.
     *
     * <p>This feature is disabled by default. You can enable it by calling this method.
     *
     * @return {@code this}
     */
    public MiddlewareBuilder enableDebug() {
        configFlags.enableDebug();
        return this;
    }

    /**
     * Enables the storage-based buffering of telemetry. If this feature is enabled, telemetry is
     * buffered in the local storage until it is exported; otherwise, it is buffered in memory and
     * throttled.
     *
     * <p>This feature is disabled by default. You can enable it by calling this method.
     *
     * @return {@code this}
     */
    public MiddlewareBuilder enableDiskBuffering() {
        configFlags.enableDiskBuffering();
        return this;
    }

    /**
     * Enables support for the React Native instrumentation.
     *
     * <p>This feature is disabled by default. You can enable it by calling this method.
     *
     * @return {@code this}
     */
    public MiddlewareBuilder enableReactNativeSupport() {
        configFlags.enableReactNativeSupport();
        return this;
    }

    /**
     * Disables the crash reporting feature.
     *
     * <p>This feature is enabled by default. You can disable it by calling this method.
     *
     * @return {@code this}
     */
    public MiddlewareBuilder disableCrashReporting() {
        configFlags.disableCrashReporting();
        return this;
    }

    /**
     * Disables the network monitoring feature.
     *
     * <p>This feature is enabled by default. You can disable it by calling this method.
     *
     * @return {@code this}
     */
    public MiddlewareBuilder disableNetworkMonitor() {
        configFlags.disableNetworkMonitor();
        return this;
    }

    /**
     * Disables the ANR (application not responding) detection feature. If enabled, when the main
     * thread is unresponsive for 5 seconds or more, an event including the main thread's stack
     * trace will be reported to the RUM system.
     *
     * <p>This feature is enabled by default. You can disable it by calling this method.
     *
     * @return {@code this}
     */
    public MiddlewareBuilder disableAnrDetection() {
        configFlags.disableAnrDetection();
        return this;
    }

    /**
     * Disables the slow rendering detection feature.
     *
     * <p>This feature is enabled by default. You can disable it by calling this method.
     *
     * @return {@code this}
     */
    public MiddlewareBuilder disableSlowRenderingDetection() {
        configFlags.disableSlowRenderingDetection();
        return this;
    }

    /**
     * Configures the rate at which frame render durations are polled.
     *
     * @param interval The period that should be used for polling.
     * @return {@code this}
     */
    public MiddlewareBuilder setSlowRenderingDetectionPollInterval(Duration interval) {
        if (interval.toMillis() <= 0) {
            Log.e(
                    LOG_TAG,
                    "invalid slowRenderPollingDuration: " + interval + " is not positive");
            return this;
        }
        this.slowRenderingDetectionPollInterval = interval;
        return this;
    }

    /**
     * Provides a set of global {@link Attributes} that will be applied to every span generated by
     * the RUM instrumentation.
     *
     * @return {@code this}
     */
    public MiddlewareBuilder setGlobalAttributes(Attributes attributes) {
        this.globalAttributes = attributes == null ? Attributes.empty() : attributes;
        return this;
    }

    /**
     * Sets the deployment environment for this RUM instance. Deployment environment is passed along
     * as a span attribute to help identify in the Splunk RUM UI.
     *
     * @param environment The deployment environment name
     * @return {@code this}
     */
    public MiddlewareBuilder setDeploymentEnvironment(String environment) {
        this.deploymentEnvironment = environment;
        return this;
    }


    /**
     * Creates a new instance of {@link Middleware} with the settings of this {@link
     * MiddlewareBuilder}.
     *
     * <p>The returned {@link Middleware} is set as the global instance {@link
     * Middleware#getInstance()}. If there was a global {@link Middleware} instance configured before,
     * this method does not initialize a new one and simply returns the existing instance.
     */
    public Middleware build(Application application) {
        if (rumAccessToken == null || target == null || projectName == null) {
            throw new IllegalStateException(
                    "You must provide a rumAccessToken, target, and an projectName to create a valid Config instance.");
        }
        return Middleware.initialize(this, application, CurrentNetworkProvider::createAndStart);
    }

    /***
     * Enable deffer instrumentation when app started from background start until
     * app is brought to foreground, otherwise instrumentation data will never be
     * sent to exporter.
     *
     * <p>Use case : Track only app session started by user opening app</p>
     * @return {@code this}
     */
    public MiddlewareBuilder enableBackgroundInstrumentationDeferredUntilForeground() {
        configFlags.enableBackgroundInstrumentationDeferredUntilForeground();
        return this;
    }

    // one day maybe these can use kotlin delegation
    ConfigFlags getConfigFlags() {
        return configFlags;
    }

    public boolean isAnrDetectionEnabled() {
        return configFlags.isAnrDetectionEnabled();
    }

    public boolean isNetworkMonitorEnabled() {
        return configFlags.isNetworkMonitorEnabled();
    }

    public boolean isSlowRenderingDetectionEnabled() {
        return configFlags.isSlowRenderingDetectionEnabled();
    }

    public boolean isCrashReportingEnabled() {
        return configFlags.isCrashReportingEnabled();
    }

    boolean isDiskBufferingEnabled() {
        return configFlags.isDiskBufferingEnabled();
    }

    boolean isReactNativeSupportEnabled() {
        return configFlags.isReactNativeSupportEnabled();
    }

    boolean isSubprocessInstrumentationDisabled() {
        return !configFlags.isSubprocessInstrumentationEnabled();
    }

    boolean isBackgroundInstrumentationDeferredUntilForeground() {
        return configFlags.isBackgroundInstrumentationDeferredUntilForeground();
    }
}