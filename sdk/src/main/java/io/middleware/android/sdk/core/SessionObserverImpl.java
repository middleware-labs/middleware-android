package io.middleware.android.sdk.core;

import static io.middleware.android.sdk.utils.Constants.LOG_TAG;

import android.util.Log;

import androidx.annotation.NonNull;

import io.middleware.android.sdk.Middleware;
import io.opentelemetry.android.session.Session;

public class SessionObserverImpl implements io.opentelemetry.android.session.SessionObserver {

    final Middleware middleware = Middleware.getInstance();

    @Override
    public void onSessionEnded(@NonNull Session session) {
        if (middleware != null && middleware.stopRecording()) {
            Log.d(LOG_TAG, "Session recording stopped for sessionId: " + session.getId());
        }
    }

    @Override
    public void onSessionStarted(@NonNull Session session, @NonNull Session session1) {
        if (middleware != null && middleware.startRecording()) {
            Log.d(LOG_TAG, "Session recording started for sessionId: " + session.getId());
        }
    }
}
