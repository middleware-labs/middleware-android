package io.middleware.android.sdk.interfaces;

import android.app.Application;
import android.os.Looper;

import java.util.function.Function;

import io.middleware.android.sdk.Middleware;
import io.opentelemetry.android.instrumentation.network.CurrentNetworkProvider;

public interface IRum {
    Middleware initialize(Function<Application, CurrentNetworkProvider> currentNetworkProviderFactory, Looper mainLooper);
}
