package io.middleware.android.sdk.interfaces;

import android.os.Looper;

import io.middleware.android.sdk.Middleware;

public interface IRum {
    Middleware initialize(Looper mainLooper);
}
