package io.middleware.android.sdk.extractors;


import static io.middleware.android.sdk.utils.Constants.COMPONENT_CRASH;
import static io.middleware.android.sdk.utils.Constants.COMPONENT_ERROR;
import static io.middleware.android.sdk.utils.Constants.COMPONENT_KEY;
import static io.middleware.android.sdk.utils.Constants.EVENT_TYPE;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import io.opentelemetry.android.instrumentation.crash.CrashDetails;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;

public final class CrashComponentExtractor implements AttributesExtractor<CrashDetails, Void> {

    private final AtomicBoolean crashHappened = new AtomicBoolean(false);

    @Override
    public void onStart(
            AttributesBuilder attributes, Context parentContext, CrashDetails crashDetails) {
        // the idea here is to set component=crash only for the first error that arrives here
        // when multiple threads fail at roughly the same time (e.g. because of an OOM error),
        // the first error to arrive here is actually responsible for crashing the app; and all
        // the others that are captured before OS actually kills the process are just additional
        // info (component=error)
        String component =
                crashHappened.compareAndSet(false, true)
                        ? COMPONENT_CRASH
                        : COMPONENT_ERROR;
        attributes.put(COMPONENT_KEY, component);
        attributes.put(EVENT_TYPE, COMPONENT_ERROR);
        if (crashDetails != null) {
            attributes.put("thread.id", crashDetails.getThread().getId());
            attributes.put("thread.name", crashDetails.getThread().getName());
            attributes.put("error.name", crashDetails.getThread().getName());
            attributes.put("exception.message", Objects.requireNonNull(crashDetails.getCause().getMessage()));
            attributes.put("error.message", Objects.requireNonNull(crashDetails.getCause().getMessage()));
            attributes.put("exception.stacktrace", stackTraceToString(crashDetails.getCause()));
            attributes.put("error.stack", stackTraceToString(crashDetails.getCause()));
            attributes.put("exception.type", crashDetails.getClass().getName());
            attributes.put("error.type", crashDetails.getClass().getName());
        }
    }

    @Override
    public void onEnd(
            AttributesBuilder attributes,
            Context context,
            CrashDetails crashDetails,
            Void unused,
            Throwable error) {
    }

    private String stackTraceToString(Throwable throwable) {
        StringWriter sw = new StringWriter(256);
        PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        pw.flush();
        return sw.toString();
    }
}
