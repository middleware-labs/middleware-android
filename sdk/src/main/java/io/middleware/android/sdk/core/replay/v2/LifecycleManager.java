package io.middleware.android.sdk.core.replay.v2;

import static io.middleware.android.sdk.utils.Constants.LOG_TAG;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.ref.WeakReference;

public class LifecycleManager implements Application.ActivityLifecycleCallbacks {

    private final Context context;
    private WeakReference<Activity> currentActivityRef;
    private Application application;
    private volatile boolean isChangingConfiguration = false;

    public Activity getCurrentActivity() {
        return currentActivityRef != null ? currentActivityRef.get() : null;
    }

    public LifecycleManager(Context context, Activity initialActivity) {
        this.context = context;
        if (context instanceof Activity) {
            application = ((Activity) context).getApplication();
        } else if (context instanceof Application) {
            application = (Application) context;
        } else {
            application = (Application) context.getApplicationContext();
        }

        if (initialActivity != null) {
            currentActivityRef = new WeakReference<>(initialActivity);
            Log.d(LOG_TAG, "LifecycleManager initialized with activity: " + initialActivity.getLocalClassName());
        } else {
            Log.d(LOG_TAG, "LifecycleManager initialized without initial activity");
        }
        application.registerActivityLifecycleCallbacks(this);
    }

    public Context getContext() {
        return context;
    }

    public void unregister() {
        application.unregisterActivityLifecycleCallbacks(this);
        currentActivityRef = null;
        application = null;
        Log.d(LOG_TAG, "LifecycleManager unregistered");
    }

    @Override
    public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
        currentActivityRef = new WeakReference<>(activity);
    }

    @Override
    public void onActivityStarted(@NonNull Activity activity) {
        currentActivityRef = new WeakReference<>(activity);

    }

    @Override
    public void onActivityResumed(@NonNull Activity activity) {
        currentActivityRef = new WeakReference<>(activity);
    }

    @Override
    public void onActivityPaused(@NonNull Activity activity) {
        isChangingConfiguration = activity.isChangingConfigurations();
        if (isChangingConfiguration) {
            Log.d(LOG_TAG, "Activity paused due to configuration change: " + activity.getLocalClassName());
        }
    }

    @Override
    public void onActivityStopped(@NonNull Activity activity) {
        isChangingConfiguration = false;

    }

    @Override
    public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {

    }

    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {
        Activity current = currentActivityRef != null ? currentActivityRef.get() : null;
        if (current == activity && currentActivityRef != null) {
            currentActivityRef.clear();
            currentActivityRef = null;
        }
    }
}