package io.middleware.android.sdk.core.instrumentations.crash;

import static io.middleware.android.sdk.utils.Constants.COMPONENT_KEY;
import static io.middleware.android.sdk.utils.Constants.EVENT_TYPE;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Objects;

import io.opentelemetry.android.instrumentation.crash.CrashDetails;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;

public class CrashAttributesExtractor implements AttributesExtractor<CrashDetails, Void> {

    @Override
    public void onStart(AttributesBuilder attributes, Context parentContext, CrashDetails middlewareCrashDetails) {
        attributes.put(COMPONENT_KEY, "crash");
        attributes.put(EVENT_TYPE, "error");
        if (middlewareCrashDetails != null) {
            attributes.put("thread.id", middlewareCrashDetails.getThread().getId());
            attributes.put("thread.name", middlewareCrashDetails.getThread().getName());
            attributes.put("error.name", middlewareCrashDetails.getThread().getName());
            attributes.put("exception.message", Objects.requireNonNull(middlewareCrashDetails.getCause().getMessage()));
            attributes.put("error.message", Objects.requireNonNull(middlewareCrashDetails.getCause().getMessage()));
            attributes.put("exception.stacktrace", stackTraceToString(middlewareCrashDetails.getCause()));
            attributes.put("error.stack", stackTraceToString(middlewareCrashDetails.getCause()));
            attributes.put("exception.type", middlewareCrashDetails.getClass().getName());
            attributes.put("error.type", middlewareCrashDetails.getClass().getName());
        }
    }

    @Override
    public void onEnd(AttributesBuilder attributes, Context context, CrashDetails middlewareCrashDetails, Void unused, Throwable error) {
    }

    private String stackTraceToString(Throwable throwable) {
        StringWriter sw = new StringWriter(256);
        PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        pw.flush();
        return sw.toString();
    }
}
