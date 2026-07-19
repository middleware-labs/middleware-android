package io.middleware.android.sdk.core.replay;

import android.view.View;

/**
 * Common contract for session recorders (v2 screenshot archives, v3 rrweb
 * events) so recorder wiring — sampling sync, session watcher, sanitize API —
 * stays version-agnostic.
 */
public interface SessionRecorder {

    void start(Long startTimeMs);

    void stop();

    boolean isRunning();

    /**
     * Masks the given view in the recording.
     */
    void setViewForBlur(View view);

    /**
     * Removes a view previously registered with {@link #setViewForBlur(View)}.
     */
    void removeSanitizedElement(View element);
}
