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
        builder.build(this);

        middleware = Middleware.getInstance();
        middleware.i("APP", "CoffeeCartApplication initialised – SDK ready");

        OkHttpClient baseClient = new OkHttpClient.Builder().build();
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
