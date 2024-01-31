package io.middleware.android.sdk.extractors;


import static io.middleware.android.sdk.utils.Constants.COMPONENT_CRASH;
import static io.middleware.android.sdk.utils.Constants.COMPONENT_ERROR;
import static io.middleware.android.sdk.utils.Constants.COMPONENT_KEY;
import static io.middleware.android.sdk.utils.Constants.EVENT_TYPE;

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
    }

    @Override
    public void onEnd(
            AttributesBuilder attributes,
            Context context,
            CrashDetails crashDetails,
            Void unused,
            Throwable error) {
    }
}
