package io.middleware.android.sample;

import static io.middleware.android.sdk.utils.Constants.APP_VERSION;

import android.app.Application;
import android.os.Build;

import java.time.Duration;

import io.middleware.android.sdk.Middleware;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;

public class SampleApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Middleware.builder()
                .setGlobalAttributes(Attributes.of(APP_VERSION, BuildConfig.VERSION_NAME))
                .setTarget(BuildConfig.TARGET)
                .setResourceAttributes(Attributes.of(
                        AttributeKey.stringKey("os"), "Android",
                        AttributeKey.stringKey("os.version"), Build.VERSION.RELEASE,
                        AttributeKey.stringKey("recording"), "1",
                        AttributeKey.stringKey("project.name"), "Mobile-SDK-Android",
                        AttributeKey.stringKey("service.name"), "Mobile-SDK-Android",
                        AttributeKey.stringKey("mw.rum"), "true")

                )
                .setServiceName("Mobile-SDK-Android")
                .setProjectName("Mobile-SDK-Android")
                .setRumAccessToken(BuildConfig.ACCESS_KEY)
                .setSlowRenderingDetectionPollInterval(Duration.ofMillis(1000))
                .setDeploymentEnvironment("PROD")
                .build(this);
    }
}
