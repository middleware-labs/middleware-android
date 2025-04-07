package io.middleware.android.sdk.core.models;

import static io.middleware.android.sdk.utils.Constants.COMPONENT_APPSTART;
import static io.middleware.android.sdk.utils.Constants.COMPONENT_KEY;
import static io.middleware.android.sdk.utils.Constants.EVENT_TYPE;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.opentelemetry.android.instrumentation.startup.AppStartupTimer;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;

public class InitializationEvents {
    private final AppStartupTimer startupTimer;
    private final List<Event> events = new ArrayList<>();
    private long startTimeNanos = -1;

    public InitializationEvents(AppStartupTimer startupTimer) {
        this.startupTimer = startupTimer;
    }

    public void begin() {
        startTimeNanos = startupTimer.clockNow();
    }

    public void emit(String eventName) {
        events.add(new Event(eventName, startupTimer.clockNow()));
    }

    public void recordInitializationSpans(ConfigFlags flags, Tracer delegateTracer) {
        Tracer tracer =
                spanName ->
                        delegateTracer
                                .spanBuilder(spanName)
                                .setAttribute(COMPONENT_KEY, COMPONENT_APPSTART)
                                .setAttribute(EVENT_TYPE, "app_activity");


        Span overallAppStart = startupTimer.start(tracer);
        Span span =
                tracer.spanBuilder("Middleware.initialize")
                        .setParent(Context.current().with(overallAppStart))
                        .setStartTimestamp(startTimeNanos, TimeUnit.NANOSECONDS)
                        .startSpan();

        span.setAttribute("config_settings", flags.toString());

        for (Event initializationEvent : events) {
            span.addEvent(initializationEvent.name, initializationEvent.time, TimeUnit.NANOSECONDS);
        }
        long spanEndTime = startupTimer.clockNow();
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
