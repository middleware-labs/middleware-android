package io.middleware.android.sdk.builders;

import static io.middleware.android.sdk.utils.Constants.DEFAULT_SLOW_RENDERING_DETECTION_POLL_INTERVAL;
import static io.middleware.android.sdk.utils.Constants.LOG_TAG;

import android.content.Context;
import android.util.Log;

import androidx.annotation.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import io.middleware.android.sdk.Middleware;
import io.middleware.android.sdk.core.models.ConfigFlags;
import io.middleware.android.sdk.core.replay.RecordingFrequency;
import io.middleware.android.sdk.core.replay.RecordingQuality;
import io.middleware.android.sdk.core.replay.v2.RecordingOptions;
import io.opentelemetry.api.common.Attributes;

public final class MiddlewareBuilder {

    /**
     * Propagate to every host unless the caller narrows it. This preserves the behaviour the SDK
     * has always had and matches the browser SDK's default.
     */
    private static final List<Pattern> DEFAULT_TRACE_PROPAGATION_TARGETS =
            Collections.singletonList(Pattern.compile(".*"));

    public String projectName;
    public String serviceName;
    public String target;
    public String rumAccessToken;

    private final ConfigFlags configFlags = new ConfigFlags();

    public Duration slowRenderingDetectionPollInterval = DEFAULT_SLOW_RENDERING_DETECTION_POLL_INTERVAL;
    public Attributes globalAttributes = Attributes.empty();
    public Attributes resourceAttributes = Attributes.empty();
    @Nullable
    public String deploymentEnvironment;
    /**
     * Fraction of sessions to sample for traces and session recordings. Default {@code 1.0}.
     */
    public double sessionSamplingRatio = 1.0;
    /**
     * Regexes matched against the full outbound request URL to decide which requests carry
     * trace-context headers. See {@link #setTracePropagationTargets(List)}.
     */
    public List<Pattern> tracePropagationTargets = DEFAULT_TRACE_PROPAGATION_TARGETS;
    public RecordingOptions recordingOptions = new RecordingOptions.Builder()
            .setFrequency(RecordingFrequency.LOW)
            .setQuality(RecordingQuality.LOW)
            .build();

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
     * Set recording options that will be used when doing recording
     */
    public MiddlewareBuilder setRecordingOptions(RecordingOptions recordingOptions) {
        this.recordingOptions = recordingOptions;
        return this;
    }

    /**
     * Enables verbose logging. Logs will be exported in traces if enabled.
     *
     * @return {@code this}
     */
    public MiddlewareBuilder enableDebug() {
        configFlags.enableDebug();
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
     * Disables the activity lifecycle monitoring instrumentation.
     *
     * @return {@code this}
     */
    public MiddlewareBuilder disableActivityLifecycleMonitoring() {
        configFlags.disableActivityLifecycleMonitoring();
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
     * Disable session recording. By default session recording is enabled.
     *
     * @return {@code this}
     */
    public MiddlewareBuilder disableSessionRecording() {
        configFlags.disableSessionRecording();
        return this;
    }

    /**
     * Enables v3 session recording: rrweb-compatible screenshot events sent through the
     * metrics endpoint, replayed with the standard web session-replay player. When enabled,
     * the {@code recordingV3} resource attribute is set and the legacy (v2) screenshot
     * recorder does not run. Has no effect if session recording is disabled via
     * {@link #disableSessionRecording()}.
     *
     * @return {@code this}
     */
    public MiddlewareBuilder disableSessionRecordingV3() {
        configFlags.disableSessionRecordingV3();
        return this;
    }

    /**
     * Disables automatic UI tap instrumentation. When enabled (the default), each user tap is
     * captured and emitted as a {@code tap} RUM span (with dp coordinates and the tapped view's
     * identity) so the backend can build a click heatmap for the app.
     *
     * @return {@code this}
     */
    public MiddlewareBuilder disableUIInstrumentation() {
        configFlags.disableUIInstrumentation();
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
     * Provides additional {@link Attributes} merged into the OTLP resource (e.g. SDK identity
     * attributes from a wrapper such as React Native). SDK-controlled resource attributes
     * (service.name, project.name, recording flags, ...) always take precedence.
     *
     * @return {@code this}
     */
    public MiddlewareBuilder setResourceAttributes(Attributes attributes) {
        this.resourceAttributes = attributes == null ? Attributes.empty() : attributes;
        return this;
    }

    /**
     * Sets the deployment environment for this RUM instance.
     *
     * @param environment The deployment environment name
     * @return {@code this}
     */
    public MiddlewareBuilder setDeploymentEnvironment(String environment) {
        this.deploymentEnvironment = environment;
        return this;
    }

    /**
     * Sets the session sampling ratio used by {@link io.opentelemetry.android.SessionIdRatioBasedSampler}
     * for traces and session recordings. Must be in {@code [0.0, 1.0]}.
     *
     * @param ratio fraction of sessions to keep
     * @return {@code this}
     */
    public MiddlewareBuilder setSessionSamplingRatio(double ratio) {
        if (ratio < 0.0) {
            this.sessionSamplingRatio = 0.0;
        } else if (ratio > 1.0) {
            this.sessionSamplingRatio = 1.0;
        } else {
            this.sessionSamplingRatio = ratio;
        }
        return this;
    }


    /**
     * Restricts which outbound requests carry trace-context headers, so that backend correlation
     * works for your own services without handing your trace ids to third parties.
     *
     * <p>Each pattern is searched for anywhere in the request URL, so
     * {@code Pattern.compile("api.example.com")} matches
     * {@code https://api.example.com/orders}. Only requests made through the client returned by
     * {@link Middleware#createRumOkHttpCallFactory(okhttp3.OkHttpClient)} are affected — that is
     * the only place this SDK writes trace headers.
     *
     * <p>Defaults to matching every URL. Passing {@code null} restores that default; passing an
     * empty list disables trace propagation entirely while still recording the HTTP spans.
     *
     * @param targets regexes matched against the full request URL
     * @return {@code this}
     */
    public MiddlewareBuilder setTracePropagationTargets(@Nullable List<Pattern> targets) {
        if (targets == null) {
            this.tracePropagationTargets = DEFAULT_TRACE_PROPAGATION_TARGETS;
        } else {
            this.tracePropagationTargets =
                    Collections.unmodifiableList(new ArrayList<>(targets));
        }
        return this;
    }

    /**
     * Whether {@link #setTracePropagationTargets(List)} has narrowed propagation away from the
     * default of every URL. When it has not, the filtering interceptor is not installed at all.
     */
    public boolean hasTracePropagationTargets() {
        return tracePropagationTargets != DEFAULT_TRACE_PROPAGATION_TARGETS;
    }

    /**
     * Creates a new instance of {@link Middleware} with the settings of this {@link
     * MiddlewareBuilder}.
     *
     * <p>The returned {@link Middleware} is set as the global instance {@link
     * Middleware#getInstance()}. If there was a global {@link Middleware} instance configured before,
     * this method does not initialize a new one and simply returns the existing instance.
     */
    public Middleware build(Context context) {
        if (rumAccessToken == null || target == null || projectName == null || serviceName == null) {
            throw new IllegalStateException(
                    "You must provide a rumAccessToken, target, projectName and an serviceName to create a valid Config instance.");
        }
        return Middleware.initialize(this, context);
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

    public boolean isActivityLifecycleEnabled() {
        return configFlags.isActivityLifecycleEnabled();
    }

    public boolean isDebugEnabled() {
        return configFlags.isDebugEnabled();
    }

    public boolean isRecordingEnabled() {
        return configFlags.isRecordingEnabled();
    }

    public boolean isRecordingV3Enabled() {
        return configFlags.isRecordingEnabled() && configFlags.isRecordingV3Enabled();
    }

    /**
     * Whether v3 is the configured recorder, independent of whether recording is enabled.
     * Unlike {@link #isRecordingV3Enabled()} this does not AND in the recording flag, so a
     * {@code startRecording()} call after a disabled-at-init setup still picks v3 rather
     * than falling back to the legacy v2 recorder.
     */
    public boolean isRecordingV3Configured() {
        return configFlags.isRecordingV3Enabled();
    }

    public boolean isUIInstrumentationEnabled() {
        return configFlags.isUIInstrumentationEnabled();
    }

    public ConfigFlags getConfigFlags() {
        return configFlags;
    }
}