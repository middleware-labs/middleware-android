package io.middleware.android.sample;

import static io.middleware.android.sdk.utils.Constants.APP_VERSION;

import android.app.Application;

import java.time.Duration;

import io.middleware.android.sdk.Middleware;
import io.middleware.android.sdk.builders.MiddlewareBuilder;
import io.middleware.android.sdk.core.replay.v2.RecordingOptions;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import okhttp3.Call;
import okhttp3.OkHttpClient;

public class CoffeeCartApplication extends Application {

    private Middleware middleware;
    private Call.Factory rumOkHttpClient;
    private ProductRepository productRepository;

    @Override
    public void onCreate() {
        super.onCreate();
        OkHttpClient baseClient = new OkHttpClient.Builder().build();

        if ("no_sdk".equals(BuildConfig.BENCH_MODE)) {
            // Benchmark baseline: app runs without the SDK. Middleware.getInstance()
            // resolves to the no-op implementation everywhere else in the app.
            middleware = Middleware.getInstance();
            rumOkHttpClient = baseClient;
            productRepository = new ProductRepository(rumOkHttpClient, middleware);
            return;
        }

        RecordingOptions.Builder recordingOptins = new RecordingOptions.Builder();
        recordingOptins.setMaskAllImages(false);
        recordingOptins.setMaskAllTextInputs(false);
        MiddlewareBuilder builder = Middleware.builder()
                .setGlobalAttributes(Attributes.of(
                        APP_VERSION, BuildConfig.VERSION_NAME,
                        AttributeKey.stringKey("app.build"), String.valueOf(BuildConfig.VERSION_CODE)
                ))
                .setRecordingOptions(recordingOptins.build())
                .setTarget(BuildConfig.TARGET)
                .setServiceName("CoffeeCart-Android")
                .setProjectName("CoffeeCart-Android")
                .setRumAccessToken(BuildConfig.ACCESS_KEY)
                .setSlowRenderingDetectionPollInterval(Duration.ofMillis(1000))
                .setDeploymentEnvironment("PROD");
        if ("recording_off".equals(BuildConfig.BENCH_MODE)) {
            builder.disableSessionRecording();
        } else if (!BuildConfig.RECORDING_V3) {
            builder.disableSessionRecordingV3();
        }
        builder.build(this);

        middleware = Middleware.getInstance();
        middleware.i("APP", "CoffeeCartApplication initialised – SDK ready");

        rumOkHttpClient = middleware.createRumOkHttpCallFactory(baseClient);

        productRepository = new ProductRepository(rumOkHttpClient, middleware);
    }

    public Middleware getMiddleware() {
        return middleware;
    }

    public Call.Factory getRumOkHttpClient() {
        return rumOkHttpClient;
    }

    public ProductRepository getProductRepository() {
        return productRepository;
    }
}
