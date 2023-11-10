package io.middleware.android.sample;

import android.app.Application;

import java.time.Duration;

import io.middleware.android.sdk.Middleware;

public class SampleApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Middleware.builder()
                .setTarget(BuildConfig.TARGET)
                .setServiceName("sample-android-app-1")
                .setProjectName("Mobile-SDK-Android")
                .setRumAccessToken(BuildConfig.ACCESS_KEY)
                .setSlowRenderingDetectionPollInterval(Duration.ofMillis(1000))
                .setDeploymentEnvironment("PROD")
                .build(this);
    }
}
