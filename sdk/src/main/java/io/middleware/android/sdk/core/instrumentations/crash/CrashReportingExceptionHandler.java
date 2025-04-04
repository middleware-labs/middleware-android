package io.middleware.android.sdk.core.instrumentations.crash;

import androidx.annotation.NonNull;

import java.util.concurrent.TimeUnit;

import io.opentelemetry.android.instrumentation.crash.CrashDetails;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.SdkTracerProvider;

final class CrashReportingExceptionHandler implements Thread.UncaughtExceptionHandler {
    private final Instrumenter<CrashDetails, Void> crashSender;
    private final SdkTracerProvider sdkTracerProvider;
    private final Thread.UncaughtExceptionHandler existingHandler;

    CrashReportingExceptionHandler(
            Instrumenter<CrashDetails, Void> crashSender,
            SdkTracerProvider sdkTracerProvider,
            Thread.UncaughtExceptionHandler existingHandler) {
        this.crashSender = crashSender;
        this.sdkTracerProvider = sdkTracerProvider;
        this.existingHandler = existingHandler;
    }

    @Override
    public void uncaughtException(@NonNull Thread t, @NonNull Throwable e) {
        reportCrash(t, e);

        // do our best to make sure the crash makes it out of the VM
        CompletableResultCode flushResult = sdkTracerProvider.forceFlush();
        flushResult.join(10, TimeUnit.SECONDS);

        // preserve any existing behavior
        if (existingHandler != null) {
            existingHandler.uncaughtException(t, e);
        }
    }

    private void reportCrash(Thread t, Throwable e) {
        CrashDetails crashDetails = CrashDetails.create(t, e);
        Context context = crashSender.start(Context.current(), crashDetails);
        crashSender.end(context, crashDetails, null, e);
    }
}
