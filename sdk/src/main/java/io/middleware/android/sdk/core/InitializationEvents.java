package io.middleware.android.sdk.core;

import static io.middleware.android.sdk.core.Constants.COMPONENT_APPSTART;
import static io.middleware.android.sdk.core.Constants.COMPONENT_KEY;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.middleware.android.sdk.core.models.ConfigFlags;
import io.opentelemetry.android.instrumentation.startup.AppStartupTimer;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;

class InitializationEvents {
    private final AppStartupTimer startupTimer;
    private final List<Event> events = new ArrayList<>();
    private long startTimeNanos = -1;

    InitializationEvents(AppStartupTimer startupTimer) {
        this.startupTimer = startupTimer;
    }

    void begin() {
        startTimeNanos = startupTimer.clockNow();
    }

    void emit(String eventName) {
        events.add(new Event(eventName, startupTimer.clockNow()));
    }

    void recordInitializationSpans(ConfigFlags flags, Tracer delegateTracer) {
        Tracer tracer =
                spanName ->
                        delegateTracer
                                .spanBuilder(spanName)
                                .setAttribute(COMPONENT_KEY, COMPONENT_APPSTART);

        Span overallAppStart = startupTimer.start(tracer);
        Span span =
                tracer.spanBuilder("SplunkRum.initialize")
                        .setParent(Context.current().with(overallAppStart))
                        .setStartTimestamp(startTimeNanos, TimeUnit.NANOSECONDS)
                        .startSpan();

        span.setAttribute("config_settings", flags.toString());

        for (Event initializationEvent : events) {
            span.addEvent(initializationEvent.name, initializationEvent.time, TimeUnit.NANOSECONDS);
        }
        long spanEndTime = startupTimer.clockNow();
        // we only want to create SplunkRum.initialize span when there is a AppStart span so we
        // register a callback that is called right before AppStart span is ended
        startupTimer.setCompletionCallback(() -> span.end(spanEndTime, TimeUnit.NANOSECONDS));
    }

    private static class Event {
        private final String name;
        private final long time;

        private Event(String name, long time) {
            this.name = name;
            this.time = time;
        }
    }
}
