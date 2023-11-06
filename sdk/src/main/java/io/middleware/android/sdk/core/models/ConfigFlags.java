package io.middleware.android.sdk.core.models;

import androidx.annotation.NonNull;

public class ConfigFlags {
    private boolean debugEnabled = false;
    private boolean diskBufferingEnabled = false;
    private boolean reactNativeSupportEnabled = false;
    private boolean crashReportingEnabled = true;
    private boolean networkMonitorEnabled = true;
    private boolean anrDetectionEnabled = true;
    private boolean slowRenderingDetectionEnabled = true;
    private boolean subprocessInstrumentationEnabled = true;
    private boolean backgroundInstrumentationDeferredUntilForeground = false;

    public void enableDebug() {
        debugEnabled = true;
    }

    public void enableDiskBuffering() {
        diskBufferingEnabled = true;
    }

    public void enableReactNativeSupport() {
        reactNativeSupportEnabled = true;
    }

    public void disableCrashReporting() {
        crashReportingEnabled = false;
    }

    public void disableNetworkMonitor() {
        networkMonitorEnabled = false;
    }

    public void disableAnrDetection() {
        anrDetectionEnabled = false;
    }

    public void disableSlowRenderingDetection() {
        slowRenderingDetectionEnabled = false;
    }

    public void disableSubprocessInstrumentation() {
        subprocessInstrumentationEnabled = false;
    }

    public void enableBackgroundInstrumentationDeferredUntilForeground() {
        backgroundInstrumentationDeferredUntilForeground = true;
    }

    public boolean isDebugEnabled() {
        return debugEnabled;
    }

    public boolean isSubprocessInstrumentationEnabled() {
        return subprocessInstrumentationEnabled;
    }

    public boolean isBackgroundInstrumentationDeferredUntilForeground() {
        return backgroundInstrumentationDeferredUntilForeground;
    }

    public boolean isAnrDetectionEnabled() {
        return anrDetectionEnabled;
    }

    public boolean isNetworkMonitorEnabled() {
        return networkMonitorEnabled;
    }

    public boolean isSlowRenderingDetectionEnabled() {
        return slowRenderingDetectionEnabled;
    }

    public boolean isCrashReportingEnabled() {
        return crashReportingEnabled;
    }

    public boolean isDiskBufferingEnabled() {
        return diskBufferingEnabled;
    }

    public boolean isReactNativeSupportEnabled() {
        return reactNativeSupportEnabled;
    }

    @NonNull
    @Override
    public String toString() {
        return "[debug:"
                + debugEnabled
                + ","
                + "crashReporting:"
                + crashReportingEnabled
                + ","
                + "anrReporting:"
                + anrDetectionEnabled
                + ","
                + "slowRenderingDetector:"
                + slowRenderingDetectionEnabled
                + ","
                + "networkMonitor:"
                + networkMonitorEnabled
                + "]";
    }
}