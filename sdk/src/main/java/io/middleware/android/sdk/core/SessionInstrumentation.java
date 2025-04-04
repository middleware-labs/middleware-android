package io.middleware.android.sdk.core;

import androidx.annotation.NonNull;

import io.opentelemetry.android.instrumentation.AndroidInstrumentation;
import io.opentelemetry.android.instrumentation.InstallationContext;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Tracer;

public class SessionInstrumentation implements AndroidInstrumentation {
    @Override
    public void install(@NonNull InstallationContext installationContext) {
        installationContext.getSessionManager().addObserver(new SessionObserverImpl());
    }
}
