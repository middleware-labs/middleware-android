package io.middleware.android.sdk.core.models;

import androidx.annotation.NonNull;

public class ConfigFlags {
    private boolean debugEnabled = false;
    private boolean activityLifecycleEnabled = true;
    private boolean reactNativeSupportEnabled = false;
    private boolean crashReportingEnabled = true;
    private boolean networkMonitorEnabled = true;
    private boolean anrDetectionEnabled = true;
    private boolean slowRenderingDetectionEnabled = true;

    public void enableDebug() {
        debugEnabled = true;
    }

    public void enableReactNativeSupport() {
        reactNativeSupportEnabled = true;
    }

    public void disableCrashReporting() {
        crashReportingEnabled = false;
    }

    public void disableActivityLifecycleMonitoring() {
        activityLifecycleEnabled = false;
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

    public boolean isDebugEnabled() {
        return debugEnabled;
    }

    public boolean isActivityLifecycleEnabled() {
        return activityLifecycleEnabled;
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

    public boolean isReactNativeSupportEnabled() {
        return reactNativeSupportEnabled;
    }

    @NonNull
    @Override
    public String toString() {
        return "[debug:"
                + debugEnabled
                + ","
                + "activityLifecycle:"
                + activityLifecycleEnabled
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