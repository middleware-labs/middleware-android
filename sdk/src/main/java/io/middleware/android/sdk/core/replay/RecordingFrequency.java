package io.middleware.android.sdk.core.replay;

/**
 * Defines how often screenshots are captured.
 */
public enum RecordingFrequency {
    LOW(1000L),      // 1 FPS
    STANDARD(330L),  // ~3 FPS
    HIGH(100L);      // 10 FPS

    private final long intervalMs;

    RecordingFrequency(long intervalMs) {
        this.intervalMs = intervalMs;
    }

    public long getIntervalMs() {
        return intervalMs;
    }
}
