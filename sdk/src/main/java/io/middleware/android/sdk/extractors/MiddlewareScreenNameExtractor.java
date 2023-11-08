
package io.middleware.android.sdk.extractors;

import android.app.Activity;

import androidx.fragment.app.Fragment;

import java.util.function.Function;

import io.opentelemetry.android.instrumentation.RumScreenName;
import io.opentelemetry.android.instrumentation.ScreenNameExtractor;

public class MiddlewareScreenNameExtractor implements ScreenNameExtractor {

    public static ScreenNameExtractor INSTANCE = new MiddlewareScreenNameExtractor();

    private MiddlewareScreenNameExtractor() {
    }

    @Override
    public String extract(Activity activity) {
        return getOrDefault(activity, DEFAULT::extract);
    }

    @Override
    public String extract(Fragment fragment) {
        return getOrDefault(fragment, DEFAULT::extract);
    }

    private <T> String getOrDefault(T obj, Function<T, String> defaultMethod) {
        RumScreenName rumScreenName = obj.getClass().getAnnotation(RumScreenName.class);
        if (rumScreenName != null) {
            return rumScreenName.value();
        }
        return defaultMethod.apply(obj);
    }
}
