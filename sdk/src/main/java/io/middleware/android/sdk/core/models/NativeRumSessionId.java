package io.middleware.android.sdk.core.models;

import android.webkit.JavascriptInterface;

import io.middleware.android.sdk.Middleware;

/**
 * Object to inject into WebViews as a javascript object, in order to integrate with browser RUM.
 */
public class NativeRumSessionId {
    private final Middleware middleware;

    public NativeRumSessionId(Middleware middleware) {
        this.middleware = middleware;
    }

    @JavascriptInterface
    public String getNativeSessionId() {
        return middleware.getRumSessionId();
    }
}