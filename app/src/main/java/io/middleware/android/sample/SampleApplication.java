package io.middleware.android.sample;

import android.app.Application;

import java.time.Duration;

import io.middleware.android.sdk.Middleware;

public class SampleApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Middleware.builder()
                .setTarget("https://acb0-45-64-15-159.ngrok-free.app")
                .setServiceName("sample-android-app-1")
                .setProjectName("Mobile-SDK-Android")
                .setRumAccessToken("kxozdvbzbtoxbecweemnwgwhrwwkshuinoay")
                .setSlowRenderingDetectionPollInterval(Duration.ofMillis(1000))
                .setDeploymentEnvironment("PROD")
                .enableDebug()
                .enableReactNativeSupport()
                .build(this);
    }
}
