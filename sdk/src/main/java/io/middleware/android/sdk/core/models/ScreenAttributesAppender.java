package io.middleware.android.sdk.core.models;

import static io.opentelemetry.android.RumConstants.SCREEN_NAME_KEY;

import io.opentelemetry.android.instrumentation.activity.VisibleScreenTracker;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SpanProcessor;

public class ScreenAttributesAppender implements SpanProcessor {

    private final VisibleScreenTracker visibleScreenTracker;

    public ScreenAttributesAppender(VisibleScreenTracker visibleScreenTracker) {
        this.visibleScreenTracker = visibleScreenTracker;
    }

    @Override
    public void onStart(Context parentContext, ReadWriteSpan span) {
        String currentScreen = visibleScreenTracker.getCurrentlyVisibleScreen();
        span.setAttribute(SCREEN_NAME_KEY, currentScreen);
    }

    @Override
    public boolean isStartRequired() {
        return true;
    }

    @Override
    public void onEnd(ReadableSpan span) {
    }

    @Override
    public boolean isEndRequired() {
        return false;
    }
}
