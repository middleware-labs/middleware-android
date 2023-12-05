package io.middleware.android.sample;

import static io.middleware.android.sdk.utils.Constants.APP_VERSION;

import android.app.Application;

import java.time.Duration;

import io.middleware.android.sdk.Middleware;
import io.opentelemetry.api.common.Attributes;

public class SampleApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Middleware.builder()
                .setGlobalAttributes(Attributes.of(APP_VERSION, BuildConfig.VERSION_NAME))
                .setTarget(BuildConfig.TARGET)
                .setServiceName("Mobile-SDK-Android")
                .setProjectName("Mobile-SDK-Android")
                .setRumAccessToken(BuildConfig.ACCESS_KEY)
                .setSlowRenderingDetectionPollInterval(Duration.ofMillis(1000))
                .setDeploymentEnvironment("PROD")
                .build(this);
    }
}
