package io.middleware.android.sdk;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Activity;

import org.junit.Test;

/**
 * The recording-session watcher polls {@code getRumSessionId()}, and OpenTelemetry Android bumps
 * its session inactivity timer on every such call. Polling while backgrounded would keep that
 * timer permanently reset and stop the background session timeout from ever firing, so the
 * watcher runs only while {@link Middleware.ForegroundTracker} reports the foreground.
 */
public class ForegroundTrackerTest {

    private static final Activity ACTIVITY = null;

    @Test
    public void reportsForegroundBeforeAnyLifecycleCallback() {
        // A process that has just launched but not yet started an activity is starting up, not
        // backgrounded; the watcher should run.
        assertTrue(new Middleware.ForegroundTracker().isInForeground());
    }

    @Test
    public void reportsBackgroundOnceTheLastActivityStops() {
        Middleware.ForegroundTracker tracker = new Middleware.ForegroundTracker();

        tracker.onActivityStarted(ACTIVITY);
        assertTrue(tracker.isInForeground());

        tracker.onActivityStopped(ACTIVITY);
        assertFalse(tracker.isInForeground());
    }

    @Test
    public void reportsForegroundAgainWhenAnActivityRestarts() {
        Middleware.ForegroundTracker tracker = new Middleware.ForegroundTracker();

        tracker.onActivityStarted(ACTIVITY);
        tracker.onActivityStopped(ACTIVITY);
        tracker.onActivityStarted(ACTIVITY);

        assertTrue(tracker.isInForeground());
    }

    @Test
    public void staysForegroundWhileActivitiesOverlap() {
        // start B before stopping A, the ordering used when one activity launches another.
        Middleware.ForegroundTracker tracker = new Middleware.ForegroundTracker();

        tracker.onActivityStarted(ACTIVITY);
        tracker.onActivityStarted(ACTIVITY);
        tracker.onActivityStopped(ACTIVITY);

        assertTrue(tracker.isInForeground());
    }

    @Test
    public void survivesAStopWithNoMatchingStart() {
        // Callbacks are registered at SDK init, which can happen while an activity is already
        // started; the first event seen is then a stop. The count must not go negative or the
        // watcher would never run again.
        Middleware.ForegroundTracker tracker = new Middleware.ForegroundTracker();

        tracker.onActivityStopped(ACTIVITY);
        assertFalse(tracker.isInForeground());

        tracker.onActivityStarted(ACTIVITY);
        assertTrue(tracker.isInForeground());
    }
}
