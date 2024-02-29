package io.middleware.android.sdk.core.replay.v2;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class ActivityCallbacks implements Application.ActivityLifecycleCallbacks{

    private final MiddlewareScreenshotManager middlewareScreenshotManager;

    public ActivityCallbacks(MiddlewareScreenshotManager middlewareScreenshotManager) {
        this.middlewareScreenshotManager = middlewareScreenshotManager;
    }

    @Override
    public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {

    }

    @Override
    public void onActivityStarted(@NonNull Activity activity) {

    }

    @Override
    public void onActivityResumed(@NonNull Activity activity) {
        middlewareScreenshotManager.setActivity(activity);
    }

    @Override
    public void onActivityPaused(@NonNull Activity activity) {

    }

    @Override
    public void onActivityStopped(@NonNull Activity activity) {

    }

    @Override
    public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {

    }

    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {

    }
}
